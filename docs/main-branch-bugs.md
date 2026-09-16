# Main Branch Bug Log

This document records known bugs in the `main` branch. The main branch is not
widely used yet, so these issues are intentionally documented for later work
instead of being fixed immediately.

## Test Context

- Branch: `main`
- Minecraft: `1.20.1`
- Valkyrien Skies: `2.4.x`
- VMod: `1.9.1`
- Drive By Wire: `0.1.0`
- Create: Ender Transmission: `2.1.1`

## Vehicle Setup and Backup Blocks

### 1. Cross-ship block placement is offset

**Observed behavior:**

Vehicle Setup block-placement actions targeting a different ship do not restore
at the recorded position. The placed block is offset by several blocks from
the expected position.

**Affected area:**

- Vehicle Setup block placement
- Actions whose target block is on a different ship from the setup block
- Ship-relative coordinate conversion during VMod placement

**Likely investigation area:**

Review the ship-relative position calculation and the conversion from recorded
ship offsets to pasted-world positions. The error appears to be a coordinate or
ship-origin mismatch rather than a missing action.

### 2. Drive By Wire backup block links do not work

**Observed behavior:**

Drive By Wire backup block links fail completely when restored through Vehicle
Setup. The backup blocks are not linked after placement.

**Current stack:**

The main branch was prepared against Drive By Wire `0.1.0`.

**Likely investigation area:**

Update the Drive By Wire compatibility integration from `0.1.0` to the latest
Drive By Wire version, then re-check the reflected API and compatibility mixin
targets. The current DBW API may have changed since the integration was written.

## Ender Transmission

### 3. Energy transmitter isolation does not run after VMod placement

**Observed behavior:**

An Ender energy transmitter recorded during Vehicle Setup is not isolated after
the vehicle is pasted with the VMod toolgun. The action reports:

```text
Ender transmitter remapping requires a VMod placement
```

The vehicle was in fact placed by a player using the VMod toolgun.

**Affected area:**

- Ender energy transmitter isolation
- VMod placement detection
- Placement ID propagation into Vehicle Setup action replay

**Likely investigation area:**

The VMod placement completion hook is not passing or retaining the placement
context when the setup action executes. Review the main-branch VMod `SchemPlacementItem`
hook, the deferred placement task, and the point where the placement ID is
passed to `EnderTransmissionCompat.configure`.

## Deferred Work

These issues are recorded only. Do not treat this document as an indication
that the fixes have been implemented. When main becomes a priority:

1. Fix cross-ship coordinate conversion and test placements across multiple
   ships.
2. Update and revalidate the Drive By Wire integration against the latest DBW.
3. Trace VMod placement completion and restore Ender transmitter placement ID
   propagation.
