package com.djinn.item;

import com.djinn.DjinnOriginMod;
import com.djinn.block.ModBlocks;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DjinnOriginMod.MOD_ID);
	public static final DeferredItem<Item> SANDSTORM_BOTTLE = ITEMS.registerItem("sandstorm_bottle", SandstormBottleItem::new, new Item.Properties().stacksTo(1));

	private ModItems() {
	}

	public static void register(IEventBus bus) {
		ITEMS.register(bus);
		bus.addListener(ModItems::addCreativeTabItems);
	}

	private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
			event.accept(ModBlocks.MAGIC_LAMP);
			event.accept(SANDSTORM_BOTTLE);
		}
	}
}
