package com.erika.vsanalogwarfare.mixin.client;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.forsteri.createendertransmission.transmitUtil.TransmitterScreen", remap = false)
public class EnderTransmitterScreenMixin {
    @Shadow private EditBox areaTestInput;

    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private void vsaw$extendPasswordInput(CallbackInfo callback) {
        if (areaTestInput != null) areaTestInput.setMaxLength(32);
    }
}
