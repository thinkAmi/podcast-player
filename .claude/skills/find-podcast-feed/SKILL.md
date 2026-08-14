---
name: find-podcast-feed
description: Spotify などの配信ページ URL や番組名から、購読に使う大元の RSS フィード URL を突き止めて検証する。ユーザーが「この番組の RSS を探して」「Spotify のこの番組を購読したい」と URL や番組名を渡してきたときに使用する。見つけた後の登録は add-podcast-feed に引き継ぐ。
---

配信ページの URL(Spotify / Apple Podcasts など)や番組名から、公開 RSS フィードを
突き止める。Spotify のページには RSS は載っていないため、これは常に間接的な調査になる。

## 手順

1. **番組名の確定** — 渡されたのが URL なら、ページの `og:title` / `og:description` から
   正確な番組名と配信者名を取る。Apple Podcasts の URL なら id を抜き出して手順 2 の
   lookup に直行できる(`https://itunes.apple.com/lookup?id=<id>`)。
2. **ディレクトリ検索** — iTunes Search API で feedUrl を得る:
   `https://itunes.apple.com/search?term=<番組名>&media=podcast&country=JP&limit=20`
   見つからなければ `country=US` でも試す。それでも無ければ手順 4 の「見つからない場合」へ。
3. **同一性の検証(省略禁止)** — 候補の RSS を実際に取得し、証拠を最低 2 つ揃える:
    - 直近エピソードのタイトル・話数が配信ページ側の表示と一致する
    - 配信者名・番組説明・アートワークが一致する
      番組名の一致だけで同一と判断してはならない。同名・類似名の番組は普通に存在し、
      ミラー配信(LISTEN 等)では番組名すら書き換えられていることがある。
4. **報告** — 突き止めた feedUrl と検証の証拠を提示する。登録はこのスキルでは行わず、
   ユーザーが望めば add-podcast-feed に引き継ぐ。
    - 既存購読に同じ番組のミラー(別フィード)がある場合はその旨を伝える。古い方の
      購読削除は破壊的操作なので、アプリ内でユーザー自身が行う
    - **見つからない場合**: Spotify 独占配信には公開 RSS が存在しない。その場合は
      「原理的に購読不可」と正直に伝える。回避策(スクレイピング等)を探さない

## してはいけないこと

- 検証(手順 3)を飛ばして feedUrl を報告・登録すること。取り違えると別番組を
  購読し、気づくのはだいぶ後になる
- このスキル内で登録まで進めること。登録の契約は add-podcast-feed / scripts/add-feed.sh が持つ
- http:// のフィードを候補として提示すること(アプリは https のみ受け付ける)