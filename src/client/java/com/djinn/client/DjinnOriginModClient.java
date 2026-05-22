package com.djinn.client;

import com.djinn.DjinnOriginMod;
import com.djinn.block.ModBlocks;
import com.djinn.client.gui.WishScreen;
import com.djinn.client.particle.GoldenSmokeParticle;
import com.djinn.client.particle.SandstormParticle;
import com.djinn.client.render.DjinnTornadoRenderer;
import com.djinn.client.render.MagicLampVisageRenderer;
import com.djinn.particle.ModParticles;
import com.djinn.state.DjinnNbt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = DjinnOriginMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class DjinnOriginModClient {
	private DjinnOriginModClient() {
	}

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(ModBlocks.MAGIC_LAMP_BLOCK.get(), RenderType.cutout()));
		DjinnTornadoRenderer.register();
		MagicLampVisageRenderer.register();
		NeoForge.EVENT_BUS.addListener(DjinnOriginModClient::addTooltip);
	}

	@SubscribeEvent
	public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
		event.registerSpriteSet(ModParticles.SANDSTORM.get(), SandstormParticle.Factory::new);
		event.registerSpriteSet(ModParticles.GOLDEN_SMOKE.get(), GoldenSmokeParticle.Factory::new);
	}

	public static void openWishMenu(int remainingWishes, CompoundTag gamerules) {
		Minecraft.getInstance().setScreen(new WishScreen(remainingWishes, gamerules));
	}

	private static void addTooltip(ItemTooltipEvent event) {
		if (DjinnNbt.owner(event.getItemStack()).isPresent()) {
			String name = DjinnNbt.ownerName(event.getItemStack());
			if (name.isBlank()) {
				name = DjinnNbt.owner(event.getItemStack()).get().toString();
			}
			event.getToolTip().add(Component.literal("        .- golden djinn visage -.").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
			event.getToolTip().add(Component.literal("           [" + name + "]").withStyle(ChatFormatting.YELLOW));
			event.getToolTip().add(Component.literal("    sand-mist, lapis heat, lamp-bound").withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
		}
	}
}
