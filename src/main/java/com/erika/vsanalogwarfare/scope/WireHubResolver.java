package com.erika.vsanalogwarfare.scope;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.scope.compat.DbwWireCompat;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupReflection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Resolves the scope's controller-hub link to a position that actually hosts a
 * Tweaked Controller Hub — the same verify-at-use discipline the setup block's
 * controller replay and the scope's cannon links already follow. A VMod
 * schematic paste rebases links onto the pasted ship, but a hub outside the
 * pasted placement (dock/ground hub, hub on a separately pasted ship) keeps a
 * stale anchor, and a controller pointed at a non-hub position equips fine yet
 * sends nowhere. When verification fails, the ladder below heals the link and
 * persists it, mirroring how {@code initializeDefaultPrimaryLink} self-heals
 * cannon links.
 */
public final class WireHubResolver {
    /** Same per-ship volume cap as the VMod paste scan. */
    private static final long MAX_SCAN_VOLUME = 1_000_000L;
    /** Ground-scope hub search radius, matching the primary link's nearest-mount scan. */
    private static final int GROUND_SCAN_RADIUS = 8;

    private WireHubResolver() {
    }

    /**
     * Verified position of the scope's linked hub, or {@code null} when the scope
     * has no link or nothing plausible could be resolved. A stale link is healed
     * (and persisted on the scope) when exactly one live hub can be found; the
     * caller never receives a position worse than the plain link resolution.
     */
    @Nullable
    public static BlockPos resolveVerified(Level level, ScopeBlockEntity scope, @Nullable ServerPlayer feedback) {
        ScopeCannonLink link = scope.getWireHubLink();
        if (link == null) {
            return null;
        }
        BlockPos resolved = scope.resolveWireHubPos();
        if (resolved != null && isHubAt(level, resolved)) {
            return resolved;
        }

        // Ground/dock hubs never participate in the ship-id rebase, so the fallback
        // anchor is the one position that survives a paste untouched.
        BlockPos fallback = link.fallbackPos();
        if (fallback != null && isHubAt(level, fallback)) {
            return heal(level, scope, feedback, fallback, resolved);
        }

        HubScan scan = scope.getShipId() >= 0L
                ? scanShipForHubs(level, scope)
                : scanNearbyForHubs(level, scope.getBlockPos());
        if (scan.single != null) {
            return heal(level, scope, feedback, scan.single, resolved);
        }
        if (scan.multiple && feedback != null) {
            feedback.displayClientMessage(Component.literal(
                    "Multiple controller hubs found - re-link the scope with the analog screwdriver."), true);
        }
        if (feedback != null) {
            feedback.displayClientMessage(Component.literal(
                    "Scope controller link is stale - re-link the hub with the analog screwdriver."), true);
        }
        VSAnalogWarfare.LOGGER.warn("[VSAW] Scope at {} controller link is stale (resolved {}, fallback {}); no live hub found",
                scope.getBlockPos(), resolved, fallback);
        // Keep today's behavior rather than equipping nothing: the resolved position
        // may still work where our block lookup cannot see ship blocks.
        return resolved;
    }

    private static BlockPos heal(Level level, ScopeBlockEntity scope, @Nullable ServerPlayer feedback,
                                 BlockPos healed, @Nullable BlockPos stale) {
        scope.setWireHubLink(ScopeCannonLink.fromTarget(level, healed));
        if (feedback != null) {
            feedback.displayClientMessage(Component.literal("Controller link healed: re-linked to the controller hub."), true);
        }
        VSAnalogWarfare.LOGGER.info("[VSAW] Scope at {} healed its stale controller link (was {}) to the live hub at {}",
                scope.getBlockPos(), stale, healed);
        return healed;
    }

    private static boolean isHubAt(Level level, BlockPos pos) {
        return DbwWireCompat.isTweakedHub(level.getBlockState(pos).getBlock());
    }

    private static HubScan scanShipForHubs(Level level, ScopeBlockEntity scope) {
        try {
            Object ship = VehicleSetupReflection.findShip(level, scope.getBlockPos());
            if (ship == null) {
                return HubScan.EMPTY;
            }
            Object box = VehicleSetupReflection.invoke(ship, "getShipAABB");
            if (box == null) {
                return HubScan.EMPTY;
            }
            int minX = coordinate(box, "minX"), minY = coordinate(box, "minY"), minZ = coordinate(box, "minZ");
            int maxX = coordinate(box, "maxX"), maxY = coordinate(box, "maxY"), maxZ = coordinate(box, "maxZ");
            if ((long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) > MAX_SCAN_VOLUME) {
                return HubScan.EMPTY;
            }
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            BlockPos single = null;
            boolean multiple = false;
            for (int x = minX; x <= maxX && !multiple; x++) {
                for (int y = minY; y <= maxY && !multiple; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        pos.set(x, y, z);
                        if (!isHubAt(level, pos)) continue;
                        if (single == null) {
                            single = pos.immutable();
                        } else {
                            multiple = true;
                            break;
                        }
                    }
                }
            }
            return new HubScan(single, multiple);
        } catch (ReflectiveOperationException | LinkageError error) {
            return HubScan.EMPTY;
        }
    }

    private static HubScan scanNearbyForHubs(Level level, BlockPos center) {
        BlockPos single = null;
        boolean multiple = false;
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-GROUND_SCAN_RADIUS, -GROUND_SCAN_RADIUS, -GROUND_SCAN_RADIUS),
                center.offset(GROUND_SCAN_RADIUS, GROUND_SCAN_RADIUS, GROUND_SCAN_RADIUS))) {
            if (!isHubAt(level, pos)) continue;
            if (single == null) {
                single = pos.immutable();
            } else {
                multiple = true;
                break;
            }
        }
        return new HubScan(single, multiple);
    }

    private static int coordinate(Object box, String name) throws ReflectiveOperationException {
        Object value = box.getClass().getMethod(name).invoke(box);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private record HubScan(@Nullable BlockPos single, boolean multiple) {
        private static final HubScan EMPTY = new HubScan(null, false);
    }
}
