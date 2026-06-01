package com.erika.vsanalogwarfare.scope.ballistics;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BallisticProfileResolver {
    /** Temporary verbose resolver diagnostics for in-game testing. */
    private static final boolean DEBUG_RESOLVER = true;

    private BallisticProfileResolver() {}

    public static Optional<BallisticProfile> resolve(Level level, BlockPos mountPos) {
        if (level == null || mountPos == null) return Optional.empty();
        if (!level.isLoaded(mountPos)) {
            debug(level, "[Ballistics] mount {} is not loaded", mountPos);
            return Optional.empty();
        }
        BlockEntity mount = level.getBlockEntity(mountPos);
        if (mount == null) {
            debug(level, "[Ballistics] no mount block entity at {}", mountPos);
            return Optional.empty();
        }
        Object poce = invokeNoArg(mount, "getContraption");
        Object contraption = invokeNoArg(poce, "getContraption");
        debug(level, "[Ballistics] mountPos={} mountClass={} poceClass={} contraptionClass={}",
                mountPos, className(mount), className(poce), className(contraption));
        if (contraption == null) return Optional.empty();

        try {
            Optional<BallisticProfile> result;
            if (isAutocannonContraption(contraption)) {
                result = resolveAutocannon(level, contraption);
            } else if (isMediumCannonContraption(level, contraption)) {
                result = resolveMediumCannon(level, contraption);
            } else if (isBlockLoadedCannonContraption(contraption)) {
                result = resolveBlockLoadedCannon(level, contraption);
            } else {
                debug(level, "[Ballistics] unsupported contraption class {}", className(contraption));
                return Optional.empty();
            }
            debug(level, "[Ballistics] result present={} profile={}", result.isPresent(), result.orElse(BallisticProfile.EMPTY));
            return result;
        } catch (Throwable t) {
            debug(level, "[Ballistics] resolver exception for contraption {}: {}", className(contraption), t.toString());
            VSAnalogWarfare.LOGGER.warn("[Ballistics] resolver exception", t);
            return Optional.empty();
        }
    }

    private static boolean isAutocannonContraption(Object contraption) {
        return className(contraption).contains("MountedAutocannonContraption");
    }

    private static boolean isMediumCannonContraption(Level level, Object contraption) {
        String name = className(contraption).toLowerCase(java.util.Locale.ROOT);
        if (name.contains("mediumcannon") || name.contains("medium_cannon")) return true;
        Object breech = breechAtStart(contraption);
        ItemStack round = peekInputRound(breech);
        return !round.isEmpty() && hasMethod(round.getItem(), "getMediumcannonProjectile", ItemStack.class, Level.class);
    }

    private static boolean isBlockLoadedCannonContraption(Object contraption) {
        String name = className(contraption);
        return name.contains("MountedBigCannonContraption")
                || name.contains("MountedDualCannonContraption")
                || name.contains("MountedProjectileRackContraption")
                || invokeNoArg(contraption, "getMaxSafeCharges") instanceof Number;
    }

    private static Optional<BallisticProfile> resolveAutocannon(Level level, Object contraption) throws ReflectiveOperationException {
        ItemCannonBasics basics = itemCannonBasics(level, contraption, "autocannon").orElse(null);
        if (basics == null) return Optional.empty();

        Object projectile = invoke(basics.round().getItem(), "getAutocannonProjectile", new Class<?>[]{ItemStack.class, Level.class}, basics.round(), level);
        BallisticParts parts = readBallisticParts(level, projectile).orElse(null);
        if (parts == null) return Optional.empty();
        return Optional.of(BallisticProfile.of(basics.speed(), parts.gravity, parts.drag, parts.quadraticDrag, basics.lifetimeTicks(),
                itemId(basics.round()), "autocannon", "autocannon barrelTravel=" + basics.barrelTravelled()));
    }

    private static Optional<BallisticProfile> resolveMediumCannon(Level level, Object contraption) throws ReflectiveOperationException {
        ItemCannonBasics basics = itemCannonBasics(level, contraption, "medium").orElse(null);
        if (basics == null) return Optional.empty();

        Object item = basics.round().getItem();
        Object projectile = invokeBest(item, "getMediumcannonProjectile", basics.round(), level);
        debug(level, "[Ballistics][Medium] round={} itemClass={} projectile={} speed={} barrelTravel={}",
                itemId(basics.round()), className(item), className(projectile), basics.speed(), basics.barrelTravelled());
        BallisticParts parts = readBallisticParts(level, projectile).orElse(null);
        if (parts == null) return Optional.empty();
        return Optional.of(BallisticProfile.of(basics.speed(), parts.gravity, parts.drag, parts.quadraticDrag, basics.lifetimeTicks(),
                itemId(basics.round()), "medium_cannon", "medium cannon barrelTravel=" + basics.barrelTravelled()));
    }

    private static Optional<ItemCannonBasics> itemCannonBasics(Level level, Object contraption, String label) throws ReflectiveOperationException {
        BlockPos startPos = (BlockPos) field(contraption, "startPos");
        Direction orientation = (Direction) invokeNoArg(contraption, "initialOrientation");
        Map<?, ?> presentBlockEntities = asMap(field(contraption, "presentBlockEntities"));
        if (startPos == null || orientation == null) {
            debug(level, "[Ballistics][{}] missing start/orientation start={} orientation={}", label, startPos, orientation);
            return Optional.empty();
        }
        Object breech = presentBlockEntities.get(startPos);
        if (breech == null) {
            debug(level, "[Ballistics][{}] no breech at start={} presentKeys={}", label, startPos, presentBlockEntities.keySet());
            return Optional.empty();
        }
        ItemStack roundStack = peekInputRound(breech);
        if (roundStack.isEmpty()) {
            debug(level, "[Ballistics][{}] no input round in breech={} nbt={}", label, className(breech), nbtSummary(breech));
            return Optional.empty();
        }
        Object material = field(contraption, "cannonMaterial");
        Object properties = invokeNoArg(material, "properties");
        if (properties == null) {
            debug(level, "[Ballistics][{}] missing material/properties material={}", label, className(material));
            return Optional.empty();
        }
        int barrelTravelled = countItemCannonBarrels(startPos.relative(orientation), orientation, presentBlockEntities, roundStack);
        double speed = number(invokeNoArg(properties, "baseSpeed"), 0.0);
        double speedIncrease = number(invokeNoArg(properties, "speedIncreasePerBarrel"), 0.0);
        int maxIncreases = (int) number(invokeNoArg(properties, "maxSpeedIncreases"), 0.0);
        speed += Math.min(barrelTravelled, Math.max(0, maxIncreases)) * speedIncrease;
        int lifetime = (int) number(invokeNoArg(properties, "projectileLifetime"), 0.0);
        debug(level, "[Ballistics][{}] start={} orientation={} breech={} round={} material={} props={} speed={} base+inc={}+{} maxInc={} travel={}",
                label, startPos, orientation, className(breech), itemId(roundStack), className(material), className(properties), speed,
                number(invokeNoArg(properties, "baseSpeed"), 0.0), speedIncrease, maxIncreases, barrelTravelled);
        return Optional.of(new ItemCannonBasics(roundStack, speed, lifetime, barrelTravelled));
    }

    private static ItemStack peekInputRound(Object breech) {
        Object input = invokeNoArg(breech, "getInputBuffer");
        if (input instanceof ItemStack stack) return stack.copy();
        if (input instanceof Deque<?> deque && !deque.isEmpty() && deque.peek() instanceof ItemStack stack) return stack.copy();
        Object magazineObj = invokeNoArg(breech, "getMagazine");
        if (magazineObj instanceof ItemStack magazine && !magazine.isEmpty() && magazine.hasTag()) {
            CompoundTag tag = magazine.getTag();
            if (tag != null && tag.contains("Ammo")) {
                ItemStack main = ItemStack.of(tag.getCompound("Ammo"));
                if (!main.isEmpty()) { main.setCount(1); return main; }
            }
            if (tag != null && tag.contains("Tracers")) {
                ItemStack tracer = ItemStack.of(tag.getCompound("Tracers"));
                if (!tracer.isEmpty()) { tracer.setCount(1); return tracer; }
            }
        }
        return ItemStack.EMPTY;
    }

    private static int countItemCannonBarrels(BlockPos currentPos, Direction orientation, Map<?, ?> presentBlockEntities, ItemStack roundStack) {
        int travelled = 0;
        for (int i = 0; i < 128; i++) {
            Object be = presentBlockEntities.get(currentPos);
            if (be == null) break;
            Object behavior = invokeNoArg(be, "cannonBehavior");
            Object canLoad = invokeBest(behavior, "canLoadItem", roundStack);
            if (!(canLoad instanceof Boolean b) || !b) break;
            travelled++;
            currentPos = currentPos.relative(orientation);
        }
        return travelled;
    }

    private static Optional<BallisticProfile> resolveBlockLoadedCannon(Level level, Object contraption) throws ReflectiveOperationException {
        BlockPos startPos = (BlockPos) field(contraption, "startPos");
        Direction orientation = (Direction) invokeNoArg(contraption, "initialOrientation");
        Map<?, ?> presentBlockEntities = asMap(field(contraption, "presentBlockEntities"));
        Map<?, ?> blocks = asMap(field(contraption, "blocks"));
        debug(level, "[Ballistics][Big] class={} start={} orientation={} presentBlockEntities={} blocks={}",
                className(contraption), startPos, orientation, presentBlockEntities.size(), blocks.size());
        dumpBlockLoadedState(level, presentBlockEntities, blocks);
        if (startPos == null || orientation == null) return Optional.empty();

        BlockLoadedAccum direct = scanAlongCannon(level, presentBlockEntities, startPos, orientation);
        debug(level, "[Ballistics][Big direct] final charges={} projectile={} projectileId={} infos={}",
                direct.charges, className(direct.projectile), direct.projectileId, direct.infoCount);
        Optional<BallisticProfile> directProfile = profileFromAccum(level, direct, "big_cannon", "direct");
        if (directProfile.isPresent()) return directProfile;

        BlockLoadedAccum full = scanAllLoadedInfos(level, presentBlockEntities, orientation);
        debug(level, "[Ballistics][Big scan] final charges={} projectile={} projectileId={} infos={}",
                full.charges, className(full.projectile), full.projectileId, full.infoCount);
        return profileFromAccum(level, full, "big_cannon", "scan");
    }

    private static BlockLoadedAccum scanAlongCannon(Level level, Map<?, ?> presentBlockEntities, BlockPos startPos, Direction orientation) {
        BlockLoadedAccum accum = new BlockLoadedAccum();
        BlockPos current = startPos.immutable();
        for (int i = 0; i < 256; i++) {
            Object cbe = presentBlockEntities.get(current);
            if (cbe == null) {
                debug(level, "[Ballistics][Big direct] break no cbe at local {}", current);
                break;
            }
            StructureBlockInfo info = loadedInfo(level, cbe).orElse(null);
            if (info == null || info.state().isAir()) {
                debug(level, "[Ballistics][Big direct] local={} cbe={} empty behavior/nbt={}", current, className(cbe), nbtSummary(cbe));
                current = current.relative(orientation);
                continue;
            }
            processLoadedInfo(level, accum, info, orientation, "direct local=" + current);
            current = current.relative(orientation);
        }
        return accum;
    }

    private static BlockLoadedAccum scanAllLoadedInfos(Level level, Map<?, ?> presentBlockEntities, Direction orientation) {
        BlockLoadedAccum accum = new BlockLoadedAccum();
        debug(level, "[Ballistics][Big scan] scanning {} block entities", presentBlockEntities.size());
        for (Map.Entry<?, ?> entry : presentBlockEntities.entrySet()) {
            Object cbe = entry.getValue();
            StructureBlockInfo info = loadedInfo(level, cbe).orElse(null);
            if (info == null || info.state().isAir()) continue;
            processLoadedInfo(level, accum, info, orientation, "scan local=" + entry.getKey());
        }
        return accum;
    }

    private static void processLoadedInfo(Level level, BlockLoadedAccum accum, StructureBlockInfo info, Direction orientation, String source) {
        accum.infoCount++;
        Block block = info.state().getBlock();
        Object chargePower = invokeBest(block, "getChargePower", info);
        boolean projectileBlock = isProjectileBlock(block);
        debug(level, "[Ballistics][Big {}] block={} blockClass={} projectileBlock={} chargePower={} infoNbtKeys={}",
                source, blockId(block), className(block), projectileBlock, chargePower, info.nbt() == null ? "null" : info.nbt().getAllKeys());
        if (chargePower instanceof Number) {
            accum.charges += Math.max(0.0, number(chargePower, 0.0));
        }
        if (projectileBlock && accum.projectile == null) {
            accum.projectileInfos.add(info);
            accum.projectileId = blockId(block);
            accum.projectile = createProjectileFromBlock(level, block, accum.projectileInfos, orientation);
            debug(level, "[Ballistics][Big {}] projectileCreate result={}", source, className(accum.projectile));
            accum.charges += Math.max(0.0, number(invokeNoArg(accum.projectile, "addedChargePower"), 0.0));
            accum.charges += Math.max(0.0, number(invokeNoArg(accum.projectile, "getChargePower"), 0.0));
        }
    }

    private static Optional<BallisticProfile> profileFromAccum(Level level, BlockLoadedAccum accum, String cannonType, String source) {
        if (accum.projectile == null) return Optional.empty();
        BallisticParts parts = readBallisticParts(level, accum.projectile).orElse(null);
        if (parts == null) return Optional.empty();

        // CBC big cannons use accumulated propellant charge as their initial speed input.
        // CBC More Shells single/dual-cannon projectiles can instead carry their own fixed
        // launch speed (getInitVel) and may have zero separate propellant charge, so do not
        // reject a projectile just because charges == 0.
        double muzzleSpeed = accum.charges > 0.0 ? accum.charges : number(invokeNoArg(accum.projectile, "getInitVel"), 0.0);
        if (muzzleSpeed <= 0.0) muzzleSpeed = number(invokeNoArg(accum.projectile, "minimumChargePower"), 0.0);
        if (muzzleSpeed <= 0.0) return Optional.empty();
        debug(level, "[Ballistics][Big {}] profile muzzleSpeed={} charges={} projectileInitVel={} projectileId={}",
                source, muzzleSpeed, accum.charges, number(invokeNoArg(accum.projectile, "getInitVel"), 0.0), accum.projectileId);
        return Optional.of(BallisticProfile.of(muzzleSpeed, parts.gravity, parts.drag, parts.quadraticDrag, 0,
                accum.projectileId, cannonType, "block loaded " + source + " speed=" + String.format(java.util.Locale.ROOT, "%.2f", muzzleSpeed)
                        + " charge=" + String.format(java.util.Locale.ROOT, "%.2f", accum.charges)));
    }

    private static Optional<StructureBlockInfo> loadedInfo(Level level, Object cbe) {
        Object behavior = invokeNoArg(cbe, "cannonBehavior");
        Object infoObj = invokeNoArg(behavior, "block");
        if (infoObj instanceof StructureBlockInfo info && !info.state().isAir()) return Optional.of(info);

        Object tagObj = invokeNoArg(cbe, "saveWithFullMetadata");
        if (!(tagObj instanceof CompoundTag tag)) tagObj = invokeNoArg(cbe, "saveWithoutMetadata");
        if (!(tagObj instanceof CompoundTag tag)) return Optional.empty();
        if (!tag.contains("State")) return Optional.empty();
        try {
            BlockState state = NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag.getCompound("State"));
            BlockPos pos = tag.contains("Pos") ? BlockPos.of(tag.getLong("Pos")) : BlockPos.ZERO;
            CompoundTag data = tag.contains("Data") ? tag.getCompound("Data") : null;
            return Optional.of(new StructureBlockInfo(pos, state, data));
        } catch (RuntimeException ex) {
            debug(level, "[Ballistics][Dump] failed reading State from nbt keys={} err={}", tag.getAllKeys(), ex.toString());
            return Optional.empty();
        }
    }

    private static Object createProjectileFromBlock(Level level, Object block, List<StructureBlockInfo> projectileBlocks, Direction orientation) {
        Object complete = invokeBest(block, "isComplete", projectileBlocks, orientation);
        if (Boolean.FALSE.equals(complete)) {
            Object single = invokeBest(block, "getProjectile", level, List.of(projectileBlocks.get(projectileBlocks.size() - 1)));
            if (single != null) return single;
        }
        Object projectile = invokeBest(block, "getProjectile", level, projectileBlocks);
        if (projectile != null) return projectile;
        Object type = invokeNoArg(block, "getAssociatedEntityType");
        return invoke(type, "create", new Class<?>[]{Level.class}, level);
    }

    private static boolean isProjectileBlock(Object block) {
        return hasMethod(block, "getProjectile") || className(block).contains("ProjectileBlock");
    }

    private static Optional<BallisticParts> readBallisticParts(Level level, Object projectile) {
        if (projectile == null) return Optional.empty();
        Object props = invokeNoArg(projectile, "getBallisticProperties");
        double gravity = Double.NaN;
        double drag = Double.NaN;
        boolean quadratic = false;
        if (props != null) {
            gravity = number(invokeNoArg(props, "gravity"), Double.NaN);
            drag = number(invokeNoArg(props, "drag"), Double.NaN);
            Object quadObj = invokeNoArg(props, "isQuadraticDrag");
            quadratic = quadObj instanceof Boolean b && b;
        }
        if (!Double.isFinite(gravity)) gravity = number(invokeNoArg(projectile, "getGravity"), Double.NaN);
        if (!Double.isFinite(drag)) drag = number(invokeNoArg(projectile, "getDragForce"), Double.NaN);
        debug(level, "[Ballistics] ballisticParts projectile={} props={} gravity={} drag={} quadratic={}", className(projectile), className(props), gravity, drag, quadratic);
        if (!Double.isFinite(gravity) || !Double.isFinite(drag)) return Optional.empty();
        return Optional.of(new BallisticParts(gravity, drag, quadratic));
    }

    private static void dumpBlockLoadedState(Level level, Map<?, ?> presentBlockEntities, Map<?, ?> blocks) {
        if (!shouldDebug(level)) return;
        for (Map.Entry<?, ?> entry : blocks.entrySet()) {
            Object infoObj = entry.getValue();
            String block = "unknown";
            if (infoObj instanceof StructureBlockInfo info) block = blockId(info.state().getBlock());
            debug(level, "[Ballistics][Dump] blocks key={} infoClass={} block={}", entry.getKey(), className(infoObj), block);
        }
        for (Map.Entry<?, ?> entry : presentBlockEntities.entrySet()) {
            Object cbe = entry.getValue();
            Object behavior = invokeNoArg(cbe, "cannonBehavior");
            Object infoObj = invokeNoArg(behavior, "block");
            String behaviorBlock = "null";
            String nbtBlock = "null";
            if (infoObj instanceof StructureBlockInfo info) behaviorBlock = blockId(info.state().getBlock());
            Optional<StructureBlockInfo> nbtInfo = loadedInfo(level, cbe);
            if (nbtInfo.isPresent()) nbtBlock = blockId(nbtInfo.get().state().getBlock());
            debug(level, "[Ballistics][Dump] pbe key={} cbe={} behavior={} behaviorInfo={} behaviorBlock={} nbtBlock={} nbt={}",
                    entry.getKey(), className(cbe), className(behavior), className(infoObj), behaviorBlock, nbtBlock, nbtSummary(cbe));
        }
    }

    private static Object breechAtStart(Object contraption) {
        try {
            BlockPos startPos = (BlockPos) field(contraption, "startPos");
            Map<?, ?> presentBlockEntities = asMap(field(contraption, "presentBlockEntities"));
            return startPos == null ? null : presentBlockEntities.get(startPos);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        if (target == null) return null;
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String name) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(name);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (ReflectiveOperationException ignored) {}
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name);
                m.setAccessible(true);
                return m.invoke(target);
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(name, types);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (ReflectiveOperationException ignored) {}
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, types);
                m.setAccessible(true);
                return m.invoke(target, args);
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }

    private static Object invokeBest(Object target, String name, Object... args) {
        if (target == null) return null;
        Object publicResult = invokeBestFromMethods(target, target.getClass().getMethods(), name, args);
        if (publicResult != NO_MATCH) return publicResult;
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            Object declaredResult = invokeBestFromMethods(target, c.getDeclaredMethods(), name, args);
            if (declaredResult != NO_MATCH) return declaredResult;
        }
        return null;
    }

    private static final Object NO_MATCH = new Object();

    private static Object invokeBestFromMethods(Object target, Method[] methods, String name, Object... args) {
        for (Method m : methods) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            Class<?>[] types = m.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < types.length; i++) {
                if (args[i] != null && !wrap(types[i]).isAssignableFrom(args[i].getClass())) {
                    matches = false;
                    break;
                }
            }
            if (!matches) continue;
            try {
                m.setAccessible(true);
                return m.invoke(target, args);
            } catch (ReflectiveOperationException ignored) {}
        }
        return NO_MATCH;
    }

    private static boolean hasMethod(Object target, String name, Class<?>... params) {
        if (target == null) return false;
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name) && (params.length == 0 || m.getParameterCount() == params.length)) return true;
        }
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                if (params.length == 0 || m.getParameterCount() == params.length) return true;
            }
        }
        return false;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == boolean.class) return Boolean.class;
        if (type == long.class) return Long.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    private static boolean shouldDebug(Level level) {
        return DEBUG_RESOLVER && level != null && !level.isClientSide && level.getGameTime() % 40L == 0L;
    }

    private static void debug(Level level, String message, Object... args) {
        if (shouldDebug(level)) VSAnalogWarfare.LOGGER.info(message, args);
    }

    private static String className(Object object) {
        return object == null ? "null" : object.getClass().getName();
    }

    private static String blockId(Block block) {
        return block == null ? "null" : BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    private static String nbtSummary(Object cbe) {
        Object tagObj = invokeNoArg(cbe, "saveWithFullMetadata");
        if (!(tagObj instanceof CompoundTag tag)) return "no-tag";
        return "keys=" + tag.getAllKeys();
    }

    private record BallisticParts(double gravity, double drag, boolean quadraticDrag) {}
    private record ItemCannonBasics(ItemStack round, double speed, int lifetimeTicks, int barrelTravelled) {}
    private static final class BlockLoadedAccum {
        double charges;
        int infoCount;
        Object projectile;
        String projectileId = "";
        final List<StructureBlockInfo> projectileInfos = new ArrayList<>();
    }
}
