package io.yodaka.eagle.game

enum class GameState {
    LOBBY,          // ロビー待機中
    COUNTDOWN,      // カウントダウン中
    ACTIVE,         // ゲーム進行中
    ENDING,         // ゲーム終了処理中
    TRANSITIONING   // 次のマップへ移行中
}

data class GameSession(
    val mapId: String,
    val startTime: Long,
    val timeLimit: Int, // 秒
    var state: GameState = GameState.LOBBY
) {
    fun getRemainingTime(): Int {
        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        return maxOf(0, timeLimit - elapsed.toInt())
    }
    
    fun isTimeUp(): Boolean = getRemainingTime() <= 0
} 