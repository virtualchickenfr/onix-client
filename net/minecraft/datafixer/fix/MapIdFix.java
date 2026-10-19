package net.minecraft.datafixer.fix;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import java.util.Map;
import net.minecraft.datafixer.TypeReferences;

public class MapIdFix extends DataFix {
	public MapIdFix(Schema schema) {
		super(schema, true);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		return this.writeFixAndRead(
			"Map id fix",
			this.getInputSchema().getType(TypeReferences.SAVED_DATA_MAP_DATA),
			this.getOutputSchema().getType(TypeReferences.SAVED_DATA_MAP_DATA),
			dynamic -> dynamic.createMap(Map.of(dynamic.createString("data"), dynamic))
		);
	}
}
