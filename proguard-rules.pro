# ═══════════════════════════════════════════════════════════════════════════════
#
#   PINCHO'S LOCKS - PROGUARD CONFIGURATION (OPTIMIZED)
#
#   Author: MrSingu
#   Version: 1.0.0
#   ProGuard: 7.4.2+
#
#   Compatible with: Paper/Spigot 1.21+
#   This configuration provides maximum obfuscation while maintaining
#   full plugin functionality including serialization and events.
#
# ═══════════════════════════════════════════════════════════════════════════════

# ─── GENERAL OPTIONS ──────────────────────────────────────────────────────────

# CRITICAL: Disable shrinking to prevent removal of needed classes
-dontshrink

# CRITICAL: Disable optimization to prevent VerifyError with Kotlin coroutines
# Optimization can corrupt bytecode for kotlinx.serialization and coroutines
-dontoptimize

# Target Java 21 bytecode
-target 21

# Don't use mixed case for Windows compatibility
-dontusemixedcaseclassnames

# Don't skip non-public library class members
-dontskipnonpubliclibraryclassmembers

# Process attributes needed for proper operation
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,*Annotation*,EnclosingMethod

# ─── OBFUSCATION SETTINGS ─────────────────────────────────────────────────────

# Repackage all classes into a single package
-repackageclasses 'k'

# Flatten package hierarchy for maximum obfuscation
-flattenpackagehierarchy 'k'

# CRITICAL: Use unique class member names instead of overloadaggressively
# This prevents "Duplicate key" errors in Paper 1.21+
-useuniqueclassmembernames

# DISABLED: These options cause VerifyError with Kotlin coroutines
# -allowaccessmodification (breaks sealed classes)
# -mergeinterfacesaggressively (breaks type hierarchy)

# Use obfuscation dictionaries for confusing names
-obfuscationdictionary proguard-dictionary.txt
-classobfuscationdictionary proguard-dictionary.txt
-packageobfuscationdictionary proguard-dictionary.txt

# ─── WARNINGS TO IGNORE ───────────────────────────────────────────────────────

# Kotlin
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn org.jetbrains.annotations.**
-dontwarn org.intellij.lang.annotations.**

# Bukkit/Spigot/Paper
-dontwarn org.bukkit.**
-dontwarn org.spigotmc.**
-dontwarn net.md_5.**
-dontwarn com.destroystokyo.paper.**

# Google libraries
-dontwarn com.google.**

# Kyori Adventure and platform dependencies
-dontwarn net.kyori.**
-dontwarn dev.pincho.locks.libs.kyori.**

# ViaVersion (optional dependency)
-dontwarn com.viaversion.**

# Netty (server runtime dependency)
-dontwarn io.netty.**

# Android annotations (not used)
-dontwarn android.annotation.**

# Java standard libraries
-dontwarn javax.annotation.**
-dontwarn java.lang.management.**
-dontwarn java.lang.instrument.**
-dontwarn sun.misc.**

# Animal Sniffer (compile-time only)
-dontwarn org.codehaus.mojo.animal_sniffer.**

# Relocated libraries - ignore all warnings from relocated packages
-dontwarn dev.pincho.locks.libs.**

# ─── KEEP BUKKIT/SPIGOT PLUGIN STRUCTURE ──────────────────────────────────────

# Main plugin class (referenced in plugin.yml) - MUST keep name
-keep public class dev.pincho.locks.PinchosLocks extends org.bukkit.plugin.java.JavaPlugin {
    public <init>();
    public void onEnable();
    public void onDisable();
}

# Keep CoroutineScope implementation
-keepclassmembers class dev.pincho.locks.PinchosLocks {
    public kotlinx.coroutines.CoroutineContext getCoroutineContext();
}

# ─── KEEP EVENT LISTENERS ─────────────────────────────────────────────────────

# Keep ALL methods with @EventHandler annotation
-keepclassmembers class * implements org.bukkit.event.Listener {
    @org.bukkit.event.EventHandler <methods>;
}

# Keep listener class constructors (needed for registration)
-keepclassmembers class * implements org.bukkit.event.Listener {
    public <init>(...);
}

# ─── KEEP COMMAND EXECUTORS ───────────────────────────────────────────────────

# Keep onCommand signature for all CommandExecutors
-keepclassmembers class * implements org.bukkit.command.CommandExecutor {
    public boolean onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[]);
}

# Keep onTabComplete signature for all TabCompleters
-keepclassmembers class * implements org.bukkit.command.TabCompleter {
    public java.util.List onTabComplete(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[]);
}

# ─── KEEP KOTLINX SERIALIZATION (CRITICAL) ────────────────────────────────────

# Keep all @Serializable classes and their generated serializers
-keep @kotlinx.serialization.Serializable class * {
    *;
}

# Keep serializer companion methods
-keepclassmembers class * {
    public static ** serializer();
    public static ** serializer(...);
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep generated serializer classes
-keep class **$$serializer {
    *;
}

# Keep KSerializer implementations
-keep class * implements kotlinx.serialization.KSerializer {
    *;
}

# CRITICAL: Keep relocated kotlinx.serialization classes
-keep class dev.pincho.locks.libs.kotlinx.serialization.** { *; }
-keep interface dev.pincho.locks.libs.kotlinx.serialization.** { *; }

# Keep models package completely (contains serializable data classes)
-keep class dev.pincho.locks.models.Lock { *; }
-keep class dev.pincho.locks.models.SerializableLocation { *; }
-keep class dev.pincho.locks.models.LockTier { *; }
-keep class dev.pincho.locks.models.LockpickTier { *; }
-keep class dev.pincho.locks.models.LockableBlock { *; }

# ─── KEEP ENUM CLASSES (CRITICAL FOR KOTLIN) ──────────────────────────────────

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static ** entries;
    <fields>;
}

