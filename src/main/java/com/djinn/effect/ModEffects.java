package com.djinn.effect;

import com.djinn.DjinnOriginMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
	public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, DjinnOriginMod.MOD_ID);
	public static final DeferredHolder<MobEffect, MobEffect> SAND_VEIL = EFFECTS.register("sand_veil", SandVeilEffect::new);

	private ModEffects() {
	}

	public static void register(IEventBus bus) {
		EFFECTS.register(bus);
	}

	public static Holder<MobEffect> sandVeil() {
		return SAND_VEIL;
	}

	private static class SandVeilEffect extends MobEffect {
		private SandVeilEffect() {
			super(MobEffectCategory.BENEFICIAL, 0xD7B15C);
		}
	}
}
