package com.nobodiiiii.createbiotech.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.nobodiiiii.createbiotech.registry.CBCreativeModeTabs;

import net.minecraft.world.item.CreativeModeTab;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * JEI correctly skips the empty stacks that reserve creative-tab banner rows, but normally logs
 * every one as an error. Silence that one expected diagnostic for our main tab only.
 */
@Pseudo
@Mixin(targets = "mezz.jei.library.plugins.vanilla.ingredients.ItemStackListFactory", remap = false)
public class JeiItemStackListFactoryMixin {

	@WrapOperation(method = "addFromTab", at = @At(value = "INVOKE",
		target = "Lorg/apache/logging/log4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
		ordinal = 0), require = 0)
	private static void createBiotech$ignoreSectionPadding(Logger logger, String message, Object tabArgument,
		Object displayTypeArgument, Object errorArgument, Operation<Void> original,
		@Local(argsOnly = true) CreativeModeTab tab) {
		if (!CBCreativeModeTabs.isMainTab(tab))
			original.call(logger, message, tabArgument, displayTypeArgument, errorArgument);
	}
}
