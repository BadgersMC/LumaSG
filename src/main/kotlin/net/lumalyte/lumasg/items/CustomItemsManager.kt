package net.lumalyte.lumasg.items

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import net.badgersmc.nexus.paper.BukkitDispatcher
import net.lumalyte.lumasg.chest.ChestManager
import net.lumalyte.lumasg.config.LumaSGConfig
import net.lumalyte.lumasg.game.GameManager
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory

/**
 * Central registry for all custom items.
 * Provides lookup by ID, filtering by behavior, and creation of custom item stacks.
 */
@Service
class CustomItemsManager(
    private val plugin: JavaPlugin,
    private val config: LumaSGConfig,
    private val gameManager: GameManager,
    private val chestManager: ChestManager,
    private val bukkitDispatcher: BukkitDispatcher
) {
    private val logger = LoggerFactory.getLogger(CustomItemsManager::class.java)
    private val registry = mutableMapOf<String, CustomItem>()

    @PostConstruct
    fun initialize() {
        registerDefaults()
        logger.info("Registered ${registry.size} custom items")
    }

    private fun registerDefaults() {
        listOf(
            FireBombItem(plugin),
            BombItem(plugin),
            PoisonBombItem(plugin),
            KnockbackStickItem(plugin),
            PlayerTrackerItem(plugin, gameManager),
            AirdropFlareItem(plugin, gameManager, chestManager, bukkitDispatcher),
            GliderItem(plugin, config, gameManager),
            SmokeGrenadeItem(plugin, config, gameManager),
            AirstrikeItem(plugin, config, gameManager, bukkitDispatcher)
        ).forEach { item ->
            registry[item.key.key] = item
        }
    }

    /** Get a custom item by its string ID. */
    fun getCustomItem(id: String): CustomItem? = registry[id]

    /** Get all registered custom items. */
    fun getAllCustomItems(): Collection<CustomItem> = registry.values

    /** Create an ItemStack for a custom item by ID. */
    fun createCustomItemStack(id: String): ItemStack? = registry[id]?.createStack()

    /** Create an ItemStack for a custom item by ID with a specific amount. */
    fun createCustomItemStack(id: String, amount: Int): ItemStack? =
        registry[id]?.createStack()?.apply { this.amount = amount }

    /** Check if an ItemStack is a custom item. */
    fun isCustomItem(item: ItemStack): Boolean = CustomItem.fromStack(item, registry.values) != null

    /** Get the custom item ID from an ItemStack. */
    fun getCustomItemId(item: ItemStack): String? = CustomItem.fromStack(item, registry.values)?.key?.key

    /** Get the CustomItem from an ItemStack. */
    fun getCustomItemFromStack(item: ItemStack): CustomItem? = CustomItem.fromStack(item, registry.values)

    /** Whether the custom items system is enabled. */
    fun isEnabled(): Boolean = registry.isNotEmpty()

    /** Reload custom items from config. */
    fun reload() {
        registry.clear()
        registerDefaults()
        logger.info("Reloaded ${registry.size} custom items")
    }
}
