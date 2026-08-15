package com.erika.vsanalogwarfare.vehiclesetup.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

/** Optional reflection bridge for Tallyho's directly placed entities. */
public final class TallyhoCompat {
    private static final String MOD_ID = "tallyho";
    private static final String MISSILE_REGISTRY = "edn.stratodonut.tallyho.missile.MissileRegistry";
    private static final ResourceLocation HULL_MG = new ResourceLocation(MOD_ID, "hull_mg");
    private static final ResourceLocation COAX_MG = new ResourceLocation(MOD_ID, "coax_mg");
    private static final ResourceLocation GUN_MOUNT = new ResourceLocation(MOD_ID, "gun_mount");
    private static final ResourceLocation TRIPOD_MOUNT = new ResourceLocation(MOD_ID, "tripod_mount");
    private static final ResourceLocation CHIN_TURRET = new ResourceLocation(MOD_ID, "chin_turret");
    private static final ResourceLocation CROWS_TURRET = new ResourceLocation(MOD_ID, "crows_turret");
    private static final ResourceLocation TARGETING_POD = new ResourceLocation(MOD_ID, "targeting_pod");
    private static final ResourceLocation MISSILE = new ResourceLocation(MOD_ID, "missile");
    private static final ResourceLocation PERISCOPE_ARC = new ResourceLocation(MOD_ID, "periscope_arc");
    private static final ResourceLocation REMOTE_CAMERA = new ResourceLocation(MOD_ID, "remote_camera");

    private static final String HULL_MG_CLASS = "edn.stratodonut.tallyho.camera.entity.HullMachineGunEntity";
    private static final String COAX_MG_CLASS = "edn.stratodonut.tallyho.camera.entity.CoaxMachineGunEntity";
    private static final String GUN_MOUNT_CLASS = "edn.stratodonut.tallyho.entity.GunMountEntity";
    private static final String TRIPOD_CLASS = "edn.stratodonut.tallyho.entity.TripodEntity";
    private static final String CHIN_TURRET_CLASS = "edn.stratodonut.tallyho.camera.entity.HeliChinTurretEntity";
    private static final String CROWS_CLASS = "edn.stratodonut.tallyho.camera.entity.RemoteStationEntity";
    private static final String TARGETING_POD_CLASS = "edn.stratodonut.tallyho.camera.entity.TargetingPodEntity";
    private static final String PERISCOPE_CLASS = "edn.stratodonut.tallyho.camera.entity.PeriscopeEntity";
    private static final String REMOTE_CAMERA_CLASS = "edn.stratodonut.tallyho.camera.entity.RemoteCameraEntity";

    private static final String BASE_YAW_FIELD = "BASE_YAW";
    private static final String MUZZLE_OFFSET_FIELD = "muzzle_offset";

    private TallyhoCompat() { }

    public record CapturedEntity(BlockPos supportPosition, Vec3 positionOffset, String entityId,
                                 float baseYaw, int variant, CompoundTag state) { }

    @Nullable
    public static String spawnHullMg(Level level, BlockPos position, float yaw, int muzzleOffset) {
        if (!isLoaded()) return "Tallyho is not installed";
        if (!(level instanceof ServerLevel serverLevel)) return "hull MGs can only be spawned on the server";
        try {
            Object spawned = VehicleSetupReflection.invokeStatic(Class.forName(HULL_MG_CLASS), "spawn",
                    serverLevel, position, yaw, muzzleOffset);
            if (!(spawned instanceof Entity entity) || !entity.isAlive() || entity.getVehicle() == null) {
                return "Tallyho hull MG spawn did not leave a mounted weapon";
            }
            return null;
        } catch (ReflectiveOperationException | LinkageError error) {
            return "Tallyho hull MG integration failed: " + error.getClass().getSimpleName();
        }
    }

    @Nullable
    public static CapturedEntity capture(Entity entity) {
        if (!isLoaded()) return null;
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (!MOD_ID.equals(key.getNamespace()) || !isSupported(key)) return null;

        Entity support = entity.getVehicle();
        BlockPos supportPosition = support == null ? entity.blockPosition() : support.blockPosition();
        Vec3 origin = Vec3.atCenterOf(supportPosition);
        CompoundTag state = new CompoundTag();
        entity.saveWithoutId(state);
        stripRuntimeState(state);

        int variant = 0;
        if (HULL_MG.equals(key) || COAX_MG.equals(key)) {
            Integer muzzleOffset = readIntField(entity, MUZZLE_OFFSET_FIELD);
            if (muzzleOffset == null || support == null) return null;
            variant = muzzleOffset;
        } else if (MISSILE.equals(key)) {
            if (!state.contains("MissileId")) return null;
        }
        if ("periscope_arc".equals(key.getPath()) && state.getFloat("ANGLE_LIMIT_Y") >= 180.0f) variant = 360;
        Float baseYaw = readFloatField(entity, BASE_YAW_FIELD);
        return new CapturedEntity(supportPosition, entity.position().subtract(origin), key.toString(),
                baseYaw == null ? entity.getYRot() : baseYaw, variant, state);
    }

