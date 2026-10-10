# VS Analog Warfare (vs2.3 line)

Analog optics, fire control, and vehicle tooling for **Create Big Cannons** on
**Valkyrien Skies** ships — aim real cannons from real scopes, drive turrets
with your mouse, stabilize the gun against hull motion, and record whole
vehicle setups that replay from a schematic paste.

Current release: **0.5.3-vs2.3** (targets the Valkyrien Skies **2.3.x** line;
the VS2.4/Create 6 "main line" is built from the same source tree and ships
its own releases).

## Features

- **Analog Cannon Scope** — a linked cannon's gunsight: ballistic reticle,
  world-stabilized aim on moving ships, free look, zoom, rangefinder,
  third-person gun view, and full artillery zeroing including high-arc
  (lofted) fire. While scoped, Ping Wheel and Steve's Army pings raycast
  along the scope view, so a ping lands where the reticle points.
- **Mouse Aim Controller** — point a cannon with your mouse. *Cannon* mode
  slews the barrel directly; *Turret* mode outputs a PID-controlled Create
  rotation for a physics-bearing turret ring, with per-block step-test
  auto-calibration. Input shaft speed sets the output ceiling; War
  Thunder-style free-look tether keeps the camera near the crosshair.
- **Gyro Stabilizer** — holds the linked cannon's world elevation the moment
  you stop aiming, and levels the scope horizon while the lock is engaged.
  With mouse aim it pins elevation to the view direction and compensates
  hull motion at full speed.
- **Cannon Decoration Bearing** — decorative contraptions (shields, mantlets,
  rangefinders) that follow the linked cannon's pivot in both axes, with
  yaw-only / pitch-only modes. Survives schematic paste and self-repairs.
- **Vehicle Setup Block** — records block placements, weapon registrations,
  crew, engine activation, and wiring, then replays it with one right-click
  — including on a freshly pasted VMod schematic.
- **Mount Handle + Invisible Seat** — crew positions that mount players by
  click or redstone, and a seat that renders nothing.
- **Analog Screwdriver** — the multi-tool that links scopes, stabilizers,
  decorations, and handles; records setups; and highlights acted blocks.

## Requirements

| Dependency | Version |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47+ |
| [Valkyrien Skies](https://modrinth.com/mod/valkyrien-skies) | 2.3.0-beta.5 – 2.3.x |
| [Create](https://modrinth.com/mod/create) | 0.5.1.j – 0.5.x |
| [Create Big Cannons](https://modrinth.com/mod/create-big-cannons) | 5.8.2 – 5.8.x |

Optional integrations degrade gracefully (a one-time chat warning, and only
that feature switches off): **VMod 0.1.3** (schematic pasting),
**Drive By Wire 0.0.6b**, **Trackwork 1.0.2c**, **Create Ender Transmission
2.0.7**, **Create Tweaked Controllers 1.2.4**, **CBC Terminal Ballistics**,
**Ping Wheel 1.12.0**, and **Steve's Army 0.4.0**.

## Documentation

- **[Wiki](https://github.com/MegiTicky/VS-analog-warfare/wiki)** — the
  player-facing guide: every block, controls, and integrations.
- **Changelog** — `../CHANGELOG.md` in the source workspace, a per-release
  history back to 0.3.7.
- **`docs/`** — deep dives:
  [mouse-aim-turret-mode.md](docs/mouse-aim-turret-mode.md) (wiring,
  control loop, tuning), [stabilizer-block.md](docs/stabilizer-block.md)
  (servo design, config),
  [decoration-bearing-rotation-mode.md](docs/decoration-bearing-rotation-mode.md),
  [vehicle-setup.md](docs/vehicle-setup.md) (recording, validated
  dependency versions),
  [vehicle-setup-coordinate-debug.md](docs/vehicle-setup-coordinate-debug.md).

## Building

```
gradlew build
```

ForgeGradle deobfuscates the VS2.3-line dependency jars at dev time; point
`vs23_reference_mods_dir` at a folder with the reference mods (see
`build.gradle`) for `runClient`/`runServer`.

## License

GPL-3.0 — see [LICENSE](LICENSE). Author: Erika.
