package net.minecraft.text;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.dynamic.Codecs;

public interface ClickEvent {
	Codec<ClickEvent> CODEC = ClickEvent.Action.CODEC.dispatch("action", ClickEvent::getAction, action -> action.codec);

	ClickEvent.Action getAction();

	public static enum Action implements StringIdentifiable {
		OPEN_URL("open_url", true, ClickEvent.OpenUrl.CODEC),
		OPEN_FILE("open_file", false, ClickEvent.OpenFile.CODEC),
		RUN_COMMAND("run_command", true, ClickEvent.RunCommand.CODEC),
		SUGGEST_COMMAND("suggest_command", true, ClickEvent.SuggestCommand.CODEC),
		CHANGE_PAGE("change_page", true, ClickEvent.ChangePage.CODEC),
		COPY_TO_CLIPBOARD("copy_to_clipboard", true, ClickEvent.CopyToClipboard.CODEC);

		public static final Codec<ClickEvent.Action> UNVALIDATED_CODEC = StringIdentifiable.createCodec(ClickEvent.Action::values);
		public static final Codec<ClickEvent.Action> CODEC = UNVALIDATED_CODEC.validate(ClickEvent.Action::validate);
		private final boolean userDefinable;
		private final String name;
		final MapCodec<? extends ClickEvent> codec;

		private Action(final String name, final boolean userDefinable, final MapCodec<? extends ClickEvent> codec) {
			this.name = name;
			this.userDefinable = userDefinable;
			this.codec = codec;
		}

		public boolean isUserDefinable() {
			return this.userDefinable;
		}

		@Override
		public String asString() {
			return this.name;
		}

		public static DataResult<ClickEvent.Action> validate(ClickEvent.Action action) {
			return !action.isUserDefinable() ? DataResult.error(() -> "Click event type not allowed: " + action) : DataResult.success(action, Lifecycle.stable());
		}
	}

	public record ChangePage(int page) implements ClickEvent {
		public static final MapCodec<ClickEvent.ChangePage> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(Codecs.POSITIVE_INT.fieldOf("page").forGetter(ClickEvent.ChangePage::page)).apply(instance, ClickEvent.ChangePage::new)
		);

		@Override
		public ClickEvent.Action getAction() {
			return ClickEvent.Action.CHANGE_PAGE;
		}
	}

	public record CopyToClipboard(String value) implements ClickEvent {
		public static final MapCodec<ClickEvent.CopyToClipboard> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(Codec.STRING.fieldOf("value").forGetter(ClickEvent.CopyToClipboard::value)).apply(instance, ClickEvent.CopyToClipboard::new)
		);

		@Override
		public ClickEvent.Action getAction() {
			return ClickEvent.Action.COPY_TO_CLIPBOARD;
		}
	}

	public record OpenFile(String path) implements ClickEvent {
		public static final MapCodec<ClickEvent.OpenFile> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(Codec.STRING.fieldOf("path").forGetter(ClickEvent.OpenFile::path)).apply(instance, ClickEvent.OpenFile::new)
		);

		public OpenFile(File file) {
			this(file.toString());
		}

		public OpenFile(Path path) {
			this(path.toFile());
		}

		public File file() {
			return new File(this.path);
		}

		@Override
		public ClickEvent.Action getAction() {
			return ClickEvent.Action.OPEN_FILE;
		}
	}

	public record OpenUrl(URI uri) implements ClickEvent {
		public static final MapCodec<ClickEvent.OpenUrl> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(Codecs.URI.fieldOf("url").forGetter(ClickEvent.OpenUrl::uri)).apply(instance, ClickEvent.OpenUrl::new)
		);

		@Override
		public ClickEvent.Action getAction() {
			return ClickEvent.Action.OPEN_URL;
		}
	}

	public record RunCommand(String command) implements ClickEvent {
		public static final MapCodec<ClickEvent.RunCommand> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(Codecs.CHAT_TEXT.fieldOf("command").forGetter(ClickEvent.RunCommand::command)).apply(instance, ClickEvent.RunCommand::new)
		);

		@Override
		public ClickEvent.Action getAction() {
			return ClickEvent.Action.RUN_COMMAND;
		}
	}

	public record SuggestCommand(String command) implements ClickEvent {
		public static final MapCodec<ClickEvent.SuggestCommand> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(Codecs.CHAT_TEXT.fieldOf("command").forGetter(ClickEvent.SuggestCommand::command))
				.apply(instance, ClickEvent.SuggestCommand::new)
		);

		@Override
		public ClickEvent.Action getAction() {
			return ClickEvent.Action.SUGGEST_COMMAND;
		}
	}
}
