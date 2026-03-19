package net.lumalyte.lumasg.persistence

import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class CacheManagerTest {

    @Test
    fun `put and get returns cached value`() {
        val cache = CacheManager<String, Int>()
        cache.put("key", 42)
        assertEquals(42, cache.get("key"))
    }

    @Test
    fun `get returns null for missing key`() {
        val cache = CacheManager<String, Int>()
        assertNull(cache.get("missing"))
    }

    @Test
    fun `expired entry returns null`() {
        val cache = CacheManager<String, Int>(ttl = Duration.ofMillis(1))
        cache.put("key", 42)
        Thread.sleep(5)
        assertNull(cache.get("key"))
    }

    @Test
    fun `invalidate removes entry`() {
        val cache = CacheManager<String, Int>()
        cache.put("key", 42)
        cache.invalidate("key")
        assertNull(cache.get("key"))
    }

    @Test
    fun `invalidateAll clears all entries`() {
        val cache = CacheManager<String, Int>()
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)
        cache.invalidateAll()
        assertEquals(0, cache.size())
    }

    @Test
    fun `getOrPut computes and caches on miss`() {
        val cache = CacheManager<String, Int>()
        var computed = false
        val value = cache.getOrPut("key") {
            computed = true
            99
        }
        assertEquals(99, value)
        assert(computed)
        assertEquals(99, cache.get("key"))
    }

    @Test
    fun `getOrPut returns cached on hit`() {
        val cache = CacheManager<String, Int>()
        cache.put("key", 42)
        var computed = false
        val value = cache.getOrPut("key") {
            computed = true
            99
        }
        assertEquals(42, value)
        assert(!computed)
    }

    @Test
    fun `custom TTL overrides default`() {
        val cache = CacheManager<String, Int>(ttl = Duration.ofHours(1))
        cache.put("short", 1, Duration.ofMillis(1))
        Thread.sleep(5)
        assertNull(cache.get("short"))
    }

    @Test
    fun `evictExpired removes only expired entries`() {
        val cache = CacheManager<String, Int>(ttl = Duration.ofMillis(1))
        cache.put("expired", 1)
        Thread.sleep(5)
        // Add a fresh entry after the expired one
        val freshCache = CacheManager<String, Int>(ttl = Duration.ofHours(1))
        freshCache.put("expired", 1, Duration.ofMillis(1))
        freshCache.put("fresh", 2)
        Thread.sleep(5)
        freshCache.evictExpired()
        assertNull(freshCache.get("expired"))
        assertEquals(2, freshCache.get("fresh"))
    }

    @Test
    fun `size reflects current entry count`() {
        val cache = CacheManager<String, Int>()
        assertEquals(0, cache.size())
        cache.put("a", 1)
        assertEquals(1, cache.size())
        cache.put("b", 2)
        assertEquals(2, cache.size())
        cache.invalidate("a")
        assertEquals(1, cache.size())
    }

    @Test
    fun `put overwrites existing value`() {
        val cache = CacheManager<String, Int>()
        cache.put("key", 1)
        cache.put("key", 2)
        assertEquals(2, cache.get("key"))
        assertEquals(1, cache.size())
    }
}
