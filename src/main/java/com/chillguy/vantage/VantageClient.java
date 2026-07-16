package com.chillguy.vantage;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.chillguy.vantage.gpu.GpuLodDispatcher;

public class VantageClient implements ClientModInitializer {

	public static final String MOD_ID = "vantage";

	@Override
	public void onInitializeClient() {
		// EntityCullingManager pulls the camera directly from MinecraftClient
		// when evaluate() is called, so no render-event hook is needed for
		// the CPU path. The GPU path collects + dispatches once per client
		// tick — see GpuLodDispatcher for why tick-rate rather than
		// render-frame-rate was chosen (rendering internals are mid-rework
		// upstream as of 1.21.9+).
		ClientTickEvents.END_CLIENT_TICK.register(GpuLodDispatcher.INSTANCE::onClientTick);
	}
}
