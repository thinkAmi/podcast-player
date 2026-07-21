# podcast-player

個人用の Android ポッドキャストプレイヤー。利用者は開発者本人ひとり、対象端末は1台のみ。

既存のポッドキャストプレイヤーの多機能さ(認知負荷)への反動として作った。だから**機能を増やさないことが機能**。

設定画面はなく、閾値などの設定値はコード内定数(ソースコードが設定ファイル)。
通信は例外なくユーザー起点で、自動更新・自動 ダウンロード・自動リトライは実装しない。

## できること

- RSS URL を手入力して番組を購読(検索・ディスカバリー機能はない)
- pull-to-refresh でフィードを手動更新
- エピソードを手動ダウンロードして、オフラインで聴く(ストリーミングはしない)
- バックグラウンド再生、通知・ロック画面・Bluetooth からの操作、番組ごとの再生速度
- 最後まで聴いたら自動で視聴済みになり、ダウンロードファイルは自動削除
- 番組ごとに永続化される絞り込み(「未聴のみ」×「DL済みのみ」の AND)—
  このアプリを作った動機そのもの

## 技術スタック

- Kotlin + Jetpack Compose / Media3 (ExoPlayer + MediaSession) / Room
- 通信は `HttpURLConnection`、RSS パースは `XmlPullParser`、画像は `BitmapFactory` —
  すべて OS 標準 API。**実行時のサードパーティ依存はゼロ**
  (信頼するベンダーは Google と JetBrains のみ)
- 品質ゲート: ktfmt / detekt / Android Lint(warningsAsErrors)/
  Kover(logic 層 90%)/ Gradle dependency verification

仕様と設計の経緯は [openspec/changes/podcast-player-mvp/](openspec/changes/podcast-player-mvp/)
にある。設計判断の理由(なぜ OkHttp を使わないか、なぜ設定画面がないか等)は
[design.md](openspec/changes/podcast-player-mvp/design.md) が正本。

## ビルド

```
./gradlew build          # 全品質ゲート込み
./gradlew installDebug   # USB 接続した実機へインストール
```

`minSdk = targetSdk = compileSdk = 36`。互換性のための分岐は書かない。

## ライセンス

[MIT](LICENSE)
