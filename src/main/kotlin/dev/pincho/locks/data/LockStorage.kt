package dev.pincho.locks.data

import dev.pincho.locks.models.Lock
import dev.pincho.locks.models.SerializableLocation
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles persistence of lock data to JSON files.
 * Uses coroutines for async I/O operations to prevent main thread blocking.
 */
class LockStorage(
    private val plugin: JavaPlugin,
    private val scope: CoroutineScope
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // In-memory cache of all locks, keyed by location string
    private val locksByLocation = ConcurrentHashMap<String, Lock>()

    // Index of locks by owner UUID for quick lookups
    private val locksByOwner = ConcurrentHashMap<UUID, MutableSet<String>>()

    // Index of locks by lock ID
    private val locksById = ConcurrentHashMap<String, Lock>()

    private val dataFile: File
        get() = File(plugin.dataFolder, "locks.json")

    /**
     * Loads all locks from the JSON file.
     * Should be called during plugin enable.
     */
    suspend fun load(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (!dataFile.exists()) {
                plugin.logger.info("No existing lock data found, starting fresh.")
                return@runCatching 0
            }

            val content = dataFile.readText()
            if (content.isBlank()) {
                return@runCatching 0
            }

            val locks: List<Lock> = json.decodeFromString(content)

            locksByLocation.clear()
            locksByOwner.clear()
            locksById.clear()

            locks.forEach { lock ->
                val locationKey = lock.location.toKey()
                locksByLocation[locationKey] = lock
                locksById[lock.id] = lock

                val ownerUUID = UUID.fromString(lock.ownerUUID)
                locksByOwner.getOrPut(ownerUUID) { ConcurrentHashMap.newKeySet() }.add(locationKey)
            }

            plugin.logger.info("Loaded ${locks.size} locks from storage.")
            locks.size
        }
    }

    /**
     * Saves all locks to the JSON file.
     * Uses coroutines to prevent blocking the main thread.
     */
    suspend fun save(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!plugin.dataFolder.exists()) {
                plugin.dataFolder.mkdirs()
            }

            val locks = locksByLocation.values.toList()
            val content = json.encodeToString(locks)

            // Write to temp file first, then rename for atomicity
            val tempFile = File(plugin.dataFolder, "locks.json.tmp")
            tempFile.writeText(content)
            tempFile.renameTo(dataFile)

            plugin.logger.info("Saved ${locks.size} locks to storage.")
        }
    }

    /**
     * Saves locks asynchronously (fire and forget with error logging).
     */
    fun saveAsync() {
        scope.launch {
            save().onFailure { e ->
                plugin.logger.severe("Failed to save lock data: ${e.message}")
            }
        }
    }

    /**
     * Creates a location key from a Bukkit Location.
     */
    private fun locationToKey(location: Location): String {
        return "${location.world?.name ?: "world"}:${location.blockX}:${location.blockY}:${location.blockZ}"
    }

    /**
     * Gets a lock at the specified location.
     */
    fun getLock(location: Location): Lock? {
        return locksByLocation[locationToKey(location)]
    }

    /**
     * Gets a lock at the specified serializable location.
     */
    fun getLock(location: SerializableLocation): Lock? {
        return locksByLocation[location.toKey()]
    }

    /**
     * Gets a lock by its ID.
     */
    fun getLockById(id: String): Lock? {
        return locksById[id]
    }

    /**
     * Checks if a location has a lock.
     */
    fun hasLock(location: Location): Boolean {
        return locksByLocation.containsKey(locationToKey(location))
    }

    /**
     * Adds a new lock.
     * @return true if the lock was added, false if a lock already exists
     */
    fun addLock(lock: Lock): Boolean {
        val locationKey = lock.location.toKey()

        if (locksByLocation.containsKey(locationKey)) {
            return false
        }

        locksByLocation[locationKey] = lock
        locksById[lock.id] = lock

        val ownerUUID = UUID.fromString(lock.ownerUUID)
        locksByOwner.getOrPut(ownerUUID) { ConcurrentHashMap.newKeySet() }.add(locationKey)

        return true
    }

    /**
     * Removes a lock at the specified location.
     * @return the removed lock, or null if no lock existed
     */
    fun removeLock(location: Location): Lock? {
        val locationKey = locationToKey(location)
        val lock = locksByLocation.remove(locationKey) ?: return null

        locksById.remove(lock.id)

        val ownerUUID = UUID.fromString(lock.ownerUUID)
        locksByOwner[ownerUUID]?.remove(locationKey)

        return lock
    }

    /**
     * Updates an existing lock.
     */
    fun updateLock(lock: Lock) {
        val locationKey = lock.location.toKey()
        locksByLocation[locationKey] = lock
        locksById[lock.id] = lock
    }

    /**
     * Gets all locks owned by a specific player.
     */
    fun getLocksByOwner(ownerUUID: UUID): List<Lock> {
        val locationKeys = locksByOwner[ownerUUID] ?: return emptyList()
        return locationKeys.mapNotNull { locksByLocation[it] }
    }

    /**
     * Gets the total number of locks.
     */
    fun getLockCount(): Int = locksByLocation.size

    /**
     * Gets all locks.
     */
    fun getAllLocks(): Collection<Lock> = locksByLocation.values.toList()

    /**
     * Clears all locks (for testing/admin purposes).
     */
    fun clearAll() {
        locksByLocation.clear()
        locksByOwner.clear()
        locksById.clear()
    }

    /**
     * Gets locks in a specific world.
     */
    fun getLocksInWorld(worldName: String): List<Lock> {
        return locksByLocation.values.filter { it.location.world == worldName }
    }

    /**
     * Removes all locks owned by a specific player.
     */
    fun removeAllByOwner(ownerUUID: UUID): Int {
        val locationKeys = locksByOwner.remove(ownerUUID) ?: return 0
        var count = 0

        locationKeys.forEach { locationKey ->
            val lock = locksByLocation.remove(locationKey)
            lock?.let {
                locksById.remove(it.id)
                count++
            }
        }

        return count
    }

    /**
     * Validates a key ID for a lock at a location.
     */
    fun isValidKey(location: Location, keyId: String): Boolean {
        val lock = getLock(location) ?: return false
        return lock.isValidKey(keyId)
    }

    /**
     * Adds a trusted player to a lock.
     */
    fun addTrusted(location: Location, playerUUID: UUID): Boolean {
        val lock = getLock(location) ?: return false
        val result = lock.addTrusted(playerUUID)
        if (result) {
            updateLock(lock)
        }
        return result
    }

    /**
     * Removes a trusted player from a lock.
     */
    fun removeTrusted(location: Location, playerUUID: UUID): Boolean {
        val lock = getLock(location) ?: return false
        val result = lock.removeTrusted(playerUUID)
        if (result) {
            updateLock(lock)
        }
        return result
    }

    /**
     * Checks if a player has access to a lock at a location.
     */
    fun hasAccess(location: Location, playerUUID: UUID): Boolean {
        val lock = getLock(location) ?: return true // No lock = free access
        return lock.hasAccess(playerUUID)
    }

    /**
     * Checks if a player is the owner of a lock at a location.
     */
    fun isOwner(location: Location, playerUUID: UUID): Boolean {
        val lock = getLock(location) ?: return false
        return lock.isOwner(playerUUID)
    }
}
