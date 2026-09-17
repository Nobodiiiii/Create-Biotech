package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.decoration.encasing.CasingConnectivity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.data.ModelData;

import org.joml.Vector3f;

/**
 * Renders an encased spider directly from its casing or item-vault connected-texture sheets.
 *
 * <p>The element geometry and per-face UV values below are direct transcriptions of the converted
 * andesite and vault bbmodels in {@code art/}. Using baked block quads lets Minecraft apply the
 * bbmodels' reversed UV bounds and 90-degree face rotations exactly while still allowing every
 * {@code create:casing} block to supply its own connected texture.</p>
 */
public final class SpiderAssemblyTableCasingModel {

	private static final ResourceLocation FACE_TEXTURE =
		CreateBiotech.asResource("block/spider_assembly_table_face");
	private static final float CASING_TEXTURE_SIZE = 128f;
	private static final float FACE_TEXTURE_SIZE = 32f;
	private static final float[] FULL_TEXTURE_UV = { 0, 0, 16, 16 };
	private static final float[] FALLBACK_FACE_UV = { 12, 8, 20, 16 };
	private static final Map<ResourceLocation, FaceRegion> FACE_REGIONS = Map.ofEntries(
		faceRegion("create", "andesite_casing", 0, 0),
		faceRegion("create", "brass_casing", 8, 0),
		faceRegion("create", "copper_casing", 16, 0),
		faceRegion("create", "item_vault", 24, 0),
		faceRegion("create", "shadow_steel_casing", 0, 8),
		faceRegion("create", "refined_radiance_casing", 8, 8),
		faceRegion("create", "railway_casing", 16, 8),
		faceRegion(CreateBiotech.MOD_ID, "asurine_casing", 0, 16),
		faceRegion(CreateBiotech.MOD_ID, "biotech_casing", 8, 16),
		faceRegion(CreateBiotech.MOD_ID, "explosion_proof_casing", 16, 16));
	private static final FaceBakery FACE_BAKERY = new FaceBakery();
	private static final Map<Block, BakedParts> CACHE = new IdentityHashMap<>();
	private static final List<CubeSpec> CUBES = createCubes();
	private static final List<CubeSpec> VAULT_CUBES = createVaultCubes();

	public void render(Block casing, ModelPart root, PoseStack poseStack, MultiBufferSource buffer,
		int packedLight, int packedOverlay) {
		BakedParts baked = getOrCreate(casing);
		VertexConsumer consumer = buffer.getBuffer(Sheets.cutoutBlockSheet());
		for (Map.Entry<String, List<BakedQuad>> entry : baked.byPart().entrySet()) {
			ModelPart part = root.getChild(entry.getKey());
			poseStack.pushPose();
			part.translateAndRotate(poseStack);
			for (BakedQuad quad : entry.getValue())
				consumer.putBulkData(poseStack.last(), quad, 1, 1, 1, 1, packedLight, packedOverlay);
			poseStack.popPose();
		}
	}

	public static synchronized void clearTextureCache() {
		CACHE.clear();
	}

	private static synchronized BakedParts getOrCreate(Block casing) {
		return CACHE.computeIfAbsent(casing, SpiderAssemblyTableCasingModel::bake);
	}

