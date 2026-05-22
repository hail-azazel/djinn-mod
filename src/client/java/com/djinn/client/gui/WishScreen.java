package com.djinn.client.gui;

import com.djinn.network.DjinnNetworking;
import com.djinn.wish.DjinnWishBlacklist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class WishScreen extends Screen {
	private static final int PANEL_WIDTH = 338;
	private static final int PANEL_HEIGHT = 238;
	private static final int LIST_ROWS = 6;
	private final int remainingWishes;
	private final CompoundTag currentGamerules;
	private final List<String> gamerules = collectGamerules();
	private long openedAt;
	private EditBox search;
	private Tab tab = Tab.ITEMS;
	private String lastQuery = "";
	private List<Entry> matches = List.of();

	public WishScreen(int remainingWishes, CompoundTag currentGamerules) {
		super(Component.translatable("screen.djinn.wishes"));
		this.remainingWishes = remainingWishes;
		this.currentGamerules = currentGamerules == null ? new CompoundTag() : currentGamerules;
	}

	@Override
	protected void init() {
		clearWidgets();
		openedAt = openedAt == 0 ? System.currentTimeMillis() : openedAt;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_AMBIENT, 0.45F, 0.75F));
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.55F, 1.35F));
		int left = (width - PANEL_WIDTH) / 2;
		int top = (height - PANEL_HEIGHT) / 2;
		search = new EditBox(font, left + 18, top + 68, PANEL_WIDTH - 36, 20, Component.translatable("screen.djinn.search_any"));
		search.setHint(Component.translatable("screen.djinn.search_any"));
		search.setMaxLength(64);
		search.setBordered(false);
		addRenderableWidget(search);
		setFocused(search);
		refreshMatches("");
	}

	private void switchTab(Tab tab) {
		this.tab = tab;
		search.setValue("");
		refreshMatches("");
	}

	@Override
	public void tick() {
		if (!search.getValue().equals(lastQuery)) {
			refreshMatches(search.getValue());
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		renderBackground(graphics, mouseX, mouseY, delta);
		int left = (width - PANEL_WIDTH) / 2;
		int top = (height - PANEL_HEIGHT) / 2;
		float time = (System.currentTimeMillis() - openedAt) / 1000.0F + delta;
		renderEtherealBackdrop(graphics, time, mouseX, mouseY);
		renderDjinnPanel(graphics, left, top, time);
		graphics.drawCenteredString(font, title, width / 2, top + 14, 0xFFEAC36A);
		graphics.drawCenteredString(font, title, width / 2, top + 13, 0x55FFFFFF);
		graphics.drawString(font, Component.translatable("screen.djinn.remaining", remainingWishes), left + 18, top + 29, 0xFF85A8F0);
		renderTabs(graphics, left, top, mouseX, mouseY, time);
		renderSearchFrame(graphics, left, top, time);
		search.render(graphics, mouseX, mouseY, delta);
		renderMatches(graphics, left, top, mouseX, mouseY);
		renderTurbulenceOverlay(graphics, left, top, time);
		super.render(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int left = (width - PANEL_WIDTH) / 2;
		int top = (height - PANEL_HEIGHT) / 2;
		Tab clickedTab = tabAt(left, top, mouseX, mouseY);
		if (clickedTab != null) {
			switchTab(clickedTab);
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.45F, 0.95F));
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.25F, 1.8F));
			return true;
		}
		for (int i = 0; i < matches.size(); i++) {
			int y = top + 100 + i * 18;
			if (mouseX >= left + 18 && mouseX <= left + PANEL_WIDTH - 18 && mouseY >= y && mouseY <= y + 16) {
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_POWER_SELECT, 0.55F, 1.25F));
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ILLUSIONER_CAST_SPELL, 0.35F, 0.85F));
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

	private void renderTabs(GuiGraphics graphics, int left, int top, int mouseX, int mouseY, float time) {
		int tabY = top + 42;
		for (Tab candidate : Tab.values()) {
			int x = left + 18 + candidate.ordinal() * 104;
			boolean active = candidate == tab;
			boolean hovered = mouseX >= x && mouseX <= x + 94 && mouseY >= tabY && mouseY <= tabY + 20;
			Component label = switch (candidate) {
				case ITEMS -> Component.translatable("screen.djinn.category_items");
				case ORIGINS -> Component.translatable("screen.djinn.category_origin");
				case GAMERULES -> Component.translatable("screen.djinn.category_world");
			};
			int pulse = (int) (Math.sin(time * 3.4F + candidate.ordinal()) * 18.0F);
			int fill = active ? 0xAA3455B7 : hovered ? 0x775A73CF : 0x44211725;
			graphics.fill(x, tabY, x + 94, tabY + 20, fill);
			graphics.fill(x, tabY, x + 94, tabY + 1, (active ? 0xCCFFE3A0 : 0x66EAC36A) + (Math.max(0, pulse) << 24));
			graphics.fill(x, tabY + 19, x + 94, tabY + 20, active ? 0xCC5E7DFF : 0x553455B7);
			graphics.fill(x, tabY, x + 1, tabY + 20, 0x88FFD56F);
			graphics.fill(x + 93, tabY, x + 94, tabY + 20, 0x663455B7);
			graphics.drawCenteredString(font, trim(label, 84), x + 47, tabY + 6, active || hovered ? 0xFFFFE3A0 : 0xFFE6D4A7);
		}
	}

	private void renderMatches(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
		Component heading = switch (tab) {
			case ITEMS -> Component.translatable("screen.djinn.category_items");
			case ORIGINS -> Component.translatable("screen.djinn.category_origin");
			case GAMERULES -> Component.translatable("screen.djinn.category_world");
		};
		graphics.drawString(font, heading, left + 18, top + 91, 0xFFEAC36A);
		for (int i = 0; i < matches.size(); i++) {
			Entry entry = matches.get(i);
			int y = top + 100 + i * 18;
			boolean hovered = mouseX >= left + 18 && mouseX <= left + PANEL_WIDTH - 18 && mouseY >= y && mouseY <= y + 16;
			int wobble = (int) (Math.sin((System.currentTimeMillis() / 180.0) + i * 0.7) * 2.0);
			graphics.fill(left + 18 + wobble, y, left + PANEL_WIDTH - 18 + wobble, y + 16, hovered ? 0x884B67C6 : 0x55221525);
			graphics.fill(left + 18 + wobble, y, left + PANEL_WIDTH - 18 + wobble, y + 1, hovered ? 0xAAFFE3A0 : 0x44FFE3A0);
			if (entry.item != null) {
				graphics.renderItem(new ItemStack(entry.item), left + 21, y);
			}
			graphics.drawString(font, trim(entry.label, 116), left + 42, y + 4, hovered ? 0xFFFFE3A0 : 0xFFE6D4A7);
			graphics.drawString(font, trim(entry.detail, 130), left + 184, y + 4, 0xFF7890D4);
		}
		if (matches.isEmpty()) {
			graphics.drawCenteredString(font, Component.translatable("screen.djinn.no_results"), width / 2, top + 133, 0xFFB8A782);
		}
	}

	private Component trim(Component text, int maxWidth) {
		String value = text.getString();
		while (font.width(value) > maxWidth && value.length() > 3) {
			value = value.substring(0, value.length() - 2);
		}
		return Component.literal(value.equals(text.getString()) ? value : value + ".");
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
		return BuiltInRegistries.ITEM.stream()
				.filter(item -> {
					ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
					String name = item.getName(new ItemStack(item)).getString().toLowerCase(Locale.ROOT);
					if (!DjinnWishBlacklist.itemAllowed(id)) {
						return false;
					}
					return needle.isEmpty() ? id.getNamespace().equals("djinn") || id.getPath().contains("diamond") : id.toString().contains(needle) || name.contains(needle);
				})
				.sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
				.limit(LIST_ROWS)
				.map(item -> new Entry(Tab.ITEMS, item.getName(new ItemStack(item)), Component.literal(BuiltInRegistries.ITEM.getKey(item).toString()), item, null, null))
				.toList();
	}

	private List<Entry> originMatches(String needle) {
		return collectOrigins().stream()
				.filter(DjinnWishBlacklist::originAllowed)
				.map(id -> new Entry(Tab.ORIGINS, Component.literal(titleCase(id.getPath())), Component.literal(id.toString()), null, id, null))
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
				.map(rule -> new Entry(Tab.GAMERULES, Component.literal(rule), Component.literal(suggestValue(rule)), null, null, rule))
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
		GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
			@Override
			public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
				rules.add(key.getId());
			}
		});
		return rules;
	}

	private List<ResourceLocation> collectOrigins() {
		Set<ResourceLocation> origins = new LinkedHashSet<>();
		Minecraft client = Minecraft.getInstance();
		client.getResourceManager().listResources("origins", id -> id.getPath().endsWith(".json")).keySet().forEach(resource -> {
			String path = resource.getPath();
			if (path.startsWith("origins/") && path.endsWith(".json")) {
				origins.add(ResourceLocation.fromNamespaceAndPath(resource.getNamespace(), path.substring("origins/".length(), path.length() - ".json".length())));
			}
		});
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
		if (entry.tab == Tab.ITEMS) {
			PacketDistributor.sendToServer(new DjinnNetworking.MakeWishPayload(0, BuiltInRegistries.ITEM.getKey(entry.item), Math.min(64, Math.max(1, entry.item.getDefaultMaxStackSize())), "", "", null));
		} else if (entry.tab == Tab.ORIGINS) {
			PacketDistributor.sendToServer(new DjinnNetworking.MakeWishPayload(1, null, 1, "", "", entry.origin));
		} else {
			PacketDistributor.sendToServer(new DjinnNetworking.MakeWishPayload(3, null, 1, entry.gamerule, suggestValue(entry.gamerule), null));
		}
		onClose();
	}

	private void renderEtherealBackdrop(GuiGraphics graphics, float time, int mouseX, int mouseY) {
		graphics.fill(0, 0, width, height, 0xD0060308);
		int sunX = width / 2 + (int) (Math.sin(time * 0.25F) * 18.0F);
		int sunY = height / 2 - 94 + (int) (Math.cos(time * 0.18F) * 8.0F);
		for (int ring = 9; ring >= 0; ring--) {
			int r = 18 + ring * 15;
			int alpha = 10 + ring * 5;
			graphics.fill(sunX - r, sunY - r / 3, sunX + r, sunY + r / 3, alpha << 24 | 0xFFE8A6);
		}
		for (int ray = 0; ray < 28; ray++) {
			float phase = time * 0.7F + ray * 0.61F;
			int x = sunX + (int) (Math.sin(phase) * 34.0F) + ray * width / 28 - width / 2;
			int w = 8 + (ray % 5) * 4;
			graphics.fillGradient(x, 0, x + w, height, 0x34FFE6A1, 0x00201708);
		}
		for (int flare = 0; flare < 7; flare++) {
			float t = flare / 6.0F;
			int x = (int) (sunX + (mouseX - sunX) * (t - 0.28F));
			int y = (int) (sunY + (mouseY - sunY) * (t - 0.28F));
			int size = 8 + flare * 6;
			int color = (30 - flare * 3) << 24 | (flare % 2 == 0 ? 0xFFE47A : 0x6D82FF);
			graphics.fill(x - size, y - 2, x + size, y + 2, color);
			graphics.fill(x - 2, y - size, x + 2, y + size, color);
		}
		for (int band = 0; band < 26; band++) {
			int y = (int) ((band * 13 + time * 34.0F) % (height + 32)) - 16;
			int offset = (int) (Math.sin(time * 2.7F + band * 0.6F) * 24.0F);
			graphics.fillGradient(offset, y, width + offset, y + 2, 0x14FFE6A6, 0x00160B05);
		}
	}

	private void renderSearchFrame(GuiGraphics graphics, int left, int top, float time) {
		int x1 = left + 16;
		int y1 = top + 66;
		int x2 = left + PANEL_WIDTH - 16;
		int y2 = top + 90;
		int pulse = 42 + (int) (Math.sin(time * 3.2F) * 18.0F);
		graphics.fill(x1 - 2, y1 - 2, x2 + 2, y2 + 2, pulse << 24 | 0xEAC36A);
		graphics.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0x993455B7);
	}

	private void renderTurbulenceOverlay(GuiGraphics graphics, int left, int top, float time) {
		for (int i = 0; i < 18; i++) {
			int y = top + 20 + i * 11;
			int xShift = (int) (Math.sin(time * 4.1F + i * 0.77F) * 7.0F);
			graphics.fillGradient(left + 12 + xShift, y, left + PANEL_WIDTH - 12 + xShift, y + 1, 0x12FFFFFF, 0x003455B7);
		}
		for (int i = 0; i < 5; i++) {
			int x = left + 36 + i * 64 + (int) (Math.sin(time * 2.0F + i) * 4.0F);
			graphics.fillGradient(x, top + 18, x + 3, top + PANEL_HEIGHT - 18, 0x18FFE6A1, 0x00211725);
		}
	}

	private void renderDjinnPanel(GuiGraphics graphics, int left, int top, float time) {
		for (int aura = 0; aura < 5; aura++) {
			int pad = 14 + aura * 8;
			int alpha = 34 - aura * 5;
			graphics.fillGradient(left - pad, top - pad, left + PANEL_WIDTH + pad, top + PANEL_HEIGHT + pad, alpha << 24 | 0xF2C75B, 0x00100918);
		}
		graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xEE120B17);
		graphics.fill(left + 4, top + 4, left + PANEL_WIDTH - 4, top + PANEL_HEIGHT - 4, 0xFF251826);
		graphics.fill(left + 8, top + 8, left + PANEL_WIDTH - 8, top + PANEL_HEIGHT - 8, 0xF20A0710);
		for (int shimmer = 0; shimmer < 16; shimmer++) {
			int y = top + 15 + shimmer * 12;
			int xShift = (int) (Math.sin(time * 2.8F + shimmer) * 9.0F);
			graphics.fill(left + 11 + xShift, y, left + PANEL_WIDTH - 11 + xShift, y + 1, 0x12FFDFA2);
		}
		graphics.fill(left, top, left + PANEL_WIDTH, top + 3, 0xFFFFD56F);
		graphics.fill(left, top + PANEL_HEIGHT - 3, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFFFFD56F);
		graphics.fill(left, top, left + 3, top + PANEL_HEIGHT, 0xFFFFD56F);
		graphics.fill(left + PANEL_WIDTH - 3, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFFFFD56F);
		graphics.fill(left + 8, top + 8, left + PANEL_WIDTH - 8, top + 11, 0xFF3455B7);
		graphics.fill(left + 8, top + PANEL_HEIGHT - 11, left + PANEL_WIDTH - 8, top + PANEL_HEIGHT - 8, 0xFF3455B7);
	}

	private enum Tab {
		ITEMS,
		ORIGINS,
		GAMERULES
	}

	private record Entry(Tab tab, Component label, Component detail, Item item, ResourceLocation origin, String gamerule) {
	}
}
