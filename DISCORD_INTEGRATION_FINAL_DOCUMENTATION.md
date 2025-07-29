# Discord Integration - Final Documentation

## Overview

The LumaSG Discord Integration provides comprehensive real-time game announcements, statistics commands, team management, and administrative tools through Discord. This integration is production-ready with advanced monitoring, error handling, performance optimization, and security features.

## Features

### ✅ Core Features
- **Real-time Game Announcements**: Automatic announcements for game start, end, deathmatch, and player milestones
- **Discord Slash Commands**: `/stats`, `/leaderboard`, `/queue`, `/server`, and admin commands
- **Team Management**: Discord-based team formation and management
- **Security System**: Role-based permissions, audit logging, and secure token management
- **Performance Optimization**: Advanced memory management, message queuing, and connection optimization
- **Health Monitoring**: Comprehensive health checks and automatic recovery
- **Error Handling**: Resilient error handling with retry mechanisms and graceful degradation

### ✅ Advanced Features
- **Webhook Support**: High-performance webhook messaging with bot fallback
- **Message Processing**: Asynchronous message processing with rate limit handling
- **Memory Management**: Automatic memory optimization and cleanup
- **Connection Resilience**: Automatic reconnection and connection health monitoring
- **Performance Metrics**: Detailed performance monitoring and optimization
- **Configuration Validation**: Comprehensive configuration validation and error reporting

## Installation & Setup

### 1. Discord Bot Setup

1. **Create Discord Application**:
   - Go to https://discord.com/developers/applications
   - Click "New Application" and give it a name
   - Go to "Bot" section and click "Add Bot"
   - Copy the bot token (keep this secure!)

2. **Bot Permissions**:
   Required permissions: `Send Messages`, `Use Slash Commands`, `Embed Links`, `Read Message History`
   
   Permission integer: `274877908992`

3. **Invite Bot to Server**:
   ```
   https://discord.com/api/oauth2/authorize?client_id=YOUR_BOT_ID&permissions=274877908992&scope=bot%20applications.commands
   ```

### 2. Plugin Configuration

Edit `config.yml`:

```yaml
discord:
  enabled: true
  bot-token: "YOUR_BOT_TOKEN_HERE"
  
  channels:
    game-announcements: "CHANNEL_ID_HERE"
    statistics: "CHANNEL_ID_HERE"
    admin: "CHANNEL_ID_HERE"
  
  webhooks:
    game-announcements: "WEBHOOK_URL_HERE"
    statistics: "WEBHOOK_URL_HERE"
    use-webhooks-first: true
  
  features:
    announce-game-start: true
    announce-game-end: true
    announce-deathmatch: true
    announce-milestones: true
    enable-stat-commands: true
    enable-admin-commands: true
    enable-queue-commands: true
  
  permissions:
    admin-roles: ["Admin", "Owner"]
    moderator-roles: ["Moderator", "Staff"]
```

### 3. Channel Setup

1. **Get Channel IDs**:
   - Enable Developer Mode in Discord
   - Right-click channels → "Copy ID"
   - Paste IDs into configuration

2. **Create Webhooks** (Optional but recommended):
   - Go to channel settings → Integrations → Webhooks
   - Create webhook and copy URL
   - Paste URLs into configuration

## Usage

### Game Announcements

The integration automatically announces:
- **Game Start**: When a game begins with player list
- **Game End**: Winner announcement with statistics
- **Deathmatch**: When deathmatch phase starts
- **Milestones**: Player count milestones (10, 5, 3, 2 remaining)

### Discord Commands

#### Player Commands
- `/stats [player]` - View player statistics
- `/leaderboard [type]` - View server leaderboards
- `/queue` - View current game queue status
- `/server` - View server status and information

#### Admin Commands (Restricted)
- `/forcestop <arena>` - Force stop a game
- `/reload` - Reload plugin configuration
- `/maintenance <enable/disable>` - Toggle maintenance mode

### Team Management

- **Team Formation**: Players can form teams through Discord
- **Team Invitations**: Send and manage team invitations
- **Team Status**: View team members and status
- **Auto-balancing**: Automatic team balancing for fair games

## Monitoring & Administration

### In-Game Commands

Use `/sg debug discord-*` commands for monitoring:

```
/sg debug discord-status      - Basic integration status
/sg debug discord-health      - Detailed health check
/sg debug discord-performance - Performance metrics
/sg debug discord-validate    - Full integration validation
/sg debug discord-optimize    - Force performance optimization
```

### Health Monitoring

The integration includes comprehensive health monitoring:

- **Connection Health**: Monitors Discord connection status
- **Performance Health**: Tracks memory usage and processing speed
- **Component Health**: Monitors all integration components
- **Automatic Recovery**: Attempts automatic recovery from failures

### Performance Optimization

Automatic performance optimization includes:

- **Memory Management**: Automatic memory cleanup and optimization
- **Cache Optimization**: Intelligent cache management
- **Message Queue Optimization**: Efficient message processing
- **Connection Optimization**: Connection pooling and optimization

## Security

### Token Security
- Bot tokens are never logged or exposed
- Secure token storage and validation
- Automatic token validation on startup

### Permission System
- Role-based permission mapping
- Channel-specific access control
- Command permission validation
- Audit logging for all actions

### Audit Logging
- All administrative actions are logged
- Security events are tracked
- Failed authentication attempts are recorded
- Comprehensive audit trail

## Error Handling

