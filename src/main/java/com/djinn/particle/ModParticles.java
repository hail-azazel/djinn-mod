package com.djinn.particle;

import com.djinn.DjinnOriginMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
	public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, DjinnOriginMod.MOD_ID);
	public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SANDSTORM = PARTICLE_TYPES.register("sandstorm", () -> new SimpleParticleType(false));
	public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GOLDEN_SMOKE = PARTICLE_TYPES.register("golden_smoke", () -> new SimpleParticleType(false));

	private ModParticles() {
	}

	public static void register(IEventBus bus) {
		PARTICLE_TYPES.register(bus);
	}
}
