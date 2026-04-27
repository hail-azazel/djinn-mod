package com.djinn.state;

import net.minecraft.nbt.NbtCompound;

public record ScheduledGameruleRevert(String rule, String previousValue, long executeAt) {
	public static ScheduledGameruleRevert fromNbt(NbtCompound nbt) {
		return new ScheduledGameruleRevert(
				nbt.getString("Rule"),
				nbt.getString("PreviousValue"),
				nbt.getLong("ExecuteAt")
		);
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putString("Rule", rule);
		nbt.putString("PreviousValue", previousValue);
		nbt.putLong("ExecuteAt", executeAt);
		return nbt;
	}
}