### Resilience Features
- **Automatic Reconnection**: Reconnects on connection loss
- **Retry Mechanisms**: Automatic retry for failed operations
- **Graceful Degradation**: Continues operation with reduced functionality
- **Error Recovery**: Automatic recovery from common errors

### Error Types Handled
- Network connectivity issues
- Discord API rate limits
- Invalid configurations
- Missing permissions
- Channel access issues
- Bot token problems

## Performance

### Optimization Features
- **Asynchronous Processing**: All Discord operations are non-blocking
- **Message Queuing**: Efficient message queuing with rate limit handling
- **Memory Management**: Automatic memory optimization and cleanup
- **Connection Pooling**: Efficient connection management
- **Cache Management**: Intelligent caching with automatic cleanup

### Performance Metrics
- Message processing speed
- Memory usage tracking
- Connection health monitoring
- Queue size monitoring
- Error rate tracking

## Troubleshooting

### Common Issues

#### 1. Bot Not Responding
**Symptoms**: Commands don't work, no announcements
**Solutions**:
- Check bot token in configuration
- Verify bot has required permissions
- Check `/sg debug discord-status`
- Restart plugin with `/sg reload`

#### 2. Missing Announcements
**Symptoms**: Games start but no Discord announcements
**Solutions**:
- Verify channel IDs are correct
- Check bot can send messages to channels
- Verify announcement features are enabled
- Check `/sg debug discord-health`

#### 3. Commands Not Working
**Symptoms**: Slash commands don't appear or fail
**Solutions**:
- Verify bot has "Use Slash Commands" permission
- Check role permissions in configuration
- Wait up to 1 hour for Discord to update commands
- Check `/sg debug discord-validate`

#### 4. Performance Issues
**Symptoms**: Slow responses, high memory usage
**Solutions**:
- Run `/sg debug discord-performance`
- Use `/sg debug discord-optimize`
- Check server resources
- Review configuration settings

### Debug Commands

```bash
# Check overall status
/sg debug discord-status

# Perform health check
/sg debug discord-health

# View performance metrics
/sg debug discord-performance

# Validate entire integration
/sg debug discord-validate

# Force optimization
/sg debug discord-optimize
```

### Log Analysis

Check server logs for:
- `[DiscordIntegration]` - General integration messages
- `[DiscordConnection]` - Connection-related messages
- `[DiscordSecurity]` - Security and permission messages
- `[DiscordPerformance]` - Performance and optimization messages

## Configuration Reference

### Complete Configuration Example

```yaml
discord:
  enabled: true
  bot-token: "YOUR_BOT_TOKEN_HERE"
  
  connection:
    reconnect-attempts: 5
    reconnect-delay-seconds: 30
    timeout-seconds: 30
  
  channels:
    game-announcements: "123456789012345678"
    statistics: "123456789012345678"
    admin: "123456789012345678"
  
  webhooks:
    game-announcements: "https://discord.com/api/webhooks/..."
    statistics: "https://discord.com/api/webhooks/..."
    use-webhooks-first: true
  
  features:
    announce-game-start: true
    announce-game-end: true
    announce-deathmatch: true
    announce-milestones: true
    enable-stat-commands: true
    enable-admin-commands: true
    enable-queue-commands: true
  
  announcements:
    milestone-thresholds: [10, 5, 3, 2]
    include-player-list: true
    max-players-in-embed: 20
    use-player-avatars: true
  
  formatting:
    embed-color: "#00FF00"
    server-icon-url: ""
    timestamp-format: "yyyy-MM-dd HH:mm:ss"
  
  permissions:
    admin-roles: ["Admin", "Owner"]
    moderator-roles: ["Moderator", "Staff"]
    role-permissions:
      "Admin": "lumasg.admin"
      "Moderator": "lumasg.moderate"
```

## API Integration

### Event Hooks

The integration automatically hooks into LumaSG events:
- Game start/end events
- Player join/leave events
- Deathmatch events
- Player elimination events
- Statistics updates

### Custom Integration

Developers can extend the integration:

```java
// Get Discord integration manager
DiscordIntegrationManager discord = plugin.getDiscordIntegrationManager();

// Send custom announcement
if (discord.isEnabled()) {
    discord.getAnnouncementManager().sendCustomAnnouncement(
        "Custom message", 
        channel
    );
}

// Check integration health
boolean healthy = discord.isHealthy();

// Get performance metrics
var metrics = discord.getPerformanceOptimizer().getPerformanceMetrics();
```

## Support

### Getting Help

1. **Check Documentation**: Review this documentation and configuration guides
2. **Debug Commands**: Use in-game debug commands for diagnostics
3. **Log Analysis**: Check server logs for error messages
4. **Configuration Validation**: Ensure configuration is correct
5. **Permission Check**: Verify Discord bot permissions

### Reporting Issues

When reporting issues, include:
- Plugin version
- Discord integration configuration (without bot token)
- Output from `/sg debug discord-validate`
- Relevant server logs
- Steps to reproduce the issue

## Changelog

### Version 1.0.0 (Final Release)
- ✅ Complete Discord integration implementation
- ✅ Real-time game announcements
- ✅ Discord slash commands
- ✅ Team management system
- ✅ Advanced security features
- ✅ Performance optimization
- ✅ Health monitoring
- ✅ Comprehensive error handling
- ✅ Production-ready deployment
- ✅ Full documentation and guides

## License

This Discord integration is part of the LumaSG plugin and follows the same licensing terms.