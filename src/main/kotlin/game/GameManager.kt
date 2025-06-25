package io.yodaka.eagle.game

import io.yodaka.eagle.EaglePlugin
import io.yodaka.eagle.map.MapConfig
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitTask
import java.util.*

class GameManager(private val plugin: EaglePlugin) : Listener {
    
    private var currentSession: GameSession? = null
    private var currentMapConfig: MapConfig? = null
    private var gameTask: BukkitTask? = null
    private var countdownTask: BukkitTask? = null
    private val gamePlayers = mutableSetOf<UUID>()
    
    fun getCurrentState(): GameState {
        return currentSession?.state ?: GameState.LOBBY
    }
    
    fun startGame(players: List<Player>) {
        if (getCurrentState() != GameState.LOBBY) {
            plugin.logger.warning("ゲームが既に進行中です")
            return
        }
        
        // 現在のマップを取得
        val currentMapId = plugin.mapManager.getCurrentMap()
        if (currentMapId == null) {
            plugin.logger.severe("利用可能なマップがありません")
            return
        }
        
        // マップ設定を読み込み
        val mapConfig = plugin.mapManager.loadMap(currentMapId)
        if (mapConfig == null) {
            plugin.logger.severe("マップ設定の読み込みに失敗しました: $currentMapId")
            return
        }
        
        // ワールドを読み込み
        val gameWorld = plugin.mapManager.loadGameWorld(mapConfig)
        if (gameWorld == null) {
            plugin.logger.severe("ゲームワールドの読み込みに失敗しました")
            return
        }
        
        // ゲームセッションを開始
        currentSession = GameSession(
            mapId = currentMapId,
            timeLimit = mapConfig.timeLimit,
            state = GameState.COUNTDOWN
        )
        currentMapConfig = mapConfig
        
        // プレイヤーをゲームに追加
        gamePlayers.clear()
        players.forEach { player ->
            gamePlayers.add(player.uniqueId)
        }
        
        // チームに振り分け
        assignPlayersToTeams(players)
        
        // プレイヤーをスポーン地点に移動
        teleportPlayersToSpawns(players)
        
        // ゲーム開始
        startGameCountdown()
    }
    
    private fun assignPlayersToTeams(players: List<Player>) {
        // チームをリセット
        plugin.teamManager.clearAllTeams()
        
        // プレイヤーを自動振り分け
        players.forEach { player ->
            if (!plugin.teamManager.autoAssignPlayer(player)) {
                plugin.logger.warning("プレイヤー ${player.name} のチーム振り分けに失敗しました")
            }
        }
    }
    
    private fun teleportPlayersToSpawns(players: List<Player>) {
        val gameWorld = plugin.mapManager.getCurrentGameWorld() ?: return
        val mapConfig = currentMapConfig ?: return
        
        players.forEach { player ->
            val team = plugin.teamManager.getPlayerTeam(player)
            if (team != null) {
                val spawnPoint = mapConfig.getRandomSpawnPoint(team.id)
                if (spawnPoint != null) {
                    val location = spawnPoint.toLocation(gameWorld)
                    player.teleport(location)
                } else {
                    plugin.logger.warning("チーム ${team.id} のスポーンポイントが見つかりません")
                }
            }
        }
    }
    
