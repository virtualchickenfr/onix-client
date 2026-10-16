package gg.carbon.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;

/** A keyboard binding stored as a GLFW key code (InputConstants KEY_*). 0 = unbound. */
public class KeySetting extends Setting<Integer> {
	public KeySetting(String name, String description, int defaultKey) {
		super(name, description, defaultKey);
	}

	public int key() {
		return value;
	}

	public boolean bound() {
		return value != 0;
	}

	public boolean isDown() {
		return bound() && InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), value);
	}

	public String display() {
		if (!bound()) return "None";
		return InputConstants.Type.KEYSYM.getOrCreate(value).getDisplayName().getString();
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(JsonElement element) {
		if (element.isJsonPrimitive()) set(element.getAsInt());
	}
}
