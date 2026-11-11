package dev.onix.client.module.impl.visual;

import dev.onix.client.gui.SoftShapes;
import dev.onix.client.gui.Theme;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.KeybindSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Emotes: hold the wheel key, point at an emote and let go (or click) – your character plays it. Only you see it
 * (servers do not know emotes); the camera turns to the front while it plays and comes back afterwards.
 */
public class EmotesModule extends Module {
    public static final String[] EMOTES = {"Wave", "Clap", "Dance", "Salute", "Cheer", "Facepalm", "Point", "Bow"};
    private static final float[] LENGTH = {2.2f, 2.4f, 6f, 2.4f, 2.6f, 2.6f, 2.2f, 2.4f};

    private final KeybindSetting wheelKey = add(new KeybindSetting("wheelKey", "Wheel Key", GLFW.GLFW_KEY_B));
    private final BoolSetting thirdPerson = add(new BoolSetting("thirdPerson", "Show From the Front", true)
            .describe("Turns the camera to the front while an emote plays."));

    private int playing = -1;
    private long startedAt;
    private CameraType before;

    public EmotesModule() {
        super("emotes", "Emotes", "An emote wheel: wave, clap, dance, salute, cheer and more (only you see them).", Category.VISUAL, false);
        tags("emote", "wheel", "dance", "wave", "animation");
    }

    @Override
    public void onTick() {
        if (mc().player == null) return;
        if (wheelKey.poll() && mc().screen == null) mc().setScreen(new Wheel());
        if (playing >= 0) {
            float t = (System.currentTimeMillis() - startedAt) / 1000f;
            // moving or attacking stops it, like in other games
            boolean moved = mc().player.getDeltaMovement().horizontalDistanceSqr() > 0.004 || mc().options.keyAttack.isDown();
            if (t > LENGTH[playing] || (t > 0.4f && moved)) stop();
        }
    }

    /** Opens the wheel (also used by the dev test). */
    public void openWheel() {
        mc().setScreen(new Wheel());
    }

