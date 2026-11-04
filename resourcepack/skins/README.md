# Import PatPat client-side animations here

Place each skin in this directory as a separate file, for example:

- \`Bonk Bonk Animation.zip\` — a PatPat Mod resource pack with
  \`assets/patpat/textures/config.json5\`, a sprite sheet and optional
  \`sounds.json\`/OGG sound. It will become style ID \`bonk_bonk_animation\`.
- \`paw.gif\` — an animated GIF. It will become style \`paw\`.
- \`myhand.png\` — horizontal equally sized frames (or single PNG).

**Do not extract incoming ZIPs.** The builder parses their animation metadata
(\`totalFrames\`, \`duration\`, offsets, scales, and optional sound event).
The final Java pack contains *all* skins. The two Java plugins embed the
matching \`patpat-skins.properties\` registry built from those same files.

Each Java player can use \`/ppv skin list\` and \`/ppv skin <id>\`
to choose the animation independently; the selection is persisted by UUID.
On Paper, each viewer gets a separate visible-only-to-them ItemDisplay.

The repository does not redistribute third-party skin art by default. Only add
assets you have permission to share. GitHub Actions builds every skin added
here into \`PatPatJava-26.3.zip\`.

**標準リリースでは `PATPAT_DEFAULT_ONLY=1` を指定してこのフォルダーを無視します。例として提供されたBonkは公開JARに含まれません。**
