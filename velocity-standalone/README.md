# ExtendedPatPat Velocity

Java版はURL不要の**地図（Map）画像アニメーション**です。リソースパックやクライアントModは不要です。

## 導入

Velocity 3.5とPacketEvents 2.14.0（Velocity版）を導入し、`ExtendedPatPat-Velocity-0.6.5.jar`を`plugins/`に配置して再起動してください。統合版への表示はGeyser版も追加します。バックエンドのPaper/PurpurにPatPatの追加は不要です。

- 設定：`plugins/extendedpatpatvelocity/config.properties`
- コマンド：`/extendedpatpat`（略称`/epp`）
- 権限：`extendedpatpat.admin`（管理コマンド）

## マップ表示

デフォルト手画像はビルド時に128×128の地図色へ変換し、JARに内包されます。各プレイヤーのクライアントへ初めて使うマップ画像を一度だけ送信し、透明額縁の地図IDを切り替えてアニメーションします。**上下動は額縁本体の移動ではなく、5段階の地図画像の中で手を下げて戻す方式**です。スキンID単位でマップIDを固定し、同じデータの再送信を抑えます。

地図は透過に対応しますが、色数と角度が制限されます。額縁はカメラ追従のBillboardではなく、視聴者に近い4方向のいずれかを向きます。ViaVersion経由の表示はバージョン別に検証してください。元のブロック製の手・Java追加リソースパック配信は廃止しています。

## よく使う設定

`enabled`、`require-sneaking`、`cooldown-ms`、`hand-offset-y`、`hand-depth`、`hand-duration-ticks`、`max-active`、`sound-*`、`required-permission`。オプションの公式PatPat Plugin連携は`official-integration-enabled`で切り替えます。

`/extendedpatpat skin list`と`/extendedpatpat skin <ID>`で自分が見るマップ画像を選択できます。標準配布にBonkなどのサンプル素材は含まれません。カスタム素材の追加：[resourcepack/README.md](../resourcepack/README.md)。

原作由来素材のライセンス：[THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。

## 個人のON/OFF

`/extendedpatpat toggle [on|off]` は送信と受信の両方を制御し、OFFのプレイヤーは他人のPat対象にもなりません。Geyser-Velocity併用時は統合版プレイヤーの設定をGeyserと共通化します。`/extendedpatpat status` に個人のON/OFFが表示されます。

### 手の上下動

`hand-depth=0.425` が標準の上下動（約28マップピクセル）で、`0` にすると静止します。`hand-duration-ticks=6` で約0.3秒の「下げる→戻す」を実行します。地図の128×128サイズ内に収めるため、元画像は必要な場合のみ縮小されます。額縁自体はブロック座標に固定されるため、座標移動による上下動は行いません。

### サーバー移動NPCをPatする場合

NPCの右クリックが同時にVelocityの別サーバーへの接続を開始する場合、PatのPLAYパケットを接続中のCONFIGURATION状態へ送信するとMinecraftが切断されます。本バージョンはサーバー移動開始イベントでアニメーションを無効化し、送信直前にPacketEventsの出力側プロトコル状態がPLAYであることを確認します。一般のPat表示はNPC転送の判定を待つため約75ms遅らせます。転送用NPCではPat演出を中止し、通常のサーバー移動を優先します。

サーバー移動に失敗する場合は、Velocityのログも確認してください。プレイヤーの接続設定段階で未知のパケットIDが報告される場合、他のプロキシプラグインも原因となりえます。

### 0.6.5: サーバー接続後にPatが表示されない不具合を修正

Velocityの`ServerConnectedEvent`実行中に`Player#getCurrentServer()`が空になることがあり、表示対象`View.backend`に`null`を格納していた問題を修正しました。接続先は`event.getServer()`から取得します。また、NPCでのサーバー転送失敗時に`View.switching`が解除されず、そのサーバーでPatが使えなくなる問題も、5秒後に旧サーバーかつPLAY状態を確認して復旧するよう修正しました。
