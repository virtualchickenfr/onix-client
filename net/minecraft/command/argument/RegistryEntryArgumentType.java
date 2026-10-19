package net.minecraft.command.argument;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Collection;
import java.util.List;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.function.LootFunction;
import net.minecraft.loot.function.LootFunctionTypes;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public class RegistryEntryArgumentType<T> implements ArgumentType<RegistryEntry<T>> {
	private static final Collection<String> EXAMPLES = List.of("foo", "foo:bar", "012", "{}", "true");
	public static final DynamicCommandExceptionType FAILED_TO_PARSE_EXCEPTION = new DynamicCommandExceptionType(
		argument -> Text.stringifiedTranslatable("argument.resource_or_id.failed_to_parse", argument)
	);
	private static final SimpleCommandExceptionType INVALID_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("argument.resource_or_id.invalid"));
	private static final StringNbtReader<?> SNBT_READER = StringNbtReader.fromOps(NbtOps.INSTANCE);
	private final RegistryWrapper.WrapperLookup registries;
	private final boolean canLookupRegistry;
	private final Codec<RegistryEntry<T>> entryCodec;

	protected RegistryEntryArgumentType(CommandRegistryAccess registryAccess, RegistryKey<Registry<T>> registry, Codec<RegistryEntry<T>> entryCodec) {
		this.registries = registryAccess;
		this.canLookupRegistry = registryAccess.getOptional(registry).isPresent();
		this.entryCodec = entryCodec;
	}

	public static RegistryEntryArgumentType.LootTableArgumentType lootTable(CommandRegistryAccess registryAccess) {
		return new RegistryEntryArgumentType.LootTableArgumentType(registryAccess);
	}

	public static RegistryEntry<LootTable> getLootTable(CommandContext<ServerCommandSource> context, String argument) throws CommandSyntaxException {
		return getArgument(context, argument);
	}

	public static RegistryEntryArgumentType.LootFunctionArgumentType lootFunction(CommandRegistryAccess registryAccess) {
		return new RegistryEntryArgumentType.LootFunctionArgumentType(registryAccess);
	}

	public static RegistryEntry<LootFunction> getLootFunction(CommandContext<ServerCommandSource> context, String argument) {
		return getArgument(context, argument);
	}

	public static RegistryEntryArgumentType.LootConditionArgumentType lootCondition(CommandRegistryAccess registryAccess) {
		return new RegistryEntryArgumentType.LootConditionArgumentType(registryAccess);
	}

	public static RegistryEntry<LootCondition> getLootCondition(CommandContext<ServerCommandSource> context, String argument) {
		return getArgument(context, argument);
	}

	private static <T> RegistryEntry<T> getArgument(CommandContext<ServerCommandSource> context, String argument) {
		return context.getArgument(argument, RegistryEntry.class);
	}

	@Nullable
	public RegistryEntry<T> parse(StringReader stringReader) throws CommandSyntaxException {
		return this.parse(stringReader, SNBT_READER);
	}

	@Nullable
	private <O> RegistryEntry<T> parse(StringReader reader, StringNbtReader<O> snbtReader) throws CommandSyntaxException {
		RegistryOps<O> registryOps = this.registries.getOps(snbtReader.getOps());
		Dynamic<?> dynamic = parseAsNbt(registryOps, snbtReader, reader);
		return !this.canLookupRegistry ? null : this.entryCodec.parse(dynamic).getOrThrow(argument -> FAILED_TO_PARSE_EXCEPTION.createWithContext(reader, argument));
	}

	@VisibleForTesting
	static <T> Dynamic<T> parseAsNbt(DynamicOps<T> ops, StringNbtReader<T> snbtReader, StringReader reader) throws CommandSyntaxException {
		int i = reader.getCursor();
		T object = snbtReader.readAsArgument(reader);
		if (hasFinishedReading(reader)) {
			return new Dynamic<>(ops, object);
		} else {
			reader.setCursor(i);
			Identifier identifier = Identifier.fromCommandInput(reader);
			if (hasFinishedReading(reader)) {
				return new Dynamic<>(ops, ops.createString(identifier.toString()));
			} else {
				reader.setCursor(i);
				throw INVALID_EXCEPTION.createWithContext(reader);
			}
		}
	}

	private static boolean hasFinishedReading(StringReader stringReader) {
		return !stringReader.canRead() || stringReader.peek() == ' ';
	}

	@Override
	public Collection<String> getExamples() {
		return EXAMPLES;
	}

	public static class LootConditionArgumentType extends RegistryEntryArgumentType<LootCondition> {
		protected LootConditionArgumentType(CommandRegistryAccess registryAccess) {
			super(registryAccess, RegistryKeys.PREDICATE, LootCondition.ENTRY_CODEC);
		}
	}

	public static class LootFunctionArgumentType extends RegistryEntryArgumentType<LootFunction> {
		protected LootFunctionArgumentType(CommandRegistryAccess registryAccess) {
			super(registryAccess, RegistryKeys.ITEM_MODIFIER, LootFunctionTypes.ENTRY_CODEC);
		}
	}

	public static class LootTableArgumentType extends RegistryEntryArgumentType<LootTable> {
		protected LootTableArgumentType(CommandRegistryAccess registryAccess) {
			super(registryAccess, RegistryKeys.LOOT_TABLE, LootTable.ENTRY_CODEC);
		}
	}
}
