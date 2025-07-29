# LumaSG Discord Integration Troubleshooting Guide

## Quick Diagnosis

### Is Discord Integration Working?

Run through this quick checklist:

1. **Bot Online**: Is your bot showing as online in Discord?
2. **Commands Available**: Do slash commands appear when typing `/`?
3. **Announcements Working**: Do game events trigger Discord messages?
4. **Permissions Working**: Can admin users run admin commands?

If any of these fail, use the detailed troubleshooting sections below.

## Connection Issues

### Bot Won't Connect

#### Symptoms
- Bot appears offline in Discord
- "Failed to connect to Discord" in server logs
- "Invalid bot token" errors
- Connection timeouts

#### Diagnostic Steps

1. **Verify Bot Token**
   ```yaml
   # Check your config.yml
   discord:
     bot-token: "YOUR_TOKEN_HERE"  # Should be 59+ characters
   ```
   
   **Common Token Issues:**
   - Token contains placeholder text (`YOUR_BOT_TOKEN_HERE`)
   - Token was regenerated but config not updated
   - Token copied incorrectly (missing characters)
   - Token exposed and automatically reset by Discord

2. **Test Token Validity**
   - Go to Discord Developer Portal
   - Navigate to your application > Bot
   - If token shows "Click to Reveal Token", it's been reset
   - Generate new token if needed

3. **Check Network Connectivity**
   ```bash
   # Test Discord API connectivity
   curl -I https://discord.com/api/v10/gateway
   ```
   
   **Expected Response:**
   ```
   HTTP/2 200
   content-type: application/json
   ```

4. **Verify Firewall Settings**
   - Ensure outbound HTTPS (port 443) is allowed
   - Discord uses WebSocket connections
   - Check if corporate firewall blocks Discord

#### Solutions

**Invalid Token:**
1. Go to Discord Developer Portal
2. Navigate to Bot section
3. Click "Reset Token"
4. Copy new token immediately
5. Update config.yml with new token
6. Restart server

**Network Issues:**
1. Check server internet connection
2. Verify firewall allows Discord connections
3. Test from different network if possible
4. Contact hosting provider about Discord access

**Configuration Issues:**
```yaml
discord:
  enabled: true  # Must be true
  bot-token: "YOUR_BOT_TOKEN_HERE"
  
  connection:
    reconnect-attempts: 5
    reconnect-delay-seconds: 30
    timeout-seconds: 30
```

### Connection Keeps Dropping

#### Symptoms
- Bot connects then disconnects repeatedly
- "Connection lost, attempting reconnect" messages
- Intermittent command failures

#### Diagnostic Steps

1. **Check Connection Stability**
   ```yaml
   debug:
     enabled: true  # Enable detailed logging
   ```

2. **Monitor Reconnection Attempts**
   Look for log patterns:
   ```
   [Discord] Connection lost: WebSocket connection closed
   [Discord] Reconnection attempt 1/5
   [Discord] Successfully reconnected to Discord
   ```

3. **Check Rate Limiting**
   ```
   [Discord] Rate limit exceeded, waiting 30 seconds
   [Discord] Too many reconnection attempts
   ```

#### Solutions

**Unstable Network:**
```yaml
discord:
  connection:
    reconnect-attempts: 10      # Increase retry attempts
    reconnect-delay-seconds: 60 # Increase delay between retries
    timeout-seconds: 60         # Increase timeout
```

**Rate Limiting:**
- Reduce message frequency
- Implement message queuing
- Use webhooks for better rate limits

**Server Resource Issues:**
- Check server CPU/memory usage
- Reduce other plugin load
- Optimize database queries

## Command Issues

### Slash Commands Not Appearing

#### Symptoms
- Typing `/` shows no LumaSG commands
- "This interaction failed" when using commands
- Commands work for some users but not others

#### Diagnostic Steps

