package com.erika.vsanalogwarfare.network;

import com.erika.vsanalogwarfare.mouseaim.MouseAimBlockEntity;
import com.erika.vsanalogwarfare.mouseaim.MouseAimMode;
import com.erika.vsanalogwarfare.mouseaim.TurretStrength;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Config exchange for the Mouse Aim Controller: the client sends the screen's
 * selection ({@link MouseAimConfigPacket}), the server answers with the
 * authoritative state ({@link Snapshot}) which also opens the screen on
 * right-click, mirroring the vehicle setup editor flow.
 */
public record MouseAimConfigPacket(BlockPos pos, MouseAimMode mode, TurretStrength strength) {
    public static void encode(MouseAimConfigPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeEnum(packet.mode);
        buf.writeEnum(packet.strength);
    }

    public static MouseAimConfigPacket decode(FriendlyByteBuf buf) {
        return new MouseAimConfigPacket(buf.readBlockPos(), buf.readEnum(MouseAimMode.class),
                buf.readEnum(TurretStrength.class));
    }

    public static void handle(MouseAimConfigPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
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
            aim.setMode(packet.mode);
            aim.setStrength(packet.strength);
            ModNetwork.sendToPlayer(player, new Snapshot(packet.pos, aim.getMode(), aim.getTurretStrength()));
        });
        context.setPacketHandled(true);
    }

    public record Snapshot(BlockPos pos, MouseAimMode mode, TurretStrength strength) {
        public static void encode(Snapshot packet, FriendlyByteBuf buf) {
            buf.writeBlockPos(packet.pos);
            buf.writeEnum(packet.mode);
            buf.writeEnum(packet.strength);
        }

        public static Snapshot decode(FriendlyByteBuf buf) {
            return new Snapshot(buf.readBlockPos(), buf.readEnum(MouseAimMode.class),
                    buf.readEnum(TurretStrength.class));
        }

        public static void handle(Snapshot packet, Supplier<NetworkEvent.Context> contextSupplier) {
            contextSupplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientNetworkHandlers.openMouseAimConfig(packet)));
            contextSupplier.get().setPacketHandled(true);
        }
    }
}
