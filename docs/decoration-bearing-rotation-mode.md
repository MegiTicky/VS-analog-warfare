# Decoration Bearing Rotation Mode Switch

Planned for `0.4.4-vs2.3+`: a wrench value-settings UI on the decoration
bearing that lets the player switch between three rotation modes — **Yaw
Only**, **Pitch Only**, and **Yaw + Pitch**. The implementation registers
Create's `ScrollOptionBehaviour` on the block entity, mirroring the
Mechanical Bearing's movement-mode UI.

> Revision note: an earlier draft of this document assumed (a) a plain enum
> works as the `ScrollOptionBehaviour` type parameter, (b) free mouse-wheel
> scrolling while holding a wrench cycles the mode, and (c) the pose is
> computed in the block entity's `tick()`. All three are wrong for Create
> 0.5.1.j; this version reflects the verified API and code paths.

## User flow

1. Place a **Decoration Bearing** and link it to a cannon mount with the Analog
   Screwdriver.
2. Right-click to assemble the decoration.
3. Hold a **wrench** (`forge:tools/wrench` tag — Create wrench and
   compatible). The rotation-mode value box appears on the bearing with
   Create's "hold to edit" tip.
4. **Hold right-click** on the value box for a few ticks — Create's
   `ValueSettingsScreen` opens (this is Create 0.5.1's value-settings
   interaction; `ScrollValueHandler` is cosmetic-only and there is no
   free-scroll path).
5. **Scroll or drag** inside the screen to select the mode, then release/close
   to save:
   - **Yaw + Pitch** (default) — decoration tracks both cannon yaw and pitch.
   - **Yaw Only** — decoration tracks only horizontal rotation; pitch is
     frozen at whatever value it had when the mode became active.
   - **Pitch Only** — decoration tracks only vertical rotation; yaw is frozen
     at its current value.
6. The mode persists across save/load and disassemble/reassemble (NBT key
   `"ScrollValue"`, written through the behaviour framework). Switching modes
   never snaps the decoration — the frozen axis keeps its last value.
7. Wrench right-clicks that miss the value box do nothing (no accidental
   assemble/disassemble). Without a wrench, right-click still
   assembles/disassembles normally.

## Architecture

### How Create 0.5.1's value-settings UI actually works (verified)

- `ScrollOptionBehaviour<E>` is declared
  `E extends Enum<E> & INamedIconOptions` (confirmed via `javap` on the
  0.5.1.j jar — the CFR-decompiled tree drops this bound). The enum MUST
  implement `INamedIconOptions` (`getIcon(): AllIcons`,
  `getTranslationKey(): String`), exactly like Create's own
  `IControlContraption.RotationMode`.
- The behaviour persists an integer index under NBT key `"ScrollValue"` via
  `SmartBlockEntity.write/read`, which every Create BE reaches by calling
  `super.write/super.read`. `sendData()` on value change syncs it to clients.
- Interaction is handled upstream of `Block.use()` by
  `ValueSettingsInputHandler` (a `RightClickBlock` event subscriber): with the
  behaviour's `testHit` on the value box (and the wrench in hand when
  `requiresWrench()` is set), it cancels the click and starts the hold-to-edit
  interaction; after ~5 held ticks the `ValueSettingsScreen` opens, and its
  `mouseScrolled`/drag picks the row. The server receives a
  `ValueSettingsPacket` and calls `setValue()`, which triggers `sendData()`.
- `onShortInteract` returns `void` and is only invoked for FakePlayers or
  behaviours that don't accept value settings — it is NOT an integration
  point for real players and must not be called from `Block.use()`.

### VSAW implementation

#### `decorationbearing/DecorationRotationMode.java` (NEW)

```java
public enum DecorationRotationMode implements INamedIconOptions {
    YAW_ONLY(AllIcons.I_TOOL_ROTATE),
    PITCH_ONLY(AllIcons.I_TOOL_MOVE_Y),
    YAW_AND_PITCH(AllIcons.I_FOLLOW_DIAGONAL);
    // getIcon() + getTranslationKey(), mirroring IControlContraption.RotationMode
}
```

