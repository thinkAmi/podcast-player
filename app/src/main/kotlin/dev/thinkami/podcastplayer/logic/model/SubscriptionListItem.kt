package dev.thinkami.podcastplayer.logic.model

/**
 * 購読一覧の1行分。
 *
 * [unplayedCount] は番組の属性ではなく一覧表示のための派生値なので、[Feed] には持たせない (詳細画面用の [Feed]
 * と非対称になるのを避ける)。未聴数は「視聴済みでない(played=0)エピソードの 全件数」であり、番組ごとに保存された絞り込み条件には依存しない。
 */
data class SubscriptionListItem(val feed: Feed, val unplayedCount: Int)
