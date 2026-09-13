# Mouse Aim Block — Turret Mode

Turret mode turns the Mouse Aim Controller from a *barrel* servo into a *turret* servo:
the mouse keeps controlling the gun's elevation, but horizontal tracking is done by
physically rotating the whole turret structure with a Clockwork physics bearing,
driven by a PID-controlled Create rotation output on the block.

## Modes (hold right-click value settings, wrench required)

| Setting | Values | Meaning |
|---|---|---|
| Aim mode | Cannon / Turret | Cannon: mouse aim slews barrel yaw + pitch (classic). Turret: mouse aim slews barrel pitch only; yaw becomes a rotation output. |
| Output strength | Gentle / Firm / Aggressive / Brutal | Scales the yaw PID gains together with the max output RPM (8/16/24/32). |

Both values persist in the block NBT (Create's `ScrollValue`).

## Turret mode wiring

The rotation output leaves the block **on the arrow-marked face only** — the face the
arrow texture points to. The arrow follows the block's placed axis (east for
`axis=x`, south for `axis=z`, up for `axis=y`), and the output is a separate kinetic
network from the block's power input and from the cannon mount — it cannot leak into
either.

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
- The block still needs its normal horizontal power (≥ `mouseAimMinSpeed` RPM) to
  be active, in both modes.

## Control loop

Per server tick (`TurretYawController`):

- **Setpoint** — aim direction from the scope free-look packet. When the player was
  mounted, the packet is flagged and the direction is converted to world space with
  the scope ship's tick transform; otherwise it already is world-space.
- **Measurement** — current bore direction: CBC mount yaw/pitch read from the mount,
  rotated into ship-local space (`CbcCompat.getAimDirection`), then into world space
  with the **turret** ship's tick transform.
- **Error** — wrapped world azimuth difference (`atan2(-x, z)` convention).
- **Output** — `RPM = -strength × (kp·err + kd·d(err)/dt + kff·d(aim)/dt)`, clamped
  to the strength's max RPM, slew-limited per tick, zeroed under the deadband.
  The sign assumes an up-facing bearing (positive RPM = counter-clockwise seen from
  above = decreasing Minecraft azimuth); `turretAim.invert` flips it if needed.
- **Ramp-down** — when the scope closes or the target times out, the output ramps
  to zero at the slew rate instead of freezing.

## Config (`turretAim`)

| Key | Default | Meaning |
|---|---|---|
| `kp` | 0.3 | RPM per degree of aim error (before strength scaling) |
| `kd` | 0.6 | damping RPM per deg/tick of error change |
| `feedForward` | 0.8 | RPM per deg/tick of aim sweep |
| `deadbandDeg` | 0.05 | errors below this command nothing |
| `outputSlewRpmPerTick` | 2.0 | max output change per tick (also ramp-down rate) |
| `stressCapacity` | 4096 | stress capacity provided on the output network |
| `invert` | false | flip output sign (bearing placed/spinning differently) |
| `debug` | false | log setpoint/measurement/error/rpm once per second |

## Implementation notes

- The output face is a second kinetic persona on the same block position
  (`MouseAimOutputInterface`, the CBC `HasMultipleKineticInterfaces` trick): the
  main entity keeps the horizontal power connections, the persona is bound to the
  arrow-marked face via a synthetic `output=true` block state that never occurs in
  the world. CBC's mixins on Create's `RotationPropagator` (plain `instanceof`
  checks) do the routing; we only ship a compile-only stub.
- The sub-BE is a `GeneratingKineticBlockEntity`; `getGeneratedSpeed()` returns the
  PID output and `updateGeneratedRotation()` is called only when the command changes
  by more than 0.01 RPM to avoid network churn.
- Network isolation: both personas sit at the same `BlockPos`, and Create keys
  kinetic networks by `KineticBlockEntity.createNetworkId()` which defaults to the
  block position. Without an override the output would merge into the main block's
  power network and let the constant input RPM reach the turret. The persona
  overrides `createNetworkId()` with a position-derived-but-distinct id so the
  generated output forms its own isolated network.
- Placement: inherits `RotatedPillarKineticBlock`'s Create-standard
  `getStateForPlacement` (aligns to an adjacent shaft axis, else the player's look /
  horizontal axis), so it orients like every other Create pillar block. The arrowed
  output face is derived from the placed axis (`MouseAimBlock.getOutputFace`), kept
  consistent with the blockstate's `axis` → model-rotation mapping (verified against
  vanilla `BlockModelRotation` + JOML `rotateYXZ`: `axis=x` → east, `axis=z` →
  south, `axis=y` → up).
- Limitation: aiming while mounted **on the turret itself** is unsupported for yaw
  (the setpoint frame would rotate with the turret). Pitch still works.

## Test checklist (first in-game session)

1. Cannon mode regression: aim + zeroing scroll behave exactly as before.
2. Wrench UI: hold a Create Wrench and right-click / scroll over the E/W faces to
   switch Cannon ↔ Turret mode and the Z faces for the output strength value box.
3. Placement: place against a shaft to auto-orient; the arrow ends up on the east
   (`axis=x`), south (`axis=z`) or up (`axis=y`) face and matches the placed axis.
4. Turret mode sign: with a bearing facing up, sweeping the aim right rotates the
   turret right. If inverted, set `turretAim.invert = true`.
5. Strength levels 1–4: chase speed and oscillation feel.
6. Wireless hop through Ender transmitters across the physics-bearing joint.
7. **No passthrough**: power the block's input at a constant RPM and confirm the
   turret stops when on-target (the output network is isolated; the constant input
   must not keep spinning the turret).
8. Scope close / power off: turret coasts to a stop (no freeze, no jump).
9. World reload: mode, strength and output network persist.
