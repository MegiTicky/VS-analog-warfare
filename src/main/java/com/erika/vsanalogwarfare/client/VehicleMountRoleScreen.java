package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.network.ModNetwork;
import com.erika.vsanalogwarfare.network.VehicleMountPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class VehicleMountRoleScreen extends Screen {
    private final BlockPos handle;
    private final UUID seat;
    private final BlockPos seatPos;
    private final long shipId;
    private final BlockPos shipOffset;
    private EditBox role;

    public VehicleMountRoleScreen(BlockPos handle, UUID seat, BlockPos seatPos, long shipId, BlockPos shipOffset) {
        super(Component.literal("Name vehicle seat"));
        this.handle = handle; this.seat = seat; this.seatPos = seatPos; this.shipId = shipId; this.shipOffset = shipOffset;
    }

    @Override protected void init() {
        role = new EditBox(font, width / 2 - 100, height / 2 - 22, 200, 20, Component.literal("Role"));
        role.setMaxLength(32);
        role.setValue("Seat");
        addRenderableWidget(role);
        addRenderableWidget(Button.builder(Component.literal("Link"), button -> submit())
                .bounds(width / 2 - 100, height / 2 + 5, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(width / 2 + 5, height / 2 + 5, 95, 20).build());
        setInitialFocus(role);
    }

    private void submit() {
        if (!role.getValue().isBlank()) ModNetwork.sendToServer(new VehicleMountPacket.Link(handle, seat, seatPos, shipId, shipOffset, role.getValue()));
        onClose();
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { submit(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 48, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