	private static BakedParts bake(Block casing) {
		Minecraft minecraft = Minecraft.getInstance();
		CasingConnectivity.Entry entry = CreateClient.CASING_CONNECTIVITY.get(casing.defaultBlockState());
		boolean itemVault = casing == AllBlocks.ITEM_VAULT.get();
		TextureAtlasSprite casingSprite;
		float casingTextureSize;
		if (entry != null) {
			casingSprite = entry.getCasing().getTarget();
			casingTextureSize = CASING_TEXTURE_SIZE;
		} else if (itemVault) {
			// The vault bbmodel selects its own source or connected target for every face.
			casingSprite = null;
			casingTextureSize = 0;
		} else {
			// A data pack can add a plain block to create:casing without registering a CT
			// sheet. It still encases successfully; its particle sprite is the safe visual
			// fallback and uses ordinary 16x16 UV space.
			casingSprite = minecraft.getBlockRenderer()
				.getBlockModel(casing.defaultBlockState())
				.getParticleIcon(ModelData.EMPTY);
			casingTextureSize = 16f;
		}
		TextureAtlasSprite faceSprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
			.apply(FACE_TEXTURE);
		FaceRegion faceRegion = FACE_REGIONS.get(BuiltInRegistries.BLOCK.getKey(casing));

		Map<String, List<BakedQuad>> parts = new LinkedHashMap<>();
		for (CubeSpec cube : itemVault ? VAULT_CUBES : CUBES) {
			List<BakedQuad> quads = parts.computeIfAbsent(cube.part(), ignored -> new ArrayList<>());
			for (FaceSpec face : cube.faces()) {
				TextureAtlasSprite sprite = itemVault ? vaultSprite(face.vaultTexture()) : casingSprite;
				float textureSize = itemVault ? face.vaultTexture().textureSize : casingTextureSize;
				float[] uv = entry == null && !itemVault ? FULL_TEXTURE_UV : face.uv();
				if (face.special()) {
					if (faceRegion != null) {
						sprite = faceSprite;
						textureSize = FACE_TEXTURE_SIZE;
						uv = faceRegion.uv();
					} else if (entry != null) {
						// Add-on casings without a dedicated face use the requested 8x8
						// region starting at (12, 8) in their connected texture.
						uv = FALLBACK_FACE_UV;
					}
				}
				quads.add(bakeFace(cube, face, sprite, textureSize, uv));
			}
		}
		parts.replaceAll((part, quads) -> List.copyOf(quads));
		return new BakedParts(Map.copyOf(parts));
	}

	private static TextureAtlasSprite vaultSprite(VaultTexture texture) {
		return switch (texture) {
			case FRONT_LARGE -> AllSpriteShifts.VAULT_FRONT.get(false).getTarget();
			case FRONT_SMALL -> AllSpriteShifts.VAULT_FRONT.get(true).getOriginal();
			case SIDE_LARGE -> AllSpriteShifts.VAULT_SIDE.get(false).getTarget();
		};
	}

	private static BakedQuad bakeFace(CubeSpec cube, FaceSpec face, TextureAtlasSprite sprite,
		float textureSize, float[] uv) {
		float scale = 16f / textureSize;
		// The surrounding entity transform flips model X and Y to Blockbench's editor
		// coordinates. That is a 180-degree turn around Z, so the face's UV corner order
		// must turn with it as well.
		BlockFaceUV blockUv = new BlockFaceUV(new float[] {
			uv[0] * scale, uv[1] * scale, uv[2] * scale, uv[3] * scale
		}, (face.rotation() + 180) % 360);
		BlockElementFace elementFace = new BlockElementFace(null, -1, "#spider", blockUv);
		return FACE_BAKERY.bakeQuad(cube.modelFrom(), cube.modelTo(), elementFace, sprite,
			face.direction().modelDirection, BlockModelRotation.X0_Y0, cube.rotation(), true);
	}

