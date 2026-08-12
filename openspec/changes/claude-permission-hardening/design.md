## Context

このリポジトリは個人開発・単一端末(Pixel 7 Pro / Android 16)・利用者は開発者本人ひとり。実機には
本番アプリ `dev.thinkami.podcastplayer` があり、購読・視聴データを消したくない(最重要保護対象)。
計装テストは `applicationIdSuffix = ".instrumented"` の別パッケージで走り OS サンドボックスにより
本番に到達しない。現状の防御は `.claude/settings.json` の deny 8 本のみ。

Claude Code を `acceptEdits`(編集を自動承認)で自律的に走らせたいが、その際の破壊リスクは
実機に触る Bash に集中する。本設計の脅威モデルは「悪意ある攻撃者」ではなく「善意の Claude の
想定外の一手」。ただし敵対的ケースへの退避策も Non-Goals/Risks で明示する。

Claude Code 公式ドキュメント(code.claude.com/docs)で確認済みの事実を前提にする:
- 権限ルールの優先順位は deny → ask → allow で、deny はどの階層・モードの allow でも上書き不可
- `Bash(cmd)`(ワイルドカードなし)は完全一致。`Bash(cmd *)` は接頭辞一致
- 複合コマンド(`&&` `;` `|` 等)は分解され各サブコマンドが独立評価される。自動承認には全サブコマンドが
  allow に一致する必要がある
- 「引数を制約する Bash パターンは fragile」と明記され、対処に PreToolUse フックを推奨
- `acceptEdits` は作業ディレクトリ内なら `rm`/`mv`/`cp`/`mkdir`/`touch`/`rmdir`/`sed` も自動承認する
- PreToolUse の exit 2 ブロックは `bypassPermissions` でも allow でも上書き不可
- `disableBypassPermissionsMode` / `disableAutoMode` は任意の settings ファイルで有効
- 設定ファイルは変更時に自動リロードされる

## Goals / Non-Goals

**Goals:**
- 破壊経路(実機の本番データを消しうるコマンド)を一つも自動承認に載せない
- 日常ループ(check / installDebug / connectedAndroidTest / add-feed 等)は無確認で回す
- 防御を Claude 自身が緩和できないようにする
- 防御一式をリポジトリ内で完結させる(グローバル `~/.claude` を汚さない。他リポジトリへ影響させない)

**Non-Goals:**
- 攻撃者を想定した完全な脱獄耐性。特に `rm` 後に permissive な settings を `Write` で作り直す
  敵対的シーケンスは今回の脅威モデル外(緩和策は Risks に記載)
- 間接化(`$(cat f)`・base64・生成スクリプト実行)をフックで検知すること。これは原理的に不可能であり、
  「allowlist に無いのでプロンプトに落ちる」ことに委ねる
- OS サンドボックス(Seatbelt)の導入。adb 破壊は localhost:5037 への TCP のためサンドボックスでは
  意味的に選別できず、主脅威の解にならない。ファイルシステム爆発半径の補強としては将来検討可

## Decisions

### D1: 主防御を「deny 検知」から「allowlist 姿勢 + プロンプト」へ転換

**選択**: 破壊的コマンドを列挙して deny するのをやめ、非破壊コマンドだけを完全固定で allowlist 化。
未登録は全てプロンプトに落とす。deny/フックはプロンプト前の二重化に格下げ。

**理由**: deny/フックは文字列マッチであり、Gradle/adb は等価表記が無限にある。パッチを当てても表記を
変えれば次が生える(検証で C-1〜C-3・H-1・H-2 として実証)。「未登録=プロンプト」なら表記ゆれは
すべて等しくプロンプトに落ち、イタチごっこが終わる。

**代替案**: (a) deny を強化し続ける → 原理的に追いつけない。(b) OS サンドボックス → adb 主脅威に無効。

### D2: `./gradlew` の allowlist から末尾ワイルドカードを撤廃し完全引数で固定

**選択**: `Bash(./gradlew check)` 等の完全一致のみ。`check *` のような接頭辞 allow は使わない。

**理由**: Gradle は末尾トークンを追加タスクとして実行するため、`./gradlew check uninstallDebug` が
接頭辞 allow に一致して自動承認される(検証 C-1)。完全一致なら追加タスクを混ぜられない。
副作用としてフィルタ付きテストやクリーンビルドはプロンプトになるが、それは人間のゲートとして正しい。

