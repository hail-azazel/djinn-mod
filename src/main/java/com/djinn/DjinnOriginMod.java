package com.djinn;

import com.djinn.block.ModBlocks;
import com.djinn.command.DjinnCommands;
import com.djinn.effect.ModEffects;
import com.djinn.event.DjinnEvents;
import com.djinn.item.ModItems;
import com.djinn.network.DjinnNetworking;
import com.djinn.particle.ModParticles;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DjinnOriginMod implements ModInitializer {
	public static final String MOD_ID = "djinn";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModParticles.register();
		ModEffects.register();
		ModBlocks.register();
		ModItems.register();
		DjinnNetworking.registerServerReceivers();
		DjinnCommands.register();
		DjinnEvents.register();
		LOGGER.info("Djinn Origin is awake.");
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}
}
