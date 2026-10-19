package net.minecraft.client.render.entity.state;

import com.google.common.collect.ImmutableList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record EntityHitboxAndView(double viewX, double viewY, double viewZ, ImmutableList<EntityHitbox> hitboxes) {
}
