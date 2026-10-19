package net.minecraft.datafixer.schema;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.datafixer.TypeReferences;

public class Schema1458 extends IdentifierNormalizingSchema {
	public Schema1458(int i, Schema schema) {
		super(i, schema);
	}

	@Override
	public void registerTypes(Schema schema, Map<String, Supplier<TypeTemplate>> map, Map<String, Supplier<TypeTemplate>> map2) {
		super.registerTypes(schema, map, map2);
		schema.registerType(
			true,
			TypeReferences.ENTITY,
			() -> DSL.and(
				TypeReferences.ENTITY_EQUIPMENT.in(schema),
				DSL.optionalFields("CustomName", TypeReferences.TEXT_COMPONENT.in(schema), DSL.taggedChoiceLazy("id", getIdentifierType(), map))
			)
		);
	}

	@Override
	public Map<String, Supplier<TypeTemplate>> registerBlockEntities(Schema schema) {
		Map<String, Supplier<TypeTemplate>> map = super.registerBlockEntities(schema);
		schema.register(map, "minecraft:beacon", (Supplier<TypeTemplate>)(() -> customName(schema)));
		schema.register(map, "minecraft:banner", (Supplier<TypeTemplate>)(() -> customName(schema)));
		schema.register(map, "minecraft:brewing_stand", (Supplier<TypeTemplate>)(() -> itemsAndCustomName(schema)));
		schema.register(map, "minecraft:chest", (Supplier<TypeTemplate>)(() -> itemsAndCustomName(schema)));
		schema.register(map, "minecraft:trapped_chest", (Supplier<TypeTemplate>)(() -> itemsAndCustomName(schema)));
		schema.register(map, "minecraft:dispenser", (Supplier<TypeTemplate>)(() -> itemsAndCustomName(schema)));
		schema.register(map, "minecraft:dropper", (Supplier<TypeTemplate>)(() -> itemsAndCustomName(schema)));
		schema.register(map, "minecraft:enchanting_table", (Supplier<TypeTemplate>)(() -> customName(schema)));
		schema.register(map, "minecraft:furnace", (Supplier<TypeTemplate>)(() -> itemsAndCustomName(schema)));
		schema.register(map, "minecraft:hopper", (Supplier<TypeTemplate>)(() -> itemsAndCustomName(schema)));
		schema.register(map, "minecraft:shulker_box", (Supplier<TypeTemplate>)(() -> itemsAndCustomName(schema)));
		return map;
	}

	public static TypeTemplate itemsAndCustomName(Schema schema) {
		return DSL.optionalFields("Items", DSL.list(TypeReferences.ITEM_STACK.in(schema)), "CustomName", TypeReferences.TEXT_COMPONENT.in(schema));
	}

	public static TypeTemplate customName(Schema schema) {
		return DSL.optionalFields("CustomName", TypeReferences.TEXT_COMPONENT.in(schema));
	}
}
