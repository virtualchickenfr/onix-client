package gg.carbon.client.module;

import com.mojang.blaze3d.platform.InputConstants;
import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.setting.KeySetting;
import gg.carbon.client.setting.ModeSetting;
import gg.carbon.client.setting.NumberSetting;
import gg.carbon.client.setting.Setting;
import java.util.List;

/** Global Carbon preferences, shown on the "Settings" and "Design" pages of the mod menu. */
public class ClientSettings extends Module {
	public final KeySetting menuKey = add(new KeySetting("Mod Menu Key", "Opens the Carbon mod menu", InputConstants.KEY_RSHIFT));
	public final KeySetting hudKey = add(new KeySetting("HUD Editor Key", "Opens the HUD editor directly", 0));
	public final BoolSetting customFont = add(new BoolSetting("Custom Font", "Use the Inter typeface across Carbon's interface", true));
	public final BoolSetting mainMenu = add(new BoolSetting("Carbon Main Menu", "Replace the title screen with Carbon's", true));
	public final ModeSetting menuDesign = add(new ModeSetting("Menu Design",
		"Look of the main menu", "Carbon", "Carbon", "Carbon Client", "Voxel", "Portal", "Classic", "Vanilla"));
	public final BoolSetting menuLogo = add(new BoolSetting("Carbon Client Logo", "Show the CARBON CLIENT artwork instead of the wordmark", false));
	public final BoolSetting menuSkin = add(new BoolSetting("Character Render", "Show the character artwork on the main menu", false));
	public final BoolSetting menuLight = add(new BoolSetting("Light Mode", "White mode for Carbon's menus and screens", false));
	public final BoolSetting menuBackgrounds = add(new BoolSetting("Menu Backgrounds",
		"Carbon backdrop behind singleplayer, multiplayer and options", true));
	public final BoolSetting buttons = add(new BoolSetting("Restyle Buttons", "Draw all vanilla buttons in Carbon style", true));
	public final BoolSetting blur = add(new BoolSetting("Menu Blur", "Blur the game behind Carbon menus", true));
	public final NumberSetting animationSpeed = add(new NumberSetting("Animation Speed", "Speed multiplier for interface animations", 1, 0.5, 2, 0.1).suffix("x"));
	public final BoolSetting clickSounds = add(new BoolSetting("Click Sounds", "Play a sound when clicking interface elements", true));
	public final BoolSetting hudInDebug = add(new BoolSetting("HUD With Debug Screen", "Keep the HUD visible while F3 is open", false));

	public ClientSettings() {
		super("client", "Client", "Carbon client preferences", Category.UTILITY);
		setEnabled(true);
	}

	/** Everything about the look: shown on the mod menu's "Design" page. */
	public List<Setting<?>> designSettings() {
		return List.of(menuDesign, menuLogo, menuSkin, menuLight, menuBackgrounds, mainMenu, buttons, customFont, blur, animationSpeed);
	}

	/** The remaining preferences: shown on the "Settings" page. */
	public List<Setting<?>> generalSettings() {
		List<Setting<?>> design = designSettings();
		return settings().stream().filter(s -> !design.contains(s)).toList();
	}

	@Override
	public void onTick() {
		gg.carbon.client.ui.Fonts.enabled = customFont.on();
	}
}
