package dev.pincho.locks.config

import dev.pincho.locks.models.LockTier
import dev.pincho.locks.models.LockableBlock
import dev.pincho.locks.models.LockpickTier
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * Manages plugin configuration loading and access.
 * Provides type-safe access to all configuration values.
 */
class ConfigManager(private val plugin: JavaPlugin) {

    private lateinit var config: FileConfiguration

    // General settings
    var language: String = "es_ES"
        private set
    var debug: Boolean = false
        private set
    var autoSaveInterval: Int = 5
        private set

    // Tier configurations
    private val tierConfigs = mutableMapOf<LockTier, TierConfig>()
    private val lockpickConfigs = mutableMapOf<LockpickTier, LockpickConfig>()

    // Key settings
    var keyBaseMaterial: Material = Material.TRIAL_KEY
        private set
    var keyMasterMaterial: Material = Material.OMINOUS_TRIAL_KEY
        private set
    var allowDuplicateKeys: Boolean = true
        private set
    var maxKeysPerLock: Int = 5
        private set

    // Protection settings
    var preventExplosions: Boolean = true
        private set
    var preventPistons: Boolean = true
        private set
    var preventHoppers: Boolean = true
        private set
    var preventFire: Boolean = true
        private set
    var preventEnderman: Boolean = true
        private set
    var preventWither: Boolean = true
        private set

    // Cooldowns
    var lockPlacementCooldown: Double = 1.0
        private set
    var keyUseCooldown: Double = 0.5
        private set
    var lockpickAttemptCooldown: Double = 3.0
        private set

    // Sounds
    var soundLockPlace: Sound = Sound.BLOCK_IRON_DOOR_CLOSE
        private set
    var soundLockRemove: Sound = Sound.BLOCK_IRON_DOOR_OPEN
        private set
    var soundLockSuccess: Sound = Sound.BLOCK_CHEST_OPEN
        private set
    var soundLockDenied: Sound = Sound.BLOCK_IRON_DOOR_CLOSE
        private set
    var soundKeyUse: Sound = Sound.BLOCK_IRON_TRAPDOOR_OPEN
        private set
    var soundLockpickSuccess: Sound = Sound.ENTITY_PLAYER_LEVELUP
        private set
    var soundLockpickFail: Sound = Sound.ENTITY_ITEM_BREAK
        private set
    var soundLockpickBreak: Sound = Sound.ENTITY_ITEM_BREAK
        private set

    // Particles
    var particlesEnabled: Boolean = true
        private set

    // Lockpicks enabled
    var lockpicksEnabled: Boolean = true
        private set

    /**
     * Data class for tier configuration.
     */
    data class TierConfig(
        val enabled: Boolean,
        val displayName: String,
        val material: Material,
        val customModelData: Int,
        val pickDifficulty: Int,
        val pickable: Boolean
    )

    /**
     * Data class for lockpick configuration.
     */
    data class LockpickConfig(
        val enabled: Boolean,
        val displayName: String,
        val material: Material,
        val customModelData: Int,
        val successModifier: Int,
        val durability: Int,
        val cooldown: Double
    )

    /**
     * Loads or reloads the configuration.
     */
    fun load() {
        // Save default config if it doesn't exist
        if (!File(plugin.dataFolder, "config.yml").exists()) {
            plugin.saveDefaultConfig()
        }

        // Save default language files
        saveDefaultLanguageFiles()

        plugin.reloadConfig()
        config = plugin.config

        loadGeneralSettings()
        loadTierConfigs()
        loadLockpickConfigs()
        loadKeySettings()
        loadProtectionSettings()
        loadCooldowns()
        loadSounds()
        loadParticles()
        loadLockableBlocks()

        if (debug) {
            plugin.logger.info("[Debug] Configuration loaded successfully")
        }
    }

