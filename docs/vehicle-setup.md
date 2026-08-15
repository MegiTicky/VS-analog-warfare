# Vehicle Setup System

Vehicle Setup records configuration actions that VMod schematics do not restore
by themselves. The actions are stored in the Vehicle Setup Block's block entity
NBT, so they travel with an ordinary VMod schematic.

## Workflow

1. Place a Vehicle Setup Block on the vehicle.
2. Use the Vehicle Setup Recorder on that block to start recording.
3. Perform the normal interaction that should be restored later:
   - Place or break blocks.
   - Link two DBW backup blocks with the normal DBW interaction.
   - Use a configured tweaked controller on a DBW controller hub.
   - Use the Trackwork toolkit's stiffness mode on a track block.
4. Use the recorder on the Vehicle Setup Block again to stop recording.
5. Save the vehicle with VMod as usual.
6. With VMod installed, the actions run automatically after placement. An
   empty-hand right-click retries the actions manually.

Sneak-use the recorder on the setup block to inspect the saved action summary
without changing the recording state.

## Supported Actions

| Action | Optional integration |
| --- | --- |
| Place or remove a block | None |
| Link DBW backup networks | Drive By Wire |
| Restore a tweaked controller linked to a hub | Drive By Wire and Create Tweaked Controllers |
| Restore Trackwork suspension stiffness | Trackwork |

Block actions record both the setup-block offset and, when applicable, the
ship-relative position. DBW links record the original ship IDs and both ship
offsets. During VMod placement, the port resolves those original IDs through
VMod's old-to-new ship map before replaying the action.

Tallyho is intentionally not part of the VS2.4 port because no compatible
VS2.4 Tallyho build is available.

## Optional Compatibility

The optional integrations are discovered at runtime and invoked through
reflection. Missing or changed optional mods disable only the affected action;
they do not make Vehicle Setup a mandatory dependency. The compatibility
mixins are in a separate non-required mixin configuration and are applied only
when their target mod is loaded.

The VS2.4 port was prepared against the local test stack:

- Valkyrien Skies 2.4.13 development build
- Create 6.0.8
- Create Big Cannons 5.10.2
- Drive By Wire 0.1.0
- Trackwork 1.2.3
- VMod 1.9.1
- Create Tweaked Controllers 1.2.5
