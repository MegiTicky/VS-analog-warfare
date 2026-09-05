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
- **Render-time pitch lock** (`renderLock` config, default on): extrapolation
  alone still shows 20 TPS stepping whenever the correction rate changes.
  So while holding, a `@ModifyReturnValue` on `getPitchOffset` re-solves the
  rendered pitch **per frame** against the ship's interpolated render
  transform (`ClientShip.getRenderTransform()` — the same slerp the hull is
  drawn with): one Newton step through the elevation/pitch Jacobian puts the
  bore exactly on the held elevation for this frame. The correction is
  low-passed (0.4/frame) and capped at ±2° from CBC's value, so the visual
  can never meaningfully diverge from the logical pitch the shot uses.
  Passes through during player input, on the server, and wherever no
  stabilizer state exists. The rendered gun is therefore exactly as smooth
  as the hull itself — no 20 TPS component at all.
  Blink-proof application (fixed 2026-09-05): the correction is *always*
  applied and glides toward its per-frame target (the solved clamp, or 0 on
  any gate failure), so a transient gate failure — the pose-dependent
  `shipManaging()` AABB query blinks during ship motion, `inputActive`
  flickers — moves the target instead of snapping the render between
  corrected and raw. The ship query is also grace-cached for 5 ticks
  (MountState.renderShipCache), the same pattern as the tick-side servo.
  This matters twice over since the scope reads the same value: the snap
  was sub-pixel on the hull but fills an 8× scope.
- **Failed experiment, reverted (commits `85546eec` → revert `437aff51`,
  2026-09-05):** an attempt to make the lock frame-exact and to feed the
  scope camera from the same locked offsets. It made everything worse in
  testing (scope oscillating between two angles, pitch control dead, gun
  sagging after bumps) and was fully reverted. Two root causes, both of
  which any re-land must fix first:
  1. **Gate blink with no damping.** The lock's per-frame gates flip during
     motion — `shipManaging()` is the pose-dependent AABB query that blinks,
     and `inputActive` flickers — and removing the low-pass exposed every
     blink as a full-size jump between the locked and unlocked angles. The
     0.4/frame low-pass in the shipped lock was what had been hiding this.
  2. **`getPitchOffset` has a second branch.** When the mount is
     seat-controlled or stalled (`!canBeTurnedByController`), CBC returns
     `contraptionLerp(pt) * sgn * modifier` — a *different* convention from
     the servo branch the solve assumes. `inputActive` goes stale exactly in
     those states (the tick mixin is not invoked, so nothing updates it), so
     the lock could stay active where it must be off, solving with a wrong
     Jacobian sign and slamming to its cap. Any re-land must hard-gate the
     lock on `canBeTurnedByController`/`isStalled` (sampled, not inferred)
     and hold the last correction through gate blinks instead of snapping
     to zero.
  Also known: the scope camera's aim path
  (`CbcCompat.getAimDirection` → `PitchOrientedContraptionEntity.applyRotation`)
  reads the raw 20 TPS entity lerp and bypasses `getPitchOffset` entirely —
  that is why the shipped lock never smoothed the scope view, and remains
  the problem any future attempt must actually solve.
- **Scope-frame fix (added 2026-09-05, `smoothScopeAim` config, default on)**:
  the shutter is only visible through the scope; the externally drawn gun is
  already smooth because it renders via `getPitchOffset(partialTicks)` (velocity
  extrapolation + the low-passed render lock). The scope rig now reads the same
  source: `FixedCoaxScopeRig` calls the new `CbcCompat.getScopeRenderFrame`,
  which resolves the bore from `getYawOffset`/`getPitchOffset` at the rendered
  partialTick (reflective reads hit the mixins, so the reticle is pixel-consistent
  with the drawn barrel), falls back to the contraption entity lerp, then holds
  the last good ship-local direction through transient failures (blink-proof,
  single-entry cache keyed by mount). The up vector is projected from the same
  resolved forward, so aim and roll come from one source. The guard
  `getContraption() != null` prevents the NORTH fallback of a disassembled mount
  from poisoning the frame. No changes to `getPitchOffset`/`applyRotation`
  semantics, the servo, or any pt≥1.0 logical path; works for all cannons, not
  just stabilized ones. Set `smoothScopeAim=false` to restore the old scope path.
  - **Observed-velocity extrapolation (follow-up, 2026-09-05):** CBC's offset
    extrapolation `lerp(pt, pitch, pitch + shaftSpeed)` goes flat — one hard
    step per tick — whenever the cannon is driven by anything but the shaft
    (mouse-aim writes, stabilizer corrections), because the shaft is idle then.
    The scope now measures each mount's actual per-tick yaw/pitch delta
    (reflective `cannonYaw`/`cannonPitch` reads, captured on the first render
    frame of each tick, wrap-safe, per-mount client cache) and extrapolates
    `getYawOffset(0) + yawVel·pt` / `getPitchOffset(0) + modifier·pitchVel·pt`.
    The pt=0 endpoints keep CBC's conventions, the render-lock correction, and
    the seat-control entity-lerp branch; the observed velocity adds the missing
    lead, so the view moves every frame for every drive path. Falls back to the
    static offsets path if the fields can't be read.
  - **g-h filtered extrapolation (final form, 2026-09-05):** raw last-tick
    deltas still kink at tick boundaries (velocity re-estimates) and land sync
    yanks at full amplitude. The scope now runs a per-mount g-h filter
    (`SCOPE_FILTER_ALPHA = 0.2`, `SCOPE_FILTER_BETA = 0.06`, tunable constants
    in `CbcCompat`): predict `pos + vel·dt`, measure the raw
    `cannonYaw`/`cannonPitch`, split the error — `pos += ALPHA·err`,
    `vel += (BETA/dt)·err` — and render `pos + vel·partialTicks`. Measurement
    jumps glide out over several ticks (the continuity the pre-stabilizer
    entity lerp had) at effectively zero lag (which that lerp lacked). The
    render-lock correction is added via
    `StabilizerController.computeRenderPitchOffset(be, 0.0f)`, which also keeps
    the lock's per-frame glide updating even when the barrel is frustum-culled
    while scoped. Comparison note: 0.4.3 (pre-stabilizer) had NO aim smoothing
    at all — its smoothness was vanilla entity interpolation's 1-tick lag; the
    filter keeps that continuity without the lag.
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
| `renderLock` | `true` | Per-frame render-time pitch lock while holding (removes 20 TPS scope stepping; visual only) |

Also in the `scope` section: `smoothScopeAim` (default `true`) — build the scope
camera frame from the extrapolated render offsets (see above) instead of the raw
20 TPS entity lerp.

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
