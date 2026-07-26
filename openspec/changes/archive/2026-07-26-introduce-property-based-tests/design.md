# Design: introduce-property-based-tests

## Context

`logic/` は純粋 Kotlin の「判断」層で、テストの大半を高速な JVM ユニットテストで賄う戦略を取っている。現行テストは JUnit4 の例ベースで、代表値の検証に留まる。事前調査(ワークツリーでの導入トライアル)で以下を確認済み:

- kotest-property 6.2.3 は Kotlin 2.3.10 / AGP 9.3 / JUnit4 と共存でき、Maven Central のみで解決できる
- `./gradlew check`(ktfmt / detekt / lint / Kover)は設定変更なしで通る
- 依存検証(`verification-metadata.xml`)が有効なため、追加時は SHA-256 の再生成が必要
- `parseDurationMs` に負成分受理と Long オーバーフローの2バグが現存する

## Goals / Non-Goals

**Goals:**

- 入力空間が無限のロジック(RSS 値解釈、再生順)を性質で検証し、既知バグ2件を修正する
- SQL WHERE 句と `EpisodeFiltering` の等価性宣言を機械検証に変える
- 将来のセッションが従うべきテスト方針(kotest の採用範囲)を CLAUDE.md に固定する

**Non-Goals:**

- 既存テスト46件の kotest Spec スタイル移行(計装テスト側が JUnit4 固定のため2方言体制になる。業界動向が現実化してから行ってもコストは同じ)
- kotest-assertions の採用(shouldBe 化は慣れの効果が薄い割に方言を増やす)
- Room 等価性テストのプロパティ化(現状の状態空間は16通りで全数列挙が可能。フィルター条件が増えたときの改修とする)
- 素振り用学習リポジトリの作成(このリポジトリの外の話)

## Decisions

### D1: kotest は property モジュールのみ、JUnit4 のまま使う

`checkAll` は suspend 関数だがランナー非依存のため、既存の JUnit4 テストから `runBlocking` で包んで呼ぶ。kotest-runner-junit5 / `useJUnitPlatform()` への切り替えは行わない。

- 代替案: Spec スタイルへ全面移行 → 計装テスト33件は AndroidJUnitRunner(JUnit4)固定のため2方言になる。学習目的は別リポジトリに分離済みで、この製品には移行の受益がない
- 帰結: テスト関数は `@Test fun x() { runBlocking { checkAll(...) } }` の形(JUnit4 はテスト関数の戻り値 void を要求するためブロック本体で書く)

### D2: 手書きジェネレータではなく kotest-property を使う

手書き(固定シード `Random` + `repeat`)でも同じ性質は書けるが、失敗時の自動シュリンク(最小反例への縮小)と失敗シードの自動保存・再利用(`~/.kotest/seeds`)は再実装コストが高い。テスト限定依存であり「実行時のサードパーティ依存ゼロ」原則は維持される。ベンダー信頼(Google / JetBrains のみ)の例外となることは CLAUDE.md に明記して意図的な判断として残す。

### D3: parseDurationMs の拒否条件は「成分の形式検査」で実装する

負の成分・オーバーフローの拒否は、合計値の事後チェックではなく成分段階で行う:

- 各成分は `toLongOrNull` に加えて非負(先頭 `-`/`+` や空白を含む表記は不正)であること
- ミリ秒換算は範囲検査してから行う(合計秒 > `Long.MAX_VALUE / 1000` なら null)

事後チェック(結果が負なら null)は `"10:-5"` のような「負成分でも合計が正」のケースを素通しするため不採用。detekt の `SwallowedException` 系ルールに触れない形(例外に頼らず null を返す)を保つ。

### D4: Room 等価性テストは全数列挙の計装テスト

`unplayedOnly` × `downloadedOnly` × `played` × `downloaded` = 16通りを全列挙し、`inMemoryDatabaseBuilder` の Room に投入して DAO クエリ結果と `EpisodeFiltering.apply` の結果(ID と並び順)を突き合わせる。`publishedAtEpochMillis` の同値ケース(未解釈日付の 0L)を含め、ORDER BY のタイブレーク(`id DESC`)と論理側の前提が一致することも確認する。

- 代替案: `checkAll` によるランダム生成 → 状態空間が全列挙可能な現状では複雑さに見合わない。計装テスト側に kotest 依存を足す理由も消える

### D5: 依存追加は catalog + verification-metadata 再生成をセットで行う

`./gradlew --write-verification-metadata sha256 :app:testInstrumentedUnitTest` で再生成する。差分には kotest 本体のほか rgxgen / classgraph / java-diff-utils / xmlutil / byte-buddy / JNA / opentest4j 等の推移的依存(約37アーティファクト、+251行)が含まれることを想定内とする。

## Risks / Trade-offs

- [プロパティテストの実行時間増] → 事前検証で2プロパティ(各1,000ケース)0.2秒を確認済み。現実的な影響なし
- [テスト用クラスパスに Google / JetBrains 外のベンダーが約8社入る] → 実行時依存はゼロのまま。CLAUDE.md に例外として明記し、無自覚な拡大(assertions や framework の追加採用)を防ぐ
- [kotest 更新のたびに verification-metadata 再生成が必要] → 更新は意図的に行う方針(動的バージョン禁止)と整合。手順を CLAUDE.md に記載
- [ファジングのプロパティが稀にしか踏まない反例を持つ] → kotest は失敗シードを保存・再利用するため、一度見つかれば回帰テストとして機能する。境界値(空文字、コロン連打、`Long.MAX_VALUE / 1000` 近傍)は例ベーステストでも固定する
- [`"1:99"`(秒が59超)の扱いが未定義] → 現行実装は 159 秒として受理している。実フィードに `MM:SS` の分超過表記が存在するため受理を維持し、性質の対象外(仕様変更しない)とする
