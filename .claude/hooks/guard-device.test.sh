#!/bin/bash
# guard-device.sh の判定に対する回帰テスト。
#
# レッドチーム検証で見つかった既知バイパスを全数列挙する。
# ここに並ぶのは文字列だけで、adb や gradle を実行することはない(端末に触れない)。
#
# 実行: ./.claude/hooks/guard-device.test.sh

set -uo pipefail

HOOK="$(cd "$(dirname "$0")" && pwd)/guard-device.sh"
PKG='dev.thinkami.podcastplayer'
pass=0
fail=0

payload_of() {
  python3 -c 'import json,sys; print(json.dumps({"tool_name": sys.argv[1], "tool_input": {"command": sys.argv[2]}}))' "$1" "$2"
}

check() { # check <期待コード> <説明> <コマンド文字列> [tool_name]
  local expected="$1" desc="$2" cmd="$3" tool="${4:-Bash}"
  local actual payload_file
  # パイプで渡すとフック側が stdin を読み切らなかったときに測る終了コードが
  # 生成側(python3 の SIGPIPE)のものにすり替わる。ファイル経由にしてフックの
  # 終了コードだけを測る。
  payload_file=$(mktemp)
  payload_of "$tool" "$cmd" >"$payload_file"
  "$HOOK" <"$payload_file" >/dev/null 2>&1
  actual=$?
  rm -f "$payload_file"
  if [ "$actual" = "$expected" ]; then
    pass=$((pass + 1))
  else
    fail=$((fail + 1))
    echo "FAIL: $desc"
    echo "      command : $cmd"
    echo "      expected: $expected / actual: $actual"
  fi
}

echo "== ブロックすべきもの(exit 2) =="

check 2 "素の uninstall" "adb uninstall $PKG"
check 2 "シリアル指定つき uninstall(deny の接頭辞を外す形)" "adb -s ABC123 uninstall $PKG"
check 2 "pm clear" "adb shell pm clear $PKG"
check 2 "クォート付き pm clear(C-3)" "adb shell \"pm clear $PKG\""
check 2 "cmd package clear(H-2)" "adb shell cmd package clear $PKG"
check 2 "cmd package uninstall(H-2)" "adb shell cmd package uninstall $PKG"
check 2 "pm disable-user" "adb shell pm disable-user $PKG"
check 2 "pm suspend" "adb shell pm suspend $PKG"
check 2 "run-as からの rm" "run-as $PKG rm -rf databases"
check 2 "run-as のパッケージ名がクォート済み(C-3)" "run-as \"$PKG\" rm -rf databases"
check 2 "run-as の前後が二重スペース(C-3)" "run-as  $PKG  rm files"
check 2 "run-as から sh -c 経由" "adb shell run-as $PKG sh -c 'rm databases/podcast.db'"
check 2 "run-as から sqlite3 で書き込み" "run-as $PKG sqlite3 databases/podcast.db \"delete from episodes\""
check 2 "run-as の後続コマンドなし(対話シェル)" "run-as $PKG"
check 2 "instrumented を先に置いて本番を後ろに隠す(M-1)" "adb uninstall $PKG.instrumented && adb uninstall $PKG"
check 2 "dumpsys からの device-side インジェクション" "adb shell dumpsys package; pm clear $PKG"
check 2 "改行区切りの複合コマンド" "adb devices
adb uninstall $PKG"

echo "== 通すべきもの(exit 0 = 決定なし、通常の権限フローへ) =="

check 0 "デバイス一覧" "adb devices"
check 0 "計装テストパッケージの掃除" "adb uninstall $PKG.instrumented"
check 0 "run-as からの cat(実機DB読み取り手順)" "run-as $PKG cat databases/podcast.db"
check 0 "run-as からの ls" "run-as $PKG ls databases"
check 0 "パッケージ指定の dumpsys(読み取り)" "adb shell dumpsys package $PKG"
check 0 "パッケージ名を含まない pm list" "adb shell pm list packages"
check 0 "品質ゲート" "./gradlew check"
check 0 "フィード登録スクリプト" "./scripts/add-feed.sh https://example.com/rss"
check 0 "無関係なコミット" "git commit -m 'close player when queue ends'"
check 0 "Bash 以外のツールには関与しない" "adb uninstall $PKG" "Edit"

echo
echo "pass=$pass fail=$fail"
[ "$fail" -eq 0 ] || exit 1
echo "すべて通過しました。"
