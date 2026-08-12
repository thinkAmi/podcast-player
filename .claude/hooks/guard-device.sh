#!/bin/bash
# PreToolUse フック: 実機の本番アプリを壊す adb / run-as を検知してブロックする。
#
# 位置づけ(重要): これは主防御ではない。主防御は .claude/settings.json の
# 完全固定 allowlist と、そこに無いコマンドが必ずプロンプトへ落ちること。
# PreToolUse は exit 2 以外(内部エラー・スクリプト不在・実行権限なし)では
# 素通りする fail-open のため、このフックを唯一の防壁にしてはならない。
# ここが担うのは「見慣れた形の破壊コマンドを反射的に承認する事故」の抑止だけ。
#
# 終了コード: 2 = ブロック / 0 = 決定なし(通常の権限フローへ委ねる)

set -uo pipefail
trap 'echo "guard-device: 内部エラーのためブロックしました" >&2; exit 2' ERR

readonly PKG='dev.thinkami.podcastplayer'
readonly BLOCK=2
readonly NO_DECISION=0

block() {
  echo "guard-device: $1" >&2
  echo "本番アプリ($PKG)のデータを壊す操作はブロックされます。" >&2
  echo "本当に必要なら利用者自身が実行してください。回避策を探さないこと。" >&2
  exit "$BLOCK"
}

payload=$(cat)

# --- JSON 抽出(失敗は内部エラー扱いでブロック) ---------------------------
if command -v jq >/dev/null 2>&1; then
  tool_name=$(printf '%s' "$payload" | jq -r '.tool_name // ""' 2>/dev/null) ||
    block "フック入力の解析に失敗しました"
  command_str=$(printf '%s' "$payload" | jq -r '.tool_input.command // ""' 2>/dev/null) ||
    block "フック入力の解析に失敗しました"
elif command -v python3 >/dev/null 2>&1; then
  tool_name=$(printf '%s' "$payload" |
    python3 -c 'import sys,json;print(json.load(sys.stdin).get("tool_name",""),end="")' 2>/dev/null) ||
    block "フック入力の解析に失敗しました"
  command_str=$(printf '%s' "$payload" |
    python3 -c 'import sys,json;print(json.load(sys.stdin).get("tool_input",{}).get("command",""),end="")' 2>/dev/null) ||
    block "フック入力の解析に失敗しました"
else
  block "JSON パーサ(jq / python3)が見つかりません"
fi

if [ "$tool_name" != "Bash" ] || [ -z "$command_str" ]; then
  exit "$NO_DECISION"
fi

# --- 正規化: クォート除去 + 空白(改行含む)を単一スペースへ ----------------
# クォートだけを外すのは `run-as "pkg" rm` のような整形差で判定が破れるのを防ぐため。
normalized=$(printf '%s' "$command_str" | tr -d '\042\047' | tr -s '[:space:]' ' ')

# --- 計装テスト用パッケージを退避し、本番パッケージの出現だけを残す ---------
# 全出現を置換するため `adb uninstall <pkg>.instrumented && adb uninstall <pkg>` のような
# 複数出現でも後段の本番パッケージを取りこぼさない。
stripped=${normalized//${PKG}.instrumented/__INSTRUMENTED_PKG__}

# 本番パッケージが登場しないコマンドには関心を持たない。
case "$stripped" in
*"$PKG"*) ;;
*) exit "$NO_DECISION" ;;
esac

# --- 破壊動詞との共起で判定(pm / cmd package の双方を包含) ----------------
# 語として出現するかを見るため、`pm` や `cmd package` という前置詞には依存しない。
if printf '%s' "$stripped" |
  grep -qE '(^| )(uninstall|clear|disable-user|disable|suspend)( |$)'; then
  block "本番パッケージに対する破壊的な操作を検知しました: ${command_str}"
fi

# --- run-as は読み取りのみ許可(ホワイトリスト方式) ------------------------
# 危険な語を列挙するのではなく安全な語だけを通す。未知のコマンドはブロック側へ倒れる。
rest="$stripped"
while [ "$rest" != "${rest#*run-as $PKG}" ]; do
  rest=${rest#*run-as $PKG}
  next=$(printf '%s' "$rest" | awk '{print $1}')
  case "$next" in
  cat | ls) ;;
  *) block "run-as 経由の読み取り以外の操作を検知しました: ${command_str}" ;;
  esac
done

exit "$NO_DECISION"