#### `DecorationBearingBlockEntity.java`

1. Field `private ScrollOptionBehaviour<DecorationRotationMode> rotationMode;`
2. The previously empty `addBehaviours()` registers the behaviour:

   ```java
   rotationMode = new ScrollOptionBehaviour<>(DecorationRotationMode.class,
           Component.literal("Rotation Mode"), this, new CenteredSideValueBoxTransform());
   rotationMode.requiresWrench();
   ```

   `requiresWrench()` makes both the label and the interaction wrench-gated.
   No manual NBT: `super.write/read` already propagate to behaviours.
3. `getRotationMode()` exposes the current mode (defaults to YAW_AND_PITCH if
   the behaviour isn't registered yet).
4. `assemble()` filters the initial pose before `setDecorationRotation(...)`:
   YAW_ONLY starts at pitch 0, PITCH_ONLY starts at yaw `-initialYaw` (identity
   orientation). The contraption tick holds the frozen axis from there.

#### `DecorationBearingContraptionEntity.java`

The pose is NOT computed in the block entity — `tickContraption()` copies the
linked CBC cannon's pose each tick (`yaw = pose.viewYaw(); pitch =
pose.viewPitch();`). Both the server branch and the client's zero-lag
live-CBC-read branch now route through:

```java
private void applyRotationModeFilter(float newYaw, float newPitch) {
    DecorationRotationMode mode = resolveRotationMode(); // from controller BE
    if (mode == PITCH_ONLY)   pitch = newPitch;
    else if (mode == YAW_ONLY) yaw = newYaw;
    else                      { yaw = newYaw; pitch = newPitch; }
}
```

The frozen axis simply keeps its existing field value, so "frozen at its
current value" falls out naturally with no extra state and no snap on mode
switches. No new NBT is required on the contraption entity.

#### `DecorationBearingBlock.java`

`use()` currently consumes every MAIN_HAND click (assemble/disassemble). Two
adjustments:

- The screwdriver branch stays first (link selection).
- **Wrench clicks return `PASS`** before the assemble/disassemble logic:
  Create's `ValueSettingsInputHandler` has already handled (canceled) clicks
  that hit the value box, so a wrench click reaching `use()` missed the box
  and must not disassemble the decoration.
  `com.simibubi.create.AllTags.AllItemTags.WRENCH.matches(stack)` is the same
  tag check Create itself uses.

Note: there is no `onShortInteract` delegation — it returns `void`, and the
upstream event handler makes it unreachable for real players.

### Files touched

| File | Action |
|---|---|
| `decorationbearing/DecorationRotationMode.java` | **NEW** |
| `decorationbearing/DecorationBearingBlockEntity.java` | behaviour registration + `getRotationMode()` + assemble filter |
| `decorationbearing/DecorationBearingContraptionEntity.java` | `applyRotationModeFilter()` in both pose-copy branches |
| `decorationbearing/DecorationBearingBlock.java` | wrench-click PASS gate |

### Testing

1. Place a decoration bearing, link it, right-click to assemble.
2. Hold a wrench and look at the bearing — the "Rotation Mode" value box
   appears with the hold-to-edit tip.
3. Hold right-click on the box; in the value screen, scroll/drag between
   Yaw Only / Pitch Only / Yaw + Pitch and release to save.
4. Verify each mode:
   - **Yaw + Pitch**: decoration tracks both axes (existing behavior).
   - **Yaw Only**: decoration only rotates horizontally; pitch stays fixed at
     the value it had when the mode was applied.
   - **Pitch Only**: decoration only rotates vertically; yaw stays fixed.
   - Switching modes must not snap the decoration.
5. Disassemble and reassemble — mode should persist (and the initial pose
   respects the mode).
6. Save and reload — mode should persist.
7. Without a wrench, right-click should still assemble/disassemble normally;
   with a wrench, a right-click that misses the value box should do nothing.

### Future enhancements (out of scope)

- Localized mode names (add lang entries for
  `vsanalogwarfare.decoration_bearing.rotation_mode.*`).
- Sound effect on mode switch.
- Per-mode speed limits or easing.
