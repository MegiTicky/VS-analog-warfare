package com.erika.vsanalogwarfare.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Server -> client sync of one stabilizer's hold-on-release state so the
 * client-side control loop (mixin) mirrors the server. A null mount pos means
 * "this stabilizer is inactive/unlinked".
 */
public record StabilizerStatePacket(@Nullable BlockPos mountPos, BlockPos stabilizerPos,
                                    boolean active, double targetElevDeg) {

    public static void encode(StabilizerStatePacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.mountPos != null);
        if (packet.mountPos != null) {
            buf.writeBlockPos(packet.mountPos);
        }
        buf.writeBlockPos(packet.stabilizerPos);
        buf.writeBoolean(packet.active);
        buf.writeDouble(packet.targetElevDeg);
    }

    public static StabilizerStatePacket decode(FriendlyByteBuf buf) {
        BlockPos mountPos = buf.readBoolean() ? buf.readBlockPos() : null;
        BlockPos stabilizerPos = buf.readBlockPos();
        boolean active = buf.readBoolean();
        double targetElev = buf.readDouble();
        return new StabilizerStatePacket(mountPos, stabilizerPos, active, targetElev);
    }

    public static void handle(StabilizerStatePacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientNetworkHandlers.handleStabilizerState(packet)));
        context.setPacketHandled(true);
    }
}
