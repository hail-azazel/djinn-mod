package com.djinn.network;

import com.djinn.DjinnOriginMod;
import com.djinn.state.DjinnNbt;
import com.djinn.state.DjinnPlayerData;
import com.djinn.state.DjinnWorldState;
import com.djinn.state.ScheduledGameruleRevert;
import com.djinn.wish.DjinnWishBlacklist;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

public final class DjinnNetworking {
	public static final ResourceLocation DJINN_ORIGIN = DjinnOriginMod.id("djinn");
	public static final ResourceLocation HUMAN_ORIGIN = ResourceLocation.fromNamespaceAndPath("origins", "human");
	private static final long ONE_WEEK_MILLIS = 7L * 24L * 60L * 60L * 1000L;

	private DjinnNetworking() {
	}

	public static void register(IEventBus bus) {
		bus.addListener(DjinnNetworking::registerPayloads);
	}

	private static void registerPayloads(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1");
		registrar.playToClient(OpenWishMenuPayload.TYPE, OpenWishMenuPayload.STREAM_CODEC, DjinnNetworking::handleOpenWishMenu);
		registrar.playToServer(MakeWishPayload.TYPE, MakeWishPayload.STREAM_CODEC, DjinnNetworking::handleMakeWish);
	}

	private static void handleOpenWishMenu(OpenWishMenuPayload payload, IPayloadContext context) {
		if (FMLEnvironment.dist == Dist.CLIENT) {
			context.enqueueWork(() -> com.djinn.client.DjinnOriginModClient.openWishMenu(payload.remainingWishes(), payload.gamerules()));
		}
	}

	private static void handleMakeWish(MakeWishPayload payload, IPayloadContext context) {
		if (context.player() instanceof ServerPlayer player) {
			context.enqueueWork(() -> handleWish(player, payload.wishType(), payload.itemId(), payload.count(), payload.gamerule(), payload.gameruleValue(), payload.originId()));
		}
	}

	public static void openWishMenu(ServerPlayer player, DjinnPlayerData djinn) {
		CompoundTag rules = player.getServer().getGameRules().createTag();
		PacketDistributor.sendToPlayer(player, new OpenWishMenuPayload(Math.max(0, 3 - djinn.wishesUsed()), rules));
	}

	private static void handleWish(ServerPlayer master, int wishType, ResourceLocation itemId, int count, String gamerule, String gameruleValue, ResourceLocation originId) {
		ItemStack lamp = heldLamp(master);
		if (lamp.isEmpty()) {
			master.level().playSound(null, master.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.45F, 0.55F);
			master.displayClientMessage(Component.translatable("command.djinn.no_lamp"), true);
			return;
		}
		UUID djinnId = DjinnNbt.owner(lamp).orElse(null);
		if (djinnId == null) {
			master.level().playSound(null, master.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.45F, 0.55F);
			master.displayClientMessage(Component.translatable("command.djinn.unbound_lamp"), true);
			return;
		}
		DjinnWorldState worldState = DjinnWorldState.get(master.getServer());
		DjinnPlayerData djinn = worldState.player(djinnId);
		if (!djinn.isDjinn()) {
			master.level().playSound(null, master.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.45F, 0.55F);
			master.displayClientMessage(Component.translatable("message.djinn.not_djinn"), true);
			return;
		}
		if (!djinn.canWish()) {
			master.level().playSound(null, master.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.55F, 0.75F);
			master.displayClientMessage(Component.translatable("command.djinn.no_wishes"), true);
			return;
		}
		if (!applyWish(master, wishType, itemId, count, gamerule, gameruleValue, originId)) {
			master.level().playSound(null, master.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.45F, 0.55F);
			master.displayClientMessage(Component.translatable("message.djinn.wish_unknown"), true);
			return;
		}
		djinn.spendWish();
		djinn.setLampMaster(master.getUUID());
		DjinnNbt.master(lamp, master.getUUID());
		DjinnNbt.wishesUsed(lamp, djinn.wishesUsed());
		worldState.setDirty();
		master.level().playSound(null, master.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.45F);
		master.level().playSound(null, master.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.45F, 0.9F + djinn.wishesUsed() * 0.18F);
		master.displayClientMessage(Component.translatable("message.djinn.wish_spent", 3 - djinn.wishesUsed()), true);
	}

