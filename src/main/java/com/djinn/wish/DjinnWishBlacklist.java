package com.djinn.wish;

import net.minecraft.util.Identifier;

import java.util.Set;

public final class DjinnWishBlacklist {
	private static final Set<Identifier> ITEMS = Set.of(
			new Identifier("minecraft", "command_block"),
			new Identifier("minecraft", "chain_command_block"),
			new Identifier("minecraft", "repeating_command_block"),
			new Identifier("minecraft", "structure_block"),
			new Identifier("minecraft", "jigsaw"),
			new Identifier("minecraft", "barrier"),
			new Identifier("minecraft", "bedrock"),
			new Identifier("minecraft", "debug_stick")
	);
	private static final Set<Identifier> ORIGINS = Set.of(
			new Identifier("origins", "empty"),
			new Identifier("origins", "random")
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

	public static boolean itemAllowed(Identifier id) {
		return !ITEMS.contains(id);
	}

	public static boolean originAllowed(Identifier id) {
		return !ORIGINS.contains(id);
	}

	public static boolean gameruleAllowed(String rule) {
		return !GAMERULES.contains(rule);
	}
}
