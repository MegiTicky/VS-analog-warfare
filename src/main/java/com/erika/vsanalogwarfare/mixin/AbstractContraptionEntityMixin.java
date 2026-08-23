package com.erika.vsanalogwarfare.mixin;

import com.erika.vsanalogwarfare.config.CommonConfig;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContraptionEntity.class)
public abstract class AbstractContraptionEntityMixin {
    @Inject(method = "m_7337_", at = @At("HEAD"), cancellable = true, remap = false)
    private void vsaw$disableEntityCollision(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (CommonConfig.disableContraptionEntityCollision()) {
            cir.setReturnValue(false);
        }
    }
}
