package net.minecraft.datafixer.fix;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import net.minecraft.datafixer.TypeReferences;

public class TextComponentStrictJsonFix extends DataFix {
	public TextComponentStrictJsonFix(Schema outputSchema) {
		super(outputSchema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<Pair<String, String>> type = (Type<Pair<String, String>>)this.getInputSchema().getType(TypeReferences.TEXT_COMPONENT);
		return this.fixTypeEverywhere("TextComponentStrictJsonFix", type, ops -> pair -> pair.mapSecond(TextFixes::method_56629));
	}
}
