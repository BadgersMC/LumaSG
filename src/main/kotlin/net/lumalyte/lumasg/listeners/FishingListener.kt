package net.lumalyte.lumasg.listeners

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.lumalyte.lumasg.domain.GamePhase
import net.lumalyte.lumasg.game.GameManager
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.random.Random

private val mm = MiniMessage.miniMessage()

/**
 * Handles special fishing loot during Survival Games matches.
 *
 * Loads loot tables from fishing.yml with weighted random selection.
 * Only active during Active and Deathmatch game phases.
 */
@Service
class FishingListener(
    private val plugin: Plugin,
    private val gameManager: GameManager
) : Listener {

    private val logger = LoggerFactory.getLogger(FishingListener::class.java)
    private var fishingConfig: YamlConfiguration? = null
    private val fishingFile = File(plugin.dataFolder, "fishing.yml")

    @PostConstruct
    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        if (!fishingFile.exists()) {
            try { plugin.saveResource("fishing.yml", false) } catch (_: Exception) {}
        }
        loadFishingConfig()
    }

    private fun loadFishingConfig() {
        try {
            if (!fishingFile.exists()) {
                logger.warn("fishing.yml not found, fishing loot will not be available")
                return
            }
            fishingConfig = YamlConfiguration.loadConfiguration(fishingFile)
            logger.info("Fishing loot configuration loaded successfully")
        } catch (e: Exception) {
            logger.warn("Error loading fishing loot configuration: ${e.message}")
        }
    }

    @EventHandler
    fun onFish(event: PlayerFishEvent) {
        if (event.state != PlayerFishEvent.State.CAUGHT_FISH) return

        val player = event.player
        val game = gameManager.getGameForPlayer(player.uniqueId) ?: return

        // Only during active game phases
        val phase = game.phase
        if (phase !is GamePhase.Active && phase !is GamePhase.Deathmatch) return

        val config = fishingConfig ?: run {
            loadFishingConfig()
            fishingConfig
        } ?: return

        // Check special catch chance
        val specialChance = config.getDouble("special_catch_chance", 25.0)
        if (Random.nextDouble() * 100 > specialChance) return

        // Select random item from weighted table
        val itemsSection = config.getConfigurationSection("items") ?: return
        val chances = mutableMapOf<String, Double>()
        for (key in itemsSection.getKeys(false)) {
            chances[key] = itemsSection.getDouble("$key.chance", 0.0)
        }
        val totalWeight = chances.values.sum()
        if (totalWeight <= 0) return

        var roll = Random.nextDouble() * totalWeight
        var selectedItem: String? = null
        for ((key, weight) in chances) {
            roll -= weight
            if (roll <= 0) {
                selectedItem = key
                break
            }
        }
        selectedItem ?: return

        // Cancel normal catch
        event.isCancelled = true
        event.caught?.remove()

        // Create item from config
        val itemConfig = itemsSection.getConfigurationSection(selectedItem) ?: return
        val materialName = itemConfig.getString("material") ?: return
        val material = Material.matchMaterial(materialName) ?: return

        val minAmount = itemConfig.getInt("min-amount", 1)
        val maxAmount = itemConfig.getInt("max-amount", 1)
        val amount = if (maxAmount > minAmount) Random.nextInt(minAmount, maxAmount + 1) else minAmount

        val item = ItemStack(material, amount)

        // Apply display name from config
        val displayName = itemConfig.getString("name")
        if (displayName != null) {
            val meta = item.itemMeta
            meta.displayName(mm.deserialize(displayName))
            item.itemMeta = meta
        }

        // Apply enchantments from config
        val enchSection = itemConfig.getConfigurationSection("enchantments")
        if (enchSection != null) {
            for (enchKey in enchSection.getKeys(false)) {
                val enchantment = Enchantment.getByName(enchKey.uppercase())
                if (enchantment != null) {
                    item.addUnsafeEnchantment(enchantment, enchSection.getInt(enchKey))
                }
            }
        }

        // Give to player (drop overflow on ground)
        val overflow = player.inventory.addItem(item)
        overflow.values.forEach { drop ->
            player.world.dropItem(player.location, drop)
        }

        // Notify player of special catch
        val itemName = item.itemMeta?.displayName()
            ?: Component.text(material.name.lowercase().replace('_', ' '))
        player.sendMessage(
            Component.text()
                .append(Component.text("You caught ", NamedTextColor.AQUA))
                .append(itemName)
                .append(Component.text("!", NamedTextColor.AQUA))
                .build()
        )

        // Play special catch effects
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        player.spawnParticle(Particle.SPLASH, player.location, 50, 0.5, 0.5, 0.5, 0.1)
    }
}
