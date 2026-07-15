package com.chillguy.vantage.mixin;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.chillguy.vantage.culling.EntityCullingManager;

/**
 * Hooks EntityRenderer#shouldRender to skip fully-culled entities before
 * Minecraft builds their render state. This does NOT yet touch animation/AI
 * tick cost (that's a separate, riskier mixin target) — it only saves
 * render-thread work, which is the safe first win.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void vantage$applyCulling(T entity, net.minecraft.client.render.Frustum frustum,
			double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {

		EntityCullingManager.Detail detail = EntityCullingManager.INSTANCE.evaluate(entity);
		if (detail == EntityCullingManager.Detail.SKIP) {
			cir.setReturnValue(false);
		}
		// FULL and SIMPLIFIED fall through to vanilla's own shouldRender logic
		// (frustum test etc). SIMPLIFIED-tier animation reduction is applied
		// elsewhere once you extend this to specific renderer mixins.
	}
}
