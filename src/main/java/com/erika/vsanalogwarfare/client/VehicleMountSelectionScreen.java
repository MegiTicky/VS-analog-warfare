package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.network.ModNetwork;
import com.erika.vsanalogwarfare.network.VehicleMountPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

public class VehicleMountSelectionScreen extends Screen {
    private final BlockPos handle;
    private final int revision;
    private final List<String> roles;

    public VehicleMountSelectionScreen(BlockPos handle, int revision, List<String> roles) {
        super(Component.literal("Select vehicle seat"));
        this.handle = handle;
        this.revision = revision;
        this.roles = roles;
    }

    @Override protected void init() {
        int top = Math.max(28, (height - roles.size() * 25) / 2);
        for (int i = 0; i < roles.size(); i++) {
            int index = i;
            addRenderableWidget(Button.builder(Component.literal(roles.get(i)), button -> {
                ModNetwork.sendToServer(new VehicleMountPacket.Request(handle, revision, index));
                onClose();
            }).bounds(width / 2 - 100, top + i * 25, 200, 20).build());
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