    @Nullable
    public static String spawnEntity(Level level, BlockPos supportPosition, Vec3 positionOffset,
                                     @Nullable String entityId, float yaw, int variant,
                                     @Nullable CompoundTag state) {
        if (!isLoaded()) return "Tallyho is not installed";
        if (!(level instanceof ServerLevel serverLevel)) return "Tallyho entities can only be spawned on the server";
        if (entityId == null || state == null) return "recorded Tallyho entity data is missing";
        try {
            ResourceLocation key = new ResourceLocation(entityId);
            if (!MOD_ID.equals(key.getNamespace()) || !isSupported(key)) {
                return "unsupported Tallyho entity: " + entityId;
            }
            Vec3 position = Vec3.atCenterOf(supportPosition).add(positionOffset);
            Object spawned = switch (key.getPath()) {
                case "hull_mg" -> VehicleSetupReflection.invokeStatic(Class.forName(HULL_MG_CLASS), "spawn",
                        serverLevel, supportPosition, yaw, variant);
                case "coax_mg" -> VehicleSetupReflection.invokeStatic(Class.forName(COAX_MG_CLASS), "spawn",
                        serverLevel, supportPosition, yaw, variant);
                case "gun_mount" -> VehicleSetupReflection.invokeStatic(Class.forName(GUN_MOUNT_CLASS), "spawn",
                        serverLevel, position, yaw);
                case "tripod_mount" -> VehicleSetupReflection.invokeStatic(Class.forName(TRIPOD_CLASS), "spawn",
                        serverLevel, supportPosition, yaw);
                case "chin_turret" -> VehicleSetupReflection.invokeStatic(Class.forName(CHIN_TURRET_CLASS), "spawn",
                        serverLevel, position, yaw);
                case "crows_turret" -> VehicleSetupReflection.invokeStatic(Class.forName(CROWS_CLASS), "spawn",
                        serverLevel, position, yaw);
                case "targeting_pod" -> VehicleSetupReflection.invokeStatic(Class.forName(TARGETING_POD_CLASS), "spawn",
                        serverLevel, supportPosition, yaw);
                case "periscope_arc" -> VehicleSetupReflection.invokeStatic(Class.forName(PERISCOPE_CLASS),
                        variant == 360 ? "spawn360" : "spawnArc", serverLevel, position, yaw);
                case "remote_camera" -> VehicleSetupReflection.invokeStatic(Class.forName(REMOTE_CAMERA_CLASS), "spawn",
                        serverLevel, supportPosition, yaw);
                case "missile" -> spawnMissile(serverLevel, position, yaw, state.getString("MissileId"));
                default -> null;
            };
            if (!(spawned instanceof Entity entity)) return "unsupported Tallyho entity: " + entityId;
            restoreState(entity, state, position);
            if (!entity.isAlive()) return "Tallyho entity was removed during replay: " + entityId;
            if ((HULL_MG.toString().equals(entityId) || COAX_MG.toString().equals(entityId)
                    || CHIN_TURRET.toString().equals(entityId) || CROWS_TURRET.toString().equals(entityId)
                    || TARGETING_POD.toString().equals(entityId) || PERISCOPE_ARC.toString().equals(entityId)
                    ) && entity.getVehicle() == null) {
                return "Tallyho entity was not mounted during replay: " + entityId;
            }
            return null;
        } catch (ReflectiveOperationException | LinkageError | IllegalArgumentException error) {
            return "Tallyho entity integration failed: " + error.getClass().getSimpleName();
        }
    }

    private static Object spawnMissile(ServerLevel level, Vec3 position, float yaw, String missileId)
            throws ReflectiveOperationException {
        Class<?> registry = Class.forName(MISSILE_REGISTRY);
        Object entry = VehicleSetupReflection.invokeStatic(registry, "getEntry", missileId);
        if (entry == null) return null;
        return VehicleSetupReflection.invoke(entry, "spawn", level, position, yaw);
    }

    private static void restoreState(Entity entity, CompoundTag state, Vec3 position) {
        entity.load(state.copy());
        entity.setPos(position.x, position.y, position.z);
    }

    private static void stripRuntimeState(CompoundTag state) {
        state.remove("UUID");
        state.remove("Pos");
        state.remove("Motion");
        state.remove("Rotation");
        state.remove("Passengers");
        state.remove("Vehicle");
        state.remove("Dimension");
        state.remove("PortalCooldown");
    }

    private static boolean isSupported(ResourceLocation key) {
        return HULL_MG.equals(key) || COAX_MG.equals(key) || GUN_MOUNT.equals(key) || TRIPOD_MOUNT.equals(key)
                || CHIN_TURRET.equals(key) || CROWS_TURRET.equals(key) || TARGETING_POD.equals(key)
                || MISSILE.equals(key) || PERISCOPE_ARC.equals(key)
                || REMOTE_CAMERA.equals(key);
    }

    @Nullable private static Integer readIntField(Entity entity, String name) {
        try {
            Field field = findField(entity.getClass(), name);
            field.setAccessible(true);
            return field.getInt(entity);
        } catch (ReflectiveOperationException | LinkageError ignored) { return null; }
    }

    @Nullable private static Float readFloatField(Entity entity, String name) {
        try {
            Field field = findField(entity.getClass(), name);
            field.setAccessible(true);
            return field.getFloat(entity);
        } catch (ReflectiveOperationException | LinkageError ignored) { return null; }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null && current != Entity.class; current = current.getSuperclass()) {
            try { return current.getDeclaredField(name); } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }
}
