# episode-row-play-pause-toggle

## Why

エピソード一覧の行の再生アイコン(▶)は「開始」専用で、いま鳴っているエピソードの行からは一時停止できない。さらに、再生中の行の ▶ をタップすると一時停止どころかキューが再構築されて保存位置へシークし直す「リセット」動作になる。同じ見た目のボタンなのに行によって結果が違い、押した結果が予測できない。これはこのアプリが敵視する認知負荷そのものである。

## What Changes

- 「現在のエピソード」(PlaybackStatus.episodeId と一致する行)のアイコンを再生状態に追随させる: 再生中は Pause、一時停止中は PlayArrow
- 現在の行のアイコンタップは常に `togglePlayPause()`。キューを組み直さず、位置もシークしない
- それ以外の行は従来どおり: タップでいまの一覧の並びからキューを構築して再生を開始する
- UI の分岐だけに頼らず、`EpisodeListViewModel.play()` にも「対象が現在のエピソードならトグルに読み替える」ガードを置く(意味論を1箇所に固定する)
- `EpisodeRow` の KDoc(「行のアイコンは常に2つ」の設計意図)を新しい仕様に合わせて更新する

変更しないこと:

- ミニプレイヤー・プレイヤー画面・通知の挙動
- 他の行タップ時のキュー再構築(現行仕様どおり)
- バッファリング中に `isPlaying` が false になる Media3 の挙動への作り込み(ミニプレイヤーと同じ状態源を読み、同じ揺らぎ方を許容する)

## Capabilities

### New Capabilities

(なし)

### Modified Capabilities

- `playback`: 行の再生アイコンに関する要件を変更する。「DL済みの行には再生アイコンを表示しタップで再生開始」に加え、現在のエピソードの行はアイコンが再生状態を表示し、タップが再生/一時停止のトグルになる

## Impact

- `ui/episodes/EpisodeRow.kt` — アイコン分岐とコールバックの追加、KDoc 更新
- `ui/episodes/EpisodeListScreen.kt` — 再生状態を行へ配線
- `ui/episodes/EpisodeListViewModel.kt` — PlaybackStatus の購読、play() のトグルガード
- `player/PlaybackConnection.kt` — 変更なしの見込み(status: StateFlow と togglePlayPause() は実装済み)
- テスト — `EpisodeRowTest`(計装テスト)に現在の行の表示・トグルのケースを追加。計装テストは `.instrumented` パッケージ隔離済みのため実機で安全に流せる
