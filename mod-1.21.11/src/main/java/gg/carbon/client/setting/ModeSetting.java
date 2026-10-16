package gg.carbon.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.List;

public class ModeSetting extends Setting<String> {
	private final List<String> modes;

	public ModeSetting(String name, String description, String defaultValue, String... modes) {
		super(name, description, defaultValue);
		this.modes = List.of(modes);
	}

	public List<String> modes() {
		return modes;
	}

	public boolean is(String mode) {
		return value.equals(mode);
	}

	public int index() {
		return Math.max(0, modes.indexOf(value));
	}

	public void cycle(int dir) {
		int i = Math.floorMod(index() + dir, modes.size());
		set(modes.get(i));
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(JsonElement element) {
		if (element.isJsonPrimitive() && modes.contains(element.getAsString())) set(element.getAsString());
	}
}
