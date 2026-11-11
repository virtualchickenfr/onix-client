package dev.onix.client.gui;

import dev.onix.client.mixin.PlayerSkinWidgetAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.model.player.PlayerModel;

/**
 * The world loading transition, filmed like a shot (the same scene as the launcher's Play transition): the camera
 * follows your own character falling through an evening sky (cloud layers, wind), the sea comes up, a short slow
 * motion as it breaks the surface (flash, foam, a crown of droplets, shock rings), then the camera dives after it:
 * god rays, drifting specks, bubbles, the figure sinking into the dark while the world loads. Clean text at the bottom.
 */
public final class FallTransition {
    private FallTransition() {}

    private static final float IMPACT = 1.25f;

    private static PlayerSkinWidget figure;
    private static long start;
    private static Object owner;

    private static float clamp(float x) {
        return Math.max(0f, Math.min(1f, x));
    }

    private static float ease(float x) {
        x = clamp(x);
        return 1f - (1f - x) * (1f - x) * (1f - x);
    }

    private static float rnd(float seed) {
        double x = Math.sin(seed * 127.1 + 311.7) * 43758.5453;
        return (float) (x - Math.floor(x));
    }

    private static int a(int rgb, float alpha) {
        return Math.round(clamp(alpha) * 255) << 24 | (rgb & 0xFFFFFF);
    }

    /** Scene time: a short slow motion around the moment the figure breaks the surface. */
    private static float slowMotion(float real) {
        float a = IMPACT - 0.12f, len = 0.75f, rate = 0.35f;
        if (real < a) return real;
        if (real < a + len) return a + (real - a) * rate;
        return a + len * rate + (real - a - len);
    }

