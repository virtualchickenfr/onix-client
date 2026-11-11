package dev.onix.client.module.impl.chat;

import dev.onix.client.OnixClient;
import dev.onix.client.gui.Notifications;
import dev.onix.client.mixin.ChatComponentAccessor;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import org.lwjgl.glfw.GLFW;

/** Ctrl + click on a chat message (with the chat open) copies the whole message, without colour codes. */
public class ChatCopyModule extends Module {
    private final BoolSetting notify = add(new BoolSetting("notify", "Show 'Copied'", true));

    public ChatCopyModule() {
        super("chatcopy", "Chat Copy", "Ctrl + click a chat message to copy it.", Category.CHAT, true);
        tags("copy", "clipboard", "chat");
    }

    /** Called when the chat screen is clicked; true when a message was copied (the click is used up). */
    public static boolean tryCopy() {
        if (OnixClient.MODULES == null) return false;
        ChatCopyModule m = OnixClient.MODULES.get(ChatCopyModule.class);
        if (m == null || !m.isEnabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().handle();
        boolean ctrl = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS;
        if (!ctrl) return false;
        String text = m.messageAt(mc);
        if (text == null || text.isBlank()) return false;
        mc.keyboardHandler.setClipboard(text);
        if (m.notify.isOn()) Notifications.show("Copied", text.length() > 60 ? text.substring(0, 57) + "…" : text);
        return true;
    }

    /** The whole chat message under the mouse, or null. Mirrors ChatComponent's layout (lines from the bottom up). */
    private String messageAt(Minecraft mc) {
        ChatComponent chat = mc.gui.getChat();
        List<GuiMessage.Line> lines = ((ChatComponentAccessor) chat).onix$lines();
        if (lines.isEmpty()) return null;
        var window = mc.getWindow();
        double mx = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / Math.max(1, window.getScreenWidth());
        double my = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / Math.max(1, window.getScreenHeight());
        double scale = mc.options.chatScale().get();
        int width = ChatComponent.getWidth(mc.options.chatWidth().get());
        if (mx < 0 || mx > width + 12) return null;
        int bottom = (int) Math.floor((window.getGuiScaledHeight() - 40) / scale);
        int lineHeight = (int) (9 * (mc.options.chatLineSpacing().get() + 1.0));
        double y = my / scale;
        if (y > bottom) return null;
        int row = (int) Math.floor((bottom - y) / lineHeight);
        if (row < 0 || row >= chat.getLinesPerPage()) return null;
        int index = row + ((ChatComponentAccessor) chat).onix$scroll();
        if (index >= lines.size()) return null;
        // a message is the lines from its last line (endOfEntry, lower index) up to the next message's last line
        int end = index;
        while (end > 0 && !lines.get(end).endOfEntry()) end--;
        int start = end + 1;
        while (start < lines.size() && !lines.get(start).endOfEntry()) start++;
        StringBuilder out = new StringBuilder();
        for (int i = start - 1; i >= end; i--) {
            lines.get(i).content().accept((pos, style, codePoint) -> {
                out.appendCodePoint(codePoint);
                return true;
            });
        }
        return out.toString().replaceAll("§.", "").trim();
    }
}
