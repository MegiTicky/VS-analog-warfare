package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.network.ModNetwork;
import com.erika.vsanalogwarfare.network.ScrewdriverHudPacket;
import com.erika.vsanalogwarfare.network.SetScrewdriverModePacket;
import com.erika.vsanalogwarfare.vehiclesetup.AnalogScrewdriverItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.List;

public final class AnalogScrewdriverOverlay {
    private static final int MODE_COUNT = 3;
    private static final int PANEL_WIDTH = 290;
    private static final int PANEL_LEFT = 10;
    private static final int MAX_ENTRIES = 12;

    private static final String[] MODE_NAMES = {
            "Record",
            "Mark for removal",
            "Transmitter scan"
    };
    private static final String[] MODE_DESCRIPTIONS = {
            "Right-click a Vehicle Setup block to record.",
            "Right-click blocks to mark them for removal.",
            "Right-click a Vehicle Setup block to scan transmitters."
    };

    private static boolean holding;
    private static boolean focused;
    private static int selectedMode;
    private static boolean recording;
    private static int recordingMode;
    private static String setupName = "";
    private static List<String> entries = List.of();

    private AnalogScrewdriverOverlay() {
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack stack = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        boolean wasHolding = holding;
        holding = stack.getItem() instanceof AnalogScrewdriverItem;
        if (!holding) {
            focused = false;
            selectedMode = AnalogScrewdriverItem.REGULAR_MODE;
            if (wasHolding) clearHudState();
            return;
        }

        selectedMode = AnalogScrewdriverItem.mode(stack);
        focused = minecraft.player != null && minecraft.player.isShiftKeyDown();
    }

    public static void reset() {
        holding = false;
        focused = false;
        selectedMode = AnalogScrewdriverItem.REGULAR_MODE;
        clearHudState();
    }

    public static void setHudState(ScrewdriverHudPacket packet) {
        recording = packet.active();
        recordingMode = packet.recordingMode();
        setupName = packet.setupName();
        entries = List.copyOf(packet.entries());
    }

    private static void clearHudState() {
        recording = false;
        recordingMode = AnalogScrewdriverItem.REGULAR_MODE;
        setupName = "";
        entries = List.of();
    }

    public static boolean mouseScrolled(double delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!holding || minecraft.player == null || !minecraft.player.isShiftKeyDown() || delta == 0.0) {
            return false;
        }

        selectedMode = Math.floorMod(selectedMode + (delta > 0.0 ? -1 : 1), MODE_COUNT);
        ModNetwork.sendToServer(new SetScrewdriverModePacket(selectedMode));
        return true;
    }

    public static void render(ForgeGui forgeGui, GuiGraphics graphics, float partialTick,
                              int screenWidth, int screenHeight) {
        if (!holding || Minecraft.getInstance().options.hideGui) return;

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int x = screenWidth - PANEL_WIDTH - PANEL_LEFT;
        int y = 10;
        int visibleEntries = Math.min(entries.size(), MAX_ENTRIES);
        boolean hasMoreEntries = entries.size() > MAX_ENTRIES;
        int panelHeight = 60;
        if (recording) {
            panelHeight = 76 + visibleEntries * 12 + (hasMoreEntries ? 12 : 0);
        }

        graphics.fill(x, y, x + PANEL_WIDTH, y + panelHeight, 0xB820242A);
        graphics.fill(x, y, x + PANEL_WIDTH, y + 2, 0xE08A6A32);

        graphics.drawString(font, "ANALOG SCREWDRIVER", x + 8, y + 7, 0xFFFFD27D, false);
        graphics.drawString(font, focused ? "Shift + scroll to change mode" : "Hold Shift + scroll to change mode",
                x + 8, y + 19, 0xFFB7B0A3, false);
        graphics.drawString(font, trimToWidth(font, MODE_DESCRIPTIONS[selectedMode], PANEL_WIDTH - 16),
                x + 8, y + 31, 0xFFB7B0A3, false);

        int tabsY = y + 43;
        int tabWidth = (PANEL_WIDTH - 12) / MODE_COUNT;
        for (int mode = 0; mode < MODE_COUNT; mode++) {
            int tabX = x + 4 + mode * tabWidth;
            if (mode == selectedMode) {
                graphics.fill(tabX, tabsY - 2, tabX + tabWidth - 2, tabsY + 11, 0xD08A6A32);
            }
            int color = mode == selectedMode ? 0xFFFFFFFF : 0xFFB7B0A3;
            graphics.drawCenteredString(font, MODE_NAMES[mode], tabX + (tabWidth - 2) / 2, tabsY, color);
        }

        if (!recording) return;

        int contentY = y + 64;
        String modeName = MODE_NAMES[Math.max(0, Math.min(MODE_COUNT - 1, recordingMode))];
        graphics.drawString(font, "RECORDING: " + modeName, x + 8, contentY, 0xFFFFA04A, false);
        graphics.drawString(font, setupName, x + PANEL_WIDTH - 8 - font.width(setupName), contentY,
                0xFFDDDDDD, false);

        if (entries.isEmpty()) {
            graphics.drawString(font, "No entries recorded yet", x + 8, contentY + 13, 0xFFB7B0A3, false);
            return;
        }
        for (int index = 0; index < visibleEntries; index++) {
            String entry = (index + 1) + ". " + entries.get(index);
            graphics.drawString(font, trimToWidth(font, entry, PANEL_WIDTH - 16),
                    x + 8, contentY + 14 + index * 12, 0xFFE5E5E5, false);
        }
        if (entries.size() > MAX_ENTRIES) {
            graphics.drawString(font, "+" + (entries.size() - MAX_ENTRIES) + " more",
                    x + 8, contentY + 14 + visibleEntries * 12, 0xFFB7B0A3, false);
        }
    }

    private static String trimToWidth(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        String suffix = "...";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + suffix) > width) end--;
        return text.substring(0, end) + suffix;
    }
}
