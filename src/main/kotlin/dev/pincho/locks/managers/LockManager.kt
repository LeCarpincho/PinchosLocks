package dev.pincho.locks.managers

import dev.pincho.locks.PinchosLocks
import dev.pincho.locks.config.ConfigManager
import dev.pincho.locks.data.LockStorage
import dev.pincho.locks.models.Lock
import dev.pincho.locks.models.LockTier
import dev.pincho.locks.models.LockableBlock
import dev.pincho.locks.utils.ItemBuilder
import dev.pincho.locks.utils.MessageUtils
import kotlinx.coroutines.CoroutineScope
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.block.Block
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.type.Door
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages lock creation, validation, and operations.
 * This is the central service for all lock-related functionality.
 */
class LockManager(
    private val plugin: PinchosLocks,
    private val storage: LockStorage,
    private val config: ConfigManager,
    private val messages: MessageUtils,
    private val scope: CoroutineScope
) {

    // Cooldown tracking: Player UUID -> Last action timestamp
    private val placementCooldowns = ConcurrentHashMap<UUID, Long>()

    // Temporary access from lockpicking: Player UUID -> (Lock ID -> Expiry time)
    private val temporaryAccess = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>()

    companion object {
        const val LOCK_TIER_KEY = "lock_tier"
        const val LOCK_ID_KEY = "lock_id"
    }

    /**
     * Attempts to place a lock on a block.
     * @return Result indicating success or failure with message
     */
    fun placeLock(player: Player, block: Block, tier: LockTier): LockResult {
        // Check if block type is lockable
        if (!LockableBlock.isLockable(block)) {
            return LockResult.Failure("lock.cannot-lock")
        }

        // Check if tier is enabled
        if (!config.isTierEnabled(tier)) {
            return LockResult.Failure("lock.tier-locked", mapOf("tier" to tier.displayName))
        }

        // Check permission for tier
        if (!player.hasPermission(tier.permission)) {
            return LockResult.Failure("lock.tier-locked", mapOf("tier" to tier.displayName))
        }

        // Check if already locked
        val targetLocation = getCanonicalLocation(block)
        if (storage.hasLock(targetLocation)) {
            return LockResult.Failure("lock.already-locked")
        }

        // Check cooldown
        if (!checkCooldown(player, placementCooldowns, config.lockPlacementCooldown)) {
            val remaining = getRemainingCooldown(player, placementCooldowns, config.lockPlacementCooldown)
            return LockResult.Failure("general.cooldown", mapOf("time" to String.format("%.1f", remaining)))
        }

        // Create and store the lock
        val lock = Lock.create(player, tier, targetLocation)

        if (!storage.addLock(lock)) {
            return LockResult.Failure("lock.already-locked")
        }

        // Update cooldown
        placementCooldowns[player.uniqueId] = System.currentTimeMillis()

        // Play effects
        playLockPlaceEffects(player, targetLocation)

        // Schedule async save
        storage.saveAsync()

        return LockResult.Success(lock, "lock.placed", mapOf("tier" to tier.displayName))
    }

    /**
     * Removes a lock from a block.
     */
    fun removeLock(player: Player, block: Block): LockResult {
        val targetLocation = getCanonicalLocation(block)
        val lock = storage.getLock(targetLocation)
            ?: return LockResult.Failure("lock.not-locked")

        // Check ownership or bypass permission
        if (!lock.isOwner(player.uniqueId) && !player.hasPermission("pinchoslocks.bypass")) {
            return LockResult.Failure("lock.not-owner")
        }

        storage.removeLock(targetLocation)

        // Play effects
        playLockRemoveEffects(player, targetLocation)

        // Schedule async save
        storage.saveAsync()

        return LockResult.Success(lock, "lock.removed")
    }

    /**
     * Checks if a player can access a locked block.
     */
    fun canAccess(player: Player, block: Block): AccessResult {
        val targetLocation = getCanonicalLocation(block)
        plugin.logger.info("[Debug] canAccess check for ${player.name} at ${targetLocation.world?.name}:${targetLocation.blockX}:${targetLocation.blockY}:${targetLocation.blockZ}")

        val lock = storage.getLock(targetLocation)
        if (lock == null) {
            plugin.logger.info("[Debug] No lock found at location")
            return AccessResult.Unlocked
        }

        plugin.logger.info("[Debug] Lock found with ID: ${lock.id}, owner: ${lock.ownerName}")

        // Check bypass permission
        if (player.hasPermission("pinchoslocks.bypass")) {
            plugin.logger.info("[Debug] Player has bypass permission")
            return AccessResult.Bypass(lock)
        }

        // Check ownership
        if (lock.isOwner(player.uniqueId)) {
            plugin.logger.info("[Debug] Player is owner")
            return AccessResult.Owner(lock)
        }

        // Check trusted
        if (lock.isTrusted(player.uniqueId)) {
            plugin.logger.info("[Debug] Player is trusted")
            return AccessResult.Trusted(lock)
        }

        // Check temporary access from lockpicking (15 second window)
        plugin.logger.info("[Debug] Checking temporary access for player ${player.uniqueId} and lock ${lock.id}")
        val hasTempAccess = hasTemporaryAccess(player.uniqueId, lock.id)
        plugin.logger.info("[Debug] hasTemporaryAccess result: $hasTempAccess")

        if (hasTempAccess) {
            plugin.logger.info("[Debug] Player has temporary access!")
            return AccessResult.TemporaryAccess(lock)
        }

        // Check if holding valid key
        val keyResult = checkPlayerHasKey(player, lock)
        if (keyResult) {
            plugin.logger.info("[Debug] Player has valid key")
            return AccessResult.HasKey(lock)
        }

        plugin.logger.info("[Debug] Access denied for player ${player.name}")
        return AccessResult.Denied(lock)
    }

    /**
     * Checks if a player is holding a valid key for a lock.
     */
    private fun checkPlayerHasKey(player: Player, lock: Lock): Boolean {
        val mainHand = player.inventory.itemInMainHand
        val offHand = player.inventory.itemInOffHand

        return isValidKeyItem(mainHand, lock) || isValidKeyItem(offHand, lock)
    }

    /**
     * Checks if an item is a valid key for a lock.
     */
    private fun isValidKeyItem(item: ItemStack?, lock: Lock): Boolean {
        if (item == null || item.type == Material.AIR) return false

        val meta = item.itemMeta ?: return false
        val container = meta.persistentDataContainer

        val keyId = container.get(
            plugin.createKey(KeyManager.KEY_ID_KEY),
            PersistentDataType.STRING
        ) ?: return false

        return lock.isValidKey(keyId)
    }

    /**
     * Gets the canonical location for a block (handles double doors, double chests).
     */
    fun getCanonicalLocation(block: Block): Location {
        // Handle doors - always use the bottom half
        val blockData = block.blockData
        if (blockData is Door) {
            if (blockData.half == Bisected.Half.TOP) {
                return block.getRelative(org.bukkit.block.BlockFace.DOWN).location
            }
        }

        // Handle double chests - use the northern/western block
        if (block.type == Material.CHEST || block.type == Material.TRAPPED_CHEST) {
            val chestData = block.blockData as? org.bukkit.block.data.type.Chest
            if (chestData != null && chestData.type != org.bukkit.block.data.type.Chest.Type.SINGLE) {
                val facing = chestData.facing
                val connectedBlock = when {
                    chestData.type == org.bukkit.block.data.type.Chest.Type.LEFT -> {
                        when (facing) {
                            org.bukkit.block.BlockFace.NORTH -> block.getRelative(org.bukkit.block.BlockFace.EAST)
                            org.bukkit.block.BlockFace.SOUTH -> block.getRelative(org.bukkit.block.BlockFace.WEST)
                            org.bukkit.block.BlockFace.EAST -> block.getRelative(org.bukkit.block.BlockFace.SOUTH)
                            org.bukkit.block.BlockFace.WEST -> block.getRelative(org.bukkit.block.BlockFace.NORTH)
                            else -> null
                        }
                    }
                    else -> null
                }

                // Return the block with smaller coordinates (canonical position)
                if (connectedBlock != null) {
                    if (block.x < connectedBlock.x || block.z < connectedBlock.z) {
                        return block.location
                    }
                    return connectedBlock.location
                }
            }
        }

        return block.location
    }

    /**
     * Gets the lock at a block location.
     */
    fun getLock(block: Block): Lock? {
        val targetLocation = getCanonicalLocation(block)
        return storage.getLock(targetLocation)
    }

    /**
     * Gets the lock at a location.
     * Normalizes the location to handle double doors, double chests, etc.
     */
    fun getLock(location: Location): Lock? {
        // Get the block at this location and use canonical location
        val block = location.block
        val canonicalLocation = getCanonicalLocation(block)
        return storage.getLock(canonicalLocation)
    }

    /**
     * Adds a trusted player to a lock.
     */
    fun addTrusted(lock: Lock, playerUUID: UUID): Boolean {
        if (lock.addTrusted(playerUUID)) {
            storage.updateLock(lock)
            storage.saveAsync()
            return true
        }
        return false
    }

    /**
     * Removes a trusted player from a lock.
     */
    fun removeTrusted(lock: Lock, playerUUID: UUID): Boolean {
        if (lock.removeTrusted(playerUUID)) {
            storage.updateLock(lock)
            storage.saveAsync()
            return true
        }
        return false
    }

    /**
     * Creates a lock item for a specific tier.
     */
    fun createLockItem(tier: LockTier, amount: Int = 1): ItemStack {
        val tierConfig = config.getTierConfig(tier)

        return ItemBuilder.of(tierConfig.material)
            .amount(amount)
            .name(messages.parse(tierConfig.displayName))
            .loreStrings(
                messages.getList("items.lock.lore").map {
                    it.replace("{tier}", tier.displayName)
                }
            )
            .customModelData(tierConfig.customModelData)
            .persistentString(plugin, LOCK_TIER_KEY, tier.name)
            .glow(true)
            .build()
    }

    /**
     * Gets the lock tier from an item, if it's a lock item.
     */
    fun getLockTierFromItem(item: ItemStack?): LockTier? {
        if (item == null || item.type == Material.AIR) return null

        val meta = item.itemMeta ?: return null
        val tierName = meta.persistentDataContainer.get(
            plugin.createKey(LOCK_TIER_KEY),
            PersistentDataType.STRING
        ) ?: return null

        return LockTier.fromString(tierName)
    }

    /**
     * Checks if an item is a lock item.
     */
    fun isLockItem(item: ItemStack?): Boolean = getLockTierFromItem(item) != null

    /**
     * Checks and updates cooldown.
     */
    private fun checkCooldown(
        player: Player,
        cooldowns: ConcurrentHashMap<UUID, Long>,
        cooldownSeconds: Double
    ): Boolean {
        if (player.hasPermission("pinchoslocks.bypass.cooldown")) return true

        val lastUse = cooldowns[player.uniqueId] ?: return true
        val elapsed = (System.currentTimeMillis() - lastUse) / 1000.0
        return elapsed >= cooldownSeconds
    }

    /**
     * Gets remaining cooldown time in seconds.
     */
    private fun getRemainingCooldown(
        player: Player,
        cooldowns: ConcurrentHashMap<UUID, Long>,
        cooldownSeconds: Double
    ): Double {
        val lastUse = cooldowns[player.uniqueId] ?: return 0.0
        val elapsed = (System.currentTimeMillis() - lastUse) / 1000.0
        return (cooldownSeconds - elapsed).coerceAtLeast(0.0)
    }

    /**
     * Plays lock placement effects.
     */
    private fun playLockPlaceEffects(player: Player, location: Location) {
        player.playSound(location, config.soundLockPlace, 1.0f, 1.0f)

        if (config.particlesEnabled) {
            location.world?.spawnParticle(
                Particle.HAPPY_VILLAGER,
                location.clone().add(0.5, 0.5, 0.5),
                10,
                0.3, 0.3, 0.3,
                0.0
            )
        }
    }

    /**
     * Plays lock removal effects.
     */
    private fun playLockRemoveEffects(player: Player, location: Location) {
        player.playSound(location, config.soundLockRemove, 1.0f, 1.0f)
    }

    /**
     * Plays access denied effects.
     */
    fun playAccessDeniedEffects(player: Player, location: Location) {
        player.playSound(location, config.soundLockDenied, 1.0f, 0.5f)

        if (config.particlesEnabled) {
            location.world?.spawnParticle(
                Particle.ANGRY_VILLAGER,
                location.clone().add(0.5, 0.5, 0.5),
                5,
                0.2, 0.2, 0.2,
                0.0
            )
        }
    }

    /**
     * Plays access granted effects.
     */
    fun playAccessGrantedEffects(player: Player, location: Location) {
        player.playSound(location, config.soundLockSuccess, 1.0f, 1.0f)
    }

    /**
     * Gets all locks owned by a player.
     */
    fun getPlayerLocks(playerUUID: UUID): List<Lock> {
        return storage.getLocksByOwner(playerUUID)
    }

    /**
     * Gets total lock count.
     */
    fun getTotalLockCount(): Int = storage.getLockCount()

    /**
     * Adds temporary access for a player to a lock.
     * @param playerUUID The player's UUID
     * @param lockId The lock's ID
     * @param durationSeconds How long the access lasts
     */
    fun addTemporaryAccess(playerUUID: UUID, lockId: String, durationSeconds: Int) {
        plugin.logger.info("[Debug] addTemporaryAccess - Player: $playerUUID, Lock: $lockId, Duration: $durationSeconds seconds")
        val playerAccess = temporaryAccess.getOrPut(playerUUID) { ConcurrentHashMap() }
        val expiryTime = System.currentTimeMillis() + (durationSeconds * 1000L)
        playerAccess[lockId] = expiryTime
        plugin.logger.info("[Debug] Temporary access added! Expiry time: $expiryTime")
        plugin.logger.info("[Debug] temporaryAccess map now has ${temporaryAccess.size} players")
    }

    /**
     * Checks if a player has temporary access to a lock.
     */
    fun hasTemporaryAccess(playerUUID: UUID, lockId: String): Boolean {
        plugin.logger.info("[Debug] hasTemporaryAccess check - Player: $playerUUID, Lock: $lockId")
        plugin.logger.info("[Debug] temporaryAccess map keys: ${temporaryAccess.keys}")

        val playerAccess = temporaryAccess[playerUUID]
        if (playerAccess == null) {
            plugin.logger.info("[Debug] No temporary access entry for player $playerUUID")
            return false
        }

        plugin.logger.info("[Debug] Player's access map keys: ${playerAccess.keys}")
        val expiryTime = playerAccess[lockId]
        if (expiryTime == null) {
            plugin.logger.info("[Debug] No expiry time for lock $lockId")
            return false
        }

        val currentTime = System.currentTimeMillis()
        plugin.logger.info("[Debug] Current time: $currentTime, Expiry time: $expiryTime, Diff: ${expiryTime - currentTime}ms")

        if (currentTime > expiryTime) {
            // Access expired, remove it
            plugin.logger.info("[Debug] Access expired, removing")
            playerAccess.remove(lockId)
            if (playerAccess.isEmpty()) {
                temporaryAccess.remove(playerUUID)
            }
            return false
        }

        plugin.logger.info("[Debug] Temporary access VALID!")
        return true
    }

    /**
     * Clears all temporary access for a player.
     */
    fun clearTemporaryAccess(playerUUID: UUID) {
        temporaryAccess.remove(playerUUID)
    }

    /**
     * Removes temporary access for a player to a specific lock.
     * Called when the player opens the chest/door to invalidate their access.
     */
    fun removeTemporaryAccess(playerUUID: UUID, lockId: String) {
        val playerAccess = temporaryAccess[playerUUID] ?: return
        playerAccess.remove(lockId)
        if (playerAccess.isEmpty()) {
            temporaryAccess.remove(playerUUID)
        }
    }

    /**
     * Sealed class for lock operation results.
     */
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

    /**
     * Sealed class for access check results.
     */
    sealed class AccessResult {
        object Unlocked : AccessResult()
        data class Owner(val lock: Lock) : AccessResult()
        data class Trusted(val lock: Lock) : AccessResult()
        data class HasKey(val lock: Lock) : AccessResult()
        data class Bypass(val lock: Lock) : AccessResult()
        data class TemporaryAccess(val lock: Lock) : AccessResult()
        data class Denied(val lock: Lock) : AccessResult()
    }
}
