## 1. 実機/実測での事前検証(実装前に実施)

- [x] 1.1 `.claude/settings.json` を `rm` した後、設定リロードが後続ツール呼び出し前に同期反映され、
        acceptEdits が外れて後続 Write がプロンプトになるか確認する(design D4 の自己defeating の成否)
        → **V1: 作成・削除とも同期的。D4 支持**(settings.local.json で実測)
- [x] 1.2 PreToolUse フックがスクリプト内部エラー(exit 1 / 構文破損 / スクリプト不在)のとき
        fail-open か fail-closed かを実測し、fail-closed 前提で書けるか、ラッパで担保する必要があるか判断する
        → **V2: fail-OPEN。exit 2 のみブロック。スクリプト不在も素通り**(公式ドキュメントで確定)
- [x] 1.3 `./gradlew uninstallDeb`(camelCase 省略)の曖昧解決の実挙動を確認する(参考情報。allowlist 固定化で
        実害は消えているが挙動理解のため)
        → **V3: `uninstallDeb` / `:app:uninstallDebug` / `uD` すべて解決。D1/D2 を強く支持**
- [x] 1.4 上記の結果を design.md の Open Questions に追記して確定させる
        → design.md に「検証結果(実測)」節として V1〜V4 を記載。V4 は V2 から派生した設計変更

## 2. フック本体と回帰テスト

- [x] 2.1 `.claude/hooks/guard-device.sh` を作成。判定を純関数的シェル関数に分離し、トークナイズ+
        空白/クォート正規化+本番パッケージ名(`.instrumented` を除く)の全出現走査を実装する
- [x] 2.2 破壊動詞集合(`uninstall` / `clear` / `disable(-user)?` / `cmd package (clear|uninstall)` /
        `suspend`)とパッケージ名の共起で exit 2 する判定を実装する(`pm` と `cmd package` 双方を包含)
- [x] 2.3 `run-as <本番パッケージ>` 後続のホワイトリスト(cat/ls のみ許可、他は exit 2)を、クォート・
        二重空白・変数展開後・複数出現を正規化して実装する
- [x] 2.4 スクリプト**内部**の判定分岐・想定外入力・パース失敗時に exit 2 へ倒す(V2 により外部要因=
        スクリプト不在/実行不可は原理的に fail-open。内部のみ fail-closed にする)
- [x] 2.5 `.claude/hooks/guard-device.test.sh` を作成。既知バイパスを全数列挙した回帰ケースを用意する
        (クォート回避 C-3 / `cmd package` 抜け H-2 / 複数出現 M-1 / dumpsys device-side インジェクション /
        `adb -s <serial>` / 正常な読み取り系が誤ブロックされないこと)
- [x] 2.6 回帰テストが全ケース通ることを確認し、git の pre-commit で実行されるよう配線する

## 3. settings.json の差し替え

- [x] 3.1 `permissions.defaultMode` を `acceptEdits`、`disableBypassPermissionsMode` と
        `disableAutoMode` を `"disable"` に設定する
- [x] 3.2 allow を完全固定で列挙する(`./gradlew` 系は末尾ワイルドカードなし。check / ktfmtFormat /
        installDebug / connectedAndroidTest / write-verification-metadata の固定形、adb devices /
        pm list packages / instrumented uninstall、git 読み取り/add/commit)。
        **V4 により `adb shell dumpsys *` と `adb logcat *` のワイルドカードは allow に入れない**
        (フックを唯一の防壁にしないため。必要時はプロンプト経由で実行する)
- [x] 3.3 ask に `git push *` と `gh *` を設定する
- [x] 3.4 deny に `Edit(/.claude/**)`・`Edit(/scripts/**)`・`Edit(/.githooks/**)`、既存 8 本、
        `adb shell cmd package *` を設定する
        → **V5: `Write(path)` は参照されないため `Edit(path)` に統一。アンカーも `/` 始まり(設定基準)へ修正**
- [x] 3.5 hooks.PreToolUse に `guard-device.sh` を `matcher: "Bash"`、`$CLAUDE_PROJECT_DIR` 相対で配線する
- [x] 3.6 settings.json が有効な JSON で、Claude Code に読み込まれることを確認する

## 4. ドキュメント改訂

- [x] 4.1 CLAUDE.md「実機のデータを消さないために」節を改訂し、主防御は allowlist とプロンプトであること、
        deny は表記ゆれに弱くフックは補助であること、フックは間接化に無力であることを明記する
- [x] 4.2 CLAUDE.md にフック回帰テストの実行方法(pre-commit / 手動)を一行記載する
- [x] 4.3 敵対的ケース向けの任意オプション(chflags immutable + chflags/chmod deny)を、採用可能な
        バックログとして tasks もしくは CLAUDE.md に残す

## 5. 動作確認

- [ ] 5.1 allow 済みコマンド(`./gradlew check` 等)が無確認で走ることを確認する
        → **未完了。V7 のとおり `defaultMode` はセッション開始時の既定であり、実行中のセッションには
        効かない。次回このリポジトリで Claude Code を起動したときに確認すること**
- [ ] 5.2 未登録の破壊的コマンド(`./gradlew uninstallDebug` / `:app:uninstallDebug`)がプロンプトに
        落ちることを確認する
        → `./gradlew uninstallDebug` は deny 発火を確認済み。`:app:uninstallDebug` が
        **プロンプトへ落ちる**ことの確認は 5.1 と同じ理由で次回セッションに持ち越し
- [x] 5.3 `.claude/**` / `scripts/**` の Edit/Write がブロックされることを確認する
        → Edit ツールでの自己改変が拒否されることを確認。さらに **Bash の `touch` / `rm` も
        ブロックされる**ことを実測(V5)。`find -delete` 等の間接実行は素通り(既知の限界)
- [x] 5.4 フックが既知の破壊形(`adb -s <serial> uninstall <本番>`・クォート付き `pm clear`・
        `cmd package clear`・`run-as <本番> rm ...`)を exit 2 でブロックすることを実機で確認する
        → 実セッションで確認。**deny がマッチしないフルパス起動
        (`~/Library/Android/sdk/platform-tools/adb uninstall <本番>`)もフックが捕捉**(V6)。
        読み取り系(`run-as <本番> cat ...`)は誤ブロックされないことも確認

## 6. 採用しなかった選択肢(バックログ)

- [ ] 6.1 敵対的ケース向けの `chflags uchg`(OS レベルの immutable)+ `Bash(chflags *)`/`Bash(chmod *)` の
        deny。V5 により `rm`/`touch` は Edit deny で既に塞がっているため優先度は下がった。
        間接実行(`find -delete`・python 等)まで塞ぎたくなったときに検討する
- [ ] 6.2 OS サンドボックス(Seatbelt)の導入。adb は localhost:5037 への TCP のため主脅威の解にはならず、
        ファイルシステムの爆発半径を縛る補強としてのみ有効
