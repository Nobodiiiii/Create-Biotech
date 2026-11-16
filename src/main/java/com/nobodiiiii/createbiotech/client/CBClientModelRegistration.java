package com.nobodiiiii.createbiotech.client;

import com.nobodiiiii.createbiotech.content.automaticfishreleasemachine.AutomaticFishReleaseMachineRenderer;
import com.nobodiiiii.createbiotech.content.bionicmechanism.BionicMechanismItemRenderer;
import com.nobodiiiii.createbiotech.content.experience.ExperiencePumpRenderer;
import com.nobodiiiii.createbiotech.content.boneratchet.BoneRatchetRenderer;
import com.nobodiiiii.createbiotech.content.dingdongchicken.DingDongChickenRenderer;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.client.render.SlimeBeltFunnelModel;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CardboardBoxPartials;
import com.nobodiiiii.createbiotech.content.capturenet.client.CaptureNetRenderer;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultCTBehaviour;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTableModel;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalKitItemModel;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;
import com.nobodiiiii.createbiotech.content.universaljoint.HalfShaftVisual;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;
import com.simibubi.create.content.logistics.tableCloth.TableClothModel;
import com.nobodiiiii.createbiotech.content.allay.client.render.AllayPortRenderer;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.client.CasingConnectedHorizontalCTBehaviour;
import com.simibubi.create.Create;
import com.simibubi.create.CreateClient;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.content.decoration.MetalScaffoldingCTBehaviour;
import com.simibubi.create.content.fluids.PipeAttachmentModel;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.SimpleCTBehaviour;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.data.Couple;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Internal CBClientModelRegistration implementation; public entry points remain on CreateBiotechClient. */
final class CBClientModelRegistration {
	private CBClientModelRegistration() {}


	static boolean customBlockModelsRegistered;

