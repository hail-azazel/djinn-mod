package com.djinn.block;

import com.djinn.state.DjinnNbt;
import com.djinn.item.DjinnLampStacks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;
import java.util.UUID;

public class MagicLampBlockEntity extends BlockEntity {
	private UUID owner;
	private String ownerName = "";
	private UUID master;
	private int wishesUsed;

	public MagicLampBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.MAGIC_LAMP_BLOCK_ENTITY, pos, state);
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
		markDirty();
		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
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
		ItemStack stack = new ItemStack(ModBlocks.MAGIC_LAMP);
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
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		if (owner != null) {
			nbt.putUuid("Owner", owner);
		}
		if (!ownerName.isBlank()) {
			nbt.putString("OwnerName", ownerName);
		}
		if (master != null) {
			nbt.putUuid("Master", master);
		}
		nbt.putInt("WishesUsed", wishesUsed);
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
		ownerName = nbt.contains("OwnerName") ? nbt.getString("OwnerName") : "";
		master = nbt.containsUuid("Master") ? nbt.getUuid("Master") : null;
		wishesUsed = nbt.getInt("WishesUsed");
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt() {
		return createNbt();
	}
}
