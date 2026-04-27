package com.djinn.item;

import com.djinn.state.DjinnPlayerData;
import com.djinn.state.DjinnWorldState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class SandstormBottleItem extends Item {
	private static final int DURATION_TICKS = 20 * 12;

	public SandstormBottleItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!(user instanceof ServerPlayerEntity player)) {
			return TypedActionResult.success(stack);
		}
		DjinnWorldState worldState = DjinnWorldState.get(player.getServer());
		DjinnPlayerData data = worldState.player(player.getUuid());
		if (!data.isDjinn()) {
			player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.45F, 0.55F);
			player.sendMessage(Text.translatable("message.djinn.not_djinn"), true);
			return TypedActionResult.fail(stack);
		}
		boolean activating = data.sandFormTicks() <= 0;
		data.setSandFormTicks(activating ? DURATION_TICKS : 0);
		worldState.markDirty();
		player.getItemCooldownManager().set(this, 20 * 20);
		player.getWorld().playSound(null, player.getBlockPos(), activating ? SoundEvents.ENTITY_BLAZE_SHOOT : SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, activating ? 0.7F : 0.65F, activating ? 0.55F : 0.75F);
		player.getWorld().playSound(null, player.getBlockPos(), activating ? SoundEvents.ENTITY_EVOKER_CAST_SPELL : SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.45F, activating ? 0.7F : 0.45F);
		return TypedActionResult.success(stack);
	}
}