**代替案**: フックで Gradle 呼び出しをパースして許可タスク集合を検査 → D1 の姿勢なら不要。allowlist で
完結する方が単純で堅い。

### D3: モードは `acceptEdits`、`bypassPermissions`/`auto` を封印

**選択**: `defaultMode: "acceptEdits"` + `disableBypassPermissionsMode`/`disableAutoMode` を `"disable"`。

**理由**: 編集は git + `check` ゲート + 自己レビューで無リスク。一方 auto は分類器という「読めない動的な
判定」を権限に持ち込み、`run-as ... rm databases/...` のようなプロジェクト固有の危険を分類器は文脈なしに
見分けられない。未知は分類器でなく人間に倒す(fail-closed)。bypass はホストでは公式が非推奨。

### D4: 防御ファイルは in-repo。書き換えは Edit/Write deny、削除は安全側退化で許容

**選択**: 安全設定・allowlist・フックを全て `.claude/` 配下(コミット対象)に置く。`.claude/**` と
`scripts/**` を Edit/Write deny。削除については追加の機械的防御を置かず「安全側にしか退化しない」性質に依拠。

**理由**: グローバルを汚したくないという要求。D1 の allowlist 姿勢では、防御ファイル削除は
「allowlist 喪失→プロンプト増」「defaultMode 喪失→ default モード(編集もプロンプト)」と、すべて
より制限的な方向へ退化する。危険なのは削除でなく緩和編集であり、それは Edit/Write deny で塞ぐ。
設定の自動リロードにより、`rm` した瞬間 acceptEdits が外れ後続 Write がプロンプトになるため、
敵対的な rm→再作成も実務上は自己defeating(要検証)。

**代替案**: グローバル `~/.claude` へ退避(v2 当初案)→ 他リポジトリへ影響するため却下。
chflags immutable による OS レベルロック → 編集運用コストと引き換えのため任意オプション扱い(Risks)。

### D5: フックは adb/run-as の device-side 破壊専用に範囲限定、fail-closed、回帰テスト付き

**選択**: `guard-device.sh` は Gradle を見ない(D2 で解決済み)。adb/run-as だけを、トークナイズ+
空白/クォート正規化+パッケージ名の全出現走査+破壊動詞集合(`pm`/`cmd package` 両対応)で判定。
`run-as` 後続は cat/ls のホワイトリスト。既知バイパスを全数列挙したシェル回帰テストを同梱し pre-commit で実行。

**fail-open の受容(V2 の結果を反映)**: PreToolUse は `exit 2` 以外の非ゼロもスクリプト不在も
non-blocking で素通りするため、フックを fail-closed にできるのは**内部判定分岐の中だけ**である。
スクリプトが消えれば無言で素通りする。したがってフックは**いかなる箇所でも唯一の防壁にしてはならない**
(V4)。フックは allowlist とプロンプトで既に守られた領域の二次的な取りこぼし検知に限定する。

**理由**: v1 はフックに Gradle と adb を両方背負わせて破綻した。範囲を絞れば堅く書ける。判定を純関数的
シェル関数にし全数列挙テストを当てるのは、本プロジェクトの「判断は純粋関数、状態空間が全列挙できる検証は
全数列挙で書く」方針と同型。

## Risks / Trade-offs

- [フックスクリプトを `rm` される] → V2 により、消えたフックは**無言で素通り**することが確定した
  (fail-open)。かつ `acceptEdits` は作業ディレクトリ内の `rm` を自動承認するため削除自体にプロンプトは出ない。
  「フック自身が検知する」緩和は削除後には機能しないため放棄する。緩和は V4 のとおり構造で行う:
  フックを唯一の防壁にしない(allowlist からワイルドカードを外す)。加えて git で即復元できる。
- [敵対的な `rm`→`Write` 再作成で settings を permissive 化] → 脅威モデル外。塞ぎたい場合は
  防御ファイルへ `chflags uchg`(immutable)+ `Bash(chflags *)`/`Bash(chmod *)` を deny する任意オプション。
- [フックが間接化に無力] → 設計上の限界。allowlist/プロンプトに委ねる。CLAUDE.md にフックは補助と明記。
- [完全固定 allowlist によるプロンプト頻発] → 初回承認で `settings.local.json` に蓄積され漸減。
  広域ワイルドカード allow の混入は定期レビューで検出。deny 優先のため誤って広い allow を足しても
  deny は破れない。
