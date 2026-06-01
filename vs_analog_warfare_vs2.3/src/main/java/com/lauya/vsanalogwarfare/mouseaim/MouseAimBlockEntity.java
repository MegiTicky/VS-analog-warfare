package com.lauya.vsanalogwarfare.mouseaim;

import com.lauya.vsanalogwarfare.config.CommonConfig;
import com.lauya.vsanalogwarfare.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

public class MouseAimBlockEntity extends KineticBlockEntity {
    @Nullable
    private UUID targetPlayer;
    @Nullable
    private BlockPos targetMountPos;
    @Nullable
    private Vec3 targetDirection;
    private long lastTargetGameTime = Long.MIN_VALUE;

    public MouseAimBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOUSE_AIM.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        if (!isMouseAimActive() || targetDirection == null || targetMountPos == null) {
            return;
        }
        if (level.getGameTime() - lastTargetGameTime > CommonConfig.mouseAimTargetTimeoutTicks()) {
            clearTarget();
            return;
        }
        MouseAimController.tick(this, targetMountPos, targetDirection.normalize(), getMouseAimRateDegreesPerTick());
    }

    public boolean isMouseAimActive() {
        return Math.abs(getSpeed()) >= CommonConfig.mouseAimMinSpeed() && !isOverStressed();
    }

    public double getMouseAimRateDegreesPerTick() {
        return Math.abs(convertToAngular(getSpeed())) * CommonConfig.mouseAimRateMultiplier();
    }

    public void setTarget(UUID playerId, BlockPos mountPos, Vec3 direction) {
        this.targetPlayer = playerId;
        this.targetMountPos = mountPos.immutable();
        this.targetDirection = direction.normalize();
        this.lastTargetGameTime = level == null ? 0L : level.getGameTime();
        setChanged();
    }

    public boolean controls(BlockPos mountPos) {
        if (level == null || mountPos == null) {
            return false;
        }
        return MouseAimController.findAdjacentMount(level, worldPosition).filter(mountPos::equals).isPresent();
    }

    public void clearTarget() {
        this.targetPlayer = null;
        this.targetMountPos = null;
        this.targetDirection = null;
        this.lastTargetGameTime = Long.MIN_VALUE;
        setChanged();
    }

    @Nullable
    public UUID targetPlayer() {
        return targetPlayer;
    }
}
