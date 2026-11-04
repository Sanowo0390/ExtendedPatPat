# Java版マップ画像スキンのビルド

Velocity／Paper版の手は、**画像を128×128のMinecraft地図色に変換してパケット表示**します。リソースパック・URLは不要です。元のPatPat Mod対応ZIP、横並びPNG、GIFから生成できます。

`resourcepack/skins/`にカスタムZIP／PNG／GIFを置いて、`python resourcepack/build.py`を実行してください。ビルド結果は`resourcepack/dist/maps/<ID>/<FRAME>.bin`と`patpat-skins.properties`です。各JARのMavenビルドがそれを読み込みます。

公開GitHub Actionsでは`PATPAT_DEFAULT_ONLY=1`のため、デフォルトの画像のみを内包します。Bonkなど例示素材は追加していません。ActionsはVelocity、Paper、Geyserの3種類のJARのみを配布します。

Java版の各プレイヤーは`/extendedpatpat skin list`と`/extendedpatpat skin <ID>`で切り替えられます。地図は128×128、有限の色パレット、4方向の額縁表示という制限があります。カスタムOGGはリソースパックなしでは追加配信できないため、Java版での音はバニラ音源です。画像や音声の再配布には権利者の許諾・ライセンスを確認してください。

## Velocityの手の上下アニメーション

`resourcepack/build.py` は各スプライトに対して `0_0.bin` ～ `0_4.bin` のように5段階の上下位置を生成します（元の `0.bin` はPaper版用として残します）。Velocityは1つの透明額縁に表示するマップを切り替えて、手を下げて戻す動きを実現します。地図データは同じ接続先に対して一度だけ送信されます。

## Velocity: Text Display表示（リソースパック不要）

`resourcepack/build.py` は元のRGBA画像を `resourcepack/dist/text-art/<ID>/<FRAME>.png` にも変換し、Velocity JARに含めます。画像ファイル自体をクライアントへ配信せず、起動時に色付き文字コンポーネントへ変換します。`hand-renderer=text_display` を選ぶと、`billboard=center` の疑似画像と仮想エンティティの上下移動を使用します。デフォルトの額縁＋地図方式は維持されます。

## 1.21.9+のプレイヤーヘッド画像タイル方式

Velocity 0.7.0では`hand-renderer=head_tiles`を追加しました。ビルド時に含めたRGBAフレームをサーバー側で24×24等に縮小して8×8のタイルへ分割し、MineSkin APIで署名を取得します。`/extendedpatpat tiles generate default`はそれを非同期で実行し、`plugins/extendedpatpatvelocity/head-tiles/`にキャッシュします。タイルはテキストのobject/playerコンポーネントとして送信され、各行を別々のText Displayエンティティで表示するため、文字方式の行間を回避します。詳しい準備は[VelocityのREADME](../velocity-standalone/README.md)を参照してください。
