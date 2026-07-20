# Design: podcast-player-mvp

## Context

グリーンフィールドのAndroidアプリ。利用者は開発者本人のみ、端末はPixel 7 Pro(Android 16)1台。聴取フローは「朝、自宅Wi-Fiでフィード更新→手動DL→外で聴く」。設計思想は「機能を増やさないことが機能」— Podcast Addictの認知負荷への反動がプロジェクトの出発点であり、機能追加の判断基準は常に「Podcast Addictになってしまわないか」。

本designは探索セッション(2026-07-20)での網羅的なインタビューで確定した決定の記録である。

## Goals / Non-Goals

**Goals:**

- 事前DL→オフライン再生のコアループが確実に動くこと(DLの信頼性 > 機能数)
- 「未聴 AND DL済み」フィルターが番組ごとに永続化され、日常の一覧が常に「いま聴けるもの」になること
- サードパーティ実行時依存ゼロ(信頼ベンダーをGoogle + JetBrainsの2社に限定)
- AI駆動開発を機械的に統制できる品質ゲート(コンパイル時検証・リンター・カバレッジ)

**Non-Goals:**

- ストリーミング再生、自動DL、定期バックグラウンド更新(WorkManager不使用)
- 番組検索・ディスカバリー、複数端末同期、ソーシャル機能
- 設定画面(定数変更はソースコード編集で行う。「ソースコードが設定ファイル」)
- 複数端末・旧OSバージョン対応(minSdk = targetSdk = 36 固定、互換性分岐禁止)
- OPMLインポート(移行は手入力+一括視聴済みで行う)、お気に入りUI(スキーマのみ先行)

## Decisions

### D1: Androidネイティブ(Kotlin + Jetpack Compose)

- 代替案: PWA + CORSプロキシ / Capacitor → 却下。RSS取得のCORS制約と、バックグラウンド再生の信頼性(フォアグラウンドサービス保護の有無)が理由。Web標準の限界を確認した上での選択。

### D2: 再生はMedia3一式(ExoPlayer + MediaSession + MediaSessionService)

- バックグラウンド再生・通知・ロック画面・Bluetooth・オーディオフォーカス・ピッチ保持付き速度変更をすべて担う。
- 代替案: framework MediaPlayer自作 → 却下。端末依存の罠が多く品質が出ない。Media3はAndroid Security Bulletin水準のパッチ体制がある。

### D3: DBはRoom、リアクティブの背骨はRoomのFlow

- 代替案: SQLiteOpenHelper直書き → 却下。AI実装の自己修正ループにはコンパイル時SQL検証が決定的に有利(SQLのtypo・型不一致がビルド時に露見する)。
- 「DBを書けば画面が追随する」を基本構造とし、✓トグル→DB更新→一覧・フィルターの自動更新を宣言的に成立させる。

### D4: 通信・パースはOS標準APIの自作(HttpURLConnection + XmlPullParser)

- 代替案: OkHttp → 不要(Media3のデフォルトネットワーク層もHttpURLConnection)。Coil等の画像ライブラリ → BitmapFactory + 自前簡易キャッシュで代替。
- サプライチェーン対策: リポジトリを google() / mavenCentral() に固定、Gradle dependency verification導入、バージョン完全固定(動的バージョン禁止)。

### D5: 4層パッケージ + 手動DI + UseCaseレス(単一モジュール)

```
ui/     Compose画面 + ViewModel
logic/  純粋Kotlin(Android API依存ゼロ)— 視聴済み判定、削除対象決定、
        フィルター条件、RSS構造の解釈 等「判断」のすべて
data/   Room・HTTP・XmlPullParser・ファイル管理(「実行」)
player/ Media3の結線。再生イベントをdata層へ書き戻す

依存の向き: ui → logic/data/player、data・player → logic
禁止(detekt ForbiddenImportで機械強制): logic→他層、ui→Room直接、logic→android.*
```