    public static void render(Object screen, GuiGraphics ctx, String title, String detail, float progress) {
        long now = System.currentTimeMillis();
        if (owner != screen) {
            if (owner == null || now - start > 20_000) start = now;
            owner = screen;
        }
        float real = (now - start) / 1000f;
        float t = slowMotion(real);
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        float u = h / 100f;

        // the shot: world y grows downwards, the camera keeps the figure a bit above the middle
        float surfaceW = 150 * u, startW = -20 * u;
        float accel = 2 * (surfaceW - startW) / (IMPACT * IMPACT);
        float vImpact = accel * IMPACT;
        float figY = t < IMPACT ? startW + 0.5f * accel * t * t
                : surfaceW + (vImpact / 3.2f) * (1 - (float) Math.exp(-3.2f * (t - IMPACT))) + 5 * u * (t - IMPACT);
        float camY = figY - 42 * u;
        float surfaceY = surfaceW - camY;
        float under = clamp((t - IMPACT) / 0.35f);
        float depth = clamp((figY - surfaceW) / (60 * u));
        float speed = t < IMPACT ? accel * t : vImpact * (float) Math.exp(-3.2f * (t - IMPACT));
        float shake = (t > IMPACT ? clamp(1 - (t - IMPACT) / 0.35f) : 0) * 1.2f * u;

        ctx.pose().pushMatrix();
        ctx.pose().translate((float) Math.sin(real * 70) * shake, (float) Math.cos(real * 57) * shake);

        // ---------------------------------------------------------------- sky, sun and clouds
        int sy = Math.round(surfaceY);
        if (sy > 0) {
            int skyTop = Math.round(surfaceY - 160 * u);
            ctx.fillGradient(-20, Math.min(skyTop, 0) - 20, w + 20, Math.round(surfaceY - 70 * u), 0xFF0D1230, 0xFF3B3A72);
            ctx.fillGradient(-20, Math.round(surfaceY - 70 * u), w + 20, Math.round(surfaceY - 22 * u), 0xFF3B3A72, 0xFFB06A7F);
            ctx.fillGradient(-20, Math.round(surfaceY - 22 * u), w + 20, sy, 0xFFB06A7F, 0xFFFFB27A);
            SoftShapes.glow(ctx, w / 2f, surfaceY, w * 0.55f, 40 * u, 0x88FFD6A0);
            for (int i = 0; i < 22; i++) {
                float z = 0.35f + (i % 3) * 0.32f;
                float wy = surfaceW - (12 + rnd(i) * 150) * u;
                float y = wy - camY * z - startW * (1 - z);
                float x = rnd(i + 9) * w;
                float r = (10 + rnd(i + 3) * 18) * u * z;
                if (y < -r || y > surfaceY) continue;
                float warm = clamp(1 - (surfaceY - y) / (90 * u));
                SoftShapes.glow(ctx, x, y, r * 1.8f, r * 0.55f, a(Theme.lerpColor(0xFFFFE6E6, 0xFFFFC8A0, warm), 0.18f + 0.12f * z));
            }
            float wind = clamp(speed / vImpact) * (1 - under);
            for (int i = 0; i < 36 && wind > 0; i++) {
                float x = rnd(i * 3.1f) * w;
                float len = (6 + rnd(i) * 16) * u * wind;
                float y = (((rnd(i * 7.7f) * h * 1.6f - camY * (1.4f + rnd(i) * 0.8f)) % (h * 1.6f)) + h * 1.6f) % (h * 1.6f) - 0.3f * h;
                if (y > surfaceY) continue;
                ctx.fill(Math.round(x), Math.round(y), Math.round(x) + 1, Math.round(y + len), a(0xFFFFFF, 0.09f * wind));
            }
        }

        // ---------------------------------------------------------------- the sea
        int top = Math.max(-20, sy);
        ctx.fillGradient(-20, top, w + 20, Math.round(surfaceY + 25 * u), 0xFF2E7FA8, 0xFF18557E);
        ctx.fillGradient(-20, Math.max(top, Math.round(surfaceY + 25 * u)), w + 20, Math.round(surfaceY + 80 * u), 0xFF18557E, 0xFF0A2747);
        ctx.fillGradient(-20, Math.max(top, Math.round(surfaceY + 80 * u)), w + 20, h + 20, 0xFF0A2747, 0xFF02060F);
        if (sy > -5 && sy < h + 5) {
            if (under < 0.5f) {
                // the sun's reflection: a column of glints, small waves
                for (int i = 0; i < 40; i++) {
                    float yy = surfaceY + 1 + (float) Math.pow(rnd(i), 1.6) * 18 * u;
                    float gw = (2 + rnd(i + 2) * 9) * u * (1 - (yy - surfaceY) / (22 * u));
                    float x = w / 2f + (rnd(i + 5) - 0.5f) * 14 * u + (float) Math.sin(t * 3 + i) * 1.5f * u;
                    ctx.fill(Math.round(x - gw / 2), Math.round(yy), Math.round(x + gw / 2), Math.round(yy) + 1,
                            a(0xFFD6AA, 0.4f * (0.5f + 0.5f * (float) Math.sin(t * 6 + i * 2.3f))));
                }
                ctx.fill(0, sy, w, sy + 1, 0x88FFE2C0);
            } else {
                // from below: the bright, rippling underside of the surface
                ctx.fillGradient(0, sy - Math.round(2 * u), w, sy + Math.round(10 * u), a(0xB4E6FF, 0.3f), 0x00B4E6FF);
                for (int x = 0; x < w; x += 3) {
                    int yy = Math.round(surfaceY + (float) Math.sin(x / 22f + t * 3) * 0.9f * u + (float) Math.sin(x / 9f - t * 5) * 0.3f * u);
                    ctx.fill(x, yy, x + 3, yy + 1, 0x99DCF5FF);
                }
            }
        }

        // ---------------------------------------------------------------- under water: god rays and specks
        if (under > 0) {
            for (int i = 0; i < 9; i++) {
                float x0 = w * (0.05f + i * 0.11f) + (float) Math.sin(t * 0.6f + i * 1.7f) * 3 * u;
                float bw = (3 + rnd(i) * 5) * u;
                float len = (70 + rnd(i + 4) * 50) * u;
                float al = (0.07f + 0.05f * (float) Math.sin(t * 1.3f + i * 2.1f)) * under * (1 - depth * 0.5f);
                ctx.fillGradient(Math.round(x0), Math.max(0, sy), Math.round(x0 + bw), Math.round(surfaceY + len), a(0xAAE1FF, al), 0x00AAE1FF);
            }
            for (int i = 0; i < 50; i++) {
                float x = rnd(i) * w + (float) Math.sin(t * 0.5f + i) * 2 * u;
                float wy = surfaceW + rnd(i + 11) * 160 * u;
                float y = wy - camY * (0.7f + rnd(i + 2) * 0.5f);
                float yy = ((y - surfaceY) % (160 * u) + 160 * u) % (160 * u) + surfaceY;
                ctx.fill(Math.round(x), Math.round(yy), Math.round(x) + 1, Math.round(yy) + 1, a(0xC8EBFF, 0.3f * under * rnd(i + 6)));
            }
        }

        // ---------------------------------------------------------------- the character
        int fh = Math.round(Math.min(h * 0.42f, 190) * (1 - 0.18f * depth));
        int fw = Math.round(fh * 0.55f);
        float fx = w / 2f + (float) Math.sin(t * 0.9f) * 2 * u * under;
        float fy = figY - camY;
        drawFigure(Minecraft.getInstance(), ctx, Math.round(fx - fw / 2f), Math.round(fy - fh / 2f), fw, fh, t, under);
        // the deep tints it blue and swallows it slowly
        if (under > 0) SoftShapes.glow(ctx, fx, fy, fw * 0.9f, fh * 0.6f, a(0x0E4678, 0.2f * under + 0.45f * depth));

        // bubbles: rise, wobble, catch the light
        if (t > IMPACT) {
            for (int i = 0; i < 26; i++) {
                float born = IMPACT + rnd(i) * 1.8f;
                if (t < born) continue;
                float age = t - born;
                float r = (0.35f + rnd(i + 1) * 0.9f) * u;
                float x = fx + (rnd(i + 2) - 0.5f) * fh * 0.25f + (float) Math.sin(age * 5 + i) * 1.2f * u;
                float y = fy - fh * 0.25f - age * (14 + rnd(i + 3) * 18) * u;
                if (y < surfaceY + r) continue;
                ring(ctx, x, y, r, a(0xDCF5FF, 0.6f * (1 - depth * 0.4f)));
                ctx.fill(Math.round(x - r * 0.45f), Math.round(y - r * 0.5f), Math.round(x - r * 0.1f) + 1, Math.round(y - r * 0.15f) + 1, 0xB3FFFFFF);
            }
        }

        // ---------------------------------------------------------------- the splash
        float since = t - IMPACT;
        if (since > -0.02f && since < 1.4f) {
            float ssy = surfaceW - camY;
            SoftShapes.glow(ctx, w / 2f, ssy, (8 + since * 30) * u, (4 + since * 12) * u, a(0xFFFFFF, 0.75f * clamp(1 - since / 0.6f)));
            for (int i = 0; i < 4; i++) {
                float k = clamp((since - i * 0.09f) / 1.2f);
                if (k <= 0 || k >= 1) continue;
                ellipse(ctx, w / 2f, ssy, (4 + ease(k) * 46) * u, (0.8f + ease(k) * 7) * u, a(0xEBF8FF, 0.6f * (1 - k)));
            }
            for (int i = 0; i < 110 && since > 0; i++) {
                float ang = (float) Math.PI + 0.2f + rnd(i) * ((float) Math.PI - 0.4f);
                float sp = (50 + rnd(i + 1) * 85) * u;
                float vx = (float) Math.cos(ang) * sp * (0.6f + rnd(i + 2) * 0.6f);
                float vy = (float) Math.sin(ang) * sp;
                float gr = 120 * u;
                float x = w / 2f + vx * since;
                float y = ssy + vy * since + 0.5f * gr * since * since;
                if (y > ssy + 1) continue;
                float dyv = vy + gr * since;
                float l = (float) Math.hypot(vx, dyv);
                float len = (1.6f + rnd(i + 3) * 3.4f) * u;
                segment(ctx, x, y, x - vx / l * len, y - dyv / l * len, Math.max(1f, (0.25f + rnd(i + 4) * 0.35f) * u), a(0xF0FAFF, 0.85f * clamp(1 - since / 1.4f)));
            }
            ctx.fill(-20, -20, w + 20, h + 20, a(0xFFFFFF, 0.35f * clamp(1 - since / 0.25f)));
        }
        ctx.pose().popMatrix();

        // ---------------------------------------------------------------- depth, vignette, text
        SoftShapes.vignette(ctx, 0, 0, w, h, a(0x00040C, 0.5f + 0.4f * depth));
        int ty = h - 34;
        if (title != null) Theme.textCentered(ctx, title, w / 2, ty - 12, a(0xFFFFFF, 0.92f));
        if (detail != null) {
            String d = Theme.ellipsize(detail, w - 40);
            Theme.small(ctx, d, w / 2 - Theme.smallWidth(d) / 2, ty + 2, a(0xBFD4FF, 0.75f));
        }
        int bw = Math.min(160, w / 3);
        ctx.fill(w / 2 - bw / 2, ty + 14, w / 2 + bw / 2, ty + 15, 0x22FFFFFF);
        if (progress >= 0) {
            ctx.fill(w / 2 - bw / 2, ty + 14, w / 2 - bw / 2 + Math.round(bw * Math.min(1f, progress)), ty + 15, 0xCCBFD4FF);
        } else {
            float pp = (real * 0.6f) % 1f;
            int x0 = w / 2 - bw / 2 + Math.round((bw - bw / 4f) * pp);
            ctx.fill(x0, ty + 14, x0 + bw / 4, ty + 15, 0xAABFD4FF);
        }
    }

