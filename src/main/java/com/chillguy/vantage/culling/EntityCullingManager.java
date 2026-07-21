package com.chillguy.vantage.culling;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;

/**
 * CPU-side entity LOD/culling decisions, evaluated once per entity per frame
 * from the render mixin. GPU path (GpuLodDispatcher) runs once per tick and
 * caches results; this class checks that cache first and falls back to a
 * direct CPU calculation when no GPU result is available.
 *
 * 1.20.1 branch: uses Camera#getPos() / Entity#getPos() — the original
 * method names before the 1.21.9+ rework renamed them to getCameraPos() /
 * getEntityPos(). Don't copy this file's method names to the main/1.21.11
 * branch or vice versa.
 */
public final class EntityCullingManager {

	public static final EntityCullingManager INSTANCE = new EntityCullingManager();

	// Tune these — good starting values for a 128-160 render distance setup.
	private static final double FULL_DETAIL_RANGE_SQ = 24 * 24;   // full render + full AI-driven animation
	private static final double SIMPLIFIED_RANGE_SQ = 48 * 48;    // render, skip minor animation detail
	private static final double CULL_RANGE_SQ = 96 * 96;          // beyond this: skip non-essential entities entirely

	private EntityCullingManager() {}

	public enum Detail { FULL, SIMPLIFIED, SKIP }

	public Detail evaluate(Entity entity) {
		// Never touch the player, vehicles the player rides, or anything
		// with a nameplate/glow — those are always visually relevant.
		if (entity == null || entity.hasCustomName() || entity.isGlowing()) {
			return Detail.FULL;
		}

		// GPU pass runs once per tick and caches a result per entity ID —
		// use it when available (see GpuLodDispatcher for why tick-rate,
		// not frame-rate). Falls through to the CPU path below otherwise,
		// e.g. GPU unsupported, or entity too new to have been collected yet.
		Detail gpuResult = com.chillguy.vantage.gpu.GpuLodDispatcher.INSTANCE.getCached(entity.getId());
		if (gpuResult != null) {
			return gpuResult;
		}

		Vec3d cameraPos = getCameraPos();
		if (cameraPos == null) {
			return Detail.FULL; // camera not ready yet (e.g. very first frames) — don't cull blindly
		}

		double distSq = entity.getPos().squaredDistanceTo(cameraPos);

		if (distSq <= FULL_DETAIL_RANGE_SQ) {
			return Detail.FULL;
		}

		// Hostile mobs actively targeting the player stay visible longer —
		// don't let a creeper pop out of nowhere because it was "simplified".
		if (entity instanceof LivingEntity living && isThreatening(living) && distSq <= CULL_RANGE_SQ) {
			return Detail.SIMPLIFIED;
		}

		if (distSq <= SIMPLIFIED_RANGE_SQ) {
			return Detail.SIMPLIFIED;
		}

		if (distSq <= CULL_RANGE_SQ) {
			return Detail.SKIP; // skipped only if not threatening (checked above)
		}

		return Detail.SKIP;
	}

	private Vec3d getCameraPos() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.gameRenderer == null) return null;
		Camera camera = client.gameRenderer.getCamera();
		return camera != null ? camera.getPos() : null;
	}

	private boolean isThreatening(LivingEntity living) {
		if (living.getAttacker() != null) return true;
		return living instanceof MobEntity mob && mob.getTarget() != null;
	}
}
