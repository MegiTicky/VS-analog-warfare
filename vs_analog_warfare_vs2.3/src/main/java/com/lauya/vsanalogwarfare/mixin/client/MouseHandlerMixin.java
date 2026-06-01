package com.lauya.vsanalogwarfare.mixin.client;

import com.lauya.vsanalogwarfare.client.ClientScopeState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow(remap = false) private double f_91516_;
    @Shadow(remap = false) private double f_91517_;
    @Shadow(remap = false) @Final private Minecraft f_91503_;

    @Inject(method = "m_91523_", at = @At("HEAD"), cancellable = true, remap = false)
    private void vs_analog_warfare$turnFreeLook(CallbackInfo ci) {
        if (!ClientScopeState.freeLookEnabled()) {
            return;
        }
        double sensitivity = this.f_91503_.options.sensitivity().get() * 0.6000000238418579D + 0.20000000298023224D;
        double scaled = sensitivity * sensitivity * sensitivity * 8.0D;
        double scopedScale = ClientScopeState.mouseSensitivityScale();
        double dx = this.f_91516_ * scaled * scopedScale;
        double dy = this.f_91517_ * scaled * scopedScale;
        this.f_91516_ = 0.0D;
        this.f_91517_ = 0.0D;
        int invert = this.f_91503_.options.invertYMouse().get() ? -1 : 1;
        ClientScopeState.addFreeLookInput(dx, dy * invert);
        ci.cancel();
    }
}
