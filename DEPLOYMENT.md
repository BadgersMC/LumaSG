# LumaSG Deployment Guide

This guide explains how to set up automatic deployment of your LumaSG plugin to your server.

## Setup

### 1. Create Server Credentials File

Create a file named `server-credentials.properties` in the root directory of your project with your server details:

```properties
# Server connection details
server.host=your-server-ip-or-domain
server.port=22
server.user=your-username
server.password=your-password
server.plugins.path=/path/to/your/minecraft/server/plugins
```

**Example configurations:**

For a VPS/dedicated server:
```properties
server.host=123.456.789.012
server.user=root
server.plugins.path=/home/minecraft/server/plugins
```

For shared hosting:
```properties
server.host=yourhost.com
server.user=yourusername
server.plugins.path=/home/yourusername/public_html/plugins
```

### 2. SSH Key Authentication (Recommended)

For better security, use SSH key authentication instead of passwords:

1. Generate an SSH key pair if you don't have one:
   ```bash
   ssh-keygen -t rsa -b 4096 -C "your-email@example.com"
   ```

2. Copy your public key to the server:
   ```bash
   ssh-copy-id your-username@your-server-ip
   ```

3. Update your `server-credentials.properties`:
   ```properties
   server.host=your-server-ip
   server.user=your-username
   server.keyfile=/path/to/your/private/key
   server.plugins.path=/path/to/plugins
   # Remove or comment out the password line
   ```

## Available Deployment Tasks

### `./gradlew deployToServer`
- Builds the plugin
- Removes old LumaSG JAR files from server
- Uploads the new plugin
- Simple and fast deployment

### `./gradlew deployAndRestart`
- Does everything `deployToServer` does
- Attempts to restart the server automatically
- Tries multiple methods (systemctl, screen, tmux)

### `./gradlew deployHotReload`
- Does everything `deployToServer` does
- Attempts to reload the plugin without server restart
- Requires PlugMan or similar plugin manager

## Usage Examples

```bash
# Simple deployment
./gradlew deployToServer

# Deploy and restart server
./gradlew deployAndRestart

# Deploy and try hot reload
./gradlew deployHotReload
```

## Security Notes

- The `server-credentials.properties` file is automatically ignored by Git
- Never commit your credentials to version control
- Use SSH key authentication when possible
- Keep your credentials file secure and don't share it

## Troubleshooting

### "server-credentials.properties not found"
Create the credentials file in the root directory of your project.

### "Permission denied" or "Authentication failed"
- Check your username and password/key file
- Ensure your SSH key is properly set up
- Verify the server allows SSH connections

### "No such file or directory" for plugins path
- Verify the plugins path exists on your server
- The deployment will create the directory if it doesn't exist

### Server restart fails
- The restart commands are examples and may need adjustment for your server setup
- You may need to modify the restart commands in `build.gradle` to match your server configuration 