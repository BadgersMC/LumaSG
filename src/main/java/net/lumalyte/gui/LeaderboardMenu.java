package net.lumalyte.gui;

import net.lumalyte.LumaSG;
import net.lumalyte.statistics.PlayerStats;
import net.lumalyte.statistics.StatType;
import net.lumalyte.util.DebugLogger;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

import java.text.DecimalFormat;
import java.util.List;

/**
 * GUI menu for displaying player leaderboards.
 */
public class LeaderboardMenu {
    private final LumaSG plugin;
    private final DecimalFormat decimalFormat = new DecimalFormat("#.##");
    
    /** The debug logger instance for this leaderboard menu */
    private final DebugLogger.ContextualLogger logger;
    
    /**
     * Creates a new leaderboard menu.
     * 
     * @param plugin The plugin instance
     */
    public LeaderboardMenu(LumaSG plugin) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("LeaderboardMenu");
    }
    
    /**
     * Opens the main leaderboard menu for a player.
     * 
     * @param player The player to open the menu for
     */
    public void openMenu(Player player) {
        openLeaderboardTab(player, StatType.WINS);
    }
    
    /**
     * Opens a specific leaderboard tab.
     * 
     * @param player The player to open the menu for
     * @param statType The statistic type to display
     */
    public void openLeaderboardTab(Player player, StatType statType) {
        // Create tab items
        Item killsTabItem = new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                Material material = statType == StatType.KILLS ? Material.DIAMOND_SWORD : Material.IRON_SWORD;
                String name = statType == StatType.KILLS ? "§e§lKills §7(Current)" : "§7Kills";
                return new ItemBuilder(material).setDisplayName(name);
            }
            
            @Override
            public void handleClick(ClickType clickType, Player player, org.bukkit.event.inventory.InventoryClickEvent event) {
                if (clickType.isLeftClick() && statType != StatType.KILLS) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    openLeaderboardTab(player, StatType.KILLS);
                }
            }
        };
        
        Item winsTabItem = new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                Material material = statType == StatType.WINS ? Material.GOLDEN_APPLE : Material.APPLE;
                String name = statType == StatType.WINS ? "§e§lWins §7(Current)" : "§7Wins";
                return new ItemBuilder(material).setDisplayName(name);
            }
            
            @Override
            public void handleClick(ClickType clickType, Player player, org.bukkit.event.inventory.InventoryClickEvent event) {
                if (clickType.isLeftClick() && statType != StatType.WINS) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    openLeaderboardTab(player, StatType.WINS);
                }
            }
        };
        
        Item gamesTabItem = new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                Material material = statType == StatType.GAMES_PLAYED ? Material.BOOK : Material.WRITABLE_BOOK;
                String name = statType == StatType.GAMES_PLAYED ? "§e§lGames §7(Current)" : "§7Games";
                return new ItemBuilder(material).setDisplayName(name);
            }
            
            @Override
            public void handleClick(ClickType clickType, Player player, org.bukkit.event.inventory.InventoryClickEvent event) {
                if (clickType.isLeftClick() && statType != StatType.GAMES_PLAYED) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    openLeaderboardTab(player, StatType.GAMES_PLAYED);
                }
            }
        };
        
        // Create border and back button items
        Item borderItem = new SimpleItem(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
            .setDisplayName(""));
        
        Item backButton = new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return new ItemBuilder(Material.ARROW)
                    .setDisplayName("§c§lBack")
                    .addLoreLines("§7Click to go back to main menu");
            }
            
            @Override
            public void handleClick(ClickType clickType, Player player, org.bukkit.event.inventory.InventoryClickEvent event) {
                if (clickType.isLeftClick() && statType != StatType.GAMES_PLAYED) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    openLeaderboardTab(player, StatType.GAMES_PLAYED);
                }
            }
        };
        
        // Create loading item for leaderboard data
        Item loadingItem = new SimpleItem(new ItemBuilder(Material.HOPPER)
            .setDisplayName("§e§lLoading...")
            .addLoreLines("§7Fetching leaderboard data..."));
        
        // Create the GUI structure with loading items
        Gui gui = Gui.normal()
            .setStructure(
                "# # # k # w # g #",
                "# l l l l l l l #",
                "# l l l l l l l #",
                "# l l l l l l l #",
                "# # # # b # # # #"
            )
            .addIngredient('#', borderItem)
            .addIngredient('k', killsTabItem)
            .addIngredient('w', winsTabItem)
            .addIngredient('g', gamesTabItem)
            .addIngredient('l', loadingItem)
            .addIngredient('b', backButton)
            .build();
        
        // Create and open the window
        Window window = Window.single()
            .setViewer(player)
            .setTitle("§8§lLumaSG §7- §f" + getStatTypeDisplayName(statType) + " Leaderboard")
            .setGui(gui)
            .build();
        
        window.open();
        
        // Load leaderboard data asynchronously
        loadLeaderboardData(player, gui, statType);
    }
    
    /**
     * Loads leaderboard data asynchronously and updates the GUI.
     */
    private void loadLeaderboardData(Player player, Gui gui, StatType statType) {
        plugin.getStatisticsManager().getLeaderboard(statType, 21) // 3x7 grid
            .thenAccept(leaderboard -> {
                // Update GUI on main thread
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    updateLeaderboardGUI(gui, leaderboard, statType);
                });
            })
            .exceptionally(throwable -> {
                logger.warn("Failed to load leaderboard data: " + throwable.getMessage());
                
                // Show error on main thread
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Item errorItem = new SimpleItem(new ItemBuilder(Material.BARRIER)
                        .setDisplayName("§c§lError")
                        .addLoreLines("§7Failed to load leaderboard data"));
                    
                    // Replace loading items with error item
                    for (int slot = 10; slot <= 16; slot++) {
                        gui.setItem(slot, errorItem);
                    }
                    for (int slot = 19; slot <= 25; slot++) {
                        gui.setItem(slot, errorItem);
                    }
                    for (int slot = 28; slot <= 34; slot++) {
                        gui.setItem(slot, errorItem);
                    }
                });
                
                return null;
            });
    }
    
    /**
     * Updates the GUI with leaderboard data.
     */
    private void updateLeaderboardGUI(Gui gui, List<PlayerStats> leaderboard, StatType statType) {
        // Define the slots for leaderboard items (3x7 grid)
        int[] slots = {
            10, 11, 12, 13, 14, 15, 16,  // Row 1
            19, 20, 21, 22, 23, 24, 25,  // Row 2
            28, 29, 30, 31, 32, 33, 34   // Row 3
        };
        
        // Clear existing items
        for (int slot : slots) {
            gui.setItem(slot, null);
        }
        
        // Add leaderboard entries
        for (int i = 0; i < Math.min(leaderboard.size(), slots.length); i++) {
            PlayerStats stats = leaderboard.get(i);
            int rank = i + 1;
            
            Item playerItem = createLeaderboardItem(stats, rank, statType);
            gui.setItem(slots[i], playerItem);
        }
        
        // Fill remaining slots with empty items if needed
        Item emptyItem = new SimpleItem(new ItemBuilder(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
            .setDisplayName(""));
        
        for (int i = leaderboard.size(); i < slots.length; i++) {
            gui.setItem(slots[i], emptyItem);
        }
    }
    
    /**
     * Creates a leaderboard item for a player.
     */
    private Item createLeaderboardItem(PlayerStats stats, int rank, StatType statType) {
        Material material = switch (rank) {
            case 1 -> Material.GOLDEN_HELMET;
            case 2 -> Material.IRON_HELMET;
            case 3 -> Material.LEATHER_HELMET;
            default -> Material.PLAYER_HEAD;
        };
        
        String rankColor = switch (rank) {
            case 1 -> "§6";
            case 2 -> "§7";
            case 3 -> "§c";
            default -> "§e";
        };
        
        String statValue = getStatValueString(stats, statType);
        
        return new SimpleItem(new ItemBuilder(material)
            .setDisplayName(rankColor + "#" + rank + " §f" + stats.getPlayerName())
            .addLoreLines(
                "§7" + getStatTypeDisplayName(statType) + ": §f" + statValue,
                "",
                "§7Wins: §a" + stats.getWins(),
                "§7Losses: §c" + stats.getLosses(),
                "§7Kills: §e" + stats.getKills(),
                "§7Deaths: §c" + stats.getDeaths(),
                "§7Games Played: §b" + stats.getGamesPlayed(),
                "§7K/D Ratio: §f" + decimalFormat.format(stats.getKillDeathRatio()),
                "§7Win Rate: §f" + decimalFormat.format(stats.getWinRate()) + "%"
            ));
    }
    
    /**
     * Gets the display name for a statistic type.
     */
    private String getStatTypeDisplayName(StatType statType) {
        return switch (statType) {
            case WINS -> "Wins";
            case KILLS -> "Kills";
            case GAMES_PLAYED -> "Games Played";
            case KILL_DEATH_RATIO -> "K/D Ratio";
            case WIN_RATE -> "Win Rate";
            case TIME_PLAYED -> "Time Played";
            case BEST_PLACEMENT -> "Best Placement";
            case WIN_STREAK -> "Win Streak";
            case TOP3_FINISHES -> "Top 3 Finishes";
            case DAMAGE_DEALT -> "Damage Dealt";
            case CHESTS_OPENED -> "Chests Opened";
        };
    }
    
    /**
     * Gets the formatted string value for a statistic.
     */
    private String getStatValueString(PlayerStats stats, StatType statType) {
        return switch (statType) {
            case WINS -> String.valueOf(stats.getWins());
            case KILLS -> String.valueOf(stats.getKills());
            case GAMES_PLAYED -> String.valueOf(stats.getGamesPlayed());
            case KILL_DEATH_RATIO -> decimalFormat.format(stats.getKillDeathRatio());
            case WIN_RATE -> decimalFormat.format(stats.getWinRate()) + "%";
            case TIME_PLAYED -> formatTime(stats.getTotalTimePlayed());
            case BEST_PLACEMENT -> stats.getBestPlacement() == 0 ? "N/A" : String.valueOf(stats.getBestPlacement());
            case WIN_STREAK -> String.valueOf(stats.getBestWinStreak());
            case TOP3_FINISHES -> String.valueOf(stats.getTop3Finishes());
            case DAMAGE_DEALT -> decimalFormat.format(stats.getTotalDamageDealt());
            case CHESTS_OPENED -> String.valueOf(stats.getChestsOpened());
        };
    }
    
    /**
     * Formats time in seconds to a human-readable format.
     */
    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        }
    }
} 