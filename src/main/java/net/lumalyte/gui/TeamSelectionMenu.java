package net.lumalyte.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.lumalyte.LumaSG;
import net.lumalyte.game.Game;
import net.lumalyte.game.Team;
import net.lumalyte.game.TeamQueueManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.gui.structure.Markers;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu for players to view and join available teams when joining games.
 */
public class TeamSelectionMenu {
    private final LumaSG plugin;
    private final TeamQueueManager queueManager;
    
    /**
     * Constructs a new TeamSelectionMenu.
     * 
     * @param plugin The LumaSG plugin instance
     */
    public TeamSelectionMenu(LumaSG plugin) {
        this.plugin = plugin;
        this.queueManager = plugin.getTeamQueueManager();
    }
    
    /**
     * Opens the team selection menu for a player wanting to join a specific game.
     * 
     * @param player The player to open the menu for
     * @param game The game they want to join
     */
    public void openMenu(Player player, Game game) {
        // Check if player can join this game
        if (!canPlayerJoinGame(player, game)) {
            return;
        }
        
        // Create border item
        Item borderItem = new SimpleItem(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setDisplayName(""));
        
        // Create back button
        Item backButton = createBackButton(player);
        
        // Create refresh button
        Item refreshButton = createRefreshButton(player, game);
        
        // Get all teams for this game
        List<Item> teamItems = new ArrayList<>();
        
        // Add "Create New Team" option first
        teamItems.add(createNewTeamItem(player, game));
        
        // Add existing teams
        for (Team team : game.getTeamManager().getTeams()) {
            teamItems.add(createTeamItem(player, game, team));
        }
        
        // Create the paged GUI
        PagedGui.Builder<Item> builder = PagedGui.items()
            .setStructure(
                "# # # # # # # # #",
                "# x x x x x x x #",
                "# x x x x x x x #",
                "# x x x x x x x #",
                "# b # < p > # r #"
            )
            .addIngredient('#', borderItem)
            .addIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
            .addIngredient('<', new SimpleItem(new ItemBuilder(Material.ARROW).setDisplayName("§a§lPrevious Page")))
            .addIngredient('>', new SimpleItem(new ItemBuilder(Material.ARROW).setDisplayName("§a§lNext Page")))
            .addIngredient('p', new SimpleItem(new ItemBuilder(Material.PAPER)
                .setDisplayName("§f§lPage {page}/{pages}")
                .addLoreLines("§7Choose a team to join")))
            .addIngredient('b', backButton)
            .addIngredient('r', refreshButton)
            .setContent(teamItems);
        
        Gui gui = builder.build();
        
        // Create and open the window
        Window window = Window.single()
            .setViewer(player)
            .setTitle("§8§lLumaSG §7- §fTeam Selection")
            .setGui(gui)
            .build();
        
        window.open();
    }
    
    /**
     * Checks if a player can join the specified game.
     */
    private boolean canPlayerJoinGame(Player player, Game game) {
        // Check if player is already in a game
        if (queueManager.getPlayerTeam(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("§cYou are already in a team! Leave your current team first.", NamedTextColor.RED));
            return false;
        }
        
        // Check game state
        switch (game.getState()) {
            case INACTIVE -> {
                player.sendMessage(Component.text("§cThis game is not ready for players yet!", NamedTextColor.RED));
                return false;
            }
            case WAITING -> {
                return true; // Can join
            }
            default -> {
                player.sendMessage(Component.text("§cThis game has already started!", NamedTextColor.RED));
                return false;
            }
        }
    }
    
