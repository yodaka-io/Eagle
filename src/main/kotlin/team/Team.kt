package io.yodaka.eagle.team

import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import java.util.*

data class Team(
    val id: String,
    val name: String,
    val color: TeamColor,
    val maxSize: Int = 16
) {
    private val members = mutableSetOf<UUID>()
    var score: Int = 0
        private set
    
    fun addPlayer(player: Player): Boolean {
        if (members.size >= maxSize) return false
        return members.add(player.uniqueId)
    }
    
    fun removePlayer(player: Player): Boolean {
        return members.remove(player.uniqueId)
    }
    
    fun getMembers(): Set<UUID> = members.toSet()
    
    fun getOnlineMembers(): List<Player> {
        return members.mapNotNull { 
            org.bukkit.Bukkit.getPlayer(it) 
        }.filter { it.isOnline }
    }
    
    fun addScore(points: Int) {
        score += points
    }
    
    fun resetScore() {
        score = 0
    }
    
    fun size(): Int = members.size
    
    fun isEmpty(): Boolean = members.isEmpty()
    
    fun isFull(): Boolean = members.size >= maxSize
} 