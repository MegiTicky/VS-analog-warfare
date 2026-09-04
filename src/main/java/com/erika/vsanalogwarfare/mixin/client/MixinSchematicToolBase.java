package com.erika.vsanalogwarfare.mixin.client;

import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toJOML;
import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toMinecraft;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.schematics.client.SchematicTransformation;
import com.simibubi.create.content.schematics.client.tools.SchematicToolBase;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * SchematicToolBase is responsible for the placement position of the schematic.
 *
 * Create uses world-space hit coordinates, which do not respect VS ship space.
 */
@Mixin(value = SchematicToolBase.class)
public abstract class MixinSchematicToolBase {
    /**
     * Create uses HitResult::getLocation to get the schematic placement position, which doesn't
     * respect ship-space. Redirect it to BlockHitResult::getBlockPos when the target is on a ship.
     */
    @Redirect(
        method = "updateTargetPos()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/BlockHitResult;m_82450_()Lnet/minecraft/world/phys/Vec3;",
            ordinal = 0
        ),
        remap = false
    )
    public Vec3 redirectGetLocation(BlockHitResult instance) {
        BlockPos b = instance.getBlockPos();
        Ship ship = VSGameUtilsKt.getShipObjectManagingPos(Minecraft.getInstance().level, b);
        if (ship != null) {
            // The return value is used to form a BlockPos,
            // so the vec position within a block should not make a difference
            return Vec3.atLowerCornerOf(b);
        } else {
            return instance.getLocation();
        }
    }

    /**
     * Create evaluates the player look in the local space of the transform, so the player
     * position must first be converted into the ship-space the schematic is anchored on.
     */
    @WrapOperation(
        method = "updateTargetPos",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/schematics/client/SchematicTransformation;toLocalSpace(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
        ),
        remap = false
    )
    public Vec3 wrapLocalSpaceToShip(SchematicTransformation transformation, Vec3 vec, Operation<Vec3> original) {
        Ship ship = VSGameUtilsKt.getShipObjectManagingPos(Minecraft.getInstance().level, transformation.getAnchor());
        if (ship != null) {
            return original.call(transformation, toMinecraft(ship.getWorldToShip().transformPosition(toJOML(vec))));
        }

        return original.call(transformation, vec);
    }
}
