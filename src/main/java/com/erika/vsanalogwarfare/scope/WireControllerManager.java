package com.erika.vsanalogwarfare.scope;

import com.erika.vsanalogwarfare.scope.compat.DbwWireCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import javax.annotation.Nullable;

/**
 * Server side of the scope-as-controller feature: while a scope session with a
 * linked Tweaked Controller Hub is open, the player's main hand holds a real
 * create_tweaked_controllers controller linked to the hub (drivebywire's
 * {@code Hub} NBT tag), so the game's own tweaked-controller pipeline — buttons,
 * axes, custom binds, mouse focus — runs exactly as if the player had activated
 * one manually. The displaced item is restored when the session ends.
 */
public final class WireControllerManager {
    public static final ResourceLocation CONTROLLER_ID =
            new ResourceLocation("create_tweaked_controllers", "tweaked_linked_controller");

    private WireControllerManager() {
    }

    public static boolean isController(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && CONTROLLER_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /**
     * Puts a hub-linked controller into the player's main hand for this session,
     * stashing the displaced stack. No-op (without stashing) when there is no
     * link, drivebywire is absent, the hub cannot be resolved, or the player
     * already holds a tweaked controller (their own item is then used as-is).
     */
    public static void equip(ServerPlayer player, ScopeSession session, ScopeBlockEntity scope) {
        if (session.wireControllerOriginal() != null) return;
        if (!DbwWireCompat.isAvailable()) return;
        if (scope.getWireHubLink() == null) return;
        BlockPos hubPos = scope.getWireHubLink().resolve(player.level(), null);
        if (hubPos == null) return;
        ItemStack current = player.getMainHandItem();
        if (isController(current)) return; // player already holds their own controller
        ItemStack controller = createController(hubPos);
        if (controller == null) return;
        session.setWireControllerOriginal(current.copy());
        player.setItemInHand(InteractionHand.MAIN_HAND, controller);
    }

    /** Restores the stashed main-hand stack when the session ends. */
    public static void unequip(ServerPlayer player, ScopeSession session) {
        ItemStack original = session.wireControllerOriginal();
        if (original == null) return;
        session.setWireControllerOriginal(null);
        if (player.hasDisconnected()) {
            // Inventory writes are lost on a disconnecting player; drop instead.
            player.drop(original, false, false);
        } else if (isController(player.getMainHandItem())) {
            player.setItemInHand(InteractionHand.MAIN_HAND, original);
        } else {
            // The fake controller left the hand somehow; give the original back
            // instead of overwriting whatever the player holds now.
            player.getInventory().placeItemBackInInventory(original);
        }
    }

    @Nullable
    private static ItemStack createController(BlockPos hubPos) {
        // get() returns the air placeholder when the id is absent.
        Item item = BuiltInRegistries.ITEM.get(CONTROLLER_ID);
        if (item.getDefaultInstance().isEmpty()) return null;
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = stack.getOrCreateTag();
        // A real controller that has been opened carries an Items compound; a
        // fresh stack without it NPEs in toFrequency inside the button packet's
        // handleItem, which aborts before drivebywire's RETURN mixin can push
        // the wire signal. An empty compound keeps the 50-slot handler empty.
        tag.put("Items", new CompoundTag());
        tag.putLong("Hub", hubPos.asLong());
        stack.setTag(tag);
        return stack;
    }
}
