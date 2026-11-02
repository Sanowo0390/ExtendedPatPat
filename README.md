# PatPatGeyserExtension 🐾

Geyserを利用する**Minecraft統合版（Bedrock Edition）プレイヤー**向けのPatPat拡張機能です。しゃがみながら空のメインハンドでモブやプレイヤーを操作すると、対象の頭上に手のGIFアニメーションを表示し、**手が下がって戻るモーション**と効果音を再生します。さらに統合版側では対象のモデルが一瞬むにゅっと縮む「Squish」演出にも対応します。

サーバー側のPatPatプラグインがなくても動作します。PatPat本体がある場合は本体との通信を優先し、Java版クライアントModとの演出共有にも対応します。

## 必要環境

- **Geyser-Velocity 2.12.1 build 1280**（commit `64bb3993b4beef6743af0ab76ee5dfc8e67a0d21`）専用
- Java 21以上
- PatPat Plugin 1.2.5以上：**任意**（Java版Modとの演出連携が必要な場合）
- Paper / Purpur側に専用のPatPatGeyserAddonを入れる必要はありません。

> [!IMPORTANT]
> Geyserの内部パケットAPIに依存するため、**指定外のGeyserビルドでは動作を停止**します。Bedrock用の表示パックをクライアントが読み込む必要があります。

## 導入

