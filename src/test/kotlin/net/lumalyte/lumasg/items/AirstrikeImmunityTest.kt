package net.lumalyte.lumasg.items

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class AirstrikeImmunityTest {

    @Test
    fun `solo - caller immune to own strike when team is null`() {
        val caller = UUID.randomUUID()
        val result = AirstrikeItem.airstrikeImmuneUuids(null, caller)
        assertEquals(setOf(caller), result)
    }

    @Test
    fun `team - caller and teammates are immune`() {
        val caller = UUID.randomUUID()
        val mate = UUID.randomUUID()
        val result = AirstrikeItem.airstrikeImmuneUuids(listOf(caller, mate), caller)
        assertEquals(setOf(caller, mate), result)
    }
}
