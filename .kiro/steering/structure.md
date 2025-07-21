# Project Structure

## Root Directory
```
├── src/main/java/net/lumalyte/     # Main source code
├── src/test/java/net/lumalyte/     # Test code
├── src/main/resources/             # Plugin resources and configs
├── build.gradle                    # Build configuration
├── settings.gradle                 # Project settings
└── gradle.properties               # Gradle configuration
```

## Main Package Structure (`net.lumalyte.lumasg`)

### Core Plugin Files
- `LumaSG.java` - Main plugin class and entry point
- `LumaSGBootstrap.java` - Plugin bootstrap and initialization
- `LumaSGLibraryLoader.java` - Dynamic library loading

### Manager Components
- `arena/` - Arena management and configuration
- `game/` - Core game logic and state management
- `chest/` - Loot system and chest management
- `customitems/` - Custom items system with behaviors
- `statistics/` - Player stats and database operations
- `gui/` - Inventory-based user interfaces

### Supporting Systems
- `commands/` - Command handling (single SGCommand class)
- `listeners/` - Event listeners for game mechanics
- `hooks/` - External plugin integrations
- `permissions/` - Permission management
- `util/` - Utility classes and helpers
- `exception/` - Custom exception classes

## Key Architectural Patterns

### Manager Pattern
Each major system has a dedicated manager class:
- `ArenaManager` - Arena lifecycle and configuration
- `GameManager` - Game instances and state
- `ChestManager` - Loot distribution and chest filling
- `CustomItemsManager` - Custom item behaviors and tracking
- `StatisticsManager` - Player data and leaderboards

### Utility Organization
- `util/cache/` - Caching implementations
- `util/performance/` - Performance monitoring and profiling
- Core utilities for common operations (ItemUtils, MessageUtils, etc.)

### Resource Configuration
- `config.yml` - Main plugin configuration
- `custom-items.yml` - Custom item definitions
- `chest.yml` - Loot table configuration
- `fishing.yml` - Fishing mechanics
- `placeholders.yml` - PlaceholderAPI integration
- `paper-plugin.yml` - Plugin metadata

## Naming Conventions
- **Classes**: PascalCase (e.g., `GameManager`, `CustomItemBehavior`)
- **Methods**: camelCase (e.g., `startGame()`, `handlePlayerJoin()`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_PLAYERS`, `DEFAULT_RADIUS`)
- **Packages**: lowercase (e.g., `customitems`, `statistics`)

## Code Organization Principles
- **Single Responsibility** - Each manager handles one domain
- **Dependency Injection** - Managers receive dependencies via constructor
- **Thread Safety** - Concurrent collections and atomic operations
- **Resource Management** - Proper cleanup in disable methods
- **Error Handling** - Comprehensive exception handling with retry mechanisms