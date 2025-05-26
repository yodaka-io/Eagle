package io.yodaka.eagle

import events.PlayerJoin
//import io.yodaka.eagle.game.GameManager
//import io.yodaka.eagle.lobby.LobbyManager
//import io.yodaka.eagle.map.MapManager
//import io.yodaka.eagle.score.ScoreManager
import io.yodaka.eagle.team.TeamManager
//import io.yodaka.eagle.ui.UIManager
import org.bukkit.plugin.java.JavaPlugin
//import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Path

class EaglePlugin : JavaPlugin() {
    
//    lateinit var gameManager: GameManager
//        private set
    lateinit var teamManager: TeamManager
        private set
//    lateinit var mapManager: MapManager
//        private set
//    lateinit var lobbyManager: LobbyManager
//        private set
//    lateinit var scoreManager: ScoreManager
//        private set
//    lateinit var uiManager: UIManager
//        private set
    
    companion object {
        lateinit var instance: EaglePlugin
            private set
    }
    
    override fun onEnable() {
        println("it works!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
        instance = this
        
        // 設定ファイルの保存
        saveDefaultConfig()
        saveResource("lang.yml", false)
        
        // マネージャーの初期化
        initializeManagers()
        
        // イベントリスナーの登録
        registerEvents()
        
        // コマンドの登録
        registerCommands()
        
        logger.info("Eagle プラグインが有効になりました！")
    }
    
    override fun onDisable() {
//        gameManager.shutdown()
        logger.info("Eagle プラグインが無効になりました。")
    }
    
    private fun initializeManagers() {
        teamManager = TeamManager(this)
//        mapManager = MapManager(this)
//        scoreManager = ScoreManager(this)
//        uiManager = UIManager(this)
//        lobbyManager = LobbyManager(this)
//        gameManager = GameManager(this)
    }
    
    private fun registerEvents() {
        val pluginManager = server.pluginManager
//        pluginManager.registerEvents(gameManager, this)
        pluginManager.registerEvents(teamManager, this)
//        pluginManager.registerEvents(lobbyManager, this)
//        pluginManager.registerEvents(scoreManager, this)
    }
    
    private fun registerCommands() {
        // コマンド登録は後で実装
    }
} 