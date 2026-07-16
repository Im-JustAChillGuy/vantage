package com.chillguy.vantage.gpu;

import com.chillguy.vantage.culling.EntityCullingManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects nearby entities once per client tick and dispatches them to the
 * GPU LOD compute pass, caching the result per entity ID for the render
 * mixin to read.
 *
 * DELIBERATE TRADEOFF: this runs on the tick loop (20/s) rather than the
 * render loop (usually 60-240+/s). The obvious "correct" hook would be a
 * per-frame world-render event, but as of 1.21.9+ that pipeline was split
 * into an extraction/FrameGraphBuilder architecture that is still being
 * redesigned upstream (Fabric's own docs call out more renames coming in
 * 26.1). Building a mixin against that right now means re-fixing this same
 * code again next migration. Tick-rate freshness is an acceptable tradeoff
 * for a LOD/cull decision — entities don't teleport between ticks — and
 * this keeps Vantage decoupled from Minecraft's render-internals churn.
 * Revisit this once the extraction/render split stabilizes.
 *
 * UNTESTED, same caveats as GpuLodManager: not run against a real client.
 */
public final class GpuLodDispatcher {

	public static final GpuLodDispatcher INSTANCE = new GpuLodDispatcher();

	private static final double COLLECT_RADIUS = 128.0;
	private static final int ENTITY_STRIDE_FLOATS = 8;

	private final Map<Integer, EntityCullingManager.Detail> resultsByEntityId = new HashMap<>();

	private GpuLodDispatcher() {}

	/** Call from a stable per-tick hook (e.g. ClientTickEvents.END_CLIENT_TICK). */
	public void onClientTick(MinecraftClient client) {
		if (client.world == null || client.player == null) return;

		if (!GpuLodManager.INSTANCE.isSupported()) {
			GpuLodManager.INSTANCE.init(); // lazy — no-op once already attempted
			if (!GpuLodManager.INSTANCE.isSupported()) return; // fell back to CPU path, nothing to do here
		}

		Camera camera = client.gameRenderer.getCamera();
		if (camera == null) return;
		Vec3d cameraPos = camera.getCameraPos();

		Box collectBox = Box.of(cameraPos, COLLECT_RADIUS * 2, COLLECT_RADIUS * 2, COLLECT_RADIUS * 2);
		List<Entity> nearby = client.world.getOtherEntities(null, collectBox, e -> true);

		int count = Math.min(nearby.size(), 4096); // matches GpuLodManager.MAX_ENTITIES
		float[] packed = new float[count * ENTITY_STRIDE_FLOATS];
		int[] entityIds = new int[count];

		for (int i = 0; i < count; i++) {
			Entity entity = nearby.get(i);
			Vec3d relPos = entity.getEntityPos().subtract(cameraPos);
			int base = i * ENTITY_STRIDE_FLOATS;
			packed[base] = (float) relPos.x;
			packed[base + 1] = (float) relPos.y;
			packed[base + 2] = (float) relPos.z;
			packed[base + 3] = (float) entity.getBoundingBox().getAverageSideLength() / 2f; // approx radius
			packed[base + 4] = isThreatening(entity) ? 1f : 0f;
			// base+5..7 reserved/padding
			entityIds[i] = entity.getId();
		}

		float[] frustumPlanes = new float[24]; // 6 planes * vec4 — TODO: populate from actual frustum, zeroed for now
		float[] gpuResults = GpuLodManager.INSTANCE.dispatch(
			packed, count, frustumPlanes,
			24f * 24f, 48f * 48f, 96f * 96f
		);

		resultsByEntityId.clear();
		if (gpuResults == null) return; // GPU path unavailable this tick, evaluate() falls back to CPU

		for (int i = 0; i < count; i++) {
			float visible = gpuResults[i * 2];
			float tier = gpuResults[i * 2 + 1];
			EntityCullingManager.Detail detail = visible < 0.5f ? EntityCullingManager.Detail.SKIP
				: tier < 0.5f ? EntityCullingManager.Detail.FULL
				: EntityCullingManager.Detail.SIMPLIFIED;
			resultsByEntityId.put(entityIds[i], detail);
		}
	}

	/** @return cached GPU result for this entity this tick, or null if not GPU-evaluated (caller should fall back to CPU). */
	public EntityCullingManager.Detail getCached(int entityId) {
		return resultsByEntityId.get(entityId);
	}

	private boolean isThreatening(Entity entity) {
		if (!(entity instanceof LivingEntity living)) return false;
		if (living.getAttacker() != null) return true;
		return living instanceof MobEntity mob && mob.getTarget() != null;
	}
}
