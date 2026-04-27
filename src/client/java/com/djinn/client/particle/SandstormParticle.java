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
public class SandstormParticle extends SpriteBillboardParticle {
	private final SpriteProvider spriteProvider;

	protected SandstormParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, SpriteProvider spriteProvider) {
		super(world, x, y, z, velocityX, velocityY, velocityZ);
		this.spriteProvider = spriteProvider;
		this.maxAge = 24 + random.nextInt(16);
		this.scale = 0.08F + random.nextFloat() * 0.12F;
		this.gravityStrength = -0.01F;
		this.velocityX = velocityX + (random.nextDouble() - 0.5) * 0.08;
		this.velocityY = Math.abs(velocityY) + random.nextDouble() * 0.05;
		this.velocityZ = velocityZ + (random.nextDouble() - 0.5) * 0.08;
		this.red = 0.86F;
		this.green = 0.68F;
		this.blue = 0.36F;
		this.alpha = 0.85F;
		setSpriteForAge(spriteProvider);
	}

	@Override
	public void tick() {
		super.tick();
		float life = (float) age / (float) maxAge;
		this.alpha = 0.85F * (1.0F - life);
		this.velocityX += Math.cos((age + random.nextFloat()) * 0.45F) * 0.008F;
		this.velocityZ += Math.sin((age + random.nextFloat()) * 0.45F) * 0.008F;
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
			return new SandstormParticle(world, x, y, z, velocityX, velocityY, velocityZ, spriteProvider);
		}
	}
}
