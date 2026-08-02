# Tasks: complete-on-playback-end

## 1. logic 層の縮小

- [x] 1.1 `ListeningRules` から `COMPLETION_THRESHOLD_MS` と `isPlaybackComplete` を削除する(`shouldDeleteDownload` / `resumePositionMs` / `countUnplayed` は変更しない)
- [x] 1.2 `ListeningRulesTest` から `isPlaybackComplete` 系のテスト5件(残り閾値ちょうど・閾値より多い・末尾まで・長さ不明・長さ0以下)を削除する

## 2. PlaybackService の改修

- [x] 2.1 `persistProgress` を位置保存専任にする: 完了判定(`isPlaybackComplete` 呼び出し・`setPlayed`)と `currentEpisodeId` への代入を削除する
- [x] 2.2 `currentEpisodeId` の書き込みを `onMediaItemTransition` の1箇所に一本化する
- [x] 2.3 `pendingDeletion` フィールドと `flushPendingDeletion` を削除し、完了処理を `completeAndDelete`(`markPlaybackCompleted` 経由の視聴済み化+即時削除)に一本化する
- [x] 2.4 `onPlaybackStateChanged(STATE_ENDED)` で、完了対象 ID の読み取り → `completeAndDelete`(コルーチン起動)→ `player.stop()` → `player.clearMediaItems()` の順に同期的に実行する(design.md D2 の順序制約)
- [x] 2.5 クラス冒頭の KDoc(遅延削除への言及)を新しい構造に合わせて更新する

## 3. 品質ゲート

- [x] 3.1 `./gradlew check` が通ることを確認する(ktfmt / detekt / Lint / Kover 90% すべて)

## 4. 実機検証(installDebug 後)

Pixel 7 Pro で実施。「セキュリティのアレ」の第310回(id=694・未聴DL済み)を主対象、第309回(id=695・
視聴済み)を自動継続の相手として一時DLして使用。検証後、両者とも元の状態へ戻した(下記「復元結果」)。

- [x] 4.1 キュー末尾のエピソードを最後まで聴き終える → 視聴済みチェックが付き、メディア通知とミニプレイヤーが消えることを確認する
  - 695 が鳴り切ったところで `played=1` / `downloaded=0` / ファイル削除。ミニプレイヤー消滅、
    通知はアクティブレコード 0 件(投稿1・削除1)。プレイヤーは 0:00/0:00 の停止状態に戻った
- [x] 4.2 聴き終えたエピソードの行がダウンロードアイコンに戻り、再ダウンロード → 先頭から再生できることを確認する(一周の回復経路)
  - 694 の行が ⬇️ + ✔ に復帰(修正前はここが ▶️ のまま固まっていた)。再DL後に再生すると
    約2.5秒の再生で `positionMs=2118` → 保存位置ではなく先頭から鳴っている
- [x] 4.3 DL済みエピソードが2件以上ある状態で自動継続再生させ、前のエピソードが視聴済みになりファイルが消えること・次のエピソードが途切れず鳴ることを確認する
  - 694 → 695 の自動遷移で 694 が `played=1` / ファイル削除、695 が途切れず再生継続
- [x] 4.4 終端の数秒前で一時停止して放置 → 未聴のままであること、再開して鳴り終えると視聴済みになることを確認する(受容したトレードオフの動作確認)
  - 再生したまま `positionMs=4242640` / `durationMs=4249000`(残り 6.36 秒)で `played=0` を確認。
    旧コードなら `4242640 >= 4249000-10000` が成立し視聴済み化+削除が起きていた地点。
    その後そのまま鳴らし切ると視聴済みになった

### 復元結果

| id | 検証前 (played, downloaded, positionMs) | 検証後 |
|---|---|---|
| 694 | 0, 1, 0 | 0, 1, **2118** |
| 695 | 1, 0, 0 | 1, 0, **3447498** |

視聴済みフラグとDLファイルは完全に復元(694.mp3 はサイズも一致、695.mp3 は元どおり存在しない)。
再生位置のみ差が残る: 694 は先頭から 2.1 秒の地点、695 は `played=1` のため
`ListeningRules.resumePositionMs` が 0 を返し実質影響なし。再生位置を 0 へ戻すには本番DBへの
直接書き込みが必要で、2秒のために取るリスクではないと判断した。

### 検証中に見つかった別件(この変更のスコープ外)

プレイヤー画面を開いたままキューを聴き終えると、空のプレイヤー(0:00/0:00・タイトルなし)が
残る。修正前は「終わったエピソードを指したまま反応しないプレイヤー」だったので退行ではないが、
一覧へ自動で戻す等の余地はある。別チェンジで扱う。

→ `close-player-when-queue-ends` で対応済み(2026-08-02 に同日アーカイブ)。
