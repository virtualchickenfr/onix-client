package net.minecraft.client.render.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public class SheepEntityRenderState extends LivingEntityRenderState {
	public float neckAngle;
	public float headAngle;
	public boolean sheared;
	public DyeColor color = DyeColor.WHITE;
	public int id;

	public int getRgbColor() {
		if (this.isJeb()) {
			int i = 25;
			int j = MathHelper.floor(this.age);
			int k = j / 25 + this.id;
			int l = DyeColor.values().length;
			int m = k % l;
			int n = (k + 1) % l;
			float f = (j % 25 + MathHelper.fractionalPart(this.age)) / 25.0F;
			int o = SheepEntity.getRgbColor(DyeColor.byIndex(m));
			int p = SheepEntity.getRgbColor(DyeColor.byIndex(n));
			return ColorHelper.lerp(f, o, p);
		} else {
			return SheepEntity.getRgbColor(this.color);
		}
	}

	public boolean isJeb() {
		return this.customName != null && "jeb_".equals(this.customName.getString());
	}
}
