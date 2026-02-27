package dev.pincho.locks.listeners

import dev.pincho.locks.PinchosLocks
import dev.pincho.locks.config.ConfigManager
import dev.pincho.locks.data.LockStorage
import dev.pincho.locks.managers.LockManager
import dev.pincho.locks.models.LockableBlock
import dev.pincho.locks.utils.MessageUtils
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
import org.bukkit.event.block.BlockRedstoneEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.inventory.InventoryHolder

/**
 * Protects locked blocks from destruction and manipulation.
 * Handles explosions, pistons, hoppers, fire, mobs, etc.
 */
class LockProtectionListener(
    private val plugin: PinchosLocks,
    private val lockManager: LockManager,
    private val storage: LockStorage,
    private val config: ConfigManager,
    private val messages: MessageUtils
) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onBlockBreak(event: BlockBreakEvent) {
        val lock = lockManager.getLock(event.block) ?: return

        val player = event.player
        val playerUuidStr = player.uniqueId.toString()

        // Allow if owner, trusted, or has bypass - using optimized string-based checks
        if (lock.isOwnerByString(playerUuidStr) ||
            lock.isTrustedByString(playerUuidStr) ||
            player.hasPermission("pinchoslocks.bypass")) {

            storage.removeLock(lockManager.getCanonicalLocation(event.block))
            storage.saveAsync()
            event.isCancelled = false
            return
        }

        // Deny breaking
        event.isCancelled = true
        messages.send(player, "lock.cannot-break")
        player.playSound(player.location, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        event.blockList().removeIf { lockManager.getLock(it) != null }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        event.blockList().removeIf { lockManager.getLock(it) != null }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (event.blocks.any { lockManager.getLock(it) != null }) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (event.blocks.any { lockManager.getLock(it) != null }) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryMoveItem(event: InventoryMoveItemEvent) {
        // Check source
        getBlockFromHolder(event.source.holder)?.let {
            if (lockManager.getLock(it) != null) {
                event.isCancelled = true
                return
            }
        }

        // Check destination
        getBlockFromHolder(event.destination.holder)?.let {
            if (lockManager.getLock(it) != null) {
                event.isCancelled = true
            }
        }
    }

    private fun getBlockFromHolder(holder: InventoryHolder?): Block? {
        return when (holder) {
            is org.bukkit.block.Container -> holder.block
            is org.bukkit.block.DoubleChest -> holder.location.block
            else -> null
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBurn(event: BlockBurnEvent) {
        if (lockManager.getLock(event.block) != null) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        if (lockManager.getLock(event.block) != null) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockRedstone(event: BlockRedstoneEvent) {
        val block = event.block

        if (!LockableBlock.isDoor(block.type) && !block.type.name.contains("TRAPDOOR")) {
            return
        }

        if (lockManager.getLock(block) != null) {
            event.newCurrent = event.oldCurrent
        }
    }
}
