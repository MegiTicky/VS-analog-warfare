package com.erika.vsanalogwarfare.vehiclesetup.compat;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.registry.ModBlocks;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupAction;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupActionType;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupBlockEntity;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/** Bridges VMod's private placement queue without making VMod a required dependency. */
public final class VmodVehicleSetupCompat {
    private VmodVehicleSetupCompat() { }

    public static void placementComplete(Object item) {
        try {
            Object levelValue = VehicleSetupReflection.invoke(item, "getLevel");
            Object playerValue = VehicleSetupReflection.invoke(item, "getPlayer");
            Object createdShipsValue = VehicleSetupReflection.invoke(item, "getCreatedShips");
            Object oldToNewValue = VehicleSetupReflection.invoke(item, "getOldToNewId");
            if (!(levelValue instanceof ServerLevel level) || !(playerValue instanceof ServerPlayer player)
                    || !(createdShipsValue instanceof List<?> createdShips)
                    || !(oldToNewValue instanceof Map<?, ?> oldToNew)) return;
            // VMod completes block-entity loading as part of its placement tick. Defer one
            // server task so the setup block and optional ship attachments are visible.
            level.getServer().execute(() -> runForPlacedShips(level, player, createdShips, oldToNew));
        } catch (ReflectiveOperationException | LinkageError error) {
            VSAnalogWarfare.LOGGER.warn("[VSAW] Could not process VMod placement: {}",
                    error.getClass().getSimpleName());
        }
    }

    private static void runForPlacedShips(ServerLevel level, ServerPlayer player, List<?> createdShips,
                                          Map<?, ?> oldToNew) {
        Map<Long, Object> createdByNewId = new HashMap<>();
        for (Object pair : createdShips) {
            Object newId = pairValue(pair, "getFirst");
            Object ship = pairValue(pair, "getSecond");
            if (newId instanceof Number number && ship != null) {
                createdByNewId.put(number.longValue(), ship);
            }
        }

        Map<Long, Object> shipsByOriginalId = new HashMap<>();
        for (Entry<?, ?> entry : oldToNew.entrySet()) {
            if (entry.getKey() instanceof Number oldId && entry.getValue() instanceof Number newId) {
                Object ship = createdByNewId.get(newId.longValue());
                if (ship != null) shipsByOriginalId.put(oldId.longValue(), ship);
            }
        }

        Map<BlockPos, VehicleSetupBlockEntity> setups = new HashMap<>();
        for (Object ship : createdByNewId.values()) scanShip(level, ship, setups);
        for (Entry<BlockPos, VehicleSetupBlockEntity> entry : setups.entrySet()) {
            runSetup(level, entry.getKey(), entry.getValue(), player, shipsByOriginalId);
        }

    }

    private static void scanShip(ServerLevel level, Object ship,
                                 Map<BlockPos, VehicleSetupBlockEntity> setups) {
        try {
            Object box = VehicleSetupReflection.invoke(ship, "getShipAABB");
            if (box == null) return;
            int minX = coordinate(box, "minX"), minY = coordinate(box, "minY"), minZ = coordinate(box, "minZ");
            int maxX = coordinate(box, "maxX"), maxY = coordinate(box, "maxY"), maxZ = coordinate(box, "maxZ");
            if ((long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) > 1_000_000L) {
                VSAnalogWarfare.LOGGER.warn("[VSAW] Skipped vehicle setup scan for oversized ship {}", ship);
                return;
            }
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
                pos.set(x, y, z);
                if (!level.getBlockState(pos).is(ModBlocks.VEHICLE_SETUP.get())) continue;
                BlockEntity entity = level.getBlockEntity(pos);
                if (entity instanceof VehicleSetupBlockEntity setup) setups.put(pos.immutable(), setup);
            }
        } catch (ReflectiveOperationException ignored) { }
    }

    private static void runSetup(ServerLevel level, BlockPos setupPos, VehicleSetupBlockEntity setup,
                                 ServerPlayer player, Map<Long, Object> ships) {
        int succeeded = 0;
        String firstError = null;
        for (VehicleSetupAction action : setup.actions()) {
            String error = action.type() == VehicleSetupActionType.LINK_DBW_BACKUPS
                    ? runDbw(level, setupPos, action, ships)
                    : VehicleSetupExecutor.run(level, setupPos, player, action, ships);
            if (error == null) succeeded++;
            else if (firstError == null) firstError = error;
        }
        player.displayClientMessage(Component.literal(firstError == null
                ? "Vehicle setup complete: " + succeeded + " actions."
                : "Vehicle setup: " + succeeded + " complete. " + firstError), true);
    }

    @Nullable
    private static String runDbw(ServerLevel level, BlockPos setupPos, VehicleSetupAction action,
                                 Map<Long, Object> ships) {
        Object sourceShip = ships.get(action.targetShipId());
        Object targetShip = ships.get(action.secondaryShipId());
        if (sourceShip == null || targetShip == null || action.targetOffset() == null
                || action.secondaryOffset() == null) {
            return "DBW link could not resolve both placed ships";
        }
        BlockPos source = VehicleSetupReflection.positionOnShip(sourceShip, action.targetOffset());
        BlockPos target = VehicleSetupReflection.positionOnShip(targetShip, action.secondaryOffset());
        if (source == null || target == null) return "DBW link could not resolve backup positions";
        String error = DbwCompat.linkBackups(level, source, target);
        if (error != null) VSAnalogWarfare.LOGGER.warn("[VSAW] DBW link at {} failed: {}", setupPos, error);
        return error;
    }

    @Nullable
    private static Object pairValue(Object pair, String method) {
        try { return VehicleSetupReflection.invoke(pair, method); }
        catch (ReflectiveOperationException ignored) { return null; }
    }

    private static int coordinate(Object box, String name) throws ReflectiveOperationException {
        Object value = box.getClass().getMethod(name).invoke(box);
        return value instanceof Number number ? number.intValue() : 0;
    }
}
