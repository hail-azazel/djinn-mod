package com.djinn.network;

import com.djinn.DjinnOriginMod;
import com.djinn.state.DjinnNbt;
import com.djinn.state.DjinnPlayerData;
import com.djinn.state.DjinnWorldState;
import com.djinn.state.ScheduledGameruleRevert;
import com.djinn.wish.DjinnWishBlacklist;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.GameRules;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class DjinnNetworking {
	public static final Identifier OPEN_WISH_MENU = DjinnOriginMod.id("open_wish_menu");
	public static final Identifier MAKE_WISH = DjinnOriginMod.id("make_wish");
	public static final Identifier DJINN_ORIGIN = DjinnOriginMod.id("djinn");
	public static final Identifier HUMAN_ORIGIN = new Identifier("origins", "human");
	private static final long ONE_WEEK_MILLIS = 7L * 24L * 60L * 60L * 1000L;

	private DjinnNetworking() {
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(MAKE_WISH, (server, player, handler, buf, responseSender) -> {
			int wishType = buf.readVarInt();
			Identifier itemId = wishType == 0 ? buf.readIdentifier() : null;
			int count = wishType == 0 ? buf.readVarInt() : 1;
			String gamerule = wishType == 3 ? buf.readString(64) : "";
			String gameruleValue = wishType == 3 ? buf.readString(64) : "";
			Identifier originId = wishType == 1 ? buf.readIdentifier() : null;
			server.execute(() -> handleWish(player, wishType, itemId, count, gamerule, gameruleValue, originId));
		});
	}

	public static void openWishMenu(ServerPlayerEntity player, DjinnPlayerData djinn) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(Math.max(0, 3 - djinn.wishesUsed()));
		net.minecraft.nbt.NbtCompound rules = player.getServer().getGameRules().toNbt();
		buf.writeNbt(rules);
		ServerPlayNetworking.send(player, OPEN_WISH_MENU, buf);
	}

	private static void handleWish(ServerPlayerEntity master, int wishType, Identifier itemId, int count, String gamerule, String gameruleValue, Identifier originId) {
		ItemStack lamp = heldLamp(master);
		if (lamp.isEmpty()) {
			master.getWorld().playSound(null, master.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.45F, 0.55F);
			master.sendMessage(Text.translatable("command.djinn.no_lamp"), true);
			return;
		}
		UUID djinnId = DjinnNbt.owner(lamp).orElse(null);
		if (djinnId == null) {
			master.getWorld().playSound(null, master.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.45F, 0.55F);
			master.sendMessage(Text.translatable("command.djinn.unbound_lamp"), true);
			return;
		}
		DjinnWorldState worldState = DjinnWorldState.get(master.getServer());
		DjinnPlayerData djinn = worldState.player(djinnId);
		if (!djinn.isDjinn()) {
			master.getWorld().playSound(null, master.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.45F, 0.55F);
			master.sendMessage(Text.translatable("message.djinn.not_djinn"), true);
			return;
		}
		if (!djinn.canWish()) {
			master.getWorld().playSound(null, master.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.55F, 0.75F);
			master.sendMessage(Text.translatable("command.djinn.no_wishes"), true);
			return;
		}
		if (!applyWish(master, wishType, itemId, count, gamerule, gameruleValue, originId)) {
			master.getWorld().playSound(null, master.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.45F, 0.55F);
			master.sendMessage(Text.translatable("message.djinn.wish_unknown"), true);
			return;
		}
		djinn.spendWish();
		djinn.setLampMaster(master.getUuid());
		DjinnNbt.master(lamp, master.getUuid());
		DjinnNbt.wishesUsed(lamp, djinn.wishesUsed());
		worldState.markDirty();
		master.getWorld().playSound(null, master.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.7F, 1.45F);
		master.getWorld().playSound(null, master.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 0.45F, 0.9F + djinn.wishesUsed() * 0.18F);
		master.sendMessage(Text.translatable("message.djinn.wish_spent", 3 - djinn.wishesUsed()), true);
	}

	private static boolean applyWish(ServerPlayerEntity master, int wishType, Identifier itemId, int count, String gameruleName, String gameruleValue, Identifier originId) {
		if (wishType == 0) {
			if (itemId == null || !Registries.ITEM.containsId(itemId) || !DjinnWishBlacklist.itemAllowed(itemId)) {
				return false;
			}
			return give(master, new ItemStack(Registries.ITEM.get(itemId), Math.max(1, Math.min(64, count))));
		}
		return switch (wishType) {
			case 1 -> originId != null && DjinnWishBlacklist.originAllowed(originId) && changeOrigin(master, originId);
			case 2 -> give(master, new ItemStack(Items.GOLD_INGOT, 8));
			case 3 -> gamerule(master, gameruleName, gameruleValue);
			default -> false;
		};
	}

	private static boolean give(ServerPlayerEntity player, ItemStack stack) {
		if (!player.getInventory().insertStack(stack)) {
			player.dropItem(stack, false);
		}
		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_GOLD, SoundCategory.PLAYERS, 0.55F, 1.4F);
		return true;
	}

	private static boolean changeOrigin(ServerPlayerEntity player, Identifier originId) {
		if (originId.equals(DjinnOriginMod.id("djinn"))) {
			DjinnWorldState.get(player.getServer()).player(player.getUuid()).setDjinn(true);
			DjinnWorldState.get(player.getServer()).markDirty();
		}
		if (FabricLoader.getInstance().isModLoaded("origins")) {
			String command = "origin set " + player.getGameProfile().getName() + " origins:origin " + originId;
			player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(), command);
		}
		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.PLAYERS, 0.8F, 1.0F);
		return true;
	}

	private static boolean gamerule(ServerPlayerEntity player, String rule, String value) {
		if (rule == null || rule.isBlank() || value == null || value.isBlank() || !isKnownGamerule(rule) || !DjinnWishBlacklist.gameruleAllowed(rule)) {
			return false;
		}
		String previousValue = player.getServer().getGameRules().toNbt().getString(rule);
		player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(), "gamerule " + rule + " " + value);
		DjinnWorldState state = DjinnWorldState.get(player.getServer());
		state.gameruleReverts().add(new ScheduledGameruleRevert(rule, previousValue, System.currentTimeMillis() + ONE_WEEK_MILLIS));
		state.markDirty();
		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.85F, 0.85F);
		return true;
	}

	private static boolean isKnownGamerule(String rule) {
		final boolean[] found = {false};
		GameRules.accept(new GameRules.Visitor() {
			@Override
			public <T extends GameRules.Rule<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
				if (key.getName().equals(rule)) {
					found[0] = true;
				}
			}
		});
		return found[0];
	}

	private static ItemStack heldLamp(ServerPlayerEntity player) {
		ItemStack main = player.getMainHandStack();
		if (DjinnNbt.owner(main).isPresent() && Registries.ITEM.getId(main.getItem()).equals(DjinnOriginMod.id("magic_lamp"))) {
			return main;
		}
		ItemStack off = player.getOffHandStack();
		return DjinnNbt.owner(off).isPresent() && Registries.ITEM.getId(off.getItem()).equals(DjinnOriginMod.id("magic_lamp")) ? off : ItemStack.EMPTY;
	}
}
