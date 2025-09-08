package io.yodaka.eagle.score

import io.yodaka.eagle.EaglePlugin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*

class ScoreManager(private val plugin: EaglePlugin) : Listener {
    
    private val playerStats = mutableMapOf<UUID, PlayerStats>()
    
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (!playerStats.containsKey(player.uniqueId)) {
            playerStats[player.uniqueId] = PlayerStats()
        }
    }
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // プレイヤーが退出してもスコアは保持
        // 必要に応じてデータベースに保存する処理を追加
    }
    
    fun getPlayerStats(player: Player): PlayerStats {
        return playerStats.getOrPut(player.uniqueId) { PlayerStats() }
    }
    
    fun addKill(player: Player) {
        val stats = getPlayerStats(player)
        stats.kills++
        
        // チームスコアも更新
        val team = plugin.teamManager.getPlayerTeam(player)
        team?.addScore(plugin.configManager.getKillPoints().toInt())
    }
    
    fun addDeath(player: Player) {
        val stats = getPlayerStats(player)
        stats.deaths++
    }
    
    fun addAssist(player: Player) {
        val stats = getPlayerStats(player)
        stats.assists++
        
        // アシストポイントを追加
        val team = plugin.teamManager.getPlayerTeam(player)
        team?.addScore(plugin.configManager.getAssistPoints().toInt())
    }
    
    fun addPoints(player: Player, points: Int) {
        val stats = getPlayerStats(player)
        stats.points += points
    }
    
    fun resetPlayerStats(player: Player) {
        playerStats[player.uniqueId] = PlayerStats()
    }
    
    fun resetAllStats() {
        playerStats.clear()
    }
    
    fun getTopKillers(limit: Int = 10): List<Pair<UUID, PlayerStats>> {
        return playerStats.toList()
            .sortedByDescending { it.second.kills }
            .take(limit)
    }
    
    fun getTopScorers(limit: Int = 10): List<Pair<UUID, PlayerStats>> {
        return playerStats.toList()
            .sortedByDescending { it.second.points }
            .take(limit)
    }
    
    fun getLeaderboard(): Map<String, List<Pair<String, Int>>> {
        val onlinePlayers = plugin.server.onlinePlayers
        
        val killLeaders = onlinePlayers
            .map { it.name to getPlayerStats(it).kills }
            .sortedByDescending { it.second }
            .take(5)
        
        val pointLeaders = onlinePlayers
            .map { it.name to getPlayerStats(it).points }
            .sortedByDescending { it.second }
            .take(5)
        
        return mapOf(
            "kills" to killLeaders,
            "points" to pointLeaders
        )
    }
    
    fun getGameSummary(): GameSummary {
        val allStats = playerStats.values
        return GameSummary(
            totalKills = allStats.sumOf { it.kills },
            totalDeaths = allStats.sumOf { it.deaths },
            totalAssists = allStats.sumOf { it.assists },
            totalPoints = allStats.sumOf { it.points },
            playerCount = playerStats.size
        )
    }
    
    fun exportStats(): Map<UUID, PlayerStats> {
        return playerStats.toMap()
    }
    
    fun importStats(stats: Map<UUID, PlayerStats>) {
        playerStats.clear()
        playerStats.putAll(stats)
    }
}

data class PlayerStats(
    var kills: Int = 0,
    var deaths: Int = 0,
    var assists: Int = 0,
    var points: Int = 0,
    var gamesPlayed: Int = 0,
    var gamesWon: Int = 0
) {
    fun getKDRatio(): Double {
        return if (deaths > 0) kills.toDouble() / deaths else kills.toDouble()
    }
    
    fun getWinRate(): Double {
        return if (gamesPlayed > 0) gamesWon.toDouble() / gamesPlayed else 0.0
    }
    
    fun getTotalScore(): Int {
        return points + kills * 2 + assists
    }
}

data class GameSummary(
    val totalKills: Int,
    val totalDeaths: Int,
    val totalAssists: Int,
    val totalPoints: Int,
    val playerCount: Int
) 