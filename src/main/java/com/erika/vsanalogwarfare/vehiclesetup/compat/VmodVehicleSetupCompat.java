package com.erika.vsanalogwarfare.vehiclesetup.compat;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.registry.ModBlocks;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupAction;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupActionType;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupBlockEntity;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VmodVehicleSetupCompat {
    private static final ConcurrentHashMap<Integer, UUID> PLACERS = new ConcurrentHashMap<>();
    private VmodVehicleSetupCompat() { }

    public static void rememberPlacement(UUID player, List<?> ships) { PLACERS.put(System.identityHashCode(ships), player); }
    public static void placementComplete(Object item) {
        try {
            Object levelValue = VehicleSetupReflection.invoke(item, "getLevel");
            Object shipsValue = VehicleSetupReflection.invoke(item, "getShips");
            if (!(levelValue instanceof ServerLevel level) || !(shipsValue instanceof List<?> ships)) return;
            UUID player = PLACERS.remove(System.identityHashCode(ships));
            level.getServer().execute(() -> run(level, player, ships));
        } catch (ReflectiveOperationException | LinkageError error) {
            VSAnalogWarfare.LOGGER.warn("[VSAW] Could not process VMod placement: {}", error.getClass().getSimpleName());
        }
    }

    private static void run(ServerLevel level, @Nullable UUID playerId, List<?> pairs) {
        ServerPlayer player = playerId == null ? null : level.getServer().getPlayerList().getPlayer(playerId);
        Map<Long, Object> ships = new HashMap<>();
        for (Object pair : pairs) {
            Object ship = pairValue(pair, "getFirst"); Object id = pairValue(pair, "getSecond");
            if (ship != null && id instanceof Number number) ships.put(number.longValue(), ship);
        }
        for (Object ship : ships.values()) scanShip(level, player, ship, ships);
    }

    private static void scanShip(ServerLevel level, @Nullable ServerPlayer player, Object ship, Map<Long, Object> ships) {
        try {
            Object box = VehicleSetupReflection.invoke(ship, "getShipAABB"); if (box == null) return;
            int minX = coordinate(box, "minX"), minY = coordinate(box, "minY"), minZ = coordinate(box, "minZ");
            int maxX = coordinate(box, "maxX"), maxY = coordinate(box, "maxY"), maxZ = coordinate(box, "maxZ");
            if ((long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) > 1_000_000L) return;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
                pos.set(x, y, z);
                if (!level.getBlockState(pos).is(ModBlocks.VEHICLE_SETUP.get())) continue;
                BlockEntity entity = level.getBlockEntity(pos);
                if (entity instanceof VehicleSetupBlockEntity setup) runSetup(level, player, setup, ships);
            }
        } catch (ReflectiveOperationException ignored) { }
    }

    private static void runSetup(ServerLevel level, @Nullable ServerPlayer player, VehicleSetupBlockEntity setup, Map<Long, Object> ships) {
        for (VehicleSetupAction action : setup.actions()) {
            if (action.type() == VehicleSetupActionType.LINK_DBW_BACKUPS) { runDbw(level, setup.getBlockPos(), action, ships); continue; }
            String error = VehicleSetupExecutor.run(level, setup.getBlockPos(), player, action);
            if (error != null) VSAnalogWarfare.LOGGER.warn("[VSAW] Vehicle setup at {} failed: {}", setup.getBlockPos(), error);
        }
    }

    private static void runDbw(ServerLevel level, BlockPos setupPos, VehicleSetupAction action, Map<Long, Object> ships) {
        Object sourceShip = ships.get(action.targetShipId()), targetShip = ships.get(action.secondaryShipId());
        if (sourceShip == null || targetShip == null || action.targetOffset() == null || action.secondaryOffset() == null) return;
        BlockPos source = VehicleSetupReflection.positionOnShip(sourceShip, action.targetOffset());
        BlockPos target = VehicleSetupReflection.positionOnShip(targetShip, action.secondaryOffset());
        if (source == null || target == null) return;
        String error = DbwCompat.linkBackups(level, source, target);
        if (error != null) VSAnalogWarfare.LOGGER.warn("[VSAW] DBW link at {} failed: {}", setupPos, error);
    }

    @Nullable private static Object pairValue(Object pair, String method) {
        try { return VehicleSetupReflection.invoke(pair, method); } catch (ReflectiveOperationException ignored) { return null; }
    }
    private static int coordinate(Object box, String name) throws ReflectiveOperationException {
        Object value = box.getClass().getMethod(name).invoke(box);
        return value instanceof Number number ? number.intValue() : 0;
    }
}
