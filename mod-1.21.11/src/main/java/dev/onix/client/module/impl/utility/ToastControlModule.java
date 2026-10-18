package dev.onix.client.module.impl.utility;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.ModeSetting;
import net.minecraft.client.gui.components.toasts.Toast;

/** From Carbon: hides the pop-up toasts in the top right. */
public class ToastControlModule extends Module {
    private final ModeSetting mode = add(new ModeSetting("hide", "Hide", "All", "Recipes & Tutorials", "Advancements"));

    public ToastControlModule() {
        super("toastcontrol", "Toast Control", "Hides notification toasts like new recipes (from Carbon).", Category.UTILITY, false);
        tags("toast", "recipe", "advancement", "notification", "carbon");
    }

    public boolean block(Toast toast) {
        if (!isEnabled()) return false;
        String cls = toast.getClass().getSimpleName();
        return switch (mode.get()) {
            case "Recipes & Tutorials" -> cls.contains("Recipe") || cls.contains("Tutorial");
            case "Advancements" -> cls.contains("Advancement");
            default -> true;
        };
    }
}
