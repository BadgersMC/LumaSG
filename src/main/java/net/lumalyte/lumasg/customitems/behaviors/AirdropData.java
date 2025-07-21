package net.lumalyte.customitems.behaviors;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

/**
 * Data record for tracking active airdrops.
 * Contains all necessary information about an airdrop including:
 * - Unique identifier
 * - Player who called it
 * - Target location
 * - Loot tier
 * - Expected arrival time
 */
public record AirdropData(
    @NotNull UUID airdropId,
    @NotNull UUID callerId,
    @NotNull Location dropLocation,
    @NotNull String lootTier,
    long arrivalTime
) {} 