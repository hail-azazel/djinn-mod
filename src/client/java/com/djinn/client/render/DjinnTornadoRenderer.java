package com.djinn.client.render;

import com.djinn.effect.ModEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class DjinnTornadoRenderer {
	private static final Identifier SAND_TEXTURE = new Identifier("minecraft", "textures/block/sand.png");
	private static final float WORLD_PIXEL = 1.0F / 16.0F;

	private DjinnTornadoRenderer() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.world == null) {
				return;
			}
			MatrixStack matrices = context.matrixStack();
			Vec3d camera = context.camera().getPos();
			float tickDelta = context.tickDelta();
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.enableDepthTest();
			RenderSystem.disableCull();
			RenderSystem.depthMask(false);
			RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
			RenderSystem.setShaderTexture(0, SAND_TEXTURE);
			Quaternionf cameraRotation = context.camera().getRotation();
			Vector3f cameraRight = new Vector3f(1.0F, 0.0F, 0.0F).rotate(cameraRotation);
			Vector3f cameraUp = new Vector3f(0.0F, 1.0F, 0.0F).rotate(cameraRotation);
			for (PlayerEntity player : client.world.getPlayers()) {
				if (player.hasStatusEffect(ModEffects.SAND_VEIL)) {
					renderTornado(matrices, camera, player, tickDelta, cameraRight, cameraUp);
				}
			}
			RenderSystem.depthMask(true);
			RenderSystem.enableCull();
			RenderSystem.disableBlend();
		});
	}

	private static void renderTornado(MatrixStack matrices, Vec3d camera, PlayerEntity player, float tickDelta, Vector3f cameraRight, Vector3f cameraUp) {
		double x = MathHelper.lerp(tickDelta, player.prevX, player.getX()) - camera.x;
		double y = MathHelper.lerp(tickDelta, player.prevY, player.getY()) - camera.y;
		double z = MathHelper.lerp(tickDelta, player.prevZ, player.getZ()) - camera.z;
		double motionX = player.getX() - player.prevX;
		double motionZ = player.getZ() - player.prevZ;
		float speed = (float) Math.min(0.42D, Math.sqrt(motionX * motionX + motionZ * motionZ));
		float inertiaX = (float) (-motionX * 2.8D);
		float inertiaZ = (float) (-motionZ * 2.8D);
		float yawTurn = MathHelper.lerpAngleDegrees(tickDelta, player.prevYaw, player.getYaw()) * 0.017453292F;
		matrices.push();
		matrices.translate(x, y, z);
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder buffer = tessellator.getBuffer();
		buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		float time = (player.age + tickDelta) * (0.16F + speed * 0.28F);
		addPixelTornado(buffer, matrix, time, inertiaX, inertiaZ, yawTurn, speed, cameraRight, cameraUp);
		BufferRenderer.drawWithGlobalProgram(buffer.end());
		matrices.pop();
	}

	private static void addPixelTornado(BufferBuilder buffer, Matrix4f matrix, float time, float inertiaX, float inertiaZ, float yawTurn, float speed, Vector3f cameraRight, Vector3f cameraUp) {
		for (int stream = 0; stream < 8; stream++) {
			float streamPhase = stream * 0.7854F;
			float flow = (time * (2.0F + stream * 0.05F + speed * 0.6F) + stream * 0.13F) % 1.0F;
			for (int i = 0; i < 62; i++) {
				float t = (i + flow) / 62.0F;
				float base = t * t * (3.0F - 2.0F * t);
				float lean = base * base;
				float angle = time * (5.2F + stream * 0.11F + speed * 0.8F) + yawTurn * 0.55F + t * 22.5F + streamPhase;
				float radius = base * (0.82F + pixelHash(stream, i, time) * WORLD_PIXEL * 0.9F);
				float jitter = pixelHash(stream + 13, i, time) * WORLD_PIXEL * base;
				float x = quantize(MathHelper.cos(angle) * radius + inertiaX * lean + jitter);
				float y = quantize(0.05F + t * 2.42F);
				float z = quantize(MathHelper.sin(angle) * radius + inertiaZ * lean - jitter * 0.55F);
				float size = WORLD_PIXEL * (0.85F + (i % 5 == 0 ? 0.65F : 0.0F) + speed * 0.8F);
				float baseFade = MathHelper.clamp(t * 4.5F, 0.0F, 1.0F);
				float tipFade = MathHelper.clamp((1.0F - t) * 1.9F, 0.0F, 1.0F);
				int alpha = (int) ((92 - stream * 4) * baseFade * tipFade);
				int red = 166 + stream * 3;
				int green = 127 + stream * 2;
				int blue = 70;
				addPixel(buffer, matrix, cameraRight, cameraUp, x, y, z, size, stream, i, red, green, blue, alpha);
				if (i % 6 == 0) {
					addPixel(buffer, matrix, cameraRight, cameraUp, quantize(x * 0.7F), y + WORLD_PIXEL, quantize(z * 0.7F), WORLD_PIXEL * 0.8F, stream + 7, i, 122, 91, 55, alpha / 2);
				}
			}
		}
		for (int ring = 0; ring < 6; ring++) {
			float t = ring / 5.0F;
			float y = quantize(0.16F + t * 2.1F);
			float ringRadius = (0.16F + t * 0.62F);
			int count = 10 + ring * 2;
			for (int i = 0; i < count; i += 2) {
				float angle = time * (3.5F + ring * 0.12F) + i * 6.28318F / count;
				float lean = t * t * t;
				float x = quantize(MathHelper.cos(angle) * ringRadius + inertiaX * lean);
				float z = quantize(MathHelper.sin(angle) * ringRadius + inertiaZ * lean);
				addPixel(buffer, matrix, cameraRight, cameraUp, x, y, z, WORLD_PIXEL * 0.9F, ring, i, 174, 130, 72, 44 - ring * 4);
			}
		}
	}

	private static void addRibbon(BufferBuilder buffer, Matrix4f matrix, float phase, int ribbon, float inertiaX, float inertiaZ, float yawTurn, float speed) {
		int segments = 28;
		float width = 0.31F + ribbon * 0.065F + speed * 0.12F;
		for (int i = 0; i < segments; i++) {
			float t0 = i / (float) segments;
			float t1 = (i + 1) / (float) segments;
			float y0 = t0 * 2.42F;
			float y1 = t1 * 2.42F;
			float grain0 = granular(phase, ribbon, i) * (0.075F + speed * 0.08F);
			float grain1 = granular(phase, ribbon, i + 1) * (0.075F + speed * 0.08F);
			float turbulence0 = MathHelper.sin(phase * 4.1F + t0 * 35.0F + ribbon) * 0.075F + grain0;
			float turbulence1 = MathHelper.sin(phase * 4.1F + t1 * 35.0F + ribbon) * 0.075F + grain1;
			float lean0 = t0 * t0;
			float lean1 = t1 * t1;
			float shear0 = MathHelper.sin(phase * 2.2F + t0 * 8.5F + yawTurn) * t0 * (0.10F + speed * 0.32F);
			float shear1 = MathHelper.sin(phase * 2.2F + t1 * 8.5F + yawTurn) * t1 * (0.10F + speed * 0.32F);
			float r0 = 0.34F + t0 * 0.56F + turbulence0;
			float r1 = 0.34F + t1 * 0.56F + turbulence1;
			float a0 = phase + yawTurn * 0.45F + t0 * (20.5F + ribbon * 0.85F) + turbulence0 * 1.8F;
			float a1 = phase + yawTurn * 0.45F + t1 * (20.5F + ribbon * 0.85F) + turbulence1 * 1.8F;
			float x0 = MathHelper.cos(a0) * r0 + shear0 + inertiaX * lean0;
			float z0 = MathHelper.sin(a0) * r0 - shear0 * 0.45F + inertiaZ * lean0;
			float x1 = MathHelper.cos(a1) * r1 + shear1 + inertiaX * lean1;
			float z1 = MathHelper.sin(a1) * r1 - shear1 * 0.45F + inertiaZ * lean1;
			float nx0 = MathHelper.cos(a0 + 1.5708F) * width;
			float nz0 = MathHelper.sin(a0 + 1.5708F) * width;
			float nx1 = MathHelper.cos(a1 + 1.5708F) * width;
			float nz1 = MathHelper.sin(a1 + 1.5708F) * width;
			int alpha = 138 - (int) (t0 * 58.0F) - ribbon * 10;
			int red = 156 + ribbon * 18;
			int green = 120 + ribbon * 15;
			int blue = 62 + ribbon * 7;
			vertex(buffer, matrix, x0 - nx0, y0, z0 - nz0, red, green, blue, alpha);
			vertex(buffer, matrix, x1 - nx1, y1, z1 - nz1, red, green, blue, alpha);
			vertex(buffer, matrix, x1 + nx1, y1, z1 + nz1, 222, 181, 93, alpha);
			vertex(buffer, matrix, x0 + nx0, y0, z0 + nz0, 198, 151, 76, alpha);
		}
	}

	private static float granular(float phase, int ribbon, int segment) {
		float seed = MathHelper.sin(segment * 12.9898F + ribbon * 78.233F + phase * 9.17F) * 43758.5453F;
		return (seed - MathHelper.floor(seed)) * 2.0F - 1.0F;
	}

	private static float quantize(float value) {
		return Math.round(value / WORLD_PIXEL) * WORLD_PIXEL;
	}

	private static float pixelHash(int a, int b, float time) {
		float seed = MathHelper.sin(a * 37.13F + b * 17.97F + MathHelper.floor(time * 8.0F) * 0.73F) * 43758.5453F;
		return (seed - MathHelper.floor(seed)) * 2.0F - 1.0F;
	}

	private static void addPixel(BufferBuilder buffer, Matrix4f matrix, Vector3f cameraRight, Vector3f cameraUp, float x, float y, float z, float size, int uSeed, int vSeed, int red, int green, int blue, int alpha) {
		float u = (uSeed & 15) / 16.0F;
		float v = (vSeed & 15) / 16.0F;
		float du = 1.0F / 16.0F;
		float dv = 1.0F / 16.0F;
		billboardVertex(buffer, matrix, cameraRight, cameraUp, x, y, z, -size, -size, u, v + dv, red, green, blue, alpha);
		billboardVertex(buffer, matrix, cameraRight, cameraUp, x, y, z, -size, size, u, v, red, Math.min(255, green + 18), Math.min(255, blue + 10), alpha);
		billboardVertex(buffer, matrix, cameraRight, cameraUp, x, y, z, size, size, u + du, v, red, Math.min(255, green + 24), Math.min(255, blue + 16), alpha);
		billboardVertex(buffer, matrix, cameraRight, cameraUp, x, y, z, size, -size, u + du, v + dv, red, green, blue, alpha);
	}

	private static void billboardVertex(BufferBuilder buffer, Matrix4f matrix, Vector3f cameraRight, Vector3f cameraUp, float x, float y, float z, float right, float up, float u, float v, int red, int green, int blue, int alpha) {
		float px = x + cameraRight.x() * right + cameraUp.x() * up;
		float py = y + cameraRight.y() * right + cameraUp.y() * up;
		float pz = z + cameraRight.z() * right + cameraUp.z() * up;
		texturedVertex(buffer, matrix, px, py, pz, u, v, red, green, blue, alpha);
	}

	private static void addTurbulenceRing(BufferBuilder buffer, Matrix4f matrix, float time, int ring, float inertiaX, float inertiaZ, float speed) {
		float y = 0.22F + ring * 0.43F;
		float radius = 0.42F + ring * 0.095F + MathHelper.sin(time * 4.0F + ring) * 0.08F;
		float thickness = 0.07F + ring * 0.01F + speed * 0.04F;
		int segments = 14;
		for (int i = 0; i < segments; i += 2) {
			float a0 = time * (2.4F + ring * 0.12F) + i * 6.28318F / segments;
			float a1 = time * (2.4F + ring * 0.12F) + (i + 1) * 6.28318F / segments;
			float r0 = radius + MathHelper.sin(a0 * 3.0F + time) * 0.04F;
			float r1 = radius + MathHelper.sin(a1 * 3.0F + time) * 0.04F;
			float lean = (y / 2.4F) * (y / 2.4F);
			float dx = inertiaX * lean;
			float dz = inertiaZ * lean;
			int alpha = 62 - ring * 5;
			vertex(buffer, matrix, dx + MathHelper.cos(a0) * (r0 - thickness), y, dz + MathHelper.sin(a0) * (r0 - thickness), 176, 128, 68, alpha);
			vertex(buffer, matrix, dx + MathHelper.cos(a1) * (r1 - thickness), y + 0.035F, dz + MathHelper.sin(a1) * (r1 - thickness), 126, 95, 54, alpha);
			vertex(buffer, matrix, dx + MathHelper.cos(a1) * (r1 + thickness), y + 0.07F, dz + MathHelper.sin(a1) * (r1 + thickness), 231, 191, 103, alpha);
			vertex(buffer, matrix, dx + MathHelper.cos(a0) * (r0 + thickness), y + 0.035F, dz + MathHelper.sin(a0) * (r0 + thickness), 204, 151, 78, alpha);
		}
	}

	private static void addSandGrainSheets(BufferBuilder buffer, Matrix4f matrix, float time, float inertiaX, float inertiaZ, float speed) {
		for (int i = 0; i < 16; i++) {
			float t = i / 16.0F;
			float y = 0.16F + t * 2.2F;
			float angle = time * 6.0F + i * 2.399F;
			float radius = 0.28F + t * 0.7F + granular(time, 6, i) * 0.09F;
			float size = 0.035F + (i % 4) * 0.009F + speed * 0.035F;
			float lean = t * t;
			float x = MathHelper.cos(angle) * radius + inertiaX * lean;
			float z = MathHelper.sin(angle) * radius + inertiaZ * lean;
			int alpha = 82 - (int) (t * 34.0F);
			vertex(buffer, matrix, x - size, y, z - size, 185, 137, 73, alpha);
			vertex(buffer, matrix, x + size, y + size * 0.35F, z - size, 226, 184, 103, alpha);
			vertex(buffer, matrix, x + size, y + size, z + size, 164, 119, 64, alpha);
			vertex(buffer, matrix, x - size, y + size * 0.65F, z + size, 96, 75, 47, alpha);
		}
	}

	private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, int red, int green, int blue, int alpha) {
		buffer.vertex(matrix, x, y, z).color(red, green, blue, alpha).next();
	}

	private static void texturedVertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, float u, float v, int red, int green, int blue, int alpha) {
		buffer.vertex(matrix, x, y, z).texture(u, v).color(red, green, blue, alpha).next();
	}
}
