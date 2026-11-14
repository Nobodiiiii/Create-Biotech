package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.entity.PlayerMimicEntity;
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomySnapshot;
import com.nobodiiiii.createbiotech.entity.trait.BionicModelNode;
import com.nobodiiiii.createbiotech.entity.trait.BionicModelAlias;
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomyRole;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitRegistry;
import com.nobodiiiii.createbiotech.mixin.client.ModelPartAccessor;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.DolphinCarryingItemLayer;
import net.minecraft.client.renderer.entity.layers.FoxHeldItemLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.PandaHoldsItemLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.phys.Vec3;

/** Internal SurgicalRenderCapture implementation; public entry points remain on SurgicalCapturedRenderPlan. */
final class SurgicalRenderCapture {
	private SurgicalRenderCapture() {}

	static final AtomicInteger ACTIVE_MODEL_CUBE_CAPTURES = new AtomicInteger();
	static final ThreadLocal<ModelCubeCaptureScope> CURRENT_MODEL_CUBE_CAPTURE = new ThreadLocal<>();

	/**
	 * @param topology whether the caller will read {@code modelCorners} and {@code faceGrids} off the
	 *   resulting components. Only {@link #snapshot} exposes them, so the live and death paths pass
	 *   {@code false} and skip topology recovery. Saddled models still identify their tack so it
	 *   retains its source appearance even when it shares the body's texture.
	 */
	static SurgicalCapturedRenderPlan capture(EntityRenderer<LivingEntity> renderer, LivingEntity preview,
		float yaw, float partialTick, boolean topology) {
		return SurgicalMeshRecovery.build(captureInput(renderer, preview, yaw, partialTick, topology));
	}

	static SurgicalCapturedRenderPlan.CapturedInput captureInput(EntityRenderer<LivingEntity> renderer, LivingEntity preview,
		float yaw, float partialTick, boolean topology) {
		RecordingBuffer recording = new RecordingBuffer();
		PoseStack neutralPose = new PoseStack();
		boolean bindPlayerSkinLayers = preview instanceof PlayerMimicEntity;
		List<SurgicalCapturedRenderPlan.ObservedCube> observedCubes = topology ? new ArrayList<>() : List.of();
		Set<SurgicalCapturedRenderPlan.GeometryKey> saddleCubes = new HashSet<>();
		boolean observeSaddle = preview instanceof Saddleable saddleable && saddleable.isSaddled()
			|| preview instanceof net.minecraft.world.entity.animal.horse.AbstractChestedHorse horse && horse.hasChest();
		// Ordinary live captures keep the fast rejection in observeModelCube. Only saddled models
		// need the small extra observation for equipment embedded in their main model.
		ResourceLocation entity = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(preview.getType());
		ModelNames modelNames = topology || observeSaddle ? namedModelCubes(renderer, entity) : new ModelNames(Map.of(), Set.of(), Map.of());
		ModelCubeCaptureScope captureScope = topology || observeSaddle
			? openModelCubeCapture(observedCubes, topology ? modelNames.heads : Set.of(), modelNames.nodes,
				modelNames.names, topology, saddleCubes) : null;
		try (SurgicalModelPartNames names = topology ? SurgicalModelPartNames.open(renderer, entity) : null) {
			renderer.render(preview, yaw, partialTick, neutralPose, recording, SurgicalCapturedRenderPlan.CAPTURE_LIGHT);
		} finally {
			try {
				recording.finish();
			} finally {
				if (captureScope != null)
					captureScope.close();
			}
		}
		return new SurgicalCapturedRenderPlan.CapturedInput(List.copyOf(recording.streams), List.copyOf(observedCubes), topology,
			bindPlayerSkinLayers, Set.copyOf(saddleCubes));
	}

	/** Held-item layers retain their native geometry, material and glint instead of becoming tissue. */
	static MultiBufferSource bufferForLayer(RenderLayer<?, ?> layer, MultiBufferSource buffer) {
		if (!(buffer instanceof RecordingBuffer recording))
			return buffer;
		// Include the non-humanoid held-item layers. Player and witch layers inherit these types.
		return layer instanceof ItemInHandLayer<?, ?> || layer instanceof CrossedArmsItemLayer<?, ?>
			|| layer instanceof FoxHeldItemLayer || layer instanceof PandaHoldsItemLayer
			|| layer instanceof DolphinCarryingItemLayer ? recording.items : buffer;
	}

