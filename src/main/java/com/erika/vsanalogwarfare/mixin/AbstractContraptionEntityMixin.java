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
    // Vanilla method: SRG name for the production runtime, Mojang name for the
    // dev runtime. remap=false keeps the reobf step from rewriting either
    // string; require=1 so matching exactly one in either environment is valid.
    @Inject(method = {"canCollideWith", "m_7337_"}, at = @At("HEAD"), require = 1, cancellable = true, remap = false)
    private void vsaw$disableEntityCollision(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (CommonConfig.disableContraptionEntityCollision()) {
            cir.setReturnValue(false);
        }
    }
}
