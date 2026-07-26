# Tasks: show-unplayed-count

## 1. logic/(判断)

- [x] 1.1 `logic/model/SubscriptionListItem.kt` を新設(`data class SubscriptionListItem(val feed: Feed, val unplayedCount: Int)`。一覧表示のための派生値であり `Feed` に混ぜない旨を KDoc に記す)
- [x] 1.2 `ListeningRules` に `countUnplayed(episodes: List<Episode>): Int` を追加(SQL の COUNT と同じ意味であることの宣言・等価テストの参照実装である旨を `EpisodeFiltering` の KDoc に倣って記す)
- [x] 1.3 `ListeningRulesTest` に `countUnplayed` のテストを追加(空リスト・全件未聴・全件視聴済み・混在)

## 2. data/(実行)

- [x] 2.1 `data/db` に Room POJO `FeedWithUnplayedCount`(`@Embedded FeedEntity` + `unplayedCount`)を追加し、`FeedDao` に LEFT JOIN + COUNT の Flow クエリ `observeAllWithUnplayedCount()` を追加(並びは既存 `observeAll()` と同じ `title COLLATE NOCASE ASC`)
- [x] 2.2 `FeedRepository` インターフェースに `observeSubscriptionList(): Flow<List<SubscriptionListItem>>` を追加し、`RoomFeedRepository` で実装(`observeFeeds()` は変更しない)
- [x] 2.3 SQL ↔ logic の全数列挙等価計装テストを追加(`EpisodeFilteringEquivalenceTest` と同型。played × downloaded の状態を全列挙した episodes を投入し、DAO クエリの unplayedCount と `ListeningRules.countUnplayed` の一致を検証。エピソード0件のフィードで COUNT が 0 になること=LEFT JOIN の検証を含める)

## 3. ui/(表示)

- [x] 3.1 `SubscriptionListViewModel` の `feeds: StateFlow<List<Feed>>` を `observeSubscriptionList()` 由来の `StateFlow<List<SubscriptionListItem>>` に差し替える
- [x] 3.2 `SubscriptionListScreen` の `FeedRow` を `SubscriptionListItem` 受け取りに変え、行末に自前ピル(`Surface` 角丸 + `secondaryContainer` 系色 + `Text`)で未聴数を表示。`unplayedCount == 0` のときはピル自体を描画しない。数字は丸めない
- [x] 3.3 `FeedRow` の Compose 計装テストを追加(`EpisodeRowTest` に倣い、count>0 で数字が表示される / count=0 でバッジが存在しない、の2ケース)

## 4. 検証

- [x] 4.1 `./gradlew check` が通ること(ktfmt / detekt / Lint / Kover 90%)
- [x] 4.2 `./gradlew connectedAndroidTest` で計装テスト(等価テスト・FeedRow テスト含む)が通ること
- [x] 4.3 `./gradlew installDebug` で実機に上書きインストールし、購読一覧に未聴数が表示されること・視聴済み操作で数字が減ること・全件視聴済みの番組にバッジが出ないことを確認する
