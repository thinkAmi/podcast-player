# Proposal: complete-on-playback-end

## Why

実機での動作確認で2つの問題が見つかった。(1) キューの最後まで聴き終えたエピソードが「現在のエピソード」のまま残り続け、行のアイコンが再生ボタンから戻らず、タップしても鳴らず、再ダウンロードにも到達できない。(2) 視聴済み判定が「残り10秒」の位置ベース早期判定であり、「必ず最後まで聴く」という実際の利用実態(エンディング途中で止めることはない)に対して不要な先回りになっている。

両者は「再生完了」の意味論を整理することで同じ構造から解消できる: 完了の定義をプレイヤーの再生イベント(実際に鳴り終わった)に一本化し、キューが尽きたらプレイヤーを空に戻す。

## What Changes

- 視聴済みの自動判定を「残り10秒への到達(1秒ポーリングでの位置判定)」から「プレイヤーが実際に鳴り終えたイベント(次エピソードへの自動遷移・キュー末尾での再生終了)」へ変更する
- キューの最後まで聴き終えたとき、プレイヤーを停止しプレイリストを空に戻す。これにより:
  - メディア通知が消える
  - ミニプレイヤーが消える
  - 聴き終えたエピソードの行が「現在のエピソード」でなくなり、ファイル削除済みならダウンロードアイコンに戻る(=再DL→聴き直しが可能になる)
- `logic/ListeningRules` から `COMPLETION_THRESHOLD_MS` と `isPlaybackComplete` を削除する(対応するテスト5件も削除)
- `PlaybackService` の `pendingDeletion`(再生中ファイルの遅延削除機構)を削除する。早期判定がなくなれば「再生中のファイルを消さないための遅延」は不要になる
- `currentEpisodeId` の更新を `onMediaItemTransition` リスナーに一本化し、1秒ポーリングは再生位置の保存専任にする

受容するトレードオフ: 終端の数秒前で止めてそのまま放置すると未聴のまま残る(再開して鳴り終えれば視聴済みになるし、手動チェックでも対応できる。「必ず最後まで聴く」運用では実害がない)。

スコープ外(既知の穴・現行と同挙動): 聴き終わって削除されたエピソードはExoPlayerのプレイリスト上に残るため、直後に通知等の「前へ」で戻ると削除済みファイルを開こうとしてエラーになる。今回の変更で退行はしない。

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `listening-status`: 「再生完了による自動視聴済み判定」の完了条件を、位置ベース(残り10秒)からイベントベース(実際に鳴り終わった)へ変更。「視聴済みファイルの自動削除」の自動判定経由シナリオも同条件に追随
- `playback`: 「リスト順の自動継続再生」の継続先がない場合の挙動を「停止する」から「プレイヤーを空に戻す(通知・ミニプレイヤーが消え、現在のエピソードが存在しない状態になる)」へ具体化

## Impact

- `player/PlaybackService.kt` — 変更の中核。ポーリングの完了判定削除、`STATE_ENDED` での `stop()` + `clearMediaItems()`、`pendingDeletion` 削除、`currentEpisodeId` 更新の一本化
- `logic/ListeningRules.kt` — `COMPLETION_THRESHOLD_MS` / `isPlaybackComplete` の削除。`shouldDeleteDownload` / `resumePositionMs` / `countUnplayed` は変更なし
- `app/src/test/.../ListeningRulesTest.kt` — `isPlaybackComplete` のテスト5件を削除。Kover(logic/ 90%)への影響なし
- `ui/` `data/` — 変更なし。`PlaybackStatus.episodeId` が null になれば既存の分岐(ミニプレイヤー非表示・行のダウンロードアイコン復帰)がそのまま正しく働く
- 依存追加なし・DBスキーマ変更なし・通信挙動の変更なし
