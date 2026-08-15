# episode-download Specification

## Purpose
TBD - created by archiving change podcast-player-mvp. Update Purpose after archive.
## Requirements
### Requirement: エピソード単位の手動ダウンロード
未DLエピソードの行および統合エピソード画面にはDLアイコン(ボタン)を表示し、タップでそのエピソードの音声ファイル(enclosure URL)のダウンロードを開始するものとする(SHALL)。自動DLは行わない(MUST NOT)。

#### Scenario: Wi-Fi接続時のDL開始
- **WHEN** Wi-Fi接続中に未DLエピソードのDLアイコンをタップする
- **THEN** 確認なしで即座にDLが開始され、行に進捗が表示される

#### Scenario: DL完了
- **WHEN** DLが完了する
- **THEN** エピソードはDL済み状態になり、行のアイコンがDLから再生に切り替わり、「DL済みのみ」フィルターの対象になる

#### Scenario: 統合エピソード画面からのDL
- **WHEN** 未DLエピソードの統合エピソード画面でDLボタンをタップする
- **THEN** 一覧からの操作と同じ規則でDLが開始され、画面に進捗が表示され、完了すると再生ボタンに切り替わる

### Requirement: モバイル回線時の確認ダイアログ
従量制(モバイル)回線でDLアイコンをタップした場合のみ、ファイルサイズを表示する確認ダイアログを挟むものとする(SHALL)。この確認は一覧の行と統合エピソード画面のどちらからの操作でも同一とする(SHALL)。

#### Scenario: モバイル回線でのDL操作
- **WHEN** モバイル回線接続中にDLアイコンをタップする(一覧の行・統合エピソード画面のいずれでも)
- **THEN** 「モバイル回線です。<サイズ>をダウンロードしますか?」の確認が表示され、承認時のみDLが開始される

#### Scenario: 確認のキャンセル
- **WHEN** 確認ダイアログでキャンセルする
- **THEN** 通信は一切発生しない

### Requirement: 保存先はアプリ専用領域
DLファイルは外部ストレージのアプリ専用領域(getExternalFilesDir)に保存するものとする(SHALL)。ランタイム権限を要求しない。

#### Scenario: DLファイルの保存
- **WHEN** エピソードをDLする
- **THEN** ファイルはアプリ専用領域に保存され、ストレージ権限のリクエストは発生しない

### Requirement: 失敗時は手動再試行のみ
DL失敗時は行に失敗状態を表示し、自動再試行は行わないものとする(SHALL NOT)。再試行はユーザーの再タップによる。

#### Scenario: DL途中での通信断
- **WHEN** DL中に通信が切断される
- **THEN** エピソードは失敗状態(未DL扱い)として表示され、バックグラウンドでの自動再試行は発生しない

#### Scenario: 手動再試行
- **WHEN** 失敗状態のエピソードのDLアイコンを再タップする
- **THEN** DLが最初からやり直される

