# ExtendedPatPat 🐾

**Minecraft Java版・統合版で、プレイヤーやモブを撫でる非公式プラグイン。** Java版はリソースパック不要の地図アニメーション、統合版はGeyserのPNG/GIFを使います。

> [!IMPORTANT]
> [LopyMine氏のPatPat Mod](https://github.com/LopyMine/PatPat)／[PatPat Plugin](https://github.com/LopyMine/PatPat-Plugin) に着想を得た独立プロジェクトです。原作者との提携・承認関係はありません。Minecraftの公式製品でもありません。

## ダウンロード・導入

[**GitHub Actions**](https://github.com/Sanowo0390/ExtendedPatPat/actions/workflows/build-all.yml) から3つのJARをダウンロードできます。

| JAR | 動作環境 |
|---|---|
| **ExtendedPatPat-Velocity** (0.6.7) | Velocity 3.5 + PacketEvents 2.14.0 |
| **ExtendedPatPat-Geyser** (0.13.1) | Geyser-Velocity 2.12.1系 |
| **ExtendedPatPat-Paper** (0.6.1) | Paper/Purpur 26.3（Java 25） |

- **Java・統合版のクロスプレイ**：Velocity版を`plugins/`に、Geyser版をGeyserの`extensions/`に配置。Paperバックエンド用PatPatは不要です。
- **Paper単体**：Paper版のJARのみを`plugins/`に配置します。Velocity版との重複導入は避けてください。

## 主な機能

- しゃがみ＋対象への操作でPat。手のアニメーション・効果音・クールダウン
- Java版：**128×128の地図画像を透明な額縁で表示**。Geyserの表示を参考に、Java版に合わせて調整した画像スケールと視聴者方向のX/Zオフセットを使用し、地図画像内の手が上下に動きます。画像データは各地図につき初回のみ転送
- 統合版：PNG/GIF、手の上下動、Squish（凹み）、音、任意のカメラ演出
- Velocity＋Geyser：Java⇔統合版の相互演出
- 個人ごとのON/OFF、権限設定、Java版スキン切替（地図画像に変換して同梱可能）
- Java版Squish：Velocityでスケール変更による近似。Paper版には未実装

**Java版にURLもリソースパックも不要です。** 地図の色数・128×128解像度・額縁の向き（4方向）という制約があります。ViaVersion経由は接続元プロトコルに配慮していますが、各バージョンの実機互換性は未検証です。

## コマンド

| Java版（Velocity/Paper） | 内容 |
|---|---|
| `/extendedpatpat status` | 状態 |
| `/extendedpatpat toggle` | 演出ON/OFF |
| `/extendedpatpat skin list` | 地図画像スキン一覧 |
| `/extendedpatpat skin <ID>` | 自分が見る手の画像を変更 |
| `/extendedpatpat reload` | 設定再読み込み（管理者） |

`/epp` でも利用できます。統合版は `/extendedpatpatgeyser`（`status`、`test`、`pat`、`toggle`、`reload`）。

**ON/OFFの仕様：** `toggle off` でPatの送信・受信を両方停止し、自分がPat対象になることも防ぎます。表示中の手も終了します。`toggle on` で再開し、指定なしの `toggle` は反転します。設定は再接続後も保持されます。Geyser-VelocityとVelocity版を併用している統合版プレイヤーは、Velocityの `/extendedpatpat toggle` またはGeyserの `/extendedpatpatgeyser toggle` のどちらでも同じ状態を操作できます。

Java版（Velocity）はGeyser版の見た目を基準に、手のサイズを `image-scale=0.6`、上下移動量を `hand-motion-depth=0.28` ブロックに調整しています。**Geyser版は従来どおり `image-scale=0.5` / `hand-motion-depth=0.425`** です。描画方式が違うため、両方を同じ数値にする必要はありません。既存のVelocity設定ファイルは自動変更されません。

詳細：[Velocityの設定](velocity-standalone/README.md) ／ [Paperの設定](paper-standalone/README.md) ／ [画像の追加方法](resourcepack/README.md)

## 注意事項・クレジット

以前のバージョンからの設定移行・コマンド互換はありません。旧JARを削除し、旧設定をバックアップしてから新規導入してください。**CIのビルド成功はMinecraft実機の描画保証ではありません。**

原作：[PatPat Mod](https://github.com/LopyMine/PatPat)／[PatPat Plugin](https://github.com/LopyMine/PatPat-Plugin)（LopyMine、LGPL-3.0）。本プロジェクトの独自コードは[MIT](LICENSE)表記ですが、原作由来の画像・音声は再ライセンスされません。[第三者著作物の通知](THIRD_PARTY_NOTICES.md)・[LGPL-3.0](LICENSES/LGPL-3.0.txt)を参照してください。

不具合・要望：[GitHub Issues](https://github.com/Sanowo0390/ExtendedPatPat/issues)
