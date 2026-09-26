package io.github.avacadowizard120.mobamputation.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Bite-sized reference for the guided projectile-rule editor. */
final class ProjectileRuleHelpScreen extends Screen {
    private final Screen parent;
    private final List<FormattedCharSequence> lines = new ArrayList<>();

    ProjectileRuleHelpScreen(Screen parent) {
        super(Component.translatable("mobamputation.config.projectiles.help.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        lines.clear();
        int textWidth = Math.max(180, Math.min(430, width - 30));
        for (int index = 1; index <= 5; index++) {
            if (!lines.isEmpty()) {
                lines.add(FormattedCharSequence.EMPTY);
            }
            lines.addAll(font.split(
                    Component.translatable("mobamputation.config.projectiles.help.line" + index),
                    textWidth
            ));
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - 75, height - 28, 150, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        int y = 30;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, Math.max(15, (width - Math.min(430, width - 30)) / 2), y,
                    0xFFE0E0E0);
            y += 10;
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
