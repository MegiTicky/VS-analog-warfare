package com.erika.vsanalogwarfare.mixin.client;

import com.erika.vsanalogwarfare.client.ScopeLookCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a scope session is active, the local player's eye position and look
 * vector report the scope view's world-frame ray (see ScopeLookCompat). Mods
 * that aim from the player entity - ping wheels, spotters, vanilla crosshair
 * picking - then target what the reticle points at instead of the frozen
 * first-person head. Client-side mixin: on a dedicated server these getters
 * must stay untouched.
 *
 * Priority above other Entity mixins (e.g. VS2's ship-mounted look
 * corrections) so while scoped the scope ray wins. Composes idempotently with
 * the per-mod ping compat mixins, which substitute the same ray.
 */
@Mixin(value = Entity.class, priority = 1500)
public abstract class EntityViewCompatMixin {

    @Inject(method = "m_20252_(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true, remap = false)
    private void vs_analog_warfare$scopeViewVector(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        Vec3 direction = ScopeLookCompat.virtualLookVector((Entity) (Object) this);
        if (direction != null) {
            cir.setReturnValue(direction);
        }
    }

    @Inject(method = "m_20299_(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true, remap = false)
    private void vs_analog_warfare$scopeEyePosition(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        Vec3 origin = ScopeLookCompat.virtualEyePosition((Entity) (Object) this);
        if (origin != null) {
            cir.setReturnValue(origin);
        }
    }
}
