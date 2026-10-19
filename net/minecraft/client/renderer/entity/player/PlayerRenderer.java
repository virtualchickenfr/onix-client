package net.minecraft.client.renderer.entity.player;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.client.renderer.entity.layers.BeeStingerLayer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.Deadmau5EarsLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.SpinAttackEffectLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class PlayerRenderer extends LivingEntityRenderer<AbstractClientPlayer, PlayerRenderState, PlayerModel> {
	public PlayerRenderer(EntityRendererProvider.Context context, boolean bl) {
		super(context, new PlayerModel(context.bakeLayer(bl ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), bl), 0.5F);
		this.addLayer(
			new HumanoidArmorLayer<>(
				this,
				new HumanoidArmorModel(context.bakeLayer(bl ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR)),
				new HumanoidArmorModel(context.bakeLayer(bl ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR)),
				context.getEquipmentRenderer()
			)
		);
		this.addLayer(new PlayerItemInHandLayer<>(this));
		this.addLayer(new ArrowLayer<>(this, context));
		this.addLayer(new Deadmau5EarsLayer(this, context.getModelSet()));
		this.addLayer(new CapeLayer(this, context.getModelSet(), context.getEquipmentAssets()));
		this.addLayer(new CustomHeadLayer<>(this, context.getModelSet()));
		this.addLayer(new WingsLayer<>(this, context.getModelSet(), context.getEquipmentRenderer()));
		this.addLayer(new ParrotOnShoulderLayer(this, context.getModelSet()));
		this.addLayer(new SpinAttackEffectLayer(this, context.getModelSet()));
		this.addLayer(new BeeStingerLayer<>(this, context));
	}

	protected boolean shouldRenderLayers(PlayerRenderState playerRenderState) {
		return !playerRenderState.isSpectator;
	}

	public Vec3 getRenderOffset(PlayerRenderState playerRenderState) {
		Vec3 vec3 = super.getRenderOffset(playerRenderState);
		return playerRenderState.isCrouching ? vec3.add(0.0, playerRenderState.scale * -2.0F / 16.0, 0.0) : vec3;
	}

	private static HumanoidModel.ArmPose getArmPose(AbstractClientPlayer abstractClientPlayer, HumanoidArm humanoidArm) {
		ItemStack itemStack = abstractClientPlayer.getItemInHand(InteractionHand.MAIN_HAND);
		ItemStack itemStack2 = abstractClientPlayer.getItemInHand(InteractionHand.OFF_HAND);
		HumanoidModel.ArmPose armPose = getArmPose(abstractClientPlayer, itemStack, InteractionHand.MAIN_HAND);
		HumanoidModel.ArmPose armPose2 = getArmPose(abstractClientPlayer, itemStack2, InteractionHand.OFF_HAND);
		if (armPose.isTwoHanded()) {
			armPose2 = itemStack2.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		}

		return abstractClientPlayer.getMainArm() == humanoidArm ? armPose : armPose2;
	}

	private static HumanoidModel.ArmPose getArmPose(Player player, ItemStack itemStack, InteractionHand interactionHand) {
		if (itemStack.isEmpty()) {
			return HumanoidModel.ArmPose.EMPTY;
		}

		if (!player.swinging && itemStack.is(Items.CROSSBOW) && CrossbowItem.isCharged(itemStack)) {
			return HumanoidModel.ArmPose.CROSSBOW_HOLD;
		}

		if (player.getUsedItemHand() == interactionHand && player.getUseItemRemainingTicks() > 0) {
			ItemUseAnimation itemUseAnimation = itemStack.getUseAnimation();
			if (itemUseAnimation == ItemUseAnimation.BLOCK) {
				return HumanoidModel.ArmPose.BLOCK;
			}

			if (itemUseAnimation == ItemUseAnimation.BOW) {
				return HumanoidModel.ArmPose.BOW_AND_ARROW;
			}

			if (itemUseAnimation == ItemUseAnimation.SPEAR) {
				return HumanoidModel.ArmPose.THROW_SPEAR;
			}

			if (itemUseAnimation == ItemUseAnimation.CROSSBOW) {
				return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
			}

			if (itemUseAnimation == ItemUseAnimation.SPYGLASS) {
				return HumanoidModel.ArmPose.SPYGLASS;
			}

			if (itemUseAnimation == ItemUseAnimation.TOOT_HORN) {
				return HumanoidModel.ArmPose.TOOT_HORN;
			}

			if (itemUseAnimation == ItemUseAnimation.BRUSH) {
				return HumanoidModel.ArmPose.BRUSH;
			}
		}

		return HumanoidModel.ArmPose.ITEM;
	}

	public ResourceLocation getTextureLocation(PlayerRenderState playerRenderState) {
		return playerRenderState.skin.texture();
	}

	protected void scale(PlayerRenderState playerRenderState, PoseStack poseStack) {
		float f = 0.9375F;
		poseStack.scale(0.9375F, 0.9375F, 0.9375F);
	}

	protected void renderNameTag(PlayerRenderState playerRenderState, Component component, PoseStack poseStack, MultiBufferSource multiBufferSource, int i) {
		poseStack.pushPose();
		if (playerRenderState.scoreText != null) {
			super.renderNameTag(playerRenderState, playerRenderState.scoreText, poseStack, multiBufferSource, i);
			poseStack.translate(0.0F, 9.0F * 1.15F * 0.025F, 0.0F);
		}

		super.renderNameTag(playerRenderState, component, poseStack, multiBufferSource, i);
		poseStack.popPose();
	}

	public PlayerRenderState createRenderState() {
		return new PlayerRenderState();
	}

	public void extractRenderState(AbstractClientPlayer abstractClientPlayer, PlayerRenderState playerRenderState, float f) {
		super.extractRenderState(abstractClientPlayer, playerRenderState, f);
		HumanoidMobRenderer.extractHumanoidRenderState(abstractClientPlayer, playerRenderState, f, this.itemModelResolver);
		playerRenderState.leftArmPose = getArmPose(abstractClientPlayer, HumanoidArm.LEFT);
		playerRenderState.rightArmPose = getArmPose(abstractClientPlayer, HumanoidArm.RIGHT);
		playerRenderState.skin = abstractClientPlayer.getSkin();
		playerRenderState.arrowCount = abstractClientPlayer.getArrowCount();
		playerRenderState.stingerCount = abstractClientPlayer.getStingerCount();
		playerRenderState.useItemRemainingTicks = abstractClientPlayer.getUseItemRemainingTicks();
		playerRenderState.swinging = abstractClientPlayer.swinging;
		playerRenderState.isSpectator = abstractClientPlayer.isSpectator();
		playerRenderState.showHat = abstractClientPlayer.isModelPartShown(PlayerModelPart.HAT);
		playerRenderState.showJacket = abstractClientPlayer.isModelPartShown(PlayerModelPart.JACKET);
		playerRenderState.showLeftPants = abstractClientPlayer.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
		playerRenderState.showRightPants = abstractClientPlayer.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
		playerRenderState.showLeftSleeve = abstractClientPlayer.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
		playerRenderState.showRightSleeve = abstractClientPlayer.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
		playerRenderState.showCape = abstractClientPlayer.isModelPartShown(PlayerModelPart.CAPE);
		extractFlightData(abstractClientPlayer, playerRenderState, f);
		extractCapeState(abstractClientPlayer, playerRenderState, f);
		if (playerRenderState.distanceToCameraSq < 100.0) {
			Scoreboard scoreboard = abstractClientPlayer.getScoreboard();
			Objective objective = scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME);
			if (objective != null) {
				ReadOnlyScoreInfo readOnlyScoreInfo = scoreboard.getPlayerScoreInfo(abstractClientPlayer, objective);
				Component component = ReadOnlyScoreInfo.safeFormatValue(readOnlyScoreInfo, objective.numberFormatOrDefault(StyledFormat.NO_STYLE));
				playerRenderState.scoreText = Component.empty().append(component).append(CommonComponents.SPACE).append(objective.getDisplayName());
			} else {
				playerRenderState.scoreText = null;
			}
		} else {
			playerRenderState.scoreText = null;
		}

		playerRenderState.parrotOnLeftShoulder = getParrotOnShoulder(abstractClientPlayer, true);
		playerRenderState.parrotOnRightShoulder = getParrotOnShoulder(abstractClientPlayer, false);
		playerRenderState.id = abstractClientPlayer.getId();
		playerRenderState.name = abstractClientPlayer.getGameProfile().getName();
		playerRenderState.heldOnHead.clear();
		if (playerRenderState.isUsingItem) {
			ItemStack itemStack = abstractClientPlayer.getItemInHand(playerRenderState.useItemHand);
			if (itemStack.is(Items.SPYGLASS)) {
				this.itemModelResolver.updateForLiving(playerRenderState.heldOnHead, itemStack, ItemDisplayContext.HEAD, abstractClientPlayer);
			}
		}
	}

	private static void extractFlightData(AbstractClientPlayer abstractClientPlayer, PlayerRenderState playerRenderState, float f) {
		playerRenderState.fallFlyingTimeInTicks = abstractClientPlayer.getFallFlyingTicks() + f;
		Vec3 vec3 = abstractClientPlayer.getViewVector(f);
		Vec3 vec32 = abstractClientPlayer.getDeltaMovementLerped(f);
		if (vec32.horizontalDistanceSqr() > 1.0E-5F && vec3.horizontalDistanceSqr() > 1.0E-5F) {
			playerRenderState.shouldApplyFlyingYRot = true;
			double d = vec32.horizontal().normalize().dot(vec3.horizontal().normalize());
			double e = vec32.x * vec3.z - vec32.z * vec3.x;
			playerRenderState.flyingYRot = (float)(Math.signum(e) * Math.acos(Math.min(1.0, Math.abs(d))));
		} else {
			playerRenderState.shouldApplyFlyingYRot = false;
			playerRenderState.flyingYRot = 0.0F;
		}
	}

	private static void extractCapeState(AbstractClientPlayer abstractClientPlayer, PlayerRenderState playerRenderState, float f) {
		double d = Mth.lerp(f, abstractClientPlayer.xCloakO, abstractClientPlayer.xCloak) - Mth.lerp(f, abstractClientPlayer.xo, abstractClientPlayer.getX());
		double e = Mth.lerp(f, abstractClientPlayer.yCloakO, abstractClientPlayer.yCloak) - Mth.lerp(f, abstractClientPlayer.yo, abstractClientPlayer.getY());
		double g = Mth.lerp(f, abstractClientPlayer.zCloakO, abstractClientPlayer.zCloak) - Mth.lerp(f, abstractClientPlayer.zo, abstractClientPlayer.getZ());
		float h = Mth.rotLerp(f, abstractClientPlayer.yBodyRotO, abstractClientPlayer.yBodyRot);
		double i = Mth.sin(h * (float) (Math.PI / 180.0));
		double j = -Mth.cos(h * (float) (Math.PI / 180.0));
		playerRenderState.capeFlap = (float)e * 10.0F;
		playerRenderState.capeFlap = Mth.clamp(playerRenderState.capeFlap, -6.0F, 32.0F);
		playerRenderState.capeLean = (float)(d * i + g * j) * 100.0F;
		playerRenderState.capeLean = playerRenderState.capeLean * (1.0F - playerRenderState.fallFlyingScale());
		playerRenderState.capeLean = Mth.clamp(playerRenderState.capeLean, 0.0F, 150.0F);
		playerRenderState.capeLean2 = (float)(d * j - g * i) * 100.0F;
		playerRenderState.capeLean2 = Mth.clamp(playerRenderState.capeLean2, -20.0F, 20.0F);
		float k = Mth.lerp(f, abstractClientPlayer.oBob, abstractClientPlayer.bob);
		float l = Mth.lerp(f, abstractClientPlayer.walkDistO, abstractClientPlayer.walkDist);
		playerRenderState.capeFlap = playerRenderState.capeFlap + Mth.sin(l * 6.0F) * 32.0F * k;
	}

	@Nullable
	private static Parrot.Variant getParrotOnShoulder(AbstractClientPlayer abstractClientPlayer, boolean bl) {
		CompoundTag compoundTag = bl ? abstractClientPlayer.getShoulderEntityLeft() : abstractClientPlayer.getShoulderEntityRight();
		if (compoundTag.isEmpty()) {
			return null;
		}

		EntityType<?> entityType = compoundTag.read("id", EntityType.CODEC).orElse(null);
		return entityType == EntityType.PARROT ? compoundTag.read("Variant", Parrot.Variant.LEGACY_CODEC).orElse(Parrot.Variant.RED_BLUE) : null;
	}

	public void renderRightHand(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, ResourceLocation resourceLocation, boolean bl) {
		this.renderHand(poseStack, multiBufferSource, i, resourceLocation, this.model.rightArm, bl);
	}

	public void renderLeftHand(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, ResourceLocation resourceLocation, boolean bl) {
		this.renderHand(poseStack, multiBufferSource, i, resourceLocation, this.model.leftArm, bl);
	}

	private void renderHand(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, ResourceLocation resourceLocation, ModelPart modelPart, boolean bl) {
		PlayerModel playerModel = this.getModel();
		modelPart.resetPose();
		modelPart.visible = true;
		playerModel.leftSleeve.visible = bl;
		playerModel.rightSleeve.visible = bl;
		playerModel.leftArm.zRot = -0.1F;
		playerModel.rightArm.zRot = 0.1F;
		modelPart.render(poseStack, multiBufferSource.getBuffer(RenderType.entityTranslucent(resourceLocation)), i, OverlayTexture.NO_OVERLAY);
	}

	protected void setupRotations(PlayerRenderState playerRenderState, PoseStack poseStack, float f, float g) {
		float h = playerRenderState.swimAmount;
		float i = playerRenderState.xRot;
		if (playerRenderState.isFallFlying) {
			super.setupRotations(playerRenderState, poseStack, f, g);
			float j = playerRenderState.fallFlyingScale();
			if (!playerRenderState.isAutoSpinAttack) {
				poseStack.mulPose(Axis.XP.rotationDegrees(j * (-90.0F - i)));
			}

			if (playerRenderState.shouldApplyFlyingYRot) {
				poseStack.mulPose(Axis.YP.rotation(playerRenderState.flyingYRot));
			}
		} else if (h > 0.0F) {
			super.setupRotations(playerRenderState, poseStack, f, g);
			float j = playerRenderState.isInWater ? -90.0F - i : -90.0F;
			float k = Mth.lerp(h, 0.0F, j);
			poseStack.mulPose(Axis.XP.rotationDegrees(k));
			if (playerRenderState.isVisuallySwimming) {
				poseStack.translate(0.0F, -1.0F, 0.3F);
			}
		} else {
			super.setupRotations(playerRenderState, poseStack, f, g);
		}
	}
}
