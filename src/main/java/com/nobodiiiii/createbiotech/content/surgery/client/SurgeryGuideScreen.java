package com.nobodiiiii.createbiotech.content.surgery.client;

import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Reads the surgery guide in the vanilla book view, paginated for the active language. */
@OnlyIn(Dist.CLIENT)
public class SurgeryGuideScreen extends BookViewScreen {
	private SurgeryGuideScreen(BookAccess access) {
		super(access);
	}

	public static void open() {
		Minecraft minecraft = Minecraft.getInstance();
		ScreenOpener.open(new SurgeryGuideScreen(new BookAccess(SurgeryGuidePages.build(minecraft.font))));
	}
}
