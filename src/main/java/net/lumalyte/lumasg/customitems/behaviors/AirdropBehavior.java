package net.lumalyte.lumasg.customitems.behaviors;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.customitems.CustomItem;
import net.lumalyte.lumasg.game.core.Game;
import net.lumalyte.lumasg.util.core.DebugLogger;
import net.lumalyte.lumasg.util.messaging.MiniMessageUtils;
import net.lumalyte.lumasg.exception.LumaSGException;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Handles airdrop flare behavior and the complete airdrop system.
 * 
 * <p>This behavior system provides realistic airdrop mechanics including:</p>
 * <ul>
 *   <li>Flare activation with one-time use</li>
 *   <li>Meteor-style falling airdrop with particle trail</li>
 *   <li>Physics-based explosion on impact</li>
 *   <li>Loot chest placement and protection</li>
 *   <li>Visual indicators and announcements</li>
 * </ul>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class AirdropBehavior implements Listener {
    
    private final LumaSG plugin;
    private final DebugLogger.ContextualLogger logger;
    private final Map<UUID, Long> playerCooldowns;
    private final Map<UUID, AirdropData> activeAirdrops;
    
    // Constants for airdrop mechanics
    private static final double METEOR_FALL_SPEED = 6.0; // Increased for faster fall
    private static final int METEOR_HEIGHT = 150; // Reduced height for better visibility
    private static final int EXPLOSION_RADIUS = 4;
    private static final double EXPLOSION_DAMAGE = 6.0;
    private static final int CHEST_PROTECTION_TIME = 180; // 9 seconds (180 ticks)
    private static final int PARTICLE_CIRCLE_RADIUS = 8;
    private static final int MAX_ACTIVE_AIRDROPS = 3;
    private static final double METEOR_HORIZONTAL_SPEED = 6.0; // Increased horizontal speed
    private static final double METEOR_ARC_HEIGHT = 40.0; // Reduced arc height for better visibility
    
    // Constants for improved meteor physics
    private static final double METEOR_GRAVITY = 0.05; // Increased gravity for more dramatic arc
    private static final int METEOR_SPHERE_RADIUS = 3; // Increased size for better visibility
    private static final int METEOR_UPDATE_INTERVAL = 1; // Update every tick for smooth movement
    private static final int PARTICLE_DENSITY = 6; // Reduced particle density
    private static final double PARTICLE_SPREAD = 1.5; // Reduced particle spread
    
    // Constants for async chunk loading
    private static final int CHUNK_LOAD_AHEAD_DISTANCE = 16; // Reduced to more reasonable distance
    private static final int MAX_METEOR_FLIGHT_TIME_TICKS = 2000; //100 seconds maximum
    private static final int CHUNK_LOAD_INTERVAL = 32; // Increased interval to reduce initial load time
    
    private final Location targetLocation;
	private final BukkitTask protectionTask;
    private final Set<Block> protectedBlocks = new HashSet<>();
    private BukkitTask cleanupTask;
    
    /**
     * Creates a new airdrop behavior handler.
     * 
     * @param plugin The plugin instance
     */
    public AirdropBehavior(@NotNull LumaSG plugin, @NotNull Location targetLocation, @NotNull Location spawnLocation, @NotNull Player player) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("AirdropBehavior");
        this.playerCooldowns = new ConcurrentHashMap<>();
        this.activeAirdrops = new ConcurrentHashMap<>();
        this.targetLocation = targetLocation;
		this.protectionTask = null;
        
        // Register as listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * Handles airdrop flare activation.
     */
    public void activateAirdropFlare(@NotNull Player player, @NotNull CustomItem customItem, @NotNull PlayerInteractEvent event) {
        if (!canUseAirdropFlare(player, customItem)) {
            return;
        }
        
        // Use the exact location where the flare was activated
        Location dropLocation;
        if (event.getClickedBlock() != null) {
            // If they clicked a block, use that block's location
            dropLocation = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);
        } else {
            // If they clicked air, use their exact standing location
            dropLocation = player.getLocation().clone();
        }
        
        // Get configuration
        int delay = customItem.getBehaviorInt("delay", 30);
        String lootTier = customItem.getBehaviorString("loot-tier", "tier4");
        boolean announcement = customItem.getBehaviorBoolean("announcement", true);
        
        // Consume the flare item (one-time use)
        consumeFlareItem(player, event);
        
        // Set cooldown
        setCooldown(player, customItem);
        
        // Create airdrop data
        UUID airdropId = UUID.randomUUID();
        AirdropData airdropData = new AirdropData(
            airdropId,
            player.getUniqueId(),
            dropLocation,
            lootTier,
            System.currentTimeMillis() + (delay * 1000L)
        );
        
        activeAirdrops.put(airdropId, airdropData);
        
        // Start visual indicators
        startDropZoneIndicator(dropLocation, delay);
        
        // Play activation effects
        playFlareActivationEffects(player, dropLocation);
        
        // Schedule the airdrop
        scheduleAirdrop(airdropData, delay);
        
        // Announce to players if enabled
        if (announcement) {
            announceAirdrop(player, dropLocation, delay);
        }
        
        logger.debug("Player " + player.getName() + " activated airdrop flare at " + dropLocation);
    }
    
    /**
     * Checks if player can use airdrop flare.
     */
    private boolean canUseAirdropFlare(@NotNull Player player, @NotNull CustomItem customItem) {
        // Check global airdrop limit
        if (activeAirdrops.size() >= MAX_ACTIVE_AIRDROPS) {
            MiniMessageUtils.sendMessage(player, 
                "<red>Maximum number of active airdrops reached! Please wait for some to despawn.</red>");
            return false;
        }
        
        // Check cooldown
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastUse = playerCooldowns.get(playerId);
        
        if (lastUse != null) {
            int cooldown = customItem.getBehaviorInt("cooldown", 300) * 1000; // Convert to ms
            if (currentTime - lastUse < cooldown) {
                long remaining = (cooldown - (currentTime - lastUse)) / 1000;
                MiniMessageUtils.sendMessage(player, 
                    "<red>Airdrop flare on cooldown for " + remaining + " more seconds!</red>");
                return false;
            }
        }
        
        // Check if player is in a game
        Game game = plugin.getGameManager().getGameByPlayer(player);
        if (game == null) {
            MiniMessageUtils.sendMessage(player, "<red>You can only use airdrop flares during a game!</red>");
            return false;
        }
        
        // Check if PvP is enabled (no airdrops during grace period)
        if (!game.isPvpEnabled()) {
            MiniMessageUtils.sendMessage(player, "<red>You cannot call airdrops during the grace period!</red>");
            return false;
        }
        
        return true;
    }
    
    /**
     * Consumes the flare item from player's inventory.
     */
    private void consumeFlareItem(@NotNull Player player, @NotNull PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
    
    /**
     * Sets cooldown for player.
     */
    private void setCooldown(@NotNull Player player, @NotNull CustomItem customItem) {
        int cooldown = customItem.getBehaviorInt("cooldown", 300); // Default 5 minutes
        if (cooldown > 0) {
            playerCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }
    
    /**
     * Starts the drop zone indicator with blinking particle circle.
     */
    private void startDropZoneIndicator(@NotNull Location location, int delaySeconds) {
        new BukkitRunnable() {
            int ticksElapsed = 0;
            final int totalTicks = delaySeconds * 20;
            
            @Override
            public void run() {
                if (ticksElapsed >= totalTicks) {
                    cancel();
                    return;
                }
                
                // Create blinking effect - show particles every other second
                if ((ticksElapsed / 20) % 2 == 0) {
                    createParticleCircle(location);
                }
                
                ticksElapsed += 10; // Run every 0.5 seconds
            }
        }.runTaskTimer(plugin, 0, 10);
    }
    
    /**
     * Creates a particle circle at the specified location.
     */
    private void createParticleCircle(@NotNull Location center) {
        World world = center.getWorld();
        if (world == null) return;
        
        for (int angle = 0; angle < 360; angle += 10) {
            double radians = Math.toRadians(angle);
            double x = center.getX() + (AirdropBehavior.PARTICLE_CIRCLE_RADIUS * Math.cos(radians));
            double z = center.getZ() + (AirdropBehavior.PARTICLE_CIRCLE_RADIUS * Math.sin(radians));
            
            Location particleLocation = new Location(world, x, center.getY() + 0.5, z);

			// Red dust particles for the indicator
			Particle.DustOptions dustOptions = new Particle.DustOptions(Color.RED, 1.0f);
			world.spawnParticle(Particle.DUST, particleLocation, 1, dustOptions);
		}
    }
    
    /**
     * Plays flare activation effects.
     */
    private void playFlareActivationEffects(@NotNull Player player, @NotNull Location location) {
        World world = location.getWorld();
        if (world == null) return;
        
        // Sound effects
        world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2.0f, 0.8f);
        world.playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.2f);
        
        // Visual effects
        world.spawnParticle(Particle.FIREWORK, location, 20, 2, 2, 2, 0.1);
        world.spawnParticle(Particle.FLAME, location, 30, 1, 1, 1, 0.05);
        
        // Message to player
        MiniMessageUtils.sendMessage(player, "<gold><bold>Airdrop flare activated!</bold></gold> <yellow>Supply drop incoming...</yellow>");
    }
    
    /**
     * Schedules the airdrop to arrive after delay.
     */
    private void scheduleAirdrop(@NotNull AirdropData airdropData, int delaySeconds) {
        new BukkitRunnable() {
            @Override
            public void run() {
                executeAirdrop(airdropData);
            }
        }.runTaskLater(plugin, delaySeconds * 20L);
    }
    
    /**
     * Executes the airdrop with meteor fall and explosion.
     */
    public void executeAirdrop(@NotNull AirdropData airdropData) {
        Location dropLocation = airdropData.dropLocation();
        World world = dropLocation.getWorld();
        if (world == null) return;
        
        // Calculate spawn location - now 50 blocks away instead of 100 to reduce travel time
        double angle = Math.random() * 2 * Math.PI;
        double spawnDistance = 50.0;
        
        Location meteorStart = dropLocation.clone().add(
            Math.cos(angle) * spawnDistance,
            METEOR_HEIGHT,
            Math.sin(angle) * spawnDistance
        );
        
        // Calculate precise direction vector from start to exact target
        Vector direction = dropLocation.toVector().subtract(meteorStart.toVector()).normalize();
        direction.multiply(METEOR_FALL_SPEED);
        
        logger.debug("Meteor spawn location: " + String.format("%.1f, %.1f, %.1f", 
            meteorStart.getX(), meteorStart.getY(), meteorStart.getZ()));
        logger.debug("Target location: " + String.format("%.1f, %.1f, %.1f", 
            dropLocation.getX(), dropLocation.getY(), dropLocation.getZ()));
        logger.debug("Direction vector: " + String.format("%.3f, %.3f, %.3f", 
            direction.getX(), direction.getY(), direction.getZ()));
        logger.debug("Distance to target: " + String.format("%.1f blocks", meteorStart.distance(dropLocation)));
        
        // Start with just loading the critical chunks first
        CompletableFuture<org.bukkit.Chunk> startChunkFuture = world.getChunkAtAsync(meteorStart);
        CompletableFuture<org.bukkit.Chunk> targetChunkFuture = world.getChunkAtAsync(dropLocation);
        
        // Once critical chunks are loaded, spawn meteor and continue loading rest of path
        CompletableFuture.allOf(startChunkFuture, targetChunkFuture).thenRun(() -> {
            // Spawn meteor immediately after critical chunks are loaded
            new BukkitRunnable() {
                @Override
                public void run() {
                    startMeteorMovement(meteorStart, direction, dropLocation, airdropData, world);
                    
                    // Continue loading remaining chunks in the background
                    loadMeteorPathChunks(meteorStart, dropLocation, world).thenRun(() -> {
                        logger.debug("Finished loading remaining meteor path chunks");
                    });
                }
            }.runTask(plugin);
        });
    }
    
    /**
     * Pre-loads chunks along the meteor's flight path using Paper's async chunk loading.
     */
    private @NotNull CompletableFuture<Void> loadMeteorPathChunks(@NotNull Location start, @NotNull Location target, @NotNull World world) {
        List<CompletableFuture<org.bukkit.Chunk>> chunkFutures = new ArrayList<>();
        Set<String> loadedChunkKeys = new HashSet<>(); // Prevent duplicate chunk loading
        
        // Calculate the path from start to target
        Vector direction = target.toVector().subtract(start.toVector()).normalize();
        double distance = Math.min(start.distance(target), CHUNK_LOAD_AHEAD_DISTANCE); // Limit loading distance
        
        // Load chunks every CHUNK_LOAD_INTERVAL blocks along the path
        for (double d = 0; d <= distance; d += CHUNK_LOAD_INTERVAL) {
            Location pathPoint = start.clone().add(direction.clone().multiply(d));
            String chunkKey = (pathPoint.getBlockX() >> 4) + "," + (pathPoint.getBlockZ() >> 4);
            
            if (!loadedChunkKeys.contains(chunkKey)) {
                loadedChunkKeys.add(chunkKey);
                CompletableFuture<org.bukkit.Chunk> chunkFuture = world.getChunkAtAsync(pathPoint);
                chunkFutures.add(chunkFuture);
            }
        }
        
        // Also ensure start and target chunks are loaded
        String startChunkKey = (start.getBlockX() >> 4) + "," + (start.getBlockZ() >> 4);
        String targetChunkKey = (target.getBlockX() >> 4) + "," + (target.getBlockZ() >> 4);
        
        if (!loadedChunkKeys.contains(startChunkKey)) {
            chunkFutures.add(world.getChunkAtAsync(start));
        }
        if (!loadedChunkKeys.contains(targetChunkKey)) {
            chunkFutures.add(world.getChunkAtAsync(target));
        }
        
        logger.debug("Loading " + chunkFutures.size() + " unique chunks along meteor path (distance: " + (int)distance + " blocks)");
        
        // Wait for all chunks to load
        return CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                logger.debug("Successfully loaded all meteor path chunks");
            });
    }
    
    /**
     * Starts the meteor movement system.
     */
    private void startMeteorMovement(@NotNull Location meteorStart, @NotNull Vector direction, 
                                   @NotNull Location target, @NotNull AirdropData airdropData, @NotNull World world) {
        // Announce meteor
        Game game = findNearbyGame(meteorStart);
        if (game != null) {
            String message = "<gold><bold>Meteor incoming!</bold></gold> <yellow>Impact in approximately 5 seconds...</yellow>";
            game.broadcastMessage(MiniMessageUtils.parseMessage(message));
        }

        new BukkitRunnable() {
            private int ticksAlive = 0;
            private final Location startLocation = meteorStart.clone();
            private final double totalDistance = startLocation.distance(target);
            private double currentSpeed = METEOR_HORIZONTAL_SPEED * 0.5;
            private final Location currentLocation = meteorStart.clone();
            
            @Override
            public void run() {
                ticksAlive++;
                
                // Safety check
                if (ticksAlive > MAX_METEOR_FLIGHT_TIME_TICKS) {
                    logger.warn("Meteor exceeded maximum flight time, forcing impact at target");
                    handleMeteorImpact(target, airdropData);
                    cancel();
                    return;
                }
                
                double distanceToTarget = currentLocation.distance(target);
                
                // Debug logging every second
                if (ticksAlive % 20 == 0) {
                    logger.debug("Meteor tick " + ticksAlive + " - Position: " + 
                        String.format("%.1f, %.1f, %.1f", currentLocation.getX(), currentLocation.getY(), currentLocation.getZ()) +
                        " | Distance to target: " + String.format("%.1f", distanceToTarget) + " blocks" +
                        " | Speed: " + String.format("%.1f", currentSpeed));
                }
                
                // Calculate progress and update speed
                double progress = 1.0 - (distanceToTarget / totalDistance);
                double arcProgress = Math.min(progress, 1.0);
                currentSpeed = METEOR_HORIZONTAL_SPEED * (0.5 + (0.5 * arcProgress)); // Gradual acceleration
                
                // Calculate arc height
                double arcHeight = Math.sin(arcProgress * Math.PI) * METEOR_ARC_HEIGHT;
                
                // Calculate direction with arc
                Vector preciseDirection = target.toVector().subtract(currentLocation.toVector()).normalize();
                preciseDirection.multiply(currentSpeed * METEOR_UPDATE_INTERVAL); // Adjust for update interval
                
                // Apply arc height
                double yOffset = METEOR_GRAVITY * arcHeight;
                if (arcProgress < 0.5) {
                    preciseDirection.setY(preciseDirection.getY() + yOffset);
                } else {
                    preciseDirection.setY(preciseDirection.getY() - yOffset);
                }
                
                // Update current location
                currentLocation.add(preciseDirection);
                
                // Create meteor sphere using particles
                createMeteorSphere(currentLocation);
                
                // Check for impact
                if (distanceToTarget < METEOR_SPHERE_RADIUS || currentLocation.getY() <= target.getY()) {
                    logger.debug("Meteor impact triggered - Distance: " + String.format("%.1f", distanceToTarget));
                    handleMeteorImpact(target, airdropData);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, METEOR_UPDATE_INTERVAL);
    }
    
    /**
     * Creates a sphere of particles around the meteor using the METEOR_SPHERE_RADIUS
     */
    private void createMeteorSphere(@NotNull Location center) {
        World world = center.getWorld();
        if (world == null) return;
        
        // Core particles - dense inner sphere
        for (int i = 0; i < PARTICLE_DENSITY * 3; i++) {
            // Generate points using spherical coordinates for better distribution
            double radius = Math.random() * (METEOR_SPHERE_RADIUS * 0.7); // Inner 70% for core
            double theta = Math.random() * 2 * Math.PI;
            double phi = Math.acos(2 * Math.random() - 1); // Better distribution
            
            double x = radius * Math.sin(phi) * Math.cos(theta);
            double y = radius * Math.sin(phi) * Math.sin(theta);
            double z = radius * Math.cos(phi);
            
            Location particleLoc = center.clone().add(x, y, z);
            
            // Core effects - more intense near center
            if (radius < METEOR_SPHERE_RADIUS * 0.3) {
                // Inner core - very hot
                world.spawnParticle(Particle.LAVA, particleLoc, 2, PARTICLE_SPREAD * 0.1, PARTICLE_SPREAD * 0.1, PARTICLE_SPREAD * 0.1, 0);
                world.spawnParticle(Particle.FLAME, particleLoc, 3, PARTICLE_SPREAD * 0.1, PARTICLE_SPREAD * 0.1, PARTICLE_SPREAD * 0.1, 0.05);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc, 1, PARTICLE_SPREAD * 0.1, PARTICLE_SPREAD * 0.1, PARTICLE_SPREAD * 0.1, 0.02);
            } else {
                // Outer core - mix of effects
                world.spawnParticle(Particle.FLAME, particleLoc, 2, PARTICLE_SPREAD * 0.1, PARTICLE_SPREAD * 0.1, PARTICLE_SPREAD * 0.1, 0.02);
                world.spawnParticle(Particle.FIREWORK, particleLoc, 2, PARTICLE_SPREAD * 0.1, PARTICLE_SPREAD * 0.1, PARTICLE_SPREAD * 0.1, 0.01);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc, 1, PARTICLE_SPREAD * 0.1, PARTICLE_SPREAD * 0.1, PARTICLE_SPREAD * 0.1, 0.01);
            }
        }
        
        // Outer shell - more defined sphere surface
        int shellPoints = PARTICLE_DENSITY * 6;
        for (int i = 0; i < shellPoints; i++) {
            // Use golden ratio for better point distribution
            double phi = Math.acos(1 - 2 * (i + 0.5) / shellPoints);
            double theta = Math.PI * (1 + Math.sqrt(5)) * i;
            
            double x = METEOR_SPHERE_RADIUS * Math.sin(phi) * Math.cos(theta);
            double y = METEOR_SPHERE_RADIUS * Math.sin(phi) * Math.sin(theta);
            double z = METEOR_SPHERE_RADIUS * Math.cos(phi);
            
            Location shellLoc = center.clone().add(x, y, z);
            
            // Shell effects - mix of particles
            world.spawnParticle(Particle.CLOUD, shellLoc, 2, 0.1, 0.1, 0.1, 0);
            world.spawnParticle(Particle.FIREWORK, shellLoc, 2, 0, 0, 0, 0.05);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, shellLoc, 1, 0.1, 0.1, 0.1, 0.02);
            
            // Random additional effects
            if (Math.random() < 0.4) {
                world.spawnParticle(Particle.FLAME, shellLoc, 2, 0.1, 0.1, 0.1, 0.02);
            }
        }
        
        // Trailing particles - more dynamic trail
        double trailLength = METEOR_SPHERE_RADIUS * 4;
        for (int i = 0; i < PARTICLE_DENSITY * 3; i++) {
            // Calculate trail position with exponential falloff
            double trailProgress = Math.random();
            double trailRadius = METEOR_SPHERE_RADIUS * (1 + Math.random());
            
            Location trailLoc = center.clone().add(
                (Math.random() - 0.5) * trailRadius,
                -trailProgress * trailLength,
                (Math.random() - 0.5) * trailRadius
            );
            
            // Trail effects - vary based on distance
            if (trailProgress < 0.3) {
                // Near meteor - hot particles
                world.spawnParticle(Particle.FLAME, trailLoc, 2, 0.2, 0.2, 0.2, 0.02);
                world.spawnParticle(Particle.FIREWORK, trailLoc, 2, 0.1, 0.1, 0.1, 0.01);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, trailLoc, 1, 0.1, 0.1, 0.1, 0.01);
            } else if (trailProgress < 0.7) {
                // Mid trail - smoke and embers
                world.spawnParticle(Particle.CLOUD, trailLoc, 2, 0.3, 0.3, 0.3, 0.01);
                if (Math.random() < 0.4) {
                    world.spawnParticle(Particle.FLAME, trailLoc, 2, 0.1, 0.1, 0.1, 0.01);
                }
            } else {
                // Far trail - mostly smoke
                world.spawnParticle(Particle.CLOUD, trailLoc, 2, 0.4, 0.4, 0.4, 0);
            }
        }
        
        // Add sound effects for better feedback
        if (Math.random() < 0.3) { // 30% chance per update to play sound
            world.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 0.5f);
        }
    }
    
    /**
     * Handles meteor impact with visual explosion.
     */
    private void handleMeteorImpact(@NotNull Location impactLocation, @NotNull AirdropData airdropData) {
        World world = impactLocation.getWorld();
        if (world == null) return;
        
        // Ensure impact chunk is loaded before creating explosion
        world.getChunkAtAsync(impactLocation).thenAccept(chunk -> {
            // Create visual explosion
            createPhysicsExplosion(impactLocation);
            
            // Damage nearby players
            damageNearbyPlayers(impactLocation);
            
            // Place airdrop chest
            placeAirdropChest(impactLocation, airdropData);
            
            // Play impact effects
            playImpactEffects(impactLocation);
            
            // Announce airdrop arrival
            announceAirdropArrival(impactLocation);
            
            // Clean up airdrop data
            activeAirdrops.remove(airdropData.airdropId());
            
            logger.debug("Airdrop impact at " + impactLocation);
        }).exceptionally(throwable -> {
            logger.error("Failed to load impact chunk, performing impact anyway", throwable);
            // Fallback: perform impact without guaranteed chunk loading
            performImpactFallback(impactLocation, airdropData);
            return null;
        });
    }
    
    /**
     * Fallback method for meteor impact when chunk loading fails.
     */
    private void performImpactFallback(@NotNull Location impactLocation, @NotNull AirdropData airdropData) {
        // Create visual explosion
        createPhysicsExplosion(impactLocation);
        
        // Damage nearby players
        damageNearbyPlayers(impactLocation);
        
        // Place airdrop chest
        placeAirdropChest(impactLocation, airdropData);
        
        // Play impact effects
        playImpactEffects(impactLocation);
        
        // Announce airdrop arrival
        announceAirdropArrival(impactLocation);
        
        // Clean up airdrop data
        activeAirdrops.remove(airdropData.airdropId());
        
        logger.debug("Airdrop impact fallback at " + impactLocation);
    }
    
    /**
     * Creates a physics-based explosion effect.
     */
    private void createPhysicsExplosion(@NotNull Location center) {
        World world = center.getWorld();
        if (world == null) return;
        
        // Create visual effects
        world.spawnParticle(Particle.EXPLOSION, center, 3, 1, 1, 1, 0);
        world.spawnParticle(Particle.EXPLOSION, center, 8, 2, 2, 2, 0.1);
        world.spawnParticle(Particle.FLAME, center, 30, 2, 2, 2, 0.2);
        world.spawnParticle(Particle.LAVA, center, 20, 2, 2, 2, 0.1);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 25, 3, 3, 3, 0.1);
        
        // Create debris effect particles
        for (int i = 0; i < 50; i++) {
            double angle = Math.random() * Math.PI * 2;
            double radius = Math.random() * EXPLOSION_RADIUS;
            double height = Math.random() * 2;
            
            Location particleLoc = center.clone().add(
                Math.cos(angle) * radius,
                height,
                Math.sin(angle) * radius
            );
            
            world.spawnParticle(Particle.FALLING_DUST, particleLoc, 5, 0.2, 0.2, 0.2, 0.1, 
                Material.MAGMA_BLOCK.createBlockData());
        }
        
        // Play explosion sounds
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.7f);
        world.playSound(center, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.5f, 0.8f);
        world.playSound(center, Sound.BLOCK_ANCIENT_DEBRIS_BREAK, 1.0f, 0.5f);
    }
    
    /**
     * Damages nearby players from the airdrop impact.
     */
    private void damageNearbyPlayers(@NotNull Location center) {
        World world = center.getWorld();
        if (world == null) return;
        
        for (Player player : world.getPlayers()) {
            double distance = player.getLocation().distance(center);
            if (distance <= EXPLOSION_RADIUS) {
                // Calculate damage based on distance
                double damageMultiplier = 1.0 - (distance / EXPLOSION_RADIUS);
                double damage = EXPLOSION_DAMAGE * damageMultiplier;
                
                if (damage > 0) {
                    player.damage(damage);
                    
                    // Add knockback effect with proper validation
                    Vector knockback = calculateKnockbackVector(player.getLocation(), center, damageMultiplier);
                    player.setVelocity(knockback);
                    
                    // Send impact message
                    MiniMessageUtils.sendMessage(player, "<red>You were caught in the meteor impact!</red>");
                }
            }
        }
    }
    
    /**
     * Calculates a safe knockback vector that won't produce infinite values.
     */
    private @NotNull Vector calculateKnockbackVector(@NotNull Location playerLoc, @NotNull Location center, double damageMultiplier) {
        try {
            Vector knockback = playerLoc.toVector().subtract(center.toVector());
            
            // Handle case where player is exactly at explosion center
            if (knockback.lengthSquared() < 0.01) {
                // Give a small random knockback if player is at exact center
                knockback = new Vector(
                    (Math.random() - 0.5) * 0.5,
                    0.3,
                    (Math.random() - 0.5) * 0.5
                );
            } else {
                knockback = knockback.normalize();
                    knockback.multiply(damageMultiplier * 0.5);
                    knockback.setY(Math.max(knockback.getY(), 0.2));
            }
            
            // Validate the vector before returning
            if (isValidVector(knockback)) {
                return knockback;
            } else {
                logger.warn("Invalid knockback vector calculated, using fallback");
                return new Vector(0, 0.3, 0); // Simple upward knockback as fallback
            }
            
        } catch (Exception e) {
            logger.warn("Error calculating knockback vector: " + e.getMessage());
            return new Vector(0, 0.3, 0); // Simple upward knockback as fallback
        }
    }
    
    /**
     * Validates that a vector contains finite values.
     */
    private boolean isValidVector(@NotNull Vector vector) {
        return Double.isFinite(vector.getX()) && 
               Double.isFinite(vector.getY()) && 
               Double.isFinite(vector.getZ());
    }
    
    /**
     * Places the airdrop chest with loot.
     */
    private void placeAirdropChest(@NotNull Location location, @NotNull AirdropData airdropData) {
        // Find a suitable location for the chest
        Location chestLocation = findSuitableChestLocation(location);
        
        // Place chest
        Block chestBlock = chestLocation.getBlock();
        chestBlock.setType(Material.CHEST);
        
        // Fill with loot
        fillAirdropChest(chestLocation, airdropData);
        
        // Start chest protection
        startChestProtection(chestLocation);
        
        logger.debug("Airdrop chest placed at " + chestLocation);
    }
    
    /**
     * Finds a suitable location for the chest near the impact site.
     */
    private @NotNull Location findSuitableChestLocation(@NotNull Location impact) {
        World world = impact.getWorld();
        if (world == null) return impact;
        
        // Start from impact location and look for solid ground
        Location chestLocation = impact.clone();
        
        // Move down until we find solid ground or hit bedrock
        while (chestLocation.getY() > 0 && !chestLocation.getBlock().getType().isSolid()) {
            chestLocation.subtract(0, 1, 0);
        }
        
        // Move up one block to place chest on top
        chestLocation.add(0, 1, 0);
        
        return chestLocation;
    }
    
    /**
     * Fills the airdrop chest with loot.
     */
    private void fillAirdropChest(@NotNull Location chestLocation, @NotNull AirdropData airdropData) {
        // Use the chest manager to fill with appropriate tier loot
        try {
            plugin.getChestManager().fillChest(chestLocation, airdropData.lootTier());
        } catch (Exception e) {
            logger.error("Failed to fill airdrop chest", e);
            // Fallback: place some basic items
            fillChestWithFallbackLoot(chestLocation, airdropData);
        }
    }
    
    /**
     * Fills chest with fallback loot if chest manager fails.
     */
    private void fillChestWithFallbackLoot(@NotNull Location chestLocation, @NotNull AirdropData airdropData) {
        // Implementation for fallback loot - basic survival items
        // This would be implemented based on your existing loot system
        logger.debug("Using fallback loot for airdrop chest");
    }
    
    /**
     * Starts chest protection to prevent immediate opening.
     */
    private void startChestProtection(@NotNull Location chestLocation) {
        // Add glowing effect to the chest
        Block chestBlock = chestLocation.getBlock();
        
        // Create protection task
        new BukkitRunnable() {
            int ticksRemaining = CHEST_PROTECTION_TIME;
            
            @Override
            public void run() {
                if (ticksRemaining <= 0 || chestBlock.getType() != Material.CHEST) {
                    cancel();
                    return;
                }
                
                // Create glowing particles around chest
                World world = chestLocation.getWorld();
                if (world != null) {
                    world.spawnParticle(Particle.END_ROD, 
                        chestLocation.clone().add(0.5, 1, 0.5), 
                        3, 0.3, 0.3, 0.3, 0.01);
                }
                
                ticksRemaining -= 20; // Decrease by 1 second
            }
        }.runTaskTimer(plugin, 0, 20);
    }
    
    /**
     * Plays additional impact effects at the impact location.
     */
    private void playImpactEffects(@NotNull Location center) {
        World world = center.getWorld();
        if (world == null) return;
        
        // Create shockwave effect
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
            for (double radius = 0.5; radius <= EXPLOSION_RADIUS; radius += 0.5) {
                Location particleLoc = center.clone().add(
                    Math.cos(angle) * radius,
                    0.1,
                    Math.sin(angle) * radius
                );
                
                world.spawnParticle(Particle.FLAME, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                if (radius % 1 == 0) {
                    world.spawnParticle(Particle.LAVA, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                }
            }
        }
        
        // Create rising fire column
        for (int i = 0; i < 20; i++) {
            Location columnLoc = center.clone().add(0, i * 0.5, 0);
            world.spawnParticle(Particle.FLAME, columnLoc, 3, 0.2, 0.1, 0.2, 0.05);
            world.spawnParticle(Particle.LARGE_SMOKE, columnLoc, 2, 0.2, 0.1, 0.2, 0.02);
        }
        
        // Additional ambient sounds
        world.playSound(center, Sound.BLOCK_FIRE_AMBIENT, 1.0f, 0.5f);
        world.playSound(center, Sound.BLOCK_LAVA_POP, 1.0f, 0.8f);
    }
    
    /**
     * Announces airdrop to players.
     */
    private void announceAirdrop(@NotNull Player caller, @NotNull Location location, int delay) {
        Game game = plugin.getGameManager().getGameByPlayer(caller);
        if (game == null) return;
        
        String message = "<gold><bold>" + caller.getName() + "</bold></gold> <yellow>called in an airdrop! " +
                        "ETA: <white>" + delay + "</white> seconds</yellow>";
        
        game.broadcastMessage(MiniMessageUtils.parseMessage(message));
    }
    
    /**
     * Announces airdrop arrival.
     */
    private void announceAirdropArrival(@NotNull Location location) {
        // Find nearby game
        Game nearbyGame = findNearbyGame(location);
        if (nearbyGame == null) return;
        
        String message = "<gold><bold>Airdrop has landed!</bold></gold> <yellow>Coordinates: " +
                        (int)location.getX() + ", " + (int)location.getZ() + "</yellow>";
        
        nearbyGame.broadcastMessage(MiniMessageUtils.parseMessage(message));
    }
    
    /**
     * Finds a game near the specified location.
     */
    private @Nullable Game findNearbyGame(@NotNull Location location) {
        for (Game game : plugin.getGameManager().getActiveGames()) {
            if (game.getArena().getWorld() != null && 
                game.getArena().getWorld().equals(location.getWorld())) {
                return game;
            }
        }
        return null;
    }
    
    /**
     * Shuts down the airdrop behavior system.
     */
    public void shutdown() {
        playerCooldowns.clear();
        activeAirdrops.clear();
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        protectedBlocks.clear();
        logger.info("Airdrop behavior system shut down");
    }
    
    /**
     * Gets a map of active airdrop IDs to their locations.
     * 
     * @return Map of airdrop IDs to their locations
     */
    public @NotNull Map<UUID, Location> getActiveAirdropLocations() {
        Map<UUID, Location> locations = new HashMap<>();
        for (Map.Entry<UUID, AirdropData> entry : activeAirdrops.entrySet()) {
            locations.put(entry.getKey(), entry.getValue().dropLocation());
        }
        return locations;
    }

    private void placeAirdropChest() {
        // Place the chest at the target location
        Block block = targetLocation.getBlock();
        block.setType(Material.CHEST);

        // Fill the chest with loot
        try {
            plugin.getChestManager().fillChest(targetLocation, "tier4");
        } catch (LumaSGException.ChestException e) {
            logger.severe("Failed to fill airdrop chest: " + e.getMessage());
            return;
        }

        // Start protection
        startChestProtection(targetLocation);

        // Schedule cleanup after 5 minutes (configurable in future)
        cleanupTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (block.getType() == Material.CHEST) {
                block.setType(Material.AIR);
                protectedBlocks.remove(block);
                logger.debug("Cleaned up airdrop chest at " + block.getLocation());
            }
        }, 20 * 60 * 5); // 5 minutes in ticks

        logger.debug("Placed airdrop chest at " + block.getLocation());
    }

    /**
     * Cleans up any resources used by this behavior.
     */
    public void cleanup() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }
        if (protectionTask != null && !protectionTask.isCancelled()) {
            protectionTask.cancel();
        }
        // Clear any remaining protected blocks
        protectedBlocks.clear();
    }
} 
