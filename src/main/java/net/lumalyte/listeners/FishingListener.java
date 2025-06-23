package net.lumalyte.listeners;

import net.lumalyte.LumaSG;
import net.lumalyte.game.Game;
import net.lumalyte.game.GameState;
import net.lumalyte.util.DebugLogger;
import net.lumalyte.util.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * Listener for fishing events in Survival Games.
 * 
 * <p>This class handles special fishing loot during Survival Games matches,
 * allowing players to catch unique items that can help them in the game.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class FishingListener implements Listener {
    private final @NotNull LumaSG plugin;
    private final @NotNull Random random = new Random();
    private @Nullable ConfigurationSection fishingConfig;
    private @Nullable File fishingFile;
    
    /** The debug logger instance for this fishing listener */
    private final @NotNull DebugLogger.ContextualLogger logger;

    /**
     * Constructs a new FishingListener.
     * 
     * @param plugin The plugin instance
     */
    public FishingListener(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.fishingFile = new File(plugin.getDataFolder(), "fishing.yml");
        this.logger = plugin.getDebugLogger().forContext("FishingListener");
        
        // Save default fishing configuration if it doesn't exist
        if (!fishingFile.exists()) {
            plugin.saveResource("fishing.yml", false);
        }
        
        loadFishingConfig();
    }
    
    /**
     * Loads the fishing loot configuration from fishing.yml
     */
    private void loadFishingConfig() {
        try {
            if (fishingFile == null || !fishingFile.exists()) {
                logger.warn("fishing.yml not found, fishing loot will not be available");
                return;
            }
            
            FileConfiguration fishingYml = YamlConfiguration.loadConfiguration(fishingFile);
            fishingConfig = fishingYml;
            
            if (fishingConfig == null) {
                logger.warn("Failed to load fishing.yml");
            } else {
                logger.info("Fishing loot configuration loaded successfully");
            }
        } catch (Exception e) {
            logger.warn("Error loading fishing loot configuration: " + e.getMessage());
        }
    }

    /**
     * Handles player fishing events.
     * 
     * <p>This method intercepts successful fish catches during active games
     * and potentially replaces the caught item with a special item from the
     * fishing loot table.</p>
     * 
     * @param event The fishing event
     */
    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        // Only handle successful catches
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        Player player = event.getPlayer();
        Game game = plugin.getGameManager().getGameByPlayer(player);

        // Only handle fishing in active games
        if (game == null || (game.getState() != GameState.ACTIVE && game.getState() != GameState.DEATHMATCH)) {
            return;
        }

        // Check if fishing configuration is loaded
        if (fishingConfig == null) {
            // Try to reload the configuration
            loadFishingConfig();
            
            if (fishingConfig == null) {
                logger.warn("Fishing loot configuration not available");
                return;
            }
        }

        // Debug logging
        logger.debug("Player " + player.getName() + " caught something while fishing in an active game");

        // Check if player catches a special item
        double specialChance = fishingConfig.getDouble("special_catch_chance", 25.0);
        double roll = random.nextDouble() * 100;
        if (roll > specialChance) {
            logger.debug("Player didn't get a special item (rolled " + roll + " > " + specialChance + ")");
            return; // Normal fish catch
        }

        // Cancel the original fish catch and prevent default item
        event.setCancelled(true);
        if (event.getCaught() != null) {
            event.getCaught().remove();
        }

        // Get all possible special items
        ConfigurationSection itemsSection = fishingConfig.getConfigurationSection("items");
        if (itemsSection == null) {
            logger.warn("No items section found in fishing.yml");
            return;
        }

        // Calculate total weight of all items
        double totalWeight = 0;
        Map<String, Double> chances = new HashMap<>();
        for (String key : itemsSection.getKeys(false)) {
            double chance = itemsSection.getDouble(key + ".chance", 0);
            chances.put(key, chance);
            totalWeight += chance;
        }

        // Select a random item based on weights
        double randomValue = random.nextDouble() * totalWeight;
        String selectedItem = null;
        double currentWeight = 0;

        for (Map.Entry<String, Double> entry : chances.entrySet()) {
            currentWeight += entry.getValue();
            if (randomValue <= currentWeight) {
                selectedItem = entry.getKey();
                break;
            }
        }

        if (selectedItem == null) {
            logger.warn("Failed to select a random fishing item");
            return;
        }

        // Get the item configuration
        ConfigurationSection itemConfig = itemsSection.getConfigurationSection(selectedItem);
        if (itemConfig == null) {
            logger.warn("Invalid item config for: " + selectedItem);
            return;
        }

        try {
            // Create the special item using ItemUtils
            ItemStack item = ItemUtils.createItemFromConfig(plugin, itemConfig, selectedItem);
            if (item == null) {
                logger.warn("Failed to create item: " + selectedItem);
                return;
            }
            
            // Set random amount if specified
            if (itemConfig.contains("min-amount") && itemConfig.contains("max-amount")) {
                int minAmount = itemConfig.getInt("min-amount", 1);
                int maxAmount = itemConfig.getInt("max-amount", 1);
                if (maxAmount > minAmount) {
                    item.setAmount(random.nextInt(maxAmount - minAmount + 1) + minAmount);
                } else {
                    item.setAmount(minAmount);
                }
            }

            // Give the item to the player
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            if (!overflow.isEmpty()) {
                // Drop items that didn't fit in inventory
                for (ItemStack drop : overflow.values()) {
                    player.getWorld().dropItem(player.getLocation(), drop);
                }
            }

            // Send message to player
            Component itemName = item.getItemMeta().displayName();
            player.sendMessage(Component.text()
                .append(Component.text("You caught ", NamedTextColor.AQUA))
                .append(itemName)
                .append(Component.text("!", NamedTextColor.AQUA))
                .build());

            // Play effects
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            player.spawnParticle(org.bukkit.Particle.SPLASH, player.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);

            logger.debug("Player " + player.getName() + " caught special item: " + selectedItem);
        } catch (Exception e) {
            logger.warn("Error creating special fishing item: " + e.getMessage());
        }
    }
} 