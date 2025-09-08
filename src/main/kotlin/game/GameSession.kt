package io.yodaka.eagle.game

import java.util.UUID

data class GameSession(
    val id: UUID = UUID.randomUUID(),
    val mapId: String,
    var state: GameState = GameState.LOBBY,
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    val players: MutableSet<UUID> = mutableSetOf(),
    val timeLimit: Int = 1800 // デフォルト30分
) {
    fun getRemainingTime(): Int {
        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        return maxOf(0, timeLimit - elapsed.toInt())
    }
    
    fun isTimeUp(): Boolean = getRemainingTime() <= 0
} 