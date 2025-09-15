package io.yodaka.eagle.world

import io.yodaka.eagle.EaglePlugin
import io.yodaka.eagle.map.MapConfig
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.concurrent.CompletableFuture

class WorldManager(private val plugin: EaglePlugin) {

    private val mapsDirectory: File
    private val tempWorldsDirectory: File
    // キーはマップのディレクトリID（ローテーション/フォルダ名）。YAML内のidではない。
    private val activeGameWorlds = mutableMapOf<String, World>()

    init {
        val configManager = plugin.configManager
        mapsDirectory = File(configManager.getMapsDirectory())
        tempWorldsDirectory = File(plugin.server.worldContainer, "temp_worlds")

        if (!mapsDirectory.exists()) {
            mapsDirectory.mkdirs()
        }
        if (!tempWorldsDirectory.exists()) {
            tempWorldsDirectory.mkdirs()
        }
    }

    /**
     * マップデータを一時的なゲーム用ワールドとして非同期で読み込む
     */
    fun loadGameWorldAsync(mapConfig: MapConfig): CompletableFuture<World?> {
        return CompletableFuture.supplyAsync {
            try {
                // 実体はフォルダ名（dirId）からコピーする
                val mapDirectory = File(mapsDirectory, mapConfig.dirId)
                if (!mapDirectory.exists()) {
                    plugin.logger.severe("マップディレクトリが見つかりません: ${mapConfig.dirId}")
                    return@supplyAsync null
                }

                val tempWorldName = "${mapConfig.worldName}_${System.currentTimeMillis()}"

                plugin.logger.info("マップ「${mapConfig.name}」をコピー中... (dir=${mapConfig.dirId}, id=${mapConfig.id})")
                copyMapToTempWorld(mapDirectory, File(tempWorldName))

                // メインスレッドでワールドを作成
                return@supplyAsync Bukkit.getScheduler().callSyncMethod(plugin) {
                    createGameWorld(tempWorldName, mapConfig)
                }.get()

            } catch (e: Exception) {
                plugin.logger.severe("マップの読み込み中にエラーが発生しました: ${e.message}")
                return@supplyAsync null
            }
        }
    }

    /**
     * マップデータを一時ワールドディレクトリにコピー
     */
    private fun copyMapToTempWorld(sourceMapDir: File, targetWorldDir: File) {
        if (targetWorldDir.exists()) {
            FileUtils.deleteDirectory(targetWorldDir)
        }

        // サーバーのワールドコンテナ内に配置する必要がある
        val serverWorldDir = File(plugin.server.worldContainer, targetWorldDir.name)
        if (serverWorldDir.exists()) {
            FileUtils.deleteDirectory(serverWorldDir)
        }

        plugin.logger.info("マップデータをコピーしています: ${sourceMapDir.absolutePath} -> ${serverWorldDir.absolutePath}")
        FileUtils.copyDirectory(sourceMapDir, serverWorldDir)

        // YMLファイルは除外（ワールドデータではないため）
        val ymlFiles = serverWorldDir.listFiles { file ->
            file.extension.lowercase() == "yml" || file.extension.lowercase() == "yaml"
        }
        ymlFiles?.forEach {
            plugin.logger.info("設定ファイルを削除: ${it.name}")
            it.delete()
        }

        plugin.logger.info("マップデータのコピーが完了しました")
    }

    /**
     * ゲーム用ワールドを作成（メインスレッド用）
     */
    private fun createGameWorld(worldName: String, mapConfig: MapConfig): World? {
        try {
            val worldCreator = WorldCreator.name(worldName)
            val world = Bukkit.createWorld(worldCreator)

            if (world != null) {
                // ワールド設定を適用
                configureGameWorld(world, mapConfig)
                // フォルダ名（dirId）でアクティブなワールドを管理する
                activeGameWorlds[mapConfig.dirId] = world

                plugin.logger.info("ゲームワールド「${world.name}」を作成しました（マップ: ${mapConfig.name}）")
                return world
            } else {
                plugin.logger.severe("ワールド「$worldName」の作成に失敗しました")
                return null
            }
        } catch (e: Exception) {
            plugin.logger.severe("ワールド作成中にエラーが発生しました: ${e.message}")
            return null
        }
    }

