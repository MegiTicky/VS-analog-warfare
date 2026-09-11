package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Client bridge to create_tweaked_controllers: while a scope session is open,
 * the server has put a hub-linked tweaked controller into the player's main
 * hand; this class activates the game's own
 * {@code TweakedLinkedControllerClientHandler} (toggle, exactly like
 * right-clicking the item) so the REAL controller pipeline — buttons, axes,
 * custom binds, mouse focus — runs during the scope view, and reports its live
 * button state for the HUD.
 */
public final class TweakedControllerInputCompat {
    public static final ResourceLocation CONTROLLER_ID =
            new ResourceLocation("create_tweaked_controllers", "tweaked_linked_controller");

    private static final String HANDLER_CLASS =
            "com.getitemfromblock.create_tweaked_controllers.controller.TweakedLinkedControllerClientHandler";

    /** 15 button labels in tweaked-controller (GLFW gamepad) order. */
    public static final String[] BUTTON_LABELS = {
            "A", "B", "X", "Y", "LB", "RB", "BK", "ST", "GD", "L3", "R3", "\u25B2", "\u25B6", "\u25BC", "\u25C0"
    };

    private static boolean initTried;
    private static boolean available;
    private static Method toggle;
    private static Field modeField;
    private static Field buttonStatesField;

    private TweakedControllerInputCompat() {
    }

    public static boolean isAvailable() {
        init();
        return available;
    }

    private static void init() {
        if (initTried) return;
        initTried = true;
        if (ModList.get() == null || !ModList.get().isLoaded("create_tweaked_controllers")) return;
        try {
            Class<?> handler = Class.forName(HANDLER_CLASS);
            toggle = handler.getMethod("toggle");
            modeField = handler.getField("MODE");
            buttonStatesField = handler.getField("buttonStates");
            available = true;
        } catch (ReflectiveOperationException | LinkageError error) {
            VSAnalogWarfare.LOGGER.warn("[VSAW] tweaked controller handler unavailable: {}",
                    error.getClass().getSimpleName());
        }
    }

    public static boolean isController(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && CONTROLLER_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** Whether the game's own tweaked-controller handler is currently ACTIVE. */
    public static boolean handlerActive() {
        if (!isAvailable()) return false;
        try {
            Object mode = modeField.get(null);
            return mode != null && "ACTIVE".equals(((Enum<?>) mode).name());
        } catch (ReflectiveOperationException | LinkageError error) {
            return false;
        }
    }

    /**
     * Activates the real controller handler when the scope is open and a
     * tweaked controller is in hand; re-arms it if the player exited it
     * (TAB/ESC) mid-scope. Safe to call every client tick.
     */
    public static void ensureActive() {
        if (!isAvailable() || handlerActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !isController(mc.player.getMainHandItem())) return;
        try {
            toggle.invoke(null);
        } catch (ReflectiveOperationException | LinkageError error) {
            VSAnalogWarfare.LOGGER.warn("[VSAW] Could not activate tweaked controller: {}",
                    error.getClass().getSimpleName());
        }
    }

    /** The real handler's live 15-bit button state, for the HUD. */
    public static short handlerButtonMask() {
        if (!isAvailable()) return 0;
        try {
            return buttonStatesField.getShort(null);
        } catch (ReflectiveOperationException | LinkageError error) {
            return 0;
        }
    }
}
