package com.erika.vsanalogwarfare.network;

import com.erika.vsanalogwarfare.mouseaim.MouseAimController;
import com.erika.vsanalogwarfare.scope.ScopeSession;
import com.erika.vsanalogwarfare.scope.ScopeSessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

public record MouseAimTargetPacket(BlockPos scopePos, BlockPos mountPos, double dirX, double dirY, double dirZ) {
    public static void encode(MouseAimTargetPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.scopePos);
        buf.writeBlockPos(packet.mountPos);
        buf.writeDouble(packet.dirX);
        buf.writeDouble(packet.dirY);
        buf.writeDouble(packet.dirZ);
    }

    public static MouseAimTargetPacket decode(FriendlyByteBuf buf) {
        return new MouseAimTargetPacket(buf.readBlockPos(), buf.readBlockPos(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(MouseAimTargetPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || !(sender.level() instanceof net.minecraft.server.level.ServerLevel level)) {
                return;
            }
            Optional<ScopeSession> active = ScopeSessionManager.activeSession(sender);
            if (active.isEmpty()) {
                return;
            }
            ScopeSession session = active.get();
            if (!session.scopePos().equals(packet.scopePos) || !session.mountPos().equals(packet.mountPos)) {
                return;
            }
            Vec3 direction = new Vec3(packet.dirX, packet.dirY, packet.dirZ);
            if (direction.lengthSqr() < 1.0e-8) {
                return;
            }
            MouseAimController.findControllerForMount(level, packet.mountPos)
                    .ifPresent(controller -> controller.setTarget(sender.getUUID(), packet.mountPos, direction.normalize()));
        });
        context.setPacketHandled(true);
    }
}
