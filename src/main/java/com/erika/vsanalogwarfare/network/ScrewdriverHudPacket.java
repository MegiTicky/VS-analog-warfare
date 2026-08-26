package com.erika.vsanalogwarfare.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record ScrewdriverHudPacket(boolean active, int recordingMode, String setupName, List<String> entries) {
    public static void encode(ScrewdriverHudPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.active);
        buffer.writeVarInt(packet.recordingMode);
        buffer.writeUtf(packet.setupName, 256);
        buffer.writeVarInt(packet.entries.size());
        for (String entry : packet.entries) buffer.writeUtf(entry, 256);
    }

    public static ScrewdriverHudPacket decode(FriendlyByteBuf buffer) {
        boolean active = buffer.readBoolean();
        int recordingMode = buffer.readVarInt();
        String setupName = buffer.readUtf(256);
        int count = Math.min(buffer.readVarInt(), 256);
        List<String> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) entries.add(buffer.readUtf(256));
        return new ScrewdriverHudPacket(active, recordingMode, setupName, entries);
    }

    public static void handle(ScrewdriverHudPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.erika.vsanalogwarfare.client.AnalogScrewdriverOverlay.setHudState(packet)));
        context.setPacketHandled(true);
    }
}
