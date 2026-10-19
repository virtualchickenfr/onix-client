package gg.carbon.client.mixin;

import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractWidget.class)
public interface AbstractWidgetInvoker {
	@Invoker("renderScrollingStringOverContents")
	void carbon$scrolling(ActiveTextCollector output, Component message, int margin);
}