	private static List<CubeSpec> createCubes() {
		List<CubeSpec> cubes = new ArrayList<>();
		cubes.add(cube("head", 0, 9, -3, -4, 5, -11, 4, 13, -3,
			face(EditorFace.NORTH, 12, 0, 20, 8, 0, true),
			face(EditorFace.EAST, 16, 16, 24, 24, 90),
			face(EditorFace.SOUTH, 4, 4, 12, 12),
			face(EditorFace.WEST, 24, 32, 32, 40, 270),
			face(EditorFace.UP, 12, 12, 4, 4),
			face(EditorFace.DOWN, 12, 4, 4, 12)));

		cubes.add(cube("body0", 0, 9, 0, -3, 6, -3, 3, 12, 3,
			face(EditorFace.NORTH, 5, 5, 11, 11),
			face(EditorFace.EAST, 5, 5, 11, 11, 90),
			face(EditorFace.SOUTH, 5, 5, 11, 11),
			face(EditorFace.WEST, 5, 5, 11, 11, 90),
			face(EditorFace.UP, 11, 11, 5, 5),
			face(EditorFace.DOWN, 11, 5, 5, 11)));

		// The abdomen is split into four quarters in the bbmodel so every outer face can
		// retain its authored per-face UV orientation.
		cubes.add(cube("body1", 0, 9, 9, -5, 5, 3, 0, 9, 15,
			face(EditorFace.NORTH, 16, 16, 21, 20, 180),
			face(EditorFace.SOUTH, 0, 12, 5, 16),
			face(EditorFace.WEST, 4, 12, 16, 16),
			face(EditorFace.DOWN, 16, 0, 11, 12)));
		cubes.add(cube("body1", 0, 9, 9, -5, 9, 3, 0, 13, 15,
			face(EditorFace.NORTH, 6, 5, 10, 10, 90),
			face(EditorFace.SOUTH, 0, 0, 5, 4),
			face(EditorFace.WEST, 4, 4, 16, 8),
			face(EditorFace.UP, 12, 9, 0, 4, 90)));
		cubes.add(cube("body1", 0, 9, 9, 0, 5, 3, 5, 9, 15,
			face(EditorFace.NORTH, 16, 16, 20, 21, 270),
			face(EditorFace.EAST, 0, 12, 12, 16),
			face(EditorFace.SOUTH, 11, 12, 16, 16),
			face(EditorFace.DOWN, 5, 0, 0, 12)));
		cubes.add(cube("body1", 0, 9, 9, 0, 9, 3, 5, 13, 15,
			face(EditorFace.NORTH, 6, 5, 10, 10, 270),
			face(EditorFace.EAST, 0, 5, 12, 9),
			face(EditorFace.SOUTH, 11, 0, 16, 4),
			face(EditorFace.UP, 16, 9, 4, 4, 270)));

		// These three root-level elements form the thin cap above the spider's head.
		// They are attached to the non-animated head part so their absolute position
		// remains identical to the bbmodel while still sharing its model transform.
		cubes.add(cube("head", 0, 9, -3, -4.2f, 11, -11.2f, 4.2f, 13.1f, -6.2f,
			face(EditorFace.NORTH, 5, 0, 13, 2),
			face(EditorFace.EAST, 2, 0, 7, 2),
			face(EditorFace.WEST, 2, 2, 7, 0, 180)));
		cubes.add(rotatedCube("head", 0, 9, -3, -4, 12.5f, -12, 4, 13.5f, -10,
			0, 11, -4.5f, Direction.Axis.X, 22.5f,
			face(EditorFace.UP, 4, 0, 13, 2)));
		cubes.add(cube("head", 0, 9, -3, -4.2f, 13.1f, -11.2f, 4.2f, 13.1f, -9.2f,
			face(EditorFace.UP, 4, 14, 12, 16, 180)));

		addRightLeg(cubes, "right_hind_leg", 4, 9, 2, 3, 8, 1, 19, 10, 3);
		addLeftLeg(cubes, "left_hind_leg", -4, 9, 2, -19, 8, 1, -3, 10, 3);
		addRightLeg(cubes, "right_middle_hind_leg", 4, 9, 1, 3, 8, 0, 19, 10, 2);
		addLeftLeg(cubes, "left_middle_hind_leg", -4, 9, 1, -19, 8, 0, -3, 10, 2);
		addRightLeg(cubes, "right_middle_front_leg", 4, 9, 0, 3, 8, -1, 19, 10, 1);
		addLeftLeg(cubes, "left_middle_front_leg", -4, 9, 0, -19, 8, -1, -3, 10, 1);
		addRightLeg(cubes, "right_front_leg", 4, 9, -1, 3, 8, -2, 19, 10, 0);
		addLeftLeg(cubes, "left_front_leg", -4, 9, -1, -19, 8, -2, -3, 10, 0);
		return List.copyOf(cubes);
	}

