package net.minecraft.datafixer.fix;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Optional;
import net.minecraft.util.JsonHelper;

public class TextFixes {
	private static final String EMPTY_TEXT = text("");

	public static <T> Dynamic<T> text(DynamicOps<T> ops, String string) {
		String string2 = text(string);
		return new Dynamic<>(ops, ops.createString(string2));
	}

	public static <T> Dynamic<T> empty(DynamicOps<T> ops) {
		return new Dynamic<>(ops, ops.createString(EMPTY_TEXT));
	}

	public static String text(String string) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("text", string);
		return JsonHelper.toSortedString(jsonObject);
	}

	public static String method_66041(String string) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("translate", string);
		return JsonHelper.toSortedString(jsonObject);
	}

	public static <T> Dynamic<T> translate(DynamicOps<T> ops, String key) {
		String string = method_66041(key);
		return new Dynamic<>(ops, ops.createString(string));
	}

	public static String method_56629(String string) {
		if (!string.isEmpty() && !string.equals("null")) {
			char c = string.charAt(0);
			char d = string.charAt(string.length() - 1);
			if (c == '"' && d == '"' || c == '{' && d == '}' || c == '[' && d == ']') {
				try {
					JsonElement jsonElement = JsonParser.parseString(string);
					if (jsonElement.isJsonPrimitive()) {
						return text(jsonElement.getAsString());
					}

					return JsonHelper.toSortedString(jsonElement);
				} catch (JsonParseException var4) {
				}
			}

			return text(string);
		} else {
			return EMPTY_TEXT;
		}
	}

	public static Optional<String> getTranslate(String json) {
		try {
			JsonElement jsonElement = JsonParser.parseString(json);
			if (jsonElement.isJsonObject()) {
				JsonObject jsonObject = jsonElement.getAsJsonObject();
				JsonElement jsonElement2 = jsonObject.get("translate");
				if (jsonElement2 != null && jsonElement2.isJsonPrimitive()) {
					return Optional.of(jsonElement2.getAsString());
				}
			}
		} catch (JsonParseException var4) {
		}

		return Optional.empty();
	}
}
