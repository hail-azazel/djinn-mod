package com.djinn.particle;

import com.djinn.DjinnOriginMod;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModParticles {
	public static final DefaultParticleType SANDSTORM = FabricParticleTypes.simple();
	public static final DefaultParticleType GOLDEN_SMOKE = FabricParticleTypes.simple();

	private ModParticles() {
	}

	public static void register() {
		Registry.register(Registries.PARTICLE_TYPE, DjinnOriginMod.id("sandstorm"), SANDSTORM);
		Registry.register(Registries.PARTICLE_TYPE, DjinnOriginMod.id("golden_smoke"), GOLDEN_SMOKE);
	}
}
