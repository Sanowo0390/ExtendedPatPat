# ExtendedPatPat Velocity

Java版はURL不要の**地図（Map）画像アニメーション**です。リソースパックやクライアントModは不要です。

## 導入

Velocity 3.5とPacketEvents 2.14.0（Velocity版）を導入し、`ExtendedPatPat-Velocity-0.6.0.jar`を`plugins/`に配置して再起動してください。統合版への表示はGeyser版も追加します。バックエンドのPaper/PurpurにPatPatの追加は不要です。

- 設定：`plugins/extendedpatpatvelocity/config.properties`
- コマンド：`/extendedpatpat`（略称`/epp`）
- 権限：`extendedpatpat.admin`（管理コマンド）

## マップ表示

デフォルト手画像はビルド時に128×128の地図色へ変換し、JARに内包されます。各プレイヤーのクライアントへ最初のPat時に必要なマップ画像を送信し、以降は透明額縁のアイテムを切り替えてアニメーションします。スキンID単位でマップIDを固定し、同じデータの再送信を抑えます。

地図は透過に対応しますが、色数と角度が制限されます。額縁はカメラ追従のBillboardではなく、視聴者に近い4方向のいずれかを向きます。ViaVersion経由の表示はバージョン別に検証してください。元のブロック製の手・Java追加リソースパック配信は廃止しています。

## よく使う設定

`enabled`、`require-sneaking`、`cooldown-ms`、`hand-offset-y`、`hand-depth`、`max-active`、`sound-*`、`required-permission`。オプションの公式PatPat Plugin連携は`official-integration-enabled`で切り替えます。

`/extendedpatpat skin list`と`/extendedpatpat skin <ID>`で自分が見るマップ画像を選択できます。標準配布にBonkなどのサンプル素材は含まれません。カスタム素材の追加：[resourcepack/README.md](../resourcepack/README.md)。

原作由来素材のライセンス：[THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。
