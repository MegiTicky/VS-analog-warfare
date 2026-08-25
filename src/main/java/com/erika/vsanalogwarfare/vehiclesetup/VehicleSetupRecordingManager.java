package com.erika.vsanalogwarfare.vehiclesetup;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.config.ClientConfig;
import com.erika.vsanalogwarfare.registry.ModBlocks;
import com.erika.vsanalogwarfare.vehiclesetup.compat.OptionalModCompatibility;
import com.erika.vsanalogwarfare.vehiclesetup.compat.TrackworkCompat;
import com.erika.vsanalogwarfare.vehiclesetup.compat.TallyhoCompat;
import com.erika.vsanalogwarfare.vehiclesetup.compat.EnderTransmissionCompat;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupShipPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = VSAnalogWarfare.MOD_ID)
public final class VehicleSetupRecordingManager {
    private static final Map<UUID, BlockPos> ACTIVE_RECORDINGS = new HashMap<>();
    private static final Map<UUID, PendingInteraction> PENDING_INTERACTIONS = new HashMap<>();
    private static final Map<UUID, PendingTallyhoPlacement> PENDING_TALLYHO_PLACEMENTS = new HashMap<>();
    private static final Map<UUID, PendingLeftClick> PENDING_LEFT_CLICKS = new HashMap<>();
    private static final Set<UUID> REPLAYING_INTERACTIONS = new HashSet<>();
    private static final Map<UUID, Long> LAST_RECORDED_TICKS = new HashMap<>();
    private static final Map<BlockPos, PendingRun> PENDING_RUNS = new HashMap<>();
    private static final Map<UUID, BlockPos> ACTIVE_REMOVAL_RECORDINGS = new HashMap<>();
    private static final Map<UUID, BlockPos> ACTIVE_TRANSMITTER_RECORDINGS = new HashMap<>();

    private VehicleSetupRecordingManager() { }

    public static void toggle(ServerPlayer player, VehicleSetupBlockEntity setup) {
        UUID playerId = player.getUUID();
        BlockPos current = ACTIVE_RECORDINGS.get(playerId);
        if (setup.getBlockPos().equals(current)) {
            ACTIVE_RECORDINGS.remove(playerId);
            LAST_RECORDED_TICKS.remove(playerId);
            player.displayClientMessage(Component.literal("Vehicle setup recording stopped: " + setup.actionSummary() + "."), true);
            return;
        }
        OptionalModCompatibility.warnIfIssues(player);
        ACTIVE_TRANSMITTER_RECORDINGS.remove(playerId);
        ACTIVE_REMOVAL_RECORDINGS.remove(playerId);
        ACTIVE_RECORDINGS.put(playerId, setup.getBlockPos());
        LAST_RECORDED_TICKS.put(playerId, player.level().getGameTime());
        player.displayClientMessage(Component.literal(
                "Vehicle setup recording started. Place, break, or interact with blocks normally, then use the recorder on this block again to stop."), true);
    }

    public static void inspect(ServerPlayer player, VehicleSetupBlockEntity setup) {
        player.displayClientMessage(Component.literal("Vehicle setup: " + setup.actionSummary() + "."), true);
    }

    public static void recordDbwLink(ServerPlayer player, BlockPos source, BlockPos target) {
        VehicleSetupBlockEntity setup = activeSetup(player);
        if (setup == null) return;
        VehicleSetupShipPosition sourcePosition = VehicleSetupShipPosition.at(player.level(), source);
        VehicleSetupShipPosition targetPosition = VehicleSetupShipPosition.at(player.level(), target);
        if (sourcePosition == null || targetPosition == null) {
            player.displayClientMessage(Component.literal("Vehicle setup could not record the DBW relink: both backups must be on loaded ships."), true);
            return;
        }
        recordAction(player, setup, VehicleSetupAction.linkDbwBackups(
                sourcePosition.shipId(), sourcePosition.offset(),
                targetPosition.shipId(), targetPosition.offset()));
        player.displayClientMessage(Component.literal("Vehicle setup recorded DBW relink: " + setup.actionSummary() + "."), true);
    }

