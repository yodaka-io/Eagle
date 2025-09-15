package io.yodaka.eagle

import events.PlayerJoin
import io.yodaka.eagle.commands.EagleCommand
import io.yodaka.eagle.config.ConfigManager
import io.yodaka.eagle.game.GameManager
import io.yodaka.eagle.lobby.LobbyManager
import io.yodaka.eagle.map.MapManager
import io.yodaka.eagle.message.MessageManager
import io.yodaka.eagle.score.ScoreManager
import io.yodaka.eagle.team.TeamManager
import io.yodaka.eagle.ui.UIManager
import io.yodaka.eagle.world.WorldManager
import io.yodaka.eagle.player.PlayerStateManager
import io.yodaka.eagle.protection.GameProtectionManager
import org.bukkit.plugin.java.JavaPlugin

class EaglePlugin : JavaPlugin() {
    
    lateinit var configManager: ConfigManager
        private set
    lateinit var messageManager: MessageManager
        private set
    lateinit var gameManager: GameManager
        private set
    lateinit var teamManager: TeamManager
        private set
    lateinit var mapManager: MapManager
        private set
    lateinit var lobbyManager: LobbyManager
        private set
    lateinit var scoreManager: ScoreManager
        private set
    lateinit var uiManager: UIManager
        private set
    lateinit var worldManager: WorldManager
        private set
    lateinit var playerStateManager: PlayerStateManager
        private set
    lateinit var gameProtectionManager: GameProtectionManager
        private set
    
    companion object {
        lateinit var instance: EaglePlugin
            private set
    }
    
    override fun onEnable() {
        println("Eagle プラグインを開始しています...")
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
        
        logger.info("Eagle プラグインが有効になりました。")
    }
    
    override fun onDisable() {
        if (::gameManager.isInitialized) {
            gameManager.shutdown()
        }
        if (::uiManager.isInitialized) {
            uiManager.stopGameUI()
        }
        if (::worldManager.isInitialized) {
            worldManager.unloadAllGameWorlds()
            worldManager.cleanupTempWorlds()
        }
        if (::playerStateManager.isInitialized) {
            playerStateManager.clearAll()
        }
        logger.info("Eagle プラグインが無効になりました。")
    }
    
    private fun initializeManagers() {
        // 設定とメッセージマネージャーを最初に初期化
        configManager = ConfigManager(this)
        messageManager = MessageManager(this)

        // ワールド管理を早期に初期化
        worldManager = WorldManager(this)

        // プレイヤー状態管理を初期化
        playerStateManager = PlayerStateManager(this)

        // ゲーム保護システムを初期化
        gameProtectionManager = GameProtectionManager(this)

        // 他のマネージャーを初期化
        teamManager = TeamManager(this)
        mapManager = MapManager(this)
        scoreManager = ScoreManager(this)
        uiManager = UIManager(this)
        lobbyManager = LobbyManager(this)
        gameManager = GameManager(this)
        
        // マップローテーションを初期化
        mapManager.initializeMapRotation()
        
        logger.info("すべてのマネージャーが初期化されました。")
    }
    
    private fun registerEvents() {
        val pluginManager = server.pluginManager
        pluginManager.registerEvents(gameManager, this)
        pluginManager.registerEvents(teamManager, this)
        pluginManager.registerEvents(lobbyManager, this)
        pluginManager.registerEvents(scoreManager, this)
        pluginManager.registerEvents(playerStateManager, this)
        pluginManager.registerEvents(gameProtectionManager, this)
        pluginManager.registerEvents(PlayerJoin(this), this)
        
        logger.info("イベントリスナーが登録されました。")
    }
    
    private fun registerCommands() {
        val eagleCommand = EagleCommand(this)
        getCommand("eagle")?.setExecutor(eagleCommand)
        getCommand("eagle")?.tabCompleter = eagleCommand
        
        logger.info("コマンドが登録されました。")
    }
    
    override fun reloadConfig() {
        super.reloadConfig()
        if (::configManager.isInitialized) {
            configManager = ConfigManager(this)
        }
        if (::messageManager.isInitialized) {
            messageManager = MessageManager(this)
        }
        logger.info("設定ファイルが再読み込みされました。")
    }
} 