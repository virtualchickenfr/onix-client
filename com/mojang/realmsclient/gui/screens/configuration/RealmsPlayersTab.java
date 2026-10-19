package com.mojang.realmsclient.gui.screens.configuration;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.dto.Ops;
import com.mojang.realmsclient.dto.PlayerInfo;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.gui.screens.RealmsConfirmScreen;
import com.mojang.realmsclient.util.RealmsUtil;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
class RealmsPlayersTab extends GridLayoutTab implements RealmsConfigurationTab {
	static final Logger LOGGER = LogUtils.getLogger();
	static final Component TITLE = Component.translatable("mco.configure.world.players.title");
	static final Component QUESTION_TITLE = Component.translatable("mco.question");
	private static final int PADDING = 8;
	final RealmsConfigureWorldScreen configurationScreen;
	final Minecraft minecraft;
	RealmsServer serverData;
	private final RealmsPlayersTab.InvitedObjectSelectionList invitedList;

	RealmsPlayersTab(RealmsConfigureWorldScreen realmsConfigureWorldScreen, Minecraft minecraft, RealmsServer realmsServer) {
		super(TITLE);
		this.configurationScreen = realmsConfigureWorldScreen;
		this.minecraft = minecraft;
		this.serverData = realmsServer;
		GridLayout.RowHelper rowHelper = this.layout.spacing(8).createRowHelper(1);
		this.invitedList = rowHelper.addChild(
			new RealmsPlayersTab.InvitedObjectSelectionList(realmsConfigureWorldScreen.width, this.calculateListHeight()),
			LayoutSettings.defaults().alignVerticallyTop().alignHorizontallyCenter()
		);
		rowHelper.addChild(
			Button.builder(
					Component.translatable("mco.configure.world.buttons.invite"),
					button -> minecraft.setScreen(new RealmsInviteScreen(realmsConfigureWorldScreen, realmsServer))
				)
				.build(),
			LayoutSettings.defaults().alignVerticallyBottom().alignHorizontallyCenter()
		);
		this.updateData(realmsServer);
	}

	public int calculateListHeight() {
		return this.configurationScreen.getContentHeight() - 20 - 16;
	}

	@Override
	public void doLayout(ScreenRectangle screenRectangle) {
		this.invitedList.setSize(this.configurationScreen.width, this.calculateListHeight());
		super.doLayout(screenRectangle);
	}

	@Override
	public void updateData(RealmsServer realmsServer) {
		this.serverData = realmsServer;
		this.invitedList.children().clear();

		for (PlayerInfo playerInfo : realmsServer.players) {
			this.invitedList.children().add(new RealmsPlayersTab.Entry(playerInfo));
		}
	}

	@Environment(EnvType.CLIENT)
	class Entry extends ContainerObjectSelectionList.Entry<RealmsPlayersTab.Entry> {
		protected static final int SKIN_FACE_SIZE = 32;
		private static final Component NORMAL_USER_TEXT = Component.translatable("mco.configure.world.invites.normal.tooltip");
		private static final Component OP_TEXT = Component.translatable("mco.configure.world.invites.ops.tooltip");
		private static final Component REMOVE_TEXT = Component.translatable("mco.configure.world.invites.remove.tooltip");
		private static final ResourceLocation MAKE_OP_SPRITE = ResourceLocation.withDefaultNamespace("player_list/make_operator");
		private static final ResourceLocation REMOVE_OP_SPRITE = ResourceLocation.withDefaultNamespace("player_list/remove_operator");
		private static final ResourceLocation REMOVE_PLAYER_SPRITE = ResourceLocation.withDefaultNamespace("player_list/remove_player");
		private static final int ICON_WIDTH = 8;
		private static final int ICON_HEIGHT = 7;
		private final PlayerInfo playerInfo;
		private final Button removeButton;
		private final Button makeOpButton;
		private final Button removeOpButton;

		public Entry(final PlayerInfo playerInfo) {
			this.playerInfo = playerInfo;
			int i = RealmsPlayersTab.this.serverData.players.indexOf(this.playerInfo);
			this.makeOpButton = SpriteIconButton.builder(NORMAL_USER_TEXT, button -> this.op(i), false)
				.sprite(MAKE_OP_SPRITE, 8, 7)
				.width(16 + RealmsPlayersTab.this.configurationScreen.getFont().width(NORMAL_USER_TEXT))
				.narration(
					supplier -> CommonComponents.joinForNarration(
						Component.translatable("mco.invited.player.narration", playerInfo.getName()),
						supplier.get(),
						Component.translatable("narration.cycle_button.usage.focused", OP_TEXT)
					)
				)
				.build();
			this.removeOpButton = SpriteIconButton.builder(OP_TEXT, button -> this.deop(i), false)
				.sprite(REMOVE_OP_SPRITE, 8, 7)
				.width(16 + RealmsPlayersTab.this.configurationScreen.getFont().width(OP_TEXT))
				.narration(
					supplier -> CommonComponents.joinForNarration(
						Component.translatable("mco.invited.player.narration", playerInfo.getName()),
						supplier.get(),
						Component.translatable("narration.cycle_button.usage.focused", NORMAL_USER_TEXT)
					)
				)
				.build();
			this.removeButton = SpriteIconButton.builder(REMOVE_TEXT, button -> this.uninvite(i), false)
				.sprite(REMOVE_PLAYER_SPRITE, 8, 7)
				.width(16 + RealmsPlayersTab.this.configurationScreen.getFont().width(REMOVE_TEXT))
				.narration(supplier -> CommonComponents.joinForNarration(Component.translatable("mco.invited.player.narration", playerInfo.getName()), supplier.get()))
				.build();
			this.updateOpButtons();
		}