- 「判断(logic)と実行(data)の分離」はモックレス・RobolectricレスのテストStrategyの前提条件。崩さない。
- DIフレームワーク・UseCaseクラス層・マルチモジュールは規模に対して儀式なので作らない。
- **モデルの置き場所**: logic/ が `androidx.*` を import できない(detektが機械的に拒否する)以上、Roomのエンティティを logic/ の関数がそのまま扱うことはできない。したがって:
  - `logic/model/` — アノテーションのない純粋なドメインモデル(Feed / Episode)。判断ロジックの入出力はこれ
  - `data/db/` — Roomのエンティティ(`@Entity`)とDAO。エンティティ↔ドメインモデルの変換関数を同居させる
  - 重複はモデル2つ分の小さなコストで、logic層の純粋性(=テスト戦略の前提)を守るための必要経費と判断する

### D6: データモデルの先行投資

- episodeテーブルに `favorite` カラムを最初から持つ(UIなし)。削除ロジックは初日から `played AND NOT favorite` で書く。後日お気に入りUIを足すときの変更を★ボタンのみにするため。
- feedテーブルにフィルター設定2カラム(番組ごと永続化)と `playback_speed` を持つ。

### D7: 削除のタイミングは経路で非対称

- 自動判定(残り10秒)経由: 即時削除。ユーザーは確実に最後まで聴くため誤発動リスクなし。
  - **実装上の但し書き**: 「視聴済みにする」のは閾値到達の瞬間(フィルターに即座に反映させるため)だが、**ファイルの削除はプレイヤーがそのエピソードから離れてから**行う。再生中のファイルを消すと、以後のシーク時に ExoPlayer がファイルを開き直せず失敗しうるため。利用者から見た挙動は「自動で消える」ままで変わらない
- 手動マーク経由: 5秒のUndoスナックバー猶予後に削除。誤タップが唯一の非可逆事故経路(古いエピソードはフィードから消え再DL不可の場合がある)のため、そこだけに安全弁を置く。

### D8: 通信は例外なくユーザー起点

- 起動時自動更新なし・自動DLなし・自動再試行なし。自動継続再生はDL済みのみを対象としスキップ(未DLを勝手に取得しない)。
- モバイル回線でのDLタップ時のみ確認ダイアログ(サイズ表示付き)。Wi-Fi時は無確認で即DL。

### D9: テスト戦略 — モックレス・Robolectricレス

- モックライブラリ(MockK/Mockito)不使用。リポジトリはinterface + 手書きフェイク(Google公式ガイダンスもフェイク推奨。AIにとってもただのKotlinクラス)。
- Robolectric不使用。logic/を純粋KotlinにすることでテストのJVM率を最大化。Android依存(Room DAO・XmlPullParser)は実機での計装テスト。
- カバレッジはKover。logic/のみ行90%ゲート(下回ればビルド失敗)、他層は計測のみ。
- JUnit 4はテスト系で唯一の第三者依存として許容(AndroidX Test/Compose testの前提。凍結済みでAPK非同梱)。

### D10: リンター/フォーマッター — AI統制を機械化する

