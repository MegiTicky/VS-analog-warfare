package com.erika.vsanalogwarfare.vehiclesetup.compat;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Method;
import java.util.Optional;

public final class DbwCompat {
    private static final String BACKUP_BLOCK_ENTITY = "edn.stratodonut.drivebywire.blocks.WireNetworkBackupBlockEntity";
    private DbwCompat() { }

    @Nullable public static BlockPos resolveBackup(Level level, Object ship, BlockPos offset, Object targetShip) {
        BlockPos exact = VehicleSetupReflection.positionOnShip(ship, offset);
        if (exact == null) return null;
        if (isBackup(level, exact)) return exact;
        try {
            Class<?> managerClass = Class.forName("edn.stratodonut.drivebywire.wire.ShipWireNetworkManager");
            Object targetOptional = VehicleSetupReflection.invokeStatic(managerClass, "get", targetShip);
            if (!(targetOptional instanceof Optional<?> targets) || targets.isEmpty()) return null;
            String targetName = (String) VehicleSetupReflection.invoke(targets.get(), "getName");
            List<BlockPos> matches = new ArrayList<>();
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                BlockPos candidate = exact.relative(direction);
                Object candidateShip = VehicleSetupReflection.findShip(level, candidate);
                if (!isBackup(level, candidate) || candidateShip == null
                        || !VehicleSetupReflection.sameShip(candidateShip, ship)) continue;
                CompoundTag data = (CompoundTag) VehicleSetupReflection.invoke(level.getBlockEntity(candidate), "serializeNBT");
                if (data != null && data.contains("WireNetwork", Tag.TAG_COMPOUND)
                        && data.getCompound("WireNetwork").getCompound("Network").contains(targetName, Tag.TAG_COMPOUND)) {
                    matches.add(candidate);
                }
            }
            if (matches.size() == 1) {
                VSAnalogWarfare.LOGGER.warn("[VSAW] Corrected DBW backup position from {} to {}", exact, matches.get(0));
                return matches.get(0);
            }
            VSAnalogWarfare.LOGGER.warn("[VSAW] DBW backup at {} could not be uniquely resolved; matching neighbors={}",
                    exact, matches.size());
        } catch (ReflectiveOperationException | ClassCastException | LinkageError error) {
            VSAnalogWarfare.LOGGER.warn("[VSAW] Could not validate DBW backup near {}: {}", exact,
                    error.getClass().getSimpleName());
        }
        return null;
    }

    @Nullable public static String linkBackups(Level level, BlockPos source, BlockPos target) {
        if (!ModList.get().isLoaded("drivebywire")) return "Drive By Wire is not installed";
        try {
            BlockEntity sourceBlockEntity = level.getBlockEntity(source);
            Object sourceBackup = sourceBlockEntity;
            if (sourceBackup == null || !sourceBackup.getClass().getName().equals(BACKUP_BLOCK_ENTITY)) return "source is not a DBW backup block";
            BlockEntity targetBlockEntity = level.getBlockEntity(target);
            if (targetBlockEntity == null || !targetBlockEntity.getClass().getName().equals(BACKUP_BLOCK_ENTITY)) return "target is not a DBW backup block";
            Object sourceShip = VehicleSetupReflection.findShip(level, source);
            Object targetShip = VehicleSetupReflection.findShip(level, target);
            if (sourceShip == null || targetShip == null) return "both backups must belong to loaded ships";
            Class<?> managerClass = Class.forName("edn.stratodonut.drivebywire.wire.ShipWireNetworkManager");
            Object sourceOptional = VehicleSetupReflection.invokeStatic(managerClass, "get", sourceShip);
            Object targetOptional = VehicleSetupReflection.invokeStatic(managerClass, "get", targetShip);
            if (!(sourceOptional instanceof Optional<?> sources) || sources.isEmpty() || !(targetOptional instanceof Optional<?> targets) || targets.isEmpty()) return "DBW networks are not initialized";
            Object sourceManager = sources.get(); Object targetManager = targets.get();
            CompoundTag backup = (CompoundTag) VehicleSetupReflection.invoke(sourceBackup, "serializeNBT");
            if (backup == null || !backup.contains("WireNetwork", Tag.TAG_COMPOUND)) return "source backup has no network data";
            CompoundTag network = backup.getCompound("WireNetwork").getCompound("Network");
            String targetName = (String) VehicleSetupReflection.invoke(targetManager, "getName");
            if (!network.contains(targetName, Tag.TAG_COMPOUND)) return "source backup has no target link data";
            long targetId = ((Number) VehicleSetupReflection.invoke(targetShip, "getId")).longValue();
            int sourceSizeBefore = networkSize(sourceManager);
            int targetSizeBefore = networkSize(targetManager);
            long sourceId = ((Number) VehicleSetupReflection.invoke(sourceShip, "getId")).longValue();
            long targetBackupId = ((Number) VehicleSetupReflection.invoke(targetShip, "getId")).longValue();
            VSAnalogWarfare.LOGGER.info("[VSAW] DBW relink source={} (ship={}, network={}) target={} (ship={}, network={}), "
                            + "sourceNetworkSize={}, targetNetworkSize={}", source, sourceId,
                    VehicleSetupReflection.invoke(sourceManager, "getName"), target, targetBackupId, targetName,
                    sourceSizeBefore, targetSizeBefore);
            Method link = VehicleSetupReflection.findMethod(sourceManager.getClass(), "linkNetwork", targetManager, targetId, network.getCompound(targetName));
            if (link == null) return "DBW link method is incompatible";
            link.invoke(sourceManager, targetManager, targetId, network.getCompound(targetName));
            int sourceSizeAfter = networkSize(sourceManager);
            int targetSizeAfter = networkSize(targetManager);
            sourceBlockEntity.setChanged();
            targetBlockEntity.setChanged();
            level.sendBlockUpdated(source, sourceBlockEntity.getBlockState(), sourceBlockEntity.getBlockState(), 3);
            level.sendBlockUpdated(target, targetBlockEntity.getBlockState(), targetBlockEntity.getBlockState(), 3);
            if (sourceSizeAfter < sourceSizeBefore || targetSizeAfter < targetSizeBefore) return "DBW network shrank during relink";
            VSAnalogWarfare.LOGGER.info("[VSAW] DBW relink verified sourceNetworkSize={}, targetNetworkSize={}",
                    sourceSizeAfter, targetSizeAfter);
            return null;
        } catch (ReflectiveOperationException | ClassCastException | LinkageError error) { return "DBW integration failed: " + error.getClass().getSimpleName(); }
    }

    private static int networkSize(Object manager) throws ReflectiveOperationException {
        Object network = VehicleSetupReflection.invoke(manager, "getNetwork");
        return network instanceof java.util.Map<?, ?> map ? map.size() : -1;
    }

    private static boolean isBackup(Level level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity != null && BACKUP_BLOCK_ENTITY.equals(entity.getClass().getName());
    }
}
