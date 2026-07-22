# feed-subscription Specification

## Purpose
TBD - created by archiving change podcast-player-mvp. Update Purpose after archive.
## Requirements
### Requirement: RSS URLによる購読登録
購読一覧画面の「+」からRSS URLを入力して番組を購読登録できること。システムはURL入力時にフィードを取得・パースし、成功した場合のみ番組を保存するものとする(SHALL)。

#### Scenario: 有効なフィードの登録
- **WHEN** 有効なポッドキャストRSSのURLを入力して登録する
- **THEN** 番組(タイトル・アートワーク)が購読一覧に追加され、フィード内のエピソードがすべて未聴・未DL状態で取り込まれる

#### Scenario: 無効なURLまたは取得失敗
- **WHEN** 到達不能なURL・RSSでないコンテンツのURLを入力する
- **THEN** エラーが表示され、番組は保存されない

### Requirement: 購読削除
番組の購読を削除できること。削除時はDB上の番組・エピソード記録と、その番組のDL済みファイルをすべて削除するものとする(SHALL)。

#### Scenario: 購読中の番組を削除
- **WHEN** エピソード一覧画面のメニューから購読削除を実行する
- **THEN** 番組・全エピソード記録・DL済みファイルが削除され、購読一覧から消える

### Requirement: 手動フィード更新
フィード更新はpull-to-refreshによる手動操作のみとする(SHALL)。購読一覧画面では全番組を、エピソード一覧画面ではその番組のみを更新する。

#### Scenario: 購読一覧での全番組更新
- **WHEN** 購読一覧画面で引っ張って更新する
- **THEN** 全購読番組のRSSが取得され、新規エピソード(guid未知のもの)が未聴・未DL状態で追加される

#### Scenario: 番組単位の更新
- **WHEN** エピソード一覧画面で引っ張って更新する
- **THEN** その番組のRSSのみが取得され、新規エピソードが追加される

#### Scenario: 既存エピソードの状態保持
- **WHEN** フィード更新で既知のguidのエピソードを再受信する
- **THEN** 視聴済み・DL状態・再生位置は変更されない

### Requirement: 起動時に通信しない
アプリ起動時にネットワーク通信を行わないものとする(SHALL)。すべての通信はユーザー操作を起点とする。

#### Scenario: 起動直後
- **WHEN** アプリを起動する
- **THEN** フィード取得・画像取得などの通信は発生せず、DB内の既存データのみが表示される

### Requirement: RSSパースの耐障害性
RSS 2.0 + iTunes名前空間をXmlPullParserでパースし、必須要素(title / enclosure url / guid)を持たない壊れたitemはスキップして残りの取り込みを続行するものとする(SHALL)。

#### Scenario: 一部のitemが壊れているフィード
- **WHEN** enclosureを持たないitemを含むフィードを更新する
- **THEN** 壊れたitemは無視され、正常なitemはすべて取り込まれる

### Requirement: エピソード詳細(ショーノート)表示
エピソード行のアクションアイコン(DL/再生/視聴済みトグル)以外の領域をタップすると、ショーノート(フィードのdescription)を表示する詳細画面を開くものとする(SHALL)。

#### Scenario: 行タップで詳細表示
- **WHEN** エピソード行の本文領域をタップする
- **THEN** タイトル・公開日・長さ・ショーノートを表示する詳細画面が開く

