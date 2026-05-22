package com.djinn.event;

import com.djinn.DjinnOriginMod;
import com.djinn.block.ModBlocks;
import com.djinn.command.DjinnCommands;
import com.djinn.effect.ModEffects;
import com.djinn.item.DjinnLampStacks;
import com.djinn.particle.ModParticles;
import com.djinn.state.DjinnPlayerData;
import com.djinn.state.DjinnWorldState;
import com.djinn.state.ScheduledGameruleRevert;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.List;

public final class DjinnEvents {
	private static final ResourceLocation DJINN_FLIGHT_MODIFIER_ID = DjinnOriginMod.id("creative_flight");
	private static final float VANILLA_FLY_SPEED = 0.05F;
	private static final float DJINN_FLY_SPEED = 0.075F;
	private static final AttributeModifier DJINN_FLIGHT_MODIFIER = new AttributeModifier(
			DJINN_FLIGHT_MODIFIER_ID,
			0.35D,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
	);

	private DjinnEvents() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(DjinnEvents::serverTick);
		NeoForge.EVENT_BUS.addListener(DjinnEvents::allowDamage);
		NeoForge.EVENT_BUS.addListener(DjinnEvents::blockSleep);
		NeoForge.EVENT_BUS.addListener(DjinnEvents::copyInventoryOnDeath);
		NeoForge.EVENT_BUS.addListener(DjinnEvents::refreshFlightOnLogin);
	}

	private static void serverTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		DjinnWorldState state = DjinnWorldState.get(server);
		revertGamerules(server, state);
		if (server.getTickCount() % 40 == 0) {
			trackLamps(server, state);
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			DjinnPlayerData data = state.player(player.getUUID());
			syncOriginsSelection(server, player, data);
			if (!data.isDjinn()) {
				removeFlightModifier(player);
				continue;
			}
			boolean hasPlacedLamp = isLampPlaced(server, data);
			if (hasPlacedLamp) {
				refreshFlight(player, data);
			} else {
				disableDjinnFlight(player);
				data.setSandFormTicks(0);
			}
			enforceArmorLimit(player);
			if (hasPlacedLamp) {
				tickSandForm(player, data);
				tickDesertDive(player, data);
			}
		}
		state.setDirty();
	}

	private static void copyInventoryOnDeath(PlayerEvent.Clone event) {
		if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer newPlayer) || !(event.getOriginal() instanceof ServerPlayer oldPlayer)) {
			return;
		}
		if (DjinnWorldState.get(newPlayer.getServer()).player(oldPlayer.getUUID()).isDjinn()) {
			newPlayer.getInventory().replaceWith(oldPlayer.getInventory());
		}
	}

	private static void refreshFlightOnLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			refreshFlight(player, DjinnWorldState.get(player.getServer()).player(player.getUUID()));
		}
	}

	private static void trackLamps(MinecraftServer server, DjinnWorldState state) {
		for (DjinnPlayerData data : state.players()) {
			if (!data.isDjinn()) {
				continue;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(data.playerId());
			if (data.pendingLampReturn()) {
				tryReturnLamp(owner, data, state);
				continue;
			}
			if (isLampPlaced(server, data) || isLampInAnyOnlineInventory(server, data)) {
				continue;
			}
			data.setPendingLampReturn(true);
			tryReturnLamp(owner, data, state);
		}
	}

	private static boolean isLampPlaced(MinecraftServer server, DjinnPlayerData data) {
		if (data.lampPos() == null || data.lampWorld() == null) {
			return false;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.dimension().location().toString().equals(data.lampWorld()) && level.getBlockState(data.lampPos()).is(ModBlocks.MAGIC_LAMP_BLOCK.get())) {
				return true;
			}
		}
		return false;
	}

	private static boolean isLampInAnyOnlineInventory(MinecraftServer server, DjinnPlayerData data) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				ItemStack stack = player.getInventory().getItem(slot);
				if (isOwnersLamp(stack, data)) {
					DjinnLampStacks.applyLampRules(stack);
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isOwnersLamp(ItemStack stack, DjinnPlayerData data) {
		return !stack.isEmpty()
				&& stack.is(ModBlocks.MAGIC_LAMP.get())
				&& com.djinn.state.DjinnNbt.owner(stack).map(data.playerId()::equals).orElse(false);
	}

	private static void tryReturnLamp(ServerPlayer owner, DjinnPlayerData data, DjinnWorldState state) {
		if (owner == null) {
			return;
		}
		ItemStack lamp = DjinnLampStacks.boundLamp(owner, data);
		if (owner.getInventory().add(lamp)) {
			data.setPendingLampReturn(false);
			owner.level().playSound(null, owner.blockPosition(), SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.PLAYERS, 0.6F, 1.1F);
			owner.level().playSound(null, owner.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.25F);
			state.setDirty();
		}
	}

	private static void syncOriginsSelection(MinecraftServer server, ServerPlayer player, DjinnPlayerData data) {
		if (data.isDjinn() || !ModList.get().isLoaded("origins") || server.getTickCount() % 100 != 0) {
			return;
		}
		String command = "origin has origin " + player.getGameProfile().getName() + " origins:origin djinn:djinn";
		int result;
		try {
			result = server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack().withSuppressedOutput());
		} catch (CommandSyntaxException exception) {
			return;
		}
		if (result > 0) {
			data.setDjinn(true);
			DjinnCommands.giveBoundLamp(player, data);
			player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.65F, 1.35F);
			player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.55F, 1.5F);
			player.sendSystemMessage(Component.translatable("command.djinn.bound", player.getDisplayName()));
		}
	}

	private static void revertGamerules(MinecraftServer server, DjinnWorldState state) {
		long time = System.currentTimeMillis();
		Iterator<ScheduledGameruleRevert> iterator = state.gameruleReverts().iterator();
		while (iterator.hasNext()) {
			ScheduledGameruleRevert revert = iterator.next();
			if (time >= revert.executeAt()) {
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule " + revert.rule() + " " + revert.previousValue());
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.35F, 1.4F);
				}
				iterator.remove();
			}
		}
	}

	private static void refreshFlight(ServerPlayer player, DjinnPlayerData data) {
		if (!data.isDjinn()) {
			disableDjinnFlight(player);
			return;
		}
		boolean changed = false;
		if (!player.getAbilities().mayfly) {
			player.getAbilities().mayfly = true;
			changed = true;
		}
		if (Math.abs(player.getAbilities().getFlyingSpeed() - DJINN_FLY_SPEED) > 0.001F) {
			player.getAbilities().setFlyingSpeed(DJINN_FLY_SPEED);
			changed = true;
		}
		AttributeInstance flyingSpeed = player.getAttribute(Attributes.FLYING_SPEED);
		if (flyingSpeed != null && flyingSpeed.getModifier(DJINN_FLIGHT_MODIFIER_ID) == null) {
			flyingSpeed.addOrReplacePermanentModifier(DJINN_FLIGHT_MODIFIER);
			changed = true;
		}
		if (changed) {
			player.onUpdateAbilities();
		}
	}

	private static void disableDjinnFlight(ServerPlayer player) {
		removeFlightModifier(player);
		if (!player.isCreative() && !player.isSpectator()) {
			boolean changed = player.getAbilities().mayfly || player.getAbilities().flying || Math.abs(player.getAbilities().getFlyingSpeed() - VANILLA_FLY_SPEED) > 0.001F;
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
			player.getAbilities().setFlyingSpeed(VANILLA_FLY_SPEED);
			if (changed) {
				player.onUpdateAbilities();
			}
		}
	}

	private static void removeFlightModifier(ServerPlayer player) {
		AttributeInstance flyingSpeed = player.getAttribute(Attributes.FLYING_SPEED);
		if (flyingSpeed != null && flyingSpeed.getModifier(DJINN_FLIGHT_MODIFIER_ID) != null) {
			flyingSpeed.removeModifier(DJINN_FLIGHT_MODIFIER_ID);
		}
	}

	private static void enforceArmorLimit(ServerPlayer player) {
		dropArmor(player, EquipmentSlot.LEGS);
		dropArmor(player, EquipmentSlot.FEET);
	}

	private static void dropArmor(ServerPlayer player, EquipmentSlot slot) {
		ItemStack stack = player.getItemBySlot(slot);
		if (!stack.isEmpty()) {
			player.setItemSlot(slot, ItemStack.EMPTY);
			player.drop(stack, true);
			player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.55F, 0.55F);
			player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.35F, 0.65F);
			player.displayClientMessage(Component.translatable("message.djinn.no_leg_armor"), true);
		}
	}

	private static void tickSandForm(ServerPlayer player, DjinnPlayerData data) {
		if (data.sandFormTicks() <= 0) {
			return;
		}
		data.tickSandForm();
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 12, 0, false, false, true));
		player.addEffect(new MobEffectInstance(ModEffects.sandVeil(), 12, 0, false, false, false));
		ServerLevel level = player.serverLevel();
		spawnTornadoAccents(player, level);
		if (level.getGameTime() % 10 != 0) {
			return;
		}
		AABB area = player.getBoundingBox().inflate(3.0);
		List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity != player && entity.isAlive());
		for (LivingEntity target : targets) {
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
			target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 45, 0));
			target.hurt(level.damageSources().magic(), 1.0F);
		}
	}

	private static void spawnTornadoAccents(ServerPlayer player, ServerLevel level) {
		if (level.getGameTime() % 3 != 0) {
			return;
		}
		double age = player.tickCount * 0.38D;
		for (int layer = 0; layer < 4; layer++) {
			double height = 0.18D + layer * 0.32D;
			double radius = 0.35D + layer * 0.13D;
			double angle = age + layer * 1.35D;
			double x = player.getX() + Math.cos(angle) * radius;
			double z = player.getZ() + Math.sin(angle) * radius;
			level.sendParticles(ModParticles.SANDSTORM.get(), x, player.getY() + height, z, 1, 0.04, 0.04, 0.04, 0.018);
			if (layer % 2 == 0) {
				level.sendParticles(ModParticles.GOLDEN_SMOKE.get(), x, player.getY() + height, z, 1, 0.025, 0.025, 0.025, 0.01);
			}
		}
	}

	private static void tickDesertDive(ServerPlayer player, DjinnPlayerData data) {
		boolean lowHealth = player.getHealth() <= player.getMaxHealth() * 0.25F;
		boolean active = lowHealth || data.desertDiveToggled() && canDesertDive(player);
		if (!active) {
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, true, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, lowHealth ? 2 : 1, true, false, true));
		if (player.level().getGameTime() % 8 == 0) {
			player.serverLevel().sendParticles(ModParticles.GOLDEN_SMOKE.get(), player.getX(), player.getY() + 0.1, player.getZ(), 8, 0.35, 0.05, 0.35, 0.01);
		}
		if (player.level().getGameTime() % 40 == 0) {
			player.level().playSound(null, player.blockPosition(), SoundEvents.SAND_STEP, SoundSource.PLAYERS, 0.18F, lowHealth ? 1.55F : 1.25F);
		}
	}

	private static boolean canDesertDive(ServerPlayer player) {
		BlockPos pos = player.blockPosition();
		boolean hotDryBiome = player.level().getBiome(pos).unwrapKey()
				.map(key -> {
					String path = key.location().getPath();
					return path.contains("desert") || path.contains("badlands") || path.contains("savanna");
				})
				.orElse(false);
		BlockState below = player.level().getBlockState(pos.below());
		boolean sandyBlock = below.is(Blocks.SAND) || below.is(Blocks.RED_SAND) || below.is(Blocks.SANDSTONE) || below.is(Blocks.RED_SANDSTONE);
		return sandyBlock || hotDryBiome;
	}

	private static void allowDamage(LivingIncomingDamageEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			DjinnPlayerData data = DjinnWorldState.get(player.getServer()).player(player.getUUID());
			if (data.isDjinn() && data.sandFormTicks() > 0) {
				event.setCanceled(true);
				return;
			}
			DamageSource source = event.getSource();
			Entity attacker = source.getEntity();
			if (attacker instanceof ServerPlayer attackerPlayer) {
				DjinnPlayerData attackerData = DjinnWorldState.get(player.getServer()).player(attackerPlayer.getUUID());
				if (attackerData.isDjinn() && attackerData.lampMaster() != null && attackerData.lampMaster().equals(player.getUUID())) {
					event.setCanceled(true);
				}
			}
		}
	}

	private static void blockSleep(PlayerInteractEvent.RightClickBlock event) {
		Player player = event.getEntity();
		Level level = event.getLevel();
		BlockPos pos = event.getPos();
		if (player.isShiftKeyDown() && player.getItemInHand(event.getHand()).getItem() instanceof BlockItem) {
			return;
		}
		if (!level.isClientSide && level.getBlockState(pos).getBlock() instanceof BedBlock && player instanceof ServerPlayer serverPlayer) {
			if (DjinnWorldState.get(serverPlayer.getServer()).player(serverPlayer.getUUID()).isDjinn()) {
				serverPlayer.displayClientMessage(Component.translatable("message.djinn.no_sleep"), true);
				level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 0.8F);
				event.setCancellationResult(InteractionResult.FAIL);
				event.setCanceled(true);
			}
		}
	}
}
