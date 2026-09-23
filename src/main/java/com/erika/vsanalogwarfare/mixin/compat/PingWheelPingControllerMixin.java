package com.erika.vsanalogwarfare.mixin.compat;

import com.erika.vsanalogwarfare.client.ScopeLookCompat;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Ping Wheel compat: while scoped, the ping direction comes from the scope
 * view ray instead of the real player look. The scope virtualizes the render
 * camera while the player's own rotation stays frozen, so without this a ping
 * lands wherever the head pointed when scoping began. Second layer on top of
 * EntityViewCompatMixin so Ping Wheel keeps working even when the general
 * virtualization is disabled in config.
 */
@Mixin(targets = "nx.pingwheel.common.core.PingController", remap = false)
public abstract class PingWheelPingControllerMixin {

    @ModifyExpressionValue(
            method = "performPingAction(F)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;m_20252_(F)Lnet/minecraft/world/phys/Vec3;",
                    remap = false))
    private static Vec3 vs_analog_warfare$scopePingDirection(Vec3 original) {
        Vec3 direction = ScopeLookCompat.virtualLookVector(Minecraft.getInstance().player);
        return direction != null ? direction : original;
    }
}
