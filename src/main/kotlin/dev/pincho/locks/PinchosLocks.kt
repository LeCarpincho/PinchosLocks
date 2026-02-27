package dev.pincho.locks

import dev.pincho.locks.commands.LockCommands
import dev.pincho.locks.commands.LockpickCommands
import dev.pincho.locks.config.ConfigManager
import dev.pincho.locks.data.LockStorage
import dev.pincho.locks.listeners.LockInteractionListener
import dev.pincho.locks.listeners.LockProtectionListener
import dev.pincho.locks.managers.LockManager
import dev.pincho.locks.managers.LockpickManager
import dev.pincho.locks.security.IntegrityChecker
import dev.pincho.locks.utils.LockLogger
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
 * - Code integrity checking for protection
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

    // Security components
    private lateinit var integrityChecker: IntegrityChecker

    // Managers and services
    private lateinit var configManager: ConfigManager
    private lateinit var messageUtils: MessageUtils
    private lateinit var lockStorage: LockStorage
    private lateinit var lockManager: LockManager
    private lateinit var lockpickManager: LockpickManager
    private lateinit var lockLogger: LockLogger

    // Auto-save task
    private var autoSaveJob: Job? = null

    // Cleanup task for expired cooldowns
    private var cleanupJob: Job? = null

    // Security validation job
    private var securityJob: Job? = null

    override fun onEnable() {
        // Phase 1: Security initialization (integrity check)
        initializeSecurity()

        // Initialize configuration
        configManager = ConfigManager(this)
        configManager.load()

        // Initialize logger
        lockLogger = LockLogger(this, configManager)

        // Initialize message system with Adventure support
        messageUtils = MessageUtils(this)
        messageUtils.initialize()
        messageUtils.loadLanguage(configManager.language)

        // Initialize storage with coroutine scope
        lockStorage = LockStorage(this, this)

        // Initialize managers
        lockManager = LockManager(this, lockStorage, configManager, messageUtils)
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

        // Start periodic cleanup
        startCleanupTask()

        // Start security validation task
        startSecurityValidation()

        // Print startup banner
        printBanner()
    }

    /**
     * Initializes security components (integrity checking only).
     */
    private fun initializeSecurity() {
        try {
            integrityChecker = IntegrityChecker(this)
            val integrityStatus = integrityChecker.performCheck()

            when (integrityStatus) {
                IntegrityChecker.IntegrityStatus.DEBUGGER_DETECTED -> {
                    logger.warning("Debugger detected.")
                }
                IntegrityChecker.IntegrityStatus.JAR_MODIFIED,
                IntegrityChecker.IntegrityStatus.CLASS_TAMPERED -> {
                    logger.severe("Plugin integrity check failed. The plugin may have been modified.")
                }
                IntegrityChecker.IntegrityStatus.SUSPICIOUS_ENVIRONMENT -> {
                    logger.warning("Running in suspicious environment.")
                }
                else -> {
                    // Integrity OK
                }
            }
        } catch (e: Exception) {
            logger.warning("Security initialization error: ${e.message}")
        }
    }

    override fun onDisable() {
        // Cancel auto-save and cleanup
        autoSaveJob?.cancel()
        cleanupJob?.cancel()
        securityJob?.cancel()

        // Shutdown lockpick manager to cancel all sessions and timers
        if (::lockpickManager.isInitialized) {
            lockpickManager.shutdown()
        }

        // Close Adventure audiences
        if (::messageUtils.isInitialized) {
            messageUtils.close()
        }

        // Force save all data synchronously on disable (bypass debounce)
        if (::lockStorage.isInitialized) {
            runBlocking {
                lockStorage.forceSave().onFailure { e ->
                    logger.severe("Failed to save lock data on disable: ${e.message}")
                }
            }
        }

        // Cancel all coroutines and wait for completion
        runBlocking {
            supervisorJob.cancelAndJoin()
        }

        // Print shutdown message
        printShutdownMessage()
    }

    /**
     * Registers all event listeners.
     */
    private fun registerListeners() {
        val pluginManager = server.pluginManager

        pluginManager.registerEvents(
            LockInteractionListener(this, lockManager, lockpickManager, configManager, messageUtils),
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
        val lockCommand = LockCommands(this, lockManager, configManager, messageUtils)

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
                delay(interval * 60 * 1000L)
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
     * Starts the periodic cleanup task for expired cooldowns.
     * Runs every 5 minutes to clean up memory.
     */
    private fun startCleanupTask() {
        cleanupJob = launch {
            while (isActive) {
                delay(5 * 60 * 1000L)
                lockpickManager.cleanupExpiredCooldowns()
                if (configManager.debug) {
                    logger.info("[Debug] Cleanup task completed")
                }
            }
        }
    }

    /**
     * Starts periodic security validation.
     * Runs every 30 minutes to verify integrity.
     */
    private fun startSecurityValidation() {
        securityJob = launch {
            while (isActive) {
                delay(30 * 60 * 1000L)

                // Perform quick integrity check
                if (::integrityChecker.isInitialized) {
                    integrityChecker.quickCheck()
                }

                if (configManager.debug) {
                    logger.info("[Debug] Security validation completed")
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
        cleanupJob?.cancel()
        startAutoSave()
        startCleanupTask()

        logger.info("Configuration reloaded")
    }

    /**
     * Prints the shutdown message.
     */
    private fun printShutdownMessage() {
        val message = """
            |
            |${"\u00A7"}6=================================================
            |${"\u00A7"}6|  ${"\u00A7"}cPincho's Lock's ${"\u00A7"}f- ${"\u00A7"}eDisabled                 ${"\u00A7"}6|
            |${"\u00A7"}6|  ${"\u00A7"}fData saved successfully. ${"\u00A7"}aSee you later!     ${"\u00A7"}6|
            |${"\u00A7"}6=================================================
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
            |${"\u00A7"}e  ⠀⠀⢀⣀⠤⠿⢤⢖⡆⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
            |${"\u00A7"}e  ⡔⢩⠂⠀⠒⠗⠈⠀⠉⠢⠄⣀⠠⠤⠄⠒⢖⡒⢒⠂⠤⢄⠀⠀⠀⠀
            |${"\u00A7"}e  ⠇⠤⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠀⠀⠈⠀⠈⠈⡨⢀⠡⡪⠢⡀⠀
            |${"\u00A7"}e  ⠈⠒⠀⠤⠤⣄⡆⡂⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠢⠀⢕⠱⠀
            |${"\u00A7"}e  ⠀⠀⠀⠀⠀⠈⢳⣐⡐⠐⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠀⠁⠇
            |${"\u00A7"}e  ⠀⠀⠀⠀⠀⠀⠀⠑⢤⢁⠀⠆⠀⠀⠀⠀⠀⢀⢰⠀⠀⠀⡀⢄⡜⠀
            |${"\u00A7"}e  ⠀⠀⠀⠀⠀⠀⠀⠀⠘⡦⠄⡷⠢⠤⠤⠤⠤⢬⢈⡇⢠⣈⣰⠎⠀⠀
            |${"\u00A7"}e  ⠀⠀⠀⠀⠀⠀⠀⠀⠀⣃⢸⡇⠀⠀⠀⠀⠀⠈⢪⢀⣺⡅⢈⠆⠀⠀
            |${"\u00A7"}e  ⠀⠀⠀⠀⠀⠀⠀⠶⡿⠤⠚⠁⠀⠀⠀⢀⣠⡤⢺⣥⠟⢡⠃⠀⠀⠀
            |${"\u00A7"}e  ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠉⠉⠀⠀⠀⠀⠀⠀⠀⠀
            |
            |${"\u00A7"}6  ╔════════════════════════════════════════╗
            |${"\u00A7"}6  ║  ${"\u00A7"}b${"\u00A7"}lPINCHO'S LOCK'S                  ${"\u00A7"}6║
            |${"\u00A7"}6  ╠════════════════════════════════════════╣
            |${"\u00A7"}6  ║  ${"\u00A7"}a► ${"\u00A7"}fVersion: ${"\u00A7"}e$version                     ${"\u00A7"}6║
            |${"\u00A7"}6  ║  ${"\u00A7"}a► ${"\u00A7"}fDeveloper: ${"\u00A7"}bMrSingu                 ${"\u00A7"}6║
            |${"\u00A7"}6  ║  ${"\u00A7"}a► ${"\u00A7"}fLocks Loaded: ${"\u00A7"}d$lockCount                    ${"\u00A7"}6║
            |${"\u00A7"}6  ║  ${"\u00A7"}a► ${"\u00A7"}fStatus: ${"\u00A7"}aEnabled!                   ${"\u00A7"}6║
            |${"\u00A7"}6  ╚════════════════════════════════════════╝
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

    /**
     * Gets the lock logger instance.
     */
    fun getLockLogger(): LockLogger = lockLogger

    /**
     * Gets the integrity checker instance.
     */
    fun getIntegrityChecker(): IntegrityChecker = integrityChecker
}
