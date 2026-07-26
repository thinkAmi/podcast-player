# Design: show-unplayed-count

## Context

購読一覧は `FeedDao.observeAll()` → `Flow<List<Feed>>` をそのまま表示しており、エピソード側の情報は一切合流していない。未聴数は `episodes.played` を feedId ごとに数えるだけで得られる。この設計はグリリング(設計インタビュー)で全決定を確定済みであり、本ドキュメントはその書き起こしである。

## Goals / Non-Goals

**Goals:**

- 購読一覧の各行に未聴数を表示し、「次にどの番組を聴くか」をトップページで判断できるようにする
- 視聴状態の変更が追加コードなしで一覧に反映される構造(「DB を書けば画面が追随する」)を保つ
- 「何を未聴と数えるか」の判断を `logic/` に置き、SQL との等価性をテストで担保する

**Non-Goals:**

- 並び順の変更(未聴あり番組を上にする等)。タイトル順の予測可能性は認知負荷の低さそのもの
- 行レイアウトの再設計(2行目のフィードURL表示は現状維持)
- 通知・自動更新などの能動的な仕掛け(設計思想に反する)
- 設定・オプションの追加

## Decisions

### D1: 未聴数の意味論 — played=0 の全件(保存フィルター非依存)

バッジの目的は番組をまたいだ比較なので、全番組で数字の意味が同一でなければならない。保存フィルターを加味すると「未聴はあるのにバッジが出ない番組」が生まれ、0件非表示のコントラストが嘘をつく。実装・等価テストの状態空間も単純になる。

- 却下: 保存フィルターを通した件数(「開いたときの件数と一致する」は開けば分かる情報)

### D2: 表示 — 数字をそのまま、0件は非表示、自前ピル

- 数字表示(件数が「どれを聴くか」の判断材料)。ドット表示は却下
- 未聴 0 件はバッジ自体を出さない。有無のコントラストが「新着の有無」を伝える
- 丸めなし(347 は 347)。数の大きさ自体が「すべて視聴済みで潰す」操作のトリガー情報。丸め閾値という定数を増やさない
- 描画は自前ピル: `Surface`(角丸、`secondaryContainer` 系の色)+ `Text` の数行。Material3 `Badge` はデフォルトが `error` 色(赤)で「通知・警告」の記号論を背負うため使わない。このバッジは通知ではなく在庫数

### D3: モデル — `SubscriptionListItem(feed: Feed, unplayedCount: Int)` を logic/model に新設

未聴数は番組の属性ではなく一覧表示のための派生値なので、`Feed` に生やさない(詳細画面用 `observeFeed()` との非対称を避ける)。入れ子にして `Feed` のフィールド複製(二重管理)を避ける。「購読(subscription)」はスペック名 feed-subscription にも現れるドメイン語彙であり、logic/ に置いてよい。

- 却下: `Feed` への `unplayedCount` 追加、フラットなモデル

### D4: クエリ — FeedDao に LEFT JOIN + COUNT の Flow クエリ1本

```sql
SELECT feeds.*, COUNT(episodes.id) AS unplayedCount
FROM feeds
LEFT JOIN episodes ON episodes.feedId = feeds.id AND episodes.played = 0
GROUP BY feeds.id
ORDER BY title COLLATE NOCASE ASC
```

Room の Flow は episodes テーブルの変更でも再発火するため、視聴済み操作が即座に一覧へ反映される。Room 側 POJO は `FeedWithUnplayedCount`(`@Embedded FeedEntity` + count)で `data/db` 内部に閉じる。

- 却下: ViewModel で2本の Flow を combine(JOIN 1本の方が「DB を書けば画面が追随する」に素直で部品が少ない)

### D5: Repository — `observeSubscriptionList()` を新設、`observeFeeds()` は不変

`observeFeeds()` の呼び出し元 `AdbFeedReceiverTest`(7箇所)は「フィードの列挙・件数確認」が用途であり、未聴数を運ぶのは意味の混入。返り値型を変えるとテストに `it.feed.id` のようなノイズが乗る。「全フィードの列挙」と「購読一覧画面の表示用データ」は意味の異なる別クエリとして共存させる。

### D6: 判断の置き場所 — `ListeningRules.countUnplayed`

実体は `episodes.count { !it.played }` の1行だが、存在意義は計算の再利用ではなく「SQL の COUNT と同じ意味であることの宣言」と「等価テストの参照実装」。`EpisodeFiltering` の KDoc(「Room の WHERE 句と同じ条件の宣言的表現。両者は同じ意味でなければならない」)と同じ役割を KDoc で明示する。未聴かどうかは視聴状態の判断なので `ListeningRules` が家。

- 却下: `EpisodeFiltering` への追加(あちらの責務は `EpisodeFilter` の適用)、新オブジェクト新設(関数1個に家を建てるのは儀式)

### D7: テスト範囲

| テスト | 層 | 内容 |
|---|---|---|
| `ListeningRulesTest` 追記 | JVM | `countUnplayed` の単体テスト(Kover 90% 対象) |
| 等価計装テスト | 実機 | SQL COUNT ↔ `ListeningRules.countUnplayed` の全数列挙等価(`EpisodeFilteringEquivalenceTest` と同型) |
| `FeedRow` Compose テスト | 実機 | count>0 でバッジ表示 / count=0 で非表示(`EpisodeRowTest` に倣う)。0件非表示の判定は UI の `if` にしか現れないため、この層でのみ検証可能 |

- 却下: `SubscriptionListViewModel` のテストと `FakeFeedRepository` の新設。今回の ViewModel は分岐ゼロのグルーで、既存 ViewModel にもテストはない。Fake が必要なほどの判断が ViewModel に生まれたときが作りどき

## Risks / Trade-offs

- [JOIN クエリと logic/ の定義が乖離する] → 全数列挙の等価計装テストが機械的に検出する(既存の `EpisodeFilteringEquivalenceTest` と同じ防衛線)
- [購読直後は全件未聴のため大きな数字が並ぶ] → 意図した挙動。「すべて視聴済みにする」で潰す既存運用と噛み合う。丸めない判断もこれが前提
- [`FeedRepository` インターフェースのメソッド増加] → 「列挙」「詳細」「一覧表示」は返す型が用途を語る別物であり、この規模では肥大に当たらない

## Migration Plan

DB スキーマ変更なし・依存追加なし。`./gradlew installDebug` の上書きインストールのみで、購読・視聴状態・DLファイルはすべて保持される。ロールバックは前コミットの再インストールで足りる。
