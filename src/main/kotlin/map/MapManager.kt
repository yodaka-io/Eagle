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
        
        // サンプルマップ設定ファイルの作成
        createSampleMapConfigs()
    }
    
    private fun createSampleMapConfigs() {
        val sampleMaps = listOf("castle_wars", "bridge_battle", "capture_point")
        
        sampleMaps.forEach { mapId ->
            val mapConfigFile = File(mapsDirectory, "$mapId.yml")
            if (!mapConfigFile.exists()) {
                createSampleMapConfig(mapConfigFile, mapId)
            }
        }
    }
    
    private fun createSampleMapConfig(file: File, mapId: String) {
        val config = YamlConfiguration()
        
        config.set("id", mapId)
        config.set("name", mapId.replace("_", " ").split(" ").joinToString(" ") { 
            it.replaceFirstChar { char -> char.uppercase() } 
        })
        config.set("world-name", mapId)
        config.set("time-limit", 1800)
        
        // サンプルスポーンポイント
        config.set("spawn-points.red.0.x", 100.0)
        config.set("spawn-points.red.0.y", 64.0)
        config.set("spawn-points.red.0.z", -30.0)
        config.set("spawn-points.red.0.yaw", 0.0)
        config.set("spawn-points.red.0.pitch", 0.0)
        
        config.set("spawn-points.blue.0.x", -100.0)
        config.set("spawn-points.blue.0.y", 64.0)
        config.set("spawn-points.blue.0.z", 30.0)
        config.set("spawn-points.blue.0.yaw", 180.0)
        config.set("spawn-points.blue.0.pitch", 0.0)
        
        // サンプル勝利条件
        when (mapId) {
            "castle_wars" -> {
                config.set("objectives.0.type", "capture_flag")
                config.set("objectives.0.team", "red")
                config.set("objectives.0.location.x", 100.0)
                config.set("objectives.0.location.y", 65.0)
                config.set("objectives.0.location.z", -30.0)
            }
            "bridge_battle" -> {
                config.set("objectives.0.type", "kill_count")
                config.set("objectives.0.target", 50)
            }
            "capture_point" -> {
                config.set("objectives.0.type", "control_point")
                config.set("objectives.0.location.x", 0.0)
                config.set("objectives.0.location.y", 64.0)
                config.set("objectives.0.location.z", 0.0)
                config.set("objectives.0.radius", 5.0)
            }
        }
        
        try {
            config.save(file)
            plugin.logger.info("サンプルマップ設定ファイルを作成しました: ${file.name}")
        } catch (e: Exception) {
            plugin.logger.warning("マップ設定ファイルの作成に失敗しました: ${e.message}")
        }
    }
    
    fun loadMap(mapId: String): MapConfig? {
        val mapConfigFile = File(mapsDirectory, "$mapId.yml")
        if (!mapConfigFile.exists()) {
            plugin.logger.warning("マップ設定ファイルが見つかりません: $mapId")
            return null
        }
        
        try {
            val config = YamlConfiguration.loadConfiguration(mapConfigFile)
            return MapConfig.fromYaml(config)
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
            
            // 新しいワールドをロード
            val worldCreator = WorldCreator.name(mapConfig.worldName)
            val world = Bukkit.createWorld(worldCreator)
            
            if (world != null) {
                currentGameWorld = world
                plugin.messageManager.sendMapLoadedMessage(mapConfig.name)
                plugin.logger.info("マップ「${mapConfig.name}」を読み込みました")
                return world
            } else {
                plugin.logger.severe("ワールド「${mapConfig.worldName}」の作成に失敗しました")
                return null
            }
        } catch (e: Exception) {
            plugin.logger.severe("マップの読み込み中にエラーが発生しました: ${e.message}")
            return null
        }
    }
    
    fun unloadCurrentGameWorld() {
        currentGameWorld?.let { world ->
            // プレイヤーをロビーに移動
            world.players.forEach { player ->
                plugin.lobbyManager.teleportToLobby(player)
            }
            
            // ワールドをアンロード
            Bukkit.unloadWorld(world, false)
            currentGameWorld = null
        }
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
        currentMapIndex = (currentMapIndex + 1) % mapRotation.size
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
    
    fun getCurrentGameWorld(): World? = currentGameWorld
    
    fun isMapAvailable(mapId: String): Boolean {
        val mapConfigFile = File(mapsDirectory, "$mapId.yml")
        return mapConfigFile.exists()
    }
    
    fun initializeMapRotation() {
        if (mapRotation.isEmpty()) {
            plugin.logger.warning("マップローテーションが空です。デフォルトマップを追加します。")
            mapRotation.addAll(listOf("castle_wars", "bridge_battle", "capture_point"))
        }
        
        // 現在のマップが有効かチェック
        val currentMap = getCurrentMap()
        if (currentMap != null && !isMapAvailable(currentMap)) {
            plugin.logger.warning("現在のマップ「$currentMap」が利用できません。次のマップに移行します。")
            rotateToNextMap()
        }
        
        plugin.logger.info("マップローテーションが初期化されました: ${mapRotation.joinToString(", ")}")
    }
} 