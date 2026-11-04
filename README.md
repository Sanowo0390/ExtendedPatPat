# ExtendedPatPat 🐾

**Minecraft Java版・統合版で、モブやプレイヤーを撫でられる非公式プラグイン。** 手のアニメーション、効果音、凹み演出に対応し、元のPatPat Mod／Pluginを導入しなくても動作します。

> [!IMPORTANT]
> [LopyMine氏のPatPat Mod](https://github.com/LopyMine/PatPat)・[PatPat Plugin](https://github.com/LopyMine/PatPat-Plugin) に着想を得た**非公式の独立プロジェクト**です。原作者との提携・承認関係はありません。Minecraftの公式製品でもなく、Mojang／Microsoftとは無関係です。

## 配布物・導入

[**GitHub Actionsからダウンロード**](https://github.com/Sanowo0390/ExtendedPatPat/actions/workflows/build-all.yml) — 1回のビルドで次の**3つのJARのみ**を配布します。

| 配布物 | 導入先 | 用途 |
|---|---|---|
| **ExtendedPatPat-Velocity** (0.5.0) | `Velocity/plugins/` | Java版の操作検出・画像の手・統合版との連携 |
| **ExtendedPatPat-Geyser** (0.13.0) | `Velocity/plugins/Geyser-Velocity/extensions/` | 統合版の手画像・GIF・効果音・凹み演出 |
| **ExtendedPatPat-Paper** (0.5.0) | `Paper/plugins/` | Paper/Purpur単体で動作するJava版 |

**Java版＋統合版で遊ぶ場合：** Velocity 3.5、[PacketEvents](https://github.com/retrooper/packetevents) 2.14.0（Velocity版）、Geyser-Velocity 2.12.1系を導入し、**Velocity版＋Geyser版**のJARを配置して再起動します。Paper/PurpurバックエンドへのPatPat追加は不要です。

**Paper単体の場合：** Paper/Purpur 26.3（Java 25）に**Paper版のみ**を導入します。Velocity版とPaper版の併用は二重演出の原因になるため避けてください。

詳細：[Velocity版](velocity-standalone/README.md) ／ [Paper版](paper-standalone/README.md) ／ [Geyser設定](src/main/resources/config.properties)

## 機能

- **しゃがみ＋対象への操作**でPat。画像の手が上下に動き、効果音を再生
- **Java版・統合版の相互表示**（Velocity＋Geyser構成）
- **Squish（凹み）演出**：統合版に対応。VelocityのJava版ではスケール変更で近似。Paper単体版は未対応
- **PNG／GIF・位置・サイズ・音のカスタマイズ**（Geyser版）。Java版はリソースパックの画像を使用
- **個人ごとのON／OFF、クールダウン、権限設定**
- **ViaVersion接続への配慮**：Java 1.21.4以降は画像表示、1.19.4～1.21.3はブロック手、それ以前はDisplay演出を送信しない設計（実機での全バージョン動作は未確認）

### Java版のリソースパック

標準の手画像パックは**Velocity版・Paper版のJARに内包**されています。通常のサーバーパックとは**別の追加パック**として送信可能です。ただし、クライアントへの自動配布には**取得可能なHTTP(S) URLが必要**です。内蔵HTTP配信機能も使用できます。

URLを用意しない場合はプレイヤーによる手動導入、またはリソースパック不要のブロック手を利用してください。デフォルト以外の例示素材（Bonkなど）は配布物に含みません。

パック設定・独自スキン：[リソースパックの説明](resourcepack/README.md)

## コマンド

| Java版（Velocity／Paper） | 内容 |
|---|---|
| `/extendedpatpat status` | 状態を表示 |
| `/extendedpatpat toggle` | 自分の演出をON／OFF |
| `/extendedpatpat pack` | 追加パックを再送信（URL設定時） |
| `/extendedpatpat skin list` / `/extendedpatpat skin default` | 利用できる手画像の確認／選択 |
| `/extendedpatpat reload` | 設定再読み込み（管理者） |

統合版：`/extendedpatpatgeyser status`、`test`、`pat`、`toggle`、`stats`、`reload`。詳細は[Geyser拡張の設定](src/main/resources/config.properties)を参照してください。

## 更新時の注意

旧バージョンとの互換性維持・設定ファイルの自動移行は**廃止**しました。新規導入する場合は旧JARと旧設定ディレクトリをバックアップのうえ削除し、すべて新しい設定から始めてください。Java版コマンドは `/extendedpatpat`（略称 `/epp`）、Geyserは `/extendedpatpatgeyser` です。

Geyserの内部APIに依存するため、更新後は互換性の確認を推奨します。**GitHub Actionsでのビルド・単体テスト成功はMinecraft実機での動作保証ではありません。**

## クレジット・ライセンス

原作：[**PatPat Mod**](https://github.com/LopyMine/PatPat) ／ [**PatPat Plugin**](https://github.com/LopyMine/PatPat-Plugin)（LopyMine氏、**LGPL-3.0**）。

本リポジトリの独自コードは [MIT License](LICENSE) 表記ですが、**元のPatPatに由来する手画像・効果音はMITとして再ライセンスされません**。上流と一致する音声や、上流の手画像に由来する素材を含みます。配布条件・素材の来歴については [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) と [LGPL-3.0](LICENSES/LGPL-3.0.txt) を参照してください。第三者素材の再配布には各作者の権利・ライセンスを確認してください。

不具合・要望：[GitHub Issues](https://github.com/Sanowo0390/ExtendedPatPat/issues)。
