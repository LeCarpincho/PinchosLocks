package dev.pincho.locks.models

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Represents a serializable location for storage.
 * Optimized with cached key generation.
 */
@Serializable
data class SerializableLocation(
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int
) {
    // Cached key to avoid repeated string concatenation
    // Using @Volatile for thread-safe lazy initialization
    @kotlinx.serialization.Transient
    @Volatile
    private var cachedKey: String? = null

    companion object {
        fun fromBukkit(location: org.bukkit.Location): SerializableLocation {
            return SerializableLocation(
                world = location.world?.name ?: "world",
                x = location.blockX,
                y = location.blockY,
                z = location.blockZ
            )
        }
    }

    fun toBukkit(): org.bukkit.Location? {
        val world = org.bukkit.Bukkit.getWorld(world) ?: return null
        return org.bukkit.Location(world, x.toDouble(), y.toDouble(), z.toDouble())
    }

    /**
     * Returns a cached location key for efficient lookups.
     * Format: "worldname:x:y:z"
     */
    fun toKey(): String {
        return cachedKey ?: buildString(world.length + 30) {
            append(world)
            append(':')
            append(x)
            append(':')
            append(y)
            append(':')
            append(z)
        }.also { cachedKey = it }
    }

    override fun toString(): String = "$world ($x, $y, $z)"
}

/**
 * Represents a lock placed on a block.
 * This is the core data model for the lock system.
 *
 * Optimizations:
 * - Cached UUID parsing to avoid repeated conversion
 * - Cached tier lookup
 * - Efficient string-based UUID comparison for trusted checks
 */
@Serializable
data class Lock(
    val id: String,
    val ownerUUID: String,
    val ownerName: String,
    val tier: String,
    val location: SerializableLocation,
    val createdAt: Long,
    val trustedPlayers: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
) {
    // Cached parsed values to avoid repeated parsing
    @kotlinx.serialization.Transient
    private var cachedOwnerUUID: UUID? = null

    @kotlinx.serialization.Transient
    private var cachedTier: LockTier? = null

    @kotlinx.serialization.Transient
    private var cachedCreatedAt: Instant? = null

    /**
     * Gets the owner UUID as a Java UUID.
     * Cached for performance.
     */
    fun getOwnerUUID(): UUID {
        return cachedOwnerUUID ?: UUID.fromString(ownerUUID).also { cachedOwnerUUID = it }
    }

    /**
     * Gets the lock tier enum.
     * Cached for performance.
     */
    fun getTier(): LockTier {
        return cachedTier ?: (LockTier.fromString(tier) ?: LockTier.BRONZE).also { cachedTier = it }
    }

    /**
     * Gets the creation time as an Instant.
     * Cached for performance.
     */
    fun getCreatedAt(): Instant {
        return cachedCreatedAt ?: Instant.ofEpochMilli(createdAt).also { cachedCreatedAt = it }
    }

    /**
     * Checks if a player has access to this lock.
     * Optimized: compares strings directly without UUID parsing.
     * @param playerUUID The UUID of the player to check
     * @return true if the player is the owner or is trusted
     */
    fun hasAccess(playerUUID: UUID): Boolean {
        val playerUuidStr = playerUUID.toString()
        return playerUuidStr == ownerUUID || playerUuidStr in trustedPlayers
    }

    /**
     * Checks if a player has access using string UUID (avoids UUID parsing).
     * More efficient when UUID string is already available.
     */
    fun hasAccessByString(playerUuidStr: String): Boolean {
        return playerUuidStr == ownerUUID || playerUuidStr in trustedPlayers
    }

    /**
     * Checks if a player is the owner of this lock.
     */
    fun isOwner(playerUUID: UUID): Boolean = playerUUID.toString() == ownerUUID

    /**
     * Checks if a player is the owner using string UUID.
     */
    fun isOwnerByString(playerUuidStr: String): Boolean = playerUuidStr == ownerUUID

    /**
     * Adds a trusted player to the lock.
     * @return true if the player was added, false if already trusted
     */
    fun addTrusted(playerUUID: UUID): Boolean {
        return trustedPlayers.add(playerUUID.toString())
    }

    /**
     * Removes a trusted player from the lock.
     * @return true if the player was removed, false if not trusted
     */
    fun removeTrusted(playerUUID: UUID): Boolean {
        return trustedPlayers.remove(playerUUID.toString())
    }

    /**
     * Checks if a player is in the trusted list.
     * Note: Owner is automatically added to trusted on lock creation.
     */
    fun isTrusted(playerUUID: UUID): Boolean {
        return playerUUID.toString() in trustedPlayers
    }

    /**
     * Checks if a player is trusted using string UUID.
     */
    fun isTrustedByString(playerUuidStr: String): Boolean {
        return playerUuidStr in trustedPlayers
    }

    companion object {
        /**
         * Creates a new lock with generated ID.
         * IMPORTANT: Owner is automatically added to trustedPlayers as failsafe
         */
        fun create(
            owner: org.bukkit.entity.Player,
            tier: LockTier,
            location: org.bukkit.Location
        ): Lock {
            val lockId = UUID.randomUUID().toString().substring(0, 8)
            val ownerUuidString = owner.uniqueId.toString()

            return Lock(
                id = lockId,
                ownerUUID = ownerUuidString,
                ownerName = owner.name,
                tier = tier.name,
                location = SerializableLocation.fromBukkit(location),
                createdAt = System.currentTimeMillis(),
                // Owner is automatically added to trusted as failsafe
                // Using ConcurrentHashMap.newKeySet() for thread-safety
                trustedPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet<String>().apply { add(ownerUuidString) }
            )
        }
    }
}
