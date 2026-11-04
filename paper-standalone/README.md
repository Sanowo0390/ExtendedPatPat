# PatPatVanilla — Paper/Purpur 26.3

Independent **server-only** implementation for unmodified Java Edition clients. The existing Geyser extension remains a separate build.

Install the JAR from the **Build PatPatVanilla Paper** GitHub Actions artifact into your backend server's plugins folder (not Velocity/Geyser). Restart. Sneak and right-click a mob or player with an empty main hand. No client Mod. The new animated image mode requires PatPatJava-26.3.zip; fallback voxel mode needs no pack.

- Animated voxel hand built with 6 vanilla BlockDisplay entities; follows the target and moves smoothly down/up
- Optional arm swing and hearts, randomized vanilla sounds, cooldown and per-user toggle
- Configure in plugins/PatPatVanilla/config.yml; use /ppv reload (permission patpatvanilla.admin)
- /ppv toggle [on|off]; permission patpatvanilla.use defaults to everyone

**Limitations:** this is **not pixel-identical** to PatPat's client 2D PNG/GIF renderer. Client-side keybindings, shaders/mixins, and exact player/mob squish/camera transforms cannot be recreated by a pure vanilla server plugin. This variant does not import the original PatPat's source or assets. Not tested with Folia. Bedrock visuals are managed independently by PatPatGeyserExtension; test carefully if both addons handle Bedrock interactions, to avoid duplicated effects.

Requires Java 25 and Paper or Purpur 26.3. To build: `mvn -B -ntp -f paper-standalone/pom.xml package`. Output: target/PatPatVanilla-0.3.0.jar.


## Animated Java hand (v0.2.0)

The Paper plugin now uses one `ItemDisplay` with the five image frames from the shared [PatPat Java pack](../resourcepack/README.md). Download `PatPatJava-26.3.zip` from the resource-pack GitHub Actions workflow. Install it on each client or configure Paper's `server.properties` resource pack URL and SHA-1 to distribute it. The server cycles the five `patpat:hand_*` item models using `ItemMeta.setItemModel`.

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

Use \`/ppv skin list\` and \`/ppv skin <id>\` to select the Java-side hand.
Selections are saved under \`selected-skins\` in the plugin config. Each nearby
Java viewer receives a separate ItemDisplay hidden from all other viewers
with their selected frame sequence and optional sound. That is heavier than
one shared display, so check \`animation.max-active\` and \`view-range\`.
