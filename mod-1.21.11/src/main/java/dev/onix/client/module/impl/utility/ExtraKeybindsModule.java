package dev.onix.client.module.impl.utility;

import com.mojang.blaze3d.platform.InputConstants;
import dev.onix.client.mixin.KeyBindingAccessor;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.KeybindSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Up to two extra keys or mouse buttons for every vanilla action – jump on Space and Mouse 5,
 * drop on Q and R, hotbar slots on side buttons. The extra key behaves exactly like the vanilla one.
 */
public class ExtraKeybindsModule extends Module {
    private record Action(String id, String name, Function<Options, KeyMapping> binding, KeybindSetting first, KeybindSetting second) {}

    private final List<Action> actions = new ArrayList<>();

    public ExtraKeybindsModule() {
        super("extrakeys", "Extra Keybinds", "Give any action a second and third key – mouse side buttons included.",
                Category.UTILITY, false);
        tags("multiple hotkeys", "keybinds", "second key", "mouse buttons", "rebind", "hotkeys");

        group("Movement");
        action("jump", "Jump", o -> o.keyJump);
        action("sneak", "Sneak", o -> o.keyShift);
        action("sprint", "Sprint", o -> o.keySprint);
        action("forward", "Walk Forward", o -> o.keyUp);
        action("back", "Walk Backward", o -> o.keyDown);
        action("left", "Strafe Left", o -> o.keyLeft);
        action("right", "Strafe Right", o -> o.keyRight);

        group("Combat & Items");
        action("attack", "Attack / Destroy", o -> o.keyAttack);
        action("use", "Use Item / Place", o -> o.keyUse);
        action("pick", "Pick Block", o -> o.keyPickItem);
        action("drop", "Drop Item", o -> o.keyDrop);
        action("swap", "Swap Hands", o -> o.keySwapOffhand);
        action("inventory", "Inventory", o -> o.keyInventory);

        group("Hotbar");
        for (int i = 0; i < 9; i++) {
            int slot = i;
            action("hotbar" + (i + 1), "Slot " + (i + 1), o -> o.keyHotbarSlots[slot]);
        }

        group("Interface");
        action("chat", "Open Chat", o -> o.keyChat);
        action("command", "Open Command", o -> o.keyCommand);
        action("playerlist", "Player List", o -> o.keyPlayerList);
        action("perspective", "Toggle Perspective", o -> o.keyTogglePerspective);
    }

    private void action(String id, String name, Function<Options, KeyMapping> binding) {
        KeybindSetting first = add(new KeybindSetting(id + "1", name, KeybindSetting.NONE));
        KeybindSetting second = add(new KeybindSetting(id + "2", name + " (2nd extra)", KeybindSetting.NONE)
                .visibleIf(first::isBound));
        actions.add(new Action(id, name, binding, first, second));
    }

    /** Called for every key and mouse button event (see KeyboardMixin, MouseMixin). */
    public void onInput(int code, int glfwAction) {
        if (glfwAction == GLFW.GLFW_REPEAT) return;
        Minecraft mc = mc();
        if (mc.options == null || mc.getWindow() == null) return;
        com.mojang.blaze3d.platform.Window window = mc.getWindow();
        for (Action a : actions) {
            if (!a.first().matches(code) && !a.second().matches(code)) continue;
            KeyMapping kb = a.binding().apply(mc.options);
            InputConstants.Key own = KeyBindingHelper.getBoundKeyOf(kb);
            if (own.equals(KeybindSetting.toKey(code))) continue; // vanilla handles its own key
            if (glfwAction == GLFW.GLFW_PRESS) {
                if (mc.screen != null) continue; // like vanilla: keys do nothing while a menu is open
                kb.setDown(true);
                KeyBindingAccessor acc = (KeyBindingAccessor) kb;
                acc.onix$setTimesPressed(acc.onix$getTimesPressed() + 1);
            } else if (glfwAction == GLFW.GLFW_RELEASE) {
                // stay pressed while the vanilla key or the other extra key is still held
                int other = a.first().matches(code) ? a.second().get() : a.first().get();
                kb.setDown(isDown(window, own) || KeybindSetting.isPhysicallyDown(window, other));
            }
        }
    }

    private static boolean isDown(com.mojang.blaze3d.platform.Window window, InputConstants.Key key) {
        if (key == null || key.equals(InputConstants.UNKNOWN)) return false;
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window.handle(), key.getValue()) == GLFW.GLFW_PRESS;
        if (key.getType() == InputConstants.Type.KEYSYM) return InputConstants.isKeyDown(window, key.getValue());
        return false;
    }

    @Override
    protected void onDisable() {
        // release anything still held through an extra key
        Minecraft mc = mc();
        if (mc.options == null || mc.getWindow() == null) return;
        com.mojang.blaze3d.platform.Window window = mc.getWindow();
        for (Action a : actions) {
            KeyMapping kb = a.binding().apply(mc.options);
            kb.setDown(isDown(window, KeyBindingHelper.getBoundKeyOf(kb)));
        }
    }

    /** Extra keys that are also a vanilla key of another action – both would fire. */
    private List<String> conflicts() {
        List<String> out = new ArrayList<>();
        Minecraft mc = mc();
        if (mc.options == null) return out;
        for (Action a : actions) {
            for (KeybindSetting extra : new KeybindSetting[]{a.first(), a.second()}) {
                if (!extra.isBound()) continue;
                InputConstants.Key key = KeybindSetting.toKey(extra.get());
                KeyMapping own = a.binding().apply(mc.options);
                for (KeyMapping kb : mc.options.keyMappings) {
                    if (kb != own && KeyBindingHelper.getBoundKeyOf(kb).equals(key)) {
                        out.add(extra.display() + " (" + a.name() + " / " + net.minecraft.network.chat.Component.translatable(kb.getName()).getString() + ")");
                    }
                }
            }
        }
        return out;
    }

    @Override
    public String statusLine() {
        int bound = 0;
        for (Action a : actions) {
            if (a.first().isBound()) bound++;
            if (a.second().isBound()) bound++;
        }
        if (bound == 0) return "No extra keys yet";
        List<String> clash = conflicts();
        String base = bound + (bound == 1 ? " extra key" : " extra keys");
        return clash.isEmpty() ? base : base + " · also used by Minecraft: " + clash.get(0) + (clash.size() > 1 ? " +" + (clash.size() - 1) : "");
    }
}
