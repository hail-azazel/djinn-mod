package com.djinn.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;

@Environment(EnvType.CLIENT)
public class GoldenSmokeParticle extends SpriteBillboardParticle {
	private final SpriteProvider spriteProvider;

	protected GoldenSmokeParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, SpriteProvider spriteProvider) {
		super(world, x, y, z, velocityX, velocityY, velocityZ);
		this.spriteProvider = spriteProvider;
		this.maxAge = 34 + random.nextInt(20);
		this.scale = 0.18F + random.nextFloat() * 0.18F;
		this.velocityX = velocityX * 0.4 + (random.nextDouble() - 0.5) * 0.025;
		this.velocityY = velocityY * 0.4 + 0.025 + random.nextDouble() * 0.02;
		this.velocityZ = velocityZ * 0.4 + (random.nextDouble() - 0.5) * 0.025;
		this.red = 1.0F;
		this.green = 0.76F;
		this.blue = 0.25F;
		this.alpha = 0.65F;
		setSpriteForAge(spriteProvider);
	}

	@Override
	public void tick() {
		super.tick();
		float life = (float) age / (float) maxAge;
		this.alpha = 0.65F * (1.0F - life);
		this.scale *= 1.015F;
		setSpriteForAge(spriteProvider);
	}

	@Override
	public ParticleTextureSheet getType() {
		return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}

	public static class Factory implements ParticleFactory<DefaultParticleType> {
		private final SpriteProvider spriteProvider;

		public Factory(SpriteProvider spriteProvider) {
			this.spriteProvider = spriteProvider;
		}

		@Override
		public Particle createParticle(DefaultParticleType type, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
			return new GoldenSmokeParticle(world, x, y, z, velocityX, velocityY, velocityZ, spriteProvider);
		}
	}
}
