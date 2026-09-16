# VS2.4 Port — Feature Test Checklist

Features ported from `vs2.3` to `main` (VS2.4.9 / Create 6.0.8 / CBC 5.10.2),
tested in the `VS2.4Temp4` instance with VMod 1.9.1, Drive By Wire 0.1.1,
Trackwork 1.2.3, and Create: Ender Transmission 2.1.1.

Tallyho is intentionally **not** ported (VS2.4 has native ship schematics);
a recorded Tallyho action should gracefully report "not supported in this
VS2.4 build" instead of crashing.

## Scope and camera

- [x] Scope link to a cannon mount: aim follows the barrel, no 180° flip
      regardless of the vehicle's assembly heading (absolute-yaw convention).
- [x] Smooth scope aim: no stutter, no flicker when the crosshair enters or
      leaves a target (blink-proof lock).
- [x] Stabilizer block on a moving/rotating ship: lock holds elevation in
      world terms, horizon levels (roll stabilization), no snap when lock
      engages or disengages, no instability near the 87° vertical pole.
- [x] Third-person view toggle (Z): orbit works, +2 lift pivot, and the
      camera does **not** clip through the player's own ship (other ships
      and terrain still block).
- [x] Freelook (X), zoom (C), rangefinder (V) — check keybinds actually
      match after the keybind rework; if the instance has stale defaults,
      reset Controls in options.
- [x] High-angle zeroing wheel: elevation segments work, no depression
      segment, traverse near vertical keeps the camera up-hint on the hull
      (never bore-projected).
- [x] HUD instruction lines reflect the actual bound keys.

## Mouse-aim turret mode

- [ ] Toggle turret mode on a mouse-aim controller: world-stable mounted
      free-look, turret chases the view.
- [ ] PID tracking feels right with the shipped defaults (kp 0.40 / kd 1.2,
      fallback 0.55 / 1.5); no oscillation, no visible lag on a real
      moment-dominated turret.
- [ ] Step-test auto-calibration: factor message sane, capped factor shown
      correctly (not the raw unclamped percentage).
- [ ] Cannon mode: rate-clamped chase with a ceiling — a fast input (e.g.
      256 RPM) must not slingshot the turret past its max output RPM.
- [ ] Restart persistence: save + quit + reload — turret output face,
      input face, and output speed survive; no persona leak while
      unpowered, no collapse or first-tick flip after load.
- [ ] Tuning values travel with a VMod paste (paste-safe NBT); Reset
      reverts to the current template.
- [ ] Decoration bearing rotation-mode switch: hold-right-click value
      settings work; rotation is smooth (no visual lag beyond the known
      frame-exact sampling limitation).

## Screwdriver and seat

- [ ] Analog screwdriver HUD: latest-3 acted list + count, x-ray wireframes
      amber for acted / red for removal, positions correct **on a ship**
      (per-frame ship transform resolution).
- [ ] Invisible seat: sitting works on ground and on ships, standing riders
      behave; note the known ~0.5 block X/Z rider offset from vs2.3 is
      expected to carry over (under investigation there).

## Vehicle Setup

- [ ] Record + replay of block place/remove actions.
- [ ] DBW backup block link recorded on one ship, restored correctly after
      a VMod paste (DBW 0.1.1 — the broken 0.1.0 backup block was the
      reason for the version bump).
- [ ] Tweaked-controller ↔ hub link restored after paste.
- [ ] Trackwork suspension stiffness restored after paste.
- [ ] Ender energy transmitter isolation per paste: unique password suffix,
      same-channel/password transmitters stay networked, screwdriver pairing
      with an external transmitter works. **Known main-branch bug: the
      isolation may not run at all after a VMod paste** — see
      `main-branch-bugs.md` §3.
- [ ] Cross-ship block placement action restores at the exact recorded
      position. **Known main-branch bug: offset by several blocks** — see
      `main-branch-bugs.md` §1.
- [ ] Empty-hand right-click on the setup block retries pending actions;
      sneak-use shows the action summary.
- [ ] VMod paste: scope links rebase to the new ship correctly; DBW scope
      controller equip/close cycle works (controller in main hand, hand
      hidden, item restored on close).

## Server / general

- [ ] Game and world load with no mixin errors (startup crash from stale
      mixin entries is fixed as of commit 4c92ba81).
- [ ] Mounted dual cannon sounds change with hull material.
- [ ] Flywheel visuals: mouse-aim output shaft spins at the turret output
      RPM and is still when idle; decoration bearing renders via the
      Create 6 bearing visual.
- [ ] Dedicated server boots (watch for client-only classes leaking).
