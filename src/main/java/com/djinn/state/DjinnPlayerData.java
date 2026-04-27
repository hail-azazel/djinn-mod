package com.djinn.state;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

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

	public static DjinnPlayerData fromNbt(UUID playerId, NbtCompound nbt) {
		DjinnPlayerData data = new DjinnPlayerData(playerId);
		data.djinn = nbt.getBoolean("Djinn");
		data.lampWorld = nbt.contains("LampWorld") ? nbt.getString("LampWorld") : null;
		if (nbt.contains("LampX")) {
			data.lampPos = new BlockPos(nbt.getInt("LampX"), nbt.getInt("LampY"), nbt.getInt("LampZ"));
		}
		if (nbt.containsUuid("LampMaster")) {
			data.lampMaster = nbt.getUuid("LampMaster");
		}
		data.wishesUsed = nbt.getInt("WishesUsed");
		data.sandFormTicks = nbt.getInt("SandFormTicks");
		data.desertDiveToggled = !nbt.contains("DesertDiveToggled") || nbt.getBoolean("DesertDiveToggled");
		data.pendingLampReturn = nbt.getBoolean("PendingLampReturn");
		return data;
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putBoolean("Djinn", djinn);
		if (lampWorld != null && lampPos != null) {
			nbt.putString("LampWorld", lampWorld);
			nbt.putInt("LampX", lampPos.getX());
			nbt.putInt("LampY", lampPos.getY());
			nbt.putInt("LampZ", lampPos.getZ());
		}
		if (lampMaster != null) {
			nbt.putUuid("LampMaster", lampMaster);
		}
		nbt.putInt("WishesUsed", wishesUsed);
		nbt.putInt("SandFormTicks", sandFormTicks);
		nbt.putBoolean("DesertDiveToggled", desertDiveToggled);
		nbt.putBoolean("PendingLampReturn", pendingLampReturn);
		return nbt;
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
