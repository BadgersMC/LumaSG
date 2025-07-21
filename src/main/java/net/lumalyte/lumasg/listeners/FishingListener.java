package net.lumalyte.lumasg.listeners;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.game.Game;
import net.lumalyte.lumasg.game.GameState;
import net.lumalyte.lumasg.util.core.DebugLogger;
import net.lumalyte.lumasg.util.core.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
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
    private final @Nullable File fishingFile;
    
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

			fishingConfig = YamlConfiguration.loadConfiguration(fishingFile);

			logger.info("Fishing loot configuration loaded successfully");
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
        if (!isValidGameState(game)) {
            return;
        }

        // Check if fishing configuration is loaded
        if (!ensureFishingConfigLoaded()) {
            return;
        }

        // Debug logging
        logger.debug("Player " + player.getName() + " caught something while fishing in an active game");

        // Check if player catches a special item
        if (!shouldGiveSpecialItem()) {
            return; // Normal fish catch
        }

        // Process special item catch
        processSpecialItemCatch(event, player);
    }
    
    /**
     * Checks if the game state is valid for special fishing.
     */
    private boolean isValidGameState(@Nullable Game game) {
        return game != null && 
               (game.getState() == GameState.ACTIVE || game.getState() == GameState.DEATHMATCH);
    }
    
    /**
     * Ensures the fishing configuration is loaded.
     */
    private boolean ensureFishingConfigLoaded() {
        if (fishingConfig == null) {
            // Try to reload the configuration
            loadFishingConfig();
            
            if (fishingConfig == null) {
                logger.warn("Fishing loot configuration not available");
                return false;
            }
        }
        return true;
    }
    
    /**
     * Determines if the player should receive a special item based on chance.
     */
    private boolean shouldGiveSpecialItem() {
		assert fishingConfig != null;
		double specialChance = fishingConfig.getDouble("special_catch_chance", 25.0);
        double roll = random.nextDouble() * 100;
        if (roll > specialChance) {
            logger.debug("Player didn't get a special item (rolled " + roll + " > " + specialChance + ")");
            return false;
        }
        return true;
    }
    
    /**
     * Processes the special item catch by canceling the event and giving the player a special item.
     */
    private void processSpecialItemCatch(@NotNull PlayerFishEvent event, @NotNull Player player) {
        // Cancel the original fish catch and prevent default item
        event.setCancelled(true);
        if (event.getCaught() != null) {
            event.getCaught().remove();
        }

        String selectedItem = selectRandomSpecialItem();
        if (selectedItem == null) {
            return;
        }

        createAndGiveSpecialItem(player, selectedItem);
    }
    
    /**
     * Selects a random special item based on configured weights.
     */
    private @Nullable String selectRandomSpecialItem() {
		assert fishingConfig != null;
		ConfigurationSection itemsSection = fishingConfig.getConfigurationSection("items");
        if (itemsSection == null) {
            logger.warn("No items section found in fishing.yml");
            return null;
        }

        // Calculate total weight of all items
        Map<String, Double> chances = calculateItemChances(itemsSection);
        double totalWeight = chances.values().stream().mapToDouble(Double::doubleValue).sum();

        // Select a random item based on weights
        return selectItemByWeight(chances, totalWeight);
    }
    
    /**
     * Calculates the chances for all items in the configuration.
     */
    private @NotNull Map<String, Double> calculateItemChances(@NotNull ConfigurationSection itemsSection) {
        Map<String, Double> chances = new HashMap<>();
        for (String key : itemsSection.getKeys(false)) {
            double chance = itemsSection.getDouble(key + ".chance", 0);
            chances.put(key, chance);
        }
        return chances;
    }
    
    /**
     * Selects an item based on weighted random selection.
     */
    private @Nullable String selectItemByWeight(@NotNull Map<String, Double> chances, double totalWeight) {
        double randomValue = random.nextDouble() * totalWeight;
        double currentWeight = 0;

        for (Map.Entry<String, Double> entry : chances.entrySet()) {
            currentWeight += entry.getValue();
            if (randomValue <= currentWeight) {
                return entry.getKey();
            }
        }
        
        logger.warn("Failed to select a random fishing item");
        return null;
    }
    
    /**
     * Creates and gives the special item to the player.
     */
    private void createAndGiveSpecialItem(@NotNull Player player, @NotNull String selectedItem) {
		assert fishingConfig != null;
		ConfigurationSection itemsSection = fishingConfig.getConfigurationSection("items");
        if (itemsSection == null) {
            return;
        }
        
        ConfigurationSection itemConfig = itemsSection.getConfigurationSection(selectedItem);
        if (itemConfig == null) {
            logger.warn("Invalid item config for: " + selectedItem);
            return;
        }

        try {
            ItemStack item = createSpecialFishingItem(itemConfig, selectedItem);
            if (item == null) {
                return;
            }
            
            giveItemToPlayer(player, item);
            notifyPlayerOfCatch(player, item);
            playFishingEffects(player);
            
            logger.debug("Player " + player.getName() + " caught special item: " + selectedItem);
        } catch (Exception e) {
            logger.warn("Error creating special fishing item: " + e.getMessage());
        }
    }
    
    /**
     * Creates the special fishing item from configuration.
     */
    private @Nullable ItemStack createSpecialFishingItem(@NotNull ConfigurationSection itemConfig, @NotNull String selectedItem) {
        ItemStack item = ItemUtils.createItemFromConfig(plugin, itemConfig, selectedItem);
        if (item == null) {
            logger.warn("Failed to create item: " + selectedItem);
            return null;
        }
        
        // Set random amount if specified
        setRandomItemAmount(item, itemConfig);
        return item;
    }
    
    /**
     * Sets a random amount for the item if min/max amounts are configured.
     */
    private void setRandomItemAmount(@NotNull ItemStack item, @NotNull ConfigurationSection itemConfig) {
        if (itemConfig.contains("min-amount") && itemConfig.contains("max-amount")) {
            int minAmount = itemConfig.getInt("min-amount", 1);
            int maxAmount = itemConfig.getInt("max-amount", 1);
            if (maxAmount > minAmount) {
                item.setAmount(random.nextInt(maxAmount - minAmount + 1) + minAmount);
            } else {
                item.setAmount(minAmount);
            }
        }
    }
    
    /**
     * Gives the item to the player, dropping overflow items.
     */
    private void giveItemToPlayer(@NotNull Player player, @NotNull ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            // Drop items that didn't fit in inventory
            for (ItemStack drop : overflow.values()) {
                player.getWorld().dropItem(player.getLocation(), drop);
            }
        }
    }
    
    /**
     * Notifies the player about their special catch.
     */
    private void notifyPlayerOfCatch(@NotNull Player player, @NotNull ItemStack item) {
        Component itemName = item.getItemMeta().displayName();
		assert itemName != null;
		player.sendMessage(Component.text()
            .append(Component.text("You caught ", NamedTextColor.AQUA))
            .append(itemName)
            .append(Component.text("!", NamedTextColor.AQUA))
            .build());
    }
    
    /**
     * Plays sound and particle effects for the special catch.
     */
    private void playFishingEffects(@NotNull Player player) {
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        player.spawnParticle(org.bukkit.Particle.SPLASH, player.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);
    }
} 
