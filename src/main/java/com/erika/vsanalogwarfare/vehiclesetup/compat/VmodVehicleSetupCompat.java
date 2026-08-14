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
import net.minecraft.network.chat.Component;

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
    private VmodVehicleSetupCompat() { }

    public static void rememberPlacement(UUID player, List<?> ships) { PLACERS.put(System.identityHashCode(ships), player); }
    public static void placementComplete(Object item) {
        try {
            Object levelValue = VehicleSetupReflection.invoke(item, "getLevel");
            Object shipsValue = VehicleSetupReflection.invoke(item, "getShips");
            if (!(levelValue instanceof ServerLevel level) || !(shipsValue instanceof List<?> ships)) return;
            UUID player = PLACERS.remove(System.identityHashCode(ships));
             level.getServer().execute(() -> register(level, ships));
        } catch (ReflectiveOperationException | LinkageError error) {
            VSAnalogWarfare.LOGGER.warn("[VSAW] Could not process VMod placement: {}", error.getClass().getSimpleName());
        }
    }

    private static void register(ServerLevel level, List<?> pairs) {
        Map<Long, Object> ships = new HashMap<>();
        for (Object pair : pairs) {
            Object ship = pairValue(pair, "getFirst"); Object id = pairValue(pair, "getSecond");
            if (ship != null && id instanceof Number number) ships.put(number.longValue(), ship);
        }
        for (Object ship : ships.values()) scanShip(level, ship, ships);
    }

    public static void runSetupOrLocal(ServerLevel level, BlockPos setupPos, ServerPlayer player,
                                       VehicleSetupBlockEntity setup) {
        Map<Long, Object> ships = PLACED_SHIP_MAPPINGS.get(setupPos);
        if (ships == null) {
            setup.run(player);
            return;
        }
        int succeeded = 0;
        String firstError = null;
        for (VehicleSetupAction action : setup.actions()) {
            String error = action.type() == VehicleSetupActionType.LINK_DBW_BACKUPS
                    ? runDbw(level, setupPos, action, ships)
                    : VehicleSetupExecutor.run(level, setupPos, player, action, ships);
            if (error == null) succeeded++; else if (firstError == null) firstError = error;
        }
        player.displayClientMessage(Component.literal(firstError == null
                ? "Vehicle setup complete: " + succeeded + " actions."
                : "Vehicle setup: " + succeeded + " complete. " + firstError), true);
        PLACED_SHIP_MAPPINGS.remove(setupPos);
    }

    private static void scanShip(ServerLevel level, Object ship, Map<Long, Object> ships) {
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
                if (entity instanceof VehicleSetupBlockEntity) PLACED_SHIP_MAPPINGS.put(pos.immutable(), ships);
            }
        } catch (ReflectiveOperationException ignored) { }
    }

    @Nullable private static String runDbw(ServerLevel level, BlockPos setupPos, VehicleSetupAction action, Map<Long, Object> ships) {
        Object sourceShip = ships.get(action.targetShipId()), targetShip = ships.get(action.secondaryShipId());
        if (sourceShip == null || targetShip == null || action.targetOffset() == null || action.secondaryOffset() == null) {
            VSAnalogWarfare.LOGGER.warn("[VSAW] DBW link at {} could not resolve both placed ships", setupPos);
            return "DBW link could not resolve both placed ships";
        }
        BlockPos source = VehicleSetupReflection.positionOnShip(sourceShip, action.targetOffset());
        BlockPos target = VehicleSetupReflection.positionOnShip(targetShip, action.secondaryOffset());
        if (source == null || target == null) {
            VSAnalogWarfare.LOGGER.warn("[VSAW] DBW link at {} could not resolve backup positions", setupPos);
            return "DBW link could not resolve backup positions";
        }
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
}
