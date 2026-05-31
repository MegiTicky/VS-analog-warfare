package com.lauya.vsanalogwarfare.scope;

import com.lauya.vsanalogwarfare.scope.ballistics.BallisticProfile;
import com.lauya.vsanalogwarfare.scope.rig.CameraPose;
import com.lauya.vsanalogwarfare.scope.rig.CameraRig;
import com.lauya.vsanalogwarfare.scope.rig.FixedCoaxScopeRig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

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

    public ScopeSession(ServerPlayer player, ScopeBlockEntity scope, BlockPos mountPos) {
        this.playerId = player.getUUID();
        this.dimension = player.level().dimension();
        this.scopePos = scope.getBlockPos();
        this.mountPos = mountPos;
        this.rig = new FixedCoaxScopeRig(scope, mountPos);
        this.currentPose = this.rig.getCameraPose(1.0f);
        this.displayProfile = scope.getDisplayProfile();
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
        }
    }
}