	/** Records unscaled ModelPart pixel bounds while the renderer emits the matching transformed vertices. */
	static void observeModelCube(ModelPart.Cube cube, PoseStack.Pose pose) {
		if (ACTIVE_MODEL_CUBE_CAPTURES.get() == 0)
			return;
		ModelCubeCaptureScope capture = CURRENT_MODEL_CUBE_CAPTURE.get();
		if (capture == null || capture.recordingCube)
			return;
		Set<String> partNames = capture.namedCubes.getOrDefault(cube, Set.of());
		boolean saddle = partNames.stream().anyMatch(SurgicalRenderCapture::isSaddlePart);
		if (!capture.topology && !saddle)
			return;
		SurgicalCapturedRenderPlan.RecoveredCandidate actual = capture.recordCube(cube, pose);
		if (saddle && actual != null)
			capture.saddleCubes.add(SurgicalCapturedRenderPlan.GeometryKey.of(actual.cuboid().corners()));
		if (!capture.topology)
			return;
		List<Vector3f> transformed = new ArrayList<>(8);
		List<Vec3> model = new ArrayList<>(8);
		Matrix4f matrix = pose.pose();
		if (actual != null && Math.abs(matrix.determinant()) > 1.0e-12f) {
			// Cube.min/max exclude CubeDeformation. Match the emitted cuboid, including the
			// inflated horse/donkey body, so its model-part label survives topology recovery.
			Matrix4f inverse = new Matrix4f(matrix).invert();
			for (Vector3f corner : actual.cuboid().corners()) {
				transformed.add(new Vector3f(corner));
				Vector3f local = inverse.transformPosition(new Vector3f(corner)).mul(16.0f);
				model.add(new Vec3(local.x, local.y, local.z));
			}
		} else {
			for (int z = 0; z < 2; z++) {
				for (int y = 0; y < 2; y++) {
					for (int x = 0; x < 2; x++) {
						Vec3 source = new Vec3(x == 0 ? cube.minX : cube.maxX,
							y == 0 ? cube.minY : cube.maxY, z == 0 ? cube.minZ : cube.maxZ);
						model.add(source);
						transformed.add(matrix.transformPosition(new Vector3f((float) (source.x / 16.0d),
							(float) (source.y / 16.0d), (float) (source.z / 16.0d))));
					}
				}
			}
		}
		boolean head = capture.headCubes.contains(cube);
		capture.observedCubes.add(new SurgicalCapturedRenderPlan.ObservedCube(transformed, model, head, partNames, capture.nodes.getOrDefault(cube, Set.of())));
	}

	static boolean isSaddlePart(String name) {
		return name.equals("saddle") || name.startsWith("saddle_") || name.endsWith("_saddle")
			|| name.contains("_saddle_") || name.equals("bridle") || name.equals("reins")
			|| name.equals("left_chest") || name.equals("right_chest") || name.equals("leftChest") || name.equals("rightChest");
	}

	static ModelCubeCaptureScope openModelCubeCapture(List<SurgicalCapturedRenderPlan.ObservedCube> observedCubes,
		Set<ModelPart.Cube> headCubes, Map<ModelPart.Cube, Set<BionicModelNode>> nodes,
		Map<ModelPart.Cube, Set<String>> namedCubes, boolean topology, Set<SurgicalCapturedRenderPlan.GeometryKey> saddleCubes) {
		ModelCubeCaptureScope scope = new ModelCubeCaptureScope(Thread.currentThread(),
			CURRENT_MODEL_CUBE_CAPTURE.get(), observedCubes, headCubes, nodes, namedCubes, topology, saddleCubes);
		CURRENT_MODEL_CUBE_CAPTURE.set(scope);
		ACTIVE_MODEL_CUBE_CAPTURES.incrementAndGet();
		return scope;
	}

	static final class ModelCubeCaptureScope implements AutoCloseable {
		private final Thread owner;
		private final ModelCubeCaptureScope parent;
		private final List<SurgicalCapturedRenderPlan.ObservedCube> observedCubes;
		private final Set<ModelPart.Cube> headCubes;
		private final Map<ModelPart.Cube, Set<String>> namedCubes;
		private final Map<ModelPart.Cube, Set<BionicModelNode>> nodes;
		private final boolean topology;
		private final Set<SurgicalCapturedRenderPlan.GeometryKey> saddleCubes;
		private boolean recordingCube;
		private boolean closed;

		private ModelCubeCaptureScope(Thread owner, ModelCubeCaptureScope parent,
			List<SurgicalCapturedRenderPlan.ObservedCube> observedCubes, Set<ModelPart.Cube> headCubes, Map<ModelPart.Cube, Set<BionicModelNode>> nodes,
			Map<ModelPart.Cube, Set<String>> namedCubes, boolean topology, Set<SurgicalCapturedRenderPlan.GeometryKey> saddleCubes) {
			this.owner = owner;
			this.parent = parent;
			this.observedCubes = observedCubes;
			this.headCubes = headCubes;
			this.namedCubes = namedCubes;
			this.nodes = nodes;
			this.topology = topology;
			this.saddleCubes = saddleCubes;
		}

