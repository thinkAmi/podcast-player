# Tasks: podcast-player-mvp

## 1. プロジェクト基盤

- [ ] 1.1 開発環境の確認(Android Studioインストール済み・Pixel 7 Proの開発者向けオプション+USBデバッグ有効化・adbで実機認識)
- [x] 1.2 Androidプロジェクト新規作成(Kotlin + Compose、applicationId `dev.thinkami.podcastplayer`、minSdk=targetSdk=36、単一モジュール)
- [ ] 1.3 git初期化・.gitignore整備・MITライセンス・GitHub公開リポジトリ `podcast-player` 作成・初回push
- [x] 1.4 リポジトリ制限(settings.gradle.ktsで google()/mavenCentral() のみ)+ Gradle dependency verification 有効化
- [x] 1.5 依存追加(Compose / Media3 / Room+KSP / kotlinx-coroutines のみ。バージョン完全固定)
- [x] 1.6 品質ツール導入: ktfmt / Android Lint(warningsAsErrors)/ allWarningsAsErrors / detekt 2.0.0-alpha.5(design D10のルール群をdetekt.ymlに設定)
- [x] 1.7 Kover導入(logic/パッケージのみ行カバレッジ90%ゲート)
- [x] 1.8 最小CI(GitHub Actions: build + JVMテスト + detekt/lint + koverVerify。actionsはSHA固定)
- [x] 1.9 CLAUDE.md作成(4層構造・detektルール・モックレス方針・「機能を増やさない/通信はユーザー起点」を事前指示として記載)

## 2. データ層とロジック層の骨格

- [x] 2.1 Roomスキーマ定義(feed: フィルター2カラム+playback_speed含む / episode: guid・played・downloaded・position_ms・favorite・enclosure_url・local_path含む)
- [x] 2.2 DAO定義(購読CRUD / エピソードupsert / フィルタークエリ(Flow) / 状態更新 / 一括視聴済み・未聴UPDATE)
- [x] 2.3 logic/: フィルター条件・視聴済み判定(残り10秒定数)・削除対象決定(played AND NOT favorite)の純粋関数 + JVMユニットテスト
- [x] 2.4 手動DIコンテナ(Applicationクラスに依存物組み立て)
- [x] 2.5 Room DAOの計装テスト(in-memory DB。フィルターAND条件・一括UPDATE・状態保持を検証)

## 3. フィード購読(feed-subscription)

- [x] 3.1 HttpURLConnectionによるフィード取得(タイムアウト・エラーハンドリング)
- [x] 3.2 XmlPullParserによるRSS 2.0+iTunes名前空間パーサー(logic/に構造解釈、壊れitemスキップ)
- [x] 3.3 購読予定の実フィードをテストフィクスチャ化しパーサーの計装テスト作成
- [x] 3.4 購読登録・削除・フィード更新(guid既知判定・状態保持upsert)のリポジトリ実装
- [x] 3.5 アートワーク取得(BitmapFactory + ファイルキャッシュ。フィード更新時のみ取得)

## 4. ダウンロード管理(episode-download)

- [x] 4.1 HttpURLConnectionによるファイルDL(getExternalFilesDirへ保存、進捗通知、失敗時は未DL状態へ)
- [x] 4.2 従量制回線判定(ConnectivityManager)と確認ダイアログ分岐
- [x] 4.3 DL状態のDB反映(downloaded・local_path)と手動再試行

## 5. 再生(playback)

- [x] 5.1 MediaSessionService実装(ExoPlayer結線・フォアグラウンドサービス・通知・オーディオフォーカス)
- [x] 5.2 ±10秒スキップ・番組ごと再生速度(読み書き)・再生位置の定期保存と復元
- [x] 5.3 再生完了イベント→視聴済み化→自動継続(現在リスト順の次のDL済みへ。未DLスキップ・継続先なしで停止)
- [ ] 5.4 実機での再生スモークテスト(画面消灯継続・ロック画面操作・Bluetooth・着信一時停止)

## 6. 視聴状態と自動削除(listening-status)

- [x] 6.1 自動視聴済み判定(残り10秒到達)→即時削除の結線
- [x] 6.2 手動トグル→Undoスナックバー5秒→削除実行のフロー(1件・一括共通)
- [x] 6.3 番組メニューの一括「すべて視聴済み/未聴」実装
- [x] 6.4 削除実行(ファイル削除+downloaded解除、favorite除外、DB記録・URL保持)の計装テスト

## 7. UI(4画面+ミニプレイヤー)

- [x] 7.1 購読一覧画面(番組リスト・「+」URL入力ダイアログ・pull-to-refresh全番組更新)
- [x] 7.2 エピソード一覧画面(フィルターチップ2個・行=DL/再生アイコン+✓トグル+本文タップ詳細・⋮メニュー・pull-to-refresh単独更新)
- [x] 7.3 詳細画面(ショーノート表示)
- [x] 7.4 プレイヤー画面(アートワーク・シークバー・速度・±10秒・ショーノート)+ ミニプレイヤー
- [x] 7.5 主要画面のComposeスモークテスト(フィルター切替・✓トグルの表示反映)

## 8. 統合検証

- [ ] 8.1 全計装テストの実機実行(DAO・パーサー・削除)
- [ ] 8.2 Koverゲート・detekt・lint・全テストのグリーン確認(ローカル+CI)
- [ ] 8.3 実機E2E: 実フィード購読→更新→DL→外でのバックグラウンド再生→自動視聴済み→自動削除→フィルター追随の一連確認
- [ ] 8.4 移行リハーサル: 実際の購読番組を登録し「すべて視聴済み」→聴きたいものを未聴へ戻す運用の確認
