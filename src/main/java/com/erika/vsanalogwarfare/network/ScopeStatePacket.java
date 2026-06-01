package com.erika.vsanalogwarfare.network;

import com.erika.vsanalogwarfare.client.ClientScopeState;
import com.erika.vsanalogwarfare.scope.ballistics.BallisticProfile;
import com.erika.vsanalogwarfare.scope.rig.CameraPose;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ScopeStatePacket(boolean active, float fov, int zoomMagnification, BlockPos scopePos, BlockPos mountPos,
                               double x, double y, double z, float yaw, float pitch,
                               float qx, float qy, float qz, float qw,
                               BallisticProfile ballisticProfile) {
    public static ScopeStatePacket active(float fov, int zoomMagnification, BlockPos scopePos, BlockPos mountPos, CameraPose pose, BallisticProfile profile) {
        Vec3 pos = pose.position();
        return new ScopeStatePacket(true, fov, zoomMagnification, scopePos, mountPos, pos.x, pos.y, pos.z, pose.yaw(), pose.pitch(),
                pose.qx(), pose.qy(), pose.qz(), pose.qw(), profile == null ? BallisticProfile.EMPTY : profile);
    }

    public static ScopeStatePacket inactive() {
        return new ScopeStatePacket(false, 70.0f, 3, BlockPos.ZERO, BlockPos.ZERO,
                0.0, 0.0, 0.0, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f, BallisticProfile.EMPTY);
    }

    public static void encode(ScopeStatePacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.active);
        buf.writeFloat(packet.fov);
        buf.writeInt(packet.zoomMagnification);
        buf.writeBlockPos(packet.scopePos);
        buf.writeBlockPos(packet.mountPos);
        buf.writeDouble(packet.x);
        buf.writeDouble(packet.y);
        buf.writeDouble(packet.z);
        buf.writeFloat(packet.yaw);
        buf.writeFloat(packet.pitch);
        buf.writeFloat(packet.qx);
        buf.writeFloat(packet.qy);
        buf.writeFloat(packet.qz);
        buf.writeFloat(packet.qw);
        (packet.ballisticProfile == null ? BallisticProfile.EMPTY : packet.ballisticProfile).encode(buf);
    }

    public static ScopeStatePacket decode(FriendlyByteBuf buf) {
        return new ScopeStatePacket(buf.readBoolean(), buf.readFloat(), buf.readInt(), buf.readBlockPos(), buf.readBlockPos(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                BallisticProfile.decode(buf));
    }

    public static void handle(ScopeStatePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        contextSupplier.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientScopeState.set(
                        packet.active, packet.fov, packet.zoomMagnification, packet.scopePos, packet.mountPos,
                        packet.x, packet.y, packet.z, packet.yaw, packet.pitch,
                        packet.qx, packet.qy, packet.qz, packet.qw, packet.ballisticProfile)));
        contextSupplier.get().setPacketHandled(true);
    }
}
