package com.djinn.wish;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class DjinnWishBlacklist {
	private static final Set<ResourceLocation> ITEMS = Set.of(
			ResourceLocation.fromNamespaceAndPath("minecraft", "command_block"),
			ResourceLocation.fromNamespaceAndPath("minecraft", "chain_command_block"),
			ResourceLocation.fromNamespaceAndPath("minecraft", "repeating_command_block"),
			ResourceLocation.fromNamespaceAndPath("minecraft", "structure_block"),
			ResourceLocation.fromNamespaceAndPath("minecraft", "jigsaw"),
			ResourceLocation.fromNamespaceAndPath("minecraft", "barrier"),
			ResourceLocation.fromNamespaceAndPath("minecraft", "bedrock"),
			ResourceLocation.fromNamespaceAndPath("minecraft", "debug_stick")
	);
	private static final Set<ResourceLocation> ORIGINS = Set.of(
			ResourceLocation.fromNamespaceAndPath("origins", "empty"),
			ResourceLocation.fromNamespaceAndPath("origins", "random")
	);
	private static final Set<String> GAMERULES = Set.of(
			"commandBlockOutput",
			"disableRaids",
			"doLimitedCrafting",
			"functionCommandLimit",
			"maxCommandChainLength",
			"playersSleepingPercentage",
			"spawnRadius"
	);

	private DjinnWishBlacklist() {
	}

	public static boolean itemAllowed(ResourceLocation id) {
		return !ITEMS.contains(id);
	}

	public static boolean originAllowed(ResourceLocation id) {
		return !ORIGINS.contains(id);
	}

	public static boolean gameruleAllowed(String rule) {
		return !GAMERULES.contains(rule);
	}
}