    /**
     * ゲームワールドの設定を適用
     */
    private fun configureGameWorld(world: World, mapConfig: MapConfig) {
        // PvP有効
        world.pvp = true

        // モンスタースポーン無効
        world.setSpawnFlags(false, true)

        // ワールドボーダー設定（一旦無効化 - マップ保護で範囲制限を行う予定）
        // val boundary = mapConfig.gameBoundary
        // val centerX = (boundary.minX + boundary.maxX) / 2
        // val centerZ = (boundary.minZ + boundary.maxZ) / 2
        // val size = maxOf(
        //     boundary.maxX - boundary.minX,
        //     boundary.maxZ - boundary.minZ
        // ) + 100.0 // 100ブロックのバッファ
        //
        // world.worldBorder.setCenter(centerX, centerZ)
        // world.worldBorder.size = size

        // 時間固定（日中）
        world.setTime(6000)
        world.setGameRuleValue("doDaylightCycle", "false")

        // 天候固定（晴れ）
        world.setStorm(false)
        world.isThundering = false
        world.setGameRuleValue("doWeatherCycle", "false")

        plugin.logger.info("ワールド設定を適用しました: ${world.name}")
    }

    /**
     * ゲームワールドをアンロードして一時ファイルを削除
     */
    fun unloadGameWorld(mapId: String) {
        // 引数はローテーションのID/フォルダ名を想定（dirId）
        val world = activeGameWorlds[mapId] ?: return

        try {
            // プレイヤーを退避
            world.players.forEach { player ->
                // プレイヤーを観戦状態にする（新しいマップが読み込まれる際に適切な場所に移動される）
                plugin.playerStateManager.setPlayerSpectating(player)

                // 一時的にデフォルトワールドに移動（すぐに新しいマップに移動される予定）
                val defaultWorld = Bukkit.getWorlds().firstOrNull()
                if (defaultWorld != null) {
                    player.teleport(defaultWorld.spawnLocation)
                    plugin.logger.info("プレイヤー ${player.name} を一時的にデフォルトワールドに移動しました")
                }
            }

            // ワールドをアンロード
            val worldName = world.name
            Bukkit.unloadWorld(world, false)

            // 一時ファイルを削除
            val tempWorldDir = File(tempWorldsDirectory, worldName)
            if (tempWorldDir.exists()) {
                CompletableFuture.runAsync {
                    try {
                        FileUtils.deleteDirectory(tempWorldDir)
                        plugin.logger.info("一時ワールドファイルを削除しました: $worldName")
                    } catch (e: Exception) {
                        plugin.logger.warning("一時ワールドファイルの削除に失敗しました: ${e.message}")
                    }
                }
            }

            activeGameWorlds.remove(mapId)
            plugin.logger.info("ゲームワールドをアンロードしました: $worldName")

        } catch (e: Exception) {
            plugin.logger.severe("ワールドのアンロード中にエラーが発生しました: ${e.message}")
        }
    }

    /**
     * すべてのゲームワールドをアンロード
     */
    fun unloadAllGameWorlds() {
        val mapIds = activeGameWorlds.keys.toList()
        mapIds.forEach { unloadGameWorld(it) }
    }

    /**
     * 内部管理から指定ワールドを除外（アンロード済みの片付け用）
     */
    fun untrackWorld(world: World) {
        val removed = activeGameWorlds.entries.removeIf { it.value == world }
        if (removed) {
            plugin.logger.info("ワールドの追跡を解除しました: ${world.name}")
        }
    }

    /**
     * 指定したマップのアクティブなワールドを取得
     */
    fun getActiveGameWorld(mapId: String): World? {
        // dirIdベースで取得
        return activeGameWorlds[mapId]
    }

    /**
     * マップが利用可能かチェック
     */
    fun isMapAvailable(mapId: String): Boolean {
        val mapDirectory = File(mapsDirectory, mapId)
        val configFile = File(mapDirectory, "map.yml")
        val levelDat = File(mapDirectory, "level.dat")

        return mapDirectory.exists() && configFile.exists() && levelDat.exists()
    }

    /**
     * 利用可能なすべてのマップIDを取得
     */
    fun getAvailableMaps(): List<String> {
        if (!mapsDirectory.exists()) return emptyList()

        return mapsDirectory.listFiles { file ->
            file.isDirectory && isMapAvailable(file.name)
        }?.map { it.name } ?: emptyList()
    }

    /**
     * 一時ワールドディレクトリをクリーンアップ
     */
    fun cleanupTempWorlds() {
        CompletableFuture.runAsync {
            try {
                if (tempWorldsDirectory.exists()) {
                    FileUtils.cleanDirectory(tempWorldsDirectory)
                    plugin.logger.info("一時ワールドディレクトリをクリーンアップしました")
                }
            } catch (e: Exception) {
                plugin.logger.warning("一時ワールドディレクトリのクリーンアップに失敗しました: ${e.message}")
            }
        }
    }
}
