# 自動連続再生を古→新の時系列順にする

## Why

自動連続再生の順序が利用者の聴き方と逆になっている。一覧は新しい順(`publishedAt DESC`)で表示され、再生キューは「選んだエピソードから一覧の下方向(=より古い方向)」に組まれるため、聴き終わるたびに過去へ遡ってしまう。利用者の実際の運用は「残っている中で一番古い回から聴き、新しい回へ進む」であり、キューの向きを時系列順(古→新)へ反転する必要がある。

## What Changes

- 再生キューの構築規則を反転する: 選んだエピソードを起点に、それより**新しい**DL済みエピソードを古→新の順で並べる(現在は「それより古い方向」)。一覧の表示順(新しい順)は変えない
- 選んだエピソードより古いエピソードはキューに含めない(利用者は常に「残りの中で一番古い回」から開始する運用のため、一方通行で足りる)
- 未DLスキップ・自動DL禁止・キュー終端で停止、という既存の約束はそのまま維持する
- `PlaybackQueue.nextAutoPlayable` を削除する(**BREAKING** ではない: main から未使用のデッドコード。事前組み立て方式への移行で役目を終えており、対応するテストも削除する)

## Capabilities

### New Capabilities

(なし)

### Modified Capabilities

- `playback`: 「リスト順の自動継続再生」要件を「時系列順(古→新)の自動継続再生」に変更する。キュー構築の向きが反転し、「画面の並び順=再生順」という規則は「画面の並び順の逆(=時系列順)=再生順」に置き換わる。統合エピソード画面のキュー構築規則の文言も同期して更新する

## Impact

- `logic/PlaybackQueue.kt` — `playbackOrderFrom` の向き反転、`nextAutoPlayable` の削除、KDoc の書き直し
- `app/src/test/.../logic/PlaybackQueueTest.kt` — 期待値の反転、`nextAutoPlayable` ケースの削除
- `app/src/test/.../logic/PlaybackQueuePropertyTest.kt` — `nextAutoPlayable` を使うプロパティの削除、残る不変条件の再定義(2関数の等価性前提が消えるためクラスコメントも書き直し)
- `openspec/specs/playback/spec.md` — 「リスト順の自動継続再生」要件と統合エピソード画面のキュー構築規則の文言
- 変更不要: `player/PlaybackService.kt`(キューの向きを知らない)、`ui/` の画面追随・ミニプレイヤー・視聴済み確定(すべて方向非依存)、DAO の `ORDER BY`(表示順は維持)
- Kover の `logic/` 90% ゲートには未使用関数の削除が有利に働く
