# feed-subscription Delta Specification

## ADDED Requirements

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
