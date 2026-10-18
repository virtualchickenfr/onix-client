package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.WeatherChangerModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client-side weather. Only touches the client world, never the integrated server. */
@Mixin(Level.class)
public abstract class WorldMixin {
    @Unique
    private WeatherChangerModule onix$weather() {
        if (!((Object) this instanceof ClientLevel) || OnixClient.MODULES == null) return null;
        WeatherChangerModule weather = OnixClient.MODULES.get(WeatherChangerModule.class);
        return weather != null && weather.isEnabled() ? weather : null;
    }

    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void onix$rain(float delta, CallbackInfoReturnable<Float> cir) {
        WeatherChangerModule weather = onix$weather();
        if (weather != null) cir.setReturnValue(weather.rain(cir.getReturnValueF()));
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void onix$thunder(float delta, CallbackInfoReturnable<Float> cir) {
        WeatherChangerModule weather = onix$weather();
        if (weather != null) cir.setReturnValue(weather.thunder(cir.getReturnValueF()));
    }
}
