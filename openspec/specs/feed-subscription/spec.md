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

item 内の個別属性が不正な場合は、item 全体を捨てるのではなく当該属性のみを「不明」として扱うものとする(SHALL)。特に `itunes:duration` は、時分秒のいずれかの成分が負であるもの、数値として解釈できないもの、ミリ秒換算で Long の範囲を超えるものを「長さ不明」(null)として扱い、視聴済みの自動判定を行わない。長さの解釈は任意の入力文字列に対して例外を送出せず、null または正のミリ秒値のみを返すものとする(SHALL)。

#### Scenario: 一部のitemが壊れているフィード
- **WHEN** enclosureを持たないitemを含むフィードを更新する
- **THEN** 壊れたitemは無視され、正常なitemはすべて取り込まれる

#### Scenario: 負の成分を含む長さ表記
- **WHEN** `itunes:duration` が `"10:-5"` のように負の成分を含むitemを取り込む
- **THEN** そのエピソードは長さ不明(null)として取り込まれ、視聴済みの自動判定は行われない

#### Scenario: ミリ秒換算がオーバーフローする長さ表記
- **WHEN** `itunes:duration` の合計秒数がミリ秒換算で Long の最大値を超えるitemを取り込む
- **THEN** そのエピソードは長さ不明(null)として取り込まれ、負値などの不正な長さが記録されることはない

#### Scenario: 任意の不正文字列に対する頑健性
- **WHEN** `itunes:duration` に数値・時分秒のいずれとも解釈できない任意の文字列が指定されている
- **THEN** パースは例外を送出せず、そのエピソードは長さ不明(null)として取り込まれる

### Requirement: エピソード詳細(ショーノート)表示
エピソード行のアクションアイコン(DL/再生/視聴済みトグル)以外の領域をタップすると、ショーノート(フィードのdescription)を表示する詳細画面を開くものとする(SHALL)。

#### Scenario: 行タップで詳細表示
- **WHEN** エピソード行の本文領域をタップする
- **THEN** タイトル・公開日・長さ・ショーノートを表示する詳細画面が開く

### Requirement: adb 経由の購読登録
USB デバッグを許可した PC から、`run-as` 配下の `am broadcast` で `exported="false"` の BroadcastReceiver に RSS URL を届けることで購読登録できるものとする(SHALL)。受け取った URL は画面のダイアログからの登録と同一の経路(`FeedRepository.subscribe()`)で処理し、この経路のためのアプリ内 UI・設定は追加しない(MUST NOT)。端末内の他アプリからこの Receiver に登録を指示できてはならない(MUST NOT)。この経路は debuggable ビルドでのみ成立し、リリースビルドでは利用できないものとする(SHALL)。

#### Scenario: PC からの有効なフィード登録
- **WHEN** PC のスクリプトが `run-as` 経由の `am broadcast` で有効な https フィード URL を届ける
- **THEN** 画面から登録した場合と同様に番組が購読一覧に追加され、エピソードが取り込まれる

#### Scenario: 他アプリからの登録指示
- **WHEN** 端末内の別アプリ(別 uid)が同じコンポーネント名・extra キーで broadcast を送る
- **THEN** Receiver には配達されず、購読は追加されず、通信も発生しない

#### Scenario: 空または欠落した URL extra
- **WHEN** `feed_url` extra が欠落・空文字・空白のみの broadcast が届く
- **THEN** 何も行われない(購読追加なし・通信なし・クラッシュなし)

#### Scenario: 許可外スキームの URL
- **WHEN** `file://` など https 以外の URL が届く
- **THEN** 購読は保存されず、アプリはクラッシュしない

### Requirement: PC 側登録スクリプト
PC からの登録コマンド(コンポーネント名・extra キーという契約を含む)はリポジトリ内のスクリプトに一元化するものとする(SHALL)。スクリプトは引数の URL が https であることの早期検証・adb 接続とアプリ存在の前提チェック・シェルの二重クォート処理を行い、Receiver が返した結果コードと結果データをそのまま報告するものとする(SHALL)。結果の取得は broadcast 1 往復で完結させ、購読状態のポーリングや DB の直接参照は行わない(MUST NOT)。AI からの実行はスクリプト実行にルーティングするスキルを経由し、adb コマンドを即興で組み立てないものとする(SHALL)。

#### Scenario: スクリプトによる登録
- **WHEN** `scripts/` の登録スクリプトを有効な https URL を引数に実行する
- **THEN** broadcast が配達され、スクリプトは Receiver が返した結果を報告して終了する

#### Scenario: 取得に失敗するフィードの登録
- **WHEN** 到達不能な https URL を引数にスクリプトを実行する
- **THEN** 配達は成功し、スクリプトは失敗を示す結果コードと理由を報告する(黙って成功扱いにしない)

#### Scenario: クエリパラメータを含む URL
- **WHEN** `&` や `?` を含むフィード URL を引数に実行する
- **THEN** URL は変形せずそのまま extra として届く

#### Scenario: 前提が満たされていない実行
- **WHEN** 端末が未接続、または引数が https 以外の URL でスクリプトを実行する
- **THEN** broadcast を送らずにエラーメッセージを表示して終了する

