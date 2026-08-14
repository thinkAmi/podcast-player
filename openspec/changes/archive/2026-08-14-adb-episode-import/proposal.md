# adb-episode-import

## Why

購読中の「セキュリティのアレ」は配信側の設定によりフィードに最新300件しか含まれず、それ以前の過去回(現行シリーズ第1回〜第14回・旧シリーズ、計42回)はどのプレイヤーからも後追いで取得できない。別リポジトリのツールで過去回を標準 RSS 2.0 のアーカイブ XML として再構成し Gist で配信できるようになったため、これを既存購読のエピソード一覧へ合流させる取り込み経路が必要になった。

## What Changes

- `AdbFeedReceiver` に取り込みモードを追加する。`feed_url` + `import_url` の broadcast で、既存購読へアーカイブ XML のエピソードを一度きり取り込む(`feed_url` のみの従来 broadcast は現行どおり購読登録)
- 誤注入防止のバリデーションを追加する。アーカイブ XML の全 item が持つ出典宣言(RSS 2.0 標準の `<source url>`)と、取り込み先購読の feedUrl との完全一致を検証し、宣言なし・混在・不一致は全体を拒否する
- `RssXmlReader` / `ParsedItem` に item の `<source url>` 読み取りを追加する(通常経路の挙動は不変)
- `FeedRepository` に取り込み操作を追加する。エピソードのみを取り込み、フィードのメタデータ(title / artworkUrl / feedUrl)には触れない。取り込み元 URL は永続化しない
- PC 側スクリプト `scripts/import-episodes.sh` とスキル `import-podcast-episodes` を追加する(`.claude/**` と `scripts/**` は Edit deny のため、本文は design.md に用意し配置は利用者が行う)
- CLAUDE.md に取り込み経路の記述を追記し、実装と乖離した「視聴済み判定の残り10秒」の例示を再生完了イベント基準の記述に修正する

## Capabilities

### New Capabilities

- `episode-import`: 既存購読へのエピソード取り込み(adb 経路)。出典宣言の検証、既存エピソード状態・フィードメタデータの不変性、従来の購読登録 broadcast との後方互換、PC 側スクリプトとスキルの契約を含む

### Modified Capabilities

なし。既存の `feed-subscription`(購読登録・adb 経由の購読登録)の要件はすべて現行のまま有効で、`feed_url` のみの broadcast の挙動は変更しない。`agent-permissions` も変更しない(取り込みスクリプトは意図的に allowlist へ載せず、実行ごとに利用者の許可プロンプトへ落とす)。

## Impact

- 変更するコード: `data/rss/RssXmlReader`(source 読み取り)、`logic/`(取り込み可否の純粋関数)、`data/FeedRepository` + `RoomFeedRepository`(取り込み操作)、`AdbFeedReceiver`(取り込みモード・結果コード追加)
- 追加するテスト: JVM(検証関数の網羅)、計装(パーサーの source 読み取り・リポジトリの取り込み/冪等性/不変性/拒否)
- 利用者が配置するファイル: `scripts/import-episodes.sh`、`.claude/skills/import-podcast-episodes/SKILL.md`(本文は design.md に完成形を用意)
- 依存追加なし・DB スキーマ変更なし・UI 変更なし
- 外部前提: アーカイブ XML(Gist 配信、全 item に `<source url>`、検証済み)。生成ツール(rss_maker_for_security_no_are)との契約は「全 item の source url = 取り込み先フィード URL」
