# Proposal: podcast-player-mvp

## Why

Podcast Addict は個人利用には機能が多すぎて認知負荷が高く、唯一必要だった「番組ごとのAND条件フィルター(未聴 × DL済み)」が存在しない。個人のユースケース(事前DLして外で聴く)に最適化した、機能を増やさないことを設計思想とする自作Androidポッドキャストプレイヤーを作る。

## What Changes

- Androidネイティブアプリ(Kotlin + Jetpack Compose)をゼロから新規作成する
- RSS URL手入力による購読管理と、pull-to-refreshによる手動フィード更新
- エピソードの手動ダウンロード(DLファースト。ストリーミング再生はしない)
- Media3によるローカルファイルのバックグラウンド再生(通知・ロック画面・速度・±10秒スキップ・リスト順自動継続)
- 視聴済み管理(残り10秒で自動判定 + 手動トグル + 番組単位の一括切り替え)と再生位置の記憶
- 視聴済みエピソードのDLファイル自動削除(favorite除外ロジック。手動マーク時のみ5秒Undo)
- 番組ごとに永続化されるAND条件フィルター(「未聴のみ」×「DL済みのみ」)
- 品質基盤: モックレステスト、Kover(logic層90%ゲート)、ktfmt + Android Lint + detekt、最小CI

## Capabilities

### New Capabilities

- `feed-subscription`: RSS URLによる番組の購読登録・削除、手動フィード更新(全番組一括/番組単位)、RSSパース
- `episode-download`: エピソード単位の手動DL、アプリ専用領域への保存、モバイル回線時の確認ダイアログ、失敗時の手動再試行
- `playback`: DL済みローカルファイルのバックグラウンド再生、通知/ロック画面/Bluetooth操作、番組ごとの再生速度、±10秒スキップ、再生位置の記憶・復元、フィルター済みリスト順の自動継続(DL済みのみ・自動DLなし)
- `listening-status`: 視聴済みの自動判定(残り10秒)・手動トグル・番組単位一括切り替え、視聴済みDLファイルの自動削除(favorite除外・手動時Undo)
- `episode-filtering`: 番組ごとの「未聴のみ」「DL済みのみ」AND条件フィルターと設定の永続化

### Modified Capabilities

(なし — 新規プロジェクトのため既存specはない)

## Impact

- 新規リポジトリ(現状はmise.tomlとopenspec/のみ)。Androidプロジェクト一式を新規生成する
- 依存: Google製Jetpack(Compose/Media3/Room)+ JetBrains(Kotlin/coroutines/Kover)のみ。実行時サードパーティ依存ゼロ(ビルド/テスト時のみktfmt・detekt・JUnit 4)
- 通信はHttpURLConnection、RSSパースはXmlPullParser(いずれもOS標準・自作)
- 対象端末はPixel 7 Pro単一(minSdk = targetSdk = 36)。互換性分岐なし
- GitHub公開リポジトリ(MIT)+ 最小CI(build/JVMテスト/静的解析/Kover)
- 配布はAndroid StudioからのUSB直接インストール。Play Store非対応
