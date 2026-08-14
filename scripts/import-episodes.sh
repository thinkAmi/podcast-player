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