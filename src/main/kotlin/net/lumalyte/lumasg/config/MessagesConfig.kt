package net.lumalyte.lumasg.config

import kotlinx.serialization.Serializable
import net.badgersmc.nexus.config.ConfigFile

@ConfigFile("messages.yml")
@Serializable
data class MessagesConfig(
    val gameStart: String = "<green>Game starting in <gold>{countdown}</gold> seconds!",
    val gameEnd: String = "<gold>{winner}</gold> <green>has won the game!",
    val playerEliminated: String = "<red>{player}</red> has been eliminated!",
    val deathmatchWarning: String = "<red>⚠ Deathmatch starting in {seconds} seconds!",
    val noGamesAvailable: String = "<red>No games available right now.",
    val joinedGame: String = "<green>You joined the game on arena <gold>{arena}</gold>!"
)
