package net.lumalyte.gui;

import net.lumalyte.LumaSG;
import net.lumalyte.statistics.PlayerStats;
import net.lumalyte.statistics.StatType;
import net.lumalyte.util.DebugLogger;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

import java.text.DecimalFormat;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;

/**
 * GUI menu for displaying player leaderboards.
 */
public class LeaderboardMenu {
    private static final Map<StatType, TabItemConfig> TAB_CONFIGS = new EnumMap<>(StatType.class);
    
    static {
        TAB_CONFIGS.put(StatType.KILLS, new TabItemConfig(Material.DIAMOND_SWORD, Material.IRON_SWORD, "Kills"));
        TAB_CONFIGS.put(StatType.WINS, new TabItemConfig(Material.GOLDEN_APPLE, Material.APPLE, "Wins"));
        TAB_CONFIGS.put(StatType.GAMES_PLAYED, new TabItemConfig(Material.BOOK, Material.WRITABLE_BOOK, "Games"));
        TAB_CONFIGS.put(StatType.DAMAGE_DEALT, new TabItemConfig(Material.DIAMOND_AXE, Material.IRON_AXE, "Damage"));
        TAB_CONFIGS.put(StatType.CHESTS_OPENED, new TabItemConfig(Material.CHEST, Material.BARREL, "Chests"));
    }
    
    private final LumaSG plugin;
    private final DecimalFormat decimalFormat = new DecimalFormat("#.##");
    private final DebugLogger.ContextualLogger logger;
    private final LeaderboardLayout layout;
    
