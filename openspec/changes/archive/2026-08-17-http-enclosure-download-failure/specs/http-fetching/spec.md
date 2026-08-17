## MODIFIED Requirements

### Requirement: URL スキーム検証
HTTP 取得はすべて接続前に URL スキームを検証するものとする(SHALL)。許可するのは `https://` のすべての URL と、`http://127.0.0.1` への loopback URL(計装テスト用の例外)のみとする。loopback 以外の `http://` URL は拒否せず、先頭のスキームだけを `https://` に書き換えた URL で接続するものとする(SHALL)。書き換えはホスト・パス・クエリに手を加えず、DB に保存された URL を変更してはならない(MUST NOT)。平文 HTTP で接続を試みてはならない(MUST NOT)。それ以外の許可外の URL(`file://`、未知スキーム等)は `IOException` 系の例外として拒否し、アプリをクラッシュさせてはならない(MUST NOT)。検証と書き換えは購読 URL・フィード内 artworkUrl・enclosureUrl のすべての入口に対して単一のチョークポイントで働くものとし(SHALL)、判定と書き換えは `logic/` の純粋関数とする。

#### Scenario: file スキームの購読 URL
- **WHEN** `file:///sdcard/x.xml` のような URL で購読登録を試みる
- **THEN** エラーメッセージが表示され、番組は保存されず、アプリはクラッシュしない

#### Scenario: フィード内の許可外 artworkUrl
- **WHEN** artworkUrl に `file://` スキームの URL を含むフィードを購読・更新する
- **THEN** アートワークなしで購読・更新は成功し、アプリはクラッシュしない

#### Scenario: フィード内の許可外 enclosureUrl
- **WHEN** enclosureUrl に許可外スキームの URL を持つエピソードの DL を実行する
- **THEN** DL は失敗状態として行に表示され、アプリはクラッシュしない

#### Scenario: 平文 http の enclosureUrl
- **WHEN** enclosureUrl が `http://example.com/a.mp3` のエピソードの DL を実行する
- **THEN** 接続先は `https://example.com/a.mp3` となり、平文 HTTP への接続は発生せず、DB 上の enclosureUrl は `http://` のまま保持される

#### Scenario: 平文 http の書き換えは先頭スキームのみ
- **WHEN** `HTTP://Example.com/p?x=http://y` のような URL を取得する
- **THEN** 接続先は `https://Example.com/p?x=http://y` となり、スキーム以外の部分は変更されない

#### Scenario: loopback への平文 HTTP
- **WHEN** 計装テストが `http://127.0.0.1:<port>/...` へ取得を行う
- **THEN** スキーム検証は通過し、書き換えなしで平文のまま取得が実行される

#### Scenario: userinfo による loopback 詐称
- **WHEN** `http://127.0.0.1@evil.example.com/a.mp3` を取得する
- **THEN** loopback とはみなされず、`https://127.0.0.1@evil.example.com/a.mp3` として扱われ、平文 HTTP への接続は発生しない
