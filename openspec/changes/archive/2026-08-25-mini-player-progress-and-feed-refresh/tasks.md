# Tasks

## 1. ミニプレイヤーの進捗表示

- [x] 1.1 `formatTime` を `EpisodeDetailScreen.kt` からミニプレイヤーと共用できる `ui/` 内の場所へ移す(書式・挙動は変えない)
- [x] 1.2 `MiniPlayer` が `PlaybackStatus` を受け取るようにし、細い進捗線と「残り m:ss」表示を追加する(進捗線は表示専用。`durationMs` が未確定(0以下)の間は進捗線・残り時間とも非表示)
- [x] 1.3 `PodcastPlayerApp.kt` の呼び出し元で、収集済みの `status` を `MiniPlayer` へ渡す

## 2. 番組単位更新のメニュー項目

- [x] 2.1 `EpisodeListScreen.kt` の `FeedMenu` の先頭に「この番組を更新」を追加し、既存の `EpisodeListViewModel.refresh()` を呼ぶよう配線する(更新中表示は既存の `PullToRefreshBox` インジケーターのまま)

## 3. 検証

- [x] 3.1 `./gradlew check` が通ることを確認する
- [x] 3.2 `./gradlew installDebug` で実機に入れ、(a) 再生中のミニプレイヤーに進捗線と残り時間が出て追随する、(b) duration 確定前は出ない、(c) メニュー先頭の「この番組を更新」でその番組だけ更新される、をスモーク確認する
