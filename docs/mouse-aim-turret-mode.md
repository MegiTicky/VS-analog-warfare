# Mouse Aim Block — Turret Mode

Turret mode turns the Mouse Aim Controller from a *barrel* servo into a *turret* servo:
the mouse keeps controlling the gun's elevation, but horizontal tracking is done by
physically rotating the whole turret structure with a Clockwork physics bearing,
driven by a PID-controlled Create rotation output on the block.

## Modes (right-click config screen)

**Right-click the block with an empty hand** to open its config screen (styled
like the vehicle setup editor, immediate-apply buttons). The wrench only
rotates the axis.

| Setting | Values | Meaning |
|---|---|---|
| Aim mode | Cannon / Turret | Cannon: mouse aim slews barrel yaw + pitch (classic). Turret: mouse aim slews barrel pitch only; yaw becomes a rotation output. |

There is no separate output-strength setting: the rotation speed of the shaft
feeding the block sets how fast the gun may move (see below). The mode
persists in the block NBT. Turret mode adds a **Servo tuning** section
(Calibrate / Reset tuning) to the same screen.

## Input speed = output ceiling

The block is a single in-line kinetic block (the encased chain drive model):
shafts enter from **either end** of its axis — that end is the power input and
the **opposite end becomes the rotation output** used by turret mode. The
sides are dynamic: with both ends powered the stronger network is the input,
and rewiring re-routes which end outputs.

Input shaft speed is *power as throttle, not direction*: you are not turning
the gun with torque, you are telling the servo how fast it may move. The
input speed caps every output path:

