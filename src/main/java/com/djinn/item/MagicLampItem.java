package com.djinn.item;

import com.djinn.block.MagicLampBlockEntity;
import com.djinn.network.DjinnNetworking;
import com.djinn.particle.ModParticles;
import com.djinn.state.DjinnNbt;
import com.djinn.state.DjinnPlayerData;
import com.djinn.state.DjinnWorldState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class MagicLampItem extends BlockItem {
	public MagicLampItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);
		if (!(user instanceof ServerPlayer player)) {
			return InteractionResultHolder.success(stack);
		}
		Optional<UUID> ownerId = DjinnNbt.owner(stack);
		if (ownerId.isEmpty()) {
			DjinnPlayerData data = DjinnWorldState.get(player.getServer()).player(player.getUUID());
			if (data.isDjinn()) {
				DjinnNbt.owner(stack, player.getUUID());
				DjinnNbt.ownerName(stack, player.getGameProfile().getName());
				DjinnLampStacks.applyLampRules(stack);
				level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.75F, 1.6F);
				level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.PLAYERS, 0.5F, 1.15F);
				player.displayClientMessage(Component.translatable("message.djinn.lamp_bound"), true);
				return InteractionResultHolder.success(stack);
			}
			return InteractionResultHolder.pass(stack);
		}
		ServerPlayer djinn = player.getServer().getPlayerList().getPlayer(ownerId.get());
		if (djinn == null) {
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.55F, 0.55F);
			player.displayClientMessage(Component.translatable("message.djinn.owner_offline"), true);
			return InteractionResultHolder.fail(stack);
		}
		if (djinn.getUUID().equals(player.getUUID())) {
			return InteractionResultHolder.pass(stack);
		}
		DjinnNbt.master(stack, player.getUUID());
		DjinnWorldState state = DjinnWorldState.get(player.getServer());
		DjinnPlayerData djinnData = state.player(djinn.getUUID());
		djinnData.setLampMaster(player.getUUID());
		state.setDirty();
		ServerLevel targetWorld = player.serverLevel();
		djinn.teleportTo(targetWorld, player.getX(), player.getY(), player.getZ(), Set.of(), player.getYRot(), player.getXRot());
		targetWorld.sendParticles(ModParticles.GOLDEN_SMOKE.get(), player.getX(), player.getY(0.5), player.getZ(), 48, 0.55, 0.75, 0.55, 0.025);
		targetWorld.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.75F, 1.35F);
		targetWorld.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.65F, 0.85F);
		djinn.serverLevel().playSound(null, djinn.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.65F, 0.75F);
		player.displayClientMessage(Component.translatable("message.djinn.summoned"), true);
		DjinnNetworking.openWishMenu(player, djinnData);
		return InteractionResultHolder.success(stack);
	}

	@Override
	protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
		boolean placed = super.placeBlock(context, state);
		Level level = context.getLevel();
		if (!level.isClientSide && level.getBlockEntity(context.getClickedPos()) instanceof MagicLampBlockEntity lamp) {
			lamp.readFromStack(context.getItemInHand());
		}
		return placed;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getPlayer() instanceof ServerPlayer player) {
			ItemStack stack = context.getItemInHand();
			DjinnPlayerData data = DjinnWorldState.get(player.getServer()).player(player.getUUID());
			Optional<UUID> owner = DjinnNbt.owner(stack);
			if (!data.isDjinn() || owner.map(id -> !id.equals(player.getUUID())).orElse(false)) {
				if (owner.isPresent() && !owner.get().equals(player.getUUID())) {
					return use(context.getLevel(), player, context.getHand()).getResult();
				}
				context.getLevel().playSound(null, context.getClickedPos(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.35F, 0.65F);
				player.displayClientMessage(Component.translatable("message.djinn.lamp_only_djinn_place"), true);
				return InteractionResult.FAIL;
			}
			DjinnLampStacks.applyLampRules(stack);
		}
		return super.useOn(context);
	}
}
