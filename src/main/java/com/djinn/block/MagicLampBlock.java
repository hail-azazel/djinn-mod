package com.djinn.block;

import com.djinn.state.DjinnWorldState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class MagicLampBlock extends Block implements BlockEntityProvider {
	private static final VoxelShape SHAPE = Block.createCuboidShape(2.0, 0.0, 3.75, 14.0, 25.0, 12.25);

	public MagicLampBlock(Settings settings) {
		super(settings);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new MagicLampBlockEntity(pos, state);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable net.minecraft.entity.LivingEntity placer, ItemStack itemStack) {
		super.onPlaced(world, pos, state, placer, itemStack);
		if (!(world instanceof ServerWorld serverWorld) || !(world.getBlockEntity(pos) instanceof MagicLampBlockEntity lamp)) {
			return;
		}
		lamp.readFromStack(itemStack);
		if (placer instanceof ServerPlayerEntity player) {
			DjinnWorldState stateData = DjinnWorldState.get(serverWorld.getServer());
			if (stateData.player(player.getUuid()).isDjinn() && lamp.owner().isEmpty()) {
				lamp.owner(player.getUuid());
				lamp.ownerName(player.getGameProfile().getName());
			}
			lamp.owner().ifPresent(owner -> {
				stateData.player(owner).setLamp(serverWorld.getRegistryKey().getValue().toString(), pos);
				ServerPlayerEntity ownerPlayer = serverWorld.getServer().getPlayerManager().getPlayer(owner);
				if (ownerPlayer != null) {
					ownerPlayer.setSpawnPoint(serverWorld.getRegistryKey(), pos.up(), ownerPlayer.getYaw(), true, true);
				}
				serverWorld.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.BLOCKS, 0.9F, 1.35F);
				serverWorld.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.65F, 0.72F);
				stateData.markDirty();
			});
		}
	}

	@Override
	public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		if (!world.isClient && world.getBlockEntity(pos) instanceof MagicLampBlockEntity lamp) {
			ItemStack stack = lamp.asStack();
			if (world instanceof ServerWorld serverWorld && lamp.owner().isPresent()) {
				DjinnWorldState worldState = DjinnWorldState.get(serverWorld.getServer());
				worldState.player(lamp.owner().get()).clearLamp();
				ServerPlayerEntity owner = serverWorld.getServer().getPlayerManager().getPlayer(lamp.owner().get());
				if (owner != null) {
					if (!owner.getInventory().insertStack(stack)) {
						worldState.player(owner.getUuid()).setPendingLampReturn(true);
					} else {
						worldState.player(owner.getUuid()).setPendingLampReturn(false);
					}
					owner.setSpawnPoint(serverWorld.getRegistryKey(), null, owner.getYaw(), true, true);
				} else {
					worldState.player(lamp.owner().get()).setPendingLampReturn(true);
				}
				serverWorld.playSound(null, pos, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 0.85F, 1.45F);
				serverWorld.playSound(null, pos, SoundEvents.BLOCK_SAND_BREAK, SoundCategory.BLOCKS, 0.75F, 0.55F);
				worldState.markDirty();
			}
		}
		super.onBreak(world, pos, state, player);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!player.isSneaking()) {
			return ActionResult.PASS;
		}
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		if (!(world.getBlockEntity(pos) instanceof MagicLampBlockEntity lamp)) {
			return ActionResult.PASS;
		}
		ItemStack stack = lamp.asStack();
		if (player.getStackInHand(hand).isEmpty()) {
			player.setStackInHand(hand, stack);
		} else if (!player.getInventory().insertStack(stack)) {
			player.sendMessage(net.minecraft.text.Text.translatable("message.djinn.lamp_inventory_full"), true);
			world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.35F, 0.65F);
			return ActionResult.FAIL;
		}
		if (world instanceof ServerWorld serverWorld && lamp.owner().isPresent()) {
			DjinnWorldState worldState = DjinnWorldState.get(serverWorld.getServer());
			worldState.player(lamp.owner().get()).clearLamp();
			worldState.player(lamp.owner().get()).setPendingLampReturn(false);
			ServerPlayerEntity owner = serverWorld.getServer().getPlayerManager().getPlayer(lamp.owner().get());
			if (owner != null) {
				owner.setSpawnPoint(serverWorld.getRegistryKey(), null, owner.getYaw(), true, true);
			}
			worldState.markDirty();
		}
		world.removeBlock(pos, false);
		world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_GOLD, SoundCategory.PLAYERS, 0.7F, 1.25F);
		world.playSound(null, pos, SoundEvents.BLOCK_SAND_PLACE, SoundCategory.BLOCKS, 0.55F, 0.8F);
		return ActionResult.SUCCESS;
	}
}
