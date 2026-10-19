package net.minecraft.network.chat.contents;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

public class KeybindContents implements ComponentContents {
	public static final MapCodec<KeybindContents> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(Codec.STRING.fieldOf("keybind").forGetter(keybindContents -> keybindContents.name)).apply(instance, KeybindContents::new)
	);
	public static final ComponentContents.Type<KeybindContents> TYPE = new ComponentContents.Type<>(CODEC, "keybind");
	private final String name;
	@Nullable
	private Supplier<Component> nameResolver;

	public KeybindContents(String string) {
		this.name = string;
	}

	private Component getNestedComponent() {
		if (this.nameResolver == null) {
			this.nameResolver = KeybindResolver.keyResolver.apply(this.name);
		}

		return this.nameResolver.get();
	}

	@Override
	public <T> Optional<T> visit(FormattedText.ContentConsumer<T> contentConsumer) {
		return this.getNestedComponent().visit(contentConsumer);
	}

	@Override
	public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> styledContentConsumer, Style style) {
		return this.getNestedComponent().visit(styledContentConsumer, style);
	}

	@Override
	public boolean equals(Object object) {
		return this == object ? true : object instanceof KeybindContents keybindContents && this.name.equals(keybindContents.name);
	}

	@Override
	public int hashCode() {
		return this.name.hashCode();
	}

	@Override
	public String toString() {
		return "keybind{" + this.name + "}";
	}

	public String getName() {
		return this.name;
	}

	@Override
	public ComponentContents.Type<?> type() {
		return TYPE;
	}
}
