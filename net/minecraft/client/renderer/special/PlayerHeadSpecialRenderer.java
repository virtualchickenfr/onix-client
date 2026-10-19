package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class PlayerHeadSpecialRenderer implements SpecialModelRenderer<PlayerHeadSpecialRenderer.PlayerHeadRenderInfo> {
	private final Map<ResolvableProfile, PlayerHeadSpecialRenderer.PlayerHeadRenderInfo> updatedResolvableProfiles = new HashMap<>();
	private final SkinManager skinManager;
	private final SkullModelBase modelBase;
	private final PlayerHeadSpecialRenderer.PlayerHeadRenderInfo defaultPlayerHeadRenderInfo;

	PlayerHeadSpecialRenderer(SkinManager skinManager, SkullModelBase skullModelBase, PlayerHeadSpecialRenderer.PlayerHeadRenderInfo playerHeadRenderInfo) {
		this.skinManager = skinManager;
		this.modelBase = skullModelBase;
		this.defaultPlayerHeadRenderInfo = playerHeadRenderInfo;
	}

	public void render(
		@Nullable PlayerHeadSpecialRenderer.PlayerHeadRenderInfo playerHeadRenderInfo,
		ItemDisplayContext itemDisplayContext,
		PoseStack poseStack,
		MultiBufferSource multiBufferSource,
		int i,
		int j,
		boolean bl
	) {
		PlayerHeadSpecialRenderer.PlayerHeadRenderInfo playerHeadRenderInfo2 = Objects.requireNonNullElse(playerHeadRenderInfo, this.defaultPlayerHeadRenderInfo);
		RenderType renderType = playerHeadRenderInfo2.renderType();
		SkullBlockRenderer.renderSkull(null, 180.0F, 0.0F, poseStack, multiBufferSource, i, this.modelBase, renderType);
	}

	@Override
	public void getExtents(Set<Vector3f> set) {
		PoseStack poseStack = new PoseStack();
		poseStack.translate(0.5F, 0.0F, 0.5F);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		this.modelBase.root().getExtentsForGui(poseStack, set);
	}

	@Nullable
	public PlayerHeadSpecialRenderer.PlayerHeadRenderInfo extractArgument(ItemStack itemStack) {
		ResolvableProfile resolvableProfile = itemStack.get(DataComponents.PROFILE);
		if (resolvableProfile == null) {
			return null;
		}

		PlayerHeadSpecialRenderer.PlayerHeadRenderInfo playerHeadRenderInfo = this.updatedResolvableProfiles.get(resolvableProfile);
		if (playerHeadRenderInfo != null) {
			return playerHeadRenderInfo;
		}

		ResolvableProfile resolvableProfile2 = resolvableProfile.pollResolve();
		return resolvableProfile2 != null ? this.createAndCacheIfTextureIsUnpacked(resolvableProfile2) : null;
	}

	@Nullable
	private PlayerHeadSpecialRenderer.PlayerHeadRenderInfo createAndCacheIfTextureIsUnpacked(ResolvableProfile resolvableProfile) {
		PlayerSkin playerSkin = this.skinManager.getInsecureSkin(resolvableProfile.gameProfile(), null);
		if (playerSkin != null) {
			PlayerHeadSpecialRenderer.PlayerHeadRenderInfo playerHeadRenderInfo = PlayerHeadSpecialRenderer.PlayerHeadRenderInfo.create(playerSkin);
			this.updatedResolvableProfiles.put(resolvableProfile, playerHeadRenderInfo);
			return playerHeadRenderInfo;
		} else {
			return null;
		}
	}

	@Environment(EnvType.CLIENT)
	public record PlayerHeadRenderInfo(RenderType renderType) {
		static PlayerHeadSpecialRenderer.PlayerHeadRenderInfo create(PlayerSkin playerSkin) {
			return new PlayerHeadSpecialRenderer.PlayerHeadRenderInfo(SkullBlockRenderer.getPlayerSkinRenderType(playerSkin.texture()));
		}
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked() implements SpecialModelRenderer.Unbaked {
		public static final MapCodec<PlayerHeadSpecialRenderer.Unbaked> MAP_CODEC = MapCodec.unit(PlayerHeadSpecialRenderer.Unbaked::new);

		@Override
		public MapCodec<PlayerHeadSpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Nullable
		@Override
		public SpecialModelRenderer<?> bake(EntityModelSet entityModelSet) {
			SkullModelBase skullModelBase = SkullBlockRenderer.createModel(entityModelSet, SkullBlock.Types.PLAYER);
			return skullModelBase == null
				? null
				: new PlayerHeadSpecialRenderer(
					Minecraft.getInstance().getSkinManager(), skullModelBase, PlayerHeadSpecialRenderer.PlayerHeadRenderInfo.create(DefaultPlayerSkin.getDefaultSkin())
				);
		}
	}
}
