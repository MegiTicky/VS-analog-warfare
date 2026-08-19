package com.erika.vsanalogwarfare.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "com.forsteri.createendertransmission.transmitUtil.TransmitterScreen", remap = false)
public class EnderTransmitterScreenMixin {
    @ModifyConstant(method = "m_7856_()V", constant = @Constant(intValue = 16), remap = false)
    private int vsaw$extendPasswordInput(int ignored) {
        return 32;
    }
}
