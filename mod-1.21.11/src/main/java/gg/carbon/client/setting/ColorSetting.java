package gg.carbon.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.awt.Color;

/** ARGB colour with optional chroma (slowly cycling hue). */
public class ColorSetting extends Setting<Integer> {
	private boolean chroma;

	public ColorSetting(String name, String description, int argb) {
		super(name, description, argb);
	}

	public int color() {
		if (!chroma) return value;
		float hue = (System.currentTimeMillis() % 6000L) / 6000f;
		float[] hsb = hsb();
		int rgb = Color.HSBtoRGB(hue, Math.max(0.5f, hsb[1]), Math.max(0.6f, hsb[2])) & 0xFFFFFF;
		return (value & 0xFF000000) | rgb;
	}

	public boolean chroma() {
		return chroma;
	}

	public void setChroma(boolean chroma) {
		this.chroma = chroma;
	}

	public int alpha() {
		return (value >>> 24) & 0xFF;
	}

	public float[] hsb() {
		return Color.RGBtoHSB((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF, null);
	}

	public void setHsb(float h, float s, float b, int alpha) {
		int rgb = Color.HSBtoRGB(h, s, b) & 0xFFFFFF;
		set((Math.max(0, Math.min(255, alpha)) << 24) | rgb);
	}

	@Override
	public JsonElement save() {
		JsonObject o = new JsonObject();
		o.add("argb", new JsonPrimitive(value));
		o.add("chroma", new JsonPrimitive(chroma));
		return o;
	}

	@Override
	public void load(JsonElement element) {
		if (element.isJsonObject()) {
			JsonObject o = element.getAsJsonObject();
			if (o.has("argb")) set(o.get("argb").getAsInt());
			if (o.has("chroma")) chroma = o.get("chroma").getAsBoolean();
		} else if (element.isJsonPrimitive()) {
			set(element.getAsInt());
		}
	}
}
