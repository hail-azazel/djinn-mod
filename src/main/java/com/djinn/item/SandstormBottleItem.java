package com.djinn.item;

import com.djinn.state.DjinnPlayerData;
import com.djinn.state.DjinnWorldState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SandstormBottleItem extends Item {
	private static final int DURATION_TICKS = 20 * 12;

	public SandstormBottleItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);
		if (!(user instanceof ServerPlayer player)) {
			return InteractionResultHolder.success(stack);
		}
		DjinnWorldState worldState = DjinnWorldState.get(player.getServer());
		DjinnPlayerData data = worldState.player(player.getUUID());
		if (!data.isDjinn()) {
			player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.45F, 0.55F);
			player.displayClientMessage(Component.translatable("message.djinn.not_djinn"), true);
			return InteractionResultHolder.fail(stack);
		}
		boolean activating = data.sandFormTicks() <= 0;
		data.setSandFormTicks(activating ? DURATION_TICKS : 0);
		worldState.setDirty();
		player.getCooldowns().addCooldown(this, 20 * 20);
		player.level().playSound(null, player.blockPosition(), activating ? SoundEvents.BLAZE_SHOOT : SoundEvents.SAND_BREAK, SoundSource.PLAYERS, activating ? 0.7F : 0.65F, activating ? 0.55F : 0.75F);
		player.level().playSound(null, player.blockPosition(), activating ? SoundEvents.EVOKER_CAST_SPELL : SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.45F, activating ? 0.7F : 0.45F);
		return InteractionResultHolder.success(stack);
	}
}
