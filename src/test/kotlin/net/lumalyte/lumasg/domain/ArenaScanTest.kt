package net.lumalyte.lumasg.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArenaScanTest {

    @Test
    fun `default radius 500 is rejected as unsafe`() {
        assertFalse(Arena.isChestScanRadiusSafe(500.0))
    }

    @Test
    fun `a sane arena-local radius is safe`() {
        assertTrue(Arena.isChestScanRadiusSafe(32.0))
        assertTrue(Arena.isChestScanRadiusSafe(Arena.MAX_CHEST_SCAN_RADIUS.toDouble()))
    }

    @Test
    fun `zero or negative radius is rejected`() {
        assertFalse(Arena.isChestScanRadiusSafe(0.0))
        assertFalse(Arena.isChestScanRadiusSafe(-5.0))
    }
}
