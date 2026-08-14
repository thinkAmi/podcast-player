# Tasks: show-podcast-artwork

## 1. logic/ の純粋関数(判断)

- [x] 1.1 縮小率算出関数を追加する(元の幅・高さ + 目標ピクセル → 目標サイズ以上を保つ最大の 2 の冪の inSampleSize)
- [x] 1.2 縮小率算出の JVM ユニットテストを書く(等倍・丁度 2 の冪・非正方形・目標より小さい元画像・0/負の入力)
- [x] 1.3 モノグラム頭文字選定関数を追加する(タイトル → 表示 1 文字。先頭空白のスキップ・サロゲートペア・空文字/空白のみで例外を出さない)
- [x] 1.4 頭文字選定の JVM ユニットテストを書く(通常・空白始まり・絵文字始まり・空文字・空白のみ)

## 2. data/ の縮小デコード(実行)

- [x] 2.1 ArtworkStore に目標ピクセルサイズ付きの読み込みを追加する(inJustDecodeBounds で寸法取得 → 1.1 の関数で縮小率算出 → 縮小デコード。破損ファイルは null)

## 3. ui/ 共有コンポーザブル

- [x] 3.1 アートワーク/モノグラムを描く共有コンポーザブルを追加する(Bitmap があれば画像、無ければ頭文字タイル。サイズは呼び出し側指定・角丸・secondaryContainer 系単色)

## 4. 購読一覧

- [x] 4.1 SubscriptionListViewModel にアートワーク読み込みを追加する(Map<feedId, Bitmap> を IO で構築しメモリ保持。再コンポジションで再デコードしない)
- [x] 4.2 FeedRow の左端にアートワーク(56dp)を表示し、feedUrl の行を削除する(未聴数ピルは維持)

## 5. エピソード一覧

- [x] 5.1 EpisodeListViewModel に番組アートワークの読み込みを追加する(1枚・メモリ保持)
- [x] 5.2 EpisodeListTopBar の title スロットを Row(アートワーク 40dp, Column(番組名, feedUrl)) に差し替える(EpisodeRow は変更しない)

## 6. プレイヤー画面

- [x] 6.1 PlayerViewModel に currentEpisode.feedId → observeFeed → artworkLocalPath の配線とアートワーク読み込みを追加する(大きめの目標サイズ)
- [x] 6.2 PlayerScreen の Column 先頭(タイトルの上)にアートワークを表示する(正方形・中央寄せ。無ければ大きいモノグラム)

## 7. 検証

- [x] 7.1 `./gradlew check` を通す(ktfmt / detekt / Lint / Kover 90%)
- [x] 7.2 installDebug で実機確認する(購読一覧のアートワークと feedUrl の消失・エピソード一覧の上部バー・スクロール時のヘッダー残留・TopAppBar の高さが標準に収まること)
- [x] 7.3 モノグラム代替表示と feedUrl 非表示を計装テストで検証する(実機の全13番組がアートワーク取得済みで、画像なしの経路を実データでは踏めないため)
- [x] 7.4 プレイヤー画面のアートワークを実機で目視する(利用者が確認し、表示されていることを確認済み)
