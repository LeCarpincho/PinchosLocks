package dev.pincho.locks.managers

import dev.pincho.locks.PinchosLocks
import dev.pincho.locks.config.ConfigManager
import dev.pincho.locks.data.LockStorage
import dev.pincho.locks.models.Lock
import dev.pincho.locks.models.LockTier
import dev.pincho.locks.models.LockableBlock
import dev.pincho.locks.utils.ItemBuilder
import dev.pincho.locks.utils.MessageUtils
import dev.pincho.locks.utils.ParticleEffects
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.type.Chest
import org.bukkit.block.data.type.Door
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Central manager for all lock-related operations.
 */
class LockManager(
    private val plugin: PinchosLocks,
    private val storage: LockStorage,
    private val config: ConfigManager,
    private val messages: MessageUtils
) {
    private val placementCooldowns = ConcurrentHashMap<UUID, Long>()
    private val temporaryAccess = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>()

    companion object {
        const val LOCK_TIER_KEY = "lock_tier"
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOCK OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    fun placeLock(player: Player, block: Block, tier: LockTier): LockResult {
        if (!LockableBlock.isLockable(block)) {
            return LockResult.Failure("lock.cannot-lock")
        }

        if (!config.isTierEnabled(tier) || !player.hasPermission(tier.permission)) {
            return LockResult.Failure("lock.tier-locked", mapOf("tier" to tier.displayName))
        }

        val targetLocation = getCanonicalLocation(block)
        if (storage.hasLock(targetLocation)) {
            return LockResult.Failure("lock.already-locked")
        }

        if (!checkCooldown(player)) {
            val remaining = getRemainingCooldown(player)
            return LockResult.Failure("general.cooldown", mapOf("time" to String.format("%.1f", remaining)))
        }

        val lock = Lock.create(player, tier, targetLocation)
        if (!storage.addLock(lock)) {
            return LockResult.Failure("lock.already-locked")
        }

        placementCooldowns[player.uniqueId] = System.currentTimeMillis()
        playLockPlaceEffects(player, targetLocation)
        storage.saveAsync()

        return LockResult.Success(lock, "lock.placed", mapOf("tier" to tier.displayName))
    }

    fun removeLock(player: Player, block: Block): LockResult {
        val targetLocation = getCanonicalLocation(block)
        val lock = storage.getLock(targetLocation) ?: return LockResult.Failure("lock.not-locked")

        if (!lock.isOwner(player.uniqueId) && !player.hasPermission("pinchoslocks.bypass")) {
            return LockResult.Failure("lock.not-owner")
        }

        storage.removeLock(targetLocation)
        playLockRemoveEffects(player, targetLocation)
        storage.saveAsync()

        return LockResult.Success(lock, "lock.removed")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOCK QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    fun getLock(block: Block): Lock? = storage.getLock(getCanonicalLocation(block))

    fun getLock(location: Location): Lock? = storage.getLock(getCanonicalLocation(location.block))

    fun getPlayerLocks(playerUUID: UUID): List<Lock> = storage.getLocksByOwner(playerUUID)

    fun getTotalLockCount(): Int = storage.getLockCount()

    // ═══════════════════════════════════════════════════════════════════════════
    // TRUST MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════

    fun addTrusted(lock: Lock, playerUUID: UUID): Boolean {
        if (lock.addTrusted(playerUUID)) {
            storage.updateLock(lock)
            storage.saveAsync()
            return true
        }
        return false
    }

    fun removeTrusted(lock: Lock, playerUUID: UUID): Boolean {
        if (lock.removeTrusted(playerUUID)) {
            storage.updateLock(lock)
            storage.saveAsync()
            return true
        }
        return false
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEMPORARY ACCESS (LOCKPICKING)
    // ═══════════════════════════════════════════════════════════════════════════

    fun addTemporaryAccess(playerUUID: UUID, lockId: String, durationSeconds: Int) {
        temporaryAccess.getOrPut(playerUUID) { ConcurrentHashMap() }[lockId] =
            System.currentTimeMillis() + (durationSeconds * 1000L)
    }

    fun hasTemporaryAccess(playerUUID: UUID, lockId: String): Boolean {
        val playerAccess = temporaryAccess[playerUUID] ?: return false
        val expiryTime = playerAccess[lockId] ?: return false

        if (System.currentTimeMillis() > expiryTime) {
            playerAccess.remove(lockId)
            if (playerAccess.isEmpty()) temporaryAccess.remove(playerUUID)
            return false
        }
        return true
    }

    fun removeTemporaryAccess(playerUUID: UUID, lockId: String) {
        temporaryAccess[playerUUID]?.let {
            it.remove(lockId)
            if (it.isEmpty()) temporaryAccess.remove(playerUUID)
        }
    }

    fun clearTemporaryAccess(playerUUID: UUID) {
        temporaryAccess.remove(playerUUID)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ITEM MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════

    fun createLockItem(tier: LockTier, amount: Int = 1): ItemStack {
        val tierConfig = config.getTierConfig(tier)
        val tierKey = tier.name.lowercase()

        // Get translated name for this tier
        val translatedName = messages.getRaw("items.lock.name.$tierKey")

        // Get translated tier display name
        val translatedTierName = messages.getRaw("items.tiers.lock.$tierKey")

        // Get and process lore with placeholders
        val lore = messages.getList("items.lock.lore").map { line ->
            line.replace("{tier}", translatedTierName)
        }

        return ItemBuilder.of(tierConfig.material)
            .amount(amount)
            .name(translatedName)
            .loreStrings(lore)
            .customModelData(tierConfig.customModelData)
            .persistentString(plugin, LOCK_TIER_KEY, tier.name)
            .glow(true)
            .build()
    }

    fun getLockTierFromItem(item: ItemStack?): LockTier? {
        if (item == null || item.type == Material.AIR) return null
        val tierName = item.itemMeta?.persistentDataContainer?.get(
            plugin.createKey(LOCK_TIER_KEY), PersistentDataType.STRING
        ) ?: return null
        return LockTier.fromString(tierName)
    }

    fun isLockItem(item: ItemStack?): Boolean = getLockTierFromItem(item) != null

    // ═══════════════════════════════════════════════════════════════════════════
    // EFFECTS
    // ═══════════════════════════════════════════════════════════════════════════

    fun playAccessDeniedEffects(player: Player, location: Location) {
        if (config.particlesEnabled) {
            ParticleEffects.playAccessDeniedEffect(plugin, location, player)
        } else {
            player.playSound(location, config.soundLockDenied, 1.0f, 0.5f)
        }
    }

    fun playAccessGrantedEffects(player: Player, location: Location) {
        if (config.particlesEnabled) {
            ParticleEffects.playAccessGrantedEffect(plugin, location, player)
        } else {
            player.playSound(location, config.soundLockSuccess, 1.0f, 1.0f)
        }
    }

    private fun playLockPlaceEffects(player: Player, location: Location) {
        if (config.particlesEnabled) {
            ParticleEffects.playLockPlaceEffect(plugin, location, player)
        } else {
            player.playSound(location, config.soundLockPlace, 1.0f, 1.0f)
        }
    }

    private fun playLockRemoveEffects(player: Player, location: Location) {
        if (config.particlesEnabled) {
            ParticleEffects.playLockRemoveEffect(plugin, location, player)
        } else {
            player.playSound(location, config.soundLockRemove, 1.0f, 1.0f)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOCATION HANDLING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets canonical location for double chests and doors.
     * For double chests, returns the block with smaller coordinates.
     * For doors, returns the bottom half.
     */
    fun getCanonicalLocation(block: Block): Location {
        val blockData = block.blockData

        // Handle doors - use bottom half
        if (blockData is Door && blockData.half == Bisected.Half.TOP) {
            return block.getRelative(BlockFace.DOWN).location
        }

        // Handle double chests - use smaller coordinates
        if (block.type == Material.CHEST || block.type == Material.TRAPPED_CHEST) {
            (blockData as? Chest)?.let { chestData ->
                if (chestData.type != Chest.Type.SINGLE) {
                    findConnectedChest(block, chestData)?.let { connected ->
                        val thisLoc = block.location
                        val connLoc = connected.location
                        return when {
                            thisLoc.blockX < connLoc.blockX -> thisLoc
                            thisLoc.blockX > connLoc.blockX -> connLoc
                            thisLoc.blockZ <= connLoc.blockZ -> thisLoc
                            else -> connLoc
                        }
                    }
                }
            }
        }

        return block.location
    }

    private fun findConnectedChest(block: Block, chestData: Chest): Block? {
        val facing = chestData.facing
        return when (chestData.type) {
            Chest.Type.LEFT -> when (facing) {
                BlockFace.NORTH -> block.getRelative(BlockFace.EAST)
                BlockFace.SOUTH -> block.getRelative(BlockFace.WEST)
                BlockFace.EAST -> block.getRelative(BlockFace.SOUTH)
                BlockFace.WEST -> block.getRelative(BlockFace.NORTH)
                else -> null
            }
            Chest.Type.RIGHT -> when (facing) {
                BlockFace.NORTH -> block.getRelative(BlockFace.WEST)
                BlockFace.SOUTH -> block.getRelative(BlockFace.EAST)
                BlockFace.EAST -> block.getRelative(BlockFace.NORTH)
                BlockFace.WEST -> block.getRelative(BlockFace.SOUTH)
                else -> null
            }
            else -> null
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COOLDOWNS
    // ═══════════════════════════════════════════════════════════════════════════

    private fun checkCooldown(player: Player): Boolean {
        if (player.hasPermission("pinchoslocks.bypass.cooldown")) return true
        val lastUse = placementCooldowns[player.uniqueId] ?: return true
        return (System.currentTimeMillis() - lastUse) / 1000.0 >= config.lockPlacementCooldown
    }

    private fun getRemainingCooldown(player: Player): Double {
        val lastUse = placementCooldowns[player.uniqueId] ?: return 0.0
        val elapsed = (System.currentTimeMillis() - lastUse) / 1000.0
        return (config.lockPlacementCooldown - elapsed).coerceAtLeast(0.0)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RESULT CLASSES
    // ═══════════════════════════════════════════════════════════════════════════

    sealed class LockResult {
        data class Success(
            val lock: Lock,
            val messageKey: String,
            val placeholders: Map<String, Any> = emptyMap()
        ) : LockResult()

        data class Failure(
            val messageKey: String,
            val placeholders: Map<String, Any> = emptyMap()
        ) : LockResult()
    }
}
