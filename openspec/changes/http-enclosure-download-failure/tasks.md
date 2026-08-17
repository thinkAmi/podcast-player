# タスク

順序は固定: **1(B)→ 2(実機確認・利用者が行う)→ 3(A)**。2 が済む前に 3 に着手しない。
A を先に入れると「取得できないURL」の表示を実データで再現できなくなるため。

## 1. (B) 失敗理由の種別表示

- [x] 1.1 `logic/DownloadFailure`(sealed: `UnsupportedUrl` / `HttpStatus(code)` / `Connection` / `CompressedResponse` / `Save`)を追加する
- [x] 1.2 `logic/DownloadFailurePresentation` に `label(failure)` と `suggestsRetry(failure)` を追加し、design.md D2 の表で全数列挙のユニットテストを書く(HttpStatus は代表的なコード数個)
- [x] 1.3 `HttpFetcher` の HTTP ステータス失敗を `HttpStatusException(status, url): IOException` に型付けする(メッセージ「HTTP <status>: <url>」は維持)。既存の計装テスト `HttpFetcherTest` を型で検証する形に更新する
- [x] 1.4 `DownloadState.Failed(failure: DownloadFailure, detail: String?)` に変更し、`EpisodeDownloader` で例外型 → 種別を分類する(`UnsupportedUrlException` / `HttpStatusException` / `CompressedResponseException` / その他 `IOException` → `Connection` / rename 失敗 → `Save`)。`IOException` の一括捕捉は維持する
- [x] 1.5 `EpisodeRow.episodeSubtitle` を `label` +(`suggestsRetry` のときのみ)「 ・ タップでやり直す」に変更する
- [x] 1.6 `EpisodeActionMapping` と `EpisodeActionMappingTest`・`EpisodeRowTest` を新しい `Failed` 型に追随させる(`hasFailedDownload` の判定は不変)
- [x] 1.7 `./gradlew check` を通し、`installDebug` で実機へ上書きインストールする(購読・視聴状態は保持される)
- [x] 1.8 B を単独でコミットする(確認時点のビルドを履歴に残す)

## 2. 実機確認(利用者が行う checkpoint)

- [ ] 2.1 「セキュリティのアレ」の第5回「緊急特番的な感じでペチャクチャやろうぜ!スペシャル」の DL アイコンをタップし、行の 2 行目が「2017/07/01 ・ 26分 ・ DL失敗(取得できないURL)」で、末尾に「タップでやり直す」が**無い**ことを確認する(通信は発生しないのでギガを消費しない)
- [ ] 2.2 長いタイトルの行(第4回・第3回など)でも同じ操作をし、2 行目が崩れず読めることを確認する
- [ ] 2.3 文言・案内の有無に修正があれば D2 の表と 1.2 のテストを直してから 3 へ進む。問題なければそのまま 3 へ

## 3. (A) 平文 http の https 書き換え

- [ ] 3.1 `HttpUrlPolicy.isAllowed` を `resolveFetchUrl(url): String?` に置き換える(https → そのまま / loopback http → そのまま / それ以外の http → 先頭スキームのみ `https://` に置換 / 他 → null)。`HttpUrlPolicyTest` を更新し、大文字スキーム・クエリ内の `http://`・userinfo 詐称・空白を含む URL のケースを足す
- [ ] 3.2 `HttpFetcher.connect` を `resolveFetchUrl` の結果で接続する形にする(null は `UnsupportedUrlException`)。DB や呼び出し側の URL は変えない
- [ ] 3.3 `./gradlew check` を通し、`installDebug` で実機へ上書きインストールする
- [ ] 3.4 実機で第5回を DL し、進捗表示 → DL 済みになること、再生できることを確認する(Wi-Fi 接続で行う。実サイズ 37.4MB がフィード申告 25MB を超えるため進捗が 100% を超えて見えるのは既知)
- [ ] 3.5 `connectedAndroidTest` で `HttpFetcherTest`(loopback が書き換えられないこと)が通ることを確認する(`.instrumented` 別パッケージで実行される)
- [ ] 3.6 A をコミットする
