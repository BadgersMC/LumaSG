package net.lumalyte.lumasg.discord

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.lumalyte.lumasg.domain.LootMode

object GameEmbed {
    fun gameStarted(arena: String, playerCount: Int, mode: String, lootMode: LootMode = LootMode.MODERN): MessageEmbed =
        EmbedBuilder()
            .setTitle("⚔ Game Started")
            .setColor(0x00FF88)
            .addField("Arena", arena, true)
            .addField("Players", playerCount.toString(), true)
            .addField("Mode", mode, true)
            .addField("Loot", lootMode.name.lowercase().replaceFirstChar { it.uppercase() }, true)
            .build()

    fun gameEnded(winner: String?, arena: String, duration: String): MessageEmbed =
        EmbedBuilder()
            .setTitle("🏆 Game Over")
            .setColor(0xFFD700)
            .addField("Winner", winner ?: "No winner", true)
            .addField("Arena", arena, true)
            .addField("Duration", duration, true)
            .build()
}
