package net.lumalyte.lumasg.game

import net.lumalyte.lumasg.domain.Arena
import org.bukkit.Bukkit

/**
 * Validates that an arena is fully configured and ready to host games.
 * Returns a list of issues — empty list means setup is complete.
 */
object GameSetupValidator {

    data class ValidationResult(
        val arena: Arena,
        val issues: List<String>
    ) {
        val isValid: Boolean get() = issues.isEmpty()

        fun summary(): String = if (isValid) {
            "Arena '${arena.name}' is ready."
        } else {
            "Arena '${arena.name}' has ${issues.size} issue(s):\n" +
                issues.joinToString("\n") { "  - $it" }
        }
    }

    /** Validate an arena's setup completeness. */
    fun validate(arena: Arena): ValidationResult {
        val issues = mutableListOf<String>()

        if (Bukkit.getWorld(arena.worldName) == null) {
            issues.add("World '${arena.worldName}' is not loaded")
        }

        if (arena.spawnPoints.isEmpty()) {
            issues.add("No spawn points configured")
        } else if (arena.spawnPoints.size < arena.minPlayers) {
            issues.add("Only ${arena.spawnPoints.size} spawn points for ${arena.minPlayers} minimum players")
        }

        if (arena.minPlayers < 2) {
            issues.add("Minimum players must be at least 2 (currently ${arena.minPlayers})")
        }
        if (arena.maxPlayers < arena.minPlayers) {
            issues.add("Max players (${arena.maxPlayers}) is less than min players (${arena.minPlayers})")
        }
        if (arena.maxPlayers > arena.spawnPoints.size && arena.spawnPoints.isNotEmpty()) {
            issues.add("Max players (${arena.maxPlayers}) exceeds spawn point count (${arena.spawnPoints.size})")
        }

        if (arena.center.toBukkit() == null) {
            issues.add("Arena center location is invalid (world not loaded)")
        }

        if (arena.radius <= 0) {
            issues.add("Arena radius must be positive (currently ${arena.radius})")
        }

        if (arena.lobbySpawn == null) {
            issues.add("No lobby spawn point set (players will spawn at arena center)")
        }

        if (arena.spectatorSpawn == null) {
            issues.add("No spectator spawn point set (spectators will use arena center)")
        }

        return ValidationResult(arena, issues)
    }

    /** Quick check — is the arena ready to host a game? */
    fun isSetupComplete(arena: Arena): Boolean {
        if (Bukkit.getWorld(arena.worldName) == null) return false
        if (arena.spawnPoints.size < arena.minPlayers) return false
        if (arena.minPlayers < 2) return false
        if (arena.maxPlayers < arena.minPlayers) return false
        if (arena.radius <= 0) return false
        return true
    }
}