    /**
     * Creates a new leaderboard menu.
     * 
     * @param plugin The plugin instance
     */
    public LeaderboardMenu(LumaSG plugin) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("LeaderboardMenu");
        this.layout = new LeaderboardLayout();
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
        Gui gui = createBaseGui(statType);
        Window window = createWindow(player, statType, gui);
        window.open();
        loadLeaderboardData(gui, statType);
    }
    
    private Gui createBaseGui(StatType statType) {
        return Gui.normal()
            .setStructure(
                "# k # w # g # d #",
                "# # # c # # # # #",
                "# l l l l l l l #",
                "# l l l l l l l #",
                "# l l l l l l l #",
                "# # # # b # # # #")
            .addIngredient('#', createBorderItem())
            .addIngredient('k', createTabItem(StatType.KILLS, statType))
            .addIngredient('w', createTabItem(StatType.WINS, statType))
            .addIngredient('g', createTabItem(StatType.GAMES_PLAYED, statType))
            .addIngredient('d', createTabItem(StatType.DAMAGE_DEALT, statType))
            .addIngredient('c', createTabItem(StatType.CHESTS_OPENED, statType))
            .addIngredient('l', createLoadingItem())
            .addIngredient('b', createBackButton())
            .build();
    }
    
    private Window createWindow(Player player, StatType statType, Gui gui) {
        return Window.single()
            .setViewer(player)
            .setTitle("§8§lLumaSG §7- §f" + getStatTypeDisplayName(statType) + " Leaderboard")
            .setGui(gui)
            .build();
    }
    
    private Item createBorderItem() {
        return new SimpleItem(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
            .setDisplayName(""));
    }
    
    private Item createTabItem(StatType tabType, StatType currentStatType) {
        TabItemConfig config = TAB_CONFIGS.get(tabType);
        return new LeaderboardTabItem(
            tabType,
            currentStatType,
            config,
            player -> openLeaderboardTab(player, tabType)
        );
    }
    
    private Item createBackButton() {
        return new LeaderboardActionItem(
            Material.ARROW,
            "§c§lBack",
            "§7Click to go back to main menu",
            this::openMenu
        );
    }
    
    private Item createLoadingItem() {
        return new SimpleItem(new ItemBuilder(Material.HOPPER)
            .setDisplayName("§e§lLoading...")
            .addLoreLines("§7Fetching leaderboard data..."));
    }
    
    /**
     * Loads leaderboard data asynchronously and updates the GUI.
     */
    private void loadLeaderboardData(Gui gui, StatType statType) {
        CompletableFuture<List<PlayerStats>> future = plugin.getStatisticsManager()
            .getLeaderboard(statType, layout.getMaxEntries());
            
        future.thenAccept(leaderboard -> 
            plugin.getServer().getScheduler().runTask(plugin, () -> 
                updateLeaderboardGUI(gui, leaderboard, statType)))
            .exceptionally(throwable -> {
                logger.warn("Failed to load leaderboard data: " + throwable.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () -> 
                    handleLoadError(gui));
                return null;
            });
    }
    
    /**
     * Updates the GUI with leaderboard data.
     */
    private void updateLeaderboardGUI(Gui gui, List<PlayerStats> leaderboard, StatType statType) {
        layout.getSlots().forEach(slot -> gui.setItem(slot, null));
        
        for (int i = 0; i < Math.min(leaderboard.size(), layout.getMaxEntries()); i++) {
            PlayerStats stats = leaderboard.get(i);
            Item playerItem = createLeaderboardItem(stats, i + 1, statType);
            gui.setItem(layout.getSlots().get(i), playerItem);
        }
        
        fillEmptySlots(gui, leaderboard.size());
    }
    
    private void handleLoadError(Gui gui) {
        Item errorItem = new SimpleItem(new ItemBuilder(Material.BARRIER)
            .setDisplayName("§c§lError")
            .addLoreLines("§7Failed to load leaderboard data"));
            
        layout.getSlots().forEach(slot -> gui.setItem(slot, errorItem));
    }
    
    private void fillEmptySlots(Gui gui, int filledSlots) {
        Item emptyItem = new SimpleItem(new ItemBuilder(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
            .setDisplayName(""));
            
        for (int i = filledSlots; i < layout.getMaxEntries(); i++) {
            gui.setItem(layout.getSlots().get(i), emptyItem);
        }
    }
    
    /**
     * Creates a leaderboard item for a player.
     */
    private Item createLeaderboardItem(PlayerStats stats, int rank, StatType statType) {
        Material material = getMaterialForRank(rank);
        String displayName = String.format("§e§l#%d §f%s", rank, stats.getPlayerName());
        String value = getStatValueString(stats, statType);
        
        return new SimpleItem(new ItemBuilder(material)
            .setDisplayName(displayName)
            .addLoreLines(
                "§7" + getStatTypeDisplayName(statType) + ": §e" + value,
                "§8Last updated: §7" + stats.getLastUpdated()
            ));
    }
    
    private Material getMaterialForRank(int rank) {
        return switch (rank) {
            case 1 -> Material.GOLDEN_HELMET;
            case 2 -> Material.IRON_HELMET;
            case 3 -> Material.COPPER_BLOCK;
            default -> Material.PLAYER_HEAD;
        };
    }
    
    /**
     * Gets the display name for a statistic type.
     */
    private String getStatTypeDisplayName(StatType statType) {
        return switch (statType) {
            case KILLS -> "Kills";
            case WINS -> "Wins";
            case GAMES_PLAYED -> "Games Played";
            case DAMAGE_DEALT -> "Damage Dealt";
            case CHESTS_OPENED -> "Chests Opened";
            default -> statType.name();
        };
    }
    
    /**
     * Gets the formatted string value for a statistic.
     */
    private String getStatValueString(PlayerStats stats, StatType statType) {
        return switch (statType) {
            case KILLS -> String.valueOf(stats.getKills());
            case WINS -> String.valueOf(stats.getWins());
            case GAMES_PLAYED -> String.valueOf(stats.getGamesPlayed());
            case DAMAGE_DEALT -> decimalFormat.format(stats.getTotalDamageDealt());
            case CHESTS_OPENED -> String.valueOf(stats.getChestsOpened());
            default -> "N/A";
        };
    }
    
    private static class LeaderboardLayout {
        private final List<Integer> slots = List.of(
            18, 19, 20, 21, 22, 23, 24,  // Row 3
            27, 28, 29, 30, 31, 32, 33,  // Row 4
            36, 37, 38, 39, 40, 41, 42   // Row 5
        );
        
        public List<Integer> getSlots() {
            return slots;
        }
        
        public int getMaxEntries() {
            return slots.size();
        }
    }
    
    /**
     * Configuration for a tab item.
     */
    private static class TabItemConfig {
        final Material selectedMaterial;
        final Material unselectedMaterial;
        final String displayName;
        
        TabItemConfig(Material selectedMaterial, Material unselectedMaterial, String displayName) {
            this.selectedMaterial = selectedMaterial;
            this.unselectedMaterial = unselectedMaterial;
            this.displayName = displayName;
        }
    }
    
    private static class LeaderboardTabItem extends AbstractItem {
        private final StatType tabType;
        private final StatType currentStatType;
        private final TabItemConfig config;
        private final Consumer<Player> onClick;
        
        LeaderboardTabItem(StatType tabType, StatType currentStatType, TabItemConfig config, Consumer<Player> onClick) {
            this.tabType = tabType;
            this.currentStatType = currentStatType;
            this.config = config;
            this.onClick = onClick;
        }
        
        @Override
        public ItemProvider getItemProvider() {
            boolean isSelected = tabType == currentStatType;
            Material material = isSelected ? config.selectedMaterial : config.unselectedMaterial;
            String name = isSelected ? "§e§l" + config.displayName + " §7(Current)" : "§7" + config.displayName;
            return new ItemBuilder(material).setDisplayName(name);
        }
        
        @Override
        public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
            if (clickType.isLeftClick() && tabType != currentStatType) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                onClick.accept(player);
            }
        }
    }
    
    private static class LeaderboardActionItem extends AbstractItem {
        private final Material material;
        private final String displayName;
        private final String lore;
        private final Consumer<Player> onClick;
        
        LeaderboardActionItem(Material material, String displayName, String lore, Consumer<Player> onClick) {
            this.material = material;
            this.displayName = displayName;
            this.lore = lore;
            this.onClick = onClick;
        }
        
        @Override
        public ItemProvider getItemProvider() {
            return new ItemBuilder(material)
                .setDisplayName(displayName)
                .addLoreLines(lore);
        }
        
        @Override
        public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
            if (clickType.isLeftClick()) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                onClick.accept(player);
            }
        }
    }
} 