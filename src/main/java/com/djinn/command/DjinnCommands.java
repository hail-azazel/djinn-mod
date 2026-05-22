package com.djinn.command;

import com.djinn.DjinnOriginMod;
import com.djinn.block.ModBlocks;
import com.djinn.item.DjinnLampStacks;
import com.djinn.state.DjinnNbt;
import com.djinn.state.DjinnPlayerData;
import com.djinn.state.DjinnWorldState;
import com.djinn.state.ScheduledGameruleRevert;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class DjinnCommands {
	private static final long ONE_WEEK_MILLIS = 7L * 24L * 60L * 60L * 1000L;

	private DjinnCommands() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(DjinnCommands::registerCommands);
	}

	private static void registerCommands(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("djinn")
				.then(Commands.literal("bind")
						.requires(source -> source.hasPermission(2))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(context -> bind(context.getSource(), EntityArgument.getPlayer(context, "player"), true))))
				.then(Commands.literal("unbind")
						.requires(source -> source.hasPermission(2))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(context -> bind(context.getSource(), EntityArgument.getPlayer(context, "player"), false))))
				.then(Commands.literal("desert_dive")
						.executes(context -> desertDiveToggle(context.getSource()))
						.then(Commands.argument("enabled", BoolArgumentType.bool())
								.executes(context -> desertDive(context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
				.then(Commands.literal("sand_form")
						.executes(context -> sandForm(context.getSource())))
				.then(Commands.literal("wish")
						.then(Commands.literal("item")
								.then(Commands.argument("target", EntityArgument.player())
										.then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
												.executes(context -> wishItem(context.getSource(), EntityArgument.getPlayer(context, "target"), ItemArgument.getItem(context, "item").createItemStack(1, false)))
												.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
														.executes(context -> wishItem(context.getSource(), EntityArgument.getPlayer(context, "target"), ItemArgument.getItem(context, "item").createItemStack(IntegerArgumentType.getInteger(context, "count"), false)))))))
						.then(Commands.literal("origin")
								.then(Commands.argument("target", EntityArgument.player())
										.then(Commands.argument("origin", ResourceLocationArgument.id())
												.executes(context -> wishOrigin(context.getSource(), EntityArgument.getPlayer(context, "target"), ResourceLocationArgument.getId(context, "origin"))))))
						.then(Commands.literal("gamerule")
								.requires(source -> source.hasPermission(2))
								.then(Commands.argument("rule", StringArgumentType.word())
										.then(Commands.argument("value", StringArgumentType.word())
												.executes(context -> wishGamerule(context.getSource(), StringArgumentType.getString(context, "rule"), StringArgumentType.getString(context, "value"))))))
						.then(Commands.literal("sun_boost")
								.executes(context -> wishSunBoost(context.getSource())))));
	}

	private static int bind(CommandSourceStack source, ServerPlayer player, boolean enabled) {
		DjinnWorldState state = DjinnWorldState.get(source.getServer());
		DjinnPlayerData data = state.player(player.getUUID());
		data.setDjinn(enabled);
		if (enabled) {
			giveBoundLamp(player, data);
		}
		state.setDirty();
		player.level().playSound(null, player.blockPosition(), enabled ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.65F, enabled ? 1.35F : 0.75F);
		player.level().playSound(null, player.blockPosition(), enabled ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.55F, enabled ? 1.5F : 0.85F);
		source.sendSuccess(() -> Component.translatable(enabled ? "command.djinn.bound" : "command.djinn.unbound", player.getDisplayName()), true);
		return 1;
	}

	public static void giveBoundLamp(ServerPlayer player, DjinnPlayerData data) {
		if (hasBoundLamp(player)) {
			return;
		}
		ItemStack lamp = DjinnLampStacks.boundLamp(player, data);
		if (!player.getInventory().add(lamp)) {
			data.setPendingLampReturn(true);
		}
	}

	private static boolean hasBoundLamp(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(ModBlocks.MAGIC_LAMP.get()) && DjinnNbt.owner(stack).map(player.getUUID()::equals).orElse(false)) {
				DjinnLampStacks.applyLampRules(stack);
				return true;
			}
		}
		return false;
	}

	private static int desertDive(CommandSourceStack source, boolean enabled) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		DjinnWorldState state = DjinnWorldState.get(source.getServer());
		DjinnPlayerData data = state.player(player.getUUID());
		if (!data.isDjinn()) {
			source.sendFailure(Component.translatable("message.djinn.not_djinn"));
			return 0;
		}
		if (!hasPlacedLamp(source.getServer(), data)) {
			source.sendFailure(Component.translatable("message.djinn.lamp_required"));
			return 0;
		}
		data.setDesertDiveToggled(enabled);
		state.setDirty();
		player.level().playSound(null, player.blockPosition(), enabled ? SoundEvents.SAND_PLACE : SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.65F, enabled ? 1.25F : 0.7F);
		source.sendSuccess(() -> Component.translatable(enabled ? "command.djinn.desert_dive_on" : "command.djinn.desert_dive_off"), false);
		return 1;
	}

	private static int desertDiveToggle(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		DjinnWorldState state = DjinnWorldState.get(source.getServer());
		DjinnPlayerData data = state.player(player.getUUID());
		if (!data.isDjinn()) {
			source.sendFailure(Component.translatable("message.djinn.not_djinn"));
			return 0;
		}
		if (!hasPlacedLamp(source.getServer(), data)) {
			source.sendFailure(Component.translatable("message.djinn.lamp_required"));
			return 0;
		}
		data.setDesertDiveToggled(!data.desertDiveToggled());
		state.setDirty();
		player.level().playSound(null, player.blockPosition(), data.desertDiveToggled() ? SoundEvents.SAND_PLACE : SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.65F, data.desertDiveToggled() ? 1.25F : 0.7F);
		source.sendSuccess(() -> Component.translatable(data.desertDiveToggled() ? "command.djinn.desert_dive_on" : "command.djinn.desert_dive_off"), false);
		return 1;
	}

	private static int sandForm(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		DjinnWorldState state = DjinnWorldState.get(source.getServer());
		DjinnPlayerData data = state.player(player.getUUID());
		if (!data.isDjinn()) {
			source.sendFailure(Component.translatable("message.djinn.not_djinn"));
			return 0;
		}
		if (!hasPlacedLamp(source.getServer(), data)) {
			source.sendFailure(Component.translatable("message.djinn.lamp_required"));
			return 0;
		}
		boolean activating = data.sandFormTicks() <= 0;
		data.setSandFormTicks(activating ? 20 * 12 : 0);
		state.setDirty();
		player.level().playSound(null, player.blockPosition(), activating ? SoundEvents.BLAZE_SHOOT : SoundEvents.SAND_BREAK, SoundSource.PLAYERS, activating ? 0.8F : 0.65F, activating ? 0.55F : 0.75F);
		player.level().playSound(null, player.blockPosition(), activating ? SoundEvents.EVOKER_CAST_SPELL : SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.45F, activating ? 0.7F : 0.45F);
		source.sendSuccess(() -> Component.translatable("command.djinn.sand_form"), false);
		return 1;
	}

	private static int wishItem(CommandSourceStack source, ServerPlayer target, ItemStack stack) throws CommandSyntaxException {
		ServerPlayer master = source.getPlayerOrException();
		DjinnPlayerData djinn = requireHeldLampDjinn(source, master);
		if (djinn == null || !spendWish(source, master, djinn)) {
			return 0;
		}
		if (!target.getInventory().add(stack)) {
			target.drop(stack, false);
		}
		master.level().playSound(null, master.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.65F, 1.7F);
		source.sendSuccess(() -> Component.translatable("command.djinn.wish_item", target.getDisplayName(), stack.getHoverName()), true);
		return 1;
	}

	private static int wishOrigin(CommandSourceStack source, ServerPlayer target, ResourceLocation originId) throws CommandSyntaxException {
		ServerPlayer master = source.getPlayerOrException();
		DjinnPlayerData djinn = requireHeldLampDjinn(source, master);
		if (djinn == null || !spendWish(source, master, djinn)) {
			return 0;
		}
		if (originId.equals(DjinnOriginMod.id("djinn"))) {
			DjinnWorldState.get(source.getServer()).player(target.getUUID()).setDjinn(true);
			DjinnWorldState.get(source.getServer()).setDirty();
		}
		if (ModList.get().isLoaded("origins")) {
			String command = "origin set " + target.getGameProfile().getName() + " origins:origin " + originId;
			source.getServer().getCommands().performPrefixedCommand(source.getServer().createCommandSourceStack(), command);
		}
		master.level().playSound(null, master.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 1.0F);
		source.sendSuccess(() -> Component.translatable("command.djinn.wish_origin", target.getDisplayName(), originId.toString()), true);
		return 1;
	}

	private static int wishGamerule(CommandSourceStack source, String rule, String value) throws CommandSyntaxException {
		ServerPlayer master = source.getPlayerOrException();
		DjinnPlayerData djinn = requireHeldLampDjinn(source, master);
		if (djinn == null || !spendWish(source, master, djinn)) {
			return 0;
		}
		String previousValue = source.getServer().getGameRules().createTag().getString(rule);
		source.getServer().getCommands().performPrefixedCommand(source.getServer().createCommandSourceStack(), "gamerule " + rule + " " + value);
		DjinnWorldState state = DjinnWorldState.get(source.getServer());
		state.gameruleReverts().add(new ScheduledGameruleRevert(rule, previousValue, System.currentTimeMillis() + ONE_WEEK_MILLIS));
		state.setDirty();
		master.level().playSound(null, master.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.85F, 0.85F);
		source.sendSuccess(() -> Component.translatable("command.djinn.wish_gamerule", rule, value), true);
		return 1;
	}

	private static int wishSunBoost(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer master = source.getPlayerOrException();
		DjinnPlayerData djinn = requireHeldLampDjinn(source, master);
		if (djinn == null || !spendWish(source, master, djinn)) {
			return 0;
		}
		ItemStack token = new ItemStack(Items.GOLD_INGOT, 8);
		if (!master.getInventory().add(token)) {
			master.drop(token, false);
		}
		master.level().playSound(null, master.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.75F, 1.7F);
		source.sendSuccess(() -> Component.translatable("command.djinn.wish_sun_boost"), true);
		return 1;
	}

	private static DjinnPlayerData requireHeldLampDjinn(CommandSourceStack source, ServerPlayer master) {
		ItemStack lamp = heldLamp(master);
		if (lamp.isEmpty()) {
			source.sendFailure(Component.translatable("command.djinn.no_lamp"));
			return null;
		}
		if (DjinnNbt.owner(lamp).isEmpty()) {
			source.sendFailure(Component.translatable("command.djinn.unbound_lamp"));
			return null;
		}
		DjinnWorldState state = DjinnWorldState.get(source.getServer());
		DjinnPlayerData djinn = state.player(DjinnNbt.owner(lamp).get());
		if (!djinn.isDjinn()) {
			source.sendFailure(Component.translatable("message.djinn.not_djinn"));
			return null;
		}
		return djinn;
	}

	private static boolean spendWish(CommandSourceStack source, ServerPlayer master, DjinnPlayerData djinn) {
		if (!djinn.canWish()) {
			source.sendFailure(Component.translatable("command.djinn.no_wishes"));
			return false;
		}
		djinn.spendWish();
		ItemStack lamp = heldLamp(master);
		DjinnNbt.wishesUsed(lamp, djinn.wishesUsed());
		DjinnNbt.master(lamp, master.getUUID());
		master.level().playSound(null, master.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.45F, 0.9F + djinn.wishesUsed() * 0.18F);
		DjinnWorldState.get(source.getServer()).setDirty();
		return true;
	}

	private static ItemStack heldLamp(ServerPlayer player) {
		ItemStack main = player.getMainHandItem();
		if (DjinnNbt.owner(main).isPresent()) {
			return main;
		}
		ItemStack off = player.getOffhandItem();
		return DjinnNbt.owner(off).isPresent() ? off : ItemStack.EMPTY;
	}

	private static boolean hasPlacedLamp(MinecraftServer server, DjinnPlayerData data) {
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
}
