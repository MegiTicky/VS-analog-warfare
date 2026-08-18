package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.network.ModNetwork;
import com.erika.vsanalogwarfare.network.VehicleSetupEditorPacket;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupAction;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupActionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
    private final BlockPos setupPos;
    private int revision;
    private final List<VehicleSetupAction> actions;
    private boolean transmitters;
    private double scroll;
    private int dragged = -1;
    private int insertion = -1;
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
            screen.rebuildDelayField();
            return;
        }
        minecraft.setScreen(new VehicleSetupEditorScreen(pos, revision, actions));
    }

    @Override
    protected void init() {
        int bottom = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Record more"), button -> {
            ModNetwork.sendToServer(new VehicleSetupEditorPacket(setupPos, revision,
                    VehicleSetupEditorPacket.Operation.APPEND_RECORDING, 0, 0));
            onClose();
        }).bounds(width / 2 - 152, bottom, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Standard time"), button -> send(
                VehicleSetupEditorPacket.Operation.STANDARD_TIME, 0, 0)).bounds(width / 2 - 76, bottom, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal(transmitters ? "Actions" : "Transmitters"), button -> {
            commitDelayFields();
            transmitters = !transmitters;
            button.setMessage(Component.literal(transmitters ? "Actions" : "Transmitters"));
            scroll = 0;
            clampScroll();
            rebuildDelayField();
        }).bounds(width / 2 + 6, bottom, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose()).bounds(width / 2 + 88, bottom, 64, 20).build());
        rebuildDelayField();
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
        int contentHeight = rows.size() * ROW_HEIGHT;
        clampScroll();
        int maxScroll = maxScroll();
        graphics.enableScissor(left, top, right, bottom);
        int textY = (ROW_HEIGHT - font.lineHeight) / 2;
        for (int visibleIndex = 0; visibleIndex < rows.size(); visibleIndex++) {
            int actionIndex = rows.get(visibleIndex);
            int y = top + visibleIndex * ROW_HEIGHT - (int) scroll;
            if (y + ROW_HEIGHT < top || y > bottom) continue;
            VehicleSetupAction action = actions.get(actionIndex);
            boolean hovered = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + ROW_HEIGHT;
            graphics.fill(left + 1, y + 1, right - 1, y + ROW_HEIGHT - 1,
                    hovered ? 0xFF343B45 : 0xFF1B2026);
            graphics.fill(left + 1, y + ROW_HEIGHT - 1, right - 1, y + ROW_HEIGHT, 0xFF47515D);
            graphics.drawString(font, "=" + (actionIndex + 1), left + 5, y + textY, 0xFFB7C3D0);
            graphics.renderItem(icon(action), left + 34, y + (ROW_HEIGHT - 16) / 2);
            graphics.drawString(font, summary(action), left + 56, y + textY, 0xFFFFFFFF);
            if (transmitters) {
                graphics.drawString(font, "Ship " + action.targetShipId() + "  Offset " + action.shipOffset()
                        + "  Channel " + action.transmitterChannel() + "  Password: "
                        + (action.transmitterPassword() == null ? "" : action.transmitterPassword()), left + 56, y + textY, 0xFFB7C3D0);
            } else {
                graphics.drawString(font, "Wait", right - 146, y + textY, 0xFFB7C3D0);
                graphics.drawString(font, "ticks", right - 38, y + textY, 0xFFB7C3D0);
                graphics.drawString(font, "X", right - 13, y + textY, 0xFFFF7777);
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
        scroll -= delta * ROW_HEIGHT;
        clampScroll();
        rebuildDelayField();
        return true;
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
        return (int) ((mouseY - LIST_TOP + scroll) / ROW_HEIGHT);
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
        return Math.max(0, rows().size() * ROW_HEIGHT - (listBottom() - LIST_TOP));
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
        int first = Math.max(0, (int) Math.floor(scroll / ROW_HEIGHT));
        int last = Math.min(rows.size(), (int) Math.ceil((scroll + listBottom() - LIST_TOP) / ROW_HEIGHT) + 1);
        for (int visible = first; visible < last; visible++) {
            int actionIndex = rows.get(visible);
            int y = LIST_TOP + visible * ROW_HEIGHT - (int) scroll;
            if (y + ROW_HEIGHT <= LIST_TOP || y >= listBottom()) continue;
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
            case CREATE_TWEAKED_CONTROLLER -> "Create tweaked controller";
            case SET_TRACKWORK_STIFFNESS -> "Set track stiffness to " + action.stiffness() + "x";
            case SPAWN_TALLYHO_HULL_MG -> "Spawn Tallyho hull MG";
            case SPAWN_TALLYHO_ENTITY -> "Spawn " + action.tallyhoEntity();
            case GENERIC_BLOCK_INTERACTION -> "Use " + ItemStack.of(action.interactionItem()).getHoverName().getString();
            case GENERIC_BLOCK_LEFT_CLICK -> "Left-click with " + ItemStack.of(action.interactionItem()).getHoverName().getString();
            case CONFIGURE_ENDER_TRANSMITTER -> "Configure Ender transmitter";
        };
    }

    private static String blockName(CompoundTag state) {
        if (state == null) return "block";
        Block block = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), state).getBlock();
        return block.getName().getString();
    }
}