- **Turret mode (yaw output):** the tuned PID command is clamped to a cap that
  scales linearly with the input speed — **256 RPM input (Create's maximum)
  commands the full `turretAim.maxOutputRpm` ceiling; half that input commands
  half the cap.** The PID gains are never scaled, so the calibrated response
  shape is identical at every input speed, only time-limited.
- **Turret mode (pitch slew):** the barrel pitch slew doubles with the cannon
  chase change and also scales linearly with input speed (full rate at 256 RPM
  input), so elevation speed matches the yaw authority.
- **Cannon mode:** the chase rate is the input speed scaled by
  `mouseAimRateMultiplier` (default 0.25 → input/4 RPM-equivalent), capped at
  `maxOutputRpm × mouseAimRateMultiplier` (`maxOutputRpm`/4 RPM-equivalent —
  4 RPM-equivalent at the default 16 RPM ceiling). Below the cap the input
  speed sets the rate, so a slow crank fine-aims.
- There is **no minimum input gate**: both modes are inert at zero input
  (zero chase rate, zero cap), so sub-1-RPM input gives fine control.

## Turret mode wiring

```
[Mouse Aim Controller] --shaft--> [Energy Transmitter] ~wireless~ [Energy Transmitter] --shaft--> [Physics Bearing (facing up)]
     on turret ship                       turret ship                hull ship                       hull ship, turret attached
```

- The turret structure is assembled on a **Clockwork physics bearing** placed on the
  hull. The bearing consumes the network's RPM as a target angular velocity and
  applies the torque itself; the controller only commands velocity.
- Direct shafts **cannot** cross the bearing joint — use the Ender Energy
  Transmitters (same channel + password) to bridge turret ship → hull ship.
- Leave the cannon mount's **yaw shaft unpowered**: at zero speed CBC freezes the
  mount yaw, so the barrel holds its position inside the turret while the ring
  rotates. The gun's relative yaw is *not* re-centered; aim the barrel roughly
  forward before engaging turret mode for a natural look.
- The output network is isolated from the input: the block's power input can
  never leak into the bearing, and the bearing's stress is provided by the
  controller (`turretAim.stressCapacity`, default 4096).

## Control loop

Per server tick (`TurretYawController`):

- **Setpoint** — aim direction from the scope free-look packet (world space;
  the free-look angles are seeded in the world frame even when mounted).
- **Measurement** — current bore direction: CBC mount yaw/pitch read from the mount,
  rotated into ship-local space (`CbcCompat.getAimDirection`), then into world space
  with the **turret** ship's tick transform.
- **Error** — wrapped world azimuth difference (`atan2(-x, z)` convention).
- **Output** — the per-block tuned PID (`kp·err + kd·d(err)/dt + ff·d(aim)/dt`,
  with smoothed derivative and feed-forward) clamped to the input-scaled cap
  (see above), slew-limited per tick, zeroed under the deadband. The sign
  assumes an up-facing bearing (positive RPM = counter-clockwise seen from
  above = decreasing Minecraft azimuth); `turretAim.invert` flips it if needed.
- **Tuning** — gains come from the block's `TurretTuning`: the global config
  template until a calibration stores per-block values (Reset reverts).
- **Ramp-down** — when the scope closes or the target times out, the output ramps
  to zero at the slew rate instead of freezing.

## Gyro stabilizer interplay

With a linked, enabled [Gyro Stabilizer](stabilizer-block.md), mouse aim
changes in two ways (server-side, gated on the stabilizer link):

- **Pitch is hard-set** to the aim direction each tick instead of chasing —
  absolute elevation, no chase lag. CBC's elevation limits still clamp; the
  gyro's own servo stays suppressed while aiming, so nothing else writes the
  axis. Cannon-mode yaw keeps the chase; turret-mode yaw stays with the PID
  bearing.
- **Hull-motion compensation at full speed:** each tick's correction is split
  into a frame-rotation share (the hull's motion re-mapped through the frame)
  and a mouse share. For a gyro-stabilized mount the frame share is applied at
  full speed while the mouse share keeps the input-scaled slew limit. A mount
  **without** a stabilizer gets no frame share — the plain slew-limited chase
  rides the hull only as far as its drive rate allows. That gap is what makes
  the gyro block worth its cost: without it, mouse aim is not a free
  stabilizer.

## Free-look tether

While scoped, the free-look camera is clamped into a configurable cone around
the zeroed reticle direction (client config `freeLookTetherDegrees`, default
15°; 180+ disables). Mouse input past the boundary is absorbed, and hull
rotation that carries the crosshair away drags the camera along, so the turret
is never left behind. The aim cross tints amber while the camera is pinned.

## Config

Common config, `mouseAim` section:

| Key | Default | Meaning |
|---|---|---|
| `mouseAimPacketIntervalTicks` | 2 | Client→server aim update interval |
| `mouseAimTargetTimeoutTicks` | 6 | Ticks before the block forgets a stale target |
| `mouseAimRateMultiplier` | 0.25 | Cannon chase rate (input × this, capped at `maxOutputRpm` × this); also scales the turret pitch slew |

Common config, `turretAim` section:

| Key | Default | Meaning |
|---|---|---|
| `maxOutputRpm` | 16 | Ceiling of the turret rotation output; the input shaft speed maps linearly onto it (full ceiling at 256 RPM input) |
| `kp` | 0.4 | Proportional gain: RPM per degree of aim error (before the input-speed cap) |
| `kd` | 1.2 | Derivative gain: damping RPM per deg/tick of error change |
| `feedForward` | 0.05 | Lead RPM per deg/tick of aim sweep |
| `deadbandDeg` | 0.05 | errors below this command nothing |
| `outputSlewRpmPerTick` | 10.0 | max output change per tick (also ramp-down rate) |
| `calibrationReferenceLagTicks` | 75 | Spin-up lag of the hand-tuned reference turret; calibrations score 100% near this, slower turrets get de-rated gains |
| `stressCapacity` | 4096 | stress capacity provided on the output network |
| `invert` | false | flip output sign (bearing placed/spinning differently) |
| `debug` | false | log setpoint/measurement/error/rpm once per second |

## Implementation notes

- The rotation output is a second kinetic persona on the same block position
  (`MouseAimOutputInterface`, bound through a synthetic `output=true` block
  state that never occurs in the world): the main entity keeps the in-line
  power connections, the persona connects only through the current output end
  (`MouseAimBlock.getOutputFace` = opposite of the resolved input face). CBC's
  mixins on Create's `RotationPropagator` (plain `instanceof` checks) do the
  routing; CBC's own `HasMultipleKineticInterfaces` comes from the CBC jar, so
  nothing of it ships in this one.
- The sub-BE is a `GeneratingKineticBlockEntity`; `getGeneratedSpeed()` returns the
  PID output and `updateGeneratedRotation()` is called only when the command changes
  by more than 0.01 RPM to avoid network churn.
- Network isolation: both personas sit at the same `BlockPos`, and Create keys
  kinetic networks by `KineticBlockEntity.createNetworkId()` which defaults to the
  block position. Without an override the output would merge into the main block's
  power network and let the constant input RPM reach the turret. The persona
  overrides `createNetworkId()` with a position-derived-but-distinct id so the
  generated output forms its own isolated network. The power persona never
  reaches through the output end — claiming that end too would let the input
  walk claim the output shaft, which Create's conflict handling then destroys.
- Placement: inherits `RotatedPillarKineticBlock`'s Create-standard
  `getStateForPlacement` (aligns to an adjacent shaft axis, else the player's look /
  horizontal axis), so it orients like every other Create pillar block.
- Calibration: the step test measures the turret's spin-up lag and stores tuned
  gains in the block (`TurretTuning`), so tuned designs travel with schematic
  paste; Reset reverts to the global template. The global template's kp is
  de-rated for turrets slower than `calibrationReferenceLagTicks`.
- Limitation: aiming while mounted **on the turret itself** is unsupported for yaw
  (the setpoint frame would rotate with the turret). Pitch still works.

## Test checklist (first in-game session)

1. Cannon mode regression: aim + zeroing scroll behave exactly as before.
2. Config screen: right-click the block with an empty hand — mode buttons
   (Cannon/Turret) and, in turret mode, Calibrate / Reset tuning. The wrench
   still rotates the axis.
3. Input-speed cap: sweep the input from ~32 to 256 RPM — the turret's top
   speed scales with the input while the response shape stays identical.
4. Turret mode sign: with a bearing facing up, sweeping the aim right rotates the
   turret right. If inverted, set `turretAim.invert = true`.
5. Wireless hop through Ender transmitters across the physics-bearing joint.
6. **No passthrough**: power the block's input at a constant RPM and confirm the
   turret stops when on-target (the output network is isolated; the constant input
   must not keep spinning the turret).
7. Scope close / power off: turret coasts to a stop (no freeze, no jump).
8. World reload: mode and output network persist; calibrated tuning survives
   schematic paste.
9. With a gyro stabilizer linked: pitch pins to the aim direction, hull pitch
   is compensated at full speed; without one, the bore rides the hull beyond
   the drive rate (by design).
10. Free-look tether: look past ~15° off the crosshair — the camera pins (amber
    cross) and hull rotation drags it back.
