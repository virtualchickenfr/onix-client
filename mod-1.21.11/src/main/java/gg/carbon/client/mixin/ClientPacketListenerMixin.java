package gg.carbon.client.mixin;

import gg.carbon.client.util.Stats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleSetTime", at = @At("TAIL"))
	private void carbon$time(ClientboundSetTimePacket packet, CallbackInfo ci) {
		Stats.onTimePacket(packet.gameTime());
	}

	@Inject(method = "handleEntityEvent", at = @At("TAIL"))
	private void carbon$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (packet.getEventId() == 35 && mc.level != null && packet.getEntity(mc.level) == mc.player) Stats.onTotemPop();
	}
}
