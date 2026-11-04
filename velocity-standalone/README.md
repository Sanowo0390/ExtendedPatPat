# ExtendedPatPat Velocity

Java版はURL不要の**地図（Map）画像アニメーション**です。リソースパックやクライアントModは不要です。

## 導入

Velocity 3.5とPacketEvents 2.14.0（Velocity版）を導入し、`ExtendedPatPat-Velocity-0.7.1.jar`を`plugins/`に配置して再起動してください。統合版への表示はGeyser版も追加します。バックエンドのPaper/PurpurにPatPatの追加は不要です。

- 設定：`plugins/extendedpatpatvelocity/config.properties`
- コマンド：`/extendedpatpat`（略称`/epp`）
- 権限：`extendedpatpat.admin`（管理コマンド）

## 描画方式の切り替え（0.6.8）

Velocity版の `plugins/extendedpatpatvelocity/config.properties` で `hand-renderer=item_frame`（従来の高画質な地図方式、**デフォルト**）または `hand-renderer=text_display`（実験的なカメラ追従表示）を選べます。変更後は `/extendedpatpat reload` を実行してください。**どちらもリソースパック不要**で、Geyser拡張やPaper版には影響しません。

`text_display` はJava版1.19.4以降のみ対応し、それ以前のクライアントには従来方式を自動使用します。RGBA画像を24×24程度の色付きブロック文字に変換して1体のText Displayで表示します。`billboard=center` によってプレイヤーの視線に正対し、240msの上下動はDisplayエンティティの位置を更新します。

`text-display-resolution=24`（8〜32）を上げると精細になりますが、1フレームあたりの色コンポーネント数と通信量が増えます。標準の額縁より描画負荷が高くなる可能性があります。バニラフォントの字形・行間・透明セルの制約によって隙間やズレが出る可能性があるため、**実機で試すための実験的レンダラー**です。

### Text Displayの見た目改善（0.6.9）

透明部分で点状に見えていたBraille空白（U+2800）を、描画されない通常の空白に変更しました。着色部分には太字＋下線を用いて、標準フォントの字間・行間による格子を軽減します。ただし、**フォント方式のため完全に継ぎ目のない画像にはできません**。文字間隔やフォントの違いで多少の段差が出る場合があります。高画質優先なら `hand-renderer=item_frame` に戻してください。

## スキン画像タイルによるカメラ追従（0.7.0・実験的）

`hand-renderer=head_tiles` を追加しました。Minecraft Java版 **1.21.9以降**のテキスト内プレイヤーヘッド画像（object/player）を、透明背景の `TEXT_DISPLAY` で表示します。**リソースパック不要**。8×8の画像タイルを各行1体のBillboardに並べるため、以前の文字方式の字間・行間の格子を避けられます。Geyser版には影響しません。

ただし、これは**実機での表示・透過をまだ保証できない実験的方式**です。プレイヤースキンの仕様により、特に半透明ピクセルが不透明化する可能性があります。実機で崩れる場合は `hand-renderer=item_frame` に戻してください。

### 初回セットアップ（MineSkin）

この方式は署名済みのプレイヤースキンが必要なので、最初の1回だけMineSkin APIで画像タイルを生成し、Velocityサーバーにキャッシュします。ゲームプレイ中は外部APIを呼びません。

1. <https://account.mineskin.org/keys> でMineSkin APIキーを発行し、Velocity側で環境変数 `MINESKIN_API_KEY` にセットするか、`plugins/extendedpatpatvelocity/config.properties` の `mineskin-api-key=` に指定します。**APIキーをGitHubや公開ログに貼り付けないでください。**
2. `head-tile-resolution=24`（16/24/32に対応、既定24）を確認し、`/extendedpatpat reload` を実行。
3. 権限 `extendedpatpat.admin` で `/extendedpatpat tiles generate default` を実行。生成はVelocityの非同期タスクで実行し、MineSkinのジョブ数・レート制限により完了まで時間がかかる場合があります。進捗はVelocityログに出ます。
4. `/extendedpatpat tiles` でキャッシュ状況を確認し、`hand-renderer=head_tiles` に変更して `/extendedpatpat reload` を実行します。

