# Tasks: close-player-when-queue-ends

## 1. 実装(ui 層のみ)

- [x] 1.1 `PlayerScreen` に `LaunchedEffect` を追加し、`status.episodeId` の 非null → null 遷移を検出して `onBack()` を呼ぶ(design.md D1。「null なら閉じる」にしないこと)
- [x] 1.2 pop を呼ぶ側に二重 pop ガードを入れる: 実行前に現在の backstack entry がまだプレイヤー画面であること(route 確認、または entry の lifecycle が RESUMED)を確認する。ユーザー操作由来の `onBack` と自動クローズが同じガードを通る形にする(design.md D2)
- [x] 1.3 design.md D3 の受容トレードオフ(プロセス死復元での取り残し)をコード近傍の KDoc/コメントに一行残す
- [x] 1.4 `PlaybackConnection.publishStatus` を「`controller == null` のときは何も流さない(最後の状態を保持)」に変える(design.md D4。実機検証 3.3 で発見した、再接続の谷間に UI の定期更新が空 status を流して誤 pop する競合の修正)

## 2. 品質ゲート

- [x] 2.1 `./gradlew check` が通ることを確認する(ktfmt / detekt / Lint / Kover 90% すべて)

## 3. 実機検証

Pixel 7 Pro で実施。本番データを消費しないよう、`.instrumented` パッケージ(別サンドボックス)+
Mac から `adb reverse` 経由で配信した使い捨てフィード(無音 WAV 15秒 × 2、`http://127.0.0.1:8765`)
で検証した。cleartext-to-loopback は instrumented ビルドタイプのみ許可のため、この経路が成立する。
購読登録は `scripts/add-feed.sh` のスクラッチパッド上のコピー(対象パッケージと loopback 許可の
2点だけ変更)で行い、本体スクリプトは変更していない。検証後は `.instrumented` をアンインストールし、
回転設定(accelerometer_rotation=0 / user_rotation=0)も元通りであることを確認。本番アプリへは
`installDebug` で反映済み(購読・視聴状態・DLファイルは保持)。

- [x] 3.1 プレイヤー画面を開いたままキュー末尾のエピソードを鳴り切らせる → プレイヤー画面が自動で閉じて一覧へ戻り、ミニプレイヤーも表示されないことを確認する
  - 3回観測(単独再生の終端、自動継続後の終端、回転検証後の終端)。いずれも一覧へ戻り、
    ミニプレイヤーなし、行は視聴済み✔+DLアイコンに復帰
- [x] 3.2 DL済み2件で自動継続再生させ、プレイヤー画面を開いたままエピソード境界をまたぐ → 画面が閉じず、タイトルが次のエピソードへ切り替わることを確認する
  - ep1 0:13/0:15 → ep2 0:02/0:15 を画面を開いたまま観測。閉じずにタイトルのみ切り替わった
    (検証フィードにショーノートは無いため表示切替はタイトルで確認)
- [x] 3.3 再生中にプレイヤー画面で画面を回転する → 画面が閉じず再生表示が継続することを確認する(再接続直後の初期 null で誤爆しないこと)
  - **初回実装で不合格**: 2回目の回転で誤って閉じた。原因は PlayerViewModel の 500ms tick が
    再接続の谷間(controller=null)に空 status を publish し、遷移検出が終端と誤認する競合
    (design.md D4 に記録)。`publishStatus` の修正後、landscape⇄portrait を4回連続で
    切り替えてすべて生存、その後の自動継続・終端クローズも正常
- [x] 3.4 キュー終端の直前で閉じるボタンを押すのとほぼ同時に鳴り終わらせる → 一覧画面より下まで pop されないことを確認する
  - 終端の約1.5秒前・約0.5秒前に閉じる操作を行う2試行。いずれもエピソード一覧で停止し、
    購読画面までの二重 pop は発生しなかった