1. **Verify Bot Permissions**
   Required permissions:
   - Use Slash Commands (2147483648)
   - Send Messages (2048)
   - Embed Links (16384)

2. **Check Command Registration**
   ```
   [Discord] Registering Discord slash commands...
   [Discord] Successfully registered 6 Discord slash commands
   ```

3. **Test Command Availability**
   - Commands may take up to 1 hour to appear globally
   - Try in different channels
   - Test with different user accounts

#### Solutions

**Missing Permissions:**
1. Go to Discord server settings
2. Navigate to Roles
3. Find your bot's role
4. Ensure "Use Slash Commands" is enabled
5. Check channel-specific permissions

**Command Registration Failed:**
```yaml
discord:
  features:
    enable-stat-commands: true    # Enable statistics commands
    enable-admin-commands: true   # Enable admin commands
    enable-queue-commands: true   # Enable queue commands
```

**Global Command Delay:**
- Wait up to 1 hour for global commands
- Use guild-specific commands for testing
- Restart Discord client to refresh commands

### Commands Return Errors

#### Symptoms
- "This interaction failed" messages
- Commands timeout without response
- Partial command responses

#### Diagnostic Steps

1. **Check Error Logs**
   ```
   [Discord] Error processing command /stats: Player not found
   [Discord] Command timeout: /leaderboard
   [Discord] Database connection failed during command
   ```

2. **Test Database Connectivity**
   ```yaml
   statistics:
     enabled: true  # Must be enabled for stat commands
   ```

3. **Verify Plugin State**
   - Is LumaSG plugin fully loaded?
   - Are statistics being tracked?
   - Is database accessible?

#### Solutions

**Database Issues:**
1. Check database connection
2. Verify statistics are being saved
3. Test with `/admin-stats` command

**Plugin Configuration:**
```yaml
statistics:
  enabled: true
  save-interval-seconds: 300
  preload-on-join: true
```

**Command Timeouts:**
- Optimize database queries
- Increase command timeout
- Use caching for frequently accessed data

## Permission Issues

### Admin Commands Not Working

#### Symptoms
- "Insufficient permissions" for admin users
- Admin commands don't appear in slash command list
- Commands work in some channels but not others

#### Diagnostic Steps

1. **Verify Role Configuration**
   ```yaml
   discord:
     permissions:
       admin-roles: ["Admin", "Owner"]  # Check role names match exactly
   ```

2. **Check User Roles**
   - Right-click user in Discord
   - Verify they have the correct role
   - Check role hierarchy

3. **Test Permission Mapping**
   ```yaml
   discord:
     permissions:
       role-permissions:
         "Admin": "lumasg.admin"  # Verify permission exists
   ```

#### Solutions

**Role Name Mismatch:**
```yaml
discord:
  permissions:
    admin-roles: ["Administrator"]  # Use exact Discord role name
    moderator-roles: ["Moderator", "Staff"]
```

**Missing Plugin Permissions:**
1. Verify user has plugin permissions in-game
2. Check permission plugin configuration
3. Test with `/lumasg admin` command in-game

**Role Hierarchy Issues:**
1. Ensure bot role is above user roles
2. Check Discord server role hierarchy
3. Move bot role higher if needed

### Role Permissions Not Working

#### Symptoms
- Role mappings don't apply
- Users with roles can't use commands
- Permission checks fail

#### Diagnostic Steps

1. **Check Role Names**
   - Role names are case-sensitive
   - Special characters must match exactly
   - Spaces matter

2. **Verify Permission Strings**
   ```yaml
   role-permissions:
     "Admin": "lumasg.admin"      # Correct
     "Mod": "lumasg.moderate"     # Correct
     "Helper": "lumasg.help"      # Check if this permission exists
   ```

3. **Test Permission Resolution**
   Enable debug logging to see permission checks:
   ```yaml
   debug:
     enabled: true
   ```

#### Solutions

