"""Export the Blockbench cultivation chamber into NeoForge OBJ block and item models."""

import base64
import json
import math
from collections import defaultdict
from itertools import product
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "art/large_bio_culture_chamber_3x3x4_cubes.bbmodel"
ASSETS = ROOT / "src/main/resources/assets/create_biotech"
NAME = "slime_culture_chamber"
NAMESPACE = "create_biotech"
MODEL_DIR = ASSETS / "models/block" / NAME
TEXTURE_DIR = ASSETS / "textures/block" / NAME
ATLAS = "atlas.png"
ATLAS_SIZE = (256, 128)
PARTICLE = "create:block/zinc_block"
LAYERS = {"frame": "minecraft:cutout", "interior": "minecraft:translucent"}
EPSILON = 1e-7

# UV bounds in atlas pixels. The materials share the sprite but keep their render settings.
MATERIAL_REGIONS = {
    "materials": (0, 0, 192, 128),
    "glass": (192, 0, 256, 48),
    "slime_shell": (192, 48, 256, 68),
    "phosphor": (192, 68, 256, 128),
}

# Counterclockwise corner order, starting at the top-left corner of each UV rectangle.
FACE_CORNERS = {
    "north": ((1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)),
    "east": ((1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)),
    "south": ((0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)),
    "west": ((0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)),
    "up": ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)),
    "down": ((0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)),
}


def cube_faces(cube, atlas):
    origin = cube["origin"]
    angles = [math.radians(angle) for angle in cube.get("rotation", (0, 0, 0))]
    if sum(angle != 0 for angle in angles) > 1:
        raise ValueError(f"Expected a single rotation axis for {cube['name']}")
    (sx, sy, sz), (cx, cy, cz) = (tuple(map(fn, angles)) for fn in (math.sin, math.cos))
    bounds = (cube["from"], cube["to"])

    def position(corner):
        x, y, z = (bounds[edge][axis] - origin[axis] for axis, edge in enumerate(corner))
        y, z = y * cx - z * sx, y * sx + z * cx
        x, z = x * cy + z * sy, -x * sy + z * cy
        x, y = x * cz - y * sz, x * sz + y * cz
        return tuple(value + offset for value, offset in zip((x, y, z), origin))

    for direction, face in cube["faces"].items():
        texture_index = face["texture"]
        if texture_index is None:
            continue
        if texture_index != 0:
            raise ValueError(f"Expected atlas UVs for {cube['name']} {direction}")
        width, height = atlas["uv_width"], atlas["uv_height"]
        u0, v0, u1, v1 = face["uv"]
        u_min, u_max = sorted((u0, u1))
        v_min, v_max = sorted((v0, v1))
        for material, (left, top, right, bottom) in MATERIAL_REGIONS.items():
            if left <= u_min < u_max <= right and top <= v_min < v_max <= bottom:
                break
        else:
            raise ValueError(f"UVs cross atlas material bounds for {cube['name']} {direction}")
        corners = ((u0, v0), (u0, v1), (u1, v1), (u1, v0))
        uv_turns = face.get("rotation", 0) // 90
        polygon = []
        for index, corner in enumerate(FACE_CORNERS[direction]):
            u, v = corners[(index + uv_turns) % 4]
            polygon.append((*position(corner), u / width, 1 - v / height))
        yield material, polygon


def clip(polygon, axis, boundary, sign):
    """Clip against one cell plane, linearly interpolating positions and UVs."""
    result = []
    for start, end in zip(polygon, polygon[1:] + polygon[:1]):
        start_distance = (start[axis] - boundary) * sign
        end_distance = (end[axis] - boundary) * sign
        start_inside = start_distance >= -EPSILON
        end_inside = end_distance >= -EPSILON
        if start_inside:
            result.append(start)
        if start_inside != end_inside and abs(start_distance) > EPSILON and abs(end_distance) > EPSILON:
            fraction = start_distance / (start_distance - end_distance)
            result.append(tuple(a + (b - a) * fraction for a, b in zip(start, end)))
    return result


def face_normal(vertices):
    first, second, third = vertices[:3]
    a, b, c = (second[i] - first[i] for i in range(3))
    d, e, f = (third[i] - first[i] for i in range(3))
    return b * f - c * e, c * d - a * f, a * e - b * d


def obj_faces(polygon):
    # NeoForge's OBJ baker accepts triangles and quads. Clipped polygons can have more vertices.
    if len(polygon) == 4 and math.hypot(*face_normal(polygon)) > EPSILON:
        yield polygon
        return
    for index in range(1, len(polygon) - 1):
        triangle = [polygon[0], polygon[index], polygon[index + 1]]
        if math.hypot(*face_normal(triangle)) > EPSILON:
            yield triangle


def split_face(polygon, cells):
    normal = face_normal(polygon)
    for part, minimum in enumerate(cells):
        piece = polygon
        for axis, lo in enumerate(minimum):
            hi = lo + 16
            # A face exactly on a shared boundary belongs to the cell on its solid side.
            plane = polygon[0][axis]
            if all(abs(vertex[axis] - plane) <= EPSILON for vertex in polygon):
                if (abs(plane - lo) <= EPSILON and normal[axis] > 0
                        or abs(plane - hi) <= EPSILON and normal[axis] < 0):
                    piece = []
                    break
            piece = clip(clip(piece, axis, lo, 1), axis, hi, -1)
        for face in obj_faces(piece):
            yield part, [tuple((vertex[i] - minimum[i]) / 16 for i in range(3)) + vertex[3:]
                         for vertex in face]