		@Nullable
		private SurgicalCapturedRenderPlan.RecoveredCandidate recordCube(ModelPart.Cube cube, PoseStack.Pose pose) {
			List<CapturedVertex> vertices = new ArrayList<>();
			RecordingConsumer consumer = new RecordingConsumer(vertices);
			recordingCube = true;
			try {
				cube.compile(pose, consumer, SurgicalCapturedRenderPlan.CAPTURE_LIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
				consumer.finish();
			} finally {
				recordingCube = false;
			}
			return SurgicalMeshRecovery.recoverCandidate(vertices, 0);
		}

		@Override
		public void close() {
			if (closed)
				throw new IllegalStateException("Model-cube capture scope was already closed");
			if (Thread.currentThread() != owner)
				throw new IllegalStateException("Model-cube capture scope closed on a different thread");
			if (CURRENT_MODEL_CUBE_CAPTURE.get() != this)
				throw new IllegalStateException("Model-cube capture scopes must close in LIFO order");

			closed = true;
			if (parent == null)
				CURRENT_MODEL_CUBE_CAPTURE.remove();
			else
				CURRENT_MODEL_CUBE_CAPTURE.set(parent);
			if (ACTIVE_MODEL_CUBE_CAPTURES.decrementAndGet() < 0) {
				ACTIVE_MODEL_CUBE_CAPTURES.set(0);
				throw new IllegalStateException("Model-cube capture scope count underflow");
			}
		}
	}

	record ModelNames(Map<ModelPart.Cube, Set<String>> names, Set<ModelPart.Cube> heads, Map<ModelPart.Cube, Set<BionicModelNode>> nodes) {}

	/** Capture the model's own labels; trait JSON on the server assigns their meaning. */
	static ModelNames namedModelCubes(
		EntityRenderer<LivingEntity> renderer, ResourceLocation entity) {
		if (!(renderer instanceof LivingEntityRenderer<?, ?> livingRenderer))
			return new ModelNames(Map.of(), Set.of(), Map.of());
		Map<ModelPart.Cube, Set<String>> result = new IdentityHashMap<>();
		Set<ModelPart.Cube> heads = Collections.newSetFromMap(new IdentityHashMap<>());
		Map<ModelPart.Cube, Set<BionicModelNode>> nodes = new IdentityHashMap<>();
		collectModelPartNames(livingRenderer.getModel(), result, heads, nodes, entity);
		collectLayerModelNames(livingRenderer, result, heads, nodes, entity);
		return new ModelNames(result, heads, nodes);
	}

	/** Independent layer models have their own head/body/limb parts, not just a material. */
	static void collectLayerModelNames(Object owner, Map<ModelPart.Cube, Set<String>> result,
		Set<ModelPart.Cube> heads, Map<ModelPart.Cube, Set<BionicModelNode>> nodes, ResourceLocation entity) {
		for (Class<?> type = owner.getClass(); type != null && type != Object.class; type = type.getSuperclass())
			for (Field field : type.getDeclaredFields()) {
				if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
					|| !(EntityModel.class.isAssignableFrom(field.getType())
						|| Iterable.class.isAssignableFrom(field.getType())) || !field.trySetAccessible())
					continue;
				try {
					Object value = field.get(owner);
					if (value instanceof EntityModel<?> model)
						collectModelPartNames(model, result, heads, nodes, entity);
					else if (value instanceof Iterable<?> layers)
						for (Object layer : layers)
							if (layer instanceof RenderLayer<?, ?>)
								collectLayerModelNames(layer, result, heads, nodes, entity);
				} catch (IllegalAccessException ignored) {
					// Inaccessible third-party layer models retain their material labels only.
				}
			}
	}

	static void collectModelPartNames(EntityModel<?> model, Map<ModelPart.Cube, Set<String>> result,
		Set<ModelPart.Cube> heads, Map<ModelPart.Cube, Set<BionicModelNode>> nodes, ResourceLocation entity) {
		if (model instanceof HierarchicalModel<?> hierarchical)
			collectPartNames(hierarchical.root(), "root", result, heads, nodes, entity, null);
		for (Class<?> type = model.getClass(); type != null && type != Object.class;
			type = type.getSuperclass())
			for (Field field : type.getDeclaredFields()) {
				if (!ModelPart.class.isAssignableFrom(field.getType()) || !field.trySetAccessible())
					continue;
				try {
					if (field.get(model) instanceof ModelPart part) {
						collectPartNames(part, field.getName(), result, heads, nodes, entity, null);
					}
				} catch (IllegalAccessException ignored) {
					// Other model libraries can keep their fields inaccessible.
				}
			}
	}

