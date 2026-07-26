# Design: register-feed-from-pc

## Context

購読登録の唯一の経路は購読一覧画面のダイアログへの手入力である。PC で見つけた RSS URL を
登録するには端末へ URL を運ぶ手作業が要る。転送 UI をアプリに足すことは設計思想
(機能を増やさないことが機能)に反するため、計装テストと同じ「USB デバッグを許可した PC」
という既存の信頼境界を登録経路として使う。

検討過程のセキュリティレビューで、`HttpFetcher` に 2 つの既存欠陥を確認した:

1. **スキーム未検証**: `URL(url).openConnection() as HttpURLConnection` は `file://` 等で
   `ClassCastException`(IOException でないため ViewModel の catch を素通りしてクラッシュ)。
   入口は購読 URL・フィード内 artworkUrl・enclosureUrl の 3 つで、悪意ある/壊れたフィードを
   1 つ購読するだけで更新のたびにクラッシュする経路が存在する
2. **gzip 解凍漏れ**: `Accept-Encoding: gzip, deflate` の手動設定により Android
   (OkHttp ベース)の透過 gzip 解凍が無効化される。サーバーが gzip 応答を返すと
   `fetchText` はバイナリを文字列化してパース失敗、`fetchStream` は圧縮バイトのまま保存する

なお、パストラバーサル(保存名は DB 行 ID 由来)・XSS(ショーノートはプレーンテキスト化)・
SQL インジェクション(Room バインド)・XXE(KXmlParser は外部エンティティ非解決)・
平文 HTTP(targetSdk 36 デフォルト遮断)は問題なしを確認済み。

## Goals / Non-Goals

**Goals:**

- PC から 1 コマンド(スクリプト経由)で購読登録できる
- 端末内の他アプリからこの経路を構造的に(設定や秘密でなく仕組みで)遮断する
- `file://` 等によるクラッシュ経路を 3 入口まとめて閉じる
- gzip 起因のパース失敗・破損保存を構造的に排除しつつ、RSS の転送量削減(ギガ節約)は維持する
- 上記をモックライブラリなし・ランタイム依存追加なしでテスト可能にする

**Non-Goals:**

- アプリ内 UI・設定の追加(転送画面、登録履歴などは作らない)
- 複数 URL の一括受信(必要なら PC 側スクリプトのループで足りる)
- adb への購読成否の返却(配達確認まで。最終結果は既存の画面追随・Snackbar で見る)
- OPML インポート/エクスポート
- 案 B(adb による UI 遠隔操作)の整備

## Decisions

### D1: 登録経路は `exported="false"` BroadcastReceiver + `run-as`

```
PC (scripts/add-feed.sh)
  └─ adb shell run-as dev.thinkami.podcastplayer \
       am broadcast -n dev.thinkami.podcastplayer/.AdbFeedReceiver --es feed_url <URL>
            │  run-as により呼び出し uid = アプリ自身の uid
            ▼
  AdbFeedReceiver (exported="false")  ← 同一 uid 以外は OS が配達を拒否
            │  extra を trim / 空なら無視 / スキーム検証
            ▼
  FeedRepository.subscribe()  (既存経路に合流。新しい判断ロジックなし)
```

- **なぜ Receiver か**: Activity と違い画面を持たず、バックグラウンド起動制限とも無縁。
  受け取って既存経路に流すだけの最小の受け口
- **なぜ exported=false + run-as か**: 素朴な exported な受け口は端末内の全アプリに開き、
  「通信は例外なくユーザー起点」に反する第三者起点の通信を許してしまう。
  `exported="false"` は同一 uid 以外を OS レベルで遮断し、`run-as` は debuggable ビルドに
  対して adb だけが同一 uid になれる。信頼境界が計装テストと同一(USB デバッグを許可した PC)
  になり、コード内秘密や obscurity に依存しない
- **debuggable 前提の明示**: 日常ビルド(installDebug)が debuggable であることがこの経路の
  成立条件。adb 認証済み PC は元々 `run-as` で全データを読み書きできるため、この受け口が
  adb 保持者に与える追加権限は実質ゼロ。リリースビルドでは `run-as` ごと使えなくなり
  fail-closed
- **代替案**: (a) MainActivity の intent extra — exported 必須のため他アプリに開く。却下。
  (b) signature 権限ガード — shell が署名権限を持てず adb も遮断される。却下。
  (c) adb で UI 遠隔操作(案 B)— アプリ変更ゼロだが UI 文言がスクリプトの API になり脆い。却下

### D2: 契約はリポジトリ内スクリプトに一元化し、AI スキルは薄いルーティングに限定

