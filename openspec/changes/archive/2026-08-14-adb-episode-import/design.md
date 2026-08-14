# adb-episode-import Design

## Context

「セキュリティのアレ」の公式フィードは最新300件のみを配信し、過去回(42回分)は後追いで取得できない。別リポジトリ(rss_maker_for_security_no_are)がファンサイトの公開データから過去回を標準 RSS 2.0 のアーカイブ XML として再構成し、Gist(secret、raw URL は認証不要)で配信している。この XML は検証済みで、全 item が RSS 2.0 標準の `<source url="https://www.tsujileaks.com/?feed=podcast">` による出典宣言を持つ。

取り込み先のアプリ側には既に adb 経路(`AdbFeedReceiver`、`exported="false"` + `run-as`)と PC 側スクリプト(`scripts/add-feed.sh`)の枠組みがあり、信頼境界は「USB デバッグを許可した PC」。本 change はこの枠組みに取り込みモードを追加する。

エピソード一覧は `ORDER BY publishedAtEpochMillis DESC` の日付順であり、取り込まれた過去回は既存エピソードと自然に混ざる。取り込みの合流点は既存の `storeEpisodes`(guid ベースの upsert: `insertIgnoringKnown` + `updateMetadataByGuid`、状態カラム不変)をそのまま使う。

## Goals / Non-Goals

**Goals:**

- アーカイブ XML のエピソードを既存購読へ一度きり注入する adb 経路
- 誤注入(引数ペアの取り違え)を機械的に拒否する出典宣言バリデーション
- 従来の購読登録 broadcast との完全な後方互換
- PC 側スクリプトとスキル(実行前確認付き)による契約の一元化

**Non-Goals:**

- アプリ内 UI・設定・定数の追加
- `import_url` の永続化・再取得・同期(取り込みは記憶されない一方向操作)
- アーカイブ XML の内容の真正性検証(出典宣言と item 中身の対応はツール側の責務)
- 番組固有ロジック(「セキュリティのアレ」への特別扱いはコード上ゼロ)
- 防御構造(allowlist・フック・testBuildType)の変更

## Decisions

### D1: 誤注入防止は「出典宣言と購読 feedUrl の完全一致」で行う

XML の全 item が持つ `<source url>`(生成ツールが差分計算に使ったフィード URL をそのまま書く)と、DB に保存済みの購読 feedUrl を trim 後に文字列完全一致で照合する。比較の両辺が独立した出所(XML と DB)を持つため、引数の取り違えは必ず不一致になる。

代替案と棄却理由:

- **配信ドメインの一致**: 音源が CDN 配信の番組では恒常的に不一致(誤検知)、同一プラットフォーム(anchor.fm 等)の別番組では一致(見逃し)。危険が大きい場面ほど効かない
- **channel `<link>` の照合**: 検証のために公式フィードの追加フェッチが必要になり、サイト移転で経年劣化する
- **独自拡張タグ**: 機能は等価だが、RSS 2.0 標準の `<source>`(意味論: この item の出所であるフィードの URL)で表現できるため不要
- **`atom:link rel="self"` の流用**: rel="self" は「この文書自身の URL」の意味であり、フィード移転検出として解釈するクライアントが購読 URL を書き換える恐れがあるため禁止

検証はペア整合のみを見る。「宣言が item の中身と本当に対応しているか」は検証しない(ツール側の構造とテストが保証する契約)。

### D2: 判断は logic/ の純粋関数、拒否理由は型で区別

検証(全 item に宣言あり・全件同一・feedUrl と一致)は `logic/` の純粋関数として実装し、結果を sealed 型(許可 / 宣言なし / 混在 / 不一致)で返す。Receiver はこれを結果メッセージに変換するだけ。Kover 90% の対象であり、JVM ユニットテストで全分岐を網羅する。

### D3: `importEpisodes` は `refresh()` を流用しない

`refresh()` は channel タイトル・アートワークをフィードメタデータへ書き戻すため、流用するとアーカイブの「セキュリティのアレ 過去回アーカイブ(非公式)」が本物の番組名を上書きする。取り込みは `fetchAndParse` → 検証 → `storeEpisodes` のみで構成し、feeds テーブルには一切書き込まない。

新規件数は `insertIgnoringKnown` の戻り値を `List<Long>` に変更して得る(Room の `OnConflictStrategy.IGNORE` は無視した行に -1 を返す)。既存呼び出し箇所は戻り値を使わないため互換。

### D4: Receiver は同一クラスで extras 分岐、結果コードを追加

新しい Receiver は作らず、`AdbFeedReceiver` が `import_url` extra の有無でモードを分ける(空白のみの `import_url` は欠落と同義)。broadcast の入口が 1 つであることは「破壊経路を増やさない」構造の維持でもある。結果コードは既存の 1〜3 に `RESULT_IMPORTED = 4` を追加。8 秒のタイムアウト予算を踏襲する(フィード取得は行わず、取り込む XML は 21KB 程度)。

### D5: 取り込みスクリプトは allowlist に載せない

