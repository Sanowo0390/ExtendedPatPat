# PatPat — Velocity / Geyser / Paper 🐾

このリポジトリでは、**Java版・統合版をサポートするVelocity構成**と、**Paper/Purpur単体向け構成**を、それぞれ別のJARとして開発・配布しています。開発・ビルドの基準ブランチは **`main`** です。

## 配布する3つのJAR

| コンポーネント | 最新版 | 導入先 | 役割 | GitHub Actions |
|---|---|---|---|---|
| **PatPatVelocity** | 0.2.0 | `Velocity/plugins/` | Java版の操作検出、仮想手、Java↔統合版中継 | [Velocity版をビルド](https://github.com/Sanowo0390/PatPatGeyserExtension/actions/workflows/build-velocity.yml) |
| **PatPatGeyserExtension** | 0.12.0 | `Velocity/plugins/Geyser-Velocity/extensions/` | 統合版の手画像・効果音・モーション | [Geyser版をビルド](https://github.com/Sanowo0390/PatPatGeyserExtension/actions/workflows/build.yml) |
| **PatPatVanilla** | 0.2.0 | `Paper/plugins/` | Paper/Purpur単体向けJava版の手アニメーション | [Paper版をビルド](https://github.com/Sanowo0390/PatPatGeyserExtension/actions/workflows/build-paper.yml) |

### どちらの構成を使う？

**VelocityでJava版＋統合版を連携**させたい場合は、PatPatVelocity＋PacketEvents＋Geyser-Velocity＋PatPatGeyserExtensionを導入してください。バックエンドのPaper/Purpur側にはPatPatプラグインを入れる必要はありません。設定は `official-integration-enabled=false` と `standalone-mode=true` が基本です。詳細は [Velocity版README](velocity-standalone/README.md)。

**Paper/Purpur単体**でJava版クライアントにPat演出を追加したい場合は、PatPatVanillaだけを導入してください。詳細は [Paper版README](paper-standalone/README.md)。Paper版は独立実装で、**Velocity版のJava↔統合版ブリッジ機能は含まれていません**。

**同一サーバーにPaper版とVelocity版を同時導入しないでください。** 演出の重複を招きます。Java版は共通のリソースパックによって5コマの画像手を表示します。リソースパックを使用しない場合はブロック製の手へ切り替え可能です。

ビルドはリポジトリの `main` で実行し、ActionsのArtifactsから対応するJARを取得してください。Geyser版はGeyser 2.12.1系のb1280および最新ビルドに対して検証されます。なお、GitHub Actionsの成功はゲーム内動作の保証ではありません。

---

## Java版リソースパック（26.3）

- <https://github.com/Sanowo0390/PatPatGeyserExtension/actions/workflows/build-java-pack.yml> の成功したActionsから `PatPatJava-26.3.zip` を取得できます（ArtifactsのZIPを展開すると、適用用ZIPが入っています）。
- Java版プレイヤーがパックを導入するか、ZIPをHTTPSで配布してVelocityの `resource-pack-url` やPaperの `resource-pack` に指定します。
- **手のアニメーション**：送っていただいた5コマのPNGを利用して、5個のモデルを1tickずつ切り替えます。
- **凹み**：Velocity版は周囲のJavaプレイヤーに送る表示用パケットで一瞬だけ全体のスケールを小さくします。実際の当たり判定やサーバー側のスケールは変えません。縦方向だけ潰すMod版と完全には同じではありません。Paper単体版の凹み処理は未実装です。
- **リソースパックなし**：Voxel表示へ切替できます（Velocity: `hand-renderer=voxel`、Paper: `animation.renderer=voxel`）。

生成方法・配置方法：[resourcepack/README.md](resourcepack/README.md)。

## Geyser拡張の詳細

Geyserを利用する**Minecraft統合版（Bedrock Edition）プレイヤー**向けのPatPat拡張機能です。しゃがみながら空のメインハンドでモブやプレイヤーを操作すると、対象の頭上に手のPNGスプライトシート／GIFアニメーションを表示し、**手が下がって戻るモーション**と効果音を再生します。さらに統合版側では対象のモデルが一瞬むにゅっと縮む「Squish」演出にも対応します。

サーバー側のPatPatプラグインがなくても動作します。Velocity版を併用するとJava版との演出共有に対応します。公式PatPat Pluginとの通信は任意で有効化できます。

## 必要環境

- **Geyser-Velocity 2.12.1系**（b1280～現在のb1284でコンパイル・単体テスト成功）
- Java 21以上
- PatPat Plugin 1.2.5以上：**任意**（Java版Modとの演出連携が必要な場合）
- Paper / Purpur側に専用のPatPatGeyserAddonを入れる必要はありません。

> [!IMPORTANT]
> Geyserの内部パケットAPIを使用するため、更新時の完全互換は保証できません。起動時に必要なAPIを検査し、互換性がない場合は安全に拡張機能を停止します。Bedrock用表示パックの適用が必要です。

## 導入

1. [GitHub Actions](https://github.com/Sanowo0390/PatPatGeyserExtension/actions/workflows/build.yml) の成功したビルドから `PatPatGeyserExtension-0.12.0.jar` を取得します。
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
image-file=images/hand.png
image-sprite-columns=5
image-sprite-frame-ms=50
image-scale=0.5
image-loop=true
offset-x=0.5
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

## デフォルトの手画像（v0.11.0）

送っていただいた **560×112px の5コマ横並び透過PNG** をデフォルト画像に変更しました。GIFへ変換せず、そのまま `images/hand.png` として配置し、112×112pxずつ左から右へ切り出してアニメーションします。

```properties
image-file=images/hand.png
image-sprite-columns=5
image-sprite-frame-ms=50
```

5コマ×50ms＝約250msで1サイクル（従来の手の上下運動240msとほぼ同じ）です。画像自体はPNGですが静止画像ではなく、スプライトシートのコマ送りで手の開閉を表現します。

- PNGが `幅 = 高さ × image-sprite-columns` に一致する場合だけ分割します。普通の正方形PNGは従来どおり静止画です。
- **GIFは従来どおり直接読み込めます。** カスタムGIFを使う場合、スプライト設定は無視されます。
- 以前のデフォルト `images/hand.gif` が**未変更の同梱ファイル**だった場合のみ、自動で新しいPNGを使用します。既に差し替えたカスタムGIFは維持します。
- デフォルト画像ファイルは既存の `images/hand.png` を上書きしません。
- 画像やコマ数・コマ速度を変更した場合はVelocityを再起動し、統合版で再接続してください（リソースパックが再生成されます）。

## 手の表示位置と腕振り

手の座標は `offset-x` / `offset-y` / `offset-z` の設定に従います。v0.9.0で導入した**モブの当たり判定に合わせて手を手前に動かす自動補正は撤回しました**。旧設定の `hand-avoid-occlusion` / `hand-front-clearance` がファイルに残っていても無視されます。

- `swing-hand-enabled=true`：Pat成功時に左クリックと同じ腕振りアニメーションを再生（攻撃・ダメージなし）
- `camera-shake-enabled=false`：**他の人から撫でられた統合版プレイヤー本人だけ**に軽い上下視点モーションを適用（既定OFF）。撫でた側・周囲の観客・モブには適用しません

## 撫でられた時の上下カメラモーション

Bedrock標準のランダムなスクリーンシェイクは使用しません。代わりに、撫でられたプレイヤー本人の**カメラ位置だけ**を約240msかけて軽く下げて戻す動きにしました（X/Z方向の揺れやプレイヤーの座標変更はありません）。

```properties
# 自分が撫でられたときだけ
camera-shake-enabled=true
# 最大上下幅（ブロック）
camera-shake-intensity=0.06
```

- **他の人から撫でられたときだけ**再生。自分からPatした場合は再生しません。
- バックエンドPatPat連携時と `standalone-mode=true` の両方に対応。自分がJava版でログインしている場合はこのGeyser拡張のカメラ演出対象外です。
- 標準の `CameraShakePacket` では軸指定ができないため、一時的にBedrockのフリーカメラを使い、最後に必ず通常の視点へ戻します。**他のカメラ制御がGeyser上で有効な場合や観戦中は適用しません。**
- 一時的なカメラ切り替えのため、シネマティック系の他プラグインと競合する場合があります。動作に問題がある場合は `camera-shake-enabled=false` にしてください。
- カメラ演出は既定でOFFです。変更後、`/patpatbedrock reload` で反映できます。

## Configのレイアウト

`config.properties` を**日本語の説明付き・9カテゴリー構成**へ変更しました。全36項目を用途別に並べ、単位と再起動が必要な設定を明示しています。

v0.9.1以前の設定ファイルは、初回起動時に**設定値と独自キーを保持**したまま新しい配置に整形します。変更前のファイルは `config.properties.backup-before-layout-v2` に原文のまま保存します（旧ファイル中の独自コメントは新レイアウトに移動しないので、必要ならバックアップを参照してください）。その後は自動で書き換えません。

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

**画像/GIF表示では、手の上下動をリソースパック内のBedrockアニメーションで再生**します。従来の20msごとのサーバー位置更新は手の上下動に使いません。そのため、手の動きはエンティティが表示された時点から開始され、Squishと同じ約240msのカーブで動きます。文字表示モードでは従来のサーバー側移動処理を使用します。

`hand-motion-enabled`、`hand-motion-depth`、`hand-motion-duration-ms`、`image-scale` を変更した場合は、**リソースパックの再生成が必要なのでVelocityを再起動し、統合版で再接続してください**（`reload`だけでは適用できません）。手のモーション時間を240ms以外に変更した場合、Squishは240msのままなので完全には同期しません。

## Squish（潰れる演出）

`squish-enabled=true`（既定）でPat時に約0.24秒間、対象のモデルを縦に縮めてから元に戻すBedrockアニメーションを再生します。GeyserからBedrockへ直接アニメーションパケットを送るため、バックエンドのPatPatプラグインは不要です。

- **統合版プレイヤーの画面だけ**に適用します。Java版には影響しません。
- 標準的なモブの主要なボーンに合わせた演出を使用します。独自モデルや一部のモブは、ボーン名が一致せず変化しない場合があります。
- 既存のPat用リソースパックにアニメーション定義を含めています。**v0.9.0への更新後はProxy再起動と統合版の再接続が必要**です。
- `squish-enabled=false` で無効化できます。このON/OFF設定は `/patpatbedrock reload` で反映できます。
- サーバー側のエンティティのサイズ・当たり判定は変更しません。あくまでクライアント側の見た目の演出です。
- 実機の描画結果は端末・Minecraftバージョン・他のリソースパックによって変わる可能性があります。

## 画像・GIF・効果音の変更

- `extensions/patpatbedrock/images/` にPNGまたはGIFを配置し、`image-file=images/ファイル名.png` などを設定します（GIFは最大128フレーム、画像サイズ最大16MiB）。
- `extensions/patpatbedrock/sounds/` 内の小文字英数字・`_`・`-` の名前のOGGファイルを効果音候補として使います。既定で `pat.ogg`、`pat1.ogg`、`pat2.ogg` をランダム再生します。
- 固定音にしたい場合は例として `sound-event=patpatbedrock.sound.pat1` と指定できます。`sound-enabled=false` で音を無効化できます。
- **画像・GIF・音声そのものを変更した場合はVelocity再起動と統合版プレイヤーの再接続が必要**です。位置・サイズや音量などの設定変更だけなら `reload` で反映できます。

## 動作モードと制限

- **バックエンドPatPatあり**：本体とのV2通信を利用。PatPat本体の権限・制限に従い、Java版Modからの演出も統合版に届きます。
- **バックエンドPatPatなし**：Geyserが統合版への演出を直接表示します。PatPatVelocityを併用すると、同じVelocityインスタンスのJava版へも演出を中継できます。PatPatVelocityを導入していない場合は、Java版への中継はありません。
- `/patpatbedrock test` は照準先の対象が見つからないと表示しません。自分の頭上に自動表示する仕様ではありません。
- 本機能はGeyserのパケット・エンティティ情報に依存します。端末ごとの操作や他プラグインとの組合せは実際のサーバーで確認してください。

## ライセンス

[MIT License](LICENSE)


## Geyserのビルド更新への対応（v0.12.0）

Geyserのビルド番号やコミットSHAの固定判定を撤廃しました。起動時には利用中のGeyserに必要なクラス・メソッドが存在するかを確認します。互換性があれば新しいビルドでも有効化します。互換性がなければ不足したAPI名をログに表示して停止します。カメラの内部APIのみ非対応の場合は、カメラ演出を無効化し、他のPat演出を維持します。

GitHub Actionsは **Geyser 2.12.1-b1280と2.12.1系の最新ビルド** を別々にコンパイル・単体テストします。公式ダウンロードAPIが返すSHA-256でJARを検証しており、GitHubのデフォルトブランチ上では週1回の互換性チェックが実行されます。

ローカルでビルドを固定する場合は `GEYSER_BUILD=1284 python scripts/prepare_build.py`、最新を使う場合は `python scripts/prepare_build.py` とします。

**注意:** 単体テストの成功は、実際の統合版クライアントで描画・通信できることを保証しません。Geyserの内部APIで戻り値型やパケットの意味が変更された場合、追加対応が必要になる可能性があります。