	private static List<CubeSpec> createVaultCubes() {
		List<CubeSpec> cubes = new ArrayList<>();
		cubes.add(cube("head", 0, 9, -3, -4, 5, -11, 4, 13, -3,
			vaultFace(EditorFace.NORTH, 12, 0, 20, 8, 0, true, VaultTexture.FRONT_LARGE),
			vaultFace(EditorFace.EAST, 28, 12, 36, 20, 90, VaultTexture.FRONT_LARGE),
			vaultFace(EditorFace.SOUTH, 36, 12, 44, 20, VaultTexture.FRONT_LARGE),
			vaultFace(EditorFace.WEST, 28, 12, 36, 20, 270, VaultTexture.FRONT_LARGE),
			vaultFace(EditorFace.UP, 48, 21, 40, 13, 90, VaultTexture.SIDE_LARGE),
			vaultFace(EditorFace.DOWN, 44, 12, 36, 20, VaultTexture.FRONT_LARGE)));

		cubes.add(cube("body0", 0, 9, 0, -3, 6, -3, 3, 12, 3,
			vaultFace(EditorFace.NORTH, 29, 13, 35, 19, VaultTexture.FRONT_LARGE),
			vaultFace(EditorFace.EAST, 29, 13, 35, 19, 90, VaultTexture.FRONT_LARGE),
			vaultFace(EditorFace.SOUTH, 29, 13, 35, 19, VaultTexture.FRONT_LARGE),
			vaultFace(EditorFace.WEST, 29, 13, 35, 19, 90, VaultTexture.FRONT_LARGE),
			vaultFace(EditorFace.UP, 35, 19, 29, 13, VaultTexture.FRONT_LARGE),
			vaultFace(EditorFace.DOWN, 35, 13, 29, 19, VaultTexture.FRONT_LARGE)));

		cubes.add(cube("body1", 0, 9, 9, -5, 5, 3, 0, 9, 15,
			vaultFace(EditorFace.NORTH, 28, 13, 33, 17, VaultTexture.FRONT_LARGE),
			vaultFace(EditorFace.SOUTH, 0, 12, 5, 16, VaultTexture.FRONT_SMALL),
			vaultFace(EditorFace.WEST, 4, 12, 16, 16, VaultTexture.FRONT_SMALL),
			vaultFace(EditorFace.DOWN, 16, 0, 11, 12, VaultTexture.FRONT_SMALL)));
		cubes.add(cube("body1", 0, 9, 9, -5, 9, 3, 0, 13, 15,
			vaultFace(EditorFace.NORTH, 6, 5, 10, 10, 90, VaultTexture.FRONT_SMALL),
			vaultFace(EditorFace.SOUTH, 0, 0, 5, 4, VaultTexture.FRONT_SMALL),
			vaultFace(EditorFace.WEST, 4, 7, 16, 11, VaultTexture.FRONT_SMALL),
			vaultFace(EditorFace.UP, 12, 11, 0, 6, 90, VaultTexture.FRONT_SMALL)));
		cubes.add(cube("body1", 0, 9, 9, 0, 5, 3, 5, 9, 15,
			vaultFace(EditorFace.NORTH, 29, 12, 33, 17, 90, VaultTexture.FRONT_LARGE),
			vaultFace(EditorFace.EAST, 0, 12, 12, 16, VaultTexture.FRONT_SMALL),
			vaultFace(EditorFace.SOUTH, 11, 12, 16, 16, VaultTexture.FRONT_SMALL),
			vaultFace(EditorFace.DOWN, 5, 0, 0, 12, VaultTexture.FRONT_SMALL)));
		cubes.add(cube("body1", 0, 9, 9, 0, 9, 3, 5, 13, 15,
			vaultFace(EditorFace.NORTH, 6, 5, 10, 10, 270, VaultTexture.FRONT_SMALL),
			vaultFace(EditorFace.EAST, 0, 7, 12, 11, VaultTexture.FRONT_SMALL),
			vaultFace(EditorFace.SOUTH, 11, 0, 16, 4, VaultTexture.FRONT_SMALL),
			vaultFace(EditorFace.UP, 12, 6, 0, 11, 90, VaultTexture.FRONT_SMALL)));

		cubes.add(cube("head", 0, 9, -3, -4.2f, 11, -11.2f, 4.2f, 13.1f, -6.2f,
			vaultFace(EditorFace.NORTH, 23, 0, 31, 2, VaultTexture.FRONT_LARGE),
			vaultFace(EditorFace.EAST, 16, 0, 21, 2, VaultTexture.FRONT_LARGE),
			vaultFace(EditorFace.WEST, 16, 2, 21, 0, 180, VaultTexture.FRONT_LARGE)));
		cubes.add(rotatedCube("head", 0, 9, -3, -4, 12.5f, -12, 4, 13.5f, -10,
			0, 11, -4.5f, Direction.Axis.X, 22.5f,
			vaultFace(EditorFace.UP, 32, 46, 41, 48, VaultTexture.FRONT_LARGE)));
		cubes.add(cube("head", 0, 9, -3, -4.2f, 13.1f, -11.2f, 4.2f, 13.1f, -9.2f,
			vaultFace(EditorFace.UP, 23, 2, 31, 0, 180, VaultTexture.FRONT_LARGE)));

		addVaultRightLeg(cubes, "right_hind_leg", 4, 9, 2, 3, 8, 1, 19, 10, 3);
		addVaultLeftLeg(cubes, "left_hind_leg", -4, 9, 2, -19, 8, 1, -3, 10, 3, true, false);
		addVaultRightLeg(cubes, "right_middle_hind_leg", 4, 9, 1, 3, 8, 0, 19, 10, 2);
		addVaultLeftLeg(cubes, "left_middle_hind_leg", -4, 9, 1, -19, 8, 0, -3, 10, 2, false, false);
		addVaultRightLeg(cubes, "right_middle_front_leg", 4, 9, 0, 3, 8, -1, 19, 10, 1);
		addVaultLeftLeg(cubes, "left_middle_front_leg", -4, 9, 0, -19, 8, -1, -3, 10, 1, false, false);
		addVaultRightLeg(cubes, "right_front_leg", 4, 9, -1, 3, 8, -2, 19, 10, 0);
		addVaultLeftLeg(cubes, "left_front_leg", -4, 9, -1, -19, 8, -2, -3, 10, 0, false, true);
		return List.copyOf(cubes);
	}

