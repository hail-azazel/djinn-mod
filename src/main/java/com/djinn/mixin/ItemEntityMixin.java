package com.djinn.mixin;

import com.djinn.item.DjinnLampStacks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void djinn$removeDuplicateLamp(CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;
		if (self.level().isClientSide) {
			return;
		}
		if (!DjinnLampStacks.isMagicLamp(self.getItem())) {
			return;
		}

		AABB search = self.getBoundingBox().inflate(0.5D);
		List<ItemEntity> nearby = self.level().getEntitiesOfClass(ItemEntity.class, search, it -> it != self && DjinnLampStacks.isMagicLamp(it.getItem()));
		if (nearby.isEmpty()) {
			return;
		}

		int selfId = self.getId();
		for (ItemEntity other : nearby) {
			if (other != self && other.getId() < selfId) {
				self.discard();
				return;
			}
		}
	}
}
