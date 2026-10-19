package net.minecraft.client.gui.screen;

import com.google.common.collect.Lists;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.NarratorManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class GameModeSwitcherScreen extends Screen {
	static final Identifier SLOT_TEXTURE = Identifier.ofVanilla("gamemode_switcher/slot");
	static final Identifier SELECTION_TEXTURE = Identifier.ofVanilla("gamemode_switcher/selection");
	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/gui/container/gamemode_switcher.png");
	private static final int TEXTURE_WIDTH = 128;
	private static final int TEXTURE_HEIGHT = 128;
	private static final int BUTTON_SIZE = 26;
	private static final int ICON_OFFSET = 5;
	private static final int field_32314 = 31;
	private static final int field_32315 = 5;
	private static final int UI_WIDTH = GameModeSwitcherScreen.GameModeSelection.values().length * 31 - 5;
	private static final Text SELECT_NEXT_TEXT = Text.translatable(
		"debug.gamemodes.select_next", Text.translatable("debug.gamemodes.press_f4").formatted(Formatting.AQUA)
	);
	private final GameModeSwitcherScreen.GameModeSelection currentGameMode;
	private GameModeSwitcherScreen.GameModeSelection gameMode;
	private int lastMouseX;
	private int lastMouseY;
	private boolean mouseUsedForSelection;
	private final List<GameModeSwitcherScreen.ButtonWidget> gameModeButtons = Lists.<GameModeSwitcherScreen.ButtonWidget>newArrayList();

	public GameModeSwitcherScreen() {
		super(NarratorManager.EMPTY);
		this.currentGameMode = GameModeSwitcherScreen.GameModeSelection.of(this.getPreviousGameMode());
		this.gameMode = this.currentGameMode;
	}

	private GameMode getPreviousGameMode() {
		ClientPlayerInteractionManager clientPlayerInteractionManager = MinecraftClient.getInstance().interactionManager;
		GameMode gameMode = clientPlayerInteractionManager.getPreviousGameMode();
		if (gameMode != null) {
			return gameMode;
		} else {
			return clientPlayerInteractionManager.getCurrentGameMode() == GameMode.CREATIVE ? GameMode.SURVIVAL : GameMode.CREATIVE;
		}
	}

	@Override
	protected void init() {
		super.init();
		this.gameMode = this.currentGameMode;

		for (int i = 0; i < GameModeSwitcherScreen.GameModeSelection.VALUES.length; i++) {
			GameModeSwitcherScreen.GameModeSelection gameModeSelection = GameModeSwitcherScreen.GameModeSelection.VALUES[i];
			this.gameModeButtons.add(new GameModeSwitcherScreen.ButtonWidget(gameModeSelection, this.width / 2 - UI_WIDTH / 2 + i * 31, this.height / 2 - 31));
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		if (!this.checkForClose()) {
			context.getMatrices().push();
			int i = this.width / 2 - 62;
			int j = this.height / 2 - 31 - 27;
			context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, i, j, 0.0F, 0.0F, 125, 75, 128, 128);
			context.getMatrices().pop();
			super.render(context, mouseX, mouseY, deltaTicks);
			context.drawCenteredTextWithShadow(this.textRenderer, this.gameMode.getText(), this.width / 2, this.height / 2 - 31 - 20, Colors.WHITE);
			context.drawCenteredTextWithShadow(this.textRenderer, SELECT_NEXT_TEXT, this.width / 2, this.height / 2 + 5, 16777215);
			if (!this.mouseUsedForSelection) {
				this.lastMouseX = mouseX;
				this.lastMouseY = mouseY;
				this.mouseUsedForSelection = true;
			}

			boolean bl = this.lastMouseX == mouseX && this.lastMouseY == mouseY;

			for (GameModeSwitcherScreen.ButtonWidget buttonWidget : this.gameModeButtons) {
				buttonWidget.render(context, mouseX, mouseY, deltaTicks);
				buttonWidget.setSelected(this.gameMode == buttonWidget.gameMode);
				if (!bl && buttonWidget.isSelected()) {
					this.gameMode = buttonWidget.gameMode;
				}
			}
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
	}

	private void apply() {
		apply(this.client, this.gameMode);
	}

	private static void apply(MinecraftClient client, GameModeSwitcherScreen.GameModeSelection gameModeSelection) {
		if (client.interactionManager != null && client.player != null) {
			GameModeSwitcherScreen.GameModeSelection gameModeSelection2 = GameModeSwitcherScreen.GameModeSelection.of(client.interactionManager.getCurrentGameMode());
			if (client.player.hasPermissionLevel(2) && gameModeSelection != gameModeSelection2) {
				client.player.networkHandler.sendCommand(gameModeSelection.getCommand());
			}
		}
	}

	private boolean checkForClose() {
		if (!InputUtil.isKeyPressed(this.client.getWindow().getHandle(), GLFW.GLFW_KEY_F3)) {
			this.apply();
			this.client.setScreen(null);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_F4) {
			this.mouseUsedForSelection = false;
			this.gameMode = this.gameMode.next();
			return true;
		} else {
			return super.keyPressed(keyCode, scanCode, modifiers);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Environment(EnvType.CLIENT)
	public static class ButtonWidget extends ClickableWidget {
		final GameModeSwitcherScreen.GameModeSelection gameMode;
		private boolean selected;

		public ButtonWidget(GameModeSwitcherScreen.GameModeSelection gameMode, int x, int y) {
			super(x, y, 26, 26, gameMode.getText());
			this.gameMode = gameMode;
		}

		@Override
		public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
			this.drawBackground(context);
			this.gameMode.renderIcon(context, this.getX() + 5, this.getY() + 5);
			if (this.selected) {
				this.drawSelectionBox(context);
			}
		}

		@Override
		public void appendClickableNarrations(NarrationMessageBuilder builder) {
			this.appendDefaultNarrations(builder);
		}

		@Override
		public boolean isSelected() {
			return super.isSelected() || this.selected;
		}

		public void setSelected(boolean selected) {
			this.selected = selected;
		}

		private void drawBackground(DrawContext context) {
			context.drawGuiTexture(RenderLayer::getGuiTextured, GameModeSwitcherScreen.SLOT_TEXTURE, this.getX(), this.getY(), 26, 26);
		}

		private void drawSelectionBox(DrawContext context) {
			context.drawGuiTexture(RenderLayer::getGuiTextured, GameModeSwitcherScreen.SELECTION_TEXTURE, this.getX(), this.getY(), 26, 26);
		}
	}

	@Environment(EnvType.CLIENT)
	static enum GameModeSelection {
		CREATIVE(Text.translatable("gameMode.creative"), "gamemode creative", new ItemStack(Blocks.GRASS_BLOCK)),
		SURVIVAL(Text.translatable("gameMode.survival"), "gamemode survival", new ItemStack(Items.IRON_SWORD)),
		ADVENTURE(Text.translatable("gameMode.adventure"), "gamemode adventure", new ItemStack(Items.MAP)),
		SPECTATOR(Text.translatable("gameMode.spectator"), "gamemode spectator", new ItemStack(Items.ENDER_EYE));

		protected static final GameModeSwitcherScreen.GameModeSelection[] VALUES = values();
		private static final int field_32317 = 16;
		protected static final int field_32316 = 5;
		final Text text;
		final String command;
		final ItemStack icon;

		private GameModeSelection(final Text text, final String command, final ItemStack icon) {
			this.text = text;
			this.command = command;
			this.icon = icon;
		}

		void renderIcon(DrawContext context, int x, int y) {
			context.drawItem(this.icon, x, y);
		}

		Text getText() {
			return this.text;
		}

		String getCommand() {
			return this.command;
		}

		GameModeSwitcherScreen.GameModeSelection next() {
			return switch (this) {
				case CREATIVE -> SURVIVAL;
				case SURVIVAL -> ADVENTURE;
				case ADVENTURE -> SPECTATOR;
				case SPECTATOR -> CREATIVE;
			};
		}

		static GameModeSwitcherScreen.GameModeSelection of(GameMode gameMode) {
			return switch (gameMode) {
				case SPECTATOR -> SPECTATOR;
				case SURVIVAL -> SURVIVAL;
				case CREATIVE -> CREATIVE;
				case ADVENTURE -> ADVENTURE;
			};
		}
	}
}
