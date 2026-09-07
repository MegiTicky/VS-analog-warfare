package rbasamoyai.createbigcannons.base.multiple_kinetic_interface;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.List;

/**
 * COMPILE-ONLY STUB (excluded from the built jar; the real class is provided
 * by Create Big Cannons at runtime).
 *
 * <p>Mirrors {@code rbasamoyai.createbigcannons.base.multiple_kinetic_interface.HasMultipleKineticInterfaces}
 * from CBC 5.8.2. CBC's mixins on Create's {@code RotationPropagator} reroute
 * kinetic propagation through {@code getInterfacingBlockEntity} for any block
 * entity implementing this interface, which is how a single block position can
 * host several independent kinetic networks (the cannon mount uses it for its
 * yaw/pitch interfaces; the mouse aim block uses it to separate its power
 * network from the turret-mode rotation output).
 */
public interface HasMultipleKineticInterfaces {

    /**
     * The kinetic block entity seen by a neighbour approaching from the given
     * offset (relative to this block's position), or null to fall back to the
     * block entity itself.
     */
    @Nullable
    KineticBlockEntity getInterfacingBlockEntity(BlockPos from);

    /**
     * All kinetic block entities hosted at this position; each one is seeded
     * into the propagation graph when the block is added.
     */
    List<KineticBlockEntity> getAllKineticBlockEntities();
}
