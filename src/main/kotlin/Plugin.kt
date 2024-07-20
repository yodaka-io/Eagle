package io.yodaka.eagle

import events.PlayerJoin
import org.bukkit.plugin.java.JavaPlugin

class EaglePlugin: JavaPlugin(){
    override fun onEnable() {
        println("it works!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
        server.pluginManager.registerEvents(PlayerJoin(), this)
        logger.info("EaglePluginが有効になりました")
    }

    override fun onDisable() {
        logger.info("EaglePluginが無効になりました")
    }
}