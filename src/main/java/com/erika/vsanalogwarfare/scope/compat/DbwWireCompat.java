package com.erika.vsanalogwarfare.scope.compat;

import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupReflection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * Reflection bridge into drivebywire 0.0.6b for the scope-as-controller feature.
 *
 * <p>The scope behaves exactly like a create_tweaked_controllers controller held
 * against a drivebywire Tweaked Controller Hub: key presses are encoded into the
 * same 15-button bitmask and fed to {@code TweakedControllerWireServerHandler.receiveButton},
 * so DBW routes them to the hub's wire network precisely as if a real tweaked
 * controller had pressed the buttons (including the 30-tick timeout re-arm).</p>
 */
public final class DbwWireCompat {
    private static final String TWEAKED_HANDLER = "edn.stratodonut.drivebywire.compat.TweakedControllerWireServerHandler";
    private static final String TWEAKED_HUB_CLASS = "edn.stratodonut.drivebywire.blocks.TweakedControllerHubBlock";
    private static final String HUB_CLASS = "edn.stratodonut.drivebywire.blocks.ControllerHubBlock";
    private static final ResourceLocation TWEAKED_HUB_ID = new ResourceLocation("drivebywire", "tweaked_controller_hub");
    private static final ResourceLocation HUB_ID = new ResourceLocation("drivebywire", "controller_hub");

    private DbwWireCompat() {
    }

    public static boolean isAvailable() {
        return ModList.get() != null && ModList.get().isLoaded("drivebywire");
    }

    /**
     * Whether the block is drivebywire's Tweaked Controller Hub (the hub the scope
     * acts as a controller for). Matches by class name, by walking the superclass
     * chain (defensive against versions that wrap or rename the class), or by the
     * registry id {@code drivebywire:tweaked_controller_hub}.
     */
    public static boolean isTweakedHub(Block block) {
        return block != null && (matchesClass(block, TWEAKED_HUB_CLASS) || isRegisteredAs(block, TWEAKED_HUB_ID));
    }

    /**
     * Whether the block is drivebywire's plain Controller Hub. Same matching
     * strategy as {@link #isTweakedHub}.
     */
    public static boolean isHub(Block block) {
        return block != null && (matchesClass(block, HUB_CLASS) || isRegisteredAs(block, HUB_ID));
    }

    private static boolean matchesClass(Block block, String className) {
        for (Class<?> c = block.getClass(); c != null; c = c.getSuperclass()) {
            if (className.equals(c.getName())) return true;
        }
        return false;
    }

    private static boolean isRegisteredAs(Block block, ResourceLocation id) {
        try {
            return id.equals(BuiltInRegistries.BLOCK.getKey(block));
        } catch (RuntimeException error) {
            return false;
        }
    }

    /**
     * Feed a 15-button bitmask (bit i = button channel {@code i}) into the hub's
     * network exactly like the real tweaked-controller pipeline. {@code mask == 0}
     * releases everything. No-ops safely when drivebywire is absent.
     */
    public static void receiveButton(Level level, BlockPos hubPos, short mask) {
        if (level == null || hubPos == null || !isAvailable()) return;
        List<Boolean> buttons = new ArrayList<>(15);
        for (int i = 0; i < 15; i++) {
            buttons.add((mask & (1 << i)) != 0);
        }
        try {
            Class<?> handler = Class.forName(TWEAKED_HANDLER);
            VehicleSetupReflection.invoke(handler, "receiveButton", level, hubPos, buttons);
        } catch (ReflectiveOperationException | LinkageError error) {
            com.erika.vsanalogwarfare.VSAnalogWarfare.LOGGER.warn("[VSAW] Could not deliver scope button mask to DBW hub at {}: {}",
                    hubPos, error.getClass().getSimpleName());
        }
    }

    /**
     * Feed 10 wire half-axis values (0..15, drivebywire channel order:
     * leftX+/leftX-/leftY+/leftY-/rightX+/rightX-/rightY+/rightY-/triggerL/triggerR)
     * into the hub's network exactly like the real tweaked-controller axis
     * pipeline. All zeros release everything. No-ops safely when drivebywire is
     * absent.
     */
    public static void receiveAxis(Level level, BlockPos hubPos, byte[] halfAxes) {
        if (level == null || hubPos == null || !isAvailable()) return;
        List<Byte> axes = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            axes.add(i < halfAxes.length ? halfAxes[i] : (byte) 0);
        }
        try {
            Class<?> handler = Class.forName(TWEAKED_HANDLER);
            VehicleSetupReflection.invoke(handler, "receiveAxis", level, hubPos, axes);
        } catch (ReflectiveOperationException | LinkageError error) {
            com.erika.vsanalogwarfare.VSAnalogWarfare.LOGGER.warn("[VSAW] Could not deliver scope axis state to DBW hub at {}: {}",
                    hubPos, error.getClass().getSimpleName());
        }
    }
}
