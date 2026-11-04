# PatPat Java Edition 26.3 Resource Pack

This 26.3 resource pack contains the project's own hand image
(\`src/main/resources/default-hand.png\`, five 112×112 pixel frames).
No textures are copied from the upstream PatPat client Mod.

The Python builder generates \`PatPatJava-26.3.zip\` with item definitions:
\`patpat:hand_0\` through \`patpat:hand_4\`.
Both ExtendedPatPat Velocity (proxy) and ExtendedPatPat Paper (Paper/Purpur) use the **same pack**.

### Installation

1. Download \`PatPatJava-26.3.zip\` from GitHub Actions workflow
   **Build Java PatPat Resource Pack**. Download the artifact, extract the
   pack ZIP, and keep that inner ZIP intact.
2. Either install it under Java Edition \`resourcepacks\` and enable it
   in the game, **or host the ZIP on an HTTPS URL** and configure your proxy
   or server to offer it. Set pack SHA-1 when possible.
3. On the proxy use \`hand-renderer=resourcepack\`. To use voxel fallback
   instead set \`hand-renderer=voxel\`.
4. On Paper use \`animation.renderer=resourcepack\` (default).
   To use voxel fallback set \`animation.renderer=voxel\`.

The pack cannot force itself onto clients. A client that has not
downloaded/applied the pack will see Minecraft's missing item model.
For automatic delivery, publish the ZIP to a reachable HTTP(S) URL and
set your proxy resource-pack settings / Paper server resource pack URL.

To build: \`pip install Pillow==11.3.0 && python resourcepack/build.py\`
The script also prints the pack SHA-1 and writes a \`.zip.sha1\` file.

**Pack format**: 26.3, \`min_format=[97,1]\` and \`max_format=97\`.
Older Java versions need their own pack format declaration.


## Import multiple client-side PatPat texture packs

Place any number of compatible ZIP, PNG or GIF files under
\`resourcepack/skins/\` on the repository's \`main\` branch.

For example, the uploaded **Bonk Bonk Animation.zip** is a real PatPat Mod
resource pack, not a GIF: it contains a six-frame horizontal PNG,
\`assets/patpat/textures/config.json5\` (250 ms, scale and offsets), and
an OGG effect. Upload that ZIP **without extracting it** to
\`resourcepack/skins/Bonk Bonk Animation.zip\`, then run **Build All PatPat**.

The builder automatically generates:
- \`patpat:hand_default_0\` through \`hand_default_4\` from the bundled hand;
- \`patpat:hand_bonk_bonk_animation_0\` through \`_5\` from Bonk;
- custom sound events, if the incoming mod pack defines a sound;
- the same \`patpat-skins.properties\` in **both Java plugin JARs**.

Each Java player selects a style independently:
\`/ppv skin list\` lists available choices and
\`/ppv skin bonk_bonk_animation\` enables the example.
\`/ppv skin default\` reverts. Selections persist after reconnect and restart.

On Velocity, only the receiving Java client sees their chosen style using
viewer-local ItemDisplay packets. Paper produces viewer-specific hidden-by-default
ItemDisplays visible to each chosen player, so selections are independent there too.

**Unified GitHub Actions run:**
[Build ExtendedPatPat](https://github.com/Sanowo0390/ExtendedPatPat/actions/workflows/build-all.yml)
builds all three plugin JARs. It publishes exactly three artifacts:
`ExtendedPatPat-Velocity`, `ExtendedPatPat-Geyser`, and
`ExtendedPatPat-Paper`. The Java hand pack is **embedded** in both Java-side JARs;
there is no standalone pack or Complete artifact.

**Note:** Do not redistribute third-party textures unless you have permission.
The repository does not automatically commit uploaded third-party skin ZIPs.
Custom skins must be committed into \`resourcepack/skins/\` for GitHub Actions
to include them. ZIP extraction is never performed on the build runner.


## 標準配布（0.5.0以降）

GitHub Actionsからダウンロードできるのは `ExtendedPatPat Velocity`・`ExtendedPatPat Geyser`・`ExtendedPatPat Paper` の**3つのJARのみ**です。Java版の標準画像ZIPはVelocity版とPaper版のJARに内包され、起動時にプラグインのデータフォルダーへ書き出されます。サンプルBonkは同梱しません。

ユーザー側への適用は、通常のサーバーリソースパックとは独立したパックの追加リクエスト（UUID指定）で行います。ただしMinecraftはクライアントがダウンロードできるHTTP(S) URLを要求します。`resource-pack-url` にZIPの公開URLを設定するか、JARに付属する読み取り専用HTTP配信機能を有効化し、そのURLを公開してください。既存の `server.properties` のリソースパック設定は変更不要です。

ViaVersion経由のJava 1.21.4以降は画像方式、1.19.4～1.21.3はVoxel方式、それより古いクライアントはDisplayパケットの送信を抑制します。実際の表示互換性はクライアント／Viaの組み合わせごとに確認が必要です。


## GitHubの標準配布に関する変更

`PATPAT_DEFAULT_ONLY=1` で標準画像以外を取り込みません。GitHub Actionsが配布するのはExtendedPatPat Velocity・ExtendedPatPat Geyser・ExtendedPatPat Paperの各JARのみです。生成されたZIPは前者とPaper版のJAR内に入ります。サンプルBonkなどを取り込むことはありません。

The internal Java pack ZIP filename and the `patpat:` model namespace are intentionally unchanged for compatibility with existing resource-pack URLs, item models and player selections. The distributed plugins and the project itself are branded **ExtendedPatPat**.
