# Proposal: close-player-when-queue-ends

## Why

complete-on-playback-end により、キューを聴き終えるとプレイヤーは空になり(`STATE_ENDED` →
`stop()` + `clearMediaItems()`)、メディア通知とミニプレイヤーは自動で消えるようになった。
しかしプレイヤー画面だけはフルスクリーンのナビゲーション先であるため追随できず、開いたまま
聴き終えると「0:00/0:00・タイトルなし・操作に無反応」の空画面が残る(complete-on-playback-end
の実機検証で発見、tasks.md にスコープ外として記録済み)。

## What Changes

- プレイヤー画面を開いたままキューを聴き終えたとき、プレイヤー画面を自動で閉じて
  元の画面(一覧)へ戻す
- 検出は「再生状態の `episodeId` が 非null → null へ遷移した」ことによる。
  現行コードでこの遷移が起きる経路はキュー終端の `clearMediaItems()` のみであり、
  遷移検出であれば再接続直後の初期 null(回転・復元)や自動継続再生で誤って閉じることがない
- 閉じる操作(`popBackStack`)の前に現在の destination がまだプレイヤー画面であることを
  確認し、ユーザーの閉じる操作と競合したときの二重 pop(下の一覧まで閉じてしまう)を防ぐ

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `playback`: 「ミニプレイヤーとプレイヤー画面」要件に、キュー終端でプレイヤー画面が
  自動で閉じる振る舞いを追加する

## Impact

- 影響コード: `ui/` 層(`PlayerScreen` / `PodcastPlayerApp` の数行)と、
  `player/PlaybackConnection` の1点(切断中に空の status を流さない — design.md D4)。
  `logic/` `data/` は変更しない
- 受容するトレードオフ: プロセス死からの復元時に既にキューが尽きていた場合、遷移が
  観測できないため空のプレイヤー画面が一度表示される(閉じるボタンで抜けられる。現状と
  同じ挙動であり退行ではない)。完全に潰すには接続済みフラグの追加が必要になるが、
  稀なケースのために状態を増やさない
