package dev.pincho.locks.data

import dev.pincho.locks.models.Lock
import dev.pincho.locks.models.SerializableLocation
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Handles persistence of lock data to JSON files.
 * Uses coroutines for async I/O operations to prevent main thread blocking.
 *
 * Optimizations:
 * - Mutex for thread-safe write operations
 * - Debounced saves to prevent excessive I/O
 * - Dirty flag tracking for efficient saves
 * - Atomic operations for thread safety
 */
class LockStorage(
    private val plugin: JavaPlugin,
    private val scope: CoroutineScope
) {

    companion object {
        /** Minimum time between saves in milliseconds (debounce) */
        private const val SAVE_DEBOUNCE_MS = 2000L

        /** Maximum pending changes before forcing a save */
        private const val MAX_PENDING_CHANGES = 50
    }

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

    // Mutex for serializing write operations to prevent race conditions
    private val writeMutex = Mutex()

    // Track if data needs to be saved
    private val isDirty = AtomicBoolean(false)

    // Count of pending changes since last save
    private val pendingChanges = AtomicLong(0)

    // Last save timestamp for debouncing
    private val lastSaveTime = AtomicLong(0)

    // Debounced save job
    private var debouncedSaveJob: Job? = null

    private val dataFile: File
        get() = File(plugin.dataFolder, "locks.json")

    /**
     * Loads all locks from the JSON file.
     * Should be called during plugin enable.
     * Fully async - runs on IO dispatcher.
     */
    suspend fun load(): Result<Int> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
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

                // Clear all caches atomically
                locksByLocation.clear()
                locksByOwner.clear()
                locksById.clear()

                var migratedCount = 0
                locks.forEach { lock ->
                    // MIGRATION: Ensure owner is in trustedPlayers (for old locks)
                    if (lock.ownerUUID !in lock.trustedPlayers) {
                        lock.trustedPlayers.add(lock.ownerUUID)
                        migratedCount++
                    }

                    indexLock(lock)
                }

                plugin.logger.info("Loaded ${locks.size} locks from storage.")

                // Save migrated locks if any were updated
                if (migratedCount > 0) {
                    plugin.logger.info("Migrated $migratedCount locks to include owner in trusted list. Saving...")
                    saveInternal()
                    plugin.logger.info("Migration saved successfully.")
                }

                // Reset dirty state after load
                isDirty.set(false)
                pendingChanges.set(0)

                locks.size
            }
        }
    }

    /**
     * Indexes a lock in all cache maps.
     * Must be called with proper synchronization.
     */
    private fun indexLock(lock: Lock) {
        val locationKey = lock.location.toKey()
        locksByLocation[locationKey] = lock
        locksById[lock.id] = lock

        val ownerUUID = UUID.fromString(lock.ownerUUID)
        locksByOwner.getOrPut(ownerUUID) { ConcurrentHashMap.newKeySet() }.add(locationKey)
    }

    /**
     * Removes a lock from all cache maps.
     * Must be called with proper synchronization.
     */
    private fun unindexLock(lock: Lock, locationKey: String) {
        locksByLocation.remove(locationKey)
        locksById.remove(lock.id)

        val ownerUUID = UUID.fromString(lock.ownerUUID)
        locksByOwner[ownerUUID]?.remove(locationKey)
    }

    /**
     * Saves all locks to the JSON file.
     * Uses coroutines to prevent blocking the main thread.
     * Thread-safe with mutex protection.
     */
    suspend fun save(): Result<Unit> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            saveInternal()
        }
    }

    /**
     * Internal save implementation - must be called with mutex held.
     */
    private fun saveInternal(): Result<Unit> = runCatching {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }

        val locks = locksByLocation.values.toList()
        val content = json.encodeToString(locks)

        // Write to temp file first, then rename for atomicity
        val tempFile = File(plugin.dataFolder, "locks.json.tmp")
        tempFile.writeText(content)

        // Atomic rename (on most filesystems)
        if (!tempFile.renameTo(dataFile)) {
            // Fallback: copy and delete if rename fails (cross-filesystem)
            tempFile.copyTo(dataFile, overwrite = true)
            tempFile.delete()
        }

        // Reset state after successful save
        isDirty.set(false)
        pendingChanges.set(0)
        lastSaveTime.set(System.currentTimeMillis())

        plugin.logger.info("Saved ${locks.size} locks to storage.")
    }

    /**
     * Saves locks asynchronously with debouncing.
     * Multiple rapid calls will be coalesced into a single save.
     * Fire-and-forget with error logging.
     */
    fun saveAsync() {
        markDirty()

        // Check if we should force an immediate save due to many pending changes
        if (pendingChanges.get() >= MAX_PENDING_CHANGES) {
            scope.launch {
                save().onFailure { e ->
                    plugin.logger.severe("Failed to save lock data: ${e.message}")
                }
            }
            return
        }

        // Cancel any pending debounced save
        debouncedSaveJob?.cancel()

        // Schedule a new debounced save
        debouncedSaveJob = scope.launch {
            // Wait for debounce period
            val timeSinceLastSave = System.currentTimeMillis() - lastSaveTime.get()
            val waitTime = (SAVE_DEBOUNCE_MS - timeSinceLastSave).coerceAtLeast(0)

            if (waitTime > 0) {
                delay(waitTime)
            }

            // Only save if still dirty
            if (isDirty.get()) {
                save().onFailure { e ->
                    plugin.logger.severe("Failed to save lock data: ${e.message}")
                }
            }
        }
    }

    /**
     * Marks data as dirty and increments pending change count.
     */
    private fun markDirty() {
        isDirty.set(true)
        pendingChanges.incrementAndGet()
    }

    /**
     * Creates a location key from a Bukkit Location.
     * Format: "worldname:x:y:z"
     * Cached StringBuilder pattern for reduced allocations.
     */
    private fun locationToKey(location: Location): String {
        val worldName = location.world?.name ?: "world"
        return buildString(worldName.length + 30) {
            append(worldName)
            append(':')
            append(location.blockX)
            append(':')
            append(location.blockY)
            append(':')
            append(location.blockZ)
        }
    }

    /**
     * Gets a lock at the specified location.
     * This method is the main entry point for finding locks.
     * Thread-safe read operation.
     */
    fun getLock(location: Location): Lock? {
        val key = locationToKey(location)
        return locksByLocation[key]
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
     * Optimized for quick existence check.
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

        // Use putIfAbsent for atomic check-and-set
        if (locksByLocation.putIfAbsent(locationKey, lock) != null) {
            return false
        }

        // Index in other maps
        locksById[lock.id] = lock
        val ownerUUID = UUID.fromString(lock.ownerUUID)
        locksByOwner.getOrPut(ownerUUID) { ConcurrentHashMap.newKeySet() }.add(locationKey)

        markDirty()
        return true
    }

    /**
     * Removes a lock at the specified location.
     * @return the removed lock, or null if no lock existed
     */
    fun removeLock(location: Location): Lock? {
        val locationKey = locationToKey(location)
        val lock = locksByLocation.remove(locationKey) ?: return null

        // Remove from other indices
        locksById.remove(lock.id)
        val ownerUUID = UUID.fromString(lock.ownerUUID)
        locksByOwner[ownerUUID]?.remove(locationKey)

        markDirty()
        return lock
    }

    /**
     * Updates an existing lock.
     */
    fun updateLock(lock: Lock) {
        val locationKey = lock.location.toKey()
        locksByLocation[locationKey] = lock
        locksById[lock.id] = lock
        markDirty()
    }

    /**
     * Gets all locks owned by a specific player.
     * Returns a defensive copy to prevent external modification.
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
     * Gets all locks as an unmodifiable collection.
     */
    fun getAllLocks(): Collection<Lock> = locksByLocation.values.toList()

    /**
     * Clears all locks (for testing/admin purposes).
     */
    fun clearAll() {
        locksByLocation.clear()
        locksByOwner.clear()
        locksById.clear()
        markDirty()
    }

    /**
     * Gets locks in a specific world.
     * Optimized with early filtering.
     */
    fun getLocksInWorld(worldName: String): List<Lock> {
        return locksByLocation.values.filter { it.location.world == worldName }
    }

    /**
     * Removes all locks owned by a specific player.
     * @return the number of locks removed
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

        if (count > 0) {
            markDirty()
        }
        return count
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

    /**
     * Forces an immediate save, bypassing debounce.
     * Used during plugin disable.
     */
    suspend fun forceSave(): Result<Unit> {
        debouncedSaveJob?.cancel()
        return save()
    }

    /**
     * Checks if there are unsaved changes.
     */
    fun hasPendingChanges(): Boolean = isDirty.get()
}