- コンポーネント名・extra キーという PC↔アプリ間の契約を `scripts/` のスクリプトに置き、
  Receiver の変更と同じコミットで追随できるようにする(エイリアス等のリポジトリ外設置は
  ドリフトの温床)
- スクリプトが担う決定論的処理: 引数検証(https か)、adb 接続・アプリ存在の前提チェック、
  PC シェル/端末シェルの二重クォート処理、`am broadcast` の配達結果報告
- AI スキルは「フィード登録はこのスクリプトを実行する。adb コマンドを即興で組み立てない」の
  ルーティングのみ。手順ロジックをスキル(モデルが解釈する文書)に書くと非決定性が再侵入する
  ため、人間が直接実行しても同一動作になるようロジックは全てスクリプト側に置く
- スクリプトの https チェックはアプリ側検証の複製だが、PC 側での早期フィードバックとして許容。
  正は `logic/` の判定関数であり、スクリプト側は早期警告という主従とする

### D3: URL スキーム検証は `logic/` の純粋関数、強制は `HttpFetcher`

- 許可 = `https://…` + `http://127.0.0.1…`(loopback 例外。D5 のテストに必要)
- 判定(「このスキームは許可か」)は Android API 非依存の判断なので `logic/` に置き、
  JVM ユニットテストで全数検証・Kover カバレッジ対象にする。強制(接続前に拒否して
  `IOException` を投げる)はチョークポイントの `HttpFetcher` に置く。入口ごと(ダイアログ・
  Receiver・フィード内 URL)の検証でなく 1 箇所で守る
- loopback 例外を本番に残す安全性: 本番ビルドには D5 の平文許可がないため、仮にフィードが
  `http://127.0.0.1:…` を仕込んでも OS の cleartext 遮断で接続自体が失敗する。
  アプリの判断(スキーム検証)と OS の強制(cleartext 遮断)の二重壁
- 拒否は `IOException`(またはそのサブクラス)で表現し、既存の ViewModel の catch 経路に乗せる

### D4: gzip は fetchText / fetchStream で書き分ける

- `fetchText`(RSS XML): 手動 `Accept-Encoding` を削除 → OS が自動で gzip を要求し
  透過解凍する。テキストは圧縮が 1/5〜1/10 に効くため転送量削減を維持しつつ、
  解凍漏れが構造的に消える
- `fetchStream`(音声・画像): `Accept-Encoding: identity` を明示。既に圧縮済みのメディアに
  gzip は効かず(配信側も Range 互換性のため圧縮しない)、identity 明示により
  Content-Length が常に保持され進捗表示の分母が確実になる。さらに応答の
  `Content-Encoding` が identity 以外なら `IOException` を投げるガードを置き、
  非準拠サーバーの圧縮応答を壊れたファイルとして黙って保存する事故を根絶する
- **なぜ一律削除(書き分けなし)にしないか**: 正しさの根拠がすべて OS の暗黙挙動になり、
  テストがプラットフォーム挙動の検証に偏る。identity 明示は「アプリが送るリクエスト」という
  自分のコードの出力を assert でき決定論的。透過 gzip 発動時に Content-Length が隠されて
  -1 になる分岐(進捗表示への波及)も排除できる

### D5: テストは計装テスト + `ServerSocket` 手書き Fake サーバー

- Android の `HttpURLConnection` は OkHttp ベース、JVM のそれは素の JDK 実装で透過 gzip の
  挙動が異なる。JVM ユニットテストでは本番と別物を検証してしまうため、`HttpFetcher` の
  テストは androidTest(実機)に置く
- Fake サーバーは `ServerSocket` 手書き(数十行・OS 標準 API のみ)。テストプロセス内で
  127.0.0.1 の空きポートに bind し、受信リクエストヘッダを記録、用意した応答バイト列を返す。
  テストメソッドと同寿命で、テスト APK にのみ含まれる。外部ネットワーク不要で決定論的。
  MockWebServer(Square 製)はベンダー方針(Google/JetBrains のみ)により使わない
- `src/instrumented/` にのみ network security config のオーバーレイを置き、127.0.0.1 に限り
  平文 HTTP を許可する。日常の debug ビルド・リリースビルドの平文遮断は不変。
  計装テストを別パッケージに隔離した既存設計と同じ build type 単位の分離

## Risks / Trade-offs

- [`run-as` 配下の `am broadcast` が exported=false Receiver に届かない可能性
  (Android バージョン固有の挙動差)] → 実装前スパイクとして tasks の先頭で実機検証する。
  不成立なら D1 のみ再設計(exported な受け口 + 実害許容、または案 B へ後退)。
  D3/D4/D5 は独立に成立する
