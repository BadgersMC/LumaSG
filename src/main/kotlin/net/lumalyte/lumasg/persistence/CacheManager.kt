package net.lumalyte.lumasg.persistence

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Generic time-based cache with configurable TTL and max size.
 * Thread-safe via ConcurrentHashMap.
 */
class CacheManager<K : Any, V : Any>(
    private val ttl: Duration = Duration.ofMinutes(5),
    private val maxSize: Int = 1000
) {
    private data class CacheEntry<V>(val value: V, val expiresAt: Instant)

    private val store = ConcurrentHashMap<K, CacheEntry<V>>()

    /** Get a cached value, or null if missing/expired. */
    fun get(key: K): V? {
        val entry = store[key] ?: return null
        if (Instant.now().isAfter(entry.expiresAt)) {
            store.remove(key)
            return null
        }
        return entry.value
    }

    /** Get a cached value, or compute and cache it if missing/expired. */
    fun getOrPut(key: K, compute: () -> V): V {
        get(key)?.let { return it }
        val value = compute()
        put(key, value)
        return value
    }

    /** Cache a value with the default TTL. */
    fun put(key: K, value: V) {
        if (store.size >= maxSize) evictExpired()
        store[key] = CacheEntry(value, Instant.now().plus(ttl))
    }

    /** Cache a value with a custom TTL. */
    fun put(key: K, value: V, customTtl: Duration) {
        if (store.size >= maxSize) evictExpired()
        store[key] = CacheEntry(value, Instant.now().plus(customTtl))
    }

    /** Remove a specific key. */
    fun invalidate(key: K) {
        store.remove(key)
    }

    /** Remove all cached entries. */
    fun invalidateAll() {
        store.clear()
    }

    /** Number of entries (including possibly expired). */
    fun size(): Int = store.size

    /** Remove all expired entries. */
    fun evictExpired() {
        val now = Instant.now()
        store.entries.removeIf { now.isAfter(it.value.expiresAt) }
    }
}
