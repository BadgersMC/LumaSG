# Discord Bot Setup Instructions for LumaSG

## Overview

This guide walks you through creating and configuring a Discord bot for LumaSG integration. Follow these steps carefully to ensure proper setup and security.

## Prerequisites

- Discord account with server administrator permissions
- Access to your Minecraft server configuration
- Basic understanding of Discord permissions
- Text editor for configuration files

## Step-by-Step Setup

### Step 1: Create Discord Application

1. **Navigate to Discord Developer Portal**
   - Go to [https://discord.com/developers/applications](https://discord.com/developers/applications)
   - Log in with your Discord account

2. **Create New Application**
   - Click "New Application" button (top right)
   - Enter application name: `LumaSG Bot` (or your server name)
   - Click "Create"

3. **Configure Application Settings**
   - **Name**: Choose a descriptive name (e.g., "MyServer LumaSG")
   - **Description**: "LumaSG Survival Games integration bot"
   - **Avatar**: Upload your server logo or game-related image
   - **Tags**: Add relevant tags like "gaming", "minecraft"

### Step 2: Create Bot User

1. **Navigate to Bot Section**
   - Click "Bot" in the left sidebar
   - Click "Add Bot" button
   - Confirm by clicking "Yes, do it!"

2. **Configure Bot Settings**
   ```
   Username: LumaSG-Bot (or your preference)
   Avatar: Upload server logo or Minecraft-related image
   ```

3. **Important Bot Settings**
   - **Public Bot**: ❌ Disable (recommended for private servers)
   - **Requires OAuth2 Code Grant**: ❌ Disable
   - **Server Members Intent**: ❌ Not required
   - **Presence Intent**: ❌ Not required
   - **Message Content Intent**: ❌ Not required

### Step 3: Get Bot Token

1. **Generate Token**
   - In the Bot section, find "Token"
   - Click "Reset Token" (or "Copy" if first time)
   - **IMPORTANT**: Copy the token immediately and store it securely

2. **Token Security**
   ```
   ⚠️  CRITICAL SECURITY WARNING:
   - Never share this token publicly
   - Don't commit it to version control
   - Store it in a secure location
   - Treat it like a password
   ```

3. **Example Token Format**
   ```
   MTIzNDU2Nzg5MDEyMzQ1Njc4.GhIjKl.MnOpQrStUvWxYzAbCdEfGhIjKlMnOpQrStUvWx
   ```

### Step 4: Configure Bot Permissions

1. **Navigate to OAuth2 > URL Generator**
   - Click "OAuth2" in left sidebar
   - Click "URL Generator" sub-section

2. **Select Scopes**
   - ✅ `bot` - Required for bot functionality
   - ✅ `applications.commands` - Required for slash commands

3. **Select Bot Permissions**
   
   **Essential Permissions:**
   - ✅ View Channels (1024)
   - ✅ Send Messages (2048)
   - ✅ Use Slash Commands (2147483648)
   - ✅ Embed Links (16384)
   - ✅ Attach Files (32768)
   - ✅ Read Message History (65536)
   - ✅ Use External Emojis (262144)
   - ✅ Add Reactions (64)

   **Optional Permissions:**
   - ✅ Manage Messages (8192) - For message cleanup
   - ✅ Use External Stickers (137438953472) - For enhanced messages

   **Avoid These Permissions:**
   - ❌ Administrator - Too broad, security risk
   - ❌ Manage Server - Not needed
   - ❌ Manage Channels - Not needed
   - ❌ Manage Roles - Not needed

4. **Permission Integer**
   - Total permission integer: `274877975552`
   - Copy this for manual permission setup if needed

### Step 5: Invite Bot to Server

1. **Generate Invite URL**
   - Copy the generated URL from OAuth2 URL Generator
   - Example: `https://discord.com/api/oauth2/authorize?client_id=123456789&permissions=274877975552&scope=bot%20applications.commands`

2. **Invite Bot**
   - Open the URL in your browser
   - Select your Discord server from dropdown
   - Click "Authorize"
   - Complete any CAPTCHA if prompted

3. **Verify Bot Joined**
   - Check your Discord server member list
   - Bot should appear as offline (until configured)
   - Bot should have a "BOT" tag next to its name

### Step 6: Set Up Discord Server

1. **Enable Developer Mode**
   - Go to Discord User Settings (gear icon)
   - Navigate to Advanced
   - Enable "Developer Mode"
   - This allows copying channel/role IDs

2. **Create Bot Channels**
   
   **Recommended Channel Structure:**
   ```
   📁 LUMASG CATEGORY
   ├── 📢 game-announcements
   ├── 📊 statistics  
   ├── 🔧 admin-commands
   └── 📋 server-status
   ```

3. **Configure Channel Permissions**

   **Game Announcements Channel:**
   ```
   @everyone:
   - ✅ View Channel
   - ✅ Read Message History
   - ❌ Send Messages
   
   LumaSG-Bot:
   - ✅ View Channel
   - ✅ Send Messages
   - ✅ Embed Links
   - ✅ Use External Emojis
   ```

   **Statistics Channel:**
   ```
   @everyone:
   - ✅ View Channel
   - ✅ Read Message History
   - ✅ Use Application Commands
   - ❌ Send Messages
   
   LumaSG-Bot:
   - ✅ View Channel
   - ✅ Send Messages
   - ✅ Embed Links
   - ✅ Use Application Commands
   ```

   **Admin Commands Channel:**
   ```
   @everyone:
   - ❌ View Channel
   
   Admin Role:
   - ✅ View Channel
   - ✅ Read Message History
   - ✅ Use Application Commands
   
   LumaSG-Bot:
   - ✅ View Channel
   - ✅ Send Messages
   - ✅ Embed Links
   - ✅ Use Application Commands
   ```

4. **Get Channel IDs**
   - Right-click each channel
   - Select "Copy ID"
   - Save these IDs for configuration

### Step 7: Configure Server Roles

1. **Create/Identify Admin Roles**
   ```
   Recommended Roles:
   - Owner (highest permissions)
   - Admin (full server management)
   - Moderator (limited management)
   ```

2. **Set Role Hierarchy**
   - Go to Server Settings > Roles
   - Ensure bot role is above user roles
   - Drag roles to correct order:
     ```
     1. Owner
     2. LumaSG-Bot
     3. Admin
     4. Moderator
     5. @everyone
     ```

3. **Get Role Names**
   - Note exact role names (case-sensitive)
   - These will be used in configuration

### Step 8: Configure LumaSG Plugin

1. **Edit config.yml**
   ```yaml
   discord:
     # Enable Discord integration
     enabled: true
     
     # Your bot token (keep secure!)
     bot-token: "YOUR_BOT_TOKEN_HERE"
     
     # Channel configuration (use your channel IDs)
     channels:
       game-announcements: "123456789012345678"
       statistics: "234567890123456789"
       admin: "345678901234567890"
     
     # Permission configuration (use your role names)
     permissions:
       admin-roles: ["Owner", "Admin"]
       moderator-roles: ["Moderator"]
       role-permissions:
         "Owner": "lumasg.admin"
         "Admin": "lumasg.admin"
         "Moderator": "lumasg.moderate"
   ```

2. **Save and Restart Server**
   - Save the configuration file
   - Restart your Minecraft server
   - Check console for Discord connection messages

### Step 9: Test Bot Functionality

1. **Check Bot Status**
   - Bot should appear online in Discord
   - Look for connection success message in server console:
     ```
     [INFO] [Discord] Successfully connected to Discord
     [INFO] [Discord] Successfully registered 6 Discord slash commands
     ```

2. **Test Slash Commands**
   - Type `/` in statistics channel
   - LumaSG commands should appear:
     - `/stats <player>`
     - `/leaderboard [category]`
     - `/server-status`
     - `/arena-status`
     - `/queue`

3. **Test Admin Commands** (in admin channel)
   - `/admin-stats`
   - `/force-start <arena>`
   - `/stop-game <arena>`

4. **Test Game Announcements**
   - Start a test game
   - Check if announcements appear in game-announcements channel

### Step 10: Optional Webhook Setup

Webhooks provide better performance for announcements.

1. **Create Webhooks**
   - Go to channel settings for each announcement channel
   - Navigate to Integrations > Webhooks
   - Click "Create Webhook"
   - Configure:
     ```
     Name: LumaSG Announcements
     Avatar: Server logo
     Channel: #game-announcements
     ```

2. **Get Webhook URLs**
   - Copy webhook URL
   - Example: `https://discord.com/api/webhooks/123456789/abcdef...`

3. **Configure Webhooks**
   ```yaml
   discord:
     webhooks:
       game-announcements: "https://discord.com/api/webhooks/123456789/abcdef..."
       statistics: "https://discord.com/api/webhooks/987654321/fedcba..."
       use-webhooks-first: true
   ```

## Verification Checklist

### Bot Setup Verification
- [ ] Discord application created
- [ ] Bot user created and configured
- [ ] Bot token obtained and secured
- [ ] Bot invited to server with correct permissions
- [ ] Bot appears in server member list

### Server Configuration Verification
- [ ] Developer mode enabled
- [ ] Channels created with proper permissions
- [ ] Channel IDs copied correctly
- [ ] Roles configured with proper hierarchy
- [ ] Role names noted correctly

### Plugin Configuration Verification
- [ ] config.yml updated with bot token
- [ ] Channel IDs configured correctly
- [ ] Role permissions mapped properly
- [ ] Server restarted successfully
- [ ] Bot shows as online in Discord

### Functionality Verification
- [ ] Bot connects successfully (check console)
- [ ] Slash commands appear in Discord
- [ ] Commands respond correctly
- [ ] Game announcements work
- [ ] Admin commands restricted properly
- [ ] Webhooks working (if configured)

## Common Setup Issues

### Bot Won't Connect
**Symptoms:** Bot appears offline, connection errors in console

**Solutions:**
1. Verify bot token is correct
2. Check internet connectivity
3. Ensure bot has proper permissions
4. Restart server after configuration changes

### Commands Don't Appear
**Symptoms:** Slash commands not visible when typing `/`

**Solutions:**
1. Wait up to 1 hour for global command registration
2. Verify bot has "Use Slash Commands" permission
3. Check if bot is in the server
4. Restart Discord client

### Permission Errors
**Symptoms:** "Insufficient permissions" for admin commands

**Solutions:**
1. Verify role names match exactly (case-sensitive)
2. Check user has correct Discord roles
3. Ensure bot role is above user roles in hierarchy
4. Verify role permission mappings

### No Announcements
**Symptoms:** Game events don't trigger Discord messages

**Solutions:**
1. Check channel permissions for bot
2. Verify channel IDs are correct
3. Ensure announcement features are enabled
4. Test with webhook disabled

## Security Reminders

### Token Security
- ✅ Store bot token securely
- ✅ Use environment variables in production
- ✅ Never share token publicly
- ✅ Rotate token if compromised

### Permission Security
- ✅ Use minimal required permissions
- ✅ Restrict admin commands to specific roles
- ✅ Regular permission audits
- ✅ Monitor command usage

### Channel Security
- ✅ Separate channels by function
- ✅ Restrict admin channel access
- ✅ Use webhooks for public announcements
- ✅ Monitor for unauthorized access

## Support Resources

### Documentation
- [Discord Developer Portal](https://discord.com/developers/docs)
- [LumaSG Configuration Guide](DISCORD_CONFIGURATION_GUIDE.md)
- [LumaSG Troubleshooting Guide](DISCORD_TROUBLESHOOTING_GUIDE.md)
- [LumaSG Security Guide](DISCORD_SECURITY_GUIDE.md)

### Getting Help
1. Check troubleshooting guide first
2. Enable debug logging for detailed information
3. Collect relevant logs and configuration
4. Contact support with specific error messages

---

*This setup guide provides step-by-step instructions for creating and configuring a Discord bot for LumaSG. Follow security best practices and test thoroughly before production use.*