package com.erika.vsanalogwarfare.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record ScrewdriverHudPacket(boolean active, int recordingMode, String setupName, List<String> entries,
                                   BlockPos anchor, List<HighlightRecord> highlights) {
    public static final int MAX_HIGHLIGHTS = 128;

    /**
     * One block position to outline client-side. {@code shipOffset} is ship-local
     * (relative to the ship's AABB min, 0 when the action has none);
     * {@code targetOffset} is relative to the setup anchor (0 when absent).
     */
    public record HighlightRecord(long shipId, long shipOffset, long targetOffset, boolean removal) {
    }

    public static void encode(ScrewdriverHudPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.active);
        buffer.writeVarInt(packet.recordingMode);
        buffer.writeUtf(packet.setupName, 256);
        buffer.writeVarInt(packet.entries.size());
        for (String entry : packet.entries) buffer.writeUtf(entry, 256);
        buffer.writeLong(packet.anchor == null ? 0L : packet.anchor.asLong());
        int highlightCount = Math.min(packet.highlights.size(), MAX_HIGHLIGHTS);
        buffer.writeVarInt(highlightCount);
        for (int index = 0; index < highlightCount; index++) {
            HighlightRecord record = packet.highlights.get(index);
            buffer.writeLong(record.shipId());
            buffer.writeLong(record.shipOffset());
            buffer.writeLong(record.targetOffset());
            buffer.writeBoolean(record.removal());
        }
    }

    public static ScrewdriverHudPacket decode(FriendlyByteBuf buffer) {
        boolean active = buffer.readBoolean();
        int recordingMode = buffer.readVarInt();
        String setupName = buffer.readUtf(256);
        int count = Math.min(buffer.readVarInt(), 256);
        List<String> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) entries.add(buffer.readUtf(256));
        long anchorRaw = buffer.readLong();
        BlockPos anchor = anchorRaw == 0L ? null : BlockPos.of(anchorRaw);
        int highlightCount = Math.min(buffer.readVarInt(), MAX_HIGHLIGHTS);
        List<HighlightRecord> highlights = new ArrayList<>(highlightCount);
        for (int index = 0; index < highlightCount; index++) {
            highlights.add(new HighlightRecord(buffer.readLong(), buffer.readLong(),
                    buffer.readLong(), buffer.readBoolean()));
        }
        return new ScrewdriverHudPacket(active, recordingMode, setupName, entries, anchor, highlights);
    }

    public static void handle(ScrewdriverHudPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.erika.vsanalogwarfare.client.AnalogScrewdriverOverlay.setHudState(packet)));
        context.setPacketHandled(true);
    }
}
