# Design: complete-on-playback-end

## Context

再生完了(=自動視聴済み化)のトリガーは現在3つある:

1. 1秒ポーリング(`PlaybackService.persistProgress`)— 残り10秒を切ったら `played=true` にし、`pendingDeletion` に積む(位置ベース・早期判定)
2. `onMediaItemTransition(reason=AUTO)` — 鳴り切って次のエピソードへ自動遷移した(イベントベース)
3. `onPlaybackStateChanged(STATE_ENDED)` — キューの最後まで鳴り切った(イベントベース)

①だけが閾値 `ListeningRules.COMPLETION_THRESHOLD_MS`(10秒)を使う。また①が「再生中のファイルを掴んだまま削除する」状況を作るため、削除を遅延させる `pendingDeletion` 機構が存在する。

一方、③の後 ExoPlayer はプレイリストを保持したまま `STATE_ENDED` で停止し、`currentMediaItem` が最後のエピソードを指し続ける。そのため `PlaybackStatus.episodeId` が非 null のまま残り、UI は聴き終えたエピソードを「現在のエピソード」として扱い続ける(行の再生/一時停止トグルがダウンロードアイコンを覆い隠す・ミニプレイヤーが常駐する)。この状態でトグルをタップしても `STATE_ENDED` のプレイヤーでは何も鳴らない。

設計ツリーはグリルセッションで全決定済み。この文書はその結果の記録である。

## Goals / Non-Goals

**Goals:**

- 再生完了の定義を「プレイヤーが実際に鳴り終えたイベント」(上記②③)に一本化する
- キューが尽きたらプレイヤーを空に戻し、「現在のエピソードが存在しない」状態へ確実に遷移させる
- 上記により、聴き終えたエピソードの再DL→聴き直しの経路を回復する(UI・データ層は無変更で成立させる)

**Non-Goals:**

- 「前へ」操作で削除済みエピソードに戻るとエラーになる既存の穴の修正(現行と同挙動・退行なし。必要になったら別チェンジ)
- 自動継続再生の仕様変更(リスト順・未DLスキップはそのまま)
- 再ダウンロード機能の新規実装(既存機能が健在であることを調査で確認済み。問題はボタンの隠蔽だった)
- UI 層・データ層・DBスキーマの変更

## Decisions

### D1. 完了判定はイベント一本化(位置ベース早期判定の廃止)

`persistProgress` から完了判定を取り除き、位置保存専任にする。完了は `onMediaItemTransition(AUTO)` と `STATE_ENDED` のみで確定する。

- 理由: 実際の利用は「必ず最後まで聴く」であり、10秒の先回りは不要。イベントは Media3 の契約上取りこぼしがなく、`durationMs` 不明のフィードでも判定が効くようになる(位置判定は長さ不明時に無効だった)
- 代替案(却下): 閾値を 0 にして仕組みを残す — ポーリング判定が実質発火しないデッドコードになる

### D2. `STATE_ENDED` で `stop()` + `clearMediaItems()`

キュー末尾の完了処理(視聴済み化)を行った後、同じコールバック内で同期的に `player.stop()` → `player.clearMediaItems()` を呼ぶ。

- `stop()` が読み込み中のメディア(開いているファイル)を解放し、`clearMediaItems()` で `currentMediaItem` が null になる → `PlaybackStatus.episodeId` が null → ミニプレイヤー非表示・行のアイコン復帰が既存の分岐で成立する
- メディア通知も消える。既存の `onTaskRemoved` は `mediaItemCount == 0` でサービスを止める判定を持っており、クリア後はタスク除去時に自然に終了する
- ファイル削除はコルーチン(`serviceScope.launch`)で行われるため、リスナー内の同期的な stop/clear が必ず削除より先に実行される(Main ディスパッチャの実行順序)
- 代替案(却下): `clearMediaItems()` のみ — ファイル解放のタイミングが内部実装任せになる。`stopSelf()` 併用 — UI が MediaController で接続中にセッションを壊し、再接続経路が複雑化する
- 代替案(却下): `PlaybackStatus` に ENDED 状態を追加して UI 側で分岐 — 状態が増え、ミニプレイヤー・行・詳細画面に分岐が散る。プレイヤーを空に戻せば既存の null 分岐がすべて正しく働く

### D3. `pendingDeletion` 機構の削除

早期判定がなくなると、完了が確定する時点(AUTO 遷移・ENDED)ではそのファイルはもう再生されていないため、削除を遅延させる理由が消える。`markPlaybackCompleted` による即時削除(視聴済み化+`deleteDownloadsIfEligible`)に一本化する。

- 補強事実: Android(Linux)では開いているファイルの unlink は安全(読み手は最後まで読める)。また現行コードも AUTO 遷移のコールバック内で前エピソードのファイルを削除しており、タイミングは変わらない
- `flushPendingDeletion` も削除対象

### D4. `currentEpisodeId` の書き込みを `onMediaItemTransition` に一本化

このフィールドの存在理由は「AUTO 遷移時に『前のエピソード』を知る」こと(コールバックは新しいアイテムしか渡さない)。`onMediaItemTransition` は全切り替え(PLAYLIST_CHANGED / AUTO / SEEK / クリア)で必ず発火するため、ここだけで追跡が完全。ポーリング側の毎秒の再代入(冗長防御)は削除する。

- 結果として責務が一直線になる: リスナー=「誰が」鳴っているか、ポーリング=「どこまで」鳴ったか

### D5. `ListeningRules` の縮小

`COMPLETION_THRESHOLD_MS` と `isPlaybackComplete` を削除する(デッドコードを残さない)。`shouldDeleteDownload` / `resumePositionMs` / `countUnplayed` は変更なし。対応するテスト5件(`ListeningRulesTest` の完了判定系)も削除する。テスト済みの行を関数ごと消すため Kover(logic/ 90%)は下がらない。

### D6. 検証は `./gradlew check` + 実機手順書

`player/` 層は薄いグルーでカバレッジを追わない方針(CLAUDE.md)。実機での確認手順を tasks.md に明記する。計装テストの追加はしない(音源再生の完了待ちが必要になり遅く壊れやすい。この規模では儀式)。

## Risks / Trade-offs

- [終端の数秒前で止めて放置すると未聴のまま残る] → 受容する。再開して鳴り終えれば視聴済みになり、手動チェックでも対応できる。「必ず最後まで聴く」運用では実害がない。なお早期判定の廃止により「未聴のみフィルター中に、まだ再生中の行がリストから消える」現行の違和感は解消される
- [「前へ」で削除済みエピソードへ戻るとエラー] → スコープ外(現行と同挙動・退行なし)。頻度が上がるようなら別チェンジで対応
- [`STATE_ENDED` 内の player 操作と完了処理の順序ミス] → リスナー内で「完了対象の ID を読む → 視聴済み化・削除をコルーチンに投げる → stop/clear」の順に同期的に書く。実装時に順序をテスト観点として実機手順書に含める

## Migration Plan

通常の `./gradlew installDebug`(上書きインストール)のみ。DBスキーマ変更なし・データ移行なし。ロールバックは前コミットのビルドを入れ直すだけ。

## Open Questions

なし(グリルセッションで全決定済み)。
