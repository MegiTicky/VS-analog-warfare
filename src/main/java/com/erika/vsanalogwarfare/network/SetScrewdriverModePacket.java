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
                AnalogScrewdriverItem.setRemovalMode(player.getMainHandItem(), packet.mode != 0);
                player.displayClientMessage(AnalogScrewdriverItem.removalMode(player.getMainHandItem())
                        ? net.minecraft.network.chat.Component.literal("Screwdriver mode: mark for removal")
                        : net.minecraft.network.chat.Component.literal("Screwdriver mode: regular"), true);
            }
        });
        context.setPacketHandled(true);
    }
}
