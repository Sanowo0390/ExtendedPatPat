# ExtendedPatPat Velocity — Velocity + PacketEvents + Geyser

**Preferred deployment: no Paper/Purpur PatPat Plugin is needed.**

This repository provides two pieces that run in the same Velocity JVM:

| Component | Install directory | Responsibility |
|---|---|---|
| ExtendedPatPat Velocity 0.5.0 | \`Velocity/plugins/\` | Java edition interaction detection, proxy-side policy, temporary client-only BlockDisplay hand, cross-edition relay |
| ExtendedPatPat Geyser 0.13.0 | \`Velocity/plugins/Geyser-Velocity/extensions/\` | Bedrock edition hand image/GIF, resource pack, effects, Bedrock interaction detection |

Requirements: Velocity 3.5, **PacketEvents 2.14.0 for Velocity**, and **Geyser-Velocity 2.12.1系** (required Geyser internals are checked at startup). The normal Geyser-Velocity plugin is also in \`Velocity/plugins/\`.

## How standalone mode works

1. An unmodded Java client sneaks and right-clicks a tracked living entity/player. PacketEvents sees the entity interaction packet and the plugin validates proximity and cooldown.
2. The Velocity plugin spawns **virtual BlockDisplay entities** for each nearby Java viewer to show a short downward-moving six-part hand; no real world entities or world edits are made.
3. A small in-process JDK callback sends the actor/target UUIDs to the Geyser extension. Each Bedrock viewer looks up the UUID independently in its Geyser entity cache and displays the existing 5-frame Pat animation.
4. A Bedrock player Pats through the Geyser extension's existing interaction logic. The extension renders Bedrock viewers and invokes the same bridge to show Java viewers.
5. Both plugins use the **proxy-only** path whenever \`official-integration-enabled=false\`, **even if an official PatPat Plugin happens to be present on a backend**. That prevents one edition using upstream permissions while the other bypasses them.

No official PatPat Plugin and no server-side Paper plugin are necessary.

## Install

1. Install **PacketEvents 2.14.0 Velocity** and **Geyser-Velocity 2.12.1系** in \`Velocity/plugins/\` as normal Velocity plugins.
2. Put **ExtendedPatPat-Velocity-0.5.0.jar** from the unified **Build ExtendedPatPat** GitHub Actions run in \`Velocity/plugins/\`.
3. Put **ExtendedPatPat-Geyser-0.13.0.jar** from the same unified GitHub Actions run in \`Velocity/plugins/Geyser-Velocity/extensions/\`. **Remove older copies** of this extension first.
4. Restart Velocity. Run \`/ppv status\`; the default mode is **standalone**, and Geyser bridge should be \`true\`.
5. Java: sneak + right-click a mob or another player; Bedrock: use the configured Geyser gesture. Other people on the same backend should see it.

**If you already ran an earlier version, manually edit \`plugins/patpatvelocity/config.properties\`**. Existing configs are preserved; ensure \`official-integration-enabled=false\`, \`standalone-fallback=true\`, and \`bridge-enabled=true\`. In the Geyser extension's config, keep \`standalone-mode=true\`.

The separate Paper \`ExtendedPatPat Paper\` prototype must **not** be installed together with this plugin because it can produce duplicate Java visuals.

## Optional upstream PatPat Plugin mode

If you deliberately want Paper/Purpur's official PatPat Plugin to enforce its whitelist, permission, rate limits, and cancellable API events, install it on every intended backend and set \`official-integration-enabled=true\` on Velocity. The proxy will negotiate PatPat v2 through the standard plugin-message channels, then render only the official backend's approved S2C notification for each viewer. Set \`standalone-fallback=false\` to require an official backend on every server.

Upstream does not always notify the initiating Java player for mob Pats. \`official-local-preview=false\` avoids falsely displaying rejected attempts; setting it true adds a speculative local animation that can appear even when the backend rejects a Pat.

## Config and commands

\`Velocity/plugins/patpatvelocity/config.properties\`: \`official-integration-enabled=false\` by default, \`cooldown-ms=250\`, \`max-distance=4.0\`, \`require-sneaking=true\`, \`hand-enabled=true\`, \`hand-size=1.0\`, \`hand-depth=0.425\`, \`bridge-enabled=true\`, and optional Velocity \`required-permission\`.

- \`/ppv status\` — current mode/bridge status
- \`/ppv toggle [on|off]\` — individual Java visual preference, persisted by UUID
- \`/ppv reload\` — reload proxy settings; requires \`patpatvelocity.admin\`

## Important limitations

- **Java's six BlockDisplay cuboids are a hand approximation, not the original PNG/GIF.** Exact Java client sprites require a resource pack or client Mod.
- Velocity doesn't expose Paper's inventory, server-side hitbox API, WorldGuard or other plugin rules. The standalone proxy checks tracked entity position, sneaking, proximity, cooldown and an optional Velocity permission. It **cannot currently require an empty main hand**; Geyser has its own empty-hand check. Do not treat this as a protection/anti-cheat plugin.
- Java entity tracking relies on packets and is not guaranteed to show a hand when a target isn't tracked, immediately after a server switch or when receiving an unsupported client version.
- Real PatPat client Mod compatibility in backend-free mode requires additional testing; detected client Mods suppress the vanilla proxy hand to avoid double rendering.
- The Geyser extension checks runtime API capabilities instead of pinning one build. Geyser 2.12.1-b1280 and b1284 compile and pass unit tests; future builds are allowed if the internal API remains compatible. Incompatible internals fail closed with a descriptive log.
- **GitHub Actions compilation and unit tests are not an in-game test**. Validate Java→Bedrock, Bedrock→Java, cross-world and cross-backend transitions before public production deployment.

## Build

\`\`\`bash
mvn -B -ntp -f velocity-standalone/pom.xml verify
python scripts/prepare_build.py
mvn -B -ntp verify
\`\`\`

Geyser builds are verified by SHA-256 from its official download API, and the extension CI runs both the b1280 baseline and the newest 2.12.1 build. New untested builds should still be validated in-game before production deployment.


## Java sprite pack & client-only squish (v0.2.0)

Use the **PatPatJava-26.3.zip embedded in the JAR** (extracted at startup; not a separate Actions artifact), built by the [unified workflow](https://github.com/Sanowo0390/ExtendedPatPat/actions/workflows/build-all.yml), and install it into each Java client's resourcepacks or host it on HTTPS. This repo's source image contains five frames; the server advances `patpat:hand_0` through `patpat:hand_4` in one virtual ItemDisplay, using the per-frame `minecraft:item_model` data component. These are **client-only fake entities**; no world mutation.

New Velocity settings:

```properties
hand-renderer=resourcepack
resource-pack-url=
resource-pack-sha1=
resource-pack-assume-installed=false
squish-enabled=true
squish-min-scale=0.86
```

If `resource-pack-url` is provided, Velocity offers the pack to Java clients automatically; set `resource-pack-sha1` to the 40-character SHA-1 in `PatPatJava-26.3.zip.sha1`. Packs must be available via direct HTTP(S) download (GitHub Actions artifact pages are **not** direct URLs). `hand-renderer=auto` selects the sprite only for viewers whose pack download is confirmed successful and uses voxel otherwise; for manually installed packs set `resource-pack-assume-installed=true`. `hand-renderer=resourcepack` always shows the sprite and will show a missing model on clients lacking the pack.

`squish-enabled=true` sends temporary Java clientbound `minecraft:scale` attribute packets and restores the most recent server scale afterward. This is **uniform size scaling**, not the Mod's true per-axis squash. It does not modify server attributes or hitboxes. Live Java/Bedrock client tests are required.

Minecraft **26.3** resource pack format is **97.1**. Older clients need separately compatible packs and are not guaranteed.


## Per-client PatPat skins (v0.3.0)

Place custom PatPat Mod ZIPs, PNG sprite strips or GIFs in
\`resourcepack/skins/\`, then run [Build All](../resourcepack/README.md)
to rebuild **both the Java resource pack and the matching plugin JARs**.

\`/ppv skin list\` shows the styles in the combined pack.
\`/ppv skin <id>\` selects one for this Java viewer, and
\`/ppv skin default\` resets it. Each user's choice is persisted in
\`plugins/patpatvelocity/player-skins.properties\`.

The viewer's selected style drives item model frames, duration, transforms,
and optional sound. Importing \`Bonk Bonk Animation.zip\` as
\`resourcepack/skins/Bonk Bonk Animation.zip\` makes it available under
\`bonk_bonk_animation\` with six frames. The resource pack ZIP must also be
installed/accepted by Java clients; commands do not transmit textures.


## 内包型パック配布・ViaVersion（v0.5.0）

**ExtendedPatPat-Velocity.jarに標準用の `PatPatJava-26.3.zip` が入っています。** 起動すると `plugins/patpatvelocity/PatPatJava-26.3.zip` に取り出します。通常のサーバーパックを置き換える必要はありません。

自動適用（任意）にはこのZIPがクライアントからHTTP(S)で取得できるURLが必要です。2つの方法があります。

- 通常のWebサーバー/CDNへZIPを配置し `resource-pack-url=https://.../PatPatJava-26.3.zip` を設定する
- 内蔵の読み取り専用Web配信を `pack-http-enabled=true` にして `pack-http-bind=127.0.0.1`, `pack-http-port=8765` で起動し、Nginx Proxy Manager等を経由して公開した `https://.../patpat/PatPatJava-26.3.zip` を `resource-pack-url` に設定する

