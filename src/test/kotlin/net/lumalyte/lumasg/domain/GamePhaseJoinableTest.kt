package net.lumalyte.lumasg.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GamePhaseJoinableTest {

    @Test
    fun `Waiting is joinable`() {
        assertTrue(GamePhase.Waiting.isJoinable(), "Waiting phase should be joinable")
    }

    @Test
    fun `Countdown is joinable`() {
        assertTrue(GamePhase.Countdown(10).isJoinable(), "Countdown phase should be joinable")
    }

    @Test
    fun `Grace is not joinable`() {
        assertFalse(GamePhase.Grace(1).isJoinable(), "Grace phase should not be joinable")
    }

    @Test
    fun `Active is not joinable`() {
        assertFalse(GamePhase.Active(1).isJoinable(), "Active phase should not be joinable")
    }

    @Test
    fun `Deathmatch is not joinable`() {
        assertFalse(GamePhase.Deathmatch(1).isJoinable(), "Deathmatch phase should not be joinable")
    }

    @Test
    fun `Ended is not joinable`() {
        assertFalse(GamePhase.Ended(null).isJoinable(), "Ended phase should not be joinable")
    }
}
