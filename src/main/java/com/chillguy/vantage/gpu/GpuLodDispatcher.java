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

public final class GpuLodDispatcher {

	public static final GpuLodDispatcher INSTANCE = new GpuLodDispatcher();

	private static final double COLLECT_RADIUS = 128.0;
	private static final int ENTITY_STRIDE_FLOATS = 8;

	private final Map<Integer, EntityCullingManager.Detail> resultsByEntityId = new HashMap<>();
	private int debugLogTick = 0;

	private GpuLodDispatcher() {}

	public void onClientTick(MinecraftClient client) {
		if (client.world == null || client.player == null) return;

		if (!GpuLodManager.INSTANCE.isSupported()) {
			GpuLodManager.INSTANCE.init();
			if (!GpuLodManager.INSTANCE.isSupported()) return;
		}

		Camera camera = client.gameRenderer.getCamera();
		if (camera == null) return;
		Vec3d cameraPos = camera.getCameraPos();

		Box collectBox = Box.of(cameraPos, COLLECT_RADIUS * 2, COLLECT_RADIUS * 2, COLLECT_RADIUS * 2);
		List<Entity> nearby = client.world.getOtherEntities(null, collectBox, e -> true);

		int count = Math.min(nearby.size(), 4096);
		float[] packed = new float[count * ENTITY_STRIDE_FLOATS];
		int[] entityIds = new int[count];

		for (int i = 0; i < count; i++) {
			Entity entity = nearby.get(i);
			Vec3d relPos = entity.getEntityPos().subtract(cameraPos);
			int base = i * ENTITY_STRIDE_FLOATS;
			packed[base] = (float) relPos.x;
			packed[base + 1] = (float) relPos.y;
			packed[base + 2] = (float) relPos.z;
			packed[base + 3] = (float) entity.getBoundingBox().getAverageSideLength() / 2f;
			packed[base + 4] = isThreatening(entity) ? 1f : 0f;
			entityIds[i] = entity.getId();
		}

		float[] frustumPlanes = FrustumPlaneExtractor.extract(client, camera);
		float[] gpuResults = GpuLodManager.INSTANCE.dispatch(
			packed, count, frustumPlanes,
			24f * 24f, 48f * 48f, 96f * 96f
		);

		resultsByEntityId.clear();
		if (gpuResults == null) return;

		for (int i = 0; i < count; i++) {
			float visible = gpuResults[i * 2];
			float tier = gpuResults[i * 2 + 1];
			EntityCullingManager.Detail detail = visible < 0.5f ? EntityCullingManager.Detail.SKIP
				: tier < 0.5f ? EntityCullingManager.Detail.FULL
				: EntityCullingManager.Detail.SIMPLIFIED;
			resultsByEntityId.put(entityIds[i], detail);
		}

		debugLogTick++;
		if (debugLogTick % 20 == 0) { // once a second at 20 TPS — remove once verified
			long full = resultsByEntityId.values().stream().filter(d -> d == EntityCullingManager.Detail.FULL).count();
			long simplified = resultsByEntityId.values().stream().filter(d -> d == EntityCullingManager.Detail.SIMPLIFIED).count();
			long skip = resultsByEntityId.values().stream().filter(d -> d == EntityCullingManager.Detail.SKIP).count();
			System.out.println("[Vantage DEBUG] entities=" + count + " full=" + full + " simplified=" + simplified + " skip=" + skip);
		}
	}

	public EntityCullingManager.Detail getCached(int entityId) {
		return resultsByEntityId.get(entityId);
	}

	private boolean isThreatening(Entity entity) {
		if (!(entity instanceof LivingEntity living)) return false;
		if (living.getAttacker() != null) return true;
		return living instanceof MobEntity mob && mob.getTarget() != null;
	}
}