	static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
		java.util.function.Consumer<net.minecraft.resources.ResourceLocation> register = location ->
			event.register(new ModelResourceLocation(location, ModelResourceLocation.STANDALONE_VARIANT));
		MachineCreatureModels.allPartials()
			.forEach(partial -> register.accept(partial.modelLocation()));
		CaptureNetRenderer.registerModels(register);
		register.accept(CreateBiotech.asResource("block/universal_joint_endpoint_slime_overlay"));
		PartialModel asurineDoorFoldLeft = PartialModel.of(
			CreateBiotech.asResource("block/asurine_door/fold_left"));
		PartialModel asurineDoorFoldRight = PartialModel.of(
			CreateBiotech.asResource("block/asurine_door/fold_right"));
		AllPartialModels.FOLDING_DOORS.put(CreateBiotech.asResource("asurine_door"),
			Couple.create(asurineDoorFoldLeft, asurineDoorFoldRight));
		register.accept(asurineDoorFoldLeft.modelLocation());
		register.accept(asurineDoorFoldRight.modelLocation());
		register.accept(DingDongChickenRenderer.BELL_BASE_MODEL.modelLocation());
		register.accept(DingDongChickenRenderer.BELL_MODEL.modelLocation());
		register.accept(DingDongChickenRenderer.BELL_PLUNGER_MODEL.modelLocation());
		register.accept(AutomaticFishReleaseMachineRenderer.BLADE_CLAMP_MODEL_LOCATION);
		register.accept(HalfShaftVisual.MODEL.modelLocation());
		register.accept(CreateBiotech.asResource("block/blast_chamber_display/panel"));
		register.accept(CreateBiotech.asResource("block/blast_chamber_display/dial"));
		register.accept(CreateBiotech.asResource("block/blast_chamber_display/creeper_face"));
		register.accept(BoneRatchetRenderer.COGWHEEL_MODEL_LOCATION);
		register.accept(ExperiencePumpRenderer.COG_MODEL_LOCATION);
		register.accept(BionicMechanismItemRenderer.HALF_OPEN_MODEL_LOCATION);
		register.accept(BionicMechanismItemRenderer.HALF_OPEN_LEFT_MODEL_LOCATION);
		register.accept(BionicMechanismItemRenderer.LOOK_RIGHT_MODEL_LOCATION);
		register.accept(BionicMechanismItemRenderer.LOOK_CENTER_MODEL_LOCATION);
		register.accept(BionicMechanismItemRenderer.LOOK_LEFT_MODEL_LOCATION);
		register.accept(SonicDogCannonItemRenderer.GEAR_MODEL_LOCATION);
		register.accept(SonicDogCannonItemRenderer.SCOPE_MODEL_LOCATION);
		register.accept(SonicDogCannonItemRenderer.LEFT_SCOPE_MODEL_LOCATION);
		register.accept(SonicDogCannonItemRenderer.FOLDED_SCOPE_MODEL_LOCATION);
		register.accept(SonicDogCannonItemRenderer.LEFT_FOLDED_SCOPE_MODEL_LOCATION);
		register.accept(SonicDogCannonItemRenderer.COLLAR_MODEL_LOCATION);
		register.accept(SonicDogCannonItemRenderer.WOLF_MODEL_LOCATION);
		register.accept(SonicDogCannonItemRenderer.WOLF_ANGRY_MODEL_LOCATION);
		register.accept(SonicDogCannonItemRenderer.SHRIEK_EYES_MODEL_LOCATION);
		register.accept(SonicDogCannonItemRenderer.PAW_LEFT_MODEL_LOCATION);
		register.accept(SonicDogCannonItemRenderer.PAW_RIGHT_MODEL_LOCATION);
		register.accept(SonicDogCannonItemRenderer.PAW_LEFT_ANGRY_MODEL_LOCATION);
		register.accept(SonicDogCannonItemRenderer.PAW_RIGHT_ANGRY_MODEL_LOCATION);
		register.accept(CreateBiotech.asResource("block/schrodingers_cat/redstone_torch_on"));
		register.accept(CreateBiotech.asResource("block/schrodingers_cat/redstone_torch_off"));
		register.accept(CreateBiotech.asResource("block/spider_assembly_table/body"));
		register.accept(CreateBiotech.asResource("block/spider_assembly_table/head"));
		register.accept(CreateBiotech.asResource("block/spider_assembly_table/abdomen"));
		register.accept(CreateBiotech.asResource("block/spider_assembly_table/leg"));
		register.accept(CreateBiotech.asResource("block/ghast_helm/block_open"));
		register.accept(CreateBiotech.asResource("block/ghast_helm/train/cover"));
		register.accept(CreateBiotech.asResource("block/ghast_helm/train/lever"));
		register.accept(CreateBiotech.asResource("block/bio_packager/hatch_open"));
		register.accept(CreateBiotech.asResource("block/bio_packager/hatch_closed"));
		register.accept(CreateBiotech.asResource("block/bio_packager/tray"));
		register.accept(CreateBiotech.asResource("block/shulker_packager/hatch_open"));
		register.accept(CreateBiotech.asResource("block/shulker_packager/hatch_closed"));
		register.accept(CreateBiotech.asResource("block/shulker_packager/tray"));
		AllayPortRenderer.CURTAIN_MODEL_LOCATIONS.forEach(register);
		register.accept(CreateBiotech.asResource("item/shulker_package"));
		register.accept(CreateBiotech.asResource("item/cardboard_box"));
		register.accept(CreateBiotech.asResource("item/small_cardboard_box"));
		register.accept(CreateBiotech.asResource("item/small_cardboard_box_captured"));
		register.accept(CardboardBoxPartials.SMALL_BOX_LOGISTICS_LOCATION);
		register.accept(CreateBiotech.asResource("item/large_cardboard_box"));
		register.accept(CreateBiotech.asResource("item/large_cardboard_box_captured"));
		register.accept(CardboardBoxPartials.LARGE_BOX_LOGISTICS_LOCATION);
		register.accept(CreateBiotech.asResource("item/allay_courier_package"));
		ButterCatPartials.allModels()
			.forEach(model -> register.accept(model.modelLocation()));
	}

	static void modifyBakedModels(ModelEvent.ModifyBakingResult event) {
		var models = event.getModels();
		TextureAtlasSprite wheelParticle = event.getTextureGetter().apply(new Material(
			InventoryMenu.BLOCK_ATLAS, ResourceLocation.withDefaultNamespace("block/oak_planks")));
		// Create's invisible wheel structure uses the air model, whose particle is missing.
		for (var state : AllBlocks.WATER_WHEEL_STRUCTURAL.get().getStateDefinition().getPossibleStates()) {
			ModelResourceLocation location = BlockModelShaper.stateToModelLocation(state);
			BakedModel model = models.get(location);
			if (model != null)
				models.put(location, new BakedModelWrapper<BakedModel>(model) {
					@Override
					public TextureAtlasSprite getParticleIcon(ModelData data) {
						return wheelParticle;
					}
				});
		}
		for (String path : java.util.List.of("surgical_kit", "creative_surgical_kit")) {
			ModelResourceLocation modelLocation = ModelResourceLocation.inventory(
				CreateBiotech.asResource(path));
			var model = models.get(modelLocation);
			if (model != null)
				models.put(modelLocation, new SurgicalKitItemModel(model));
		}
	}

	/**
	 * Create snapshots its custom block model registrations during the first model bake and does not invalidate that
	 * snapshot. This must run before the initial resource reload instead of from the parallel client setup event.
	 */
	static synchronized void registerCustomBlockModels() {
		if (customBlockModelsRegistered)
			return;

		var customBlockModels = CreateClient.MODEL_SWAPPER.getCustomBlockModels();
		customBlockModels.register(CreateBiotech.asResource("experience_pump"), PipeAttachmentModel::withAO);
		customBlockModels.register(Create.asResource("andesite_belt_funnel"), SlimeBeltFunnelModel::new);
		customBlockModels.register(Create.asResource("brass_belt_funnel"), SlimeBeltFunnelModel::new);
		customBlockModels.register(CreateBiotech.asResource("magma_belt"),
			com.simibubi.create.content.kinetics.belt.BeltModel::new);
		customBlockModels.register(CreateBiotech.asResource("power_belt"),
			com.simibubi.create.content.kinetics.belt.BeltModel::new);
		customBlockModels.register(CreateBiotech.asResource("asurine_casing"),
			model -> new CTModel(model, new EncasedCTBehaviour(CBSpriteShifts.ASURINE_CASING)));
		customBlockModels.register(CreateBiotech.asResource("asurine_scaffolding"),
			model -> new CTModel(model, new MetalScaffoldingCTBehaviour(CBSpriteShifts.ASURINE_SCAFFOLD,
				CBSpriteShifts.ASURINE_SCAFFOLD_INSIDE, CBSpriteShifts.ASURINE_CASING)));
		customBlockModels.register(CreateBiotech.asResource("biotech_casing"),
			model -> new CTModel(model, new EncasedCTBehaviour(CBSpriteShifts.BIOTECH_CASING)));
		customBlockModels.register(CreateBiotech.asResource("explosion_proof_casing"),
			model -> new CTModel(model, new CasingConnectedHorizontalCTBehaviour(
				CBSpriteShifts.EXPLOSION_PROOF_CASING_SIDE, CBSpriteShifts.EXPLOSION_PROOF_CASING)));
		customBlockModels.register(CreateBiotech.asResource("creeper_blast_chamber"),
			model -> new CTModel(model, new CasingConnectedHorizontalCTBehaviour(
				CBSpriteShifts.EXPLOSION_PROOF_CASING_SIDE, CBSpriteShifts.EXPLOSION_PROOF_CASING)));
		customBlockModels.register(CreateBiotech.asResource("explosion_proof_item_vault"),
			model -> new CTModel(model, new ExplosionProofItemVaultCTBehaviour()));
		customBlockModels.register(CreateBiotech.asResource("blast_proof_chain_drive"),
			model -> new CTModel(model, new EncasedCTBehaviour(CBSpriteShifts.EXPLOSION_PROOF_CASING_SIDE)));
		customBlockModels.register(CreateBiotech.asResource("blast_proof_framed_glass"),
			model -> new CTModel(model, new SimpleCTBehaviour(CBSpriteShifts.BLAST_PROOF_FRAMED_GLASS)));
		customBlockModels.register(CreateBiotech.asResource("asurine_table_cloth"), TableClothModel::new);
		customBlockModels.register(CreateBiotech.asResource("surgical_table"), SurgicalTableModel::new);
		customBlockModelsRegistered = true;
	}

	static void registerCardboardBoxModelProperties() {
		ItemProperties.register(CBItems.CARDBOARD_BOX.get(), CreateBiotech.asResource("captured"),
			(stack, level, entity, seed) -> CapturedEntityBoxHelper.hasCapturedEntity(stack) ? 1.0f : 0.0f);
		ItemProperties.register(CBItems.LARGE_CARDBOARD_BOX.get(), CreateBiotech.asResource("captured"),
			(stack, level, entity, seed) -> CapturedEntityBoxHelper.hasCapturedEntity(stack) ? 1.0f : 0.0f);
	}

	static void registerSurgicalKitModelProperties() {
		ItemProperties.register(CBItems.SURGICAL_KIT.get(), CreateBiotech.asResource("surgical_tool"),
			(stack, level, entity, seed) -> SurgicalKitItem.modelValue(stack));
		ItemProperties.register(CBItems.CREATIVE_SURGICAL_KIT.get(), CreateBiotech.asResource("surgical_tool"),
			(stack, level, entity, seed) -> SurgicalKitItem.modelValue(stack));
	}
}