1. [GitHub Actions](https://github.com/Sanowo0390/PatPatGeyserExtension/actions/workflows/build.yml) の成功したビルドから `PatPatGeyserExtension-0.9.0.jar` を取得します。
2. JARをVelocityの `plugins/Geyser-Velocity/extensions/` に配置し、古い同拡張のJARを取り除きます。
3. Velocityを再起動すると `extensions/patpatbedrock/` に設定・画像・音声ファイルとリソースパックが生成されます。
4. 統合版プレイヤーで再接続し、配布されるリソースパックを適用します。必要に応じてGeyser側の `force-resource-packs: true` を使用してください。
5. `/patpatbedrock status` で動作状況を確認し、対象に照準を合わせて `/patpatbedrock test` を実行します。

**基本操作：** 空のメインハンドでしゃがみながら対象を右クリック（統合版の「使う」操作）します。既定の最大距離は3ブロックです。

## コマンドと権限

| コマンド | 説明 | 権限 |
|---|---|---|
| `/patpatbedrock status` | 接続状態、PatPat本体との通信、個人のON/OFFを確認 | 追加権限なし |
| `/patpatbedrock test` | **現在照準を合わせた対象**に自分だけ表示テスト | `required-permission` 設定時はその権限 |
| `/patpatbedrock test text` | 画像ではなくテキストを使った表示テスト | 同上 |
| `/patpatbedrock pat` | 照準先を撫でる（しゃがみは不要） | 同上 |
| `/patpatbedrock toggle` | 自分のPat送受信をON/OFF切り替え | 追加権限なし |
| `/patpatbedrock toggle on` / `off` | 個人設定を明示的に切り替え | 追加権限なし |
| `/patpatbedrock stats` | 自分のPat回数（拡張で受け付けた操作）を表示 | 追加権限なし |
| `/patpatbedrock reload` | 設定を再読み込み | `patpatbedrock.admin` |

通常は権限プラグイン不要です。`required-permission=patpatbedrock.use` などを設定すると `pat` と `test` だけをその権限で制限できます。付与する場合は**Velocity側**の権限プラグインを使用してください。`toggle` は演出の拒否にも使うため、利用権限設定に関係なく実行できます。

`toggle off` は**自分のPat操作と自分が見るPat演出だけ**を無効にします。他プレイヤーの設定は変更しません。ON/OFFの設定は `extensions/patpatbedrock/disabled-players.txt` にUUID単位で保存され、再接続・再起動後も保持されます。既定では全員ONです。

## 既定の設定

編集先：`plugins/Geyser-Velocity/extensions/patpatbedrock/config.properties`

```properties
enabled=true
required-permission=
auto-pack=true
require-sneaking=true
standalone-mode=true
require-empty-hand=true
allow-players=true
ignored-entity-types=
ignored-player-uuids=
swing-hand-enabled=true
camera-shake-enabled=false
camera-shake-intensity=0.06
max-distance=3.0
cooldown-ms=100
duration-ms=0
frame-ms=100
renderer=image
image-file=images/hand.gif
image-scale=0.5
image-loop=true
offset-x=0.5
hand-avoid-occlusion=true
hand-front-clearance=0.15
offset-space=view
offset-y=0.0
offset-z=0.0
sitting-offset-y=0.2
max-active-per-viewer=32
local-preview=true
frames=\uE2A0,\uE2A1,\uE2A2,\uE2A3
squish-enabled=true
hand-motion-enabled=true
hand-motion-depth=0.425
hand-motion-duration-ms=240
sound-enabled=true
sound-event=patpatbedrock.pat
sound-volume=1.0
sound-pitch=1.0
```

**主要な設定**：`image-scale` は画像のサイズ、`offset-x` は見る人の方向への距離、`offset-y` は上下、`offset-z` は見る人から見て左右の位置調整です。`sitting-offset-y` は座っているプレイヤーに対する追加の高さ補正です。`cooldown-ms` は連打の最小間隔です。`standalone-mode=true` ならバックエンド本体がなくても統合版でPatできます。

`/patpatbedrock reload` で位置・サイズ・表示時間などを再読み込みできます。**既存の設定ファイルは更新時に上書きしません。** 旧設定に `cooldown-ms=600` などが残っている場合は、必要に応じて手動で変更してください。

## 手の重なり対策と腕振り

`hand-avoid-occlusion=true` のとき、手の画像がモブの当たり判定と重なりにくいよう、**モブの横幅、斜めからの視線、画像サイズ**を考慮して手を視聴者側へ移動します。

- `hand-front-clearance=0.15`：当たり判定面と画像の間に追加する余裕（ブロック単位）
- `hand-avoid-occlusion=false`：自動補正をOFFにして従来どおりの手動オフセットを使用
- `offset-space=world`：ワールド基準の位置指定では自動補正しません
- `swing-hand-enabled=true`：Pat成功時に**左クリックと同じ腕振りアニメーション**を本人と同じGeyser上の統合版プレイヤーに再生。攻撃・ダメージは発生しません
- `camera-shake-enabled=false`：元Modにあるカメラの軽い揺れを任意で有効にできます。`camera-shake-intensity=0.06` は揺れの強さです（0〜0.3）。酔いやすさを考慮して既定ではOFFです

なお、当たり判定と実際の3Dモデル形状が違うモブ、視点が非常に近い場合、異なるリソースパックを使う場合は完全に重なりを防げないことがあります。

## 除外リストとPat回数

`ignored-entity-types` にカンマ区切りで対象のBedrockエンティティ識別子を設定すると、該当モブへのPatを禁止できます。`minecraft:` は省略可能です。

```properties
ignored-entity-types=minecraft:creeper,minecraft:warden
ignored-player-uuids=
```

`ignored-player-uuids` は除外したいプレイヤーのUUIDをカンマ区切りで指定します。設定変更後は `/patpatbedrock reload` を実行してください。

`/patpatbedrock stats` で、自分がこの拡張で受け付けられたPatの回数を確認できます。カウンターは `pat-stats.properties` に保存されます。元のFabric Modが独自に記録した回数とは同期せず、バックエンドで拒否された未確認の要求を含む場合があります。

## 手の上下アニメーション（PatPat Java版に準拠）

PatPat Java版（1.3.2）の描画処理に合わせ、手の画像は**約240msかけて一度下がり、滑らかに元の位置へ戻ります**。Java版と同様、単純な等速移動ではなく、`1-(1-t)^2` で加速した進捗に `sin(π・進捗)` を適用した動きです。

| 設定 | 既定値 | 内容 |
|---|---:|---|
| `hand-motion-enabled` | `true` | 手の上下移動をON/OFF |
| `hand-motion-depth` | `0.425` | 手を下げる最大量（ブロック単位） |
| `hand-motion-duration-ms` | `240` | 1回の上下移動にかける時間（ミリ秒） |

通常の位置設定 `offset-x` / `offset-y` / `offset-z` は**基準位置**として維持され、上下移動はY方向にだけ加算されます。GIFやPNGのサイズ、各フレームの表示時間には影響しません。文字だけの `test text` は移動させません。

この機能はGeyserから送る表示用エンティティの位置パケットを利用し、表示中の約240msは20ms間隔で位置更新します。Squishも**標準設定では同じ240msと同じイージングカーブ**で再生します。 カスタムの `hand-motion-duration-ms` を使う場合、Squish側は標準の240msのままなので完全には同期しません。**サーバープラグイン不要**で、設定の変更は `/patpatbedrock reload` で反映できます。画像素材・リソースパックを再生成する必要はありません。

## Squish（潰れる演出）

`squish-enabled=true`（既定）でPat時に約0.24秒間、対象のモデルを縦に縮めてから元に戻すBedrockアニメーションを再生します。GeyserからBedrockへ直接アニメーションパケットを送るため、バックエンドのPatPatプラグインは不要です。

- **統合版プレイヤーの画面だけ**に適用します。Java版には影響しません。
- 標準的なモブの主要なボーンに合わせた演出を使用します。独自モデルや一部のモブは、ボーン名が一致せず変化しない場合があります。
- 既存のPat用リソースパックにアニメーション定義を含めています。**v0.9.0への更新後はProxy再起動と統合版の再接続が必要**です。
- `squish-enabled=false` で無効化できます。このON/OFF設定は `/patpatbedrock reload` で反映できます。
- サーバー側のエンティティのサイズ・当たり判定は変更しません。あくまでクライアント側の見た目の演出です。
- 実機の描画結果は端末・Minecraftバージョン・他のリソースパックによって変わる可能性があります。

## 画像・GIF・効果音の変更

- `extensions/patpatbedrock/images/` にPNGまたはGIFを配置し、`image-file=images/ファイル名.gif` を設定します（GIFは最大128フレーム、画像サイズ最大16MiB）。
- `extensions/patpatbedrock/sounds/` 内の小文字英数字・`_`・`-` の名前のOGGファイルを効果音候補として使います。既定で `pat.ogg`、`pat1.ogg`、`pat2.ogg` をランダム再生します。
- 固定音にしたい場合は例として `sound-event=patpatbedrock.sound.pat1` と指定できます。`sound-enabled=false` で音を無効化できます。
- **画像・GIF・音声そのものを変更した場合はVelocity再起動と統合版プレイヤーの再接続が必要**です。位置・サイズや音量などの設定変更だけなら `reload` で反映できます。

## 動作モードと制限

- **バックエンドPatPatあり**：本体とのV2通信を利用。PatPat本体の権限・制限に従い、Java版Modからの演出も統合版に届きます。
- **バックエンドPatPatなし**：Geyserが演出を直接表示。同じGeyserインスタンスで同じ接続先・ワールドの対象を認識している統合版プレイヤーに共有します。Java版や別Geyserインスタンスには送信しません。
- `/patpatbedrock test` は照準先の対象が見つからないと表示しません。自分の頭上に自動表示する仕様ではありません。
- 本機能はGeyserのパケット・エンティティ情報に依存します。端末ごとの操作や他プラグインとの組合せは実際のサーバーで確認してください。

## ライセンス

[MIT License](LICENSE)
