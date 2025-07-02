package net.lumalyte.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.lumalyte.LumaSG;
import net.lumalyte.arena.Arena;
import net.lumalyte.game.Game;
import net.lumalyte.game.GameMode;
import net.lumalyte.game.GameState;
import net.lumalyte.game.Team;
import net.lumalyte.game.TeamQueueManager;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

/**
 * Menu for ranked players to configure and set up new games.
 * Allows selection of game mode, map, invite settings, and auto-fill preferences.
 */
public class GameSetupMenu {
    private final LumaSG plugin;
    private final TeamQueueManager queueManager;
    
    // Configuration storage for the setup process
    private final Map<UUID, GameSetupConfig> setupConfigs = new ConcurrentHashMap<>();
    
    /**
     * Configuration class to store player's setup choices.
     */
    private static class GameSetupConfig {
        GameMode gameMode = GameMode.SOLO;
        Arena selectedArena = null;
        boolean inviteOnly = false;
        boolean autoFill = true;
        
        boolean isComplete() {
            return selectedArena != null;
        }
    }
    
    /**
     * Constructs a new GameSetupMenu.
     * 
     * @param plugin The LumaSG plugin instance
     */
    public GameSetupMenu(LumaSG plugin) {
        this.plugin = plugin;
        this.queueManager = plugin.getTeamQueueManager();
    }
    
    /**
     * Opens the game setup menu for a ranked player.
     * 
     * @param player The player to open the menu for
     */
    public void openMenu(Player player) {
        // Check if player has permission to create games
        if (!hasSetupPermission(player)) {
            player.sendMessage(Component.text("§cYou don't have permission to set up games!", NamedTextColor.RED));
            return;
        }
        
        // Initialize or get existing config
        GameSetupConfig config = setupConfigs.computeIfAbsent(player.getUniqueId(), k -> new GameSetupConfig());
        
        // Create border item
        Item borderItem = new SimpleItem(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setDisplayName(""));
        
        // Create back button
        Item backButton = createBackButton(player);
        
        // Create mode selection items
        Item soloModeItem = createGameModeItem(player, GameMode.SOLO, config);
        Item duosModeItem = createGameModeItem(player, GameMode.DUOS, config);
        Item triosModeItem = createGameModeItem(player, GameMode.TRIOS, config);
        
        // Create map selection button
        Item mapSelectionItem = createMapSelectionButton(player, config);
        
        // Create invite settings button
        Item inviteSettingsItem = createInviteSettingsButton(player, config);
        
        // Create auto-fill toggle button
        Item autoFillItem = createAutoFillButton(player, config);
        
        // Create start game button
        Item startGameItem = createStartGameButton(player, config);
        
        // Create the GUI
        Gui gui = Gui.normal()
            .setStructure(
                "# # # # # # # # #",
                "# 1 . 2 . 3 . . #",
                "# . . . . . . . #",
                "# m . i . a . s #",
                "# # # # b # # # #"
            )
            .addIngredient('#', borderItem)
            .addIngredient('1', soloModeItem)
            .addIngredient('2', duosModeItem)
            .addIngredient('3', triosModeItem)
            .addIngredient('m', mapSelectionItem)
            .addIngredient('i', inviteSettingsItem)
            .addIngredient('a', autoFillItem)
            .addIngredient('s', startGameItem)
            .addIngredient('b', backButton)
            .build();
        
        // Create and open the window
        Window window = Window.single()
            .setViewer(player)
            .setTitle("§8§lLumaSG §7- §fGame Setup")
            .setGui(gui)
            .build();
        
        window.open();
    }
    
    /**
     * Opens the map selection menu.
     */
    public void openMapSelection(Player player) {
        GameSetupConfig config = setupConfigs.get(player.getUniqueId());
        if (config == null) {
            openMenu(player); // Redirect back to main menu
            return;
        }
        
        new MapSelectionMenu(plugin, this).openMenu(player, config);
    }
    
    /**
     * Called when a map is selected from the map selection menu.
     */
    public void onMapSelected(Player player, Arena arena) {
        GameSetupConfig config = setupConfigs.get(player.getUniqueId());
        if (config != null) {
            config.selectedArena = arena;
            player.sendMessage(Component.text("§aSelected map: " + arena.getName(), NamedTextColor.GREEN));
            openMenu(player); // Return to main setup menu
        }
    }
    
