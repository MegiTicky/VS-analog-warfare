package com.erika.vsanalogwarfare.network;

import com.erika.vsanalogwarfare.mouseaim.MouseAimController;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record AdjustMountPitchPacket(BlockPos mountPos, float deltaPitch) {

    public static void encode(AdjustMountPitchPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.mountPos);
        buf.writeFloat(packet.deltaPitch);
    }

    public static AdjustMountPitchPacket decode(FriendlyByteBuf buf) {
        return new AdjustMountPitchPacket(buf.readBlockPos(), buf.readFloat());
    }

    public static void handle(AdjustMountPitchPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || !(sender.level() instanceof net.minecraft.server.level.ServerLevel level)) {
                return;
            }

            // Pass the adjustment to the controller logic
            MouseAimController.adjustPitch(level, packet.mountPos, packet.deltaPitch);
        });
        context.setPacketHandled(true);
    }
}