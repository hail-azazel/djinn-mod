package com.djinn.block;

import com.djinn.DjinnOriginMod;
import com.djinn.item.MagicLampItem;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlocks {
	public static final MagicLampBlock MAGIC_LAMP_BLOCK = new MagicLampBlock(AbstractBlock.Settings.copy(Blocks.GOLD_BLOCK).strength(0.8F).nonOpaque().dropsNothing());
	public static final BlockItem MAGIC_LAMP = new MagicLampItem(MAGIC_LAMP_BLOCK, new FabricItemSettings().maxCount(1));
	public static BlockEntityType<MagicLampBlockEntity> MAGIC_LAMP_BLOCK_ENTITY;

	private ModBlocks() {
	}

	public static void register() {
		Registry.register(Registries.BLOCK, DjinnOriginMod.id("magic_lamp"), MAGIC_LAMP_BLOCK);
		Registry.register(Registries.ITEM, DjinnOriginMod.id("magic_lamp"), MAGIC_LAMP);
		MAGIC_LAMP_BLOCK_ENTITY = Registry.register(
				Registries.BLOCK_ENTITY_TYPE,
				DjinnOriginMod.id("magic_lamp"),
				BlockEntityType.Builder.create(MagicLampBlockEntity::new, MAGIC_LAMP_BLOCK).build(null)
		);
	}
}
