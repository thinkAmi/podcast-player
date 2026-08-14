# adb-episode-import Tasks

## 1. パーサーの出典宣言読み取り

- [x] 1.1 `ParsedItem` に `sourceUrl: String?` を追加し、`RssXmlReader.readItem` で item の `<source>` の url 属性を読み取る(enclosure の属性処理と同型。channel 直下ではなく item 内のみ)
- [x] 1.2 計装テスト(`data/rss`): source 付き item の読み取り・source なし item の null・複数 source は最初を採用、を既存のパーサーテストのパターンで追加する

## 2. 取り込み可否の判断(logic)

- [x] 2.1 `logic/` に取り込み検証の純粋関数を追加する。入力は「全 item の sourceUrl リストと購読 feedUrl」、出力は sealed 型(許可 / 宣言なし / 混在 / 不一致)。trim 後の文字列完全一致で判定する
- [x] 2.2 JVM ユニットテストで全分岐を網羅する(全一致 / 一部宣言なし / 全宣言なし / url 混在 / 不一致 / 空白 trim / item 0件)。Kover 90% を維持する

## 3. リポジトリの取り込み操作

- [x] 3.1 `EpisodeDao.insertIgnoringKnown` の戻り値を `List<Long>` に変更する(既存呼び出しは戻り値未使用のため互換。-1 が既知を示す)
- [x] 3.2 `FeedRepository` に `importEpisodes(feedUrl: String, importUrl: String): ImportResult`(総数・新規数)を追加し、`RoomFeedRepository` で実装する: 購読検索(未購読は失敗)→ fetch & parse → 出典検証 → `storeEpisodes`。feeds テーブルには書き込まない
- [x] 3.3 計装テスト(in-memory Room + Fake フェッチャー): 正常取り込み(件数・日付順の合流)/ 2回実行の冪等性(新規 0)/ 既存エピソードの played・downloaded・positionMs 保持 / フィードの title・artworkUrl・feedUrl 不変 / 未購読 feedUrl の失敗 / 宣言なし XML の拒否 / 別番組宣言の拒否 / 混在の拒否
- [x] 3.4 計装テスト: source 付き XML を通常の `subscribe` で登録した場合に従来どおり成功する(出典検証は import 経路のみ)

## 4. Receiver の取り込みモード

- [x] 4.1 `AdbFeedReceiver` に `EXTRA_IMPORT_URL`(`import_url`)と `RESULT_IMPORTED = 4` を追加し、`import_url` が非空白のとき取り込みモードで処理する(空白のみ・欠落は従来の購読登録)。結果メッセージに取り込み総数・新規数、拒否時は理由(宣言なし / 混在 / 不一致 / 未購読)と両辺の URL を含める
- [x] 4.2 既存の subscribe 経路の挙動が不変であることをテストで確認する(`feed_url` のみの broadcast)

## 5. 品質ゲートと配置

- [x] 5.1 `./gradlew check` を通す(ktfmt / detekt / lint / Kover)
- [x] 5.2 `./gradlew connectedAndroidTest` を実機で通す(`.instrumented` 別パッケージ。本番データに影響なし)
- [x] 5.3 【利用者作業】design.md 付録 A の `scripts/import-episodes.sh` を配置し、実行権限を付与する(`scripts/**` は Edit deny のため AI は配置できない)
- [x] 5.4 【利用者作業】design.md 付録 B の `.claude/skills/import-podcast-episodes/SKILL.md` を配置する(`.claude/**` は Edit deny のため AI は配置できない)
- [x] 5.5 CLAUDE.md を更新する: 「PC からの購読登録」の段落に取り込みスクリプトとスキルを追記し、「視聴済み判定の残り10秒など」の例示を再生完了イベント基準の記述に修正する

## 6. 実機での受け入れ確認

- [x] 6.1 `./gradlew installDebug` で本番アプリを上書きインストールする(購読・状態は保持される)
- [x] 6.2 スキルの手順どおり実行前確認(取り込み先・出典宣言・件数の提示と承認)を経て、アーカイブ XML を本番の「セキュリティのアレ」購読へ取り込む(スクリプト実行は利用者の許可プロンプトを伴う)
- [x] 6.3 実機で確認する: エピソード一覧の最下部に旧シリーズ〜第14回が公開日時順で出現 / 番組名・アートワーク不変 / 既存回の視聴状態不変。DB を read-only で吸い出して総件数(300 + 42)と取り込み内容を確認する
- [x] 6.4 ネガティブ確認: 別番組の購読フィード URL を第1引数に指定して実行し、不一致として拒否されエピソードが増えないことを実測する
