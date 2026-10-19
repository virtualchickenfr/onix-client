package net.minecraft.datafixer.fix;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import net.minecraft.datafixer.TypeReferences;

public class OminousBannerBlockEntityRenameFix extends ChoiceFix {
	public OminousBannerBlockEntityRenameFix(Schema schema, boolean bl) {
		super(schema, bl, "OminousBannerBlockEntityRenameFix", TypeReferences.BLOCK_ENTITY, "minecraft:banner");
	}

	@Override
	protected Typed<?> transform(Typed<?> inputTyped) {
		OpticFinder<?> opticFinder = inputTyped.getType().findField("CustomName");
		OpticFinder<Pair<String, String>> opticFinder2 = DSL.typeFinder((Type<Pair<String, String>>)this.getInputSchema().getType(TypeReferences.TEXT_COMPONENT));
		return inputTyped.updateTyped(
			opticFinder,
			typed -> typed.update(
				opticFinder2,
				pair -> pair.mapSecond(string -> string.replace("\"translate\":\"block.minecraft.illager_banner\"", "\"translate\":\"block.minecraft.ominous_banner\""))
			)
		);
	}
}
