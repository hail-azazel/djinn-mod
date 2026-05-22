package com.djinn.block;

import com.djinn.item.DjinnLampStacks;
import com.djinn.state.DjinnNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class MagicLampBlockEntity extends BlockEntity {
	private UUID owner;
	private String ownerName = "";
	private UUID master;
	private int wishesUsed;

	public MagicLampBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.MAGIC_LAMP_BLOCK_ENTITY.get(), pos, state);
	}

	public Optional<UUID> owner() {
		return Optional.ofNullable(owner);
	}

	public void owner(UUID owner) {
		this.owner = owner;
		sync();
	}

	public String ownerName() {
		return ownerName;
	}

	public void ownerName(String ownerName) {
		this.ownerName = ownerName;
		sync();
	}

	public Optional<UUID> master() {
		return Optional.ofNullable(master);
	}

	public void master(UUID master) {
		this.master = master;
		sync();
	}

	public int wishesUsed() {
		return wishesUsed;
	}

	public void wishesUsed(int wishesUsed) {
		this.wishesUsed = wishesUsed;
		sync();
	}

	private void sync() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	public void readFromStack(ItemStack stack) {
		DjinnNbt.owner(stack).ifPresent(this::owner);
		String stackOwnerName = DjinnNbt.ownerName(stack);
		if (!stackOwnerName.isBlank()) {
			ownerName(stackOwnerName);
		}
		DjinnNbt.master(stack).ifPresent(this::master);
		wishesUsed(DjinnNbt.wishesUsed(stack));
	}

	public ItemStack asStack() {
		ItemStack stack = new ItemStack(ModBlocks.MAGIC_LAMP.get());
		if (owner != null) {
			DjinnNbt.owner(stack, owner);
		}
		if (!ownerName.isBlank()) {
			DjinnNbt.ownerName(stack, ownerName);
		}
		if (master != null) {
			DjinnNbt.master(stack, master);
		}
		DjinnNbt.wishesUsed(stack, wishesUsed);
		DjinnLampStacks.applyLampRules(stack);
		return stack;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (owner != null) {
			tag.putUUID("Owner", owner);
		}
		if (!ownerName.isBlank()) {
			tag.putString("OwnerName", ownerName);
		}
		if (master != null) {
			tag.putUUID("Master", master);
		}
		tag.putInt("WishesUsed", wishesUsed);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
		ownerName = tag.contains("OwnerName") ? tag.getString("OwnerName") : "";
		master = tag.hasUUID("Master") ? tag.getUUID("Master") : null;
		wishesUsed = tag.getInt("WishesUsed");
	}

	@Nullable
	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
		CompoundTag tag = pkt.getTag();
		if (tag != null) {
			loadAdditional(tag, registries);
		}
	}
}
