# add-favorite-toggle — Design

## Context

削除判定 `ListeningRules.shouldDeleteDownload` は初日から `!episode.favorite` を条件に持ち、`EpisodeEntity.favorite`(default false)と `EpisodeDao.setFavorite` も実装済みだが呼び手がいない。本変更は UI と配線を足すだけで、削除機構そのものは新設しない。

既存の削除の型は2経路ある:

1. **自動**: 鳴り終わりイベント → `markPlaybackCompleted` → `deleteDownloadsIfEligible`(即時)
2. **手動**: 視聴済みトグル → `UndoablePlayedChange`(スナップショット+5秒Undoスナックバー)→ 猶予明けに `deleteDownloadsIfEligible`

`deleteDownloadsIfEligible` は実行時に行を読み直して `shouldDeleteDownload` で再判定するため、猶予中に状態が変われば削除は自然に中止される。この性質を favorite でもそのまま使う。

## Goals / Non-Goals

**Goals:**

- 統合エピソード画面から favorite を付け外しできる
- ★解除で不要になった保持ファイルが、既存の削除の型(猶予付き/鳴り終わり)で確実に消える
- 判断(削除を予約すべきか)を `logic/` の純粋関数に置き、JVM ユニットテストで検証できる

**Non-Goals:**

- favorite によるフィルター・並び順・バッジ・購読一覧表示(意図的に作らない。spec の MUST NOT)
- エピソード一覧の行への★表示
- ファイル削除機構の新設・変更(`deleteDownloadsIfEligible` を流用する)
- DB マイグレーション(不要)

## Decisions

### D1: ★解除時の削除予約の判断は `logic/` の純粋関数に置く

`ListeningRules` に「★を外したとき削除を予約すべきか」を追加する。判断材料は「視聴済みか・DL済みか・現在のエピソードか」の真偽値のみ(`EpisodeActions.actionFor` と同じ流儀)。`shouldDeleteDownload` は変更しない — 猶予明け・鳴り終わりの最終判定として今のまま使われ続ける。

代替案: ViewModel に条件分岐を直書き → 却下。判断と実行の分離(テスト戦略の前提)に反する。

### D2: 現在のエピソードの除外は「予約時」に判定する

★解除の時点で現在のエピソードなら削除を予約しない(スナックバーも出さない)。猶予明けに再チェックはしない。現在のエピソードかは player の状態であり、ViewModel が予約時に知っている情報で判断を完結させる。`deleteDownloadsIfEligible`(data 層)に player の知識を持ち込まない。

「予約後5秒以内にそのエピソードの再生を始める」隙間は残るが、これは既存の手動視聴済み化にも同型の隙間があり(視聴済み化した別エピソードを猶予中に再生開始)、許容済みのリスクと揃える。

★を外した現在のエピソードは、鳴り終わった時点で favorite=false のため既存の自動削除経路がそのまま削除する。追加コードはゼロ。

### D3: スナックバーは削除が予約されるときだけ出す

★の付与、および削除対象のない★解除は無音(アイコンの状態変化のみ)。★はトグルとして画面に残るので、誤タップは再タップで戻る。Undo が守るべき非可逆操作はファイル削除だけで、それが起きない場面の通知はノイズ。

文言は既存の `playedMessage` の流儀に合わせる: 「★を外しました。ダウンロードを削除します」+「元に戻す」。

### D4: favorite 用の Undo 表現は既存レールの小さな別型にする

`UndoablePlayedChange` は played スナップショットのリストを持つ型で、favorite の Undo(1件の favorite 復元+削除中止)とは形が違う。無理に汎用化せず、同じレール(スナップショット → 5秒 → `deleteDownloadsIfEligible`)に乗る favorite 用の小さな型を `ui/` に置く。Undo は favorite=true への復元のみで、視聴状態には触れない。

### D5: repository の口は `setFavorite(episodeId, favorite)` のみ追加

`EpisodeRepository` interface に1メソッド追加し、`RoomEpisodeRepository` から既存 DAO へ配線。削除は既存の `deleteDownloadsIfEligible` を呼ぶ。猶予中の★付け直し・未聴戻しは、実行時再判定の性質(favorite=true または played=false で `shouldDeleteDownload` が false)により自動的に削除中止となる。競合制御の追加実装は不要。

### D6: アイコンは導入済み material-icons-extended の star 系を使う

輪郭(未 favorite)/塗りつぶし(favorite)で状態を区別する。視聴済みトグル(CheckCircle の輪郭/塗りつぶし)と同じ表現規則。依存追加なし。

## Risks / Trade-offs

- [★解除した現在のエピソードを途中で止めて聴き直さないと、ファイルが残り続ける] → 次にそのエピソードが鳴り終わった時点で削除される(favorite は既に false)。狭い残渣で自己回復するため許容。利用者がすぐ消したければ★解除後に視聴済みトグルを再度オン→オフ…ではなく、単に非再生状態で★を外し直せばよい(現在のエピソードでなくなれば通常の猶予付き削除が走る)
- [予約後5秒以内に当該エピソードの再生を開始すると、鳴っているファイルが消える] → 既存の手動視聴済み化と同型の許容済みリスク。POSIX の unlink 意味論により再生自体は開いた FD で継続するため実害は限定的
- [favorite の状態が一覧から見えない] → 意図的な設計。見えるのは統合エピソード画面のみ。「どれを favorite したか一覧したい」が実需になったときに初めて検討する(Podcast Addict 化の防波堤)

## Migration Plan

`./gradlew installDebug` の上書きインストールのみ。DB マイグレーション不要(favorite カラムは初期スキーマから存在、default false)。ロールバックは前バージョンの再インストールで足りる(favorite=true が残っても旧コードの削除判定は同じ条件を見るため無害)。

## Open Questions

(なし — 探索で解消済み)