    /**
     * Starts the game with the configured settings.
     */
    private void startGameWithSettings(Player player, GameSetupConfig config) {
        if (!config.isComplete()) {
            player.sendMessage(Component.text("§cPlease complete all configuration options!", NamedTextColor.RED));
            return;
        }
        
        try {
            // Check if a game already exists for this arena
            Game existingGame = plugin.getGameManager().getGameByArena(config.selectedArena);
            Game game;
            
            if (existingGame != null && existingGame.getState() == GameState.INACTIVE) {
                // Use existing inactive game
                game = existingGame;
            } else if (existingGame != null) {
                // Arena is in use by an active game
                player.sendMessage(Component.text("§cArena is currently in use by another game!", NamedTextColor.RED));
                return;
            } else {
                // Create new game
                game = plugin.getGameManager().createGame(config.selectedArena);
                if (game == null) {
                    player.sendMessage(Component.text("§cFailed to create game! Arena may be in use.", NamedTextColor.RED));
                    return;
                }
            }
            
            // Activate the game with the selected mode
            game.activateGame(config.gameMode);
            
            // Create and join team for the setup player
            Team playerTeam = queueManager.createTeam(player, game, config.inviteOnly, config.autoFill);
            if (playerTeam == null) {
                player.sendMessage(Component.text("§cFailed to create team!", NamedTextColor.RED));
                return;
            }
            
            // Add the setup period timer
            int setupTime = plugin.getConfig().getInt("game.setup-period-seconds", 120);
            queueManager.startSetupPeriod(game, setupTime);
            
            // Clear the setup config
            setupConfigs.remove(player.getUniqueId());
            
            // Close the menu
            player.closeInventory();
            
            // Success message
            player.sendMessage(Component.text("§aGame created successfully! Players can now join your team.", NamedTextColor.GREEN));
            player.sendMessage(Component.text("§7Setup period: " + setupTime + " seconds", NamedTextColor.GRAY));
            player.sendMessage(Component.text("§7Game Mode: " + config.gameMode.getDisplayName(), NamedTextColor.GRAY));
            player.sendMessage(Component.text("§7Arena: " + config.selectedArena.getName(), NamedTextColor.GRAY));
            
        } catch (Exception e) {
            player.sendMessage(Component.text("§cError creating game: " + e.getMessage(), NamedTextColor.RED));
            plugin.getDebugLogger().forContext("GameSetupMenu").error("Failed to create game", e);
        }
    }
    
    /**
     * Checks if a player has permission to set up games.
     */
    private boolean hasSetupPermission(Player player) {
        // Check for specific rank permissions or admin permission
        return player.hasPermission("lumasg.setup.game") || 
               player.hasPermission("lumasg.rank.bronze") ||
               player.hasPermission("lumasg.rank.silver") ||
               player.hasPermission("lumasg.rank.gold") ||
               player.hasPermission("lumasg.rank.platinum") ||
               player.hasPermission("lumasg.rank.diamond") ||
               player.hasPermission("lumasg.admin");
    }
    
