package dev.pincho.locks.models

import org.bukkit.Material
import org.bukkit.block.Block

/**
 * Defines categories of blocks that can be locked.
 */
enum class LockableBlockCategory {
    CONTAINER,
    SHULKER,
    DOOR,
    TRAPDOOR
}

/**
 * Utility object for determining if a block can be locked.
 */
object LockableBlock {

    // Default lockable materials by category
    private val defaultContainers = setOf(
        Material.CHEST,
        Material.TRAPPED_CHEST,
        Material.BARREL,
        Material.ENDER_CHEST
    )

    private val defaultShulkers = Material.entries
        .filter { it.name.contains("SHULKER_BOX") }
        .toSet()

    private val defaultDoors = Material.entries
        .filter { it.name.endsWith("_DOOR") && it.isBlock }
        .toSet()

    private val defaultTrapdoors = Material.entries
        .filter { it.name.endsWith("_TRAPDOOR") && it.isBlock }
        .toSet()

    // Mutable sets that can be modified by config
    private val containers = defaultContainers.toMutableSet()
    private val shulkers = defaultShulkers.toMutableSet()
    private val doors = defaultDoors.toMutableSet()
    private val trapdoors = defaultTrapdoors.toMutableSet()

    /**
     * Updates the lockable blocks from configuration.
     */
    fun updateFromConfig(
        containerList: List<String>,
        shulkerList: List<String>,
        doorList: List<String>,
        trapdoorList: List<String>
    ) {
        containers.clear()
        shulkers.clear()
        doors.clear()
        trapdoors.clear()

        containerList.mapNotNull { runCatching { Material.valueOf(it) }.getOrNull() }
            .forEach { containers.add(it) }

        shulkerList.mapNotNull { runCatching { Material.valueOf(it) }.getOrNull() }
            .forEach { shulkers.add(it) }

        doorList.mapNotNull { runCatching { Material.valueOf(it) }.getOrNull() }
            .forEach { doors.add(it) }

        trapdoorList.mapNotNull { runCatching { Material.valueOf(it) }.getOrNull() }
            .forEach { trapdoors.add(it) }
    }

    /**
     * Checks if a material can be locked.
     */
    fun isLockable(material: Material): Boolean {
        return material in containers ||
                material in shulkers ||
                material in doors ||
                material in trapdoors
    }

    /**
     * Checks if a block can be locked.
     */
    fun isLockable(block: Block): Boolean = isLockable(block.type)

    /**
     * Gets the category of a lockable material.
     */
    fun getCategory(material: Material): LockableBlockCategory? {
        return when {
            material in containers -> LockableBlockCategory.CONTAINER
            material in shulkers -> LockableBlockCategory.SHULKER
            material in doors -> LockableBlockCategory.DOOR
            material in trapdoors -> LockableBlockCategory.TRAPDOOR
            else -> null
        }
    }

    /**
     * Checks if a material is a door type (for double-door handling).
     */
    fun isDoor(material: Material): Boolean = material in doors

    /**
     * Checks if a material is a container type.
     */
    fun isContainer(material: Material): Boolean = material in containers || material in shulkers

    /**
     * Gets all lockable materials.
     */
    fun getAllLockable(): Set<Material> = containers + shulkers + doors + trapdoors
}
