package com.djinn.state;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

import java.util.Optional;
import java.util.UUID;

public final class DjinnNbt {
	public static final String OWNER = "DjinnOwner";
	public static final String OWNER_NAME = "DjinnOwnerName";
	public static final String MASTER = "DjinnMaster";
	public static final String WISHES_USED = "DjinnWishesUsed";

	private DjinnNbt() {
	}

	public static Optional<UUID> owner(ItemStack stack) {
		NbtCompound nbt = stack.getNbt();
		return nbt != null && nbt.containsUuid(OWNER) ? Optional.of(nbt.getUuid(OWNER)) : Optional.empty();
	}

	public static void owner(ItemStack stack, UUID owner) {
		stack.getOrCreateNbt().putUuid(OWNER, owner);
	}

	public static String ownerName(ItemStack stack) {
		NbtCompound nbt = stack.getNbt();
		return nbt != null && nbt.contains(OWNER_NAME) ? nbt.getString(OWNER_NAME) : "";
	}

	public static void ownerName(ItemStack stack, String ownerName) {
		stack.getOrCreateNbt().putString(OWNER_NAME, ownerName);
	}

	public static Optional<UUID> master(ItemStack stack) {
		NbtCompound nbt = stack.getNbt();
		return nbt != null && nbt.containsUuid(MASTER) ? Optional.of(nbt.getUuid(MASTER)) : Optional.empty();
	}

	public static void master(ItemStack stack, UUID master) {
		stack.getOrCreateNbt().putUuid(MASTER, master);
	}

	public static int wishesUsed(ItemStack stack) {
		NbtCompound nbt = stack.getNbt();
		return nbt == null ? 0 : nbt.getInt(WISHES_USED);
	}

	public static void wishesUsed(ItemStack stack, int wishesUsed) {
		stack.getOrCreateNbt().putInt(WISHES_USED, wishesUsed);
	}
}