    /**
     * Saves default language files to the plugin folder.
     */
    private fun saveDefaultLanguageFiles() {
        val langFolder = File(plugin.dataFolder, "lang")
        if (!langFolder.exists()) {
            langFolder.mkdirs()
        }

        listOf("es_ES.yml", "en_EN.yml").forEach { fileName ->
            val file = File(langFolder, fileName)
            if (!file.exists()) {
                plugin.saveResource("lang/$fileName", false)
            }
        }
    }

    private fun loadGeneralSettings() {
        language = config.getString("general.language", "es_ES") ?: "es_ES"
        debug = config.getBoolean("general.debug", false)
        autoSaveInterval = config.getInt("general.auto-save-interval", 5)
    }

    private fun loadTierConfigs() {
        tierConfigs.clear()

        LockTier.entries.forEach { tier ->
            val path = "tiers.${tier.name.lowercase()}"
            val tierConfig = TierConfig(
                enabled = config.getBoolean("$path.enabled", true),
                displayName = config.getString("$path.display-name", "<gold>${tier.displayName} Lock</gold>")
                    ?: "<gold>${tier.displayName} Lock</gold>",
                material = config.getString("$path.material")?.let {
                    runCatching { Material.valueOf(it) }.getOrNull()
                } ?: tier.defaultMaterial,
                customModelData = config.getInt("$path.custom-model-data", tier.defaultCustomModelData),
                pickDifficulty = config.getInt("$path.pick-difficulty", 50),
                pickable = config.getBoolean("$path.pickable", true)
            )
            tierConfigs[tier] = tierConfig
        }
    }

    private fun loadLockpickConfigs() {
        lockpickConfigs.clear()

        lockpicksEnabled = config.getBoolean("lockpicks.enabled", true)

        LockpickTier.entries.forEach { tier ->
            val path = "lockpicks.${tier.name.lowercase()}"
            val pickConfig = LockpickConfig(
                enabled = config.getBoolean("$path.enabled", true),
                displayName = config.getString("$path.display-name", "<gray>${tier.displayName} Lockpick</gray>")
                    ?: "<gray>${tier.displayName} Lockpick</gray>",
                material = config.getString("$path.material")?.let {
                    runCatching { Material.valueOf(it) }.getOrNull()
                } ?: tier.defaultMaterial,
                customModelData = config.getInt("$path.custom-model-data", tier.defaultCustomModelData),
                successModifier = config.getInt("$path.success-modifier", 0),
                durability = config.getInt("$path.durability", 5),
                cooldown = config.getDouble("$path.cooldown", 3.0)
            )
            lockpickConfigs[tier] = pickConfig
        }
    }

    private fun loadKeySettings() {
        keyBaseMaterial = config.getString("keys.base-material")?.let {
            runCatching { Material.valueOf(it) }.getOrNull()
        } ?: Material.TRIAL_KEY

        keyMasterMaterial = config.getString("keys.master-material")?.let {
            runCatching { Material.valueOf(it) }.getOrNull()
        } ?: Material.OMINOUS_TRIAL_KEY

        allowDuplicateKeys = config.getBoolean("keys.allow-duplicates", true)
        maxKeysPerLock = config.getInt("keys.max-keys-per-lock", 5)
    }

    private fun loadProtectionSettings() {
        preventExplosions = config.getBoolean("protection.prevent-explosions", true)
        preventPistons = config.getBoolean("protection.prevent-pistons", true)
        preventHoppers = config.getBoolean("protection.prevent-hoppers", true)
        preventFire = config.getBoolean("protection.prevent-fire", true)
        preventEnderman = config.getBoolean("protection.prevent-enderman", true)
        preventWither = config.getBoolean("protection.prevent-wither", true)
    }

    private fun loadCooldowns() {
        lockPlacementCooldown = config.getDouble("cooldowns.lock-placement", 1.0)
        keyUseCooldown = config.getDouble("cooldowns.key-use", 0.5)
        lockpickAttemptCooldown = config.getDouble("cooldowns.lockpick-attempt", 3.0)
    }

