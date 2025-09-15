package events

import io.yodaka.eagle.EaglePlugin
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class PlayerJoin(private val plugin: EaglePlugin) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun playerJoin(event: PlayerJoinEvent) {
        val player: Player = event.player

        // カスタム参加メッセージ
        event.joinMessage(Component.text("${player.name} がサーバーに参加しました"))

        // 1tick後に待機エリアに移動（ワールド読み込み完了を待つ）
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            teleportToWaitingArea(player)
        }, 1L)

        plugin.logger.info("プレイヤー ${player.name} がサーバーに参加しました")
    }

    private fun teleportToWaitingArea(player: Player) {
        try {
            // 現在のマップを取得
            val currentMapId = plugin.mapManager.getCurrentMap()
            if (currentMapId == null) {
                player.sendMessage("現在利用可能なマップがありません。管理者に連絡してください。")
                plugin.logger.warning("プレイヤー ${player.name} の参加時に利用可能なマップがありませんでした")
                return
            }

            // マップ設定を読み込み
            val mapConfig = plugin.mapManager.loadMap(currentMapId)
            if (mapConfig == null) {
                player.sendMessage("マップ設定の読み込みに失敗しました。管理者に連絡してください。")
                plugin.logger.severe("マップ設定の読み込みに失敗しました: $currentMapId")
                return
            }

            // アクティブなゲームワールドを取得
            var gameWorld = plugin.worldManager.getActiveGameWorld(currentMapId)

            // ワールドが読み込まれていない場合は読み込む
            if (gameWorld == null) {
                plugin.logger.info("ゲームワールドが未読み込みのため、読み込み中...")
                plugin.worldManager.loadGameWorldAsync(mapConfig).thenAccept { world ->
                    if (world != null) {
                        // ワールド読み込み完了後に移動
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            teleportPlayerToWaiting(player, world, mapConfig)
                        })
                    } else {
                        player.sendMessage("ワールドの読み込みに失敗しました。")
                    }
                }
                return
            }

            // 既にワールドが読み込まれている場合はすぐに移動
            teleportPlayerToWaiting(player, gameWorld, mapConfig)

        } catch (e: Exception) {
            plugin.logger.severe("プレイヤー ${player.name} の待機エリア移動中にエラーが発生しました: ${e.message}")
            player.sendMessage("待機エリアへの移動中にエラーが発生しました。")
        }
    }

    private fun teleportPlayerToWaiting(player: Player, world: org.bukkit.World, mapConfig: io.yodaka.eagle.map.MapConfig) {
        // 待機エリアに移動
        val waitingLocation = mapConfig.getWaitingAreaLocation(world)
        player.teleport(waitingLocation)

        // プレイヤー状態を待機に設定
        plugin.playerStateManager.setPlayerWaiting(player)

        // ウェルカムメッセージ
        player.sendMessage("マップ「${mapConfig.name}」の待機エリアへようこそ！")
        player.sendMessage("ゲームに参加するには /eagle join コマンドを使用してください。")

        plugin.logger.info("プレイヤー ${player.name} を待機エリアに移動しました（マップ: ${mapConfig.name}）")
    }
}
