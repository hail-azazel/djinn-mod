package com.djinn.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public class SandstormParticle extends TextureSheetParticle {
	private final SpriteSet sprites;

	protected SandstormParticle(ClientLevel level, double x, double y, double z, double velocityX, double velocityY, double velocityZ, SpriteSet sprites) {
		super(level, x, y, z, velocityX, velocityY, velocityZ);
		this.sprites = sprites;
		this.lifetime = 24 + random.nextInt(16);
		this.quadSize = 0.08F + random.nextFloat() * 0.12F;
		this.gravity = -0.01F;
		this.xd = velocityX + (random.nextDouble() - 0.5) * 0.08;
		this.yd = Math.abs(velocityY) + random.nextDouble() * 0.05;
		this.zd = velocityZ + (random.nextDouble() - 0.5) * 0.08;
		this.rCol = 0.86F;
		this.gCol = 0.68F;
		this.bCol = 0.36F;
		this.alpha = 0.85F;
		setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		float life = (float) age / (float) lifetime;
		this.alpha = 0.85F * (1.0F - life);
		this.xd += Math.cos((age + random.nextFloat()) * 0.45F) * 0.008F;
		this.zd += Math.sin((age + random.nextFloat()) * 0.45F) * 0.008F;
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
			return new SandstormParticle(level, x, y, z, velocityX, velocityY, velocityZ, sprites);
		}
	}
}
