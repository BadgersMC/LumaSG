package net.lumalyte.game;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.lumalyte.LumaSG;
import net.lumalyte.util.DebugLogger;
import net.lumalyte.util.MiniMessageUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Handles death message formatting and display in games.
 */
public class GameDeathMessageManager {
    private final @NotNull LumaSG plugin;
    private final @NotNull DebugLogger.ContextualLogger logger;
    private final @NotNull GamePlayerManager playerManager;
    private final @NotNull OkHttpClient httpClient;
    private final @NotNull Map<UUID, CachedSkinData> skinCache = new ConcurrentHashMap<>();
    
    private final boolean enabled;
    private final String format;
    private final String finalTwoFormat;
	private final Map<String, List<String>> actionsByWeapon;
    
    private static final Title.Times TITLE_TIMES = Title.Times.times(
        Duration.ofMillis(500),  // Fade in
        Duration.ofMillis(2000), // Stay
        Duration.ofMillis(500)   // Fade out
    );

    /**
     * Cached skin data with timestamp for expiration.
     */
    private static class CachedSkinData {
        final BufferedImage image;
        final Instant cachedAt;

        CachedSkinData(BufferedImage image) {
            this.image = image;
            this.cachedAt = Instant.now();
        }

        boolean isExpired() {
            return Instant.now().isAfter(cachedAt.plus(Duration.ofMinutes(30)));
        }
    }
    
    public GameDeathMessageManager(@NotNull LumaSG plugin, @NotNull String gameId,
                                 @NotNull GamePlayerManager playerManager) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("GameDeathMessageManager-" + gameId);
        this.playerManager = playerManager;
        
        // Initialize HTTP client with timeouts and connection pooling
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .connectionPool(new okhttp3.ConnectionPool(5, 5, java.util.concurrent.TimeUnit.MINUTES))
            .build();
        
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("messages.death-messages");
		String winnerFormat;
		if (config == null) {
            this.enabled = true;
            this.format = "<dark_red>☠ <red><victim> <gray>was <action> <gray>by <killer><gray>! <yellow><remaining> players remain!";
            this.finalTwoFormat = "<dark_red>\n⚔ FINAL BATTLE ⚔\n<victim> vs <killer>\n<gray>Only one will survive!";
            winnerFormat = "<gold>\n⚔ VICTORY ⚔\n<winner> is victorious!";
            this.actionsByWeapon = createDefaultActions();
            logger.debug("Using default death message configuration");
            return;
        }
        
        this.enabled = config.getBoolean("enabled", true);
        this.format = config.getString("format", "<dark_red>☠ <red><victim> <gray>was <action> <gray>by <killer><gray>! <yellow><remaining> players remain!");
        this.finalTwoFormat = config.getString("final-two-format", "<dark_red>\n⚔ FINAL BATTLE ⚔\n<victim> vs <killer>\n<gray>Only one will survive!");
        winnerFormat = config.getString("winner-format", "<gold>\n⚔ VICTORY ⚔\n<winner> is victorious!");
        