	static void collectPartNames(ModelPart part, String name,
		Map<ModelPart.Cube, Set<String>> result, Set<ModelPart.Cube> heads, Map<ModelPart.Cube, Set<BionicModelNode>> nodes, ResourceLocation entity, BionicModelNode parent) {
		BionicModelNode node = new BionicModelNode(BionicAnatomySnapshot.validPartName(name) ? Set.of(name) : Set.of(), parent);
		boolean head = BionicTraitRegistry.clientMatchesPart(BionicAnatomyRole.HEAD, entity, node.names(), Set.of(node));
		ModelPartAccessor accessor = (ModelPartAccessor) (Object) part;
		if (head) heads.addAll(accessor.createBiotech$getCubes());
		for (ModelPart.Cube cube : accessor.createBiotech$getCubes()) {
			Set<BionicModelNode> known = nodes.computeIfAbsent(cube, ignored -> new HashSet<>());
			if (known.size() < 16) known.add(node);
		}
		if (BionicAnatomySnapshot.validPartName(name))
			for (ModelPart.Cube cube : accessor.createBiotech$getCubes()) {
				Set<String> names = result.computeIfAbsent(cube, ignored -> new HashSet<>());
				if (names.size() < 16)
					names.add(name);
			}
		for (Map.Entry<String, ModelPart> child : accessor.createBiotech$getChildren().entrySet())
			collectPartNames(child.getValue(), child.getKey(), result, heads, nodes, entity, node);
		List<BionicModelAlias> aliases = BionicTraitRegistry.clientModelAliases(entity);
		for (int index = 0; index < accessor.createBiotech$getCubes().size(); index++) {
			ModelPart.Cube cube = accessor.createBiotech$getCubes().get(index);
			Set<String> names = result.computeIfAbsent(cube, ignored -> new HashSet<>());
			for (BionicModelAlias alias : aliases)
				if (alias.matches(node.names(), index))
					for (String label : alias.names()) if (names.size() < 16) names.add(label);
			if (BionicTraitRegistry.clientMatchesPart(BionicAnatomyRole.HEAD, entity, names, nodes.getOrDefault(cube, Set.of())))
				heads.add(cube);
		}
	}

	static final class RecordingBuffer implements MultiBufferSource {
		private final List<CaptureStream> streams = new ArrayList<>();
		private final MultiBufferSource items = renderType -> record(renderType, true);

		@Override
		public VertexConsumer getBuffer(RenderType renderType) {
			return record(renderType, false);
		}

		private VertexConsumer record(RenderType renderType, boolean preserveSource) {
			CaptureStream stream = new CaptureStream(streams.size(), renderType, preserveSource);
			streams.add(stream);
			return stream.consumer;
		}

		private void finish() {
			for (CaptureStream stream : streams)
				stream.consumer.finish();
		}
	}

	static final class CaptureStream {
		final int id;
		final RenderType renderType;
		final boolean preserveSource;
		final List<CapturedVertex> vertices = new ArrayList<>();
		private final RecordingConsumer consumer = new RecordingConsumer(vertices);

		private CaptureStream(int id, RenderType renderType, boolean preserveSource) {
			this.id = id;
			this.renderType = renderType;
			this.preserveSource = preserveSource;
		}
	}

	static final class RecordingConsumer implements VertexConsumer {
		private final List<CapturedVertex> target;
		@Nullable
		private MutableVertex current;

		private RecordingConsumer(List<CapturedVertex> target) {
			this.target = target;
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			finish();
			current = new MutableVertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			if (current != null) {
				current.red = red;
				current.green = green;
				current.blue = blue;
				current.alpha = alpha;
			}
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			if (current != null) {
				current.u = u;
				current.v = v;
			}
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			if (current != null) {
				current.overlayU = u;
				current.overlayV = v;
			}
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			if (current != null) {
				current.lightU = u;
				current.lightV = v;
			}
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			if (current != null) {
				current.normalX = x;
				current.normalY = y;
				current.normalZ = z;
			}
			return this;
		}

		private void finish() {
			if (current == null)
				return;
			target.add(current.freeze());
			current = null;
		}
	}

	static final class MutableVertex {
		private final float x;
		private final float y;
		private final float z;
		private int red = 255;
		private int green = 255;
		private int blue = 255;
		private int alpha = 255;
		private float u;
		private float v;
		private int overlayU;
		private int overlayV;
		private int lightU;
		private int lightV;
		private float normalX;
		private float normalY = 1.0f;
		private float normalZ;
		private final SurgicalModelPartNames.Source source;

		private MutableVertex(float x, float y, float z) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.source = SurgicalModelPartNames.current();
		}

		private CapturedVertex freeze() {
			return new CapturedVertex(x, y, z, red, green, blue, alpha, u, v,
				overlayU, overlayV, lightU, lightV, normalX, normalY, normalZ, source);
		}
	}

	record CapturedVertex(float x, float y, float z, int red, int green, int blue, int alpha,
		float u, float v, int overlayU, int overlayV, int lightU, int lightV,
		float normalX, float normalY, float normalZ, SurgicalModelPartNames.Source source) {}
}
