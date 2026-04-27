package com.djinn.item;

import com.djinn.block.MagicLampBlockEntity;
import com.djinn.network.DjinnNetworking;
import com.djinn.particle.ModParticles;
import com.djinn.state.DjinnNbt;
import com.djinn.state.DjinnPlayerData;
import com.djinn.state.DjinnWorldState;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;

public class MagicLampItem extends BlockItem {
	public MagicLampItem(Block block, Settings settings) {
		super(block, settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, net.minecraft.entity.player.PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!(user instanceof ServerPlayerEntity player)) {
			return TypedActionResult.success(stack);
		}
		Optional<UUID> ownerId = DjinnNbt.owner(stack);
		if (ownerId.isEmpty()) {
			DjinnPlayerData data = DjinnWorldState.get(player.getServer()).player(player.getUuid());
			if (data.isDjinn()) {
				DjinnNbt.owner(stack, player.getUuid());
				DjinnNbt.ownerName(stack, player.getGameProfile().getName());
				DjinnLampStacks.applyLampRules(stack);
				world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.75F, 1.6F);
				world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_GOLD, SoundCategory.PLAYERS, 0.5F, 1.15F);
				player.sendMessage(Text.translatable("message.djinn.lamp_bound"), true);
				return TypedActionResult.success(stack);
			}
			return TypedActionResult.pass(stack);
		}
		ServerPlayerEntity djinn = player.getServer().getPlayerManager().getPlayer(ownerId.get());
		if (djinn == null) {
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.55F, 0.55F);
			player.sendMessage(Text.translatable("message.djinn.owner_offline"), true);
			return TypedActionResult.fail(stack);
		}
		if (djinn.getUuid().equals(player.getUuid())) {
			return TypedActionResult.pass(stack);
		}
		DjinnNbt.master(stack, player.getUuid());
		DjinnWorldState state = DjinnWorldState.get(player.getServer());
		DjinnPlayerData djinnData = state.player(djinn.getUuid());
		djinnData.setLampMaster(player.getUuid());
		state.markDirty();
		ServerWorld targetWorld = player.getServerWorld();
		djinn.teleport(targetWorld, player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
		targetWorld.spawnParticles(ModParticles.GOLDEN_SMOKE, player.getX(), player.getBodyY(0.5), player.getZ(), 48, 0.55, 0.75, 0.55, 0.025);
		targetWorld.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.75F, 1.35F);
		targetWorld.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.PLAYERS, 0.65F, 0.85F);
		djinn.getServerWorld().playSound(null, djinn.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.65F, 0.75F);
		player.sendMessage(Text.translatable("message.djinn.summoned"), true);
		DjinnNetworking.openWishMenu(player, djinnData);
		return TypedActionResult.success(stack);
	}

	@Override
	protected boolean postPlacement(net.minecraft.util.math.BlockPos pos, World world, net.minecraft.entity.player.PlayerEntity player, ItemStack stack, net.minecraft.block.BlockState state) {
		boolean placed = super.postPlacement(pos, world, player, stack, state);
		if (!world.isClient && world.getBlockEntity(pos) instanceof MagicLampBlockEntity lamp) {
			lamp.readFromStack(stack);
		}
		return placed;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (context.getPlayer() instanceof ServerPlayerEntity player) {
			ItemStack stack = context.getStack();
			DjinnPlayerData data = DjinnWorldState.get(player.getServer()).player(player.getUuid());
			Optional<UUID> owner = DjinnNbt.owner(stack);
			if (!data.isDjinn() || owner.map(id -> !id.equals(player.getUuid())).orElse(false)) {
				if (owner.isPresent() && !owner.get().equals(player.getUuid())) {
					return use(context.getWorld(), player, context.getHand()).getResult();
				}
				context.getWorld().playSound(null, context.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.35F, 0.65F);
				player.sendMessage(Text.translatable("message.djinn.lamp_only_djinn_place"), true);
				return ActionResult.FAIL;
			}
			DjinnLampStacks.applyLampRules(stack);
		}
		return super.useOnBlock(context);
	}
}