    /**
     * Creates a back button.
     */
    private Item createBackButton(Player player) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return new ItemBuilder(Material.ARROW)
                    .setDisplayName("§c§lBack to Main Menu")
                    .addLoreLines("Click to return to the main menu");
            }
            
            @Override
            public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    player.closeInventory();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        new net.lumalyte.gui.menus.MainMenu(plugin).openMenu(player);
                    });
                }
            }
        };
    }
    
    /**
     * Creates a game mode selection item.
     */
    private Item createGameModeItem(Player player, GameMode gameMode, GameSetupConfig config) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                Material material = switch (gameMode) {
                    case SOLO -> Material.IRON_SWORD;
                    case DUOS -> Material.GOLDEN_SWORD;
                    case TRIOS -> Material.DIAMOND_SWORD;
                };
                
                boolean isSelected = config.gameMode == gameMode;
                
                ItemBuilder builder = new ItemBuilder(material)
                    .setDisplayName((isSelected ? "§a§l" : "§7§l") + gameMode.name())
                    .addLoreLines(
                        "§7Team Size: §f" + gameMode.getTeamSize(),
                        "§7Max Teams: §f" + gameMode.getMaxTeams(24), // Assuming 24 max players
                        "",
                        isSelected ? "§a✓ Selected" : "§eClick to select"
                    );
                
                if (config.gameMode == gameMode) {
                    builder.addEnchantment(org.bukkit.enchantments.Enchantment.POWER, 1, true);
                }
                
                return builder;
            }
            
            @Override
            public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    config.gameMode = gameMode;
                    openMenu(player); // Refresh the menu
                }
            }
        };
    }
    
    /**
     * Creates the map selection button.
     */
    private Item createMapSelectionButton(Player player, GameSetupConfig config) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                String mapName = config.selectedArena != null ? config.selectedArena.getName() : "None";
                
                return new ItemBuilder(Material.FILLED_MAP)
                    .setDisplayName("§b§lMap Selection")
                    .addLoreLines(
                        "§7Selected Map: §f" + mapName,
                        "",
                        "§eClick to choose a map"
                    );
            }
            
            @Override
            public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    player.closeInventory();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        openMapSelection(player);
                    });
                }
            }
        };
    }
    
    /**
     * Creates the invite settings button.
     */
    private Item createInviteSettingsButton(Player player, GameSetupConfig config) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                Material material = config.inviteOnly ? Material.REDSTONE_BLOCK : Material.LIME_CONCRETE;
                String status = config.inviteOnly ? "Invite Only" : "Open to All";
                
                return new ItemBuilder(material)
                    .setDisplayName("§6§lTeam Privacy")
                    .addLoreLines(
                        "§7Current: §f" + status,
                        "",
                        "§7Invite Only: §fOnly invited players can join",
                        "§7Open to All: §fAnyone can join your team",
                        "",
                        "§eClick to toggle"
                    );
            }
            
            @Override
            public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    config.inviteOnly = !config.inviteOnly;
                    openMenu(player); // Refresh the menu
                }
            }
        };
    }
    
    /**
     * Creates the auto-fill toggle button.
     */
    private Item createAutoFillButton(Player player, GameSetupConfig config) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                Material material = config.autoFill ? Material.HOPPER : Material.BARRIER;
                String status = config.autoFill ? "Enabled" : "Disabled";
                
                return new ItemBuilder(material)
                    .setDisplayName("§d§lAuto-Fill")
                    .addLoreLines(
                        "§7Current: §f" + status,
                        "",
                        "§7When enabled, empty team slots will be",
                        "§7automatically filled with players",
                        "",
                        "§eClick to toggle"
                    );
            }
            
            @Override
            public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    config.autoFill = !config.autoFill;
                    openMenu(player); // Refresh the menu
                }
            }
        };
    }
    
    /**
     * Creates the start game button.
     */
    private Item createStartGameButton(Player player, GameSetupConfig config) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                boolean canStart = config.isComplete();
                Material material = canStart ? Material.EMERALD_BLOCK : Material.GRAY_STAINED_GLASS;
                
                ItemBuilder builder = new ItemBuilder(material)
                    .setDisplayName(canStart ? "§a§lStart Game" : "§7§lStart Game")
                    .addLoreLines(
                        "§7Mode: §f" + config.gameMode.name(),
                        "§7Map: §f" + (config.selectedArena != null ? config.selectedArena.getName() : "Not selected"),
                        "§7Privacy: §f" + (config.inviteOnly ? "Invite Only" : "Open"),
                        "§7Auto-Fill: §f" + (config.autoFill ? "Enabled" : "Disabled"),
                        ""
                    );
                
                if (canStart) {
                    builder.addLoreLines("§aClick to create the game!");
                } else {
                    builder.addLoreLines("§cComplete all settings first!");
                }
                
                return builder;
            }
            
            @Override
            public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    if (config.isComplete()) {
                        startGameWithSettings(player, config);
                    } else {
                        player.sendMessage(Component.text("§cPlease complete all configuration options!", NamedTextColor.RED));
                    }
                }
            }
        };
    }
    
    /**
     * Cleans up setup configuration when a player disconnects.
     */
    public void cleanupPlayer(UUID playerId) {
        setupConfigs.remove(playerId);
    }
}