package com.erika.vsanalogwarfare.mixin.compat;

import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupRecordingManager;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket", remap = false)
public class EnderTransmitterPacketMixin {
    @Inject(method = "applySettings(Lnet/minecraft/server/level/ServerPlayer;Lcom/simibubi/create/foundation/blockEntity/SyncedBlockEntity;)V",
            at = @At("RETURN"), remap = false)
    private void vsaw$recordConfiguration(ServerPlayer player, SyncedBlockEntity blockEntity,
                                          CallbackInfo callback) {
        if (!(blockEntity instanceof KineticBlockEntity transmitter)
                || !getClass().getName().equals(
                "com.forsteri.createendertransmission.transmitUtil.ConfigureTransmitterPacket")) return;
        VehicleSetupRecordingManager.recordEnderTransmitterConfiguration(player, transmitter);
    }
}
