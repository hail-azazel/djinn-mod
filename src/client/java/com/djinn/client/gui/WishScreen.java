package com.djinn.client.gui;

import com.djinn.network.DjinnNetworking;
import com.djinn.wish.DjinnWishBlacklist;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;

public class WishScreen extends Screen {
	private static final int PANEL_WIDTH = 338;
	private static final int PANEL_HEIGHT = 238;
	private static final int LIST_ROWS = 6;
	private final int remainingWishes;
	private final NbtCompound currentGamerules;
	private final List<String> gamerules = collectGamerules();
	private long openedAt;
	private TextFieldWidget search;
	private Tab tab = Tab.ITEMS;
	private String lastQuery = "";
	private List<Entry> matches = List.of();

	public WishScreen(int remainingWishes, NbtCompound currentGamerules) {
		super(Text.translatable("screen.djinn.wishes"));
		this.remainingWishes = remainingWishes;
		this.currentGamerules = currentGamerules == null ? new NbtCompound() : currentGamerules;
	}

	@Override
	protected void init() {
		clearChildren();
		openedAt = openedAt == 0 ? System.currentTimeMillis() : openedAt;
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_BEACON_AMBIENT, 0.75F, 0.45F));
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.35F, 0.55F));
		int left = (width - PANEL_WIDTH) / 2;
		int top = (height - PANEL_HEIGHT) / 2;
		search = new TextFieldWidget(textRenderer, left + 18, top + 68, PANEL_WIDTH - 36, 20, Text.translatable("screen.djinn.search_any"));
		search.setPlaceholder(Text.translatable("screen.djinn.search_any"));
		search.setMaxLength(64);
		search.setDrawsBackground(false);
		addSelectableChild(search);
		setInitialFocus(search);
		refreshMatches("");
	}

	private void switchTab(Tab tab) {
		this.tab = tab;
		search.setText("");
		refreshMatches("");
	}

	@Override
	public void tick() {
		search.tick();
		if (!search.getText().equals(lastQuery)) {
			refreshMatches(search.getText());
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		int left = (width - PANEL_WIDTH) / 2;
		int top = (height - PANEL_HEIGHT) / 2;
		float time = (System.currentTimeMillis() - openedAt) / 1000.0F + delta;
		renderEtherealBackdrop(context, time, mouseX, mouseY);
		renderDjinnPanel(context, left, top, time);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 14, 0xFFEAC36A);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 13, 0x55FFFFFF);
		context.drawTextWithShadow(textRenderer, Text.translatable("screen.djinn.remaining", remainingWishes), left + 18, top + 29, 0xFF85A8F0);
		renderTabs(context, left, top, mouseX, mouseY, time);
		renderSearchFrame(context, left, top, time);
		search.render(context, mouseX, mouseY, delta);
		renderMatches(context, left, top, mouseX, mouseY);
		renderTurbulenceOverlay(context, left, top, time);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int left = (width - PANEL_WIDTH) / 2;
		int top = (height - PANEL_HEIGHT) / 2;
		Tab clickedTab = tabAt(left, top, mouseX, mouseY);
		if (clickedTab != null) {
			switchTab(clickedTab);
			MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 0.95F, 0.45F));
			MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.8F, 0.25F));
			return true;
		}
		for (int i = 0; i < matches.size(); i++) {
			int y = top + 100 + i * 18;
			if (mouseX >= left + 18 && mouseX <= left + PANEL_WIDTH - 18 && mouseY >= y && mouseY <= y + 16) {
				MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.25F, 0.55F));
				MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, 0.85F, 0.35F));
				sendEntry(matches.get(i));
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private Tab tabAt(int left, int top, double mouseX, double mouseY) {
		int tabY = top + 42;
		for (Tab candidate : Tab.values()) {
			int x = left + 18 + candidate.ordinal() * 104;
			if (mouseX >= x && mouseX <= x + 94 && mouseY >= tabY && mouseY <= tabY + 20) {
				return candidate;
			}
		}
		return null;
	}

	private void renderTabs(DrawContext context, int left, int top, int mouseX, int mouseY, float time) {
		int tabY = top + 42;
		for (Tab candidate : Tab.values()) {
			int x = left + 18 + candidate.ordinal() * 104;
			boolean active = candidate == tab;
			boolean hovered = mouseX >= x && mouseX <= x + 94 && mouseY >= tabY && mouseY <= tabY + 20;
			Text label = switch (candidate) {
				case ITEMS -> Text.translatable("screen.djinn.category_items");
				case ORIGINS -> Text.translatable("screen.djinn.category_origin");
				case GAMERULES -> Text.translatable("screen.djinn.category_world");
			};
			int pulse = (int) (Math.sin(time * 3.4F + candidate.ordinal()) * 18.0F);
			int fill = active ? 0xAA3455B7 : hovered ? 0x775A73CF : 0x44211725;
			context.fill(x, tabY, x + 94, tabY + 20, fill);
			context.fill(x, tabY, x + 94, tabY + 1, (active ? 0xCCFFE3A0 : 0x66EAC36A) + (Math.max(0, pulse) << 24));
			context.fill(x, tabY + 19, x + 94, tabY + 20, active ? 0xCC5E7DFF : 0x553455B7);
			context.fill(x, tabY, x + 1, tabY + 20, 0x88FFD56F);
			context.fill(x + 93, tabY, x + 94, tabY + 20, 0x663455B7);
			context.drawCenteredTextWithShadow(textRenderer, trim(label, 84), x + 47, tabY + 6, active || hovered ? 0xFFFFE3A0 : 0xFFE6D4A7);
		}
	}

	private void renderMatches(DrawContext context, int left, int top, int mouseX, int mouseY) {
		Text heading = switch (tab) {
			case ITEMS -> Text.translatable("screen.djinn.category_items");
			case ORIGINS -> Text.translatable("screen.djinn.category_origin");
			case GAMERULES -> Text.translatable("screen.djinn.category_world");
		};
		context.drawTextWithShadow(textRenderer, heading, left + 18, top + 91, 0xFFEAC36A);
		for (int i = 0; i < matches.size(); i++) {
			Entry entry = matches.get(i);
			int y = top + 100 + i * 18;
			boolean hovered = mouseX >= left + 18 && mouseX <= left + PANEL_WIDTH - 18 && mouseY >= y && mouseY <= y + 16;
			int wobble = (int) (Math.sin((System.currentTimeMillis() / 180.0) + i * 0.7) * 2.0);
			context.fill(left + 18 + wobble, y, left + PANEL_WIDTH - 18 + wobble, y + 16, hovered ? 0x884B67C6 : 0x55221525);
			context.fill(left + 18 + wobble, y, left + PANEL_WIDTH - 18 + wobble, y + 1, hovered ? 0xAAFFE3A0 : 0x44FFE3A0);
			if (entry.item != null) {
				context.drawItem(new ItemStack(entry.item), left + 21, y);
			}
			context.drawTextWithShadow(textRenderer, trim(entry.label, 116), left + 42, y + 4, hovered ? 0xFFFFE3A0 : 0xFFE6D4A7);
			context.drawTextWithShadow(textRenderer, trim(entry.detail, 130), left + 184, y + 4, 0xFF7890D4);
		}
		if (matches.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.djinn.no_results"), width / 2, top + 133, 0xFFB8A782);
		}
	}

	private Text trim(Text text, int maxWidth) {
		String value = text.getString();
		while (textRenderer.getWidth(value) > maxWidth && value.length() > 3) {
			value = value.substring(0, value.length() - 2);
		}
		return Text.literal(value.equals(text.getString()) ? value : value + ".");
	}

	private void refreshMatches(String query) {
		lastQuery = query;
		String needle = query.toLowerCase(Locale.ROOT).trim();
		matches = switch (tab) {
			case ITEMS -> itemMatches(needle);
			case ORIGINS -> originMatches(needle);
			case GAMERULES -> gameruleMatches(needle);
		};
	}

	private List<Entry> itemMatches(String needle) {
		return Registries.ITEM.stream()
				.filter(item -> {
					Identifier id = Registries.ITEM.getId(item);
					String name = item.getName().getString().toLowerCase(Locale.ROOT);
					if (!DjinnWishBlacklist.itemAllowed(id)) {
						return false;
					}
					return needle.isEmpty() ? id.getNamespace().equals("djinn") || id.getPath().contains("diamond") : id.toString().contains(needle) || name.contains(needle);
				})
				.sorted(Comparator.comparing(item -> Registries.ITEM.getId(item).toString()))
				.limit(LIST_ROWS)
				.map(item -> new Entry(Tab.ITEMS, item.getName(), Text.literal(Registries.ITEM.getId(item).toString()), item, null, null))
				.toList();
	}

	private List<Entry> originMatches(String needle) {
		return collectOrigins().stream()
				.filter(DjinnWishBlacklist::originAllowed)
				.map(id -> new Entry(Tab.ORIGINS, Text.literal(titleCase(id.getPath())), Text.literal(id.toString()), null, id, null))
				.filter(entry -> needle.isEmpty() || entry.detail.getString().toLowerCase(Locale.ROOT).contains(needle) || entry.label.getString().toLowerCase(Locale.ROOT).contains(needle))
				.sorted(Comparator.comparing(entry -> entry.detail.getString()))
				.limit(LIST_ROWS)
				.toList();
	}

	private List<Entry> gameruleMatches(String needle) {
		return gamerules.stream()
				.filter(rule -> needle.isEmpty() || rule.toLowerCase(Locale.ROOT).contains(needle))
				.filter(DjinnWishBlacklist::gameruleAllowed)
				.sorted()
				.limit(LIST_ROWS)
				.map(rule -> new Entry(Tab.GAMERULES, Text.literal(rule), Text.literal(suggestValue(rule)), null, null, rule))
				.toList();
	}

	private String suggestValue(String rule) {
		String current = currentGamerules.getString(rule);
		if ("true".equalsIgnoreCase(current)) {
			return "false";
		}
		if ("false".equalsIgnoreCase(current)) {
			return "true";
		}
		return switch (rule) {
			case "randomTickSpeed" -> "3";
			case "playersSleepingPercentage" -> "100";
			case "spawnRadius" -> "10";
			default -> current.isBlank() ? "true" : current;
		};
	}

	private static List<String> collectGamerules() {
		List<String> rules = new ArrayList<>();
		GameRules.accept(new GameRules.Visitor() {
			@Override
			public <T extends GameRules.Rule<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
				rules.add(key.getName());
			}
		});
		return rules;
	}

	private List<Identifier> collectOrigins() {
		Set<Identifier> origins = new LinkedHashSet<>();
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.getResourceManager() != null) {
			client.getResourceManager().findResources("origins", id -> id.getPath().endsWith(".json")).keySet().forEach(resource -> {
				String path = resource.getPath();
				if (path.startsWith("origins/") && path.endsWith(".json")) {
					origins.add(new Identifier(resource.getNamespace(), path.substring("origins/".length(), path.length() - ".json".length())));
				}
			});
		}
		origins.add(DjinnNetworking.DJINN_ORIGIN);
		origins.add(DjinnNetworking.HUMAN_ORIGIN);
		return List.copyOf(origins);
	}

	private String titleCase(String path) {
		String clean = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
		clean = clean.replace('_', ' ');
		return clean.isEmpty() ? path : clean.substring(0, 1).toUpperCase(Locale.ROOT) + clean.substring(1);
	}

	private void sendEntry(Entry entry) {
		PacketByteBuf buf = PacketByteBufs.create();
		if (entry.tab == Tab.ITEMS) {
			buf.writeVarInt(0);
			buf.writeIdentifier(Registries.ITEM.getId(entry.item));
			buf.writeVarInt(Math.min(64, Math.max(1, entry.item.getMaxCount())));
		} else if (entry.tab == Tab.ORIGINS) {
			buf.writeVarInt(1);
			buf.writeIdentifier(entry.origin);
		} else {
			buf.writeVarInt(3);
			buf.writeString(entry.gamerule);
			buf.writeString(suggestValue(entry.gamerule));
		}
		ClientPlayNetworking.send(DjinnNetworking.MAKE_WISH, buf);
		close();
	}

	private void renderEtherealBackdrop(DrawContext context, float time, int mouseX, int mouseY) {
		context.fill(0, 0, width, height, 0xD0060308);
		int sunX = width / 2 + (int) (Math.sin(time * 0.25F) * 18.0F);
		int sunY = height / 2 - 94 + (int) (Math.cos(time * 0.18F) * 8.0F);
		for (int ring = 9; ring >= 0; ring--) {
			int r = 18 + ring * 15;
			int alpha = 10 + ring * 5;
			context.fill(sunX - r, sunY - r / 3, sunX + r, sunY + r / 3, alpha << 24 | 0xFFE8A6);
		}
		for (int ray = 0; ray < 28; ray++) {
			float phase = time * 0.7F + ray * 0.61F;
			int x = sunX + (int) (Math.sin(phase) * 34.0F) + ray * width / 28 - width / 2;
			int w = 8 + (ray % 5) * 4;
			context.fillGradient(x, 0, x + w, height, 0x34FFE6A1, 0x00201708);
		}
		for (int flare = 0; flare < 7; flare++) {
			float t = flare / 6.0F;
			int x = (int) (sunX + (mouseX - sunX) * (t - 0.28F));
			int y = (int) (sunY + (mouseY - sunY) * (t - 0.28F));
			int size = 8 + flare * 6;
			int color = (30 - flare * 3) << 24 | (flare % 2 == 0 ? 0xFFE47A : 0x6D82FF);
			context.fill(x - size, y - 2, x + size, y + 2, color);
			context.fill(x - 2, y - size, x + 2, y + size, color);
		}
		for (int band = 0; band < 26; band++) {
			int y = (int) ((band * 13 + time * 34.0F) % (height + 32)) - 16;
			int offset = (int) (Math.sin(time * 2.7F + band * 0.6F) * 24.0F);
			context.fillGradient(offset, y, width + offset, y + 2, 0x14FFE6A6, 0x00160B05);
		}
	}

	private void renderSearchFrame(DrawContext context, int left, int top, float time) {
		int x1 = left + 16;
		int y1 = top + 66;
		int x2 = left + PANEL_WIDTH - 16;
		int y2 = top + 90;
		int pulse = 42 + (int) (Math.sin(time * 3.2F) * 18.0F);
		context.fill(x1 - 2, y1 - 2, x2 + 2, y2 + 2, pulse << 24 | 0xEAC36A);
		context.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0x993455B7);
	}

	private void renderTurbulenceOverlay(DrawContext context, int left, int top, float time) {
		for (int i = 0; i < 18; i++) {
			int y = top + 20 + i * 11;
			int xShift = (int) (Math.sin(time * 4.1F + i * 0.77F) * 7.0F);
			context.fillGradient(left + 12 + xShift, y, left + PANEL_WIDTH - 12 + xShift, y + 1, 0x12FFFFFF, 0x003455B7);
		}
		for (int i = 0; i < 5; i++) {
			int x = left + 36 + i * 64 + (int) (Math.sin(time * 2.0F + i) * 4.0F);
			context.fillGradient(x, top + 18, x + 3, top + PANEL_HEIGHT - 18, 0x18FFE6A1, 0x00211725);
		}
	}

	private void renderDjinnPanel(DrawContext context, int left, int top, float time) {
		for (int aura = 0; aura < 5; aura++) {
			int pad = 14 + aura * 8;
			int alpha = 34 - aura * 5;
			context.fillGradient(left - pad, top - pad, left + PANEL_WIDTH + pad, top + PANEL_HEIGHT + pad, alpha << 24 | 0xF2C75B, 0x00100918);
		}
		context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xEE120B17);
		context.fill(left + 4, top + 4, left + PANEL_WIDTH - 4, top + PANEL_HEIGHT - 4, 0xFF251826);
		context.fill(left + 8, top + 8, left + PANEL_WIDTH - 8, top + PANEL_HEIGHT - 8, 0xF20A0710);
		for (int shimmer = 0; shimmer < 16; shimmer++) {
			int y = top + 15 + shimmer * 12;
			int xShift = (int) (Math.sin(time * 2.8F + shimmer) * 9.0F);
			context.fill(left + 11 + xShift, y, left + PANEL_WIDTH - 11 + xShift, y + 1, 0x12FFDFA2);
		}
		context.fill(left, top, left + PANEL_WIDTH, top + 3, 0xFFFFD56F);
		context.fill(left, top + PANEL_HEIGHT - 3, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFFFFD56F);
		context.fill(left, top, left + 3, top + PANEL_HEIGHT, 0xFFFFD56F);
		context.fill(left + PANEL_WIDTH - 3, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFFFFD56F);
		context.fill(left + 8, top + 8, left + PANEL_WIDTH - 8, top + 11, 0xFF3455B7);
		context.fill(left + 8, top + PANEL_HEIGHT - 11, left + PANEL_WIDTH - 8, top + PANEL_HEIGHT - 8, 0xFF3455B7);
	}

	private enum Tab {
		ITEMS,
		ORIGINS,
		GAMERULES
	}

	private record Entry(Tab tab, Text label, Text detail, Item item, Identifier origin, String gamerule) {
	}
}
