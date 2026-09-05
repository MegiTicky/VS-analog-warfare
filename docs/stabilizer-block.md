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
  - **Position servo (deadbeat)**: the offset commands the *exact* relative
    pitch that places the bore at the held elevation —
    `offset = clamp((target − elev) / J / sgn, ±maxCompensationDegPerTick)`.
    One Newton step converges within the slew limit; no integral, no windup,
    no drift-rate estimation. Because the command is a position, not a rate,
    the hold cannot degrade: a gun railed at a mechanical limit keeps its
    original target and returns the moment geometry allows. (An earlier
    feedforward + PI + error-recapture design adopted drift whenever its
    commanded advance got clipped — the velocity-servo lesson that motivated
    this rewrite.)
  - **Convert**: elevation error → ship-space pitch via the Jacobian
    `∂elev/∂p = sgn·ŷ·(a_w × d_w)/cos(elev)` where `a` is the contraption
    pitch axis (X-facing → ship Z, else ship X) and `sgn` is CBC's orientation
    sign. Division by `sgn` returns CBC pre-sign speed units. Gimbal lock
    (`|Jacobian| ≈ 0`) holds the output and keeps the target.
- Frame smoothness: the gun only ever moves in 20 TPS steps (stock CBC
  included); smoothness comes from per-frame extrapolation. A second
  `@ModifyExpressionValue` in the same mixin feeds the tick's offset into
  `getPitchOffset(partialTicks)` (CBC's render extrapolation otherwise
  re-derives the speed from the shaft alone and under-projects every
  stabilizer step — a 20 Hz snap in the zoomed scope). VS render transform is
  a plain `createFromSlerp(prev, curr, partialTick)` in this build, so ship
  rotation and the constant pitch step interpolate consistently.
- Client sync: `StabilizerStatePacket` (network protocol bumped to "7") sends
  `{mountPos, active, targetElevDeg}` on capture/transition plus a 20-tick
  heartbeat to players within 160 blocks; `ClientStabilizerState` mirrors it
  with a 100-tick TTL. The client runs the same servo with the synced target,
  so slewing looks identical on both sides.
- Input detection (fully local on both sides, no packet lag): each tick the
  controller predicts the pitch advance from last tick's base speed plus its
  own offset, folded through CBC's `% 360` wrap and
  `maximumDepression()/maximumElevation()` clamp exactly like CBC's tick does
  (limits read by reflection from `mountedContraption`, per-concrete-class
  caches). A clamp-eaten advance — the gun railed — predicts to zero delta
  and is **not** external input. A persistent mismatch (> 0.3° for 2
  consecutive ticks, debounce absorbs recoil kicks) re-captures the held
  elevation. If the advance loop was suspended > 2 ticks (seat gunner control
  or stall — the mixin is not invoked then), the target re-captures once on
  resume instead of fighting whoever drove the gun.

- Transient resilience (added after the fast-bump playtest: a violent bump
  could kill the hold permanently):
  - **Ship-lookup grace**: the position-vs-AABB ship query blinks during
    violent motion; the controller reuses the last known ship for up to 5
    ticks so the servo rides through instead of dropping (a dropped servo
    lets the gun bounce free).
  - **Validation grace + fallback**: `validateLink` only unlinks after 3
    consecutive failed validations (60 ticks), and `resolveMountPos` falls
    back to the link-time world position when the ship-relative resolve
    misses. Previously one failed resolve (ship AABB is pose-dependent and
    transiently null) permanently unlinked the stabilizer — the fast-bump
    "stops working and settles wrong" bug.
  - **Re-registration on load**: the link lives in NBT; the controller
    registry does not. On tick, a fresh BE instance re-registers itself, so
    chunk unload/reload and world restarts no longer silently disable the
    servo.
  - **Durable target anchor**: the held elevation is mirrored into the
    stabilizer BE (persisted in NBT, fresh for 6000 ticks) and restored on
    relink/reload instead of recapturing — drift-free across restarts.
  - **Stall vs seat gunner**: the stabilizer BE samples the mount's
    `isStalled()` / seat control each tick; a >2-tick advance-loop gap only
    re-captures the target when a seat gunner was driving. A physics stall
    moves nothing, so the original target survives it.
  - **Client sync-yank immunity**: on the client, a nonzero `clientPitchDiff`
    (a block-entity sync yank, CBC's own correction) resets the input
    detector instead of masquerading as player input.

## Config (`common` config, `stabilizer` section)

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch |
| `maxCompensationDegPerTick` | `4.0` | Output clamp and slew rate for large corrections (80°/s at 4.0) |
| `deadZoneDeg` | `0.02` | Errors below this are ignored (no dither) |
| `linkRange` | `24` | Max stabilizer→mount distance (blocks) |
| `debug` | `false` | Log servo state (elev/target/offset/input/ext/railed/suspend) once per second per linked mount to the server log |

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
   horizon through the scope (8x). Any residual dither = the `deadZoneDeg` is
   fine; check `debug` log lines show `input=false` while holding.
3. Climb a hill until the gun rails at its depression limit, then level out:
   the gun must return to the held elevation. If it adopts the drifted
   elevation, check the debug line for `railed=true` while holding — the
   clamp-aware prediction should keep `ext=false` there.
4. Recoil kicks and recovers within a tick or two (the 2-tick input debounce
   deliberately ignores single-tick recoil steps).
5. Post-release correction slew speed = `maxCompensationDegPerTick`
   (4.0 ≈ 80°/s); lower for a heavier feel.
6. Seat gunner aiming: while a seat operator drives the gun the stabilizer is
   transparent; on release the held elevation re-captures (suspension
   detection), it must not snap back to the pre-seat target.
7. `debug=true` in the config prints one server-log line per second per mount:
   `elev/target/offset/input/ext/railed/suspend` for diagnosing any report.
