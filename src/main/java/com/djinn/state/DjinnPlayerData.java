package com.djinn.state;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class DjinnPlayerData {
	private final UUID playerId;
	private boolean djinn;
	private BlockPos lampPos;
	private String lampWorld;
	private UUID lampMaster;
	private int wishesUsed;
	private int sandFormTicks;
	private boolean desertDiveToggled = true;
	private boolean pendingLampReturn;

	public DjinnPlayerData(UUID playerId) {
		this.playerId = playerId;
	}

	public static DjinnPlayerData fromNbt(UUID playerId, CompoundTag tag) {
		DjinnPlayerData data = new DjinnPlayerData(playerId);
		data.djinn = tag.getBoolean("Djinn");
		data.lampWorld = tag.contains("LampWorld") ? tag.getString("LampWorld") : null;
		if (tag.contains("LampX")) {
			data.lampPos = new BlockPos(tag.getInt("LampX"), tag.getInt("LampY"), tag.getInt("LampZ"));
		}
		if (tag.hasUUID("LampMaster")) {
			data.lampMaster = tag.getUUID("LampMaster");
		}
		data.wishesUsed = tag.getInt("WishesUsed");
		data.sandFormTicks = tag.getInt("SandFormTicks");
		data.desertDiveToggled = !tag.contains("DesertDiveToggled") || tag.getBoolean("DesertDiveToggled");
		data.pendingLampReturn = tag.getBoolean("PendingLampReturn");
		return data;
	}

	public CompoundTag toNbt() {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("Djinn", djinn);
		if (lampWorld != null && lampPos != null) {
			tag.putString("LampWorld", lampWorld);
			tag.putInt("LampX", lampPos.getX());
			tag.putInt("LampY", lampPos.getY());
			tag.putInt("LampZ", lampPos.getZ());
		}
		if (lampMaster != null) {
			tag.putUUID("LampMaster", lampMaster);
		}
		tag.putInt("WishesUsed", wishesUsed);
		tag.putInt("SandFormTicks", sandFormTicks);
		tag.putBoolean("DesertDiveToggled", desertDiveToggled);
		tag.putBoolean("PendingLampReturn", pendingLampReturn);
		return tag;
	}

	public UUID playerId() {
		return playerId;
	}

	public boolean isDjinn() {
		return djinn;
	}

	public void setDjinn(boolean djinn) {
		this.djinn = djinn;
	}

	public BlockPos lampPos() {
		return lampPos;
	}

	public String lampWorld() {
		return lampWorld;
	}

	public void setLamp(String lampWorld, BlockPos lampPos) {
		this.lampWorld = lampWorld;
		this.lampPos = lampPos;
	}

	public void clearLamp() {
		this.lampWorld = null;
		this.lampPos = null;
	}

	public UUID lampMaster() {
		return lampMaster;
	}

	public void setLampMaster(UUID lampMaster) {
		this.lampMaster = lampMaster;
	}

	public int wishesUsed() {
		return wishesUsed;
	}

	public boolean canWish() {
		return wishesUsed < 3;
	}

	public void spendWish() {
		wishesUsed++;
	}

	public void resetWishes() {
		wishesUsed = 0;
	}

	public int sandFormTicks() {
		return sandFormTicks;
	}

	public void setSandFormTicks(int sandFormTicks) {
		this.sandFormTicks = Math.max(0, sandFormTicks);
	}

	public void tickSandForm() {
		if (sandFormTicks > 0) {
			sandFormTicks--;
		}
	}

	public boolean desertDiveToggled() {
		return desertDiveToggled;
	}

	public void setDesertDiveToggled(boolean desertDiveToggled) {
		this.desertDiveToggled = desertDiveToggled;
	}

	public boolean pendingLampReturn() {
		return pendingLampReturn;
	}

	public void setPendingLampReturn(boolean pendingLampReturn) {
		this.pendingLampReturn = pendingLampReturn;
	}
}
