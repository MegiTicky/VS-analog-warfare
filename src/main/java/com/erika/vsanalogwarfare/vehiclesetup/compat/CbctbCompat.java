package com.erika.vsanalogwarfare.vehiclesetup.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.List;

/**
 * CBC Terminal Ballistics integration: ballistic goggle links. Everything is
 * reflective so this mod never needs CBCTB on the compile classpath; when
 * CBCTB is absent the setup action degrades to a no-op with the usual
 * integration warning.
 *
 * <p>Links live purely as a {@code GoggleLinks} NBT list on the item stack
 * (no capability, no per-player store), and CBCTB's
 * {@code GoggleLinkManager.addLink(stack, level, pos)} links a stack directly,
 * so the setup mints a fresh goggles item, links it to the recorded mount and
 * hands it to the placer — no pre-owned goggles required.
 */
public final class CbctbCompat {
    private static final String GOGGLES_ITEM_CLASS =
            "com.cbc_terminal_ballistics.goggles.BallisticGogglesItem";
    private static final String GOGGLES_ITEM_ID = "cbc_terminal_ballistics:ballistic_goggles";
    private static final String LINK_MANAGER_CLASS =
            "com.cbc_terminal_ballistics.goggles.GoggleLinkManager";

    private CbctbCompat() { }

    /** True when the stack is CBCTB's ballistic goggles item. */
    public static boolean isGogglesItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem().getClass().getName().equals(GOGGLES_ITEM_CLASS);
    }

    /**
     * True when the block is a CBC cannon mount (regular or fixed). Mirrors
     * CBCTB's own class-name check so recording works without CBCTB classes.
     */
    public static boolean isCannonMount(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.hasBlockEntity()
                && state.getBlock().getClass().getName().contains("CannonMountBlock");
    }

    /**
     * Mints a fresh ballistic-goggles stack, links it to the cannon mount's
     * current world position through CBCTB
     * ({@code GoggleLinkManager.addLink(ItemStack, ServerLevel, BlockPos)})
     * and gives it to the acting player (dropped if their inventory is full).
     * Null = success; a string is an error for the setup run report.
     */
    @Nullable
    public static String linkGoggles(Level level, BlockPos mount, @Nullable ServerPlayer player) {
        if (player == null) return "Goggle links require a player to run the setup";
        if (!(level instanceof ServerLevel serverLevel)) return "Goggle links require a server world";
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(GOGGLES_ITEM_ID));
        if (item == net.minecraft.world.item.Items.AIR) {
            return "CBC Terminal Ballistics is not installed";
        }
        ItemStack goggles = new ItemStack(item);
        try {
            Class<?> manager = Class.forName(LINK_MANAGER_CLASS);
            Method addLink = manager.getMethod("addLink", ItemStack.class, ServerLevel.class, BlockPos.class);
            String result = (String) addLink.invoke(null, goggles, serverLevel, mount);
            if (!"linked".equals(result)) {
                return "Goggle link failed: " + result;
            }
        } catch (ClassNotFoundException ignored) {
            return "CBC Terminal Ballistics is not installed";
        } catch (ReflectiveOperationException t) {
            return "Goggle link failed: " + t.getMessage();
        }
        if (!player.getInventory().add(goggles)) {
            player.drop(goggles, false);
        }
        return null;
    }
}
