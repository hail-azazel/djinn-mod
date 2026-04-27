package com.djinn.client.render;

import com.djinn.DjinnOriginMod;
import com.djinn.block.MagicLampBlockEntity;
import com.djinn.block.ModBlocks;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class MagicLampVisageRenderer {
	private static final Identifier MODEL_ID = DjinnOriginMod.id("models/block/magic_lamp.json");
	private static final Identifier SAND_NOISE_TEXTURE = new Identifier("minecraft", "textures/block/sand.png");
	private static final float WORLD_PIXEL = 1.0F / 16.0F;
	private static Vec3d cachedAnchor;
	private static BlockPos hoveredLamp;
	private static BlockPos fadingLamp;
	private static String fadingName = "";
	private static float hoverStartAge;
	private static float hoverEndAge;
	private static float fadingProgress;

	private MagicLampVisageRenderer() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.world == null) {
				return;
			}
			float age = client.world.getTime() + context.tickDelta();
			if (!(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
				stopHover(age);
				renderFading(context.matrixStack(), context.camera().getPos(), client, age);
				return;
			}
			BlockPos pos = hit.getBlockPos();
			if (!client.world.getBlockState(pos).isOf(ModBlocks.MAGIC_LAMP_BLOCK) || !(client.world.getBlockEntity(pos) instanceof MagicLampBlockEntity lamp)) {
				stopHover(age);
				renderFading(context.matrixStack(), context.camera().getPos(), client, age);
				return;
			}
			if (!pos.equals(hoveredLamp)) {
				hoveredLamp = pos.toImmutable();
				hoverStartAge = age;
			}
			lamp.owner().ifPresent(owner -> {
				String name = lamp.ownerName().isBlank() ? owner.toString() : lamp.ownerName();
				fadingLamp = pos.toImmutable();
				fadingName = name;
				renderAt(context.matrixStack(), context.camera().getPos(), client, pos, name, age, smooth(MathHelper.clamp((age - hoverStartAge) / 22.0F, 0.0F, 1.0F)), 1.0F);
			});
		});
	}

	private static void stopHover(float age) {
		if (hoveredLamp != null) {
			hoverEndAge = age;
			fadingProgress = smooth(MathHelper.clamp((age - hoverStartAge) / 22.0F, 0.0F, 1.0F));
			hoveredLamp = null;
		}
	}

	private static void renderFading(MatrixStack matrices, Vec3d camera, MinecraftClient client, float age) {
		if (fadingLamp == null || fadingName.isBlank()) {
			return;
		}
		float fadeAge = age - hoverEndAge;
		if (fadeAge > 18.0F) {
			fadingLamp = null;
			fadingName = "";
			return;
		}
		float opacity = 1.0F - smooth(MathHelper.clamp(fadeAge / 18.0F, 0.0F, 1.0F));
		renderAt(matrices, camera, client, fadingLamp, fadingName, age, fadingProgress, opacity);
	}

	private static void renderAt(MatrixStack matrices, Vec3d camera, MinecraftClient client, BlockPos pos, String name, float age, float progress, float opacity) {
		Vec3d anchor = getAnchor(client);
		double x = pos.getX() + anchor.x - camera.x;
		double y = pos.getY() + anchor.y - camera.y;
		double z = pos.getZ() + anchor.z - camera.z;
		float time = age * 0.08F;

		matrices.push();
		matrices.translate(x, y + (1.0F - opacity) * 0.35F, z);
		matrices.multiply(client.gameRenderer.getCamera().getRotation());
		renderVisageGeometry(matrices, time, progress, opacity);
		renderName(matrices, client, name, progress, opacity);
		matrices.pop();
	}

	private static Vec3d getAnchor(MinecraftClient client) {
		if (cachedAnchor != null) {
			return cachedAnchor;
		}
		cachedAnchor = loadAnchor(client).orElse(new Vec3d(0.5D, 0.78D, 0.5D));
		return cachedAnchor;
	}

	private static Optional<Vec3d> loadAnchor(MinecraftClient client) {
		Optional<Resource> resource = client.getResourceManager().getResource(MODEL_ID);
		if (resource.isEmpty()) {
			return Optional.empty();
		}
		try (InputStreamReader reader = new InputStreamReader(resource.get().getInputStream(), StandardCharsets.UTF_8)) {
			JsonObject model = JsonParser.parseReader(reader).getAsJsonObject();
			if (model.has("djinn_visage_bone")) {
				return readVec(model.get("djinn_visage_bone"));
			}
			if (model.has("djinn:visage_bone")) {
				JsonElement bone = model.get("djinn:visage_bone");
				if (bone.isJsonObject() && bone.getAsJsonObject().has("origin")) {
					return readVec(bone.getAsJsonObject().get("origin"));
				}
				return readVec(bone);
			}
			if (model.has("elements") && model.get("elements").isJsonArray()) {
				for (JsonElement element : model.getAsJsonArray("elements")) {
					if (element.isJsonObject()) {
						JsonObject object = element.getAsJsonObject();
						if (object.has("name") && "djinn_visage".equals(object.get("name").getAsString())) {
							Optional<Vec3d> from = object.has("from") ? readVec(object.get("from")) : Optional.empty();
							Optional<Vec3d> to = object.has("to") ? readVec(object.get("to")) : Optional.empty();
							if (from.isPresent() && to.isPresent()) {
								return Optional.of(from.get().add(to.get()).multiply(0.5D));
							}
						}
					}
				}
			}
		} catch (Exception exception) {
			DjinnOriginMod.LOGGER.warn("Could not read magic lamp visage bone from block model.", exception);
		}
		return Optional.empty();
	}

	private static Optional<Vec3d> readVec(JsonElement element) {
		if (!element.isJsonArray()) {
			return Optional.empty();
		}
		JsonArray array = element.getAsJsonArray();
		if (array.size() < 3) {
			return Optional.empty();
		}
		return Optional.of(new Vec3d(array.get(0).getAsDouble() / 16.0D, array.get(1).getAsDouble() / 16.0D, array.get(2).getAsDouble() / 16.0D));
	}

	private static float smooth(float value) {
		return value * value * (3.0F - 2.0F * value);
	}

	private static void renderVisageGeometry(MatrixStack matrices, float time, float progress, float opacity) {
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
		RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
		RenderSystem.setShaderTexture(0, SAND_NOISE_TEXTURE);
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder buffer = tessellator.getBuffer();
		buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		addVolumetricTail(buffer, matrix, time, progress, opacity);
		addHeadCloud(buffer, matrix, time, progress, opacity);
		BufferRenderer.drawWithGlobalProgram(buffer.end());
		RenderSystem.defaultBlendFunc();
		RenderSystem.depthMask(true);
	}

	private static void addVolumetricTail(BufferBuilder buffer, Matrix4f matrix, float time, float progress, float opacity) {
		for (int stream = 0; stream < 6; stream++) {
			float streamAngle = stream * 1.0472F;
			float flow = (time * (2.1F + stream * 0.09F) + stream * 0.23F) % 1.0F;
			for (int i = 0; i < 22; i++) {
				float t = (i + flow) / 22.0F;
				float reveal = MathHelper.clamp((progress - t * 0.76F) * 3.8F, 0.0F, 1.0F);
				if (reveal <= 0.0F) {
					continue;
				}
				float base = t * t * (3.0F - 2.0F * t);
				float angle = time * (4.8F + stream * 0.12F) + t * 10.0F + streamAngle;
				float radius = base * (0.24F + stream * 0.006F);
				float jitter = pixelHash(stream, i, time) * WORLD_PIXEL * 0.6F * base;
				float x = quantize(MathHelper.cos(angle) * radius + jitter);
				float y = quantize(t * 1.05F * reveal);
				float z = quantize(MathHelper.sin(angle) * radius - jitter * 0.5F);
				float size = (i % 4 == 0 ? 1.5F : 1.0F) * WORLD_PIXEL;
				float baseFade = MathHelper.clamp(t * 5.0F, 0.0F, 1.0F);
				float tipFade = MathHelper.clamp((1.0F - t) * 1.7F, 0.0F, 1.0F);
				int alpha = (int) ((58 - stream * 4) * baseFade * tipFade * reveal * opacity);
				addPixel(buffer, matrix, x, y, z, size, stream, i, 255, 210 + stream * 4, 92, alpha);
			}
		}
	}

	private static void addHeadCloud(BufferBuilder buffer, Matrix4f matrix, float time, float progress, float opacity) {
		float cloudProgress = smooth(MathHelper.clamp((progress - 0.46F) / 0.54F, 0.0F, 1.0F));
		for (int pixel = 0; pixel < 34; pixel++) {
			float angle = pixel * 2.39996F + time * 1.8F;
			float ring = (pixel % 9) / 8.0F;
			float radius = (0.08F + ring * 0.38F) * cloudProgress;
			float x = quantize(MathHelper.cos(angle) * radius + pixelHash(2, pixel, time) * WORLD_PIXEL * cloudProgress);
			float y = quantize(0.84F * cloudProgress + (pixel % 5) * WORLD_PIXEL + MathHelper.sin(time * 2.0F + pixel) * WORLD_PIXEL * 0.5F);
			float z = quantize(MathHelper.sin(angle) * radius - pixelHash(5, pixel, time) * WORLD_PIXEL * cloudProgress);
			float size = WORLD_PIXEL * (1.0F + (pixel % 3) * 0.5F);
			int alpha = (int) ((22 + (pixel % 4) * 8) * cloudProgress * opacity);
			addPixel(buffer, matrix, x, y, z, size, pixel, 0, 255, 223, 124, alpha);
		}
	}

	private static float quantize(float value) {
		return Math.round(value / WORLD_PIXEL) * WORLD_PIXEL;
	}

	private static float pixelHash(int a, int b, float time) {
		float seed = MathHelper.sin(a * 37.13F + b * 17.97F + MathHelper.floor(time * 8.0F) * 0.73F) * 43758.5453F;
		return (seed - MathHelper.floor(seed)) * 2.0F - 1.0F;
	}

	private static void addPixel(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, float size, int uSeed, int vSeed, int red, int green, int blue, int alpha) {
		float u = (uSeed & 15) / 16.0F;
		float v = (vSeed & 15) / 16.0F;
		float du = 1.0F / 16.0F;
		float dv = 1.0F / 16.0F;
		texturedVertex(buffer, matrix, x - size, y - size, z, u, v + dv, red, green, blue, alpha);
		texturedVertex(buffer, matrix, x - size, y + size, z, u, v, red, Math.min(255, green + 18), blue, alpha);
		texturedVertex(buffer, matrix, x + size, y + size, z, u + du, v, red, Math.min(255, green + 24), Math.min(255, blue + 20), alpha);
		texturedVertex(buffer, matrix, x + size, y - size, z, u + du, v + dv, red, green, blue, alpha);
	}

	private static void renderName(MatrixStack matrices, MinecraftClient client, String name, float progress, float opacity) {
		float nameProgress = smooth(MathHelper.clamp((progress - 0.62F) / 0.38F, 0.0F, 1.0F));
		if (nameProgress <= 0.0F) {
			return;
		}
		int visibleChars = Math.max(1, MathHelper.ceil(name.length() * nameProgress));
		String visibleName = name.substring(0, Math.min(name.length(), visibleChars));
		int textAlpha = (int) (255.0F * nameProgress * opacity);
		matrices.push();
		matrices.translate(0.0F, 1.18F * nameProgress, 0.0F);
		float scale = 0.014F + 0.004F * nameProgress;
		matrices.scale(-scale, -scale, scale);
		float width = client.textRenderer.getWidth(visibleName) / 2.0F;
		VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
		client.textRenderer.draw(Text.literal(visibleName), -width, 0.0F, textAlpha << 24 | 0xFFF2BE, false, matrices.peek().getPositionMatrix(), immediate, net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
		immediate.draw();
		matrices.pop();
	}

	private static void texturedVertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, float u, float v, int red, int green, int blue, int alpha) {
		buffer.vertex(matrix, x, y, z).texture(u, v).color(red, green, blue, alpha).next();
	}

}
