# episode-row-play-pause-toggle 設計

## Context

- `EpisodeRow` の左アイコンは「未DLならDL、DL済みなら再生開始」の1アクション。行が再生状態を知らないため、再生中の行でも常に ▶ を表示する
- `EpisodeListViewModel.play()` は無条件に `PlaybackQueue.playbackOrderFrom()` でキューを組み直し、`PlaybackConnection.play()` が `setMediaItems` → `prepare` → 保存位置へ `seekTo` する。再生中の行の ▶ は実質リセット
- 一時停止は `PodcastPlayerApp` の Scaffold に常駐するミニプレイヤーだけができる
- 必要な状態は実装済み: `PlaybackConnection.status: StateFlow<PlaybackStatus>`(`episodeId` / `isPlaying`)と `togglePlayPause()`

## Goals / Non-Goals

**Goals:**

- 現在のエピソードの行アイコンが再生状態(再生中/一時停止中)を表示する
- 現在の行のタップは再生/一時停止のトグル。キュー再構築・シークを伴わない
- 「現在の行=トグル・他の行=開始」の意味論を ViewModel の1箇所に固定する

**Non-Goals:**

- ミニプレイヤー・プレイヤー画面・メディア通知の変更
- 他の行タップ時のキュー再構築仕様の変更
- バッファリング中の `isPlaying=false` によるアイコンの一瞬の揺らぎの抑制(ミニプレイヤーと同じ状態源・同じ挙動に揃えることを優先)
- 「現在の行からキューを組み直す」手段の提供(別の行をタップすれば足りる)

## Decisions

### D1: 「現在の行」の判定は episodeId の一致のみ。isPlaying はアイコン表示にだけ使う

`episode.id == PlaybackStatus.episodeId` の行を「現在の行」とし、タップは isPlaying に関わらず常に `togglePlayPause()`。

- 理由: 「一時停止中の現在の行」の ▶ を `play()` に流すと、見た目は再開なのにキュー再構築+シークが走る罠が残る。Pause アイコンの有無(見た目)とタップの意味(トグルか開始か)を別の条件に載せない
- 代替案(却下): isPlaying のときだけトグル → 上記の罠が残る

### D2: ガードは ViewModel に置く

`EpisodeListViewModel.play(episode)` の先頭で `episode.id == status.episodeId` ならば `togglePlayPause()` に読み替える。`EpisodeRow` はコールバックを分けず、既存の `onPlay` 1本のまま。

- 理由: 意味論が ViewModel の1箇所に固定され、UI の配線変更で罠が復活しない。`EpisodeRow` の API 変更は表示用の状態追加だけで済む
- 代替案(却下): Row に `onPlay` / `onTogglePlayPause` の2コールバック → 呼び分けの判断が UI 側に漏れ、テストすべき箇所が増える

### D3: EpisodeRow へは表示用の最小状態だけ渡す

`EpisodeRow(episode, downloadState, isCurrent: Boolean, isPlaying: Boolean, ...)` とし、アイコン分岐は:

1. DL進行中 → 進捗サークル(従来どおり)
2. `isCurrent` → `isPlaying ? Pause : PlayArrow`、contentDescription はミニプレイヤーと同文言(「一時停止」/「再生」)
3. DL済み → PlayArrow(従来どおり)
4. DL失敗/未DL → 従来どおり

`isCurrent` の行は定義上DL済みなので、分岐2は分岐3より先に評価する。行のアイコン数は2つのまま。

- 代替案(却下): `PlaybackStatus` をそのまま Row に渡す → Row が全行分の状態比較を持ち、recomposition の範囲も広がる

### D4: 状態の流れは既存の Flow 合成に乗せる

`EpisodeListViewModel` が `PlaybackConnection.status` を購読し、UI state に `currentEpisodeId: Long?` と `isPlaying: Boolean` を加えて画面へ流す。新しい状態源・ポーリングは作らない。

## Risks / Trade-offs

- [1秒ごとの位置保存より細かい精度は出ない] → 現在の行のトグルは `seekTo` を伴わないため影響なし。他の行への切り替え時に最大1秒巻き戻るのは既存仕様のまま
- [再生キューが別フィードのエピソードを指しているとき、この画面に現在の行が存在しない] → 単に一致する行がないだけで、全行が「開始」動作になり破綻しない
- [Media3 の自動遷移で現在の行が移動する] → `status.episodeId` 由来なので自動で追随する。実機確認項目に含める
- [detekt の CyclomaticComplexMethod] → `LeadingAction` の分岐が1つ増える。閾値に当たったら表示分岐を小さな private 関数に抽出する

## Open Questions

(なし — 主要な論点は利用者と合意済み: 現在の行はトグル専用とし、キュー組み直しは他の行タップで行う)
