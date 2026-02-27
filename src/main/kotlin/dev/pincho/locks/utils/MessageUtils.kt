package dev.pincho.locks.utils

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles message loading and formatting for the plugin.
 * Supports MiniMessage format and placeholder replacement.
 * Compatible with Bukkit, Spigot and Paper servers.
 */
class MessageUtils(private val plugin: JavaPlugin) {

    private val miniMessage = MiniMessage.miniMessage()
    private val messages = ConcurrentHashMap<String, String>()
    private var prefix: Component = Component.empty()
    private var currentLanguage: String = "es_ES"

    // BukkitAudiences for cross-platform Adventure support
    private lateinit var audiences: BukkitAudiences

    /**
     * Initializes the Adventure audiences.
     * Must be called on plugin enable.
     */
    fun initialize() {
        audiences = BukkitAudiences.create(plugin)
    }

    /**
     * Closes the Adventure audiences.
     * Must be called on plugin disable.
     */
    fun close() {
        if (::audiences.isInitialized) {
            audiences.close()
        }
    }

    /**
     * Loads messages from the specified language file.
     * @param language The language code (e.g., "es_ES", "en_EN")
     */
    fun loadLanguage(language: String) {
        currentLanguage = language
        messages.clear()

        // Try to load from plugin data folder first
        val langFile = File(plugin.dataFolder, "lang/$language.yml")

        val config = if (langFile.exists()) {
            YamlConfiguration.loadConfiguration(langFile)
        } else {
            // Fall back to embedded resource
            val resource = plugin.getResource("lang/$language.yml")
                ?: plugin.getResource("lang/en_EN.yml")
                ?: run {
                    plugin.logger.warning("Could not load language file: $language")
                    return
                }

            YamlConfiguration.loadConfiguration(InputStreamReader(resource))
        }

        // Recursively load all messages
        loadMessagesRecursive(config, "")

        // Load prefix separately
        val prefixString = messages["prefix"] ?: ""
        prefix = miniMessage.deserialize(prefixString)

        plugin.logger.info("Loaded ${messages.size} messages for language: $language")
    }

    /**
     * Recursively loads messages from a configuration section.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun loadMessagesRecursive(config: YamlConfiguration, path: String) {
        for (key in config.getKeys(true)) {
            val value = config.get(key)
            if (value is String) {
                messages[key] = value
            } else if (value is List<*>) {
                // Join list values with newlines for multi-line messages
                messages[key] = value.filterIsInstance<String>().joinToString("\n")
            }
        }
    }

    /**
     * Gets a raw message string by key.
     */
    fun getRaw(key: String): String {
        return messages[key] ?: "<red>Missing message: $key</red>"
    }

    /**
     * Gets a formatted Component by key with placeholder support.
     * @param key The message key
     * @param placeholders Map of placeholder names to their values
     */
    fun get(key: String, placeholders: Map<String, Any> = emptyMap()): Component {
        var message = getRaw(key)

        // Replace simple {placeholder} style placeholders
        placeholders.forEach { (placeholder, value) ->
            message = message.replace("{$placeholder}", value.toString())
        }

        return miniMessage.deserialize(message)
    }

    /**
     * Gets a formatted Component with prefix.
     */
    fun getWithPrefix(key: String, placeholders: Map<String, Any> = emptyMap()): Component {
        return prefix.append(get(key, placeholders))
    }

    /**
     * Gets a Component with MiniMessage tag resolvers.
     */
    fun getWithResolvers(key: String, vararg resolvers: TagResolver): Component {
        val message = getRaw(key)
        return miniMessage.deserialize(message, *resolvers)
    }

    /**
     * Sends a message to a CommandSender with prefix.
     * Uses Adventure Platform for cross-server compatibility.
     */
    fun send(sender: CommandSender, key: String, placeholders: Map<String, Any> = emptyMap()) {
        audiences.sender(sender).sendMessage(getWithPrefix(key, placeholders))
    }

    /**
     * Sends a message to a CommandSender without prefix.
     * Uses Adventure Platform for cross-server compatibility.
     */
    fun sendRaw(sender: CommandSender, key: String, placeholders: Map<String, Any> = emptyMap()) {
        audiences.sender(sender).sendMessage(get(key, placeholders))
    }

    /**
     * Sends a message to a Player with an action bar.
     * Uses Adventure Platform for cross-server compatibility.
     */
    fun sendActionBar(player: Player, key: String, placeholders: Map<String, Any> = emptyMap()) {
        audiences.player(player).sendActionBar(get(key, placeholders))
    }

    /**
     * Sends a raw component to a sender.
     */
    fun sendComponent(sender: CommandSender, component: Component) {
        audiences.sender(sender).sendMessage(component)
    }

    /**
     * Parses a raw MiniMessage string to a Component.
     */
    fun parse(text: String): Component = miniMessage.deserialize(text)

    /**
     * Parses a raw MiniMessage string with placeholders.
     */
    fun parse(text: String, placeholders: Map<String, Any>): Component {
        var message = text
        placeholders.forEach { (placeholder, value) ->
            message = message.replace("{$placeholder}", value.toString())
        }
        return miniMessage.deserialize(message)
    }

    /**
     * Gets a list of strings from a message key.
     */
    fun getList(key: String): List<String> {
        return getRaw(key).split("\n")
    }

    /**
     * Gets a list of Components from a message key.
     */
    fun getComponentList(key: String, placeholders: Map<String, Any> = emptyMap()): List<Component> {
        return getList(key).map { line ->
            var processed = line
            placeholders.forEach { (placeholder, value) ->
                processed = processed.replace("{$placeholder}", value.toString())
            }
            miniMessage.deserialize(processed)
        }
    }

    /**
     * Gets the current prefix as a Component.
     */
    fun getPrefix(): Component = prefix

    /**
     * Gets the currently loaded language.
     */
    fun getCurrentLanguage(): String = currentLanguage

    /**
     * Checks if a message key exists.
     */
    fun hasMessage(key: String): Boolean = messages.containsKey(key)

    /**
     * Reloads the current language.
     */
    fun reload() {
        loadLanguage(currentLanguage)
    }

    /**
     * Gets the BukkitAudiences instance.
     */
    fun getAudiences(): BukkitAudiences = audiences

    companion object {
        /**
         * Creates a placeholder for MiniMessage.
         */
        fun placeholder(key: String, value: Any): TagResolver {
            return Placeholder.parsed(key, value.toString())
        }

        /**
         * Creates a component placeholder for MiniMessage.
         */
        fun componentPlaceholder(key: String, value: Component): TagResolver {
            return Placeholder.component(key, value)
        }
    }
}

/**
 * Extension function to send a formatted message to a player.
 */
fun CommandSender.sendFormatted(message: String) {
    sendMessage(MiniMessage.miniMessage().deserialize(message).toString())
}

/**
 * Extension function to send a formatted message with placeholders.
 */
fun CommandSender.sendFormatted(message: String, placeholders: Map<String, Any>) {
    var processed = message
    placeholders.forEach { (key, value) ->
        processed = processed.replace("{$key}", value.toString())
    }
    sendMessage(MiniMessage.miniMessage().deserialize(processed).toString())
}
