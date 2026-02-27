package dev.pincho.locks.utils

import dev.pincho.locks.config.ConfigManager
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.logging.Level

/**
 * Centralized logging utility for PinchosLocks.
 * Provides debug logging that can be enabled/disabled via config.
 */
class LockLogger(
    private val plugin: JavaPlugin,
    private val config: ConfigManager
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    private val logFile: File by lazy {
        File(plugin.dataFolder, "debug.log").also {
            if (!it.exists()) {
                it.parentFile?.mkdirs()
                it.createNewFile()
            }
        }
    }

    /**
     * Logs a debug message (only if debug is enabled).
     */
    fun debug(message: String) {
        if (config.debug) {
            val formatted = "[DEBUG] $message"
            plugin.logger.info(formatted)
            writeToFile(formatted)
        }
    }

    /**
     * Logs an info message (always shown).
     */
    fun info(message: String) {
        plugin.logger.info(message)
        if (config.debug) {
            writeToFile("[INFO] $message")
        }
    }

    /**
     * Logs a warning message (always shown).
     */
    fun warn(message: String) {
        plugin.logger.warning(message)
        writeToFile("[WARN] $message")
    }

    /**
     * Logs an error message (always shown).
     */
    fun error(message: String, throwable: Throwable? = null) {
        plugin.logger.log(Level.SEVERE, message, throwable)
        writeToFile("[ERROR] $message")
        throwable?.let {
            writeToFile("[ERROR] ${it.stackTraceToString()}")
        }
    }

    /**
     * Logs access check details for debugging.
     */
    fun logAccessCheck(
        playerName: String,
        playerUuid: String,
        lockId: String,
        lockOwnerName: String,
        lockOwnerUuid: String,
        trustedList: Set<String>,
        result: String
    ) {
        if (config.debug) {
            val sb = StringBuilder()
            sb.appendLine("=== ACCESS CHECK ===")
            sb.appendLine("  Player: $playerName")
            sb.appendLine("  Player UUID: $playerUuid")
            sb.appendLine("  Lock ID: $lockId")
            sb.appendLine("  Lock Owner: $lockOwnerName")
            sb.appendLine("  Lock Owner UUID: $lockOwnerUuid")
            sb.appendLine("  UUID Match: ${playerUuid == lockOwnerUuid}")
            sb.appendLine("  Trusted List: $trustedList")
            sb.appendLine("  In Trusted: ${playerUuid in trustedList}")
            sb.appendLine("  RESULT: $result")
            sb.appendLine("====================")

            val message = sb.toString()
            plugin.logger.info(message)
            writeToFile(message)
        }
    }

    /**
     * Logs block interaction for debugging.
     */
    fun logBlockInteraction(
        action: String,
        playerName: String,
        blockType: String,
        location: String,
        hasLock: Boolean,
        result: String
    ) {
        if (config.debug) {
            val message = "[INTERACTION] $action | Player: $playerName | Block: $blockType @ $location | HasLock: $hasLock | Result: $result"
            plugin.logger.info(message)
            writeToFile(message)
        }
    }

    private fun writeToFile(message: String) {
        try {
            val timestamp = dateFormat.format(Date())
            logFile.appendText("[$timestamp] $message\n")
        } catch (e: Exception) {
            // Silently fail file logging
        }
    }

    /**
     * Clears the debug log file.
     */
    fun clearLog() {
        try {
            logFile.writeText("")
            plugin.logger.info("Debug log cleared.")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to clear debug log: ${e.message}")
        }
    }
}
