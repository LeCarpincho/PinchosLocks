package dev.pincho.locks.managers

import dev.pincho.locks.PinchosLocks
import dev.pincho.locks.config.ConfigManager
import dev.pincho.locks.models.Lock
import dev.pincho.locks.models.LockTier
import dev.pincho.locks.models.LockpickTier
import dev.pincho.locks.utils.MessageUtils
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Manages the lockpicking minigame system.
 * Handles progress bars, probability calculations, and outcomes.
 */
class LockpickManager(
    private val plugin: PinchosLocks,
    private val config: ConfigManager,
    private val messages: MessageUtils,
    private val lockManager: LockManager
) {

    // Track players currently picking locks
    private val activePickers = ConcurrentHashMap<UUID, LockpickSession>()

    // Cooldowns for lockpick attempts
    private val cooldowns = ConcurrentHashMap<UUID, Long>()

    /**
     * Represents an active lockpicking session.
     */
    data class LockpickSession(
        val player: Player,
        val lock: Lock,
        val lockpickTier: LockpickTier,
        val lockpickItem: ItemStack,
        val location: Location,
        val startPosition: Location,  // Player's position when they started
        var progress: Int = 0,
        var task: BukkitRunnable? = null
    )

    /**
     * Sealed class for lockpick results.
     */
    sealed class LockpickResult {
        data class Success(val lock: Lock) : LockpickResult()
        data class Failed(val reason: String) : LockpickResult()
        data class Broke(val wasLastUse: Boolean) : LockpickResult()
        object AlreadyPicking : LockpickResult()
        object OnCooldown : LockpickResult()
        object NotPickable : LockpickResult()
        object Disabled : LockpickResult()
        object NoPermission : LockpickResult()
        object LockpickTooWeak : LockpickResult()
    }

    /**
     * Starts a lockpicking attempt.
     */
    fun startLockpicking(
        player: Player,
        lock: Lock,
        lockpickItem: ItemStack,
        location: Location
    ): LockpickResult {
        // Check if lockpicks are enabled
        if (!config.lockpicksEnabled) {
            return LockpickResult.Disabled
        }

        // Check if already picking
        if (activePickers.containsKey(player.uniqueId)) {
            return LockpickResult.AlreadyPicking
        }

        // Check cooldown
        val cooldownEnd = cooldowns[player.uniqueId] ?: 0L
        if (System.currentTimeMillis() < cooldownEnd) {
            return LockpickResult.OnCooldown
        }

        // Get lockpick tier
        val lockpickTier = getLockpickTier(lockpickItem) ?: return LockpickResult.Failed("Invalid lockpick")

        // Check permission for this specific lockpick tier
        val tierPermission = "pinchoslocks.lockpick.${lockpickTier.name.lowercase()}"
        if (!player.hasPermission(tierPermission)) {
            return LockpickResult.NoPermission
        }

        // Check if player is sneaking (required to start lockpicking)
        if (!player.isSneaking) {
            return LockpickResult.Failed("must_sneak")
        }

        // Check if lock is pickable
        val lockTier = lock.getTier()
        val tierConfig = config.getTierConfig(lockTier)
        if (!tierConfig.pickable) {
            return LockpickResult.NotPickable
        }

        // Check if lockpick tier is strong enough for this lock tier
        // BASIC can only pick BRONZE
        // ADVANCED can pick BRONZE and SILVER
        // MASTER can pick any lock
        if (!canLockpickOpenLock(lockpickTier, lockTier)) {
            return LockpickResult.LockpickTooWeak
        }

        // Create session with player's starting position
        val session = LockpickSession(
            player = player,
            lock = lock,
            lockpickTier = lockpickTier,
            lockpickItem = lockpickItem,
            location = location,
            startPosition = player.location.clone()
        )

        activePickers[player.uniqueId] = session

        // Start the minigame
        startMinigame(session)

        return LockpickResult.Success(lock)
    }

    /**
     * Cancels an active lockpicking session.
     */
    fun cancelLockpicking(player: Player) {
        val session = activePickers.remove(player.uniqueId)
        session?.task?.cancel()

        if (session != null) {
            // Clear action bar
            messages.sendActionBar(player, "lockpick.cancelled", emptyMap())
        }
    }

    /**
     * Checks if a player is currently picking a lock.
     */
    fun isPicking(player: Player): Boolean {
        return activePickers.containsKey(player.uniqueId)
    }

    /**
     * Starts the lockpicking minigame with progress bar.
     */
    private fun startMinigame(session: LockpickSession) {
        val player = session.player
        val lock = session.lock
        val lockpickTier = session.lockpickTier

        // Calculate success chance
        val lockTier = lock.getTier()
        val lockDifficulty = config.getTierConfig(lockTier).pickDifficulty
        val lockpickBonus = config.getLockpickConfig(lockpickTier).successModifier
        val playerLuck = getPlayerLuck(player)

        // Base chance calculation:
        // 65 - lockDifficulty + lockpickBonus + (playerLuck * 4)
        // Balanced difficulty - challenging but achievable
        val baseChance = (65 - lockDifficulty + lockpickBonus + (playerLuck * 4)).coerceIn(15.0, 80.0).toInt()

        // Duration based on lock tier (in ticks, 20 ticks = 1 second)
        // Bronze: 20 seconds, Silver: 40 seconds, Gold: 60 seconds (1 minute)
        val duration = when (lockTier) {
            LockTier.BRONZE -> 400   // 20 seconds
            LockTier.SILVER -> 800   // 40 seconds
            LockTier.GOLD -> 1200    // 60 seconds (1 minute)
        }

        // Number of "checkpoints" during the picking (more checkpoints = more chances to fail)
        val checkpoints = when (lockTier) {
            LockTier.BRONZE -> 5
            LockTier.SILVER -> 8
            LockTier.GOLD -> 12
        }

        val ticksPerCheckpoint = duration / checkpoints
        var currentCheckpoint = 0

        // Send initial message
        messages.send(player, "lockpick.attempting")

        // Play starting sound
        player.playSound(session.location, Sound.BLOCK_IRON_DOOR_CLOSE, 0.5f, 1.5f)

        session.task = object : BukkitRunnable() {
            var ticks = 0

            override fun run() {
                // Check if player is still sneaking (REQUIRED)
                if (!player.isSneaking) {
                    cancelLockpicking(player)
                    messages.send(player, "lockpick.stood-up")
                    cancel()
                    return
                }

                // Check if player moved (must stay in place)
                val currentPos = player.location
                val startPos = session.startPosition
                if (currentPos.x != startPos.x || currentPos.z != startPos.z) {
                    cancelLockpicking(player)
                    messages.send(player, "lockpick.moved-away")
                    cancel()
                    return
                }

                // Check if player moved too far from the lock
                if (player.location.distance(session.location) > 5) {
                    cancelLockpicking(player)
                    messages.send(player, "lockpick.too-far")
                    cancel()
                    return
                }

                // Check if player still has the lockpick
                if (!isHoldingLockpick(player, session.lockpickItem)) {
                    cancelLockpicking(player)
                    messages.send(player, "lockpick.switched-item")
                    cancel()
                    return
                }

                // Update progress bar
                val progress = ((ticks.toFloat() / duration) * 100).toInt().coerceIn(0, 100)
                session.progress = progress
                sendProgressBar(player, progress, lockTier, lockpickTier)

                // Check for checkpoint (probability check)
                if (ticks > 0 && ticks % ticksPerCheckpoint == 0 && currentCheckpoint < checkpoints) {
                    currentCheckpoint++

                    // Random check at each checkpoint - harder difficulty
                    val roll = Random.nextInt(100)
                    // Only small bonus per checkpoint (+1 instead of +3)
                    val checkpointChance = baseChance + currentCheckpoint

                    if (roll > checkpointChance) {
                        // Failed checkpoint - lockpick might break
                        // Higher break chance based on lock difficulty
                        val breakChance = (lockDifficulty / 2) + 10 - (lockpickBonus / 4)
                        val breakRoll = Random.nextInt(100)

                        if (breakRoll < breakChance) {
                            // Lockpick breaks
                            handleLockpickBreak(player, session)
                            cancel()
                            return
                        } else {
                            // Just a stumble - play sound, show warning and continue
                            player.playSound(session.location, Sound.BLOCK_CHAIN_BREAK, 0.7f, 0.8f)
                            sendProgressBar(player, progress, lockTier, lockpickTier, stumble = true)
                        }
                    } else {
                        // Successful checkpoint - play click sound
                        player.playSound(session.location, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.5f, 1.0f + (currentCheckpoint * 0.05f))
                    }
                }

                // Check if complete
                if (ticks >= duration) {
                    // If progress reached 98% or more, GUARANTEED success!
                    // The player earned it by staying still and passing all checkpoints
                    if (progress >= 98) {
                        // Success! Player completed the lockpick
                        handleLockpickSuccess(player, session)
                    } else {
                        // Shouldn't happen normally, but just in case - still success
                        handleLockpickSuccess(player, session)
                    }
                    cancel()
                    return
                }

                ticks += 2 // Run every 2 ticks for smoother animation
            }
        }.also { it.runTaskTimer(plugin, 0L, 2L) }
    }

    /**
     * Sends the progress bar to the player's action bar.
     */
    private fun sendProgressBar(
        player: Player,
        progress: Int,
        lockTier: LockTier,
        lockpickTier: LockpickTier,
        stumble: Boolean = false
    ) {
        val totalBars = 20
        val filledBars = (progress * totalBars / 100)
        val emptyBars = totalBars - filledBars

        // Color based on progress and tier
        val progressColor = when {
            stumble -> "§c" // Red on stumble
            progress < 33 -> "§e" // Yellow
            progress < 66 -> "§6" // Gold
            else -> "§a" // Green
        }

        val tierColor = when (lockTier) {
            LockTier.BRONZE -> "§6"
            LockTier.SILVER -> "§7"
            LockTier.GOLD -> "§e"
        }

        val lockpickColor = when (lockpickTier) {
            LockpickTier.BASIC -> "§7"
            LockpickTier.ADVANCED -> "§b"
            LockpickTier.MASTER -> "§d"
        }

        val filledChar = if (stumble) "§c▓" else "$progressColor▓"
        val emptyChar = "§8░"

        val progressBar = buildString {
            append("$tierColor§l⚿ ")
            append("§8[")
            repeat(filledBars) { append(filledChar) }
            repeat(emptyBars) { append(emptyChar) }
            append("§8] ")
            append("$progressColor$progress% ")
            append("$lockpickColor[${lockpickTier.displayName}]")
        }

        // Send directly to action bar using Spigot API
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
            net.md_5.bungee.api.chat.TextComponent(progressBar))
    }

    // Track players with active access timers
    private val accessTimers = ConcurrentHashMap<UUID, BukkitRunnable>()

    /**
     * Handles successful lockpicking.
     */
    private fun handleLockpickSuccess(player: Player, session: LockpickSession) {
        activePickers.remove(player.uniqueId)

        // Set cooldown
        val cooldownTime = (config.getLockpickConfig(session.lockpickTier).cooldown * 1000).toLong()
        cooldowns[player.uniqueId] = System.currentTimeMillis() + cooldownTime

        // Use the lockpick (decrease durability)
        decreaseLockpickDurability(player, session.lockpickItem)

        // Play success effects
        player.playSound(session.location, config.soundLockpickSuccess, 1.0f, 1.0f)
        session.location.world?.spawnParticle(
            Particle.HAPPY_VILLAGER,
            session.location.clone().add(0.5, 0.5, 0.5),
            15,
            0.3, 0.3, 0.3,
            0.0
        )

        // Send success message
        messages.send(player, "lockpick.success")

        // Notify owner that someone successfully lockpicked their lock
        notifyOwner(session.lock, "lockpick.owner-success-notification", player.name)

        // AUTOMATICALLY open the container/door after successful lockpicking
        // This provides better UX than requiring another click
        openBlockAutomatically(player, session.location)
    }

    /**
     * Opens a container or toggles a door/trapdoor automatically after successful lockpicking.
     * This is called immediately when lockpicking succeeds - no additional click needed!
     */
    private fun openBlockAutomatically(player: Player, location: Location) {
        plugin.logger.info("[Lockpick] Auto-opening block at $location for ${player.name}")

        // Schedule on next tick to ensure everything is properly cleaned up
        org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                val block = location.block
                val blockState = block.state
                val blockData = block.blockData

                plugin.logger.info("[Lockpick] Block type: ${block.type}, State: ${blockState::class.simpleName}")

                // Handle Chest specifically (single and double)
                if (blockState is org.bukkit.block.Chest) {
                    plugin.logger.info("[Lockpick] Opening chest automatically for ${player.name}")
                    try {
                        val inventory = blockState.inventory
                        player.openInventory(inventory)
                        plugin.logger.info("[Lockpick] Chest opened successfully!")
                    } catch (e: Exception) {
                        plugin.logger.severe("[Lockpick] Error opening chest: ${e.message}")
                        // Fallback: try blockInventory
                        try {
                            player.openInventory(blockState.blockInventory)
                        } catch (e2: Exception) {
                            plugin.logger.severe("[Lockpick] Fallback also failed: ${e2.message}")
                        }
                    }
                    return@Runnable
                }

                // Handle Barrel
                if (blockState is org.bukkit.block.Barrel) {
                    plugin.logger.info("[Lockpick] Opening barrel automatically for ${player.name}")
                    player.openInventory(blockState.inventory)
                    return@Runnable
                }

                // Handle ShulkerBox
                if (blockState is org.bukkit.block.ShulkerBox) {
                    plugin.logger.info("[Lockpick] Opening shulker box automatically for ${player.name}")
                    player.openInventory(blockState.inventory)
                    return@Runnable
                }

                // Handle other containers
                if (blockState is org.bukkit.block.Container) {
                    plugin.logger.info("[Lockpick] Opening generic container automatically for ${player.name}")
                    player.openInventory(blockState.inventory)
                    return@Runnable
                }

                // Handle doors
                if (blockData is org.bukkit.block.data.type.Door) {
                    plugin.logger.info("[Lockpick] Toggling door automatically for ${player.name}")
                    blockData.isOpen = true  // Always open
                    block.blockData = blockData

                    val isIron = block.type.name.contains("IRON")
                    val sound = if (isIron) Sound.BLOCK_IRON_DOOR_OPEN else Sound.BLOCK_WOODEN_DOOR_OPEN
                    block.world.playSound(location, sound, 1.0f, 1.0f)

                    // Schedule auto-close after 15 seconds
                    scheduleAutoClose(block, location, 15)
                    startAutoCloseTimer(player, 15)
                    return@Runnable
                }

                // Handle trapdoors
                if (blockData is org.bukkit.block.data.type.TrapDoor) {
                    plugin.logger.info("[Lockpick] Toggling trapdoor automatically for ${player.name}")
                    blockData.isOpen = true  // Always open
                    block.blockData = blockData

                    val isIron = block.type.name.contains("IRON")
                    val sound = if (isIron) Sound.BLOCK_IRON_TRAPDOOR_OPEN else Sound.BLOCK_WOODEN_TRAPDOOR_OPEN
                    block.world.playSound(location, sound, 1.0f, 1.0f)

                    // Schedule auto-close after 15 seconds
                    scheduleAutoClose(block, location, 15)
                    startAutoCloseTimer(player, 15)
                    return@Runnable
                }

                // Handle fence gates
                if (blockData is org.bukkit.block.data.type.Gate) {
                    plugin.logger.info("[Lockpick] Toggling fence gate automatically for ${player.name}")
                    blockData.isOpen = true  // Always open
                    block.blockData = blockData
                    block.world.playSound(location, Sound.BLOCK_FENCE_GATE_OPEN, 1.0f, 1.0f)

                    // Schedule auto-close after 15 seconds
                    scheduleAutoClose(block, location, 15)
                    startAutoCloseTimer(player, 15)
                    return@Runnable
                }

                // Generic openable
                if (blockData is org.bukkit.block.data.Openable) {
                    plugin.logger.info("[Lockpick] Toggling generic openable automatically for ${player.name}")
                    blockData.isOpen = !blockData.isOpen
                    block.blockData = blockData
                    return@Runnable
                }

                plugin.logger.warning("[Lockpick] Block type ${block.type} not handled for auto-open")

            } catch (e: Exception) {
                plugin.logger.severe("[Lockpick] Error in openBlockAutomatically: ${e.message}")
                e.printStackTrace()
            }
        })
    }

    /**
     * Handles failed lockpicking.
     */
    private fun handleLockpickFailure(player: Player, session: LockpickSession) {
        activePickers.remove(player.uniqueId)

        // Set cooldown
        val cooldownTime = (config.getLockpickConfig(session.lockpickTier).cooldown * 1000).toLong()
        cooldowns[player.uniqueId] = System.currentTimeMillis() + cooldownTime

        // Play failure effects
        player.playSound(session.location, config.soundLockpickFail, 1.0f, 0.5f)
        session.location.world?.spawnParticle(
            Particle.SMOKE,
            session.location.clone().add(0.5, 0.5, 0.5),
            10,
            0.2, 0.2, 0.2,
            0.0
        )

        // Send failure message
        messages.send(player, "lockpick.failed")

        // Clear action bar
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
            net.md_5.bungee.api.chat.TextComponent("§c§l✗ §cFallaste..."))
    }

    /**
     * Handles lockpick breaking.
     */
    private fun handleLockpickBreak(player: Player, session: LockpickSession) {
        activePickers.remove(player.uniqueId)

        // Set cooldown
        val cooldownTime = (config.getLockpickConfig(session.lockpickTier).cooldown * 1000).toLong()
        cooldowns[player.uniqueId] = System.currentTimeMillis() + cooldownTime

        // Break the lockpick
        val broke = breakLockpick(player, session.lockpickItem)

        // Play break effects
        player.playSound(session.location, config.soundLockpickBreak, 1.0f, 0.3f)
        session.location.world?.spawnParticle(
            Particle.ITEM,
            session.location.clone().add(0.5, 1.0, 0.5),
            20,
            0.2, 0.2, 0.2,
            0.05,
            session.lockpickItem
        )

        // Send break message
        messages.send(player, "lockpick.broke")

        // Clear action bar
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
            net.md_5.bungee.api.chat.TextComponent("§c§l⚠ §c¡Tu ganzúa se rompió!"))

        // Notify owner that someone broke a lockpick on their lock
        notifyOwner(session.lock, "lockpick.owner-broke-notification", player.name)
    }

    /**
     * Checks if a lockpick tier can open a lock tier.
     * BASIC can only pick BRONZE
     * ADVANCED can pick BRONZE and SILVER
     * MASTER can pick any lock
     */
    private fun canLockpickOpenLock(lockpickTier: LockpickTier, lockTier: LockTier): Boolean {
        return when (lockpickTier) {
            LockpickTier.BASIC -> lockTier == LockTier.BRONZE
            LockpickTier.ADVANCED -> lockTier == LockTier.BRONZE || lockTier == LockTier.SILVER
            LockpickTier.MASTER -> true // Can open any lock
        }
    }

    /**
     * Gets the player's luck attribute value.
     */
    private fun getPlayerLuck(player: Player): Double {
        return try {
            // Try GENERIC_LUCK first (1.21+), fallback to LUCK for older versions
            player.getAttribute(Attribute.GENERIC_LUCK)?.value ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    /**
     * Gets the lockpick tier from an item.
     */
    private fun getLockpickTier(item: ItemStack): LockpickTier? {
        val meta = item.itemMeta ?: return null
        val tierName = meta.persistentDataContainer.get(
            NamespacedKey(plugin, "lockpick_tier"),
            PersistentDataType.STRING
        ) ?: return null
        return LockpickTier.fromString(tierName)
    }

    /**
     * Checks if the player is holding the same lockpick.
     */
    private fun isHoldingLockpick(player: Player, originalItem: ItemStack): Boolean {
        val heldItem = player.inventory.itemInMainHand
        if (heldItem.type != originalItem.type) return false

        val meta = heldItem.itemMeta ?: return false
        return meta.persistentDataContainer.has(
            NamespacedKey(plugin, "lockpick_tier"),
            PersistentDataType.STRING
        )
    }

    /**
     * Decreases lockpick durability by 1.
     */
    private fun decreaseLockpickDurability(player: Player, item: ItemStack) {
        val meta = item.itemMeta ?: return
        val currentUses = meta.persistentDataContainer.get(
            NamespacedKey(plugin, "lockpick_uses"),
            PersistentDataType.INTEGER
        ) ?: return

        val newUses = currentUses - 1
        if (newUses <= 0) {
            // Remove the item
            player.inventory.itemInMainHand.amount = player.inventory.itemInMainHand.amount - 1
            player.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f)
        } else {
            // Update uses
            meta.persistentDataContainer.set(
                NamespacedKey(plugin, "lockpick_uses"),
                PersistentDataType.INTEGER,
                newUses
            )

            // Update lore
            val tier = getLockpickTier(item) ?: return
            updateLockpickLore(item, meta, tier, newUses)
            item.itemMeta = meta
        }
    }

    /**
     * Breaks the lockpick completely.
     */
    private fun breakLockpick(player: Player, item: ItemStack): Boolean {
        val heldItem = player.inventory.itemInMainHand
        if (heldItem.amount <= 1) {
            player.inventory.setItemInMainHand(null)
        } else {
            heldItem.amount = heldItem.amount - 1
        }
        return true
    }

    /**
     * Updates the lore of a lockpick to show remaining uses.
     */
    private fun updateLockpickLore(item: ItemStack, meta: org.bukkit.inventory.meta.ItemMeta, tier: LockpickTier, uses: Int) {
        val tierConfig = config.getLockpickConfig(tier)
        val lore = listOf(
            "",
            "§7Tier: §f${tier.displayName}",
            "§7Usos: §e$uses",
            "§7Exito: §a+${tierConfig.successModifier}%",
            "",
            "§8Usa esto en un candado",
            "§8para intentar abrirlo."
        )
        @Suppress("DEPRECATION")
        meta.lore = lore
    }

    /**
     * Grants temporary access to a lock with a visual countdown timer.
     * Shows how long until the container/door locks again.
     */
    private fun grantTemporaryAccessWithTimer(player: Player, lock: Lock, durationSeconds: Int) {
        // Cancel any existing timer for this player
        accessTimers[player.uniqueId]?.cancel()
        accessTimers.remove(player.uniqueId)

        // Grant temporary access
        lockManager.addTemporaryAccess(player.uniqueId, lock.id, durationSeconds)
        plugin.logger.info("[Lockpick] Player ${player.name} gained $durationSeconds sec access to lock ${lock.id}")

        // Start countdown timer on actionbar showing time until it locks again
        val timerTask = object : BukkitRunnable() {
            var secondsLeft = durationSeconds

            override fun run() {
                if (secondsLeft <= 0) {
                    // Time expired - lock closes again
                    player.spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent("§c§l🔒 §cEl candado se ha vuelto a cerrar!")
                    )
                    accessTimers.remove(player.uniqueId)
                    cancel()
                    return
                }

                // Show countdown
                val color = when {
                    secondsLeft <= 3 -> "§c" // Red when critical
                    secondsLeft <= 7 -> "§e" // Yellow when low
                    else -> "§a" // Green
                }

                val barLength = 15
                val filled = (secondsLeft * barLength / durationSeconds).coerceIn(0, barLength)
                val empty = barLength - filled

                val timerBar = buildString {
                    append("§a§l🔓 §fSe cierra en: ")
                    append("§8[")
                    append(color)
                    repeat(filled) { append("▌") }
                    append("§8")
                    repeat(empty) { append("▌") }
                    append("§8] ")
                    append("$color§l$secondsLeft")
                    append("${color}s")
                }

                player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent(timerBar)
                )

                secondsLeft--
            }
        }

        timerTask.runTaskTimer(plugin, 0L, 20L) // Run every second
        accessTimers[player.uniqueId] = timerTask
    }

    /**
     * Cancels the access timer for a player (called when they open the chest/door).
     */
    fun cancelAccessTimer(player: Player) {
        accessTimers[player.uniqueId]?.cancel()
        accessTimers.remove(player.uniqueId)
    }

    /**
     * Schedules a door/trapdoor/gate to auto-close after the specified seconds.
     */
    private fun scheduleAutoClose(block: org.bukkit.block.Block, location: Location, seconds: Int) {
        object : BukkitRunnable() {
            override fun run() {
                val currentBlock = location.block
                val blockData = currentBlock.blockData

                when (blockData) {
                    is org.bukkit.block.data.type.Door -> {
                        if (blockData.isOpen) {
                            blockData.isOpen = false
                            currentBlock.blockData = blockData
                            val isIron = currentBlock.type.name.contains("IRON")
                            val sound = if (isIron) Sound.BLOCK_IRON_DOOR_CLOSE else Sound.BLOCK_WOODEN_DOOR_CLOSE
                            currentBlock.world.playSound(location, sound, 1.0f, 1.0f)
                        }
                    }
                    is org.bukkit.block.data.type.TrapDoor -> {
                        if (blockData.isOpen) {
                            blockData.isOpen = false
                            currentBlock.blockData = blockData
                            val isIron = currentBlock.type.name.contains("IRON")
                            val sound = if (isIron) Sound.BLOCK_IRON_TRAPDOOR_CLOSE else Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE
                            currentBlock.world.playSound(location, sound, 1.0f, 1.0f)
                        }
                    }
                    is org.bukkit.block.data.type.Gate -> {
                        if (blockData.isOpen) {
                            blockData.isOpen = false
                            currentBlock.blockData = blockData
                            currentBlock.world.playSound(location, Sound.BLOCK_FENCE_GATE_CLOSE, 1.0f, 1.0f)
                        }
                    }
                }
            }
        }.runTaskLater(plugin, (seconds * 20).toLong())
    }

    /**
     * Shows a countdown timer in actionbar for doors/trapdoors/gates auto-close.
     */
    private fun startAutoCloseTimer(player: Player, seconds: Int) {
        // Cancel any existing timer for this player
        accessTimers[player.uniqueId]?.cancel()
        accessTimers.remove(player.uniqueId)

        val timerTask = object : BukkitRunnable() {
            var secondsLeft = seconds

            override fun run() {
                if (secondsLeft <= 0) {
                    // Time expired - door/trapdoor closes automatically
                    player.spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent("§c§l🔒 §cLa puerta se ha cerrado automaticamente!")
                    )
                    accessTimers.remove(player.uniqueId)
                    cancel()
                    return
                }

                // Show countdown
                val color = when {
                    secondsLeft <= 3 -> "§c" // Red when critical
                    secondsLeft <= 7 -> "§e" // Yellow when low
                    else -> "§a" // Green
                }

                val barLength = 15
                val filled = (secondsLeft * barLength / seconds).coerceIn(0, barLength)
                val empty = barLength - filled

                val timerBar = buildString {
                    append("§a§l🚪 §fSe cierra en: ")
                    append("§8[")
                    append(color)
                    repeat(filled) { append("▌") }
                    append("§8")
                    repeat(empty) { append("▌") }
                    append("§8] ")
                    append("$color§l$secondsLeft")
                    append("${color}s")
                }

                player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent(timerBar)
                )

                secondsLeft--
            }
        }

        timerTask.runTaskTimer(plugin, 0L, 20L)
        accessTimers[player.uniqueId] = timerTask
    }

    /**
     * Notifies the lock owner about lockpick events if they are online.
     */
    private fun notifyOwner(lock: Lock, messageKey: String, attackerName: String) {
        val ownerUUID = lock.getOwnerUUID()
        val owner = org.bukkit.Bukkit.getPlayer(ownerUUID)

        if (owner != null && owner.isOnline) {
            // Get the block type name for the message
            val bukkitLocation = lock.location.toBukkit()
            val blockTypeName = if (bukkitLocation != null) {
                getBlockTypeName(bukkitLocation.block.type)
            } else {
                "bloque"
            }

            messages.send(owner, messageKey, mapOf(
                "player" to attackerName,
                "block" to blockTypeName
            ))

            // Play alert sound to owner
            owner.playSound(owner.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f)
        }
    }

    /**
     * Gets a friendly name for a block type.
     */
    private fun getBlockTypeName(type: org.bukkit.Material): String {
        return when {
            type.name.contains("CHEST") -> "cofre"
            type.name.contains("BARREL") -> "barril"
            type.name.contains("SHULKER") -> "caja shulker"
            type.name.contains("DOOR") && !type.name.contains("TRAP") -> "puerta"
            type.name.contains("TRAPDOOR") -> "trampilla"
            type.name.contains("GATE") -> "valla"
            else -> "bloque"
        }
    }

    /**
     * Checks if a player is on cooldown.
     */
    fun isOnCooldown(player: Player): Boolean {
        val cooldownEnd = cooldowns[player.uniqueId] ?: return false
        return System.currentTimeMillis() < cooldownEnd
    }

    /**
     * Gets remaining cooldown in seconds.
     */
    fun getRemainingCooldown(player: Player): Double {
        val cooldownEnd = cooldowns[player.uniqueId] ?: return 0.0
        val remaining = cooldownEnd - System.currentTimeMillis()
        return if (remaining > 0) remaining / 1000.0 else 0.0
    }

    /**
     * Clears cooldown for a player.
     */
    fun clearCooldown(player: UUID) {
        cooldowns.remove(player)
        activePickers.remove(player)
    }
}
