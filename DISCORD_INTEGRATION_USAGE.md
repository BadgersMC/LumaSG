# Discord Integration Usage Guide

## Overview

The Discord integration system provides comprehensive Discord bot functionality for LumaSG, including game announcements, player statistics, and server status updates.

## Architecture

The Discord integration follows a modular architecture with specialized managers:

```
DiscordIntegrationManager (Main orchestrator)
├── DiscordConfigManager (Configuration management)
├── DiscordConnectionManager (JDA connection handling)
├── DiscordEmbedBuilder (Rich embed creation)
├── DiscordWebhookManager (Webhook operations)
├── DiscordAnnouncementManager (Game announcements)
└── DiscordCommandManager (Discord slash commands)
```

## Setup Instructions

### 1. Add Discord Integration to Main Plugin

Add the Discord integration manager to your main plugin class:

```java
public class LumaSG extends JavaPlugin {
    
    private DiscordIntegrationManager discordManager;
    
    @Override
    public void onEnable() {
        // ... existing initialization ...
        
        // Initialize Discord integration
        discordManager = new DiscordIntegrationManager(this);
        if (discordManager.initialize()) {
            debugLogger.startup("Discord integration enabled successfully!");
        } else {
            debugLogger.warn("Discord integration failed to initialize");
        }
    }
    
    @Override
    public void onDisable() {
        // ... existing shutdown ...
        
        // Shutdown Discord integration
        if (discordManager != null) {
            discordManager.shutdown();
        }
    }
    
    public DiscordIntegrationManager getDiscordIntegrationManager() {
        return discordManager;
    }
}
```

### 2. Configure Discord Settings

Add Discord configuration to your `config.yml`:

```yaml
discord:
  enabled: true
  bot-token: "YOUR_BOT_TOKEN_HERE"
  
  # Channel configuration
  announcement-channel-id: "1234567890123456789"
  
  # Connection settings
  connection-timeout: 30000
  reconnect-attempts: 5
  reconnect-delay: 5000
  
  # Embed settings
  embed-color: "#00FF00"
  server-icon-url: "https://your-server.com/icon.png"
  use-player-avatars: true
  include-player-list: true
  max-players-in-embed: 10
```

### 3. Integrate Game Announcements

Modify your Game class to include Discord announcements:

```java
public class Game {
    
    private final GameDiscordIntegration discordIntegration;
    
    public Game(LumaSG plugin, Arena arena) {
        // ... existing initialization ...
        
        // Initialize Discord integration
        this.discordIntegration = new GameDiscordIntegration(plugin);
    }
    
    private void startGame() {
        // ... existing game start logic ...
        
        // Announce game start to Discord
        discordIntegration.announceGameStart(this);
    }
    
    public void endGame(Player winner) {
        // ... existing game end logic ...
        
        // Announce game end to Discord
        discordIntegration.announceGameEnd(this, winner);
    }
    
    private void startDeathmatch() {
        // ... existing deathmatch logic ...
        
        // Announce deathmatch to Discord
        discordIntegration.announceDeathmatch(this);
    }
    
    // Call this when players are eliminated
    private void checkPlayerMilestones() {
        int remainingPlayers = getPlayerCount();
        
        // Announce milestones at key player counts
        if (remainingPlayers == 10 || remainingPlayers == 5 || 
            remainingPlayers == 3 || remainingPlayers == 2) {
            discordIntegration.announcePlayerMilestone(this, remainingPlayers);
        }
    }
}
```

## Discord Bot Setup

### 1. Create Discord Application

1. Go to https://discord.com/developers/applications
2. Click "New Application"
3. Name your application (e.g., "LumaSG Bot")
4. Go to the "Bot" section
5. Click "Add Bot"
6. Copy the bot token and add it to your config

### 2. Bot Permissions

Your bot needs the following permissions:
- Send Messages
- Use Slash Commands
- Embed Links
- Read Message History
- Use External Emojis

### 3. Invite Bot to Server

1. Go to OAuth2 > URL Generator
2. Select "bot" and "applications.commands" scopes
3. Select the required permissions
4. Use the generated URL to invite the bot

## Available Announcement Types

### Game Start Announcements

Automatically sent when a game begins:
- Arena name
- Player count
- Game mode
- Player list (if enabled)

### Game End Announcements

Sent when a game concludes:
- Winner information
- Game duration
- Final statistics

### Deathmatch Announcements

Sent when deathmatch phase begins:
- Remaining players
- Border shrinking notification

### Player Milestones

Sent at key player counts:
- 10 players remaining
- 5 players remaining
- 3 players remaining (Final 3)
- 2 players remaining (Final 2)

## Embed Customization

All Discord embeds can be customized through configuration:

```yaml
discord:
  embed-color: "#FF5733"  # Hex color code
  server-icon-url: "https://example.com/icon.png"
  use-player-avatars: true
  include-player-list: true
  max-players-in-embed: 15
```

## Error Handling

The Discord integration includes comprehensive error handling:

- **Connection failures**: Automatic reconnection with exponential backoff
- **Rate limiting**: Built-in rate limit handling
- **Invalid configuration**: Graceful degradation
- **Channel not found**: Fallback to console logging

## Performance Considerations

- All Discord operations are asynchronous
- Embed creation is optimized for Discord's size limits
- Connection pooling for webhook operations
- Automatic cleanup of resources

## Troubleshooting

### Bot Not Responding

1. Check bot token in configuration
2. Verify bot has required permissions
3. Check Discord connection status in logs
4. Ensure channel IDs are correct

### Announcements Not Sending

1. Verify announcement channel ID
2. Check bot permissions in the channel
3. Review Discord integration logs
4. Test connection with `/discord status` command

### Performance Issues

1. Monitor Discord API rate limits
2. Check for excessive announcement frequency
3. Review embed size limits
4. Monitor memory usage

## API Reference

### DiscordAnnouncementManager

```java
// Announce game events
CompletableFuture<Boolean> announceGameStart(Game game)
CompletableFuture<Boolean> announceGameEnd(Game game, Player winner)
CompletableFuture<Boolean> announceDeathmatch(Game game)
CompletableFuture<Boolean> announcePlayerMilestone(Game game, int remainingPlayers)

// Check if announcements are enabled
boolean isAnnouncementsEnabled()
```

### DiscordEmbedBuilder

```java
// Game embeds
MessageEmbed createGameStartEmbed(Game game)
MessageEmbed createGameEndEmbed(Game game, Player winner)
MessageEmbed createDeathmatchEmbed(Game game)
MessageEmbed createPlayerMilestoneEmbed(Game game, int remainingPlayers)

// Status embeds
MessageEmbed createServerStatusEmbed(int totalPlayers, int activeGames, int waitingGames)
MessageEmbed createErrorEmbed(String title, String description)
MessageEmbed createSuccessEmbed(String title, String description)
```

## Future Enhancements

Planned features for future releases:

- Discord slash commands for server management
- Player statistics embeds
- Leaderboard announcements
- Custom webhook support
- Multi-language support
- Advanced embed templates