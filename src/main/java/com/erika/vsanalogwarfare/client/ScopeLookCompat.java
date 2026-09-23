package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Scope-look virtualization for other mods: while a scope session is active,
 * the local player's eye position and look vector report the scope view's
 * world-frame ray ({@link ClientScopeState#scopeAimRay} - sight ray in scope
 * view, rendered orbit-camera ray in third-person view).
 * {@code EntityViewCompatMixin} feeds {@code Entity#getViewVector(F)} and
 * {@code #getEyePosition(F)} through this, so mods that aim from the player
 * entity (Ping Wheel, Steve's Army pings, vanilla crosshair targeting) aim at
 * what the reticle points at instead of where the frozen first-person head
 * looks. The per-mod mixins in {@code mixin.compat} use this as their fallback
 * layer and keep working when the general virtualization is disabled.
 */
public final class ScopeLookCompat {
    private ScopeLookCompat() {
    }

    /** True when {@code entity}'s eye/look getters should report the scope view ray. */
    public static boolean isVirtualized(@Nullable Entity entity) {
        return entity != null
                && entity == Minecraft.getInstance().player
                && ClientScopeState.active()
                && ClientConfig.virtualizePlayerLookWhileScoped();
    }

    /**
     * The scope view look vector for {@code entity}, or null when the real
     * (player) vector should be used.
     */
    @Nullable
    public static Vec3 virtualLookVector(@Nullable Entity entity) {
        ClientScopeState.ScopeRay ray = scopeAimRayOrNull(entity);
        return ray != null ? ray.direction() : null;
    }

    /**
     * The scope view eye position for {@code entity}, or null when the real
     * (player) position should be used.
     */
    @Nullable
    public static Vec3 virtualEyePosition(@Nullable Entity entity) {
        ClientScopeState.ScopeRay ray = scopeAimRayOrNull(entity);
        return ray != null ? ray.origin() : null;
    }

    /**
     * Current scope aim ray while a scope session is active, else null.
     * Entry point for the per-mod ping compat mixins (which act for the local
     * player and have no entity to test). Deliberately ignores
     * {@code virtualizePlayerLookWhileScoped}: that switch only turns the
     * general Entity-level virtualization off, and the per-mod compat layers
     * must keep working when it is.
     */
    @Nullable
    public static ClientScopeState.ScopeRay scopeAimRayWhileScoped() {
        if (!ClientScopeState.active()) {
            return null;
        }
        return ClientScopeState.scopeAimRay(1.0f);
    }

    /**
     * Current scope aim ray when scope-look virtualization applies to
     * {@code entity}, else null.
     */
    @Nullable
    public static ClientScopeState.ScopeRay scopeAimRayOrNull(@Nullable Entity entity) {
        if (!isVirtualized(entity)) {
            return null;
        }
        return ClientScopeState.scopeAimRay(1.0f);
    }

    /**
     * Clip along the scope view ray the way ping mods do, skipping glass and
     * non-colliding vegetation (mirrors Steve's Army's ping clip; Ping Wheel's
     * own entity scan runs through its redirected raycast instead). Returns
     * the crosshair world position, or null while the scope is inactive.
     */
    @Nullable
    public static Vec3 scopeCrosshairClip(@Nullable Minecraft mc) {
        if (mc == null || mc.level == null || mc.player == null || !ClientScopeState.active()) {
            return null;
        }
        ClientScopeState.ScopeRay ray = ClientScopeState.scopeAimRay(1.0f);
        if (ray == null) {
            return null;
        }
        double maxDistance = mc.options.renderDistance().get() * 16.0;
        // The 1.5-block head start keeps the ray out of the scope's own block
        // (same convention as the rangefinder).
        Vec3 origin = ray.origin().add(ray.direction().scale(1.5));
        Vec3 end = ray.origin().add(ray.direction().scale(maxDistance));
        Vec3 cursor = origin;
        for (int i = 0; i < 64; i++) {
            BlockHitResult hit = mc.level.clip(new ClipContext(
                    cursor, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS) {
                break;
            }
            if (!isPingTransparent(mc.level, hit)) {
                return hit.getLocation();
            }
            // Move beyond the hit point so the next clip cannot hit this block again.
            cursor = hit.getLocation().add(ray.direction().scale(0.001));
        }
        return end;
    }

    private static boolean isPingTransparent(Level level, BlockHitResult hit) {
        BlockState state = level.getBlockState(hit.getBlockPos());
        return state.getBlock() instanceof AbstractGlassBlock
                || state.getBlock() instanceof StainedGlassPaneBlock
                || state.getCollisionShape(level, hit.getBlockPos()).isEmpty();
    }
}
