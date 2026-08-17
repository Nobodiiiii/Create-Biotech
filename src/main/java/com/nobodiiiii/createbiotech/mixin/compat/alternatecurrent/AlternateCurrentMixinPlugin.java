package com.nobodiiiii.createbiotech.mixin.compat.alternatecurrent;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Keeps the optional Alternate Current adapter inert when the optimization mod is absent. */
public final class AlternateCurrentMixinPlugin implements IMixinConfigPlugin {

	private static final String[] REQUIRED_CLASSES = {
		"alternate.current.wire.Node",
		"alternate.current.wire.WireNode",
		"alternate.current.wire.WireHandler"
	};

	private boolean alternateCurrentAvailable;

	@Override
	public void onLoad(String mixinPackage) {
		ClassLoader loader = AlternateCurrentMixinPlugin.class.getClassLoader();
		alternateCurrentAvailable = true;
		for (String className : REQUIRED_CLASSES) {
			if (loader.getResource(className.replace('.', '/') + ".class") == null) {
				alternateCurrentAvailable = false;
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
		return alternateCurrentAvailable;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
		IMixinInfo mixinInfo) {}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
		IMixinInfo mixinInfo) {}
}
