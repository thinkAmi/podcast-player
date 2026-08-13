# podcast-player

個人用の Android ポッドキャストプレイヤー。利用者は開発者本人ひとり、端末は Pixel 7 Pro
(Android 16)1台のみ。

仕様と設計の正本は `openspec/changes/podcast-player-mvp/` にある。実装前にそこを読むこと。

## 設計思想(判断に迷ったらここに戻る)

このアプリは Podcast Addict の**認知負荷**への反動として作られた。したがって:

1. **機能を増やさないことが機能** — 機能追加の判断基準は「Podcast Addict になってしまわないか」。
   便利そうという理由で機能・設定・オプションを足さない。
2. **通信は例外なくユーザー起点** — 起動時の自動更新、自動ダウンロード、自動リトライを
   実装してはならない。バックグラウンドで勝手にギガを消費しないことが利用者の明示的な要求。
3. **設定画面を作らない。ソースコードが設定ファイル** — 閾値(視聴済み判定の残り10秒など)は
   コード内定数にする。変更したくなったら定数を書き換えてビルドする。
4. **判断と実行を分離する** — 「削除すべきか」の判断は純粋関数(`logic/`)、
   「実際に削除する」は `data/`。これはテスト戦略が成立するための前提条件であって、
   単なる好みの構造ではない。

## 構成

単一モジュール `:app`、4層パッケージ構成。

```
ui/     Compose 画面 + ViewModel
logic/  純粋 Kotlin(Android API 依存ゼロ)。視聴済み判定・削除対象決定・
        フィルター条件・RSS の構造解釈など「判断」のすべて
data/   Room / HTTP(HttpURLConnection) / XmlPullParser / ファイル管理(「実行」)
player/ Media3 の結線。再生イベントを data 層へ書き戻す
```

依存の向き: `ui → logic, data, player` / `data, player → logic`。

禁止事項:

- `logic/` から `android.*` `androidx.*` および他層への import(**detekt が機械的に拒否する**)
- `ui/` から Room の DAO/Entity を直接 import すること(規約。ViewModel 経由で repository を使う)
- DI フレームワーク、UseCase クラス層、マルチモジュール化(この規模では儀式)

## 技術選定の要点

- **Media3** (ExoPlayer + MediaSession + MediaSessionService) — 再生の全責務。自作しない
- **Room** — DB。`Flow` を返すクエリがリアクティブの背骨。
  「DB を書けば画面が追随する」を基本構造にする
- **HttpURLConnection / XmlPullParser** — OS 標準。OkHttp や RSS ライブラリを追加しない
- **画像は BitmapFactory + 自前ファイルキャッシュ** — Coil 等を追加しない
- 実行時のサードパーティ依存は**ゼロ**。信頼するベンダーは Google と JetBrains のみ。
  依存を足す提案をする前に、OS 標準 API で書けないか検討すること
- バージョンは `gradle/libs.versions.toml` で完全固定。動的バージョンを使わない
- `minSdk = targetSdk = compileSdk = 36`。**互換性のための分岐を書かない**

## 品質ゲート(すべてビルドを失敗させる)

コミット前に `./gradlew check` が通ること。

| ツール | 役割 |
|---|---|
| ktfmt (kotlinlang style) | 整形。設定不可・決定論的。`./gradlew ktfmtFormat` で自動整形 |
| detekt 2.0 | 意味の検査。設定は `config/detekt/detekt.yml` |
| Android Lint | `warningsAsErrors = true` |
| Kotlin コンパイラ | `allWarningsAsErrors = true` |
| Kover | `logic/` の行カバレッジ 90% 未満で失敗 |

detekt が特に狙って禁止しているもの(AI 実装が使いがちな逃げ道):

- **例外の握りつぶし** — `SwallowedException` / `TooGenericExceptionCaught` /
  `EmptyCatchBlock` / `ReturnFromFinally`
- **未完成コードのコミット** — `ForbiddenComment` が `TODO:` `FIXME:` `HACK:` `STOPSHIP:` を禁止。
  「あとで実装」のスタブを残して完了と報告しない。やり残しは
  `openspec/changes/*/tasks.md` に記録する
- **関数の肥大化** — `LongMethod`(60行)/ `CyclomaticComplexMethod` /
  `NestedBlockDepth` / `LongParameterList`
- **層違反** — `ForbiddenImport`(`logic/` の純粋性)

detekt の baseline は使わない。違反は抑制せず直すこと。

## テストの書き方

- **モックライブラリを使わない**。リポジトリは interface にし、テストでは手書きの
  Fake 実装を渡す。MockK / Mockito を追加しない
- **Robolectric を使わない**。判断ロジックを `logic/` の純粋関数に寄せることで、
  テストの大半を高速な JVM ユニットテストにする
- Android 依存(Room の DAO、XmlPullParser を使うパーサー)は実機での計装テスト。
  `XmlPullParser` は JVM ユニットテストでは動かないので注意
- 計装テストの Room は `inMemoryDatabaseBuilder` を使う。実 DB ファイルを作らない
- `ui/` `player/` のカバレッジ数値は追わない。薄いグルーコードに保ち、スモークテストで足りる
- **kotest は property モジュールのみ**(`kotest-property`、テスト限定依存)。プロパティテストは
  入力空間が列挙できないロジック(パーサー・順序決定など)に限って使い、テスト自体は JUnit4 の
  まま書く(`runBlocking { checkAll(...) }`)。kotest の assertions / framework(Spec スタイル)は
  採用しない。ベンダー信頼(Google / JetBrains のみ)の意図的な例外であり、無断で広げないこと
