package com.erika.vsanalogwarfare.network;

import com.erika.vsanalogwarfare.scope.ScopeSessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ToggleScopeZoomPacket() {
    public static void encode(ToggleScopeZoomPacket packet, FriendlyByteBuf buf) {
    }

    public static ToggleScopeZoomPacket decode(FriendlyByteBuf buf) {
        return new ToggleScopeZoomPacket();
    }

    public static void handle(ToggleScopeZoomPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                ScopeSessionManager.toggleZoom(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
