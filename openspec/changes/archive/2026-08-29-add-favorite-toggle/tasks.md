# add-favorite-toggle — Tasks

## 1. logic/ — 判断の追加

- [x] 1.1 `ListeningRules` に★解除時の削除予約判断の純粋関数を追加する(材料は played / downloaded / isCurrent の真偽値のみ。`shouldDeleteDownload` は変更しない)
- [x] 1.2 判断関数の JVM ユニットテストを書く(状態空間が小さいので全数列挙で: played × downloaded × isCurrent の8通り)

## 2. data/ — repository の配線

- [x] 2.1 `EpisodeRepository` interface に `setFavorite(episodeId, favorite)` を追加し、`RoomEpisodeRepository` で既存の `EpisodeDao.setFavorite` に配線する
- [x] 2.2 ~~既存の Fake リポジトリ実装に `setFavorite` を足す~~ → 該当なし。planning 時の思い込みで、Fake リポジトリはこのプロジェクトに存在しなかった(ViewModel テスト自体が無い)。3.4 の見直しも参照
- [x] 2.3 計装テスト: DAO `setFavorite` の更新が `findById` / Flow に反映されること、および favorite=true の行が `deleteDownloadsIfEligible` で削除されないこと(inMemoryDatabaseBuilder)

## 3. ui/ — ★トグルと Undo レール

- [x] 3.1 favorite 解除用の Undo 表現(favorite 復元+削除中止のみを持つ小さな型)を `ui/` に追加する(design D4)。あわせて Undo レールの型を `UndoableChange`(sealed)に一般化し、`PlayedUndoHolder` を `UndoHolder` に改名した(favorite も持つようになり名前が実態と合わなくなるため)
- [x] 3.2 統合エピソード画面の ViewModel に★トグル処理を追加する: 付与と削除対象なしの解除は `setFavorite` のみ(無音)、削除予約ありの解除はスナップショット → スナックバー表示 → 猶予明けに `deleteDownloadsIfEligible`。現在のエピソードは予約しない(logic の判断関数を使う)
- [x] 3.3 `EpisodeDetailScreen` に★アイコン(輪郭/塗りつぶし、material-icons-extended の star 系)を追加し、スナックバー文言(「★を外しました。ダウンロードを削除します」+「元に戻す」)を配線する
- [x] 3.4 ~~ViewModel のユニットテストを書く(Fake リポジトリで)~~ → 見直し: `EpisodeDetailViewModel` の依存(`PlaybackConnection` 等)は具象クラスで Fake 注入不可能。CLAUDE.md の方針(ui/ は薄いグルーに保ちカバレッジを追わない。判断は logic/ へ)に従い、判断は 1.2 の全数列挙テスト、削除・復元の実行は 2.3 の計装テストでカバーし、ViewModel は既存の `togglePlayed` と同じくスモーク(4.3)で確認する

## 4. 検証と仕上げ

- [x] 4.1 `./gradlew check` を通す(ktfmt / detekt / lint / kover 90%)
- [ ] 4.2 `./gradlew connectedAndroidTest` で計装テストを通す
- [x] 4.3 `./gradlew installDebug` で実機に入れ、spec のシナリオを手で確認する(★付与→聴き終えても残る / ★解除→5秒後に削除 / Undo / 聴き直し中の★解除で再生継続)— 利用者が実機で確認済み
