# Technology Stack

## Build System
- **Gradle 8.x** with Kotlin DSL
- **Shadow plugin** for minimal JAR creation (dependencies loaded at runtime)
- **Paper Library Loader** for runtime dependency management
- **Java 21** target with toolchain support

## Core Dependencies
- **Paper API 1.21.4** - Modern Minecraft server platform (required, not Spigot/CraftBukkit)
- **Adventure API** - Modern text/component system with MiniMessage support
- **Brigadier** - Command framework integration
- **InvUI** - Advanced inventory GUI framework

## Performance Libraries
- **Caffeine Cache 3.2.1** - High-performance caching (shadowed)
- **OkHttp 4.12.0** - HTTP client for external API calls
- **Concurrent collections** - Thread-safe data structures

## Plugin Integrations
- **PlaceholderAPI** - Custom placeholder support
- **KingdomsX** - PvP conflict resolution
- **Nexo** - Custom items integration

## Testing Framework
- **JUnit 5** - Primary testing framework
- **Mockito** - Mocking framework with inline support
- **Awaitility** - Async testing utilities

## Runtime Dependencies (Loaded by LumaSGLibraryLoader)
- **Caffeine 3.2.1** - High-performance caching
- **HikariCP 5.0.1** - Database connection pooling
- **PostgreSQL 42.6.0** - PostgreSQL database driver
- **MySQL 8.0.33** - MySQL database driver
- **Kryo 5.6.2** - High-performance serialization
- **Hibernate Validator 9.0.1** - Input validation framework
- **Apache Commons Text 1.10.0** - Text processing and sanitization

## JAR Size Optimization
- **Production JAR**: ~0.55 MB (minimal size)
- **Dependencies**: Loaded at runtime by Paper's library system
- **Benefits**: Faster downloads, reduced memory footprint, shared libraries across plugins

## Common Commands

### Build & Development
```bash
# Build the plugin
./gradlew build

# Build with tests
./gradlew test build

# Create shadow JAR only
./gradlew shadowJar

# Clean build
./gradlew clean build

# Run tests with coverage
./gradlew test

# Skip flaky tests in CI
./gradlew test -Dci=true
```

### Development Workflow
```bash
# Quick development build (skips tests)
./gradlew shadowJar

# Full validation build
./gradlew clean test build

# Continuous build during development
./gradlew build --continuous
```

## Architecture Patterns
- **Manager pattern** - Core systems organized as managers (ArenaManager, GameManager, etc.)
- **Hook system** - Plugin integration abstraction
- **Multi-tier caching** - Hot/Warm/Cold/Persistent cache tiers
- **Thread-safe operations** - Extensive use of concurrent collections and atomic operations
- **Resource pooling** - Game instance pooling for performance
- **Runtime library loading** - Dependencies loaded via Paper's library system for minimal JAR size