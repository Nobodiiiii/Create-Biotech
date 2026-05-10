package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;

import net.createmod.catnip.lang.Lang;

public enum CreeperBlastChamberVaultRole implements INamedIconOptions {
	INPUT(AllIcons.I_FULL_REQUESTS),
	OUTPUT(AllIcons.I_SEND_ONLY);

	private final AllIcons icon;
	private final String translationKey;

	CreeperBlastChamberVaultRole(AllIcons icon) {
		this.icon = icon;
		this.translationKey =
			CreateBiotech.MOD_ID + ".creeper_blast_chamber.vault_role." + Lang.asId(name());
	}

	public CreeperBlastChamberVaultRole opposite() {
		return this == INPUT ? OUTPUT : INPUT;
	}

	@Override
	public AllIcons getIcon() {
		return icon;
	}

	@Override
	public String getTranslationKey() {
		return translationKey;
	}
}
