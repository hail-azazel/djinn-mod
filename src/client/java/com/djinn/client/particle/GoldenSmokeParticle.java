package com.djinn.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public class GoldenSmokeParticle extends TextureSheetParticle {
	private final SpriteSet sprites;

	protected GoldenSmokeParticle(ClientLevel level, double x, double y, double z, double velocityX, double velocityY, double velocityZ, SpriteSet sprites) {
		super(level, x, y, z, velocityX, velocityY, velocityZ);
		this.sprites = sprites;
		this.lifetime = 34 + random.nextInt(20);
		this.quadSize = 0.18F + random.nextFloat() * 0.18F;
		this.xd = velocityX * 0.4 + (random.nextDouble() - 0.5) * 0.025;
		this.yd = velocityY * 0.4 + 0.025 + random.nextDouble() * 0.02;
		this.zd = velocityZ * 0.4 + (random.nextDouble() - 0.5) * 0.025;
		this.rCol = 1.0F;
		this.gCol = 0.76F;
		this.bCol = 0.25F;
		this.alpha = 0.65F;
		setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		float life = (float) age / (float) lifetime;
		this.alpha = 0.65F * (1.0F - life);
		this.quadSize *= 1.015F;
		setSpriteFromAge(sprites);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	public static class Factory implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Factory(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
			return new GoldenSmokeParticle(level, x, y, z, velocityX, velocityY, velocityZ, sprites);
		}
	}
}
