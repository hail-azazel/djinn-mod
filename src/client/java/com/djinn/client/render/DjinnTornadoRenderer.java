package com.djinn.client.render;

import com.djinn.effect.ModEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class DjinnTornadoRenderer {
	private static final ResourceLocation SAND_TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/sand.png");
	private static final float WORLD_PIXEL = 1.0F / 16.0F;

	private DjinnTornadoRenderer() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(DjinnTornadoRenderer::render);
	}

	private static void render(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		PoseStack poseStack = event.getPoseStack();
		Vec3 camera = event.getCamera().getPosition();
		float tickDelta = event.getPartialTick().getGameTimeDeltaPartialTick(false);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.disableCull();
		RenderSystem.depthMask(false);
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, SAND_TEXTURE);
		Quaternionf cameraRotation = event.getCamera().rotation();
		Vector3f cameraRight = new Vector3f(1.0F, 0.0F, 0.0F).rotate(cameraRotation);
		Vector3f cameraUp = new Vector3f(0.0F, 1.0F, 0.0F).rotate(cameraRotation);
		for (Player player : client.level.players()) {
			if (player.hasEffect(ModEffects.sandVeil())) {
				renderTornado(poseStack, camera, player, tickDelta, cameraRight, cameraUp);
			}
		}
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	private static void renderTornado(PoseStack poseStack, Vec3 camera, Player player, float tickDelta, Vector3f cameraRight, Vector3f cameraUp) {
		double x = Mth.lerp(tickDelta, player.xo, player.getX()) - camera.x;
		double y = Mth.lerp(tickDelta, player.yo, player.getY()) - camera.y;
		double z = Mth.lerp(tickDelta, player.zo, player.getZ()) - camera.z;
		double motionX = player.getX() - player.xo;
		double motionZ = player.getZ() - player.zo;
		float speed = (float) Math.min(0.42D, Math.sqrt(motionX * motionX + motionZ * motionZ));
		float inertiaX = (float) (-motionX * 2.8D);
		float inertiaZ = (float) (-motionZ * 2.8D);
		float yawTurn = Mth.lerp(tickDelta, player.yRotO, player.getYRot()) * 0.017453292F;
		poseStack.pushPose();
		poseStack.translate(x, y, z);
		Matrix4f matrix = poseStack.last().pose();
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		float time = (player.tickCount + tickDelta) * (0.16F + speed * 0.28F);
		addPixelTornado(buffer, matrix, time, inertiaX, inertiaZ, yawTurn, speed, cameraRight, cameraUp);
		BufferUploader.drawWithShader(buffer.buildOrThrow());
		poseStack.popPose();
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
				float x = quantize(Mth.cos(angle) * radius + inertiaX * lean + jitter);
				float y = quantize(0.05F + t * 2.42F);
				float z = quantize(Mth.sin(angle) * radius + inertiaZ * lean - jitter * 0.55F);
				float size = WORLD_PIXEL * (0.85F + (i % 5 == 0 ? 0.65F : 0.0F) + speed * 0.8F);
				float baseFade = Mth.clamp(t * 4.5F, 0.0F, 1.0F);
				float tipFade = Mth.clamp((1.0F - t) * 1.9F, 0.0F, 1.0F);
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
			float ringRadius = 0.16F + t * 0.62F;
			int count = 10 + ring * 2;
			for (int i = 0; i < count; i += 2) {
				float angle = time * (3.5F + ring * 0.12F) + i * 6.28318F / count;
				float lean = t * t * t;
				float x = quantize(Mth.cos(angle) * ringRadius + inertiaX * lean);
				float z = quantize(Mth.sin(angle) * ringRadius + inertiaZ * lean);
				addPixel(buffer, matrix, cameraRight, cameraUp, x, y, z, WORLD_PIXEL * 0.9F, ring, i, 174, 130, 72, 44 - ring * 4);
			}
		}
	}

	private static float quantize(float value) {
		return Math.round(value / WORLD_PIXEL) * WORLD_PIXEL;
	}

	private static float pixelHash(int a, int b, float time) {
		float seed = Mth.sin(a * 37.13F + b * 17.97F + Mth.floor(time * 8.0F) * 0.73F) * 43758.5453F;
		return (seed - Mth.floor(seed)) * 2.0F - 1.0F;
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

	private static void texturedVertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, float u, float v, int red, int green, int blue, int alpha) {
		buffer.addVertex(matrix, x, y, z).setUv(u, v).setColor(red, green, blue, alpha);
	}
}