**Fix Role Names:**
```yaml
discord:
  permissions:
    admin-roles: ["Server Admin"]     # Exact match required
    moderator-roles: ["Game Mod"]     # Case sensitive
```

**Verify Permissions:**
1. Check plugin permission documentation
2. Test permissions in-game first
3. Use standard permission nodes

## Message Issues

### Announcements Not Appearing

#### Symptoms
- Game events don't trigger Discord messages
- Some announcements work, others don't
- Messages appear delayed

#### Diagnostic Steps

1. **Check Feature Toggles**
   ```yaml
   discord:
     features:
       announce-game-start: true
       announce-game-end: true
       announce-deathmatch: true
   ```

2. **Verify Channel Configuration**
   ```yaml
   discord:
     channels:
       game-announcements: "123456789012345678"  # Valid channel ID
   ```

3. **Test Channel Permissions**
   - Bot can view channel
   - Bot can send messages
   - Bot can embed links

#### Solutions

**Missing Channel Permissions:**
1. Go to channel settings
2. Navigate to Permissions
3. Add bot role with required permissions:
   - View Channel
   - Send Messages
   - Embed Links
   - Use External Emojis

**Invalid Channel IDs:**
1. Enable Developer Mode in Discord
2. Right-click channel → Copy ID
3. Update configuration with correct ID

**Feature Disabled:**
```yaml
discord:
  features:
    announce-game-start: true
    announce-game-end: true
    announce-deathmatch: true
    announce-milestones: true
```

### Webhook Issues

#### Symptoms
- Webhook messages not appearing
- "Webhook failed, falling back to bot message" in logs
- Inconsistent message delivery

#### Diagnostic Steps

1. **Test Webhook URLs**
   ```bash
   curl -X POST "YOUR_WEBHOOK_URL" \
     -H "Content-Type: application/json" \
     -d '{"content": "Test message"}'
   ```

2. **Check Webhook Configuration**
   ```yaml
   discord:
     webhooks:
       game-announcements: "https://discord.com/api/webhooks/123/abc"
       use-webhooks-first: true
   ```

3. **Verify Webhook Exists**
   - Go to Discord channel settings
   - Check Integrations → Webhooks
   - Ensure webhook wasn't deleted

#### Solutions

**Invalid Webhook URL:**
1. Go to Discord channel settings
2. Navigate to Integrations
3. Create new webhook or copy existing URL
4. Update configuration

**Webhook Deleted:**
1. Create new webhook in Discord
2. Update configuration with new URL
3. Restart plugin to reload configuration

**Rate Limiting:**
```yaml
discord:
  webhooks:
    use-webhooks-first: false  # Disable webhooks temporarily
```

## Performance Issues

### Slow Command Responses

#### Symptoms
- Commands take >3 seconds to respond
- "This interaction failed" due to timeouts
- Database connection timeouts

#### Diagnostic Steps

1. **Check Database Performance**
   ```yaml
   database:
     pool:
       maximum-pool-size: 8
       connection-timeout: 30000
   ```

2. **Monitor Resource Usage**
   - Server CPU usage
   - Memory consumption
   - Database query times

3. **Enable Performance Logging**
   ```yaml
   debug:
     enabled: true
   ```

#### Solutions

**Database Optimization:**
```yaml
database:
  pool:
    minimum-idle: 4
    maximum-pool-size: 16
    connection-timeout: 15000
```

**Caching Configuration:**
```yaml
statistics:
  preload-on-join: true
  save-interval-seconds: 600  # Reduce save frequency
```

**Resource Allocation:**
- Increase server RAM
- Optimize other plugins
- Use SSD storage for database

### Memory Issues

#### Symptoms
- OutOfMemoryError exceptions
- Gradual memory increase
- Server performance degradation

#### Diagnostic Steps

1. **Monitor Memory Usage**
   ```bash
   # Check Java heap usage
   jstat -gc [PID]
   ```

