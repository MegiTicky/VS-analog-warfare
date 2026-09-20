package com.erika.vsanalogwarfare.vehiclesetup.compat;

import java.util.Map;

/**
 * A block entity that stores VS ship ids inside its own NBT and therefore
 * breaks when its ship is saved to a VMod schematic and pasted: the paste
 * copies the stored ids verbatim (VMod remaps only the top-level x/y/z of
 * each block entity tag), and a schematic's source-world ship ids never match
 * the freshly allocated ids of the pasted ships.
 *
 * <p>Implementations rewrite every stored ship id onto the pasted ships'
 * runtime ids and persist themselves. The VMod paste scan
 * ({@link VmodVehicleSetupCompat}) calls this for every implementing block
 * entity found on a pasted ship — implementing this interface is all a block
 * entity needs to survive a paste.
 *
 * <p>This covers the paste event only. Links whose stored id died by other
 * means (a ship disassembly retires its id; reassembly allocates a fresh one;
 * schematics pasted before this interface existed carry permanently dead ids)
 * need resolution-time healing — see
 * {@link com.erika.vsanalogwarfare.scope.ShipLinkSupport}, and attempt that
 * heal before declaring a stored link dead.
 */
public interface VmodPasteRebasable {
    /**
     * @param placedShips schematic-id AND runtime-id keyed map of this
     *        placement's ships ({@link VmodVehicleSetupCompat#globalPastedShips()}
     *        holds the session-wide equivalent)
     */
    void rebaseAfterVmodPaste(Map<Long, Object> placedShips);
}