`scripts/import-episodes.sh` は意図的に `.claude/settings.json` の allow へ追加しない。AI からの実行は毎回利用者の許可プロンプトに落ちる。本番データへ書く低頻度操作であり、「本番に触る経路を自動承認に載せない」防御構造に従う。スキルの実行前確認(人間)+ 許可プロンプト(人間)+ 出典宣言検証(機械)の三層になる。

### D6: 利用者が配置する 2 ファイル

`scripts/**` と `.claude/**` は Edit deny のため、`import-episodes.sh` と `import-podcast-episodes` スキルは本書の付録に完成形を置き、配置は利用者が行う。付録が正本、配置物はそのコピー。

## Risks / Trade-offs

- [宣言が真実でない XML(ツールのバグ)は素通りする] → ツール側テストで「source url = diff 元 URL」を保証する契約。アプリ側は関知しない設計を明記
- [feedUrl の表記揺れ(http/https・www)で正当なペアが拒否される] → 仕様どおりの挙動(事故ではない)。拒否メッセージに両辺の URL を含め、スキルの結果対応表に「第 1 引数は購読登録時の文字列と一字一句同じ」と明記
- [誤注入がすり抜けた場合の復旧が重い(エピソード個別削除の UI がない)] → 三層防御で予防に全振り。復旧経路はスコープ外とし、必要になったときに別 change で検討
- [`insertIgnoringKnown` の戻り値変更が既存経路に影響] → 戻り値の追加のみで挙動不変。既存の計装テストが回帰を検出する
- [Receiver の 8 秒予算内に取得+検証+保存が収まらない] → 対象 XML は数十 KB・数十件で実測上余裕。予算超過時は既存の subscribe と同じくタイムアウト失敗として PC へ返る

## Migration Plan

1. 実装 + `./gradlew check` + `connectedAndroidTest`(`.instrumented` サンドボックスのため本番データに影響なし)
2. 利用者が付録の 2 ファイルを配置
3. `./gradlew installDebug`(上書きインストール。購読・状態は保持)
4. スキル経由で本番注入(実行前確認 → 許可プロンプト → 実行)。期待値: 総数 42・一覧最下部に旧シリーズ〜第14回が日付順で出現・番組名/アートワーク不変・既存回の状態不変
5. ネガティブ確認(任意): 別番組の feedUrl を指定し拒否されることを 1 回実測
6. ロールバック: コード変更の revert のみ(DB スキーマ変更なし。取り込み済みエピソードは残るが、通常のエピソードと等価なので害はない)

## Open Questions

なし(検証方式・結果コード・配置方法は事前の設計セッションで合意済み)。

---

## 付録 A: `scripts/import-episodes.sh`(利用者が配置)

```bash
#!/usr/bin/env bash
#
# PC から podcast-player の既存購読へ、アーカイブ XML のエピソードを取り込む。
#
#   ./scripts/import-episodes.sh <購読中フィードの URL> <アーカイブ XML の URL>
#
# 第1引数が「取り込み先」(購読登録時の文字列と一字一句同じであること)、
# 第2引数が「取り込み元」。アプリ側は XML の出典宣言(<source url>)と第1引数の
# 購読 feedUrl の完全一致を検証し、不一致・宣言なしは全体を拒否する。
#
# コンポーネント名・extra キー・結果コードはアプリ側 AdbFeedReceiver との契約。
# どちらかを変えたらもう一方も直すこと。

set -euo pipefail

PACKAGE="dev.thinkami.podcastplayer"
RECEIVER=".AdbFeedReceiver"
EXTRA_FEED="feed_url"
EXTRA_IMPORT="import_url"

# AdbFeedReceiver.RESULT_* と対応する。0 は「Receiver に届かなかった」を意味する。
RESULT_NOT_DELIVERED=0
RESULT_FAILED=2
RESULT_IGNORED=3
RESULT_IMPORTED=4

die() {
  echo "error: $*" >&2
  exit 1
}

feed_url="${1:-}"
import_url="${2:-}"
[ -n "$feed_url" ] && [ -n "$import_url" ] ||
  die "使い方: $(basename "$0") <購読中フィードの URL> <アーカイブ XML の URL>"

for url in "$feed_url" "$import_url"; do
  case "$url" in
    https://*) ;;
    *) die "https:// の URL を指定してください: $url" ;;
  esac
  case "$url" in
    *"'"*) die "シングルクォートを含む URL には対応していません: $url" ;;
  esac
done

adb="$(command -v adb || true)"
if [ -z "$adb" ]; then
  adb="$HOME/Library/Android/sdk/platform-tools/adb"
fi
[ -x "$adb" ] || die "adb が見つかりません。Android SDK の platform-tools を PATH に通してください"

devices="$("$adb" devices | awk 'NR>1 && $2=="device" {count++} END {print count+0}')"
[ "$devices" -ge 1 ] || die "端末が接続されていません(USB 接続と USB デバッグ許可を確認してください)"
[ "$devices" -eq 1 ] || die "端末が複数接続されています。1台だけにしてください"

"$adb" shell pm list packages | grep -q "^package:${PACKAGE}$" ||
  die "$PACKAGE が端末にインストールされていません(./gradlew installDebug)"

output="$("$adb" shell run-as "$PACKAGE" am broadcast --user 0 \
  -n "${PACKAGE}/${RECEIVER}" \
  --es "$EXTRA_FEED" "'${feed_url}'" \
  --es "$EXTRA_IMPORT" "'${import_url}'" 2>&1 || true)"

code="$(printf '%s\n' "$output" | sed -n 's/.*Broadcast completed: result=\(-\{0,1\}[0-9]\{1,\}\).*/\1/p' | tail -1)"
data="$(printf '%s\n' "$output" | sed -n 's/.*Broadcast completed: result=[^,]*, data="\(.*\)"$/\1/p' | tail -1)"

if [ -z "$code" ]; then
  echo "$output" >&2
  die "broadcast の結果を読み取れませんでした"
fi

case "$code" in
  "$RESULT_IMPORTED")
    echo "取り込みました: ${data:-件数不明}"
    ;;
  "$RESULT_FAILED")
    echo "届きましたが取り込めませんでした: ${data:-理由不明}" >&2
    exit 1
    ;;
  "$RESULT_IGNORED")
    echo "引数が渡っていません: ${data:-理由不明}" >&2
    exit 1
    ;;
  "$RESULT_NOT_DELIVERED")
    echo "$output" >&2
    die "Receiver に届きませんでした(コンポーネント名か run-as の可否を確認してください)"
    ;;
  *)
    echo "$output" >&2
    die "未知の結果コードです: $code"
    ;;
esac
```

