package com.erika.vsanalogwarfare.mouseaim;

import com.erika.vsanalogwarfare.config.CommonConfig;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * The turret-mode rotation output of a {@link MouseAimBlockEntity}, hosted at
 * the same block position as an internal sub-block entity (the same trick CBC
 * uses for its yaw/pitch interfaces). Its synthetic block state carries
 * {@link MouseAimBlock#OUTPUT}, restricting it to the arrow-marked top face,
 * so the PID-generated RPM reaches only the network the player wires there
 * (shafts → Ender Energy Transmitters → Clockwork physics bearing) and never
 * leaks into the mount's power network on the horizontal faces.
 *
 * <p>This block entity is never placed in the world; like CBC's interfaces it
 * borrows the owning block's registered type and lives only as a field.
 */
public class MouseAimOutputInterface extends GeneratingKineticBlockEntity {
    @Nullable
    private final MouseAimBlockEntity parent;

    public MouseAimOutputInterface(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        this(type, pos, state, null);
    }

    public MouseAimOutputInterface(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                   @Nullable MouseAimBlockEntity parent) {
        super(type, pos, state);
        this.parent = parent;
    }

    /**
     * The PID yaw controller's commanded output, in Create RPM.
     *
     * <p>The output persona lives at the same block position as the input-driven
     * main block entity. Create keys kinetic networks by the value returned here,
     * and {@code GeneratingKineticBlockEntity} defaults it to the block
     * position's long — which would merge the turret output into the main
     * block's power network, letting the constant input RPM reach the turret
     * directly. Return a position-derived-but-distinct id so the generated
     * output forms its own isolated network.
     */
    @Override
    public float getGeneratedSpeed() {
        return parent != null ? parent.getTurretOutputRpm() : 0.0F;
    }

    @Override
    public Long createNetworkId() {
        return worldPosition.asLong() ^ 0x51A7_3C0_FL;
    }

    /** Exposes the protected kinetic serialization for the parent's NBT. */
    public CompoundTag writeServer(CompoundTag tag) {
        this.write(tag, false);
        return tag;
    }

    /** Exposes the protected kinetic deserialization for the parent's NBT. */
    public void readServer(CompoundTag tag) {
        this.read(tag, false);
    }

    @Override
    public float calculateAddedStressCapacity() {
        return (float) CommonConfig.turretStressCapacity();
    }

    @Override
    public float calculateStressApplied() {
        return 0.0F;
    }
}
