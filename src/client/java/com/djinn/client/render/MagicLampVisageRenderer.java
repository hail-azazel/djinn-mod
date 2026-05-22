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
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class MagicLampVisageRenderer {
	private static final ResourceLocation MODEL_ID = DjinnOriginMod.id("models/block/magic_lamp.json");
	private static final ResourceLocation SAND_NOISE_TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/sand.png");
	private static final float WORLD_PIXEL = 1.0F / 16.0F;
	private static Vec3 cachedAnchor;
	private static BlockPos hoveredLamp;
	private static BlockPos fadingLamp;
	private static String fadingName = "";
	private static float hoverStartAge;
	private static float hoverEndAge;
	private static float fadingProgress;

	private MagicLampVisageRenderer() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(MagicLampVisageRenderer::render);
	}

	private static void render(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		float age = client.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
		if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			stopHover(age);
			renderFading(event.getPoseStack(), event.getCamera().getPosition(), event.getCamera().rotation(), client, age);
			return;
		}
		BlockPos pos = hit.getBlockPos();
		if (!client.level.getBlockState(pos).is(ModBlocks.MAGIC_LAMP_BLOCK.get()) || !(client.level.getBlockEntity(pos) instanceof MagicLampBlockEntity lamp)) {
			stopHover(age);
			renderFading(event.getPoseStack(), event.getCamera().getPosition(), event.getCamera().rotation(), client, age);
			return;
		}
		if (!pos.equals(hoveredLamp)) {
			hoveredLamp = pos.immutable();
			hoverStartAge = age;
		}
		lamp.owner().ifPresent(owner -> {
			String name = lamp.ownerName().isBlank() ? owner.toString() : lamp.ownerName();
			fadingLamp = pos.immutable();
			fadingName = name;
			renderAt(event.getPoseStack(), event.getCamera().getPosition(), event.getCamera().rotation(), client, pos, name, age, smooth(Mth.clamp((age - hoverStartAge) / 22.0F, 0.0F, 1.0F)), 1.0F);
		});
	}

	private static void stopHover(float age) {
		if (hoveredLamp != null) {
			hoverEndAge = age;
			fadingProgress = smooth(Mth.clamp((age - hoverStartAge) / 22.0F, 0.0F, 1.0F));
			hoveredLamp = null;
		}
	}

	private static void renderFading(PoseStack poseStack, Vec3 camera, Quaternionf cameraRotation, Minecraft client, float age) {
		if (fadingLamp == null || fadingName.isBlank()) {
			return;
		}
		float fadeAge = age - hoverEndAge;
		if (fadeAge > 18.0F) {
			fadingLamp = null;
			fadingName = "";
			return;
		}
		float opacity = 1.0F - smooth(Mth.clamp(fadeAge / 18.0F, 0.0F, 1.0F));
		renderAt(poseStack, camera, cameraRotation, client, fadingLamp, fadingName, age, fadingProgress, opacity);
	}

	private static void renderAt(PoseStack poseStack, Vec3 camera, Quaternionf cameraRotation, Minecraft client, BlockPos pos, String name, float age, float progress, float opacity) {
		Vec3 anchor = getAnchor(client);
		double x = pos.getX() + anchor.x - camera.x;
		double y = pos.getY() + anchor.y - camera.y;
		double z = pos.getZ() + anchor.z - camera.z;
		float time = age * 0.08F;

		poseStack.pushPose();
		poseStack.translate(x, y + (1.0F - opacity) * 0.35F, z);
		poseStack.mulPose(cameraRotation);
		renderVisageGeometry(poseStack, time, progress, opacity);
		renderName(poseStack, client, name, progress, opacity);
		poseStack.popPose();
	}

	private static Vec3 getAnchor(Minecraft client) {
		if (cachedAnchor != null) {
			return cachedAnchor;
		}
		cachedAnchor = loadAnchor(client).orElse(new Vec3(0.5D, 0.78D, 0.5D));
		return cachedAnchor;
	}

	private static Optional<Vec3> loadAnchor(Minecraft client) {
		Optional<Resource> resource = client.getResourceManager().getResource(MODEL_ID);
		if (resource.isEmpty()) {
			return Optional.empty();
		}
		try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
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
							Optional<Vec3> from = object.has("from") ? readVec(object.get("from")) : Optional.empty();
							Optional<Vec3> to = object.has("to") ? readVec(object.get("to")) : Optional.empty();
							if (from.isPresent() && to.isPresent()) {
								return Optional.of(from.get().add(to.get()).scale(0.5D));
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

	private static Optional<Vec3> readVec(JsonElement element) {
		if (!element.isJsonArray()) {
			return Optional.empty();
		}
		JsonArray array = element.getAsJsonArray();
		if (array.size() < 3) {
			return Optional.empty();
		}
		return Optional.of(new Vec3(array.get(0).getAsDouble() / 16.0D, array.get(1).getAsDouble() / 16.0D, array.get(2).getAsDouble() / 16.0D));
	}

	private static float smooth(float value) {
		return value * value * (3.0F - 2.0F * value);
	}

	private static void renderVisageGeometry(PoseStack poseStack, float time, float progress, float opacity) {
		Matrix4f matrix = poseStack.last().pose();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, SAND_NOISE_TEXTURE);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		addVolumetricTail(buffer, matrix, time, progress, opacity);
		addHeadCloud(buffer, matrix, time, progress, opacity);
		BufferUploader.drawWithShader(buffer.buildOrThrow());
		RenderSystem.defaultBlendFunc();
		RenderSystem.depthMask(true);
	}

	private static void addVolumetricTail(BufferBuilder buffer, Matrix4f matrix, float time, float progress, float opacity) {
		for (int stream = 0; stream < 6; stream++) {
			float streamAngle = stream * 1.0472F;
			float flow = (time * (2.1F + stream * 0.09F) + stream * 0.23F) % 1.0F;
			for (int i = 0; i < 22; i++) {
				float t = (i + flow) / 22.0F;
				float reveal = Mth.clamp((progress - t * 0.76F) * 3.8F, 0.0F, 1.0F);
				if (reveal <= 0.0F) {
					continue;
				}
				float base = t * t * (3.0F - 2.0F * t);
				float angle = time * (4.8F + stream * 0.12F) + t * 10.0F + streamAngle;
				float radius = base * (0.24F + stream * 0.006F);
				float jitter = pixelHash(stream, i, time) * WORLD_PIXEL * 0.6F * base;
				float x = quantize(Mth.cos(angle) * radius + jitter);
				float y = quantize(t * 1.05F * reveal);
				float z = quantize(Mth.sin(angle) * radius - jitter * 0.5F);
				float size = (i % 4 == 0 ? 1.5F : 1.0F) * WORLD_PIXEL;
				float baseFade = Mth.clamp(t * 5.0F, 0.0F, 1.0F);
				float tipFade = Mth.clamp((1.0F - t) * 1.7F, 0.0F, 1.0F);
				int alpha = (int) ((58 - stream * 4) * baseFade * tipFade * reveal * opacity);
				addPixel(buffer, matrix, x, y, z, size, stream, i, 255, 210 + stream * 4, 92, alpha);
			}
		}
	}

	private static void addHeadCloud(BufferBuilder buffer, Matrix4f matrix, float time, float progress, float opacity) {
		float cloudProgress = smooth(Mth.clamp((progress - 0.46F) / 0.54F, 0.0F, 1.0F));
		for (int pixel = 0; pixel < 34; pixel++) {
			float angle = pixel * 2.39996F + time * 1.8F;
			float ring = (pixel % 9) / 8.0F;
			float radius = (0.08F + ring * 0.38F) * cloudProgress;
			float x = quantize(Mth.cos(angle) * radius + pixelHash(2, pixel, time) * WORLD_PIXEL * cloudProgress);
			float y = quantize(0.84F * cloudProgress + (pixel % 5) * WORLD_PIXEL + Mth.sin(time * 2.0F + pixel) * WORLD_PIXEL * 0.5F);
			float z = quantize(Mth.sin(angle) * radius - pixelHash(5, pixel, time) * WORLD_PIXEL * cloudProgress);
			float size = WORLD_PIXEL * (1.0F + (pixel % 3) * 0.5F);
			int alpha = (int) ((22 + (pixel % 4) * 8) * cloudProgress * opacity);
			addPixel(buffer, matrix, x, y, z, size, pixel, 0, 255, 223, 124, alpha);
		}
	}

	private static float quantize(float value) {
		return Math.round(value / WORLD_PIXEL) * WORLD_PIXEL;
	}

	private static float pixelHash(int a, int b, float time) {
		float seed = Mth.sin(a * 37.13F + b * 17.97F + Mth.floor(time * 8.0F) * 0.73F) * 43758.5453F;
		return (seed - Mth.floor(seed)) * 2.0F - 1.0F;
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

	private static void renderName(PoseStack poseStack, Minecraft client, String name, float progress, float opacity) {
		float nameProgress = smooth(Mth.clamp((progress - 0.62F) / 0.38F, 0.0F, 1.0F));
		if (nameProgress <= 0.0F) {
			return;
		}
		int visibleChars = Math.max(1, Mth.ceil(name.length() * nameProgress));
		String visibleName = name.substring(0, Math.min(name.length(), visibleChars));
		int textAlpha = (int) (255.0F * nameProgress * opacity);
		poseStack.pushPose();
		poseStack.translate(0.0F, 1.18F * nameProgress, 0.0F);
		float scale = 0.014F + 0.004F * nameProgress;
		poseStack.scale(-scale, -scale, scale);
		float width = client.font.width(visibleName) / 2.0F;
		MultiBufferSource.BufferSource immediate = client.renderBuffers().bufferSource();
		client.font.drawInBatch(Component.literal(visibleName), -width, 0.0F, textAlpha << 24 | 0xFFF2BE, false, poseStack.last().pose(), immediate, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
		immediate.endBatch();
		poseStack.popPose();
	}

	private static void texturedVertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, float u, float v, int red, int green, int blue, int alpha) {
		buffer.addVertex(matrix, x, y, z).setUv(u, v).setColor(red, green, blue, alpha);
	}
}
