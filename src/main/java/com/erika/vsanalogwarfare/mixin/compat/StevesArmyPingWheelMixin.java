package com.erika.vsanalogwarfare.mixin.compat;

import com.erika.vsanalogwarfare.client.ScopeLookCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Steve's Army compat: the ping wheel (and the vehicle mount/dismount orders
 * fed by the same crosshair position) compute their target from the real
 * player look; while scoped that must be the scope view ray instead. The
 * replacement mirrors the original's glass-skipping clip and max-distance
 * fallback. The wheel UI itself already works while scoped - only the
 * position math was wrong.
 */
@Mixin(targets = "com.stevesarmy.client.PingWheelHandler", remap = false)
public abstract class StevesArmyPingWheelMixin {

    @Inject(method = "findCrosshairPosition", at = @At("HEAD"), cancellable = true)
    private static void vs_analog_warfare$scopeCrosshair(Minecraft mc, CallbackInfoReturnable<Vec3> cir) {
        Vec3 hit = ScopeLookCompat.scopeCrosshairClip(mc);
        if (hit != null) {
            cir.setReturnValue(hit);
        }
    }
}
