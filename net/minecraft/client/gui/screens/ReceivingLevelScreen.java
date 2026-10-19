package net.minecraft.client.gui.screens;

import java.util.function.BooleanSupplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ReceivingLevelScreen extends Screen {
	private static final Component DOWNLOADING_TERRAIN_TEXT = Component.translatable("multiplayer.downloadingTerrain");
	private static final long CHUNK_LOADING_START_WAIT_LIMIT_MS = 30000L;
	private final long createdAt;
	private final BooleanSupplier levelReceived;
	private final ReceivingLevelScreen.Reason reason;
	@Nullable
	private TextureAtlasSprite cachedNetherPortalSprite;

	public ReceivingLevelScreen(BooleanSupplier booleanSupplier, ReceivingLevelScreen.Reason reason) {
		super(GameNarrator.NO_TITLE);
		this.levelReceived = booleanSupplier;
		this.reason = reason;
		this.createdAt = Util.getMillis();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	protected boolean shouldNarrateNavigation() {
		return false;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		guiGraphics.drawCenteredString(this.font, DOWNLOADING_TERRAIN_TEXT, this.width / 2, this.height / 2 - 50, -1);
	}

	@Override
	public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
		switch (this.reason) {
			case NETHER_PORTAL:
				guiGraphics.blitSprite(RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND, this.getNetherPortalSprite(), 0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight());
				break;
			case END_PORTAL:
				TextureManager textureManager = Minecraft.getInstance().getTextureManager();
				TextureSetup textureSetup = TextureSetup.doubleTexture(
					textureManager.getTexture(TheEndPortalRenderer.END_SKY_LOCATION).getTextureView(),
					textureManager.getTexture(TheEndPortalRenderer.END_PORTAL_LOCATION).getTextureView()
				);
				guiGraphics.fill(RenderPipelines.END_PORTAL, textureSetup, 0, 0, this.width, this.height);
				break;
			case OTHER:
				this.renderPanorama(guiGraphics, f);
				this.renderBlurredBackground(guiGraphics);
				this.renderMenuBackground(guiGraphics);
		}
	}

	private TextureAtlasSprite getNetherPortalSprite() {
		if (this.cachedNetherPortalSprite != null) {
			return this.cachedNetherPortalSprite;
		}

		this.cachedNetherPortalSprite = this.minecraft.getBlockRenderer().getBlockModelShaper().getParticleIcon(Blocks.NETHER_PORTAL.defaultBlockState());
		return this.cachedNetherPortalSprite;
	}

	@Override
	public void tick() {
		if (this.levelReceived.getAsBoolean() || Util.getMillis() > this.createdAt + 30000L) {
			this.onClose();
		}
	}

	@Override
	public void onClose() {
		this.minecraft.getNarrator().saySystemNow(Component.translatable("narrator.ready_to_play"));
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Environment(EnvType.CLIENT)
	public enum Reason {
		NETHER_PORTAL,
		END_PORTAL,
		OTHER;
	}
}
