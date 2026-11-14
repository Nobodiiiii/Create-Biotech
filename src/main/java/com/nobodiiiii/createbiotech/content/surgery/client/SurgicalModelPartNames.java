package com.nobodiiiii.createbiotech.content.surgery.client;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomySnapshot;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitRegistry;
import com.nobodiiiii.createbiotech.entity.trait.BionicModelNode;
import com.nobodiiiii.createbiotech.entity.trait.BionicModelAlias;
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomyRole;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Library-neutral provenance for collection-backed model parts. Field names and map keys identify
 * objects; observing their geometry collections identifies the vertices they actually emit. No
 * model transforms, cube coordinates, mod IDs or third-party classes are interpreted here.
 */
final class SurgicalModelPartNames implements AutoCloseable {

	static final Source UNKNOWN = new Source(Set.of(), false, Set.of());
	private static final ThreadLocal<SurgicalModelPartNames> CURRENT = new ThreadLocal<>();
	private static final int MAX_OBJECTS = 4096;
	private static final Set<String> NAME_FIELDS = Set.of("name", "boxname", "bonename", "partname");
	private static final ClassValue<Shape> SHAPES = new ClassValue<>() {
		@Override protected Shape computeValue(Class<?> type) {
			List<Field> fields = new ArrayList<>();
			boolean part = false;
			for (Class<?> parent = type; parent != null && parent != Object.class; parent = parent.getSuperclass()) {
				for (Field field : parent.getDeclaredFields())
					if (!Modifier.isStatic(field.getModifiers()) && !field.getType().isPrimitive()
						&& field.trySetAccessible()) fields.add(field);
				for (Method method : parent.getDeclaredMethods()) {
					if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class) continue;
					List<Class<?>> parameters = List.of(method.getParameterTypes());
					part |= parameters.contains(PoseStack.class) && parameters.contains(VertexConsumer.class);
				}
			}
			return new Shape(List.copyOf(fields), part && !ModelPart.class.isAssignableFrom(type));
		}
	};

	private final SurgicalModelPartNames previous;
	private final Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Map<Object, Part> parts = new IdentityHashMap<>();
	private final Map<Object, Map<Object, Source>> geometrySources = new IdentityHashMap<>();
	private final List<Replacement> replacements = new ArrayList<>();
	private Source current = UNKNOWN;
	private boolean closed;

	private SurgicalModelPartNames() {
		previous = CURRENT.get();
	}

	static SurgicalModelPartNames open(Object renderer, ResourceLocation entity) {
		SurgicalModelPartNames scope = new SurgicalModelPartNames();
		try {
			scope.discover(renderer);
			Map<Object, Set<BionicModelNode>> paths = new IdentityHashMap<>();
			Set<Object> children = Collections.newSetFromMap(new IdentityHashMap<>());
			scope.parts.values().forEach(part -> children.addAll(part.children));
			for (Object node : scope.parts.keySet()) if (!children.contains(node))
				scope.collectPaths(node, null, paths, Collections.newSetFromMap(new IdentityHashMap<>()));
			for (var entry : scope.parts.entrySet()) {
				Part part = entry.getValue();
				Set<BionicModelNode> nodes = paths.getOrDefault(entry.getKey(), Set.of(new BionicModelNode(part.names, null)));
				part.source = new Source(Set.copyOf(part.names),
					BionicTraitRegistry.clientMatchesPart(BionicAnatomyRole.HEAD, entity, part.names, nodes), Set.copyOf(nodes));
				scope.collectGeometryAliases(entry.getKey(), part.source, entity);
				scope.instrument(entry.getKey());
			}
			CURRENT.set(scope);
			return scope;
		} catch (RuntimeException | Error exception) {
			try { scope.close(); }
			catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
			throw exception;
		}
	}

	static Source current() {
		SurgicalModelPartNames scope = CURRENT.get();
		return scope == null ? UNKNOWN : scope.current;
	}

	private void discover(Object owner) {
		if (owner == null || visited.size() >= MAX_OBJECTS || !visited.add(owner)) return;
		boolean model = owner instanceof EntityModel<?>;
		boolean node = isPart(owner);
		if (node) {
			Part part = parts.computeIfAbsent(owner, ignored -> new Part());
			for (Field field : SHAPES.get(owner.getClass()).fields) {
				String name = field.getName().toLowerCase(java.util.Locale.ROOT);
				if (NAME_FIELDS.contains(name)
					&& read(field, owner) instanceof String label) part.addName(label);
			}
		}
		List<Field> fields = SHAPES.get(owner.getClass()).fields;
		// Read all direct model aliases before following parent/child links.
		if (model)
			for (Field field : fields) {
				Object value = read(field, owner);
				if (isPart(value)) parts.computeIfAbsent(value, ignored -> new Part()).addName(field.getName());
			}
		for (Field field : fields) {
			Object value = read(field, owner);
			if (isPart(value)) {
				if (!visited.contains(value)) {
					parts.computeIfAbsent(value, ignored -> new Part()).addName(field.getName());
					discover(value);
				}
			} else if (value instanceof EntityModel<?> || value instanceof RenderLayer<?, ?>) {
				discover(value);
			} else if (value instanceof Map<?, ?> map) {
				for (var entry : map.entrySet()) discoverElement(owner, entry.getValue(), entry.getKey());
			} else if (value instanceof Iterable<?> values) {
				for (Object element : values) discoverElement(owner, element, null);
			} else if (value != null && value.getClass().isArray() && !value.getClass().componentType().isPrimitive()) {
				for (int i = 0; i < Array.getLength(value); i++) discoverElement(owner, Array.get(value, i), null);
			}
		}
	}

	private void discoverElement(Object owner, Object value, Object key) {
		if (isPart(value)) {
			Part part = parts.computeIfAbsent(value, ignored -> new Part());
			if (key instanceof String label) part.addName(label);
			child(owner, value);
			discover(value);
		} else if (value instanceof EntityModel<?> || value instanceof RenderLayer<?, ?>) discover(value);
	}

	private void child(Object owner, Object value) {
		Part parent = parts.get(owner);
		// Collection membership supplies directed child links. Direct node fields may instead
		// be parent back-references, so they are discovered without inferring ancestry.
		if (parent != null) parent.children.add(value);
	}

	private void collectPaths(Object node, BionicModelNode parent, Map<Object, Set<BionicModelNode>> paths, Set<Object> branch) {
		if (!branch.add(node)) return;
		Part part = parts.get(node);
		if (part != null) {
			BionicModelNode path = new BionicModelNode(part.names, parent);
			Set<BionicModelNode> known = paths.computeIfAbsent(node, ignored -> new LinkedHashSet<>());
			if (known.size() < 16 && known.add(path))
				for (Object child : part.children) collectPaths(child, path, paths, branch);
		}
		branch.remove(node);
	}

	private static boolean isPart(Object value) {
		return value != null && !(value instanceof EntityModel<?>) && !(value instanceof ModelPart)
			&& !(value instanceof Iterable<?>) && !(value instanceof Map<?, ?>)
			&& !value.getClass().isArray() && SHAPES.get(value.getClass()).part;
	}

	/** Geometry collection indices are local to their owning node, independent of render order. */
	private void collectGeometryAliases(Object owner, Source source, ResourceLocation entity) {
		List<BionicModelAlias> aliases = BionicTraitRegistry.clientModelAliases(entity);
		if (aliases.isEmpty()) return;
		Map<Object, Source> sources = new IdentityHashMap<>();
		for (Field field : SHAPES.get(owner.getClass()).fields) {
			if (!(read(field, owner) instanceof Iterable<?> values)) continue;
			int index = 0;
			for (Object element : values) {
				if (element != null && !isPart(element)) {
					Set<String> names = new LinkedHashSet<>(source.names());
					for (BionicModelAlias alias : aliases)
						if (alias.matches(source.names(), index))
							for (String name : alias.names()) if (names.size() < 16) names.add(name);
					if (!names.equals(source.names())) sources.put(element, new Source(Set.copyOf(names),
						BionicTraitRegistry.clientMatchesPart(BionicAnatomyRole.HEAD, entity, names, source.nodes()), source.nodes()));
				}
				index++;
			}
		}
		if (!sources.isEmpty()) geometrySources.put(owner, sources);
	}

	private void instrument(Object owner) {
		for (Field field : SHAPES.get(owner.getClass()).fields) {
			Object value = read(field, owner);
			if (!(value instanceof Iterable<?>) || !field.getType().isInterface()) continue;
			try {
				Object proxy = Proxy.newProxyInstance(field.getType().getClassLoader(), new Class<?>[]{field.getType()},
					(ignored, method, args) -> access(owner, value, method, args));
				field.set(owner, proxy);
				replacements.add(new Replacement(owner, field, value));
			} catch (IllegalAccessException | IllegalArgumentException ignored) {
				// A concrete/inaccessible collection retains its normal rendering and unknown labels.
			}
		}
	}

	private Object access(Object owner, Object delegate, Method method, Object[] args) throws Throwable {
		if (method.getName().equals("forEach") && args != null && args.length == 1 && args[0] instanceof Consumer<?> action) {
			Source saved = current;
			try {
				return invoke(delegate, method, new Object[]{(Consumer<Object>) element -> {
					select(owner, element);
					accept(action, element);
				}});
			} finally { current = saved; }
		}
		Object result = invoke(delegate, method, args);
		if (result instanceof Iterator<?> && method.getReturnType().isInterface()) {
			Source saved = current;
			return Proxy.newProxyInstance(method.getReturnType().getClassLoader(), new Class<?>[]{method.getReturnType()},
				(ignored, operation, arguments) -> {
					if (operation.getName().equals("forEachRemaining") && arguments != null
						&& arguments.length == 1 && arguments[0] instanceof Consumer<?> action) {
						try {
							return invoke(result, operation, new Object[]{(Consumer<Object>) element -> {
								select(owner, element);
								accept(action, element);
							}});
						} finally { current = saved; }
					}
					Object element = invoke(result, operation, arguments);
					if (operation.getName().equals("next") || operation.getName().equals("previous")) select(owner, element);
					else if ((operation.getName().equals("hasNext") || operation.getName().equals("hasPrevious"))
						&& Boolean.FALSE.equals(element)) current = saved;
					return element;
				});
		}
		if (result instanceof Stream<?> stream) return stream.peek(element -> select(owner, element));
		if (method.getName().equals("get") || method.getName().equals("getFirst") || method.getName().equals("getLast"))
			select(owner, result);
		return result;
	}

	@SuppressWarnings("unchecked")
	private static void accept(Consumer<?> action, Object element) {
		((Consumer<Object>) action).accept(element);
	}

	private void select(Object owner, Object element) {
		Source geometry = geometrySources.getOrDefault(owner, Map.of()).get(element);
		if (geometry != null) { current = geometry; return; }
		Part part = parts.get(element);
		if (part == null) part = parts.get(owner);
		current = part == null ? UNKNOWN : part.source;
	}

	private static Object invoke(Object owner, Method method, Object[] arguments) throws Throwable {
		try { return method.invoke(owner, arguments); }
		catch (InvocationTargetException exception) { throw exception.getCause(); }
	}

	private static Object read(Field field, Object owner) {
		try { return field.get(owner); }
		catch (IllegalAccessException ignored) { return null; }
	}

	@Override public void close() {
		if (closed) return;
		closed = true;
		IllegalStateException failure = null;
		try {
			for (int i = replacements.size() - 1; i >= 0; i--) {
				Replacement replacement = replacements.get(i);
				try { replacement.field.set(replacement.owner, replacement.value); }
				catch (IllegalAccessException | RuntimeException exception) {
					if (failure == null) failure = new IllegalStateException("Could not restore model collection", exception);
					else failure.addSuppressed(exception);
				}
			}
		} finally {
			if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
		}
		if (failure != null) throw failure;
	}

	record Source(Set<String> names, boolean head, Set<BionicModelNode> nodes) {}
	private record Shape(List<Field> fields, boolean part) {}
	private record Replacement(Object owner, Field field, Object value) {}
	private static final class Part {
		private final Set<String> names = new LinkedHashSet<>();
		private final Set<Object> children = Collections.newSetFromMap(new IdentityHashMap<>());
		private Source source = UNKNOWN;
		private void addName(String name) {
			if (names.size() < 16 && BionicAnatomySnapshot.validPartName(name)) names.add(name);
		}
	}
}
