package dev.pincho.locks.listeners

import dev.pincho.locks.PinchosLocks
import dev.pincho.locks.config.ConfigManager
import dev.pincho.locks.managers.KeyManager
import dev.pincho.locks.managers.LockManager
import dev.pincho.locks.managers.LockpickManager
import dev.pincho.locks.models.LockableBlock
import dev.pincho.locks.utils.MessageUtils
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.Container
import org.bukkit.block.DoubleChest
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
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.inventory.EquipmentSlot

/**
 * Handles player interactions with locks.
 * This includes placing locks, using keys, lockpicking, and accessing locked blocks.
 */
class LockInteractionListener(
    private val plugin: PinchosLocks,
    private val lockManager: LockManager,
    private val keyManager: KeyManager,
    private val lockpickManager: LockpickManager,
    private val config: ConfigManager,
    private val messages: MessageUtils
) : Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        // Only handle main hand interactions to prevent double-firing
        if (event.hand != EquipmentSlot.HAND) return

        val player = event.player
        val block = event.clickedBlock ?: return
        val item = event.item

        // Handle right-click interactions
        if (event.action == Action.RIGHT_CLICK_BLOCK) {
            // Check if player is trying to place a lock
            if (item != null && lockManager.isLockItem(item)) {
                handleLockPlacement(event, player, block, item)
                return
            }

            // Check if player is using a lockpick on a locked block
            if (item != null && plugin.getLockpickCommands().isLockpick(item)) {
                handleLockpickAttempt(event, player, block, item)
                return
            }

            // Check if block is lockable and potentially locked
            if (LockableBlock.isLockable(block)) {
                handleLockedBlockAccess(event, player, block)
            }
        }
    }

    /**
     * Cancels lockpicking if player changes held item.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onItemSwitch(event: PlayerItemHeldEvent) {
        val player = event.player
        if (lockpickManager.isPicking(player)) {
            lockpickManager.cancelLockpicking(player)
        }
    }

    /**
     * Handles inventory close - shows message that the lock is secured again.
     * This applies to containers that were opened via lockpicking.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val inventory = event.inventory

        // Check if this is a container type that can be locked
        val inventoryType = inventory.type
        if (inventoryType != InventoryType.CHEST &&
            inventoryType != InventoryType.BARREL &&
            inventoryType != InventoryType.SHULKER_BOX &&
            inventoryType != InventoryType.ENDER_CHEST) {
            return
        }

        // Get the location of the inventory holder
        val location = inventory.location ?: return

        // Check if this container has a lock
        val lock = lockManager.getLock(location) ?: return

        // Check if player is NOT the owner (meaning they accessed via lockpick, key, or trust)
        if (!lock.isOwner(player.uniqueId)) {
            // Cancel any access timer
            lockpickManager.cancelAccessTimer(player)

            // Remove temporary access if any
            lockManager.removeTemporaryAccess(player.uniqueId, lock.id)

            // Send message that the lock is secured again
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                player.sendMessage("§c§l🔒 §cEl candado se ha vuelto a cerrar. Debes ganzuarlo nuevamente para acceder.")

                // Also show in actionbar
                player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent("§c§l🔒 §cCandado asegurado")
                )
            }, 1L) // Small delay to ensure smooth transition
        }
    }

    /**
     * Handles lock placement on a block.
     */
    private fun handleLockPlacement(
        event: PlayerInteractEvent,
        player: Player,
        block: Block,
        item: org.bukkit.inventory.ItemStack
    ) {
        event.isCancelled = true

        // Check basic permission
        if (!player.hasPermission("pinchoslocks.use")) {
            messages.send(player, "general.no-permission")
            return
        }

        val tier = lockManager.getLockTierFromItem(item) ?: return

        val result = lockManager.placeLock(player, block, tier)

        when (result) {
            is LockManager.LockResult.Success -> {
                messages.send(player, result.messageKey, result.placeholders)

                // Consume one lock item
                if (item.amount > 1) {
                    item.amount = item.amount - 1
                } else {
                    player.inventory.setItemInMainHand(null)
                }
            }
            is LockManager.LockResult.Failure -> {
                messages.send(player, result.messageKey, result.placeholders)
            }
        }
    }

    /**
     * Handles access to a potentially locked block.
     */
    private fun handleLockedBlockAccess(
        event: PlayerInteractEvent,
        player: Player,
        block: Block
    ) {
        plugin.logger.info("[Debug] handleLockedBlockAccess for ${player.name} on ${block.type}")

        val accessResult = lockManager.canAccess(player, block)
        plugin.logger.info("[Debug] Access result: ${accessResult::class.simpleName}")

        when (accessResult) {
            is LockManager.AccessResult.Unlocked -> {
                plugin.logger.info("[Debug] Block is unlocked, allowing normal interaction")
                // No lock, allow normal interaction
                return
            }
            is LockManager.AccessResult.Owner -> {
                plugin.logger.info("[Debug] Player is owner, FORCING access allowed")
                // Owner always has access - FORCE allow the interaction
                event.isCancelled = false
                event.setUseInteractedBlock(Event.Result.ALLOW)
                event.setUseItemInHand(Event.Result.ALLOW)
                return
            }
            is LockManager.AccessResult.Trusted -> {
                plugin.logger.info("[Debug] Player is trusted, FORCING access allowed")
                // Trusted player has access - FORCE allow the interaction
                event.isCancelled = false
                event.setUseInteractedBlock(Event.Result.ALLOW)
                event.setUseItemInHand(Event.Result.ALLOW)
                return
            }
            is LockManager.AccessResult.Bypass -> {
                plugin.logger.info("[Debug] Player has bypass permission, FORCING access allowed")
                // Admin bypass - FORCE allow the interaction
                event.isCancelled = false
                event.setUseInteractedBlock(Event.Result.ALLOW)
                event.setUseItemInHand(Event.Result.ALLOW)
                messages.send(player, "lock.bypass")
                return
            }
            is LockManager.AccessResult.HasKey -> {
                plugin.logger.info("[Debug] Player has key, FORCING access allowed")
                // Player has a valid key
                if (!keyManager.canUseKey(player)) {
                    val remaining = keyManager.getRemainingCooldown(player)
                    messages.send(player, "general.cooldown", mapOf("time" to String.format("%.1f", remaining)))
                    event.isCancelled = true
                    return
                }

                keyManager.recordKeyUsage(player)
                lockManager.playAccessGrantedEffects(player, block.location)
                messages.send(player, "lock.access-granted")
                // FORCE allow the interaction
                event.isCancelled = false
                event.setUseInteractedBlock(Event.Result.ALLOW)
                event.setUseItemInHand(Event.Result.ALLOW)
                return
            }
            is LockManager.AccessResult.TemporaryAccess -> {
                plugin.logger.info("[Debug] Player has TEMPORARY ACCESS! Lock ID: ${accessResult.lock.id}")
                // Temporary access from lockpicking - cancel timer, remove access, and allow ONE opening
                val lock = accessResult.lock
                lockpickManager.cancelAccessTimer(player)
                lockManager.removeTemporaryAccess(player.uniqueId, lock.id)
                lockManager.playAccessGrantedEffects(player, block.location)

                // Send visible confirmation to player
                player.sendMessage("§a§l✓ §aAcceso temporal usado! Abriendo...")

                plugin.logger.info("[Debug] Calling openBlockManually for TemporaryAccess")
                // MANUALLY open the container or door - this is the key fix!
                event.isCancelled = true
                openBlockManually(player, block)
                return
            }
            is LockManager.AccessResult.Denied -> {
                plugin.logger.info("[Debug] Access DENIED for ${player.name}")
                // Access denied
                event.isCancelled = true
                lockManager.playAccessDeniedEffects(player, block.location)

                // Send message with lockpick suggestion
                messages.send(player, "lock.access-denied-lockpick")
            }
        }
    }

    /**
     * Handles lockpick attempt on a locked block.
     */
    private fun handleLockpickAttempt(
        event: PlayerInteractEvent,
        player: Player,
        block: Block,
        item: org.bukkit.inventory.ItemStack
    ) {
        plugin.logger.info("[Debug] handleLockpickAttempt for ${player.name} on ${block.type} (has lockpick in hand)")

        // Check if block is locked
        val lock = lockManager.getLock(block)
        if (lock == null) {
            plugin.logger.info("[Debug] Block is not locked, allowing normal interaction")
            // Not locked, let normal interaction happen
            return
        }

        plugin.logger.info("[Debug] Block is locked with ID: ${lock.id}")

        // Check if player already has access (owner, trusted, has key, temporary access, etc.)
        val accessResult = lockManager.canAccess(player, block)
        plugin.logger.info("[Debug] Access result in handleLockpickAttempt: ${accessResult::class.simpleName}")

        // If player has any form of access, let them interact normally
        if (accessResult !is LockManager.AccessResult.Denied) {
            // For temporary access (from lockpicking), cancel the timer, remove access, and allow ONE opening
            if (accessResult is LockManager.AccessResult.TemporaryAccess) {
                plugin.logger.info("[Debug] Player has TEMPORARY ACCESS in handleLockpickAttempt! Lock ID: ${accessResult.lock.id}")
                lockpickManager.cancelAccessTimer(player)
                lockManager.removeTemporaryAccess(player.uniqueId, accessResult.lock.id)
                lockManager.playAccessGrantedEffects(player, block.location)

                // Send visible confirmation to player
                player.sendMessage("§a§l✓ §aAcceso temporal usado! Abriendo...")

                plugin.logger.info("[Debug] Calling openBlockManually from handleLockpickAttempt")
                // MANUALLY open the container or door - this is the key fix!
                // Cancel the event to prevent normal processing, then handle manually
                event.isCancelled = true
                openBlockManually(player, block)
                return
            }
            plugin.logger.info("[Debug] Player has other access type: ${accessResult::class.simpleName}, allowing normal interaction")
            // For other access types, allow normal interaction
            event.isCancelled = false
            event.setUseInteractedBlock(Event.Result.ALLOW)
            return
        }

        // Player doesn't have access - NOW we cancel and try to lockpick
        event.isCancelled = true

        // Start lockpicking
        val result = lockpickManager.startLockpicking(player, lock, item, block.location)

        when (result) {
            is LockpickManager.LockpickResult.Disabled -> {
                messages.send(player, "lockpick.disabled")
            }
            is LockpickManager.LockpickResult.AlreadyPicking -> {
                // Already picking, ignore
            }
            is LockpickManager.LockpickResult.OnCooldown -> {
                val remaining = lockpickManager.getRemainingCooldown(player)
                messages.send(player, "general.cooldown", mapOf("time" to String.format("%.1f", remaining)))
            }
            is LockpickManager.LockpickResult.NotPickable -> {
                messages.send(player, "lockpick.not-pickable")
            }
            is LockpickManager.LockpickResult.NoPermission -> {
                messages.send(player, "general.no-permission")
            }
            is LockpickManager.LockpickResult.LockpickTooWeak -> {
                messages.send(player, "lockpick.too-weak")
            }
            is LockpickManager.LockpickResult.Failed -> {
                // Check specific failure reasons
                if (result.reason == "must_sneak") {
                    messages.send(player, "lockpick.must-sneak")
                }
            }
            is LockpickManager.LockpickResult.Success -> {
                // Minigame started, handled by LockpickManager
            }
            is LockpickManager.LockpickResult.Broke -> {
                // Handled internally
            }
        }
    }

    /**
     * Manually opens a container inventory or toggles a door/trapdoor/gate.
     * This is used when a player has temporary access from lockpicking.
     *
     * IMPORTANT: We use runTask to schedule the opening on the next tick
     * because Paper/Spigot may have issues when opening inventories
     * during a cancelled event in the same tick.
     */
    private fun openBlockManually(player: Player, block: Block) {
        plugin.logger.info("[Debug] openBlockManually called for ${player.name} on ${block.type} at ${block.location}")

        // Schedule on next tick to avoid issues with cancelled events
        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                val blockState = block.state
                val blockData = block.blockData

                plugin.logger.info("[Debug] Block state type: ${blockState::class.simpleName}, Block data type: ${blockData::class.simpleName}")

                // Handle Chest specifically (single and double)
                if (blockState is org.bukkit.block.Chest) {
                    plugin.logger.info("[Debug] Opening CHEST inventory for ${player.name}")
                    try {
                        // For double chests, getInventory() returns the combined DoubleChestInventory
                        val inventory = blockState.inventory
                        plugin.logger.info("[Debug] Chest inventory type: ${inventory.type}, size: ${inventory.size}, holder: ${inventory.holder?.javaClass?.simpleName}")

                        // Open the inventory
                        player.openInventory(inventory)
                        plugin.logger.info("[Debug] Chest openInventory called successfully")
                    } catch (e: Exception) {
                        plugin.logger.severe("[Debug] Error opening chest: ${e.message}")
                        e.printStackTrace()

                        // Fallback: try to get block inventory directly
                        try {
                            plugin.logger.info("[Debug] Trying fallback method...")
                            val blockInventory = blockState.blockInventory
                            player.openInventory(blockInventory)
                            plugin.logger.info("[Debug] Fallback openInventory called successfully")
                        } catch (e2: Exception) {
                            plugin.logger.severe("[Debug] Fallback also failed: ${e2.message}")
                        }
                    }
                    return@Runnable
                }

                // Handle other containers (barrels, shulker boxes, etc.)
                if (blockState is Container) {
                    plugin.logger.info("[Debug] Opening generic container inventory for ${player.name}")
                    try {
                        val inventory = blockState.inventory
                        plugin.logger.info("[Debug] Container inventory type: ${inventory.type}, size: ${inventory.size}")
                        player.openInventory(inventory)
                        plugin.logger.info("[Debug] Container openInventory called successfully")
                    } catch (e: Exception) {
                        plugin.logger.severe("[Debug] Error opening container: ${e.message}")
                        e.printStackTrace()
                    }
                    return@Runnable
                }

                // Handle doors
                if (blockData is Door) {
                    plugin.logger.info("[Debug] Toggling door for ${player.name}")
                    blockData.isOpen = !blockData.isOpen
                    block.blockData = blockData

                    // Check if it's iron or wooden door for sound
                    val isIron = block.type.name.contains("IRON")
                    val openSound = if (isIron) Sound.BLOCK_IRON_DOOR_OPEN else Sound.BLOCK_WOODEN_DOOR_OPEN
                    val closeSound = if (isIron) Sound.BLOCK_IRON_DOOR_CLOSE else Sound.BLOCK_WOODEN_DOOR_CLOSE
                    block.world.playSound(block.location, if (blockData.isOpen) openSound else closeSound, 1.0f, 1.0f)
                    return@Runnable
                }

                // Handle trapdoors
                if (blockData is TrapDoor) {
                    plugin.logger.info("[Debug] Toggling trapdoor for ${player.name}")
                    blockData.isOpen = !blockData.isOpen
                    block.blockData = blockData

                    val isIron = block.type.name.contains("IRON")
                    val openSound = if (isIron) Sound.BLOCK_IRON_TRAPDOOR_OPEN else Sound.BLOCK_WOODEN_TRAPDOOR_OPEN
                    val closeSound = if (isIron) Sound.BLOCK_IRON_TRAPDOOR_CLOSE else Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE
                    block.world.playSound(block.location, if (blockData.isOpen) openSound else closeSound, 1.0f, 1.0f)
                    return@Runnable
                }

                // Handle fence gates
                if (blockData is Gate) {
                    plugin.logger.info("[Debug] Toggling fence gate for ${player.name}")
                    blockData.isOpen = !blockData.isOpen
                    block.blockData = blockData
                    block.world.playSound(block.location, if (blockData.isOpen) Sound.BLOCK_FENCE_GATE_OPEN else Sound.BLOCK_FENCE_GATE_CLOSE, 1.0f, 1.0f)
                    return@Runnable
                }

                // Generic openable blocks
                if (blockData is Openable) {
                    plugin.logger.info("[Debug] Toggling generic openable for ${player.name}")
                    blockData.isOpen = !blockData.isOpen
                    block.blockData = blockData
                    return@Runnable
                }

                plugin.logger.warning("[Debug] Block type ${block.type} not handled in openBlockManually")

            } catch (e: Exception) {
                plugin.logger.severe("[Debug] CRITICAL ERROR in openBlockManually: ${e.message}")
                e.printStackTrace()
            }
        })
    }

    /**
     * Checks if a player is sneaking and holding a specific item type.
     * Useful for special interactions.
     */
    private fun isSpecialInteraction(player: Player, itemType: Material): Boolean {
        return player.isSneaking && player.inventory.itemInMainHand.type == itemType
    }
}
