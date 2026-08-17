## Why

「セキュリティのアレ」の 2021 年以前の回(342 件中 73 件)は enclosure URL が平文 `http://` で配信されており、
アプリの URL スキーム検証(https のみ許可)で通信前に拒否されるため DL できない。ところが行には
固定文言「DL失敗 ・ タップでやり直す」しか出ないため、再タップで直るかのように読めてしまい、原因の
特定に端末 DB の読み出しが必要だった。配信サーバー自体は同じパスを https でも返すことを確認済みなので、
取得時にスキームを https へ書き換えれば取り込める。

## What Changes

- **(B) DL 失敗理由の表示** — `DownloadState.Failed` を「理由の種別」を持つ型にし、行の 2 行目に
  種別ごとの短い理由(例: `DL失敗(取得できないURL)`、`DL失敗(HTTP 404)`)を出す。再試行しても
  無意味な種別(取得できない URL・保存失敗)では末尾の「タップでやり直す」を出さない。
  種別 → 表示文言の対応は `logic/` の純粋関数にする。URL 全文など長い詳細は行に出さない。
- **実機確認の checkpoint** — B の実装後、A に着手する前に利用者が実機で第5回
  「緊急特番的な感じでペチャクチャやろうぜ!スペシャル」の行表示を確認する。A を先に入れると
  実データで「取得できないURL」の表示を再現できなくなるため、この順序を tasks.md で固定する。
- **(A) 平文 http の https への書き換え** — loopback 以外の `http://` URL は取得直前に `https://` へ
  書き換えて接続する。書き換えは通信の唯一の出口(`HttpFetcher`)で行い、判断は `logic/` の純粋関数に
  置く。https 非対応ホストは今と同じく失敗する(悪化しない)。DB に保存された URL は書き換えない。
- 平文 HTTP を許可する(cleartext 許可・ポリシー緩和)ことは**しない**。

## Capabilities

### New Capabilities

なし

### Modified Capabilities

- `episode-download`: 「失敗時は手動再試行のみ」の要件に、失敗理由の種別を短く表示すること、
  再試行が無意味な種別では再試行の案内を出さないことを追加する
- `http-fetching`: 「URL スキーム検証」の要件を、loopback 以外の `http://` を拒否ではなく `https://` へ
  書き換えて取得する形に変更する(`file://` 等の許可外スキームの拒否は従来どおり)

## Impact

- `logic/HttpUrlPolicy`(書き換え判断の追加)、`logic/` に失敗種別 → 文言の純粋関数を追加
- `data/net/HttpFetcher`(接続直前の書き換え)、`data/download/DownloadState` / `EpisodeDownloader`
  (失敗種別の分類)
- `ui/episodes/EpisodeRow`(2 行目の文言)、`ui/EpisodeActionMapping` と関連テストの型追随
- 既存の JVM テスト(`HttpUrlPolicyTest`)、計装テスト(`HttpFetcherTest`・`EpisodeRowTest`)の更新
- 依存追加なし。DB スキーマ変更なし
