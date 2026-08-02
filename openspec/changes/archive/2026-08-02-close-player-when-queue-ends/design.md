# Design: close-player-when-queue-ends

## Context

complete-on-playback-end 以降、キュー終端では `PlaybackService` が `STATE_ENDED` を受けて
`stop()` + `clearMediaItems()` を実行する。`currentMediaItem` が null になることで
`PlaybackConnection.publishStatus()` は `PlaybackStatus(episodeId = null, ...)` を publish し、
メディア通知(Media3)とミニプレイヤー(`episode != null` の条件分岐)は自動で消える。

プレイヤー画面(`Routes.PLAYER`)だけは Navigation の backstack 上のフルスクリーン destination
であるため、この状態変化に追随する仕組みがなく、空画面(0:00/0:00・タイトルなし・無反応)が残る。

制約: 単一利用者・認知負荷最小の思想。状態やフラグを増やさず、「状態に UI が追随する」既存構造の
延長で解決したい。

## Goals / Non-Goals

**Goals:**

- プレイヤー画面を開いたままキューを聴き終えたとき、画面を自動で閉じて元の一覧へ戻す
- 再生中・再接続中・自動継続再生中に誤って閉じない(false positive ゼロ)

**Non-Goals:**

- プロセス死からの復元時に既にキューが尽きていたケースの救済(D3 で受容)
- 空状態のプレースホルダ UI(「再生を終了しました」等)の追加
- `PlaybackService` / `logic/` の変更(`PlaybackConnection` は D4 の1点のみ変更する)

## Decisions

### D1: 「episodeId の 非null → null 遷移」を閉じる合図にする

`PlayerScreen` 内の `LaunchedEffect` で `status.episodeId`(または `currentEpisode`)を監視し、
**直前値が非null かつ 現在値が null** になったら `onBack()` を呼ぶ。

- 「null なら閉じる」ではなく遷移検出にする理由:
  - 回転・プロセス復元直後は `MediaController` の再接続が非同期のため、一瞬
    `episodeId == null` の初期状態があり得る。「null なら閉じる」だと再生中に画面が閉じる誤爆になる。
    遷移検出なら直前値も null なので発火しない(実際には `release()` が status を publish しない
    ため stale な非null が残り、さらに安全側)
  - 自動継続再生では `currentMediaItem` が直接次のアイテムへ切り替わるため null を挟まず、
    遷移自体が発生しない
  - 現行コードで 非null → null が起きる経路はキュー終端の `clearMediaItems()` のみ。
    UI に「停止」操作は存在しない。つまり遷移 = キューが尽きた、と同値
- 監視の置き場所を `PlayerScreen` にする理由: NavHost は現在の destination しか compose
  しないため、「この画面が出ている間だけ監視が生きる」スコープが自然に得られる。
  `PodcastPlayerApp` に置く案は route 判定の条件が増えるだけで利点がない
- 代替案(却下): 空状態プレースホルダ表示 — 画面状態が1つ増え、結局ユーザーのタップが要る。
  認知負荷最小の思想に合わない

### D2: pop の前に二重 pop ガードを入れる

ユーザーの閉じる操作と自動クローズが競合すると、退場アニメーション中の `PlayerScreen` は
まだ composition に残っているため `LaunchedEffect` が二度目の pop を呼び、下の一覧画面まで
閉じてしまう恐れがある。pop の実行前に「現在の backstack entry がまだプレイヤー画面か」
(destination の route 確認、または entry の lifecycle が RESUMED か)を確認する。

ガードは `onBack` コールバックの供給側(`PodcastPlayerApp` の `navigate` 側)ではなく
pop を呼ぶ側に置き、ユーザー操作由来の `onBack` と自動クローズの双方が同じガードを通る形にする。

### D3: プロセス死復元の取り残しは受容する

プレイヤー画面を開いたままバックグラウンドで聴き終え、その間にプロセスが死んだ場合、
復元時は最初から `episodeId == null` のため遷移が観測されず、空のプレイヤーが一度表示される。

- 完全に潰すには `PlaybackStatus` に「controller 接続済み」フラグを追加し
  「接続済み かつ null なら閉じる」とする必要があるが、稀なケースのために状態を増やすのは
  思想に反する
- 閉じるボタンで抜けられ、現状と同じ挙動なので退行ではない

### D4: 切断中の `publishStatus` は何も流さない(実機検証で発見した競合の修正)

当初の設計は「`release()` は status を publish しないため、回転中に偽の null は流れない」と
分析していたが、実機検証(タスク 3.3)で2回目の回転時にプレイヤー画面が誤って閉じる事象が
出た。見落としていた経路は UI 側の定期更新である:

- `PlayerViewModel` は回転を生き延び、500ms ごとに `PlaybackConnection.refreshStatus()` を呼ぶ
- 回転中は `release()` → 再 `connect()`(非同期)の谷間で `controller == null`
- このとき `publishStatus()` が `PlaybackStatus()`(episodeId = null)を publish するため、
  新 composition の `LaunchedEffect` が stale な非null → この null を「キュー終端」と誤認する。
  tick と再接続のどちらが先かの競合であり、再現は確率的

修正: `publishStatus()` は `controller == null` のとき何も流さず、最後に観測した状態を保持する。
これにより **「episodeId = null が流れるのは、接続済みプレイヤーのキューが空のときだけ」** という
不変条件が決定論的に成立し、D1 の遷移検出はこの不変条件の上に立つ。副次効果として、
回転中にミニプレイヤーが一瞬消えて戻るちらつきも解消する。

受容するトレードオフ: サービスだけが先に破棄されて controller が切断されたまま残る稀な状況
(同一プロセスなので通常はアプリごと死ぬ)では、status が古い表示のまま残り得る。
アプリの再起動で回復するため、状態フラグを増やしてまで対処しない。

## Risks / Trade-offs

- [プロセス死復元で空画面が残る] → D3 のとおり受容。閉じるボタンで復帰可能
- [将来 UI に「停止」操作を追加すると、そこでもプレイヤー画面が自動で閉じる] →
  その時点では「停止=プレイヤーが空になる」なので、閉じるのはむしろ期待動作。
  ただし挙動が結合していることは本 design に明記しておく(この項がその記録)
- [検証手段が実機の手動確認のみ] → `ui/` はカバレッジ対象外・スモークテスト方針の範囲内。
  検証シナリオを tasks.md に明記して補う
