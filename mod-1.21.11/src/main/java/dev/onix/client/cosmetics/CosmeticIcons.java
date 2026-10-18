package dev.onix.client.cosmetics;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/**
 * 3D cosmetic icons in menus. Since 1.21.6 menus cannot draw 3D models directly; they are rendered into a small
 * texture ("picture in picture") by {@link Renderer}, registered in OnixClient.
 */
public final class CosmeticIcons {
    private CosmeticIcons() {}

    public record State(CosmeticCatalog.Item item, boolean cape, float time, float extraYaw, int w, int h,
                        int x0, int y0, int x1, int y1, Matrix3x2f pose, @Nullable ScreenRectangle scissorArea,
                        @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
        @Override
        public float scale() {
            return 1f;
        }
    }

    public static void submit(GuiGraphics ctx, CosmeticCatalog.Item item, boolean cape, int x, int y, int w, int h, float t, float extraYaw) {
        if (w <= 0 || h <= 0) return;
        Matrix3x2f pose = new Matrix3x2f(ctx.pose());
        ScreenRectangle scissor = ctx.scissorStack.peek();
        ScreenRectangle area = new ScreenRectangle(x, y, w, h).transformMaxBounds(pose);
        if (scissor != null) area = scissor.intersection(area);
        if (area == null) return;
        ctx.guiRenderState.submitPicturesInPictureState(new State(item, cape, t, extraYaw, w, h, x, y, x + w, y + h, pose, scissor, area));
    }

    public static final class Renderer extends PictureInPictureRenderer<State> {
        public Renderer(MultiBufferSource.BufferSource buffers) {
            super(buffers);
        }

        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        @Override
        protected void renderToTexture(State state, PoseStack poseStack) {
            Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_FLAT);
            if (state.cape()) CapeRenderer.renderIcon(poseStack, bufferSource, state.item(), state.w(), state.h(), state.time(), state.extraYaw());
            else CosmeticModels.renderIcon(poseStack, bufferSource, state.item(), state.w(), state.h(), state.time(), state.extraYaw());
        }

        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height / 2f;
        }

        @Override
        protected String getTextureLabel() {
            return "onix cosmetic icon";
        }
    }
}
