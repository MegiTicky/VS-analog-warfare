package com.erika.vsanalogwarfare.stabilizer;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.network.ModNetwork;
import com.erika.vsanalogwarfare.network.StabilizerStatePacket;
import com.erika.vsanalogwarfare.scope.ScopeCannonLink;
import com.erika.vsanalogwarfare.scope.compat.CbcCompat;
import com.erika.vsanalogwarfare.scope.compat.VsCompat;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VmodPasteRebasable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Gyro stabilizer block. Links to one CBC cannon mount and registers the pair
 * with {@link StabilizerController}; the actual compensation runs inside the
 * mount's own tick. Link is stored ship-relative so it survives schematic
 * placement, like scope links.
 */
public class StabilizerBlockEntity extends BlockEntity implements VmodPasteRebasable {

    /** Failed validations tolerated before unlinking (the ship AABB query blinks under violent motion). */
    private static final int MAX_FAILED_VALIDATIONS = 3;
    /** How long an anchored target stays fresh across reloads/relinks (ticks). */
    private static final long TARGET_ANCHOR_FRESHNESS_TICKS = 6000L;

    @Nullable
    private ScopeCannonLink mountLink;
    private long shipId = -1L;
    private Vec3 shipLocalPos = Vec3.ZERO;
    private int failedValidations;
    /** Mirror of the controller's held target; persisted so reloads/relinks restore it. */
    private double anchoredTargetElevDeg = Double.NaN;
    private long anchoredTargetGameTime = -1L;

