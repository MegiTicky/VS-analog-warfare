package com.erika.vsanalogwarfare.scope.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.joml.primitives.AABBdc;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

public final class CbcCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CANNON_MOUNT = "rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity";
    private static final String FIXED_CANNON_MOUNT = "rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlockEntity";
    private static final String COMPACT_CANNON_MOUNT = "riftyboi.cbcmodernwarfare.cannon_control.compact_mount.CompactCannonMountBlockEntity";
    private static long lastAimDirectionLogMs = 0;
    private static final double MATCH_THRESHOLD = 0.95;

    private CbcCompat() {
    }

    public static Optional<BlockPos> findNearestMount(Level level, BlockPos origin, int radius) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.isLoaded(cursor)) {
                        continue;
                    }
                    if (isCannonMount(level.getBlockEntity(cursor))) {
                        double dist = cursor.distSqr(origin);
                        if (dist < bestDist) {
                            best = cursor.immutable();
                            bestDist = dist;
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static Optional<MountMatchResult> findMountByAimDirection(Level level, BlockPos searchOrigin, Vec3 projectileVelocityDir, int radius) {
        BlockPos best = null;
        double bestMatch = MATCH_THRESHOLD;
        Vec3 bestAimDir = null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(searchOrigin.getX() + dx, searchOrigin.getY() + dy, searchOrigin.getZ() + dz);
                    if (!level.isLoaded(cursor)) {
                        continue;
                    }
                    BlockEntity be = level.getBlockEntity(cursor);
                    if (!isCannonMount(be)) {
                        continue;
                    }
                    
                    Vec3 mountAim = getAimDirectionQuiet(level, cursor.immutable(), Direction.NORTH, 1.0f, true).orElse(null);
                    if (mountAim == null) {
                        LOGGER.debug("[VSAW_SCOPE] findMountByAimDirection: mount at {} returned null aim", cursor);
                        continue;
                    }
                    
                    Vec3 mountAimNorm = mountAim.normalize();
                    double match = projectileVelocityDir.dot(mountAimNorm);
                    LOGGER.debug("[VSAW_SCOPE] findMountByAimDirection: mount at {} aim={} dot={}",
                            cursor, mountAimNorm, String.format("%.4f", match));
                    
                    if (match > bestMatch) {
                        bestMatch = match;
                        best = cursor.immutable();
                        bestAimDir = mountAim;
                    }
                }
            }
        }
        
        if (best != null && bestAimDir != null) {
            LOGGER.debug("[VSAW_SCOPE] findMountByAimDirection: BEST mount at {} with match={}", best, bestMatch);
            return Optional.of(new MountMatchResult(best, bestAimDir, bestMatch));
        }
        LOGGER.debug("[VSAW_SCOPE] findMountByAimDirection: no mount found with match >= {}", MATCH_THRESHOLD);
        return Optional.empty();
    }

    public static Optional<MountMatchResult> findMountByAimDirectionGlobal(Level level, Vec3 projectileVelocityDir, Vec3 projectileWorldPos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            LOGGER.debug("[VSAW_SCOPE] findMountByAimDirectionGlobal: not a ServerLevel");
            return Optional.empty();
        }
        
        BlockPos best = null;
        double bestMatch = MATCH_THRESHOLD;
        Vec3 bestAimDir = null;
        int mountsChecked = 0;
        
        LOGGER.debug("[VSAW_SCOPE] findMountByAimDirectionGlobal: searching for projectile dir={} worldPos={}",
                projectileVelocityDir, projectileWorldPos);
        
        List<Object> ships = VsCompat.getAllShips(level);
        LOGGER.debug("[VSAW_SCOPE] findMountByAimDirectionGlobal: found {} ships", ships.size());
        
        for (Object ship : ships) {
            AABBdc shipAABB = VsCompat.getShipAABB(ship);
            long shipId = VsCompat.getShipId(ship);
            
            if (shipAABB != null) {
                boolean projectileInShipAABB = projectileWorldPos.x >= shipAABB.minX() && projectileWorldPos.x <= shipAABB.maxX()
                        && projectileWorldPos.y >= shipAABB.minY() && projectileWorldPos.y <= shipAABB.maxY()
                        && projectileWorldPos.z >= shipAABB.minZ() && projectileWorldPos.z <= shipAABB.maxZ();
                
                LOGGER.debug("[VSAW_SCOPE] findMountByAimDirectionGlobal: ship {} AABB=({},{},{})-({},{},{}) projectileInAABB={}",
                        shipId,
                        String.format("%.1f", shipAABB.minX()), String.format("%.1f", shipAABB.minY()), String.format("%.1f", shipAABB.minZ()),
                        String.format("%.1f", shipAABB.maxX()), String.format("%.1f", shipAABB.maxY()), String.format("%.1f", shipAABB.maxZ()),
                        projectileInShipAABB);
            }
            
            Vec3 shipLocalVelocityDir = VsCompat.worldToShipDirection(ship, projectileVelocityDir);
            LOGGER.debug("[VSAW_SCOPE] findMountByAimDirectionGlobal: ship {} worldDir={} -> localDir={}",
                    shipId, projectileVelocityDir, shipLocalVelocityDir);
            
            Object chunkClaim = VsCompat.getChunkClaim(ship);
            LOGGER.debug("[VSAW_SCOPE] findMountByAimDirectionGlobal: ship {} chunkClaim={}", shipId, chunkClaim != null ? chunkClaim.getClass().getSimpleName() : "null");
            if (chunkClaim != null) {
                Iterable<int[]> chunks = VsCompat.getChunkClaimChunks(chunkClaim);
                int chunkCount = 0;
                int nullChunkCount = 0;
                int beCount = 0;
                for (int[] chunkPos : chunks) {
                    if (chunkPos == null || chunkPos.length < 2) {
                        continue;
                    }
                    chunkCount++;
                    int chunkX = chunkPos[0];
                    int chunkZ = chunkPos[1];
                    LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null) {
                        nullChunkCount++;
                        LOGGER.debug("[VSAW_SCOPE] findMountByAimDirectionGlobal: ship {} chunk ({}, {}) is null", shipId, chunkX, chunkZ);
                        continue;
                    }
                    beCount += chunk.getBlockEntities().size();
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (!isCannonMount(be)) {
                            continue;
                        }
                        mountsChecked++;
                        
                        BlockPos mountPos = be.getBlockPos();
                        Vec3 mountAimLocal = getAimDirectionQuiet(level, mountPos, Direction.NORTH, 1.0f, false).orElse(null);
                        if (mountAimLocal == null) {
                            continue;
                        }
                        
                        Vec3 mountAimWorld = VsCompat.shipToWorldDirection(ship, mountAimLocal);
                        Vec3 mountAimNorm = mountAimWorld.normalize();
                        double match = projectileVelocityDir.dot(mountAimNorm);
                        
                        if (match > bestMatch) {
                            bestMatch = match;
                            best = mountPos;
                            bestAimDir = mountAimWorld;
                            LOGGER.debug("[VSAW_SCOPE] findMountByAimDirectionGlobal: ship {} mount at {} localAim={} worldAim={} match={}",
                                    shipId, mountPos, mountAimLocal, mountAimNorm, String.format("%.4f", match));
                        }
                    }
                }
                LOGGER.debug("[VSAW_SCOPE] findMountByAimDirectionGlobal: ship {} iterated {} chunks, {} null, {} blockEntities",
                        shipId, chunkCount, nullChunkCount, beCount);
            } else {
                LOGGER.debug("[VSAW_SCOPE] findMountByAimDirectionGlobal: ship {} has no chunkClaim, skipping", shipId);
            }
        }
        
        int worldspawnRadius = 8;
        for (int cx = -worldspawnRadius; cx <= worldspawnRadius; cx++) {
            for (int cz = -worldspawnRadius; cz <= worldspawnRadius; cz++) {
                LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!isCannonMount(be)) {
                        continue;
                    }
                    Object mountShip = VsCompat.findShip(level, be.getBlockPos());
                    if (mountShip != null) {
                        continue;
                    }
                    mountsChecked++;
                    
                    BlockPos mountPos = be.getBlockPos();
                    Vec3 mountAim = getAimDirectionQuiet(level, mountPos, Direction.NORTH, 1.0f, true).orElse(null);
                    if (mountAim == null) {
                        continue;
                    }
                    
                    Vec3 mountAimNorm = mountAim.normalize();
                    double match = projectileVelocityDir.dot(mountAimNorm);
                    
                    if (match > bestMatch) {
                        bestMatch = match;
                        best = mountPos;
                        bestAimDir = mountAim;
                        LOGGER.debug("[VSAW_SCOPE] findMountByAimDirectionGlobal: worldspawn mount at {} aim={} match={}",
                                mountPos, mountAimNorm, String.format("%.4f", match));
                    }
                }
            }
        }
        
        LOGGER.debug("[VSAW_SCOPE] findMountByAimDirectionGlobal: checked {} cannon mounts", mountsChecked);
        
        if (best != null && bestAimDir != null) {
            LOGGER.debug("[VSAW_SCOPE] findMountByAimDirectionGlobal: BEST mount at {} with match={}", best, bestMatch);
            return Optional.of(new MountMatchResult(best, bestAimDir, bestMatch));
        }
        LOGGER.debug("[VSAW_SCOPE] findMountByAimDirectionGlobal: no mount found with match >= {}", MATCH_THRESHOLD);
        return Optional.empty();
    }

    public record MountMatchResult(BlockPos mountPos, Vec3 aimDirection, double matchScore) {}

    private static Optional<Vec3> getAimDirectionQuiet(Level level, BlockPos mountPos, Direction fallbackFacing, float partialTicks, boolean applyShipTransform) {
        BlockEntity be = level.getBlockEntity(mountPos);
        boolean isMount = isCannonMount(be);

        LOGGER.debug("[VSAW_SCOPE] getAimDirectionQuiet: mountPos={} isCannonMount={} blockEntityClass={}",
                mountPos, isMount, be != null ? be.getClass().getSimpleName() : "null");

        if (!isMount) {
            LOGGER.debug("[VSAW_SCOPE] getAimDirectionQuiet: NOT a cannon mount, using fallbackFacing={}", fallbackFacing);
            Vec3 fallback = Vec3.atLowerCornerOf(fallbackFacing.getNormal()).normalize();
            if (applyShipTransform) {
                fallback = VsCompat.shipToWorldDirection(level, mountPos, fallback);
            }
            return Optional.of(fallback);
        }

        Vec3 byContraption = tryDirectionFromContraptionQuiet(be, partialTicks).orElse(null);
        LOGGER.debug("[VSAW_SCOPE] getAimDirectionQuiet: byContraption={}", byContraption);
        if (byContraption != null) {
            if (applyShipTransform) {
                Vec3 transformed = VsCompat.shipToWorldDirection(level, mountPos, byContraption);
                LOGGER.debug("[VSAW_SCOPE] getAimDirectionQuiet: byContraption after shipTransform={}", transformed);
                return Optional.of(transformed);
            }
            return Optional.of(byContraption);
        }

        Vec3 byMount = tryDirectionFromMountOffsetsQuiet(be, partialTicks).orElse(Vec3.atLowerCornerOf(fallbackFacing.getNormal()).normalize());
        LOGGER.debug("[VSAW_SCOPE] getAimDirectionQuiet: byMount={}", byMount);
        if (applyShipTransform) {
            Vec3 result = VsCompat.shipToWorldDirection(level, mountPos, byMount);
            LOGGER.debug("[VSAW_SCOPE] getAimDirectionQuiet: byMount after shipTransform={}", result);
            return Optional.of(result);
        }
        return Optional.of(byMount);
    }

    private static Optional<Vec3> tryDirectionFromContraptionQuiet(Object mount, float partialTicks) {
        try {
            Object poce = callNoArg(mount, "getContraption");
            if (poce == null) {
                LOGGER.debug("[VSAW_SCOPE] tryDirectionFromContraptionQuiet: getContraption returned null");
                return Optional.empty();
            }
            Object initial = callNoArg(poce, "getInitialOrientation");
            if (!(initial instanceof Direction direction)) {
                LOGGER.debug("[VSAW_SCOPE] tryDirectionFromContraptionQuiet: getInitialOrientation returned {} (not Direction)", initial);
                return Optional.empty();
            }
            Vec3 base = Vec3.atLowerCornerOf(direction.getNormal());
            Method applyRotation = poce.getClass().getMethod("applyRotation", Vec3.class, float.class);
            Object rotated = applyRotation.invoke(poce, base, partialTicks);
            if (rotated instanceof Vec3 vec) {
                LOGGER.debug("[VSAW_SCOPE] tryDirectionFromContraptionQuiet: success, direction={}", vec.normalize());
                return Optional.of(vec.normalize());
            }
            LOGGER.debug("[VSAW_SCOPE] tryDirectionFromContraptionQuiet: applyRotation returned non-Vec3: {}", rotated);
            return Optional.empty();
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.debug("[VSAW_SCOPE] tryDirectionFromContraptionQuiet: exception - {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static Optional<Vec3> tryDirectionFromMountOffsetsQuiet(Object mount, float partialTicks) {
        try {
            Direction baseDir = Direction.NORTH;
            Object direction = callNoArg(mount, "getContraptionDirection");
            if (direction instanceof Direction d) {
                baseDir = d;
            } else {
                LOGGER.debug("[VSAW_SCOPE] tryDirectionFromMountOffsetsQuiet: getContraptionDirection returned {} (not Direction)", direction);
            }
            float yaw = callFloat(mount, "getYawOffset", partialTicks);
            float pitch = callFloat(mount, "getPitchOffset", partialTicks);
            Vec3 result = directionFromYawPitch(baseDir.toYRot() + yaw, pitch);
            LOGGER.debug("[VSAW_SCOPE] tryDirectionFromMountOffsetsQuiet: baseDir={} yaw={} pitch={} result={}", baseDir, yaw, pitch, result);
            return Optional.of(result);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.debug("[VSAW_SCOPE] tryDirectionFromMountOffsetsQuiet: exception - {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public static boolean isCannonMount(BlockEntity be) {
        if (be == null) {
            return false;
        }
        String name = be.getClass().getName();
        if (name.equals(CANNON_MOUNT) || name.equals(FIXED_CANNON_MOUNT) || name.equals(COMPACT_CANNON_MOUNT)) {
            return true;
        }
        for (Class<?> c = be.getClass(); c != null; c = c.getSuperclass()) {
            String className = c.getName();
            if (className.equals(CANNON_MOUNT) || className.equals(FIXED_CANNON_MOUNT) || className.equals(COMPACT_CANNON_MOUNT)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<Vec3> getAimDirection(Level level, BlockPos mountPos, Direction fallbackFacing, float partialTicks) {
        return getAimDirection(level, mountPos, fallbackFacing, partialTicks, true);
    }

    public static Optional<Vec3> getAimDirection(Level level, BlockPos mountPos, Direction fallbackFacing, float partialTicks, boolean applyShipTransform) {
        BlockEntity be = level.getBlockEntity(mountPos);
        boolean isMount = isCannonMount(be);

        LOGGER.debug("[VSAW_SCOPE] getAimDirection: mountPos={} isCannonMount={} blockEntityClass={} applyShipTransform={}",
                mountPos, isMount, be != null ? be.getClass().getSimpleName() : "null", applyShipTransform);

        if (!isMount) {
            LOGGER.debug("[VSAW_SCOPE] getAimDirection: NOT a cannon mount, using fallbackFacing={}", fallbackFacing);
            Vec3 fallback = Vec3.atLowerCornerOf(fallbackFacing.getNormal()).normalize();
            if (applyShipTransform) {
                fallback = VsCompat.shipToWorldDirection(level, mountPos, fallback);
            }
            return Optional.of(fallback);
        }

        Vec3 byContraption = tryDirectionFromContraption(be, partialTicks).orElse(null);
        if (byContraption != null) {
            LOGGER.debug("[VSAW_SCOPE] getAimDirection: using byContraption={}", byContraption);
            if (applyShipTransform) {
                Vec3 transformed = VsCompat.shipToWorldDirection(level, mountPos, byContraption);
                LOGGER.debug("[VSAW_SCOPE] getAimDirection: byContraption after shipTransform={}", transformed);
                return Optional.of(transformed);
            }
            return Optional.of(byContraption);
        }

        Vec3 byMount = tryDirectionFromMountOffsets(be, partialTicks).orElse(Vec3.atLowerCornerOf(fallbackFacing.getNormal()).normalize());
        LOGGER.debug("[VSAW_SCOPE] getAimDirection: byMount={} (using mount offsets or fallback)", byMount);
        if (applyShipTransform) {
            Vec3 result = VsCompat.shipToWorldDirection(level, mountPos, byMount);
            LOGGER.debug("[VSAW_SCOPE] getAimDirection: byMount after shipTransform={}", result);
            return Optional.of(result);
        }
        return Optional.of(byMount);
    }

    public static Optional<Vec3> getAimUpDirection(Level level, BlockPos mountPos, Direction fallbackFacing, Direction scopeUp, float partialTicks) {
        return getAimUpDirection(level, mountPos, fallbackFacing, scopeUp, partialTicks, true);
    }

    public static Optional<Vec3> getAimUpDirection(Level level, BlockPos mountPos, Direction fallbackFacing, Direction scopeUp, float partialTicks, boolean applyShipTransform) {
        BlockEntity be = level.getBlockEntity(mountPos);
        Vec3 fallbackUp = Vec3.atLowerCornerOf(scopeUp.getNormal()).normalize();
        if (!isCannonMount(be)) {
            return Optional.of(applyShipTransform ? VsCompat.shipToWorldDirection(level, mountPos, fallbackUp) : fallbackUp);
        }

        Vec3 byContraption = tryUpFromContraption(be, fallbackUp, partialTicks).orElse(null);
        if (byContraption != null) {
            return Optional.of(applyShipTransform ? VsCompat.shipToWorldDirection(level, mountPos, byContraption) : byContraption);
        }

        Vec3 forward = tryDirectionFromMountOffsets(be, partialTicks).orElse(Vec3.atLowerCornerOf(fallbackFacing.getNormal()).normalize());
        Vec3 projectedUp = projectUp(fallbackUp, forward);
        return Optional.of(applyShipTransform ? VsCompat.shipToWorldDirection(level, mountPos, projectedUp) : projectedUp);
    }

    private static Optional<Vec3> tryDirectionFromContraption(Object mount, float partialTicks) {
        try {
            Object poce = callNoArg(mount, "getContraption");
            if (poce == null) {
                LOGGER.debug("[VSAW_SCOPE] tryDirectionFromContraption: getContraption returned null");
                return Optional.empty();
            }
            Object initial = callNoArg(poce, "getInitialOrientation");
            if (!(initial instanceof Direction direction)) {
                LOGGER.debug("[VSAW_SCOPE] tryDirectionFromContraption: getInitialOrientation returned {} (not Direction)", initial);
                return Optional.empty();
            }
            Vec3 base = Vec3.atLowerCornerOf(direction.getNormal());
            Method applyRotation = poce.getClass().getMethod("applyRotation", Vec3.class, float.class);
            Object rotated = applyRotation.invoke(poce, base, partialTicks);
            if (rotated instanceof Vec3 vec) {
                LOGGER.debug("[VSAW_SCOPE] tryDirectionFromContraption: success, direction={}", vec.normalize());
                return Optional.of(vec.normalize());
            } else {
                LOGGER.debug("[VSAW_SCOPE] tryDirectionFromContraption: applyRotation returned non-Vec3: {}", rotated);
                return Optional.empty();
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.debug("[VSAW_SCOPE] tryDirectionFromContraption: exception - {}", e.getClass().getSimpleName(), e);
            return Optional.empty();
        }
    }

    private static Optional<Vec3> tryUpFromContraption(Object mount, Vec3 fallbackUp, float partialTicks) {
        try {
            Object poce = callNoArg(mount, "getContraption");
            if (poce == null) {
                return Optional.empty();
            }
            Object initial = callNoArg(poce, "getInitialOrientation");
            Vec3 localUp = fallbackUp;
            if (initial instanceof Direction direction && Math.abs(Vec3.atLowerCornerOf(direction.getNormal()).normalize().dot(localUp)) > 0.98) {
                localUp = new Vec3(0.0, 0.0, 1.0);
            }
            Method applyRotation = poce.getClass().getMethod("applyRotation", Vec3.class, float.class);
            Object rotated = applyRotation.invoke(poce, localUp, partialTicks);
            return rotated instanceof Vec3 vec ? Optional.of(vec.normalize()) : Optional.empty();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Vec3> tryDirectionFromMountOffsets(Object mount, float partialTicks) {
        try {
            Direction baseDir = Direction.NORTH;
            Object direction = callNoArg(mount, "getContraptionDirection");
            if (direction instanceof Direction d) {
                baseDir = d;
            } else {
                LOGGER.debug("[VSAW_SCOPE] tryDirectionFromMountOffsets: getContraptionDirection returned {} (not Direction)", direction);
            }
            float yaw = callFloat(mount, "getYawOffset", partialTicks);
            float pitch = callFloat(mount, "getPitchOffset", partialTicks);
            LOGGER.debug("[VSAW_SCOPE] tryDirectionFromMountOffsets: baseDir={} yaw={} pitch={}", baseDir, yaw, pitch);
            return Optional.of(directionFromYawPitch(baseDir.toYRot() + yaw, pitch));
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.debug("[VSAW_SCOPE] tryDirectionFromMountOffsets: exception - {}", e.getClass().getSimpleName(), e);
            return Optional.empty();
        }
    }

    private static Object callNoArg(Object target, String method) throws ReflectiveOperationException {
        Method m = target.getClass().getMethod(method);
        return m.invoke(target);
    }

    private static float callFloat(Object target, String method, float partialTicks) throws ReflectiveOperationException {
        Method m = target.getClass().getMethod(method, float.class);
        Object value = m.invoke(target, partialTicks);
        return value instanceof Number n ? n.floatValue() : 0.0f;
    }

    public static Object invokeNoArg(Object target, String method) throws ReflectiveOperationException {
        Method m = target.getClass().getMethod(method);
        return m.invoke(target);
    }

    public static float invokeFloatNoArg(Object target, String method) throws ReflectiveOperationException {
        Method m = target.getClass().getMethod(method);
        Object value = m.invoke(target);
        return value instanceof Number n ? n.floatValue() : 0.0f;
    }

    public static float invokeFloat(Object target, String method, float argument) throws ReflectiveOperationException {
        Method m = target.getClass().getMethod(method, float.class);
        Object value = m.invoke(target, argument);
        return value instanceof Number n ? n.floatValue() : 0.0f;
    }

    /**
     * Read initialOrientation from the CBC cannon's contraption via reflection.
     * Returns the Direction from PitchOrientedContraptionEntity.getInitialOrientation(),
     * or null if the cannon block is not a CBC mount or the reflection fails.
     */
    @Nullable
    public static Direction getInitialOrientationFromCannon(Level level, BlockPos mount) {
        try {
            BlockEntity be = level.getBlockEntity(mount);
            if (be == null) return null;
            Object contraption = callNoArg(be, "getContraption");
            if (contraption == null) return null;
            Object initial = callNoArg(contraption, "getInitialOrientation");
            if (initial instanceof Direction d) return d;
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return null;
    }

    /**
     * Read viewXRot (pitch) from a CBC PitchOrientedContraptionEntity via reflection.
     * Returns the value of getViewXRot() or 0 on failure.
     */
    public static float getCbcViewXRot(Object entity, float partialTicks) {
        try {
            return invokeFloat(entity, "getViewXRot", partialTicks);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return 0.0f;
        }
    }

    /**
     * Read viewYRot (yaw) from a CBC PitchOrientedContraptionEntity via reflection.
     * Returns the value of getViewYRot() or 0 on failure.
     */
    public static float getCbcViewYRot(Object entity, float partialTicks) {
        try {
            return invokeFloat(entity, "getViewYRot", partialTicks);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return 0.0f;
        }
    }

    /**
     * Read initialYaw from a CBC PitchOrientedContraptionEntity via reflection.
     * Returns the value of getInitialYaw() or 0 on failure.
     */
    public static float getCbcInitialYaw(Object entity) {
        try {
            return invokeFloatNoArg(entity, "getInitialYaw");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return 0.0f;
        }
    }

    /**
     * Read the mounted cannon contraption entity from a cannon mount.
     * The returned object is intentionally untyped so this remains optional CBC compatibility.
     */
    @Nullable
    public static Object getCannonContraptionEntity(Level level, BlockPos mount) {
        try {
            BlockEntity be = level.getBlockEntity(mount);
            return be == null ? null : callNoArg(be, "getContraption");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    public static Vec3 directionFromYawPitch(float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double x = -Math.sin(yaw) * Math.cos(pitch);
        double y = Math.sin(pitch);
        double z = Math.cos(yaw) * Math.cos(pitch);
        return new Vec3(x, y, z).normalize();
    }

    private static Vec3 projectUp(Vec3 up, Vec3 forward) {
        Vec3 f = forward.normalize();
        Vec3 projected = up.subtract(f.scale(up.dot(f)));
        if (projected.lengthSqr() < 1.0e-8) {
            projected = Math.abs(f.dot(new Vec3(0.0, 1.0, 0.0))) > 0.98
                    ? new Vec3(0.0, 0.0, 1.0)
                    : new Vec3(0.0, 1.0, 0.0);
            projected = projected.subtract(f.scale(projected.dot(f)));
        }
        return projected.normalize();
    }
}