    public static void recordControllerLink(net.minecraft.world.entity.player.Player player, BlockPos hub,
                                            ItemStack controller) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!controller.hasTag() || !controller.getTag().contains("Hub")) return;
        VehicleSetupBlockEntity setup = activeSetup(serverPlayer);
        if (setup == null) return;
        claimGenericInteraction(serverPlayer, hub);
        VehicleSetupShipPosition hubPosition = VehicleSetupShipPosition.at(serverPlayer.level(), hub);
        if (hubPosition == null) {
            serverPlayer.displayClientMessage(Component.literal("Vehicle setup could not record controller link: hub is not on a loaded ship."), true);
            return;
        }
        recordAction(serverPlayer, setup, VehicleSetupAction.createTweakedController(
                hubPosition.shipId(), hub.subtract(setup.getBlockPos()), controller));
        serverPlayer.displayClientMessage(Component.literal("Vehicle setup recorded controller link: " + setup.actionSummary() + "."), true);
    }

    public static void recordTrackworkStiffness(net.minecraft.world.entity.player.Player player,
                                                BlockPos clicked, ItemStack stack, float stiffness) {
        if (!(player instanceof ServerPlayer serverPlayer) || !TrackworkCompat.isStiffnessTool(stack)
                || !TrackworkCompat.isStiffnessTarget(serverPlayer.level(), clicked)) return;
        VehicleSetupBlockEntity setup = activeSetup(serverPlayer);
        if (setup == null) return;
        claimGenericInteraction(serverPlayer, clicked);
        VehicleSetupShipPosition ship = VehicleSetupShipPosition.at(serverPlayer.level(), clicked);
        recordAction(serverPlayer, setup, VehicleSetupAction.setTrackworkStiffness(
                ship == null ? -1L : ship.shipId(), ship == null ? null : ship.offset(),
                clicked.subtract(setup.getBlockPos()), stiffness));
        serverPlayer.displayClientMessage(Component.literal(
                "Vehicle setup recorded suspension stiffness " + stiffness + "x: " + setup.actionSummary() + "."), true);
    }
    public static void toggleRemovalRecording(ServerPlayer player, VehicleSetupBlockEntity setup) {
        if (setup.getBlockPos().equals(ACTIVE_REMOVAL_RECORDINGS.get(player.getUUID()))) { ACTIVE_REMOVAL_RECORDINGS.remove(player.getUUID()); player.displayClientMessage(Component.literal("Removal marker recording stopped."), true); }
        else {
            ACTIVE_RECORDINGS.remove(player.getUUID());
            ACTIVE_TRANSMITTER_RECORDINGS.remove(player.getUUID());
            ACTIVE_REMOVAL_RECORDINGS.put(player.getUUID(), setup.getBlockPos());
            player.displayClientMessage(Component.literal("Removal marker recording started. Right-click temporary blocks to mark them."), true);
        }
    }

    public static void toggleTransmitterRecording(ServerPlayer player, VehicleSetupBlockEntity setup) {
        UUID playerId = player.getUUID();
        if (setup.getBlockPos().equals(ACTIVE_TRANSMITTER_RECORDINGS.get(playerId))) {
            ACTIVE_TRANSMITTER_RECORDINGS.remove(playerId);
            player.displayClientMessage(Component.literal("Energy transmitter recording stopped."), true);
            return;
        }
        ACTIVE_RECORDINGS.remove(playerId);
        LAST_RECORDED_TICKS.remove(playerId);
        ACTIVE_REMOVAL_RECORDINGS.remove(playerId);
        ACTIVE_TRANSMITTER_RECORDINGS.put(playerId, setup.getBlockPos());
        player.displayClientMessage(Component.literal(
                "Energy transmitter recording started. Right-click any block on a ship to scan it, then use the recorder on this block again to stop."), true);
    }

    public static void stopTransmitterRecording(ServerPlayer player) {
        ACTIVE_TRANSMITTER_RECORDINGS.remove(player.getUUID());
    }

    public static void recordEnderTransmitter(ServerPlayer player, BlockPos pos, int channel, String password) {
        VehicleSetupBlockEntity setup = activeSetup(player);
        if (setup == null || !EnderTransmissionCompat.isEnergyTransmitter(player.level().getBlockState(pos))) return;
        claimGenericInteraction(player, pos);
        VehicleSetupShipPosition ship = VehicleSetupShipPosition.at(player.level(), pos);
        if (ship == null) {
            player.displayClientMessage(Component.literal(
                    "Vehicle setup could not record the Ender transmitter: it must be on a loaded ship."), true);
            return;
        }
        setup.upsertEnderTransmitter(timedAction(player, VehicleSetupAction.configureEnderTransmitter(
                ship.shipId(), ship.offset(), pos.subtract(setup.getBlockPos()), channel, password)));
        player.displayClientMessage(Component.literal("Vehicle setup recorded Ender transmitter: "
                + setup.actionSummary() + "."), true);
    }

    public static void recordEnderTransmitterConfiguration(ServerPlayer player, KineticBlockEntity transmitter) {
        CompoundTag data = transmitter.getPersistentData();
        recordEnderTransmitter(player, transmitter.getBlockPos(), data.getInt("channel"),
                data.getString("password"));
    }

    public static void scanEnderTransmitters(ServerPlayer player, VehicleSetupBlockEntity setup) {
        scanEnderTransmitters(player, setup, setup.getBlockPos());
    }

    public static boolean scanTransmitterShip(ServerPlayer player, BlockPos scanOrigin) {
        VehicleSetupBlockEntity setup = activeTransmitterSetup(player);
        if (setup == null) return false;
        scanEnderTransmitters(player, setup, scanOrigin);
        return true;
    }

    private static void scanEnderTransmitters(ServerPlayer player, VehicleSetupBlockEntity setup, BlockPos scanOrigin) {
        java.util.List<EnderTransmissionCompat.DetectedTransmitter> detected =
                EnderTransmissionCompat.scan(player.level(), scanOrigin);
        if (detected.isEmpty()) {
            player.displayClientMessage(Component.literal(
                    "No energy transmitters found on the clicked ship."), true);
            return;
        }
        for (EnderTransmissionCompat.DetectedTransmitter transmitter : detected) {
            setup.upsertEnderTransmitter(VehicleSetupAction.configureEnderTransmitter(
                    transmitter.shipId(), transmitter.shipOffset(),
                    transmitter.worldPos().subtract(setup.getBlockPos()),
                    transmitter.channel(), transmitter.password()));
        }
        player.displayClientMessage(Component.literal("Added " + detected.size()
                + " energy transmitter" + (detected.size() == 1 ? "" : "s") + " to the setup."), true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        recordPlace(player, event.getPos(), event.getPlacedBlock());
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || event.getState().isAir()) return;
        discardPendingLeftClick(player, event.getPos(), true);
        recordRemove(player, event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer player)
                || event.getFace() == null) return;
        VehicleSetupBlockEntity setup = activeSetup(player);
        ItemStack item = event.getItemStack();
        BlockPos pos = event.getPos();
        if (setup == null || pos.equals(setup.getBlockPos()) || item.getItem() instanceof BlockItem
                || item.getItem() instanceof AnalogScrewdriverItem) return;

        PendingLeftClick pending = PENDING_LEFT_CLICKS.get(player.getUUID());
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
            if (player.level().getBlockState(pos).isAir()) return;
            if (pending != null && pending.pos().equals(pos)) return;
            pending = new PendingLeftClick(setup.getBlockPos(), pos,
                    player.level().getBlockState(pos), item.copy(), event.getFace(), player.isShiftKeyDown(), null);
            PENDING_LEFT_CLICKS.put(player.getUUID(), pending);
            VSAnalogWarfare.LOGGER.debug("[VSAW] Generic left-click queued: block={} pos={} player={} item={} canceled={} useBlock={} useItem={}",
                    blockId(player.level().getBlockState(pos)), pos, player.getGameProfile().getName(),
                    BuiltInRegistries.ITEM.getKey(item.getItem()), event.isCanceled(), event.getUseBlock(), event.getUseItem());
            if (event.isCanceled()) {
                finalizeLeftClick(player, pending);
                PENDING_LEFT_CLICKS.remove(player.getUUID());
            }
        } else if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.ABORT) {
            discardPendingLeftClick(player, pos, false);
        } else if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.STOP) {
            if (pending != null && player.level().getBlockState(pos).equals(pending.initialState())) {
                finalizeLeftClick(player, pending);
            } else if (pending != null && !player.level().getBlockState(pos).isAir()) {
                finalizeLeftClick(player, pending);
            } else {
                discardPendingLeftClick(player, pos, true);
            }
            PENDING_LEFT_CLICKS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getItemStack().getItem() instanceof AnalogScrewdriverItem)) return;
        VehicleSetupBlockEntity setup = activeSetup(player);
        if (setup == null) return;
        TallyhoCompat.CapturedEntity captured = TallyhoCompat.capture(event.getTarget());
        if (captured == null) return;
        VehicleSetupShipPosition ship = VehicleSetupShipPosition.at(player.level(), captured.supportPosition());
        recordAction(player, setup, VehicleSetupAction.spawnTallyhoEntity(ship == null ? -1L : ship.shipId(),
                ship == null ? null : ship.offset(), captured.supportPosition().subtract(setup.getBlockPos()),
                captured.positionOffset(), captured.entityId(), captured.baseYaw(), captured.variant(), captured.state()));
        player.displayClientMessage(Component.literal("Vehicle setup recorded Tallyho entity: "
                + captured.entityId() + ". "
                + setup.actionSummary() + "."), true);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack item = event.getItemStack();
        boolean tallyhoPlacement = TallyhoCompat.isPlacementItem(item);
        if (!tallyhoPlacement && event.isCanceled() && !event.getCancellationResult().consumesAction()) {
            return;
        }
        BlockPos removalAnchor = ACTIVE_REMOVAL_RECORDINGS.get(player.getUUID());
        if (item.getItem() instanceof AnalogScrewdriverItem
                && AnalogScrewdriverItem.removalMode(item) && removalAnchor != null) {
            if (player.level().getBlockEntity(removalAnchor) instanceof VehicleSetupBlockEntity setup
                    && event.getPos().equals(removalAnchor)) {
                toggleRemovalRecording(player, setup);
            } else if (player.level().getBlockEntity(removalAnchor) instanceof VehicleSetupBlockEntity setup) {
                VehicleSetupShipPosition ship = VehicleSetupShipPosition.at(player.level(), event.getPos());
                BlockPos targetOffset = event.getPos().subtract(removalAnchor);
                boolean duplicate = setup.markedRemovals().stream().anyMatch(action ->
                        targetOffset.equals(action.targetOffset())
                                && (ship == null ? action.targetShipId() < 0L : action.targetShipId() == ship.shipId()
                                && ship.offset().equals(action.shipOffset())));
                if (!duplicate) {
                    setup.addMarkedRemoval(VehicleSetupAction.removeBlock(ship == null ? -1L : ship.shipId(),
                            ship == null ? null : ship.offset(), targetOffset,
                            player.level().getBlockState(event.getPos())));
                    player.displayClientMessage(Component.literal("Marked temporary block for removal. Total marked: "
                            + setup.markedRemovals().size() + "."), true);
                }
            }
            if (event.getPos().equals(removalAnchor) || player.level().getBlockState(event.getPos()).isAir()) {
                event.setCanceled(true); event.setCancellationResult(InteractionResult.CONSUME); return;
            }
            event.setCanceled(true); event.setCancellationResult(InteractionResult.CONSUME); return;
        }
        VehicleSetupBlockEntity setup = activeSetup(player);
        if (tallyhoPlacement) {
            if (ClientConfig.ignoreTallyhoEntityPlacement()) {
                return;
            }
            if (setup == null || event.getPos().equals(setup.getBlockPos())) {
                return;
            }
            if (event.getUseItem() == Event.Result.DENY) {
                return;
            }
            Vec3 hitPosition = event.getHitVec().getLocation();
            PENDING_TALLYHO_PLACEMENTS.put(player.getUUID(), new PendingTallyhoPlacement(
                    setup.getBlockPos(), player.level().getGameTime() + 1L, 0,
                    hitPosition, Vec3.atCenterOf(event.getPos()),
                    TallyhoCompat.nearbyEntityIds((net.minecraft.server.level.ServerLevel) player.level(),
                            hitPosition, Vec3.atCenterOf(event.getPos()))));
            return;
        }
        if (setup == null || event.getPos().equals(setup.getBlockPos()) || item.getItem() instanceof BlockItem
                || item.getItem() instanceof AnalogScrewdriverItem) return;
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        if (state.isAir()) return;
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (!event.isCanceled() && event.getUseBlock() == Event.Result.DENY
                && event.getUseItem() == Event.Result.DENY) {
            VSAnalogWarfare.LOGGER.debug("[VSAW] Generic interaction discarded: block={} player={} both uses denied.",
                    blockId, player.getGameProfile().getName());
            return;
        }
        BlockHitResult hit = event.getHitVec();
        Vec3 hitOffset = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        PENDING_INTERACTIONS.put(player.getUUID(), new PendingInteraction(setup.getBlockPos(), pos, state,
                item.copy(), event.getHand(), hit.getDirection(), hitOffset, event.isCanceled(), player.isShiftKeyDown()));
        VSAnalogWarfare.LOGGER.debug("[VSAW] Generic interaction queued: block={} pos={} player={} hand={} item={} "
                        + "canceled={} result={} useBlock={} useItem={}", blockId, pos,
                player.getGameProfile().getName(), event.getHand(), BuiltInRegistries.ITEM.getKey(item.getItem()),
                event.isCanceled(), event.getCancellationResult(), event.getUseBlock(), event.getUseItem());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Map<UUID, PendingInteraction> pending = new HashMap<>(PENDING_INTERACTIONS);
        PENDING_INTERACTIONS.clear();
        for (Map.Entry<UUID, PendingInteraction> entry : pending.entrySet()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            PendingInteraction interaction = entry.getValue();
            VehicleSetupBlockEntity setup = activeSetup(player);
            if (setup == null || !setup.getBlockPos().equals(interaction.anchor())) {
                VSAnalogWarfare.LOGGER.debug("[VSAW] Generic interaction discarded: recording anchor disappeared for player={}.",
                        player.getGameProfile().getName());
                continue;
            }
            recordGenericInteraction(player, setup, interaction);
        }
        Map<UUID, PendingTallyhoPlacement> tallyhoPlacements = new HashMap<>(PENDING_TALLYHO_PLACEMENTS);
        PENDING_TALLYHO_PLACEMENTS.clear();
        for (Map.Entry<UUID, PendingTallyhoPlacement> entry : tallyhoPlacements.entrySet()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.level().getGameTime() < entry.getValue().captureTick()) {
                if (player != null) PENDING_TALLYHO_PLACEMENTS.put(entry.getKey(), entry.getValue());
                continue;
            }
            PendingTallyhoPlacement placement = entry.getValue();
            VehicleSetupBlockEntity setup = activeSetup(player);
            if (setup == null || !setup.getBlockPos().equals(placement.anchor())) continue;
            TallyhoCompat.CapturedEntity captured = TallyhoCompat.captureNewEntity(
                    (net.minecraft.server.level.ServerLevel) player.level(), placement.position(),
                    placement.alternatePosition(), placement.existingEntities());
            if (captured == null) {
                if (placement.attempt() < 4) {
                    PENDING_TALLYHO_PLACEMENTS.put(entry.getKey(), placement.withNextAttempt());
                }
                continue;
            }
            recordTallyhoEntity(player, setup, captured);
            player.displayClientMessage(Component.literal("Vehicle setup automatically recorded Tallyho entity: "
                    + captured.entityId() + ". " + setup.actionSummary() + "."), true);
        }
        for (java.util.Iterator<Map.Entry<BlockPos, PendingRun>> iterator = PENDING_RUNS.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<BlockPos, PendingRun> entry = iterator.next();
            PendingRun run = entry.getValue();
            if (run.remainingTicks > 0 && --run.remainingTicks > 0) continue;
            do {
                java.util.List<VehicleSetupAction> phaseActions = run.removing ? run.removals : run.actions;
                VehicleSetupAction action = phaseActions.get(run.index++);
                String error = VehicleSetupExecutor.run(run.level, entry.getKey(), run.player, action,
                        null, null, run.index - 1);
                if (error == null) { if (run.removing) run.removalSucceeded++; else run.succeeded++; }
                else if (run.firstError == null) run.firstError = error;
                if (run.index >= phaseActions.size()) {
                    if (!run.removing && !run.removals.isEmpty()) {
                        run.removing = true; run.index = 0; run.remainingTicks = run.removalDelay;
                        run.player.displayClientMessage(Component.literal("Vehicle setup: " + run.actions.size() + "/" + run.actions.size() + " completed. Removing temporary blocks in " + run.removalDelay + " ticks."), true);
                        if (run.remainingTicks == 0) continue;
                        break;
                    }
                    run.player.displayClientMessage(Component.literal(run.firstError == null
                            ? "Vehicle setup: " + run.actions.size() + "/" + run.actions.size() + " completed."
                            : "Vehicle setup: " + run.actions.size() + "/" + run.actions.size() + " completed. " + run.firstError)
                            .append(run.removals.isEmpty() ? "" : " Temporary blocks removed: " + run.removalSucceeded + "/" + run.removals.size() + "."), true);
                    iterator.remove();
                    break;
                }
                if (!run.removing) run.player.displayClientMessage(Component.literal("Vehicle setup: " + run.index + "/"
                        + run.actions.size() + " completed."), true);
                run.remainingTicks = phaseActions.get(run.index).delayBeforeTicks();
            } while (run.remainingTicks == 0);
        }
    }

    private static void recordPlace(ServerPlayer player, BlockPos pos, BlockState state) {
        VehicleSetupBlockEntity setup = activeSetup(player);
        if (setup == null || state.is(ModBlocks.VEHICLE_SETUP.get())) return;
        BlockPos anchorOffset = pos.subtract(setup.getBlockPos());
        VehicleSetupShipPosition ship = VehicleSetupShipPosition.at(player.level(), pos);
        recordAction(player, setup, VehicleSetupAction.placeBlock(ship == null ? -1L : ship.shipId(),
                ship == null ? null : ship.offset(), anchorOffset, state));
        player.displayClientMessage(Component.literal("Recorded placement: " + setup.actionSummary() + "."), true);
    }

    private static void recordRemove(ServerPlayer player, BlockPos pos) {
        VehicleSetupBlockEntity setup = activeSetup(player);
        if (setup == null || pos.equals(setup.getBlockPos())) return;
        BlockPos anchorOffset = pos.subtract(setup.getBlockPos());
        VehicleSetupShipPosition ship = VehicleSetupShipPosition.at(player.level(), pos);
        recordAction(player, setup, VehicleSetupAction.removeBlock(ship == null ? -1L : ship.shipId(),
                ship == null ? null : ship.offset(), anchorOffset, player.level().getBlockState(pos)));
        player.displayClientMessage(Component.literal("Recorded removal: " + setup.actionSummary() + "."), true);
    }

    private static VehicleSetupBlockEntity activeSetup(ServerPlayer player) {
        if (REPLAYING_INTERACTIONS.contains(player.getUUID())) return null;
        BlockPos anchor = ACTIVE_RECORDINGS.get(player.getUUID());
        if (anchor == null) return null;
        Level level = player.level();
        if (level.getBlockEntity(anchor) instanceof VehicleSetupBlockEntity setup) return setup;
        ACTIVE_RECORDINGS.remove(player.getUUID());
        return null;
    }

    private static VehicleSetupBlockEntity activeTransmitterSetup(ServerPlayer player) {
        if (REPLAYING_INTERACTIONS.contains(player.getUUID())) return null;
        BlockPos anchor = ACTIVE_TRANSMITTER_RECORDINGS.get(player.getUUID());
        if (anchor == null) return null;
        if (player.level().getBlockEntity(anchor) instanceof VehicleSetupBlockEntity setup) return setup;
        ACTIVE_TRANSMITTER_RECORDINGS.remove(player.getUUID());
        return null;
    }

    private static void finalizeLeftClick(ServerPlayer player, PendingLeftClick pending) {
        if (pending.recordedAction() != null || !player.level().getBlockState(pending.pos()).equals(pending.initialState())) return;
        VehicleSetupBlockEntity setup = activeSetup(player);
        if (setup == null || !setup.getBlockPos().equals(pending.anchor())) return;
        VehicleSetupShipPosition ship = VehicleSetupShipPosition.at(player.level(), pending.pos());
        VehicleSetupAction action = VehicleSetupAction.leftClickBlock(
                ship == null ? -1L : ship.shipId(), ship == null ? null : ship.offset(),
                pending.pos().subtract(setup.getBlockPos()), pending.item(), pending.face(), pending.sneaking());
        recordAction(player, setup, action);
        pending.setRecordedAction(action);
        VSAnalogWarfare.LOGGER.debug("[VSAW] Generic left-click recorded: block={} pos={} player={}",
                blockId(pending.initialState()), pending.pos(), player.getGameProfile().getName());
        player.displayClientMessage(Component.literal("Vehicle setup recorded block left-click: "
                + setup.actionSummary() + "."), true);
    }

    private static void discardPendingLeftClick(ServerPlayer player, BlockPos pos, boolean blockWasBroken) {
        PendingLeftClick pending = PENDING_LEFT_CLICKS.get(player.getUUID());
        if (pending == null || !pending.pos().equals(pos)) return;
        if (pending.recordedAction() != null) {
            VehicleSetupBlockEntity setup = activeSetup(player);
            if (setup != null && setup.getBlockPos().equals(pending.anchor())) setup.removeAction(pending.recordedAction());
        }
        PENDING_LEFT_CLICKS.remove(player.getUUID());
        VSAnalogWarfare.LOGGER.debug("[VSAW] Generic left-click discarded: block={} pos={} player={} reason={}",
                blockId(pending.initialState()), pos, player.getGameProfile().getName(),
                blockWasBroken ? "block broken; recorded as removal" : "click aborted");
    }

    private static void claimGenericInteraction(ServerPlayer player, BlockPos pos) {
        PendingInteraction interaction = PENDING_INTERACTIONS.get(player.getUUID());
        if (interaction != null && interaction.pos().equals(pos)) PENDING_INTERACTIONS.remove(player.getUUID());
    }

    private static void recordGenericInteraction(ServerPlayer player, VehicleSetupBlockEntity setup,
                                                  PendingInteraction interaction) {
        String blockId = BuiltInRegistries.BLOCK.getKey(
                player.level().getBlockState(interaction.pos()).getBlock()).toString();
        VehicleSetupShipPosition ship = VehicleSetupShipPosition.at(player.level(), interaction.pos());
        recordAction(player, setup, VehicleSetupAction.interactWithBlock(ship == null ? -1L : ship.shipId(),
                ship == null ? null : ship.offset(), interaction.pos().subtract(setup.getBlockPos()),
                interaction.item(), interaction.hand(), interaction.face(), interaction.hitOffset(), interaction.sneaking()));
        VSAnalogWarfare.LOGGER.debug("[VSAW] Generic interaction recorded: block={} pos={} player={} handled={}.",
                blockId, interaction.pos(), player.getGameProfile().getName(), interaction.handled());
        player.displayClientMessage(Component.literal("Vehicle setup recorded block interaction: "
                + setup.actionSummary() + "."), true);
    }

    private static void recordTallyhoEntity(ServerPlayer player, VehicleSetupBlockEntity setup,
                                            TallyhoCompat.CapturedEntity captured) {
        VehicleSetupShipPosition ship = VehicleSetupShipPosition.at(player.level(), captured.supportPosition());
        recordAction(player, setup, VehicleSetupAction.spawnTallyhoEntity(ship == null ? -1L : ship.shipId(),
                ship == null ? null : ship.offset(), captured.supportPosition().subtract(setup.getBlockPos()),
                captured.positionOffset(), captured.entityId(), captured.baseYaw(), captured.variant(), captured.state()));
    }

    public static void beginInteractionReplay(ServerPlayer player) {
        REPLAYING_INTERACTIONS.add(player.getUUID());
    }

    public static void endInteractionReplay(ServerPlayer player) {
        REPLAYING_INTERACTIONS.remove(player.getUUID());
    }

    public static void runScheduled(ServerPlayer player, VehicleSetupBlockEntity setup) {
        if (PENDING_RUNS.containsKey(setup.getBlockPos())) {
            player.displayClientMessage(Component.literal("Vehicle setup is already running."), true);
            return;
        }
        java.util.List<VehicleSetupAction> actions = setup.actions();
        java.util.List<VehicleSetupAction> removals = setup.markedRemovals();
        if (actions.isEmpty() && removals.isEmpty()) {
            player.displayClientMessage(Component.literal("Vehicle setup has no saved actions."), true);
            return;
        }
        PendingRun run = new PendingRun(player, player.level(), actions, removals, setup.removalDelayTicks(),
                actions.isEmpty() ? setup.removalDelayTicks() : actions.get(0).delayBeforeTicks());
        if (actions.isEmpty()) run.removing = true;
        PENDING_RUNS.put(setup.getBlockPos(), run);
    }

    private static void recordAction(ServerPlayer player, VehicleSetupBlockEntity setup, VehicleSetupAction action) {
        setup.addAction(timedAction(player, action));
    }

    private static VehicleSetupAction timedAction(ServerPlayer player, VehicleSetupAction action) {
        long now = player.level().getGameTime();
        long previous = LAST_RECORDED_TICKS.getOrDefault(player.getUUID(), now);
        LAST_RECORDED_TICKS.put(player.getUUID(), now);
        return action.withDelayBeforeTicks((int) Math.min(Integer.MAX_VALUE, Math.max(0L, now - previous)));
    }

    private record PendingInteraction(BlockPos anchor, BlockPos pos, BlockState initialState, ItemStack item,
                                      InteractionHand hand, Direction face, Vec3 hitOffset, boolean handled,
                                        boolean sneaking) { }

    private record PendingTallyhoPlacement(BlockPos anchor, long captureTick, int attempt, Vec3 position,
                                           Vec3 alternatePosition, java.util.Set<UUID> existingEntities) {
        private PendingTallyhoPlacement withNextAttempt() {
            return new PendingTallyhoPlacement(anchor, captureTick + 1L, attempt + 1,
                    position, alternatePosition, existingEntities);
        }
    }

    private static final class PendingRun {
        private final ServerPlayer player;
        private final Level level;
        private final java.util.List<VehicleSetupAction> actions;
        private final java.util.List<VehicleSetupAction> removals;
        private final int removalDelay;
        private boolean removing;
        private int index;
        private int remainingTicks;
        private int succeeded;
        private int removalSucceeded;
        private String firstError;

        private PendingRun(ServerPlayer player, Level level, java.util.List<VehicleSetupAction> actions,
                           java.util.List<VehicleSetupAction> removals, int removalDelay, int remainingTicks) {
            this.player = player;
            this.level = level;
            this.actions = actions;
            this.removals = removals;
            this.removalDelay = removalDelay;
            this.remainingTicks = remainingTicks;
        }
    }

    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static final class PendingLeftClick {
        private final BlockPos anchor;
        private final BlockPos pos;
        private final BlockState initialState;
        private final ItemStack item;
        private final Direction face;
        private final boolean sneaking;
        private VehicleSetupAction recordedAction;

        private PendingLeftClick(BlockPos anchor, BlockPos pos, BlockState initialState, ItemStack item,
                                 Direction face, boolean sneaking, VehicleSetupAction recordedAction) {
            this.anchor = anchor;
            this.pos = pos;
            this.initialState = initialState;
            this.item = item;
            this.face = face;
            this.sneaking = sneaking;
            this.recordedAction = recordedAction;
        }

        private BlockPos anchor() { return anchor; }
        private BlockPos pos() { return pos; }
        private BlockState initialState() { return initialState; }
        private ItemStack item() { return item; }
        private Direction face() { return face; }
        private boolean sneaking() { return sneaking; }
        private VehicleSetupAction recordedAction() { return recordedAction; }
        private void setRecordedAction(VehicleSetupAction action) { recordedAction = action; }
    }
}
