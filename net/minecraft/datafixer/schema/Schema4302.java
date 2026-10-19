package net.minecraft.datafixer.schema;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.function.Supplier;

public class Schema4302 extends IdentifierNormalizingSchema {
	public Schema4302(int i, Schema schema) {
		super(i, schema);
	}

	@Override
	public Map<String, Supplier<TypeTemplate>> registerBlockEntities(Schema schema) {
		Map<String, Supplier<TypeTemplate>> map = super.registerBlockEntities(schema);
		schema.registerSimple(map, "minecraft:test_block");
		schema.registerSimple(map, "minecraft:test_instance_block");
		return map;
	}
}
