package dev.onix.client.module.impl.utility;

import dev.onix.client.config.KeybindProfiles;
import dev.onix.client.gui.Notifications;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.KeybindSetting;
import dev.onix.client.setting.TextSetting;

import java.util.List;

/**
 * Save whole key binding setups – PvP, building, redstone, elytra – and switch between them with
 * a click or a key. The profile list itself is on the module's page in the Onix menu.
 */
public class KeybindProfilesModule extends Module {
    private final KeybindSetting nextKey = add(new KeybindSetting("nextKey", "Next Profile Key", KeybindSetting.NONE)
            .describe("Cycles through your keybind profiles in alphabetical order."));
    private final KeybindSetting previousKey = add(new KeybindSetting("previousKey", "Previous Profile Key", KeybindSetting.NONE));
    private final BoolSetting followProfiles = add(new BoolSetting("followProfiles", "Follow Onix Profiles", true)
            .describe("When an Onix profile loads (also CWT mode or a server profile), the keybind profile with the same name is applied."));
    private final BoolSetting notify = add(new BoolSetting("notify", "Notify on Switch", true));

    /** Name typed into the "Save current keys" field on the module page. */
    public final TextSetting newName = add(new TextSetting("newName", "New Profile Name", "", 24).hidden());

    public KeybindProfilesModule() {
        super("keybindprofiles", "Keybind Profiles", "Save your key bindings as profiles – PvP, building, elytra – and switch with one key.",
                Category.UTILITY, false);
        tags("hotkey profiles", "multiple hotkeys", "controls", "keybinds", "key layout");
    }

    @Override
    protected void onEnable() {
        // start with the current keys, so there is always something to come back to
        if (KeybindProfiles.list().isEmpty()) KeybindProfiles.save("Default");
    }

    @Override
    public void onTick() {
        if (nextKey.poll()) cycle(1);
        if (previousKey.poll()) cycle(-1);
    }

    private void cycle(int direction) {
        List<String> names = KeybindProfiles.list();
        if (names.isEmpty()) {
            Notifications.show("No keybind profiles", "Save one on the Keybind Profiles page first");
            return;
        }
        int i = names.indexOf(KeybindProfiles.active());
        String next = names.get(Math.floorMod(i + direction, names.size()));
        apply(next);
    }

    /** Applies a profile and tells you how many keys changed. */
    public void apply(String name) {
        int changed = KeybindProfiles.apply(name);
        if (changed < 0) {
            Notifications.warn("Keybind profile missing", name);
        } else if (notify.isOn()) {
            Notifications.show("Keys: " + name, changed == 0 ? "Already in use" : changed + (changed == 1 ? " key changed" : " keys changed"));
        }
    }

    /** Called after an Onix profile was loaded. */
    public void onOnixProfile(String profile) {
        // at game start the keys are already loaded from options.txt
        if (mc() == null || mc().options == null || mc().getSoundManager() == null) return;
        if (isEnabled() && followProfiles.isOn() && KeybindProfiles.exists(profile) && !profile.equals(KeybindProfiles.active())) {
            apply(profile);
        }
    }

    @Override
    public String statusLine() {
        String active = KeybindProfiles.active();
        int n = KeybindProfiles.list().size();
        if (n == 0) return "No profiles yet";
        return (active.isEmpty() ? "None applied" : "Active: " + active) + " · " + n + (n == 1 ? " profile" : " profiles");
    }
}
