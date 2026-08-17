## Context

- `logic/HttpUrlPolicy.isAllowed` は https と loopback 以外を拒否し、`data/net/HttpFetcher.connect` が
  `UnsupportedUrlException`(IOException)を投げる。`EpisodeDownloader` は IOException を一括で捕捉して
  `DownloadState.Failed(reason: String)` にするが、`ui/episodes/EpisodeRow` は理由を無視して
  「DL失敗 ・ タップでやり直す」の固定文言を出す。
- 「セキュリティのアレ」の 73 件は enclosure が `http://tsujileaks.com/...` / `http://www.tsujileaks.com/...`。
  同一パスを https で取得できることを Mac から確認済み(200, `audio/mpeg`)。
- Manifest に cleartext 許可はなく、targetSdk 36 の既定で平文 HTTP は OS が遮断する。この前提は変えない。
- 統合エピソード画面(`EpisodeDetailScreen`)は失敗理由を出しておらず、失敗時はアクションが
  「再DL」ボタンに変わるだけ。

## Goals / Non-Goals

**Goals:**
- 一覧の行で失敗の種別が判別でき、再試行が無意味な失敗では「タップでやり直す」を出さない
- 平文 `http://` の enclosure / artwork / フィード URL を https に書き換えて取得できるようにする
- 種別の分類・文言・URL 書き換えの判断はすべて `logic/` の純粋関数にし、JVM テストで固定する
- B の実装後に利用者が実機で「取得できないURL」の表示を確認できる順序を tasks.md で保証する

**Non-Goals:**
- 平文 HTTP を実際に流すこと(cleartext 許可・ポリシー緩和)
- 統合エピソード画面への失敗理由の表示(行で足りる。必要になったら別 change)
- DB 上の URL の書き換え、フィード再取得時の正規化(取得の出口でだけ書き換える)
- 自動リトライ、失敗の永続化(`Failed` は今までどおりプロセス内の状態)
- フィード申告 `length` と実 Content-Length の食い違い(進捗が 100% を超えるだけ)への対処

## Decisions

### D1. 失敗の種別は `logic/` の sealed class にし、`Failed` はそれを持つ

`logic/DownloadFailure`(sealed):
`UnsupportedUrl` / `HttpStatus(code: Int)` / `Connection` / `CompressedResponse` / `Save`。
`DownloadState.Failed(failure: DownloadFailure, detail: String?)` に変更する(`detail` は例外メッセージ・
URL 全文などの長い情報。行には出さない。ログや将来の詳細表示に備えて保持するだけ)。

- 代替: `Failed(reason: String)` のまま UI で文字列を整形 → 例外メッセージの書式に UI が結合し、
  種別の全数列挙テストが書けない。却下
- 代替: enum + 別フィールドで status → HTTP 以外では無意味なフィールドが残る。sealed のほうが素直

### D2. 種別 → 文言・再試行案内の判断は純粋関数 `DownloadFailurePresentation`

`logic/DownloadFailurePresentation.label(failure): String` と `.suggestsRetry(failure): Boolean`。
文言(コード内定数、設定にしない):

| 種別 | label | suggestsRetry |
|---|---|---|
| UnsupportedUrl | `DL失敗(取得できないURL)` | false |
| HttpStatus(n) | `DL失敗(HTTP n)` | true |
| Connection | `DL失敗(接続できず)` | true |
| CompressedResponse | `DL失敗(非対応の応答)` | true |
| Save | `DL失敗(保存できず)` | false |

`EpisodeRow.episodeSubtitle` は `label` に、`suggestsRetry` のときだけ「 ・ タップでやり直す」を足す。
`suggestsRetry=false` でもタップ自体は今までどおり再試行になる(`EpisodeActions` の RETRY_DOWNLOAD は
不変)。案内を消すだけで、操作を奪わない。文言はすべて全数列挙のユニットテストで固定する。

### D3. 例外 → 種別の分類は `data/` に置き、HTTP ステータスは型付き例外にする

`HttpFetcher` の `IOException("HTTP $status: $url")` を `HttpStatusException(status, url): IOException` に
変える(メッセージは従来どおり)。`EpisodeDownloader` は
`UnsupportedUrlException → UnsupportedUrl`、`HttpStatusException → HttpStatus(code)`、
`CompressedResponseException → CompressedResponse`、その他 `IOException → Connection`、
rename 失敗 → `Save` に分類する。分類は例外型に依存するので `data/` の責務(判断のうち Android/JVM
例外に触れる部分)。detekt の `TooGenericExceptionCaught` に触れないよう `IOException` の捕捉は維持する。

### D4. https 書き換えは `HttpUrlPolicy` の判断 + `HttpFetcher.connect` の 1 箇所で実行

`HttpUrlPolicy.isAllowed(url): Boolean` を `HttpUrlPolicy.resolveFetchUrl(url): String?` に置き換える:

- `https://...` → そのまま
- `http://127.0.0.1...`(loopback、userinfo 詐称は従来どおり URI でホスト判定)→ そのまま
- loopback 以外の `http://...` → 先頭の `http://` を `https://` に置換(大文字小文字を問わず先頭一致。
  ホスト・パス・クエリは触らない。空白や非 ASCII を含む URL も従来と同じく前置検査だけで通す)
- それ以外(`file://`、未知スキーム、空)→ `null`

`HttpFetcher.connect` は `null` なら `UnsupportedUrlException`、それ以外は返った URL で接続する。
入口(購読 URL・artworkUrl・enclosureUrl)ごとに書き換えず、出口 1 箇所で行うのは既存の
チョークポイント方針の踏襲。DB に保存する URL は変えないので、配信側が後日 https に直しても
`guid` ベースの取り込みに影響しない。

- 代替: RSS パース時に書き換えて DB に保存 → 既存 73 件のマイグレーションが必要になり、
  「取得の判断」がパーサーに漏れる。却下
- 代替: `http://` で失敗したら https で再試行 → 平文を一度流すことになる。却下

### D5. 実装順序を B → 実機確認 → A に固定する

A を先に入れると `UnsupportedUrl` の表示を実データで再現できない。tasks.md で「実機確認」を利用者が行う
独立タスクにし、AI がそこを飛ばして A に進まない構造にする。commit も B と A で分け、確認時点の
ビルドが履歴に残るようにする。

## Risks / Trade-offs

- [https 非対応ホストの `http://` enclosure] → 書き換え後の接続で `Connection` / `HttpStatus` として
  失敗し、行に理由が出る。今も失敗しているので悪化はしない。現時点の DB では該当ホストは
  tsujileaks.com のみで https 対応を確認済み
- [`Failed` の型変更で `EpisodeActionMapping` / 計装テスト `EpisodeRowTest` がコンパイルエラー] →
  同じタスク内で追随する。`hasFailedDownload = downloadState is DownloadState.Failed` の判定は不変
- [文言が長いタイトルの行で 2 行目を圧迫] → 最長は `DL失敗(取得できないURL)` の 14 文字。
  実機確認の観点に含める
- [A の後は `UnsupportedUrl` の表示を実機で再現できない] → checkpoint で確認済みとし、以後は
  ユニットテストで文言を固定する。`file://` 等の許可外スキームは引き続き `UnsupportedUrl` になるが、
  実フィードにはまず現れない
