package com.djinn.mixin;

import com.djinn.item.DjinnLampStacks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ServerLevel.class)
public abstract class WorldMixin {
	@Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
	private void djinn$preventDuplicateLampSpawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!(entity instanceof ItemEntity item) || !DjinnLampStacks.isMagicLamp(item.getItem())) {
			return;
		}

		ServerLevel level = (ServerLevel) (Object) this;
		AABB search = item.getBoundingBox().inflate(0.5D);
		List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, search, it -> DjinnLampStacks.isMagicLamp(it.getItem()));
		if (!nearby.isEmpty()) {
			cir.setReturnValue(false);
			cir.cancel();
		}
	}
}
