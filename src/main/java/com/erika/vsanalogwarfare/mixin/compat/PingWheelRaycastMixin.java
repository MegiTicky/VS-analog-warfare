package com.erika.vsanalogwarfare.mixin.compat;

import com.erika.vsanalogwarfare.client.ScopeLookCompat;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Ping Wheel compat: while scoped, {@code Raycast#traceDirectional} and the
 * Distant Horizons fallback raycast run from the scope view ray instead of
 * the real player eye. The entity-scan bounding box is re-anchored at the
 * scope origin so entity pings scan the corridor the scope looks down
 * (live-follow entity pings keep working). Only the caller-side box in
 * {@code traceDirectional} is redirected - the per-target boxes inside
 * {@code traceEntity} stay real.
 */
@Mixin(targets = "nx.pingwheel.common.math.Raycast", remap = false)
public abstract class PingWheelRaycastMixin {

    // Ray origin: the scope view origin instead of the real player eye.
    @ModifyExpressionValue(
            method = "traceDirectional(Lnet/minecraft/world/phys/Vec3;FDZ)Lnet/minecraft/world/phys/HitResult;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;m_20299_(F)Lnet/minecraft/world/phys/Vec3;",
                    remap = false))
    private static Vec3 vs_analog_warfare$scopeRayOrigin(Vec3 original) {
        var ray = ScopeLookCompat.scopeAimRayWhileScoped();
        return ray != null ? ray.origin() : original;
    }

    // Entity-scan box anchor: from the scope origin so the scan covers what
    // the scope sees, not the real player position.
    @ModifyExpressionValue(
            method = "traceDirectional(Lnet/minecraft/world/phys/Vec3;FDZ)Lnet/minecraft/world/phys/HitResult;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;m_20191_()Lnet/minecraft/world/phys/AABB;",
                    remap = false))
    private static AABB vs_analog_warfare$scopeScanBox(AABB original) {
        var ray = ScopeLookCompat.scopeAimRayWhileScoped();
        return ray != null ? new AABB(ray.origin(), ray.origin()) : original;
    }

    // Bounding-box expansion direction (getViewVector(1f) feeding expandTowards).
    @ModifyExpressionValue(
            method = "traceDirectional(Lnet/minecraft/world/phys/Vec3;FDZ)Lnet/minecraft/world/phys/HitResult;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;m_20252_(F)Lnet/minecraft/world/phys/Vec3;",
                    remap = false))
    private static Vec3 vs_analog_warfare$scopeBoxDirection(Vec3 original) {
        var ray = ScopeLookCompat.scopeAimRayWhileScoped();
        return ray != null ? ray.direction() : original;
    }

    // Distant Horizons fallback ray origin.
    @ModifyExpressionValue(
            method = "traceDistantAsync(Lnet/minecraft/world/phys/Vec3;FLjava/util/function/Consumer;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;m_20299_(F)Lnet/minecraft/world/phys/Vec3;",
                    remap = false))
    private static Vec3 vs_analog_warfare$scopeDhRayOrigin(Vec3 original) {
        var ray = ScopeLookCompat.scopeAimRayWhileScoped();
        return ray != null ? ray.origin() : original;
    }
}
