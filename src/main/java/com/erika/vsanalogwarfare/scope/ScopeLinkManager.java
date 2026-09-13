package com.erika.vsanalogwarfare.scope;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.network.ModNetwork;
import com.erika.vsanalogwarfare.network.ScopeLinkPacket;
import com.erika.vsanalogwarfare.scope.compat.CbcCompat;
import com.erika.vsanalogwarfare.vehiclesetup.AnalogScrewdriverItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/** Server-side selection and editing flow for scope cannon links. */
@Mod.EventBusSubscriber(modid = VSAnalogWarfare.MOD_ID)
public final class ScopeLinkManager {
    public static final String SELECTED_SCOPE = "VSAWScopeLinkScope";
    public static final String LINK_MODE = "VSAWScopeLinkMode";
    public static final int PRIMARY_MODE = 0;
    public static final int SECONDARY_MODE = 1;
    public static final int WIRE_HUB_MODE = 2;

    private ScopeLinkManager() { }

    public static void open(ServerPlayer player, ScopeBlockEntity scope) {
        ModNetwork.sendToPlayer(player, ScopeLinkPacket.Open.fromScope(scope));
    }

    public static boolean arm(ServerPlayer player, BlockPos scopePos, int mode) {
        if (!(player.getMainHandItem().getItem() instanceof AnalogScrewdriverItem)
                || (mode != PRIMARY_MODE && mode != SECONDARY_MODE && mode != WIRE_HUB_MODE)) return false;
        if (!(resolveScopeEntity(player.level(), scopePos) instanceof ScopeBlockEntity)) return false;
        if (mode == WIRE_HUB_MODE && !com.erika.vsanalogwarfare.scope.compat.DbwWireCompat.isAvailable()) return false;
        ItemStack screwdriver = player.getMainHandItem();
        screwdriver.getOrCreateTag().putLong(SELECTED_SCOPE, scopePos.asLong());
        screwdriver.getOrCreateTag().putInt(LINK_MODE, mode);
        player.displayClientMessage(Component.literal(mode == PRIMARY_MODE
                ? "Primary link armed. Right-click a cannon mount."
                : mode == SECONDARY_MODE
                ? "Secondary link armed. Right-click a cannon mount."
                : "Controller link armed. Right-click a Tweaked Controller Hub."), true);
        return true;
    }

    /**
     * Resolves the block entity at {@code pos}, following the position onto its
     * owning VS ship when the block lives on a ship (world {@code getBlockEntity}
     * does not see ship-local blocks).
     */
    @Nullable
    private static BlockEntity resolveScopeEntity(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) return be;
        Object ship = com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupReflection.findShip(level, pos);
        if (ship == null) return null;
        BlockPos onShip = com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupReflection.positionOnShip(ship, pos);
        return onShip == null ? null : level.getBlockEntity(onShip);
    }

    public static void clearSelection(ItemStack screwdriver) {
        if (!screwdriver.hasTag()) return;
        screwdriver.getTag().remove(SELECTED_SCOPE);
        screwdriver.getTag().remove(LINK_MODE);
    }

    public static void delete(ServerPlayer player, BlockPos scopePos, int index, int revision) {
        BlockEntity blockEntity = player.level().getBlockEntity(scopePos);
        if (!(blockEntity instanceof ScopeBlockEntity scope)) {
            player.displayClientMessage(Component.literal("That scope is unavailable."), true);
            return;
        }
        if (revision != scope.getLinkRevision()) {
            player.displayClientMessage(Component.literal("The scope links changed. Open the scope again."), true);
            return;
        }
        boolean removed;
        if (index == -2) {
            removed = scope.getWireHubLink() != null;
            scope.setWireHubLink(null);
        } else {
            removed = index < 0 ? scope.clearPrimaryLinkAndReturn() : scope.removeSecondary(index);
        }
        if (!removed) {
            player.displayClientMessage(Component.literal("That scope link is no longer available."), true);
            return;
        }
        player.displayClientMessage(Component.literal(index == -2
                ? "Controller link removed."
                : index < 0 ? "Primary cannon link deleted." : "Secondary cannon link deleted."), true);
        open(player, scope);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onCannonLink(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getItemStack().getItem() instanceof AnalogScrewdriverItem)) return;
        ItemStack screwdriver = event.getItemStack();
        if (!screwdriver.hasTag() || !screwdriver.getTag().contains(SELECTED_SCOPE)) return;
        int mode = screwdriver.getTag().getInt(LINK_MODE);
        if (mode == WIRE_HUB_MODE) {
            BlockPos target = event.getPos();
            if (!com.erika.vsanalogwarfare.scope.compat.DbwWireCompat.isTweakedHub(
                    player.level().getBlockState(target).getBlock())) {
                // Not a tweaked hub: leave the arm in place and let normal
                // block interaction proceed (e.g. right-clicking the scope
                // reopens its link UI). On a genuine hub the hardened
                // isTweakedHub check consumes the click before drivebywire's
                // own handler can show "Item not compatible!".
                return;
            }
            BlockPos scopePos = BlockPos.of(screwdriver.getTag().getLong(SELECTED_SCOPE));
            if (!(resolveScopeEntity(player.level(), scopePos) instanceof ScopeBlockEntity scope)) {
                clearSelection(screwdriver);
                player.displayClientMessage(Component.literal("The selected scope is unavailable."), true);
                return;
            }
            scope.setWireHubLink(ScopeCannonLink.fromTarget(player.level(), target));
            clearSelection(screwdriver);
            player.displayClientMessage(Component.literal(
                    "Scope linked to Tweaked Controller Hub. Scope keys 1-8 send wire signals."), true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
            return;
        }
        BlockPos target = event.getPos();
        if (!CbcCompat.isCannonMount(player.level().getBlockEntity(target))) return;

        BlockPos scopePos = BlockPos.of(screwdriver.getTag().getLong(SELECTED_SCOPE));
        if (!(player.level().getBlockEntity(scopePos) instanceof ScopeBlockEntity scope)) {
            clearSelection(screwdriver);
            player.displayClientMessage(Component.literal("The selected scope is unavailable."), true);
            return;
        }
        if (mode == PRIMARY_MODE) {
            scope.linkPrimary(target);
            player.displayClientMessage(Component.literal("Primary cannon linked."), true);
        } else if (mode == SECONDARY_MODE) {
            if (!scope.addSecondary(target)) {
                player.displayClientMessage(Component.literal("That cannon is already linked or unavailable."), true);
                return;
            }
            player.displayClientMessage(Component.literal("Secondary cannon linked."), true);
        } else {
            clearSelection(screwdriver);
            return;
        }
        clearSelection(screwdriver);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }
}
