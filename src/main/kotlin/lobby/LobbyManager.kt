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
    
    private val minPlayers: Int
    private val maxPlayers: Int
    private val countdownDuration: Int
    private val lobbySpawnPoint: Location?
    
    init {
        val configManager = plugin.configManager
        minPlayers = configManager.getMinPlayers()
        maxPlayers = configManager.getMaxPlayers()
        countdownDuration = configManager.getCountdownTime()
        lobbySpawnPoint = configManager.getLobbySpawnPoint()
    }
    
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        
        // ゲーム中でない場合のみロビーに追加
        if (plugin.gameManager.getCurrentState() == GameState.LOBBY) {
            addPlayerToLobby(player)
        }
    }
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        removePlayerFromLobby(event.player)
    }
    
    fun addPlayerToLobby(player: Player) {
        if (lobbyPlayers.size >= maxPlayers) {
            plugin.messageManager.sendMessage(player, "lobby.lobby-full")
            return
        }
        
        lobbyPlayers.add(player.uniqueId)
        teleportToLobby(player)
        plugin.messageManager.sendWelcomeMessage(player)
        
        updateLobbyStatus()
        checkGameStart()
    }
    
    fun removePlayerFromLobby(player: Player) {
        if (lobbyPlayers.remove(player.uniqueId)) {
            updateLobbyStatus()
            
            // カウントダウン中に人数が足りなくなった場合
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
    
    private fun updateLobbyStatus() {
        val current = lobbyPlayers.size
        val required = minPlayers
        
        if (current > 0) {
            plugin.messageManager.sendWaitingPlayersMessage(current, required)
        }
    }
    
    private fun checkGameStart() {
        if (lobbyPlayers.size >= minPlayers && !isCountingDown) {
            startCountdown()
        }
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
    
    fun getMinPlayers(): Int = minPlayers
    fun getMaxPlayers(): Int = maxPlayers
    fun isCountingDown(): Boolean = isCountingDown
    fun getCountdownTime(): Int = countdownTime
} 