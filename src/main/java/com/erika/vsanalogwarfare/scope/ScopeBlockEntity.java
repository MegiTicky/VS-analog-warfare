package com.erika.vsanalogwarfare.scope;

import com.erika.vsanalogwarfare.registry.ModBlockEntities;
import com.erika.vsanalogwarfare.scope.ballistics.BallisticProfile;
import com.erika.vsanalogwarfare.scope.ballistics.BallisticProfileResolver;
import com.erika.vsanalogwarfare.scope.compat.CbcCompat;
import com.erika.vsanalogwarfare.scope.compat.VsCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ScopeBlockEntity extends BlockEntity {
    private static final int DEFAULT_SCAN_RADIUS = 8;

    @Nullable
    private ScopeCannonLink primaryLink;
    /** drivebywire Controller Hub this scope acts as a controller for (ship-local link). */
    @Nullable
    private ScopeCannonLink wireHubLink;
    private final List<ScopeCannonLink> secondaryLinks = new ArrayList<>();
    private boolean primaryLinkDeleted;
    private transient Map<Long, Object> placedShips;
    private int revision;
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
    private int zeroDistance = 0;
    private boolean highAngleZero = false;

    public ScopeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCOPE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ScopeBlockEntity be) {
        if (level.isClientSide) return;
        if (level.getGameTime() % 40L == 0L) {
            be.captureVsAnchor();
            be.initializeDefaultPrimaryLink();
            be.refreshBallisticProfile();
        }
        if (!be.secondaryLinks.isEmpty() && level.getGameTime() % 2L == 0L) {
            be.synchronizeSecondaryCannons();
        }
    }

    public void refreshBallisticProfile() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        BlockPos mount = resolveMountPos();
        if (!CbcCompat.isCannonMount(this.level.getBlockEntity(mount))) {
            this.currentProfile = BallisticProfile.EMPTY;
            setChanged();
            return;
        }
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
        return resolveMountPos();
    }

    public void setLinkedMountPos(@Nullable BlockPos linkedMountPos) {
        if (linkedMountPos == null) {
            clearPrimaryLink();
        } else {
            linkPrimary(linkedMountPos);
        }
    }

    public BlockPos resolveMountPos() {
        if (this.level == null) {
            return this.worldPosition;
        }
        if (this.primaryLink != null) {
            BlockPos resolved = this.primaryLink.resolve(this.level, this.placedShips);
            if (resolved != null && CbcCompat.isCannonMount(this.level.getBlockEntity(resolved))) {
                return resolved;
            }
            return this.worldPosition;
        }
        initializeDefaultPrimaryLink();
        if (this.primaryLink != null) {
            BlockPos resolved = this.primaryLink.resolve(this.level, this.placedShips);
            if (resolved != null && CbcCompat.isCannonMount(this.level.getBlockEntity(resolved))) return resolved;
        }
        return this.worldPosition;
    }

    public void initializeDefaultPrimaryLink() {
        if (this.level == null || this.level.isClientSide || this.primaryLink != null || this.primaryLinkDeleted) return;
        BlockPos found = CbcCompat.findNearestMount(this.level, this.worldPosition, DEFAULT_SCAN_RADIUS).orElse(null);
        if (found != null) linkPrimary(found);
    }

    @Nullable
    public ScopeCannonLink getPrimaryLink() {
        return primaryLink;
    }

    public List<ScopeCannonLink> getSecondaryLinks() {
        return List.copyOf(secondaryLinks);
    }

    @Nullable
    public ScopeCannonLink getWireHubLink() {
        return wireHubLink;
    }

    /** Links this scope to a drivebywire Controller Hub; overwrites any previous link. */
    public boolean setWireHubLink(@Nullable ScopeCannonLink link) {
        if (link == null) {
            this.wireHubLink = null;
            markLinkChanged();
            return true;
        }
        if (sameTarget(wireHubLink, link)) return false;
        this.wireHubLink = link;
        markLinkChanged();
        return true;
    }

    /** World position of the linked hub, resolved through ship-local links. */
    @Nullable
    public BlockPos resolveWireHubPos() {
        if (this.wireHubLink == null || this.level == null) return null;
        return this.wireHubLink.resolve(this.level, this.placedShips);
    }

    public int getLinkRevision() {
        return revision;
    }

    public void linkPrimary(BlockPos target) {
        if (this.level == null || !CbcCompat.isCannonMount(this.level.getBlockEntity(target))) return;
        this.primaryLink = ScopeCannonLink.fromTarget(this.level, target);
        this.primaryLinkDeleted = false;
        markLinkChanged();
        refreshBallisticProfile();
    }

    public boolean addSecondary(BlockPos target) {
        if (this.level == null || !CbcCompat.isCannonMount(this.level.getBlockEntity(target))) return false;
        ScopeCannonLink link = ScopeCannonLink.fromTarget(this.level, target);
        if ((primaryLink != null && sameTarget(primaryLink, link))
                || secondaryLinks.stream().anyMatch(existing -> sameTarget(existing, link))) return false;
        secondaryLinks.add(link);
        markLinkChanged();
        return true;
    }

    public boolean clearPrimaryLinkAndReturn() {
        if (primaryLink == null) return false;
        primaryLink = null;
        primaryLinkDeleted = true;
        markLinkChanged();
        currentProfile = BallisticProfile.EMPTY;
        return true;
    }

    public void clearPrimaryLink() {
        clearPrimaryLinkAndReturn();
    }

    public boolean removeSecondary(int index) {
        if (index < 0 || index >= secondaryLinks.size()) return false;
        secondaryLinks.remove(index);
        markLinkChanged();
        return true;
    }

    public void setPlacedShips(Map<Long, Object> placedShips) {
        this.placedShips = placedShips;
        initializeDefaultPrimaryLink();
        refreshBallisticProfile();
    }

    public void initializeAfterSchematicPlacement(Map<Long, Object> placedShips) {
        this.placedShips = placedShips;
        captureVsAnchor();
        initializeDefaultPrimaryLink();
        refreshBallisticProfile();
        synchronizeSecondaryCannons();
    }

    private void synchronizeSecondaryCannons() {
        if (this.level == null || this.primaryLink == null) return;
        BlockPos primary = resolveMountPos();
        if (!CbcCompat.isCannonMount(this.level.getBlockEntity(primary))) return;
        Vec3 primaryDirection = CbcCompat.getAimDirection(this.level, primary, Direction.NORTH, 1.0f, true).orElse(null);
        if (primaryDirection == null) return;
        for (ScopeCannonLink link : secondaryLinks) {
            BlockPos secondary = link.resolve(this.level, this.placedShips);
            if (secondary == null || secondary.equals(primary)
                    || !CbcCompat.isCannonMount(this.level.getBlockEntity(secondary))) continue;
            com.erika.vsanalogwarfare.mouseaim.MouseAimController.setAimDirection(this.level, secondary, primaryDirection);
        }
    }

    private static boolean sameTarget(@Nullable ScopeCannonLink first, @Nullable ScopeCannonLink second) {
        if (first == null || second == null) return first == second;
        if (first.shipId() != second.shipId()) return false;
        if (first.shipOffset() != null || second.shipOffset() != null) {
            return first.shipOffset() != null && first.shipOffset().equals(second.shipOffset());
        }
        return first.fallbackPos().equals(second.fallbackPos());
    }

    private void markLinkChanged() {
        revision++;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
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

    public int getZeroDistance() {
        return zeroDistance;
    }

    public void setZeroDistance(int zeroDistance) {
        this.zeroDistance = zeroDistance;
        setChanged();
    }

    public boolean getHighAngleZero() {
        return highAngleZero;
    }

    public void setHighAngleZero(boolean highAngleZero) {
        this.highAngleZero = highAngleZero;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.primaryLink != null) tag.put("PrimaryLink", this.primaryLink.save());
        tag.putBoolean("PrimaryLinkDeleted", this.primaryLinkDeleted);
        if (this.wireHubLink != null) tag.put("WireHubLink", this.wireHubLink.save());
        net.minecraft.nbt.ListTag secondary = new net.minecraft.nbt.ListTag();
        for (ScopeCannonLink link : this.secondaryLinks) secondary.add(link.save());
        tag.put("SecondaryLinks", secondary);
        tag.putInt("LinkRevision", this.revision);
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
        tag.putInt("ZeroDistance", this.zeroDistance);
        tag.putBoolean("HighAngleZero", this.highAngleZero);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.primaryLink = tag.contains("PrimaryLink") ? ScopeCannonLink.load(tag.getCompound("PrimaryLink")) : null;
        if (this.primaryLink == null && tag.contains("LinkedMountPos")) {
            this.primaryLink = new ScopeCannonLink(-1L, null, BlockPos.of(tag.getLong("LinkedMountPos")));
        }
        this.primaryLinkDeleted = tag.getBoolean("PrimaryLinkDeleted");
        this.wireHubLink = tag.contains("WireHubLink") ? ScopeCannonLink.load(tag.getCompound("WireHubLink")) : null;
        this.secondaryLinks.clear();
        if (tag.contains("SecondaryLinks", net.minecraft.nbt.Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag secondary = tag.getList("SecondaryLinks", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < secondary.size(); i++) {
                ScopeCannonLink link = ScopeCannonLink.load(secondary.getCompound(i));
                if (link != null) this.secondaryLinks.add(link);
            }
        }
        this.revision = tag.getInt("LinkRevision");
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
        this.zeroDistance = tag.contains("ZeroDistance") ? tag.getInt("ZeroDistance") : 0;
        this.highAngleZero = tag.getBoolean("HighAngleZero");
    }

    private static ControlMode parseControlMode(String name) {
        try {
            return ControlMode.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return ControlMode.FOLLOW_CANNON;
        }
    }
}
