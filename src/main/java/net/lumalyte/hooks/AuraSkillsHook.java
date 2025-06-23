package net.lumalyte.hooks;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.user.SkillsUser;
import dev.aurelium.auraskills.api.stat.StatModifier;
import dev.aurelium.auraskills.api.stat.Stats;
import dev.aurelium.auraskills.api.event.skill.XpGainEvent;
import dev.aurelium.auraskills.api.event.mana.ManaAbilityActivateEvent;
import dev.aurelium.auraskills.api.event.mana.ManaRegenerateEvent;
import net.lumalyte.LumaSG;
import net.lumalyte.game.Game;
import net.lumalyte.game.GameManager;
import net.lumalyte.util.DebugLogger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hook for the AuraSkills plugin to handle player stat modifications and game mechanics.
 * Manages stat caching, skill gain prevention, and ability usage during games.
 */
public class AuraSkillsHook implements PluginHook, Listener {
    
    private static final String PLUGIN_NAME = "AuraSkills";
    private static final String MODIFIER_PREFIX = "sg_";
    
    private Plugin auraSkillsPlugin;
    private boolean available = false;
    private AuraSkillsApi api;
    
    // Store original player stats
    private final Map<UUID, PlayerStats> originalStats = new HashMap<>();
    
    private final LumaSG plugin;
    
    /** The debug logger instance for this AuraSkills hook */
    private final DebugLogger.ContextualLogger logger;
    