def add_face(groups, material, face):
    layer = "interior" if material in {"slime_shell", "phosphor"} else "frame"
    groups[layer][material].append(face)
    if material in {"glass", "phosphor"}:
        groups[layer][material].append(list(reversed(face)))


def write_obj(path, groups):
    lines = [f"# Exported by {Path(__file__).name}", f"mtllib {NAME}.mtl"]
    position_indices = {}
    uv_indices = {}

    def index(values, indices, kind):
        line = " ".join(f"{value:.7f}" for value in values)
        if line not in indices:
            indices[line] = len(indices) + 1
            lines.append(f"{kind} {line}")
        return indices[line]

    for layer, materials in groups.items():
        lines.append(f"g {layer}")
        for material, faces in materials.items():
            lines.append(f"usemtl {material}")
            for face in faces:
                vertices = [f"{index(vertex[:3], position_indices, 'v')}/{index(vertex[3:], uv_indices, 'vt')}"
                            for vertex in face]
                lines.append("f " + " ".join(vertices))
    path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


def make_model(obj_name, groups):
    base = {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": {"particle": PARTICLE}}
    children = {}
    for layer, render_type in LAYERS.items():
        if layer in groups:
            children[layer] = {
                **base, "loader": "neoforge:obj", "render_type": render_type,
                "model": f"{NAMESPACE}:models/block/{NAME}/{obj_name}.obj", "flip_v": True,
                "automatic_culling": False,
            }
    if not children:
        return base
    if len(children) == 1:
        return next(iter(children.values()))
    for layer, child in children.items():
        child["visibility"] = {other: False for other in children if other != layer}
    return {**base, "loader": "neoforge:composite", "children": children, "item_render_order": list(children)}


def write_json(path, model):
    path.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8", newline="\n")


def main():
    source = json.loads(SOURCE.read_text(encoding="utf-8"))
    atlas, = source["textures"]
    if atlas["name"] != ATLAS or (atlas["uv_width"], atlas["uv_height"]) != ATLAS_SIZE:
        raise ValueError(f"Expected {ATLAS} with UV size {ATLAS_SIZE}")
    for group in source["groups"]:
        if any(group.get("rotation", (0, 0, 0))) or not group.get("export", True):
            raise ValueError(f"Unsupported group settings for {group['name']}")

    # Match SlimeCultureChamberBlock's part numbering. The Blockbench front points south.
    cells = [(x, y, z) for y, z, x in product(range(0, 64, 16), range(-24, 24, 16), range(-24, 24, 16))]
    block_groups = [defaultdict(lambda: defaultdict(list)) for _ in cells]
    item_groups = defaultdict(lambda: defaultdict(list))
    for cube in source["elements"]:
        if not cube.get("export", True):
            continue
        if cube["type"] != "cube" or cube.get("inflate", 0):
            raise ValueError(f"Expected an uninflated cube for {cube['name']}")
        for material, face in cube_faces(cube, atlas):
            for x, y, z, _, _ in face:
                if max(abs(x), abs(z)) > 24 + EPSILON or not -EPSILON <= y <= 64 + EPSILON:
                    raise ValueError(f"Cube crosses the 3×4×3 export bounds: {cube['name']}")
            item_face = [(0.5 + x / 64, y / 64, 0.5 + z / 64, u, v) for x, y, z, u, v in face]
            add_face(item_groups, material, item_face)
            for part, piece in split_face(face, cells):
                add_face(block_groups[part], material, piece)

    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    TEXTURE_DIR.mkdir(parents=True, exist_ok=True)
    (TEXTURE_DIR / ATLAS).write_bytes(base64.b64decode(atlas["source"].split(",", 1)[1]))
    materials = []
    atlas_location = f"{NAMESPACE}:block/{NAME}/{Path(ATLAS).stem}"
    for name in MATERIAL_REGIONS:
        ambient = int(name == "phosphor")
        materials.extend([f"newmtl {name}", f"Ka {ambient} {ambient} {ambient}",
                          f"map_Kd {atlas_location}", ""])
    (MODEL_DIR / f"{NAME}.mtl").write_text("\n".join(materials), encoding="utf-8", newline="\n")

    variants = []
    for part, groups in enumerate(block_groups):
        obj_name = f"part_{part}" if groups else "empty"
        if groups:
            write_obj(MODEL_DIR / f"{obj_name}.obj", groups)
        write_json(MODEL_DIR / f"{obj_name}.json", make_model(obj_name, groups))
        for facing, rotation in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
            model = {"model": f"{NAMESPACE}:block/{NAME}/{obj_name}"}
            if rotation:
                model["y"] = rotation
            variants.append(f'    "facing={facing},part={part}": {json.dumps(model)}')
    (ASSETS / "blockstates" / f"{NAME}.json").write_text(
        '{\n  "variants": {\n' + ",\n".join(variants) + "\n  }\n}\n", encoding="utf-8", newline="\n")

    write_obj(MODEL_DIR / "item.obj", item_groups)
    item = make_model("item", item_groups)
    item["display"] = {
        "gui": {"rotation": [25, 35, 0], "scale": [0.8, 0.8, 0.8]},
        "fixed": {"scale": [0.75, 0.75, 0.75]},
    }
    write_json(ASSETS / "models/item" / f"{NAME}.json", item)


if __name__ == "__main__":
    main()
