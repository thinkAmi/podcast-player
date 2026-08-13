## Why

Claude Code を自律的に(編集を自動承認する `acceptEdits` で)走らせたいが、実機 Pixel 7 Pro
上の本番アプリ `dev.thinkami.podcastplayer` には消したくない購読・視聴データがある。現状の防御は
`.claude/settings.json` の deny 8 本だけで、これは完全一致/接頭辞マッチのため
`adb -s <serial> uninstall …` や `:app:uninstallDebug` のような表記ゆれで容易にすり抜ける
(Claude Code 公式ドキュメントも「引数を制約する Bash パターンは fragile」と明記)。脅威モデルは
悪意ある攻撃者ではなく「善意の Claude の想定外の一手」だが、それでも本番データ喪失は避けたい。

## What Changes

- **BREAKING**(開発運用のみ。アプリのコードには影響しない): 権限の主防御を「破壊的コマンドを
  文字列で検知して deny する」方式から「破壊経路を一つも自動承認に載せない allowlist 姿勢」へ転換する。
- `defaultMode: "acceptEdits"` を設定し、`disableBypassPermissionsMode` を `"disable"` にして
  bypassPermissions を封じる(V9: `auto` の封印は利用者の判断で解除。分類器という判定層があること、
  および deny/ask/フック/Gradle ガードがモード非依存であることが根拠)。
- **破壊的な Gradle タスク(uninstall 系)をビルドスクリプト自身が拒否する**。明示フラグ
  `-PallowUninstall=true` がある場合のみ実行できる。権限モード・プロンプト・フック・deny、
  そして人間の注意力のいずれにも依存しない最終防衛線(V8 インシデントへの対策)。
- allowlist を**完全固定**にする。`./gradlew` 系の末尾ワイルドカードを撤廃し、日常ループのコマンドを
  完全引数で列挙する。これにより破壊的 Gradle タスク(uninstall 系)はすべてプロンプトに落ちる。
- `.claude/**` と `scripts/**` を Edit/Write の deny にし、防御ファイル自体の書き換えによる緩和を封じる。
- PreToolUse フック `guard-device.sh` を導入する。役割を adb / run-as の device-side 破壊の検知だけに
  限定し、トークナイズ+空白/クォート正規化で判定、fail-closed で動く。判定は既知バイパスを全数列挙した
  回帰テストで担保する。
- deny リストは維持(プロンプト前の二重化)し、`adb shell cmd package *` を追加する。
- CLAUDE.md の開発環境・データ保護セクションを改訂し、「フックは補助であり deny は表記ゆれに弱い、
  主防御は allowlist とプロンプト」という位置づけを明記する。

## Capabilities

### New Capabilities
- `agent-permissions`: Claude Code をこのリポジトリで自律的に走らせる際の権限境界。どのコマンドが
  無確認で自動実行され、どれがプロンプト/ブロックに落ちるか、実機の本番データをどう守るか、
  防御ファイルの自己改変をどう防ぐか、そして人間の承認が破られたときに何が最後に止めるかを定める。

### Modified Capabilities
<!-- なし。既存のプロダクト spec(playback 等)の要件は変わらない -->

## Impact

- `.claude/settings.json`: permissions(allow/ask/deny/defaultMode/各種 disable)とフック定義を全面改訂
- `.claude/hooks/guard-device.sh`(新規): PreToolUse フック本体
- `.claude/hooks/guard-device.test.sh`(新規): フック判定の全数列挙回帰テスト
- `CLAUDE.md`: 「実機のデータを消さないために」節および開発環境の記述を改訂
- アプリのソースコード(`app/`)・ビルド設定・依存には影響しない
- 検証は Claude Code / Gradle / adb の版依存挙動(設定リロードの同期性、フックの fail-open/closed、
  クォート正規化)に対する実機/実測を要する
