# Proposal: show-podcast-artwork

## Why

購読一覧・エピソード一覧が文字だけの表示であり、どのポッドキャスト番組であるかをパッと見で判断できない。番組の視覚的な識別子であるアートワークは、データ層(取得・ファイルキャッシュ・DB へのパス永続化・購読解除時の削除)がすべて実装済みで、表示だけが未実装のまま残っている(`ArtworkStore.load()` は呼び出し箇所ゼロ)。また playback spec は「プレイヤー画面はアートワークを表示する」と SHALL で定めているが、現在の PlayerScreen に表示コードが無く、spec と実装が乖離している。

## What Changes

- 購読一覧の各行の左端に番組アートワーク(56dp・角丸)を表示する
- 購読一覧の行から 2 行目の feedUrl 表示を削除する(識別の役割はアートワークが引き継ぐ)
- エピソード一覧の TopAppBar に番組アートワーク(40dp)と feedUrl を表示する(番組名の左に画像、番組名の下に URL。feedUrl の移設先)
- プレイヤー画面のタイトル上部にアートワークを大きく表示する(playback spec の既存 SHALL の実装)
- アートワークが無い・読めない番組は、番組名の頭文字によるモノグラムタイルで代替表示する
- 表示用のビットマップは目標ピクセルサイズへの縮小デコードとし、`inSampleSize` の算出と頭文字の選定は `logic/` の純粋関数とする
- 縮小デコード結果は ViewModel のメモリ内に保持し、画面表示のたびの再デコードを避ける

やらないこと(対話で確定した設計判断):

- 新たな通信の追加(表示はローカルファイルの読み込みのみ。取得は従来どおり購読時・手動更新時)
- 取得済みアートワークの再取得・差し替え(鮮度は追わない)
- 番組個別の「アートワーク更新」メニュー(未取得番組は既存の refresh が再試行するため不要)
- 取得失敗の記録・バックオフ(負荷を精査した結果、誤差の範囲)
- エピソード個別アートワーク(item 単位の `<itunes:image>`)の取得・表示
- 縮小版ファイルの事前生成・保存(サイズ定数変更時の鮮度管理という新しい問題を作るため)
- MiniPlayer・Media3 通知への表示(必要なら別 change)
- 画像ライブラリ(Coil 等)の導入

## Capabilities

### New Capabilities

- `artwork-display`: 購読一覧・エピソード一覧・プレイヤー画面での番組アートワークの表示。モノグラムによる代替表示、縮小デコード、通信を伴わないことの制約を含む

### Modified Capabilities

なし。playback spec はプレイヤー画面のアートワーク表示を既に SHALL で要求しており(本 change はその実装)、feed-subscription spec は購読一覧の行の表示内容(feedUrl の有無)を要求として定めていないため、既存要求の変更は発生しない。

## Impact

- `ui/subscriptions/FeedRow.kt` — アートワーク表示の追加と feedUrl 行の削除
- `ui/episodes/EpisodeListScreen.kt` — TopAppBar へのアートワークと feedUrl の追加
- `ui/player/PlayerScreen.kt` / `PlayerViewModel.kt` — アートワーク表示の追加。Episode の feedId から Feed を引く配線(既存 `observeFeed` を利用)が今回唯一の新しいデータ配線
- `ui/` 共通 — アートワーク/モノグラムを描く共有コンポーザブル(新規)
- `logic/` — `inSampleSize` 算出・頭文字選定の純粋関数(新規。JVM ユニットテスト対象、Kover 90% ゲート配下)
- `data/artwork/ArtworkStore.kt` — 縮小デコード対応(目標ピクセルサイズを受け取る)
- DB スキーマ・ネットワーク・依存ライブラリの変更なし
