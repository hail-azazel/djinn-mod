package com.djinn.client;

import com.djinn.client.gui.WishScreen;
import com.djinn.client.particle.GoldenSmokeParticle;
import com.djinn.client.particle.SandstormParticle;
import com.djinn.client.render.DjinnTornadoRenderer;
import com.djinn.client.render.MagicLampVisageRenderer;
import com.djinn.block.ModBlocks;
import com.djinn.network.DjinnNetworking;
import com.djinn.particle.ModParticles;
import com.djinn.state.DjinnNbt;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class DjinnOriginModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ParticleFactoryRegistry.getInstance().register(ModParticles.SANDSTORM, SandstormParticle.Factory::new);
		ParticleFactoryRegistry.getInstance().register(ModParticles.GOLDEN_SMOKE, GoldenSmokeParticle.Factory::new);
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.MAGIC_LAMP_BLOCK, RenderLayer.getCutout());
		DjinnTornadoRenderer.register();
		MagicLampVisageRenderer.register();
		ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
			if (DjinnNbt.owner(stack).isPresent()) {
				String name = DjinnNbt.ownerName(stack);
				if (name.isBlank()) {
					name = DjinnNbt.owner(stack).get().toString();
				}
				lines.add(Text.literal("        .- golden djinn visage -.").formatted(Formatting.GOLD, Formatting.ITALIC));
				lines.add(Text.literal("           [" + name + "]").formatted(Formatting.YELLOW));
				lines.add(Text.literal("    sand-mist, lapis heat, lamp-bound").formatted(Formatting.BLUE, Formatting.ITALIC));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(DjinnNetworking.OPEN_WISH_MENU, (client, handler, buf, responseSender) -> {
			int remainingWishes = buf.readVarInt();
			net.minecraft.nbt.NbtCompound gamerules = buf.readNbt();
			client.execute(() -> client.setScreen(new WishScreen(remainingWishes, gamerules)));
		});
	}
}
