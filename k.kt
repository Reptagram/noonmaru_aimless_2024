package org.enteras.projectlostar.lostarplugin

import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Arrow
import org.bukkit.entity.Damageable
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.cos
import kotlin.math.sin

class TerminatorListener(private val plugin: Main) : Listener {
    private val playerHitCount = mutableMapOf<UUID, Int>()
    private val salvationCooldown = HashMap<UUID, Long>()
    private val shotCooldown = mutableMapOf<UUID, Long>()
    private val salvationActive = mutableMapOf<UUID, Boolean>()
    private val salvationStackTimer = mutableMapOf<UUID, BukkitTask?>()

    init {
        // Initialize timers for salvation stack decrement
        Bukkit.getServer().scheduler.runTaskTimer(plugin, object : Runnable {
            override fun run() {
                for ((playerId, hits) in playerHitCount) {
                    if (hits > 0) {
                        playerHitCount[playerId] = hits - 1
                        val player = Bukkit.getPlayer(playerId)
                        if (player != null) {
                            updateActionBar(player)
                        }
                    }
                }
            }
        }, 200L, 200L)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand

        if (item != null && item.hasItemMeta()) {
            val meta = item.itemMeta
            if (meta.hasDisplayName() && meta.displayName.equals(
                    "Terminator",
                    ignoreCase = true
                ) && item.type == Material.BOW
            ) {
                if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) {
                    // Cancel default bow shooting mechanism
                    event.isCancelled = true

                    if (canUseSalvation(player)) {
                        player.world.playSound(player.location, Sound.ENTITY_ARROW_SHOOT, 1f, 1.0f)
                        fireArrow(player, 0.0)
                        fireArrow(player, 5.0)
                        fireArrow(player, -5.0)
                    }
                } else if (event.action == Action.LEFT_CLICK_AIR || event.action == Action.LEFT_CLICK_BLOCK) {
                    if (salvationActive.getOrDefault(player.uniqueId, false)) {
                        if (playerHitCount[player.uniqueId] == 3) {
                            // Cast Salvation ability if in T3 state
                            castSalvation(player)
                        }
                    }
                }
            }
        }

        // Update Salvation indicator regardless of interaction action
        updateActionBar(player)
    }

    private fun fireArrow(player: Player, angle: Double) {
        val direction = player.location.direction
        val yaw = Math.toRadians(angle)
        val newDirection = Vector(
            direction.x * cos(yaw) - direction.z * sin(yaw),
            direction.y,
            direction.x * sin(yaw) + direction.z * cos(yaw)
        )

        val arrow = player.launchProjectile(Arrow::class.java)
        arrow.velocity = newDirection.multiply(2.0) // Adjust speed as needed
    }

    private fun castSalvation(player: Player) {
        val loc = player.location
        val direction = loc.direction.normalize()
        val range = 40.0

        // Increase the loop count and add finer granularity for denser particles
        val particleDensity = 0.5
        val particlesPerMeter = 1
        val totalParticles = (range * particlesPerMeter).toInt() * 2

        for (i in 0..totalParticles) {
            val offset = i * particleDensity / particlesPerMeter
            val targetLoc = loc.clone().add(direction.clone().multiply(offset))

            // Spawn lava drip particles
            player.world.spawnParticle(Particle.DRIP_LAVA, targetLoc.clone().add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0)

            // Spawn red dust particles
            val dustOptions = Particle.DustOptions(Color.RED, 1.0f)
            player.world.spawnParticle(
                Particle.REDSTONE,
                targetLoc.clone().add(0.0, 1.0, 0.0),
                1,
                0.1,
                0.1,
                0.1,
                0.0,
                dustOptions
            )

            val entities = targetLoc.getNearbyEntities(0.5, 0.5, 0.5)
            for (entity in entities) {
                if (entity is Damageable && entity != player) {
                    entity.damage(100.0, player)
                }
            }
        }

        playerHitCount[player.uniqueId] = 3 // Reset the hit count to T3
        startSalvationStackTimer(player)
        //player.sendMessage("${ChatColor.GREEN}Salvation activated!")
    }

    private fun startSalvationStackTimer(player: Player) {
        salvationStackTimer[player.uniqueId]?.cancel()
        salvationStackTimer[player.uniqueId] = Bukkit.getServer().scheduler.runTaskTimer(plugin, object : Runnable {
            override fun run() {
                val hits = playerHitCount[player.uniqueId] ?: 0
                if (hits > 0) {
                    playerHitCount[player.uniqueId] = hits - 1
                    updateActionBar(player)
                }
            }
        }, 200L, 200L)
    }

    private fun canUseSalvation(player: Player): Boolean {
        val playerId = player.uniqueId
        val lastUseTime = salvationCooldown[playerId] ?: 0L
        val currentTime = System.currentTimeMillis()
        val cooldownMillis = 250L // 0.25 seconds cooldown

        if (currentTime - lastUseTime >= cooldownMillis) {
            salvationCooldown[playerId] = currentTime
            return true
        }
        return false
    }

    @EventHandler
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        if (event.damager is Arrow && event.entity is Damageable) {
            val arrow = event.damager as Arrow
            val shooter = arrow.shooter

            if (shooter is Player) {
                val player = shooter as Player
                val item = player.inventory.itemInMainHand

                if (item != null && item.hasItemMeta()) {
                    val meta = item.itemMeta
                    if (meta.hasDisplayName() && meta.displayName.equals(
                            "Terminator",
                            ignoreCase = true
                        ) && item.type == Material.BOW
                    ) {
                        val playerId = player.uniqueId
                        if (playerHitCount[playerId] != 3) {
                            playerHitCount[playerId] = (playerHitCount[playerId] ?: 0) + 1 // Increment hit count by 1
                            if (playerHitCount[playerId]!! > 3) {
                                playerHitCount[playerId] = 3
                            }
                            startSalvationStackTimer(player)
                        }
                        updateActionBar(player)
                    }
                }
            }
        }
    }

    private fun updateActionBar(player: Player) {
        val hits = playerHitCount[player.uniqueId] ?: 0
        val indicator = when (hits) {
            0 -> null // Return null for T0 to hide the indicator
            1 -> "${ChatColor.RED}T1"
            2 -> "${ChatColor.GOLD}T2"
            3 -> "${ChatColor.GREEN}${ChatColor.BOLD}T3!"
            else -> "" // Handle other cases if needed
        }

        if (indicator != null) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(indicator))
        } else {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(""))
        }

        // Update salvation active state
        salvationActive[player.uniqueId] = hits == 3
    }
}
