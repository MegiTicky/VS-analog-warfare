package com.erika.vsanalogwarfare.vehiclesetup.compat;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Optional reflection bridge for Steve's Army's vehicle crew. */
public final class StevesArmyCompat {
    private static final String MOD_ID = "steves_army";
    private static final ResourceLocation CREW_ENTITY = new ResourceLocation(MOD_ID, "vehicle_crew");
    private static final ResourceLocation CREW_SPAWN_EGG = new ResourceLocation(MOD_ID, "vehicle_crew_spawn_egg");
    private static final String API_CLASS = "com.stevesarmy.api.VehicleCrewApi";
    private static final double IDEMPOTENCY_SEARCH_RADIUS = 1.0;

    private StevesArmyCompat() { }

    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isCrewSpawnEgg(ItemStack stack) {
        return isLoaded() && !stack.isEmpty()
                && CREW_SPAWN_EGG.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static boolean isCrewEntity(Entity entity) {
        return CREW_ENTITY.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    /** Snapshot of crew UUIDs near {@code position} so a just-spawned crew can be told apart from existing ones. */
    public static Set<UUID> nearbyCrewIds(ServerLevel level, Vec3 position, double radius) {
        return nearbyCrew(level, position, null, radius).stream()
                .map(Entity::getUUID)
                .collect(Collectors.toSet());
    }

    @Nullable
    public static Entity findNewCrew(ServerLevel level, Vec3 position, @Nullable Vec3 alternatePosition,
                                     Set<UUID> existingIds) {
        return nearbyCrew(level, position, alternatePosition, IDEMPOTENCY_SEARCH_RADIUS + 2.0).stream()
                .filter(entity -> !existingIds.contains(entity.getUUID()))
                .filter(Entity::isAddedToWorld)
                .filter(entity -> level.getEntity(entity.getUUID()) == entity)
                .findFirst()
                .orElse(null);
    }

    /**
     * Replays a recorded crew spawn: Steve's Army spawns a crew owned by {@code player}
     * at the recorded seat position and seats it. A silent success when a crew already
     * occupies the recorded spot, so re-running a setup never duplicates crew.
     */
    @Nullable
    public static String spawnCrew(Level level, BlockPos supportPosition, Vec3 positionOffset,
                                   @Nullable ServerPlayer player) {
        if (!isLoaded()) return "Steve's Army is not installed";
        if (!(level instanceof ServerLevel serverLevel)) return "vehicle crew can only be spawned on the server";
        if (player == null) return "vehicle crew spawn requires the schematic placer to be online";
        try {
            Vec3 target = Vec3.atCenterOf(supportPosition).add(positionOffset);
            if (!nearbyCrewIds(serverLevel, target, IDEMPOTENCY_SEARCH_RADIUS).isEmpty()) {
                return null;
            }
            VehicleSetupReflection.invokeStatic(Class.forName(API_CLASS), "spawnCrewOnVehicle",
                    player, serverLevel, supportPosition, positionOffset);
            if (nearbyCrewIds(serverLevel, target, IDEMPOTENCY_SEARCH_RADIUS).isEmpty()) {
                return "Steve's Army did not spawn a vehicle crew near " + supportPosition;
            }
            return null;
        } catch (ReflectiveOperationException | LinkageError error) {
            return "Steve's Army crew integration failed: " + error.getClass().getSimpleName();
        }
    }

    /**
     * Tells Steve's Army a vehicle setup finished at {@code setupPos}, so nearby
     * station-less crew owned by {@code player} board the vehicle (its
     * {@code onVehicleSetupCompleted} entry point).
     */
    public static void notifySetupCompleted(ServerPlayer player, Level level, BlockPos setupPos) {
        if (!isLoaded() || !(level instanceof ServerLevel serverLevel)) return;
        try {
            VehicleSetupReflection.invokeStatic(Class.forName(API_CLASS), "onVehicleSetupCompleted",
                    player, serverLevel, setupPos);
        } catch (ReflectiveOperationException | LinkageError error) {
            VSAnalogWarfare.LOGGER.warn("[VSAW] Could not notify Steve's Army of setup completion at {}: {}",
                    setupPos, error.getClass().getSimpleName());
        }
    }

    private static List<Entity> nearbyCrew(ServerLevel level, Vec3 position, @Nullable Vec3 alternatePosition,
                                           double radius) {
        List<Entity> entities = new ArrayList<>(level.getEntities((Entity) null,
                new AABB(position, position).inflate(radius), StevesArmyCompat::isCrewEntity));
        if (alternatePosition != null && alternatePosition.distanceToSqr(position) > 0.01) {
            for (Entity entity : level.getEntities((Entity) null,
                    new AABB(alternatePosition, alternatePosition).inflate(radius), StevesArmyCompat::isCrewEntity)) {
                if (!entities.contains(entity)) entities.add(entity);
            }
        }
        return entities;
    }
}