    /**
     * Creates a back button.
     */
    private Item createBackButton(Player player) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return new ItemBuilder(Material.ARROW)
                    .setDisplayName("§c§lBack to Games")
                    .addLoreLines("Click to return to the game browser");
            }
            
            @Override
            public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    player.closeInventory();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        new GameBrowserMenu(plugin).openMenu(player);
                    });
                }
            }
        };
    }
    
    /**
     * Creates a refresh button.
     */
    private Item createRefreshButton(Player player, Game game) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return new ItemBuilder(Material.CLOCK)
                    .setDisplayName("§a§lRefresh")
                    .addLoreLines("Click to refresh the team list");
            }
            
            @Override
            public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    player.closeInventory();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        openMenu(player, game);
                    });
                }
            }
        };
    }
    
    /**
     * Creates the "Create New Team" item.
     */
    private Item createNewTeamItem(Player player, Game game) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return new ItemBuilder(Material.EMERALD_BLOCK)
                    .setDisplayName("§a§lCreate New Team")
                    .addLoreLines(
                        "§7Start your own team for this game",
                        "§7Other players can join your team",
                        "",
                        "§eClick to create a new team"
                    );
            }
            
            @Override
            public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    player.closeInventory();
                    
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        // Create team with default settings (open, auto-fill enabled)
                        Team newTeam = queueManager.createTeam(player, game, false, true);
                        if (newTeam != null) {
                            player.sendMessage(Component.text("§aCreated new team! Other players can now join you.", NamedTextColor.GREEN));
                        } else {
                            player.sendMessage(Component.text("§cFailed to create team!", NamedTextColor.RED));
                        }
                    });
                }
            }
        };
    }
    
    /**
     * Creates an item representing a team.
     */
    private Item createTeamItem(Player player, Game game, Team team) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                int teamSize = team.getMemberCount();
                int maxSize = game.getGameMode().getTeamSize();
                boolean isFull = teamSize >= maxSize;
                boolean isInviteOnly = team.isInviteOnly();
                boolean canJoin = !isFull && (!isInviteOnly || queueManager.hasInvitation(player.getUniqueId(), team));
                
                // Determine material and status
                Material material;
                if (team.isFull(game.getGameMode())) {
                    material = Material.RED_CONCRETE;
                } else if (team.isInviteOnly() && !queueManager.hasInvitation(player.getUniqueId(), team)) {
                    material = Material.ORANGE_CONCRETE;
                } else {
                    material = Material.LIME_CONCRETE;
                }
                
                // Build team member list
                List<String> lore = new ArrayList<>();
                lore.add("§7Status: " + (isFull ? "§cFull" : (isInviteOnly ? "§6Invite Only" : "§aJoinable")));
                lore.add("§7Size: §f" + teamSize + "/" + maxSize);
                lore.add("§7Privacy: §f" + (isInviteOnly ? "Invite Only" : "Open"));
                lore.add("");
                lore.add("§7Members:");
                
                // Add team members to lore
                List<String> memberNames = team.getMemberNames();
                if (memberNames.isEmpty()) {
                    lore.add("§8  None");
                } else {
                    for (int i = 0; i < memberNames.size() && i < 5; i++) { // Limit to 5 names
                        String memberName = memberNames.get(i);
                        String prefix = team.getLeader().equals(team.getMemberByName(memberName)) ? "§6★ " : "§7  ";
                        lore.add(prefix + memberName);
                    }
                    if (memberNames.size() > 5) {
                        lore.add("§7  ... and " + (memberNames.size() - 5) + " more");
                    }
                }
                
                lore.add("");
                if (canJoin) {
                    lore.add("§aClick to join this team");
                } else if (isFull) {
                    lore.add("§cThis team is full");
                } else if (isInviteOnly) {
                    lore.add("§cYou need an invitation to join");
                }
                
                return new ItemBuilder(material)
                    .setDisplayName("§b§lTeam " + team.getDisplayNumber())
                    .addLoreLines(lore.toArray(new String[0]));
            }
            
            @Override
            public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    
                    // Check if player can join
                    int teamSize = team.getMemberCount();
                    int maxSize = game.getGameMode().getTeamSize();
                    boolean isFull = teamSize >= maxSize;
                    boolean isInviteOnly = team.isInviteOnly();
                    boolean hasInvite = queueManager.hasInvitation(player.getUniqueId(), team);
                    
                    if (isFull) {
                        player.sendMessage(Component.text("§cThis team is full!", NamedTextColor.RED));
                        return;
                    }
                    
                    if (isInviteOnly && !hasInvite) {
                        player.sendMessage(Component.text("§cYou need an invitation to join this team!", NamedTextColor.RED));
                        return;
                    }
                    
                    player.closeInventory();
                    
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        // Join the team
                        boolean success = queueManager.joinTeam(player, team);
                        if (success) {
                            player.sendMessage(Component.text("§aJoined Team " + team.getDisplayNumber() + "!", NamedTextColor.GREEN));
                            
                            // Remove any pending invitation
                            if (hasInvite) {
                                queueManager.removeInvitation(player.getUniqueId());
                            }
                        } else {
                            player.sendMessage(Component.text("§cFailed to join team!", NamedTextColor.RED));
                        }
                    });
                }
            }
        };
    }
} 