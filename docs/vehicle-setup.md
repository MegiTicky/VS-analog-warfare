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
   recorded actions and activate the engine.

While recording, all normal block placements and breaks by the recording
player are saved relative to the setup block. A placement followed by a break
is therefore replayed in that same order. Stop recording before removing a
temporary activation shaft when that removal must not be saved.

Sneak-right-click the setup block with the recorder to inspect its saved action
count without changing the recording state.

The optional DBW and Trackwork recorder actions remain available. Select the
setup block with the recorder first, then use the appropriate compatible block
or sneak-right-click in air for Trackwork stiffness.
