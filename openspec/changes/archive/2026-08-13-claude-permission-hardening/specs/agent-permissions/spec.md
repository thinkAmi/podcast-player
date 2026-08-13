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

#### Scenario: bypassPermissions へは逃げられない
- **WHEN** セッションの権限モードが評価される
- **THEN** `bypassPermissions` は `disableBypassPermissionsMode: "disable"` により使用不能である
  (このモードには分類器のような代替の判定層が存在しないため)

#### Scenario: auto モードは利用者の opt-in で選択できる
- **WHEN** 利用者が調査などのために `auto` モードを選択する
- **THEN** 未登録コマンドのゲートは人間のプロンプトから分類器へ置き換わるが、
  deny / ask / PreToolUse フック / Gradle 実行時ガードはモード非依存で効き続ける
- **AND** モードを選択できるのは利用者だけであり、Claude は自らのセッションのモードを変更できない

### Requirement: 日常ループのコマンドは無確認で自動実行できる

システムは日常ループのコマンドを無確認で自動実行できるようにしなければならない(SHALL)。対象は破壊的でないことがビルド設定またはコマンド仕様により保証されるコマンドに限り、完全引数で allowlist に登録する。

#### Scenario: 品質ゲートとインストールは自動実行
- **WHEN** Claude が `./gradlew check` / `./gradlew installDebug` /
  `./gradlew connectedAndroidTest` を実行する
- **THEN** これらは allowlist に完全一致し、確認なしで実行される

#### Scenario: adb コマンドはプロンプトに落ち、安全性はフックが担保する
- **WHEN** Claude が `adb` を使うコマンド(例: `adb uninstall dev.thinkami.podcastplayer.instrumented`)
  を実行しようとする
- **THEN** Claude の実行環境では `adb` が PATH に無くフルパス起動になるため、`Bash(adb ...)` 形式の
  allow / deny ルールはいずれもマッチせず、コマンドは利用者のプロンプトに落ちる
- **AND** 破壊的な形(本番パッケージ + 破壊動詞、run-as の書き込み)は、パスの書き方によらず
  PreToolUse フックがブロックする

#### Scenario: 外部公開はプロンプトに落ちる
- **WHEN** Claude が `git push` または `gh` コマンドを実行しようとする
- **THEN** ask ルールにより必ずユーザーへの確認プロンプトが表示される

### Requirement: 防御ファイルの自己改変を防ぐ

Claude が自身の権限設定・防御スクリプトを書き換えて防御を緩和することを防がなければならない(MUST)。
防御ファイルの削除は「より制限的(=プロンプトが増える)」方向にのみ作用してよく、
「より許容的(=自動承認が増える)」方向に作用してはならない(SHALL NOT)。

#### Scenario: 防御ファイルの編集はブロックされる
- **WHEN** Claude が `.claude/**` / `scripts/**` / `.githooks/**` 配下のファイルを変更しようとする
- **THEN** `Edit(path)` の deny ルールによりブロックされる(deny は allow で上書きできない)
- **AND** ルールは `Edit(path)` 形式で書かなければならない。`Write(path)` / `NotebookEdit(path)` /
  `MultiEdit(path)` は受理されるが**参照されない**ため、防御として機能しない
- **AND** この deny は Claude の編集ツールだけでなく、Bash の `rm` / `touch` にも及ぶ
  (`find -delete` や python 等の間接実行には及ばない)

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

#### Scenario: ガードの脱出ハッチは Claude 経由では使えない
- **WHEN** コマンド文字列に `allowUninstall`(大文字小文字を問わない)が含まれる
- **THEN** フックは exit 2 でブロックする。このフラグは利用者が自分のターミナルで打つ専用であり、
  Claude 経由の正当な用途は存在しない
- **AND** 判定は部分一致であるため、フラグを使う意図がなく文字列に言及しただけのコマンド
  (`grep allowUninstall` 等)もブロックされる。これは安全側に倒すための意図的な代償である

### Requirement: 破壊的な Gradle タスクはビルドスクリプト自身が拒否する

システムは uninstall 系の Gradle タスクを、明示フラグなしでは失敗させなければならない(SHALL)。
この層は権限モード・プロンプト・フック・deny ルール・そして**人間の注意力**のいずれにも依存しない
最終防衛線であり、削除してはならない(SHALL NOT)。

Gradle はタスク名の省略形を解決するため(`uD` でも `:app:uninstallDebug` に到達する)、コマンド文字列を
見る防御では表記ゆれを塞ぎ切れない。どの表記でも最終的に同じタスクに解決されることを逆手に取り、
タスク自身にガードを持たせる。

#### Scenario: フラグなしの uninstall はタスク内で失敗する
- **WHEN** `-PallowUninstall=true` を伴わずに uninstall 系タスクが実行される
  (`./gradlew uninstallDebug` / `:app:uninstallDebug` / `uD` などの表記を問わない)
- **THEN** タスクは `GradleException` で失敗し、実機のアプリには到達しない

#### Scenario: 利用者が明示したときだけ実行できる
- **WHEN** `-PallowUninstall=true` を伴って uninstall 系タスクが実行される
- **THEN** ガードを通過して本来のタスクが実行される

#### Scenario: すべての uninstall 系タスクを構造的に覆う
- **WHEN** 新しい uninstall 系タスクがビルドプラグインによって追加される
- **THEN** ガードはタスク名の接頭辞で判定するため、明示的な列挙を更新しなくても自動的に覆われる