	private static void addVaultRightLeg(List<CubeSpec> cubes, String part, float originX, float originY,
		float originZ, float fromX, float fromY, float fromZ, float toX, float toY, float toZ) {
		cubes.add(cube(part, originX, originY, originZ, fromX, fromY, fromZ, toX, toY, toZ,
			vaultFace(EditorFace.NORTH, 16, 7, 0, 9, VaultTexture.FRONT_SMALL),
			vaultFace(EditorFace.EAST, 33, 15, 31, 17, VaultTexture.FRONT_LARGE),
			vaultFace(EditorFace.SOUTH, 16, 7, 0, 9, VaultTexture.FRONT_SMALL),
			vaultFace(EditorFace.WEST, 33, 15, 31, 17, VaultTexture.FRONT_LARGE),
			vaultFace(EditorFace.UP, 16, 7, 0, 9, VaultTexture.FRONT_SMALL),
			vaultFace(EditorFace.DOWN, 16, 7, 0, 9, VaultTexture.FRONT_SMALL)));
	}

	private static void addVaultLeftLeg(List<CubeSpec> cubes, String part, float originX, float originY,
		float originZ, float fromX, float fromY, float fromZ, float toX, float toY, float toZ,
		boolean hindMost, boolean frontMost) {
		FaceSpec east = hindMost
			? vaultFace(EditorFace.EAST, 4, 15, 2, 17, VaultTexture.FRONT_LARGE)
			: vaultFace(EditorFace.EAST, 33, 15, 31, 17, VaultTexture.FRONT_LARGE);
		FaceSpec up = frontMost
			? vaultFace(EditorFace.UP, 16, 7, 0, 9, VaultTexture.FRONT_SMALL)
			: vaultFace(EditorFace.UP, 0, 9, 16, 7, VaultTexture.FRONT_SMALL);
		FaceSpec down = frontMost
			? vaultFace(EditorFace.DOWN, 16, 7, 0, 9, VaultTexture.FRONT_SMALL)
			: vaultFace(EditorFace.DOWN, 0, 7, 16, 9, VaultTexture.FRONT_SMALL);
		cubes.add(cube(part, originX, originY, originZ, fromX, fromY, fromZ, toX, toY, toZ,
			vaultFace(EditorFace.NORTH, 16, 7, 0, 9, VaultTexture.FRONT_SMALL),
			east,
			vaultFace(EditorFace.SOUTH, 16, 7, 0, 9, VaultTexture.FRONT_SMALL),
			vaultFace(EditorFace.WEST, 31, 15, 33, 17, VaultTexture.FRONT_LARGE),
			up,
			down));
	}

