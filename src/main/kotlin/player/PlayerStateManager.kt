package io.yodaka.eagle.player

import io.yodaka.eagle.EaglePlugin
import io.yodaka.eagle.game.GameState
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*

enum class PlayerState {
    WAITING,    // 待機中（観戦モード）
    IN_GAME,    // ゲーム参加中
    SPECTATING  // 観戦中（ゲーム終了後など）
}

class PlayerStateManager(private val plugin: EaglePlugin) : Listener {

    private val playerStates = mutableMapOf<UUID, PlayerState>()
    private val originalGameModes = mutableMapOf<UUID, GameMode>()

    /**
     * プレイヤーを待機状態に設定
     */
    fun setPlayerWaiting(player: Player) {
        // 現在のゲームモードを保存
        originalGameModes[player.uniqueId] = player.gameMode

        // 待機状態に設定
        playerStates[player.uniqueId] = PlayerState.WAITING
        applyWaitingState(player)

        plugin.logger.info("プレイヤー ${player.name} を待機状態に設定しました")
    }

    /**
     * プレイヤーをゲーム参加状態に設定
     */
    fun setPlayerInGame(player: Player) {
        playerStates[player.uniqueId] = PlayerState.IN_GAME
        applyInGameState(player)

        plugin.logger.info("プレイヤー ${player.name} をゲーム参加状態に設定しました")
    }

    /**
     * プレイヤーを観戦状態に設定
     */
    fun setPlayerSpectating(player: Player) {
        playerStates[player.uniqueId] = PlayerState.SPECTATING
        applySpectatingState(player)

        plugin.logger.info("プレイヤー ${player.name} を観戦状態に設定しました")
    }

    /**
     * 待機状態の設定を適用
     */
    private fun applyWaitingState(player: Player) {
        player.gameMode = GameMode.SPECTATOR
        player.allowFlight = true
        player.isFlying = true
        player.health = player.maxHealth
        player.foodLevel = 20
        player.saturation = 20f
        player.exhaustion = 0f

        // インベントリをクリア
        player.inventory.clear()
        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }

        // 他のプレイヤーからは見えなくする
        hidePlayerFromGamers(player)
    }

    /**
     * ゲーム参加状態の設定を適用
     */
    private fun applyInGameState(player: Player) {
        player.gameMode = GameMode.SURVIVAL
        player.allowFlight = false
        player.isFlying = false
        player.health = player.maxHealth
        player.foodLevel = 20
        player.saturation = 20f
        player.exhaustion = 0f

        // ベッドスポーンをリセット（リスポーンイベントで正しい地点が使われるように）
        player.setBedSpawnLocation(null, true)

        // インベントリをクリア（ゲーム用アイテムは別途付与）
        player.inventory.clear()
        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }

        // 他のプレイヤーから見えるようにする
        showPlayerToAll(player)
    }

    /**
     * 観戦状態の設定を適用
     */
    private fun applySpectatingState(player: Player) {
        player.gameMode = GameMode.SPECTATOR
        player.allowFlight = true
        player.isFlying = true
        player.health = player.maxHealth
        player.foodLevel = 20

        // インベントリをクリア
        player.inventory.clear()
        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }

        // 他のプレイヤーからは見えなくする
        hidePlayerFromGamers(player)
    }

    /**
     * 待機中プレイヤーをゲーム参加者から隠す
     */
    private fun hidePlayerFromGamers(spectator: Player) {
        plugin.server.onlinePlayers.forEach { other ->
            if (other != spectator && getPlayerState(other) == PlayerState.IN_GAME) {
                other.hidePlayer(plugin, spectator)
            }
        }
    }

    /**
     * プレイヤーを全員から見えるようにする
     */
    private fun showPlayerToAll(player: Player) {
        plugin.server.onlinePlayers.forEach { other ->
            if (other != player) {
                other.showPlayer(plugin, player)
                player.showPlayer(plugin, other)
            }
        }
    }

    /**
     * プレイヤーの現在の状態を取得
     */
    fun getPlayerState(player: Player): PlayerState {
        return playerStates[player.uniqueId] ?: PlayerState.WAITING
    }

    /**
     * プレイヤーが待機中かチェック
     */
    fun isPlayerWaiting(player: Player): Boolean {
        return getPlayerState(player) == PlayerState.WAITING
    }

    /**
     * プレイヤーがゲーム中かチェック
     */
    fun isPlayerInGame(player: Player): Boolean {
        return getPlayerState(player) == PlayerState.IN_GAME
    }

    /**
     * プレイヤーが観戦中かチェック
     */
    fun isPlayerSpectating(player: Player): Boolean {
        return getPlayerState(player) == PlayerState.SPECTATING
    }

    /**
     * 全プレイヤーを待機状態に戻す
     */
    fun resetAllPlayersToWaiting() {
        plugin.server.onlinePlayers.forEach { player ->
            setPlayerWaiting(player)
        }
    }

    /**
     * ゲーム状態に応じてプレイヤー状態を調整
     */
    fun updatePlayersForGameState(gameState: GameState) {
        when (gameState) {
            GameState.LOBBY -> {
                resetAllPlayersToWaiting()
            }
            GameState.ACTIVE -> {
                // ゲーム参加者のみゲーム状態にする（チーム管理と連携）
                plugin.server.onlinePlayers.forEach { player ->
                    val team = plugin.teamManager.getPlayerTeam(player)
                    if (team != null) {
                        setPlayerInGame(player)
                    } else {
                        setPlayerSpectating(player)
                    }
                }
            }
            GameState.ENDING, GameState.TRANSITIONING -> {
                plugin.server.onlinePlayers.forEach { player ->
                    setPlayerSpectating(player)
                }
            }
            else -> {
                // その他の状態では現状維持
            }
        }
    }

    /**
     * プレイヤーのゲームモードを復元（必要に応じて）
     */
    fun restoreOriginalGameMode(player: Player) {
        val originalMode = originalGameModes[player.uniqueId]
        if (originalMode != null) {
            player.gameMode = originalMode
            originalGameModes.remove(player.uniqueId)
        }
    }

    /**
     * 待機中のプレイヤー一覧を取得
     */
    fun getWaitingPlayers(): List<Player> {
        return plugin.server.onlinePlayers.filter { isPlayerWaiting(it) }
    }

    /**
     * ゲーム参加中のプレイヤー一覧を取得
     */
    fun getInGamePlayers(): List<Player> {
        return plugin.server.onlinePlayers.filter { isPlayerInGame(it) }
    }

    /**
     * 観戦中のプレイヤー一覧を取得
     */
    fun getSpectatingPlayers(): List<Player> {
        return plugin.server.onlinePlayers.filter { isPlayerSpectating(it) }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val playerId = event.player.uniqueId
        playerStates.remove(playerId)
        originalGameModes.remove(playerId)
    }

    /**
     * 全データをクリア
     */
    fun clearAll() {
        playerStates.clear()
        originalGameModes.clear()
    }
}