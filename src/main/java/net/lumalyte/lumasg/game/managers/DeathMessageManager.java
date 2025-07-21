package net.lumalyte.lumasg.game.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.game.GamePlayerManager;
import net.lumalyte.lumasg.util.messaging.MiniMessageUtils;

/**
 * Manages death messages and kill tracking for game events.
 * Handles custom death messages, weapon identification, and kill notifications.
 */
public class DeathMessageManager {
    private final LumaSG plugin;
    private final GamePlayerManager playerManager;
    private final Logger logger;

    public DeathMessageManager(LumaSG plugin, GamePlayerManager playerManager) {
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.logger = plugin.getLogger();
    }

    /**
     * Creates a death message for a player kill.
     */
    public Component createDeathMessage(Player victim, Player killer) {
        if (killer == null) {
            return createNaturalDeathMessage(victim);
        }

        String weaponType = getWeaponType(killer.getInventory().getItemInMainHand());
        String messageTemplate = getDeathMessageTemplate(weaponType);
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("victim", victim.getName());
        placeholders.put("killer", killer.getName());
        placeholders.put("weapon", weaponType);

        try {
            return MiniMessageUtils.parseMessage(messageTemplate, placeholders);
        } catch (Exception e) {
            logger.warning("Failed to parse death message: " + e.getMessage());
            return Component.text(victim.getName() + " was killed by " + killer.getName())
                .color(NamedTextColor.RED);
        }
    }

    /**
     * Creates a natural death message (no killer).
     */
    public Component createNaturalDeathMessage(Player victim) {
        String messageTemplate = plugin.getConfig().getString("messages.death.natural", 
            "<red><victim> died");
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("victim", victim.getName());

        try {
            return MiniMessageUtils.parseMessage(messageTemplate, placeholders);
        } catch (Exception e) {
            logger.warning("Failed to parse natural death message: " + e.getMessage());
            return Component.text(victim.getName() + " died").color(NamedTextColor.RED);
        }
    }

    /**
     * Gets the weapon type from an ItemStack.
     */
    public String getWeaponType(ItemStack weapon) {
        if (weapon == null || weapon.getType() == Material.AIR) {
            return "fists";
        }

        Material material = weapon.getType();
        String materialName = material.name().toLowerCase();

        // Swords
        if (materialName.contains("sword")) {
            if (materialName.contains("wood")) return "wooden sword";
            if (materialName.contains("stone")) return "stone sword";
            if (materialName.contains("iron")) return "iron sword";
            if (materialName.contains("gold")) return "golden sword";
            if (materialName.contains("diamond")) return "diamond sword";
            if (materialName.contains("netherite")) return "netherite sword";
            return "sword";
        }

        // Axes
        if (materialName.contains("axe")) {
            if (materialName.contains("wood")) return "wooden axe";
            if (materialName.contains("stone")) return "stone axe";
            if (materialName.contains("iron")) return "iron axe";
            if (materialName.contains("gold")) return "golden axe";
            if (materialName.contains("diamond")) return "diamond axe";
            if (materialName.contains("netherite")) return "netherite axe";
            return "axe";
        }

        // Bows and crossbows
        if (material == Material.BOW) return "bow";
        if (material == Material.CROSSBOW) return "crossbow";

        // Tridents
        if (material == Material.TRIDENT) return "trident";

        // Tools as weapons
        if (materialName.contains("pickaxe")) return "pickaxe";
        if (materialName.contains("shovel") || materialName.contains("spade")) return "shovel";
        if (materialName.contains("hoe")) return "hoe";

        // Special items
        if (material == Material.STICK) return "stick";
        if (materialName.contains("rod")) return "rod";

        // Default cases
        if (weapon.hasItemMeta() && weapon.getItemMeta().displayName() != null) {
            return MiniMessageUtils.toLegacy(weapon.getItemMeta().displayName());
        }

        return material.name().toLowerCase().replace("_", " ");
    }

    /**
     * Gets the death message template for a weapon type.
     */
    private String getDeathMessageTemplate(String weaponType) {
        String configPath = "messages.death.weapons." + weaponType.replace(" ", "_");
        String defaultMessage = "<red><victim> was killed by <killer> using <weapon>";
        
        return plugin.getConfig().getString(configPath, defaultMessage);
    }

    /**
     * Broadcasts a death message to all players and spectators.
     */
    public void broadcastDeathMessage(Component deathMessage) {
        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                player.sendMessage(deathMessage);
            }
        }

        for (UUID spectatorId : playerManager.getSpectators()) {
            Player spectator = playerManager.getCachedPlayer(spectatorId);
            if (spectator != null) {
                spectator.sendMessage(deathMessage);
            }
        }
    }

    /**
     * Handles a player kill event.
     */
    public void handlePlayerKill(Player victim, Player killer) {
        Component deathMessage = createDeathMessage(victim, killer);
        broadcastDeathMessage(deathMessage);

        if (killer != null) {
            // Increment kill count
            playerManager.incrementKills(killer.getUniqueId());
            
            // Send kill notification to killer
            String killNotificationTemplate = plugin.getConfig().getString("messages.kill-notification",
                "<green>You killed <victim>! (+1 kill)");
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("victim", victim.getName());
            placeholders.put("kills", String.valueOf(playerManager.getPlayerKills(killer.getUniqueId())));

            try {
                Component killNotification = MiniMessageUtils.parseMessage(killNotificationTemplate, placeholders);
                killer.sendMessage(killNotification);
            } catch (Exception e) {
                logger.warning("Failed to parse kill notification: " + e.getMessage());
                killer.sendMessage(Component.text("You killed " + victim.getName() + "!")
                    .color(NamedTextColor.GREEN));
            }
        }
    }
} 
