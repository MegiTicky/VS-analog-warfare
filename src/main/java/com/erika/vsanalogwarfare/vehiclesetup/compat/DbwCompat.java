package com.erika.vsanalogwarfare.vehiclesetup.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Optional;

public final class DbwCompat {
    private DbwCompat() { }

    @Nullable
    public static String linkBackups(Level level, BlockPos source, BlockPos target) {
        if (!ModList.get().isLoaded("drivebywire")) return "Drive By Wire is not installed";
        try {
            Object sourceBackup = level.getBlockEntity(source);
            if (sourceBackup == null || !sourceBackup.getClass().getName().equals(
                    "edn.stratodonut.drivebywire.blocks.WireNetworkBackupBlockEntity")) {
                return "source is not a DBW backup block";
            }
            Object sourceShip = VehicleSetupReflection.findShip(level, source);
            Object targetShip = VehicleSetupReflection.findShip(level, target);
            if (sourceShip == null || targetShip == null) return "both backups must belong to loaded ships";
            Class<?> managerClass = Class.forName("edn.stratodonut.drivebywire.wire.ShipWireNetworkManager");
            Object sourceOptional = VehicleSetupReflection.invokeStatic(managerClass, "get", sourceShip);
            Object targetOptional = VehicleSetupReflection.invokeStatic(managerClass, "get", targetShip);
            if (!(sourceOptional instanceof Optional<?> sources) || sources.isEmpty()
                    || !(targetOptional instanceof Optional<?> targets) || targets.isEmpty()) {
                return "DBW networks are not initialized";
            }
            Object sourceManager = sources.get();
            Object targetManager = targets.get();
            CompoundTag backup = (CompoundTag) VehicleSetupReflection.invoke(sourceBackup, "serializeNBT");
            if (backup == null || !backup.contains("WireNetwork", Tag.TAG_COMPOUND)) {
                return "source backup has no network data";
            }
            CompoundTag network = backup.getCompound("WireNetwork").getCompound("Network");
            String targetName = (String) VehicleSetupReflection.invoke(targetManager, "getName");
            if (!network.contains(targetName, Tag.TAG_COMPOUND)) return "source backup has no target link data";
            long targetId = ((Number) VehicleSetupReflection.invoke(targetShip, "getId")).longValue();
            Method link = VehicleSetupReflection.findMethod(sourceManager.getClass(), "linkNetwork", targetManager,
                    targetId, network.getCompound(targetName));
            if (link == null) return "DBW link method is incompatible";
            link.invoke(sourceManager, targetManager, targetId, network.getCompound(targetName));
            return null;
        } catch (ReflectiveOperationException | ClassCastException | LinkageError error) {
            return "DBW integration failed: " + error.getClass().getSimpleName();
        }
    }
}