## 付録 B: `.claude/skills/import-podcast-episodes/SKILL.md`(利用者が配置)

```markdown
---
name: import-podcast-episodes
description: 過去回アーカイブ XML を podcast-player の既存購読に取り込む。ユーザーが「過去回を取り込んで」「アーカイブをインポートして」と依頼したとき、またはアーカイブ XML を既存番組のエピソード一覧へ合流させたいときに使用する。
---

`scripts/import-episodes.sh` を実行してください。ただし低頻度の操作なので、
実行前に必ず以下の 2 段階(引数の確定 → 実行前確認)を踏むこと。

## 1. 引数の確定(何を渡すか)

実行に必要なのは URL 2 つ。順序に意味がある。

    ./scripts/import-episodes.sh <取り込み先の購読フィードURL> <アーカイブXMLのURL>

- 第1引数(取り込み先): アプリに登録済みの購読フィード URL。
  購読登録時の文字列と一字一句同じであること(http/https や www の揺れも
  不一致として拒否される)
- 第2引数(取り込み元): 過去回アーカイブ XML の URL。Gist の場合は
  コミットハッシュを含まない短縮形 raw URL
  (https://gist.githubusercontent.com/<user>/<id>/raw/<file>)を使う

どちらか一方でも不明・曖昧な場合は、推測で補完せず利用者に確認する。

## 2. 実行前確認(必須)

第2引数の XML を取得して以下を要約し、利用者の承認を得る。
承認なしに実行してはならない。

1. item 件数
2. 全 item の `<source url>` の値(= この XML が名乗る出典フィード)
3. 最初と最後の item のタイトル

提示例:

    取り込み先: セキュリティのアレ (https://www.tsujileaks.com/?feed=podcast)
    取り込み元: are.xml(42件: 第1回〜 … 〜第14回)
    XML の出典宣言: https://www.tsujileaks.com/?feed=podcast → 取り込み先と一致
    この内容で実行してよいですか?

出典宣言と取り込み先が食い違って見える場合は、実行せずその旨を報告する
(アプリ側でも拒否されるが、実行前に気づくのが望ましい)。
要約は表示のためだけに行い、取り込み可否の判断はしない(判断はアプリ側の一元管轄)。

## adb コマンドを自分で組み立てないこと

コンポーネント名・extra キー・`--user 0` の要否・端末側シェルのクォート・
結果コードの意味はすべてアプリ側 `AdbFeedReceiver` との契約であり、スクリプトが
一元的に持っている。`adb shell am broadcast ...` を直接実行してはいけない。
スクリプトが動かない場合は、回避策を即興で組み立てず、エラーメッセージを
そのまま利用者に伝えること。

## 結果の読み方

スクリプトの出力をそのまま伝えれば十分。終了コード 0 が成功
(「取り込みました: N件(新規 n件)」)。よくある失敗:

- 出典宣言(source)がない — 取り込み用アーカイブではない XML を渡している
  (公式フィードそのものを指定した場合もこれになる)
- 出典宣言と購読が一致しない — 引数ペアの取り違え。両方の URL を見直す
- 購読されていない — 第1引数が購読登録時の文字列と一字一句一致していない
- 端末が接続されていません — USB 接続と USB デバッグ許可を確認する
- Receiver に届きませんでした — アプリが古い可能性(./gradlew installDebug)

拒否・失敗したら回避策を探さず、状況を利用者に報告して指示を仰ぐこと。
```
