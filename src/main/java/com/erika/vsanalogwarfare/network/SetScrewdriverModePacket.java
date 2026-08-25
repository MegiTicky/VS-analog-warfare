package com.erika.vsanalogwarfare.network;

import com.erika.vsanalogwarfare.vehiclesetup.AnalogScrewdriverItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SetScrewdriverModePacket(int mode) {
    public static void encode(SetScrewdriverModePacket packet, FriendlyByteBuf buffer) { buffer.writeVarInt(packet.mode); }
    public static SetScrewdriverModePacket decode(FriendlyByteBuf buffer) { return new SetScrewdriverModePacket(buffer.readVarInt()); }

    public static void handle(SetScrewdriverModePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.isShiftKeyDown()
                    && player.getMainHandItem().getItem() instanceof AnalogScrewdriverItem) {
                int mode = Math.max(AnalogScrewdriverItem.REGULAR_MODE,
                        Math.min(AnalogScrewdriverItem.TRANSMITTER_MODE, packet.mode));
                AnalogScrewdriverItem.setMode(player.getMainHandItem(), mode);
                if (mode != AnalogScrewdriverItem.TRANSMITTER_MODE) {
                    com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupRecordingManager.stopTransmitterRecording(player);
                }
                String message = switch (mode) {
                    case AnalogScrewdriverItem.REMOVAL_MODE_VALUE -> "Screwdriver mode: mark for removal";
                    case AnalogScrewdriverItem.TRANSMITTER_MODE -> "Screwdriver mode: energy transmitter scan";
                    default -> "Screwdriver mode: regular";
                };
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(message), true);
            }
        });
        context.setPacketHandled(true);
    }
}
