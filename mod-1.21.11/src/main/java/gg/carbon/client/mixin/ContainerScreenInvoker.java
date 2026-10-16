package gg.carbon.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenInvoker {
	/** Slot under the given GUI coordinates; works even if another mod changes how the screen renders. */
	@Invoker("getHoveredSlot")
	Slot carbon$getHoveredSlot(double mouseX, double mouseY);
}
