package dev.pincho.locks.commands

import dev.pincho.locks.PinchosLocks
import dev.pincho.locks.config.ConfigManager
import dev.pincho.locks.managers.LockManager
import dev.pincho.locks.models.Lock
import dev.pincho.locks.models.LockTier
import dev.pincho.locks.utils.MessageUtils
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Handles all /lock commands and subcommands.
 */
class LockCommands(
    private val plugin: PinchosLocks,
    private val lockManager: LockManager,
    private val config: ConfigManager,
    private val messages: MessageUtils
) : CommandExecutor, TabCompleter {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .withZone(ZoneId.systemDefault())

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
            "info" -> handleInfo(sender)
            "remove" -> handleRemove(sender)
            "trust" -> handleTrust(sender, args)
            "untrust" -> handleUntrust(sender, args)
            "trustlist" -> handleTrustList(sender)
            "reload" -> handleReload(sender)
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
                val subcommands = mutableListOf("help", "info", "remove", "trust", "untrust", "trustlist")
                if (sender.hasPermission("pinchoslocks.admin")) {
                    subcommands.addAll(listOf("give", "reload"))
                }
                subcommands.filter { it.startsWith(args[0].lowercase()) }
            }
            2 -> {
                when (args[0].lowercase()) {
                    "give" -> Bukkit.getOnlinePlayers().map { it.name }
                        .filter { it.lowercase().startsWith(args[1].lowercase()) }
                    "trust", "untrust" -> Bukkit.getOnlinePlayers().map { it.name }
                        .filter { it.lowercase().startsWith(args[1].lowercase()) }
                    else -> emptyList()
                }
            }
            3 -> {
                when (args[0].lowercase()) {
                    "give" -> LockTier.names().filter { it.startsWith(args[2].lowercase()) }
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
        messages.sendRaw(sender, "help.header")
        messages.sendRaw(sender, "help.lock")
        messages.sendRaw(sender, "help.info")
        messages.sendRaw(sender, "help.remove")
        messages.sendRaw(sender, "help.trust")
        messages.sendRaw(sender, "help.untrust")
        messages.sendRaw(sender, "help.trustlist")

        if (sender.hasPermission("pinchoslocks.admin")) {
            messages.sendRaw(sender, "help.give")
            messages.sendRaw(sender, "help.reload")
        }

        messages.sendRaw(sender, "help.footer")
    }

    /**
     * Handles /lock give <player> <tier> [amount]
     */
    private fun handleGive(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pinchoslocks.admin")) {
            messages.send(sender, "general.no-permission")
            return
        }

        if (args.size < 3) {
            messages.sendRaw(sender, "help.give")
            return
        }

        val targetPlayer = Bukkit.getPlayer(args[1])
        if (targetPlayer == null) {
            messages.send(sender, "general.player-not-found", mapOf("player" to args[1]))
            return
        }

        val tier = LockTier.fromString(args[2])
        if (tier == null) {
            messages.send(sender, "general.invalid-tier")
            return
        }

        val amount = if (args.size > 3) {
            args[3].toIntOrNull()?.coerceIn(1, 64) ?: run {
                messages.send(sender, "general.invalid-amount")
                return
            }
        } else 1

        val lockItem = lockManager.createLockItem(tier, amount)
        val remaining = targetPlayer.inventory.addItem(lockItem)

        if (remaining.isNotEmpty()) {
            remaining.values.forEach {
                targetPlayer.world.dropItemNaturally(targetPlayer.location, it)
            }
        }

        messages.send(sender, "admin.give-success", mapOf(
            "amount" to amount,
            "item" to "${tier.displayName} Lock",
            "player" to targetPlayer.name
        ))
    }

    /**
     * Handles /lock info
     */
    private fun handleInfo(sender: CommandSender) {
        if (sender !is Player) {
            messages.send(sender, "general.player-only")
            return
        }

        if (!sender.hasPermission("pinchoslocks.info")) {
            messages.send(sender, "general.no-permission")
            return
        }

        val block = sender.getTargetBlockExact(5)
        if (block == null) {
            messages.send(sender, "lock.not-looking")
            return
        }

        val lock = lockManager.getLock(block)
        if (lock == null) {
            messages.send(sender, "lock.not-locked")
            return
        }

        displayLockInfo(sender, lock)
    }

    /**
     * Displays lock information to a player.
     */
    private fun displayLockInfo(player: Player, lock: Lock) {
        messages.sendRaw(player, "lock.info.header")

        messages.sendRaw(player, "lock.info.owner", mapOf("owner" to lock.ownerName))
        messages.sendRaw(player, "lock.info.tier", mapOf("tier" to lock.getTier().displayName))

        val date = dateFormatter.format(lock.getCreatedAt())
        messages.sendRaw(player, "lock.info.created", mapOf("date" to date))

        val trustedCount = lock.trustedPlayers.size
        messages.sendRaw(player, "lock.info.trusted", mapOf("trusted" to trustedCount.toString()))

        val loc = lock.location
        messages.sendRaw(player, "lock.info.location", mapOf(
            "world" to loc.world,
            "x" to loc.x.toString(),
            "y" to loc.y.toString(),
            "z" to loc.z.toString()
        ))

        messages.sendRaw(player, "lock.info.footer")
    }

    /**
     * Handles /lock remove
     */
    private fun handleRemove(sender: CommandSender) {
        if (sender !is Player) {
            messages.send(sender, "general.player-only")
            return
        }

        if (!sender.hasPermission("pinchoslocks.use")) {
            messages.send(sender, "general.no-permission")
            return
        }

        val block = sender.getTargetBlockExact(5)
        if (block == null) {
            messages.send(sender, "lock.not-looking")
            return
        }

        val result = lockManager.removeLock(sender, block)

        when (result) {
            is LockManager.LockResult.Success -> {
                messages.send(sender, result.messageKey, result.placeholders)
            }
            is LockManager.LockResult.Failure -> {
                messages.send(sender, result.messageKey, result.placeholders)
            }
        }
    }

    /**
     * Handles /lock trust <player>
     */
    private fun handleTrust(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            messages.send(sender, "general.player-only")
            return
        }

        if (!sender.hasPermission("pinchoslocks.trust")) {
            messages.send(sender, "general.no-permission")
            return
        }

        if (args.size < 2) {
            messages.sendRaw(sender, "help.trust")
            return
        }

        val block = sender.getTargetBlockExact(5)
        if (block == null) {
            messages.send(sender, "lock.not-looking")
            return
        }

        val lock = lockManager.getLock(block)
        if (lock == null) {
            messages.send(sender, "lock.not-locked")
            return
        }

        if (!lock.isOwner(sender.uniqueId) && !sender.hasPermission("pinchoslocks.admin")) {
            messages.send(sender, "lock.not-owner")
            return
        }

        // Get target player UUID
        val targetName = args[1]
        val targetPlayer = Bukkit.getPlayer(targetName)
        val targetUUID: UUID

        if (targetPlayer != null) {
            targetUUID = targetPlayer.uniqueId

            if (targetUUID == sender.uniqueId) {
                messages.send(sender, "trust.self")
                return
            }
        } else {
            // Try to get offline player
            @Suppress("DEPRECATION")
            val offlinePlayer = Bukkit.getOfflinePlayer(targetName)
            if (!offlinePlayer.hasPlayedBefore()) {
                messages.send(sender, "general.player-not-found", mapOf("player" to targetName))
                return
            }
            targetUUID = offlinePlayer.uniqueId
        }

        if (lock.isTrusted(targetUUID)) {
            messages.send(sender, "trust.already-trusted", mapOf("player" to targetName))
            return
        }

        if (lockManager.addTrusted(lock, targetUUID)) {
            messages.send(sender, "trust.added", mapOf("player" to targetName))
        }
    }

    /**
     * Handles /lock untrust <player>
     */
    private fun handleUntrust(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            messages.send(sender, "general.player-only")
            return
        }

        if (!sender.hasPermission("pinchoslocks.trust")) {
            messages.send(sender, "general.no-permission")
            return
        }

        if (args.size < 2) {
            messages.sendRaw(sender, "help.untrust")
            return
        }

        val block = sender.getTargetBlockExact(5)
        if (block == null) {
            messages.send(sender, "lock.not-looking")
            return
        }

        val lock = lockManager.getLock(block)
        if (lock == null) {
            messages.send(sender, "lock.not-locked")
            return
        }

        if (!lock.isOwner(sender.uniqueId) && !sender.hasPermission("pinchoslocks.admin")) {
            messages.send(sender, "lock.not-owner")
            return
        }

        val targetName = args[1]

        @Suppress("DEPRECATION")
        val offlinePlayer = Bukkit.getOfflinePlayer(targetName)
        val targetUUID = offlinePlayer.uniqueId

        if (!lock.isTrusted(targetUUID)) {
            messages.send(sender, "trust.not-trusted", mapOf("player" to targetName))
            return
        }

        if (lockManager.removeTrusted(lock, targetUUID)) {
            messages.send(sender, "trust.removed", mapOf("player" to targetName))
        }
    }

    /**
     * Handles /lock trustlist
     */
    private fun handleTrustList(sender: CommandSender) {
        if (sender !is Player) {
            messages.send(sender, "general.player-only")
            return
        }

        if (!sender.hasPermission("pinchoslocks.trust")) {
            messages.send(sender, "general.no-permission")
            return
        }

        val block = sender.getTargetBlockExact(5)
        if (block == null) {
            messages.send(sender, "lock.not-looking")
            return
        }

        val lock = lockManager.getLock(block)
        if (lock == null) {
            messages.send(sender, "lock.not-locked")
            return
        }

        if (!lock.isOwner(sender.uniqueId) && !sender.hasPermission("pinchoslocks.admin")) {
            messages.send(sender, "lock.not-owner")
            return
        }

        if (lock.trustedPlayers.isEmpty()) {
            messages.send(sender, "trust.list-empty")
            return
        }

        messages.sendRaw(sender, "trust.list-header")
        lock.trustedPlayers.forEach { uuidString ->
            val uuid = UUID.fromString(uuidString)
            val name = Bukkit.getOfflinePlayer(uuid).name ?: "Unknown"
            messages.sendRaw(sender, "trust.list-entry", mapOf("player" to name))
        }
    }

    /**
     * Handles /lock reload
     */
    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission("pinchoslocks.admin")) {
            messages.send(sender, "general.no-permission")
            return
        }

        plugin.reload()
        messages.send(sender, "general.reload")
    }
}
