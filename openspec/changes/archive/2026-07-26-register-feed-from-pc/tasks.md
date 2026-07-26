# Tasks: register-feed-from-pc

## 1. 前提スパイク(D1 の成立条件検証)

- [x] 1.1 実機で `run-as` 配下の `am broadcast` が `exported="false"` Receiver に届くことを検証する
      → **成立**。あわせて 3 つの契約詳細が判明(design D6 に記録): `--user 0` が必須 /
      URL は端末側シェル用にシングルクォートで包む / 配達確認は Receiver の resultCode で行う
      (`am` は非配達でも result=0 を返す)。遮断側も実測確認(run-as なしでは shell uid でも不達)

## 2. URL スキーム検証(logic + HttpFetcher)

- [x] 2.1 `logic/` に URL スキーム判定の純粋関数を追加する(https 全許可 +
      `http://127.0.0.1` の loopback 例外。android/androidx import なし)
- [x] 2.2 判定関数の JVM ユニットテストを書く(file:// / http:// 非 loopback / 未知スキーム /
      loopback の各ケース。Kover 90% ゲート対象)
- [x] 2.3 `HttpFetcher` の接続前に判定を強制し、許可外は `IOException` 系で拒否する
      (`ClassCastException` 経路の閉鎖。既存の ViewModel の catch に乗ることを確認)

## 3. gzip の書き分け(HttpFetcher)

- [x] 3.1 `fetchText` の手動 `Accept-Encoding` 設定を削除する(OS の透過 gzip に任せる)
- [x] 3.2 `fetchStream` に `Accept-Encoding: identity` を明示し、応答の `Content-Encoding` が
      identity 以外なら `IOException` を投げるガードを追加する

## 4. テスト基盤と計装テスト

- [x] 4.1 androidTest に `ServerSocket` 手書きの Fake HTTP サーバーを実装する
      (loopback 限定・空きポート自動割当・受信リクエストヘッダの記録・応答バイト列の指定)
- [x] 4.2 `src/instrumented/` に network security config オーバーレイを追加し、127.0.0.1 に限り
      平文 HTTP を許可する(日常 debug ビルドに含まれないことを確認)
- [x] 4.3 `HttpFetcher` の計装テスト: gzip 応答の `fetchText` が解凍済みテキストを返す
- [x] 4.4 `HttpFetcher` の計装テスト: `fetchStream` のリクエストに identity が含まれ、
      Content-Length が consumer に渡り、gzip 応答には `IOException` を投げる
- [x] 4.5 スキーム検証の計装テスト: 許可外 URL が `IOException` で拒否される
      (クラッシュしない)ことを `HttpFetcher` 経由で確認する

## 5. adb 登録経路(Receiver)

- [x] 5.1 `AdbFeedReceiver` を実装する(`exported="false"` で manifest 登録。extra を
      trim・空なら無視し、既存の `FeedRepository.subscribe()` へ委譲する薄いグルーコード。
      coroutine 起動は `goAsync()` + appContainer のスコープを確認して決定。
      D6 に従い配達確認用の `resultCode` / `resultData` を設定する)
- [x] 5.2 Receiver の計装テスト: 有効 URL の broadcast で購読が追加される /
      空・欠落 extra では何も起きない(Fake サーバーのフィードを購読対象にする)
- [x] 5.3 実機で他アプリ遮断を手動確認する(`run-as` なしの `adb shell am broadcast` が
      配達されないこと)

## 6. PC 側スクリプトと AI スキル

- [x] 6.1 `scripts/add-feed.sh` を実装する(https 早期検証・adb 接続とアプリ存在の
      前提チェック・D6 の確定コマンド形(`--user 0` + シングルクォート包み)での実行・
      `am` 出力の resultCode 検査による配達判定)
- [x] 6.2 実機で E2E 確認する(スクリプト実行 → 購読一覧に番組が現れる。`&` `?` を含む
      URL でも URL が変形しないこと)
- [x] 6.3 AI スキルを追加する(スクリプト実行へのルーティングのみ。手順ロジックを
      スキル側に書かない)

## 7. 仕上げ

- [x] 7.1 `./gradlew check` と `./gradlew connectedAndroidTest` が通ることを確認する
- [x] 7.2 CLAUDE.md に adb 登録経路の存在(スクリプトとスキルの場所・debuggable 前提)を
      1〜2 行で追記する
