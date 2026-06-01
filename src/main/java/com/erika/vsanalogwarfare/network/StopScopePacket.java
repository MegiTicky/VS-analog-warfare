package com.erika.vsanalogwarfare.network;

import com.erika.vsanalogwarfare.scope.ScopeSessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record StopScopePacket() {
    public static void encode(StopScopePacket packet, FriendlyByteBuf buf) {
    }

    public static StopScopePacket decode(FriendlyByteBuf buf) {
        return new StopScopePacket();
    }

    public static void handle(StopScopePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                ScopeSessionManager.stop(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