    /**
     * Creates a new AuraSkills hook.
     * 
     * @param plugin The LumaSG plugin instance
     */
    public AuraSkillsHook(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("AuraSkillsHook");
    }
    
    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }
    
    @Override
    public boolean isAvailable() {
        return available;
    }
    
    @Override
    public Plugin getPlugin() {
        return auraSkillsPlugin;
    }
    
    @Override
    public boolean initialize() {
        try {
            auraSkillsPlugin = plugin.getServer().getPluginManager().getPlugin(PLUGIN_NAME);
            if (auraSkillsPlugin == null) {
                return false;
            }
            
            // Get AuraSkills API instance
            api = AuraSkillsApi.get();
            return true;
        } catch (Exception e) {
            logger.warn("Failed to initialize AuraSkills hook: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean enable() {
        if (auraSkillsPlugin != null && api != null) {
            available = true;
            // Register event listeners
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            logger.info("Successfully hooked into AuraSkills!");
            return true;
        }
        return false;
    }
    
    @Override
    public void disable() {
        // Restore all players' stats before disabling
        new HashMap<>(originalStats).forEach((uuid, stats) -> {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                restorePlayerStats(player);
            }
        });
        available = false;
        originalStats.clear();
    }
    
    /**
     * Resets a player's stats for the duration of a game.
     * This will store their current stats and set them to default values.
     * Also disables skill gain and active abilities.
     * 
     * @param player The player to reset stats for
     */
    public void resetPlayerStats(@NotNull Player player) {
        if (!available) return;
        
        try {
            // Get player's SkillsUser instance
            SkillsUser user = api.getUser(player.getUniqueId());
            
            // Store original stats
            PlayerStats stats = new PlayerStats(
                user.getStatLevel(Stats.HEALTH),
                user.getStatLevel(Stats.TOUGHNESS),
                user.getStatLevel(Stats.STRENGTH),
                user.getStatLevel(Stats.REGENERATION),
                user.getStatLevel(Stats.LUCK),
                user.getStatLevel(Stats.WISDOM),
                user.getStatLevel(Stats.CRIT_CHANCE),
                user.getStatLevel(Stats.CRIT_DAMAGE),
                user.getStatLevel(Stats.SPEED)
            );
            originalStats.put(player.getUniqueId(), stats);
            
            // Remove all existing game modifiers
            removeAllGameModifiers(user);
            
            // Add modifiers to set stats to default values
            setDefaultStats(user, stats);
            
            // Update player's max health
            player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20.0);
            player.setHealth(Math.min(player.getHealth(), 20.0));
            
            logger.info("Reset AuraSkills stats for player: " + player.getName());
        } catch (Exception e) {
            logger.warn("Failed to reset AuraSkills stats for player: " + player.getName(), e);
        }
    }
    
    /**
     * Restores a player's stats after a game.
     * This will restore their original stats that were stored when resetPlayerStats was called.
     * Re-enables skill gain and active abilities.
     * 
     * @param player The player to restore stats for
     */
    public void restorePlayerStats(@NotNull Player player) {
        if (!available) return;
        
        try {
            PlayerStats stats = originalStats.remove(player.getUniqueId());
            if (stats == null) return;
            
            // Get player's SkillsUser instance
            SkillsUser user = api.getUser(player.getUniqueId());
            
            // Remove all our stat modifiers
            removeAllGameModifiers(user);
            
            // Update player's max health
            player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(stats.health);
            
            logger.info("Restored AuraSkills stats for player: " + player.getName());
        } catch (Exception e) {
            logger.warn("Failed to restore AuraSkills stats for player: " + player.getName(), e);
        }
    }
    
    /**
     * Removes all stat modifiers added by the game.
     */
    private void removeAllGameModifiers(@NotNull SkillsUser user) {
        user.removeStatModifier(MODIFIER_PREFIX + "health");
        user.removeStatModifier(MODIFIER_PREFIX + "toughness");
        user.removeStatModifier(MODIFIER_PREFIX + "strength");
        user.removeStatModifier(MODIFIER_PREFIX + "regeneration");
        user.removeStatModifier(MODIFIER_PREFIX + "luck");
        user.removeStatModifier(MODIFIER_PREFIX + "wisdom");
        user.removeStatModifier(MODIFIER_PREFIX + "crit_chance");
        user.removeStatModifier(MODIFIER_PREFIX + "crit_damage");
        user.removeStatModifier(MODIFIER_PREFIX + "speed");
    }
    
    /**
     * Sets all stats to their default values for the game.
     */
    private void setDefaultStats(@NotNull SkillsUser user, @NotNull PlayerStats originalStats) {
        // Reset health to default (20.0)
        double healthDiff = 20.0 - originalStats.health;
        if (healthDiff != 0) {
            user.addStatModifier(new StatModifier(MODIFIER_PREFIX + "health", Stats.HEALTH, healthDiff));
        }
        
        // Reset other stats to 0 (default values)
        if (originalStats.toughness != 0) {
            user.addStatModifier(new StatModifier(MODIFIER_PREFIX + "toughness", Stats.TOUGHNESS, -originalStats.toughness));
        }
        if (originalStats.strength != 0) {
            user.addStatModifier(new StatModifier(MODIFIER_PREFIX + "strength", Stats.STRENGTH, -originalStats.strength));
        }
        if (originalStats.regeneration != 0) {
            user.addStatModifier(new StatModifier(MODIFIER_PREFIX + "regeneration", Stats.REGENERATION, -originalStats.regeneration));
        }
        if (originalStats.luck != 0) {
            user.addStatModifier(new StatModifier(MODIFIER_PREFIX + "luck", Stats.LUCK, -originalStats.luck));
        }
        if (originalStats.wisdom != 0) {
            user.addStatModifier(new StatModifier(MODIFIER_PREFIX + "wisdom", Stats.WISDOM, -originalStats.wisdom));
        }
        if (originalStats.critChance != 0) {
            user.addStatModifier(new StatModifier(MODIFIER_PREFIX + "crit_chance", Stats.CRIT_CHANCE, -originalStats.critChance));
        }
        if (originalStats.critDamage != 0) {
            user.addStatModifier(new StatModifier(MODIFIER_PREFIX + "crit_damage", Stats.CRIT_DAMAGE, -originalStats.critDamage));
        }
        if (originalStats.speed != 0) {
            user.addStatModifier(new StatModifier(MODIFIER_PREFIX + "speed", Stats.SPEED, -originalStats.speed));
        }
    }
    
    /**
     * Checks if a player is currently in a Survival Games match.
     * 
     * @param player The player to check
     * @return true if the player is in a game, false otherwise
     */
    private boolean isInGame(Player player) {
        GameManager gameManager = plugin.getGameManager();
        Game game = gameManager.getGameByPlayer(player);
        return game != null;
    }
    
    /**
     * Prevents players from gaining XP during games.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onXpGain(XpGainEvent event) {
        if (isInGame(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Prevents players from using mana abilities during games.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onManaAbilityActivate(ManaAbilityActivateEvent event) {
        if (isInGame(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Prevents mana regeneration during games to maintain balance.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onManaRegenerate(ManaRegenerateEvent event) {
        if (isInGame(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Stores original player stats for restoration after the game.
     */
    private static class PlayerStats {
        final double health;
        final double toughness;
        final double strength;
        final double regeneration;
        final double luck;
        final double wisdom;
        final double critChance;
        final double critDamage;
        final double speed;
        
        PlayerStats(double health, double toughness, double strength, double regeneration,
                   double luck, double wisdom, double critChance, double critDamage, double speed) {
            this.health = health;
            this.toughness = toughness;
            this.strength = strength;
            this.regeneration = regeneration;
            this.luck = luck;
            this.wisdom = wisdom;
            this.critChance = critChance;
            this.critDamage = critDamage;
            this.speed = speed;
        }
    }
} 