# add-favorite-toggle

## Why

繰り返し聴きたいエピソードがあっても、現状は聴き終わった瞬間にDLファイルが自動削除されてしまい、聴き直すたびに再DLが必要になる。データモデル・削除判定(`ListeningRules.shouldDeleteDownload`)・DAO は初日から favorite を前提に作られており、欠けているのは UI と配線だけ。その受け皿を実際に使えるようにする。

## What Changes

- 統合エピソード画面に favorite(★)トグルを追加する。エピソード一覧の行には置かない
- ★の付与・解除は DB の favorite カラムを更新する(既存の `EpisodeDao.setFavorite` に配線)
- ★を外したとき、削除対象(視聴済み かつ DL済み)のファイルがある場合に限り、既存の手動視聴済み化と同じ「5秒Undo付きスナックバー → 猶予明けに削除」を適用する。削除が起きない状況ではスナックバーを出さない
- 現在のエピソード(再生キューが指しているもの)は★解除でも猶予明けの削除対象にしない。鳴り終わりの自動削除(favorite=false になっているため既存経路で削除される)に任せる
- favorite の意味は「聴き終わっても消さない」に限定する。専用フィルター・並び順への影響・バッジ・購読一覧への表示は追加しない

## Capabilities

### New Capabilities

(なし)

### Modified Capabilities

- `listening-status`: 「favoriteカラムはMVPではデータモデルにのみ存在し、UIは設けない」を撤回し、★トグルの要件を追加する。★解除時の猶予付き削除、現在のエピソードの削除除外、favorite の意味の限定(MUST NOT 群)を要件化する

## Impact

- `logic/`: ★解除時に「削除を予約すべきか」「現在のエピソードを除外する」の判断を純粋関数として追加(`ListeningRules` または近傍)。`shouldDeleteDownload` 自体は変更しない
- `ui/episodes/EpisodeDetailScreen.kt`: ★トグルの表示と操作。アイコンは導入済みの material-icons-extended(Star / StarBorder)を使用し、依存追加はなし
- `ui/`: favorite 解除用の Undo スナップショット(既存 `UndoablePlayedChange` と同じレールの小さな型)
- `data/`: `EpisodeRepository` に favorite 更新の口を追加し、`RoomEpisodeRepository` から既存 DAO に配線。削除実行は既存の `deleteDownloadsIfEligible` を流用(実行時に行を読み直して再判定する性質により、猶予中の★付け直し・未聴戻しは自動的に削除中止となる)
- DB マイグレーション: 不要(favorite カラムは既存、default false)
- `player/`: 変更なし
