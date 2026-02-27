package dev.pincho.locks.models

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Represents a serializable location for storage.
 */
@Serializable
data class SerializableLocation(
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int
) {
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

    fun toKey(): String = "$world:$x:$y:$z"

    override fun toString(): String = "$world ($x, $y, $z)"
}

/**
 * Represents a lock placed on a block.
 * This is the core data model for the lock system.
 */
@Serializable
data class Lock(
    val id: String,
    val ownerUUID: String,
    val ownerName: String,
    val tier: String,
    val location: SerializableLocation,
    val createdAt: Long,
    val trustedPlayers: MutableSet<String> = mutableSetOf(),
    val keyIds: MutableSet<String> = mutableSetOf()
) {
    /**
     * Gets the owner UUID as a Java UUID.
     */
    fun getOwnerUUID(): UUID = UUID.fromString(ownerUUID)

    /**
     * Gets the lock tier enum.
     */
    fun getTier(): LockTier = LockTier.fromString(tier) ?: LockTier.BRONZE

    /**
     * Gets the creation time as an Instant.
     */
    fun getCreatedAt(): Instant = Instant.ofEpochMilli(createdAt)

    /**
     * Checks if a player has access to this lock.
     * @param playerUUID The UUID of the player to check
     * @return true if the player is the owner or is trusted
     */
    fun hasAccess(playerUUID: UUID): Boolean {
        return playerUUID.toString() == ownerUUID || playerUUID.toString() in trustedPlayers
    }

    /**
     * Checks if a player is the owner of this lock.
     */
    fun isOwner(playerUUID: UUID): Boolean = playerUUID.toString() == ownerUUID

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
     * Checks if a player is trusted (but not owner).
     */
    fun isTrusted(playerUUID: UUID): Boolean {
        return playerUUID.toString() in trustedPlayers
    }

    /**
     * Adds a key ID to this lock.
     */
    fun addKeyId(keyId: String): Boolean {
        return keyIds.add(keyId)
    }

    /**
     * Checks if a key ID is valid for this lock.
     */
    fun isValidKey(keyId: String): Boolean {
        return keyId in keyIds
    }

    /**
     * Gets the number of keys issued for this lock.
     */
    fun getKeyCount(): Int = keyIds.size

    companion object {
        /**
         * Creates a new lock with generated IDs.
         */
        fun create(
            owner: org.bukkit.entity.Player,
            tier: LockTier,
            location: org.bukkit.Location
        ): Lock {
            val lockId = UUID.randomUUID().toString().substring(0, 8)
            val keyId = UUID.randomUUID().toString().substring(0, 8)

            return Lock(
                id = lockId,
                ownerUUID = owner.uniqueId.toString(),
                ownerName = owner.name,
                tier = tier.name,
                location = SerializableLocation.fromBukkit(location),
                createdAt = System.currentTimeMillis(),
                trustedPlayers = mutableSetOf(),
                keyIds = mutableSetOf(keyId)
            )
        }
    }
}
