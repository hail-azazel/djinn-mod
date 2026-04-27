package com.djinn.event;

import com.djinn.block.ModBlocks;
import com.djinn.command.DjinnCommands;
import com.djinn.effect.ModEffects;
import com.djinn.item.DjinnLampStacks;
import com.djinn.particle.ModParticles;
import com.djinn.state.DjinnPlayerData;
import com.djinn.state.DjinnWorldState;
import com.djinn.state.ScheduledGameruleRevert;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class DjinnEvents {
	private static final UUID DJINN_FLIGHT_MODIFIER_ID = UUID.fromString("c81d1eda-5851-4a2b-a740-a8f26f8c8074");
	private static final float VANILLA_FLY_SPEED = 0.05F;
	private static final float DJINN_FLY_SPEED = 0.075F;
	private static final EntityAttributeModifier DJINN_FLIGHT_MODIFIER = new EntityAttributeModifier(
			DJINN_FLIGHT_MODIFIER_ID,
			"Djinn creative flight",
			0.35D,
			EntityAttributeModifier.Operation.MULTIPLY_TOTAL
	);

	private DjinnEvents() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(DjinnEvents::serverTick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(DjinnEvents::allowDamage);
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> blockSleep(player, world, hand, hitResult.getBlockPos()));
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			if (!alive && DjinnWorldState.get(newPlayer.getServer()).player(oldPlayer.getUuid()).isDjinn()) {
				newPlayer.getInventory().clone(oldPlayer.getInventory());
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> refreshFlight(handler.getPlayer(), DjinnWorldState.get(server).player(handler.getPlayer().getUuid())));
	}

	private static void serverTick(MinecraftServer server) {
		DjinnWorldState state = DjinnWorldState.get(server);
		revertGamerules(server, state);
		if (server.getTicks() % 40 == 0) {
			trackLamps(server, state);
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			DjinnPlayerData data = state.player(player.getUuid());
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
		state.markDirty();
	}

	private static void trackLamps(MinecraftServer server, DjinnWorldState state) {
		for (DjinnPlayerData data : state.players()) {
			if (!data.isDjinn()) {
				continue;
			}
			ServerPlayerEntity owner = server.getPlayerManager().getPlayer(data.playerId());
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
		for (ServerWorld world : server.getWorlds()) {
			if (world.getRegistryKey().getValue().toString().equals(data.lampWorld()) && world.getBlockState(data.lampPos()).isOf(ModBlocks.MAGIC_LAMP_BLOCK)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isLampInAnyOnlineInventory(MinecraftServer server, DjinnPlayerData data) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			for (int slot = 0; slot < player.getInventory().size(); slot++) {
				ItemStack stack = player.getInventory().getStack(slot);
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
				&& Registries.ITEM.getId(stack.getItem()).equals(com.djinn.DjinnOriginMod.id("magic_lamp"))
				&& com.djinn.state.DjinnNbt.owner(stack).map(data.playerId()::equals).orElse(false);
	}

	private static void tryReturnLamp(ServerPlayerEntity owner, DjinnPlayerData data, DjinnWorldState state) {
		if (owner == null) {
			return;
		}
		ItemStack lamp = DjinnLampStacks.boundLamp(owner, data);
		if (owner.getInventory().insertStack(lamp)) {
			data.setPendingLampReturn(false);
			owner.getWorld().playSound(null, owner.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_GOLD, SoundCategory.PLAYERS, 0.6F, 1.1F);
			owner.getWorld().playSound(null, owner.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.5F, 1.25F);
			state.markDirty();
		}
	}

	private static void syncOriginsSelection(MinecraftServer server, ServerPlayerEntity player, DjinnPlayerData data) {
		if (data.isDjinn() || !FabricLoader.getInstance().isModLoaded("origins") || server.getTicks() % 100 != 0) {
			return;
		}
		String command = "origin has origin " + player.getGameProfile().getName() + " origins:origin djinn:djinn";
		if (server.getCommandManager().executeWithPrefix(server.getCommandSource(), command) > 0) {
			data.setDjinn(true);
			DjinnCommands.giveBoundLamp(player, data);
			player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.65F, 1.35F);
			player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.55F, 1.5F);
			player.sendMessage(Text.translatable("command.djinn.bound", player.getDisplayName()), false);
		}
	}

	private static void revertGamerules(MinecraftServer server, DjinnWorldState state) {
		long time = System.currentTimeMillis();
		Iterator<ScheduledGameruleRevert> iterator = state.gameruleReverts().iterator();
		while (iterator.hasNext()) {
			ScheduledGameruleRevert revert = iterator.next();
			if (time >= revert.executeAt()) {
				server.getCommandManager().executeWithPrefix(server.getCommandSource(), "gamerule " + revert.rule() + " " + revert.previousValue());
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.35F, 1.4F);
				}
				iterator.remove();
			}
		}
	}

	private static void refreshFlight(ServerPlayerEntity player, DjinnPlayerData data) {
		if (!data.isDjinn()) {
			disableDjinnFlight(player);
			return;
		}
		boolean changed = false;
		if (!player.getAbilities().allowFlying) {
			player.getAbilities().allowFlying = true;
			changed = true;
		}
		if (Math.abs(player.getAbilities().getFlySpeed() - DJINN_FLY_SPEED) > 0.001F) {
			player.getAbilities().setFlySpeed(DJINN_FLY_SPEED);
			changed = true;
		}
		EntityAttributeInstance flyingSpeed = player.getAttributeInstance(EntityAttributes.GENERIC_FLYING_SPEED);
		if (flyingSpeed != null && flyingSpeed.getModifier(DJINN_FLIGHT_MODIFIER_ID) == null) {
			flyingSpeed.addPersistentModifier(DJINN_FLIGHT_MODIFIER);
			changed = true;
		}
		if (changed) {
			player.sendAbilitiesUpdate();
		}
	}

	private static void disableDjinnFlight(ServerPlayerEntity player) {
		removeFlightModifier(player);
		if (!player.isCreative() && !player.isSpectator()) {
			boolean changed = player.getAbilities().allowFlying || player.getAbilities().flying || Math.abs(player.getAbilities().getFlySpeed() - VANILLA_FLY_SPEED) > 0.001F;
			player.getAbilities().allowFlying = false;
			player.getAbilities().flying = false;
			player.getAbilities().setFlySpeed(VANILLA_FLY_SPEED);
			if (changed) {
				player.sendAbilitiesUpdate();
			}
		}
	}

	private static void removeFlightModifier(ServerPlayerEntity player) {
		EntityAttributeInstance flyingSpeed = player.getAttributeInstance(EntityAttributes.GENERIC_FLYING_SPEED);
		if (flyingSpeed != null && flyingSpeed.getModifier(DJINN_FLIGHT_MODIFIER_ID) != null) {
			flyingSpeed.removeModifier(DJINN_FLIGHT_MODIFIER_ID);
		}
	}

	private static void enforceArmorLimit(ServerPlayerEntity player) {
		dropArmor(player, EquipmentSlot.LEGS);
		dropArmor(player, EquipmentSlot.FEET);
	}

	private static void dropArmor(ServerPlayerEntity player, EquipmentSlot slot) {
		ItemStack stack = player.getEquippedStack(slot);
		if (!stack.isEmpty()) {
			player.equipStack(slot, ItemStack.EMPTY);
			player.dropItem(stack, true);
			player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 0.55F, 0.55F);
			player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.35F, 0.65F);
			player.sendMessage(Text.translatable("message.djinn.no_leg_armor"), true);
		}
	}

	private static void tickSandForm(ServerPlayerEntity player, DjinnPlayerData data) {
		if (data.sandFormTicks() <= 0) {
			return;
		}
		data.tickSandForm();
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 12, 0, false, false, true));
		player.addStatusEffect(new StatusEffectInstance(ModEffects.SAND_VEIL, 12, 0, false, false, false));
		ServerWorld world = player.getServerWorld();
		spawnTornadoAccents(player, world);
		if (world.getTime() % 10 != 0) {
			return;
		}
		Box area = player.getBoundingBox().expand(3.0);
		List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, area, entity -> entity != player && entity.isAlive());
		for (LivingEntity target : targets) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 45, 0));
			target.damage(world.getDamageSources().magic(), 1.0F);
		}
	}

	private static void spawnTornadoAccents(ServerPlayerEntity player, ServerWorld world) {
		if (world.getTime() % 3 != 0) {
			return;
		}
		double age = player.age * 0.38D;
		for (int layer = 0; layer < 4; layer++) {
			double height = 0.18D + layer * 0.32D;
			double radius = 0.35D + layer * 0.13D;
			double angle = age + layer * 1.35D;
			double x = player.getX() + Math.cos(angle) * radius;
			double z = player.getZ() + Math.sin(angle) * radius;
			world.spawnParticles(ModParticles.SANDSTORM, x, player.getY() + height, z, 1, 0.04, 0.04, 0.04, 0.018);
			if (layer % 2 == 0) {
				world.spawnParticles(ModParticles.GOLDEN_SMOKE, x, player.getY() + height, z, 1, 0.025, 0.025, 0.025, 0.01);
			}
		}
	}

	private static void tickDesertDive(ServerPlayerEntity player, DjinnPlayerData data) {
		boolean lowHealth = player.getHealth() <= player.getMaxHealth() * 0.25F;
		boolean active = lowHealth || data.desertDiveToggled() && canDesertDive(player);
		if (!active) {
			return;
		}
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 30, 0, true, false, true));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 30, lowHealth ? 2 : 1, true, false, true));
		if (player.getWorld().getTime() % 8 == 0) {
			player.getServerWorld().spawnParticles(ModParticles.GOLDEN_SMOKE, player.getX(), player.getY() + 0.1, player.getZ(), 8, 0.35, 0.05, 0.35, 0.01);
		}
		if (player.getWorld().getTime() % 40 == 0) {
			player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_SAND_STEP, SoundCategory.PLAYERS, 0.18F, lowHealth ? 1.55F : 1.25F);
		}
	}

	private static boolean canDesertDive(ServerPlayerEntity player) {
		BlockPos pos = player.getBlockPos();
		boolean hotDryBiome = player.getWorld().getBiome(pos).getKey()
				.map(key -> {
					String path = key.getValue().getPath();
					return path.contains("desert") || path.contains("badlands") || path.contains("savanna");
				})
				.orElse(false);
		BlockState below = player.getWorld().getBlockState(pos.down());
		boolean sandyBlock = below.isOf(Blocks.SAND) || below.isOf(Blocks.RED_SAND) || below.isOf(Blocks.SANDSTONE) || below.isOf(Blocks.RED_SANDSTONE);
		return sandyBlock || hotDryBiome;
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (entity instanceof ServerPlayerEntity player) {
			DjinnPlayerData data = DjinnWorldState.get(player.getServer()).player(player.getUuid());
			if (data.isDjinn() && data.sandFormTicks() > 0) {
				return false;
			}
			Entity attacker = source.getAttacker();
			if (attacker instanceof ServerPlayerEntity attackerPlayer) {
				DjinnPlayerData attackerData = DjinnWorldState.get(player.getServer()).player(attackerPlayer.getUuid());
				if (attackerData.isDjinn() && attackerData.lampMaster() != null && attackerData.lampMaster().equals(player.getUuid())) {
					return false;
				}
			}
		}
		return true;
	}

	private static ActionResult blockSleep(net.minecraft.entity.player.PlayerEntity player, World world, Hand hand, BlockPos pos) {
		if (player.isSneaking() && player.getStackInHand(hand).getItem() instanceof BlockItem) {
			return ActionResult.PASS;
		}
		if (!world.isClient && world.getBlockState(pos).getBlock() instanceof BedBlock && player instanceof ServerPlayerEntity serverPlayer) {
			if (DjinnWorldState.get(serverPlayer.getServer()).player(serverPlayer.getUuid()).isDjinn()) {
				serverPlayer.sendMessage(Text.translatable("message.djinn.no_sleep"), true);
				world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.4F, 0.8F);
				return ActionResult.FAIL;
			}
		}
		return ActionResult.PASS;
	}

}
