# episode-filtering Specification

## Purpose
TBD - created by archiving change podcast-player-mvp. Update Purpose after archive.
## Requirements
### Requirement: AND条件フィルター
エピソード一覧画面の上部に「未聴のみ」「DL済みのみ」の2つのフィルタートグル(チップ)を表示し、両方ONの場合はAND条件(未聴 かつ DL済み)で絞り込むものとする(SHALL)。この2条件以外のフィルターは設けない。

#### Scenario: 未聴のみ
- **WHEN** 「未聴のみ」だけをONにする
- **THEN** 視聴済みエピソードが一覧から除外される

#### Scenario: AND条件(いま聴けるもの)
- **WHEN** 「未聴のみ」と「DL済みのみ」を両方ONにする
- **THEN** 未聴かつDL済みのエピソードだけが表示される

### Requirement: デフォルトは全件表示
新規購読した番組のフィルター初期状態は両トグルOFF(全件表示)とする(SHALL)。

#### Scenario: 購読直後の一覧
- **WHEN** 番組を購読して初めてエピソード一覧を開く
- **THEN** フィルターは適用されておらず、全エピソードが表示される

### Requirement: 番組ごとの永続化
フィルター設定は番組ごとに保存し、アプリ再起動後も維持するものとする(SHALL)。

#### Scenario: 再起動後の維持
- **WHEN** 番組Aのフィルターを「未聴×DL済み」に設定し、アプリを再起動して番組Aを開く
- **THEN** フィルターは「未聴×DL済み」のまま適用されている

#### Scenario: 番組間の独立性
- **WHEN** 番組Aのフィルターを変更する
- **THEN** 番組Bのフィルター設定は影響を受けない

### Requirement: 状態変化への即時追随
フィルター適用中の一覧は、エピソードの状態変化(視聴済み化・DL完了・削除)に即時追随するものとする(SHALL)。

#### Scenario: 視聴済み化による一覧からの消滅
- **WHEN** 「未聴のみ」フィルター適用中にエピソードを視聴済みにする
- **THEN** そのエピソードは一覧から即座に消える(Undoスナックバーの猶予中も表示は視聴済み扱いとする)

