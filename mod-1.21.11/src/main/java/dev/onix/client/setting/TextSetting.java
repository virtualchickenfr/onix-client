package dev.onix.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Free text, e.g. a chat message, a date pattern or an item id. */
public final class TextSetting extends Setting<String, TextSetting> {
    private final int maxLength;
    private String placeholder = "";

    public TextSetting(String key, String name, String defaultValue, int maxLength) {
        super(key, name, defaultValue);
        this.maxLength = maxLength;
    }

    public TextSetting placeholder(String text) {
        this.placeholder = text;
        return this;
    }

    public String placeholder() {
        return placeholder;
    }

    public int maxLength() {
        return maxLength;
    }

    @Override
    protected String sanitize(String candidate) {
        if (candidate == null) return null;
        String clean = candidate.replaceAll("[\\r\\n\\t]", " ");
        return clean.length() > maxLength ? clean.substring(0, maxLength) : clean;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) set(json.getAsString());
    }
}
