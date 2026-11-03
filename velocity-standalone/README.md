# PatPatVelocity (Proxy-only Java/Bedrock PatPat bridge)

Standalone Velocity 3.5 plugin targeting Java Minecraft 26.3 with PacketEvents 2.14.0 and the repository's pinned Geyser extension.

## Architecture

- Unmodified **Java clients**: a sneak+right-click serverbound packet triggers short-lived **client-side-only BlockDisplay hand geometry** (six pink terracotta parts), visible to nearby players who track the target entity. No Paper/Purpur plugin, resource pack, client mod, world mutations or server entities.
- **Bedrock clients**: existing PatPatGeyserExtension remains responsible for Pat animations, PNG sprites, squish and sound. The proxy plugin does not send Java render packets to known Bedrock clients.
- **Cross-play**: two in-process JDK Consumer hooks relay accepted Java pats to Geyser and accepted Bedrock pats to Java viewers. The payload is actor UUID + target UUID. Both sides independently resolve the target in each viewer's entity cache, avoiding cross-backend entity-ID reuse. The bridge is optional and only works when both components run in the **same Velocity JVM**.
- Unmodified backend servers need **no addons**.

## Installation

1. Install **PacketEvents 2.14.0 Velocity** as a normal plugin in \`Velocity/plugins/\`.
2. Download **PatPatVelocity-0.1.0.jar** from the successful **Build PatPatVelocity** Action and put it in \`Velocity/plugins/\`.
3. Keep \`PatPatGeyserExtension\` in \`plugins/Geyser-Velocity/extensions/\` for Bedrock support. This exact extension requires **Geyser b1280 (commit 64bb399...)**.
4. Restart Velocity. Java players sneak-right-click a living mob or player. Bedrock users retain their existing Geyser gestures.
5. Run \`/ppv status\`. Geyser bridge status is reported as true if its callback is present.

No backend Paper plugin or original PatPat Plugin is required. **Do not install the independent Paper prototype** at the same time unless you want duplicate Java effects.

## Commands
- \`/ppv status\` — runtime status
- \`/ppv toggle [on|off]\` — individual Java-side preference
- \`/ppv reload\` — reload config (Velocity permission \`patpatvelocity.admin\`)

\`config.properties\` is generated in \`plugins/patpatvelocity/\`. Set \`required-permission\` to a Velocity permission to restrict patting, or leave blank for everybody. Personal preferences are stored separately by UUID.

## Current limitations

- **Proof of concept**: compile-tested via GitHub Actions, not tested with actual clients. Check in-game rendering, especially Display metadata across versions.
- Display geometry approximates a hand. It is not PatPat's original 2D sprite/GIF. Exact textures require a Java resource pack or client mod.
- Proxy cannot reliably read server-side inventory and hitbox rules. Sneaking is enforced; this first version **does not enforce empty main hand** and deliberately forwards normal interactions by default.
- Target head heights are approximate; no Paper backend integration is required.
- Java clients with the original PatPat mod might see duplicate effects if the original backend PatPat Plugin is also present.
- No Folia requirement because the backend is not touched. It supports only clients capable of Display entities (1.19.4+), tested target is 26.3.
- Proxy packet tracking needs entity spawn and movement packets to have arrived before a pat; it may not show an effect when the target is untracked or after a server switch until new spawns arrive.
- The Bedrock extension's own Geyser build pin remains unchanged; newer Geyser builds will intentionally disable it.
- Existing Bedrock preferences and effect settings remain in the Geyser extension.
- For a shared host where multiple Geyser extensions are installed, the bridge key is JVM-global; run just one PatPat Geyser extension instance.

## Build
\`mvn -B -ntp -f velocity-standalone/pom.xml package\`
