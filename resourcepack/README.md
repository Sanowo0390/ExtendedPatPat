# Java版マップ画像スキンのビルド

Velocity／Paper版の手は、**画像を128×128のMinecraft地図色に変換してパケット表示**します。リソースパック・URLは不要です。元のPatPat Mod対応ZIP、横並びPNG、GIFから生成できます。

`resourcepack/skins/`にカスタムZIP／PNG／GIFを置いて、`python resourcepack/build.py`を実行してください。ビルド結果は`resourcepack/dist/maps/<ID>/<FRAME>.bin`と`patpat-skins.properties`です。各JARのMavenビルドがそれを読み込みます。

公開GitHub Actionsでは`PATPAT_DEFAULT_ONLY=1`のため、デフォルトの画像のみを内包します。Bonkなど例示素材は追加していません。ActionsはVelocity、Paper、Geyserの3種類のJARのみを配布します。

Java版の各プレイヤーは`/extendedpatpat skin list`と`/extendedpatpat skin <ID>`で切り替えられます。地図は128×128、有限の色パレット、4方向の額縁表示という制限があります。カスタムOGGはリソースパックなしでは追加配信できないため、Java版での音はバニラ音源です。画像や音声の再配布には権利者の許諾・ライセンスを確認してください。
