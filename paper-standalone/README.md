# PatPatVanilla — Paper/Purpur 26.3

Independent **server-only** implementation for unmodified Java Edition clients. The existing Geyser extension remains a separate build.

Install the JAR from the **Build PatPatVanilla Paper** GitHub Actions artifact into your backend server's plugins folder (not Velocity/Geyser). Restart. Sneak and right-click a mob or player with an empty main hand. No client Mod or resource pack.

- Animated voxel hand built with 6 vanilla BlockDisplay entities; follows the target and moves smoothly down/up
- Optional arm swing and hearts, randomized vanilla sounds, cooldown and per-user toggle
- Configure in plugins/PatPatVanilla/config.yml; use /ppv reload (permission patpatvanilla.admin)
- /ppv toggle [on|off]; permission patpatvanilla.use defaults to everyone

**Limitations:** this is **not pixel-identical** to PatPat's client 2D PNG/GIF renderer. Client-side keybindings, shaders/mixins, and exact player/mob squish/camera transforms cannot be recreated by a pure vanilla server plugin. This variant does not import the original PatPat's source or assets. Not tested with Folia. Bedrock visuals are managed independently by PatPatGeyserExtension; test carefully if both addons handle Bedrock interactions, to avoid duplicated effects.

Requires Java 25 and Paper or Purpur 26.3. To build: `mvn -B -ntp -f paper-standalone/pom.xml package`. Output: target/PatPatVanilla-0.1.0.jar.
