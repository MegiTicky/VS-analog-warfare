package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.mouseaim.MouseAimMode;
import com.erika.vsanalogwarfare.network.ModNetwork;
import com.erika.vsanalogwarfare.network.MouseAimConfigPacket;
import com.erika.vsanalogwarfare.network.MouseAimTuningPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Config screen for the Mouse Aim Controller, styled after
 * {@link VehicleSetupEditorScreen}: code-drawn flat panel, no GUI texture,
 * immediate-apply option buttons. Opened by the server's config snapshot
 * packet on right-click. Output strength is not set here — it follows the
 * rotation speed of the shaft feeding the block.
 */
public class MouseAimConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 240;
    private static final int COLOR_BORDER = 0xFF22272E;
    private static final int COLOR_BODY = 0xD8101419;
    private static final int COLOR_NORMAL = 0xFF343B45;
    private static final int COLOR_HOVERED = 0xFF414B56;
    private static final int COLOR_SELECTED = 0xFF3E5367;
    private static final int COLOR_ACCENT = 0xFF8AA1B7;
    private static final int COLOR_LABEL = 0xFFC9D4DF;
    private static final int COLOR_HINT = 0xFF8A97A5;

    private final BlockPos pos;
    private MouseAimMode mode;

    public static void open(BlockPos pos, MouseAimMode mode) {
        Minecraft.getInstance().setScreen(new MouseAimConfigScreen(pos, mode));
    }

    private MouseAimConfigScreen(BlockPos pos, MouseAimMode mode) {
        super(Component.translatable("block.vs_analog_warfare.mouse_aim_block"));
        this.pos = pos;
        this.mode = mode;
    }

    @Override
    protected void init() {
        int left = width / 2 - PANEL_WIDTH / 2;
        int buttonWidth = PANEL_WIDTH / 2 - 8;

        // Aim mode: one button per enum value.
        MouseAimMode[] modes = MouseAimMode.values();
        for (int i = 0; i < modes.length; i++) {
            MouseAimMode value = modes[i];
            addRenderableWidget(new OptionButton(left + 4 + i * (buttonWidth + 4), 52, buttonWidth, 18,
                    Component.translatable("vs_analog_warfare.mouse_aim.ui.mode." + value.name().toLowerCase()),
                    () -> selectMode(value), () -> mode == value));
        }

        // Servo tuning: only the turret mode drives the calibration loop.
        if (mode == MouseAimMode.TURRET) {
            addRenderableWidget(new OptionButton(left + 4, 146, buttonWidth, 18,
                    Component.translatable("vs_analog_warfare.mouse_aim.ui.calibrate"),
                    this::calibrate, () -> false));
            addRenderableWidget(new OptionButton(left + 8 + buttonWidth, 146, buttonWidth, 18,
                    Component.translatable("vs_analog_warfare.mouse_aim.ui.reset_tuning"),
                    this::resetTuning, () -> false));
        }

        addRenderableWidget(Button.builder(Component.translatable("vs_analog_warfare.mouse_aim.ui.done"), b -> onClose())
                .bounds(width / 2 - 40, height - 28, 80, 18)
                .build());
    }

    private void selectMode(MouseAimMode value) {
        if (mode == value) {
            return;
        }
        mode = value;
        send();
        // The tuning section is mode-gated; re-init to show or hide it.
        rebuildWidgets();
    }

    private void calibrate() {
        ModNetwork.sendToServer(new MouseAimTuningPacket(pos, MouseAimTuningPacket.Action.CALIBRATE));
        // The test reports progress on the action bar, which the screen hides.
        onClose();
    }

    private void resetTuning() {
        ModNetwork.sendToServer(new MouseAimTuningPacket(pos, MouseAimTuningPacket.Action.RESET));
        onClose();
    }

    private void send() {
        ModNetwork.sendToServer(new MouseAimConfigPacket(pos, mode));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = width / 2 - PANEL_WIDTH / 2;
        int right = left + PANEL_WIDTH;
        int top = 8;
        int bottom = height - 40;

        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, COLOR_BORDER);
        graphics.fill(left, top, right, bottom, COLOR_BODY);
        graphics.drawCenteredString(font, title, width / 2, top + 6, 0xFFFFFFFF);

        graphics.drawString(font, Component.translatable("vs_analog_warfare.mouse_aim.ui.mode_section"),
                left + 6, top + 24, COLOR_LABEL, false);
        graphics.drawCenteredString(font,
                Component.translatable(mode.getTranslationKey()).withStyle(ChatFormatting.GRAY),
                width / 2, top + 78, COLOR_HINT);

        graphics.drawCenteredString(font,
                Component.translatable("vs_analog_warfare.mouse_aim.ui.strength_hint").withStyle(ChatFormatting.GRAY),
                width / 2, top + 108, COLOR_HINT);

        if (mode == MouseAimMode.TURRET) {
            graphics.drawString(font, Component.translatable("vs_analog_warfare.mouse_aim.ui.tuning_section"),
                    left + 6, top + 126, COLOR_LABEL, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Flat option button with a selected-state highlight, like the editor's row style. */
    private class OptionButton extends AbstractButton {
        private final Runnable onPress;
        private final java.util.function.Supplier<Boolean> isSelected;

        private OptionButton(int x, int y, int width, int height, Component label, Runnable onPress,
                             java.util.function.Supplier<Boolean> isSelected) {
            super(x, y, width, height, label);
            this.onPress = onPress;
            this.isSelected = isSelected;
        }

        @Override
        public void onPress() {
            onPress.run();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean selected = isSelected.get();
            int color = selected ? COLOR_SELECTED : isHoveredOrFocused() ? COLOR_HOVERED : COLOR_NORMAL;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, color);
            if (selected) {
                graphics.fill(getX(), getY(), getX() + width, getY() + 1, COLOR_ACCENT);
            }
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2,
                    getY() + (height - 8) / 2, 0xFFFFFFFF);
        }
    }
}
