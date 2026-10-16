package gg.carbon.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.carbon.client.module.HudModule;
import gg.carbon.client.module.Module;
import gg.carbon.client.module.ModuleManager;
import gg.carbon.client.setting.Setting;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Persists every module, setting and HUD position to config/carbon/config.json. */
public final class ConfigManager {
	private static final Logger LOG = LoggerFactory.getLogger("Onix");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final ModuleManager modules;
	private final Path file;
	private long dirtySince = -1;

	public ConfigManager(ModuleManager modules) {
		this.modules = modules;
		this.file = FabricLoader.getInstance().getConfigDir().resolve("carbon").resolve("config.json");
	}

	public void load() {
		if (!Files.exists(file)) {
			gg.carbon.client.ui.HudLayout.pending = true;
			return;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject mods = root.getAsJsonObject("modules");
			if (mods == null) return;
			List<Module> all = new ArrayList<>(modules.all());
			all.add(modules.client);
			for (Module m : all) {
				JsonObject o = mods.getAsJsonObject(m.id());
				if (o == null) continue;
				if (m != modules.client && o.has("enabled")) m.setEnabled(o.get("enabled").getAsBoolean());
				JsonObject settings = o.getAsJsonObject("settings");
				if (settings != null) {
					for (Setting<?> s : m.settings()) {
						JsonElement e = settings.get(s.configKey());
						if (e != null) {
							try {
								s.load(e);
							} catch (RuntimeException ex) {
								LOG.warn("Bad value for {}.{}", m.id(), s.configKey());
							}
						}
					}
				}
				if (m instanceof HudModule h && o.has("x") && o.has("y")) h.setRelative(o.get("x").getAsFloat(), o.get("y").getAsFloat());
				if (o.has("extra")) m.loadExtra(o.getAsJsonObject("extra"));
			}
		} catch (IOException | RuntimeException e) {
			LOG.error("Failed to load Onix config", e);
		}
	}

	public void save() {
		dirtySince = -1;
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		JsonObject mods = new JsonObject();
		List<Module> all = new ArrayList<>(modules.all());
		all.add(modules.client);
		for (Module m : all) {
			JsonObject o = new JsonObject();
			o.addProperty("enabled", m.enabled());
			JsonObject settings = new JsonObject();
			for (Setting<?> s : m.settings()) settings.add(s.configKey(), s.save());
			o.add("settings", settings);
			if (m instanceof HudModule h) {
				o.addProperty("x", h.rx());
				o.addProperty("y", h.ry());
			}
			JsonObject extra = m.saveExtra();
			if (extra != null) o.add("extra", extra);
			mods.add(m.id(), o);
		}
		root.add("modules", mods);
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("config.json.tmp");
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			LOG.error("Failed to save Onix config", e);
		}
	}

	/** Debounced save, flushed from the client tick. */
	public void saveSoon() {
		if (dirtySince < 0) dirtySince = System.currentTimeMillis();
	}

	public void tick() {
		if (dirtySince >= 0 && System.currentTimeMillis() - dirtySince > 1500) save();
	}
}
