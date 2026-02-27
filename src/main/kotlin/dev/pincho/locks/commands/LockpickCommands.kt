package dev.pincho.locks.commands

import dev.pincho.locks.PinchosLocks
import dev.pincho.locks.config.ConfigManager
import dev.pincho.locks.models.LockpickTier
import dev.pincho.locks.utils.ItemBuilder
import dev.pincho.locks.utils.MessageUtils
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * Handles all /lockpick commands.
 */
class LockpickCommands(
    private val plugin: PinchosLocks,
    private val config: ConfigManager,
    private val messages: MessageUtils
) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "help", "?" -> showHelp(sender)
            "give" -> handleGive(sender, args)
            else -> showHelp(sender)
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.isEmpty()) return emptyList()

        return when (args.size) {
            1 -> {
                val subcommands = mutableListOf("help")
                if (sender.hasPermission("pinchoslocks.admin")) {
                    subcommands.add("give")
                }
                subcommands.filter { it.startsWith(args[0].lowercase()) }
            }
            2 -> {
                when (args[0].lowercase()) {
                    "give" -> Bukkit.getOnlinePlayers().map { it.name }
                        .filter { it.lowercase().startsWith(args[1].lowercase()) }
                    else -> emptyList()
                }
            }
            3 -> {
                when (args[0].lowercase()) {
                    "give" -> LockpickTier.names().filter { it.startsWith(args[2].lowercase()) }
                    else -> emptyList()
                }
            }
            4 -> {
                when (args[0].lowercase()) {
                    "give" -> listOf("1", "5", "10", "32", "64")
                        .filter { it.startsWith(args[3]) }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }

    /**
     * Shows help message.
     */
    private fun showHelp(sender: CommandSender) {
        messages.sendRaw(sender, "lockpick.help.header")
        messages.sendRaw(sender, "lockpick.help.lockpick")

        if (sender.hasPermission("pinchoslocks.admin")) {
            messages.sendRaw(sender, "lockpick.help.give")
        }

        messages.sendRaw(sender, "lockpick.help.footer")
    }

    /**
     * Handles /lockpick give <player> <tier> [amount]
     */
    private fun handleGive(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pinchoslocks.admin")) {
            messages.send(sender, "general.no-permission")
            return
        }

        if (args.size < 3) {
            messages.sendRaw(sender, "lockpick.help.give")
            return
        }

        val targetPlayer = Bukkit.getPlayer(args[1])
        if (targetPlayer == null) {
            messages.send(sender, "general.player-not-found", mapOf("player" to args[1]))
            return
        }

        val tier = LockpickTier.fromString(args[2])
        if (tier == null) {
            messages.send(sender, "lockpick.invalid-tier")
            return
        }

        val amount = if (args.size > 3) {
            args[3].toIntOrNull()?.coerceIn(1, 64) ?: run {
                messages.send(sender, "general.invalid-amount")
                return
            }
        } else 1

        val lockpickItem = createLockpickItem(tier, amount)
        val remaining = targetPlayer.inventory.addItem(lockpickItem)

        if (remaining.isNotEmpty()) {
            remaining.values.forEach {
                targetPlayer.world.dropItemNaturally(targetPlayer.location, it)
            }
        }

        messages.send(sender, "lockpick.give-success", mapOf(
            "amount" to amount,
            "tier" to tier.displayName,
            "player" to targetPlayer.name
        ))
    }

    /**
     * Creates a lockpick item of the specified tier.
     */
    fun createLockpickItem(tier: LockpickTier, amount: Int = 1): ItemStack {
        val tierConfig = config.getLockpickConfig(tier)
        val tierKey = tier.name.lowercase()

        // Get translated name for this tier
        val translatedName = messages.getRaw("items.lockpick.name.$tierKey")

        // Get translated tier display name
        val translatedTierName = messages.getRaw("items.tiers.lockpick.$tierKey")

        // Get and process lore with placeholders
        val lore = messages.getList("items.lockpick.lore").map { line ->
            line.replace("{tier}", translatedTierName)
                .replace("{uses}", tierConfig.durability.toString())
                .replace("{bonus}", tierConfig.successModifier.toString())
        }

        return ItemBuilder.of(tierConfig.material)
            .amount(amount)
            .name(translatedName)
            .loreStrings(lore)
            .customModelData(tier.defaultCustomModelData)
            .persistentString(plugin, "lockpick_tier", tier.name)
            .persistentInt(plugin, "lockpick_uses", tierConfig.durability)
            .build()
    }

    /**
     * Checks if an item is a lockpick.
     */
    fun isLockpick(item: ItemStack?): Boolean {
        if (item == null) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(
            NamespacedKey(plugin, "lockpick_tier"),
            PersistentDataType.STRING
        )
    }

    /**
     * Gets the tier of a lockpick item.
     */
    fun getLockpickTier(item: ItemStack): LockpickTier? {
        val meta = item.itemMeta ?: return null
        val tierName = meta.persistentDataContainer.get(
            NamespacedKey(plugin, "lockpick_tier"),
            PersistentDataType.STRING
        ) ?: return null
        return LockpickTier.fromString(tierName)
    }

    /**
     * Gets the remaining uses of a lockpick.
     */
    fun getLockpickUses(item: ItemStack): Int {
        val meta = item.itemMeta ?: return 0
        return meta.persistentDataContainer.get(
            NamespacedKey(plugin, "lockpick_uses"),
            PersistentDataType.INTEGER
        ) ?: 0
    }

    /**
     * Decrements the uses of a lockpick and returns if it broke.
     */
    fun useLockpick(item: ItemStack): Boolean {
        val meta = item.itemMeta ?: return true
        val currentUses = meta.persistentDataContainer.get(
            NamespacedKey(plugin, "lockpick_uses"),
            PersistentDataType.INTEGER
        ) ?: return true

        val newUses = currentUses - 1
        if (newUses <= 0) {
            return true // Lockpick broke
        }

        meta.persistentDataContainer.set(
            NamespacedKey(plugin, "lockpick_uses"),
            PersistentDataType.INTEGER,
            newUses
        )

        // Update lore with new uses using translations
        val tier = getLockpickTier(item) ?: return true
        val tierConfig = config.getLockpickConfig(tier)
        val tierKey = tier.name.lowercase()

        // Get translated tier display name
        val translatedTierName = messages.getRaw("items.tiers.lockpick.$tierKey")

        // Get and process lore with updated uses
        val lore = messages.getList("items.lockpick.lore").map { line ->
            line.replace("{tier}", translatedTierName)
                .replace("{uses}", newUses.toString())
                .replace("{bonus}", tierConfig.successModifier.toString())
        }

        @Suppress("DEPRECATION")
        meta.lore = lore.map { ItemBuilder.parseLegacy(it) }
        item.itemMeta = meta

        return false // Lockpick still usable
    }
}
