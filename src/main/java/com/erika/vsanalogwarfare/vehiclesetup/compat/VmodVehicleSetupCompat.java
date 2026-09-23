package com.erika.vsanalogwarfare.vehiclesetup.compat;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.registry.ModBlocks;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupAction;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupActionType;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupBlockEntity;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupExecutor;
import com.erika.vsanalogwarfare.vehiclemount.VehicleMountHandleBlockEntity;
import com.erika.vsanalogwarfare.scope.ScopeBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VmodVehicleSetupCompat {
    private static final ConcurrentHashMap<Integer, UUID> PLACERS = new ConcurrentHashMap<>();
    /** Freshly pasted ships may not have computed their ship AABB yet; retry this many times, 2 ticks apart. */
    private static final int AABB_READY_MAX_ATTEMPTS = 10;
    private static final ConcurrentHashMap<BlockPos, Map<Long, Object>> PLACED_SHIP_MAPPINGS = new ConcurrentHashMap<>();
    /** Runtime ship id -> mapping, so a pasted setup still resolves after the ship moves away from its paste-time block position. */
    private static final ConcurrentHashMap<Long, Map<Long, Object>> SHIP_KEYED_MAPPINGS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, String> PLACEMENT_IDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, PendingRun> PENDING_RUNS = new ConcurrentHashMap<>();
    private static final ArrayList<PendingRegistration> PENDING_REGISTRATIONS = new ArrayList<>();
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
        if (!shipBoundsReady(pairs)) {
            // VS2 computes a pasted ship's AABB on its worker jobs; scanning before it exists
            // would bail per ship and silently skip every rebase on that ship. Retry on a real
            // tick cadence (server.execute would run inline when already on the main thread).
            PENDING_REGISTRATIONS.add(new PendingRegistration(level, pairs));
            return;
        }
        registerReady(level, pairs);
    }

    private static void registerReady(ServerLevel level, List<?> pairs) {
        Map<Long, Object> ships = new HashMap<>();
        for (Object pair : pairs) {
            Object ship = pairValue(pair, "getFirst"); Object id = pairValue(pair, "getSecond");
            if (ship != null && id instanceof Number number) {
                ships.put(number.longValue(), ship);
                // Also index the ship under its fresh runtime id: recorded actions are rebased
                // onto that id at paste, and the schematic saved from an already-pasted ship
                // stores it instead of the original one.
                long runtimeShipId = VehicleSetupReflection.shipId(ship);
                if (runtimeShipId >= 0L) ships.put(runtimeShipId, ship);
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
        Map<Long, Object> ships = placedShipsFor(level, setupPos);
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
        // The in-memory id is lost on restart; the block entity's persisted copy keeps the
        // click-run able to isolate after one.
        String placementId = PLACEMENT_IDS.get(setupPos);
        if (placementId == null) placementId = setup.enderPlacementId();
        PendingRun run = new PendingRun(level, player, actions, removals, setup.removalDelayTicks(), ships,
                placementId, actions.isEmpty() ? setup.removalDelayTicks() : actions.get(0).delayBeforeTicks());
        if (actions.isEmpty()) run.removing = true;
        PENDING_RUNS.put(setupPos, run);
        VSAnalogWarfare.LOGGER.debug("[VSAW setup-debug] Setup started: gameTime={}, placementId={}, setup={}, "
                        + "actions={}, removals={}, mappedShips={}",
                level.getGameTime(), run.placementId, setupPos, actions.size(), removals.size(), ships.size());
    }

    /**
     * The paste-time block position of a setup block goes stale as soon as the pasted ship
     * settles or moves, so fall back to resolving the ship from the block's current position
     * and looking the placement mapping up by its runtime ship id.
     */
    @Nullable private static Map<Long, Object> placedShipsFor(ServerLevel level, BlockPos setupPos) {
        Map<Long, Object> ships = PLACED_SHIP_MAPPINGS.get(setupPos);
        if (ships != null) return ships;
        Object ship = VehicleSetupReflection.findShip(level, setupPos);
        if (ship == null) return null;
        long shipId = VehicleSetupReflection.shipId(ship);
        Map<Long, Object> shipKeyed = shipId < 0L ? null : SHIP_KEYED_MAPPINGS.get(shipId);
        if (shipKeyed != null) {
            VSAnalogWarfare.LOGGER.debug("[VSAW setup-debug] Setup at {} resolved via runtime ship id {} "
                    + "(paste-time position lookup missed)", setupPos, shipId);
        }
        return shipKeyed;
    }

    @Mod.EventBusSubscriber(modid = VSAnalogWarfare.MOD_ID)
    public static final class Events {
        private Events() { }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            if (!PENDING_REGISTRATIONS.isEmpty()) {
                for (PendingRegistration pending : new ArrayList<>(PENDING_REGISTRATIONS)) {
                    if (--pending.delayTicks > 0) continue;
                    PENDING_REGISTRATIONS.remove(pending);
                    if (shipBoundsReady(pending.pairs)) {
                        registerReady(pending.level, pending.pairs);
                        continue;
                    }
                    if (pending.attemptsLeft-- > 0) {
                        PENDING_REGISTRATIONS.add(pending);
                        continue;
                    }
                    VSAnalogWarfare.LOGGER.warn("[VSAW] Pasted ship AABB still not ready after {} retries; scanning "
                            + "anyway. Ships without an AABB get no paste rebase and rely on the use-time link heal.",
                            AABB_READY_MAX_ATTEMPTS);
                    registerReady(pending.level, pending.pairs);
                }
            }
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
                        if (!PLACED_SHIP_MAPPINGS.containsValue(run.ships)) {
                            SHIP_KEYED_MAPPINGS.values().removeIf(ships -> ships == run.ships);
                        }
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
                        && !level.getBlockState(pos).is(ModBlocks.SCOPE_BLOCK.get())
                        && !level.getBlockState(pos).is(ModBlocks.STABILIZER.get())
                        && !level.getBlockState(pos).is(ModBlocks.DECORATION_BEARING.get())
                        && !EnderTransmissionCompat.isEnergyTransmitter(level.getBlockState(pos))) continue;
                BlockEntity entity = level.getBlockEntity(pos);
                if (entity instanceof VehicleSetupBlockEntity setup) {
                    BlockPos setupPos = pos.immutable();
                    PLACED_SHIP_MAPPINGS.put(setupPos, ships);
                    PLACEMENT_IDS.put(setupPos, placementId);
                    long runtimeShipId = VehicleSetupReflection.shipId(ship);
                    if (runtimeShipId >= 0L) SHIP_KEYED_MAPPINGS.put(runtimeShipId, ships);
                    VSAnalogWarfare.LOGGER.debug("[VSAW setup-debug] Setup discovered: gameTime={}, setup={}, "
                                    + "runtimeShipId={}, placementId={}",
                            level.getGameTime(), setupPos, id, placementId);
                    setup.rebaseAfterSchematicPlacement(ships);
                    // The transmitter isolation rename is applied only when the player runs
                    // the setup; the id just waits here for that click.
                    setup.setEnderPlacementId(placementId);
                }
                if (entity instanceof KineticBlockEntity transmitter && EnderTransmissionCompat.isEnergyTransmitter(entity)
                        && transmitter.getPersistentData().getBoolean(EnderTransmissionCompat.REMAPPED_TAG)) {
                    // A schematic saved from an already-renamed ship keeps the old copy's
                    // remapped flag in its NBT; clear it so the click-run re-isolates this
                    // placement instead of silently skipping and sharing the old frequency.
                    transmitter.getPersistentData().remove(EnderTransmissionCompat.REMAPPED_TAG);
                    transmitter.setChanged();
                }
                if (entity instanceof VehicleMountHandleBlockEntity handle) {
                    handle.setPlacedShips(ships);
                    handle.remapPlacedPosition(pos.immutable());
                    handle.rebaseAfterSchematicPlacement(ships);
                }
                if (entity instanceof ScopeBlockEntity scope) {
                    scope.initializeAfterSchematicPlacement(ships);
                }
                if (entity instanceof VmodPasteRebasable rebasable) {
                    rebasable.rebaseAfterVmodPaste(ships);
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

    private static boolean shipBoundsReady(List<?> pairs) {
        for (Object pair : pairs) {
            Object ship = pairValue(pair, "getFirst");
            if (ship != null && !hasShipAabb(ship)) return false;
        }
        return true;
    }

    private static boolean hasShipAabb(Object ship) {
        try {
            return VehicleSetupReflection.invoke(ship, "getShipAABB") != null;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static int coordinate(Object box, String name) throws ReflectiveOperationException {
        Object value = box.getClass().getMethod(name).invoke(box);
        return value instanceof Number number ? number.intValue() : 0;
    }

    /** A placement whose ships' AABBs VS2 had not computed yet; retried a few ticks apart. */
    private static final class PendingRegistration {
        final ServerLevel level;
        final List<?> pairs;
        int attemptsLeft = AABB_READY_MAX_ATTEMPTS;
        int delayTicks = 2;

        PendingRegistration(ServerLevel level, List<?> pairs) {
            this.level = level;
            this.pairs = pairs;
        }
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
