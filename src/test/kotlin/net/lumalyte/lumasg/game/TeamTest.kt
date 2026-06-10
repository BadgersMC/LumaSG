package net.lumalyte.lumasg.game

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TeamTest {

    @Test
    fun `team with members and not eliminated is alive`() {
        val team = Team(id = 1, members = mutableListOf(UUID.randomUUID()))
        assertTrue(team.isAlive, "team with members and not eliminated should be alive")
    }

    @Test
    fun `eliminated team is not alive`() {
        val team = Team(id = 1, members = mutableListOf(UUID.randomUUID()))
        team.eliminate()
        assertFalse(team.isAlive, "eliminated team should not be alive")
    }

    @Test
    fun `empty non-eliminated team is not alive`() {
        val team = Team(id = 2)
        assertFalse(team.isAlive, "team with no members should not be alive")
    }
}