生成データは `plugins/extendedpatpatvelocity/head-tiles/` に保存され、次回起動後も再利用します。カスタムスキンも `/extendedpatpat tiles generate <skin-id>` で生成できます。透明なタイルは署名生成をスキップし、同じタイルはSHA-256で重複排除します。

古いJavaクライアント、キャッシュ未生成、または生成失敗のときは**従来のitem_frame方式に自動フォールバック**します。 `text_display` も引き続き選択できます。Minecraft 1.21.9以降を含むサーバーバージョン間のViaVersion変換・実機の透過は要検証です。

## Geyser ⇔ Java版のPat同期（0.7.1）

- **統合版→Java版:** Geyserの操作を同じバックエンドにいるJavaプレイヤーへ転送します。JavaプレイヤーにPatPat Modがある場合はネイティブのPatPat v2通知を送信し、リソースパック不要のマップ表示と重複させません。Modがない場合は従来の `hand-renderer` を使います。
- **Java版→統合版:** Javaプレイヤーの操作を検証してからGeyserへ送信します。NPCによるサーバー移動などで元の接続や対象エンティティが変わった場合は、遅延通知を破棄します。Geyser側でも撫でたJavaプレイヤーと対象が同じ受信者のワールドに存在し、近いことを確認します。
- **Java版PatPat Modからの操作:** `official-integration-enabled=false` のproxy-standalone運用では、PatPat v2クライアント要求を受け取り、Java版・統合版へ配信します。権限・距離・対象・クールダウンを再検証します。公式Paperプラグイン連携を有効にしている場合は、その承認結果を優先します。
- **個人設定:** 統合版のON/OFFはGeyser側で管理し、Velocityが読み取ります。接続直後にBedrockのUUIDが未確定でも、初回確定時に登録を更新します。

**必要な構成:** Velocity **0.7.1** とGeyser拡張 **0.13.2**。基本は `bridge-enabled=true`、`official-integration-enabled=false`、Geyserの `standalone-mode=true`。公式PatPat Pluginとの二重配信は避けてください。通信の自動テストはありますが、Minecraft実機での全組み合わせの動作は未検証です。

## マップ表示

デフォルト手画像はビルド時に128×128の地図色へ変換し、JARに内包されます。表示時にはGeyser版（`image-scale=0.5`）を見た目の基準として、Java版を少し大きめの `image-scale=0.6` に調整し、地図のピクセルを縮小します。各プレイヤーのクライアントへ初めて使うマップ画像を一度だけ送信し、透明額縁の地図IDを切り替えてアニメーションします。**上下動は額縁本体の移動ではなく、5段階の地図画像の中で手を下げて戻す方式**です。スキンID単位でマップIDを固定し、同じデータの再送信を抑えます。

地図は透過に対応しますが、色数と角度が制限されます。額縁はカメラ追従のBillboardではなく、視聴者に近い4方向のいずれかを向きます。ViaVersion経由の表示はバージョン別に検証してください。元のブロック製の手・Java追加リソースパック配信は廃止しています。

## よく使う設定

`hand-renderer`、`text-display-resolution`、`enabled`、`require-sneaking`、`cooldown-ms`、`image-scale`、`offset-space`、`offset-x/y/z`、`sitting-offset-y`、`hand-motion-depth`、`hand-motion-duration-ms`、`max-active`、`sound-*`、`required-permission`。オプションの公式PatPat Plugin連携は`official-integration-enabled`で切り替えます。

`/extendedpatpat skin list`と`/extendedpatpat skin <ID>`で自分が見るマップ画像を選択できます。標準配布にBonkなどのサンプル素材は含まれません。カスタム素材の追加：[resourcepack/README.md](../resourcepack/README.md)。

