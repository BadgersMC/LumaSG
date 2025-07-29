# LumaSG Discord Integration Configuration Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Discord Bot Setup](#discord-bot-setup)
4. [Configuration Reference](#configuration-reference)
5. [Channel Setup](#channel-setup)
6. [Webhook Configuration](#webhook-configuration)
7. [Permission System](#permission-system)
8. [Security Best Practices](#security-best-practices)
9. [Troubleshooting](#troubleshooting)
10. [Examples](#examples)

## Overview

The LumaSG Discord integration provides real-time game updates, player statistics, and administrative commands through Discord. This integration uses JDA 5.6.1 to connect to Discord's API and supports both bot messaging and webhooks for optimal performance.

### Key Features
- **Real-time Game Announcements**: Game start/end, deathmatch, player milestones
- **Statistics Commands**: Player stats, leaderboards, server status
- **Administrative Commands**: Force start/stop games, detailed server information
- **Queue Management**: View queue status and wait times
- **Team Integration**: Team formation and management (if enabled)
- **Rich Embeds**: Formatted messages with colors, thumbnails, and structured data
- **Webhook Support**: High-performance message delivery
- **Permission System**: Discord role-based access control

## Prerequisites

### Required Software
- **Minecraft Server**: Paper 1.21.4 or higher
- **Java**: Java 21 (LTS) or higher
- **LumaSG Plugin**: Latest version with Discord integration
- **Discord Bot**: Created through Discord Developer Portal

### Required Permissions
- **Server Administrator**: To create Discord bot and configure channels
- **Plugin Administrator**: To configure LumaSG settings
- **Developer Mode**: Enabled in Discord to copy channel/role IDs

## Discord Bot Setup

### Step 1: Create Discord Application

1. Go to [Discord Developer Portal](https://discord.com/developers/applications)
2. Click "New Application"
3. Enter application name (e.g., "LumaSG Bot")
4. Click "Create"

### Step 2: Create Bot User

1. Navigate to "Bot" section in left sidebar
2. Click "Add Bot"
3. Configure bot settings:
   - **Username**: Choose a descriptive name
   - **Avatar**: Upload server logo or game-related image
   - **Public Bot**: Disable (recommended for private servers)
   - **Requires OAuth2 Code Grant**: Disable

### Step 3: Get Bot Token

1. In Bot section, click "Reset Token"
2. Copy the token immediately (it won't be shown again)
3. **IMPORTANT**: Keep this token secure and never share it publicly

### Step 4: Configure Bot Permissions

Required bot permissions (permission integer: `274877975552`):
- **View Channels** (1024)
- **Send Messages** (2048)
- **Use Slash Commands** (2147483648)
- **Embed Links** (16384)
- **Attach Files** (32768)
- **Read Message History** (65536)
- **Use External Emojis** (262144)
- **Add Reactions** (64)

### Step 5: Invite Bot to Server

1. Navigate to "OAuth2" > "URL Generator"
2. Select scopes:
   - `bot`
   - `applications.commands`
3. Select permissions (use list above)
4. Copy generated URL and open in browser
5. Select your Discord server and authorize

## Configuration Reference

### Basic Configuration

```yaml
discord:
  # Enable Discord integration
  enabled: true
  
  # Your Discord bot token (keep secure!)
  bot-token: "YOUR_BOT_TOKEN_HERE"
```

### Connection Settings

```yaml
discord:
  connection:
    # Number of reconnection attempts before giving up
    reconnect-attempts: 5
    # Delay between reconnection attempts (in seconds)
    reconnect-delay-seconds: 30
    # Connection timeout (in seconds)
    timeout-seconds: 30
```

**Configuration Options:**
- `reconnect-attempts`: How many times to retry connection (1-10 recommended)
- `reconnect-delay-seconds`: Wait time between retries (10-60 seconds recommended)
- `timeout-seconds`: Connection timeout (15-60 seconds recommended)

### Channel Configuration

```yaml
discord:
  channels:
    # Channel for game announcements
    game-announcements: "123456789012345678"
    # Channel for statistics commands
    statistics: "123456789012345679"
    # Channel for admin commands (restricted recommended)
    admin: "123456789012345680"
```

**How to get Channel IDs:**
1. Enable Developer Mode in Discord (User Settings > Advanced > Developer Mode)
2. Right-click on channel
3. Select "Copy ID"
4. Paste the ID in configuration

### Feature Toggles

```yaml
discord:
  features:
    # Game event announcements
    announce-game-start: true      # Announce when games begin
    announce-game-end: true        # Announce game winners and results
    announce-deathmatch: true      # Announce when deathmatch phase starts
    announce-milestones: true      # Announce player count milestones
    
    # Discord slash commands
    enable-stat-commands: true     # Enable /stats and /leaderboard commands
    enable-admin-commands: true    # Enable admin commands like /forcestop
    enable-queue-commands: true    # Enable /queue and /server status commands
```

### Announcement Settings

```yaml
discord:
  announcements:
    # Player count thresholds for milestone announcements
    milestone-thresholds: [10, 5, 3, 2]
    # Include list of remaining players in announcements
    include-player-list: true
    # Maximum players to show in embed (prevents Discord limits)
    max-players-in-embed: 20
    # Use player Minecraft avatars in Discord embeds
    use-player-avatars: true
```

### Formatting Options

```yaml
discord:
  formatting:
    # Embed color (hex color code)
    embed-color: "#00FF00"
    # Server icon URL for embeds
    server-icon-url: "https://example.com/server-icon.png"
    # Timestamp format for embed footers
    timestamp-format: "yyyy-MM-dd HH:mm:ss"
```

**Color Options:**
- Green: `#00FF00` (default)
- Blue: `#0099FF`
- Red: `#FF0000`
- Gold: `#FFD700`
- Purple: `#9932CC`

## Channel Setup

### Recommended Channel Structure

```
📁 LUMASG CATEGORY
├── 📢 game-announcements    (Public read, bot write)
├── 📊 statistics           (Public read/write for commands)
├── 🔧 admin                (Admin only)
└── 📋 queue-status         (Optional: Public read, bot write)
```

### Channel Permissions

#### Game Announcements Channel
- **@everyone**: View Channel, Read Message History
- **Bot**: View Channel, Send Messages, Embed Links, Use External Emojis

#### Statistics Channel
- **@everyone**: View Channel, Read Message History, Use Application Commands
- **Bot**: View Channel, Send Messages, Embed Links, Use External Emojis

#### Admin Channel
- **@everyone**: No permissions
- **Admin Role**: View Channel, Read Message History, Use Application Commands
- **Bot**: View Channel, Send Messages, Embed Links, Use External Emojis

## Webhook Configuration

Webhooks provide better performance and allow custom usernames/avatars for messages.

### Creating Webhooks

1. Go to channel settings
2. Navigate to "Integrations"
3. Click "Create Webhook"
4. Configure webhook:
   - **Name**: "LumaSG Announcements"
   - **Avatar**: Upload server logo
5. Copy webhook URL

### Webhook Configuration

```yaml
discord:
  webhooks:
    # Webhook URL for game announcements
    game-announcements: "https://discord.com/api/webhooks/123456789/abcdef..."
    # Webhook URL for statistics
    statistics: "https://discord.com/api/webhooks/987654321/fedcba..."
    # Try webhooks first, fallback to bot messaging if they fail
    use-webhooks-first: true
```

### Webhook vs Bot Messages

| Feature | Webhooks | Bot Messages |
|---------|----------|--------------|
| Performance | Faster | Standard |
| Rate Limits | Higher limits | Standard limits |
| Custom Avatar | Yes | No |
| Custom Username | Yes | No |
| Slash Commands | No | Yes |
| Reliability | Requires fallback | More reliable |

## Permission System

### Role-Based Permissions

```yaml
discord:
  permissions:
    # Discord roles that have full admin permissions
    admin-roles: ["Admin", "Owner"]
    # Discord roles that have moderator permissions
    moderator-roles: ["Moderator", "Staff"]
    # Map specific Discord roles to plugin permissions
    role-permissions:
      "Admin": "lumasg.admin"
      "Moderator": "lumasg.moderate"
      "Helper": "lumasg.help"
```

### Available Commands by Permission Level

#### Public Commands (No special permissions required)
- `/stats <player>` - View player statistics
- `/leaderboard [category]` - View leaderboards
- `/server-status` - View server status
- `/arena-status` - View arena states
- `/queue` - View queue information

#### Admin Commands (Requires admin role)
- `/force-start <arena>` - Force start a game
- `/stop-game <arena> [confirm]` - Stop a running game
- `/admin-stats` - View detailed server statistics

### Permission Validation

The bot validates permissions in this order:
1. Check if user has admin role (full access)
2. Check if user has moderator role (limited access)
3. Check specific role permissions mapping
4. Deny access if no permissions found

## Security Best Practices

### Bot Token Security

**DO:**
- Store bot token in secure configuration file
- Use environment variables for production
- Restrict file permissions (600 or 644)
- Regularly rotate bot tokens

**DON'T:**
- Share bot token publicly
- Commit tokens to version control
- Log bot tokens in console/files
- Use tokens in URLs or public messages

### Channel Security

**Recommendations:**
- Use separate channels for different functions
- Restrict admin channel access
- Monitor bot permissions regularly
- Use webhooks for public announcements
- Implement role-based command restrictions

### Server Security

```yaml
discord:
  permissions:
    # Limit admin roles to trusted users only
    admin-roles: ["Owner"]  # Minimal admin access
    # Use specific role mappings instead of broad permissions
    role-permissions:
      "ServerAdmin": "lumasg.admin"
      "GameMod": "lumasg.moderate"
```

### Network Security

- Use HTTPS for all webhook URLs
- Enable SSL for database connections
- Implement rate limiting
- Monitor for suspicious activity
- Keep JDA library updated

## Troubleshooting

### Common Issues

#### Bot Not Connecting

**Symptoms:**
- "Failed to connect to Discord" in logs
- Bot appears offline in Discord
- No response to commands

**Solutions:**
1. Verify bot token is correct and not expired
2. Check internet connectivity
3. Ensure bot has proper permissions
4. Verify Discord API status
5. Check firewall settings

**Debug Steps:**
```yaml
debug:
  enabled: true  # Enable debug logging
```

#### Commands Not Working

**Symptoms:**
- Slash commands don't appear
- "This interaction failed" errors
- Commands not responding

**Solutions:**
1. Verify bot has "Use Slash Commands" permission
2. Check if commands are registered (may take up to 1 hour)
3. Ensure bot is in the server
4. Verify channel permissions
5. Check role permissions configuration

#### Permission Errors

**Symptoms:**
- "Insufficient permissions" messages
- Commands work for some users but not others
- Admin commands not accessible

**Solutions:**
1. Verify Discord role names match configuration
2. Check role hierarchy in Discord
3. Ensure users have correct roles assigned
4. Verify role permissions mapping

#### Webhook Issues

**Symptoms:**
- Messages not appearing in channels
- Webhook errors in logs
- Fallback to bot messages

**Solutions:**
1. Verify webhook URLs are correct
2. Check webhook permissions
3. Ensure webhooks haven't been deleted
4. Test webhook URLs manually

### Debug Configuration

```yaml
discord:
  enabled: true
  bot-token: "YOUR_TOKEN"
  
debug:
  enabled: true
  log-level: "DEBUG"
```

### Log Analysis

**Connection Issues:**
```
[Discord] Failed to connect: Invalid bot token
[Discord] Connection timeout after 30 seconds
[Discord] Reconnection attempt 3/5 failed
```

**Permission Issues:**
```
[Discord] User lacks permission for command: /admin-stats
[Discord] Role 'Moderator' not found in server
[Discord] Invalid role permission mapping
```

**Webhook Issues:**
```
[Discord] Webhook failed, falling back to bot message
[Discord] Invalid webhook URL format
[Discord] Webhook rate limit exceeded
```

## Examples

### Basic Setup Example

```yaml
discord:
  enabled: true
  bot-token: "MTIzNDU2Nzg5MDEyMzQ1Njc4.GhIjKl.MnOpQrStUvWxYzAbCdEfGhIjKlMnOpQrStUvWx"
  
  channels:
    game-announcements: "123456789012345678"
    statistics: "234567890123456789"
    admin: "345678901234567890"
  
  features:
    announce-game-start: true
    announce-game-end: true
    enable-stat-commands: true
    enable-admin-commands: true
  
  permissions:
    admin-roles: ["Admin"]
    moderator-roles: ["Moderator"]
```

### Advanced Setup Example

```yaml
discord:
  enabled: true
  bot-token: "MTIzNDU2Nzg5MDEyMzQ1Njc4.GhIjKl.MnOpQrStUvWxYzAbCdEfGhIjKlMnOpQrStUvWx"
  
  connection:
    reconnect-attempts: 3
    reconnect-delay-seconds: 15
    timeout-seconds: 20
  
  channels:
    game-announcements: "123456789012345678"
    statistics: "234567890123456789"
    admin: "345678901234567890"
  
  webhooks:
    game-announcements: "https://discord.com/api/webhooks/123456789/abcdef..."
    statistics: "https://discord.com/api/webhooks/987654321/fedcba..."
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
    milestone-thresholds: [15, 10, 5, 3, 2]
    include-player-list: true
    max-players-in-embed: 25
    use-player-avatars: true
  
  formatting:
    embed-color: "#FFD700"
    server-icon-url: "https://example.com/server-icon.png"
    timestamp-format: "MMM dd, yyyy HH:mm"
  
  permissions:
    admin-roles: ["Owner", "Admin"]
    moderator-roles: ["Moderator", "Staff", "Helper"]
    role-permissions:
      "Owner": "lumasg.admin"
      "Admin": "lumasg.admin"
      "Moderator": "lumasg.moderate"
      "Staff": "lumasg.moderate"
      "Helper": "lumasg.help"
```

### Production Setup Example

```yaml
discord:
  enabled: true
  bot-token: "${DISCORD_BOT_TOKEN}"  # Use environment variable
  
  connection:
    reconnect-attempts: 5
    reconnect-delay-seconds: 30
    timeout-seconds: 30
  
  channels:
    game-announcements: "123456789012345678"
    statistics: "234567890123456789"
    admin: "345678901234567890"
  
  webhooks:
    game-announcements: "${DISCORD_WEBHOOK_ANNOUNCEMENTS}"
    statistics: "${DISCORD_WEBHOOK_STATS}"
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
    include-player-list: false  # Privacy consideration
    max-players-in-embed: 20
    use-player-avatars: false   # Performance consideration
  
  formatting:
    embed-color: "#00FF00"
    server-icon-url: "https://cdn.example.com/server-icon.png"
    timestamp-format: "yyyy-MM-dd HH:mm:ss"
  
  permissions:
    admin-roles: ["Owner"]  # Minimal admin access
    moderator-roles: ["Admin", "Moderator"]
    role-permissions:
      "Owner": "lumasg.admin"
      "Admin": "lumasg.moderate"
      "Moderator": "lumasg.moderate"
```

## Support and Resources

### Documentation Links
- [Discord Developer Portal](https://discord.com/developers/docs)
- [JDA Documentation](https://jda.wiki/)
- [LumaSG Plugin Documentation](https://github.com/LumaLyte/LumaSG)

### Getting Help
1. Check this configuration guide
2. Review troubleshooting section
3. Enable debug logging
4. Check Discord API status
5. Contact plugin support with logs

### Version Compatibility
- **LumaSG**: 1.0.0+
- **JDA**: 5.6.1+
- **Discord API**: v10
- **Java**: 21+
- **Paper**: 1.21.4+

---

*This guide covers Discord integration configuration for LumaSG. For general plugin configuration, see the main configuration documentation.*