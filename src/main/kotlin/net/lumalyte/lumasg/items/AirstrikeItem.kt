package net.lumalyte.lumasg.items

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.PreDestroy
import net.badgersmc.nexus.annotations.Service
import net.badgersmc.nexus.paper.BukkitDispatcher
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.lumalyte.lumasg.config.LumaSGConfig
import net.lumalyte.lumasg.domain.GamePhase
import net.lumalyte.lumasg.game.Game
import net.lumalyte.lumasg.game.GameManager
import net.lumalyte.lumasg.util.MeteorUtils
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

@Service
class AirstrikeItem(
    private val plugin: JavaPlugin,
    private val config: LumaSGConfig,
    private val gameManager: GameManager,
    private val bukkitDispatcher: BukkitDispatcher
) : CustomItem, Listener {

    companion object {
        fun airstrikeImmuneUuids(teamMembers: Collection<UUID>?, callerUuid: UUID): Set<UUID> =
            (teamMembers?.toSet() ?: emptySet()) + callerUuid
    }

    override val material = Material.SPYGLASS
    override val displayName = "§c§lAirstrike Designator"
    override val key = NamespacedKey(plugin, "airstrike")

    private val cfg get() = config.airstrike
    private val mm = MiniMessage.miniMessage()

    sealed class ChargeState {
        data class Charging(
            val startTick: Int,
            var ticksCharged: Int = 0,
            var targetLocation: Location? = null
        ) : ChargeState()

        data class Locked(
            val targetLocation: Location,
            var lockTicks: Int = 0
        ) : ChargeState()
    }

    val chargeStates = ConcurrentHashMap<UUID, ChargeState>()
    private var chargeTask: BukkitRunnable? = null

    @PostConstruct
    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @PreDestroy
    fun shutdown() {
        chargeTask?.cancel()
        chargeTask = null
        chargeStates.clear()
    }

    override fun createStack(): ItemStack {
        val stack = ItemStack(material)
        val meta: ItemMeta = stack.itemMeta ?: return stack
        meta.setDisplayName(displayName)
        meta.lore = listOf(
            "§7Hold right-click to lock target",
            "§7Calls in a meteor barrage",
            "§cDanger radius: ${cfg.blastRadius} blocks"
        )
        stack.itemMeta = meta
        return tag(stack)
    }

    fun isAirstrikeItem(stack: ItemStack): Boolean {
        val meta = stack.itemMeta ?: return false
        return meta.persistentDataContainer.has(key, PersistentDataType.BOOLEAN)
    }

    override fun onUse(player: Player, item: ItemStack) {
        if (!cfg.enabled) return
        if (chargeStates.containsKey(player.uniqueId)) return

        chargeStates[player.uniqueId] = ChargeState.Charging(startTick = player.world.fullTime.toInt())
        ensureTaskRunning()
    }

    // ── Charge task ───────────────────────────────────────────────────────

    private fun ensureTaskRunning() {
        if (chargeTask != null) return
        chargeTask = object : BukkitRunnable() {
            override fun run() {
                if (chargeStates.isEmpty()) {
                    cancel()
                    chargeTask = null
                    return
                }
                val toRemove = mutableListOf<UUID>()
                for ((uuid, state) in chargeStates) {
                    val player = Bukkit.getPlayer(uuid)
                    if (player == null || !player.isOnline) {
                        toRemove.add(uuid)
                        continue
                    }

                    if (!isAirstrikeItem(player.inventory.itemInMainHand) || !player.isHandRaised) {
                        cancelCharge(player)
                        toRemove.add(uuid)
                        continue
                    }

                    when (state) {
                        is ChargeState.Charging -> handleCharging(player, state, uuid, toRemove)
                        is ChargeState.Locked -> handleLocked(player, state, uuid, toRemove)
                    }
                }
                toRemove.forEach { chargeStates.remove(it) }
            }
        }
        chargeTask!!.runTaskTimer(plugin, 0L, 2L)
    }

    private fun handleCharging(player: Player, state: ChargeState.Charging, uuid: UUID, toRemove: MutableList<UUID>) {
        val rayResult = player.world.rayTraceBlocks(
            player.eyeLocation, player.eyeLocation.direction,
            cfg.maxRange.toDouble(), FluidCollisionMode.NEVER
        )
        if (rayResult?.hitBlock == null) {
            player.sendActionBar(mm.deserialize(cfg.noTargetMessage))
            return
        }

        state.targetLocation = rayResult.hitPosition.toLocation(player.world)
        state.ticksCharged += 2

        val progress = (state.ticksCharged.toDouble() / cfg.chargeTimeTicks).coerceIn(0.0, 1.0)
        val barLength = 20
        val filled = (progress * barLength).toInt()
        val bar = "▰".repeat(filled) + "▱".repeat(barLength - filled)
        val color = if (progress < 0.5) NamedTextColor.YELLOW else NamedTextColor.RED
        player.sendActionBar(Component.text(bar, color))

        if (state.ticksCharged >= cfg.chargeTimeTicks) {
            chargeStates[uuid] = ChargeState.Locked(targetLocation = state.targetLocation!!)
        }
    }

    private fun handleLocked(player: Player, state: ChargeState.Locked, uuid: UUID, toRemove: MutableList<UUID>) {
        state.lockTicks += 2

        val lockText = mm.deserialize(cfg.lockActionbar)
        if ((state.lockTicks / 2) % 2 == 0) {
            player.sendActionBar(lockText)
        } else {
            player.sendActionBar(Component.empty())
        }

        val pitchProgress = state.lockTicks.toFloat() / cfg.lockOnDurationTicks
        val pitch = 0.5f + pitchProgress * 1.5f
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1f, pitch)

        if (state.lockTicks >= cfg.lockOnDurationTicks) {
            val game = gameManager.getGameForPlayer(uuid)
            if (game == null) {
                // No game (player left / game ended): clear the lock so we don't loop the
                // charge forever, but do NOT consume the item since no strike fires.
                toRemove.add(uuid)
                player.sendActionBar(Component.empty())
                return
            }
            toRemove.add(uuid)
            val held = player.inventory.itemInMainHand
            if (isAirstrikeItem(held)) held.amount--
            player.sendActionBar(Component.empty())
            launchAirstrike(state.targetLocation, game, uuid, player.name)
        }
    }

    fun cancelCharge(uuid: UUID) {
        chargeStates.remove(uuid)
        Bukkit.getPlayer(uuid)?.sendActionBar(Component.empty())
    }

    private fun cancelCharge(player: Player) {
        player.sendActionBar(Component.empty())
    }

    // ── Cancel events ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    fun onItemSwitch(event: PlayerItemHeldEvent) {
        val oldItem = event.player.inventory.getItem(event.previousSlot)
        if (oldItem != null && isAirstrikeItem(oldItem)) {
            cancelCharge(event.player.uniqueId)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDrop(event: PlayerDropItemEvent) {
        if (isAirstrikeItem(event.itemDrop.itemStack)) {
            cancelCharge(event.player.uniqueId)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        cancelCharge(event.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeath(event: PlayerDeathEvent) {
        cancelCharge(event.entity.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDamage(event: EntityDamageEvent) {
        if (!cfg.cancelOnDamage) return
        val player = event.entity as? Player ?: return
        cancelCharge(player.uniqueId)
    }

    // ── Airstrike coroutine ───────────────────────────────────────────────

    private fun launchAirstrike(target: Location, game: Game, callerUuid: UUID, callerName: String) {
        game.scope.launch {
            // Broadcast
            withContext(bukkitDispatcher) {
                val playerResolver = TagResolver.resolver("player", Tag.selfClosingInserting(Component.text(callerName)))
                val msg = mm.deserialize(cfg.broadcastMessage, playerResolver)
                for (uuid in game.players.keys) {
                    Bukkit.getPlayer(uuid)?.sendMessage(msg)
                }
            }

            // ── Warning phase ────────────────────────────────────────────
            val warningMs = cfg.warningDurationTicks * 50L

            val warningJob = launch {
                while (true) {
                    withContext(bukkitDispatcher) {
                        MeteorUtils.spawnGroundCircle(target, cfg.blastRadius, Color.RED, 15)

                        val warningMsg = mm.deserialize(cfg.warningActionbar)
                        target.world.getNearbyEntities(
                            target, cfg.blastRadius.toDouble(), 50.0, cfg.blastRadius.toDouble()
                        ).filterIsInstance<Player>().forEach { p ->
                            val dist2d = sqrt((p.location.x - target.x).pow(2) + (p.location.z - target.z).pow(2))
                            if (dist2d <= cfg.blastRadius) {
                                p.sendActionBar(warningMsg)
                                p.playSound(p.location, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 0.8f)
                            }
                        }
                    }
                    delay(1000L)
                }
            }

            delay(warningMs)

            // ── Barrage phase ────────────────────────────────────────────
            val meteorCount = if (game.phase is GamePhase.Deathmatch) {
                cfg.deathmatchMeteorCount
            } else {
                cfg.meteorCount
            }

            for (i in 0 until meteorCount) {
                if (i > 0) delay(cfg.meteorDelayTicks * 50L)

                val angle = Math.random() * 2 * Math.PI
                val distance = sqrt(Math.random()) * cfg.blastRadius
                val impactPoint = target.clone().add(cos(angle) * distance, 0.0, sin(angle) * distance)

                MeteorUtils.launchMeteor(
                    target = impactPoint,
                    approachAngle = Math.random() * 2 * Math.PI,
                    bukkitDispatcher = bukkitDispatcher
                ) {
                    val callerTeam = game.teamManager.getTeamForPlayer(callerUuid)
                    val immuneUUIDs = airstrikeImmuneUuids(callerTeam?.members, callerUuid)

                    val chunk = impactPoint.chunk
                    if (!chunk.isLoaded) chunk.load()

                    MeteorUtils.spawnExplosion(
                        center = impactPoint,
                        damage = cfg.explosionDamage,
                        explosionRadius = cfg.explosionRadius,
                        immuneUUIDs = immuneUUIDs,
                        debrisCountMultiplier = cfg.debrisCountMultiplier,
                        plugin = plugin
                    )
                }
            }

            warningJob.cancel()

            withContext(bukkitDispatcher) {
                target.world.getNearbyEntities(
                    target, cfg.blastRadius.toDouble(), 50.0, cfg.blastRadius.toDouble()
                ).filterIsInstance<Player>().forEach { p ->
                    p.sendActionBar(Component.empty())
                }
            }
        }
    }
}
