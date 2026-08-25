package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.network.ModNetwork;
import com.erika.vsanalogwarfare.network.SetScrewdriverModePacket;
import com.erika.vsanalogwarfare.vehiclesetup.AnalogScrewdriverItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;

public final class AnalogScrewdriverOverlay {
    private static final int MODE_COUNT = 3;
    private static final int CELL_WIDTH = 95;
    private static final int BAR_HEIGHT = 38;
    private static final int BAR_WIDTH = CELL_WIDTH * MODE_COUNT + 4;

    private static final String[] MODE_NAMES = {
            "Record",
            "Mark for removal",
            "Transmitter scan"
    };
    private static final String[] MODE_DESCRIPTIONS = {
            "Right-click a Vehicle Setup block to record.",
            "Right-click blocks to mark them for removal.",
            "Right-click a Vehicle Setup block to start transmitter recording."
    };

    private static boolean active;
    private static boolean focused;
    private static int selectedMode;
    private static float focusOffset;

    private AnalogScrewdriverOverlay() {
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack stack = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        boolean holding = stack.getItem() instanceof AnalogScrewdriverItem;

        if (!holding) {
            active = false;
            focused = false;
            selectedMode = AnalogScrewdriverItem.REGULAR_MODE;
            focusOffset = 0.0f;
            return;
        }

        if (!active) {
            selectedMode = AnalogScrewdriverItem.mode(stack);
        }
        active = true;
        boolean shiftDown = minecraft.player != null && minecraft.player.isShiftKeyDown();
        if (shiftDown && !focused) {
            selectedMode = AnalogScrewdriverItem.mode(stack);
        } else if (!shiftDown) {
            selectedMode = AnalogScrewdriverItem.mode(stack);
        }
        focused = shiftDown;
        focusOffset += ((focused ? 18.0f : 0.0f) - focusOffset) * 0.15f;
    }

    public static void reset() {
        active = false;
        focused = false;
        selectedMode = AnalogScrewdriverItem.REGULAR_MODE;
        focusOffset = 0.0f;
    }

    public static boolean mouseScrolled(double delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || minecraft.player == null || !minecraft.player.isShiftKeyDown() || delta == 0.0) {
            return false;
        }

        if (!focused) {
            selectedMode = AnalogScrewdriverItem.mode(minecraft.player.getMainHandItem());
        }
        focused = true;
        // Scrolling up moves left through the menu, matching the visual order.
        selectedMode = Math.floorMod(selectedMode + (delta > 0.0 ? -1 : 1), MODE_COUNT);
        ModNetwork.sendToServer(new SetScrewdriverModePacket(selectedMode));
        return true;
    }

    public static void render(ForgeGui forgeGui, GuiGraphics graphics, float partialTick,
                              int screenWidth, int screenHeight) {
        if (!active || Minecraft.getInstance().options.hideGui) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ItemStack stack = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        if (!(stack.getItem() instanceof AnalogScrewdriverItem)) {
            return;
        }

        int x = (screenWidth - BAR_WIDTH) / 2;
        int y = screenHeight - BAR_HEIGHT - 55 - Math.round(focusOffset);
        int background = focused ? 0xD820242A : 0x9020242A;
        int selected = focused ? 0xE08A6A32 : 0xA06B542D;

        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, background);
        graphics.fill(x + 2 + selectedMode * CELL_WIDTH, y + 2,
                x + 2 + (selectedMode + 1) * CELL_WIDTH, y + BAR_HEIGHT - 2, selected);

        Font font = minecraft.font;
        for (int mode = 0; mode < MODE_COUNT; mode++) {
            int cellX = x + 2 + mode * CELL_WIDTH;
            int color = mode == selectedMode ? 0xFFFFFFFF : 0xFFB7B0A3;
            graphics.renderItem(stack, cellX + 38, y + 3);
            graphics.drawCenteredString(font, MODE_NAMES[mode], cellX + CELL_WIDTH / 2, y + 25, color);
        }

        String prompt = "Hold Shift and scroll to select";
        graphics.drawCenteredString(font, prompt, screenWidth / 2, y - 12, 0xFFE0D6C8);

        if (focused) {
            String description = MODE_DESCRIPTIONS[selectedMode];
            int descriptionWidth = Math.max(BAR_WIDTH, font.width(description) + 12);
            int descriptionX = (screenWidth - descriptionWidth) / 2;
            int descriptionY = y + BAR_HEIGHT + 4;
            graphics.fill(descriptionX, descriptionY, descriptionX + descriptionWidth,
                    descriptionY + 15, 0xB820242A);
            graphics.drawCenteredString(font, description, screenWidth / 2, descriptionY + 3, 0xFFE0D6C8);
        }
    }
}
