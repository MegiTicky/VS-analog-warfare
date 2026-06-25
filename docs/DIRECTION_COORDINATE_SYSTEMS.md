# Direction & Coordinate Systems

This document explains how direction vectors, coordinate transformations, and raycasts work in VS Analog Warfare, particularly when interacting with Valkyrien Skies (VS2) ships and Create Big Cannons (CBC) contraptions.

## Coordinate Spaces

### 1. World Space
- **Origin**: Minecraft world origin (0, 0, 0)
- **Axes**: Standard Minecraft axes (X=east/west, Y=up/down, Z=north/south)
- **Use**: Final coordinate space for raycasts, rendering, and most game logic

### 2. Ship-Local Space
- **Origin**: Ship's center/anchor point
- **Axes**: Rotated relative to world axes based on ship's orientation
- **Use**: Positions and directions relative to a VS2 ship

### 3. Contraption-Local Space (CBC)
- **Origin**: Cannon mount block position
- **Axes**: Rotated based on contraption's current yaw/pitch angles
- **Use**: Cannon aim direction relative to the mount's orientation

---

## Key Components

### VsCompat (`scope/compat/VsCompat.java`)

Handles coordinate transformations between ship-local and world space.

| Method | Input Space | Output Space | Notes |
|--------|-------------|--------------|-------|
| `shipToWorldPosition()` | Ship-local | World | Transforms position vectors |
| `shipToWorldDirection()` | Ship-local | World | Transforms direction vectors (uses `isPlayerMountedToShip()` check) |
| `shipToWorldDirectionForRaycast()` | Ship-local | World | Always transforms, bypasses seated check |
| `worldToShipDirection()` | World | Ship-local | Reverse transformation |
| `isPlayerMountedToShip()` | - | boolean | Returns true if player entity is mounted to a VS2 ship |

#### The `isPlayerMountedToShip()` Check

When a player is seated on a ship, VS2's camera mounting system handles coordinate transformations internally. This means:

- **Camera rendering**: Yaw/pitch are set directly via `ViewportEvent.ComputeCameraAngles`, VS2 interprets them correctly
- **Direction queries**: When seated, some operations should NOT transform coordinates because VS2 already handles it

The `shipToWorldDirection()` method has a special check:
```java
public static Vec3 shipToWorldDirection(Level level, BlockPos anchorPos, Vec3 localDirection) {
    if (isPlayerMountedToShip()) {
        return localDirection.normalize();  // Skip transform - VS2 handles it
    }
    // ... apply ship-to-world matrix transformation
}
```

This was added to fix a camera offset bug when the player is seated on a rotating ship.

### CbcCompat (`scope/compat/CbcCompat.java`)

Queries cannon aim direction from Create Big Cannons contraptions.

| Method | Returns | Space | Notes |
|--------|---------|-------|-------|
| `getAimDirection(level, mountPos, fallback, partialTicks, applyShipTransform)` | Vec3 | Depends on flag | Gets cannon's current aim direction |
| `getAimUpDirection()` | Vec3 | World | Gets cannon's up vector |

#### The `applyShipTransform` Flag

```java
// Returns ship-local direction
getAimDirection(level, mountPos, fallback, partialTicks, false)

// Returns world-space direction (applies VsCompat.shipToWorldDirection())
getAimDirection(level, mountPos, fallback, partialTicks, true)  // default
```

**Internal flow:**
1. Try `getContraption().applyRotation()` - uses CBC's contraption rotation
2. Fallback to `getYawOffset()` + `getPitchOffset()` - uses mount's yaw/pitch offsets
3. Result is in contraption-local space
4. If `applyShipTransform=true`, additionally applies `VsCompat.shipToWorldDirection()`

---

## Direction Pipelines

