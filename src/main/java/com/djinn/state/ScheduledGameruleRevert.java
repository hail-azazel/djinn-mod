package com.djinn.state;

import net.minecraft.nbt.CompoundTag;

public record ScheduledGameruleRevert(String rule, String previousValue, long executeAt) {
	public static ScheduledGameruleRevert fromNbt(CompoundTag tag) {
		return new ScheduledGameruleRevert(
				tag.getString("Rule"),
				tag.getString("PreviousValue"),
				tag.getLong("ExecuteAt")
		);
	}

	public CompoundTag toNbt() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Rule", rule);
		tag.putString("PreviousValue", previousValue);
		tag.putLong("ExecuteAt", executeAt);
		return tag;
	}
}
