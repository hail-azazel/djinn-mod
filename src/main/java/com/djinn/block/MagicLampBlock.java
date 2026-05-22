package com.djinn.block;

import com.djinn.state.DjinnWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class MagicLampBlock extends Block implements EntityBlock {
	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 3.75, 14.0, 25.0, 12.25);

	public MagicLampBlock(Properties properties) {
		super(properties);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MagicLampBlockEntity(pos, state);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!(level instanceof ServerLevel serverLevel) || !(level.getBlockEntity(pos) instanceof MagicLampBlockEntity lamp)) {
			return;
		}
		lamp.readFromStack(stack);
		if (placer instanceof ServerPlayer player) {
			DjinnWorldState stateData = DjinnWorldState.get(serverLevel.getServer());
			if (stateData.player(player.getUUID()).isDjinn() && lamp.owner().isEmpty()) {
				lamp.owner(player.getUUID());
				lamp.ownerName(player.getGameProfile().getName());
			}
			lamp.owner().ifPresent(owner -> {
				stateData.player(owner).setLamp(serverLevel.dimension().location().toString(), pos);
				ServerPlayer ownerPlayer = serverLevel.getServer().getPlayerList().getPlayer(owner);
				if (ownerPlayer != null) {
					ownerPlayer.setRespawnPosition(serverLevel.dimension(), pos.above(), ownerPlayer.getYRot(), true, true);
				}
				serverLevel.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 0.9F, 1.35F);
				serverLevel.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.65F, 0.72F);
				stateData.setDirty();
			});
		}
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide && level.getBlockEntity(pos) instanceof MagicLampBlockEntity lamp) {
			ItemStack stack = lamp.asStack();
			if (level instanceof ServerLevel serverLevel && lamp.owner().isPresent()) {
				DjinnWorldState worldState = DjinnWorldState.get(serverLevel.getServer());
				worldState.player(lamp.owner().get()).clearLamp();
				ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(lamp.owner().get());
				if (owner != null) {
					if (!owner.getInventory().add(stack)) {
						worldState.player(owner.getUUID()).setPendingLampReturn(true);
					} else {
						worldState.player(owner.getUUID()).setPendingLampReturn(false);
					}
					owner.setRespawnPosition(serverLevel.dimension(), null, owner.getYRot(), true, true);
				} else {
					worldState.player(lamp.owner().get()).setPendingLampReturn(true);
				}
				serverLevel.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.85F, 1.45F);
				serverLevel.playSound(null, pos, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 0.75F, 0.55F);
				worldState.setDirty();
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (!(level.getBlockEntity(pos) instanceof MagicLampBlockEntity lamp)) {
			return InteractionResult.PASS;
		}
		ItemStack stack = lamp.asStack();
		if (player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		} else if (!player.getInventory().add(stack)) {
			player.displayClientMessage(Component.translatable("message.djinn.lamp_inventory_full"), true);
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.35F, 0.65F);
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel serverLevel && lamp.owner().isPresent()) {
			DjinnWorldState worldState = DjinnWorldState.get(serverLevel.getServer());
			worldState.player(lamp.owner().get()).clearLamp();
			worldState.player(lamp.owner().get()).setPendingLampReturn(false);
			ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(lamp.owner().get());
			if (owner != null) {
				owner.setRespawnPosition(serverLevel.dimension(), null, owner.getYRot(), true, true);
			}
			worldState.setDirty();
		}
		level.removeBlock(pos, false);
		level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.PLAYERS, 0.7F, 1.25F);
		level.playSound(null, pos, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.55F, 0.8F);
		return InteractionResult.SUCCESS;
	}
}
