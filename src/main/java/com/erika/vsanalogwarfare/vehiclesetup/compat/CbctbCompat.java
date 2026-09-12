package com.erika.vsanalogwarfare.vehiclesetup.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
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
 */
public final class CbctbCompat {
    private static final String GOGGLES_ITEM_CLASS =
            "com.cbc_terminal_ballistics.goggles.BallisticGogglesItem";
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
     * Applies the goggle link for the acting player through CBCTB
     * ({@code GoggleLinkManager.setupRelink(ServerPlayer, List<BlockPos>)}),
     * resolving the cannon mount's current world position. Null = success (or
     * a silent no-op when the player carries no goggles); a string is an error
     * for the setup run report.
     */
    @Nullable
    public static String linkGoggles(Level level, BlockPos mount, @Nullable ServerPlayer player) {
        if (player == null) return "Goggle links require a player to run the setup";
        try {
            Class<?> manager = Class.forName(LINK_MANAGER_CLASS);
            Method relink = manager.getMethod("setupRelink", ServerPlayer.class, List.class);
            relink.invoke(null, player, List.of(mount));
            return null;
        } catch (ClassNotFoundException ignored) {
            return "CBC Terminal Ballistics is not installed";
        } catch (ReflectiveOperationException t) {
            return "Goggle link failed: " + t.getMessage();
        }
    }
}
