# Vehicle Setup Coordinate Debug Notes

## Symptom

Some Vehicle Setup actions can be one block away when a schematic is saved in
singleplayer and pasted into a dedicated server with VMod. The issue is rare
and has primarily been observed on actions belonging to the ship that contains
the Vehicle Setup block.

## Coordinate Data

Vehicle Setup actions currently retain two position references:

- `targetOffset`: the block position relative to the Vehicle Setup block.
- `shipOffset`: the block position relative to the ship AABB minimum, together
  with the original ship ID.

For actions on other ships, the setup anchor cannot be used because VMod places
each ship independently. Those actions continue to resolve through the mapped
runtime ship and its saved `shipOffset`.

For actions on the same ship as the Vehicle Setup block, the pasted setup block
is a more stable reference than the runtime AABB minimum. The executor now
resolves these actions using:

```text
setup block position + targetOffset
```

The saved `shipOffset` remains the fallback coordinate system for cross-ship
actions and for compatibility with existing setup data.

## Evidence From Reproduction

In the reproduced dedicated-server case:

```text
anchor       = (-28641272, -52, 12290071)
targetOffset = (0, -1, -2)
```

The anchor-relative position is:

```text
(-28641272, -53, 12290069)
```

The AABB-based position was:

```text
ship AABB min = (-28641274, -54, 12290061)
shipOffset    = (2, 1, 9)
result        = (-28641272, -53, 12290070)
```

The two candidates differed by one block on Z. The direct `setBlock` call
acted exactly at the logged AABB-based target, confirming that the offset was
introduced during target resolution rather than by Minecraft moving the block.

## Runtime Behavior

When a VMod mapping is available, the executor:

1. Resolves the saved original ship ID to its pasted runtime ship.
2. Finds the runtime ship containing the setup anchor.
3. If both ships are the same, uses `anchor + targetOffset`.
4. If they are different, uses `runtime AABB minimum + shipOffset`.
5. Logs a correction when the same-ship candidates disagree.

The correction is runtime-only. It does not rewrite saved macros or change the
serialized action format.

## Diagnostic Log Lines

Diagnostic lines use the following marker:

```text
[VSAW setup-debug]
```

The most important line for this issue is:

```text
Same-ship target correction
```

It includes the original ship ID, runtime ship ID, AABB candidate, anchor
candidate, and the correction delta.

## Verification Checklist

- Test a schematic saved in singleplayer and pasted into a dedicated server.
- Include actions on the setup ship and at least one other ship.
- Check that same-ship actions use the anchor candidate.
- Check that cross-ship actions retain their independent ship positions.
- Test direct placements, removals, and generic interactions.
- Repeat after waiting several seconds after the paste completes.
- Confirm that no saved action data is modified.

## Deliberately Not Changed

The implementation does not replace the saved AABB-relative coordinates with
Valkyrien Skies transform coordinates. A transform-based migration would need
to confirm the exact shipyard coordinate convention used by VMod and would
require a versioned action format for safe compatibility with existing setups.
