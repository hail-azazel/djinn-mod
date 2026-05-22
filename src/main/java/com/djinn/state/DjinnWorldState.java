package com.djinn.state;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DjinnWorldState extends SavedData {
	private static final String KEY = "djinn_state";
	private final Map<UUID, DjinnPlayerData> players = new HashMap<>();
	private final List<ScheduledGameruleRevert> gameruleReverts = new ArrayList<>();

	public static DjinnWorldState get(MinecraftServer server) {
		return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(
				new SavedData.Factory<>(DjinnWorldState::new, DjinnWorldState::fromNbt, null),
				KEY
		);
	}

	public static DjinnWorldState fromNbt(CompoundTag tag, HolderLookup.Provider registries) {
		DjinnWorldState state = new DjinnWorldState();
		ListTag playersNbt = tag.getList("Players", Tag.TAG_COMPOUND);
		for (int i = 0; i < playersNbt.size(); i++) {
			CompoundTag entry = playersNbt.getCompound(i);
			UUID id = entry.getUUID("Id");
			state.players.put(id, DjinnPlayerData.fromNbt(id, entry.getCompound("Data")));
		}
		ListTag revertsNbt = tag.getList("GameruleReverts", Tag.TAG_COMPOUND);
		for (int i = 0; i < revertsNbt.size(); i++) {
			state.gameruleReverts.add(ScheduledGameruleRevert.fromNbt(revertsNbt.getCompound(i)));
		}
		return state;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag playersNbt = new ListTag();
		for (Map.Entry<UUID, DjinnPlayerData> entry : players.entrySet()) {
			CompoundTag playerNbt = new CompoundTag();
			playerNbt.putUUID("Id", entry.getKey());
			playerNbt.put("Data", entry.getValue().toNbt());
			playersNbt.add(playerNbt);
		}
		tag.put("Players", playersNbt);

		ListTag revertsNbt = new ListTag();
		for (ScheduledGameruleRevert revert : gameruleReverts) {
			revertsNbt.add(revert.toNbt());
		}
		tag.put("GameruleReverts", revertsNbt);
		return tag;
	}

	public DjinnPlayerData player(UUID playerId) {
		return players.computeIfAbsent(playerId, DjinnPlayerData::new);
	}

	public Iterable<DjinnPlayerData> players() {
		return players.values();
	}

	public List<ScheduledGameruleRevert> gameruleReverts() {
		return gameruleReverts;
	}
}
