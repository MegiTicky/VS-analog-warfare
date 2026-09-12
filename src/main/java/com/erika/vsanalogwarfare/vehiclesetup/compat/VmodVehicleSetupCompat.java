package com.erika.vsanalogwarfare.vehiclesetup.compat;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.registry.ModBlocks;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupAction;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupActionType;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupBlockEntity;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupExecutor;
import com.erika.vsanalogwarfare.vehiclemount.VehicleMountHandleBlockEntity;
import com.erika.vsanalogwarfare.scope.ScopeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VmodVehicleSetupCompat {
    private static final ConcurrentHashMap<Integer, UUID> PLACERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, Map<Long, Object>> PLACED_SHIP_MAPPINGS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, String> PLACEMENT_IDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, PendingRun> PENDING_RUNS = new ConcurrentHashMap<>();
    private VmodVehicleSetupCompat() { }

    public static void rememberPlacement(UUID player, List<?> ships) { PLACERS.put(System.identityHashCode(ships), player); }
    public static void placementComplete(Object item) {
        try {
            Object levelValue = VehicleSetupReflection.invoke(item, "getLevel");
            Object shipsValue = VehicleSetupReflection.invoke(item, "getShips");
            if (!(levelValue instanceof ServerLevel level) || !(shipsValue instanceof List<?> ships)) return;
            UUID player = PLACERS.remove(System.identityHashCode(ships));
            VSAnalogWarfare.LOGGER.debug("[VSAW setup-debug] VMod placement complete: gameTime={}, player={}, shipPairs={}",
                    level.getGameTime(), player, ships.size());
             // VMod may load schematic block-entity tags in a delayed task.
             level.getServer().execute(() -> level.getServer().execute(() -> register(level, ships)));
        } catch (ReflectiveOperationException | LinkageError error) {
            VSAnalogWarfare.LOGGER.warn("[VSAW] Could not process VMod placement: {}", error.getClass().getSimpleName());
        }
    }

    private static void register(ServerLevel level, List<?> pairs) {
        Map<Long, Object> ships = new HashMap<>();
        for (Object pair : pairs) {
            Object ship = pairValue(pair, "getFirst"); Object id = pairValue(pair, "getSecond");
            if (ship != null && id instanceof Number number) {
                ships.put(number.longValue(), ship);
                logShip("Mapped pasted ship", number.longValue(), ship);
            }
        }
        String placementId = EnderTransmissionCompat.newPlacementId();
        VSAnalogWarfare.LOGGER.debug("[VSAW setup-debug] VMod registration: gameTime={}, placementId={}, mappedShips={}",
                level.getGameTime(), placementId, ships.size());
        for (Object ship : ships.values()) scanShip(level, ship, ships, placementId);
    }

    public static void runSetupOrLocal(ServerLevel level, BlockPos setupPos, ServerPlayer player,
                                       VehicleSetupBlockEntity setup) {
        Map<Long, Object> ships = PLACED_SHIP_MAPPINGS.get(setupPos);
        if (ships == null) {
            setup.run(player);
            return;
        }
        if (PENDING_RUNS.containsKey(setupPos)) {
            player.displayClientMessage(Component.literal("Vehicle setup is already running."), true);
            return;
        }
        List<VehicleSetupAction> actions = setup.actions(), removals = setup.markedRemovals();
        if (actions.isEmpty() && removals.isEmpty()) {
            player.displayClientMessage(Component.literal("Vehicle setup has no saved actions."), true);
            return;
        }
        PendingRun run = new PendingRun(level, player, actions, removals, setup.removalDelayTicks(), ships,
                PLACEMENT_IDS.get(setupPos), actions.isEmpty() ? setup.removalDelayTicks() : actions.get(0).delayBeforeTicks());
        if (actions.isEmpty()) run.removing = true;
        PENDING_RUNS.put(setupPos, run);
        VSAnalogWarfare.LOGGER.debug("[VSAW setup-debug] Setup started: gameTime={}, placementId={}, setup={}, "
                        + "actions={}, removals={}, mappedShips={}",
                level.getGameTime(), run.placementId, setupPos, actions.size(), removals.size(), ships.size());
    }

    @Mod.EventBusSubscriber(modid = VSAnalogWarfare.MOD_ID)
    public static final class Events {
        private Events() { }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            for (Map.Entry<BlockPos, PendingRun> entry : PENDING_RUNS.entrySet()) {
                PendingRun run = entry.getValue();
                if (run.remainingTicks > 0 && --run.remainingTicks > 0) continue;
                do {
                    List<VehicleSetupAction> phaseActions = run.removing ? run.removals : run.actions;
                    VehicleSetupAction action = phaseActions.get(run.index++);
                    int actionIndex = run.index - 1;
                    String error = action.type() == VehicleSetupActionType.LINK_DBW_BACKUPS
                            ? runDbw(run.level, entry.getKey(), action, run.ships)
                            : VehicleSetupExecutor.run(run.level, entry.getKey(), run.player, action, run.ships,
                                    run.placementId, actionIndex);
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
                        StevesArmyCompat.notifySetupCompleted(run.player, run.level, entry.getKey());
                        PENDING_RUNS.remove(entry.getKey(), run);
                        PLACED_SHIP_MAPPINGS.remove(entry.getKey(), run.ships);
                        if (run.placementId != null) PLACEMENT_IDS.remove(entry.getKey(), run.placementId);
                        else PLACEMENT_IDS.remove(entry.getKey());
                        break;
                    }
                    if (!run.removing) run.player.displayClientMessage(Component.literal("Vehicle setup: " + run.index + "/"
                            + run.actions.size() + " completed."), true);
                    run.remainingTicks = phaseActions.get(run.index).delayBeforeTicks();
                } while (run.remainingTicks == 0);
            }
        }
    }

    private static void scanShip(ServerLevel level, Object ship, Map<Long, Object> ships, String placementId) {
        try {
            Object id = VehicleSetupReflection.invoke(ship, "getId");
            Object box = VehicleSetupReflection.invoke(ship, "getShipAABB"); if (box == null) return;
            int minX = coordinate(box, "minX"), minY = coordinate(box, "minY"), minZ = coordinate(box, "minZ");
            int maxX = coordinate(box, "maxX"), maxY = coordinate(box, "maxY"), maxZ = coordinate(box, "maxZ");
            VSAnalogWarfare.LOGGER.debug("[VSAW setup-debug] Scanning pasted ship: runtimeShipId={}, aabbMin=({}, {}, {}), "
                            + "aabbMax=({}, {}, {}), gameTime={}",
                    id, minX, minY, minZ, maxX, maxY, maxZ, level.getGameTime());
            if ((long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) > 1_000_000L) return;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
                pos.set(x, y, z);
                if (!level.getBlockState(pos).is(ModBlocks.VEHICLE_SETUP.get())
                        && !level.getBlockState(pos).is(ModBlocks.VEHICLE_MOUNT_HANDLE.get())
                        && !level.getBlockState(pos).is(ModBlocks.SCOPE_BLOCK.get())) continue;
                BlockEntity entity = level.getBlockEntity(pos);
                if (entity instanceof VehicleSetupBlockEntity) {
                    BlockPos setupPos = pos.immutable();
                    PLACED_SHIP_MAPPINGS.put(setupPos, ships);
                    PLACEMENT_IDS.put(setupPos, placementId);
                    VSAnalogWarfare.LOGGER.debug("[VSAW setup-debug] Setup discovered: gameTime={}, setup={}, "
                                    + "runtimeShipId={}, placementId={}",
                            level.getGameTime(), setupPos, id, placementId);
                    runEnderTransmitterActions(level, setupPos, (VehicleSetupBlockEntity) entity, ships, placementId);
                }
                if (entity instanceof VehicleMountHandleBlockEntity handle) {
                    handle.setPlacedShips(ships);
                    handle.remapPlacedPosition(pos.immutable());
                }
                if (entity instanceof ScopeBlockEntity scope) {
                    scope.initializeAfterSchematicPlacement(ships);
                }
            }
        } catch (ReflectiveOperationException ignored) { }
    }

    private static void logShip(String label, long originalShipId, Object ship) {
        try {
            Object runtimeId = VehicleSetupReflection.invoke(ship, "getId");
            Object box = VehicleSetupReflection.invoke(ship, "getShipAABB");
            if (box == null) {
                VSAnalogWarfare.LOGGER.debug("[VSAW setup-debug] {}: originalShipId={}, runtimeShipId={}, aabb=missing",
                        label, originalShipId, runtimeId);
                return;
            }
            VSAnalogWarfare.LOGGER.debug("[VSAW setup-debug] {}: originalShipId={}, runtimeShipId={}, "
                            + "aabbMin=({}, {}, {}), aabbMax=({}, {}, {})",
                    label, originalShipId, runtimeId,
                    coordinate(box, "minX"), coordinate(box, "minY"), coordinate(box, "minZ"),
                    coordinate(box, "maxX"), coordinate(box, "maxY"), coordinate(box, "maxZ"));
        } catch (ReflectiveOperationException ignored) {
            VSAnalogWarfare.LOGGER.debug("[VSAW setup-debug] {}: originalShipId={}, runtimeShipId=unknown, aabb=unavailable",
                    label, originalShipId);
        }
    }

    private static void runEnderTransmitterActions(ServerLevel level, BlockPos setupPos,
                                                   VehicleSetupBlockEntity setup, Map<Long, Object> ships,
                                                   String placementId) {
        for (VehicleSetupAction action : setup.actions()) {
            if (action.type() != VehicleSetupActionType.CONFIGURE_ENDER_TRANSMITTER) continue;
            Object ship = ships.get(action.targetShipId());
            BlockPos target = ship == null || action.shipOffset() == null ? null
                    : VehicleSetupExecutor.target(level, setupPos, action, ships);
            if (target == null) {
                VSAnalogWarfare.LOGGER.warn("[VSAW] Ender transmitter at {} could not resolve after paste", setupPos);
                continue;
            }
            String error = EnderTransmissionCompat.configure(level, target, action, placementId);
            if (error != null) VSAnalogWarfare.LOGGER.warn("[VSAW] Ender transmitter at {}: {}", target, error);
        }
    }

    @Nullable private static String runDbw(ServerLevel level, BlockPos setupPos, VehicleSetupAction action, Map<Long, Object> ships) {
        Object sourceShip = ships.get(action.targetShipId()), targetShip = ships.get(action.secondaryShipId());
        if (sourceShip == null || targetShip == null || action.targetOffset() == null || action.secondaryOffset() == null) {
            VSAnalogWarfare.LOGGER.warn("[VSAW] DBW link at {} could not resolve both placed ships", setupPos);
            return "DBW link could not resolve both placed ships";
        }
        BlockPos source = DbwCompat.resolveBackup(level, sourceShip, action.targetOffset(), targetShip);
        BlockPos target = DbwCompat.resolveBackup(level, targetShip, action.secondaryOffset(), sourceShip);
        if (source == null || target == null) {
            VSAnalogWarfare.LOGGER.warn("[VSAW] DBW link at {} could not resolve backup positions", setupPos);
            return "DBW link could not resolve backup positions";
        }
        VSAnalogWarfare.LOGGER.info("[VSAW] DBW relink positions: sourceShipId={}, source={}, targetShipId={}, target={}",
                action.targetShipId(), source, action.secondaryShipId(), target);
        String error = DbwCompat.linkBackups(level, source, target);
        if (error != null) {
            VSAnalogWarfare.LOGGER.warn("[VSAW] DBW link at {} failed: {}", setupPos, error);
        } else {
            VSAnalogWarfare.LOGGER.info("[VSAW] Restored DBW link at {}", setupPos);
        }
        return error;
    }

    @Nullable private static Object pairValue(Object pair, String method) {
        try { return VehicleSetupReflection.invoke(pair, method); } catch (ReflectiveOperationException ignored) { return null; }
    }
    private static int coordinate(Object box, String name) throws ReflectiveOperationException {
        Object value = box.getClass().getMethod(name).invoke(box);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static final class PendingRun {
        private final ServerLevel level;
        private final ServerPlayer player;
        private final List<VehicleSetupAction> actions;
        private final List<VehicleSetupAction> removals;
        private final int removalDelay;
        private final Map<Long, Object> ships;
        @Nullable private final String placementId;
        private int index;
        private boolean removing;
        private int remainingTicks;
        private int succeeded;
        private int removalSucceeded;
        @Nullable private String firstError;

        private PendingRun(ServerLevel level, ServerPlayer player, List<VehicleSetupAction> actions,
                           List<VehicleSetupAction> removals, int removalDelay, Map<Long, Object> ships,
                           @Nullable String placementId, int remainingTicks) {
            this.level = level;
            this.player = player;
            this.actions = actions;
            this.removals = removals;
            this.removalDelay = removalDelay;
            this.ships = ships;
            this.placementId = placementId;
            this.remainingTicks = remainingTicks;
        }
    }
}
