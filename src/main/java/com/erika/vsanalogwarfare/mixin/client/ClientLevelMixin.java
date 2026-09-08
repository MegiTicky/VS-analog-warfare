package com.erika.vsanalogwarfare.mixin.client;

import com.erika.vsanalogwarfare.seat.InvisibleSeatBlock;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets the invisible seat participate in vanilla's held-marker-particle
 * mechanic (the one that reveals barrier blocks while held): vanilla checks
 * the held main-hand item against a fixed set of marker items before scanning
 * random positions for the matching block and drawing its particle sprite.
 * Target method is ClientLevel.getMarkerParticleTarget; SRG literal + remap
 * false per the mod's no-refmap mixin convention.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @ModifyExpressionValue(method = "m_194187_",
            at = @At(value = "INVOKE", target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z"),
            remap = false)
    private boolean vs_analog_warfare$allowInvisibleSeatMarker(boolean original, @Local Item item) {
        if (original) {
            return true;
        }
        return item instanceof BlockItem blockItem && blockItem.getBlock() instanceof InvisibleSeatBlock;
    }
}
