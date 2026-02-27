package dev.pincho.locks.models

import org.bukkit.Material

/**
 * Represents the tier levels for locks in the plugin.
 * Each tier has different properties affecting security and pickability.
 */
enum class LockTier(
    val displayName: String,
    val defaultMaterial: Material,
    val defaultCustomModelData: Int,
    val permission: String
) {
    BRONZE(
        displayName = "Bronze",
        defaultMaterial = Material.COPPER_INGOT,
        defaultCustomModelData = 1001,
        permission = "pinchoslocks.tier.bronze"
    ),
    SILVER(
        displayName = "Silver",
        defaultMaterial = Material.IRON_INGOT,
        defaultCustomModelData = 1002,
        permission = "pinchoslocks.tier.silver"
    ),
    GOLD(
        displayName = "Gold",
        defaultMaterial = Material.GOLD_INGOT,
        defaultCustomModelData = 1003,
        permission = "pinchoslocks.tier.gold"
    );

    companion object {
        /**
         * Gets a LockTier from a string name, case-insensitive.
         * @param name The name of the tier
         * @return The matching LockTier or null if not found
         */
        fun fromString(name: String): LockTier? {
            return entries.find { it.name.equals(name, ignoreCase = true) }
        }

        /**
         * Gets all tier names as a list for tab completion.
         */
        fun names(): List<String> = entries.map { it.name.lowercase() }
    }
}
