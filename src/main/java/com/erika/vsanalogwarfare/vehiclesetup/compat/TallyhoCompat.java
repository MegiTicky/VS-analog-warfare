package com.erika.vsanalogwarfare.vehiclesetup.compat;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

/** Optional reflection bridge for Tallyho's directly placed entities. */
public final class TallyhoCompat {
    private static final String MOD_ID = "tallyho";
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
    private static final ResourceLocation GUN_MOUNT_ITEM = new ResourceLocation(MOD_ID, "gun_mount");
    private static final ResourceLocation TRIPOD_ITEM = new ResourceLocation(MOD_ID, "tripod_mount");
    private static final ResourceLocation PERISCOPE_ARC_ITEM = new ResourceLocation(MOD_ID, "periscope_arc_item");
    private static final ResourceLocation PERISCOPE_360_ITEM = new ResourceLocation(MOD_ID, "periscope_360_item");
    private static final ResourceLocation CHIN_TURRET_ITEM = new ResourceLocation(MOD_ID, "turret_remote");
    private static final ResourceLocation CROWS_ITEM = new ResourceLocation(MOD_ID, "crows_item");
    private static final ResourceLocation TARGETING_POD_ITEM = new ResourceLocation(MOD_ID, "tgp_remote");
    private static final ResourceLocation REMOTE_CAMERA_ITEM = new ResourceLocation(MOD_ID, "remote_camera");
    private static final GameProfile REPLAY_PROFILE = new GameProfile(
            UUID.fromString("7c7016c1-c84e-45fc-8102-5cb75e272d8f"), "[VSAW Replay]");

