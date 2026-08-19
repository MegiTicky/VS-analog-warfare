package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.network.ModNetwork;
import com.erika.vsanalogwarfare.network.VehicleSetupEditorPacket;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupAction;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupActionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.NbtUtils;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class VehicleSetupEditorScreen extends Screen {
    private static final int ROW_HEIGHT = 30;
    private static final int LIST_TOP = 35;
    private static final int FIELD_HEIGHT = 18;
    private static final int ACTION_TEXT_WIDTH = 160;
    private final BlockPos setupPos;
    private int revision;
    private final List<VehicleSetupAction> actions;
    private boolean transmitters;
    private double scroll;
    private int dragged = -1;
    private int insertion = -1;
    private int selectedTransmitter = -1;
    private Button showButton;
    private Button clearAllButton;
    private final List<DelayField> delayFields = new ArrayList<>();

    private VehicleSetupEditorScreen(BlockPos setupPos, int revision, List<VehicleSetupAction> actions) {
        super(Component.literal("Vehicle Setup Macro"));
        this.setupPos = setupPos;
        this.revision = revision;
        this.actions = new ArrayList<>(actions);
    }

    public static void open(BlockPos pos, int revision, List<CompoundTag> tags) {
        List<VehicleSetupAction> actions = new ArrayList<>();
        for (CompoundTag tag : tags) {
            VehicleSetupAction action = VehicleSetupAction.load(tag);
            if (action != null) actions.add(action);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof VehicleSetupEditorScreen screen && screen.setupPos.equals(pos)) {
            screen.revision = revision;
            screen.actions.clear();
            screen.actions.addAll(actions);
            screen.selectedTransmitter = -1;
            ClientTransmitterHighlight.clear();
            screen.rebuildDelayField();
            return;
        }
        ClientTransmitterHighlight.clear();
        minecraft.setScreen(new VehicleSetupEditorScreen(pos, revision, actions));
    }

    @Override
    protected void init() {
        addFooterWidgets();
        rebuildDelayField();
    }

    private void addFooterWidgets() {
        int bottom = height - 28;
        if (transmitters) {
            addRenderableWidget(Button.builder(Component.literal("Scan energy"), button -> send(
                    VehicleSetupEditorPacket.Operation.SCAN_TRANSMITTERS, 0, 0))
                    .bounds(width / 2 - 152, bottom, 94, 20)
                    .tooltip(Tooltip.create(Component.literal("Find and add Ender Transmitters on the setup ship.")))
                    .build());
            showButton = addRenderableWidget(Button.builder(Component.literal("Show added"), button -> toggleHighlight())
                    .bounds(width / 2 - 54, bottom, 76, 20)
                    .tooltip(Tooltip.create(Component.literal("Highlight the selected transmitter in the world.")))
                    .build());
            updateShowButton();
            addRenderableWidget(Button.builder(Component.literal("Actions"), button -> switchView())
                    .bounds(width / 2 + 26, bottom, 58, 20)
                    .tooltip(Tooltip.create(Component.literal("Return to the recorded action list.")))
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                    .bounds(width / 2 + 88, bottom, 64, 20)
                    .tooltip(Tooltip.create(Component.literal("Close the Vehicle Setup Macro.")))
                    .build());
            return;
        }
        clearAllButton = addRenderableWidget(new RedButton(width / 2 - 152, bottom, 72, 20,
                Component.literal("Delete all").withStyle(ChatFormatting.RED), button -> confirmClearAll()));
        clearAllButton.setTooltip(Tooltip.create(Component.literal("Delete every recorded action in this macro.")));
        addRenderableWidget(Button.builder(Component.literal("Standard time"), button -> send(
                VehicleSetupEditorPacket.Operation.STANDARD_TIME, 0, 0))
                .bounds(width / 2 - 76, bottom, 78, 20)
                .tooltip(Tooltip.create(Component.literal("Set the first action to 0 ticks and all following actions to 1 tick.")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("Transmitters"), button -> switchView())
                .bounds(width / 2 + 6, bottom, 78, 20)
                .tooltip(Tooltip.create(Component.literal("View and configure recorded Ender Transmitter actions.")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 + 88, bottom, 64, 20)
                .tooltip(Tooltip.create(Component.literal("Close the Vehicle Setup Macro.")))
                .build());
    }

    private void switchView() {
        commitDelayFields();
        transmitters = !transmitters;
        scroll = 0;
        clampScroll();
        clearWidgets();
        addFooterWidgets();
        rebuildDelayField();
    }

    private void confirmClearAll() {
        if (!clearAllButton.getMessage().getString().equals("Confirm?")) {
            clearAllButton.setMessage(Component.literal("Confirm?").withStyle(ChatFormatting.RED));
            clearAllButton.setTooltip(Tooltip.create(Component.literal("Click again to permanently delete every recorded action.")));
            return;
        }
        send(VehicleSetupEditorPacket.Operation.CLEAR_ALL, 0, 0);
    }

    private void toggleHighlight() {
        VehicleSetupAction action = selectedAction();
        if (action == null) return;
        if (ClientTransmitterHighlight.isShowing(action)) ClientTransmitterHighlight.clear();
        else ClientTransmitterHighlight.show(setupPos, action);
        updateShowButton();
    }

    private void updateShowButton() {
        if (showButton == null) return;
        VehicleSetupAction action = selectedAction();
        showButton.active = action != null;
        showButton.setMessage(Component.literal(action != null && ClientTransmitterHighlight.isShowing(action)
                ? "Hide" : "Show added"));
    }

    private VehicleSetupAction selectedAction() {
        return selectedTransmitter >= 0 && selectedTransmitter < actions.size()
                && actions.get(selectedTransmitter).type() == VehicleSetupActionType.CONFIGURE_ENDER_TRANSMITTER
                ? actions.get(selectedTransmitter) : null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = width / 2 - 154;
        int right = width / 2 + 154;
        int top = LIST_TOP;
        int bottom = listBottom();
        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, 0xFF22272E);
        graphics.fill(left, top, right, bottom, 0xD8101419);
        graphics.drawCenteredString(font, transmitters ? "Recorded Energy Transmitters" : title.getString(), width / 2, 14, 0xFFFFFFFF);
        List<Integer> rows = rows();
        int contentHeight = rows.size() * rowHeight();
        clampScroll();
        int maxScroll = maxScroll();
        graphics.enableScissor(left, top, right, bottom);
        int textY = (ROW_HEIGHT - font.lineHeight) / 2;
        for (int visibleIndex = 0; visibleIndex < rows.size(); visibleIndex++) {
            int actionIndex = rows.get(visibleIndex);
            int y = top + visibleIndex * rowHeight() - (int) scroll;
            if (y + rowHeight() < top || y > bottom) continue;
            VehicleSetupAction action = actions.get(actionIndex);
            boolean hovered = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + rowHeight();
            graphics.fill(left + 1, y + 1, right - 1, y + rowHeight() - 1,
                    actionIndex == selectedTransmitter ? 0xFF3E5367 : hovered ? 0xFF343B45 : 0xFF1B2026);
            graphics.fill(left + 1, y + rowHeight() - 1, right - 1, y + rowHeight(), 0xFF47515D);
            graphics.drawString(font, "=" + (actionIndex + 1), left + 5, y + textY, 0xFFB7C3D0);
            graphics.renderItem(icon(action), left + 34, y + (rowHeight() - 16) / 2);
            if (transmitters) {
                graphics.drawString(font, summary(action), left + 56, y + 4, 0xFFFFFFFF);
                graphics.drawString(font, "Ship " + action.targetShipId() + "  Offset " + action.shipOffset(),
                        left + 56, y + 17, 0xFFB7C3D0);
                graphics.drawString(font, "Channel " + action.transmitterChannel() + "  Password: "
                        + (action.transmitterPassword() == null ? "" : action.transmitterPassword()),
                        left + 56, y + 30, 0xFFB7C3D0);
            } else {
                graphics.drawString(font, abbreviatedSummary(action), left + 56, y + textY, 0xFFFFFFFF);
                graphics.drawString(font, "ticks", right - 38, y + textY, 0xFFB7C3D0);
                graphics.drawString(font, "X", right - 13, y + textY, 0xFFFF7777);
                if (mouseX >= left + 56 && mouseX < width / 2 + 67 && mouseY >= y && mouseY < y + rowHeight()) {
                    graphics.renderTooltip(font, Component.literal("Click and hold to drag and change this action's order."),
                            mouseX, mouseY);
                }
            }
            if (dragged >= 0 && insertion == visibleIndex) graphics.fill(left, y, right, y + 2, 0xFF71B7FF);
        }
        for (DelayField field : delayFields) {
            field.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();
        if (maxScroll > 0) {
            int barHeight = Math.max(12, (bottom - top) * (bottom - top) / contentHeight);
            int barY = top + (int) ((bottom - top - barHeight) * scroll / maxScroll);
            graphics.fill(right + 4, barY, right + 8, barY + barHeight, 0xFF8AA1B7);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!insideList(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, delta);
        scroll -= delta * rowHeight();
        clampScroll();
        rebuildDelayField();
        return true;
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
    public void tick() {
        for (DelayField field : delayFields) field.tick();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (!insideList(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
        int row = rowAt(mouseY);
        if (row >= 0 && button == 0 && transmitters) {
            selectedTransmitter = row;
            updateShowButton();
            return true;
        }
        if (row >= 0 && button == 0 && !transmitters) {
            int right = width / 2 + 154;
            if (mouseX >= right - 20) {
                send(VehicleSetupEditorPacket.Operation.DELETE, row, 0);
            } else if (mouseX < width / 2 - 125) {
                dragged = row;
                insertion = visibleRowAt(mouseY);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragged >= 0) {
            insertion = Math.max(0, Math.min(rows().size() - 1, visibleRowAt(mouseY)));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragged >= 0) {
            List<Integer> rows = rows();
            if (insertion >= 0 && insertion < rows.size()) send(VehicleSetupEditorPacket.Operation.MOVE, dragged, rows.get(insertion));
            dragged = -1;
            insertion = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void send(VehicleSetupEditorPacket.Operation operation, int first, int second) {
        ModNetwork.sendToServer(new VehicleSetupEditorPacket(setupPos, revision, operation, first, second));
    }

    private List<Integer> rows() {
        List<Integer> rows = new ArrayList<>();
        for (int index = 0; index < actions.size(); index++) {
            if (!transmitters || actions.get(index).type() == VehicleSetupActionType.CONFIGURE_ENDER_TRANSMITTER) rows.add(index);
        }
        return rows;
    }

    private int visibleRowAt(double mouseY) {
        return (int) ((mouseY - LIST_TOP + scroll) / rowHeight());
    }

    private int rowAt(double mouseY) {
        if (!insideList(0, mouseY)) return -1;
        int visible = visibleRowAt(mouseY);
        List<Integer> rows = rows();
        return visible >= 0 && visible < rows.size() ? rows.get(visible) : -1;
    }

    private int listBottom() {
        return height - 36;
    }

    private int maxScroll() {
        return Math.max(0, rows().size() * rowHeight() - (listBottom() - LIST_TOP));
    }

    private int rowHeight() {
        return transmitters ? 52 : ROW_HEIGHT;
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private boolean insideList(double mouseX, double mouseY) {
        return mouseY >= LIST_TOP && mouseY < listBottom()
                && (mouseX == 0 || mouseX >= width / 2 - 154 && mouseX < width / 2 + 154);
    }

    private void rebuildDelayField() {
        commitDelayFields();
        for (DelayField field : delayFields) removeWidget(field);
        delayFields.clear();
        if (transmitters || minecraft == null) return;
        List<Integer> rows = rows();
        int first = Math.max(0, (int) Math.floor(scroll / rowHeight()));
        int last = Math.min(rows.size(), (int) Math.ceil((scroll + listBottom() - LIST_TOP) / rowHeight()) + 1);
        for (int visible = first; visible < last; visible++) {
            int actionIndex = rows.get(visible);
            int y = LIST_TOP + visible * rowHeight() - (int) scroll;
            if (y + rowHeight() <= LIST_TOP || y >= listBottom()) continue;
            DelayField field = new DelayField(actionIndex, width / 2 + 67,
                    y + (ROW_HEIGHT - FIELD_HEIGHT) / 2);
            delayFields.add(field);
            addWidget(field);
        }
    }

    private void commitDelayFields() {
        for (DelayField field : delayFields) field.commit();
    }

    private final class DelayField extends EditBox {
        private final int actionIndex;
        private final int originalDelay;
        private boolean committed;

        private DelayField(int actionIndex, int x, int y) {
            super(font, x, y, 42, FIELD_HEIGHT, Component.literal("Wait ticks"));
            this.actionIndex = actionIndex;
            this.originalDelay = actions.get(actionIndex).delayBeforeTicks();
            setValue(Integer.toString(originalDelay));
            setFilter(value -> value.matches("\\d{0,5}"));
            setTooltip(Tooltip.create(Component.literal("Delay before this action runs, in ticks.")));
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commit();
                setFocused(false);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void setFocused(boolean focused) {
            if (!focused && isFocused()) commit();
            super.setFocused(focused);
        }

        private void commit() {
            if (committed) return;
            committed = true;
            try {
                int delay = Integer.parseInt(getValue());
                if (delay != originalDelay) send(VehicleSetupEditorPacket.Operation.SET_DELAY, actionIndex, delay);
            } catch (NumberFormatException ignored) {
                setValue(Integer.toString(originalDelay));
            }
        }
    }

    private static ItemStack icon(VehicleSetupAction action) {
        if (action.interactionItem() != null) return ItemStack.of(action.interactionItem());
        if (action.controller() != null) return ItemStack.of(action.controller());
        if (action.type() == VehicleSetupActionType.PLACE_BLOCK && action.blockState() != null) {
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), action.blockState());
            return new ItemStack(state.getBlock());
        }
        if (action.type() == VehicleSetupActionType.CONFIGURE_ENDER_TRANSMITTER) {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation("createendertransmission", "energy_transmitter"));
            return item == Items.AIR ? new ItemStack(Items.REDSTONE) : new ItemStack(item);
        }
        return switch (action.type()) {
            case REMOVE_BLOCK -> new ItemStack(Items.IRON_PICKAXE);
            case LINK_DBW_BACKUPS -> new ItemStack(Items.CHAIN);
            case SET_TRACKWORK_STIFFNESS -> trackToolKit();
            case SPAWN_TALLYHO_HULL_MG, SPAWN_TALLYHO_ENTITY -> new ItemStack(Items.DISPENSER);
            default -> new ItemStack(Items.PAPER);
        };
    }

    private static ItemStack trackToolKit() {
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation("trackwork", "track_tool_kit"));
        return item == Items.AIR ? new ItemStack(Items.REPEATER) : new ItemStack(item);
    }

    private static String summary(VehicleSetupAction action) {
        return switch (action.type()) {
            case PLACE_BLOCK -> "Place " + blockName(action.blockState());
            case REMOVE_BLOCK -> "Remove block";
            case LINK_DBW_BACKUPS -> "Link DBW backups";
            case CREATE_TWEAKED_CONTROLLER -> "Link controller to DBW hub";
            case SET_TRACKWORK_STIFFNESS -> "Set track stiffness to " + action.stiffness() + "x";
            case SPAWN_TALLYHO_HULL_MG -> "Spawn Tallyho hull MG";
            case SPAWN_TALLYHO_ENTITY -> "Spawn " + action.tallyhoEntity();
            case GENERIC_BLOCK_INTERACTION -> "Use " + ItemStack.of(action.interactionItem()).getHoverName().getString();
            case GENERIC_BLOCK_LEFT_CLICK -> "Left-click with " + ItemStack.of(action.interactionItem()).getHoverName().getString();
            case CONFIGURE_ENDER_TRANSMITTER -> "Configure Ender transmitter";
        };
    }

    private String abbreviatedSummary(VehicleSetupAction action) {
        String text = summary(action);
        if (font.width(text) <= ACTION_TEXT_WIDTH) return text;
        return font.plainSubstrByWidth(text, ACTION_TEXT_WIDTH - font.width("..."), false) + "...";
    }

    private static String blockName(CompoundTag state) {
        if (state == null) return "block";
        Block block = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), state).getBlock();
        return block.getName().getString();
    }

    private static final class RedButton extends Button {
        private RedButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int color = isHoveredOrFocused() ? 0xFF9D3535 : 0xFF702424;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, color);
            graphics.fill(getX(), getY(), getX() + width, getY() + 1, 0xFFFF7777);
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                    getY() + (height - 8) / 2, active ? 0xFFFFFFFF : 0xFF777777);
        }
    }
}
