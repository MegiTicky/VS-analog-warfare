package com.erika.vsanalogwarfare.scope;

import com.erika.vsanalogwarfare.scope.ballistics.BallisticProfile;
import com.erika.vsanalogwarfare.scope.rig.CameraPose;
import com.erika.vsanalogwarfare.scope.rig.CameraRig;
import com.erika.vsanalogwarfare.scope.rig.FixedCoaxScopeRig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

public class ScopeSession {
    private final UUID playerId;
    private final ResourceKey<Level> dimension;
    private final BlockPos scopePos;
    private final BlockPos mountPos;
    private final CameraRig rig;
    private int zoomMagnification = 3;
    private CameraPose currentPose;
    private BallisticProfile displayProfile;
    private int zeroDistance;
    private boolean highAngleZero;
    private float maxDepressionDeg;
    private float maxElevationDeg;
    @Nullable
    private ItemStack wireControllerOriginal;

    public ScopeSession(ServerPlayer player, ScopeBlockEntity scope, BlockPos mountPos) {
        this.playerId = player.getUUID();
        this.dimension = player.level().dimension();
        this.scopePos = scope.getBlockPos();
        this.mountPos = mountPos;
        this.rig = new FixedCoaxScopeRig(scope, mountPos);
        this.currentPose = this.rig.getCameraPose(1.0f);
        this.displayProfile = scope.getDisplayProfile();
        this.zeroDistance = scope.getZeroDistance();
        this.highAngleZero = scope.getHighAngleZero();
        float[] limits = com.erika.vsanalogwarfare.scope.compat.CbcCompat.getMountPitchLimits(player.level(), mountPos);
        this.maxDepressionDeg = limits[0];
        this.maxElevationDeg = limits[1];
    }

    public UUID playerId() { return playerId; }
    public ResourceKey<Level> dimension() { return dimension; }
    public float fov() { return rig.getFov() * 3.0f / zoomMagnification; }
    public int zoomMagnification() { return zoomMagnification; }
    public void toggleZoom() { this.zoomMagnification = this.zoomMagnification == 3 ? 8 : 3; }
    public CameraPose currentPose() { return currentPose; }
    public BallisticProfile displayProfile() { return displayProfile == null ? BallisticProfile.EMPTY : displayProfile; }
    public BlockPos scopePos() { return scopePos; }
    public BlockPos mountPos() { return mountPos; }
    public int zeroDistance() { return zeroDistance; }
    public boolean highAngleZero() { return highAngleZero; }
    public float maxDepressionDeg() { return maxDepressionDeg; }
    public float maxElevationDeg() { return maxElevationDeg; }

    /** Main-hand stack displaced while the fake wire controller is equipped; null = none. */
    @Nullable public ItemStack wireControllerOriginal() { return wireControllerOriginal; }
    public void setWireControllerOriginal(@Nullable ItemStack stack) { this.wireControllerOriginal = stack; }

    public boolean isValid(ServerPlayer player) {
        if (!player.isAlive() || player.isRemoved()) return false;
        if (player.level().dimension() != this.dimension || !(player.level() instanceof ServerLevel level)) return false;
        if (player.isShiftKeyDown()) return false;
        return level.getBlockEntity(scopePos) instanceof ScopeBlockEntity;
    }

    public void update(ServerLevel level) {
        currentPose = rig.getCameraPose(1.0f);
        if (level.getBlockEntity(scopePos) instanceof ScopeBlockEntity scope) {
            scope.refreshBallisticProfile();
            displayProfile = scope.getDisplayProfile();
            zeroDistance = scope.getZeroDistance();
            highAngleZero = scope.getHighAngleZero();
        }
        float[] limits = com.erika.vsanalogwarfare.scope.compat.CbcCompat.getMountPitchLimits(level, mountPos);
        maxDepressionDeg = limits[0];
        maxElevationDeg = limits[1];
    }
}
