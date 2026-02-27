package dev.pincho.locks.utils

import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Handles visual particle effects and sounds for lock interactions.
 * Provides animated and static particle effects for various lock events.
 *
 * All effects are designed to be visually appealing while maintaining
 * good performance on servers with many players.
 */
object ParticleEffects {

    // ═══════════════════════════════════════════════════════════════════════════
    // LOCK PLACEMENT EFFECTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Plays a success effect when a lock is placed.
     * Creates a spiral of happy villager particles rising upward.
     */
    @Suppress("UNUSED_PARAMETER")
    fun playLockPlaceEffect(plugin: JavaPlugin, location: Location, player: Player) {
        val center = location.clone().add(0.5, 0.5, 0.5)
        val world = location.world ?: return

        // Initial burst of particles
        world.spawnParticle(
            Particle.HAPPY_VILLAGER,
            center,
            15,
            0.3, 0.3, 0.3,
            0.0
        )

        // Animated spiral effect
        object : BukkitRunnable() {
            var tick = 0
            val maxTicks = 20

            override fun run() {
                if (tick >= maxTicks) {
                    cancel()
                    return
                }

                val angle = tick * 0.5
                val radius = 0.4
                val y = tick * 0.05

                val x = cos(angle) * radius
                val z = sin(angle) * radius

                world.spawnParticle(
                    Particle.COMPOSTER,
                    center.clone().add(x, y, z),
                    2,
                    0.0, 0.0, 0.0,
                    0.0
                )

                world.spawnParticle(
                    Particle.COMPOSTER,
                    center.clone().add(-x, y, -z),
                    2,
                    0.0, 0.0, 0.0,
                    0.0
                )

                tick++
            }
        }.runTaskTimer(plugin, 0L, 1L)

        // Sound effect
        player.playSound(location, Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 1.2f)
        player.playSound(location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.5f)
    }

