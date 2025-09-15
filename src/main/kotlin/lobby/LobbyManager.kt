package io.yodaka.eagle.lobby

import io.yodaka.eagle.EaglePlugin
import io.yodaka.eagle.game.GameState
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitTask
import java.util.*

class LobbyManager(private val plugin: EaglePlugin) : Listener {
    
    private val lobbyPlayers = mutableSetOf<UUID>()
    private var countdownTask: BukkitTask? = null
    private var countdownTime: Int = 0
    private var isCountingDown = false
    
    private val countdownDuration: Int
    private val lobbySpawnPoint: Location?

    init {
        val configManager = plugin.configManager
        countdownDuration = configManager.getCountdownTime()
        lobbySpawnPoint = configManager.getLobbySpawnPoint()
    }
    
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // プレイヤーの参加処理は events.PlayerJoin で処理されるため、ここでは何もしない
        // 必要に応じて統計情報の更新などを行う
    }
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        removePlayerFromLobby(event.player)
    }
    
    fun addPlayerToLobby(player: Player, skipTeleport: Boolean = false) {
        val mapConfig = getCurrentMapConfig()
        val maxPlayers = mapConfig?.maxPlayers ?: plugin.configManager.getMaxPlayers()

        if (lobbyPlayers.size >= maxPlayers) {
            plugin.messageManager.sendMessage(player, "lobby.lobby-full")
            return
        }

        lobbyPlayers.add(player.uniqueId)

        // プレイヤーが既に適切な場所（ゲームワールド）にいる場合はテレポートをスキップ
        if (!skipTeleport && !isPlayerInGameWorld(player)) {
            teleportToLobby(player)
        }

        plugin.messageManager.sendWelcomeMessage(player)

        updateLobbyStatus()
        checkGameStart()
    }
    
    fun removePlayerFromLobby(player: Player) {
        if (lobbyPlayers.remove(player.uniqueId)) {
            updateLobbyStatus()
            
            // カウントダウン中に人数が足りなくなった場合
            val mapConfig = getCurrentMapConfig()
            val minPlayers = mapConfig?.minPlayers ?: plugin.configManager.getMinPlayers()

            if (isCountingDown && lobbyPlayers.size < minPlayers) {
                cancelCountdown()
                plugin.messageManager.sendNotEnoughPlayersMessage(minPlayers)
            }
        }
    }
    
    fun teleportToLobby(player: Player) {
        lobbySpawnPoint?.let { location ->
            player.teleport(location)
        } ?: run {
            // ロビーワールドが見つからない場合はメインワールドのスポーンに送る
            val mainWorld = Bukkit.getWorlds().firstOrNull()
            mainWorld?.let { world ->
                player.teleport(world.spawnLocation)
            }
        }
    }

    /**
     * プレイヤーが現在ゲームワールド（マップワールド）にいるかチェック
     */
    private fun isPlayerInGameWorld(player: Player): Boolean {
        val currentWorld = player.world
        val gameWorld = plugin.mapManager.getCurrentGameWorld()

        // ゲームワールドが読み込まれていて、プレイヤーがそこにいる場合
        return gameWorld != null && currentWorld.name == gameWorld.name
    }

    /**
     * 現在のマップの設定を取得
     */
    private fun getCurrentMapConfig(): io.yodaka.eagle.map.MapConfig? {
        val currentMapId = plugin.mapManager.getCurrentMap() ?: return null
        return plugin.mapManager.loadMap(currentMapId)
    }
    
    private fun updateLobbyStatus() {
        val current = lobbyPlayers.size
        val mapConfig = getCurrentMapConfig()
        val required = mapConfig?.minPlayers ?: plugin.configManager.getMinPlayers()

        if (current > 0) {
            plugin.messageManager.sendWaitingPlayersMessage(current, required)
        }
    }

    private fun checkGameStart() {
        val mapConfig = getCurrentMapConfig()
        val minPlayers = mapConfig?.minPlayers ?: plugin.configManager.getMinPlayers()

        plugin.logger.info("checkGameStart: lobbyPlayers=${lobbyPlayers.size}, minPlayers=$minPlayers, isCountingDown=$isCountingDown")

        if (lobbyPlayers.size >= minPlayers && !isCountingDown) {
            // チームバランスをチェック
            val hasBalance = hasBalancedTeams()
            plugin.logger.info("hasBalancedTeams: $hasBalance")
            if (hasBalance) {
                startCountdown()
            } else {
                plugin.logger.info("Team balance check failed - game not starting")
            }
        }
    }

    /**
     * チームバランスをチェック（ロビー段階では簡単な条件で判定）
     * ゲーム開始時に自動振り分けが行われるため、ロビー段階では最低人数のみをチェック
     */
    private fun hasBalancedTeams(): Boolean {
        val lobbyPlayersList = getLobbyPlayers()
        plugin.logger.info("hasBalancedTeams: lobbyPlayers=${lobbyPlayersList.size}")

        // 最低2人のプレイヤーがいればゲーム開始可能
        // ゲーム開始時に自動でチーム振り分けが行われるため、この段階では複雑なチェックは不要
        val result = lobbyPlayersList.size >= 2
        plugin.logger.info("hasBalancedTeams: result=$result")
        return result
    }
    
    private fun startCountdown() {
        if (isCountingDown) return
        
        isCountingDown = true
        countdownTime = countdownDuration
        
        plugin.messageManager.broadcastMessageWithoutPrefix("lobby.game-starting")
        
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (countdownTime <= 0) {
                // ゲーム開始
                startGame()
                return@Runnable
            }
            
            // カウントダウンメッセージ
            if (countdownTime <= 10 || countdownTime % 10 == 0) {
                plugin.messageManager.sendCountdownMessage(countdownTime)
            }
            
            countdownTime--
        }, 0L, 20L) // 1秒間隔
    }
    
    private fun cancelCountdown() {
        if (!isCountingDown) return
        
        isCountingDown = false
        countdownTask?.cancel()
        countdownTask = null
        
        plugin.logger.info("ゲーム開始カウントダウンがキャンセルされました")
    }
    
    private fun startGame() {
        cancelCountdown()
        
        // ロビープレイヤーをゲームに移行
        val playersToStart = lobbyPlayers.mapNotNull { Bukkit.getPlayer(it) }
        lobbyPlayers.clear()
        
        // ゲームマネージャーにゲーム開始を依頼
        plugin.gameManager.startGame(playersToStart)
    }
    
    fun getLobbyPlayers(): List<Player> {
        return lobbyPlayers.mapNotNull { Bukkit.getPlayer(it) }
    }
    
    fun getLobbyPlayerCount(): Int = lobbyPlayers.size
    
    fun isInLobby(player: Player): Boolean = lobbyPlayers.contains(player.uniqueId)
    
    fun clearLobby() {
        lobbyPlayers.clear()
        cancelCountdown()
    }
    
    fun forceStartGame() {
        if (lobbyPlayers.isNotEmpty()) {
            startGame()
        }
    }
    
    fun getMinPlayers(): Int {
        val mapConfig = getCurrentMapConfig()
        return mapConfig?.minPlayers ?: plugin.configManager.getMinPlayers()
    }

    fun getMaxPlayers(): Int {
        val mapConfig = getCurrentMapConfig()
        return mapConfig?.maxPlayers ?: plugin.configManager.getMaxPlayers()
    }
    fun isCountingDown(): Boolean = isCountingDown
    fun getCountdownTime(): Int = countdownTime
} 