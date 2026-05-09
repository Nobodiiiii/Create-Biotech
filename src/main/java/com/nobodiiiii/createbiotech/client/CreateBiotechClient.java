package com.nobodiiiii.createbiotech.client;

import com.nobodiiiii.createbiotech.content.evokertank.EvokerTankRenderer;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberRenderer;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.client.render.SlimeBeltFunnelModel;
import com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod.FixedCarrotFishingRodRenderer;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltHelper;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltRenderer;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltSpriteShifts;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltRenderer;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltSpriteShifts;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltRenderer;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltSpriteShifts;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointRenderer;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBFluids;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.client.CasingConnectedHorizontalCTBehaviour;
import com.simibubi.create.Create;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.SimpleCTBehaviour;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CreateBiotechClient {

	@SubscribeEvent
	public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerBlockEntityRenderer(CBBlockEntityTypes.EVOKER_TANK.get(), EvokerTankRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.SLIME_BELT.get(), SlimeBeltRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.MAGMA_BELT.get(), MagmaBeltRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.POWER_BELT.get(), PowerBeltRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.UNIVERSAL_JOINT.get(), UniversalJointRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.FIXED_CARROT_FISHING_ROD.get(),
			FixedCarrotFishingRodRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.CREEPER_BLAST_CHAMBER.get(),
			CreeperBlastChamberRenderer::new);
	}

	@SubscribeEvent
	public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
		event.register(CreateBiotech.asResource("block/universal_joint_endpoint_slime_overlay"));
	}

	@SubscribeEvent
	public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
		SlimeBeltSpriteShifts.init();
		MagmaBeltSpriteShifts.init();
		PowerBeltSpriteShifts.init();
		CBSpriteShifts.init();
		event.registerReloadListener(SlimeBeltHelper.LISTENER);
		event.registerReloadListener(MagmaBeltHelper.LISTENER);
	}

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.FIXED_CARROT_FISHING_ROD.get(), RenderType.cutout());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.BLAST_PROOF_GLASS.get(), RenderType.translucent());
			ItemBlockRenderTypes.setRenderLayer(CBBlocks.BLAST_PROOF_FRAMED_GLASS.get(), RenderType.translucent());
				ItemBlockRenderTypes.setRenderLayer(CBFluids.LIQUID_LIVING_SLIME_BLOCK.get(), RenderType.translucent());
			CreateClient.MODEL_SWAPPER.getCustomBlockModels()
				.register(Create.asResource("andesite_belt_funnel"), SlimeBeltFunnelModel::new);
			CreateClient.MODEL_SWAPPER.getCustomBlockModels()
				.register(Create.asResource("brass_belt_funnel"), SlimeBeltFunnelModel::new);
			CreateClient.MODEL_SWAPPER.getCustomBlockModels()
				.register(CreateBiotech.asResource("explosion_proof_casing"),
					model -> new CTModel(model, new CasingConnectedHorizontalCTBehaviour(
						CBSpriteShifts.EXPLOSION_PROOF_CASING_SIDE, CBSpriteShifts.EXPLOSION_PROOF_CASING)));
			CreateClient.MODEL_SWAPPER.getCustomBlockModels()
				.register(CreateBiotech.asResource("blast_proof_chain_drive"),
					model -> new CTModel(model,
						new EncasedCTBehaviour(CBSpriteShifts.EXPLOSION_PROOF_CASING_SIDE)));
			CreateClient.MODEL_SWAPPER.getCustomBlockModels()
				.register(CreateBiotech.asResource("blast_proof_framed_glass"),
					model -> new CTModel(model, new SimpleCTBehaviour(CBSpriteShifts.BLAST_PROOF_FRAMED_GLASS)));
			CreateClient.CASING_CONNECTIVITY.makeCasing(CBBlocks.EXPLOSION_PROOF_CASING.get(),
				CBSpriteShifts.EXPLOSION_PROOF_CASING_SIDE);
			CreateClient.CASING_CONNECTIVITY.make(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get(),
				CBSpriteShifts.EXPLOSION_PROOF_CASING_SIDE,
				(state, face) -> face.getAxis().isHorizontal()
					&& face.getAxis() != state.getValue(BlockStateProperties.AXIS));
			if (ModList.get()
				.isLoaded("jei"))
				ItemProperties.register(CBItems.CAPTURED_SMALL_SLIME.get(),
					CreateBiotech.asResource("jei_slime_model"), (stack, level, entity, seed) -> 1);
		});
	}
}