原作由来素材のライセンス：[THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。

## 個人のON/OFF

`/extendedpatpat toggle [on|off]` は送信と受信の両方を制御し、OFFのプレイヤーは他人のPat対象にもなりません。Geyser-Velocity併用時は統合版プレイヤーの設定をGeyserと共通化します。`/extendedpatpat status` に個人のON/OFFが表示されます。

### 手の上下動

`hand-motion-depth=0.28` は既定の上下移動量（約36マップピクセル）で、`0` にすると静止します。`hand-motion-duration-ms=240` で手を下げて戻します。移動は `image-scale` と別にブロック単位で計算します。Geyser版の標準移動幅（0.425ブロック）より控えめにするため、Java版では0.28ブロックにしています。地図の128×128に収まらない設定では移動幅を制限します。額縁自体は上下移動しません。

### サーバー移動NPCをPatする場合

NPCの右クリックが同時にVelocityの別サーバーへの接続を開始する場合、PatのPLAYパケットを接続中のCONFIGURATION状態へ送信するとMinecraftが切断されます。本バージョンはサーバー移動開始イベントでアニメーションを無効化し、送信直前にPacketEventsの出力側プロトコル状態がPLAYであることを確認します。一般のPat表示はNPC転送の判定を待つため約75ms遅らせます。転送用NPCではPat演出を中止し、通常のサーバー移動を優先します。

サーバー移動に失敗する場合は、Velocityのログも確認してください。プレイヤーの接続設定段階で未知のパケットIDが報告される場合、他のプロキシプラグインも原因となりえます。

### 0.6.6: サーバー接続後にPatが表示されない不具合を修正

Velocityの`ServerConnectedEvent`実行中に`Player#getCurrentServer()`が空になることがあり、表示対象`View.backend`に`null`を格納していた問題を修正しました。接続先は`event.getServer()`から取得します。また、NPCでのサーバー転送失敗時に`View.switching`が解除されず、そのサーバーでPatが使えなくなる問題も、5秒後に旧サーバーかつPLAY状態を確認して復旧するよう修正しました。

## Geyser版の表示を基準にしたJava版専用調整（0.6.7）

Velocity版は `image-scale=0.6`、`offset-space=view`、`offset-x=0.5`、`offset-y=0`、`offset-z=0`、`sitting-offset-y=0.2`、`hand-motion-depth=0.28`、`hand-motion-duration-ms=240` を既定にします。Geyser版の既定値はサイズ0.5、移動幅0.425で変更しません。既存の設定ファイルは自動追記されませんが、新しいキーがなくてもこの既定値が適用されます。以前の `hand-offset-y`、`hand-duration-ticks` は廃止されています（`hand-depth` のみ移行互換で読み取ります）。

**サイズ:** Geyserはカメラに正対する1ブロック四方の画像を `image-scale` で縮小します。Velocityでは1ブロックの額縁自体は縮小できないため、地図内の画像ピクセルを縮小します。画像は設定リロード時にメモリ上で再生成されます。

**位置:** Geyserの `offset-space=view` に合わせ、Xを視聴者方向、Zを右方向に扱います。ただしJava版額縁はブロック座標に固定されるため、0.5ブロックなどの端数は厳密には一致しません。Geyserの実際のエンティティ当たり判定の代わりに、Velocityでは種類別の標準的な高さを使用します。特にファントムは0.5ブロックとして計算します。乗っているモブは `set_passengers` を追跡して乗り物の位置から近似し、移動するボート上でも古いスポーン位置を使わないようにします。乗車席の厳密な回転・個体別サイズまではプロキシの情報だけでは再現できません。

**動き:** Geyser `HandMotion` と同じイージング曲線を使いますが、Java版のみ振れ幅を減らしています。Velocityは既定値で約36px動かします。画像が大きすぎる場合、128pxの枠に入る範囲に制限されます。既存ファイルの image-scale と hand-motion-depth は手動で変更してください。
