package io.yodaka.eagle.config

import io.yodaka.eagle.EaglePlugin
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration

class ConfigManager(private val plugin: EaglePlugin) {
    
    private val config: FileConfiguration = plugin.config
    
    // 設定セクションのプロパティ
    val lobbySettings: ConfigurationSection
        get() = config.getConfigurationSection("lobby") ?: config.createSection("lobby")
    
    val gameSettings: ConfigurationSection
        get() = config.getConfigurationSection("game") ?: config.createSection("game")
    
    val teamSettings: ConfigurationSection
        get() = config.getConfigurationSection("teams") ?: config.createSection("teams")
    
    val mapSettings: ConfigurationSection
        get() = config.getConfigurationSection("maps") ?: config.createSection("maps")
    
    // ロビー設定
    fun getMinPlayers(): Int = config.getInt("lobby.min-players", 4)
    fun getMaxPlayers(): Int = config.getInt("lobby.max-players", 16)
    fun getCountdownTime(): Int = config.getInt("lobby.countdown-time", 10)
    fun getLobbyWorldName(): String = config.getString("lobby.world-name", "lobby") ?: "lobby"
    
    fun getLobbySpawnPoint(): Location? {
        val world = plugin.server.getWorld(getLobbyWorldName()) ?: return null
        val x = config.getDouble("lobby.spawn-point.x", 0.0)
        val y = config.getDouble("lobby.spawn-point.y", 64.0)
        val z = config.getDouble("lobby.spawn-point.z", 0.0)
        val yaw = config.getDouble("lobby.spawn-point.yaw", 0.0).toFloat()
        val pitch = config.getDouble("lobby.spawn-point.pitch", 0.0).toFloat()
        return Location(world, x, y, z, yaw, pitch)
    }
    
    // チーム設定
    fun getTeamConfig(teamId: String): TeamConfig? {
        val section = config.getConfigurationSection("teams.$teamId") ?: return null
        return TeamConfig(
            id = teamId,
            name = section.getString("name") ?: teamId,
            color = section.getString("color") ?: "WHITE",
            maxPlayers = section.getInt("max-players", 8)
        )
    }
    
    fun getAllTeamConfigs(): List<TeamConfig> {
        val teamsSection = config.getConfigurationSection("teams") ?: return emptyList()
        return teamsSection.getKeys(false).mapNotNull { getTeamConfig(it) }
    }
    
    // マップ設定
    fun getMapRotation(): List<String> = config.getStringList("maps.rotation")
    fun getMapsDirectory(): String = config.getString("maps.maps-directory", "plugins/Eagle/maps") ?: "plugins/Eagle/maps"
    
    // ゲーム設定
    fun getDefaultTimeLimit(): Int = config.getInt("game.default-time-limit", 1800)
    fun getResultDisplayTime(): Int = config.getInt("game.result-display-time", 10)
    fun getMapTransitionTime(): Int = config.getInt("game.map-transition-time", 5)
    
    // スコア設定
    fun getKillPoints(): Double = config.getDouble("scoring.kill-points", 1.0)
    fun getAssistPoints(): Double = config.getDouble("scoring.assist-points", 0.5)
    fun getWinBonus(): Double = config.getDouble("scoring.win-bonus", 10.0)
    
    // UI設定
    fun getSidebarUpdateInterval(): Long = config.getLong("ui.sidebar-update-interval", 20)
    fun getActionBarUpdateInterval(): Long = config.getLong("ui.actionbar-update-interval", 10)
    
    // メッセージ設定
    fun getLanguage(): String = config.getString("messages.language", "ja") ?: "ja"
    fun getPrefix(): String = config.getString("messages.prefix", "&8[&cEagle&8] &r") ?: "&8[&cEagle&8] &r"
    
    // デバッグ設定
    fun isDebugEnabled(): Boolean = config.getBoolean("debug.enabled", false)
    fun isVerboseLogging(): Boolean = config.getBoolean("debug.verbose-logging", false)
}

data class TeamConfig(
    val id: String,
    val name: String,
    val color: String,
    val maxPlayers: Int
) 