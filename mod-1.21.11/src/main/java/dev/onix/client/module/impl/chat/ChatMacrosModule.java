package dev.onix.client.module.impl.chat;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.KeybindSetting;
import dev.onix.client.setting.TextSetting;

/** Send a chat message or command with a single key. */
public class ChatMacrosModule extends Module {
    private static final int SLOTS = 5;
    private final KeybindSetting[] keys = new KeybindSetting[SLOTS];
    private final TextSetting[] messages = new TextSetting[SLOTS];

    public ChatMacrosModule() {
        super("macros", "Chat Macros", "Bind messages and commands to keys.", Category.CHAT, false);
        restricted("Sending messages or commands with one key can break spam or automation rules.");
        tags("binds", "commands", "hotkeys");
        String[] examples = {"/spawn", "gg", "/party warp", "", ""};
        for (int i = 0; i < SLOTS; i++) {
            group("Macro " + (i + 1));
            keys[i] = add(new KeybindSetting("key" + i, "Key", KeybindSetting.NONE));
            messages[i] = add(new TextSetting("message" + i, "Message or Command", examples[i], 256).placeholder("/command or text"));
        }
        group(GROUP_GENERAL);
    }

    @Override
    public void onTick() {
        if (mc().getConnection() == null) return;
        for (int i = 0; i < SLOTS; i++) {
            if (!keys[i].poll()) continue;
            String msg = messages[i].get().trim();
            if (msg.isEmpty()) continue;
            if (msg.startsWith("/")) mc().getConnection().sendCommand(msg.substring(1));
            else mc().getConnection().sendChat(msg);
        }
    }
}
