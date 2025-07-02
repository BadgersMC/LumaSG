package net.lumalyte.customitems.behaviors;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.lumalyte.LumaSG;
import net.lumalyte.customitems.CustomItem;
import net.lumalyte.customitems.CustomItemBehavior;
import net.lumalyte.game.Game;
import net.lumalyte.util.DebugLogger;
import net.lumalyte.util.MiniMessageUtils;

/**
 * Handles explosive custom item behaviors including fire bombs and poison bombs.
 * 
 * <p>This behavior system provides realistic throwing physics, timed explosions,
 * and area effects for explosive custom items. It supports both fire-based
 * and poison-based explosives with configurable parameters.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class ExplosiveBehavior implements Listener {
    
    private final LumaSG plugin;
    private final DebugLogger.ContextualLogger logger;
    private final Map<UUID, ExplosiveData> activeExplosives;
    private final Map<UUID, Long> playerCooldowns;
    
    // Constants for explosion effects
    private static final double DEFAULT_THROW_VELOCITY = 1.0;
    private static final int DEFAULT_FUSE_TIME = 60; // 3 seconds
    private static final int DEFAULT_FIRE_DURATION = 60; // 3 seconds
    private static final int DEFAULT_POISON_DURATION = 100; // 5 seconds
    private static final int CLEANUP_INTERVAL = 100; // 5 seconds
    
    /**
     * Creates a new explosive behavior handler.
     * 
     * @param plugin The plugin instance
     */
    public ExplosiveBehavior(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("ExplosiveBehavior");
        this.activeExplosives = new ConcurrentHashMap<>();
        this.playerCooldowns = new ConcurrentHashMap<>();
        
        // Register as listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        // Start cleanup task
        startCleanupTask();
    }
    
    /**
     * Handles throwing a fire bomb.
     */
    public void throwFireBomb(@NotNull Player player, @NotNull CustomItem customItem, @NotNull PlayerInteractEvent event) {
        if (!canUseExplosive(player, customItem)) {
            return;
        }
        
        // Get configuration
        int fuseTime = customItem.getBehaviorInt("fuse-time", DEFAULT_FUSE_TIME);
        double throwVelocity = customItem.getBehaviorDouble("throw-velocity", DEFAULT_THROW_VELOCITY);
        int fireRadiusMin = customItem.getBehaviorInt("fire-radius-min", 3);
        int fireRadiusMax = customItem.getBehaviorInt("fire-radius-max", 7);
        int fireDuration = customItem.getBehaviorInt("fire-duration", DEFAULT_FIRE_DURATION);
        boolean damagesThrower = customItem.getBehaviorBoolean("damage-thrower", false);
        
        // Create and throw TNT
        TNTPrimed tnt = throwExplosive(player, throwVelocity, fuseTime);
        if (tnt == null) {
            return;
        }
        
        // Store explosive data
        ExplosiveData data = new ExplosiveData(
            tnt.getUniqueId(),
            player.getUniqueId(),
            CustomItemBehavior.FIRE_BOMB,
            fireRadiusMin,
            fireRadiusMax,
            fireDuration,
            0, // No poison for fire bomb
            0,
            damagesThrower
        );
        activeExplosives.put(tnt.getUniqueId(), data);
        
        // Consume item
        consumeExplosiveItem(player, event);
        
        // Set cooldown
        setCooldown(player, customItem);
        
        // Play effects
        playThrowEffects(player, CustomItemBehavior.FIRE_BOMB);
        
        logger.debug("Player " + player.getName() + " threw a fire bomb");
    }
    
    /**
     * Handles throwing a poison bomb.
     */
    public void throwPoisonBomb(@NotNull Player player, @NotNull CustomItem customItem, @NotNull PlayerInteractEvent event) {
        if (!canUseExplosive(player, customItem)) {
            return;
        }
        
        // Get configuration
        int fuseTime = customItem.getBehaviorInt("fuse-time", DEFAULT_FUSE_TIME);
        double throwVelocity = customItem.getBehaviorDouble("throw-velocity", DEFAULT_THROW_VELOCITY);
        int effectRadius = customItem.getBehaviorInt("effect-radius", 5);
        int poisonDuration = customItem.getBehaviorInt("poison-duration", DEFAULT_POISON_DURATION);
        int poisonAmplifier = customItem.getBehaviorInt("poison-amplifier", 1);
        boolean damagesThrower = customItem.getBehaviorBoolean("damage-thrower", false);
        
        // Create and throw TNT
        TNTPrimed tnt = throwExplosive(player, throwVelocity, fuseTime);

		// Store explosive data
        ExplosiveData data = new ExplosiveData(
            tnt.getUniqueId(),
            player.getUniqueId(),
            CustomItemBehavior.POISON_BOMB,
            effectRadius,
            effectRadius,
            0, // No fire for poison bomb
            poisonDuration,
            poisonAmplifier,
            damagesThrower
        );
        activeExplosives.put(tnt.getUniqueId(), data);
        
        // Consume item
        consumeExplosiveItem(player, event);
        
        // Set cooldown
        setCooldown(player, customItem);
        
        // Play effects
        playThrowEffects(player, CustomItemBehavior.POISON_BOMB);
        
        logger.debug("Player " + player.getName() + " threw a poison bomb");
    }
    
    /**
     * Creates and throws a TNT entity with realistic physics.
     */
    private @NotNull TNTPrimed throwExplosive(@NotNull Player player, double velocity, int fuseTime) {
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection().normalize().multiply(velocity);
        
        // Spawn TNT at eye level
        TNTPrimed tnt = player.getWorld().spawn(eyeLocation, TNTPrimed.class);
        tnt.setFuseTicks(fuseTime);
        tnt.setVelocity(direction);
        tnt.setSource(player);
        
        return tnt;
    }
    
    /**
     * Handles TNT explosion events for custom explosives.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(@NotNull EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) {
            return;
        }
        
        ExplosiveData data = activeExplosives.remove(tnt.getUniqueId());
        if (data == null) {
            return;
        }
        
        // Cancel block damage for custom explosives
        event.blockList().clear();
        event.setCancelled(false); // Allow the explosion for effects, but no block damage
        
        Location explosionLocation = tnt.getLocation();
        Player thrower = plugin.getServer().getPlayer(data.throwerId);
        
        // Handle different explosive types
        switch (data.explosiveType) {
            case FIRE_BOMB -> handleFireExplosion(explosionLocation, data, thrower);
            case POISON_BOMB -> handlePoisonExplosion(explosionLocation, data, thrower);
            default -> {
                logger.warn("Unknown explosive type: " + data.explosiveType);
                // Handle unknown explosive type gracefully
            }
        }
        
        logger.debug("Custom explosive detonated at " + explosionLocation);
    }
    
    /**
     * Handles fire bomb explosion effects.
     */
    private void handleFireExplosion(@NotNull Location location, @NotNull ExplosiveData data, @Nullable Player thrower) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        
        // Calculate random fire radius
        Random random = new Random();
        int radius = random.nextInt(data.maxRadius - data.minRadius + 1) + data.minRadius;
        
        // Create fire blocks in an imperfect circle
        Set<Location> fireLocations = generateImperfectCircle(location, radius);
        
        for (Location fireLocation : fireLocations) {
            Block block = world.getBlockAt(fireLocation);
            
            // Only place fire on air blocks or replaceable blocks
            if (block.getType() == Material.AIR || block.getType().isAir()) {
                block.setType(Material.FIRE);
                
                // Schedule fire removal
                scheduleFireRemoval(fireLocation, data.fireDuration);
            }
        }
        
        // Play fire explosion effects
        playFireExplosionEffects(location, radius);
        
        // Damage nearby players (except thrower if configured)
        damageNearbyPlayers(location, radius, thrower, data.damagesThrower, false);
        
        logger.debug("Fire explosion created with radius " + radius + " at " + location);
    }
    
    /**
     * Handles poison bomb explosion effects.
     */
    private void handlePoisonExplosion(@NotNull Location location, @NotNull ExplosiveData data, @Nullable Player thrower) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        
        // Create poison cloud effect
        createPoisonCloud(location, data.maxRadius);
        
        // Apply poison to nearby players
        applyPoisonToNearbyPlayers(location, data.maxRadius, data.poisonDuration, 
                                 data.poisonAmplifier, thrower, data.damagesThrower);
        
        // Play poison explosion effects
        playPoisonExplosionEffects(location, data.maxRadius);
        
        logger.debug("Poison explosion created with radius " + data.maxRadius + " at " + location);
    }
    
    /**
     * Generates an imperfect circle of locations for fire placement.
     */
    private @NotNull Set<Location> generateImperfectCircle(@NotNull Location center, int radius) {
        Set<Location> locations = new HashSet<>();
        Random random = new Random();
        
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distance = Math.sqrt(x * x + z * z);
                
                // Create imperfect circle with some randomness
                double threshold = radius + (random.nextGaussian() * 0.5);
                
                if (distance <= threshold) {
                    Location fireLocation = center.clone().add(x, 0, z);
                    
                    // Add some vertical variation
                    if (random.nextDouble() < 0.3) {
                        fireLocation.add(0, random.nextInt(3) - 1, 0);
                    }
                    
                    locations.add(fireLocation);
                }
            }
        }
        
        return locations;
    }
    
    /**
     * Schedules fire block removal after duration.
     */
    private void scheduleFireRemoval(@NotNull Location location, int duration) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Block block = location.getBlock();
                if (block.getType() == Material.FIRE) {
                    block.setType(Material.AIR);
                }
            }
        }.runTaskLater(plugin, duration);
    }
    
    /**
     * Creates a poison cloud effect at the specified location.
     */
    private void createPoisonCloud(@NotNull Location center, int radius) {
        World world = center.getWorld();
        if (world == null) return;
        
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 100; // 5 seconds
            
            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    cancel();
                    return;
                }
                
                // Spawn poison particles in a sphere
                for (int i = 0; i < 20; i++) {
                    double angle1 = Math.random() * Math.PI * 2;
                    double angle2 = Math.random() * Math.PI;
                    double r = Math.random() * radius;
                    
                    double x = r * Math.sin(angle2) * Math.cos(angle1);
                    double y = r * Math.cos(angle2);
                    double z = r * Math.sin(angle2) * Math.sin(angle1);
                    
                    Location particleLocation = center.clone().add(x, y, z);
                    
                    // Use DUST particles with green color for poison effect
                    Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(0, 150, 0), 1.0f);
                    world.spawnParticle(Particle.DUST, particleLocation, 1, dustOptions);
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 2);
    }
    
    /**
     * Applies poison effects to nearby players.
     */
    private void applyPoisonToNearbyPlayers(@NotNull Location center, int radius, int duration, 
                                          int amplifier, @Nullable Player thrower, boolean damagesThrower) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        
        for (Player player : world.getPlayers()) {
            if (shouldSkipPlayerForThrowerDamage(player, thrower, damagesThrower)) {
                continue;
            }
            
            double distance = player.getLocation().distance(center);
            if (distance <= radius) {
                // Apply poison effect
                PotionEffect poisonEffect = new PotionEffect(
                    PotionEffectType.POISON, 
                    duration, 
                    amplifier, 
                    false, 
                    true, 
                    true
                );
                player.addPotionEffect(poisonEffect);
                
                // Send message to player
                MiniMessageUtils.sendMessage(player, 
                    "<dark_purple>You've been poisoned by a toxic cloud!</dark_purple>");
            }
        }
    }
    
    /**
     * Damages nearby players from explosion.
     */
    private void damageNearbyPlayers(@NotNull Location center, int radius, @Nullable Player thrower, 
                                   boolean damagesThrower, boolean isPoisonBomb) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        
        for (Player player : world.getPlayers()) {
            if (shouldSkipPlayerForThrowerDamage(player, thrower, damagesThrower)) {
                continue;
            }
            
            double distance = player.getLocation().distance(center);
            if (distance <= radius) {
                // Calculate damage based on distance
                double damageMultiplier = 1.0 - (distance / radius);
                double damage = isPoisonBomb ? 2.0 : 4.0; // Poison bombs do less direct damage
                damage *= damageMultiplier;
                
                if (damage > 0) {
                    player.damage(damage);
                }
            }
        }
    }
    
    /**
     * Determines if a player should be skipped for thrower damage protection.
     * 
     * @param player The player to check
     * @param thrower The player who threw the explosive (may be null)
     * @param damagesThrower Whether the explosive should damage its thrower
     * @return true if the player should be skipped, false otherwise
     */
    private boolean shouldSkipPlayerForThrowerDamage(@NotNull Player player, @Nullable Player thrower, boolean damagesThrower) {
        return !damagesThrower && thrower != null && player.equals(thrower);
    }
    
    /**
     * Plays fire explosion effects.
     */
    private void playFireExplosionEffects(@NotNull Location location, int radius) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        
        // Play explosion sound
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        world.playSound(location, Sound.BLOCK_FIRE_AMBIENT, 2.0f, 1.0f);
        
        // Spawn fire particles
        world.spawnParticle(Particle.EXPLOSION, location, 1);
        world.spawnParticle(Particle.FLAME, location, radius * 10, radius, 1, radius, 0.1);
        world.spawnParticle(Particle.LARGE_SMOKE, location, radius * 5, radius, 2, radius, 0.1);
    }
    
    /**
     * Plays poison explosion effects.
     */
    private void playPoisonExplosionEffects(@NotNull Location location, int radius) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        
        // Play explosion sound
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
        world.playSound(location, Sound.ENTITY_WITCH_THROW, 2.0f, 0.8f);
        
        // Spawn poison particles
        world.spawnParticle(Particle.EXPLOSION, location, 1);
        
        // Use DUST particles with green color for poison effect
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(0, 150, 0), 1.5f);
        world.spawnParticle(Particle.DUST, location, radius * 15, radius, 1, radius, 0.1);
        
        world.spawnParticle(Particle.SMOKE, location, radius * 8, radius, 2, radius, 0.1);
    }
    
    /**
     * Plays throwing effects when explosive is thrown.
     */
    private void playThrowEffects(@NotNull Player player, @NotNull CustomItemBehavior explosiveType) {
        Location location = player.getLocation();
        
        switch (explosiveType) {
            case FIRE_BOMB -> {
                player.playSound(location, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.2f);
                MiniMessageUtils.sendMessage(player, "<red>Fire bomb thrown!</red>");
            }
            case POISON_BOMB -> {
                player.playSound(location, Sound.ENTITY_TNT_PRIMED, 1.0f, 0.8f);
                MiniMessageUtils.sendMessage(player, "<dark_purple>Poison bomb thrown!</dark_purple>");
            }
            default -> {
                // Handle unknown explosive type
                player.playSound(location, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
                MiniMessageUtils.sendMessage(player, "<yellow>Explosive thrown!</yellow>");
                logger.warn("Unknown explosive type in playThrowEffects: " + explosiveType);
            }
        }
    }
    
    /**
     * Consumes the explosive item from player's inventory.
     */
    private void consumeExplosiveItem(@NotNull Player player, @NotNull PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
    
    /**
     * Checks if player can use explosive (cooldown, game state, etc.).
     */
    private boolean canUseExplosive(@NotNull Player player, @NotNull CustomItem customItem) {
        // Check cooldown
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastUse = playerCooldowns.get(playerId);
        
        if (lastUse != null) {
            int cooldown = customItem.getBehaviorInt("cooldown", 40) * 50; // Convert ticks to ms
            if (currentTime - lastUse < cooldown) {
                long remaining = (cooldown - (currentTime - lastUse)) / 1000;
                MiniMessageUtils.sendMessage(player, 
                    "<red>Explosive on cooldown for " + remaining + " more seconds!</red>");
                return false;
            }
        }
        
        // Check if player is in a game
        Game game = plugin.getGameManager().getGameByPlayer(player);
        if (game == null) {
            MiniMessageUtils.sendMessage(player, "<red>You can only use explosives during a game!</red>");
            return false;
        }
        
        // Check if PvP is enabled (no explosives during grace period)
        if (!game.isPvpEnabled()) {
            MiniMessageUtils.sendMessage(player, "<red>You cannot use explosives during the grace period!</red>");
            return false;
        }
        
        return true;
    }
    
    /**
     * Sets cooldown for player.
     */
    private void setCooldown(@NotNull Player player, @NotNull CustomItem customItem) {
        int cooldown = customItem.getBehaviorInt("cooldown", 40); // Default 2 seconds
        if (cooldown > 0) {
            playerCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }
    
    /**
     * Starts cleanup task for expired data.
     */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredData();
            }
        }.runTaskTimer(plugin, CLEANUP_INTERVAL, CLEANUP_INTERVAL);
    }
    
    /**
     * Cleans up expired explosive data and cooldowns.
     */
    private void cleanupExpiredData() {
        long currentTime = System.currentTimeMillis();
        
        // Clean up old cooldowns (older than 5 minutes)
        playerCooldowns.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > 300000);
        
        // Clean up orphaned explosive data
        activeExplosives.entrySet().removeIf(entry -> {
            UUID tntId = entry.getKey();
            return plugin.getServer().getEntity(tntId) == null;
        });
    }
    
    /**
     * Shuts down the explosive behavior system.
     */
    public void shutdown() {
        activeExplosives.clear();
        playerCooldowns.clear();
        logger.info("Explosive behavior system shut down");
    }

    /**
         * Data class for tracking active explosives.
         */
        private record ExplosiveData(UUID tntId, UUID throwerId, CustomItemBehavior explosiveType, int minRadius,
                                     int maxRadius, int fireDuration, int poisonDuration, int poisonAmplifier,
                                     boolean damagesThrower) {
    }
} 