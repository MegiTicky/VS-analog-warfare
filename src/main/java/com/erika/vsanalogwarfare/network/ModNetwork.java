package com.erika.vsanalogwarfare.network;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.client.ClientScopeState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.lang.reflect.Method;
import java.util.function.Supplier;

public final class ModNetwork {
    private static final String PROTOCOL = "5";
    public static SimpleChannel CHANNEL;

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(VSAnalogWarfare.MOD_ID, "main"))
                .networkProtocolVersion(() -> PROTOCOL)
                .clientAcceptedVersions(PROTOCOL::equals)
                .serverAcceptedVersions(PROTOCOL::equals)
                .simpleChannel();

        int id = 0;
        CHANNEL.messageBuilder(ScopeStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScopeStatePacket::encode)
                .decoder(ScopeStatePacket::decode)
                .consumerMainThread(ScopeStatePacket::handle)
                .add();
        CHANNEL.messageBuilder(StopScopePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StopScopePacket::encode)
                .decoder(StopScopePacket::decode)
                .consumerMainThread(StopScopePacket::handle)
                .add();
        CHANNEL.messageBuilder(ToggleScopeZoomPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleScopeZoomPacket::encode)
                .decoder(ToggleScopeZoomPacket::decode)
                .consumerMainThread(ToggleScopeZoomPacket::handle)
                .add();
        CHANNEL.messageBuilder(MouseAimTargetPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MouseAimTargetPacket::encode)
                .decoder(MouseAimTargetPacket::decode)
                .consumerMainThread(MouseAimTargetPacket::handle)
                .add();

        CHANNEL.messageBuilder(AdjustMountPitchPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AdjustMountPitchPacket::encode)
                .decoder(AdjustMountPitchPacket::decode)
                .consumerMainThread(AdjustMountPitchPacket::handle)
                .add();

        // Register Rangefinder Request (Client -> Server)
        CHANNEL.messageBuilder(RangefinderRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RangefinderRequestPacket::encode)
                .decoder(RangefinderRequestPacket::decode)
                .consumerMainThread(RangefinderRequestPacket::handle)
                .add();

        // Register Rangefinder Result (Server -> Client)
        CHANNEL.messageBuilder(RangefinderResultPacket.class, id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RangefinderResultPacket::encode)
                .decoder(RangefinderResultPacket::decode)
                .consumerMainThread(RangefinderResultPacket::handle)
                .add();
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public record RangefinderRequestPacket(Vec3 start, Vec3 direction) {
        public static void encode(RangefinderRequestPacket packet, FriendlyByteBuf buf) {
            buf.writeDouble(packet.start.x);
            buf.writeDouble(packet.start.y);
            buf.writeDouble(packet.start.z);
            buf.writeDouble(packet.direction.x);
            buf.writeDouble(packet.direction.y);
            buf.writeDouble(packet.direction.z);
        }

        public static RangefinderRequestPacket decode(FriendlyByteBuf buf) {
            return new RangefinderRequestPacket(
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
            );
        }

        public static void handle(RangefinderRequestPacket packet, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;

                double closestDistance = com.erika.vsanalogwarfare.config.CommonConfig.maxRangefinderDistance(); // Max Range
                boolean hitShip = false;

                // Safely iterate ships via reflection
                Iterable<?> ships = getAllShips(player.level());
                if (ships != null) {
                    for (Object ship : ships) {
                        Object aabb = getShipAABB(ship);
                        if (aabb == null) continue;

                        double minX = getDouble(aabb, "minX");
                        double minY = getDouble(aabb, "minY");
                        double minZ = getDouble(aabb, "minZ");
                        double maxX = getDouble(aabb, "maxX");
                        double maxY = getDouble(aabb, "maxY");
                        double maxZ = getDouble(aabb, "maxZ");

                        double dist = intersectRayAABB(packet.start, packet.direction,
                                minX, minY, minZ,
                                maxX, maxY, maxZ);

                        if (dist > 0 && dist < closestDistance) {
                            closestDistance = dist;
                            hitShip = true;
                        }
                    }
                }

                ModNetwork.sendToPlayer(player, new RangefinderResultPacket(hitShip ? closestDistance : -1.0));
            });
            context.setPacketHandled(true);
        }

        // --- Reflection Helpers for VS2 Compatibility ---
        private static Iterable<?> getAllShips(Level level) {
            try {
                Class<?> vsGameUtilsClass = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
                Method getAllShips = vsGameUtilsClass.getMethod("getAllShips", Level.class);
                return (Iterable<?>) getAllShips.invoke(null, level);
            } catch (Exception e) {
                return null;
            }
        }

        private static Object getShipAABB(Object ship) {
            try {
                Method getShipAABB = ship.getClass().getMethod("getShipAABB");
                return getShipAABB.invoke(ship);
            } catch (Exception e) {
                return null;
            }
        }

        private static double getDouble(Object aabb, String method) {
            try {
                Method m = aabb.getClass().getMethod(method);
                return ((Number) m.invoke(aabb)).doubleValue();
            } catch (Exception e) {
                return 0.0;
            }
        }
    }

    public record RangefinderResultPacket(double shipDistance) {
        public static void encode(RangefinderResultPacket packet, FriendlyByteBuf buf) {
            buf.writeDouble(packet.shipDistance);
        }

        public static RangefinderResultPacket decode(FriendlyByteBuf buf) {
            return new RangefinderResultPacket(buf.readDouble());
        }

        public static void handle(RangefinderResultPacket packet, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                double currentDist = ClientScopeState.rangefinderDistance();

                if (packet.shipDistance > 0) {
                    if (currentDist < 0 || packet.shipDistance < currentDist) {
                        ClientScopeState.setRangefinderDistance(packet.shipDistance);
                    }
                }

                ClientScopeState.decrementRangefinderTasks();
            });
            context.setPacketHandled(true);
        }
    }

    private static double intersectRayAABB(Vec3 start, Vec3 dir, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double tmin = (minX - start.x) / dir.x;
        double tmax = (maxX - start.x) / dir.x;
        if (tmin > tmax) { double temp = tmin; tmin = tmax; tmax = temp; }

        double tymin = (minY - start.y) / dir.y;
        double tymax = (maxY - start.y) / dir.y;
        if (tymin > tymax) { double temp = tymin; tymin = tymax; tymax = temp; }

        if ((tmin > tymax) || (tymin > tmax)) return -1.0;

        if (tymin > tmin) tmin = tymin;
        if (tymax < tmax) tmax = tymax;

        double tzmin = (minZ - start.z) / dir.z;
        double tzmax = (maxZ - start.z) / dir.z;
        if (tzmin > tzmax) { double temp = tzmin; tzmin = tzmax; tzmax = temp; }

        if ((tmin > tzmax) || (tzmin > tmax)) return -1.0;

        if (tzmin > tmin) tmin = tzmin;
        if (tzmax < tmax) tmax = tzmax;

        if (tmax < 0) return -1.0;

        // CHANGED HERE: If tmin < 0, the camera is currently inside this ship's invisible bounding box.
        // We ignore it and return -1.0 so we don't rangefind the invisible exit wall!
        if (tmin < 0) return -1.0;

        return tmin;
    }
}