	private static void addRightLeg(List<CubeSpec> cubes, String part, float originX, float originY,
		float originZ, float fromX, float fromY, float fromZ, float toX, float toY, float toZ) {
		cubes.add(cube(part, originX, originY, originZ, fromX, fromY, fromZ, toX, toY, toZ,
			face(EditorFace.NORTH, 32, 2, 16, 4),
			face(EditorFace.EAST, 32, 2, 16, 4),
			face(EditorFace.SOUTH, 32, 2, 16, 4),
			face(EditorFace.WEST, 32, 2, 16, 4),
			face(EditorFace.UP, 32, 2, 16, 4),
			face(EditorFace.DOWN, 32, 2, 16, 4)));
	}

	private static void addLeftLeg(List<CubeSpec> cubes, String part, float originX, float originY,
		float originZ, float fromX, float fromY, float fromZ, float toX, float toY, float toZ) {
		cubes.add(cube(part, originX, originY, originZ, fromX, fromY, fromZ, toX, toY, toZ,
			face(EditorFace.NORTH, 32, 2, 16, 4),
			face(EditorFace.EAST, 4, 15, 2, 17),
			face(EditorFace.SOUTH, 32, 2, 16, 4),
			face(EditorFace.WEST, 4, 15, 2, 17),
			face(EditorFace.UP, 16, 4, 32, 2),
			face(EditorFace.DOWN, 16, 2, 32, 4)));
	}

	private static CubeSpec cube(String part, float originX, float originY, float originZ,
		float fromX, float fromY, float fromZ, float toX, float toY, float toZ, FaceSpec... faces) {
		return cube(part, originX, originY, originZ, fromX, fromY, fromZ, toX, toY, toZ, null, faces);
	}