2. **Check for Memory Leaks**
   - Monitor Discord connection objects
   - Check message queue sizes
   - Verify proper cleanup

3. **Review Configuration**
   ```yaml
   discord:
     announcements:
       max-players-in-embed: 20  # Limit embed size
   ```

#### Solutions

**Increase Memory:**
```bash
# JVM arguments
-Xmx4G -Xms2G
```

**Optimize Configuration:**
```yaml
discord:
  announcements:
    include-player-list: false    # Reduce memory usage
    use-player-avatars: false     # Reduce network/memory load
    max-players-in-embed: 10      # Smaller embeds
```

**Connection Management:**
```yaml
discord:
  connection:
    timeout-seconds: 30           # Prevent hanging connections
```

## Configuration Validation

### Common Configuration Errors

#### Invalid YAML Syntax

**Symptoms:**
- Plugin fails to load
- "Configuration error" messages
- Default values used instead of configured values

**Common Issues:**
```yaml
# WRONG - Missing quotes around channel ID
channels:
  game-announcements: 123456789012345678

# CORRECT - Channel ID in quotes
channels:
  game-announcements: "123456789012345678"

# WRONG - Incorrect indentation
discord:
enabled: true

# CORRECT - Proper indentation
discord:
  enabled: true
```

#### Missing Required Fields

**Symptoms:**
- Features don't work as expected
- Default values used
- Validation warnings in logs

**Required Fields:**
```yaml
discord:
  enabled: true                    # Required
  bot-token: "YOUR_TOKEN"         # Required if enabled
  channels:
    game-announcements: "ID"      # Required for announcements
```

### Configuration Validation Tool

Use this checklist to validate your configuration:

1. **Basic Structure**
   - [ ] `discord.enabled` is `true`
   - [ ] `discord.bot-token` is set and valid
   - [ ] YAML syntax is correct

2. **Channel Configuration**
   - [ ] Channel IDs are in quotes
   - [ ] Channel IDs are 18 digits long
   - [ ] Channels exist in Discord server
   - [ ] Bot has access to channels

3. **Permission Configuration**
   - [ ] Role names match Discord exactly
   - [ ] Permission strings are valid
   - [ ] Users have required roles

4. **Feature Configuration**
   - [ ] Required features are enabled
   - [ ] Feature dependencies are met
   - [ ] Statistics enabled for stat commands

## Getting Help

### Before Asking for Help

1. **Enable Debug Logging**
   ```yaml
   debug:
     enabled: true
     log-level: "DEBUG"
   ```

2. **Collect Information**
   - Plugin version
   - Server version (Paper/Spigot)
   - Java version
   - Error logs (last 50 lines)
   - Configuration file (remove bot token)

3. **Test Basic Functionality**
   - Can bot connect?
   - Do commands appear?
   - Are permissions working?

### Support Channels

1. **Plugin Documentation**
   - Configuration guide
   - API documentation
   - Example configurations

2. **Community Support**
   - Discord server
   - GitHub issues
   - Community forums

3. **Professional Support**
   - Priority support
   - Custom configuration
   - Integration assistance

### Log Analysis

**Connection Logs:**
```
[INFO] [Discord] Initializing Discord integration...
[INFO] [Discord] Successfully connected to Discord
[INFO] [Discord] Successfully registered 6 Discord slash commands
```

**Error Logs:**
```
[ERROR] [Discord] Failed to connect: Invalid bot token
[WARN] [Discord] Channel not found: 123456789012345678
[ERROR] [Discord] Command failed: java.sql.SQLException
```

**Debug Logs:**
```
[DEBUG] [Discord] Processing command: /stats player123
[DEBUG] [Discord] User has permission: lumasg.admin
[DEBUG] [Discord] Sending embed to channel: 123456789012345678
```

---

*This troubleshooting guide covers common Discord integration issues. For configuration details, see the main Discord Configuration Guide.*