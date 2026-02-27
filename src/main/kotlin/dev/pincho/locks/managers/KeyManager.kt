package dev.pincho.locks.managers

import dev.pincho.locks.PinchosLocks
import dev.pincho.locks.config.ConfigManager
import dev.pincho.locks.data.LockStorage
import dev.pincho.locks.models.Lock
import dev.pincho.locks.utils.ItemBuilder
import dev.pincho.locks.utils.MessageUtils
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages key creation, validation, and operations.
 */
class KeyManager(
    private val plugin: PinchosLocks,
    private val storage: LockStorage,
    private val config: ConfigManager,
    private val messages: MessageUtils
) {

    // Cooldown tracking for key usage
    private val keyCooldowns = ConcurrentHashMap<UUID, Long>()

    companion object {
        const val KEY_ID_KEY = "key_id"
        const val KEY_LOCK_ID_KEY = "key_lock_id"
        const val KEY_OWNER_KEY = "key_owner"
    }

    /**
     * Creates a key item for a specific lock.
     * @param lock The lock this key opens
     * @param keyId Optional specific key ID, generates new if null
     * @return The key ItemStack and the key ID
     */
    fun createKeyItem(lock: Lock, keyId: String? = null): Pair<ItemStack, String> {
        val actualKeyId = keyId ?: generateKeyId()

        // Add key to lock if it's new
        if (keyId == null) {
            lock.addKeyId(actualKeyId)
            storage.updateLock(lock)
            storage.saveAsync()
        }

        val ownerName = lock.ownerName
        val shortId = actualKeyId.take(8)

        val item = ItemBuilder.of(config.keyBaseMaterial)
            .name(messages.getRaw("items.key.name"))
            .loreStrings(
                messages.getList("items.key.lore").map {
                    it.replace("{id}", shortId)
                        .replace("{owner}", ownerName)
                }
            )
            .persistentString(plugin, KEY_ID_KEY, actualKeyId)
            .persistentString(plugin, KEY_LOCK_ID_KEY, lock.id)
            .persistentString(plugin, KEY_OWNER_KEY, lock.ownerUUID)
            .glow(true)
            .build()

        return item to actualKeyId
    }

    /**
     * Creates a master key that can open any lock (admin item).
     */
    fun createMasterKey(): ItemStack {
        return ItemBuilder.of(config.keyMasterMaterial)
            .name("<light_purple><bold>Master Key</bold></light_purple>")
            .loreStrings(
                listOf(
                    "<gray>This key can open any lock.</gray>",
                    "",
                    "<red>Admin Item</red>"
                )
            )
            .persistentString(plugin, KEY_ID_KEY, "MASTER")
            .glow(true)
            .build()
    }

    /**
     * Generates a new unique key ID.
     */
    private fun generateKeyId(): String {
        return UUID.randomUUID().toString().substring(0, 8)
    }

    /**
     * Gets the key ID from a key item.
     */
    fun getKeyId(item: ItemStack?): String? {
        if (item == null || item.type == Material.AIR) return null

        val meta = item.itemMeta ?: return null
        return meta.persistentDataContainer.get(
            plugin.createKey(KEY_ID_KEY),
            PersistentDataType.STRING
        )
    }

    /**
     * Gets the lock ID associated with a key.
     */
    fun getKeyLockId(item: ItemStack?): String? {
        if (item == null || item.type == Material.AIR) return null

        val meta = item.itemMeta ?: return null
        return meta.persistentDataContainer.get(
            plugin.createKey(KEY_LOCK_ID_KEY),
            PersistentDataType.STRING
        )
    }

    /**
     * Checks if an item is a key item.
     */
    fun isKeyItem(item: ItemStack?): Boolean {
        return getKeyId(item) != null
    }

    /**
     * Checks if a key item is a master key.
     */
    fun isMasterKey(item: ItemStack?): Boolean {
        return getKeyId(item) == "MASTER"
    }

    /**
     * Validates a key against a lock.
     */
    fun validateKey(item: ItemStack?, lock: Lock): Boolean {
        val keyId = getKeyId(item) ?: return false

        // Master keys always work
        if (keyId == "MASTER") return true

        return lock.isValidKey(keyId)
    }

    /**
     * Gives a key to a player for their lock at a location.
     * @return KeyResult indicating success or failure
     */
    fun giveKey(player: Player, lock: Lock): KeyResult {
        // Check if max keys reached
        if (lock.getKeyCount() >= config.maxKeysPerLock) {
            return KeyResult.MaxKeysReached
        }

        val (keyItem, keyId) = createKeyItem(lock)

        // Try to give the item
        val remaining = player.inventory.addItem(keyItem)
        if (remaining.isNotEmpty()) {
            // Inventory full, drop at player's location
            player.world.dropItemNaturally(player.location, keyItem)
        }

        return KeyResult.Success(keyItem, keyId)
    }

    /**
     * Duplicates an existing key.
     * @return The duplicated key or null if duplication is disabled
     */
    fun duplicateKey(originalKey: ItemStack): ItemStack? {
        if (!config.allowDuplicateKeys) return null

        val keyId = getKeyId(originalKey) ?: return null
        val lockId = getKeyLockId(originalKey) ?: return null

        // Find the lock
        val lock = storage.getLockById(lockId) ?: return null

        // Check if this key ID is still valid
        if (!lock.isValidKey(keyId)) return null

        // Create a duplicate with the same key ID
        return createKeyItem(lock, keyId).first
    }

    /**
     * Checks if a player can use a key (cooldown check).
     */
    fun canUseKey(player: Player): Boolean {
        if (player.hasPermission("pinchoslocks.bypass.cooldown")) return true

        val lastUse = keyCooldowns[player.uniqueId] ?: return true
        val elapsed = (System.currentTimeMillis() - lastUse) / 1000.0
        return elapsed >= config.keyUseCooldown
    }

    /**
     * Records key usage for cooldown tracking.
     */
    fun recordKeyUsage(player: Player) {
        keyCooldowns[player.uniqueId] = System.currentTimeMillis()
    }

    /**
     * Gets the remaining cooldown time for key usage.
     */
    fun getRemainingCooldown(player: Player): Double {
        val lastUse = keyCooldowns[player.uniqueId] ?: return 0.0
        val elapsed = (System.currentTimeMillis() - lastUse) / 1000.0
        return (config.keyUseCooldown - elapsed).coerceAtLeast(0.0)
    }

    /**
     * Clears cooldown data for a player (called on quit).
     */
    fun clearCooldown(playerUUID: UUID) {
        keyCooldowns.remove(playerUUID)
    }

    /**
     * Result class for key operations.
     */
    sealed class KeyResult {
        data class Success(val item: ItemStack, val keyId: String) : KeyResult()
        object MaxKeysReached : KeyResult()
        object DuplicationDisabled : KeyResult()
        object InvalidKey : KeyResult()
    }
}