- [Receiver での購読はアプリプロセス外から起きるため、失敗(重複登録・取得失敗)が
  PC 側から見えない] → 意図した制約(Non-Goal)。配達確認はスクリプトが報告し、
  結果は端末画面の既存フィードバック(Snackbar・一覧追随)で確認する
- [スキーム検証の厳格化により、既存の `http://` フィード購読が明示的にエラーになる] →
  挙動の実質変化なし(現状も OS の cleartext 遮断で失敗している)。エラーの出所が
  OS からアプリの検証に変わり、メッセージはむしろ明確になる
- [identity ガードにより、identity を無視して圧縮を返す非準拠サーバーからの DL が
  失敗するようになる] → 意図した fail-closed。壊れた mp3 の黙殺保存より明示的な失敗を選ぶ
- [Receiver 追加により Kover/detekt のゲートに新コードが乗る] → Receiver は受け取って
  委譲するだけの薄いグルーコードに保つ(`ui/` `player/` と同じ扱い。判断はすべて `logic/`)

### D6: スパイク検証で確定した呼び出し契約(実機 Pixel 7 Pro / Android 16 で確認済み)

タスク 1.1 のスパイクにより D1 の成立を確認した。あわせて、設計時に想定していなかった
3 つの制約が判明したため契約として確定する:

1. **`--user 0` の明示が必須**: `am broadcast` はデフォルトで `USER_CURRENT`(-2)を指定するが、
   その解決には `INTERACT_ACROSS_USERS` 権限が要り、アプリ uid は持たないため
   `SecurityException` になる。`--user 0` を明示すると通る。スクリプトはこれを必ず付ける
2. **URL は端末側シェル用にシングルクォートで包む**: `adb shell` は引数を連結して端末側シェルに
   渡すため、`&` を含む URL は端末側で切断される(実測: `...?a=1&b=2` が `...?a=1` になった)。
   PC 側のクォートとは別に、端末側シェルが見るクォートが必要
3. **配達確認は結果コードで行う**: `am broadcast` は配達されなくても `result=0` を返すため
   終了コードでは確認できない(実測: run-as なしの遮断時も `result=0`)。Receiver 側で
   `resultCode` / `resultData` を設定すると `am` の出力に伝播する(実測:
   `Broadcast completed: result=42, data="delivered"`)。スクリプトはこの出力を検査して
   配達成否を判定する

確定コマンド形:

```
adb shell run-as <pkg> am broadcast --user 0 -n <pkg>/.AdbFeedReceiver --es feed_url "'<URL>'"
```

遮断側も実測で確認済み: `run-as` を外すと、特権的な shell uid であっても配達されない
(marker 未作成・`result=0`)。他アプリは shell uid より権限が低いため当然遮断される。

### D7: 購読結果は resultData で返す(実装中に判明したギャップへの対応)

当初の設計は「配達確認まで。購読の成否は端末画面で見る」としていたが、実装時に前提の誤りが
判明した。Receiver には画面がなく、失敗(取得エラー・重複登録)は購読一覧にも Snackbar にも
現れない。「画面で確認できる」のは成功時(番組が増える)だけで、失敗は完全に無音になる。
一方で例外の握りつぶしはプロジェクトの禁止事項である。

したがって、D6 で既に確立している `resultCode` / `resultData` に購読結果も載せる。新しい
仕組みは増えない。スクリプトは broadcast の 1 往復だけで結果を得る(購読状態のポーリングや
DB の直接参照はしない)。例外の扱いは画面からの登録([SubscriptionListViewModel])と同じ粒度
(IOException / IllegalStateException / IllegalArgumentException)に揃える。

あわせて実行時間の上限を設ける。スパイクで確認したとおり `am broadcast` は
`FLAG_RECEIVER_FOREGROUND`(実測 `flg=0x400000`)を立てるため、Receiver の実行には約10秒の
制限がある。`HttpFetcher` の接続15秒・読み取り30秒はこれを超えうるので、購読処理を8秒で
区切って結果を返す。制限に達して ANR 扱いになるより、時間切れを明示して再実行できるほうがよい。

- **トレードオフ**: 遅いフィードでは処理が中断され、番組行だけが作られてエピソードが不完全に
  なる可能性がある。pull-to-refresh で解消できるため許容する

## Open Questions

- Receiver 内での coroutine 起動方法(`goAsync()` + 既存の Application スコープ利用を想定。
  実装時に既存の DI 結線(appContainer)を確認して決める)
