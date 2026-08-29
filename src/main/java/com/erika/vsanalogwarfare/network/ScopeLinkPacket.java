package com.erika.vsanalogwarfare.network;

import com.erika.vsanalogwarfare.scope.ScopeBlockEntity;
import com.erika.vsanalogwarfare.scope.ScopeCannonLink;
import com.erika.vsanalogwarfare.scope.ScopeLinkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ScopeLinkPacket {
    private ScopeLinkPacket() { }

    public record Open(BlockPos scope, int revision, @Nullable LinkView primary, List<LinkView> secondary) {
        public static Open fromScope(ScopeBlockEntity scope) {
            return new Open(scope.getBlockPos(), scope.getLinkRevision(), view(scope.getPrimaryLink()),
                    scope.getSecondaryLinks().stream().map(Open::view).toList());
        }

        private static LinkView view(@Nullable ScopeCannonLink link) {
            return link == null ? null : new LinkView(link.shipId(), link.shipOffset(), link.fallbackPos());
        }

        public static void encode(Open packet, FriendlyByteBuf buf) {
            buf.writeBlockPos(packet.scope);
            buf.writeVarInt(packet.revision);
            writeNullable(packet.primary, buf);
            buf.writeVarInt(packet.secondary.size());
            for (LinkView link : packet.secondary) writeNullable(link, buf);
        }

        public static Open decode(FriendlyByteBuf buf) {
            BlockPos scope = buf.readBlockPos();
            int revision = buf.readVarInt();
            LinkView primary = readNullable(buf);
            int size = Math.min(256, buf.readVarInt());
            List<LinkView> secondary = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                LinkView link = readNullable(buf);
                if (link != null) secondary.add(link);
            }
            return new Open(scope, revision, primary, secondary);
        }

        public static void handle(Open packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientNetworkHandlers.openScopeLinks(packet)));
            context.setPacketHandled(true);
        }
    }

    public record Arm(BlockPos scope, int mode) {
        public static void encode(Arm packet, FriendlyByteBuf buf) {
            buf.writeBlockPos(packet.scope);
            buf.writeVarInt(packet.mode);
        }

        public static Arm decode(FriendlyByteBuf buf) {
            return new Arm(buf.readBlockPos(), buf.readVarInt());
        }

        public static void handle(Arm packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || ScopeLinkManager.arm(player, packet.scope, packet.mode)) return;
                if (player != null) player.displayClientMessage(Component.literal("Could not arm scope linking."), true);
            });
            context.setPacketHandled(true);
        }
    }

    public record Delete(BlockPos scope, int revision, int index) {
        public static void encode(Delete packet, FriendlyByteBuf buf) {
            buf.writeBlockPos(packet.scope);
            buf.writeVarInt(packet.revision);
            buf.writeVarInt(packet.index);
        }

        public static Delete decode(FriendlyByteBuf buf) {
            return new Delete(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(Delete packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) ScopeLinkManager.delete(player, packet.scope, packet.index, packet.revision);
            });
            context.setPacketHandled(true);
        }
    }

    public record LinkView(long shipId, @Nullable BlockPos shipOffset, BlockPos fallbackPos) { }

    private static void writeNullable(@Nullable LinkView link, FriendlyByteBuf buf) {
        buf.writeBoolean(link != null);
        if (link == null) return;
        buf.writeLong(link.shipId());
        buf.writeBoolean(link.shipOffset() != null);
        if (link.shipOffset() != null) buf.writeBlockPos(link.shipOffset());
        buf.writeBlockPos(link.fallbackPos());
    }

    @Nullable
    private static LinkView readNullable(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        long shipId = buf.readLong();
        BlockPos offset = buf.readBoolean() ? buf.readBlockPos() : null;
        return new LinkView(shipId, offset, buf.readBlockPos());
    }
}
