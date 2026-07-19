package com.chillguy.vantage.mixin;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.chillguy.vantage.culling.EntityCullingManager;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Throttles EntityRenderer#updateRenderState — the method that computes
 * limb angles / animation progress each frame, separate from the actual
 * draw call — for SIMPLIFIED-tier entities. Skipping it most frames means
 * the entity keeps rendering (unlike SKIP tier) but its pose only refreshes
 * every REFRESH_INTERVAL frames instead of every frame, cutting animation
 * CPU cost for mid-distance entities without making them disappear.
 *
 * This is the actual differentiator from Entity Culling, which only does
 * binary visible/not-visible — no animation-cost tier in between.
 *
 * UNTESTED: compiles against the confirmed-correct EntityCullingManager /
 * GpuLodDispatcher pipeline, but the throttling behavior itself (does the
 * pose visibly "stutter" at REFRESH_INTERVAL=4, is that an acceptable
 * tradeoff) has not been observed in actual play. Tune REFRESH_INTERVAL
 * down if the stutter is noticeable, up if you want more savings.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRenderStateMixin<T extends Entity> {

	private static final int REFRESH_INTERVAL = 4; // update 1 in N calls for SIMPLIFIED-tier entities

	// WeakHashMap so entries are cleared automatically as entities despawn —
	// avoids an unbounded leak from tracking every entity ever rendered.
	private static final Map<Entity, Integer> frameCounters = new WeakHashMap<>();

	@Inject(method = "updateRenderState", at = @At("HEAD"), cancellable = true)
	private void vantage$throttleSimplified(T entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
		EntityCullingManager.Detail detail = EntityCullingManager.INSTANCE.evaluate(entity);
		if (detail != EntityCullingManager.Detail.SIMPLIFIED) {
			return; // FULL always updates normally; SKIP doesn't reach here since render is already cancelled upstream
		}

		int count = frameCounters.merge(entity, 1, Integer::sum);
		if (count % REFRESH_INTERVAL != 0) {
			ci.cancel(); // skip this frame's animation recompute — reuse previous render state
		}
		// else: let it fall through to vanilla's real updateRenderState this frame
	}
}
