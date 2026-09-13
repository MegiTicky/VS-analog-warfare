package com.erika.vsanalogwarfare.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.schematics.client.tools.DeployTool;
import com.simibubi.create.content.schematics.client.tools.SchematicToolBase;
import com.simibubi.create.foundation.render.SuperRenderTypeBuffer;
import com.simibubi.create.foundation.utility.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * DeployTool is responsible for the render transform of the placement bounding box (not the preview).
 *
 * Create applies both the camera and bounding-box position in one PoseStack operation, the latter
 * of which does not respect VS ship space.
 */
@Mixin(value = DeployTool.class)
public abstract class MixinDeployTool extends SchematicToolBase {
    /**
     * WrapOperation instead of @Redirect so a competing redirect from another mod degrades to a
     * skipped injection rather than a hard failure; require = 0 covers the same case when the
     * call site is missing or already consumed.
     */
    @WrapOperation(
        method = "renderTool(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/simibubi/create/foundation/render/SuperRenderTypeBuffer;Lnet/minecraft/world/phys/Vec3;)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;m_85837_(DDD)V",
            ordinal = 0
        ),
        remap = false,
        require = 0
    )
    private void vsaw$skipWorldTranslate(PoseStack ms, double _x, double _y, double _z, Operation<Void> original) {
        // The original world-space translate is deliberately not invoked;
        // vsaw$mixinRenderTool applies the ship-aware transform in its place.
    }

    @Inject(
        method = "renderTool(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/simibubi/create/foundation/render/SuperRenderTypeBuffer;Lnet/minecraft/world/phys/Vec3;)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;m_85837_(DDD)V",
            ordinal = 0
        ),
        remap = false,
        require = 0
    )
    private void vsaw$mixinRenderTool(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, CallbackInfo ci) {
        float pt = AnimationTickHolder.getPartialTicks();
        double x = Mth.lerp(pt, lastChasingSelectedPos.x, chasingSelectedPos.x);
        double y = Mth.lerp(pt, lastChasingSelectedPos.y, chasingSelectedPos.y);
        double z = Mth.lerp(pt, lastChasingSelectedPos.z, chasingSelectedPos.z);
        Ship ship = VSGameUtilsKt.getShipObjectManagingPos(Minecraft.getInstance().level, x, y, z);

        AABB bounds = schematicHandler.getBounds();
        Vec3 center = bounds.getCenter();
        int centerX = (int) center.x;
        int centerZ = (int) center.z;

        if (ship != null) {
            VSClientGameUtils.transformRenderWithShip(ship.getTransform(), ms, x - centerX, y, z - centerZ,
                    camera.x, camera.y, camera.z);
        } else {
            ms.translate(x - centerX - camera.x, y - camera.y, z - centerZ - camera.z);
        }
    }
}
