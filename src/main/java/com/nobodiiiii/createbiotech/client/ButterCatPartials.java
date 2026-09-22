package com.nobodiiiii.createbiotech.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nobodiiiii.createbiotech.CreateBiotech;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.animal.CatVariant;

public final class ButterCatPartials {
	public static final PartialModel EMPTY = create("butter_cat_engine/butter/empty");
	public static final PartialModel BREAD = create("butter_cat_engine/butter/bread");
	public static final PartialModel ROPE = create("butter_cat_engine/butter/rope");
	public static final PartialModel BUTTER = create("butter_cat_engine/butter/butter");
	public static final PartialModel SUPER_BUTTER = create("butter_cat_engine/butter/super_butter");

	public static final PartialModel CAT_ALL_BLACK = create("butter_cat_engine/cat/all_black");
	public static final PartialModel CAT_BLACK = create("butter_cat_engine/cat/black");
	public static final PartialModel CAT_BRITISH_SHORTHAIR = create("butter_cat_engine/cat/british_shorthair");
	public static final PartialModel CAT_CALICO = create("butter_cat_engine/cat/calico");
	public static final PartialModel CAT_JELLIE = create("butter_cat_engine/cat/jellie");
	public static final PartialModel CAT_PERSIAN = create("butter_cat_engine/cat/persian");
	public static final PartialModel CAT_RAGDOLL = create("butter_cat_engine/cat/ragdoll");
	public static final PartialModel CAT_RED = create("butter_cat_engine/cat/red");
	public static final PartialModel CAT_SIAMESE = create("butter_cat_engine/cat/siamese");
	public static final PartialModel CAT_TABBY = create("butter_cat_engine/cat/tabby");
	public static final PartialModel CAT_WHITE = create("butter_cat_engine/cat/white");

	private static final Map<ResourceKey<CatVariant>, PartialModel> CAT_VARIANT_MODELS = new HashMap<>();
	private static final List<PartialModel> ALL_MODELS = List.of(
		EMPTY, BREAD, ROPE, BUTTER, SUPER_BUTTER,
		CAT_ALL_BLACK, CAT_BLACK, CAT_BRITISH_SHORTHAIR, CAT_CALICO, CAT_JELLIE,
		CAT_PERSIAN, CAT_RAGDOLL, CAT_RED, CAT_SIAMESE, CAT_TABBY, CAT_WHITE);

	static {
		CAT_VARIANT_MODELS.put(CatVariant.TABBY, CAT_TABBY);
		CAT_VARIANT_MODELS.put(CatVariant.BLACK, CAT_BLACK);
		CAT_VARIANT_MODELS.put(CatVariant.RED, CAT_RED);
		CAT_VARIANT_MODELS.put(CatVariant.SIAMESE, CAT_SIAMESE);
		CAT_VARIANT_MODELS.put(CatVariant.BRITISH_SHORTHAIR, CAT_BRITISH_SHORTHAIR);
		CAT_VARIANT_MODELS.put(CatVariant.CALICO, CAT_CALICO);
		CAT_VARIANT_MODELS.put(CatVariant.PERSIAN, CAT_PERSIAN);
		CAT_VARIANT_MODELS.put(CatVariant.RAGDOLL, CAT_RAGDOLL);
		CAT_VARIANT_MODELS.put(CatVariant.WHITE, CAT_WHITE);
		CAT_VARIANT_MODELS.put(CatVariant.JELLIE, CAT_JELLIE);
		CAT_VARIANT_MODELS.put(CatVariant.ALL_BLACK, CAT_ALL_BLACK);
	}

	private ButterCatPartials() {}

	public static PartialModel getCatModel(ResourceKey<CatVariant> catVariant) {
		return CAT_VARIANT_MODELS.getOrDefault(catVariant, CAT_TABBY);
	}

	public static PartialModel getButterModel(boolean infinite, int butterLevel) {
		if (infinite)
			return SUPER_BUTTER;
		return butterLevel == 0 ? EMPTY : BUTTER;
	}

	public static PartialModel getBreadModel(boolean hasBread) {
		return hasBread ? BREAD : EMPTY;
	}

	public static PartialModel getRopeModel(boolean hasBread) {
		return hasBread ? ROPE : EMPTY;
	}

	public static List<PartialModel> allModels() {
		return ALL_MODELS;
	}

	private static PartialModel create(String path) {
		return PartialModel.of(CreateBiotech.asResource("block/" + path));
	}
}
