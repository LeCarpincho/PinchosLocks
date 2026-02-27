package dev.pincho.locks.listeners

import dev.pincho.locks.PinchosLocks
import dev.pincho.locks.config.ConfigManager
import dev.pincho.locks.data.LockStorage
import dev.pincho.locks.managers.LockManager
import dev.pincho.locks.models.LockableBlock
import dev.pincho.locks.utils.MessageUtils
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.inventory.InventoryHolder

/**
 * Protects locked blocks from various forms of destruction and manipulation.
 * Handles explosions, pistons, hoppers, fire, endermen, etc.
 */
class LockProtectionListener(
    private val plugin: PinchosLocks,
    private val lockManager: LockManager,
    private val storage: LockStorage,
    private val config: ConfigManager,
    private val messages: MessageUtils
) : Listener {

    /**
     * Prevents breaking of locked blocks.
     * Only owner or players with bypass permission can break locked blocks.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        val player = event.player

        // Debug logging
        plugin.logger.info("[LockProtection] BlockBreak event for ${player.name} at ${block.location}")

        // Check if block is locked
        val lock = lockManager.getLock(block)
        if (lock == null) {
            plugin.logger.info("[LockProtection] No lock found at this location")
            return
        }

        plugin.logger.info("[LockProtection] Lock found! ID: ${lock.id}, Owner: ${lock.ownerName}")
        plugin.logger.info("[LockProtection] Player UUID: ${player.uniqueId}, Lock Owner UUID: ${lock.ownerUUID}")
        plugin.logger.info("[LockProtection] Is Owner: ${lock.isOwner(player.uniqueId)}, Has Bypass: ${player.hasPermission("pinchoslocks.bypass")}")

        // Check if player is owner or has bypass
        if (lock.isOwner(player.uniqueId) || player.hasPermission("pinchoslocks.bypass")) {
            plugin.logger.info("[LockProtection] Player IS authorized to break - removing lock")
            // Allow breaking, but remove the lock
            storage.removeLock(lockManager.getCanonicalLocation(block))
            storage.saveAsync()
            return
        }

        // Prevent breaking and notify player
        plugin.logger.info("[LockProtection] Player NOT authorized - BLOCKING break and sending message")
        event.isCancelled = true

        // Send message directly to ensure it works
        player.sendMessage("§c§l🔒 §cNo puedes romper este bloque! Esta protegido con un candado.")
        player.playSound(player.location, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f)
    }

    /**
     * Prevents explosions from destroying locked blocks.
     * ALWAYS active - locked blocks are INDESTRUCTIBLE by explosions.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        // ALWAYS protect locked blocks from explosions (TNT, creepers, etc.)
        event.blockList().removeIf { block ->
            lockManager.getLock(block) != null
        }
    }

    /**
     * Prevents block explosions from destroying locked blocks.
     * ALWAYS active - locked blocks are INDESTRUCTIBLE by explosions.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        // ALWAYS protect locked blocks from block explosions (beds in nether, respawn anchors, etc.)
        event.blockList().removeIf { block ->
            lockManager.getLock(block) != null
        }
    }

    /**
     * Prevents pistons from moving locked blocks.
     * ALWAYS active - locked blocks cannot be moved by pistons.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        val affectedBlocks = event.blocks
        if (affectedBlocks.any { lockManager.getLock(it) != null }) {
            event.isCancelled = true
        }
    }

    /**
     * Prevents pistons from retracting locked blocks.
     * ALWAYS active - locked blocks cannot be moved by pistons.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        val affectedBlocks = event.blocks
        if (affectedBlocks.any { lockManager.getLock(it) != null }) {
            event.isCancelled = true
        }
    }

    /**
     * Prevents hoppers from extracting items from locked containers.
     * ALWAYS active - locked containers cannot be accessed by hoppers.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryMoveItem(event: InventoryMoveItemEvent) {
        // Check source inventory
        val sourceHolder = event.source.holder
        if (sourceHolder is InventoryHolder) {
            val sourceBlock = getBlockFromHolder(sourceHolder)
            if (sourceBlock != null && lockManager.getLock(sourceBlock) != null) {
                event.isCancelled = true
                return
            }
        }

        // Check destination inventory
        val destHolder = event.destination.holder
        if (destHolder is InventoryHolder) {
            val destBlock = getBlockFromHolder(destHolder)
            if (destBlock != null && lockManager.getLock(destBlock) != null) {
                event.isCancelled = true
            }
        }
    }

    /**
     * Gets the block associated with an inventory holder.
     */
    private fun getBlockFromHolder(holder: InventoryHolder): Block? {
        return when (holder) {
            is org.bukkit.block.Container -> holder.block
            is org.bukkit.block.DoubleChest -> holder.location.block
            else -> null
        }
    }

    /**
     * Prevents fire from spreading to locked blocks.
     * ALWAYS active - locked blocks cannot burn.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBurn(event: BlockBurnEvent) {
        if (lockManager.getLock(event.block) != null) {
            event.isCancelled = true
        }
    }

    /**
     * Prevents entities from changing/destroying locked blocks.
     * Covers: Enderman, Wither, Zombies breaking doors, Silverfish, etc.
     * ALWAYS active - locked blocks are INDESTRUCTIBLE by mobs.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        // Protect locked blocks from ALL entities
        if (lockManager.getLock(event.block) != null) {
            event.isCancelled = true
        }
    }

    /**
     * Prevents locked doors from being opened by redstone.
     * This is optional and can be enabled via config if desired.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockRedstone(event: org.bukkit.event.block.BlockRedstoneEvent) {
        val block = event.block

        // Only check doors and trapdoors
        if (!LockableBlock.isDoor(block.type) &&
            !block.type.name.contains("TRAPDOOR")) {
            return
        }

        // If locked, prevent redstone from affecting it
        if (lockManager.getLock(block) != null) {
            event.newCurrent = event.oldCurrent
        }
    }
}
