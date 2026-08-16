# Vehicle Setup System

Vehicle Setup records ordinary block placements and removals that must be
replayed after a VMod schematic is placed. Its actions are stored in the
Vehicle Setup Block's block-entity NBT, which is included in the schematic.

## Recording a diesel engine activation

1. Build the vehicle with its diesel engine disabled.
2. Place a Vehicle Setup Block on the vehicle.
3. Right-click that block with a Vehicle Setup Recorder to start a new
   recording. Starting a recording clears its previous actions.
4. Place the engine shaft normally with its usual item. The recorder saves the
   resulting ordinary block placement; it has no diesel-engine-specific logic.
5. Right-click the same setup block with the recorder to stop recording. The
   action-bar message reports the saved action count.
6. Break the shaft after recording has stopped, leaving no rotating Create
   components in the vehicle.
7. Save and place the VMod schematic while the engine is disabled.
8. Empty-hand right-click the Vehicle Setup Block after placement to replay the
   recorded actions, restore DBW links, activate the engine, and receive any
   linked controller items.

While recording, all normal block placements and breaks by the recording
player are saved relative to the setup block. A placement followed by a break
is therefore replayed in that same order. Stop recording before removing a
temporary activation shaft when that removal must not be saved.

When a recorded block lies on a loaded ship, its placement/removal is saved
per-ship (ship ID + ship-relative offset) exactly like DBW and controller
links, so it replays correctly even when a multi-ship schematic is placed by
clicking a ship other than the setup block's ship. Blocks not on any ship fall
back to the setup-block-relative offset.

Sneak-right-click the setup block with the recorder to inspect its typed saved
action summary without changing the recording state.

While recording, use the normal DBW cable interaction to relink the two backup
blocks. The successful server-side DBW relink is captured automatically. Use
the normal Trackwork Toolkit stiffness mode on a suspension track block; the
resulting stiffness is captured automatically after Trackwork applies it.

While recording, use each configured controller item on its DBW controller hub
normally. The controller item is saved after DBW writes its `Hub` tag. VMod
changes the ship's world position, so the setup block uses the saved ship and
hub offset to rewrite `Hub` for the newly placed vehicle before giving the
controller to the player. Multiple controller/hub pairs are supported.

The recorder does not replace either mod's normal tool interaction. The action
bar confirms DBW and Trackwork captures, and inspecting the setup block shows
the typed action summary before saving the schematic.

## Create Ender Transmission

While recording, configure each vehicle-mounted Create Ender Transmission energy
transmitter through its normal GUI. Closing the GUI records that transmitter as
part of the vehicle setup. When VMod pastes the schematic, VSAW assigns recorded
transmitters a per-placement suffix while preserving the first 16 characters of
the configured password. Transmitters with the same original channel and password
remain connected inside one pasted vehicle; copied vehicles receive different
network identities.

Unrecorded transmitters are unchanged, so intentional shared/base networks remain
possible. To connect an external engine to a pasted vehicle, sneak-right-click the
vehicle transmitter with the analog screwdriver, then right-click the external
transmitter. This copies the vehicle's generated channel and password to the
external transmitter.

## Validated dependency versions

Vehicle Setup integrates with the following mods by their exact runtime API.
The version listed was the one validated against this build of the mod:

| Mod | Mod ID | Validated version |
| --- | --- | --- |
| Drive By Wire | `drivebywire` | `0.0.6b` |
| Trackwork Plus | `trackwork` | `1.0.2c` |
| Create Ender Transmission | `createendertransmission` | `2.0.7-1.20.1` |
| Valkyrien Mod (schematics) | `valkyrien_mod` | `0.1.3` |
| Create Tweaked Controllers | `create_tweaked_controllers` | `1.20.1-1.2.4` |

## Fail-soft behavior

Optional integration mixins live in their own mixin config
(`vs_analog_warfare.compat.mixins.json`) that is marked non-required with
injections defaulting to `require = 0`. A missing or changed dependency method
therefore logs a warning and disables only that feature instead of crashing
startup. The mandatory mixins in `vs_analog_warfare.mixins.json` remain required.

At runtime the mod self-checks each integration's version and critical API
surface. When a dependency is missing, running an untested version, or
structurally incompatible, the player is warned in chat when they join the
world, start a recording, or use the Vehicle Setup Block. Each player sees the
warning once per session. Always run the validated versions above.
