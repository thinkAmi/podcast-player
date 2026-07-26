# Tasks: introduce-property-based-tests

## 1. 依存導入

- [x] 1.1 `gradle/libs.versions.toml` に `kotest = "6.2.3"` と `kotest-property`(`io.kotest:kotest-property`)を追加する
- [x] 1.2 `app/build.gradle.kts` に `testImplementation(libs.kotest.property)` を追加する
- [x] 1.3 `./gradlew --write-verification-metadata sha256 :app:testInstrumentedUnitTest` で `gradle/verification-metadata.xml` を再生成し、差分が kotest とその推移的依存のみであることを確認する

## 2. parseDurationMs のバグ修正(プロパティテスト駆動)

- [x] 2.1 `RssInterpretationPropertyTest` を新規作成し、時分秒の往復プロパティ(h:mm:ss → 合計秒×1000)と任意文字列ファジング(例外なし・null か正値)を書く。この時点で既知バグ2件により失敗することを確認する
- [x] 2.2 `RssInterpretation.parseDurationMs` / `combine` を修正する: 負の成分(先頭 `-`/`+` を含む表記)を拒否、合計秒が `Long.MAX_VALUE / 1000` を超える場合を拒否(いずれも null)
- [x] 2.3 `RssInterpretationTest` に境界値の例ベーステストを追加する: `"10:-5"` → null、オーバーフロー境界近傍、`"1:99"` → 159秒(分超過の受理は維持)
- [x] 2.4 プロパティテスト・既存テストがすべて通ることを確認する

## 3. PlaybackQueue の整合性プロパティテスト

- [x] 3.1 `PlaybackQueuePropertyTest` を新規作成する: ランダムなエピソードリストに対し、`playbackOrderFrom` の結果が「全件DL済み・先頭は選択エピソード・元リストの部分列」であること、選択エピソードがDL済みのとき `playbackOrderFrom(...)` の2番目と `nextAutoPlayable(...)` が一致することを検証する

## 4. Room 等価性の計装テスト

- [x] 4.1 `androidTest` に等価性テストを新規作成する: `unplayedOnly` × `downloadedOnly` × `played` × `downloaded` の16通りを全数列挙し、`inMemoryDatabaseBuilder` の Room に投入した DAO クエリ結果と `EpisodeFiltering.apply` の結果(ID・並び順)が一致することを検証する。`publishedAtEpochMillis` 同値時のタイブレーク(`id DESC`)を含める
- [x] 4.2 実機で `./gradlew connectedAndroidTest` を実行し、全計装テストが通ることを確認する

## 5. ドキュメント更新と品質ゲート

- [x] 5.1 CLAUDE.md「テストの書き方」を更新する: kotest-property のみテスト限定で許可 / assertions・framework(Spec スタイル)は不採用 / テストは JUnit4 のまま / 依存追加・更新時は verification-metadata 再生成が必要、を明記する
- [x] 5.2 `./gradlew check` が通ることを確認する(ktfmt / detekt / lint / Kover / JVM ユニットテスト)
