package com.djinn.item;

import com.djinn.block.ModBlocks;
import com.djinn.state.DjinnNbt;
import com.djinn.state.DjinnPlayerData;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

public final class DjinnLampStacks {
	private DjinnLampStacks() {
	}

	public static ItemStack boundLamp(ServerPlayerEntity owner, DjinnPlayerData data) {
		ItemStack lamp = new ItemStack(ModBlocks.MAGIC_LAMP);
		DjinnNbt.owner(lamp, owner.getUuid());
		DjinnNbt.ownerName(lamp, owner.getGameProfile().getName());
		DjinnNbt.wishesUsed(lamp, data.wishesUsed());
		applyLampRules(lamp);
		return lamp;
	}

	public static void applyLampRules(ItemStack stack) {
		if (!stack.hasEnchantments()) {
			stack.addEnchantment(Enchantments.VANISHING_CURSE, 1);
		} else if (net.minecraft.enchantment.EnchantmentHelper.getLevel(Enchantments.VANISHING_CURSE, stack) <= 0) {
			stack.addEnchantment(Enchantments.VANISHING_CURSE, 1);
		}
	}

	public static boolean isMagicLamp(ItemStack stack) {
		return !stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).equals(com.djinn.DjinnOriginMod.id("magic_lamp"));
	}

	public static boolean isBoundLamp(ItemStack stack) {
		return isMagicLamp(stack) && DjinnNbt.owner(stack).isPresent();
	}
}
