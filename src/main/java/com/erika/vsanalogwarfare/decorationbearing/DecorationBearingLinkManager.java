package com.erika.vsanalogwarfare.decorationbearing;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.scope.compat.CbcCompat;
import com.erika.vsanalogwarfare.vehiclesetup.AnalogScrewdriverItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VSAnalogWarfare.MOD_ID)
public final class DecorationBearingLinkManager {
    private static final String SELECTED_BEARING = "VSAWDecorationBearingLink";

    private DecorationBearingLinkManager() { }

    public static void select(net.minecraft.world.entity.player.Player player, BlockPos pos) {
        ItemStack screwdriver = player.getMainHandItem();
        screwdriver.getOrCreateTag().putLong(SELECTED_BEARING, pos.asLong());
        player.displayClientMessage(Component.literal("Decoration bearing selected. Right-click a cannon mount."), true);
    }

    private static void clear(ItemStack screwdriver) {
        if (screwdriver.hasTag()) screwdriver.getTag().remove(SELECTED_BEARING);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getItemStack().getItem() instanceof AnalogScrewdriverItem)) return;
        ItemStack screwdriver = event.getItemStack();
        if (!screwdriver.hasTag() || !screwdriver.getTag().contains(SELECTED_BEARING)) return;

        BlockPos target = event.getPos();
        if (!CbcCompat.isCannonMount(player.level().getBlockEntity(target))) return;
        BlockPos bearingPos = BlockPos.of(screwdriver.getTag().getLong(SELECTED_BEARING));
        if (!(player.level().getBlockEntity(bearingPos) instanceof DecorationBearingBlockEntity bearing)) {
            clear(screwdriver);
            player.displayClientMessage(Component.literal("The selected decoration bearing is unavailable."), true);
            return;
        }
        if (!bearing.link(target)) {
            player.displayClientMessage(Component.literal("That cannon mount cannot be linked."), true);
            return;
        }
        clear(screwdriver);
        player.displayClientMessage(Component.literal("Decoration bearing linked."), true);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }
}
