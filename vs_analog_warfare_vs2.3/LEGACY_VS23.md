# VS2.3 Legacy Branch Notes

This branch targets the legacy mod stack used by the local reference pack in
`Z:\cbc_terminal_ballistics_parent\Reference mod VS2.3`.

Tested dependency set:

- Minecraft Forge `1.20.1-47.4.0`
- Valkyrien Skies `2.3.0-beta.5tt16+f75b0eafe1`
- Create `0.5.1.j`
- Create Big Cannons `5.8.2tt3-dev+mc.1.20.1-forge`
- Copycats `2.2.2+mc.1.20.1-forge`

Build notes:

- `build.gradle` resolves VS2.3-era mods from the local flat-dir reference pack.
- The VS jar in the reference pack includes a trailing ` 2` in the file name; the
  build creates an alias under `gradle/vs23-reference-aliases` so ForgeGradle can
  deobfuscate it consistently.
- CBC integration is intentionally isolated in `CbcCompat` so API drift in the
  5.8.2tt3 line can be handled without touching scope or mouse-aim logic.
