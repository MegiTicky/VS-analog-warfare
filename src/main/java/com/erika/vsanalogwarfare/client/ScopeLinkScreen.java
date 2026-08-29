package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.network.ModNetwork;
import com.erika.vsanalogwarfare.network.ScopeLinkPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class ScopeLinkScreen extends Screen {
    private final BlockPos scope;
    private final int revision;
    private final ScopeLinkPacket.LinkView primary;
    private final List<ScopeLinkPacket.LinkView> secondary;

    public ScopeLinkScreen(BlockPos scope, int revision, ScopeLinkPacket.LinkView primary,
                           List<ScopeLinkPacket.LinkView> secondary) {
        super(Component.literal("Scope cannon links"));
        this.scope = scope;
        this.revision = revision;
        this.primary = primary;
        this.secondary = secondary;
    }

    @Override
    protected void init() {
        int y = 30;
        addRenderableWidget(Button.builder(Component.literal("Link primary cannon"), button -> arm(0))
                .bounds(width / 2 - 150, y, 145, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Link new secondary"), button -> arm(1))
                .bounds(width / 2 + 5, y, 145, 20).build());
        y += 28;

        addRenderableWidget(Button.builder(Component.literal(primary == null
                        ? "Primary: none" : "Primary: " + describe(primary)), button -> { })
                .bounds(width / 2 - 150, y, 245, 20).build());
        if (primary != null) {
            addRenderableWidget(Button.builder(Component.literal("Delete"), button -> delete(-1))
                    .bounds(width / 2 + 100, y, 50, 20).build());
        }
        y += 25;

        for (int i = 0; i < secondary.size(); i++) {
            int index = i;
            addRenderableWidget(Button.builder(Component.literal("Secondary " + (i + 1) + ": " + describe(secondary.get(i))), button -> { })
                    .bounds(width / 2 - 150, y, 245, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Delete"), button -> delete(index))
                    .bounds(width / 2 + 100, y, 50, 20).build());
            y += 25;
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(width / 2 - 50, Math.min(height - 28, y + 8), 100, 20).build());
    }

    private void arm(int mode) {
        ModNetwork.sendToServer(new ScopeLinkPacket.Arm(scope, mode));
        onClose();
    }

    private void delete(int index) {
        ModNetwork.sendToServer(new ScopeLinkPacket.Delete(scope, revision, index));
        onClose();
    }

    private static String describe(ScopeLinkPacket.LinkView link) {
        return link.shipId() >= 0L ? "ship " + link.shipId() + " " + link.shipOffset() : link.fallbackPos().toShortString();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(font, "Scope " + scope.toShortString(), width / 2, height - 14, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
