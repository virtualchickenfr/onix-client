package dev.onix.client.module.impl.chat;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.OptionOverride;
import net.minecraft.client.Options;

/**
 * Longer chat history (applied by ChatHudMixin) plus full control over the chat box size,
 * spacing and opacity while the module is on.
 */
public class ChatHistoryModule extends Module {
    private final NumberSetting limit = add(new NumberSetting("limit", "Lines to Keep", 1000, 100, 10000, 100)
            .describe("Vanilla keeps 100 lines."));

    {
        group("Layout");
    }

    private final BoolSetting layout = add(new BoolSetting("layout", "Custom Chat Layout", false)
            .describe("Overrides the vanilla chat settings below while this module is on."));
    private final NumberSetting width = add(new NumberSetting("width", "Width", 320, 40, 320, 4).unit("px").visibleIf(layout::isOn));
    private final NumberSetting focusedHeight = add(new NumberSetting("focusedHeight", "Height (Open)", 180, 20, 180, 4).unit("px").visibleIf(layout::isOn));
    private final NumberSetting unfocusedHeight = add(new NumberSetting("unfocusedHeight", "Height (Closed)", 90, 20, 180, 4).unit("px").visibleIf(layout::isOn));
    private final NumberSetting scale = add(new NumberSetting("scale", "Text Size", 100, 30, 100, 5).unit("%").visibleIf(layout::isOn));
    private final NumberSetting spacing = add(new NumberSetting("spacing", "Line Spacing", 0, 0, 100, 5).unit("%").visibleIf(layout::isOn));
    private final NumberSetting textOpacity = add(new NumberSetting("textOpacity", "Text Opacity", 100, 10, 100, 5).unit("%").visibleIf(layout::isOn));
    private final NumberSetting backgroundOpacity = add(new NumberSetting("backgroundOpacity", "Background Opacity", 50, 0, 100, 5).unit("%").visibleIf(layout::isOn));

    private final OptionOverride<Double> widthOption = new OptionOverride<>(Options::chatWidth);
    private final OptionOverride<Double> focusedOption = new OptionOverride<>(Options::chatHeightFocused);
    private final OptionOverride<Double> unfocusedOption = new OptionOverride<>(Options::chatHeightUnfocused);
    private final OptionOverride<Double> scaleOption = new OptionOverride<>(Options::chatScale);
    private final OptionOverride<Double> spacingOption = new OptionOverride<>(Options::chatLineSpacing);
    private final OptionOverride<Double> opacityOption = new OptionOverride<>(Options::chatOpacity);
    private final OptionOverride<Double> backgroundOption = new OptionOverride<>(Options::textBackgroundOpacity);

    public ChatHistoryModule() {
        super("chathistory", "Chat History & Size", "Scroll back further than 100 lines and size the chat box.", Category.CHAT, false);
        tags("infinite chat", "scrollback", "chat width", "chat height", "chat scale");
    }

    public int limit(int vanilla) {
        return isEnabled() ? limit.getInt() : vanilla;
    }

    @Override
    public void onTick() {
        if (!layout.isOn()) {
            restoreLayout();
            return;
        }
        widthOption.apply((width.get() - 40) / 280.0);
        focusedOption.apply((focusedHeight.get() - 20) / 160.0);
        unfocusedOption.apply((unfocusedHeight.get() - 20) / 160.0);
        scaleOption.apply(scale.get() / 100.0);
        spacingOption.apply(spacing.get() / 100.0);
        opacityOption.apply(textOpacity.get() / 100.0);
        backgroundOption.apply(backgroundOpacity.get() / 100.0);
    }

    private void restoreLayout() {
        widthOption.restore();
        focusedOption.restore();
        unfocusedOption.restore();
        scaleOption.restore();
        spacingOption.restore();
        opacityOption.restore();
        backgroundOption.restore();
    }

    @Override
    protected void onDisable() {
        restoreLayout();
    }

    @Override
    public void onShutdown() {
        restoreLayout();
    }
}
