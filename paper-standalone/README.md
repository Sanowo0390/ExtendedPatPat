# ExtendedPatPat Paper — Paper/Purpur 26.3

Independent **server-only** implementation for unmodified Java Edition clients. The existing Geyser extension remains a separate build.

Install the JAR from the **Build ExtendedPatPat Paper Paper** GitHub Actions artifact into your backend server's plugins folder (not Velocity/Geyser). Restart. Sneak and right-click a mob or player with an empty main hand. No client Mod. The new animated image mode requires PatPatJava-26.3.zip; fallback voxel mode needs no pack.

- Animated voxel hand built with 6 vanilla BlockDisplay entities; follows the target and moves smoothly down/up
- Optional arm swing and hearts, randomized vanilla sounds, cooldown and per-user toggle
- Configure in plugins/ExtendedPatPatPaper/config.yml; use /extendedpatpat reload (permission extendedpatpat.admin)
- /extendedpatpat toggle [on|off]; permission extendedpatpat.use defaults to everyone

**Limitations:** this is **not pixel-identical** to PatPat's client 2D PNG/GIF renderer. Client-side keybindings, shaders/mixins, and exact player/mob squish/camera transforms cannot be recreated by a pure vanilla server plugin. This project includes original PatPat-derived hand and sound assets under the upstream LGPL-3.0; see the repository's THIRD_PARTY_NOTICES.md. Not tested with Folia. Bedrock visuals are managed independently by ExtendedPatPat Geyser; test carefully if both addons handle Bedrock interactions, to avoid duplicated effects.

Requires Java 25 and Paper or Purpur 26.3. To build: `mvn -B -ntp -f paper-standalone/pom.xml package`. Output: target/ExtendedPatPat-Paper-0.5.0.jar.


## Animated Java hand (v0.2.0)

The Paper plugin now uses one `ItemDisplay` with the five image frames from the shared [PatPat Java pack](../resourcepack/README.md). The matching `PatPatJava-26.3.zip` is already embedded in the ExtendedPatPat Paper JAR and extracted at startup; it is not a separate Actions artifact. Install it on each client or configure Paper's `server.properties` resource pack URL and SHA-1 to distribute it. The server cycles the five `patpat:hand_*` item models using `ItemMeta.setItemModel`.

```yaml
animation:
  renderer: resourcepack # resourcepack / voxel
```

The original six-block hand remains selectable via `renderer: voxel`.
**Paper-only edition does not currently reproduce the squish effect**; changing a real entity scale would also alter gameplay hitboxes. Use Velocity 0.2.0 for client-only uniform squash. Never run both Paper and Velocity implementations simultaneously.


## Independent per-viewer skin choice (v0.3.0)

Paper now embeds the same generated \`patpat-skins.properties\` registry
as Velocity. Add ZIP/GIF/PNG skins under \`resourcepack/skins/\` before
running [Build All](../resourcepack/README.md).

Use \`/extendedpatpat skin list\` and \`/extendedpatpat skin <id>\` to select the Java-side hand.
Selections are saved under \`selected-skins\` in the plugin config. Each nearby
Java viewer receives a separate ItemDisplay hidden from all other viewers
with their selected frame sequence and optional sound. That is heavier than
one shared display, so check \`animation.max-active\` and \`view-range\`.


## 内包型リソースパック・別スタックで配布（v0.5.0）

**ExtendedPatPat-Paper.jar** は標準Java手のZIPを内包します。起動時に `plugins/ExtendedPatPatPaper/PatPatJava-26.3.zip` へ自動書き出しされます。これをHTTP(S)でホストした公開URLを `pack.url` に設定すると、Paperの `Player.addResourcePack(UUID,...)` によって、通常のserver.propertiesのリソースパックに**追加**として提示されます。単独のパック配布プラグインは不要です。

内蔵Web配信を使う場合は `pack.http-enabled=true` にしてポートを設定し、Nginx Proxy Manager等で公開したURLを `pack.url` に入力します。外部アクセス可能な公開URLの設定は必要です。

ViaVersionが**Paper自身に導入されている場合**、そのAPIで実際のクライアントバージョンを読み取り、1.21.4以上にPNGモデル、1.19.4～1.21.3にVoxelを個別表示します。それ以前のクライアントにはDisplayを送信しません。ViaがVelocityにしか導入されていない場合、Paper側で元のバージョンを判別できない可能性があります。混合クライアント環境ではVelocity版を推奨します。

以前のBonkなど追加スキンは含まれず、デフォルト素材だけが内包されます。

## Fresh installation (no legacy migration)

Bukkit plugin: `ExtendedPatPatPaper`. Directory: `plugins/ExtendedPatPatPaper/`. Commands: `/extendedpatpat`, `/epp`. Permissions: `extendedpatpat.use`, `extendedpatpat.admin`. Settings from earlier PatPatVanilla/PatpatPaper installations are not automatically transferred.
