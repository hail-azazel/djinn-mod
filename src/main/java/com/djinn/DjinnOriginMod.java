package com.djinn;

import com.djinn.block.ModBlocks;
import com.djinn.command.DjinnCommands;
import com.djinn.effect.ModEffects;
import com.djinn.event.DjinnEvents;
import com.djinn.item.ModItems;
import com.djinn.network.DjinnNetworking;
import com.djinn.particle.ModParticles;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(DjinnOriginMod.MOD_ID)
public class DjinnOriginMod {
	public static final String MOD_ID = "djinn";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public DjinnOriginMod(IEventBus modBus) {
		ModParticles.register(modBus);
		ModEffects.register(modBus);
		ModBlocks.register(modBus);
		ModItems.register(modBus);
		DjinnNetworking.register(modBus);
		DjinnCommands.register();
		DjinnEvents.register();
		LOGGER.info("Djinn Origin is awake.");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