	private static boolean applyWish(ServerPlayer master, int wishType, ResourceLocation itemId, int count, String gameruleName, String gameruleValue, ResourceLocation originId) {
		if (wishType == 0) {
			if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId) || !DjinnWishBlacklist.itemAllowed(itemId)) {
				return false;
			}
			return give(master, new ItemStack(BuiltInRegistries.ITEM.get(itemId), Math.max(1, Math.min(64, count))));
		}
		return switch (wishType) {
			case 1 -> originId != null && DjinnWishBlacklist.originAllowed(originId) && changeOrigin(master, originId);
			case 2 -> give(master, new ItemStack(Items.GOLD_INGOT, 8));
			case 3 -> gamerule(master, gameruleName, gameruleValue);
			default -> false;
		};
	}

	private static boolean give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.PLAYERS, 0.55F, 1.4F);
		return true;
	}

	private static boolean changeOrigin(ServerPlayer player, ResourceLocation originId) {
		if (originId.equals(DjinnOriginMod.id("djinn"))) {
			DjinnWorldState.get(player.getServer()).player(player.getUUID()).setDjinn(true);
			DjinnWorldState.get(player.getServer()).setDirty();
		}
		if (ModList.get().isLoaded("origins")) {
			String command = "origin set " + player.getGameProfile().getName() + " origins:origin " + originId;
			player.getServer().getCommands().performPrefixedCommand(player.getServer().createCommandSourceStack(), command);
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 1.0F);
		return true;
	}

	private static boolean gamerule(ServerPlayer player, String rule, String value) {
		if (rule == null || rule.isBlank() || value == null || value.isBlank() || !isKnownGamerule(rule) || !DjinnWishBlacklist.gameruleAllowed(rule)) {
			return false;
		}
		String previousValue = player.getServer().getGameRules().createTag().getString(rule);
		player.getServer().getCommands().performPrefixedCommand(player.getServer().createCommandSourceStack(), "gamerule " + rule + " " + value);
		DjinnWorldState state = DjinnWorldState.get(player.getServer());
		state.gameruleReverts().add(new ScheduledGameruleRevert(rule, previousValue, System.currentTimeMillis() + ONE_WEEK_MILLIS));
		state.setDirty();
		player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.85F, 0.85F);
		return true;
	}

	private static boolean isKnownGamerule(String rule) {
		final boolean[] found = {false};
		GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
			@Override
			public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
				if (key.getId().equals(rule)) {
					found[0] = true;
				}
			}
		});
		return found[0];
	}

	private static ItemStack heldLamp(ServerPlayer player) {
		ItemStack main = player.getMainHandItem();
		if (DjinnNbt.owner(main).isPresent() && BuiltInRegistries.ITEM.getKey(main.getItem()).equals(DjinnOriginMod.id("magic_lamp"))) {
			return main;
		}
		ItemStack off = player.getOffhandItem();
		return DjinnNbt.owner(off).isPresent() && BuiltInRegistries.ITEM.getKey(off.getItem()).equals(DjinnOriginMod.id("magic_lamp")) ? off : ItemStack.EMPTY;
	}

	public record OpenWishMenuPayload(int remainingWishes, CompoundTag gamerules) implements CustomPacketPayload {
		public static final Type<OpenWishMenuPayload> TYPE = new Type<>(DjinnOriginMod.id("open_wish_menu"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenWishMenuPayload> STREAM_CODEC = new StreamCodec<>() {
			@Override
			public OpenWishMenuPayload decode(RegistryFriendlyByteBuf buffer) {
				return new OpenWishMenuPayload(buffer.readVarInt(), buffer.readNbt());
			}

			@Override
			public void encode(RegistryFriendlyByteBuf buffer, OpenWishMenuPayload payload) {
				buffer.writeVarInt(payload.remainingWishes());
				buffer.writeNbt(payload.gamerules());
			}
		};

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record MakeWishPayload(int wishType, ResourceLocation itemId, int count, String gamerule, String gameruleValue, ResourceLocation originId) implements CustomPacketPayload {
		public static final Type<MakeWishPayload> TYPE = new Type<>(DjinnOriginMod.id("make_wish"));
		public static final StreamCodec<RegistryFriendlyByteBuf, MakeWishPayload> STREAM_CODEC = new StreamCodec<>() {
			@Override
			public MakeWishPayload decode(RegistryFriendlyByteBuf buffer) {
				int wishType = buffer.readVarInt();
				ResourceLocation itemId = wishType == 0 ? buffer.readResourceLocation() : null;
				int count = wishType == 0 ? buffer.readVarInt() : 1;
				String gamerule = wishType == 3 ? buffer.readUtf(64) : "";
				String gameruleValue = wishType == 3 ? buffer.readUtf(64) : "";
				ResourceLocation originId = wishType == 1 ? buffer.readResourceLocation() : null;
				return new MakeWishPayload(wishType, itemId, count, gamerule, gameruleValue, originId);
			}

			@Override
			public void encode(RegistryFriendlyByteBuf buffer, MakeWishPayload payload) {
				buffer.writeVarInt(payload.wishType());
				if (payload.wishType() == 0) {
					buffer.writeResourceLocation(payload.itemId());
					buffer.writeVarInt(payload.count());
				} else if (payload.wishType() == 1) {
					buffer.writeResourceLocation(payload.originId());
				} else if (payload.wishType() == 3) {
					buffer.writeUtf(payload.gamerule(), 64);
					buffer.writeUtf(payload.gameruleValue(), 64);
				}
			}
		};

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
