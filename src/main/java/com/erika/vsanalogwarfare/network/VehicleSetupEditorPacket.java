package com.erika.vsanalogwarfare.network;

import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupBlockEntity;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupRecordingManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record VehicleSetupEditorPacket(BlockPos pos, int revision, Operation operation, int first, int second) {
    public enum Operation { OPEN, MOVE, DELETE, SET_DELAY, STANDARD_TIME, APPEND_RECORDING, SCAN_TRANSMITTERS }

    public static void encode(VehicleSetupEditorPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeInt(packet.revision);
        buf.writeEnum(packet.operation);
        buf.writeInt(packet.first);
        buf.writeInt(packet.second);
    }

    public static VehicleSetupEditorPacket decode(FriendlyByteBuf buf) {
        return new VehicleSetupEditorPacket(buf.readBlockPos(), buf.readInt(), buf.readEnum(Operation.class),
                buf.readInt(), buf.readInt());
    }

    public static void handle(VehicleSetupEditorPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || player.distanceToSqr(packet.pos.getX() + .5, packet.pos.getY() + .5, packet.pos.getZ() + .5) > 64) return;
            BlockEntity blockEntity = player.level().getBlockEntity(packet.pos);
            if (!(blockEntity instanceof VehicleSetupBlockEntity setup)) return;
            if (packet.operation == Operation.OPEN) {
                sendSnapshot(player, setup);
                return;
            }
            if (packet.revision != setup.revision()) {
                sendSnapshot(player, setup);
                return;
            }
            switch (packet.operation) {
                case MOVE -> setup.moveAction(packet.first, packet.second);
                case DELETE -> setup.deleteAction(packet.first);
                case SET_DELAY -> setup.setActionDelay(packet.first, packet.second);
                case STANDARD_TIME -> setup.useStandardTiming();
                case APPEND_RECORDING -> VehicleSetupRecordingManager.toggle(player, setup, true);
                case SCAN_TRANSMITTERS -> VehicleSetupRecordingManager.scanEnderTransmitters(player, setup);
                case OPEN -> { }
            }
            if (packet.operation != Operation.APPEND_RECORDING) sendSnapshot(player, setup);
        });
        context.setPacketHandled(true);
    }

    private static void sendSnapshot(ServerPlayer player, VehicleSetupBlockEntity setup) {
        List<CompoundTag> actions = new ArrayList<>();
        setup.actions().forEach(action -> actions.add(action.save()));
        ModNetwork.sendToPlayer(player, new VehicleSetupEditorSnapshotPacket(setup.getBlockPos(), setup.revision(), actions));
    }

    public record VehicleSetupEditorSnapshotPacket(BlockPos pos, int revision, List<CompoundTag> actions) {
        public static void encode(VehicleSetupEditorSnapshotPacket packet, FriendlyByteBuf buf) {
            buf.writeBlockPos(packet.pos);
            buf.writeInt(packet.revision);
            buf.writeVarInt(packet.actions.size());
            for (CompoundTag action : packet.actions) buf.writeNbt(action);
        }

        public static VehicleSetupEditorSnapshotPacket decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            int revision = buf.readInt();
            int size = buf.readVarInt();
            List<CompoundTag> actions = new ArrayList<>(size);
            for (int index = 0; index < size; index++) actions.add(buf.readNbt());
            return new VehicleSetupEditorSnapshotPacket(pos, revision, actions);
        }

        public static void handle(VehicleSetupEditorSnapshotPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            contextSupplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientNetworkHandlers.openVehicleSetupEditor(packet)));
            contextSupplier.get().setPacketHandled(true);
        }
    }
}
