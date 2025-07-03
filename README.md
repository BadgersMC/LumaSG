# LumaSG - Advanced Survival Games Plugin

LumaSG is a powerful and feature-rich Survival Games plugin for Paper servers, offering an immersive battle royale experience with advanced features, custom items, and extensive configuration options.

[![Codacy Badge](https://app.codacy.com/project/badge/Grade/079f7794fb864d5b879febbed82a4ebe)](https://app.codacy.com/gh/BadgersMC/LumaSG/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)

### Core Game Mechanics
- **Multiple Game Modes**: Solo, Teams, and Duos support
- **Flexible Arena System**: Support for multiple arenas with customizable spawn points
- **Dynamic Border**: Configurable world border that shrinks during deathmatch
- **Grace Period**: Initial safe period with PvP disabled
- **Spectator Mode**: Eliminated players can watch the ongoing game
- **Advanced Team System**: Team invitations, auto-balancing, and team glow effects

### Custom Items
- 🎯 **Player Tracker**: Compass that points to nearby enemies
- ⚡ **Knockback Stick**: Non-lethal crowd control weapon
- 🔥 **Fire Bomb**: Creates temporary fire zones
- ☠️ **Poison Bomb**: Releases toxic clouds
- 📦 **Airdrop Flare**: Calls in supply drops with rare loot

### Chest System
- **Tiered Loot**: Common, Uncommon, and Rare item tiers (can create more tiers)
- **Distance-based Loot**: Better items in center chests (toggleable)
- **Auto-refill**: Configurable chest refill system (toggleable)
- **Custom Items Integration**: Special items in loot tables

### Visual Features
- **Custom Scoreboard**: Real-time game information
- **Death Messages**: Customizable elimination announcements
- **Winner Celebrations**: Fireworks and pixel art displays
- **Team Glow Effects**: See teammates through walls

### Statistics & Leaderboards
- **Player Stats**: Kills, wins, damage dealt/taken
- **Historical Data**: Track player performance
- **Leaderboard System**: Global and per-game rankings

### Integrations
- **PlaceholderAPI Support**: Custom placeholders for scoreboards and chat
- **KingdomsX Integration**: Handles PvP conflicts with kingdom systems (fight teammates, no RP loss on death)
- **Nexo Integration**: Support for Nexo custom items

## 🔧 Requirements

- Paper 1.21.4 or higher (Required - will not work on Spigot/CraftBukkit)
- Java 21 or higher
- MySQL/MariaDB for statistics (sqlite for now, external db support with pooling soon)

## 📦 Installation

1. Download the latest release
2. Place the JAR file in your server's `plugins` folder
3. Restart your server
4. Configure the plugin using the generated configuration files

## ⚙️ Configuration

The plugin creates several configuration files:

### config.yml
- Game settings (players, timers, modes)
- Arena configuration
- World border settings
- Scoreboard customization
- Statistics options
- Performance tuning

### custom-items.yml
- Custom item definitions
- Behavior settings
- Loot table integration
- Visual customization

### chest.yml
- Loot table configuration
- Tier-based item distribution
- Chest refill settings
- Supports CMD, and NEXO items (with their mechanics!)

## 🎮 Commands

- `/sg` - Opens interactive GUI for viewing games, creating games, and the leaderboards
- `/sg create <name> <radius>` - Create a new arena
- `/sg join [arena]` - Join a game
- `/sg leave` - Leave current game
- `/sg list` - List available arenas
- `/sg stats [player]` - View player statistics
- `/sg setup` - Enter arena setup mode

## 🔒 Permissions

- `lumasg.admin` - Access to administrative commands
- `lumasg.create` - Create new arenas
- `lumasg.join` - Join games
- `lumasg.stats` - View statistics
- `lumasg.setup` - Setup arenas

## 🌟 Performance Features

- Uses Caffeine where applicable
- Optimized chest filling system
- Concurrent operation handling
- Memory-efficient caching
- Configurable thread pool management
- Async statistics processing

## 📚 Documentation

For detailed setup instructions and configuration guides, please visit:
- [Configuration Guide](./CODE_QUALITY_ANALYSIS.md)
- [Performance Tuning](./PERFORMANCE_IMPROVEMENTS.md)
- [Development Roadmap](./DEVELOPMENT_ROADMAP.md)
- [Security Information](./SECURITY.md)

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

## 📄 License

This project is licensed under the Lumalyte Source Available License (LSAL) - see the LICENSE file for details.

**Key License Points:**
- ✅ Source code is freely available for learning and contributions
- ✅ Personal and educational use is permitted
- ❌ Commercial distribution of compiled binaries requires permission
- ❌ Only Lumalyte.net can sell on marketplaces (BuiltByBit, Polymart, etc.)
- 🔄 Commercial users must contribute back critical bug fixes
- 💬 Different support levels based on customer/contributor status

**For Networks & Commercial Users:**
Networks and commercial users must disclose their source code and make it available either through a public repository or upon request. This includes any modifications or derivative works. Critical bug fixes must be contributed back to the main repository within 30 days of discovery. This ensures the community benefits from improvements while allowing commercial use.

## 🐛 Support

If you encounter any issues or need support:
1. Check the documentation
2. Search existing issues
3. Create a new issue with detailed information about your problem

## ⭐ Credits

Developed by LumaLyte, and BadgersMC

**Uses the following Libraries, graciously**
- [InvUI](https://github.com/NichtStudioCode/InvUI)
- [Caffeine](https://github.com/ben-manes/caffeine)
- [OkHttp](https://github.com/square/okhttp)

Made because someone said I dont understand Java, and I took that *very* personally. So heres a plugin that uses some over the top Java magic, and puts their 1.8 Survival Games to SHAME. And yes, Java *is* magic.

This plugin is very much so still a WIP, please report any issues/requests to angrybadger#1 on discord.
