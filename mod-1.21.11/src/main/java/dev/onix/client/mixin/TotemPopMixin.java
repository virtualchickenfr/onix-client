package dev.onix.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.TotemSpiritModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Totem Spirit: notices totem pops (entity event 35) and can hide the vanilla totem particles. */
@Mixin(ClientPacketListener.class)
public abstract class TotemPopMixin {
    private static TotemSpiritModule onix$spirit() {
        return OnixClient.MODULES == null ? null : OnixClient.MODULES.get(TotemSpiritModule.class);
    }

    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void onix$totemPop(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (packet.getEventId() != 35) return;
        TotemSpiritModule spirit = onix$spirit();
        var level = Minecraft.getInstance().level;
        if (spirit != null && level != null) spirit.onPop(packet.getEntity(level));
    }

    @WrapWithCondition(method = "handleEntityEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleEngine;createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V"))
    private boolean onix$hideTotemParticles(ParticleEngine engine, Entity entity, ParticleOptions particle, int lifetime) {
        TotemSpiritModule spirit = onix$spirit();
        return spirit == null || !spirit.hidesVanillaParticles();
    }
}
