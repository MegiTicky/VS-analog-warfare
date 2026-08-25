package com.erika.vsanalogwarfare.vehiclesetup.compat;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    private static final ResourceLocation CAMERA_SEAT = new ResourceLocation(MOD_ID, "camera_seat");
    private static final ResourceLocation GUN_MOUNT_ITEM = new ResourceLocation(MOD_ID, "gun_mount");
    private static final ResourceLocation TRIPOD_ITEM = new ResourceLocation(MOD_ID, "tripod_mount");
    private static final ResourceLocation PERISCOPE_ARC_ITEM = new ResourceLocation(MOD_ID, "periscope_arc_item");
    private static final ResourceLocation PERISCOPE_360_ITEM = new ResourceLocation(MOD_ID, "periscope_360_item");
    private static final ResourceLocation CHIN_TURRET_ITEM = new ResourceLocation(MOD_ID, "turret_remote");
    private static final ResourceLocation CROWS_ITEM = new ResourceLocation(MOD_ID, "crows_item");
    private static final ResourceLocation TARGETING_POD_ITEM = new ResourceLocation(MOD_ID, "tgp_remote");
    private static final ResourceLocation REMOTE_CAMERA_ITEM = new ResourceLocation(MOD_ID, "remote_camera");
    private static final ResourceLocation HULL_MG_ITEM = new ResourceLocation(MOD_ID, "hull_mg");
    private static final ResourceLocation COAX_MG_ITEM = new ResourceLocation(MOD_ID, "coax_mg");

    private static final String MISSILE_REGISTRY_CLASS = "edn.stratodonut.tallyho.missile.MissileRegistry";
    private static final String GUN_MOUNT_CLASS = "edn.stratodonut.tallyho.entity.GunMountEntity";
    private static final String PERISCOPE_CLASS = "edn.stratodonut.tallyho.camera.entity.PeriscopeEntity";
    private static final String ANGLE_LIMITS_CLASS = "edn.stratodonut.tallyho.camera.AngleLimits";
    private static final String CAMERA_ENTITY_CLASS = "edn.stratodonut.tallyho.camera.entity.CameraEntity2";
    private static final String MISSILE_CLASS = "edn.stratodonut.tallyho.entity.MountedMissileEntity";
    private static final String CAMERA_SEAT_CLASS = "edn.stratodonut.tallyho.camera.entity.FlexibleSeatEntity";
    private static final String MISSILE_ID_TAG = "MissileId";
    private static final double SEAT_Y_OFFSET = 0.25;
    private static final double MAX_SLOT_DISTANCE = 64.0;
    private static final double SEAT_POSITION_TOLERANCE = 0.05;
    private static final double IDEMPOTENCY_SEARCH_RADIUS = 1.0;
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
        Vec3 positionOffset = entity.position().subtract(origin);
        CompoundTag state = new CompoundTag();
        entity.saveWithoutId(state);
        stripRuntimeState(state);

        int variant = 0;
        if (HULL_MG.equals(key) || COAX_MG.equals(key)) {
            Integer muzzleOffset = readIntField(entity, MUZZLE_OFFSET_FIELD);
            if (muzzleOffset == null || support == null) return null;
            variant = muzzleOffset;
        } else if (MISSILE.equals(key)) {
            if (support == null || !support.isAlive() || !support.isAddedToWorld()
                    || !CAMERA_SEAT.equals(BuiltInRegistries.ENTITY_TYPE.getKey(support.getType()))
                    || state.getString(MISSILE_ID_TAG).isEmpty()) return null;
            positionOffset = support.position().subtract(origin);
            if (!isValidSlotOffset(positionOffset)) return null;
        }
        if ((GUN_MOUNT.equals(key) || PERISCOPE_ARC.equals(key)) && support != null
                && CAMERA_SEAT.equals(BuiltInRegistries.ENTITY_TYPE.getKey(support.getType()))) {
            positionOffset = support.position().subtract(origin);
            if (!isValidSlotOffset(positionOffset)) return null;
        }
        if ("periscope_arc".equals(key.getPath()) && state.getFloat("ANGLE_LIMIT_Y") >= 180.0f) variant = 360;
        Float baseYaw = readFloatField(entity, BASE_YAW_FIELD);
        return new CapturedEntity(supportPosition, positionOffset, key.toString(),
                baseYaw == null ? entity.getYRot() : baseYaw, variant, state);
    }

    public static boolean isPlacementItem(ItemStack stack) {
        if (!isLoaded()) return false;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return HULL_MG_ITEM.equals(key) || COAX_MG_ITEM.equals(key)
                || GUN_MOUNT_ITEM.equals(key) || TRIPOD_ITEM.equals(key) || CHIN_TURRET_ITEM.equals(key)
                || CROWS_ITEM.equals(key) || TARGETING_POD_ITEM.equals(key)
                || PERISCOPE_ARC_ITEM.equals(key) || PERISCOPE_360_ITEM.equals(key)
                || REMOTE_CAMERA_ITEM.equals(key);
    }

    @Nullable
    public static CapturedEntity captureNewEntity(ServerLevel level, Vec3 position,
                                                  @Nullable Vec3 alternatePosition, Set<UUID> existingEntities) {
        return nearbyEntities(level, position, alternatePosition).stream()
                .filter(entity -> !existingEntities.contains(entity.getUUID()))
                .filter(Entity::isAddedToWorld)
                .map(TallyhoCompat::capture)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public static Set<UUID> nearbyEntityIds(ServerLevel level, Vec3 position,
                                            @Nullable Vec3 alternatePosition) {
        return nearbyEntities(level, position, alternatePosition).stream()
                .map(Entity::getUUID)
                .collect(java.util.stream.Collectors.toSet());
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
                case "gun_mount" -> spawnGunMount(serverLevel, supportPosition, position, yaw, state);
                case "periscope_arc" -> spawnPeriscope(serverLevel, supportPosition, position, yaw, variant);
                case "tripod_mount", "chin_turret", "crows_turret", "targeting_pod", "remote_camera" -> placeWithItem(serverLevel,
                        supportPosition, position, key, yaw, variant);
                case "missile" -> spawnMissile(serverLevel, supportPosition, position, yaw, state);
                default -> null;
            };
            if (!(spawned instanceof Entity entity)) {
                return "Tallyho placement did not create an entity: " + entityId;
            }
            restoreSupportedState(entity, state, key);
            if (!entity.isAlive()) return "Tallyho entity was removed during replay: " + entityId;
            if ((HULL_MG.toString().equals(entityId) || COAX_MG.toString().equals(entityId)
                    || GUN_MOUNT.toString().equals(entityId) || CHIN_TURRET.toString().equals(entityId)
                    || CROWS_TURRET.toString().equals(entityId)
                    || TARGETING_POD.toString().equals(entityId) || PERISCOPE_ARC.toString().equals(entityId)
                    ) && entity.getVehicle() == null) {
                return "Tallyho entity was not mounted during replay: " + entityId;
            }
            return null;
        } catch (IllegalArgumentException error) {
            String detail = error.getMessage();
            return detail == null || detail.isEmpty() ? "invalid Tallyho entity data" : detail;
        } catch (ReflectiveOperationException | LinkageError error) {
            String detail = error.getMessage();
            return "Tallyho entity integration failed: "
                    + (detail == null || detail.isEmpty() ? error.getClass().getSimpleName() : detail);
        }
    }

    @Nullable
    private static Entity placeWithItem(ServerLevel level, BlockPos supportPosition, Vec3 position,
                                        ResourceLocation entityType, float yaw, int variant)
            throws ReflectiveOperationException {
        Item item = resolvePlacementItem(entityType, variant);
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
        Vec3 shipPlayerPosition = Vec3.atCenterOf(supportPosition)
                .add(facing.getStepX() * 32.0 * directionSign, 1.0,
                        facing.getStepZ() * 32.0 * directionSign);
        Object ship = VehicleSetupReflection.findShip(level, supportPosition);
        Vec3 playerPosition = ship == null ? shipPlayerPosition
                : VehicleSetupReflection.shipToWorldPosition(ship, shipPlayerPosition);
        if (playerPosition == null) playerPosition = shipPlayerPosition;
        FakePlayer player = FakePlayerFactory.get(level, REPLAY_PROFILE);
        player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z, yaw, 0.0f);
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 worldPosition = ship == null ? null : VehicleSetupReflection.shipToWorldPosition(ship, position);
        List<UUID> before = nearbyEntities(level, position, worldPosition).stream()
                .map(Entity::getUUID).toList();
        InteractionResult result;
        try {
            result = stack.useOn(new net.minecraft.world.item.context.UseOnContext(player,
                    InteractionHand.MAIN_HAND, new BlockHitResult(hitPosition, clickedFace, clickedPos, false)));
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        if (!result.consumesAction()) {
            throw new IllegalArgumentException("Tallyho item placement returned " + result + " for " + entityType);
        }
        Entity created = nearbyEntities(level, position, worldPosition).stream()
                .filter(entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(entityType))
                .filter(entity -> !before.contains(entity.getUUID()))
                .filter(Entity::isAddedToWorld)
                .filter(entity -> level.getEntity(entity.getUUID()) == entity)
                .findFirst().orElse(null);
        if (created == null) {
            throw new IllegalArgumentException("Tallyho item placement consumed the interaction but created no "
                    + entityType + " near " + position);
        }
        return created;
    }

    private static Entity spawnGunMount(ServerLevel level, BlockPos supportPosition, Vec3 seatPosition,
                                        float yaw, CompoundTag state) throws ReflectiveOperationException {
        return spawnMountedCamera(level, supportPosition, seatPosition, yaw, GUN_MOUNT_CLASS,
                state, null);
    }

    private static Entity spawnPeriscope(ServerLevel level, BlockPos supportPosition, Vec3 seatPosition,
                                         float yaw, int variant) throws ReflectiveOperationException {
        Class<?> entityClass = Class.forName(PERISCOPE_CLASS);
        Class<?> limitsClass = Class.forName(ANGLE_LIMITS_CLASS);
        Class<?> postFxClass = Class.forName(CAMERA_ENTITY_CLASS + "$PostFXType");
        Constructor<?> limitsConstructor = limitsClass.getConstructor(float.class, float.class, float.class);
        Object limits = limitsConstructor.newInstance(0.0f, 0.0f, variant == 360 ? 360.0f : 67.0f);
        Object postFx = Enum.valueOf(postFxClass.asSubclass(Enum.class), "PERISCOPE");
        return spawnMountedCamera(level, supportPosition, seatPosition, yaw, PERISCOPE_CLASS, null,
                new CameraParams(entityClass, limits, postFx));
    }

    private static Entity spawnMountedCamera(ServerLevel level, BlockPos supportPosition, Vec3 seatPosition,
                                             float yaw, String entityClassName, @Nullable CompoundTag state,
                                             @Nullable CameraParams cameraParams) throws ReflectiveOperationException {
        Object ship = VehicleSetupReflection.findShip(level, supportPosition);
        if (ship == null) {
            throw new IllegalArgumentException("Tallyho entity support " + supportPosition
                    + " is not on a Valkyrien Skies ship");
        }
        Vec3 factoryPosition = seatPosition.subtract(0.0, SEAT_Y_OFFSET, 0.0);
        Vec3 worldPosition = VehicleSetupReflection.shipToWorldPosition(ship, factoryPosition);
        if (worldPosition == null) {
            throw new IllegalArgumentException("could not transform Tallyho entity slot to world coordinates");
        }

        Entity seat = null;
        Entity entity = null;
        try {
            Class<?> seatClass = Class.forName(CAMERA_SEAT_CLASS);
            Constructor<?> seatConstructor = seatClass.getConstructor(Level.class, BlockPos.class);
            Object seatObject = seatConstructor.newInstance(level,
                    BlockPos.containing(factoryPosition));
            if (!(seatObject instanceof Entity createdSeat)) {
                throw new IllegalArgumentException("Tallyho camera seat factory returned the wrong type");
            }
            seat = createdSeat;
            seat.setPos(seatPosition);
            if (!level.addFreshEntity(seat)) {
                throw new IllegalArgumentException("Tallyho camera seat registration was rejected");
            }

            Class<?> entityClass = cameraParams == null ? Class.forName(entityClassName)
                    : cameraParams.entityClass();
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(
                    cameraParams == null ? GUN_MOUNT : PERISCOPE_ARC);
            Constructor<?> entityConstructor = entityClass.getConstructor(EntityType.class, Level.class);
            Object createdEntity = entityConstructor.newInstance(type, level);
            if (!(createdEntity instanceof Entity created)) {
                throw new IllegalArgumentException("Tallyho camera factory returned the wrong entity type");
            }
            entity = created;
            if (cameraParams != null) {
                VehicleSetupReflection.invokeDeclared(entity, "setParams", yaw,
                        cameraParams.postFx(), cameraParams.limits(), 70);
            }
            entity.moveTo(worldPosition.x, worldPosition.y, worldPosition.z, yaw, 0.0f);
            if (!level.addFreshEntity(entity)) {
                throw new IllegalArgumentException("Tallyho camera entity registration was rejected");
            }
            if (!entity.startRiding(seat, true)) {
                throw new IllegalArgumentException("Tallyho camera entity could not mount its camera seat");
            }
            if (GUN_MOUNT_CLASS.equals(entityClassName)) {
                writeFloatField(entity, BASE_YAW_FIELD, state != null && state.contains("BASE_YAW")
                        ? state.getFloat("BASE_YAW") : yaw);
            }
            return entity;
        } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError error) {
            if (entity != null) entity.discard();
            if (seat != null) seat.discard();
            throw error;
        }
    }

    private record CameraParams(Class<?> entityClass, Object limits, Object postFx) { }

    private static Entity spawnMissile(ServerLevel level, BlockPos supportPosition, Vec3 desiredPosition,
                                       float yaw, CompoundTag state) throws ReflectiveOperationException {
        String missileId = state.getString(MISSILE_ID_TAG);
        if (missileId.isEmpty()) throw new IllegalArgumentException("recorded Tallyho missile ID is missing");
        if (!isValidSlotOffset(desiredPosition.subtract(Vec3.atCenterOf(supportPosition)))) {
            VSAnalogWarfare.LOGGER.warn("[VSAW] Rejecting Tallyho missile {} at {} relative to support {}", missileId,
                    desiredPosition, supportPosition);
            throw new IllegalArgumentException("recorded Tallyho missile position is invalid or outside the recorded slot");
        }
        Entity existing = findExistingMissile(level, desiredPosition, missileId);
        if (existing != null) return existing;

        Object ship = VehicleSetupReflection.findShip(level, supportPosition);
        if (ship == null) {
            throw new IllegalArgumentException("Tallyho missile support " + supportPosition + " is not on a Valkyrien Skies ship");
        }
        if (!level.isLoaded(supportPosition)) {
            throw new IllegalArgumentException("Tallyho missile target chunk at " + supportPosition + " is not loaded");
        }

        Class<?> registry = Class.forName(MISSILE_REGISTRY_CLASS);
        Object entry = VehicleSetupReflection.invokeStatic(registry, "getEntry", missileId);
        if (entry == null) throw new IllegalArgumentException("unknown Tallyho missile: " + missileId);

        List<UUID> before = nearbyMissilePlacementEntities(level, desiredPosition).stream()
                .map(Entity::getUUID).toList();
        Entity spawnedEntity = null;
        Entity spawnedSeat = null;
        try {
            Vec3 worldPosition = VehicleSetupReflection.shipToWorldPosition(ship, desiredPosition);
            if (worldPosition == null) {
                throw new IllegalArgumentException("could not transform the Tallyho missile slot to world coordinates");
            }

            Object itemEntry = VehicleSetupReflection.invoke(entry, "getItemEntry");
            Object item = itemEntry == null ? null : VehicleSetupReflection.invoke(itemEntry, "get");
            EntityType<?> missileType = BuiltInRegistries.ENTITY_TYPE.get(MISSILE);
            EntityType<?> seatType = BuiltInRegistries.ENTITY_TYPE.get(CAMERA_SEAT);
            if (!(item instanceof Item missileItem) || missileType == null || seatType == null) {
                throw new IllegalArgumentException("Tallyho missile factory components are unavailable for " + missileId);
            }

            Class<?> seatClass = Class.forName(CAMERA_SEAT_CLASS);
            Constructor<?> seatConstructor = seatClass.getConstructor(Level.class, BlockPos.class);
            Object seatObject = seatConstructor.newInstance(level,
                    BlockPos.containing(desiredPosition.subtract(0.0, SEAT_Y_OFFSET, 0.0)));
            if (!(seatObject instanceof Entity seat)) {
                throw new IllegalArgumentException("Tallyho camera seat factory returned the wrong type");
            }
            seat.setPos(desiredPosition);
            if (!level.addFreshEntity(seat)) {
                throw new IllegalArgumentException("Tallyho missile seat registration was rejected");
            }
            spawnedSeat = seat;

            Class<?> missileClass = Class.forName(MISSILE_CLASS);
            Constructor<?> missileConstructor = missileClass.getConstructor(
                    EntityType.class, Level.class, String.class, Item.class);
            Object missileObject = missileConstructor.newInstance(missileType, level, missileId, missileItem);
            if (!(missileObject instanceof Entity missile)) {
                throw new IllegalArgumentException("Tallyho missile factory returned the wrong type");
            }
            missile.moveTo(worldPosition.x, worldPosition.y, worldPosition.z, yaw, 0.0f);
            if (!level.addFreshEntity(missile)) {
                throw new IllegalArgumentException("Tallyho missile registration was rejected");
            }
            spawnedEntity = missile;
            if (!missile.startRiding(seat, true)) {
                throw new IllegalArgumentException("Tallyho missile could not mount its camera seat");
            }
            verifyMissile(level, missile, desiredPosition, missileId);
            return missile;
        } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError error) {
            Entity vehicle = spawnedEntity == null ? null : spawnedEntity.getVehicle();
            if (spawnedEntity != null) spawnedEntity.discard();
            if (vehicle != null) vehicle.discard();
            if (spawnedSeat != null && spawnedSeat != vehicle) spawnedSeat.discard();
            cleanupNewMissilePlacement(level, desiredPosition, before);
            throw error;
        }
    }

    private static void verifyMissile(ServerLevel level, Entity missile, Vec3 desiredPosition, String missileId)
            throws ReflectiveOperationException {
        if (!MISSILE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(missile.getType()))) {
            throw new IllegalArgumentException("Tallyho missile factory created the wrong entity type");
        }
        if (!missile.isAlive() || !missile.isAddedToWorld() || level.getEntity(missile.getUUID()) != missile) {
            throw new IllegalArgumentException("Tallyho missile registration was rejected");
        }
        Object resolvedId = VehicleSetupReflection.invoke(missile, "getMissileId");
        if (!missileId.equals(resolvedId)) {
            throw new IllegalArgumentException("Tallyho missile was created as a different missile type");
        }
        Entity seat = missile.getVehicle();
        if (seat == null || !seat.isAlive() || !seat.isAddedToWorld()
                || !CAMERA_SEAT.equals(BuiltInRegistries.ENTITY_TYPE.getKey(seat.getType()))
                || level.getEntity(seat.getUUID()) != seat) {
            throw new IllegalArgumentException("Tallyho missile seat registration was rejected");
        }
        double toleranceSquared = SEAT_POSITION_TOLERANCE * SEAT_POSITION_TOLERANCE;
        if (seat.position().distanceToSqr(desiredPosition) > toleranceSquared) {
            throw new IllegalArgumentException("Tallyho missile was created outside the recorded slot");
        }
    }

    @Nullable
    private static Entity findExistingMissile(ServerLevel level, Vec3 desiredPosition, String missileId)
            throws ReflectiveOperationException {
        double toleranceSquared = SEAT_POSITION_TOLERANCE * SEAT_POSITION_TOLERANCE;
        for (Entity seat : nearbyMissilePlacementEntities(level, desiredPosition)) {
            if (!CAMERA_SEAT.equals(BuiltInRegistries.ENTITY_TYPE.getKey(seat.getType()))
                    || !seat.isAlive() || !seat.isAddedToWorld()
                    || level.getEntity(seat.getUUID()) != seat) continue;
            if (seat.position().distanceToSqr(desiredPosition) > toleranceSquared) continue;
            for (Entity candidate : seat.getPassengers()) {
                if (!MISSILE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(candidate.getType()))
                        || !candidate.isAlive() || !candidate.isAddedToWorld()
                        || level.getEntity(candidate.getUUID()) != candidate) continue;
                Object resolvedId = VehicleSetupReflection.invoke(candidate, "getMissileId");
                if (missileId.equals(resolvedId)) return candidate;
            }
        }
        return null;
    }

    private static List<Entity> nearbyMissilePlacementEntities(ServerLevel level, Vec3 position) {
        return level.getEntities((Entity) null,
                new AABB(position, position).inflate(IDEMPOTENCY_SEARCH_RADIUS), entity -> {
                    ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                    return MISSILE.equals(type) || CAMERA_SEAT.equals(type);
                });
    }

    private static void cleanupNewMissilePlacement(ServerLevel level, Vec3 position, List<UUID> before) {
        for (Entity entity : nearbyMissilePlacementEntities(level, position)) {
            if (!before.contains(entity.getUUID())) entity.discard();
        }
    }

    private static boolean isValidSlotOffset(Vec3 offset) {
        return Double.isFinite(offset.x) && Double.isFinite(offset.y) && Double.isFinite(offset.z)
                && offset.lengthSqr() <= MAX_SLOT_DISTANCE * MAX_SLOT_DISTANCE;
    }

    @Nullable
    private static Item resolvePlacementItem(ResourceLocation entityType, int variant)
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
        throw new IllegalArgumentException("no Tallyho placement item for " + entityType);
    }

    private static Item registryItem(ResourceLocation id) {
        return BuiltInRegistries.ITEM.get(id);
    }

    private static List<Entity> nearbyEntities(ServerLevel level, Vec3 position, @Nullable Vec3 alternatePosition) {
        List<Entity> entities = level.getEntities(null, new AABB(position, position).inflate(3.0));
        if (alternatePosition != null && alternatePosition.distanceToSqr(position) > 0.01) {
            for (Entity entity : level.getEntities(null,
                    new AABB(alternatePosition, alternatePosition).inflate(3.0))) {
                if (!entities.contains(entity)) entities.add(entity);
            }
        }
        return entities;
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

    private static void writeFloatField(Entity entity, String name, float value)
            throws ReflectiveOperationException {
        Field field = findField(entity.getClass(), name);
        field.setAccessible(true);
        field.setFloat(entity, value);
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
