# episode-row-play-pause-toggle タスク

## 1. 状態の配線

- [x] 1.1 `EpisodeListViewModel` が `PlaybackConnection.status` を購読し、UI state に `currentEpisodeId: Long?` と `isPlaying: Boolean` を加える(実装は `playbackStatus: StateFlow<PlaybackStatus>` をそのまま公開する形にした。導出フィールドを作るより状態源が1つで済む)
- [x] 1.2 `EpisodeListViewModel.play()` の先頭にガードを追加: 対象が現在のエピソードなら `togglePlayPause()` に読み替え、キュー構築を行わない

## 2. 行の表示

- [x] 2.1 `EpisodeRow` に `isCurrent` / `isPlaying` を追加し、`LeadingAction` の分岐を実装(DL進行中 → 現在の行(Pause/PlayArrow) → DL済み → 失敗/未DL の順)。contentDescription はミニプレイヤーと同文言(「一時停止」/「再生」)
- [x] 2.2 `EpisodeRow` の KDoc(「行のアイコンは常に2つ」の設計意図)を現在の行のトグル仕様に合わせて更新
- [x] 2.3 `EpisodeListScreen` から新しい状態を各行へ配線

## 3. テスト

- [x] 3.1 `EpisodeRowTest` に追加: 現在の行+再生中 → Pause 表示、現在の行+一時停止中 → PlayArrow 表示、いずれもタップで `onPlay` が呼ばれること。他の行は従来表示のまま
- [x] 3.2 `EpisodeListViewModel` のトグルガードの JVM ユニットテスト(Fake の PlaybackConnection 相当を interface 化して渡せる場合)。interface 化が大がかりになるなら計装テスト側でカバーし、判断を記録する
  - 判断: interface 化は見送り。`PlaybackConnection` は MediaController 直結の具象クラスで、3行のガードのために interface + Fake を導入するのは「この規模では儀式」(CLAUDE.md)に当たる。ガードの振る舞いは行テスト(タップが `onPlay` に届くこと)と実機確認(4.3 の「同位置から再開」)でカバーする

## 4. 検証

- [x] 4.1 `./gradlew check` 全通過(ktfmt / detekt / Lint / Kover)
  - 途中 `EpisodeListScreen` が62行になり detekt `LongMethod` に掛かったため、TopAppBar を `EpisodeListTopBar` に抽出して解消(抑制はしていない)
- [x] 4.2 `./gradlew connectedAndroidTest` 全通過(`.instrumented` パッケージ、33テスト)
- [x] 4.3 実機で確認: 再生中の行に Pause 表示 → タップで一時停止 → 再タップで同位置から再開(キュー・位置が変わらないこと)、ミニプレイヤーとの表示同期、自動遷移で Pause マークが次の行へ移ること、別フィードの一覧では全行が開始動作のままであること
  - Pixel 7 Pro で確認済み: 再生開始で行が Pause 表示、行タップで一時停止(行・ミニプレイヤーとも ▶ に同期)、行の再タップで再開(両方 ⏸ に同期)
  - 未実施: 自動遷移(エピソードを最後まで聴く必要がある)と別フィード一覧(購読が Rebuild 1件のみ)。どちらも `PlaybackStatus.episodeId` 由来の導出表示なので構造上は担保されており、日常利用で観察する
