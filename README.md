# LumaSG - Modern Survival Games Plugin

A modern Minecraft Survival Games (Hunger Games) plugin built exclusively for Paper servers, inspired by classic SG servers like MCSG. This plugin is designed to take full advantage of Paper's advanced APIs and features for optimal performance and functionality.

>**⚠ Warning**: This plugin is currently a WIP. Some features may be unfinished or completely non functional.

> **Note**: This plugin is Paper-only and does not support Spigot or CraftBukkit. We recommend using Paper as it offers significant performance improvements and modern APIs that allow us to provide a better experience.

## Features

- Create and manage multiple arenas
- Customizable game settings (grace period, countdown, etc.)
- Tiered chest loot system with extensive item customization
- Special fishing loot system with unique ocean-themed rewards
- Crafting materials and resources for strategic gameplay
- Support for Nexo custom items
- Spectator mode for eliminated players
- Winner celebration with fireworks and announcements
- Configurable rewards for winners
- Admin commands for arena setup and game management
- Modern Paper APIs for optimal performance

## Third-Party Plugin Integration

### AuraSkills

**Important:** This plugin does not include AuraSkills integration. Instead, disable AuraSkills functionality in your SG arena worlds through AuraSkills configuration.

Add your SG arena world names to the `disabled-worlds` list in your AuraSkills config:

```yaml
disabled-worlds:
  - "sg_breeze_island"
  - "sg_desert_temple"
  - "your_sg_world_name"
```

This prevents skill gains, stat modifications, and abilities from working in SG games.

### PlaceholderAPI

The plugin includes PlaceholderAPI integration for displaying game statistics and information.

### Nexo

Custom items from Nexo can be configured in the chest loot tables.

## Requirements

- Paper 1.21.4 or higher (Required - will not work on Spigot/CraftBukkit)
- Java 21 or higher

## Installation

1. Download the latest release from the releases page
2. Place the JAR file in your server's `plugins` folder
3. Restart your server
4. Configure the plugin as needed

## Configuration

The plugin creates the following configuration files:

- `config.yml` - Main plugin configuration
- `chest.yml` - Chest loot and fishing loot configuration
- `arenas/` - Directory containing arena configuration files

### Main Configuration (config.yml)

```yaml
# Game Settings
game:
  min-players: 2
  max-players: 24
  countdown-seconds: 30
  grace-period-seconds: 30
  game-time-minutes: 20
  deathmatch-time-minutes: 5
  
# Chest Settings
chests:
  refill-time-minutes: 5
  enable-tier-system: true
  
# Spectator Settings
spectator:
  enabled: true
  teleport-to-lobby-after-game: true
  
# Reward Settings
rewards:
  enabled: false
  win-command: "give %player% diamond 1"
  kill-command: ""
```

### Chest Configuration (chest.yml)

The chest configuration file allows you to customize the items that can be found in chests and fishing. Items are organized into tiers (common, uncommon, rare) and each item can have various properties:

- Material
- Min/max amount
- Chance of appearing
- Custom name and lore with MiniMessage format
- Enchantments
- Item flags
- Attribute modifiers
- Custom model data
- Persistent data
- Potion effects (for potions)
- Unbreakable status

#### Example Item Configuration

```yaml
diamond_sword:
  material: DIAMOND_SWORD
  min-amount: 1
  max-amount: 1
  chance: 5.0
  name: "<!italic><aqua>Diamond Sword</aqua>"
  lore:
    - "<!italic><gray>A legendary sword</gray>"
    - "<!italic><gray>that cuts through armor</gray>"
  unbreakable: true
  enchantments:
    sharpness: 2
    fire_aspect: 1
  item-flags:
    - HIDE_ATTRIBUTES
    - HIDE_UNBREAKABLE
  attributes:
    GENERIC_ATTACK_DAMAGE: 8.0
  custom-model-data: 1001
  persistent-data:
    item-type: "legendary_weapon"
    tier: "rare"
```

### Fishing Loot System

LumaSG includes a special fishing loot system that allows players to catch unique items while fishing during games. This adds a strategic element to gameplay, as fishing carries risk but offers exclusive rewards.

```yaml
fishing_loot:
  special_catch_chance: 25
  
  items:
    trident:
      material: TRIDENT
      chance: 5.0
      name: "<!italic><dark_aqua>Ocean's Fury</dark_aqua>"
      lore:
        - "<!italic><gray>A powerful trident imbued</gray>"
        - "<!italic><gray>with the ocean's might</gray>"
      unbreakable: true
      enchantments:
        loyalty: 2
        channeling: 1
```

### Nexo Integration

LumaSG supports integration with the Nexo plugin for custom items. To use Nexo items in your chest configuration, use the following format:

```yaml
nexo_item:
  nexo-item: "item_id"
  min-amount: 1
  max-amount: 1
  chance: 5.0
```

Replace `item_id` with the ID of the Nexo item you want to use.

## Commands

### Player Commands

- `/sg join <arena>` - Join a game
- `/sg leave` - Leave the current game
- `/sg spectate <arena>` - Spectate a game

### Admin Commands

- `/sg create <n>` - Create a new arena
- `/sg delete <n>` - Delete an arena
- `/sg setspawn <arena>` - Add a spawn point to an arena
- `/sg setchest <arena>` - Add a chest location to an arena
- `/sg setlobby <arena>` - Set the lobby spawn for an arena
- `/sg setspectator <arena>` - Set the spectator spawn for an arena
- `/sg list` - List all arenas
- `/sg start <arena>` - Force start a game
- `/sg stop <arena>` - Force stop a game
- `/sg reload` - Reload the configuration
- `/sg info <arena>` - Show information about an arena

## Permissions

- `lumasg.play` - Allows players to join and leave games
- `lumasg.admin` - Allows access to all admin commands
- `lumasg.*` - Grants access to all commands

## Arena Setup Guide

1. Create a new arena: `/sg create <n>`
2. Set the lobby spawn: `/sg setlobby <n>`
3. Set the spectator spawn: `/sg setspectator <n>`
4. Add spawn points (one for each player): `/sg setspawn <n>`
5. Add chest locations: `/sg setchest <n>`

## Test Maps

To test the plugin, you'll need Survival Games maps. We recommend checking out the [MCSG Archive](https://mcsgarchive.com/) which has a collection of classic Survival Games maps.

**Important**: Always ensure you have permission from the original map creators before using their maps. Respect the work of map builders and follow any licensing or attribution requirements they may have.

The plugin does not include any maps by default to respect intellectual property rights.

## Building from Source

1. Clone the repository
   ```bash
   git clone https://github.com/yourusername/LumaSG.git
   cd LumaSG
   ```

2. Build with Gradle:
   ```bash
   ./gradlew build
   ```

3. The compiled JAR will be in `build/libs/LumaSG-1.0.0.jar`

## Development Setup

1. Make sure you have JDK 21 or higher installed
2. Import the project into your favorite IDE as a Gradle project
3. Run `./gradlew build` to download dependencies and build the project

## License

This project is licensed under the MIT License - see the LICENSE file for details. 
