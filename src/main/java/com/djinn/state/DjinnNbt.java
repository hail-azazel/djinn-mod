package com.djinn.state;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class DjinnNbt {
	public static final String OWNER = "DjinnOwner";
	public static final String OWNER_NAME = "DjinnOwnerName";
	public static final String MASTER = "DjinnMaster";
	public static final String WISHES_USED = "DjinnWishesUsed";

	private DjinnNbt() {
	}

	public static Optional<UUID> owner(ItemStack stack) {
		CompoundTag tag = tag(stack);
		return tag.hasUUID(OWNER) ? Optional.of(tag.getUUID(OWNER)) : Optional.empty();
	}

	public static void owner(ItemStack stack, UUID owner) {
		update(stack, tag -> tag.putUUID(OWNER, owner));
	}

	public static String ownerName(ItemStack stack) {
		CompoundTag tag = tag(stack);
		return tag.contains(OWNER_NAME) ? tag.getString(OWNER_NAME) : "";
	}

	public static void ownerName(ItemStack stack, String ownerName) {
		update(stack, tag -> tag.putString(OWNER_NAME, ownerName));
	}

	public static Optional<UUID> master(ItemStack stack) {
		CompoundTag tag = tag(stack);
		return tag.hasUUID(MASTER) ? Optional.of(tag.getUUID(MASTER)) : Optional.empty();
	}

	public static void master(ItemStack stack, UUID master) {
		update(stack, tag -> tag.putUUID(MASTER, master));
	}

	public static int wishesUsed(ItemStack stack) {
		return tag(stack).getInt(WISHES_USED);
	}

	public static void wishesUsed(ItemStack stack, int wishesUsed) {
		update(stack, tag -> tag.putInt(WISHES_USED, wishesUsed));
	}

	private static CompoundTag tag(ItemStack stack) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		return customData == null ? new CompoundTag() : customData.copyTag();
	}

	private static void update(ItemStack stack, Consumer<CompoundTag> updater) {
		CompoundTag tag = tag(stack);
		updater.accept(tag);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}
}
