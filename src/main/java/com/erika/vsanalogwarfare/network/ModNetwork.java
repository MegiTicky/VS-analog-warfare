package com.erika.vsanalogwarfare.network;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "5";
    public static SimpleChannel CHANNEL;

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(VSAnalogWarfare.MOD_ID, "main"))
                .networkProtocolVersion(() -> PROTOCOL)
                .clientAcceptedVersions(PROTOCOL::equals)
                .serverAcceptedVersions(PROTOCOL::equals)
                .simpleChannel();

        int id = 0;
        CHANNEL.messageBuilder(ScopeStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScopeStatePacket::encode)
                .decoder(ScopeStatePacket::decode)
                .consumerMainThread(ScopeStatePacket::handle)
                .add();
        CHANNEL.messageBuilder(StopScopePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StopScopePacket::encode)
                .decoder(StopScopePacket::decode)
                .consumerMainThread(StopScopePacket::handle)
                .add();
        CHANNEL.messageBuilder(ToggleScopeZoomPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleScopeZoomPacket::encode)
                .decoder(ToggleScopeZoomPacket::decode)
                .consumerMainThread(ToggleScopeZoomPacket::handle)
                .add();
        CHANNEL.messageBuilder(MouseAimTargetPacket.class, id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MouseAimTargetPacket::encode)
                .decoder(MouseAimTargetPacket::decode)
                .consumerMainThread(MouseAimTargetPacket::handle)
                .add();
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
