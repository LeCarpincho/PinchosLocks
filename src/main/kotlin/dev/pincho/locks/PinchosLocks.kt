package dev.pincho.locks

import dev.pincho.locks.commands.LockCommands
import dev.pincho.locks.commands.LockpickCommands
import dev.pincho.locks.config.ConfigManager
import dev.pincho.locks.data.LockStorage
import dev.pincho.locks.listeners.LockInteractionListener
import dev.pincho.locks.listeners.LockProtectionListener
import dev.pincho.locks.managers.KeyManager
import dev.pincho.locks.managers.LockManager
import dev.pincho.locks.managers.LockpickManager
import dev.pincho.locks.utils.MessageUtils
import kotlinx.coroutines.*
import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin

/**
 * Main plugin class for Pincho's Lock's.
 *
 * This plugin provides a comprehensive lock system for Minecraft servers,
 * allowing players to protect their containers, doors, and other blocks
 * with tiered locks and keys.
 *
 * Architecture:
 * - Uses Clean Architecture with separated layers
 * - Implements coroutines for async operations
 * - Thread-safe data storage with proper synchronization
 *
 * @author MrSingu
 * @version 1.0.0
 */
class PinchosLocks : JavaPlugin(), CoroutineScope {

    // Coroutine scope setup with supervisor job for fault isolation
    private val supervisorJob = SupervisorJob()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        logger.severe("Unhandled coroutine exception: ${throwable.message}")
        throwable.printStackTrace()
    }

    override val coroutineContext = supervisorJob + Dispatchers.Default + exceptionHandler

    // Managers and services
    private lateinit var configManager: ConfigManager
    private lateinit var messageUtils: MessageUtils
    private lateinit var lockStorage: LockStorage
    private lateinit var lockManager: LockManager
    private lateinit var keyManager: KeyManager
    private lateinit var lockpickManager: LockpickManager

    // Auto-save task
    private var autoSaveJob: Job? = null

    override fun onEnable() {
        // Initialize configuration
        configManager = ConfigManager(this)
        configManager.load()

        // Initialize message system with Adventure support
        messageUtils = MessageUtils(this)
        messageUtils.initialize() // Initialize BukkitAudiences for cross-platform support
        messageUtils.loadLanguage(configManager.language)

        // Initialize storage with coroutine scope
        lockStorage = LockStorage(this, this)

        // Initialize managers
        lockManager = LockManager(this, lockStorage, configManager, messageUtils, this)
        keyManager = KeyManager(this, lockStorage, configManager, messageUtils)
        lockpickManager = LockpickManager(this, configManager, messageUtils, lockManager)

        // Load stored data
        launch {
            lockStorage.load().onFailure { e ->
                logger.severe("Failed to load lock data: ${e.message}")
            }
        }

        // Register listeners
        registerListeners()

        // Register commands
        registerCommands()

        // Start auto-save if enabled
        startAutoSave()

        // Print startup banner
        printBanner()
    }

    override fun onDisable() {
        // Cancel auto-save
        autoSaveJob?.cancel()

        // Close Adventure audiences
        messageUtils.close()

        // Save all data synchronously on disable
        runBlocking {
            lockStorage.save().onFailure { e ->
                logger.severe("Failed to save lock data on disable: ${e.message}")
            }
        }

        // Cancel all coroutines
        supervisorJob.cancel()

        // Print shutdown message
        printShutdownMessage()
    }

    /**
     * Registers all event listeners.
     */
    private fun registerListeners() {
        val pluginManager = server.pluginManager

        pluginManager.registerEvents(
            LockInteractionListener(this, lockManager, keyManager, lockpickManager, configManager, messageUtils),
            this
        )

        pluginManager.registerEvents(
            LockProtectionListener(this, lockManager, lockStorage, configManager, messageUtils),
            this
        )

        // Player quit listener for cleanup
        pluginManager.registerEvents(
            object : org.bukkit.event.Listener {
                @org.bukkit.event.EventHandler
                fun onPlayerQuit(event: org.bukkit.event.player.PlayerQuitEvent) {
                    keyManager.clearCooldown(event.player.uniqueId)
                    lockpickManager.clearCooldown(event.player.uniqueId)
                    lockManager.clearTemporaryAccess(event.player.uniqueId)
                }
            },
            this
        )
    }

    // Lockpick commands handler
    private lateinit var lockpickCommands: LockpickCommands

    /**
     * Registers all commands.
     */
    private fun registerCommands() {
        val lockCommand = LockCommands(this, lockManager, keyManager, configManager, messageUtils)

        getCommand("lock")?.let { command ->
            command.setExecutor(lockCommand)
            command.tabCompleter = lockCommand
        }

        // Register lockpick command
        lockpickCommands = LockpickCommands(this, configManager, messageUtils)

        getCommand("lockpick")?.let { command ->
            command.setExecutor(lockpickCommands)
            command.tabCompleter = lockpickCommands
        }
    }

    /**
     * Gets the lockpick commands handler.
     */
    fun getLockpickCommands(): LockpickCommands = lockpickCommands

    /**
     * Starts the auto-save task based on configuration.
     */
    private fun startAutoSave() {
        val interval = configManager.autoSaveInterval
        if (interval <= 0) return

        autoSaveJob = launch {
            while (isActive) {
                delay(interval * 60 * 1000L) // Convert minutes to milliseconds
                lockStorage.save().onSuccess {
                    if (configManager.debug) {
                        logger.info("[Debug] Auto-save completed")
                    }
                }.onFailure { e ->
                    logger.warning("Auto-save failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Reloads the plugin configuration and messages.
     */
    fun reload() {
        configManager.load()
        messageUtils.loadLanguage(configManager.language)

        // Restart auto-save with new interval
        autoSaveJob?.cancel()
        startAutoSave()

        logger.info("Configuration reloaded")
    }

    /**
     * Prints the shutdown message.
     */
    private fun printShutdownMessage() {
        val message = """
            |
            |§6╔═══════════════════════════════════════════════════════════╗
            |§6║  §cPincho's Lock's §f- §eDisabled                       §6║
            |§6║  §fData saved successfully. §aSee you later! §e(ᵔᴥᵔ)    §6║
            |§6╚═══════════════════════════════════════════════════════════╝
            |
        """.trimMargin()

        message.lines().forEach { line ->
            server.consoleSender.sendMessage(line)
        }
    }

    /**
     * Prints the startup banner with capybara ASCII art.
     */
    @Suppress("DEPRECATION")
    private fun printBanner() {
        val version = description.version
        val lockCount = lockStorage.getLockCount()

        val banner = """
            |
            |§e  ⠀⠀⢀⣀⠤⠿⢤⢖⡆⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
            |§e  ⡔⢩⠂⠀⠒⠗⠈⠀⠉⠢⠄⣀⠠⠤⠄⠒⢖⡒⢒⠂⠤⢄⠀⠀⠀⠀
            |§e  ⠇⠤⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠀⠀⠈⠀⠈⠈⡨⢀⠡⡪⠢⡀⠀
            |§e  ⠈⠒⠀⠤⠤⣄⡆⡂⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠢⠀⢕⠱⠀
            |§e  ⠀⠀⠀⠀⠀⠈⢳⣐⡐⠐⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠀⠁⠇
            |§e  ⠀⠀⠀⠀⠀⠀⠀⠑⢤⢁⠀⠆⠀⠀⠀⠀⠀⢀⢰⠀⠀⠀⡀⢄⡜⠀
            |§e  ⠀⠀⠀⠀⠀⠀⠀⠀⠘⡦⠄⡷⠢⠤⠤⠤⠤⢬⢈⡇⢠⣈⣰⠎⠀⠀
            |§e  ⠀⠀⠀⠀⠀⠀⠀⠀⠀⣃⢸⡇⠀⠀⠀⠀⠀⠈⢪⢀⣺⡅⢈⠆⠀⠀
            |§e  ⠀⠀⠀⠀⠀⠀⠀⠶⡿⠤⠚⠁⠀⠀⠀⢀⣠⡤⢺⣥⠟⢡⠃⠀⠀⠀
            |§e  ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠉⠉⠀⠀⠀⠀⠀⠀⠀⠀
            |
            |§6  ╔════════════════════════════════════════╗
            |§6  ║  §b§lPINCHO'S LOCK'S                  §6║
            |§6  ╠════════════════════════════════════════╣
            |§6  ║  §a► §fVersion: §e$version                 §6║
            |§6  ║  §a► §fDeveloper: §bMrSingu             §6║
            |§6  ║  §a► §fLocks Loaded: §d$lockCount                §6║
            |§6  ║  §a► §fStatus: §aEnabled!               §6║
            |§6  ╚════════════════════════════════════════╝
            |
        """.trimMargin()

        banner.lines().forEach { line ->
            server.consoleSender.sendMessage(line)
        }
    }

    /**
     * Creates a NamespacedKey for this plugin.
     * @param key The key name
     * @return A NamespacedKey bound to this plugin
     */
    fun createKey(key: String): NamespacedKey {
        return NamespacedKey(this, key)
    }

    /**
     * Gets the lock manager instance.
     */
    fun getLockManager(): LockManager = lockManager

    /**
     * Gets the key manager instance.
     */
    fun getKeyManager(): KeyManager = keyManager

    /**
     * Gets the config manager instance.
     */
    fun getConfigManager(): ConfigManager = configManager

    /**
     * Gets the message utility instance.
     */
    fun getMessages(): MessageUtils = messageUtils

    /**
     * Gets the lock storage instance.
     */
    fun getLockStorage(): LockStorage = lockStorage

    /**
     * Gets the lockpick manager instance.
     */
    fun getLockpickManager(): LockpickManager = lockpickManager
}