        // Load weapon-specific actions
        Map<String, List<String>> tempActionsByWeapon = new HashMap<>();
        ConfigurationSection actionsSection = config.getConfigurationSection("actions");
        if (actionsSection != null) {
            for (String weaponType : actionsSection.getKeys(false)) {
                List<String> actions = actionsSection.getStringList(weaponType);
                if (!actions.isEmpty()) {
                    tempActionsByWeapon.put(weaponType, actions);
                }
            }
        }
        if (tempActionsByWeapon.isEmpty()) {
            tempActionsByWeapon.putAll(createDefaultActions());
        }
        this.actionsByWeapon = tempActionsByWeapon;
    }
    
    /**
     * Creates default death message actions by weapon type.
     */
    private @NotNull Map<String, List<String>> createDefaultActions() {
        Map<String, List<String>> defaults = new HashMap<>();
        defaults.put("sword", Arrays.asList("stabbed", "sliced", "shanked", "skewered"));
        defaults.put("axe", Arrays.asList("butchered", "chopped", "cleaved", "hacked"));
        defaults.put("bow", Arrays.asList("sniped", "shot", "bullseyed", "skewered"));
        defaults.put("crossbow", Arrays.asList("sniped", "shot", "bullseyed", "skewered"));
        defaults.put("trident", Arrays.asList("impaled", "speared", "harpooned"));
        defaults.put("explosive", Arrays.asList("blown up", "'sploded", "detonated", "bombed"));
        defaults.put("fist", Arrays.asList("punched", "knocked out", "defeated"));
        defaults.put("other", Arrays.asList("eliminated", "destroyed", "defeated"));
        return defaults;
    }
    
    /**
     * Determines the weapon type from the killer's held item.
     */
    private @NotNull String getWeaponType(@Nullable ItemStack weapon) {
        if (weapon == null || weapon.getType() == Material.AIR) {
            return "fist";
        }
        
        Material type = weapon.getType();
        String materialName = type.name();
        
        // Check weapon types using helper methods for clarity
        if (isSwordType(materialName)) return "sword";
        if (isAxeType(materialName)) return "axe";
        if (isBowType(type)) return "bow";
        if (isCrossbowType(type)) return "crossbow";
        if (isTridentType(type)) return "trident";
        if (isExplosiveType(type, materialName)) return "explosive";
        
        return "other";
    }
    
    /**
     * Checks if the material is a sword type.
     */
    private boolean isSwordType(@NotNull String materialName) {
        return materialName.endsWith("_SWORD");
    }
    
    /**
     * Checks if the material is an axe type.
     */
    private boolean isAxeType(@NotNull String materialName) {
        return materialName.endsWith("_AXE");
    }
    
    /**
     * Checks if the material is a bow.
     */
    private boolean isBowType(@NotNull Material type) {
        return type == Material.BOW;
    }
    
    /**
     * Checks if the material is a crossbow.
     */
    private boolean isCrossbowType(@NotNull Material type) {
        return type == Material.CROSSBOW;
    }
    
    /**
     * Checks if the material is a trident.
     */
    private boolean isTridentType(@NotNull Material type) {
        return type == Material.TRIDENT;
    }
    
    /**
     * Checks if the material is an explosive type.
     */
    private boolean isExplosiveType(@NotNull Material type, @NotNull String materialName) {
        return type == Material.TNT || 
               materialName.contains("TNT") || 
               materialName.contains("EXPLOSIVE");
    }
    
    /**
     * Gets a random action message for the given weapon type.
     */
    private @NotNull String getRandomAction(@NotNull String weaponType) {
        List<String> actions = actionsByWeapon.getOrDefault(weaponType, actionsByWeapon.get("other"));
        return actions.get(new Random().nextInt(actions.size()));
    }
    
    /**
     * Broadcasts a death message to all players in the game.
     */
    public void broadcastDeathMessage(@NotNull Player victim, @Nullable Player killer) {
        if (!enabled) return;
        
        int remainingPlayers = playerManager.getPlayerCount() - 1; // Subtract 1 for the player about to die
        
        // Handle final kill (2 players -> 1) - store death message for winner celebration
        if (remainingPlayers == 1) {
            broadcastFinalKillMessage(victim, killer);
            return;
        }
        
        // Handle final battle (3 players -> 2)
        if (remainingPlayers == 2) {
            broadcastFinalBattleMessage(victim, killer);
            return;
        }
        
        // Regular death message
        String weaponType = killer != null ? 
            getWeaponType(killer.getInventory().getItemInMainHand()) : "other";
        String action = getRandomAction(weaponType);
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("victim", victim.getName());
        placeholders.put("killer", killer != null ? killer.getName() : "the game");
        placeholders.put("action", action);
        placeholders.put("remaining", String.valueOf(remainingPlayers));
        
        Component message = MiniMessageUtils.parseMessage(format, placeholders);
        
        // Show death message with player heads
        showDeathMessageWithHeads(victim, killer, message);
    }
    
    /**
     * Shows a death message with player heads on either side.
     */
    private void showDeathMessageWithHeads(@NotNull Player victim, @Nullable Player killer, @NotNull Component message) {
        // Pre-fetch skins asynchronously
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            BufferedImage victimSkin = fetchPlayerSkin(victim.getUniqueId());
            BufferedImage killerSkin = killer != null ? fetchPlayerSkin(killer.getUniqueId()) : null;

            // Once skins are fetched, display the message on the main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                displayDeathMessageWithHeads(victim, killer, message, victimSkin, killerSkin);
            });
        });
    }
    
    /**
     * Displays the death message with player heads in chat.
     */
    private void displayDeathMessageWithHeads(@NotNull Player victim, @Nullable Player killer,
                                            @NotNull Component message, @Nullable BufferedImage victimSkin,
                                            @Nullable BufferedImage killerSkin) {
        int headSize = 8;
        Component[] killerHead = createHeadDisplay(killerSkin, headSize, "KILLER");
        Component[] victimHead = createHeadDisplay(victimSkin, headSize, "VICTIM");

        // First send the labels
        Component labelLine = Component.empty()
                .append(killerHead[0])
                .append(Component.text("     ").color(NamedTextColor.WHITE))
                .append(Component.text("     ").color(NamedTextColor.WHITE))
                .append(victimHead[0]);
        broadcastMessage(labelLine);

        // Then send each line of the heads with the message in the middle
        for (int i = 1; i < killerHead.length; i++) {
            Component line = Component.empty()
                    .append(killerHead[i])
                    .append(Component.text("  ").color(NamedTextColor.WHITE));

            // Add the message only on the middle line
            if (i == killerHead.length / 2) {
                line = line.append(message);
            } else {
                line = line.append(Component.text("     ").color(NamedTextColor.WHITE));
            }

            line = line.append(Component.text("  ").color(NamedTextColor.WHITE))
                    .append(victimHead[i]);

            broadcastMessage(line);
        }
    }
    
    /**
     * Creates a display of a player head using pixel art characters.
     */
    private Component[] createHeadDisplay(@Nullable BufferedImage image, int size, @NotNull String label) {
        Component[] lines = new Component[size + 1];
        
        // Add the label line first, in brackets
        lines[0] = Component.text("[" + label + "]")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);

        if (image == null) {
            // Fill with empty lines if no image
            for (int i = 1; i < lines.length; i++) {
                lines[i] = Component.empty();
            }
            return lines;
        }

        // Convert each row of pixels to text
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        
        for (int y = 0; y < size; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < size; x++) {
                // Map the display coordinates to image coordinates
                int imgX = x * imageWidth / size;
                int imgY = y * imageHeight / size;
                
                int rgb = image.getRGB(imgX, imgY);
                int alpha = (rgb >> 24) & 0xFF;
                row.append(alpha > 128 ? "⬛" : " ");
            }
            lines[y + 1] = Component.text(row.toString())
                    .color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false);
        }

        return lines;
    }
    
    /**
     * Fetches a player's skin from the configured API.
     */
    private BufferedImage fetchPlayerSkin(@NotNull UUID playerId) {
        // Check cache first if not forcing refresh
		BufferedImage cached = getCachedSkin(playerId);
		if (cached != null) {
			return cached;
		}

		String apiUrl = "https://crafatar.com/avatars/" + playerId.toString() + "?size=8&overlay";
        logger.debug("Fetching skin for " + playerId + " from: " + apiUrl);

        try {
            Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("User-Agent", "LumaSG-Plugin/1.0")
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.debug("Failed to fetch skin: " + response.code());
                    return null;
                }

				assert response.body() != null;
				BufferedImage image = ImageIO.read(response.body().byteStream());
                if (image != null) {
                    skinCache.put(playerId, new CachedSkinData(image));
                }
                return image;
            }
        } catch (Exception e) {
            logger.debug("Error fetching skin: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets cached skin data if available and not expired.
     */
    private BufferedImage getCachedSkin(@NotNull UUID playerId) {
        CachedSkinData cached = skinCache.get(playerId);
        if (cached == null) {
            return null;
        }

        if (cached.isExpired()) { // Cache for 30 minutes
            skinCache.remove(playerId);
            return null;
        }

        return cached.image;
    }
    
    /**
     * Broadcasts a message to all players and spectators.
     */
    private void broadcastMessage(@NotNull Component message) {
        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
            }
        }
        for (UUID spectatorId : playerManager.getSpectators()) {
            Player spectator = playerManager.getCachedPlayer(spectatorId);
            if (spectator != null) {
                spectator.sendMessage(message);
            }
        }
    }
    
    /**
     * Broadcasts a special message for the final battle (3 players -> 2).
     */
    private void broadcastFinalBattleMessage(@NotNull Player victim, @Nullable Player killer) {
        if (killer == null) return;
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("victim", victim.getName());
        placeholders.put("killer", killer.getName());
        
        Component message = MiniMessageUtils.parseMessage(finalTwoFormat, placeholders);
        showDeathMessageWithHeads(victim, killer, message);
    }
    
    /**
     * Handles the final kill message (2 players -> 1) and stores it for winner celebration.
     */
    private void broadcastFinalKillMessage(@NotNull Player victim, @Nullable Player killer) {
        if (killer == null) return;
        
        // Create the final kill death message
        String weaponType = getWeaponType(killer.getInventory().getItemInMainHand());
        String action = getRandomAction(weaponType);
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("victim", victim.getName());
        placeholders.put("killer", killer.getName());
        placeholders.put("action", action);
        
        // Use a modified format without the remaining players count
        String finalKillFormat = format.replace("<yellow><remaining> players remain!", "");
        Component deathMessage = MiniMessageUtils.parseMessage(finalKillFormat, placeholders);
        
        // Store the death message for the winner celebration
        // We'll need to access this when the celebration manager celebrates the winner
        Game game = plugin.getGameManager().getGameByPlayer(victim);
        if (game != null) {
            game.getCelebrationManager().setFinalKillMessage(deathMessage);
        }
        
        // Show the death message with heads
        showDeathMessageWithHeads(victim, killer, deathMessage);
    }
    


    /**
     * Pre-caches skin data for remaining players to improve performance.
     */
    public void preCachePlayerSkins() {
        logger.debug("Pre-caching player skins for remaining players");

        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                // Cache skin asynchronously in background
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    fetchPlayerSkin(player.getUniqueId());
                });
            }
        }
    }

    /**
     * Cleans up resources used by this manager.
     */
    public void cleanup() {
        // Clear skin cache
        skinCache.clear();

        // Shutdown HTTP client
		httpClient.dispatcher().executorService().shutdown();
		httpClient.connectionPool().evictAll();
	}
} 