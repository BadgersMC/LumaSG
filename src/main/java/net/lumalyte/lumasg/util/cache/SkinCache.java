package net.lumalyte.lumasg.util.cache;  import net.lumalyte.lumasg.util.core.DebugLogger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.lumalyte.lumasg.LumaSG;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * High-performance skin caching system using Caffeine
 * Reduces API calls and improves performance for skin-related operations
 */
public class SkinCache {
    
    private static final Cache<UUID, String> SKIN_CACHE = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofHours(6))
            .recordStats()
            .build();
    
    private static final Cache<UUID, String> TEXTURE_CACHE = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofHours(6))
            .recordStats()
            .build();

	private static DebugLogger.ContextualLogger logger;
    
    /**
     * Initializes the skin cache with plugin instance
     * 
     * @param pluginInstance The plugin instance
     */
    public static void initialize(LumaSG pluginInstance) {
		logger = pluginInstance.getDebugLogger().forContext("SkinCache");
    }
    
    /**
     * Gets a player's cached skin URL, fetching if not cached
     * 
     * @param uuid The player's UUID
     * @return CompletableFuture containing the skin URL
     */
    public static CompletableFuture<String> getCachedSkin(UUID uuid) {
        String cached = SKIN_CACHE.getIfPresent(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    // Use the player's skin URL directly from Paper API
                    String skinUrl = player.getPlayerProfile().getTextures().getSkin() != null ? 
                        player.getPlayerProfile().getTextures().getSkin().toString() : null;
                    
                    if (skinUrl != null) {
                        SKIN_CACHE.put(uuid, skinUrl);
                        return skinUrl;
                    }
                }
                
                // Fallback to default skin
                String defaultSkin = "https://textures.minecraft.net/texture/default_steve";
                SKIN_CACHE.put(uuid, defaultSkin);
                return defaultSkin;
            } catch (Exception e) {
                if (logger != null) {
                    logger.error("Failed to fetch skin for UUID: " + uuid, e);
                }
                String defaultSkin = "https://textures.minecraft.net/texture/default_steve";
                SKIN_CACHE.put(uuid, defaultSkin);
                return defaultSkin;
            }
        });
    }
    
    /**
     * Gets a player's cached skin texture URL
     * 
     * @param uuid The player's UUID
     * @return CompletableFuture containing the texture URL or null if not available
     */
    public static CompletableFuture<String> getCachedTexture(UUID uuid) {
        String cached = TEXTURE_CACHE.getIfPresent(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        
        return getCachedSkin(uuid).thenApply(skinUrl -> {
            try {
                TEXTURE_CACHE.put(uuid, skinUrl);
                return skinUrl;
            } catch (Exception e) {
                if (logger != null) {
                    logger.error("Failed to cache texture for UUID: " + uuid, e);
                }
                return null;
            }
        });
    }
    
    /**
     * Preloads skin data for a player when they join
     * 
     * @param player The player to preload skin data for
     */
    public static void preloadSkin(Player player) {
        UUID uuid = player.getUniqueId();
        if (SKIN_CACHE.getIfPresent(uuid) == null) {
            getCachedSkin(uuid); // Trigger async loading
        }
    }
    
    /**
     * Invalidates cached skin data for a player
     * 
     * @param uuid The player's UUID
     */
    public static void invalidateSkin(UUID uuid) {
        SKIN_CACHE.invalidate(uuid);
        TEXTURE_CACHE.invalidate(uuid);
    }
    
    /**
     * Gets cache statistics for monitoring
     * 
     * @return String containing cache statistics
     */
    public static String getCacheStats() {
        return String.format("Skin Cache - Size: %d, Hit Rate: %.2f%%, Texture Cache - Size: %d, Hit Rate: %.2f%%",
                SKIN_CACHE.estimatedSize(),
                SKIN_CACHE.stats().hitRate() * 100,
                TEXTURE_CACHE.estimatedSize(),
                TEXTURE_CACHE.stats().hitRate() * 100);
    }
    
    /**
     * Clears all cached skin data
     */
    public static void clearCache() {
        SKIN_CACHE.invalidateAll();
        TEXTURE_CACHE.invalidateAll();
    }
} 
