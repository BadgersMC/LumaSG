package net.lumalyte.lumasg.game.managers;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.game.GamePlayerManager;
import net.lumalyte.lumasg.util.messaging.MiniMessageUtils;

/**
 * Manages celebration effects and displays for game events.
 * Handles victory celebrations, firework shows, and winner announcements.
 */
public class CelebrationManager {
    private final LumaSG plugin;
    private final GamePlayerManager playerManager;
    private final Logger logger;
    private final Map<Integer, BukkitTask> activeTasks = new ConcurrentHashMap<>();

    public CelebrationManager(LumaSG plugin, GamePlayerManager playerManager) {
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.logger = plugin.getLogger();
    }

    /**
     * Handles the celebration for a game winner.
     */
    public void celebrateWinner(Player winner) {
        celebrateWinner(winner, null);
    }

    /**
     * Handles the celebration for a game winner with an optional death message.
     */
    public void celebrateWinner(Player winner, Component deathMessage) {
        // Get winner announcement configuration
        String titleFormat = plugin.getConfig().getString("rewards.winner-announcement.title", 
            "<gradient:gold:yellow:gold><bold>WINNER!</bold></gradient>");
        String subtitleFormat = plugin.getConfig().getString("rewards.winner-announcement.subtitle",
            "<gradient:#FFFF00:#FFA500:#FF4500><bold><player></bold></gradient>")
            .replace("<player>", winner.getName());

        Component titleComponent;
        Component subtitleComponent;
        try {
            titleComponent = MiniMessageUtils.parseMessage(titleFormat);
            subtitleComponent = MiniMessageUtils.parseMessage(subtitleFormat);
        } catch (Exception e) {
            // Fallback to plain text if parsing fails
            logger.warning("Failed to parse MiniMessage for winner title: " + e.getMessage());
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
            "<green>The game has ended! <gray><player> <green>is the winner!");

        // Create placeholders for the message
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", winner.getName());
        placeholders.put("kills", String.valueOf(playerManager.getPlayerKills(winner.getUniqueId())));

        Component message = MiniMessageUtils.parseMessage(winMsg, placeholders);
        broadcastMessage(message);

        // Handle winner rewards
        if (plugin.getConfig().getBoolean("rewards.enabled", true)) {
            giveWinnerRewards(winner);
        }
    }

    /**
     * Shows a title to all players and spectators.
     */
    private void showTitleToAll(Title title) {
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
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
            }
        }
        for (UUID spectatorId : playerManager.getSpectators()) {
            Player spectator = playerManager.getCachedPlayer(spectatorId);
            if (spectator != null) {
                spectator.playSound(spectator.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                spectator.playSound(spectator.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
            }
        }
    }

    /**
     * Gives rewards to the winner.
     */
    private void giveWinnerRewards(Player winner) {
        String winCommand = plugin.getConfig().getString("rewards.win-command", "");
        if (!winCommand.isEmpty()) {
            winCommand = winCommand.replace("<player>", winner.getName());
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), winCommand);
        }
    }

    /**
     * Broadcasts a message to all players and spectators.
     */
    private void broadcastMessage(Component message) {
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
    private void startWinnerFireworks(Player winner) {
        if (winner == null || !winner.isOnline()) {
            return;
        }

        Random random = new Random();
        AtomicInteger fireworkCount = new AtomicInteger(0);
        int maxFireworks = plugin.getConfig().getInt("rewards.winner-announcement.firework-count", 20);

        // Firework colors and types
        Color[] colors = {
            Color.RED, Color.BLUE, Color.GREEN,
            Color.YELLOW, Color.PURPLE, Color.ORANGE,
            Color.AQUA, Color.FUCHSIA, Color.LIME
        };

        FireworkEffect.Type[] types = {
            FireworkEffect.Type.BALL, FireworkEffect.Type.BALL_LARGE,
            FireworkEffect.Type.STAR, FireworkEffect.Type.BURST,
            FireworkEffect.Type.CREEPER
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
                Location fireworkLoc = winner.getLocation().clone().add(
                    random.nextDouble() * 6 - 3, // X: -3 to +3
                    random.nextDouble() * 4 + 2, // Y: 2 to 6 (above ground)
                    random.nextDouble() * 6 - 3  // Z: -3 to +3
                );

                // Create firework
                Firework firework = fireworkLoc.getWorld().spawn(fireworkLoc, Firework.class);
                FireworkMeta meta = firework.getFireworkMeta();

                // Make firework non-damaging by setting metadata
                firework.setPersistent(false);
                firework.setMetadata("celebration_firework", new FixedMetadataValue(plugin, true));

                // Random firework effect
                Color color1 = colors[random.nextInt(colors.length)];
                Color color2 = colors[random.nextInt(colors.length)];
                FireworkEffect.Type type = types[random.nextInt(types.length)];

                FireworkEffect effect = FireworkEffect.builder()
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
     * Cleanup method to cancel all active tasks.
     */
    public void cleanup() {
        for (BukkitTask task : activeTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        activeTasks.clear();
    }
} 