- [版依存の未確認挙動] → 設定リロードの同期性(D4 の自己defeating)、フックの fail-open/closed、
  クォート正規化の実装挙動。実装前に実機/実測し、結果に合わせて設計を微調整する。

## Migration Plan

1. `.claude/settings.json` を新方式へ差し替え(allow 完全固定 / ask / deny 拡充 / defaultMode / disable 2 種 / hooks)
2. `.claude/hooks/guard-device.sh` と `.claude/hooks/guard-device.test.sh` を追加、テストを通す
3. CLAUDE.md 改訂(フックは補助・deny は表記ゆれに弱い・主防御は allowlist とプロンプト、を明記)
4. 実機/実測で要検証 3 項目を確認し、結果を反映

ロールバック: `.claude/settings.json` を旧版(deny 8 本のみ)に git revert すれば従来動作に戻る。

## 検証結果(実測・2026-08-12)

実装前に tasks.md グループ 1 として実施した検証の結果。design の前提を 1 つ覆したため D5 と Risks を改訂した。

### V1: 設定ファイルのリロードは作成・削除とも同期的(D4 を支持)

`.claude/settings.local.json` に `deny: ["Bash(echo RELOADPROBE *)"]` を置くと**次のツール呼び出しで即座に**
拒否され、`rm` した直後の呼び出しでは**即座に**実行できた。よって設定の削除は次のツール呼び出しから反映される。
`.claude/settings.json` を削除すれば `defaultMode: acceptEdits` も同時に失われて `default` に戻るため、
「敵対的な rm → permissive な Write で再作成」は後続 Write がプロンプトになり自己defeating、という D4 の
想定は支持された。

### V2: PreToolUse フックは fail-OPEN(設計前提を覆す / H-3 を確定)

公式ドキュメント(code.claude.com/docs/en/hooks)より:

- `exit 2` **だけ**がブロックする。JSON の `permissionDecision: "allow"` でも上書きできない
- `exit 1` を含む**その他の非ゼロは non-blocking error 扱いで、アクションは続行する**
- **スクリプトが存在しない/実行権限がない場合(exit 127 等)も non-blocking で続行する**
- `exit 0` かつ無出力は「決定なし」であり、承認ではない。通常の権限フロー(allowlist/プロンプト)へ進む

したがって「フックを fail-closed にする」は**スクリプト内部の判定分岐についてのみ達成可能**で、
スクリプトの削除・破損・実行不可といった外部要因に対しては**原理的に達成できない**。当初 tasks 2.4 に
書いた「内部エラー時 exit 2 へ倒す」は内部エラーに限定した目標へ縮小する。

### V3: Gradle のタスク名省略は極めて強力(C-2 を確定、D2 を強く支持)

`--dry-run`(かつ端末未接続)で安全に実測した結果、以下がすべて `:app:uninstallDebug` に解決された:

| 入力 | 解決先 |
|---|---|
| `./gradlew uninstallDeb` | `:app:uninstallDebug` |
| `./gradlew :app:uninstallDebug` | `:app:uninstallDebug` |
| `./gradlew uD` | `:app:uninstallDebug` |

**2 文字の `uD` でも解決する。** 文字列ベースの deny で Gradle の破壊タスクを封じるのは原理的に不可能であり、
D1/D2(allowlist 姿勢・完全固定)の正しさが実証された。

### V4: V2 から派生した設計変更 — フックを load-bearing にしない

V2 により「フックスクリプトを `rm` されると**無言で素通りする**」ことが確定した。しかも `acceptEdits` は
作業ディレクトリ内の `rm` を自動承認するため、`rm .claude/hooks/guard-device.sh` はプロンプトなしで通る。
当初 Risks に書いた緩和策「フック自身が `.claude/` への破壊を検知して exit 2」は、**削除されたスクリプトは
何も検知できない**ため成立しない。

したがってフックが唯一の防壁になっている箇所を無くす。具体的には allowlist から
`adb shell dumpsys *` と `adb logcat *` のワイルドカードを外し、フックの役割を
「allowlist とプロンプトで既に守られている領域の、二次的な取りこぼし検知」に純化する。
これによりフックが消えても防御水準は allowlist とプロンプトのまま維持される。

## Open Questions

- フックの複数出現・クォート内出現の正規化を、どのシェル機能で最も堅く実装するか(回帰テストで確定する)
