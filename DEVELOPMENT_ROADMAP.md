# LumaSG Development Roadmap

## Current Status (6/27/2025)

✅ **Completed Major Cleanup**
- Enhanced error handling with retry mechanisms and circuit breaker patterns
- Improved thread safety with concurrent collections and atomic operations
- Added comprehensive validation utilities and debug logging
- Implemented KingdomsX hook for PvP conflict resolution
- Refactored game managers for better separation of concerns
- Enhanced arena management with better configuration handling
- Proper resource cleanup and memory management
- Updated documentation and README

✅ **Code Quality & Security**
- No security vulnerabilities detected (Trivy scan clean)
- Proper `.gitignore` configuration for development files
- Codacy CLI workflow established
- PMD analysis integration working

## High Priority Features (Next 2 weeks)

### 1. **Critical Bug Fixes & Core Issues**
- [ ] **World Border Deathmatch Fix** - Fix world border not shrinking during deathmatch (prevents players from running away)
- [ ] **Nameplate Hiding System** - Implement packet manipulation or custom tab hook to hide player nameplates through walls
- [ ] **Console Game Management** - Enable console to start games and add players for automated/external control
- [ ] **Adventure Mode Replacement** - Replace adventure mode with custom block placement tracking system

### 2. **Custom Items System Foundation**
- [ ] **Custom Items Configuration** - Create `custom-items.yml` config system similar to `loot.yml` and `fishing.yml`
- [ ] **Custom Items Manager** - Core manager class for handling custom item creation, tracking, and cleanup
- [ ] **Loot Integration** - Integrate custom items into chest loot system with min/max amounts and rarity

### 3. **Essential Custom Items (Phase 1)**
- [ ] **Player Tracker** - Compass-based player tracking system using hotbar
  - Red markers for all players
  - Black marker for current top killer
  - Special emoji markers for airdrops
- [ ] **Knockback Stick** - Classic stick with Knockback 5 enchantment

## Medium Priority Features (1 months)

### 4. **Advanced Custom Items (Phase 2)**
- [ ] **Fire Bomb** - Custom TNT block with realistic fire explosion mechanics
  - 2.5 second fuse time with primed TNT entity
  - Random imperfect fire shape (configurable min/max radius)
  - Fire ticks for 3 seconds then disappears
  - No block breaking damage
- [ ] **Poison Bomb** - Lingering poison effect bomb
  - Similar to fire bomb but creates poison cloud
  - More circular/rounded cloud shape for realism
  - Configurable poison duration and potency

### 5. **Airdrop System** - *Complex Feature*
- [ ] **Airdrop Flares** - Custom redstone torch item to call airdrops
- [ ] **Meteor Physics** - Vector-based falling meteor with particle trail
- [ ] **Impact Effects** - Fake explosion that "throws" blocks as falling entities
- [ ] **Chest Placement** - Place chest or Nexo furniture chest with hologram
- [ ] **Opening Mechanics** - 3-second look requirement with blindness effect
- [ ] **Item Distribution** - Fountain-style item ejection and cleanup
- [ ] **Game Integration** - Match announcements and tracking system
- [ ] **Leaderboard Tracking** - Track amount of opened air drops versus drops called in

### 6. **Block Placement System** - *Replaces Adventure Mode*
- [ ] **Block Tracking Manager** - Map and track all player-placed blocks during games
- [ ] **Placement Validation** - Allow only specific blocks (TNT items, cobwebs, anvils, crafting tables) Make configurable whitelist.
- [ ] **Game Cleanup** - Remove all tracked blocks after game ends
- [ ] **Performance Optimization** - Efficient storage and cleanup of block data

### 7. **Game Testing & Bug Fixes**
- [ ] Comprehensive multiplayer testing
- [ ] Grace period timing validation
- [ ] Spawn point barrier system testing
- [ ] Chest loot distribution testing
- [ ] Winner celebration system validation

### 8. **Arena Management Improvements**
- [ ] Arena template system completion
- [ ] Bulk arena operations (import/export)
- [ ] Arena validation and health checks
- [ ] Auto-chest scanning improvements

### 9. **Statistics & Leaderboards**
- [ ] Complete statistics database implementation
- [ ] Player ranking system
- [ ] Historical game data tracking
- [ ] Web-based statistics dashboard (optional)

## Low Priority Features (2+ months)

### 10. **Advanced Game Features**
- [ ] Multiple game modes (Teams, Solo, Duos)
- [ ] Custom kit system
- [ ] Spectator improvements (follow player, teleport)
- [ ] Game replay system (optional)

### 11. **Performance & Scalability**
- [ ] Multi-world support optimization
- [ ] Database connection pooling
- [ ] Async operations audit
- [ ] Memory usage optimization

### 12. **Admin Tools & Management**
- [ ] Advanced debugging tools
- [ ] Performance monitoring
- [ ] Automated backups

## Future Enhancements

### 13. **Integration Expansions**
- [ ] Discord bot integration
- [ ] More claim plugin hooks (GriefPrevention, etc.)
- [ ] Economy plugin integration
- [ ] Advanced custom items/weapons system

