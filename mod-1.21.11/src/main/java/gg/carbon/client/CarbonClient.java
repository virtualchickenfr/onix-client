package gg.carbon.client;

import dev.onix.client.OnixClient;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import org.slf4j.Logger;

/**
 * Carbon's menu designs live on inside Onix. This small stand-in gives them what they used to read from Carbon's
 * own module manager and settings; everything is backed by Onix's configuration.
 */
public final class CarbonClient {
	public static final String VERSION = OnixClient.VERSION;
	public static final Logger LOG = OnixClient.LOGGER;

	private static final Modules MODULES = new Modules();
	private static final Config CONFIG = new Config();

	private CarbonClient() {}

	public static Modules modules() {
		return OnixClient.CONFIG == null ? null : MODULES;
	}

	public static Config config() {
		return CONFIG;
	}

	public static final class Modules {
		public final Client client = new Client();

		public java.util.List<?> all() {
			return OnixClient.MODULES == null ? java.util.List.of() : OnixClient.MODULES.all();
		}
	}

	public static final class Config {
		public void save() {
			OnixClient.save();
		}

		public void saveSoon() {
			OnixClient.save();
		}
	}

	/** The settings the menu designs read. */
	public static final class Client {
		public final Flag menuLight = new Flag() {
			public boolean on() { return OnixClient.CONFIG.lightMode.isOn(); }
			public void set(boolean v) { OnixClient.CONFIG.lightMode.set(v); }
		};
		public final Flag menuSkin = new Flag() {
			public boolean on() { return OnixClient.CONFIG.menuCharacter.isOn(); }
			public void set(boolean v) { OnixClient.CONFIG.menuCharacter.set(v); }
		};
		public final Flag clickSounds = new Flag() {
			public boolean on() { return OnixClient.CONFIG.clickSounds.isOn(); }
			public void set(boolean v) { OnixClient.CONFIG.clickSounds.set(v); }
		};
		public final Choice menuDesign = new Choice();
		public final Key menuKey = new Key();
		public final Speed animationSpeed = new Speed();
	}

	public abstract static class Flag {
		public abstract boolean on();

		public abstract void set(boolean value);
	}

	public static final class Choice {
		public String get() {
			return OnixClient.CONFIG.menuDesign.get();
		}

		public void set(String id) {
			OnixClient.CONFIG.menuDesign.set(id);
		}
	}

	public static final class Speed {
		public float f() {
			return OnixClient.CONFIG.menuAnimations.isOn() ? 1f : 4f;
		}
	}

	/** The Onix menu key (Minecraft controls). */
	public static final class Key {
		public int key() {
			return OnixClient.MENU_KEY == null ? -1 : KeyBindingHelper.getBoundKeyOf(OnixClient.MENU_KEY).getValue();
		}

		public String display() {
			return OnixClient.MENU_KEY == null ? "-" : OnixClient.MENU_KEY.getTranslatedKeyMessage().getString();
		}
	}
}
