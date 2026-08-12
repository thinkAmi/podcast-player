## ADDED Requirements

### Requirement: 破壊経路は自動承認に載せない

システムは破壊経路を自動承認に載せてはならない(SHALL NOT)。すなわち本番アプリ `dev.thinkami.podcastplayer` のデータを破壊しうるコマンドを、いかなる形であっても無確認で自動実行してはならない。防御の主体は「破壊的コマンドを列挙して deny する」ことではなく、「allowlist に載っていないコマンドは必ずユーザーのプロンプトに落ちる」という姿勢でなければならない。

#### Scenario: 未登録の破壊的コマンドはプロンプトに落ちる
- **WHEN** Claude が allowlist に無いコマンド(例: `./gradlew uninstallDebug`)を実行しようとする
- **THEN** 自動実行されず、ユーザーへの確認プロンプトが表示される

#### Scenario: Gradle タスクの表記ゆれでも自動実行されない
- **WHEN** Claude が `:app:uninstallDebug` や `./gradlew uninstallDeb` のような表記の
  破壊的タスクを実行しようとする
- **THEN** これらは allowlist に一致しないため自動実行されず、プロンプトに落ちる

#### Scenario: プロンプトの出ないモードへ逃げられない
- **WHEN** セッションの権限モードが評価される
- **THEN** `bypassPermissions` と `auto` は `"disable"` により使用不能であり、
  非 allowlist コマンドが無確認で実行される状態は発生しない

### Requirement: 日常ループのコマンドは無確認で自動実行できる

システムは日常ループのコマンドを無確認で自動実行できるようにしなければならない(SHALL)。対象は破壊的でないことがビルド設定またはコマンド仕様により保証されるコマンドに限り、完全引数で allowlist に登録する。

#### Scenario: 品質ゲートとインストールは自動実行
- **WHEN** Claude が `./gradlew check` / `./gradlew installDebug` /
  `./gradlew connectedAndroidTest` を実行する
- **THEN** これらは allowlist に完全一致し、確認なしで実行される

#### Scenario: 計装テストパッケージの掃除は許可される
- **WHEN** Claude が `adb uninstall dev.thinkami.podcastplayer.instrumented` を実行する
- **THEN** 別パッケージ(`.instrumented`)への操作として allowlist に一致し、実行される

#### Scenario: 外部公開はプロンプトに落ちる
- **WHEN** Claude が `git push` または `gh` コマンドを実行しようとする
- **THEN** ask ルールにより必ずユーザーへの確認プロンプトが表示される

### Requirement: 防御ファイルの自己改変を防ぐ

Claude が自身の権限設定・防御スクリプトを書き換えて防御を緩和することを防がなければならない(MUST)。
防御ファイルの削除は「より制限的(=プロンプトが増える)」方向にのみ作用してよく、
「より許容的(=自動承認が増える)」方向に作用してはならない(SHALL NOT)。

#### Scenario: 防御ファイルの編集はブロックされる
- **WHEN** Claude が `.claude/**` または `scripts/**` 配下のファイルを Edit/Write しようとする
- **THEN** deny ルールにより操作がブロックされる(deny は allow で上書きできない)

#### Scenario: 防御ファイルの削除は安全側に倒れる
- **WHEN** `.claude/settings.json` が削除された場合
- **THEN** allowlist が失われ、非登録コマンドがプロンプトに落ちる方向へ退化し、
  自動承認が増える方向へは退化しない

### Requirement: adb device-side 破壊をフックで補助的に検知する

システムは PreToolUse フック `guard-device.sh` により adb device-side 破壊を補助的に検知してブロックしなければならない(SHALL)。対象は、プロンプトで承認される際に人間が見慣れた形の破壊コマンドを反射的に承認してしまう事故の抑止である。フックは主防御ではなく補助であり、いかなる箇所でも唯一の防壁になってはならない(SHALL NOT)。その限界(間接化・文字列非出現には無力であること、および削除・破損時は素通りすること)を運用ドキュメントに明記しなければならない(MUST)。

#### Scenario: パッケージ名と破壊動詞の共起をブロック
- **WHEN** コマンド文字列に本番パッケージ名(`.instrumented` を除く全出現)と破壊動詞
  (`uninstall` / `clear` / `disable` / `cmd package` の破壊系 / `suspend`)が共起する
- **THEN** フックは exit 2 でブロックし、理由を stderr に出力する

#### Scenario: run-as の後続は読み取りのみ許可
- **WHEN** `run-as <本番パッケージ>` の後続コマンドが `cat` / `ls` 以外である
  (クォート・二重空白・変数展開後・複数出現を正規化して判定)
- **THEN** フックは exit 2 でブロックする

#### Scenario: 内部エラーはブロック側に倒れる
- **WHEN** フックスクリプトの内部で想定外の入力やパース失敗が起きる
- **THEN** フックは exit 2(ブロック)に倒れる

#### Scenario: フックが失われても防御水準が下がらない
- **WHEN** `guard-device.sh` が削除・破損・実行不可になり、PreToolUse が素通り(fail-open)する
- **THEN** allowlist に無いコマンドは依然としてプロンプトに落ち、自動承認される破壊経路は生じない

#### Scenario: 判定は回帰テストで担保される
- **WHEN** フックの判定ロジックが変更される
- **THEN** 既知バイパス(クォート回避・`cmd package` 抜け・複数出現・dumpsys インジェクション)を
  全数列挙した回帰テストが実行され、取りこぼしを検出できる
