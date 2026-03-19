package net.lumalyte.lumasg.hooks

import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.game.GameManager
import org.bukkit.entity.Player

/**
 * Central hook registry providing convenience methods for querying hook state.
 * Individual hooks are Nexus @Service singletons — this class aggregates them.
 */
@Service
class HookManager(
    private val nexoHook: NexoHook,
    private val lumaGuildsHook: LumaGuildsHook,
    private val placeholderAPIHook: PlaceholderAPIHook,
    private val gameManager: GameManager
) {
    /** Check if a named hook's plugin is available. */
    fun isHookAvailable(hookName: String): Boolean = when (hookName.lowercase()) {
        "nexo" -> nexoHook.isAvailable()
        "lumaguilds" -> lumaGuildsHook.isAvailable()
        "placeholderapi" -> placeholderAPIHook.isAvailable()
        else -> false
    }

    fun getNexoHook(): NexoHook? = nexoHook.takeIf { it.isAvailable() }

    fun getLumaGuildsHook(): LumaGuildsHook? = lumaGuildsHook.takeIf { it.isAvailable() }

    /** Whether a player is in an active PvP game. */
    fun isPlayerInActivePvPGame(player: Player): Boolean {
        val game = gameManager.getGameForPlayer(player.uniqueId) ?: return false
        return game.isPvpEnabled()
    }

    /** Whether two players are in the same active PvP game. */
    fun arePlayersInSamePvPGame(player1: Player, player2: Player): Boolean {
        val game1 = gameManager.getGameForPlayer(player1.uniqueId) ?: return false
        val game2 = gameManager.getGameForPlayer(player2.uniqueId) ?: return false
        return game1.id == game2.id && game1.isPvpEnabled()
    }
}
