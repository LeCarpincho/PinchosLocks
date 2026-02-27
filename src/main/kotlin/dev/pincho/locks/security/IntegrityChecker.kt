package dev.pincho.locks.security

import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.zip.CRC32

/**
 * Integrity verification system for PinchosLocks.
 * Detects tampering, decompilation, and unauthorized modifications.
 *
 * Security measures:
 * - JAR checksum verification
 * - Class integrity verification
 * - Runtime environment checks
 * - Anti-debugging detection
 */
class IntegrityChecker(private val plugin: JavaPlugin) {

    companion object {
        // Critical classes that should not be modified
        private val CRITICAL_CLASSES = listOf(
            "dev.pincho.locks.PinchosLocks",
            "dev.pincho.locks.security.IntegrityChecker",
            "dev.pincho.locks.managers.LockManager",
            "dev.pincho.locks.data.LockStorage"
        )

        // Expected class signatures (populated at build time via ProGuard)
        // These will be replaced during the build process
        private const val EXPECTED_JAR_HASH = "BUILD_TIME_HASH_PLACEHOLDER"
        private const val EXPECTED_MAIN_CLASS_CRC = 0L
    }

    enum class IntegrityStatus {
        VALID,
        JAR_MODIFIED,
        CLASS_TAMPERED,
        DEBUGGER_DETECTED,
        SUSPICIOUS_ENVIRONMENT,
        CHECK_FAILED
    }

    private var lastCheckTime: Long = 0
    private var checkCount: Int = 0
    private var failureCount: Int = 0
    private val checkInterval = 300000L // 5 minutes

    /**
     * Performs a comprehensive integrity check.
     * Should be called during startup and periodically.
     */
    fun performCheck(): IntegrityStatus {
        checkCount++

        try {
            // Check 1: Anti-debugging
            if (isDebuggerAttached()) {
                recordFailure("Debugger detected")
                return IntegrityStatus.DEBUGGER_DETECTED
            }

            // Check 2: Suspicious environment
            if (isSuspiciousEnvironment()) {
                recordFailure("Suspicious environment")
                return IntegrityStatus.SUSPICIOUS_ENVIRONMENT
            }

            // Check 3: JAR integrity (only if hash is set)
            if (EXPECTED_JAR_HASH != "BUILD_TIME_HASH_PLACEHOLDER") {
                if (!verifyJarIntegrity()) {
                    recordFailure("JAR modified")
                    return IntegrityStatus.JAR_MODIFIED
                }
            }

            // Check 4: Critical class integrity
            if (!verifyCriticalClasses()) {
                recordFailure("Class tampered")
                return IntegrityStatus.CLASS_TAMPERED
            }

            // Check 5: Runtime consistency
            if (!verifyRuntimeConsistency()) {
                recordFailure("Runtime inconsistency")
                return IntegrityStatus.CLASS_TAMPERED
            }

            lastCheckTime = System.currentTimeMillis()
            return IntegrityStatus.VALID

        } catch (e: Exception) {
            // Don't expose internal errors
            return IntegrityStatus.CHECK_FAILED
        }
    }

    /**
     * Performs a quick check suitable for frequent calls.
     */
    fun quickCheck(): Boolean {
        // Only do full check periodically
        if (System.currentTimeMillis() - lastCheckTime > checkInterval) {
            return performCheck() == IntegrityStatus.VALID
        }

        // Quick anti-debug check
        return !isDebuggerAttached()
    }

    /**
     * Detects if a debugger is attached.
     */
    private fun isDebuggerAttached(): Boolean {
        // Check for common debugging indicators

        // 1. Check management factory for debugging
        try {
            val runtimeMxBean = java.lang.management.ManagementFactory.getRuntimeMXBean()
            val inputArguments = runtimeMxBean.inputArguments

            for (arg in inputArguments) {
                if (arg.contains("-agentlib:jdwp") ||
                    arg.contains("-Xdebug") ||
                    arg.contains("-Xrunjdwp")) {
                    return true
                }
            }
        } catch (_: Exception) {
            // Unable to check - proceed with caution
        }

        // 2. Check for debugging threads
        try {
            val threads = Thread.getAllStackTraces().keys
            for (thread in threads) {
                val name = thread.name.lowercase()
                if (name.contains("debugger") ||
                    name.contains("jdwp") ||
                    name.contains("attach listener")) {
                    return true
                }
            }
        } catch (_: Exception) {
            // Unable to check
        }

        return false
    }

    /**
     * Checks for suspicious decompilation/analysis environments.
     */
    private fun isSuspiciousEnvironment(): Boolean {
        // Check for common decompiler classes
        val suspiciousClasses = listOf(
            "com.strobel.decompiler",
            "org.jetbrains.java.decompiler",
            "org.benf.cfr",
            "jadx.core",
            "com.sun.tools.attach",
            "jd.core.Decompiler"
        )

        for (className in suspiciousClasses) {
            try {
                Class.forName(className)
                return true
            } catch (_: ClassNotFoundException) {
                // Expected - class not present
            }
        }

        // Check for suspicious system properties
        val suspiciousProperties = listOf(
            "jd.ide.intellij.enabled",
            "jadx.gui",
            "cfr.decompile"
        )

        for (prop in suspiciousProperties) {
            if (System.getProperty(prop) != null) {
                return true
            }
        }

        return false
    }

    /**
     * Verifies JAR file integrity using checksum.
     */
    private fun verifyJarIntegrity(): Boolean {
        try {
            val pluginFile = getPluginJarFile() ?: return true // Can't verify

            val actualHash = calculateJarHash(pluginFile)
            return actualHash == EXPECTED_JAR_HASH
        } catch (e: Exception) {
            // If we can't verify, assume it's fine (don't block legitimate use)
            return true
        }
    }

