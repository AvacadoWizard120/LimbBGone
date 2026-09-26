package io.github.avacadowizard120.mobamputation.client;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Advanced, lossless import/export view for projectile rules. */
final class ProjectileRuleEditorScreen extends Screen {
    private static final Duration TOOLTIP_DELAY = Duration.ofMillis(350L);

    private final Screen parent;
    private final Consumer<String> apply;
    private String rawValue;
    private MultiLineEditBox editor;
    private List<FormattedCharSequence> warningLines = List.of();
    private int warningY;

    ProjectileRuleEditorScreen(Screen parent, String rawValue, Consumer<String> apply) {
        super(Component.translatable("mobamputation.config.projectiles.raw.title"));
        this.parent = parent;
        this.rawValue = rawValue;
        this.apply = apply;
    }

    @Override
    protected void init() {
        int contentWidth = Math.max(280, Math.min(430, width - 20));
        contentWidth = Math.min(contentWidth, width - 10);
        int left = (width - contentWidth) / 2;

        Component warning = Component.translatable("mobamputation.config.projectiles.raw.warning");
        warningLines = font.split(warning, Math.max(100, contentWidth));
        warningY = 26;
        int maxWarningLines = Math.max(1, (height - 136) / 10);
        if (warningLines.size() > maxWarningLines) {
            warningLines = warningLines.subList(0, maxWarningLines);
        }
        int editorTop = Math.min(height - 110, warningY + warningLines.size() * 10 + 6);
        int editorBottom = height - 56;

        editor = new MultiLineEditBox(
                font,
                left,
                editorTop,
                contentWidth,
                editorBottom - editorTop,
                title,
                Component.translatable("mobamputation.config.projectiles.raw.placeholder")
        );
        editor.setCharacterLimit(32767);
        editor.setValue(rawValue);
        editor.setValueListener(value -> rawValue = value);
        addRenderableWidget(withTooltip(editor, "mobamputation.config.projectiles.raw.editor.tooltip"));

        int gap = 3;
        int buttonY = height - 28;
        int buttonWidth = (contentWidth - gap * 3) / 4;
        addRenderableWidget(withTooltip(Button.builder(
                Component.translatable("mobamputation.config.projectiles.raw.apply"),
                button -> applyAndClose()
        ).bounds(left, buttonY, buttonWidth, 20).build(),
                "mobamputation.config.projectiles.raw.apply.tooltip"));
        addRenderableWidget(withTooltip(Button.builder(
                Component.translatable("mobamputation.config.projectiles.raw.cancel"),
                button -> onClose()
        ).bounds(left + buttonWidth + gap, buttonY, buttonWidth, 20).build(),
                "mobamputation.config.projectiles.raw.cancel.tooltip"));
        addRenderableWidget(withTooltip(Button.builder(
                Component.translatable("mobamputation.config.projectiles.raw.export"),
                button -> minecraft.keyboardHandler.setClipboard(editor.getValue())
        ).bounds(left + (buttonWidth + gap) * 2, buttonY, buttonWidth, 20).build(),
                "mobamputation.config.projectiles.raw.export.tooltip"));
        addRenderableWidget(withTooltip(Button.builder(
                Component.translatable("mobamputation.config.projectiles.raw.import"),
                button -> editor.setValue(minecraft.keyboardHandler.getClipboard())
        ).bounds(left + (buttonWidth + gap) * 3, buttonY, buttonWidth, 20).build(),
                "mobamputation.config.projectiles.raw.import.tooltip"));

        setInitialFocus(editor);
    }

    private void applyAndClose() {
        rawValue = editor.getValue();
        apply.accept(rawValue);
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);
        int lineY = warningY;
        for (FormattedCharSequence line : warningLines) {
            graphics.drawCenteredString(font, line, width / 2, lineY, 0xFFFFC966);
            lineY += 10;
        }
    }

    private static <T extends AbstractWidget> T withTooltip(T widget, String translationKey) {
        widget.setTooltip(Tooltip.create(Component.translatable(translationKey)));
        widget.setTooltipDelay(TOOLTIP_DELAY);
        return widget;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
