package com.djinn.block;

import com.djinn.DjinnOriginMod;
import com.djinn.item.MagicLampItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DjinnOriginMod.MOD_ID);
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DjinnOriginMod.MOD_ID);
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, DjinnOriginMod.MOD_ID);

	public static final DeferredBlock<MagicLampBlock> MAGIC_LAMP_BLOCK = BLOCKS.registerBlock(
			"magic_lamp",
			MagicLampBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK).strength(0.8F).noOcclusion().noLootTable()
	);
	public static final DeferredItem<BlockItem> MAGIC_LAMP = ITEMS.register(
			"magic_lamp",
			() -> new MagicLampItem(MAGIC_LAMP_BLOCK.get(), new Item.Properties().stacksTo(1))
	);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MagicLampBlockEntity>> MAGIC_LAMP_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
			"magic_lamp",
			() -> BlockEntityType.Builder.of(MagicLampBlockEntity::new, MAGIC_LAMP_BLOCK.get()).build(null)
	);

	private ModBlocks() {
	}

	public static void register(IEventBus bus) {
		BLOCKS.register(bus);
		ITEMS.register(bus);
		BLOCK_ENTITY_TYPES.register(bus);
	}
}