# Keep enum companion objects
-keepclassmembers enum * {
    public static ** Companion;
}

# ─── KEEP KOTLIN SPECIFIC ─────────────────────────────────────────────────────

# Keep Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }

# Keep Kotlin companion objects
-keepclassmembers class * {
    public static ** Companion;
}

# Keep Kotlin intrinsics
-keep class kotlin.jvm.internal.** { *; }

# Keep Kotlin reflection
-dontwarn kotlin.reflect.jvm.internal.**

# Keep data class functions
-keepclassmembers class * {
    public ** component*();
    public ** copy(...);
    public ** copy$default(...);
}

# Keep coroutine continuation classes
-keep class kotlin.coroutines.Continuation { *; }
-keep class * implements kotlin.coroutines.Continuation { *; }

# Keep coroutine scope members
-keepclassmembers class * implements kotlinx.coroutines.CoroutineScope {
    public kotlinx.coroutines.CoroutineContext getCoroutineContext();
}

# Keep suspend functions
-keepclassmembers class * {
    ** *$suspendImpl(...);
}

# CRITICAL: Keep ALL relocated kotlinx.coroutines classes and interfaces
# This prevents VerifyError: Bad return type
-keep class dev.pincho.locks.libs.kotlinx.coroutines.** { *; }
-keep interface dev.pincho.locks.libs.kotlinx.coroutines.** { *; }

# Keep relocated Kotlin stdlib classes
-keep class dev.pincho.locks.libs.kotlin.** { *; }
-keep interface dev.pincho.locks.libs.kotlin.** { *; }

# ─── KEEP DATA STORAGE ────────────────────────────────────────────────────────

# Keep LockStorage public API for JSON operations
-keepclassmembers class dev.pincho.locks.data.LockStorage {
    public ** load(...);
    public ** save(...);
    public ** forceSave(...);
    public ** getLock(...);
    public ** addLock(...);
    public ** removeLock(...);
    public ** updateLock(...);
    public ** getLockCount();
}

# ─── KEEP CONFIG MANAGER ──────────────────────────────────────────────────────

# Keep ConfigManager for YAML configuration
-keepclassmembers class dev.pincho.locks.config.ConfigManager {
    public <init>(...);
    public void load();
    public ** get*();
}

# Keep nested config classes
-keepclassmembers class dev.pincho.locks.config.ConfigManager$* {
    *;
}

# ─── KEEP PUBLIC API (Managers) ───────────────────────────────────────────────

# Keep LockManager result classes (sealed class hierarchy)
-keep class dev.pincho.locks.managers.LockManager$LockResult { *; }
-keep class dev.pincho.locks.managers.LockManager$LockResult$* { *; }

# Keep LockpickManager result classes
-keep class dev.pincho.locks.managers.LockpickManager$LockpickResult { *; }
-keep class dev.pincho.locks.managers.LockpickManager$LockpickResult$* { *; }
-keep class dev.pincho.locks.managers.LockpickManager$LockpickSession { *; }

# ─── KEEP UTILITIES ───────────────────────────────────────────────────────────

# Keep MessageUtils for language loading
-keepclassmembers class dev.pincho.locks.utils.MessageUtils {
    public void loadLanguage(java.lang.String);
    public void send(...);
    public void sendActionBar(...);
}

# ─── KEEP SECURITY CLASSES (PUBLIC INTERFACE ONLY) ────────────────────────────

# Keep IntegrityChecker public API - internals will be obfuscated
-keep class dev.pincho.locks.security.IntegrityChecker {
    public <init>(...);
    public dev.pincho.locks.security.IntegrityChecker$IntegrityStatus performCheck();
    public boolean quickCheck();
}

# Keep IntegrityStatus enum
-keep class dev.pincho.locks.security.IntegrityChecker$IntegrityStatus {
    *;
}

# ─── KEEP BukkitRunnable TASKS ────────────────────────────────────────────────

-keepclassmembers class * extends org.bukkit.scheduler.BukkitRunnable {
    public void run();
}

# ─── KEEP BUKKIT SCHEDULER RUNNABLES ──────────────────────────────────────────

-keepclassmembers class * implements java.lang.Runnable {
    public void run();
}

# ─── REFLECTION PROTECTION ────────────────────────────────────────────────────

# Keep class names used in reflection (IntegrityChecker)
-keepnames class dev.pincho.locks.PinchosLocks
-keepnames class dev.pincho.locks.security.IntegrityChecker
-keepnames class dev.pincho.locks.managers.LockManager
-keepnames class dev.pincho.locks.data.LockStorage

# ─── OPTIMIZATION OPTIONS ─────────────────────────────────────────────────────

# Optimization is disabled (-dontoptimize) to prevent VerifyError
# These options are kept for reference if optimization is re-enabled:
# -optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# ─── AGGRESSIVE OBFUSCATION FOR SECURITY CODE ─────────────────────────────────

# Allow obfuscation of IntegrityChecker private methods
# The public interface is kept, but internal methods are obfuscated
# Methods like isDebuggerAttached, isSuspiciousEnvironment, etc. WILL be obfuscated

# ─── REMOVE LOGGING IN PRODUCTION (OPTIONAL) ──────────────────────────────────

# Uncomment to remove debug logging
#-assumenosideeffects class java.util.logging.Logger {
#    public void fine(...);
#    public void finer(...);
#    public void finest(...);
#}

# ═══════════════════════════════════════════════════════════════════════════════
#                              END OF PROGUARD RULES
# ═══════════════════════════════════════════════════════════════════════════════
