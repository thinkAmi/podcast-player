# feed-subscription Delta

## MODIFIED Requirements

### Requirement: RSSパースの耐障害性
RSS 2.0 + iTunes名前空間をXmlPullParserでパースし、必須要素(title / enclosure url / guid)を持たない壊れたitemはスキップして残りの取り込みを続行するものとする(SHALL)。

item 内の個別属性が不正な場合は、item 全体を捨てるのではなく当該属性のみを「不明」として扱うものとする(SHALL)。特に `itunes:duration` は、時分秒のいずれかの成分が負であるもの、数値として解釈できないもの、ミリ秒換算で Long の範囲を超えるものを「長さ不明」(null)として扱い、視聴済みの自動判定を行わない。長さの解釈は任意の入力文字列に対して例外を送出せず、null または正のミリ秒値のみを返すものとする(SHALL)。

#### Scenario: 一部のitemが壊れているフィード
- **WHEN** enclosureを持たないitemを含むフィードを更新する
- **THEN** 壊れたitemは無視され、正常なitemはすべて取り込まれる

#### Scenario: 負の成分を含む長さ表記
- **WHEN** `itunes:duration` が `"10:-5"` のように負の成分を含むitemを取り込む
- **THEN** そのエピソードは長さ不明(null)として取り込まれ、視聴済みの自動判定は行われない

#### Scenario: ミリ秒換算がオーバーフローする長さ表記
- **WHEN** `itunes:duration` の合計秒数がミリ秒換算で Long の最大値を超えるitemを取り込む
- **THEN** そのエピソードは長さ不明(null)として取り込まれ、負値などの不正な長さが記録されることはない

#### Scenario: 任意の不正文字列に対する頑健性
- **WHEN** `itunes:duration` に数値・時分秒のいずれとも解釈できない任意の文字列が指定されている
- **THEN** パースは例外を送出せず、そのエピソードは長さ不明(null)として取り込まれる
