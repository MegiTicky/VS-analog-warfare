package com.erika.vsanalogwarfare.vehiclesetup;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
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
    private static final Map<UUID, PendingLeftClick> PENDING_LEFT_CLICKS = new HashMap<>();
    private static final Set<UUID> REPLAYING_INTERACTIONS = new HashSet<>();

    private VehicleSetupRecordingManager() { }

    public static void toggle(ServerPlayer player, VehicleSetupBlockEntity setup) {
        UUID playerId = player.getUUID();
        BlockPos current = ACTIVE_RECORDINGS.get(playerId);
        if (setup.getBlockPos().equals(current)) {
            ACTIVE_RECORDINGS.remove(playerId);
            player.displayClientMessage(Component.literal("Vehicle setup recording stopped: " + setup.actionSummary() + "."), true);
            return;
        }
        setup.clearActions();
        OptionalModCompatibility.warnIfIssues(player);
        ACTIVE_RECORDINGS.put(playerId, setup.getBlockPos());
        player.displayClientMessage(Component.literal("Vehicle setup recording started. Place, break, or interact with blocks normally, then use the recorder on this block again to stop."), true);
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
        setup.addAction(VehicleSetupAction.linkDbwBackups(
                sourcePosition.shipId(), sourcePosition.offset(),
                targetPosition.shipId(), targetPosition.offset()));
        player.displayClientMessage(Component.literal("Vehicle setup recorded DBW relink: " + setup.actionSummary() + "."), true);
    }

    public static void recordControllerLink(net.minecraft.world.entity.player.Player player, BlockPos hub,
                                            ItemStack controller) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        VehicleSetupBlockEntity setup = activeSetup(serverPlayer);
        if (setup == null) return;
        claimGenericInteraction(serverPlayer, hub);
        VehicleSetupShipPosition hubPosition = VehicleSetupShipPosition.at(serverPlayer.level(), hub);
        if (hubPosition == null) {
            serverPlayer.displayClientMessage(Component.literal("Vehicle setup could not record controller link: hub is not on a loaded ship."), true);
            return;
        }
        setup.addAction(VehicleSetupAction.createTweakedController(hubPosition.shipId(), hubPosition.offset(), controller));
        serverPlayer.displayClientMessage(Component.literal("Vehicle setup recorded controller link: " + setup.actionSummary() + "."), true);
    }

    public static void recordTrackworkStiffness(net.minecraft.world.entity.player.Player player,
                                                BlockPos clicked, ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer) || !TrackworkCompat.isStiffnessTool(stack)
                || !TrackworkCompat.isStiffnessTarget(serverPlayer.level(), clicked)) return;
        VehicleSetupBlockEntity setup = activeSetup(serverPlayer);
        if (setup == null) return;
        claimGenericInteraction(serverPlayer, clicked);
        Float stiffness = TrackworkCompat.readStiffness(serverPlayer.level(), clicked);
        if (stiffness == null) return;
        setup.addAction(VehicleSetupAction.setTrackworkStiffness(stiffness));
        serverPlayer.displayClientMessage(Component.literal(
                "Vehicle setup recorded suspension stiffness " + stiffness + "x: " + setup.actionSummary() + "."), true);
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
        setup.upsertEnderTransmitter(VehicleSetupAction.configureEnderTransmitter(
                ship.shipId(), ship.offset(), pos.subtract(setup.getBlockPos()), channel, password));
        player.displayClientMessage(Component.literal("Vehicle setup recorded Ender transmitter: "
                + setup.actionSummary() + "."), true);
    }

    public static void recordEnderTransmitterConfiguration(ServerPlayer player, KineticBlockEntity transmitter) {
        CompoundTag data = transmitter.getPersistentData();
        recordEnderTransmitter(player, transmitter.getBlockPos(), data.getInt("channel"),
                data.getString("password"));
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
            VSAnalogWarfare.LOGGER.info("[VSAW] Generic left-click queued: block={} pos={} player={} item={} canceled={} useBlock={} useItem={}",
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
        setup.addAction(VehicleSetupAction.spawnTallyhoEntity(ship == null ? -1L : ship.shipId(),
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
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer player)
                || event.isCanceled() && !event.getCancellationResult().consumesAction()) return;
        VehicleSetupBlockEntity setup = activeSetup(player);
        ItemStack item = event.getItemStack();
        if (setup == null || event.getPos().equals(setup.getBlockPos()) || item.getItem() instanceof BlockItem
                || item.getItem() instanceof AnalogScrewdriverItem) return;
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        if (state.isAir()) return;
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (!event.isCanceled() && event.getUseBlock() == Event.Result.DENY
                && event.getUseItem() == Event.Result.DENY) {
            VSAnalogWarfare.LOGGER.info("[VSAW] Generic interaction discarded: block={} player={} both uses denied.",
                    blockId, player.getGameProfile().getName());
            return;
        }
        BlockHitResult hit = event.getHitVec();
        Vec3 hitOffset = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        PENDING_INTERACTIONS.put(player.getUUID(), new PendingInteraction(setup.getBlockPos(), pos, state,
                item.copy(), event.getHand(), hit.getDirection(), hitOffset, event.isCanceled(), player.isShiftKeyDown()));
        VSAnalogWarfare.LOGGER.info("[VSAW] Generic interaction queued: block={} pos={} player={} hand={} item={} "
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
                VSAnalogWarfare.LOGGER.info("[VSAW] Generic interaction discarded: recording anchor disappeared for player={}.",
                        player.getGameProfile().getName());
                continue;
            }
            if (!interaction.handled() && player.level().getBlockState(interaction.pos()).equals(interaction.initialState())) {
                String blockId = BuiltInRegistries.BLOCK.getKey(interaction.initialState().getBlock()).toString();
                VSAnalogWarfare.LOGGER.info("[VSAW] Generic interaction discarded: block={} pos={} player={} "
                                + "was not canceled and BlockState did not change.", blockId, interaction.pos(),
                        player.getGameProfile().getName());
                continue;
            }
            recordGenericInteraction(player, setup, interaction);
        }
    }

    private static void recordPlace(ServerPlayer player, BlockPos pos, BlockState state) {
        VehicleSetupBlockEntity setup = activeSetup(player);
        if (setup == null || state.is(ModBlocks.VEHICLE_SETUP.get())) return;
        BlockPos anchorOffset = pos.subtract(setup.getBlockPos());
        VehicleSetupShipPosition ship = VehicleSetupShipPosition.at(player.level(), pos);
        setup.addAction(VehicleSetupAction.placeBlock(ship == null ? -1L : ship.shipId(),
                ship == null ? null : ship.offset(), anchorOffset, state));
        player.displayClientMessage(Component.literal("Recorded placement: " + setup.actionSummary() + "."), true);
    }

    private static void recordRemove(ServerPlayer player, BlockPos pos) {
        VehicleSetupBlockEntity setup = activeSetup(player);
        if (setup == null || pos.equals(setup.getBlockPos())) return;
        BlockPos anchorOffset = pos.subtract(setup.getBlockPos());
        VehicleSetupShipPosition ship = VehicleSetupShipPosition.at(player.level(), pos);
        setup.addAction(VehicleSetupAction.removeBlock(ship == null ? -1L : ship.shipId(),
                ship == null ? null : ship.offset(), anchorOffset));
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

    private static void finalizeLeftClick(ServerPlayer player, PendingLeftClick pending) {
        if (pending.recordedAction() != null || !player.level().getBlockState(pending.pos()).equals(pending.initialState())) return;
        VehicleSetupBlockEntity setup = activeSetup(player);
        if (setup == null || !setup.getBlockPos().equals(pending.anchor())) return;
        VehicleSetupShipPosition ship = VehicleSetupShipPosition.at(player.level(), pending.pos());
        VehicleSetupAction action = VehicleSetupAction.leftClickBlock(
                ship == null ? -1L : ship.shipId(), ship == null ? null : ship.offset(),
                pending.pos().subtract(setup.getBlockPos()), pending.item(), pending.face(), pending.sneaking());
        setup.addAction(action);
        pending.setRecordedAction(action);
        VSAnalogWarfare.LOGGER.info("[VSAW] Generic left-click recorded: block={} pos={} player={}",
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
        VSAnalogWarfare.LOGGER.info("[VSAW] Generic left-click discarded: block={} pos={} player={} reason={}",
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
        setup.addAction(VehicleSetupAction.interactWithBlock(ship == null ? -1L : ship.shipId(),
                ship == null ? null : ship.offset(), interaction.pos().subtract(setup.getBlockPos()),
                interaction.item(), interaction.hand(), interaction.face(), interaction.hitOffset(), interaction.sneaking()));
        VSAnalogWarfare.LOGGER.info("[VSAW] Generic interaction recorded: block={} pos={} player={} handled={}.",
                blockId, interaction.pos(), player.getGameProfile().getName(), interaction.handled());
        player.displayClientMessage(Component.literal("Vehicle setup recorded block interaction: "
                + setup.actionSummary() + "."), true);
    }

    public static void beginInteractionReplay(ServerPlayer player) {
        REPLAYING_INTERACTIONS.add(player.getUUID());
    }

    public static void endInteractionReplay(ServerPlayer player) {
        REPLAYING_INTERACTIONS.remove(player.getUUID());
    }

    private record PendingInteraction(BlockPos anchor, BlockPos pos, BlockState initialState, ItemStack item,
                                      InteractionHand hand, Direction face, Vec3 hitOffset, boolean handled,
                                      boolean sneaking) { }

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
