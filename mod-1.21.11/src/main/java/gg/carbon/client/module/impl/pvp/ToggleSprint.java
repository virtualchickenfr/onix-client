package gg.carbon.client.module.impl.pvp;

import gg.carbon.client.module.Category;
import gg.carbon.client.module.TextHudModule;
import gg.carbon.client.setting.BoolSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

public class ToggleSprint extends TextHudModule {
	private final BoolSetting sprint = add(new BoolSetting("Toggle Sprint", "Pressing sprint toggles it on and off", true));
	private final BoolSetting sneak = add(new BoolSetting("Toggle Sneak", "Pressing sneak toggles it on and off", false));
	private final BoolSetting hud = add(new BoolSetting("Show Status", "Display the current state on screen", true));

	private boolean sprinting;
	private boolean sneaking;

	public ToggleSprint() {
		super("toggleSprint", "Toggle Sprint", "Toggle your sprint and sneak keys", Category.PVP, 0.005f, 0.96f);
	}

	@Override
	public boolean shouldRender() {
		return hud.on() && !value().isEmpty();
	}

	@Override
	protected String label() {
		return "";
	}

	private boolean preview;

	@Override
	public void render(net.minecraft.client.gui.GuiGraphics g, boolean editor) {
		preview = editor;
		super.render(g, editor);
		preview = false;
	}

	@Override
	protected String value() {
		if (mc.player == null) return "Sprinting (Toggled)";
		if (preview && status().isEmpty()) return "Sprinting (Toggled)";
		return status();
	}

	private String status() {
		if (sneaking && sneak.on()) return "Sneaking (Toggled)";
		if (mc.player.isShiftKeyDown()) return "Sneaking";
		if (sprinting && sprint.on()) return "Sprinting (Toggled)";
		if (mc.player.isSprinting()) return "Sprinting";
		return "";
	}

	@Override
	public float width() {
		boolean was = preview;
		preview = true;
		float w = super.width();
		preview = was;
		return w;
	}

	@Override
	public boolean onKey(int key, int action) {
		if (action != 1) return false;
		if (sprint.on() && key == KeyBindingHelper.getBoundKeyOf(mc.options.keySprint).getValue()) sprinting = !sprinting;
		if (sneak.on() && key == KeyBindingHelper.getBoundKeyOf(mc.options.keyShift).getValue()) sneaking = !sneaking;
		return false;
	}

	/** Called at the start of every client tick, before player input is read. */
	public void applyKeys() {
		if (mc.player == null || mc.screen != null) return;
		if (sprint.on() && sprinting && !mc.player.isShiftKeyDown()) mc.options.keySprint.setDown(true);
		if (sneak.on() && sneaking) mc.options.keyShift.setDown(true);
	}

	@Override
	protected void onDisable() {
		if (sprinting) mc.options.keySprint.setDown(false);
		if (sneaking) mc.options.keyShift.setDown(false);
		sprinting = false;
		sneaking = false;
	}
}
