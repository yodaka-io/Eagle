package io.yodaka.eagle.message

import io.yodaka.eagle.EaglePlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID

class MessageManager(private val plugin: EaglePlugin) {
    
    private lateinit var langConfig: FileConfiguration
    private val prefix: String
    
    init {
        loadLanguageFile()
        prefix = plugin.config.getString("messages.prefix", "&8[&cEagle&8] &r") ?: "&8[&cEagle&8] &r"
    }
    
    private fun loadLanguageFile() {
        val langFile = File(plugin.dataFolder, "lang.yml")
        if (!langFile.exists()) {
            plugin.saveResource("lang.yml", false)
        }
        langConfig = YamlConfiguration.loadConfiguration(langFile)
    }
    
    fun getMessage(key: String, vararg placeholders: Pair<String, String>): String {
        var message = langConfig.getString(key, "&c未定義メッセージ: $key") ?: "&c未定義メッセージ: $key"
        
        // プレースホルダーの置換
        placeholders.forEach { (placeholder, value) ->
            message = message.replace("{$placeholder}", value)
        }
        
        return message
    }
    
    fun getComponent(key: String, vararg placeholders: Pair<String, String>): Component {
        val message = getMessage(key, *placeholders)
        return LegacyComponentSerializer.legacyAmpersand().deserialize(message)
    }
    
    fun sendMessage(player: Player, key: String, vararg placeholders: Pair<String, String>) {
        val message = prefix + getMessage(key, *placeholders)
        player.sendMessage(getComponent("", "message" to message))
    }
    
    fun sendMessageWithoutPrefix(player: Player, key: String, vararg placeholders: Pair<String, String>) {
        player.sendMessage(getComponent(key, *placeholders))
    }
    
    fun broadcastMessage(key: String, vararg placeholders: Pair<String, String>) {
        val message = prefix + getMessage(key, *placeholders)
        val component = LegacyComponentSerializer.legacyAmpersand().deserialize(message)
        plugin.server.broadcast(component)
    }
    
    fun broadcastMessageWithoutPrefix(key: String, vararg placeholders: Pair<String, String>) {
        val component = getComponent(key, *placeholders)
        plugin.server.broadcast(component)
    }
    
    // よく使用されるメッセージのヘルパーメソッド
    fun sendWelcomeMessage(player: Player) {
        sendMessage(player, "lobby.welcome")
    }
    
    fun sendTeamAssignedMessage(player: Player, teamName: String) {
        sendMessage(player, "team.assigned", "team" to teamName)
    }
    
    fun sendGameStartMessage() {
        broadcastMessage("game.started")
    }
    
    fun sendGameEndMessage(winnerTeam: String) {
        broadcastMessage("game.victory", "team" to winnerTeam)
    }
    
    fun sendTimeRemainingMessage(timeString: String) {
        broadcastMessageWithoutPrefix("game.time-remaining", "time" to timeString)
    }
    
    fun sendPlayerKillMessage(killer: String, victim: String) {
        broadcastMessageWithoutPrefix("game.player-killed", "killer" to killer, "victim" to victim)
    }
    
    fun sendPlayerAssistMessage(assister: String) {
        broadcastMessageWithoutPrefix("game.player-assisted", "assister" to assister)
    }
    
    fun sendMapLoadingMessage(mapName: String) {
        broadcastMessage("map.loading", "map" to mapName)
    }
    
    fun sendMapLoadedMessage(mapName: String) {
        broadcastMessage("map.loaded", "map" to mapName)
    }
    
    fun sendNextMapMessage(mapName: String) {
        broadcastMessage("map.next-map", "map" to mapName)
    }
    
    fun sendWaitingPlayersMessage(current: Int, required: Int) {
        broadcastMessageWithoutPrefix("lobby.waiting-players", "current" to current.toString(), "required" to required.toString())
    }
    
    fun sendCountdownMessage(time: Int) {
        broadcastMessageWithoutPrefix("lobby.countdown", "time" to time.toString())
    }
    
    fun sendNotEnoughPlayersMessage(min: Int) {
        broadcastMessage("lobby.not-enough-players", "min" to min.toString())
    }
    
    fun sendNoPermissionMessage(player: Player) {
        sendMessage(player, "general.no-permission")
    }
    
    fun sendPlayerOnlyMessage(player: Player) {
        sendMessage(player, "general.player-only")
    }
    
    fun sendUnknownCommandMessage(player: Player) {
        sendMessage(player, "general.unknown-command")
    }
    
    fun sendGameStartCountdownMessage(players: List<UUID>, countdown: Int) {
        players.mapNotNull { plugin.server.getPlayer(it) }.forEach { player ->
            sendMessage(player, "game.countdown", "time" to countdown.toString())
        }
    }
    
    fun sendMapTransitionMessage(mapName: String) {
        broadcastMessage("map.transition", "map" to mapName)
    }
} 