### 14. **Advanced Features**
- [ ] Tournament system
- [ ] Seasonal events
- [ ] Achievement system
- [ ] Player cosmetics

## Technical Implementation Details

### Custom Items System Architecture
```yaml
# custom-items.yml structure
custom-items:
  player-tracker:
    material: COMPASS
    name: "Player Tracker"
    lore: ["Track other players", "Red: Players, Black: Top Killer, Gold: Air Drop"]
    update-interval: 20 # ticks
    track-airdrops: true
    
  fire-bomb:
    material: TNT
    name: "Fire Bomb"
    fuse-time: 50 # ticks (2.5 seconds)
    fire-radius:
      min: 3
      max: 7
    fire-duration: 60 # ticks (3 seconds)
    
  poison-bomb:
    material: TNT
    name: "Poison Bomb"
    effect-radius: 5
    poison-duration: 100 # ticks
    poison-amplifier: 1
    
  knockback-stick:
    material: STICK
    name: "Knockback Stick"
    enchantments:
      knockback: 5
      
  airdrop-flare:
    material: REDSTONE_TORCH
    name: "Airdrop Flare"
    cooldown: 300 # seconds (5 minutes)
```

### Airdrop System Components
1. **Meteor Trajectory Calculator** - Physics-based falling object
2. **Particle Trail Manager** - Visual effects during fall
3. **Impact Effect Handler** - Block throwing simulation
4. **Chest Security System** - Look-to-open mechanics
5. **Item Distribution Engine** - Fountain-style item ejection

### Block Placement Tracking
- **ConcurrentHashMap<Location, Material>** for placed blocks
- **Game cleanup integration** for automatic removal
- **Permission-based placement** for allowed block types
- **Performance monitoring** for large-scale block tracking

## Technical Debt & Maintenance

### Ongoing Tasks
- [ ] Regular dependency updates
- [ ] Performance monitoring
- [ ] Code review and refactoring
- [ ] Documentation updates
- [ ] Test coverage improvements

### Code Quality Targets
- [ ] Maintain zero security vulnerabilities
- [ ] Keep complexity metrics within acceptable ranges
- [ ] Ensure proper error handling coverage
- [ ] Maintain comprehensive logging

## Development Workflow

### Before Each Feature
1. Run Codacy CLI analysis: `mcp_codacy_codacy_cli_analyze`
2. Check for security vulnerabilities with Trivy
3. Review code complexity with Lizard (if needed)
4. Update tests and documentation

### After Each Feature
1. Run full Codacy analysis
2. Update CHANGELOG.md
3. Test on development server
4. Update documentation if needed

## Testing Strategy

### Unit Testing
- [ ] Core game logic tests
- [ ] Arena management tests
- [ ] Player manager tests
- [ ] Statistics calculation tests
- [ ] Custom items functionality tests

### Integration Testing
- [ ] Multi-player game scenarios
- [ ] Plugin hook interactions
- [ ] Database operations
- [ ] Configuration loading
- [ ] Custom items in live games

### Performance Testing
- [ ] Large player count scenarios
- [ ] Memory leak detection
- [ ] Concurrent game handling
- [ ] Database query optimization
- [ ] Block placement tracking performance

## Release Planning

### Version 1.1.0 (Target: February 2025)
- Critical bug fixes (world border, nameplates, console management)
- Adventure mode replacement with block tracking
- Basic custom items (tracker, knockback stick)
- Complete statistics system

### Version 1.2.0 (Target: April 2025)
- Advanced custom items (fire bomb, poison bomb)
- Enhanced arena management
- Performance improvements
- Comprehensive testing results

### Version 1.3.0 (Target: June 2025)
- **Major Feature**: Complete Airdrop System
- Advanced spectator features
- Web admin panel
- Tournament system foundation

### Version 2.0.0 (Target: Late 2025)
- Major architecture improvements
- Multi-server support
- Advanced integration features
- Complete rewrite of legacy components

## Notes for Future Development

### Key Architectural Decisions
- Stick with Paper-only approach for optimal performance
- Maintain modular component design
- Prioritize thread safety and async operations
- Keep comprehensive error handling and logging
- **Custom items as separate, configurable modules**

### Performance Considerations
- Monitor memory usage during large games
- Optimize database queries for statistics
- Consider caching for frequently accessed data
- Implement proper resource cleanup
- **Efficient block tracking for placement system**
- **Optimized particle effects for airdrop system**

### Security Best Practices
- Regular Trivy scans for vulnerabilities
- Input validation for all user inputs
- Proper permission checks
- Secure configuration handling
- **Validate custom item configurations**

### Custom Items Development Notes
- Use **ItemMeta** and **PersistentDataContainer** for item identification
- Implement **event-driven architecture** for item interactions
- Create **modular item handlers** for easy expansion
- Consider **ExecutableItems plugin integration** for advanced features ([ExecutableItems on Modrinth](https://modrinth.com/plugin/executableitems))

---

*Last Updated: June 27 2025*
*Next Review: July 4 2025* 
