# タスク: 自動連続再生の時系列順化

## 1. logic の変更

- [x] 1.1 `PlaybackQueue.playbackOrderFrom` を「開始エピソード + それより新しいDL済みを古→新の順」に反転する(開始エピソードが未DL・リスト不在なら空を返すガードは維持)。KDoc を新しい規則と「一覧が新しい順である前提」の明記に書き直す
- [x] 1.2 `PlaybackQueue.nextAutoPlayable` を削除し、オブジェクトの KDoc(「リスト順=再生順」の説明)を新しい規則に合わせて更新する

## 2. テストの更新

- [x] 2.1 `PlaybackQueueTest` から `nextAutoPlayable` のケースを削除し、`playbackOrderFrom` の期待値を時系列順(古→新)に書き直す。「開始より古い回を含めない」「未DLスキップ」「開始が未DLなら空」のケースを揃える
- [x] 2.2 `PlaybackQueuePropertyTest` から `nextAutoPlayable` を使うプロパティを削除し、design.md の4不変条件(先頭=開始、全件DL済み、逆順リストの部分列、開始が未DL/不在なら空)で再定義する。クラスコメントの「2関数は同じ解釈の別表現」の前提も書き直す

## 3. 検証

- [x] 3.1 `./gradlew check` を通す(ktfmt / detekt / lint / Kover 90% を含む)
- [x] 3.2 `./gradlew installDebug` で実機へ配信する(同じ署名の上書きインストールで購読・視聴状態が保持される)
- [x] 3.3 利用者が実機で聴いて確認する: 古い回から再生開始→聴き終えると新しい回へ進む/最新回まで来たら停止しプレイヤーが空に戻る/未DL回はスキップされ通信が発生しない

## 4. spec の同期

- [x] 4.1 実装完了後、`openspec/specs/playback/spec.md` へ delta を反映する(archive 時の sync で行う場合はこのタスクを archive に委ねる)