    private fun loadSounds() {
        soundLockPlace = parseSound(config.getString("sounds.lock-place"), Sound.BLOCK_IRON_DOOR_CLOSE)
        soundLockRemove = parseSound(config.getString("sounds.lock-remove"), Sound.BLOCK_IRON_DOOR_OPEN)
        soundLockSuccess = parseSound(config.getString("sounds.lock-success"), Sound.BLOCK_CHEST_OPEN)
        soundLockDenied = parseSound(config.getString("sounds.lock-denied"), Sound.BLOCK_IRON_DOOR_CLOSE)
        soundKeyUse = parseSound(config.getString("sounds.key-use"), Sound.BLOCK_IRON_TRAPDOOR_OPEN)
        soundLockpickSuccess = parseSound(config.getString("sounds.lockpick-success"), Sound.ENTITY_PLAYER_LEVELUP)
        soundLockpickFail = parseSound(config.getString("sounds.lockpick-fail"), Sound.ENTITY_ITEM_BREAK)
        soundLockpickBreak = parseSound(config.getString("sounds.lockpick-break"), Sound.ENTITY_ITEM_BREAK)
    }

    private fun loadParticles() {
        particlesEnabled = config.getBoolean("particles.enabled", true)
    }

    private fun loadLockableBlocks() {
        val containers = config.getStringList("lockable-blocks.containers")
        val shulkers = config.getStringList("lockable-blocks.shulkers")
        val doors = config.getStringList("lockable-blocks.doors")
        val trapdoors = config.getStringList("lockable-blocks.trapdoors")
        val fenceGates = config.getStringList("lockable-blocks.fence-gates")

        if (containers.isNotEmpty() || shulkers.isNotEmpty() || doors.isNotEmpty() ||
            trapdoors.isNotEmpty() || fenceGates.isNotEmpty()) {
            LockableBlock.updateFromConfig(containers, shulkers, doors, trapdoors, fenceGates)
        }
    }

    @Suppress("DEPRECATION")
    private fun parseSound(name: String?, default: Sound): Sound {
        if (name == null) return default
        return runCatching { Sound.valueOf(name) }.getOrDefault(default)
    }

    /**
     * Gets the configuration for a specific lock tier.
     */
    fun getTierConfig(tier: LockTier): TierConfig {
        return tierConfigs[tier] ?: TierConfig(
            enabled = true,
            displayName = tier.displayName,
            material = tier.defaultMaterial,
            customModelData = tier.defaultCustomModelData,
            pickDifficulty = 50,
            pickable = true
        )
    }

    /**
     * Gets the configuration for a specific lockpick tier.
     */
    fun getLockpickConfig(tier: LockpickTier): LockpickConfig {
        return lockpickConfigs[tier] ?: LockpickConfig(
            enabled = true,
            displayName = tier.displayName,
            material = tier.defaultMaterial,
            customModelData = tier.defaultCustomModelData,
            successModifier = 0,
            durability = 5,
            cooldown = 3.0
        )
    }

    /**
     * Checks if a lock tier is enabled.
     */
    fun isTierEnabled(tier: LockTier): Boolean {
        return tierConfigs[tier]?.enabled ?: true
    }

    /**
     * Checks if a lockpick tier is enabled.
     */
    fun isLockpickEnabled(tier: LockpickTier): Boolean {
        return lockpicksEnabled && (lockpickConfigs[tier]?.enabled ?: true)
    }

    /**
     * Gets all enabled lock tiers.
     */
    fun getEnabledTiers(): List<LockTier> {
        return LockTier.entries.filter { isTierEnabled(it) }
    }

    /**
     * Gets all enabled lockpick tiers.
     */
    fun getEnabledLockpicks(): List<LockpickTier> {
        if (!lockpicksEnabled) return emptyList()
        return LockpickTier.entries.filter { isLockpickEnabled(it) }
    }
}
