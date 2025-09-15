package io.yodaka.eagle.map

import io.yodaka.eagle.EaglePlugin
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class MapManager(private val plugin: EaglePlugin) {
    
    private val mapsDirectory: File
    private val mapRotation: MutableList<String> = mutableListOf()
    private var currentMapIndex: Int = 0
    private var currentGameWorld: World? = null
    
    init {
        val configManager = plugin.configManager
        mapsDirectory = File(configManager.getMapsDirectory())
        if (!mapsDirectory.exists()) {
            mapsDirectory.mkdirs()
        }
        
        // マップローテーションの読み込み
        mapRotation.addAll(configManager.getMapRotation())
    }
    
    fun loadMap(mapId: String): MapConfig? {
        val mapDirectory = File(mapsDirectory, mapId)
        val mapConfigFile = File(mapDirectory, "map.yml")
        if (!mapConfigFile.exists()) {
            plugin.logger.warning("マップ設定ファイルが見つかりません: $mapId/map.yml")
            return null
        }
        
        try {
            val config = YamlConfiguration.loadConfiguration(mapConfigFile)
            val parsed = MapConfig.fromYaml(config)
            // 物理マップフォルダ（ローテーションのID）をdirIdとして付与
            return parsed.copy(dirId = mapId)
        } catch (e: Exception) {
            plugin.logger.severe("マップ設定の読み込みに失敗しました: $mapId - ${e.message}")
            return null
        }
    }
    
    fun loadGameWorld(mapConfig: MapConfig): World? {
        try {
            // 既存のゲームワールドをアンロード
            unloadCurrentGameWorld()

            plugin.messageManager.sendMapLoadingMessage(mapConfig.name)

            // WorldManagerを使用して非同期でワールドを読み込み
            val future = plugin.worldManager.loadGameWorldAsync(mapConfig)
            val world = future.get() // 同期的に待機（後で非同期版も提供可能）

            if (world != null) {
                currentGameWorld = world
                plugin.messageManager.sendMapLoadedMessage(mapConfig.name)
                plugin.logger.info("マップ「${mapConfig.name}」を読み込みました")
                return world
            } else {
                plugin.logger.severe("ワールドの読み込みに失敗しました: ${mapConfig.id}")
                return null
            }
        } catch (e: Exception) {
            plugin.logger.severe("マップの読み込み中にエラーが発生しました: ${e.message}")
            return null
        }
    }

    
    fun unloadCurrentGameWorld() {
        val currentMapId = getCurrentMap()
        if (currentMapId != null) {
            // WorldManagerを使用してワールドをアンロード
            plugin.worldManager.unloadGameWorld(currentMapId)
        }
        currentGameWorld = null
    }
    
    fun getCurrentMap(): String? {
        return if (mapRotation.isNotEmpty()) mapRotation[currentMapIndex] else null
    }
    
    fun getNextMap(): String? {
        if (mapRotation.isEmpty()) return null
        val nextIndex = (currentMapIndex + 1) % mapRotation.size
        return mapRotation[nextIndex]
    }
    
    fun rotateToNextMap(): String? {
        if (mapRotation.isEmpty()) return null

        // マップが1つしかない場合は同じマップを繰り返す（サーバー再起動しない）
        if (mapRotation.size == 1) {
            plugin.logger.info("マップローテーションに1つのマップのみ存在します。同じマップを継続します。")
            return mapRotation[0]
        }

        val oldIndex = currentMapIndex
        currentMapIndex = (currentMapIndex + 1) % mapRotation.size

        // ローテーション完了をチェック（最初のマップに戻った場合）
        if (currentMapIndex == 0 && oldIndex != 0) {
            plugin.logger.info("マップローテーションが完了しました。サーバーを再起動します。")
            // ローテーション完了のシグナルとしてnullを返す
            return null
        }

        return mapRotation[currentMapIndex]
    }
    
    fun setCurrentMap(mapId: String): Boolean {
        val index = mapRotation.indexOf(mapId)
        if (index == -1) return false
        currentMapIndex = index
        return true
    }
    
    fun getMapRotation(): List<String> = mapRotation.toList()
    
    fun addMapToRotation(mapId: String) {
        if (!mapRotation.contains(mapId)) {
            mapRotation.add(mapId)
        }
    }
    
    fun removeMapFromRotation(mapId: String) {
        mapRotation.remove(mapId)
        if (currentMapIndex >= mapRotation.size) {
            currentMapIndex = 0
        }
    }
    
    fun getCurrentGameWorld(): World? {
        val currentMapId = getCurrentMap()
        return if (currentMapId != null) {
            plugin.worldManager.getActiveGameWorld(currentMapId)
        } else {
            currentGameWorld
        }
    }

    /**
     * 非同期でゲームワールドを読み込む
     */
    fun loadGameWorldAsync(mapConfig: MapConfig, callback: (World?) -> Unit) {
        // 古いワールドの参照を保存（後でアンロードする）
        val oldGameWorld = currentGameWorld

        plugin.messageManager.sendMapLoadingMessage(mapConfig.name)

        plugin.worldManager.loadGameWorldAsync(mapConfig).thenAccept { world ->
            if (world != null) {
                currentGameWorld = world
                plugin.messageManager.sendMapLoadedMessage(mapConfig.name)
                plugin.logger.info("マップ「${mapConfig.name}」を読み込みました")

                // 新しいワールドが準備できた後に、古いワールドをアンロード
                if (oldGameWorld != null && oldGameWorld != world) {
                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        unloadSpecificGameWorld(oldGameWorld)
                    }, 20L) // 1秒後にアンロード
                }

                // 以降の処理（テレポート等）は必ずメインスレッドで実行
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    callback(world)
                })
            } else {
                plugin.logger.severe("ワールドの読み込みに失敗しました: ${mapConfig.id}")
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    callback(null)
                })
            }
        }
    }
    
    /**
     * 特定のワールドをプレイヤーの移動なしでアンロード
     */
    private fun unloadSpecificGameWorld(world: World) {
        try {
            // プレイヤーがまだそのワールドにいる場合は移動しない（既に新しいワールドにいるはず）
            val playersInWorld = world.players.toList()
            if (playersInWorld.isNotEmpty()) {
                plugin.logger.info("ワールド ${world.name} に ${playersInWorld.size} 人のプレイヤーがいるためアンロードをスキップします")
                return
            }

            // ワールドをアンロード
            val worldName = world.name
            Bukkit.unloadWorld(world, false)
            plugin.logger.info("古いゲームワールドをアンロードしました: $worldName")

            // WorldManagerの追跡からも除外
            plugin.worldManager.untrackWorld(world)
        } catch (e: Exception) {
            plugin.logger.severe("ワールドのアンロード中にエラーが発生しました: ${e.message}")
        }
    }

    fun isMapAvailable(mapId: String): Boolean {
        return plugin.worldManager.isMapAvailable(mapId)
    }
    
    fun initializeMapRotation() {
        if (mapRotation.isEmpty()) {
            plugin.logger.warning("マップローテーションが空です。利用可能なマップを検索します。")
            val availableMaps = plugin.worldManager.getAvailableMaps()
            if (availableMaps.isNotEmpty()) {
                mapRotation.addAll(availableMaps)
                plugin.logger.info("利用可能なマップを自動追加しました: ${availableMaps.joinToString(", ")}")
            } else {
                plugin.logger.severe("利用可能なマップが見つかりません。mapsディレクトリを確認してください。")
                return
            }
        }

        // 利用可能でないマップをローテーションから除外
        val unavailableMaps = mapRotation.filter { !isMapAvailable(it) }
        if (unavailableMaps.isNotEmpty()) {
            plugin.logger.warning("以下のマップが利用できないためローテーションから除外します: ${unavailableMaps.joinToString(", ")}")
            mapRotation.removeAll(unavailableMaps)
        }

        // 利用可能なマップがない場合
        if (mapRotation.isEmpty()) {
            plugin.logger.severe("利用可能なマップがありません。サーバーを停止します。")
            plugin.server.shutdown()
            return
        }

        // 現在のマップインデックスを調整
        if (currentMapIndex >= mapRotation.size) {
            currentMapIndex = 0
        }

        // 現在のマップが有効かチェック
        val currentMap = getCurrentMap()
        if (currentMap != null && !isMapAvailable(currentMap)) {
            plugin.logger.warning("現在のマップ「$currentMap」が利用できません。次のマップに移行します。")
            rotateToNextMap()
        }

        plugin.logger.info("マップローテーションが初期化されました: ${mapRotation.joinToString(", ")}")

        if (mapRotation.isNotEmpty()) {
            val currentMapName = getCurrentMap()
            if (currentMapName != null) {
                plugin.logger.info("現在のマップ: $currentMapName")
            }
        }
    }
} 
