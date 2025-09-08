package io.yodaka.eagle.ui

import io.yodaka.eagle.EaglePlugin
import io.yodaka.eagle.game.GameState
import io.yodaka.eagle.team.Team
import io.yodaka.eagle.team.TeamColor
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard

class UIManager(private val plugin: EaglePlugin) {
    
    private var sidebarTask: BukkitTask? = null
    private var actionBarTask: BukkitTask? = null
    private val playerScoreboards = mutableMapOf<Player, Scoreboard>()
    
    fun startGameUI() {
        startSidebarUpdates()
        startActionBarUpdates()
    }
    
    fun stopGameUI() {
        sidebarTask?.cancel()
        actionBarTask?.cancel()
        clearAllScoreboards()
    }
    
    private fun startSidebarUpdates() {
        val updateInterval = plugin.configManager.getSidebarUpdateInterval()
        
        sidebarTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            updateSidebars()
        }, 0L, updateInterval)
    }
    
    private fun startActionBarUpdates() {
        val updateInterval = plugin.configManager.getActionBarUpdateInterval()
        
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            updateActionBars()
        }, 0L, updateInterval)
    }
    
    private fun updateSidebars() {
        val gameState = plugin.gameManager.getCurrentState()
        
        when (gameState) {
            GameState.LOBBY -> updateLobbySidebars()
            GameState.COUNTDOWN, GameState.ACTIVE -> updateGameSidebars()
            GameState.ENDING -> updateEndingSidebars()
            GameState.TRANSITIONING -> updateTransitionSidebars()
        }
    }
    
    private fun updateLobbySidebars() {
        val lobbyPlayers = plugin.lobbyManager.getLobbyPlayers()
        val currentCount = plugin.lobbyManager.getLobbyPlayerCount()
        val minPlayers = plugin.lobbyManager.getMinPlayers()
        val isCountingDown = plugin.lobbyManager.isCountingDown()
        val countdownTime = plugin.lobbyManager.getCountdownTime()
        
        lobbyPlayers.forEach { player ->
            val scoreboard = getOrCreateScoreboard(player)
            val objective = getOrCreateObjective(scoreboard, "lobby", "ロビー")
            
            clearScores(objective)
            
            objective.getScore("§e待機中のプレイヤー").score = 10
            objective.getScore("§f$currentCount/$minPlayers").score = 9
            objective.getScore("§r").score = 8
            
            if (isCountingDown) {
                objective.getScore("§aゲーム開始まで").score = 7
                objective.getScore("§f${countdownTime}秒").score = 6
            } else {
                objective.getScore("§c開始待ち").score = 7
                objective.getScore("§f最低${minPlayers}人必要").score = 6
            }
            
            player.scoreboard = scoreboard
        }
    }
    
    private fun updateGameSidebars() {
        val gamePlayers = plugin.gameManager.getGamePlayers()
        val session = plugin.gameManager.getCurrentSession()
        val teams = plugin.teamManager.getActiveTeams()
        
        gamePlayers.forEach { player ->
            val scoreboard = getOrCreateScoreboard(player)
            val objective = getOrCreateObjective(scoreboard, "game", plugin.messageManager.getMessage("ui.sidebar-title"))
            
            clearScores(objective)
            
            var line = 15
            
            // 残り時間
            if (session != null) {
                val remainingTime = session.getRemainingTime()
                val timeString = formatTime(remainingTime)
                objective.getScore("§e時間: §f$timeString").score = line--
                objective.getScore("§r").score = line--
            }
            
            // チームスコア
            teams.forEach { team ->
                val colorCode = getTeamColorCode(team)
                objective.getScore("$colorCode${team.name}: §f${team.score}").score = line--
            }
            
            objective.getScore("§r ").score = line--
            
            // プレイヤー個人統計
            val stats = plugin.scoreManager.getPlayerStats(player)
            objective.getScore("§aあなたの成績:").score = line--
            objective.getScore("§eキル: §f${stats.kills}").score = line--
            objective.getScore("§cデス: §f${stats.deaths}").score = line--
            objective.getScore("§bアシスト: §f${stats.assists}").score = line--
            
            player.scoreboard = scoreboard
        }
    }
    
    private fun updateEndingSidebars() {
        val gamePlayers = plugin.gameManager.getGamePlayers()
        val teams = plugin.teamManager.getActiveTeams()
        val winnerTeam = teams.maxByOrNull { it.score }
        
        gamePlayers.forEach { player ->
            val scoreboard = getOrCreateScoreboard(player)
            val objective = getOrCreateObjective(scoreboard, "ending", "§c§lゲーム終了")
            
            clearScores(objective)
            
            var line = 10
            
            // 勝利チーム
            if (winnerTeam != null) {
                val colorCode = getTeamColorCode(winnerTeam)
                objective.getScore("§a勝者:").score = line--
                objective.getScore("$colorCode${winnerTeam.name}").score = line--
                objective.getScore("§r").score = line--
            }
            
            // 最終スコア
            teams.forEach { team ->
                val colorCode = getTeamColorCode(team)
                objective.getScore("$colorCode${team.name}: §f${team.score}").score = line--
            }
            
            player.scoreboard = scoreboard
        }
    }
    
    private fun updateTransitionSidebars() {
        val allPlayers = plugin.server.onlinePlayers
        val nextMap = plugin.mapManager.getNextMap()
        
        allPlayers.forEach { player ->
            val scoreboard = getOrCreateScoreboard(player)
            val objective = getOrCreateObjective(scoreboard, "transition", "§e§l次のマップ")
            
            clearScores(objective)
            
            var line = 5
            
            if (nextMap != null) {
                objective.getScore("§a次のマップ:").score = line--
                objective.getScore("§f$nextMap").score = line--
            }
            
            objective.getScore("§eロビーに移動中...").score = line--
            
            player.scoreboard = scoreboard
        }
    }
    
    private fun updateActionBars() {
        val gameState = plugin.gameManager.getCurrentState()
        
        when (gameState) {
            GameState.LOBBY -> updateLobbyActionBars()
            GameState.COUNTDOWN, GameState.ACTIVE -> updateGameActionBars()
            else -> {}
        }
    }
    
    private fun updateLobbyActionBars() {
        val lobbyPlayers = plugin.lobbyManager.getLobbyPlayers()
        val minPlayers = plugin.configManager.lobbySettings.getInt("min-players", 2)
        
        val message = if (lobbyPlayers.size >= minPlayers) {
            "§aゲーム開始可能！ プレイヤー: ${lobbyPlayers.size}/$minPlayers"
        } else {
            "§cプレイヤー待機中... プレイヤー: ${lobbyPlayers.size}/$minPlayers"
        }
        
        lobbyPlayers.forEach { player ->
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(message))
        }
    }
    
    private fun updateGameActionBars() {
        val gamePlayers = plugin.gameManager.getGamePlayers()
        val session = plugin.gameManager.getCurrentSession()
        
        if (session != null) {
            val remainingTime = session.getRemainingTime()
            val timeString = formatTime(remainingTime)
            val message = "§e残り時間: §f$timeString"
            
            gamePlayers.forEach { player ->
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(message))
            }
        }
    }
    
    fun showGameResults(teams: List<Team>) {
        val allPlayers = plugin.server.onlinePlayers
        
        allPlayers.forEach { player ->
            val scoreboard = getOrCreateScoreboard(player)
            val objective = getOrCreateObjective(scoreboard, "results", "§6§lゲーム結果")
            
            clearScores(objective)
            
            var score = teams.size
            teams.sortedByDescending { it.score }.forEach { team ->
                val colorCode = getTeamColorCode(team)
                objective.getScore("$colorCode${team.name}: ${team.score}").score = score--
            }
            
            player.scoreboard = scoreboard
        }
    }
    
    private fun getOrCreateScoreboard(player: Player): Scoreboard {
        return playerScoreboards.getOrPut(player) {
            Bukkit.getScoreboardManager().newScoreboard
        }
    }
    
    private fun getOrCreateObjective(scoreboard: Scoreboard, name: String, displayName: String): Objective {
        var objective = scoreboard.getObjective(name)
        if (objective == null) {
            objective = scoreboard.registerNewObjective(name, "dummy", displayName)
            objective.displaySlot = DisplaySlot.SIDEBAR
        } else {
            objective.displayName = displayName
        }
        return objective
    }
    
    private fun clearScores(objective: Objective) {
        objective.scoreboard?.entries?.forEach { entry ->
            objective.scoreboard?.resetScores(entry)
        }
    }
    
    private fun clearAllScoreboards() {
        playerScoreboards.values.forEach { scoreboard ->
            scoreboard.objectives.forEach { objective ->
                objective.unregister()
            }
        }
        playerScoreboards.clear()
    }
    
    private fun getTeamColorCode(team: Team): String {
        return when (team.color) {
            TeamColor.RED -> "§c"
            TeamColor.BLUE -> "§9"
            TeamColor.GREEN -> "§a"
            TeamColor.YELLOW -> "§e"
            TeamColor.PURPLE -> "§5"
            TeamColor.ORANGE -> "§6"
            TeamColor.WHITE -> "§f"
            TeamColor.BLACK -> "§0"
            else -> "§7"
        }
    }
    
    private fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }
} 