		private void op(int i) {
			UUID uUID = RealmsPlayersTab.this.serverData.players.get(i).getUuid();
			RealmsUtil.<Ops>supplyAsync(
					realmsClient -> realmsClient.op(RealmsPlayersTab.this.serverData.id, uUID),
					realmsServiceException -> RealmsPlayersTab.LOGGER.error("Couldn't op the user", realmsServiceException)
				)
				.thenAcceptAsync(ops -> {
					this.updateOps(ops);
					this.updateOpButtons();
					this.setFocused(this.removeOpButton);
				}, RealmsPlayersTab.this.minecraft);
		}

		private void deop(int i) {
			UUID uUID = RealmsPlayersTab.this.serverData.players.get(i).getUuid();
			RealmsUtil.<Ops>supplyAsync(
					realmsClient -> realmsClient.deop(RealmsPlayersTab.this.serverData.id, uUID),
					realmsServiceException -> RealmsPlayersTab.LOGGER.error("Couldn't deop the user", realmsServiceException)
				)
				.thenAcceptAsync(ops -> {
					this.updateOps(ops);
					this.updateOpButtons();
					this.setFocused(this.makeOpButton);
				}, RealmsPlayersTab.this.minecraft);
		}

		private void uninvite(int i) {
			if (i >= 0 && i < RealmsPlayersTab.this.serverData.players.size()) {
				PlayerInfo playerInfo = RealmsPlayersTab.this.serverData.players.get(i);
				RealmsConfirmScreen realmsConfirmScreen = new RealmsConfirmScreen(
					bl -> {
						if (bl) {
							RealmsUtil.runAsync(
								realmsClient -> realmsClient.uninvite(RealmsPlayersTab.this.serverData.id, playerInfo.getUuid()),
								realmsServiceException -> RealmsPlayersTab.LOGGER.error("Couldn't uninvite user", realmsServiceException)
							);
							RealmsPlayersTab.this.serverData.players.remove(i);
							RealmsPlayersTab.this.updateData(RealmsPlayersTab.this.serverData);
						}

						RealmsPlayersTab.this.minecraft.setScreen(RealmsPlayersTab.this.configurationScreen);
					},
					RealmsPlayersTab.QUESTION_TITLE,
					Component.translatable("mco.configure.world.uninvite.player", playerInfo.getName())
				);
				RealmsPlayersTab.this.minecraft.setScreen(realmsConfirmScreen);
			}
		}

		private void updateOps(Ops ops) {
			for (PlayerInfo playerInfo : RealmsPlayersTab.this.serverData.players) {
				playerInfo.setOperator(ops.ops.contains(playerInfo.getName()));
			}
		}

		private void updateOpButtons() {
			this.makeOpButton.visible = !this.playerInfo.isOperator();
			this.removeOpButton.visible = !this.makeOpButton.visible;
		}

		private Button activeOpButton() {
			return this.makeOpButton.visible ? this.makeOpButton : this.removeOpButton;
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return ImmutableList.of(this.activeOpButton(), this.removeButton);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return ImmutableList.of(this.activeOpButton(), this.removeButton);
		}

		@Override
		public void render(GuiGraphics guiGraphics, int i, int j, int k, int l, int m, int n, int o, boolean bl, float f) {
			int p;
			if (!this.playerInfo.getAccepted()) {
				p = -6250336;
			} else if (this.playerInfo.getOnline()) {
				p = -16711936;
			} else {
				p = -1;
			}

			int q = j + m / 2 - 16;
			RealmsUtil.renderPlayerFace(guiGraphics, k, q, 32, this.playerInfo.getUuid());
			int r = j + m / 2 - 9 / 2;
			guiGraphics.drawString(RealmsPlayersTab.this.configurationScreen.getFont(), this.playerInfo.getName(), k + 8 + 32, r, p);
			int s = j + m / 2 - 10;
			int t = k + l - this.removeButton.getWidth();
			this.removeButton.setPosition(t, s);
			this.removeButton.render(guiGraphics, n, o, f);
			int u = t - this.activeOpButton().getWidth() - 8;
			this.makeOpButton.setPosition(u, s);
			this.makeOpButton.render(guiGraphics, n, o, f);
			this.removeOpButton.setPosition(u, s);
			this.removeOpButton.render(guiGraphics, n, o, f);
		}
	}

	@Environment(EnvType.CLIENT)
	class InvitedObjectSelectionList extends ContainerObjectSelectionList<RealmsPlayersTab.Entry> {
		private static final int ITEM_HEIGHT = 36;

		public InvitedObjectSelectionList(final int i, final int j) {
			super(Minecraft.getInstance(), i, j, RealmsPlayersTab.this.configurationScreen.getHeaderHeight(), 36, (int)(9.0F * 1.5F));
		}

		@Override
		protected void renderHeader(GuiGraphics guiGraphics, int i, int j) {
			String string = RealmsPlayersTab.this.serverData.players != null ? Integer.toString(RealmsPlayersTab.this.serverData.players.size()) : "0";
			Component component = Component.translatable("mco.configure.world.invited.number", string).withStyle(ChatFormatting.UNDERLINE);
			guiGraphics.drawString(
				RealmsPlayersTab.this.configurationScreen.getFont(),
				component,
				i + this.getRowWidth() / 2 - RealmsPlayersTab.this.configurationScreen.getFont().width(component) / 2,
				j,
				-1
			);
		}

		@Override
		protected void renderListBackground(GuiGraphics guiGraphics) {
		}

		@Override
		protected void renderListSeparators(GuiGraphics guiGraphics) {
		}

		@Override
		public int getRowWidth() {
			return 300;
		}
	}
}
