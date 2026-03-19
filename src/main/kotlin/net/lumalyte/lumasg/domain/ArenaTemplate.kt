package net.lumalyte.lumasg.domain

import org.bukkit.Material

/**
 * Reusable arena configuration template.
 * Captures settings that are common across arenas so they can be applied in bulk.
 */
data class ArenaTemplate(
    val name: String,
    val displayName: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    val radius: Double,
    val allowedBlocks: Set<Material> = Arena.DEFAULT_ALLOWED_BLOCKS,
    val description: String = ""
) {
    /** Apply this template's settings to an existing arena. */
    fun applyTo(arena: Arena): Arena = arena.copy(
        displayName = displayName,
        minPlayers = minPlayers,
        maxPlayers = maxPlayers,
        radius = radius,
        allowedBlocks = allowedBlocks
    )

    companion object {
        /** Extract template-able settings from an existing arena. */
        fun fromArena(arena: Arena, templateName: String): ArenaTemplate = ArenaTemplate(
            name = templateName,
            displayName = arena.displayName,
            minPlayers = arena.minPlayers,
            maxPlayers = arena.maxPlayers,
            radius = arena.radius,
            allowedBlocks = arena.allowedBlocks
        )
    }
}