    private static void ellipse(GuiGraphics ctx, float cx, float cy, float rx, float ry, int color) {
        int steps = Math.max(28, Math.round(rx));
        for (int i = 0; i < steps; i++) {
            double ang = Math.PI * 2 * i / steps;
            int x = Math.round(cx + (float) Math.cos(ang) * rx);
            int y = Math.round(cy + (float) Math.sin(ang) * ry);
            ctx.fill(x, y, x + 2, y + 1, color);
        }
    }

    private static void ring(GuiGraphics ctx, float cx, float cy, float r, int color) {
        int steps = Math.max(10, Math.round(r * 4));
        for (int i = 0; i < steps; i++) {
            double ang = Math.PI * 2 * i / steps;
            int x = Math.round(cx + (float) Math.cos(ang) * r);
            int y = Math.round(cy + (float) Math.sin(ang) * r);
            ctx.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void segment(GuiGraphics ctx, float x0, float y0, float x1, float y1, float width, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x0, y0);
        ctx.pose().rotate((float) Math.atan2(dy, dx));
        int hw = Math.max(1, Math.round(width / 2f));
        ctx.fill(0, -hw, Math.round(len) + 1, hw, color);
        ctx.pose().popMatrix();
    }

    private static void drawFigure(Minecraft mc, GuiGraphics ctx, int x, int y, int w, int h, float t, float under) {
        try {
            if (figure == null) {
                figure = new PlayerSkinWidget(w, h, mc.getEntityModels(), mc.getSkinManager().createLookup(mc.getGameProfile(), false));
            }
            figure.setRectangle(w, h, x, y);
            var acc = (PlayerSkinWidgetAccessor) figure;
            boolean water = under > 0.5f;
            float flail = (float) Math.sin(t * (water ? 1.4f : 7f)) * (water ? 0.18f : 0.28f);
            for (PlayerModel model : new PlayerModel[]{acc.onix$wide(), acc.onix$slim()}) {
                model.rightArm.zRot = 2.55f + flail;
                model.leftArm.zRot = -2.55f - flail;
                model.rightArm.xRot = -0.2f;
                model.leftArm.xRot = -0.2f;
                model.rightLeg.zRot = 0.18f + flail * 0.6f;
                model.leftLeg.zRot = -0.18f - flail * 0.6f;
                model.rightLeg.xRot = 0.25f + flail;
                model.leftLeg.xRot = -0.15f - flail;
                model.head.xRot = water ? -0.15f : -0.4f;
            }
            float spin = t < IMPACT ? t * 95f : IMPACT * 95f + (t - IMPACT) * 28f;
            acc.onix$setRotationY(24f + spin);
            acc.onix$setRotationX(-14f + 22f * under + (float) Math.sin(t * 2.4f) * 4f);
            figure.render(ctx, 0, 0, 0f);
        } catch (RuntimeException e) {
            figure = null;
        }
    }
}
