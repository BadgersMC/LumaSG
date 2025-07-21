package net.lumalyte.lumasg.hooks;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.game.Game;
import net.lumalyte.lumasg.game.GameManager;
import net.lumalyte.lumasg.game.GameState;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * KingdomsX integration hook - overrides PvP protection during survival games.
 */
public class KingdomsXHook implements PluginHook, Listener {
    
    private static final String PLUGIN_NAME = "Kingdoms";
    private static final String NOTIFICATION_METADATA = "lumasg_kingdomsx_notification";
    private static final long NOTIFICATION_COOLDOWN = 5000L; // 5 seconds

    private final LumaSG plugin;
    private final DebugLogger.ContextualLogger logger;
    private final GameManager gameManager;
    private Plugin kingdomsXPlugin;
    
    // Reflection fields
    private Method getKingdomPlayerMethod;
    private Method getKingdomMethod;
    private Method isPacifistMethod;
    private Method isInSameKingdomAsMethod;
    private Method isPvpMethod;
    
    public KingdomsXHook(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("KingdomsXHook");
        this.gameManager = plugin.getGameManager();
    }
    
    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }
    
    @Override
    public boolean isAvailable() {
        return kingdomsXPlugin != null && kingdomsXPlugin.isEnabled();
    }
    
    @Override
    public Plugin getPlugin() {
        return kingdomsXPlugin;
    }
    
    @Override
    public boolean initialize() {
        kingdomsXPlugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (kingdomsXPlugin == null) {
            logger.debug("Kingdoms not found - hook disabled");
            return false;
        }
        
        return initializeReflection();
    }
    
    @Override
    public boolean enable() {
        if (!initialize()) return false;

        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        logger.info("[KingdomsXHook] Successfully hooked into Kingdoms!");
        return true;
    }
    
    private boolean initializeReflection() {
        try {
            Class<?> kingdomsXClass = Class.forName("org.kingdoms.constants.player.KingdomPlayer");
            Class<?> kingdomClass = Class.forName("org.kingdoms.constants.group.Kingdom");
            
            getKingdomPlayerMethod = kingdomsXClass.getMethod("getKingdomPlayer", UUID.class);
            getKingdomMethod = kingdomsXClass.getMethod("getKingdom");
            isPvpMethod = kingdomsXClass.getMethod("isPvp");
            isPacifistMethod = kingdomClass.getMethod("isPacifist");
            isInSameKingdomAsMethod = kingdomsXClass.getMethod("isInSameKingdomAs", kingdomsXClass);
            
            return true;
        } catch (Exception e) {
            logger.error("Failed to initialize KingdomsX reflection", e);
            return false;
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager) || !(event.getEntity() instanceof Player victim)) return;

		// Check if both players are in the same game
        Game game = gameManager.getGameByPlayer(damager);
        if (game == null || game != gameManager.getGameByPlayer(victim)) return;

        // Check if the game is in a state where PvP is allowed
        if (game.getState() != GameState.ACTIVE && game.getState() != GameState.DEATHMATCH) return;

        // Check if KingdomsX would prevent PvP
        if (wouldKingdomsXPreventPvP(damager, victim)) {
            // Allow PvP and notify players if needed
            event.setCancelled(false);
            notifyPlayersIfNeeded(damager, victim);
        }
    }
    
    private boolean wouldKingdomsXPreventPvP(Player damager, Player victim) {
        try {
            Object damagerKP = getKingdomPlayerMethod.invoke(null, damager.getUniqueId());
            Object victimKP = getKingdomPlayerMethod.invoke(null, victim.getUniqueId());
            
            if (damagerKP == null || victimKP == null) return false;
            
            // Check if in same kingdom
            Boolean sameKingdom = (Boolean) isInSameKingdomAsMethod.invoke(damagerKP, victimKP);
            if (Boolean.TRUE.equals(sameKingdom)) return true;
            
            // Check if either kingdom is pacifist
            Object damagerKingdom = getKingdomMethod.invoke(damagerKP);
            Object victimKingdom = getKingdomMethod.invoke(victimKP);
            
            if (damagerKingdom != null && Boolean.TRUE.equals(isPacifistMethod.invoke(damagerKingdom))) return true;
            if (victimKingdom != null && Boolean.TRUE.equals(isPacifistMethod.invoke(victimKingdom))) return true;
            
            // Check individual PvP settings
            if (Boolean.FALSE.equals(isPvpMethod.invoke(damagerKP))) return true;
			return Boolean.FALSE.equals(isPvpMethod.invoke(victimKP));

		} catch (Exception e) {
            logger.error("Failed to check KingdomsX PvP prevention", e);
            return false;
        }
    }
    
    private void notifyPlayersIfNeeded(Player damager, Player victim) {
        long now = System.currentTimeMillis();
        notifyPlayerIfNeeded(damager, now);
        notifyPlayerIfNeeded(victim, now);
    }
    
    private void notifyPlayerIfNeeded(Player player, long now) {
        if (player.hasMetadata(NOTIFICATION_METADATA)) {
            long lastNotification = player.getMetadata(NOTIFICATION_METADATA).getFirst().asLong();
            if (now - lastNotification < NOTIFICATION_COOLDOWN) return;
        }
        
        player.setMetadata(NOTIFICATION_METADATA, new FixedMetadataValue(plugin, now));
        player.sendMessage("§6[LumaSG] §eKingdom PvP protection is disabled during Survival Games!");
    }
    
    @Override
    public void disable() {
        // Nothing to clean up
    }
    
    public boolean isPlayerInActivePvPGame(@NotNull Player player) {
        Game game = gameManager.getGameByPlayer(player);
        return game != null && game.getState() == GameState.ACTIVE && game.isPvpEnabled();
    }
} 
