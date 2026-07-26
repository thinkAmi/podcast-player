# http-fetching Delta Specification

## ADDED Requirements

### Requirement: URL スキーム検証
HTTP 取得はすべて接続前に URL スキームを検証するものとする(SHALL)。許可するのは `https://` のすべての URL と、`http://127.0.0.1` への loopback URL(計装テスト用の例外)のみとする。許可外の URL(`file://`、loopback 以外の `http://`、未知スキーム等)は `IOException` 系の例外として拒否し、アプリをクラッシュさせてはならない(MUST NOT)。検証は購読 URL・フィード内 artworkUrl・enclosureUrl のすべての入口に対して単一のチョークポイントで働くものとし(SHALL)、スキームの判定は `logic/` の純粋関数とする。

#### Scenario: file スキームの購読 URL
- **WHEN** `file:///sdcard/x.xml` のような URL で購読登録を試みる
- **THEN** エラーメッセージが表示され、番組は保存されず、アプリはクラッシュしない

#### Scenario: フィード内の許可外 artworkUrl
- **WHEN** artworkUrl に `file://` スキームの URL を含むフィードを購読・更新する
- **THEN** アートワークなしで購読・更新は成功し、アプリはクラッシュしない

#### Scenario: フィード内の許可外 enclosureUrl
- **WHEN** enclosureUrl に許可外スキームの URL を持つエピソードの DL を実行する
- **THEN** DL は失敗状態として行に表示され、アプリはクラッシュしない

#### Scenario: loopback への平文 HTTP
- **WHEN** 計装テストが `http://127.0.0.1:<port>/...` へ取得を行う
- **THEN** スキーム検証は通過し、取得が実行される

### Requirement: テキスト取得の透過 gzip
フィード XML などのテキスト取得は `Accept-Encoding` ヘッダを手動設定せず、OS の透過 gzip(自動要求・自動解凍)に任せるものとする(SHALL)。サーバーが gzip 応答を返した場合でも、呼び出し側には解凍済みテキストが返るものとする(SHALL)。

#### Scenario: gzip 圧縮されたフィード応答
- **WHEN** サーバーがフィード XML を `Content-Encoding: gzip` で返す
- **THEN** 取得結果は解凍済みの XML テキストであり、RSS パースが成功する

### Requirement: ストリーム取得の非圧縮強制
音声・画像などのストリーム取得はリクエストに `Accept-Encoding: identity` を明示し、転送圧縮を要求しないものとする(SHALL)。identity 指定にもかかわらず応答の `Content-Encoding` が identity 以外(gzip 等)である場合は `IOException` として失敗させ、圧縮されたバイト列をファイルとして保存してはならない(MUST NOT)。

#### Scenario: ストリーム取得のリクエストヘッダ
- **WHEN** エピソード音声またはアートワークを取得する
- **THEN** サーバーが受信するリクエストには `Accept-Encoding: identity` が含まれる

#### Scenario: 非準拠サーバーの圧縮応答
- **WHEN** identity 指定にもかかわらずサーバーが `Content-Encoding: gzip` で応答する
- **THEN** 取得は `IOException` で失敗し、DL は失敗状態になり、圧縮されたままのファイルは保存されない

#### Scenario: Content-Length の保持
- **WHEN** サーバーが Content-Length 付きで音声を返す
- **THEN** 進捗表示の分母として実ファイルサイズが利用できる
