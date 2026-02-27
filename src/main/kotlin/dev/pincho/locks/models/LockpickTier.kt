package dev.pincho.locks.models

import org.bukkit.Material

/**
 * Represents the tier levels for lockpicks.
 * Higher tiers have better success rates and durability.
 */
enum class LockpickTier(
    val displayName: String,
    val defaultMaterial: Material,
    val defaultCustomModelData: Int
) {
    BASIC(
        displayName = "Basic",
        defaultMaterial = Material.STICK,
        defaultCustomModelData = 2001
    ),
    ADVANCED(
        displayName = "Advanced",
        defaultMaterial = Material.BLAZE_ROD,
        defaultCustomModelData = 2002
    ),
    MASTER(
        displayName = "Master",
        defaultMaterial = Material.BREEZE_ROD,
        defaultCustomModelData = 2003
    );

    companion object {
        /**
         * Gets a LockpickTier from a string name, case-insensitive.
         */
        fun fromString(name: String): LockpickTier? {
            return entries.find { it.name.equals(name, ignoreCase = true) }
        }

        /**
         * Gets all tier names as a list for tab completion.
         */
        fun names(): List<String> = entries.map { it.name.lowercase() }
    }
}
