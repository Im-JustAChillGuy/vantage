package com.chillguy.vantage;

public class VantageClient implements net.fabricmc.api.ClientModInitializer {

	public static final String MOD_ID = "vantage";

	@Override
	public void onInitializeClient() {
		// Nothing to register here for now — EntityCullingManager pulls the
		// camera directly from MinecraftClient when evaluate() is called, so
		// no render-event hook is needed. GpuLodManager stays lazily
		// initialized from wherever it's first invoked once that's wired up.
	}
}
