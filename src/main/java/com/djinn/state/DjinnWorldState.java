package com.djinn.state;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DjinnWorldState extends PersistentState {
	private static final String KEY = "djinn_state";
	private final Map<UUID, DjinnPlayerData> players = new HashMap<>();
	private final List<ScheduledGameruleRevert> gameruleReverts = new ArrayList<>();

	public static DjinnWorldState get(MinecraftServer server) {
		PersistentStateManager manager = server.getWorld(World.OVERWORLD).getPersistentStateManager();
		return manager.getOrCreate(DjinnWorldState::fromNbt, DjinnWorldState::new, KEY);
	}

	public static DjinnWorldState fromNbt(NbtCompound nbt) {
		DjinnWorldState state = new DjinnWorldState();
		NbtList playersNbt = nbt.getList("Players", NbtCompound.COMPOUND_TYPE);
		for (int i = 0; i < playersNbt.size(); i++) {
			NbtCompound entry = playersNbt.getCompound(i);
			UUID id = entry.getUuid("Id");
			state.players.put(id, DjinnPlayerData.fromNbt(id, entry.getCompound("Data")));
		}
		NbtList revertsNbt = nbt.getList("GameruleReverts", NbtCompound.COMPOUND_TYPE);
		for (int i = 0; i < revertsNbt.size(); i++) {
			state.gameruleReverts.add(ScheduledGameruleRevert.fromNbt(revertsNbt.getCompound(i)));
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtList playersNbt = new NbtList();
		for (Map.Entry<UUID, DjinnPlayerData> entry : players.entrySet()) {
			NbtCompound playerNbt = new NbtCompound();
			playerNbt.putUuid("Id", entry.getKey());
			playerNbt.put("Data", entry.getValue().toNbt());
			playersNbt.add(playerNbt);
		}
		nbt.put("Players", playersNbt);

		NbtList revertsNbt = new NbtList();
		for (ScheduledGameruleRevert revert : gameruleReverts) {
			revertsNbt.add(revert.toNbt());
		}
		nbt.put("GameruleReverts", revertsNbt);
		return nbt;
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