    private static final String HULL_MG_CLASS = "edn.stratodonut.tallyho.camera.entity.HullMachineGunEntity";
    private static final String COAX_MG_CLASS = "edn.stratodonut.tallyho.camera.entity.CoaxMachineGunEntity";
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
                case "gun_mount", "tripod_mount", "chin_turret", "crows_turret", "targeting_pod",
                        "periscope_arc", "remote_camera", "missile" -> placeWithItem(serverLevel,
                        supportPosition, position, key, yaw, variant, state);
                default -> null;
            };
            if (!(spawned instanceof Entity entity)) {
                return "Tallyho placement did not create an entity: " + entityId;
            }
            restoreSupportedState(entity, state, key);
            if (!entity.isAlive()) return "Tallyho entity was removed during replay: " + entityId;
            if ((HULL_MG.toString().equals(entityId) || COAX_MG.toString().equals(entityId)
                    || CHIN_TURRET.toString().equals(entityId) || CROWS_TURRET.toString().equals(entityId)
                    || TARGETING_POD.toString().equals(entityId) || PERISCOPE_ARC.toString().equals(entityId)
                    ) && entity.getVehicle() == null) {
                return "Tallyho entity was not mounted during replay: " + entityId;
            }
            return null;
        } catch (ReflectiveOperationException | LinkageError | IllegalArgumentException error) {
            String detail = error.getMessage();
            return "Tallyho entity integration failed: "
                    + (detail == null || detail.isEmpty() ? error.getClass().getSimpleName() : detail);
        }
    }

    @Nullable
    private static Entity placeWithItem(ServerLevel level, BlockPos supportPosition, Vec3 position,
                                        ResourceLocation entityType, float yaw, int variant, CompoundTag state)
            throws ReflectiveOperationException {
        Item item = resolvePlacementItem(entityType, variant, state);
        if (item == Items.AIR) {
            throw new IllegalArgumentException("Tallyho placement item is unavailable for " + entityType);
        }

        BlockPos clickedPos = supportPosition;
        Direction clickedFace = Direction.UP;
        Vec3 hitPosition = position;
        if (TRIPOD_MOUNT.equals(entityType)) {
            clickedPos = supportPosition.below();
            hitPosition = Vec3.atCenterOf(clickedPos).add(0.0, 0.5, 0.0);
        }

        Direction facing = Direction.fromYRot(yaw);
        double directionSign = PERISCOPE_ARC.equals(entityType) ? -1.0 : 1.0;
        Vec3 playerPosition = Vec3.atCenterOf(supportPosition)
                .add(facing.getStepX() * 32.0 * directionSign, 1.0,
                        facing.getStepZ() * 32.0 * directionSign);
        FakePlayer player = FakePlayerFactory.get(level, REPLAY_PROFILE);
        player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z, yaw, 0.0f);
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        List<Entity> before = nearbyEntities(level, position);
        InteractionResult result = stack.useOn(new net.minecraft.world.item.context.UseOnContext(player,
                InteractionHand.MAIN_HAND, new BlockHitResult(hitPosition, clickedFace, clickedPos, false)));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (!result.consumesAction()) {
            throw new IllegalArgumentException("Tallyho item placement returned " + result + " for " + entityType);
        }
        return nearbyEntities(level, position).stream()
                .filter(entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(entityType))
                .filter(entity -> !before.contains(entity))
                .findFirst().orElse(null);
    }

    @Nullable
    private static Item resolvePlacementItem(ResourceLocation entityType, int variant, CompoundTag state)
            throws ReflectiveOperationException {
        if (GUN_MOUNT.equals(entityType)) return registryItem(GUN_MOUNT_ITEM);
        if (TRIPOD_MOUNT.equals(entityType)) return registryItem(TRIPOD_ITEM);
        if (CHIN_TURRET.equals(entityType)) return registryItem(CHIN_TURRET_ITEM);
        if (CROWS_TURRET.equals(entityType)) return registryItem(CROWS_ITEM);
        if (TARGETING_POD.equals(entityType)) return registryItem(TARGETING_POD_ITEM);
        if (PERISCOPE_ARC.equals(entityType)) {
            return registryItem(variant == 360 ? PERISCOPE_360_ITEM : PERISCOPE_ARC_ITEM);
        }
        if (REMOTE_CAMERA.equals(entityType)) return registryItem(REMOTE_CAMERA_ITEM);
        if (MISSILE.equals(entityType)) {
            String missileId = state.getString("MissileId");
            if (missileId.isEmpty()) throw new IllegalArgumentException("recorded missile ID is missing");
            Class<?> registry = Class.forName("edn.stratodonut.tallyho.missile.MissileRegistry");
            Object entry = VehicleSetupReflection.invokeStatic(registry, "getEntry", missileId);
            if (entry == null) throw new IllegalArgumentException("unknown Tallyho missile: " + missileId);
            Object itemEntry = VehicleSetupReflection.invoke(entry, "getItemEntry");
            Object item = itemEntry == null ? null : VehicleSetupReflection.invoke(itemEntry, "get");
            if (!(item instanceof Item resolved)) {
                throw new IllegalArgumentException("Tallyho missile item is unavailable: " + missileId);
            }
            return resolved;
        }
        throw new IllegalArgumentException("no Tallyho placement item for " + entityType);
    }

    private static Item registryItem(ResourceLocation id) {
        return BuiltInRegistries.ITEM.get(id);
    }

    private static List<Entity> nearbyEntities(ServerLevel level, Vec3 position) {
        return level.getEntities(null, new AABB(position, position).inflate(3.0));
    }

    private static void restoreSupportedState(Entity entity, CompoundTag state, ResourceLocation key)
            throws ReflectiveOperationException {
        if (GUN_MOUNT.equals(key) || TRIPOD_MOUNT.equals(key)) {
            if (!state.contains("gunItem")) return;
            ItemStack gun = ItemStack.of(state.getCompound("gunItem"));
            if (!gun.isEmpty()) VehicleSetupReflection.invoke(entity, "setMountedGun", gun);
        }
    }

    private static void stripRuntimeState(CompoundTag state) {
        state.remove("UUID");
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
