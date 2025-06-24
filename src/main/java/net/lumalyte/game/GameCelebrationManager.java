package net.lumalyte.game;

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
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages celebration and winner announcement functionality for a game instance.
 * Handles fireworks, pixel art, winner messages, and rewards.
 */
public class GameCelebrationManager {
    private final @NotNull LumaSG plugin;
    private final @NotNull GamePlayerManager playerManager;
    
    /** Debug logger instance for game celebration management */
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    // Memory management: Track scheduled tasks for cleanup
    private final @NotNull Map<Integer, BukkitTask> activeTasks = new ConcurrentHashMap<>();
    
    // Reusable HTTP client with proper resource management
    private final @NotNull OkHttpClient httpClient;
    
    // Skin caching to reduce API calls and improve performance
    private final @NotNull Map<UUID, CachedSkinData> skinCache = new ConcurrentHashMap<>();
    
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
        
        boolean isExpired(int cacheMinutes) {
            return Instant.now().isAfter(cachedAt.plus(Duration.ofMinutes(cacheMinutes)));
        }
    }
    
    public GameCelebrationManager(@NotNull LumaSG plugin, @NotNull GamePlayerManager playerManager) {
        this.plugin = plugin;
        this.playerManager = playerManager;
        
        // Initialize HTTP client with timeouts and connection pooling
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .connectionPool(new okhttp3.ConnectionPool(5, 5, java.util.concurrent.TimeUnit.MINUTES))
            .build();
        
        // Initialize debug logger
        this.logger = plugin.getDebugLogger().forContext("GameCelebrationManager");
    }
    
    /**
     * Handles the celebration for a game winner.
     */
    public void celebrateWinner(@NotNull Player winner) {
        // Get winner announcement configuration
        boolean usePixelArt = plugin.getConfig().getBoolean("rewards.winner-announcement.use-pixel-art", true);
        String titleFormat = plugin.getConfig().getString("rewards.winner-announcement.title", 
            "<gradient:gold:yellow:gold><bold>WINNER!</bold></gradient>");
        String subtitleFormat = plugin.getConfig().getString("rewards.winner-announcement.subtitle",
            "<gradient:#FFFF00:#FFA500:#FF4500><bold><player></bold></gradient>")
            .replace("<player>", winner.getName());
        
        // Add debug logging for MiniMessage parsing
        logger.debug("Parsing winner title: " + titleFormat);
        logger.debug("Parsing winner subtitle: " + subtitleFormat);
        
        Component titleComponent;
        Component subtitleComponent;
        try {
            titleComponent = MiniMessageUtils.parseMessage(titleFormat);
            subtitleComponent = MiniMessageUtils.parseMessage(subtitleFormat);
            
            logger.debug("Successfully parsed winner title and subtitle");
        } catch (Exception e) {
            // Fallback to plain text if parsing fails
            logger.warn("Failed to parse MiniMessage for winner title: " + e.getMessage());
            titleComponent = Component.text("WINNER!", NamedTextColor.GOLD, TextDecoration.BOLD);
            subtitleComponent = Component.text(winner.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD);
        }
        
        Title winnerTitle = Title.title(
            titleComponent,
            subtitleComponent,
            Title.Times.times(
                Duration.ofMillis(1000),  // Fade in
                Duration.ofMillis(3000),  // Stay
                Duration.ofMillis(1000)   // Fade out
            )
        );
        
        // Show title to all players and spectators
        showTitleToAll(winnerTitle);
        
        // Play victory sounds
        playVictorySounds();
        
        // Start rainbow fireworks if enabled
        if (plugin.getConfig().getBoolean("rewards.winner-announcement.fireworks", true)) {
            startWinnerFireworks(winner);
        }
        
        // Get and format the winner message
        String winMsg = plugin.getConfig().getString("rewards.winner-announcement.message", 
            "<green>The game has ended! <gray><player> <green>is the winner and has been awarded <yellow>1000 Mob Coins<green>!");
        
        // Create placeholders for the message
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", winner.getName());
        placeholders.put("kills", String.valueOf(playerManager.getPlayerKills(winner.getUniqueId())));
        placeholders.put("mobcoins", String.valueOf(plugin.getConfig().getInt("rewards.mob-coins", 1000)));
        
        Component message = MiniMessageUtils.parseMessage(winMsg, placeholders);
        broadcastMessage(message);
        
        // Handle winner rewards
        if (plugin.getConfig().getBoolean("rewards.enabled", true)) {
            giveWinnerRewards(winner);
        }
        
        // Show pixel art if enabled
        if (usePixelArt && plugin.getConfig().getBoolean("rewards.winner-announcement.pixel-art.enabled", true)) {
            showWinnerPixelArt(winner);
        }
    }
    
    /**
     * Handles celebration when there's no winner.
     */
    public void celebrateNoWinner() {
        String noWinnerMsg = plugin.getConfig().getString("messages.game-end", "<red>Game Over! No winner!");
        broadcastMessage(MiniMessageUtils.parseMessage(noWinnerMsg));
    }
    
    /**
     * Shows a title to all players and spectators.
     */
    private void showTitleToAll(@NotNull Title title) {
        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                player.showTitle(title);
            }
        }
        for (UUID spectatorId : playerManager.getSpectators()) {
            Player spectator = playerManager.getCachedPlayer(spectatorId);
            if (spectator != null) {
                spectator.showTitle(title);
            }
        }
    }
    
    /**
     * Plays victory sounds to all players and spectators.
     */
    private void playVictorySounds() {
        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
            }
        }
        for (UUID spectatorId : playerManager.getSpectators()) {
            Player spectator = playerManager.getCachedPlayer(spectatorId);
            if (spectator != null) {
                spectator.playSound(spectator.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                spectator.playSound(spectator.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
            }
        }
    }
    
    /**
     * Gives rewards to the winner.
     */
    private void giveWinnerRewards(@NotNull Player winner) {
        String winCommand = plugin.getConfig().getString("rewards.win-command", "");
        if (!winCommand.isEmpty()) {
            winCommand = winCommand.replace("<player>", winner.getName());
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), winCommand);
        }
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
     * Creates a rainbow fireworks display around the winner.
     */
    private void startWinnerFireworks(@NotNull Player winner) {
        if (winner == null || !winner.isOnline()) {
            return;
        }
        
        Random random = new Random();
        AtomicInteger fireworkCount = new AtomicInteger(0);
        int maxFireworks = plugin.getConfig().getInt("rewards.winner-announcement.firework-count", 20);
        
        // Firework colors and types
        org.bukkit.Color[] colors = {
            org.bukkit.Color.RED, org.bukkit.Color.BLUE, org.bukkit.Color.GREEN,
            org.bukkit.Color.YELLOW, org.bukkit.Color.PURPLE, org.bukkit.Color.ORANGE,
            org.bukkit.Color.AQUA, org.bukkit.Color.FUCHSIA, org.bukkit.Color.LIME
        };
        
        org.bukkit.FireworkEffect.Type[] types = {
            org.bukkit.FireworkEffect.Type.BALL, org.bukkit.FireworkEffect.Type.BALL_LARGE,
            org.bukkit.FireworkEffect.Type.STAR, org.bukkit.FireworkEffect.Type.BURST,
            org.bukkit.FireworkEffect.Type.CREEPER
        };
        
        // Schedule fireworks to launch over 5 seconds
        final int[] taskIdRef = new int[1];
        BukkitTask fireworkTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (fireworkCount.get() >= maxFireworks || !winner.isOnline()) {
                BukkitTask task = activeTasks.remove(taskIdRef[0]);
                if (task != null) {
                    task.cancel();
                }
                return;
            }
            
            // Launch 2-4 fireworks per tick
            int fireworksThisTick = random.nextInt(3) + 2;
            for (int i = 0; i < fireworksThisTick && fireworkCount.get() < maxFireworks; i++) {
                // Random position around the winner
                org.bukkit.Location fireworkLoc = winner.getLocation().clone().add(
                    random.nextDouble() * 6 - 3, // X: -3 to +3
                    random.nextDouble() * 4 + 2, // Y: 2 to 6 (above ground)
                    random.nextDouble() * 6 - 3  // Z: -3 to +3
                );
                
                // Create firework
                org.bukkit.entity.Firework firework = fireworkLoc.getWorld().spawn(fireworkLoc, org.bukkit.entity.Firework.class);
                org.bukkit.inventory.meta.FireworkMeta meta = firework.getFireworkMeta();
                
                // Random firework effect
                org.bukkit.Color color1 = colors[random.nextInt(colors.length)];
                org.bukkit.Color color2 = colors[random.nextInt(colors.length)];
                org.bukkit.FireworkEffect.Type type = types[random.nextInt(types.length)];
                
                org.bukkit.FireworkEffect effect = org.bukkit.FireworkEffect.builder()
                    .withColor(color1)
                    .withFade(color2)
                    .with(type)
                    .trail(random.nextBoolean())
                    .flicker(random.nextBoolean())
                    .build();
                
                meta.addEffect(effect);
                meta.setPower(random.nextInt(2) + 1); // Power 1-2
                firework.setFireworkMeta(meta);
                
                fireworkCount.incrementAndGet();
            }
        }, 0L, 5L); // Run every 5 ticks (0.25 seconds)
        
        taskIdRef[0] = fireworkTask.getTaskId();
        activeTasks.put(taskIdRef[0], fireworkTask);
        
        // Cancel the task after 5 seconds
        BukkitTask cancelTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            BukkitTask task = activeTasks.remove(taskIdRef[0]);
            if (task != null) {
                task.cancel();
            }
        }, 100L); // 5 seconds (100 ticks)
        
        activeTasks.put(cancelTask.getTaskId(), cancelTask);
    }
    
    /**
     * Shows the winner's face as pixel art in chat.
     * Uses cached skin data or fetches from API with fallback support.
     */
    private void showWinnerPixelArt(@NotNull Player winner) {
        int size = plugin.getConfig().getInt("rewards.winner-announcement.pixel-art.size", 8);
        String character = plugin.getConfig().getString("rewards.winner-announcement.pixel-art.character", "██");
        
        logger.info("Attempting to show pixel art for " + winner.getName());
        
        // Try to get cached skin first
        BufferedImage cachedImage = getCachedSkin(winner.getUniqueId());
        if (cachedImage != null) {
            logger.debug("Using cached skin for " + winner.getName());
            displayPixelArt(winner, cachedImage, character, size);
            return;
        }
        
        // Submit async task to fetch and display pixel art
        BukkitTask pixelArtTask = plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            BufferedImage image = fetchPlayerSkin(winner.getUniqueId(), true);
            
            if (image != null) {
                // Cache the image for future use
                if (plugin.getConfig().getBoolean("rewards.winner-announcement.pixel-art.cache-enabled", true)) {
                    skinCache.put(winner.getUniqueId(), new CachedSkinData(image));
                }
                
                // Display the pixel art
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    displayPixelArt(winner, image, character, size);
                });
            } else {
                logger.warn("Failed to fetch skin for " + winner.getName() + " - skipping pixel art");
            }
        });
        
        activeTasks.put(pixelArtTask.getTaskId(), pixelArtTask);
    }
    
    /**
     * Fetches a player's skin from the configured API.
     */
    private BufferedImage fetchPlayerSkin(@NotNull UUID playerId, boolean forceRefresh) {
        // Check cache first if not forcing refresh
        if (!forceRefresh) {
            BufferedImage cached = getCachedSkin(playerId);
            if (cached != null) {
                return cached;
            }
        }
        
        String apiUrl = plugin.getConfig().getString("rewards.winner-announcement.pixel-art.api-url", 
            "https://crafatar.com/avatars/<uuid>?size=8&overlay")
            .replace("<uuid>", playerId.toString());
        
        logger.info("Fetching skin for " + playerId + " from: " + apiUrl);
        
        // Fetch from API (no fallback needed since crafatar is reliable)
        BufferedImage image = fetchSkinFromUrl(apiUrl, "crafatar");
        if (image != null) {
            return image;
        }
        
        logger.warn("Failed to fetch skin for player: " + playerId);
        return null;
    }
    
    /**
     * Fetches skin image from a specific URL.
     */
    private BufferedImage fetchSkinFromUrl(@NotNull String url, @NotNull String apiType) {
        Response response = null;
        try {
            // Create request with user agent and timeout
            Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "LumaSG-Plugin/1.0")
                .addHeader("Accept", "image/png, image/jpeg, image/*")
                .build();
            
            logger.debug("Sending request to " + apiType + " API: " + url);
            
            // Execute request with timeout
            response = httpClient.newCall(request).execute();
            logger.debug("Received response from " + apiType + " API: " + response.code() + " " + response.message());
            
            if (!response.isSuccessful()) {
                logger.debug(apiType + " API returned error: " + response.code() + " " + response.message());
                return null;
            }
            
            // Check content type
            String contentType = response.header("Content-Type");
            if (contentType == null || !contentType.startsWith("image/")) {
                logger.debug("Invalid content type from " + apiType + " API: " + contentType);
                return null;
            }
            
            // Read the image
            BufferedImage image = ImageIO.read(response.body().byteStream());
            if (image == null) {
                logger.debug("Failed to read image from " + apiType + " API response");
                return null;
            }
            
            logger.debug("Successfully read image from " + apiType + " API: " + image.getWidth() + "x" + image.getHeight());
            return image;
            
        } catch (Exception e) {
            logger.debug("Error fetching from " + apiType + " API: " + e.getMessage());
            return null;
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }
    
    /**
     * Gets cached skin data if available and not expired.
     */
    private BufferedImage getCachedSkin(@NotNull UUID playerId) {
        if (!plugin.getConfig().getBoolean("rewards.winner-announcement.pixel-art.cache-enabled", true)) {
            return null;
        }
        
        CachedSkinData cached = skinCache.get(playerId);
        if (cached == null) {
            return null;
        }
        
        int cacheMinutes = plugin.getConfig().getInt("rewards.winner-announcement.pixel-art.cache-duration-minutes", 30);
        if (cached.isExpired(cacheMinutes)) {
            skinCache.remove(playerId);
            return null;
        }
        
        return cached.image;
    }
    
    /**
     * Displays pixel art from a BufferedImage.
     */
    private void displayPixelArt(@NotNull Player winner, @NotNull BufferedImage image, @NotNull String character, int targetSize) {
        try {
            BufferedImage processedImage = image;
            
            // Scale image to desired size if needed
            if (image.getWidth() != targetSize || image.getHeight() != targetSize) {
                logger.debug("Scaling image from " + image.getWidth() + "x" + image.getHeight() + " to " + targetSize + "x" + targetSize);
                processedImage = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2d = processedImage.createGraphics();
                try {
                    g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    g2d.drawImage(image, 0, 0, targetSize, targetSize, null);
                } finally {
                    g2d.dispose();
                }
            }
            
            // Convert image to components with consistent character usage
            Component[] pixelArt = new Component[targetSize];
            String pixelChar = character.isEmpty() ? "⬛" : character;
            
            for (int y = 0; y < targetSize; y++) {
                Component row = Component.text("");
                for (int x = 0; x < targetSize; x++) {
                    int rgba = processedImage.getRGB(x, y);
                    int alpha = (rgba >> 24) & 0xFF;
                    
                    // Handle transparent pixels with consistent character
                    if (alpha < 128) {
                        row = row.append(Component.text(pixelChar, net.kyori.adventure.text.format.NamedTextColor.BLACK));
                        continue;
                    }
                    
                    int r = (rgba >> 16) & 0xFF;
                    int g = (rgba >> 8) & 0xFF;
                    int b = rgba & 0xFF;
                    
                    // Create hex color string
                    String hexColor = String.format("#%02x%02x%02x", r, g, b);
                    try {
                        net.kyori.adventure.text.format.TextColor color = net.kyori.adventure.text.format.TextColor.fromHexString(hexColor);
                        row = row.append(Component.text(pixelChar).color(color));
                    } catch (Exception e) {
                        // Fallback to white if color parsing fails
                        row = row.append(Component.text(pixelChar, net.kyori.adventure.text.format.NamedTextColor.WHITE));
                    }
                }
                pixelArt[y] = row;
            }
            
            // Show pixel art in chat with winner message positioned to the right
            logger.debug("Displaying pixel art for " + winner.getName());
            broadcastMessage(Component.text("")); // Empty line before pixel art
            
            // Calculate middle rows for message placement
            int middleRow = targetSize / 2;
            int bracketTopRow = middleRow - 1;
            int messageRow = middleRow;
            int bracketBottomRow = middleRow + 1;
            
            // Create winner message components
            Component topBracket = Component.text("   ╔══════════════════╗", net.kyori.adventure.text.format.NamedTextColor.GOLD);
            Component winnerMessage = Component.text("   ║ 👑 WINNER: " + winner.getName() + " 👑 ║", net.kyori.adventure.text.format.NamedTextColor.GOLD, net.kyori.adventure.text.format.TextDecoration.BOLD);
            Component bottomBracket = Component.text("   ╚══════════════════╝", net.kyori.adventure.text.format.NamedTextColor.GOLD);
            
            // Display pixel art with winner message positioned to the right
            for (int y = 0; y < targetSize; y++) {
                Component line = pixelArt[y];
                
                if (y == bracketTopRow) {
                    // Add top bracket to the right of the pixel art
                    line = line.append(topBracket);
                } else if (y == messageRow) {
                    // Add winner message to the right of the pixel art
                    line = line.append(winnerMessage);
                } else if (y == bracketBottomRow) {
                    // Add bottom bracket to the right of the pixel art
                    line = line.append(bottomBracket);
                }
                
                broadcastMessage(line);
            }
            broadcastMessage(Component.text("")); // Empty line after pixel art
            
            // Dispose of scaled image if we created one
            if (processedImage != image) {
                processedImage.flush();
            }
            
        } catch (Exception e) {
            logger.warn("Error displaying pixel art: " + e.getMessage());
            // Skip pixel art display if there's an error
        }
    }
    

    
    /**
     * Cleans up all resources used by this celebration manager.
     */
    public void cleanup() {
        // Cancel all active tasks
        for (BukkitTask task : activeTasks.values()) {
            if (!task.isCancelled()) {
                task.cancel();
            }
        }
        activeTasks.clear();
        
        // Shutdown HTTP client executor
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }
    
    /**
     * Pre-caches skin data for remaining players to improve winner announcement performance.
     * Should be called when the game reaches 3 players remaining.
     */
    public void preCachePlayerSkins() {
        if (!plugin.getConfig().getBoolean("rewards.winner-announcement.pixel-art.pre-cache-enabled", true)) {
            return;
        }
        
        logger.debug("Pre-caching player skins for remaining players");
        
        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                // Cache skin asynchronously in background
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    fetchPlayerSkin(player.getUniqueId(), false); // Don't force refresh
                });
            }
        }
    }
} 