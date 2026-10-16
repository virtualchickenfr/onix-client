package gg.carbon.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractButton.class)
public interface AbstractButtonInvoker {
	@Invoker("renderDefaultSprite")
	void carbon$extractDefaultSprite(GuiGraphics graphics);
}
