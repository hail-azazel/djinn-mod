package com.djinn.effect;

import com.djinn.DjinnOriginMod;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEffects {
	public static final StatusEffect SAND_VEIL = new SandVeilEffect();

	private ModEffects() {
	}

	public static void register() {
		Registry.register(Registries.STATUS_EFFECT, DjinnOriginMod.id("sand_veil"), SAND_VEIL);
	}

	private static class SandVeilEffect extends StatusEffect {
		private SandVeilEffect() {
			super(StatusEffectCategory.BENEFICIAL, 0xD7B15C);
		}
	}
}