ZIPをJARから取り出す作業は自動ですが、**公開URLやポートの開放はプラグインだけでは自動設定できません**。内蔵配信を直接公開する場合はネットワークのアクセス制限も設定してください。

パックはPatPat専用UUIDでプレイヤーへ追加提供され、既存のリソースパックとは別に扱われます。受け入れたことを確認したプレイヤーにだけPNG製の手を表示する `hand-renderer=auto` が標準です。受け入れなければブロック製の手を使います。

Velocityは `Player.getProtocolVersion()` とPacketEventsのクライアントバージョンを見てViaを含む接続を識別します。Java 1.21.4以上では画像方式、1.19.4～1.21.3はVoxel、それ以前はDisplayパケットを送信しません。実際のVia経由変換は接続元バージョンごとに実機検証が必要です。

例として以前示したBonkパックは同梱せず、標準画像のみを配布します。`/ppv skin list` には配布した画像のみ表示されます。

## Release names and preserved identifiers

The distributed JAR is `ExtendedPatPat-Velocity-0.5.0.jar`. The Velocity plugin's **display name** is `ExtendedPatPatVelocity` but its **ID** remains `patpatvelocity`; this avoids losing `plugins/patpatvelocity/` settings, player choices, and bridge compatibility. Existing `/ppv` and `/patpatvelocity` commands work; aliases `/extendedpatpat` and `/epp` are also available. Remove older PatPatVelocity JARs to avoid duplicate plugin IDs.