### 1. Camera Pose Construction (`FixedCoaxScopeRig.getCameraPose()`)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         CAMERA POSE PIPELINE                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. Camera Position                                                         │
│     localCameraPos = findFirstAirViewPosition(facing)                      │
│                     └── Ship-local position of camera                      │
│                                                                             │
│     position = VsCompat.shipToWorldPosition(level, scopePos, localCameraPos)│
│                └── World-space camera position                             │
│                                                                             │
│  2. Aim Direction                                                           │
│     direction = CbcCompat.getAimDirection(level, mountPos, ..., true)      │
│                 └── Contraption-local ──→ shipToWorldDirection() ──→ World │
│                 └── World-space cannon aim direction                       │
│                                                                             │
│  3. Up Vector                                                               │
│     up = CbcCompat.getAimUpDirection(...)                                  │
│          └── World-space up vector (preserves ship roll)                   │
│                                                                             │
│  4. CameraPose Creation                                                     │
│     return CameraPose.looking(position, direction, up)                     │
│            └── Extracts yaw/pitch from world-space direction               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Result**: `cachedSightPose` contains world-space position, yaw, pitch, and quaternion.

### 2. FreeLook Mode (`ClientScopeState.ensureCached()`)

When freelook is enabled, the camera can look independently from the cannon.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         FREELOOK PIPELINE                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Initialization (toggleFreeLook):                                          │
│     freeLookYaw = cachedSightPose.yaw()    ──→ World-space yaw             │
│     freeLookPitch = cachedSightPose.pitch() ──→ World-space pitch          │
│                                                                             │
│  Mouse Input (addFreeLookInput):                                           │
│     freeLookYaw += deltaYaw                                                │
│     freeLookPitch += deltaPitch                                            │
│     └── Accumulates angle deltas                                           │
│                                                                             │
│  Pose Construction:                                                         │
│     direction = directionFromYawPitch(freeLookYaw, freeLookPitch)          │
│                 └── Converts yaw/pitch ──→ direction vector                │
│                                                                             │
│     ┌─────────────────────────────────────────────────────────────────┐    │
│     │ CRITICAL: This direction is in the SAME space as freeLookYaw/Pitch│   │
│     │                                                                   │    │
│     │ When NOT seated: World-space (correct for raycast)               │    │
│     │ When seated:     Ship-local (needs transform for raycast)        │    │
│     └─────────────────────────────────────────────────────────────────┘    │
│                                                                             │
│     cachedCameraPose = CameraPose.looking(                                 │
│         cachedSightPose.position(),  ──→ World-space position              │
│         direction,                    ──→ Freelook direction               │
│         cachedSightPose.up()          ──→ Ship's up (preserves roll)       │
│     )                                                                       │
│                                                                             │
│  Camera Rendering:                                                          │
│     event.setYaw(cachedCameraPose.yaw())                                   │
│     event.setPitch(cachedCameraPose.pitch())                               │
│     └── When seated, VS2 interprets these in ship-local context            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3. Rangefinder Raycast (`ClientScopeState.triggerRangefinder()`)

The rangefinder performs a raycast to determine distance to target.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      RANGEFINDER RAYCAST PIPELINE                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Camera Position:                                                           │
│     cameraPos = cameraPose(1.0f).position()                                │
│                └── World-space position (always correct)                   │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │                    DIRECTION SELECTION                                │ │
│  ├───────────────────────────────────────────────────────────────────────┤ │
│  │                                                                       │ │
│  │  CASE A: FreeLook OFF                                                 │ │
│  │     localDirection = CbcCompat.getAimDirection(..., false)           │ │
│  │                       └── Ship-local cannon aim direction            │ │
│  │     direction = VsCompat.shipToWorldDirectionForRaycast(...)         │ │
│  │                  └── Transform ship-local ──→ world                  │ │
│  │     └── Result: World-space cannon aim direction                     │ │
│  │                                                                       │ │
│  │  CASE B: FreeLook ON, NOT Seated                                      │ │
│  │     direction = freeLookDirection()                                   │ │
│  │                  └── directionFromYawPitch(freeLookYaw, freeLookPitch)│ │
│  │     └── Already world-space, no transform needed                     │ │
│  │                                                                       │ │
│  │  CASE C: FreeLook ON, Seated on Ship                                  │ │
│  │     freelookDir = freeLookDirection()                                 │ │
│  │                   └── Ship-local direction (VS2 handles rendering)   │ │
│  │     direction = VsCompat.shipToWorldDirectionForRaycast(...)         │ │
│  │                  └── Transform ship-local ──→ world                  │ │
│  │     └── Result: World-space freelook direction                       │ │
│  │                                                                       │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│  Raycast Execution:                                                         │
│     start = cameraPos + direction * offset                                 │
│     end = cameraPos + direction * maxRange                                 │
│     hitResult = level.clip(ClipContext(start, end, ...))                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Why Different Methods Exist

