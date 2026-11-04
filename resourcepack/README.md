# PatPat Java Edition 26.3 Resource Pack

This 26.3 resource pack contains the project's own hand image
(\`src/main/resources/default-hand.png\`, five 112×112 pixel frames).
No textures are copied from the upstream PatPat client Mod.

The Python builder generates \`PatPatJava-26.3.zip\` with item definitions:
\`patpat:hand_0\` through \`patpat:hand_4\`.
Both PatPatVelocity (proxy) and PatPatVanilla (Paper/Purpur) use the **same pack**.

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
