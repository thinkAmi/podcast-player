# Proposal: introduce-property-based-tests

## Why

`logic/` のパーサーと再生順ロジックは「任意の入力に対して壊れない」ことが仕様だが、現行の例ベーステストは代表値しか検証しておらず、実際に `parseDurationMs` には未検出のバグが2件現存する(負の成分を持つ `"10:-5"` を595秒として受理する / 巨大な秒数で `* 1000` が Long オーバーフローして負値を返す)。入力空間が無限のロジックには、性質(プロパティ)で検証するテストが例の列挙より適している。また、SQL の WHERE 句と `EpisodeFiltering` の等価性は [EpisodeFiltering.kt](../../../app/src/main/kotlin/dev/thinkami/podcastplayer/logic/EpisodeFiltering.kt) のコメントで「同じ意味でなければならない」と宣言されているだけで、機械的な検証がない。

## What Changes

- kotest-property 6.2.3 を **テスト限定**(`testImplementation`)で導入する。APK に同梱される実行時依存はゼロのまま
- `gradle/verification-metadata.xml` に kotest とその推移的依存の SHA-256 を登録する
- `RssInterpretation.parseDurationMs` の現存バグ2件を修正する: 負の成分を含む時分秒表記の拒否、ミリ秒換算時の Long オーバーフローの拒否(いずれも「解釈できない長さ」として null を返す)
- `RssInterpretation` にプロパティテストを追加する: 時分秒の往復(生成した h:mm:ss が合計秒のミリ秒に解釈される)、任意文字列のファジング(例外を投げず null か正値を返す)
- `PlaybackQueue` に関数間整合性のプロパティテストを追加する: `playbackOrderFrom` と `nextAutoPlayable` が同じ再生順を解釈していること
- Room の WHERE 句と `EpisodeFiltering` の等価性を検証する計装テストを追加する(状態空間16通りの全数列挙。kotest 不要・JUnit4 のまま)
- CLAUDE.md「テストの書き方」を更新する: kotest-property のみテスト限定で許可、kotest の assertions / framework(Spec スタイル)は不採用、テストの書き方は JUnit4 のまま、依存追加・更新時は verification-metadata の再生成が必要である旨を明記

## Capabilities

### New Capabilities

なし(テストコードの整備が主体であり、利用者から見える新機能はない)

### Modified Capabilities

- `feed-subscription`: 「RSSパースの耐障害性」要件に、不正な長さ表記(負の成分・オーバーフローする巨大値)を「長さ不明」として扱うシナリオを追加する。壊れた値を part 単位でなく item の属性単位でも捨てる、という既存原則の明確化

## Impact

- **依存**: テスト用クラスパスに kotest-property と推移的依存(約37アーティファクト、Google / JetBrains 以外のベンダーを含む)が追加される。実行時依存は変化なし。導入手順・品質ゲート(`./gradlew check`)通過はワークツリーで検証済み
- **プロダクションコード**: `logic/rss/RssInterpretation.kt` の `parseDurationMs` / `combine` のみ(バグ修正)
- **テストコード**: JVM ユニットテスト2ファイル追加(RssInterpretation / PlaybackQueue のプロパティテスト)、計装テスト1ファイル追加(Room 等価性)
- **ビルド設定**: `gradle/libs.versions.toml`、`app/build.gradle.kts`、`gradle/verification-metadata.xml`(+251行、再生成)
- **ドキュメント**: CLAUDE.md のテスト方針
- **やらないこと(明示)**: 既存テスト46件の kotest Spec スタイルへの移行、kotest-assertions の採用(いずれもペンディング。将来必要になってから行ってもコストは同じ)
