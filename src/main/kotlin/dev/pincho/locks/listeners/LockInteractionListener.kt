package dev.pincho.locks.listeners

import dev.pincho.locks.PinchosLocks
import dev.pincho.locks.config.ConfigManager
import dev.pincho.locks.managers.LockManager
import dev.pincho.locks.managers.LockpickManager
import dev.pincho.locks.models.Lock
import dev.pincho.locks.models.LockableBlock
import dev.pincho.locks.utils.MessageUtils
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.data.type.Door
import org.bukkit.block.data.type.TrapDoor
import org.bukkit.block.data.type.Gate
import org.bukkit.block.data.Openable
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.inventory.EquipmentSlot

/**
 * Handles player interactions with locks.
 * Optimized and simplified implementation.
 */
class LockInteractionListener(
    private val plugin: PinchosLocks,
    private val lockManager: LockManager,
    private val lockpickManager: LockpickManager,
    private val config: ConfigManager,
    private val messages: MessageUtils
) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return

        val player = event.player
        val block = event.clickedBlock ?: return
        val item = event.item

        when (event.action) {
            Action.LEFT_CLICK_BLOCK -> {
                lockManager.getLock(block)?.let { showLockInfo(player, it) }
            }
            Action.RIGHT_CLICK_BLOCK -> {
                when {
                    item != null && lockManager.isLockItem(item) ->
                        handleLockPlacement(event, player, block, item)
                    item != null && player.isSneaking && plugin.getLockpickCommands().isLockpick(item) ->
                        handleLockpickAttempt(event, player, block, item)
                    LockableBlock.isLockable(block) ->
                        handleLockedBlockAccess(event, player, block)
                }
            }
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onItemSwitch(event: PlayerItemHeldEvent) {
        if (lockpickManager.isPicking(event.player)) {
            lockpickManager.cancelLockpicking(event.player)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val location = event.inventory.location ?: return

        if (event.inventory.type !in LOCKABLE_INVENTORY_TYPES) return

        val lock = lockManager.getLock(location) ?: return

        // Only handle lockpicked access (not owner/trusted)
        if (!lock.isOwner(player.uniqueId) && !lock.isTrusted(player.uniqueId)) {
            lockpickManager.cancelAccessTimer(player)
            lockManager.removeTemporaryAccess(player.uniqueId, lock.id)

            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                messages.sendActionBar(player, "lock.secured")
            }, 1L)
        }
    }

    private fun showLockInfo(player: Player, lock: Lock) {
        val tier = lock.getTier()
        val tierIcon = TIER_ICONS[tier] ?: "§6🔒"

        player.spigot().sendMessage(
            net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
            net.md_5.bungee.api.chat.TextComponent("§6🔒 §7${lock.ownerName} §8| $tierIcon ${tier.displayName}")
        )
    }

    private fun handleLockPlacement(
        event: PlayerInteractEvent,
        player: Player,
        block: Block,
        item: org.bukkit.inventory.ItemStack
    ) {
        event.isCancelled = true

        if (!player.hasPermission("pinchoslocks.use")) {
            messages.send(player, "general.no-permission")
            return
        }

        val tier = lockManager.getLockTierFromItem(item) ?: return

        when (val result = lockManager.placeLock(player, block, tier)) {
            is LockManager.LockResult.Success -> {
                messages.send(player, result.messageKey, result.placeholders)
                if (item.amount > 1) item.amount-- else player.inventory.setItemInMainHand(null)
            }
            is LockManager.LockResult.Failure -> {
                messages.send(player, result.messageKey, result.placeholders)
            }
        }
    }

    private fun handleLockedBlockAccess(event: PlayerInteractEvent, player: Player, block: Block) {
        val lock = lockManager.getLock(block) ?: return

        val playerUuid = player.uniqueId
        val playerUuidStr = playerUuid.toString()

        // Check access level - using optimized string-based checks
        val accessLevel = when {
            lock.isOwnerByString(playerUuidStr) -> AccessLevel.OWNER
            lock.isTrustedByString(playerUuidStr) -> AccessLevel.TRUSTED
            player.hasPermission("pinchoslocks.bypass") -> AccessLevel.BYPASS
            lockManager.hasTemporaryAccess(playerUuid, lock.id) -> AccessLevel.TEMPORARY
            else -> AccessLevel.DENIED
        }

        when (accessLevel) {
            AccessLevel.OWNER, AccessLevel.TRUSTED -> {
                // Silent access - just allow
                allowAccess(event, block)
            }
            AccessLevel.BYPASS -> {
                // Staff bypass - show message
                messages.send(player, "lock.bypass")
                allowAccess(event, block)
            }
            AccessLevel.TEMPORARY -> {
                // Lockpicked access - consume it
                lockpickManager.cancelAccessTimer(player)
                lockManager.removeTemporaryAccess(playerUuid, lock.id)
                lockManager.playAccessGrantedEffects(player, block.location)
                allowAccess(event, block)
            }
            AccessLevel.DENIED -> {
                // Block access - show message with lockpick hint
                event.isCancelled = true
                event.setUseInteractedBlock(Event.Result.DENY)
                event.setUseItemInHand(Event.Result.DENY)

                lockManager.playAccessDeniedEffects(player, block.location)
                messages.send(player, "lock.access-denied-lockpick")
            }
        }
    }

    private fun handleLockpickAttempt(
        event: PlayerInteractEvent,
        player: Player,
        block: Block,
        item: org.bukkit.inventory.ItemStack
    ) {
        val lock = lockManager.getLock(block) ?: return

        val playerUuidStr = player.uniqueId.toString()

        // Check if player already has access - using optimized string-based checks
        when {
            lock.isOwnerByString(playerUuidStr) || lock.isTrustedByString(playerUuidStr) -> {
                allowAccess(event, block)
                return
            }
            player.hasPermission("pinchoslocks.bypass") -> {
                messages.send(player, "lock.bypass")
                allowAccess(event, block)
                return
            }
            lockManager.hasTemporaryAccess(player.uniqueId, lock.id) -> {
                lockpickManager.cancelAccessTimer(player)
                lockManager.removeTemporaryAccess(player.uniqueId, lock.id)
                lockManager.playAccessGrantedEffects(player, block.location)
                allowAccess(event, block)
                return
            }
        }

        // Start lockpicking
        event.isCancelled = true

        when (val result = lockpickManager.startLockpicking(player, lock, item, block.location)) {
            is LockpickManager.LockpickResult.Disabled -> messages.send(player, "lockpick.disabled")
            is LockpickManager.LockpickResult.AlreadyPicking -> {}
            is LockpickManager.LockpickResult.OnCooldown -> {
                messages.send(player, "general.cooldown",
                    mapOf("time" to String.format("%.1f", lockpickManager.getRemainingCooldown(player))))
            }
            is LockpickManager.LockpickResult.NotPickable -> messages.send(player, "lockpick.not-pickable")
            is LockpickManager.LockpickResult.NoPermission -> messages.send(player, "general.no-permission")
            is LockpickManager.LockpickResult.LockpickTooWeak -> messages.send(player, "lockpick.too-weak")
            is LockpickManager.LockpickResult.Failed -> {
                if (result.reason == "must_sneak") messages.send(player, "lockpick.must-sneak")
            }
            is LockpickManager.LockpickResult.Success, is LockpickManager.LockpickResult.Broke -> {}
        }
    }

    private fun allowAccess(event: PlayerInteractEvent, block: Block) {
        if (requiresManualOpen(block)) {
            event.isCancelled = true
            openBlockManually(event.player, block)
        } else {
            event.isCancelled = false
            event.setUseInteractedBlock(Event.Result.ALLOW)
            event.setUseItemInHand(Event.Result.DENY)
        }
    }

    private fun requiresManualOpen(block: Block): Boolean {
        return block.type == Material.IRON_DOOR || block.type == Material.IRON_TRAPDOOR
    }

    @Suppress("UNUSED_PARAMETER")
    private fun openBlockManually(player: Player, block: Block) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val blockData = block.blockData

            when (blockData) {
                is Door -> {
                    blockData.isOpen = !blockData.isOpen
                    block.blockData = blockData
                    val sound = if (blockData.isOpen) Sound.BLOCK_IRON_DOOR_OPEN else Sound.BLOCK_IRON_DOOR_CLOSE
                    block.world.playSound(block.location, sound, 1.0f, 1.0f)
                }
                is TrapDoor -> {
                    blockData.isOpen = !blockData.isOpen
                    block.blockData = blockData
                    val sound = if (blockData.isOpen) Sound.BLOCK_IRON_TRAPDOOR_OPEN else Sound.BLOCK_IRON_TRAPDOOR_CLOSE
                    block.world.playSound(block.location, sound, 1.0f, 1.0f)
                }
                is Gate -> {
                    blockData.isOpen = !blockData.isOpen
                    block.blockData = blockData
                    val sound = if (blockData.isOpen) Sound.BLOCK_FENCE_GATE_OPEN else Sound.BLOCK_FENCE_GATE_CLOSE
                    block.world.playSound(block.location, sound, 1.0f, 1.0f)
                }
                is Openable -> {
                    blockData.isOpen = !blockData.isOpen
                    block.blockData = blockData
                }
            }
        })
    }

    private enum class AccessLevel {
        OWNER, TRUSTED, BYPASS, TEMPORARY, DENIED
    }

    companion object {
        private val LOCKABLE_INVENTORY_TYPES = setOf(
            InventoryType.CHEST,
            InventoryType.BARREL,
            InventoryType.SHULKER_BOX,
            InventoryType.ENDER_CHEST
        )

        private val TIER_ICONS = mapOf(
            dev.pincho.locks.models.LockTier.BRONZE to "§6🥉",
            dev.pincho.locks.models.LockTier.SILVER to "§f🥈",
            dev.pincho.locks.models.LockTier.GOLD to "§e🥇"
        )
    }
}
