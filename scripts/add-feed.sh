#!/usr/bin/env bash
#
# PC から podcast-player に RSS を購読登録する。
#
#   ./scripts/add-feed.sh https://example.com/feed.xml
#
# 端末の UI を触らずに登録できるが、これは「転送機能」ではない。USB デバッグを許可した PC
# だけが通れる受け口(exported="false" の BroadcastReceiver)を run-as 経由で叩いているだけで、
# 信頼境界は計装テストと同じ。端末内の他アプリからは OS が配達を拒否する。
#
# コンポーネント名・extra キー・結果コードはアプリ側 AdbFeedReceiver との契約。
# どちらかを変えたらもう一方も直すこと。

set -euo pipefail

PACKAGE="dev.thinkami.podcastplayer"
RECEIVER=".AdbFeedReceiver"
EXTRA_KEY="feed_url"

# AdbFeedReceiver.RESULT_* と対応する。0 は「Receiver に届かなかった」を意味する
# (am broadcast は不達でも 0 を返すため、0 以外であること自体が配達の証明になる)。
RESULT_NOT_DELIVERED=0
RESULT_SUBSCRIBED=1
RESULT_FAILED=2
RESULT_IGNORED=3

die() {
  echo "error: $*" >&2
  exit 1
}

url="${1:-}"
[ -n "$url" ] || die "使い方: $(basename "$0") <RSS の URL>"

# アプリ側の HttpUrlPolicy と同じ判断をここでも行う。正はアプリ側で、こちらは
# 端末まで往復せずに気づくための早期警告。
case "$url" in
  https://*) ;;
  *) die "https:// の URL を指定してください: $url" ;;
esac

# URL は端末側シェルのためにシングルクォートで包む(そうしないと & 以降が切り捨てられる)。
# URL 自体にシングルクォートが含まれると包みが壊れるので、黙って壊れた URL を送らずに止める。
case "$url" in
  *"'"*) die "シングルクォートを含む URL には対応していません: $url" ;;
esac

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

# --user 0 が必須。既定の USER_CURRENT(-2) の解決には INTERACT_ACROSS_USERS 権限が要り、
# アプリの uid は持たないため SecurityException になる。
output="$("$adb" shell run-as "$PACKAGE" am broadcast --user 0 \
  -n "${PACKAGE}/${RECEIVER}" --es "$EXTRA_KEY" "'${url}'" 2>&1 || true)"

code="$(printf '%s\n' "$output" | sed -n 's/.*Broadcast completed: result=\(-\{0,1\}[0-9]\{1,\}\).*/\1/p' | tail -1)"
data="$(printf '%s\n' "$output" | sed -n 's/.*Broadcast completed: result=[^,]*, data="\(.*\)"$/\1/p' | tail -1)"

if [ -z "$code" ]; then
  echo "$output" >&2
  die "broadcast の結果を読み取れませんでした"
fi

case "$code" in
  "$RESULT_SUBSCRIBED")
    echo "登録しました: $url"
    ;;
  "$RESULT_FAILED")
    echo "届きましたが登録できませんでした: ${data:-理由不明}" >&2
    exit 1
    ;;
  "$RESULT_IGNORED")
    echo "URL が渡っていません: ${data:-理由不明}" >&2
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
