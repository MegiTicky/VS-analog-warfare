package com.lauya.vsanalogwarfare.scope;

import com.lauya.vsanalogwarfare.network.ModNetwork;
import com.lauya.vsanalogwarfare.network.ScopeStatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.lauya.vsanalogwarfare.VSAnalogWarfare;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = VSAnalogWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ScopeSessionManager {
    private static final Map<UUID, ScopeSession> SESSIONS = new HashMap<>();

    private ScopeSessionManager() {
    }

    public static void start(ServerPlayer player, ScopeBlockEntity scope) {
        stop(player);
        scope.captureVsAnchor();
        var mountPos = scope.resolveMountPos();
        ScopeSession session = new ScopeSession(player, scope, mountPos);
        if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            session.update(level);
        }
        SESSIONS.put(player.getUUID(), session);
        ModNetwork.sendToPlayer(player, ScopeStatePacket.active(session.fov(), session.zoomMagnification(), session.scopePos(), session.mountPos(), session.currentPose(), session.displayProfile()));
    }

    public static void stop(ServerPlayer player) {
        ScopeSession session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return;
        }
        ModNetwork.sendToPlayer(player, ScopeStatePacket.inactive());
    }

    public static void toggleZoom(ServerPlayer player) {
        ScopeSession session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        session.toggleZoom();
        if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            session.update(level);
        }
        ModNetwork.sendToPlayer(player, ScopeStatePacket.active(session.fov(), session.zoomMagnification(), session.scopePos(), session.mountPos(), session.currentPose(), session.displayProfile()));
    }

    public static Optional<ScopeSession> activeSession(ServerPlayer player) {
        if (player == null) {
            return Optional.empty();
        }
        ScopeSession session = SESSIONS.get(player.getUUID());
        return session != null && session.isValid(player) ? Optional.of(session) : Optional.empty();
    }

    public static Optional<ScopeSession> nearestSession(Level level, Vec3 position, double radius) {
        if (level == null || position == null || radius <= 0.0) {
            return Optional.empty();
        }
        double radiusSq = radius * radius;
        ScopeSession best = null;
        double bestDistSq = radiusSq;
        for (ScopeSession session : SESSIONS.values()) {
            if (session.dimension() != level.dimension()) {
                continue;
            }
            double distSq = Vec3.atCenterOf(session.mountPos()).distanceToSqr(position);
            if (distSq > bestDistSq) {
                distSq = Vec3.atCenterOf(session.scopePos()).distanceToSqr(position);
            }
            if (distSq <= bestDistSq) {
                bestDistSq = distSq;
                best = session;
            }
        }
        return Optional.ofNullable(best);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, ScopeSession>> iter = SESSIONS.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<UUID, ScopeSession> entry = iter.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            ScopeSession session = entry.getValue();
            if (player == null || !session.isValid(player)) {
                iter.remove();
                if (player != null) {
                    ModNetwork.sendToPlayer(player, ScopeStatePacket.inactive());
                }
                continue;
            }
            if (player.level() instanceof net.minecraft.server.level.ServerLevel level && level.getGameTime() % 5L == 0L) {
                session.update(level);
                ModNetwork.sendToPlayer(player, ScopeStatePacket.active(session.fov(), session.zoomMagnification(), session.scopePos(), session.mountPos(), session.currentPose(), session.displayProfile()));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player);
        }
    }
}
