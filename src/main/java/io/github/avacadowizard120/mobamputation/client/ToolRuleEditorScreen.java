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
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Raw import/export editor for item and item-tag chance rules. */
final class ToolRuleEditorScreen extends Screen {
    private static final Duration TOOLTIP_DELAY = Duration.ofMillis(350L);

    private final Screen parent;
    private final Consumer<String> apply;
    private String rawValue;
    private MultiLineEditBox editor;
    private List<FormattedCharSequence> helpLines = List.of();
    private int helpY;

    ToolRuleEditorScreen(Screen parent, String rawValue, Consumer<String> apply) {
        super(Component.translatable("mobamputation.config.tool_rules.raw.title"));
        this.parent = parent;
        this.rawValue = rawValue;
        this.apply = apply;
    }

    @Override
    protected void init() {
        int contentWidth = Math.max(220, Math.min(460, width - 20));
        contentWidth = Math.min(contentWidth, width - 10);
        int left = (width - contentWidth) / 2;

        helpLines = font.split(
                Component.translatable("mobamputation.config.tool_rules.raw.help"),
                Math.max(100, contentWidth)
        );
        helpY = 27;
        int maxHelpLines = Math.max(1, (height - 136) / 10);
        if (helpLines.size() > maxHelpLines) {
            helpLines = helpLines.subList(0, maxHelpLines);
        }
        int editorTop = Math.min(height - 110, helpY + helpLines.size() * 10 + 6);
        int editorBottom = height - 56;

        editor = new MultiLineEditBox(
                font,
                left,
                editorTop,
                contentWidth,
                editorBottom - editorTop,
                title,
                Component.translatable("mobamputation.config.tool_rules.raw.placeholder")
        );
        editor.setCharacterLimit(32767);
        editor.setValue(rawValue);
        editor.setValueListener(value -> rawValue = value);
        addRenderableWidget(withTooltip(editor, "mobamputation.config.tool_rules.raw.editor.tooltip"));

        int gap = 4;
        int buttonY = height - 28;
        int buttonWidth = (contentWidth - gap * 3) / 4;
        addRenderableWidget(withTooltip(Button.builder(
                Component.translatable("mobamputation.config.tool_rules.raw.apply"),
                button -> applyAndClose()
        ).bounds(left, buttonY, buttonWidth, 20).build(),
                "mobamputation.config.tool_rules.raw.apply.tooltip"));
        addRenderableWidget(withTooltip(Button.builder(
                CommonComponents.GUI_CANCEL,
                button -> onClose()
        ).bounds(left + buttonWidth + gap, buttonY, buttonWidth, 20).build(),
                "mobamputation.config.tool_rules.raw.cancel.tooltip"));
        addRenderableWidget(withTooltip(Button.builder(
                Component.translatable("mobamputation.config.tool_rules.raw.copy"),
                button -> minecraft.keyboardHandler.setClipboard(editor.getValue())
        ).bounds(left + (buttonWidth + gap) * 2, buttonY, buttonWidth, 20).build(),
                "mobamputation.config.tool_rules.raw.copy.tooltip"));
        addRenderableWidget(withTooltip(Button.builder(
                Component.translatable("mobamputation.config.tool_rules.raw.paste"),
                button -> editor.setValue(minecraft.keyboardHandler.getClipboard())
        ).bounds(left + (buttonWidth + gap) * 3, buttonY, buttonWidth, 20).build(),
                "mobamputation.config.tool_rules.raw.paste.tooltip"));

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
        int y = helpY;
        for (FormattedCharSequence line : helpLines) {
            graphics.drawCenteredString(font, line, width / 2, y, 0xFFE0E0E0);
            y += 10;
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
