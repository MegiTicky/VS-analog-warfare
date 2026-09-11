package com.erika.vsanalogwarfare.network;

import com.erika.vsanalogwarfare.mouseaim.MouseAimBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servo-tuning commands for a Mouse Aim Controller, sent from the config
 * screen: start a step-test calibration (result reported on the action bar)
 * or drop the stored per-block gains back to the global template.
 */
public record MouseAimTuningPacket(BlockPos pos, Action action) {
    public enum Action { CALIBRATE, RESET }

    public static void encode(MouseAimTuningPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeEnum(packet.action);
    }

    public static MouseAimTuningPacket decode(FriendlyByteBuf buf) {
        return new MouseAimTuningPacket(buf.readBlockPos(), buf.readEnum(Action.class));
    }

    public static void handle(MouseAimTuningPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || player.distanceToSqr(packet.pos.getX() + .5, packet.pos.getY() + .5, packet.pos.getZ() + .5) > 64) {
                return;
            }
            BlockEntity blockEntity = player.level().getBlockEntity(packet.pos);
            if (!(blockEntity instanceof MouseAimBlockEntity aim)) {
                return;
            }
            switch (packet.action) {
                case CALIBRATE -> aim.startCalibration(player);
                case RESET -> aim.resetTuning(player);
            }
        });
        context.setPacketHandled(true);
    }
}
