package net.minecraft.client.data;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.client.render.model.json.BlockModelDefinition;

/**
 * A supplier of a block state JSON definition.
 */
@Environment(EnvType.CLIENT)
public interface BlockModelDefinitionCreator {
	Block getBlock();

	BlockModelDefinition createBlockModelDefinition();
}
