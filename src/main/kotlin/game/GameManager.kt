package io.yodaka.eagle.game

import io.yodaka.eagle.EaglePlugin
import io.yodaka.eagle.map.MapConfig
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.scheduler.BukkitTask
import java.util.*

class GameManager(private val plugin: EaglePlugin) : Listener {
    
    private var currentSession: GameSession? = null
    private var currentMapConfig: MapConfig? = null
    private var gameTask: BukkitTask? = null
    private var countdownTask: BukkitTask? = null
    private var transitionTask: BukkitTask? = null
    private val gamePlayers = mutableSetOf<UUID>()
    private var hasEnded: Boolean = false
    private var isTransitioning: Boolean = false
    
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
        
        // ゲームワールドを取得（待機中に既に読み込み済み）
        val gameWorld = plugin.mapManager.getCurrentGameWorld()
        if (gameWorld == null) {
            plugin.logger.severe("ゲームワールドが読み込まれていません")
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

        // プレイヤーをゲーム状態に変更してからスポーン地点に移動
        players.forEach { player ->
            plugin.playerStateManager.setPlayerInGame(player)
        }
        teleportPlayersToSpawns(players)

        // ゲーム開始（即座に）
        actuallyStartGame()
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
                gameTask?.cancel()
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
                        gameTask?.cancel()
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
            gameTask?.cancel()
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
            
            // リスポーン処理はPlayerRespawnEventで処理される
        }
    }
    
    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        if (getCurrentState() != GameState.ACTIVE || !gamePlayers.contains(player.uniqueId)) {
            return
        }

        // チームのリスポーン地点を設定
        val team = plugin.teamManager.getPlayerTeam(player)
        val mapConfig = currentMapConfig
        val gameWorld = plugin.mapManager.getCurrentGameWorld()

        if (team != null && mapConfig != null && gameWorld != null) {
            val spawnPoint = mapConfig.getRandomSpawnPoint(team.id)
            if (spawnPoint != null) {
                val location = spawnPoint.toLocation(gameWorld)
                event.respawnLocation = location
                plugin.logger.info("プレイヤー ${player.name} をチーム ${team.id} のリスポーン地点に設定しました")
            } else {
                plugin.logger.warning("チーム ${team.id} のリスポーン地点が見つかりません")
            }
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
        if (hasEnded) {
            plugin.logger.info("ゲーム終了処理は既に実行済みのためスキップします: $reason")
            return
        }
        hasEnded = true
        plugin.logger.info("ゲーム終了: $reason")
        currentSession?.endTime = System.currentTimeMillis()

        // 勝者/敗者/引き分けの通知
        val activeTeams = plugin.teamManager.getActiveTeams()
        val winnerTeam = when {
            activeTeams.size == 1 -> activeTeams.first()
            activeTeams.isNotEmpty() -> activeTeams.maxByOrNull { it.score }
            else -> null
        }
        if (winnerTeam != null) {
            // 全体へ勝利メッセージ
            plugin.messageManager.sendGameEndMessage(winnerTeam.name)
            // 敗者に個別メッセージ
            activeTeams.filter { it.id != winnerTeam.id }.forEach { losingTeam ->
                losingTeam.getOnlineMembers().forEach { p ->
                    plugin.messageManager.sendMessage(p, "game.defeat", "team" to losingTeam.name)
                }
            }
        } else {
            // 勝者なし: チームが残っていれば引き分け、誰もいないなら通常の終了
            if (activeTeams.isNotEmpty()) {
                plugin.messageManager.broadcastMessage("game.draw")
            } else {
                plugin.messageManager.broadcastMessage("game.ended")
            }
        }

        // ゲーム結果スコアボード
        plugin.uiManager.showGameResults(activeTeams)
        
        // 5秒後に次のマップに移行（重複スケジュール防止）
        if (transitionTask != null) {
            transitionTask?.cancel()
            transitionTask = null
        }
        transitionTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            transitionToNextMap()
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
        // 二重実行防止
        if (isTransitioning) {
            plugin.logger.info("既にマップ移行中のため処理をスキップします")
            return
        }
        isTransitioning = true

        currentSession?.state = GameState.TRANSITIONING
        plugin.logger.info("次のマップへの移行を開始します")

        // 次のマップを取得
        val nextMapId = plugin.mapManager.rotateToNextMap()
        val playersToTransition = gamePlayers.mapNotNull { Bukkit.getPlayer(it) }

        if (nextMapId != null) {
            // 次のマップがある場合
            plugin.messageManager.sendMapTransitionMessage(nextMapId)

            // ゲーム状態をリセット（ワールドアンロードなし）
            resetGameStateOnly()

            // 次のマップを読み込んで、プレイヤーを待機エリアに移動
            loadNextMapAndMovePlayersAsync(nextMapId, playersToTransition)
        } else {
            // ローテーション完了：サーバー再起動
            plugin.messageManager.broadcastMessage("map.rotation-complete")
            plugin.logger.info("全マップローテーションが完了しました。サーバーを再起動します。")

            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                Bukkit.getServer().shutdown()
            }, 60L) // 3秒後に再起動
        }
    }
    
    /**
     * ゲーム状態のみをリセット（ワールドアンロードなし）
     */
    private fun resetGameStateOnly() {
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
    }

    /**
     * 次のマップを読み込んで、プレイヤーを待機エリアに移動
     */
    private fun loadNextMapAndMovePlayersAsync(mapId: String, players: List<Player>) {
        plugin.logger.info("マップ $mapId の非同期読み込みを開始します（プレイヤー数: ${players.size}）")

        val mapConfig = plugin.mapManager.loadMap(mapId)
        if (mapConfig == null) {
            plugin.logger.severe("次のマップ設定の読み込みに失敗しました: $mapId")
            // フォールバック：ワールドをアンロードしてデフォルト処理
            plugin.mapManager.unloadCurrentGameWorld()
            return
        }

        // 非同期でマップを読み込み
        plugin.mapManager.loadGameWorldAsync(mapConfig) { world ->
            plugin.logger.info("マップ読み込み完了コールバック実行: world=${world?.name}")

            if (world != null) {
                // プレイヤーを待機エリアに移動
                val waitingLocation = mapConfig.getWaitingAreaLocation(world)
                plugin.logger.info("プレイヤーを待機エリアに移動中: ${players.size}人")

                players.forEach { player ->
                    player.teleport(waitingLocation)
                    plugin.playerStateManager.setPlayerWaiting(player)
                    plugin.logger.info("プレイヤー ${player.name} を待機エリアに移動しました")
                }

                plugin.logger.info("次のマップ「${mapConfig.name}」に移行完了")
                // 次サイクルに向けてフラグを解除
                hasEnded = false
                isTransitioning = false
                transitionTask = null
            } else {
                plugin.logger.severe("次のマップワールドの読み込みに失敗しました")
                plugin.mapManager.unloadCurrentGameWorld()
                // エラー時もフラグ解除（再試行を許可）
                isTransitioning = false
                transitionTask = null
            }
        }
    }

    private fun resetGame() {
        resetGameStateOnly()
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
