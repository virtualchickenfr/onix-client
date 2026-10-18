package dev.onix.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BoolSetting extends Setting<Boolean, BoolSetting> {
    public BoolSetting(String key, String name, boolean defaultValue) {
        super(key, name, defaultValue);
    }

    public boolean isOn() {
        return value;
    }

    public void toggle() {
        set(!value);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) set(json.getAsBoolean());
    }
}
