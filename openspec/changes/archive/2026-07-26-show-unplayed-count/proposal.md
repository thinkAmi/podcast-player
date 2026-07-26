# Proposal: show-unplayed-count

## Why

購読一覧(トップページ)は現在タイトルとフィードURLしか表示せず、各番組を開かないと未聴エピソードの有無が分からない。購読数が多いと「新着があるのはどの番組か・何件あるか」を知るために番組を順に開いて回ることになり、次に聴く番組を選ぶという日常の操作に不要な手間がかかっている。

必要な情報(`episodes.played`)はすべて DB にあり、通信ゼロ・新しい状態ゼロで解決できる。

## What Changes

- 購読一覧の各番組行に未聴エピソード数を数字バッジで表示する
  - 値は「played=0 の全件数」。番組ごとに保存されたフィルター(未聴のみ/DL済みのみ)には依存させず、全番組で数字の意味を同一にする
  - 未聴 0 件の番組はバッジを表示しない(バッジの有無自体が「新着の有無」を伝える)
  - 数は丸めない(347 件なら 347 と表示する)
- 視聴状態の変更(個別トグル・一括変更・再生完了の自動判定)が購読一覧の数字に即時反映される(Room Flow のリアクティブ性による)
- 並び順(タイトル順)・行の2行目(フィードURL)・その他の一覧挙動は変更しない

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `listening-status`: 「購読一覧での未聴数表示」の Requirement を追加する。視聴状態の集計値の可視化であり、数える規則(何を未聴と数えるか)もこの capability の関心事に属する

## Impact

- `logic/model`: 一覧専用モデル `SubscriptionListItem(feed, unplayedCount)` を新設(`Feed` は不変)
- `logic/ListeningRules`: `countUnplayed` を追加(SQL COUNT との等価性を宣言する参照実装)
- `data/db/FeedDao`: LEFT JOIN + COUNT の Flow クエリと Room POJO `FeedWithUnplayedCount` を追加
- `data/FeedRepository` / `RoomFeedRepository`: `observeSubscriptionList()` を新設(既存 `observeFeeds()` は変更しない。`AdbFeedReceiverTest` の利用箇所に影響を出さないため)
- `ui/subscriptions`: `SubscriptionListViewModel` の流すモデルを差し替え、`FeedRow` に自前ピルのバッジを追加
- テスト: `ListeningRulesTest`(JVM)、SQL ↔ logic の全数列挙等価計装テスト、`FeedRow` の Compose 計装テストを追加
- 通信・DB スキーマ・依存ライブラリの変更なし。マイグレーション不要
