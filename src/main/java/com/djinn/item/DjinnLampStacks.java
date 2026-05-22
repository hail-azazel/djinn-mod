package com.djinn.item;

import com.djinn.block.ModBlocks;
import com.djinn.state.DjinnNbt;
import com.djinn.state.DjinnPlayerData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class DjinnLampStacks {
	private DjinnLampStacks() {
	}

	public static ItemStack boundLamp(ServerPlayer owner, DjinnPlayerData data) {
		ItemStack lamp = new ItemStack(ModBlocks.MAGIC_LAMP.get());
		DjinnNbt.owner(lamp, owner.getUUID());
		DjinnNbt.ownerName(lamp, owner.getGameProfile().getName());
		DjinnNbt.wishesUsed(lamp, data.wishesUsed());
		applyLampRules(lamp);
		return lamp;
	}

	public static void applyLampRules(ItemStack stack) {
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
	}

	public static boolean isMagicLamp(ItemStack stack) {
		return !stack.isEmpty() && stack.is(ModBlocks.MAGIC_LAMP.get());
	}

	public static boolean isBoundLamp(ItemStack stack) {
		return isMagicLamp(stack) && DjinnNbt.owner(stack).isPresent();
	}
}