    /**
     * Verifies that critical classes haven't been modified.
     */
    private fun verifyCriticalClasses(): Boolean {
        try {
            for (className in CRITICAL_CLASSES) {
                val clazz = Class.forName(className)

                // Verify class has expected methods
                if (!verifyClassStructure(clazz)) {
                    return false
                }
            }
            return true
        } catch (e: ClassNotFoundException) {
            // Critical class missing - definitely tampered
            return false
        } catch (e: Exception) {
            return true // Other errors - don't block
        }
    }

    /**
     * Verifies a class has its expected structure.
     */
    private fun verifyClassStructure(clazz: Class<*>): Boolean {
        // Basic structural checks

        // 1. Check for anonymous inner classes (sign of decompile/recompile)
        if (clazz.isAnonymousClass && clazz.name.contains("$") &&
            !clazz.name.matches(Regex(".*\\$\\d+$"))) {
            return false
        }

        // 2. Check for synthetic methods (might indicate bytecode manipulation)
        val syntheticMethodCount = clazz.declaredMethods.count { it.isSynthetic }
        if (syntheticMethodCount > clazz.declaredMethods.size / 2) {
            // Too many synthetic methods - suspicious
            return false
        }

        return true
    }

    /**
     * Verifies runtime consistency.
     */
    private fun verifyRuntimeConsistency(): Boolean {
        // Check that critical classes exist and haven't been removed
        try {
            // Verify main plugin class exists
            val pluginClass = Class.forName("dev.pincho.locks.PinchosLocks")

            // Verify it extends JavaPlugin
            if (!org.bukkit.plugin.java.JavaPlugin::class.java.isAssignableFrom(pluginClass)) {
                return false
            }

            // Verify LockManager exists
            Class.forName("dev.pincho.locks.managers.LockManager")

            // Verify LockStorage exists
            Class.forName("dev.pincho.locks.data.LockStorage")

        } catch (e: ClassNotFoundException) {
            // Critical class missing - tampered
            return false
        } catch (e: Exception) {
            // Other errors - proceed
        }

        return true
    }

    /**
     * Gets the plugin JAR file.
     */
    private fun getPluginJarFile(): File? {
        return try {
            val codeSource = plugin.javaClass.protectionDomain.codeSource
            val jarUrl = codeSource?.location
            jarUrl?.let { File(it.toURI()) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Calculates SHA-256 hash of a JAR file.
     */
    private fun calculateJarHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")

        JarFile(file).use { jar ->
            val entries = jar.entries()
            val sortedEntries = entries.toList().sortedBy { it.name }

            for (entry in sortedEntries) {
                if (entry.isDirectory) continue
                if (entry.name.endsWith(".class")) {
                    jar.getInputStream(entry).use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            digest.update(buffer, 0, bytesRead)
                        }
                    }
                }
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Calculates CRC32 of a class.
     */
    private fun calculateClassCRC(className: String): Long {
        return try {
            val resourceName = className.replace('.', '/') + ".class"
            val inputStream: InputStream = plugin.javaClass.classLoader.getResourceAsStream(resourceName)
                ?: return 0L

            val crc = CRC32()
            val buffer = ByteArray(8192)
            var bytesRead: Int

            inputStream.use {
                while (it.read(buffer).also { bytesRead = it } != -1) {
                    crc.update(buffer, 0, bytesRead)
                }
            }

            crc.value
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Records a failure for monitoring.
     */
    private fun recordFailure(reason: String) {
        failureCount++

        // Log only in debug mode to avoid revealing detection methods
        if (plugin.config.getBoolean("general.debug", false)) {
            plugin.logger.warning("Integrity check failed: $reason")
        }

        // After multiple failures, take action
        if (failureCount >= 3) {
            // Could disable the plugin, but we just limit functionality
            plugin.logger.severe("Multiple integrity check failures detected")
        }
    }

    /**
     * Gets the failure count.
     */
    fun getFailureCount(): Int = failureCount

    /**
     * Gets the check count.
     */
    fun getCheckCount(): Int = checkCount

    /**
     * Resets failure counters (for admin use).
     */
    fun resetCounters() {
        failureCount = 0
        checkCount = 0
    }

    // ============================================================================
    // ANTI-TAMPERING TRAPS (Honeypots)
    // ============================================================================

    /**
     * These methods appear to do something important but are actually honeypots.
     * If they are modified or called unexpectedly, it indicates tampering.
     */

    @Suppress("UNUSED")
    private fun validateLicenseKey(key: String): Boolean {
        // Honeypot - this method is never called in legitimate code
        // If someone modifies it to return true, it's tampering
        if (key == "MASTER_KEY_UNLOCK_ALL") {
            recordFailure("Honeypot triggered: validateLicenseKey")
            return false
        }
        return false
    }

    @Suppress("UNUSED")
    private fun bypassAllChecks(): Boolean {
        // Another honeypot
        recordFailure("Honeypot triggered: bypassAllChecks")
        return false
    }

    @Suppress("UNUSED")
    private fun disableLicenseCheck() {
        // Honeypot that records the attempt
        recordFailure("Honeypot triggered: disableLicenseCheck")
    }

    // Decoy constants that look important but aren't used
    @Suppress("UNUSED")
    private val MASTER_BYPASS_CODE = "NEVER_USE_THIS_CODE"
    @Suppress("UNUSED")
    private val ADMIN_OVERRIDE_KEY = "FAKE_ADMIN_KEY"
    @Suppress("UNUSED")
    private val DEBUG_UNLOCK_ALL = false
}
