package com.erika.vsanalogwarfare.scope;

import com.erika.vsanalogwarfare.registry.ModBlockEntities;
import com.erika.vsanalogwarfare.scope.ballistics.BallisticProfile;
import com.erika.vsanalogwarfare.scope.ballistics.BallisticProfileResolver;
import com.erika.vsanalogwarfare.scope.compat.CbcCompat;
import com.erika.vsanalogwarfare.scope.compat.VsCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;

public class ScopeBlockEntity extends BlockEntity {
    private static final int DEFAULT_SCAN_RADIUS = 8;

    @Nullable
    private BlockPos linkedMountPos;
    private long shipId = -1L;
    private Vec3 shipLocalScopePos = Vec3.ZERO;
    private Vec3 cameraOffset = new Vec3(0.0, 0.25, 0.0);
    private float fov = 18.0f;
    private int zoomLevel = 1;
    private String opticType = "fixed_coax";
    private ControlMode controlMode = ControlMode.FOLLOW_CANNON;
    private boolean mouseControlEnabled = false;
    private BallisticProfile currentProfile = BallisticProfile.EMPTY;
    private BallisticProfile lastValidProfile = BallisticProfile.EMPTY;

    public ScopeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCOPE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ScopeBlockEntity be) {
        if (!level.isClientSide && level.getGameTime() % 40L == 0L) {
            be.captureVsAnchor();
            be.refreshBallisticProfile();
        }
    }

    public void refreshBallisticProfile() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        BlockPos mount = resolveMountPos();
        Optional<BallisticProfile> resolved = BallisticProfileResolver.resolve(this.level, mount);
        this.currentProfile = resolved.orElse(BallisticProfile.EMPTY);
        resolved.ifPresent(profile -> this.lastValidProfile = profile);
        setChanged();
    }

    public BallisticProfile getCurrentProfile() {
        return currentProfile;
    }

    public BallisticProfile getLastValidProfile() {
        return lastValidProfile;
    }

    public BallisticProfile getDisplayProfile() {
        return currentProfile.valid() ? currentProfile : lastValidProfile;
    }

    public void captureVsAnchor() {
        if (this.level == null) {
            return;
        }
        this.shipId = VsCompat.findShipId(this.level, this.worldPosition).orElse(-1L);
        this.shipLocalScopePos = Vec3.atCenterOf(this.worldPosition);
        setChanged();
    }

    @Nullable
    public BlockPos getLinkedMountPos() {
        return linkedMountPos;
    }

    public void setLinkedMountPos(@Nullable BlockPos linkedMountPos) {
        this.linkedMountPos = linkedMountPos;
        setChanged();
    }

    public BlockPos resolveMountPos() {
        if (this.level == null) {
            return this.worldPosition;
        }
        if (this.linkedMountPos != null && CbcCompat.isCannonMount(this.level.getBlockEntity(this.linkedMountPos))) {
            return this.linkedMountPos;
        }
        BlockPos found = CbcCompat.findNearestMount(this.level, this.worldPosition, DEFAULT_SCAN_RADIUS).orElse(null);
        if (found != null) {
            setLinkedMountPos(found);
            return found;
        }
        return this.worldPosition;
    }

    public long getShipId() {
        return shipId;
    }

    public Vec3 getShipLocalScopePos() {
        return shipLocalScopePos;
    }

    public Vec3 getCameraOffset() {
        return cameraOffset;
    }

    public float getFov() {
        return fov;
    }

    public int getZoomLevel() {
        return zoomLevel;
    }

    public String getOpticType() {
        return opticType;
    }

    public ControlMode getControlMode() {
        return controlMode;
    }

    public boolean isMouseControlEnabled() {
        return mouseControlEnabled;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.linkedMountPos != null) {
            tag.putLong("LinkedMountPos", this.linkedMountPos.asLong());
        }
        tag.putLong("ShipId", this.shipId);
        tag.putDouble("LocalX", this.shipLocalScopePos.x);
        tag.putDouble("LocalY", this.shipLocalScopePos.y);
        tag.putDouble("LocalZ", this.shipLocalScopePos.z);
        tag.putDouble("CameraOffsetX", this.cameraOffset.x);
        tag.putDouble("CameraOffsetY", this.cameraOffset.y);
        tag.putDouble("CameraOffsetZ", this.cameraOffset.z);
        tag.putFloat("Fov", this.fov);
        tag.putInt("ZoomLevel", this.zoomLevel);
        tag.putString("OpticType", this.opticType);
        tag.putString("ControlMode", this.controlMode.name());
        tag.putBoolean("MouseControlEnabled", this.mouseControlEnabled);
        tag.put("CurrentBallisticProfile", this.currentProfile.save());
        tag.put("LastValidBallisticProfile", this.lastValidProfile.save());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.linkedMountPos = tag.contains("LinkedMountPos") ? BlockPos.of(tag.getLong("LinkedMountPos")) : null;
        this.shipId = tag.getLong("ShipId");
        this.shipLocalScopePos = new Vec3(tag.getDouble("LocalX"), tag.getDouble("LocalY"), tag.getDouble("LocalZ"));
        this.cameraOffset = new Vec3(tag.getDouble("CameraOffsetX"), tag.getDouble("CameraOffsetY"), tag.getDouble("CameraOffsetZ"));
        this.fov = tag.contains("Fov") ? tag.getFloat("Fov") : 18.0f;
        this.zoomLevel = tag.contains("ZoomLevel") ? tag.getInt("ZoomLevel") : 1;
        this.opticType = tag.contains("OpticType") ? tag.getString("OpticType") : "fixed_coax";
        this.controlMode = parseControlMode(tag.getString("ControlMode"));
        this.mouseControlEnabled = tag.getBoolean("MouseControlEnabled");
        this.currentProfile = tag.contains("CurrentBallisticProfile") ? BallisticProfile.load(tag.getCompound("CurrentBallisticProfile")) : BallisticProfile.EMPTY;
        this.lastValidProfile = tag.contains("LastValidBallisticProfile") ? BallisticProfile.load(tag.getCompound("LastValidBallisticProfile")) : BallisticProfile.EMPTY;
    }

    private static ControlMode parseControlMode(String name) {
        try {
            return ControlMode.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return ControlMode.FOLLOW_CANNON;
        }
    }
}
