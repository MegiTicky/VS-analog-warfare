package com.erika.vsanalogwarfare.mixin.compat;

import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupRecordingManager;
import net.minecraft.core.BlockPos;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "edn.stratodonut.drivebywire.network.WireLinkNetworksPacket", remap = false)
public class DbwWireLinkNetworksMixin {
    @Shadow private long start;
    @Shadow private long end;

    @Inject(method = "lambda$handle$0", at = @At(value = "INVOKE", target =
            "Ledn/stratodonut/drivebywire/wire/ShipWireNetworkManager;linkNetwork(Ledn/stratodonut/drivebywire/wire/ShipWireNetworkManager;JLnet/minecraft/nbt/CompoundTag;)V",
            shift = At.Shift.AFTER), remap = false)
    private void vsaw$recordSuccessfulLink(NetworkEvent.Context context, CallbackInfo ci) {
        if (context.getSender() != null) {
            VehicleSetupRecordingManager.recordDbwLink(context.getSender(), BlockPos.of(start), BlockPos.of(end));
        }
    }
}