- 状態空間が全列挙できる検証(例: フィルター条件の SQL ↔ `logic/` 等価性)はランダム生成でなく
  全数列挙で書く
- テスト依存を追加・更新したら `./gradlew --write-verification-metadata sha256
  :app:testInstrumentedUnitTest` で `gradle/verification-metadata.xml` を再生成する
  (依存の SHA-256 検証が有効なため、これを忘れるとビルドが落ちる)

## 開発環境

- ローカルの Android Studio(`~/Applications/Android Studio.app`)。DevContainer は使わない
- 実機へは USB 直接インストール。エミュレータは使わない
- Gradle は wrapper 経由(`./gradlew`)。ラッパーは配布物の SHA-256 を検証する設定
- **PC からの購読登録は `./scripts/add-feed.sh <RSS の URL>`**(AI からは `add-podcast-feed`
  スキル経由)。`exported="false"` の `AdbFeedReceiver` を `run-as` で叩くだけで、転送機能を
  アプリに足しているわけではない。信頼境界は計装テストと同じ「USB デバッグを許可した PC」で、
  debuggable ビルドでのみ成立する。`adb shell am broadcast` を直接組み立てないこと
  (契約とクォート処理はスクリプトが持つ)

## 実機のデータを消さないために(重要)

利用者は本番端末で普段使いしている。**改善のたびに購読リストを消してはならない。**

- **日常の改善は `./gradlew installDebug`** — 同じ署名の上書きインストールなので、購読・
  視聴状態・DL ファイルはすべて保持される。ほとんどの変更はこれで済む
- **計装テストは別パッケージで走る** — `testBuildType = "instrumented"` と
  `applicationIdSuffix = ".instrumented"`(`app/build.gradle.kts`)により、
  `./gradlew connectedAndroidTest` は `dev.thinkami.podcastplayer.instrumented` という
  別アプリとしてインストール・実行・アンインストールされる。本番アプリのデータには
  OS のサンドボックスにより到達できないため、実データのある端末でも安全に流せる。
  この設定を削除・変更してはならない
- テストが異常終了して `.instrumented` が端末に残ったら
  `adb uninstall dev.thinkami.podcastplayer.instrumented` で消す(本番アプリには影響しない)
- 消えても復旧は数分(URL を登録し直して「すべて視聴済み」を押す)。この軽さは維持する

### 防御の構造(どれが主で、どれが飾りか)

主防御は **`.claude/settings.json` の完全固定 allowlist** と、**そこに無いコマンドが必ず利用者への
プロンプトに落ちること**。破壊的コマンドを検知して止めているのではなく、**破壊経路を自動承認に
一つも載せていない**という構造で守っている。ただしプロンプトの先には「人間が見慣れた形を反射的に
承認してしまう」事故が残る(実際に一度起きた)。そのため:

- **Gradle の uninstall 系はタスク自体が失敗する**(`app/build.gradle.kts` の実行時ガード)。
  `uD` のような省略形もすべて同じタスクに解決されるため、表記ゆれ・モード・プロンプトの
  どれにも依存しない最終層。本当に必要なら `-PallowUninstall=true` を付けて実行する
  (通常は不要。掃除は `adb uninstall <pkg>.instrumented` で足りる)。このガードを削除してはならない

残りの層について:

- **allowlist にワイルドカードを足さないこと。** 特に `./gradlew ... *` の形は、Gradle が末尾トークンを
  追加タスクとして実行するため `./gradlew check uninstallDebug` が自動承認される穴になる
- **deny リストは飾りに近い。** Gradle は `uD` の 2 文字でも `:app:uninstallDebug` に解決し、
  `adb` は PATH に無いためフルパス起動になる。どちらも文字列一致の deny をすり抜ける。
  deny は「見慣れた形を早めに弾く」二重化にすぎず、これに依存してはならない
- **`guard-device.sh`(PreToolUse フック)は補助**。adb / run-as の device-side 破壊だけを見る。
  exit 2 のみがブロックで、内部エラー・スクリプト不在・実行権限なしは**素通りする(fail-open)**。
  変数経由や生成スクリプト経由の間接化も捕捉できない。フックを唯一の防壁にしてはならない
- **`.claude/**` と `scripts/**` は Edit deny**。Claude 自身が防御を緩められないようにしてある。
  `Edit(path)` ルールは Bash の `rm` / `touch` にも及ぶ(`Write(path)` ルールは**参照されない**ので
  書いても無意味)。設定を変えるのは利用者の手で行う

拒否された・フックに止められたら、**回避策を探さず**、その操作が本当に必要か利用者に確認する。

### フックの回帰テスト

判定の退行は全数列挙テストで検出する。手動実行は `./.claude/hooks/guard-device.test.sh`、
コミット時は `.githooks/pre-commit` が自動実行する(有効化はクローン直後に一度だけ
`git config core.hooksPath .githooks`)。既知バイパス(クォート回避・`cmd package` 抜け・
複数出現・device-side インジェクション)がケースとして並んでいるので、判定を触ったら必ず走らせる。