### `shipToWorldDirection()` vs `shipToWorldDirectionForRaycast()`

| Method | Seated Check | Use Case |
|--------|--------------|----------|
| `shipToWorldDirection()` | YES (bypasses transform) | Camera rendering, general queries |
| `shipToWorldDirectionForRaycast()` | NO (always transforms) | Raycasts, aim calculations |

**Reason for split:**

When player is seated on a ship:
- VS2's camera system interprets yaw/pitch in ship-local context
- Setting camera angles directly works correctly
- But raycasts need world-space coordinates

The seated check in `shipToWorldDirection()` was added to fix a camera offset bug. However, raycasts must always use world-space, hence `shipToWorldDirectionForRaycast()`.

### `getAimDirection(..., applyShipTransform)` Flag

| Flag Value | Returns | Use Case |
|------------|---------|----------|
| `true` (default) | World-space | Camera pose construction, display |
| `false` | Ship-local | Explicit transformation needed (e.g., rangefinder) |

**Reason:**

The rangefinder needs to apply `shipToWorldDirectionForRaycast()` explicitly to ensure correct world-space transformation, bypassing any seated checks.

---

## Common Scenarios

### Scenario 1: Player on Ground, Looking Through Scope

```
Cannon Aim:    Contraption-local ──→ shipToWorldDirection() ──→ World-space
Camera View:   Uses cannon aim direction
Raycast:       Uses cannon aim direction (world-space)
```

No special handling needed - no ship involved.

### Scenario 2: Cannon on Ship, Player NOT Seated

```
Cannon Aim:    Contraption-local ──→ shipToWorldDirection() ──→ World-space
               (shipToWorldDirection applies transformation)
Camera View:   Uses transformed cannon aim
Raycast:       Uses transformed cannon aim
```

Ship transformation applied normally via `shipToWorldDirection()`.

### Scenario 3: Cannon on Ship, Player Seated (FreeLook OFF)

```
Cannon Aim:    Contraption-local ──→ shipToWorldDirectionForRaycast() ──→ World-space
               (bypasses seated check for raycast)
Camera View:   Uses cannon aim (VS2 handles rendering)
Raycast:       Uses shipToWorldDirectionForRaycast() to get world-space
```

Seated check bypassed for raycast to ensure correct world-space direction.

### Scenario 4: Cannon on Ship, Player Seated (FreeLook ON)

```
Freelook Dir:  Ship-local (accumulated from mouse input)
Camera View:   Uses freelook yaw/pitch directly (VS2 interprets in ship context)
Raycast:       shipToWorldDirectionForRaycast(freelookDir) ──→ World-space
               (transform ship-local freelook to world)
```

Freelook direction is ship-local when seated, must be transformed for raycast.

---

## Key Lessons

1. **Rendering uses different rules than raycasts** when player is seated on a ship
2. **VS2 handles coordinate interpretation for camera angles** when player is mounted
3. **Raycasts always need world-space coordinates** regardless of player state
4. **The `isPlayerMountedToShip()` check is for rendering, not for calculations**
5. **Freelook yaw/pitch are in the same space as the camera rendering context**

---

## Debugging Tips

Check logs for `[VSAW_SCOPE]` prefix:
- `getAimDirection` logs show direction before/after transformation
- `shipToWorldDirection` logs show local→world transformation
- `isPlayerMountedToShip` affects whether transformation is skipped

When debugging direction issues:
1. Check `isPlayerMountedToShip()` state
2. Check which `shipToWorldDirection*` method is being used
3. Check `applyShipTransform` flag in `getAimDirection` calls
4. Verify the final direction matches visual expectations

---

## Code Locations

| File | Purpose |
|------|---------|
| `scope/compat/VsCompat.java` | Ship coordinate transformations |
| `scope/compat/CbcCompat.java` | Cannon aim direction queries |
| `scope/rig/FixedCoaxScopeRig.java` | Camera pose construction |
| `client/ClientScopeState.java` | State management, freelook, rangefinder |
| `client/ClientForgeEvents.java` | Camera angle injection (`ComputeCameraAngles`) |
| `scope/rig/CameraPose.java` | Pose data structure, yaw/pitch extraction |