    public StabilizerBlockEntity(BlockPos pos, BlockState state) {
        super(com.erika.vsanalogwarfare.registry.ModBlockEntities.STABILIZER.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, StabilizerBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        long gameTime = level.getGameTime();
        BlockPos mount = be.resolveMountPos();
        if (mount != null) {
            // Fresh BE instance after (re)load: the NBT link exists but the
            // controller registry does not — re-register and restore the
            // anchored target so a reload does not silently disable the servo.
            if (StabilizerController.linkedStabilizer(mount) == null) {
                StabilizerController.onLinked(pos, mount, be.restoreTarget(gameTime));
            }
            StabilizerController.sampleMountSuspension(level, mount);
            StabilizerController.MountState live = StabilizerController.stateFor(mount);
            if (live != null && live.targetValid && live.targetElevDeg != be.anchoredTargetElevDeg) {
                be.anchoredTargetElevDeg = live.targetElevDeg;
                be.anchoredTargetGameTime = gameTime;
                be.setChanged();
            }
        }
        if (gameTime % 20L == 0L) {
            be.captureVsAnchor();
            be.validateLink();
            be.sendStatePacket();
        }
        if (mount != null && StabilizerController.consumeDirty(mount)) {
            be.sendStatePacket();
        }
    }

    public void captureVsAnchor() {
        if (this.level == null) {
            return;
        }
        this.shipId = VsCompat.findShipId(this.level, this.worldPosition).orElse(-1L);
        this.shipLocalPos = Vec3.atCenterOf(this.worldPosition);
        setChanged();
    }

    // ------------------------------------------------------------------
    // Linking
    // ------------------------------------------------------------------

    /** True when linked and the resolved target is still a cannon mount. */
    public boolean isLinked() {
        return this.mountLink != null && resolveMountPos() != null;
    }

    @Nullable
    public BlockPos resolveMountPos() {
        if (this.level == null || this.mountLink == null) {
            return null;
        }
        // The stored offset is relative to the ship's AABB min corner, which
        // VS2 recomputes on every block edit; resolveVerified falls back to the
        // invariant link-time shipyard position when the offset no longer
        // verifies, so an edited hull no longer reads as a missing mount.
        return this.mountLink.resolveVerified(this.level, null,
                pos -> CbcCompat.isCannonMount(this.level.getBlockEntity(pos)));
    }

    @Nullable
    public ScopeCannonLink getMountLink() {
        return mountLink;
    }

    /** Attempt to link to a cannon mount. Returns an error message or null on success. */
    @Nullable
    public String linkMount(BlockPos target) {
        if (this.level == null || this.level.isClientSide) {
            return "Linking is handled server-side.";
        }
        if (CbcCompat.isCannonMount(this.level.getBlockEntity(target))) {
            double maxRange = com.erika.vsanalogwarfare.config.CommonConfig.stabilizerLinkRange();
            if (this.worldPosition.distSqr(target) > maxRange * maxRange) {
                return "Mount too far from the stabilizer.";
            }
            this.mountLink = ScopeCannonLink.fromTarget(this.level, target);
            this.failedValidations = 0;
            StabilizerController.onLinked(this.worldPosition, target);
            setChanged();
            sendStatePacket();
            return null;
        }
        return "Target block is not a cannon mount.";
    }

    public void unlink() {
        if (this.level == null) {
            return;
        }
        BlockPos mount = resolveMountPos();
        if (mount == null && this.mountLink != null) {
            mount = this.mountLink.resolve(this.level, null);
        }
        if (mount != null) {
            StabilizerController.onUnlinked(mount);
        } else {
            // Mount position unresolvable: sweep controller state by
            // stabilizer so a validation-timeout unlink cannot leak it.
            StabilizerController.forgetStabilizer(this.worldPosition);
        }
        this.mountLink = null;
        this.failedValidations = 0;
        this.anchoredTargetElevDeg = Double.NaN;
        this.anchoredTargetGameTime = -1L;
        setChanged();
        sendStatePacket();
    }

    /**
     * Schematic NBT carries the ship ids of the world it was saved in, which never match the
     * freshly allocated ids of the pasted ships. Called by the VMod paste scan so the mount
     * link is rebased onto the pasted ship before validation times it out and unlinks.
     */
    @Override
    public void rebaseAfterVmodPaste(Map<Long, Object> placedShips) {
        captureVsAnchor();
        ScopeCannonLink rebased = ScopeCannonLink.rebasedAfterPaste(this.mountLink, placedShips);
        if (rebased == null) {
            return;
        }
        VSAnalogWarfare.LOGGER.debug("[VSAW setup-debug] Stabilizer at {} rebased mount link onto pasted ship {}",
                this.worldPosition, rebased.shipId());
        this.mountLink = rebased;
        this.failedValidations = 0;
        setChanged();
        sendStatePacket();
    }

    private void validateLink() {
        if (this.mountLink == null) {
            this.failedValidations = 0;
            return;
        }
        BlockPos resolved = resolveMountPos();
        if (resolved != null) {
            this.failedValidations = 0;
            tryRefreshMountLinkFrame(resolved);
            return;
        }
        // The ship-relative resolve is pose-dependent (ship world AABB) and
        // blinks during violent motion; only give up after several consecutive
        // failures. A genuinely broken/removed mount stays failed.
        this.failedValidations++;
        if (this.failedValidations >= MAX_FAILED_VALIDATIONS) {
            unlink();
        }
    }

    /** Ticks between attempts to re-capture the mount link onto the live AABB frame. */
    private static final long FRAME_REFRESH_INTERVAL_TICKS = 100L;
    private long nextFrameRefreshAt;

    /**
     * When the link resolves through the invariant fallback position because
     * the stored AABB-min offset no longer matches the live frame (VS2
     * recomputes the ship AABB on every block edit), re-capture the link at
     * the verified position so the offset frame converges instead of living
     * on the fallback forever. Verification-gated: only an already-resolved
     * mount position is adopted.
     */
    private void tryRefreshMountLinkFrame(BlockPos resolved) {
        if (this.level == null || this.level.isClientSide || this.mountLink == null) return;
        long now = this.level.getGameTime();
        if (now < this.nextFrameRefreshAt) return;
        this.nextFrameRefreshAt = now + FRAME_REFRESH_INTERVAL_TICKS;
        ScopeCannonLink fresh = ScopeCannonLink.fromTarget(this.level, resolved);
        if (fresh.equals(this.mountLink)) return;
        VSAnalogWarfare.LOGGER.info("[VSAW setup-debug] Stabilizer at {} refreshed its mount link frame onto ship {} at {}",
                this.worldPosition, fresh.shipId(), resolved);
        this.mountLink = fresh;
        setChanged();
        sendStatePacket();
    }

    /** The anchored target if fresh enough to restore, else NaN (capture anew). */
    private double restoreTarget(long gameTime) {
        if (Double.isFinite(this.anchoredTargetElevDeg)
                && this.anchoredTargetGameTime > 0L
                && gameTime - this.anchoredTargetGameTime <= TARGET_ANCHOR_FRESHNESS_TICKS) {
            return this.anchoredTargetElevDeg;
        }
        return Double.NaN;
    }

    // ------------------------------------------------------------------
    // Sync
    // ------------------------------------------------------------------

    public void sendStatePacket() {
        if (this.level == null || this.level.isClientSide
                || !(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos mount = resolveMountPos();
        boolean active = false;
        double targetElev = Double.NaN;
        if (mount != null) {
            StabilizerController.MountState state = StabilizerController.stateFor(mount);
            if (state != null && state.targetValid) {
                active = true;
                targetElev = state.targetElevDeg;
            }
        }
        StabilizerStatePacket packet = new StabilizerStatePacket(mount, this.worldPosition, active, targetElev);
        double maxDistSq = 160.0 * 160.0;
        Vec3 center = Vec3.atCenterOf(this.worldPosition);
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(center) <= maxDistSq) {
                ModNetwork.sendToPlayer(player, packet);
            }
        }
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.mountLink != null) {
            tag.put("MountLink", this.mountLink.save());
        }
        tag.putLong("ShipId", this.shipId);
        tag.putDouble("LocalX", this.shipLocalPos.x);
        tag.putDouble("LocalY", this.shipLocalPos.y);
        tag.putDouble("LocalZ", this.shipLocalPos.z);
        if (Double.isFinite(this.anchoredTargetElevDeg)) {
            tag.putDouble("AnchorTargetElev", this.anchoredTargetElevDeg);
            tag.putLong("AnchorTargetTime", this.anchoredTargetGameTime);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.mountLink = tag.contains("MountLink") ? ScopeCannonLink.load(tag.getCompound("MountLink")) : null;
        this.shipId = tag.getLong("ShipId");
        this.shipLocalPos = new Vec3(tag.getDouble("LocalX"), tag.getDouble("LocalY"), tag.getDouble("LocalZ"));
        this.anchoredTargetElevDeg = tag.contains("AnchorTargetElev") ? tag.getDouble("AnchorTargetElev") : Double.NaN;
        this.anchoredTargetGameTime = tag.getLong("AnchorTargetTime");
    }

    @Override
    public void setRemoved() {
        if (this.level != null && !this.level.isClientSide) {
            unlink();
        }
        super.setRemoved();
    }
}
