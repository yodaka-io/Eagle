package io.yodaka.eagle.team

import io.yodaka.eagle.EaglePlugin
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*

class TeamManager(private val plugin: EaglePlugin) : Listener {
    
    private val teams = mutableMapOf<String, Team>()
    private val playerTeams = mutableMapOf<UUID, String>()
    
    init {
        // デフォルトチームの作成
        createDefaultTeams()
    }
    
    private fun createDefaultTeams() {
        registerTeam(Team("red", "Red Team", NamedTextColor.RED))
        registerTeam(Team("blue", "Blue Team", NamedTextColor.BLUE))
        registerTeam(Team("green", "Green Team", NamedTextColor.GREEN))
        registerTeam(Team("yellow", "Yellow Team", NamedTextColor.YELLOW))
    }
    
    fun registerTeam(team: Team) {
        teams[team.id] = team
    }
    
    fun getTeam(id: String): Team? = teams[id]
    
    fun getPlayerTeam(player: Player): Team? {
        val teamId = playerTeams[player.uniqueId] ?: return null
        return teams[teamId]
    }
    
    fun assignPlayerToTeam(player: Player, teamId: String): Boolean {
        val team = teams[teamId] ?: return false
        
        // 既存チームから削除
        removePlayerFromTeam(player)
        
        // 新しいチームに追加
        if (team.addPlayer(player)) {
            playerTeams[player.uniqueId] = teamId
            return true
        }
        return false
    }
    
    fun autoAssignPlayer(player: Player): Boolean {
        // 最も人数の少ないチームに自動振り分け
        val availableTeam = teams.values
            .filter { !it.isFull() }
            .minByOrNull { it.size() }
            ?: return false
            
        return assignPlayerToTeam(player, availableTeam.id)
    }
    
    fun removePlayerFromTeam(player: Player) {
        val currentTeamId = playerTeams.remove(player.uniqueId)
        currentTeamId?.let { teamId ->
            teams[teamId]?.removePlayer(player)
        }
    }
    
    fun getAllTeams(): Collection<Team> = teams.values
    
    fun getActiveTeams(): List<Team> = teams.values.filter { !it.isEmpty() }
    
    fun resetAllScores() {
        teams.values.forEach { it.resetScore() }
    }
    
    fun clearAllTeams() {
        teams.values.forEach { team ->
            team.getOnlineMembers().forEach { player ->
                removePlayerFromTeam(player)
            }
        }
        playerTeams.clear()
    }
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        removePlayerFromTeam(event.player)
    }
} 