    private fun startGameCountdown() {
        var countdown = plugin.configManager.gameSettings.getInt("countdown-seconds", 10)
        
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (countdown > 0) {
                plugin.messageManager.sendGameStartCountdownMessage(gamePlayers.toList(), countdown)
                countdown--
            } else {
                countdownTask?.cancel()
                actuallyStartGame()
            }
        }, 0L, 20L)
    }
    
    private fun actuallyStartGame() {
        currentSession?.state = GameState.ACTIVE
        plugin.messageManager.sendGameStartMessage()
        
        // ゲームタイマーを開始
        startGameTimer()
        
        // UIを更新開始
        plugin.uiManager.startGameUI()
        
        plugin.logger.info("ゲームが開始されました: ${currentMapConfig?.name}")
    }
    
    private fun startGameTimer() {
        val timeLimit = currentMapConfig?.timeLimit ?: 1800 // デフォルト30分
        
        gameTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val session = currentSession ?: return@Runnable
            val elapsedTime = (System.currentTimeMillis() - session.startTime) / 1000
            
            if (elapsedTime >= timeLimit) {
                endGame("時間切れ")
                return@Runnable
            }
            
            // 勝利条件をチェック
            checkWinConditions()
        }, 0L, 20L) // 1秒ごと
    }
    
    private fun checkWinConditions() {
        val mapConfig = currentMapConfig ?: return
        
        // 各勝利条件をチェック
        mapConfig.objectives.forEach { objective ->
            when (objective) {
                is io.yodaka.eagle.map.KillCount -> {
                    // キル数による勝利条件
                    val winningTeam = plugin.teamManager.getActiveTeams()
                        .find { it.score >= objective.target }
                    
                    if (winningTeam != null) {
                        endGame("${winningTeam.name}が${objective.target}キルを達成")
                        return
                    }
                }
                // 他の勝利条件も実装可能
                else -> {
                    // 未実装の勝利条件
                }
            }
        }
        
        // プレイヤー数チェック（1チーム以下になった場合）
        val activeTeams = plugin.teamManager.getActiveTeams().filter { !it.isEmpty() }
        if (activeTeams.size <= 1) {
            val winnerTeam = activeTeams.firstOrNull()
            if (winnerTeam != null) {
                endGame("${winnerTeam.name}が最後のチーム")
            } else {
                endGame("引き分け")
            }
        }
    }
    
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (getCurrentState() != GameState.ACTIVE) return
        
        val victim = event.entity
        val killer = victim.killer
        
        if (gamePlayers.contains(victim.uniqueId)) {
            // スコア更新
            if (killer != null && gamePlayers.contains(killer.uniqueId)) {
                val killerTeam = plugin.teamManager.getPlayerTeam(killer)
                killerTeam?.addScore(plugin.configManager.getKillPoints().toInt())
                
                plugin.messageManager.sendPlayerKillMessage(killer.name, victim.name)
                plugin.scoreManager.addKill(killer)
                plugin.scoreManager.addDeath(victim)
            } else {
                plugin.scoreManager.addDeath(victim)
            }
            
            // リスポーン処理
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                respawnPlayer(victim)
            }, 40L) // 2秒後
        }
    }
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (gamePlayers.remove(event.player.uniqueId)) {
            plugin.teamManager.removePlayerFromTeam(event.player)
            
            // プレイヤー数が少なくなりすぎた場合
            if (gamePlayers.size < 2) {
                endGame("プレイヤー不足")
            }
        }
    }
    
    private fun respawnPlayer(player: Player) {
        val team = plugin.teamManager.getPlayerTeam(player) ?: return
        val mapConfig = currentMapConfig ?: return
        val gameWorld = plugin.mapManager.getCurrentGameWorld() ?: return
        
        val spawnPoint = mapConfig.getRandomSpawnPoint(team.id)
        if (spawnPoint != null) {
            val location = spawnPoint.toLocation(gameWorld)
            player.teleport(location)
            player.health = player.maxHealth
            player.foodLevel = 20
        }
    }
    
    fun endGame(reason: String) {
        plugin.logger.info("ゲーム終了: $reason")
        currentSession?.endTime = System.currentTimeMillis()
        
        // ゲーム結果を表示
        val activeTeams = plugin.teamManager.getActiveTeams()
        plugin.uiManager.showGameResults(activeTeams)
        
        // 5秒後に次のマップに移行
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val nextMap = plugin.mapManager.rotateToNextMap()
            if (nextMap != null) {
                plugin.messageManager.sendMapTransitionMessage(nextMap)
            }
            resetGame()
        }, 100L) // 5秒 = 100 ticks
        
        currentSession?.state = GameState.ENDING
    }
    
    private fun showGameResults() {
        val activeTeams = plugin.teamManager.getActiveTeams()
        val winnerTeam = activeTeams.maxByOrNull { it.score }
        
        if (winnerTeam != null) {
            plugin.messageManager.sendGameEndMessage(winnerTeam.name)
        }
        
        // 統計情報を表示
        plugin.uiManager.showGameResults(activeTeams)
    }
    
    private fun transitionToNextMap() {
        currentSession?.state = GameState.TRANSITIONING
        
        // 次のマップを取得
        val nextMapId = plugin.mapManager.rotateToNextMap()
        if (nextMapId != null) {
            plugin.messageManager.sendNextMapMessage(nextMapId)
        }
        
        // プレイヤーをロビーに戻す
        val playersToReturn = gamePlayers.mapNotNull { Bukkit.getPlayer(it) }
        playersToReturn.forEach { player ->
            plugin.lobbyManager.addPlayerToLobby(player)
        }
        
        // ゲーム状態をリセット
        resetGame()
    }
    
    private fun resetGame() {
        currentSession = null
        currentMapConfig = null
        gamePlayers.clear()
        gameTask?.cancel()
        gameTask = null
        countdownTask?.cancel()
        countdownTask = null
        
        // チームをリセット
        plugin.teamManager.clearAllTeams()
        
        // UIを停止
        plugin.uiManager.stopGameUI()
        
        // ワールドをアンロード
        plugin.mapManager.unloadCurrentGameWorld()
    }
    
    fun forceEndGame() {
        endGame("強制終了")
    }
    
    fun getCurrentSession(): GameSession? = currentSession
    fun getCurrentMapConfig(): MapConfig? = currentMapConfig
    fun getGamePlayers(): List<Player> = gamePlayers.mapNotNull { Bukkit.getPlayer(it) }
    fun isPlayerInGame(player: Player): Boolean = gamePlayers.contains(player.uniqueId)
    
    fun shutdown() {
        gameTask?.cancel()
        countdownTask?.cancel()
        resetGame()
    }
} 