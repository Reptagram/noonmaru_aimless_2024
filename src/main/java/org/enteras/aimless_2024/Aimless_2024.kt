package org.enteras.aimless_2024

import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class Aimless_2024 : JavaPlugin() {
    override fun onEnable() {
        getLogger().info("WELCOME TO AIMLESS 2024! \nOriginal Plugin by @gakstar, 2024 Version by @RedaL_moon")
        for (world in Bukkit.getWorlds()) {
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
            world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, true)
            world.setGameRule(GameRule.SEND_COMMAND_FEEDBACK, false)
            world.setGameRule(GameRule.LOG_ADMIN_COMMANDS, false)
            world.setGameRule(GameRule.REDUCED_DEBUG_INFO, true)
        }

        Bukkit.getWorlds().first().apply {
            val border = worldBorder
            border.center = Location(this, 0.0, 0.0, 0.0)
            border.size = 20000.0
            spawnLocation = getHighestBlockAt(0, 0).location
        }

        server.pluginManager.registerEvents(EventListener(), this)
        server.scheduler.runTaskTimer(this, PlayerList, 0L , 1L)
        server.scheduler.runTaskTimer(this, Restarter(), 20L * 60L, 20L * 60L)
    }
}

@Suppress("DEPRECATION")
class Restarter: Runnable {
    private val time = System.currentTimeMillis()

    override fun run() {
        val elapsedTime = System.currentTimeMillis() - time

        val restartTime = 1000L * 60L * 60L * 2L

        if (elapsedTime >= restartTime) {
            for (player in Bukkit.getOnlinePlayers()) {
                player.sendMessage("서버가 재시작됩니다.")
            }
            Bukkit.shutdown()
        } else if (elapsedTime >= restartTime - 60000L) {
            Bukkit.broadcastMessage("1분 뒤 서버가 재시작됩니다.")
        }
    }
}