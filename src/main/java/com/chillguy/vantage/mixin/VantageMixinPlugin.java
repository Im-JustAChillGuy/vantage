package com.chillguy.vantage.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * tr7zw's "Entity Culling" mod does the same shouldRender-level distance
 * culling we do. Most users already have it installed, so instead of
 * fighting over the same injection point, we detect it and step aside
 * entirely — our render-cull mixin never applies if it's present.
 */
public class VantageMixinPlugin implements IMixinConfigPlugin {

	private static final String[] KNOWN_CULLING_MOD_IDS = {
		"entityculling", // tr7zw
	};

	private boolean cullingModPresent;

	@Override
	public void onLoad(String mixinPackage) {
		cullingModPresent = false;
		for (String id : KNOWN_CULLING_MOD_IDS) {
			if (FabricLoader.getInstance().isModLoaded(id)) {
				cullingModPresent = true;
				break;
			}
		}
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (mixinClassName.endsWith("EntityRendererMixin")) {
			// Defer to the existing culling mod instead of double-injecting.
			return !cullingModPresent;
		}
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
