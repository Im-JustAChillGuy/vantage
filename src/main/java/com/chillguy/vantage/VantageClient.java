package com.chillguy.vantage;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import com.chillguy.vantage.culling.EntityCullingManager;
import com.chillguy.vantage.gpu.GpuLodManager;

public class VantageClient implements ClientModInitializer {

	public static final String MOD_ID = "vantage";

	@Override
	public void onInitializeClient() {
		// Refresh camera-relative state once per frame, before entities render.
		WorldRenderEvents.START.register(context -> {
			EntityCullingManager.INSTANCE.onFrameStart(context.camera());

			// Lazily init on first frame — GL context is guaranteed to exist
			// by then. Falls back silently to CPU culling if unsupported.
			if (!GpuLodManager.INSTANCE.isSupported()) {
				GpuLodManager.INSTANCE.init();
			}
		});
	}
}
