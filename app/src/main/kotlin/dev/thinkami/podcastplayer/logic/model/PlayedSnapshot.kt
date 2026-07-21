package dev.thinkami.podcastplayer.logic.model

/**
 * 一括操作を取り消すための、操作前の視聴状態。
 *
 * 「すべて視聴済みにする」を取り消したとき、元から視聴済みだったものまで未聴に戻してしまわない よう、操作前の状態を1件ずつ覚えておく必要がある。
 */
data class PlayedSnapshot(val episodeId: Long, val played: Boolean)
