package com.djinn.item;

import com.djinn.DjinnOriginMod;
import com.djinn.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModItems {
	public static final Item SANDSTORM_BOTTLE = new SandstormBottleItem(new FabricItemSettings().maxCount(1));

	private ModItems() {
	}

	public static void register() {
		Registry.register(Registries.ITEM, DjinnOriginMod.id("sandstorm_bottle"), SANDSTORM_BOTTLE);
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
			entries.add(ModBlocks.MAGIC_LAMP);
			entries.add(SANDSTORM_BOTTLE);
		});
	}
}
