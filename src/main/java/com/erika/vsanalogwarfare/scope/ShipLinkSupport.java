package com.erika.vsanalogwarfare.scope;

import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupReflection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * Recovery for {@link ScopeCannonLink}s whose stored ship id no longer
 * resolves. VMod paste rebases ids at paste time ({@code VmodPasteRebasable}),
 * but ids also die without a paste: VS2 retires a ship's id when it
 * disassembles and allocates a fresh one on reassembly, and schematics pasted
 * before the rebase hook existed carry permanently dead ids.
 *
 * <p>Caller contract: attempt this heal before declaring a stored link dead.
 * The caller's verification predicate is what makes the ladder safe — every
 * candidate position must verify as the linked target before it is adopted,
 * so the heal can never re-point a link at a wrong block.
 */
public final class ShipLinkSupport {
    private ShipLinkSupport() { }

    /**
     * Attempts to re-point a link whose ship id no longer resolves. Ladder:
     * <ol>
     *     <li>the ship now managing the owner block's own position (the
     *     reassembled ship the block sits on) — rebase when the linked target
     *     verifies at the stored ship offset;</li>
     *     <li>the ship managing the link's fallback position (target on a
     *     different ship than the owner block) — same checks;</li>
     *     <li>the target verifiably sits at the recorded fallback position
     *     (e.g. the linked ship disassembled to static blocks) — re-capture a
     *     fresh link there, the automated equivalent of a manual relink.</li>
     * </ol>
     *
     * @param ownerPos position of the block entity owning the link; its own
     *        ship anchors the first step
     * @param targetVerifies true when the linked target really is at the
     *        given position (e.g. {@code CbcCompat::isCannonMount})
     * @return the healed link (persist it), or null when nothing verified
     */
    @Nullable
    public static ScopeCannonLink healStaleShipId(Level level, BlockPos ownerPos, ScopeCannonLink link,
                                                  Predicate<BlockPos> targetVerifies) {
        if (level == null || level.isClientSide || link == null) return null;
        if (link.shipId() >= 0L && link.shipOffset() != null) {
            ScopeCannonLink healed = healOntoShip(VehicleSetupReflection.findShip(level, ownerPos), link, targetVerifies);
            if (healed != null) return healed;
            healed = healOntoShip(VehicleSetupReflection.findShip(level, link.fallbackPos()), link, targetVerifies);
            if (healed != null) return healed;
        }
        BlockPos fallback = link.fallbackPos();
        if (targetVerifies.test(fallback)) {
            return ScopeCannonLink.fromTarget(level, fallback);
        }
        return null;
    }

    @Nullable
    private static ScopeCannonLink healOntoShip(@Nullable Object ship, ScopeCannonLink link,
                                                Predicate<BlockPos> targetVerifies) {
        if (ship == null) return null;
        long newShipId = VehicleSetupReflection.shipId(ship);
        if (newShipId < 0L || newShipId == link.shipId()) return null;
        BlockPos resolved = VehicleSetupReflection.positionOnShip(ship, link.shipOffset());
        if (resolved == null || !targetVerifies.test(resolved)) return null;
        return link.rebased(newShipId, resolved.immutable());
    }
}
