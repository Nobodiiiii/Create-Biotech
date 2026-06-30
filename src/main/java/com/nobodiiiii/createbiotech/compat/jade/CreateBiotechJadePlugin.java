package com.nobodiiiii.createbiotech.compat.jade;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.Identifiers;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;

@WailaPlugin
public class CreateBiotechJadePlugin implements IWailaPlugin {

	private static final String BIONIC_NAME_PREFIX_KEY = "create_biotech.jade.bionic_prefix";

	public static final ResourceLocation BIONIC_NAME = CreateBiotech.asResource("bionic_name");

	@Override
	public void register(IWailaCommonRegistration registration) {
	}

	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.registerEntityComponent(BionicNameComponent.INSTANCE, LivingEntity.class);
	}

	/**
	 * Prefixes the name of slime mimics with the "Mimic" label.
	 * Runs after Jade's core {@code object_name} provider (priority -10100, so the name is
	 * already present), then replaces that line with a prefixed copy.
	 */
	public enum BionicNameComponent implements IEntityComponentProvider {
		INSTANCE;

		@Override
		public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
			Entity entity = accessor.getEntity();
			if (!SlimeMimicHandler.isSlimeMimic(entity))
				return;
			if (tooltip.get(Identifiers.CORE_OBJECT_NAME).isEmpty())
				return;

			MutableComponent prefixedName = Component.translatable(BIONIC_NAME_PREFIX_KEY)
				.append(entity.getDisplayName());
			tooltip.remove(Identifiers.CORE_OBJECT_NAME);
			tooltip.add(0, IThemeHelper.get().title(prefixedName), Identifiers.CORE_OBJECT_NAME);
		}

		@Override
		public ResourceLocation getUid() {
			return BIONIC_NAME;
		}
	}
}
