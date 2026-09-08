package com.erika.vsanalogwarfare.network;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.lang.reflect.Method;
import java.util.function.Supplier;

public final class ModNetwork {
    private static final String PROTOCOL = "9";
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

        CHANNEL.messageBuilder(SetZeroDistancePacket.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetZeroDistancePacket::encode)
                .decoder(SetZeroDistancePacket::decode)
                .consumerMainThread(SetZeroDistancePacket::handle)
                .add();
        CHANNEL.messageBuilder(SetScrewdriverModePacket.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetScrewdriverModePacket::encode)
                .decoder(SetScrewdriverModePacket::decode)
                .consumerMainThread(SetScrewdriverModePacket::handle)
                .add();
        CHANNEL.messageBuilder(ScrewdriverHudPacket.class, ++id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScrewdriverHudPacket::encode)
                .decoder(ScrewdriverHudPacket::decode)
                .consumerMainThread(ScrewdriverHudPacket::handle)
                .add();
        CHANNEL.messageBuilder(VehicleSetupEditorPacket.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(VehicleSetupEditorPacket::encode)
                .decoder(VehicleSetupEditorPacket::decode)
                .consumerMainThread(VehicleSetupEditorPacket::handle)
                .add();
        CHANNEL.messageBuilder(VehicleSetupEditorPacket.VehicleSetupEditorSnapshotPacket.class, ++id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(VehicleSetupEditorPacket.VehicleSetupEditorSnapshotPacket::encode)
                .decoder(VehicleSetupEditorPacket.VehicleSetupEditorSnapshotPacket::decode)
                .consumerMainThread(VehicleSetupEditorPacket.VehicleSetupEditorSnapshotPacket::handle)
                .add();
        CHANNEL.messageBuilder(VehicleMountPacket.Request.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(VehicleMountPacket.Request::encode).decoder(VehicleMountPacket.Request::decode)
                .consumerMainThread(VehicleMountPacket.Request::handle).add();
        CHANNEL.messageBuilder(VehicleMountPacket.Push.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(VehicleMountPacket.Push::encode).decoder(VehicleMountPacket.Push::decode)
                .consumerMainThread(VehicleMountPacket.Push::handle).add();
        CHANNEL.messageBuilder(VehicleMountPacket.Link.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(VehicleMountPacket.Link::encode).decoder(VehicleMountPacket.Link::decode)
                .consumerMainThread(VehicleMountPacket.Link::handle).add();
        CHANNEL.messageBuilder(VehicleMountPacket.OpenRoleName.class, ++id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(VehicleMountPacket.OpenRoleName::encode).decoder(VehicleMountPacket.OpenRoleName::decode)
                .consumerMainThread(VehicleMountPacket.OpenRoleName::handle).add();
        CHANNEL.messageBuilder(VehicleMountPacket.Dismount.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(VehicleMountPacket.Dismount::encode).decoder(VehicleMountPacket.Dismount::decode)
                .consumerMainThread(VehicleMountPacket.Dismount::handle).add();
        CHANNEL.messageBuilder(VehicleMountPacket.OpenSelection.class, ++id, NetworkDirection.PLAY_TO_CLIENT)
                 .encoder(VehicleMountPacket.OpenSelection::encode).decoder(VehicleMountPacket.OpenSelection::decode)
                 .consumerMainThread(VehicleMountPacket.OpenSelection::handle).add();
        CHANNEL.messageBuilder(ScopeLinkPacket.Open.class, ++id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScopeLinkPacket.Open::encode).decoder(ScopeLinkPacket.Open::decode)
                .consumerMainThread(ScopeLinkPacket.Open::handle).add();
        CHANNEL.messageBuilder(ScopeLinkPacket.Arm.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ScopeLinkPacket.Arm::encode).decoder(ScopeLinkPacket.Arm::decode)
                .consumerMainThread(ScopeLinkPacket.Arm::handle).add();
        CHANNEL.messageBuilder(ScopeLinkPacket.Delete.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ScopeLinkPacket.Delete::encode).decoder(ScopeLinkPacket.Delete::decode)
                .consumerMainThread(ScopeLinkPacket.Delete::handle).add();
        CHANNEL.messageBuilder(StabilizerStatePacket.class, ++id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StabilizerStatePacket::encode).decoder(StabilizerStatePacket::decode)
                .consumerMainThread(StabilizerStatePacket::handle).add();
        CHANNEL.messageBuilder(MouseAimConfigPacket.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MouseAimConfigPacket::encode).decoder(MouseAimConfigPacket::decode)
                .consumerMainThread(MouseAimConfigPacket::handle).add();
        CHANNEL.messageBuilder(MouseAimConfigPacket.Snapshot.class, ++id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MouseAimConfigPacket.Snapshot::encode).decoder(MouseAimConfigPacket.Snapshot::decode)
                .consumerMainThread(MouseAimConfigPacket.Snapshot::handle).add();
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
            return com.erika.vsanalogwarfare.vehiclesetup.compat.VsGameUtilsBridge.allShips(level);
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
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientNetworkHandlers.handleRangefinderResult(packet)));
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
