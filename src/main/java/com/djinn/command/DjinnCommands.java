package com.djinn.command;

import com.djinn.item.DjinnLampStacks;
import com.djinn.block.ModBlocks;
import com.djinn.state.DjinnNbt;
import com.djinn.state.DjinnPlayerData;
import com.djinn.state.DjinnWorldState;
import com.djinn.state.ScheduledGameruleRevert;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class DjinnCommands {
	private static final long ONE_WEEK_MILLIS = 7L * 24L * 60L * 60L * 1000L;

	private DjinnCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(CommandManager.literal("djinn")
				.then(CommandManager.literal("bind")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.argument("player", EntityArgumentType.player())
								.executes(context -> bind(context.getSource(), EntityArgumentType.getPlayer(context, "player"), true))))
				.then(CommandManager.literal("unbind")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.argument("player", EntityArgumentType.player())
								.executes(context -> bind(context.getSource(), EntityArgumentType.getPlayer(context, "player"), false))))
				.then(CommandManager.literal("desert_dive")
						.executes(context -> desertDiveToggle(context.getSource()))
						.then(CommandManager.argument("enabled", BoolArgumentType.bool())
								.executes(context -> desertDive(context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
				.then(CommandManager.literal("sand_form")
						.executes(context -> sandForm(context.getSource())))
				.then(CommandManager.literal("wish")
						.then(CommandManager.literal("item")
								.then(CommandManager.argument("target", EntityArgumentType.player())
										.then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
												.executes(context -> wishItem(context.getSource(), EntityArgumentType.getPlayer(context, "target"), ItemStackArgumentType.getItemStackArgument(context, "item").createStack(1, false)))
												.then(CommandManager.argument("count", IntegerArgumentType.integer(1, 64))
														.executes(context -> wishItem(context.getSource(), EntityArgumentType.getPlayer(context, "target"), ItemStackArgumentType.getItemStackArgument(context, "item").createStack(IntegerArgumentType.getInteger(context, "count"), false)))))))
						.then(CommandManager.literal("origin")
								.then(CommandManager.argument("target", EntityArgumentType.player())
										.then(CommandManager.argument("origin", IdentifierArgumentType.identifier())
												.executes(context -> wishOrigin(context.getSource(), EntityArgumentType.getPlayer(context, "target"), IdentifierArgumentType.getIdentifier(context, "origin"))))))
						.then(CommandManager.literal("gamerule")
								.requires(source -> source.hasPermissionLevel(2))
								.then(CommandManager.argument("rule", StringArgumentType.word())
										.then(CommandManager.argument("value", StringArgumentType.word())
												.executes(context -> wishGamerule(context.getSource(), StringArgumentType.getString(context, "rule"), StringArgumentType.getString(context, "value"))))))
						.then(CommandManager.literal("sun_boost")
								.executes(context -> wishSunBoost(context.getSource()))))));
	}

	private static int bind(ServerCommandSource source, ServerPlayerEntity player, boolean enabled) {
		DjinnWorldState state = DjinnWorldState.get(source.getServer());
		DjinnPlayerData data = state.player(player.getUuid());
		data.setDjinn(enabled);
		if (enabled) {
			giveBoundLamp(player, data);
		}
		state.markDirty();
		player.getWorld().playSound(null, player.getBlockPos(), enabled ? SoundEvents.BLOCK_BEACON_ACTIVATE : SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.65F, enabled ? 1.35F : 0.75F);
		player.getWorld().playSound(null, player.getBlockPos(), enabled ? SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME : SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.55F, enabled ? 1.5F : 0.85F);
		source.sendFeedback(() -> Text.translatable(enabled ? "command.djinn.bound" : "command.djinn.unbound", player.getDisplayName()), true);
		return 1;
	}

	public static void giveBoundLamp(ServerPlayerEntity player, DjinnPlayerData data) {
		if (hasBoundLamp(player)) {
			return;
		}
		ItemStack lamp = new ItemStack(ModBlocks.MAGIC_LAMP);
		lamp = DjinnLampStacks.boundLamp(player, data);
		if (!player.getInventory().insertStack(lamp)) {
			data.setPendingLampReturn(true);
		}
	}

	private static boolean hasBoundLamp(ServerPlayerEntity player) {
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (stack.isOf(ModBlocks.MAGIC_LAMP) && DjinnNbt.owner(stack).map(player.getUuid()::equals).orElse(false)) {
				DjinnLampStacks.applyLampRules(stack);
				return true;
			}
		}
		return false;
	}

	private static int desertDive(ServerCommandSource source, boolean enabled) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		DjinnWorldState state = DjinnWorldState.get(source.getServer());
		DjinnPlayerData data = state.player(player.getUuid());
		if (!data.isDjinn()) {
			source.sendError(Text.translatable("message.djinn.not_djinn"));
			return 0;
		}
		if (!hasPlacedLamp(source.getServer(), data)) {
			source.sendError(Text.translatable("message.djinn.lamp_required"));
			return 0;
		}
		data.setDesertDiveToggled(enabled);
		state.markDirty();
		player.getWorld().playSound(null, player.getBlockPos(), enabled ? SoundEvents.BLOCK_SAND_PLACE : SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 0.65F, enabled ? 1.25F : 0.7F);
		source.sendFeedback(() -> Text.translatable(enabled ? "command.djinn.desert_dive_on" : "command.djinn.desert_dive_off"), false);
		return 1;
	}

	private static int desertDiveToggle(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		DjinnWorldState state = DjinnWorldState.get(source.getServer());
		DjinnPlayerData data = state.player(player.getUuid());
		if (!data.isDjinn()) {
			source.sendError(Text.translatable("message.djinn.not_djinn"));
			return 0;
		}
		if (!hasPlacedLamp(source.getServer(), data)) {
			source.sendError(Text.translatable("message.djinn.lamp_required"));
			return 0;
		}
		data.setDesertDiveToggled(!data.desertDiveToggled());
		state.markDirty();
		player.getWorld().playSound(null, player.getBlockPos(), data.desertDiveToggled() ? SoundEvents.BLOCK_SAND_PLACE : SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 0.65F, data.desertDiveToggled() ? 1.25F : 0.7F);
		source.sendFeedback(() -> Text.translatable(data.desertDiveToggled() ? "command.djinn.desert_dive_on" : "command.djinn.desert_dive_off"), false);
		return 1;
	}

	private static int sandForm(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		DjinnWorldState state = DjinnWorldState.get(source.getServer());
		DjinnPlayerData data = state.player(player.getUuid());
		if (!data.isDjinn()) {
			source.sendError(Text.translatable("message.djinn.not_djinn"));
			return 0;
		}
		if (!hasPlacedLamp(source.getServer(), data)) {
			source.sendError(Text.translatable("message.djinn.lamp_required"));
			return 0;
		}
		boolean activating = data.sandFormTicks() <= 0;
		data.setSandFormTicks(activating ? 20 * 12 : 0);
		state.markDirty();
		player.getWorld().playSound(null, player.getBlockPos(), activating ? SoundEvents.ENTITY_BLAZE_SHOOT : SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, activating ? 0.8F : 0.65F, activating ? 0.55F : 0.75F);
		player.getWorld().playSound(null, player.getBlockPos(), activating ? SoundEvents.ENTITY_EVOKER_CAST_SPELL : SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.45F, activating ? 0.7F : 0.45F);
		source.sendFeedback(() -> Text.translatable("command.djinn.sand_form"), false);
		return 1;
	}

	private static int wishItem(ServerCommandSource source, ServerPlayerEntity target, ItemStack stack) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity master = source.getPlayerOrThrow();
		DjinnPlayerData djinn = requireHeldLampDjinn(source, master);
		if (djinn == null || !spendWish(source, master, djinn)) {
			return 0;
		}
		if (!target.getInventory().insertStack(stack)) {
			target.dropItem(stack, false);
		}
		master.getWorld().playSound(null, master.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.65F, 1.7F);
		source.sendFeedback(() -> Text.translatable("command.djinn.wish_item", target.getDisplayName(), stack.getName()), true);
		return 1;
	}

	private static int wishOrigin(ServerCommandSource source, ServerPlayerEntity target, Identifier originId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity master = source.getPlayerOrThrow();
		DjinnPlayerData djinn = requireHeldLampDjinn(source, master);
		if (djinn == null || !spendWish(source, master, djinn)) {
			return 0;
		}
		if (originId.getNamespace().equals("djinn") && originId.getPath().equals("djinn")) {
			DjinnWorldState.get(source.getServer()).player(target.getUuid()).setDjinn(true);
			DjinnWorldState.get(source.getServer()).markDirty();
		}
		if (FabricLoader.getInstance().isModLoaded("origins")) {
			String command = "origin set " + target.getGameProfile().getName() + " origins:origin " + originId;
			source.getServer().getCommandManager().executeWithPrefix(source.getServer().getCommandSource(), command);
		}
		master.getWorld().playSound(null, master.getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.PLAYERS, 0.8F, 1.0F);
		source.sendFeedback(() -> Text.translatable("command.djinn.wish_origin", target.getDisplayName(), originId.toString()), true);
		return 1;
	}

	private static int wishGamerule(ServerCommandSource source, String rule, String value) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity master = source.getPlayerOrThrow();
		DjinnPlayerData djinn = requireHeldLampDjinn(source, master);
		if (djinn == null || !spendWish(source, master, djinn)) {
			return 0;
		}
		String previousValue = source.getServer().getGameRules().toNbt().getString(rule);
		source.getServer().getCommandManager().executeWithPrefix(source.getServer().getCommandSource(), "gamerule " + rule + " " + value);
		DjinnWorldState state = DjinnWorldState.get(source.getServer());
		state.gameruleReverts().add(new ScheduledGameruleRevert(rule, previousValue, System.currentTimeMillis() + ONE_WEEK_MILLIS));
		state.markDirty();
		master.getWorld().playSound(null, master.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.85F, 0.85F);
		source.sendFeedback(() -> Text.translatable("command.djinn.wish_gamerule", rule, value), true);
		return 1;
	}

	private static int wishSunBoost(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity master = source.getPlayerOrThrow();
		DjinnPlayerData djinn = requireHeldLampDjinn(source, master);
		if (djinn == null || !spendWish(source, master, djinn)) {
			return 0;
		}
		ItemStack token = new ItemStack(Items.GOLD_INGOT, 8);
		if (!master.getInventory().insertStack(token)) {
			master.dropItem(token, false);
		}
		master.getWorld().playSound(null, master.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.75F, 1.7F);
		source.sendFeedback(() -> Text.translatable("command.djinn.wish_sun_boost"), true);
		return 1;
	}

	private static DjinnPlayerData requireHeldLampDjinn(ServerCommandSource source, ServerPlayerEntity master) {
		ItemStack lamp = heldLamp(master);
		if (lamp.isEmpty()) {
			source.sendError(Text.translatable("command.djinn.no_lamp"));
			return null;
		}
		if (DjinnNbt.owner(lamp).isEmpty()) {
			source.sendError(Text.translatable("command.djinn.unbound_lamp"));
			return null;
		}
		DjinnWorldState state = DjinnWorldState.get(source.getServer());
		DjinnPlayerData djinn = state.player(DjinnNbt.owner(lamp).get());
		if (!djinn.isDjinn()) {
			source.sendError(Text.translatable("message.djinn.not_djinn"));
			return null;
		}
		return djinn;
	}

	private static boolean spendWish(ServerCommandSource source, ServerPlayerEntity master, DjinnPlayerData djinn) {
		if (!djinn.canWish()) {
			source.sendError(Text.translatable("command.djinn.no_wishes"));
			return false;
		}
		djinn.spendWish();
		ItemStack lamp = heldLamp(master);
		DjinnNbt.wishesUsed(lamp, djinn.wishesUsed());
		DjinnNbt.master(lamp, master.getUuid());
		master.getWorld().playSound(null, master.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 0.45F, 0.9F + djinn.wishesUsed() * 0.18F);
		DjinnWorldState.get(source.getServer()).markDirty();
		return true;
	}

	private static ItemStack heldLamp(ServerPlayerEntity player) {
		ItemStack main = player.getMainHandStack();
		if (DjinnNbt.owner(main).isPresent()) {
			return main;
		}
		ItemStack off = player.getOffHandStack();
		return DjinnNbt.owner(off).isPresent() ? off : ItemStack.EMPTY;
	}

	private static boolean hasPlacedLamp(net.minecraft.server.MinecraftServer server, DjinnPlayerData data) {
		if (data.lampPos() == null || data.lampWorld() == null) {
			return false;
		}
		for (net.minecraft.server.world.ServerWorld world : server.getWorlds()) {
			if (world.getRegistryKey().getValue().toString().equals(data.lampWorld()) && world.getBlockState(data.lampPos()).isOf(ModBlocks.MAGIC_LAMP_BLOCK)) {
				return true;
			}
		}
		return false;
	}
}
