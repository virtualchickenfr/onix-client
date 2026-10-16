package dev.onix.client.config;

import dev.onix.client.OnixClient;
import dev.onix.client.gui.Notifications;
import dev.onix.client.module.Module;

import java.util.Set;

/**
 * CWT mode: one click switches to a clanwar setup and back.
 *
 * <p>The setup is an ordinary profile called "CWT". The first time you turn the mode on it is
 * created from your current profile with distracting visuals and every module marked with a
 * server-rule note off, and the usual PvP HUD on;
 * after that it is yours – change modules while CWT mode is on and they stay in the CWT profile.
 * Turning the mode off returns to the profile you came from.
 */
public final class CwtMode {
    public static final String PROFILE = "CWT";

    /** Visual extras that distract in a fight or that tournaments often do not allow. */
    private static final Set<String> PRESET_OFF = Set.of(
            "customsky", "atmosphere", "trail", "weather", "timechanger", "fullbright",
            "chunkborders", "hitboxes", "freelook", "autotool", "killeffects");
    /** The usual competitive HUD. */
    private static final Set<String> PRESET_ON = Set.of("fps", "ping", "cps", "keystrokes", "armor", "effects");

    private CwtMode() {}

    public static boolean isOn() {
        return PROFILE.equals(OnixClient.PROFILES.active());
    }

    public static void toggle() {
        if (isOn()) disable();
        else enable();
    }

    public static void enable() {
        if (isOn()) return;
        ProfileManager profiles = OnixClient.PROFILES;
        OnixClient.CONFIG.cwtReturnProfile = profiles.active();
        if (profiles.list().contains(PROFILE)) {
            profiles.switchTo(PROFILE);
            Notifications.show("CWT mode on", "Loaded your CWT profile");
        } else {
            profiles.create(PROFILE);
            int changed = 0;
            for (Module m : OnixClient.MODULES.all()) {
                boolean want = PRESET_ON.contains(m.getId())
                        || (m.isEnabled() && !PRESET_OFF.contains(m.getId()) && !m.isRestricted());
                if (want != m.isEnabled()) {
                    m.setEnabled(want);
                    changed++;
                }
            }
            profiles.saveActive();
            Notifications.show("CWT mode on", "Created the CWT profile · " + changed + " modules changed");
        }
        OnixClient.CONFIG.save();
    }

    public static void disable() {
        if (!isOn()) return;
        ProfileManager profiles = OnixClient.PROFILES;
        String back = OnixClient.CONFIG.cwtReturnProfile;
        if (back == null || back.isEmpty() || back.equals(PROFILE) || !profiles.list().contains(back)) back = ProfileManager.DEFAULT;
        profiles.switchTo(back);
        OnixClient.CONFIG.cwtReturnProfile = "";
        OnixClient.CONFIG.save();
        Notifications.show("CWT mode off", "Back to \"" + back + "\"");
    }
}
