# Proposal: register-feed-from-pc

## Why

購読登録は現状 Android 端末上で RSS URL を検索・コピペする手作業のみで、PC で見つけたフィードを登録するのに手間がかかる。アプリに転送 UI を追加するのではなく、計装テストと同じ信頼境界(USB デバッグを許可した PC = adb)から登録できる経路を作る。

また、この受け口のセキュリティレビューの過程で、入口の追加とは独立に既存の欠陥を 2 件発見した: (1) `HttpFetcher` が URL スキームを検証せず、`file://` 等の URL(手入力・フィード内の artworkUrl / enclosureUrl のいずれからも到達可能)で `ClassCastException` クラッシュする、(2) `Accept-Encoding` を手動設定しているため OS の透過 gzip 解凍が無効化され、サーバーが gzip 応答を返すと RSS パースが失敗する(音声なら壊れたファイルが保存される)。いずれも同じ `HttpFetcher` 境界の問題なので、本 change でまとめて解消する。

## What Changes

- **adb 経由の購読登録**: `exported="false"` の BroadcastReceiver を追加し、PC から
  `adb shell run-as dev.thinkami.podcastplayer am broadcast … --es feed_url <URL>` で既存の
  `FeedRepository.subscribe()` に接続する。アプリ内 UI・設定は一切追加しない。
  他アプリは `exported="false"` により構造的に遮断され、信頼境界は「adb 認証済み PC」
  (debuggable ビルド前提。リリースビルドでは `run-as` ごと無効化される fail-closed)
- **PC 側スクリプト + AI スキル**: 契約(コンポーネント名・extra キー)をリポジトリ内
  `scripts/` のスクリプトに一元化し、クォート処理・前提チェックを決定論化する。
  AI スキルは「必ずこのスクリプトを実行する」ルーティングのみの薄い層とする
- **URL スキーム検証**: `https://` のみ許可(テスト用に `http://127.0.0.1` の loopback 例外)。
  判定は `logic/` の純粋関数、接続前の強制は `data/` の `HttpFetcher`。
  既存のクラッシュ経路(購読 URL・artworkUrl・enclosureUrl の 3 入口)を一括で閉じる
- **gzip の書き分け**: `fetchText`(RSS)は手動 `Accept-Encoding` を削除して OS の透過 gzip に
  任せる(転送量削減は維持)。`fetchStream`(音声・画像)は `Accept-Encoding: identity` を明示して
  Content-Length を保持し、identity を無視して圧縮応答を返す非準拠サーバーには `IOException` を
  投げるガードを置く(壊れたファイルの黙殺保存を根絶)
- **テスト基盤**: androidTest に `ServerSocket` 手書きの Fake HTTP サーバー(端末内 loopback 完結、
  モックライブラリ不使用)。`instrumented` ビルドタイプ限定の network security config で
  127.0.0.1 への平文 HTTP を許可(日常ビルドの平文遮断は不変)
- **前提スパイク**: `run-as` 配下の `am broadcast` が `exported="false"` Receiver に届くことを
  実機で検証してから Receiver 実装に着手する。通らない場合は登録経路のみ再設計し、
  スキーム検証・gzip 対応は独立に成立する

## Capabilities

### New Capabilities

- `http-fetching`: HTTP 取得の横断的な制約 — URL スキームの許可範囲(https + loopback 例外)、
  テキスト取得の透過 gzip、ストリーム取得の identity 強制と非準拠応答の拒否

### Modified Capabilities

- `feed-subscription`: 購読登録の第 2 経路(adb 経由)の追加。登録 URL が https に限定される
  ことの明文化(スキーム外 URL はクラッシュではなくエラー扱い)

## Impact

- `app/src/main/kotlin/.../data/net/HttpFetcher.kt` — スキーム強制・gzip 書き分け
- `app/src/main/kotlin/.../logic/` — URL スキーム判定の純粋関数を追加(JVM ユニットテスト +
  Kover カバレッジ対象)
- `app/src/main/kotlin/.../` — BroadcastReceiver 追加、`AndroidManifest.xml` に
  `exported="false"` で登録
- `app/src/instrumented/` — network security config(127.0.0.1 平文許可)の manifest オーバーレイ
- `app/src/androidTest/` — Fake HTTP サーバーと HttpFetcher / Receiver の計装テスト
- `scripts/` — PC 側登録スクリプト(新規ディレクトリ)
- `.claude/skills/` — スクリプト実行へルーティングする AI スキル
- ランタイム依存の追加: なし(すべて OS 標準 API)。テスト依存の追加もなし
