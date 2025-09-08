package io.yodaka.eagle.game

enum class GameState {
    LOBBY,          // ロビー待機中
    COUNTDOWN,      // カウントダウン中
    ACTIVE,         // ゲーム進行中
    ENDING,         // ゲーム終了処理中
    TRANSITIONING   // 次のマップへ移行中
} 