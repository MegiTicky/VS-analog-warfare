package com.erika.vsanalogwarfare.decorationbearing;

import com.erika.vsanalogwarfare.registry.ModBlockEntities;
import com.erika.vsanalogwarfare.scope.ScopeCannonLink;
import com.erika.vsanalogwarfare.scope.ShipLinkSupport;
import com.erika.vsanalogwarfare.scope.compat.CbcCompat;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VmodPasteRebasable;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.IBearingBlockEntity;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class DecorationBearingBlockEntity extends GeneratingKineticBlockEntity
        implements IBearingBlockEntity, IDisplayAssemblyExceptions, VmodPasteRebasable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private ScopeCannonLink linkedMount;
    private DecorationBearingContraptionEntity movedContraption;
    private boolean running;
    private boolean assembleNextTick;
    private float angle;
    private AssemblyException lastException;
    private ScrollOptionBehaviour<DecorationRotationMode> rotationMode;

    public DecorationBearingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DECORATION_BEARING.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DecorationBearingBlockEntity bearing) {
        if (level.isClientSide) return;

        bearing.tick();

        if (bearing.assembleNextTick) {
            bearing.assembleNextTick = false;
            if (bearing.running) {
                if (bearing.movedContraption != null && !bearing.movedContraption.isStalled()) {
                    // already running, do nothing
                } else {
                    bearing.disassemble();
                }
            } else {
                if (bearing.getSpeed() == 0) return;
                bearing.assemble();
            }
        }

        if (!bearing.running) return;
        if (bearing.movedContraption != null && bearing.movedContraption.isStalled()) return;

        // Validate the entity still exists and the link is still valid
        if (bearing.movedContraption == null) {
            bearing.disassemble();
            return;
        }

        BlockPos mount = bearing.resolveMount();
        if (mount == null) {
            // The link may be stale from a VMod schematic paste (old ship id /
            // fallback position). Try to re-link to the nearest CBC mount;
            // allow several cooldown-gated scan windows before tearing the
            // bearing down, so a freshly pasted DBC survives while the scan
            // is still recovering the link.
            if (!bearing.tryRepairStaleMount() && bearing.mountRepairFailures < 3) {
                return;
            }
            mount = bearing.resolveMount();
            if (mount == null && bearing.mountRepairFailures >= 3) {
                // Last resort before teardown: the verify-gated ship-id heal
                // ladder. Every discarded contraption entity risks stranding
                // its client-side render world, so a teardown is worth one
                // more verified attempt.
                bearing.healStaleMountLinkIfNeeded();
                mount = bearing.resolveMount();
            }
            if (mount == null) {
                LOGGER.info("[VSAW_DBC] bearing at {} disassembling — mount link unresolved after repair windows",
                        bearing.worldPosition);
                bearing.disassemble();
                return;
            }
        }
        bearing.mountRepairFailures = 0;

        // Validate the live CBC entity still exists
        Object cbcEntity = CbcCompat.resolveLiveCbcEntity(level, mount);
        if (cbcEntity == null) {
            // CBC entity temporarily unavailable — keep running, the entity will retry next tick
            return;
        }

    }

    @Nullable
    private BlockPos resolveMount() {
        if (level == null || linkedMount == null) return null;
        // resolveVerified falls back to the invariant link-time shipyard
        // position when the AABB-min offset no longer verifies (VS2 recomputes
        // the ship AABB on every block edit), so an edited hull no longer
        // sends the bearing into repair/teardown paths.
        return linkedMount.resolveVerified(level, null, pos -> CbcCompat.isCannonMount(level.getBlockEntity(pos)));
    }

    public boolean link(BlockPos target) {
        if (level == null || !CbcCompat.isCannonMount(level.getBlockEntity(target))) return false;
        linkedMount = ScopeCannonLink.fromTarget(level, target);
        setChanged();
        if (!level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return true;
    }

    @Nullable
    public BlockPos getLinkedMountPos() {
        return resolveMount();
    }

    /**
     * Re-link to a mount found after the saved link went stale — a VMod
     * schematic paste copies the bearing with a {@link ScopeCannonLink} that
     * still references the ORIGINAL ship (old ship id / fallback position),
     * which can never resolve on the pasted ship.
     */
    public void relinkMount(BlockPos newMount) {
        if (level == null || level.isClientSide) return;
        if (!CbcCompat.isCannonMount(level.getBlockEntity(newMount))) return;
        linkedMount = ScopeCannonLink.fromTarget(level, newMount);
        // INFO on purpose: a nearest-mount relink is a last-resort heuristic
        // and can re-point at the WRONG cannon on a ship with several mounts
        // — when something looks wrong after a paste, this line is the tell.
        LOGGER.info("[VSAW_DBC] bearing at {} re-linked to mount at {} (shipId={}, offset={})",
                worldPosition, newMount, linkedMount.shipId(), linkedMount.shipOffset());
        mountRepairCooldown = 100;
        mountRepairFailures = 0;
        setChanged();
    }

    /** Cooldown (ticks) between nearest-mount repair scans. */
    private int mountRepairCooldown;
    /** Failed nearest-mount repair scan windows; disassemble after 3. */
    private int mountRepairFailures;

    /**
     * VMod paste: schematic NBT carries the ship ids of the world the bearing
     * was linked in, which never match the freshly allocated ids of the pasted
     * ships. Rebase the stored link onto the pasted ship so the decoration
     * comes up linked without a manual screwdriver relink.
     */
    @Override
    public void rebaseAfterVmodPaste(Map<Long, Object> placedShips) {
        if (level == null || level.isClientSide) return;
        ScopeCannonLink rebased = ScopeCannonLink.rebasedAfterPaste(this.linkedMount, placedShips);
        if (rebased == null) return;
        // Verify the rebased target really is a cannon mount before adopting:
        // the shipOffset math assumes the pasted hull geometry is unchanged,
        // and a drifted offset must not silently re-point the link at a wrong
        // block — leave it stale so the verify-gated heal ladder handles it.
        if (!CbcCompat.isCannonMount(level.getBlockEntity(rebased.fallbackPos()))) return;
        this.linkedMount = rebased;
        LOGGER.info("[VSAW_DBC] bearing at {} rebased onto pasted shipId={}, mount={}",
                worldPosition, rebased.shipId(), rebased.fallbackPos());
        setChanged();
    }

    /**
     * Recovery for a saved link whose ship id is dead without a paste — a ship
     * disassembly retires its id and reassembly allocates a fresh one, so the
     * stored id never resolves again. Runs the shared verify-gated ladder and
     * adopts the result; the target check (isCannonMount) guarantees the link
     * is never re-pointed at a wrong block. Returns true when the link now
     * resolves.
     */
    public boolean healStaleMountLinkIfNeeded() {
        if (level == null || level.isClientSide || linkedMount == null) return false;
        if (resolveMount() != null) return false;
        ScopeCannonLink healed = ShipLinkSupport.healStaleShipId(level, worldPosition, linkedMount,
                pos -> CbcCompat.isCannonMount(level.getBlockEntity(pos)));
        if (healed == null) return false;
        linkedMount = healed;
        LOGGER.info("[VSAW_DBC] bearing at {} healed stale mount link onto shipId={}, mount={}",
                worldPosition, healed.shipId(), healed.fallbackPos());
        setChanged();
        return resolveMount() != null;
    }

    /**
     * Attempt to recover a stale mount link by scanning for the nearest CBC
     * cannon mount. Cooldown-gated because the scan is a block search.
     * Returns true if the link now resolves.
     */
    public boolean tryRepairStaleMount() {
        if (level == null || level.isClientSide) return false;
        if (mountRepairCooldown > 0) {
            mountRepairCooldown--;
            return false;
        }
        mountRepairCooldown = 40;
        BlockPos found = CbcCompat.findNearestMount(level, worldPosition, 16).orElse(null);
        if (found == null) {
            mountRepairFailures++;
            LOGGER.debug("[VSAW_DBC] bearing at {}: nearest-mount scan found nothing (attempt {})",
                    worldPosition, mountRepairFailures);
            return false;
        }
        relinkMount(found);
        return resolveMount() != null;
    }

    public boolean isRunning() {
        return running;
    }

    /** True when another decoration entity already owns this bearing. */
    public boolean isClaimed() {
        return movedContraption != null;
    }

    public void assemble() {
        if (level == null || level.isClientSide || running) {
            return;
        }
        // A saved link can carry a dead ship id (disassembly retires it,
        // reassembly allocates a fresh one; legacy pastes predate the paste
        // rebase). Heal before giving up on the link.
        if (resolveMount() == null) healStaleMountLinkIfNeeded();
        if (resolveMount() == null) {
            return;
        }
        // Clear a stale exception so callers can distinguish THIS attempt's
        // failure (AssemblyException) from "nothing to assemble".
        lastException = null;
        Direction facing = getBlockState().getValue(BlockStateProperties.FACING);
        BearingContraption contraption = new BearingContraption(false, facing);
        boolean assembled;
        try {
            assembled = contraption.assemble(level, worldPosition);
        } catch (AssemblyException e) {
            LOGGER.info("[VSAW_DBC] assemble: AssemblyException at {}", worldPosition, e);
            lastException = e;
            sendData();
            return;
        }
        if (!assembled) {
            LOGGER.info("[VSAW_DBC] assemble: contraption.assemble() returned false at {}", worldPosition);
            return;
        }
        lastException = null;
        // Resolve the live CBC entity for the initial pose and pivot. When it
        // is not yet resolvable (cannon not assembled yet, chunk race, etc.)
        // fall back to an approximate pivot and zero pose — the entity
        // re-captures the exact pivot from the live CBC anchor on the first
        // successful pose tick.
        BlockPos mount = resolveMount();
        Object cbcEntity = CbcCompat.resolveLiveCbcEntity(level, mount);
        CbcCompat.CbcPoseData pose = cbcEntity != null ? CbcCompat.readCbcPoseData(cbcEntity) : null;
        if (pose == null) {
            LOGGER.debug("[VSAW_DBC] assemble: live CBC entity unavailable at {} (mount={}, mountBE={}, reason={})",
                    worldPosition, mount,
                    mount != null ? level.getBlockEntity(mount) : null,
                    CbcCompat.describeResolutionFailure(level, mount));
        }

        Direction initialOrientation = CbcCompat.getInitialOrientationFromCannon(level, mount);
        if (initialOrientation == null) {
            Direction hFacing = level.getBlockState(mount).getValue(BlockStateProperties.HORIZONTAL_FACING);
            initialOrientation = hFacing;
        }

        movedContraption = DecorationBearingContraptionEntity.create(level, this, contraption, initialOrientation);

        // Render origin = bottom-center of the contraption's own anchor block
        // (BearingContraption anchors at bearingPos.relative(facing), so this
        // is where local block (0,0,0) renders from).
        Vec3 renderOrigin = Vec3.atBottomCenterOf(contraption.anchor);
        if (pose != null) {
            // pivotLocal is relative to the CBC entity's raw position (not its
            // anchorVec, which carries a +0.5 x/z offset) — that's what makes
            // the render rotation center land on the cannon's visual pivot.
            movedContraption.capturePivot(pose.entityPos(), renderOrigin);
            // pose.viewYaw() is already CBC's m_5675_ (negated) convention;
            // store it unmodified — except for the rotation mode filter: the
            // frozen axis starts at its neutral value (identity orientation,
            // matching the fallback branch below), then the contraption tick
            // holds it at whatever value it had when the mode was applied.
            float initYaw = pose.viewYaw();
            float initPitch = pose.viewPitch();
            DecorationRotationMode mode = getRotationMode();
            if (mode == DecorationRotationMode.YAW_ONLY) {
                initPitch = 0.0f;
            } else if (mode == DecorationRotationMode.PITCH_ONLY) {
                initYaw = -initialOrientation.toYRot();
            }
            movedContraption.setDecorationRotation(initYaw, initPitch);
        } else {
            // Approximate the POCE spawn position in the Create/VS shipyard
            // frame (atLowerCornerOf(mount - 2 along the vertical axis));
            // from the live entity on the first pose tick.
            movedContraption.capturePivotLocal(Vec3.atLowerCornerOf(mount.below(2)), renderOrigin);
            // Neutral pose in the internal convention: render applies
            // yaw + initialYaw, so -initialYaw yields identity.
            movedContraption.setDecorationRotation(-initialOrientation.toYRot(), 0.0f);
        }

        // Spawn in Create/VS shipyard space. VS's generic contraption mixin
        // handles the ship-to-world conversion for this entity.
        movedContraption.setPos(renderOrigin);

        contraption.removeBlocksFromWorld(level, BlockPos.ZERO);

        level.addFreshEntity(movedContraption);
        running = true;
        angle = 0;
        sendData();
        setChanged();
    }

    public void disassemble() {
        if (movedContraption != null) {
            movedContraption.disassemble();
            if (!movedContraption.isRemoved()) movedContraption.discard();
        }
        movedContraption = null;
        running = false;
        assembleNextTick = false;
        sendData();
        setChanged();
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide) disassemble();
        super.remove();
    }

    @Override
    public void addBehaviours(List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        rotationMode = new ScrollOptionBehaviour<>(DecorationRotationMode.class,
                Component.translatable("vsanalogwarfare.decoration_bearing.rotation_mode.label"),
                this,
                // Horizontal faces only — matches the Mechanical Bearing, whose
                // slot never appears on the top or bottom.
                new CenteredSideValueBoxTransform((state, dir) -> dir.getAxis().isHorizontal()));
        rotationMode.requiresWrench();
        behaviours.add(rotationMode);
    }

    /**
     * Current rotation mode. Persistence and client sync are handled entirely
     * by the behaviour framework (NBT key "ScrollValue"); before behaviours
     * are registered (or on stripped BEs) default to tracking both axes.
     */
    public DecorationRotationMode getRotationMode() {
        return rotationMode != null ? rotationMode.get() : DecorationRotationMode.YAW_AND_PITCH;
    }

    @Override
    public boolean isAttachedTo(AbstractContraptionEntity entity) {
        return movedContraption == entity;
    }

    @Override
    public void attach(ControlledContraptionEntity entity) {
    }

    public void attach(DecorationBearingContraptionEntity decoration) {
        // Never steal a live claim: a pasted cluster of decorations used to
        // cascade cross-claims here, which gave every ring its neighbor's
        // rotation mode and pivot. (Re-claiming our own decoration after a
        // chunk reload is fine — read() clears movedContraption.)
        if (movedContraption != null && movedContraption != decoration && movedContraption.isAlive()) {
            LOGGER.info("[VSAW_DBC] bearing at {} refused attach from entity {} — claimed by alive entity {}",
                    worldPosition, decoration.getId(), movedContraption.getId());
            return;
        }
        movedContraption = decoration;
        running = true;
        setChanged();
    }

    @Override
    public void onStall() {
        sendData();
    }

    @Override
    public boolean isValid() {
        return !isRemoved();
    }

    @Override
    public BlockPos getBlockPosition() {
        return worldPosition;
    }

    @Override
    public float getInterpolatedAngle(float partialTicks) {
        return angle;
    }

    @Override
    public boolean isWoodenTop() {
        return false;
    }

    @Override
    public void setAngle(float angle) {
        this.angle = angle;
    }

    @Override
    public float getGeneratedSpeed() {
        return 0;
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        if (linkedMount != null) tag.put("LinkedMount", linkedMount.save());
        tag.putBoolean("Running", running);
    }

    @Override
    public void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        linkedMount = tag.contains("LinkedMount") ? ScopeCannonLink.load(tag.getCompound("LinkedMount")) : null;
        // Do NOT restore running=true from saved state — the entity may not exist after reload.
        // The block entity will reassemble naturally if conditions are met.
        running = false;
        movedContraption = null;
    }

    @Nullable
    @Override
    public AssemblyException getLastAssemblyException() {
        return lastException;
    }
}