    /**
     * Plays an effect when a lock is removed.
     */
    @Suppress("UNUSED_PARAMETER")
    fun playLockRemoveEffect(plugin: JavaPlugin, location: Location, player: Player) {
        val center = location.clone().add(0.5, 0.5, 0.5)
        val world = location.world ?: return

        // Particle burst outward
        world.spawnParticle(
            Particle.POOF,
            center,
            20,
            0.3, 0.3, 0.3,
            0.05
        )

        world.spawnParticle(
            Particle.DUST,
            center,
            10,
            0.3, 0.3, 0.3,
            0.0,
            Particle.DustOptions(Color.GRAY, 1.0f)
        )

        // Sound
        player.playSound(location, Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 0.8f)
        player.playSound(location, Sound.BLOCK_CHAIN_BREAK, 0.7f, 1.0f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ACCESS EFFECTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Plays an effect when access is denied.
     * Red angry particles with a denial sound.
     */
    @Suppress("UNUSED_PARAMETER")
    fun playAccessDeniedEffect(plugin: JavaPlugin, location: Location, player: Player) {
        val center = location.clone().add(0.5, 0.5, 0.5)
        val world = location.world ?: return

        // Angry villager particles
        world.spawnParticle(
            Particle.ANGRY_VILLAGER,
            center,
            8,
            0.3, 0.3, 0.3,
            0.0
        )

        // Red dust particles forming an X
        for (i in 0..10) {
            val offset = (i - 5) * 0.08
            world.spawnParticle(
                Particle.DUST,
                center.clone().add(offset, offset, 0.0),
                1,
                0.0, 0.0, 0.0,
                0.0,
                Particle.DustOptions(Color.RED, 1.2f)
            )
            world.spawnParticle(
                Particle.DUST,
                center.clone().add(offset, -offset, 0.0),
                1,
                0.0, 0.0, 0.0,
                0.0,
                Particle.DustOptions(Color.RED, 1.2f)
            )
        }

        // Sound
        player.playSound(location, Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 0.5f)
    }

    /**
     * Plays an effect when access is granted.
     * Green success particles.
     */
    @Suppress("UNUSED_PARAMETER")
    fun playAccessGrantedEffect(plugin: JavaPlugin, location: Location, player: Player) {
        val center = location.clone().add(0.5, 0.5, 0.5)
        val world = location.world ?: return

        // Green sparkles
        world.spawnParticle(
            Particle.HAPPY_VILLAGER,
            center,
            8,
            0.2, 0.2, 0.2,
            0.0
        )

        // Sound
        player.playSound(location, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.8f, 1.2f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOCKPICKING EFFECTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a lockpicking progress animation.
     * Returns a BukkitRunnable that can be cancelled.
     */
    @Suppress("UNUSED_PARAMETER")
    fun createLockpickProgressAnimation(
        plugin: JavaPlugin,
        location: Location,
        player: Player
    ): BukkitRunnable {
        val center = location.clone().add(0.5, 0.5, 0.5)
        val world = location.world

        return object : BukkitRunnable() {
            var tick = 0

            override fun run() {
                if (world == null) {
                    cancel()
                    return
                }

                // Rotating dust particles around the lock
                val angle = tick * 0.3
                val radius = 0.4

                for (i in 0..2) {
                    val offsetAngle = angle + (i * 2.094) // 120 degrees apart
                    val x = cos(offsetAngle) * radius
                    val z = sin(offsetAngle) * radius

                    world.spawnParticle(
                        Particle.DUST,
                        center.clone().add(x, 0.0, z),
                        1,
                        0.0, 0.0, 0.0,
                        0.0,
                        Particle.DustOptions(Color.YELLOW, 0.8f)
                    )
                }

                // Occasional sparks
                if (tick % 5 == 0) {
                    world.spawnParticle(
                        Particle.ELECTRIC_SPARK,
                        center.clone().add(
                            Random.nextDouble(-0.2, 0.2),
                            Random.nextDouble(-0.2, 0.2),
                            Random.nextDouble(-0.2, 0.2)
                        ),
                        1,
                        0.0, 0.0, 0.0,
                        0.0
                    )
                }

                // Occasional metallic sounds
                if (tick % 10 == 0) {
                    player.playSound(location, Sound.BLOCK_CHAIN_STEP, 0.3f, 1.5f + Random.nextFloat() * 0.5f)
                }

                tick++
            }
        }
    }

    /**
     * Plays a stumble effect during lockpicking (checkpoint fail but not break).
     */
    @Suppress("UNUSED_PARAMETER")
    fun playLockpickStumbleEffect(plugin: JavaPlugin, location: Location, player: Player) {
        val center = location.clone().add(0.5, 0.5, 0.5)
        val world = location.world ?: return

        // Orange warning particles
        world.spawnParticle(
            Particle.DUST,
            center,
            8,
            0.2, 0.2, 0.2,
            0.0,
            Particle.DustOptions(Color.ORANGE, 1.0f)
        )

        // Sound
        player.playSound(location, Sound.BLOCK_CHAIN_BREAK, 0.7f, 0.8f)
    }

    /**
     * Plays an effect when a lockpick breaks.
     * Item break particles and sound.
     */
    @Suppress("UNUSED_PARAMETER")
    fun playLockpickBreakEffect(plugin: JavaPlugin, location: Location, player: Player, lockpickItem: org.bukkit.inventory.ItemStack) {
        val center = location.clone().add(0.5, 1.0, 0.5)
        val world = location.world ?: return

        // Item break particles
        world.spawnParticle(
            Particle.ITEM,
            center,
            25,
            0.2, 0.2, 0.2,
            0.08,
            lockpickItem
        )

        // Red dust burst
        world.spawnParticle(
            Particle.DUST,
            center,
            15,
            0.3, 0.3, 0.3,
            0.0,
            Particle.DustOptions(Color.RED, 1.2f)
        )

        // Smoke
        world.spawnParticle(
            Particle.SMOKE,
            center,
            10,
            0.2, 0.2, 0.2,
            0.02
        )

        // Sounds
        player.playSound(location, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f)
        player.playSound(location, Sound.BLOCK_ANVIL_LAND, 0.3f, 2.0f)
    }

    /**
     * Plays a success effect when lockpicking succeeds.
     * Celebration particles and triumphant sounds.
     */
    @Suppress("UNUSED_PARAMETER")
    fun playLockpickSuccessEffect(plugin: JavaPlugin, location: Location, player: Player) {
        val center = location.clone().add(0.5, 0.5, 0.5)
        val world = location.world ?: return

        // Initial success burst
        world.spawnParticle(
            Particle.HAPPY_VILLAGER,
            center,
            20,
            0.4, 0.4, 0.4,
            0.0
        )

        // Golden sparkles
        world.spawnParticle(
            Particle.DUST,
            center,
            15,
            0.3, 0.3, 0.3,
            0.0,
            Particle.DustOptions(Color.YELLOW, 1.5f)
        )

        // Animated celebration spiral
        object : BukkitRunnable() {
            var tick = 0
            val maxTicks = 30

            override fun run() {
                if (tick >= maxTicks) {
                    cancel()
                    return
                }

                val angle = tick * 0.4
                val radius = 0.3 + (tick * 0.02)
                val y = tick * 0.03

                for (i in 0..2) {
                    val offsetAngle = angle + (i * 2.094)
                    val x = cos(offsetAngle) * radius
                    val z = sin(offsetAngle) * radius

                    val color = when (i) {
                        0 -> Color.LIME
                        1 -> Color.YELLOW
                        else -> Color.WHITE
                    }

                    world.spawnParticle(
                        Particle.DUST,
                        center.clone().add(x, y, z),
                        1,
                        0.0, 0.0, 0.0,
                        0.0,
                        Particle.DustOptions(color, 0.8f)
                    )
                }

                tick++
            }
        }.runTaskTimer(plugin, 0L, 1L)

        // Sounds
        player.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f)
        player.playSound(location, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.2f)
    }

    /**
     * Plays a failure effect when lockpicking fails.
     */
    @Suppress("UNUSED_PARAMETER")
    fun playLockpickFailEffect(plugin: JavaPlugin, location: Location, player: Player) {
        val center = location.clone().add(0.5, 0.5, 0.5)
        val world = location.world ?: return

        // Smoke particles
        world.spawnParticle(
            Particle.SMOKE,
            center,
            15,
            0.2, 0.2, 0.2,
            0.02
        )

        // Red dust
        world.spawnParticle(
            Particle.DUST,
            center,
            10,
            0.2, 0.2, 0.2,
            0.0,
            Particle.DustOptions(Color.fromRGB(100, 100, 100), 1.0f)
        )

        // Sound
        player.playSound(location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY EFFECTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a lock info preview effect (when viewing lock info).
     */
    @Suppress("UNUSED_PARAMETER")
    fun playLockInfoEffect(plugin: JavaPlugin, location: Location, player: Player) {
        val center = location.clone().add(0.5, 0.5, 0.5)
        val world = location.world ?: return

        // Subtle outline around the locked block
        for (i in 0..7) {
            val angle = i * 0.785 // 45 degrees
            val x = cos(angle) * 0.6
            val z = sin(angle) * 0.6

            world.spawnParticle(
                Particle.DUST,
                center.clone().add(x, 0.0, z),
                1,
                0.0, 0.0, 0.0,
                0.0,
                Particle.DustOptions(Color.AQUA, 0.6f)
            )
        }

        // Sound
        player.playSound(location, Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 2.0f)
    }

    /**
     * Creates a tier-specific color for particle effects.
     */
    fun getTierColor(tierName: String): Color {
        return when (tierName.uppercase()) {
            "BRONZE" -> Color.fromRGB(205, 127, 50)  // Bronze/copper color
            "SILVER" -> Color.fromRGB(192, 192, 192) // Silver color
            "GOLD" -> Color.fromRGB(255, 215, 0)     // Gold color
            else -> Color.WHITE
        }
    }

    /**
     * Creates a lockpick tier-specific color.
     */
    fun getLockpickTierColor(tierName: String): Color {
        return when (tierName.uppercase()) {
            "BASIC" -> Color.GRAY
            "ADVANCED" -> Color.AQUA
            "MASTER" -> Color.FUCHSIA
            else -> Color.WHITE
        }
    }
}