    public void play(int emote) {
        playing = emote;
        startedAt = System.currentTimeMillis();
        if (thirdPerson.isOn() && before == null) {
            before = mc().options.getCameraType();
            mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
    }

    private void stop() {
        playing = -1;
        if (before != null) {
            mc().options.setCameraType(before);
            before = null;
        }
    }

    @Override
    protected void onDisable() {
        stop();
    }

    /** Called after the player model got its pose: plays the current emote on your own character. */
    public void pose(PlayerModel m, int entityId) {
        if (playing < 0 || mc().player == null || entityId != mc().player.getId()) return;
        float t = (System.currentTimeMillis() - startedAt) / 1000f;
        float in = Math.min(1f, t / 0.2f);
        float s = (float) Math.sin(t * 9);
        switch (EMOTES[playing]) {
            case "Wave" -> {
                m.rightArm.xRot = lerp(m.rightArm.xRot, -2.9f, in);
                m.rightArm.zRot = lerp(m.rightArm.zRot, -0.3f + s * 0.35f, in);
                m.head.zRot = lerp(m.head.zRot, 0.12f, in);
            }
            case "Clap" -> {
                float c = Math.abs((float) Math.sin(t * 10)) * 0.35f;
                m.rightArm.xRot = lerp(m.rightArm.xRot, -1.3f, in);
                m.leftArm.xRot = lerp(m.leftArm.xRot, -1.3f, in);
                m.rightArm.yRot = lerp(m.rightArm.yRot, -0.55f + c, in);
                m.leftArm.yRot = lerp(m.leftArm.yRot, 0.55f - c, in);
            }
            case "Dance" -> {
                float beat = (float) Math.sin(t * 6);
                m.body.zRot = beat * 0.12f;
                m.head.zRot = -beat * 0.18f;
                m.head.xRot = Math.abs(beat) * 0.15f;
                m.rightArm.zRot = 0.4f + beat * 0.9f;
                m.leftArm.zRot = -0.4f + beat * 0.9f;
                m.rightArm.xRot = -0.6f - beat * 0.5f;
                m.leftArm.xRot = -0.6f + beat * 0.5f;
                m.rightLeg.xRot = beat * 0.35f;
                m.leftLeg.xRot = -beat * 0.35f;
            }
            case "Salute" -> {
                m.rightArm.xRot = lerp(m.rightArm.xRot, -2.25f, in);
                m.rightArm.yRot = lerp(m.rightArm.yRot, -0.6f, in);
                m.rightArm.zRot = lerp(m.rightArm.zRot, 0.35f, in);
                m.head.xRot = lerp(m.head.xRot, -0.08f, in);
            }
            case "Cheer" -> {
                float j = Math.abs((float) Math.sin(t * 5)) * 0.25f;
                m.rightArm.xRot = lerp(m.rightArm.xRot, -3.0f, in);
                m.leftArm.xRot = lerp(m.leftArm.xRot, -3.0f, in);
                m.rightArm.zRot = lerp(m.rightArm.zRot, -0.35f - j, in);
                m.leftArm.zRot = lerp(m.leftArm.zRot, 0.35f + j, in);
                m.head.xRot = lerp(m.head.xRot, -0.35f, in);
            }
            case "Facepalm" -> {
                m.rightArm.xRot = lerp(m.rightArm.xRot, -2.1f, in);
                m.rightArm.yRot = lerp(m.rightArm.yRot, -0.85f, in);
                m.head.xRot = lerp(m.head.xRot, 0.45f, in);
                m.head.yRot = lerp(m.head.yRot, s * 0.08f, in);
            }
            case "Point" -> {
                m.rightArm.xRot = lerp(m.rightArm.xRot, -1.57f, in);
                m.rightArm.yRot = lerp(m.rightArm.yRot, -0.1f, in);
                m.head.xRot = lerp(m.head.xRot, -0.1f, in);
            }
            default -> { // Bow
                float b = (float) Math.sin(Math.min(1f, t / 0.8f) * Math.PI) * 0.6f;
                m.body.xRot = b;
                m.head.xRot = b * 0.6f;
                m.rightArm.xRot = b * 0.4f;
                m.leftArm.xRot = b * 0.4f;
            }
        }
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /** The wheel: eight emotes around the cursor; point and release the key or click. */
    private final class Wheel extends Screen {
        private int hover = -1;
        private final boolean byKey = wheelKey.isDown();

        Wheel() {
            super(Component.literal("Emotes"));
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void renderBackground(GuiGraphics ctx, int mx, int my, float delta) {
            ctx.fill(0, 0, width, height, 0x55000000);
        }

        @Override
        public void render(GuiGraphics ctx, int mx, int my, float delta) {
            super.render(ctx, mx, my, delta);
            int cx = width / 2, cy = height / 2;
            int r = Math.min(width, height) / 3;
            double dx = mx - cx, dy = my - cy;
            hover = Math.hypot(dx, dy) < r * 0.28 ? -1 : (int) Math.floorMod(Math.round(Math.atan2(dy, dx) / (Math.PI * 2 / EMOTES.length) + 2), EMOTES.length);
            SoftShapes.glow(ctx, cx, cy, r * 1.3f, r * 1.3f, 0x40000000);
            for (int i = 0; i < EMOTES.length; i++) {
                double ang = (i - 2) * Math.PI * 2 / EMOTES.length;
                int x = cx + (int) (Math.cos(ang) * r * 0.72), y = cy + (int) (Math.sin(ang) * r * 0.72);
                boolean on = i == hover;
                int pw = Theme.width(EMOTES[i]) + 22;
                Theme.card(ctx, x - pw / 2, y - 11, x + pw / 2, y + 11, 11, on ? 0xE6FFFFFF : 0xB8181820, on ? 0xFFFFFFFF : 0x40FFFFFF);
                Theme.textCentered(ctx, EMOTES[i], x, y - 4, on ? 0xFF101014 : 0xFFFFFFFF);
            }
            Theme.textCentered(ctx, hover >= 0 ? EMOTES[hover] : "Emotes", cx, cy - 4, 0xCCFFFFFF);
            // letting go of the wheel key plays the emote under the cursor
            if (byKey && !wheelKey.isDown()) choose();
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            choose();
            return true;
        }

        private void choose() {
            int pick = hover;
            onClose();
            if (pick >= 0) play(pick);
        }
    }
}
