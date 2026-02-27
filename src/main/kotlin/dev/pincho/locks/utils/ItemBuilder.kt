package dev.pincho.locks.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

/**
 * Fluent builder for creating ItemStacks with various properties.
 * Compatible with Bukkit, Spigot and Paper servers.
 */
class ItemBuilder(private val material: Material) {

    private var amount: Int = 1
    private var displayName: String? = null
    private var lore: MutableList<String> = mutableListOf()
    private var customModelData: Int? = null
    private var unbreakable: Boolean = false
    private var flags: MutableSet<ItemFlag> = mutableSetOf()
    private var enchantments: MutableMap<Enchantment, Int> = mutableMapOf()
    private var persistentData: MutableMap<NamespacedKey, Pair<PersistentDataType<*, *>, Any>> = mutableMapOf()
    private var glowing: Boolean = false

    companion object {
        private val miniMessage = MiniMessage.miniMessage()
        private val legacySerializer = LegacyComponentSerializer.legacySection()

        /**
         * Creates a new ItemBuilder with the specified material.
         */
        fun of(material: Material): ItemBuilder = ItemBuilder(material)

        /**
         * Creates an ItemBuilder from an existing ItemStack.
         */
        fun from(item: ItemStack): ItemBuilder {
            val builder = ItemBuilder(item.type)
            builder.amount = item.amount

            item.itemMeta?.let { meta ->
                @Suppress("DEPRECATION")
                if (meta.hasDisplayName()) {
                    builder.displayName = meta.displayName
                }
                @Suppress("DEPRECATION")
                if (meta.hasLore()) {
                    builder.lore = meta.lore?.toMutableList() ?: mutableListOf()
                }
                if (meta.hasCustomModelData()) {
                    builder.customModelData = meta.customModelData
                }
                builder.unbreakable = meta.isUnbreakable
                builder.flags.addAll(meta.itemFlags)
            }

            return builder
        }

        /**
         * Parses a MiniMessage string to a Component.
         */
        fun parse(text: String): Component = miniMessage.deserialize(text)

        /**
         * Converts a Component to legacy string format for Spigot compatibility.
         */
        fun toLegacy(component: Component): String = legacySerializer.serialize(component)

        /**
         * Parses MiniMessage and converts to legacy string.
         */
        fun parseLegacy(text: String): String = toLegacy(parse(text))
    }

    /**
     * Sets the amount of items.
     */
    fun amount(amount: Int): ItemBuilder {
        this.amount = amount.coerceIn(1, 64)
        return this
    }

    /**
     * Sets the display name using a Component.
     */
    fun name(name: Component): ItemBuilder {
        this.displayName = toLegacy(name)
        return this
    }

    /**
     * Sets the display name using a MiniMessage string.
     */
    fun name(name: String): ItemBuilder {
        this.displayName = parseLegacy(name)
        return this
    }

    /**
     * Sets the display name using a raw legacy string.
     */
    fun nameLegacy(name: String): ItemBuilder {
        this.displayName = name
        return this
    }

    /**
     * Sets the lore from a list of Components.
     */
    fun lore(lore: List<Component>): ItemBuilder {
        this.lore = lore.map { toLegacy(it) }.toMutableList()
        return this
    }

    /**
     * Sets the lore from a list of MiniMessage strings.
     */
    fun loreStrings(lore: List<String>): ItemBuilder {
        this.lore = lore.map { parseLegacy(it) }.toMutableList()
        return this
    }

    /**
     * Sets the lore from a list of legacy strings.
     */
    fun loreLegacy(lore: List<String>): ItemBuilder {
        this.lore = lore.toMutableList()
        return this
    }

    /**
     * Adds a single lore line.
     */
    fun addLore(line: Component): ItemBuilder {
        this.lore.add(toLegacy(line))
        return this
    }

    /**
     * Adds a single lore line from a MiniMessage string.
     */
    fun addLore(line: String): ItemBuilder {
        this.lore.add(parseLegacy(line))
        return this
    }

    /**
     * Adds a single lore line from a legacy string.
     */
    fun addLoreLegacy(line: String): ItemBuilder {
        this.lore.add(line)
        return this
    }

    /**
     * Sets the custom model data.
     */
    fun customModelData(data: Int): ItemBuilder {
        this.customModelData = data
        return this
    }

    /**
     * Makes the item unbreakable.
     */
    fun unbreakable(unbreakable: Boolean = true): ItemBuilder {
        this.unbreakable = unbreakable
        return this
    }

    /**
     * Adds item flags.
     */
    fun flags(vararg flags: ItemFlag): ItemBuilder {
        this.flags.addAll(flags)
        return this
    }

    /**
     * Hides all item attributes.
     */
    fun hideAttributes(): ItemBuilder {
        this.flags.addAll(ItemFlag.entries)
        return this
    }

    /**
     * Adds an enchantment.
     */
    fun enchant(enchantment: Enchantment, level: Int): ItemBuilder {
        this.enchantments[enchantment] = level
        return this
    }

    /**
     * Makes the item glow without showing enchantments.
     */
    fun glow(glow: Boolean = true): ItemBuilder {
        this.glowing = glow
        return this
    }

    /**
     * Adds persistent data to the item.
     */
    fun <T, Z : Any> persistentData(
        plugin: JavaPlugin,
        key: String,
        type: PersistentDataType<T, Z>,
        value: Z
    ): ItemBuilder {
        val namespacedKey = NamespacedKey(plugin, key)
        @Suppress("UNCHECKED_CAST")
        this.persistentData[namespacedKey] = (type to value) as Pair<PersistentDataType<*, *>, Any>
        return this
    }

    /**
     * Adds a string persistent data.
     */
    fun persistentString(plugin: JavaPlugin, key: String, value: String): ItemBuilder {
        return persistentData(plugin, key, PersistentDataType.STRING, value)
    }

    /**
     * Adds an integer persistent data.
     */
    fun persistentInt(plugin: JavaPlugin, key: String, value: Int): ItemBuilder {
        return persistentData(plugin, key, PersistentDataType.INTEGER, value)
    }

    /**
     * Builds the ItemStack with all configured properties.
     * Compatible with Bukkit, Spigot and Paper.
     */
    fun build(): ItemStack {
        val item = ItemStack(material, amount)
        val meta = item.itemMeta ?: return item

        @Suppress("DEPRECATION")
        displayName?.let { meta.setDisplayName(it) }

        @Suppress("DEPRECATION")
        if (lore.isNotEmpty()) {
            meta.lore = lore
        }

        customModelData?.let { meta.setCustomModelData(it) }

        meta.isUnbreakable = unbreakable

        flags.forEach { meta.addItemFlags(it) }

        enchantments.forEach { (enchant, level) ->
            meta.addEnchant(enchant, level, true)
        }

        // For glow effect, add a dummy enchant and hide it
        if (glowing && enchantments.isEmpty()) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true)
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        }

        persistentData.forEach { (key, pair) ->
            @Suppress("UNCHECKED_CAST")
            val type = pair.first as PersistentDataType<Any, Any>
            meta.persistentDataContainer.set(key, type, pair.second)
        }

        item.itemMeta = meta
        return item
    }
}

/**
 * Extension function to quickly create an ItemBuilder from a Material.
 */
fun Material.toItemBuilder(): ItemBuilder = ItemBuilder.of(this)
