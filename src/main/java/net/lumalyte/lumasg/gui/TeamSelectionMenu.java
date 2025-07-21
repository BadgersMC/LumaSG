package net.lumalyte.lumasg.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.game.Game;
import net.lumalyte.lumasg.game.Team;
import net.lumalyte.lumasg.game.TeamQueueManager;
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
        Item backButton = createBackButton();
        
        // Create refresh button
        Item refreshButton = createRefreshButton(game);
        
        // Get all teams for this game
        List<Item> teamItems = new ArrayList<>();
        
        // Add "Create New Team" option first
        teamItems.add(createNewTeamItem(game));
        
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
    private Item createBackButton() {
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
    private Item createRefreshButton(Game game) {
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
    private Item createNewTeamItem(Game game) {
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
                TeamDisplayData displayData = buildTeamDisplayData(team, game, player);
                List<String> lore = buildTeamLore(team, game, displayData);
                
                return new ItemBuilder(displayData.material())
                    .setDisplayName("§b§lTeam " + team.getDisplayNumber())
                    .addLoreLines(lore.toArray(new String[0]));
            }
            
            @Override
            public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    handleTeamJoinClick(player, game, team);
                }
            }
        };
    }
    
    /**
     * Data class for team display information
     */
    private record TeamDisplayData(
        Material material,
        boolean canJoin,
        boolean isFull,
        boolean isInviteOnly,
        boolean hasInvitation,
        int teamSize,
        int maxSize
    ) {}
    
    /**
     * Builds display data for a team
     */
    private TeamDisplayData buildTeamDisplayData(Team team, Game game, Player player) {
        int teamSize = team.getMemberCount();
        int maxSize = game.getGameMode().getTeamSize();
        boolean isFull = teamSize >= maxSize;
        boolean isInviteOnly = team.isInviteOnly();
        boolean hasInvitation = queueManager.hasInvitation(player.getUniqueId(), team);
        boolean canJoin = !isFull && (!isInviteOnly || hasInvitation);
        
        // Determine material based on team status
        Material material = determineMaterial(isFull, isInviteOnly, hasInvitation);
        
        return new TeamDisplayData(
            material, canJoin, isFull, isInviteOnly, 
            hasInvitation, teamSize, maxSize
        );
    }
    
    /**
     * Determines the material for team display based on status
     */
    private Material determineMaterial(boolean isFull, boolean isInviteOnly, boolean hasInvitation) {
        if (isFull) {
            return Material.RED_CONCRETE;
        } else if (isInviteOnly && !hasInvitation) {
            return Material.ORANGE_CONCRETE;
        } else {
            return Material.LIME_CONCRETE;
        }
    }
    
    /**
     * Builds the lore for a team item
     */
    private List<String> buildTeamLore(Team team, Game game, TeamDisplayData displayData) {
        List<String> lore = new ArrayList<>();
        
        // Status and basic info
        addTeamStatusToLore(lore, displayData);
        addTeamMembersToLore(lore, team);
        addTeamActionToLore(lore, displayData);
        
        return lore;
    }
    
    /**
     * Adds team status information to lore
     */
    private void addTeamStatusToLore(List<String> lore, TeamDisplayData data) {
        String status = data.isFull() ? "§cFull" : 
                       (data.isInviteOnly() ? "§6Invite Only" : "§aJoinable");
        String privacy = data.isInviteOnly() ? "Invite Only" : "Open";
        
        lore.add("§7Status: " + status);
        lore.add("§7Size: §f" + data.teamSize() + "/" + data.maxSize());
        lore.add("§7Privacy: §f" + privacy);
        lore.add("");
    }
    
    /**
     * Adds team members information to lore
     */
    private void addTeamMembersToLore(List<String> lore, Team team) {
        lore.add("§7Members:");
        
        List<String> memberNames = team.getMemberNames();
        if (memberNames.isEmpty()) {
            lore.add("§8  None");
        } else {
            addMemberNamesToLore(lore, team, memberNames);
        }
        lore.add("");
    }
    
    /**
     * Adds member names to lore with leader indication
     */
    private void addMemberNamesToLore(List<String> lore, Team team, List<String> memberNames) {
        int maxDisplayed = Math.min(memberNames.size(), 5);
        
        for (int i = 0; i < maxDisplayed; i++) {
            String memberName = memberNames.get(i);
            String prefix = isTeamLeader(team, memberName) ? "§6★ " : "§7  ";
            lore.add(prefix + memberName);
        }
        
        if (memberNames.size() > 5) {
            lore.add("§7  ... and " + (memberNames.size() - 5) + " more");
        }
    }
    
    /**
     * Checks if a member is the team leader
     */
    private boolean isTeamLeader(Team team, String memberName) {
        return team.getLeader().equals(team.getMemberByName(memberName));
    }
    
    /**
     * Adds action information to lore
     */
    private void addTeamActionToLore(List<String> lore, TeamDisplayData data) {
        if (data.canJoin()) {
            lore.add("§aClick to join this team");
        } else if (data.isFull()) {
            lore.add("§cThis team is full");
        } else if (data.isInviteOnly()) {
            lore.add("§cYou need an invitation to join");
        }
    }
    
    /**
     * Handles the click action for joining a team
     */
    private void handleTeamJoinClick(Player player, Game game, Team team) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    
        TeamJoinResult joinResult = validateTeamJoin(player, game, team);
        
        if (!joinResult.canJoin()) {
            player.sendMessage(Component.text(joinResult.errorMessage(), NamedTextColor.RED));
            return;
        }
        
        player.closeInventory();
        executeTeamJoin(player, team, joinResult.hasInvitation());
    }
    
    /**
     * Data class for team join validation results
     */
    private record TeamJoinResult(
        boolean canJoin,
        boolean hasInvitation,
        String errorMessage
    ) {}
    
    /**
     * Validates if a player can join a team
     */
    private TeamJoinResult validateTeamJoin(Player player, Game game, Team team) {
                    int teamSize = team.getMemberCount();
                    int maxSize = game.getGameMode().getTeamSize();
                    boolean isFull = teamSize >= maxSize;
                    boolean isInviteOnly = team.isInviteOnly();
                    boolean hasInvite = queueManager.hasInvitation(player.getUniqueId(), team);
                    
                    if (isFull) {
            return new TeamJoinResult(false, false, "§cThis team is full!");
                    }
                    
                    if (isInviteOnly && !hasInvite) {
            return new TeamJoinResult(false, false, "§cYou need an invitation to join this team!");
                    }
                    
        return new TeamJoinResult(true, hasInvite, "");
    }
                    
    /**
     * Executes the team join action
     */
    private void executeTeamJoin(Player player, Team team, boolean hasInvitation) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        boolean success = queueManager.joinTeam(player, team);
            
                        if (success) {
                            player.sendMessage(Component.text("§aJoined Team " + team.getDisplayNumber() + "!", NamedTextColor.GREEN));
                            
                if (hasInvitation) {
                                queueManager.removeInvitation(player.getUniqueId());
                            }
                        } else {
                            player.sendMessage(Component.text("§cFailed to join team!", NamedTextColor.RED));
                        }
                    });
    }
} 