- ktfmt(設定不可・決定論的。KotlinConf 2026でKotlin org移管が発表された事実上の次期公式)。ktlintは不採用(役割吸収・コミュニティ管理化)。
- Android Lint: warningsAsErrors + abortOnError。コンパイラも allWarningsAsErrors。
- detekt 2.0.0-alpha 系(第三者依存だがAI悪癖の機械的ブロックとして価値が上回ると判断):
  - **当初 1.23.x を選定したが、実装着手時の調査で撤回した。** detekt 1.23.8(最終安定版)の公式サポート範囲は Gradle 8.12.1 / Kotlin 2.0.21 / AGP 8.8.1 までで、Kotlin 2.3系のメタデータを処理できず(issue #8865)、AGP 9 で削除されたクラスを参照する(issue #8532)。かつ 1.23.8 はメンテナンス終了が明記されており、「メンテが続いているものが望ましい」という選定基準にも反する。1.23.8 を使うとツールチェーン全体を約1年古い状態に恒久的に固定することになるため、build時のみ・APK非同梱・バージョン固定という条件下で alpha を許容する方が総合的に有利と判断した
  - detekt 2.0 はコンパイラプラグイン化により高速かつ type resolution が標準。座標が `dev.detekt` に変更されている
  - **フォールバック方針**: 使用予定の Kotlin 版(KSP の制約で 2.3.10 が上限)と detekt 2.0 alpha が噛み合わない場合は detekt 導入を見送り、Android Lint + allWarningsAsErrors + ktfmt で当面運用する。detekt は 2.0 安定後にプラグイン1行 + yml で後付けでき、rework は発生しない
  - 例外系: SwallowedException / TooGenericExceptionCaught / EmptyCatchBlock / ReturnFromFinally
  - スタブ系: ForbiddenComment(TODO/FIXME/HACK禁止 = 未完成コードのコミット禁止)
  - 肥大系: LongMethod / CyclomaticComplexMethod / NestedBlockDepth / LongParameterList(デフォルト閾値始まり、実測後調整)
  - 層違反: ForbiddenImport(D5の依存ルールをYAML化)。detektは1ルール1定義のためスコープを1つしか持てず、最重要の「logic層の純粋性」(`includes: **/logic/**`)に適用する。`ui→Room直接` の禁止はCLAUDE.mdの規約とレビューで担保する
  - 実効性は導入時に検証済み: logic層にAndroid APIのimportを混入させるとビルドが失敗することを確認した
  - baseline不使用(greenfield)・detekt-formatting不使用
- CLAUDE.mdに同じルールを事前指示として記載(事後検出と事前指示の両輪)。

### D11: リポジトリ・CI・配布

- GitHub公開リポジトリ `podcast-player`、MITライセンス。秘密情報を扱う機能が構造的に存在しないため公開可。
- 最小CI(GitHub Actions): build + JVMテスト + 静的解析 + Kover検証。actionsはSHA固定。計装テストはCIに載せない(ローカル実機)。
- 配布: Android StudioからUSB直接インストール。applicationId `dev.thinkami.podcastplayer`(変更不可のため初回確定)。
- 開発環境はローカルAndroid Studio。DevContainerは調査の結果不採用(Apple SiliconでのLinux arm64ビルドツール非対応・Docker DesktopのUSBパススルー不可・隔離の益が小さい。ビルド再現性はCIが担保)。

## Risks / Trade-offs

- [世のRSSフィードは仕様違反が多く、自作パーサーが実フィードで躓く] → 購読予定の実フィードをテストフィクスチャ化して開発初期に検証。パースは「壊れた項目はスキップして続行」を原則とする
- [視聴済み自動判定(残り10秒)の誤発動でファイルが消え、古いエピソードは再DL不可] → 閾値10秒は「確実に最後まで聴く」実態に基づく。enclosure URLはDBに保持し再DLの可能性を残す。手動経路にはUndo
- [detekt/ktfmtのビルド時依存はサードパーティゼロ方針の例外] → APK非同梱・バージョン固定・dependency verification対象とすることで受容
- [detekt 2.0 が alpha であり、安定版までに設定形式やルール名が変わりうる] → build時のみの依存でAPKに影響しない。バージョンを固定し、2.0安定版リリース時に一度だけ移行する。噛み合わない場合はdetektなしで開始するフォールバックを用意済み
- [KSP が Kotlin 2.4 系に未対応(最新 KSP 2.3.10)のため Kotlin の上限が 2.3.10 に制約される] → Room が KSP を必要とするため受容。KSP 追従後に Kotlin を上げる
- [ktfmtのKotlin org移管は進行中で座標や運用が変わりうる] → 変わった時点で追従。決定論的フォーマットという性質は移管後も不変
- [計装テストがCIにないため、DAO/パーサーの回帰はローカル実行忘れで漏れうる] → apply時の実装ワークフローに「計装テスト実行」を明示的タスクとして含める
- [Media3のMediaSessionService結線は学習量が多い] → 定型パターンが確立しており公式ドキュメント・サンプルが豊富。AI実装との相性は良い

## Open Questions

(なし — 探索セッションで網羅的に解決済み。実装中に生じた判断はこのdesignの思想「機能を増やさない」「通信はユーザー起点」「判断と実行の分離」に照らして行う)