	private static CubeSpec rotatedCube(String part, float originX, float originY, float originZ,
		float fromX, float fromY, float fromZ, float toX, float toY, float toZ,
		float rotationOriginX, float rotationOriginY, float rotationOriginZ,
		Direction.Axis rotationAxis, float rotationAngle, FaceSpec... faces) {
		float pivotX = -originX;
		float pivotY = 24 - originY;
		float pivotZ = originZ;
		Vector3f rotationOrigin = new Vector3f(
			(-rotationOriginX - pivotX) / 16f,
			(24 - rotationOriginY - pivotY) / 16f,
			(rotationOriginZ - pivotZ) / 16f);
		BlockElementRotation rotation =
			new BlockElementRotation(rotationOrigin, rotationAxis, rotationAngle, false);
		return cube(part, originX, originY, originZ, fromX, fromY, fromZ, toX, toY, toZ,
			rotation, faces);
	}

	private static CubeSpec cube(String part, float originX, float originY, float originZ,
		float fromX, float fromY, float fromZ, float toX, float toY, float toZ,
		BlockElementRotation rotation, FaceSpec... faces) {
		// Blockbench editor coordinates are (-model X, 24 - model Y, model Z).
		float pivotX = -originX;
		float pivotY = 24 - originY;
		float pivotZ = originZ;
		Vector3f modelFrom = new Vector3f(-toX - pivotX, 24 - toY - pivotY, fromZ - pivotZ);
		Vector3f modelTo = new Vector3f(-fromX - pivotX, 24 - fromY - pivotY, toZ - pivotZ);
		return new CubeSpec(part, modelFrom, modelTo, rotation, List.of(faces));
	}

	private static Map.Entry<ResourceLocation, FaceRegion> faceRegion(String namespace, String path,
		float u, float v) {
		return Map.entry(ResourceLocation.fromNamespaceAndPath(namespace, path),
			new FaceRegion(new float[] { u, v, u + 8, v + 8 }));
	}

	private static FaceSpec face(EditorFace direction, float u1, float v1, float u2, float v2) {
		return face(direction, u1, v1, u2, v2, 0, false);
	}

	private static FaceSpec face(EditorFace direction, float u1, float v1, float u2, float v2,
		int rotation) {
		return face(direction, u1, v1, u2, v2, rotation, false);
	}

	private static FaceSpec face(EditorFace direction, float u1, float v1, float u2, float v2,
		int rotation, boolean special) {
		return new FaceSpec(direction, new float[] { u1, v1, u2, v2 }, rotation, special, null);
	}

	private static FaceSpec vaultFace(EditorFace direction, float u1, float v1, float u2, float v2,
		VaultTexture texture) {
		return vaultFace(direction, u1, v1, u2, v2, 0, false, texture);
	}

	private static FaceSpec vaultFace(EditorFace direction, float u1, float v1, float u2, float v2,
		int rotation, VaultTexture texture) {
		return vaultFace(direction, u1, v1, u2, v2, rotation, false, texture);
	}

	private static FaceSpec vaultFace(EditorFace direction, float u1, float v1, float u2, float v2,
		int rotation, boolean special, VaultTexture texture) {
		return new FaceSpec(direction, new float[] { u1, v1, u2, v2 }, rotation, special, texture);
	}

	private enum EditorFace {
		NORTH(Direction.NORTH),
		EAST(Direction.WEST),
		SOUTH(Direction.SOUTH),
		WEST(Direction.EAST),
		UP(Direction.DOWN),
		DOWN(Direction.UP);

		private final Direction modelDirection;

		EditorFace(Direction modelDirection) {
			this.modelDirection = modelDirection;
		}
	}

	private enum VaultTexture {
		FRONT_LARGE(64),
		FRONT_SMALL(16),
		SIDE_LARGE(64);

		private final float textureSize;

		VaultTexture(float textureSize) {
			this.textureSize = textureSize;
		}
	}

	private record FaceSpec(EditorFace direction, float[] uv, int rotation, boolean special,
		VaultTexture vaultTexture) {}

	private record CubeSpec(String part, Vector3f modelFrom, Vector3f modelTo,
		BlockElementRotation rotation, List<FaceSpec> faces) {}

	private record FaceRegion(float[] uv) {}

	private record BakedParts(Map<String, List<BakedQuad>> byPart) {}
}
