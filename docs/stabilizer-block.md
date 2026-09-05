# Gyro Stabilizer Block (vs2.3)

Added in `0.4.4-vs2.3`: a vertical stabilizer that holds a CBC cannon's
**world-space elevation** steady on a Valkyrien Skies ship while the player
still fully controls the gun.

## User flow

1. Place a **Gyro Stabilizer** (`vs_analog_warfare:stabilizer`) anywhere on the
   ship hull.
2. Right-click the stabilizer with the **Analog Screwdriver**, then right-click
   the **cannon mount**. The stabilizer stores a ship-relative link (survives
   schematic export like scope links). Sneak-right-click the stabilizer to
   unlink.
3. Rotate the gun as usual (pitch shaft/crank, mouse aim, scroll adjust). While
   any input is active the held elevation continuously re-captures to the
   gun's current elevation, so the stabilizer never fights you.
4. Stop rotating: the current world elevation is frozen and the servo holds it
   against ship pitch/roll. Yaw is **not** stabilized.
5. The stabilizer works through the scope: the reticle holds steady while the
   hull rocks, including during manual slews (the shaft input commands world
   elevation rate).

## How it works (why it is stable at 60 TPS)

CBC cannon pitch has **no VS physics motor** — `CannonMountBlockEntity.tick()`
(20 TPS) advances a plain float `cannonPitch += pitchSpeed * sgn` (clamped by
CBC), and VS carries the pitch contraption entity with the ship. So the
stabilizer injects a compensating speed into exactly that advance:

- Mixin `CannonMountBlockEntityMixin` — `@ModifyExpressionValue` on the pitch
  `getAngularSpeed(FF)F` call (ordinal 1; ordinal 0 is yaw) inside
  `tick()`. The value still flows through CBC's sequenced-angle-limit and
  elevation/depression clamps. Runs on server (authoritative) and client
  (smooth visuals); CBC's `clientPitchDiff` chase absorbs residuals.
- `StabilizerController.computeOffsetSpeed` — per mount, both sides:
  - **Measure**: ship tick transform (`Ship.getShipToWorld()`), ship-local aim
    direction (`CbcCompat.getAimDirection(..., applyShipTransform=false)`),
    world elevation of the bore.
  - **Feedforward**: elevation drift caused by ship rotation this tick —
    `((ω × d)·ŷ)/cos(elev)` from `Ship.getOmega()` (world frame, rad/s →
    ×0.05 per tick), falling back to a finite difference between
    `getTransform()` and `getPrevTickTransform()`. This is what makes 20 TPS
    control good enough against 60 TPS physics: the rate term is continuous
    and subtick-exact for constant rotation.
  - **Convert**: desired elevation correction → ship-space pitch via the
    Jacobian `∂elev/∂p = sgn·ŷ·(a_w × d_w)/cos(elev)` where `a` is the
    contraption pitch axis (X-facing → ship Z, else ship X) and `sgn` is CBC's
    orientation sign. Division by `sgn` returns CBC pre-sign speed units.
  - **Feedback**: `Kp·error + Ki·∫error` with dead zone, anti-windup clamp,
    and a hard `maxCompensationDegPerTick` output clamp. Gimbal-lock
    (`|Jacobian| ≈ 0`) freezes the target instead of winding up.
- VS render transform is a plain `createFromSlerp(prev, curr, partialTick)` in
  this build (no velocity extrapolation — verified in
  `DefaultClientShipTransformProvider`), so ship rotation and the injected
  constant pitch rate interpolate consistently across the same tick boundary:
  world aim is constant to second order → frame-stable in the scope.
- Client sync: `StabilizerStatePacket` (network protocol bumped to "7") sends
  `{mountPos, active, targetElevDeg}` on capture/transition plus a 20-tick
  heartbeat to players within 160 blocks; `ClientStabilizerState` mirrors it
  with a 100-tick TTL. During input the client chases locally (same rule as
  the server), so slewing looks identical on both sides.
- Anti-stutter measures (added after first playtest): input detection is
  fully local on both sides — each tick the controller predicts the pitch
  advance from last tick's base speed plus its own offset, and any deviation
  beyond 0.3° (mouse aim steps, scroll steps, CBC seat drag, BE sync
  replacing the pitch) re-captures the held elevation instantly, so the
  client never fights the server between sync packets. The client-side
  feedforward rate is low-passed (alpha 0.5) because synced ship transforms
  arrive in bursts. Large persistent errors (> `recaptureThresholdDeg`) also
  re-capture, covering slow slews and elevation-limit railing.

## Config (`common` config, `stabilizer` section)

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch |
| `proportionalGain` | `0.4` | Deg pitch per deg error per tick (keep < 1) |
| `integralGain` | `0.02` | Slow drift correction |
| `feedforwardGain` | `1.0` | 1.0 fully cancels constant ship rotation |
| `maxCompensationDegPerTick` | `4.0` | Output clamp |
| `integralLimit` | `400` | Anti-windup clamp (deg·ticks) |
| `deadZoneDeg` | `0.02` | Errors below this are ignored (no dither) |
| `recaptureThresholdDeg` | `2.0` | Errors above this are treated as external input (slow slew / mechanical rail): target re-captures instead of correcting |
| `linkRange` | `24` | Max stabilizer→mount distance (blocks) |

## Files

- `stabilizer/` — `StabilizerBlock`, `StabilizerBlockEntity` (link storage,
  NBT, sync), `StabilizerController` (control loop), `StabilizerMath`,
  `ClientStabilizerState`
- `mixin/CannonMountBlockEntityMixin` (registered in
  `vs_analog_warfare.mixins.json`)
- `network/StabilizerStatePacket`, hook-ups in `MouseAimController`
  (input notifications), `AnalogScrewdriverItem` (linking),
  `ClientForgeEvents` (client state TTL tick)

## In-game tuning checklist

1. Spawn the test ship in waves, mount a cannon, link a stabilizer.
2. Point at ~20° elevation, release input: the gun should freeze against the
   horizon through the scope (8x). Slow reticle creep = raise
   `proportionalGain` slightly or check `feedforwardGain` is 1.0.
3. Oscillation around the hold = lower `proportionalGain` toward 0.25.
4. Recoil should kick and recover quickly; long bias after recoil = raise
   `integralGain`.
5. Elevation/depression rails: the gun parks at CBC's mechanical limit, the
   target re-captures there automatically (no windup).
