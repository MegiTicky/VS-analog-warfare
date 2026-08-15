# Tallyho Entity Setup Debug Notes

## Scope

This document tracks Vehicle Setup recording and replay work for optional Tallyho
3.5.4b entities on the `vs2.3-vehicle-setup` branch. The target runtime is
JPCreate2.5 with Valkyrien Skies 2.3, Create 0.5.1.j, and CBC 5.8.2.

Worktree:
`C:\Users\lauya\AppData\Local\Temp\opencode\vsaw-vs23-build`

Test instance:
`C:\Users\lauya\curseforge\minecraft\Instances\JPCreate2.5`

## Working Entity Paths

- Hull MG and coax MG use their Tallyho factory methods.
- Gun mount, tripod mount, chin turret, CROWS turret, targeting pod,
  periscope, and remote camera use their placement items through
  `ItemStack.useOn(...)` and a Forge `FakePlayer`.
- Placement detection compares entity UUIDs before and after item use.
- The fake player's held stack is cleared in a `finally` block.
- Remote camera, turret camera, and targeting pod UUID links still require
  handheld remote rebinding after replay.

Relevant commits:

- `0954605d Record all Tallyho placed entities`
- `af2f7d1a Preserve Tallyho factory entity placement`
- `bafeb34c Replay Tallyho placements through item use`
- `b9d656c7 Fix Tallyho placement item resolution`

## Missile Requirements

- Record a directly placed `tallyho:missile` through its
  `tallyho:camera_seat` support.
- Recreate a fresh missile of the recorded `MissileId`.
- Do not restore seeker, motor, target lock, launch state, movement, or full
  entity NBT.
- A second replay must detect the existing seat and matching missile instead
  of creating a duplicate.
- Tallyho is optional. Integration must continue to use registry IDs and
  reflection without adding a mandatory dependency.

## Attempts And Results

### Generic Entity NBT Loading

The first generalized implementation used `Entity.load(...)` after placement.
This could restore unsafe runtime state and could reposition factory-created
entities. It was removed in `af2f7d1a`.

### Missile Placement Item Through FakePlayer

The missile item was resolved from `MissileRegistryEntry.getItemEntry()` and
used through the generic item replay path. This did not reliably expose the
created missile because Tallyho's item implementation has its own ship and
player checks and creates a missile plus an internal camera seat.

### Direct MissileRegistryEntry.spawn

The current uncommitted implementation calls
`MissileRegistryEntry.spawn(ServerLevel, Vec3, float)` and validates the
missile ID, server registration, camera seat, mount relationship, slot, and
idempotence. It also cleans up newly created entities when validation fails.

This implementation built and was deployed as an intermediate test jar with
SHA-256:
`5AC85B7477939614E737E61B810A37D5510765220975E8C8E74A2E232378FDF5`

It still fails in JPCreate2.5. `latest.log` contains:

```text
Warning setPosDistance too high ignoring setPos request [-2055.93594975397,108.86754827667028,-5458.762304082513]
findShip(pos=BlockPos{x=-28329985, y=128, z=12290047}): ShipData
```

Similar warnings occur at log lines 4979-4980 and 4984-4985 in the tested
log from 2026-08-15.

## Root Cause Evidence

VS2.3 `VSEntityManager` selects handlers as follows:

- Entity classes or registry paths containing `seat` use
  `DefaultShipyardEntityHandler`.
- Other entities default to `WorldEntityHandler`.

Therefore Tallyho's `camera_seat` remains in shipyard coordinates while
`MountedMissileEntity` uses world coordinates.

`WorldEntityHandler.freshEntityInShipyard(...)` immediately transforms a
new world-handled entity from shipyard coordinates to world coordinates.
`WorldEntityHandler.positionSetFromVehicle(...)` also transforms the
shipyard-space passenger position supplied by the seat into world space.

Tallyho's `MissileRegistryEntry.spawn(...)` ordering is:

1. Construct missile.
2. Set missile position.
3. Add missile to the server level.
4. Call `FlexibleSeatEntity.sitDown(...)`.
5. Set missile yaw and pitch.

`FlexibleSeatEntity.sitDown(...)` then:

1. Constructs a camera seat.
2. Places the seat at factory position plus `0.25` Y.
3. Adds the seat to the server level.
4. Calls `missile.startRiding(seat, true)`.

The missile is consequently registered before its shipyard seat exists.
On the tested Create/VS2.3 stack, the initial shipyard-to-world `setPos` is
rejected by Create's `MixinContraptionCollider` distance guard. Mounting then
mixes the world-handled missile contract with the shipyard seat contract.

The previous capture calculation also used the missile passenger's raw
position relative to the seat block center. That is not a stable slot
representation when vehicle and passenger intentionally use different VS
entity handlers. Missile placement should be keyed by the camera seat's
shipyard position.

## Planned Correction

- Capture the camera seat position as the missile slot and store its precise
  fractional offset from the support block center instead of using the
  world-handled passenger position.
- Reproduce the Tallyho factory reflectively with the camera seat created and
  registered before the missile is mounted/registered.
- Let VS's mounted-entity callback place the world-handled passenger from the
  shipyard-handled seat.
- Verify and search idempotently by camera seat position, passenger type, and
  `MissileId`.
- Keep action format version `5`; existing freshly recorded missile actions
  remain structurally compatible.

## Test Procedure

1. Remove any missile left by the failed replay.
2. Select the Vehicle Setup Block.
3. Record a freshly placed AIM-9L by right-clicking the missile with the
   recorder.
4. Remove the recorded missile and run replay.
5. Confirm one missile appears in the recorded slot, mounted to a
   `tallyho:camera_seat`, without `setPosDistance too high` warnings.
6. Run replay again and confirm no duplicate missile or seat is created.

## Follow-up Item Placement Issues

The deployed missile coordinate fix made missile replay reliable. Two separate
item-placement issues were then observed:

- Periscope yaw was wrong after replay. Tallyho's periscope items derive the
  ship-relative facing from the fake server player's block position, then
  apply VS's world-to-ship transform. The replay fake player had been placed
  directly in shipyard coordinates, so that position was transformed twice.
- Gun mounts reported successful item use but were not detected by replay.
  GunMountItem passes a shipyard-space hit position to its factory, while the
  world-handled gun mount is registered in world coordinates. Replay searched
  only around the shipyard-space position and could miss the created entity.

The follow-up correction places the fake player at the world transform of its
intended shipyard-space direction and searches for newly created item entities
around both coordinate representations. Hull MG, coax MG, and missile replay
paths remain unchanged.

## Direct Gun And Periscope Attempt

The alternate-space search did not fix gun mounts because VS/Create rejected
the Tallyho item factory's world-handled mount before the created entity could
be found. The item factory registers the mount before its shipyard-handled
camera seat, matching the earlier missile failure.

The next attempt therefore uses direct reflection for `GunMountEntity` and
`PeriscopeEntity`:

1. Capture the camera seat's shipyard-space slot for these entities.
2. Create and register the `camera_seat` first.
3. Transform the mount or periscope factory position to world coordinates.
4. Construct and register the world-handled entity at that world position.
5. Mount it to the existing seat.
6. Restore the gun item and Tallyho camera parameters without loading runtime
   entity NBT.

Periscope `BASE_YAW` is passed directly into Tallyho's `setParams(...)`, and
gun-mount `BASE_YAW` is restored reflectively before the first normal tick.
Generic placement items retain their original hit-position contract.
