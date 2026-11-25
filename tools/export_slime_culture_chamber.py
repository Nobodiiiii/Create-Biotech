"""Export the Blockbench cultivation chamber into JSON block and item models."""

import base64
import json
import math
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
EPSILON = 1e-7

# Counterclockwise corner order, starting at the top-left corner of each UV rectangle.
FACE_CORNERS = {
    "north": ((1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)),
    "east": ((1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)),
    "south": ((0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)),
    "west": ((0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)),
    "up": ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)),
    "down": ((0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)),
}
FACE_PLANES = {
    "north": (2, 0),
    "east": (0, 1),
    "south": (2, 1),
    "west": (0, 0),
    "up": (1, 1),
    "down": (1, 0),
}
Y_TURN_FACES = ("north", "west", "south", "east")


def face_vertices(bounds, direction):
    return [tuple(bounds[end][axis] for axis, end in enumerate(corner)) for corner in FACE_CORNERS[direction]]


def prepare_cube(cube):
    origin = cube["origin"]
    x, y, z = cube.get("rotation", (0, 0, 0))
    turns = round(y / 90)
    angle = y - turns * 90
    if x or z or angle not in (-45, -22.5, 0, 22.5, 45):
        raise ValueError(f"Unsupported JSON element rotation for {cube['name']}")

    # Apply full quarter turns to the box and its UVs so only the slant remains in JSON.
    def turn(vertex):
        if not turns:
            return tuple(vertex)
        x, y, z = vertex[0] - origin[0], vertex[1], vertex[2] - origin[2]
        for _ in range(turns % 4):
            x, z = z, -x
        return x + origin[0], y, z + origin[2]

    bounds = (cube["from"], cube["to"])
    axes = list(zip(*(turn(vertex) for vertex in bounds)))
    normalized = (tuple(map(min, axes)), tuple(map(max, axes)))
    faces = {}
    for direction, face in cube["faces"].items():
        if face["texture"] is None:
            continue
        if face["texture"] != 0:
            raise ValueError(f"Expected atlas UVs for {cube['name']} {direction}")
        u0, v0, u1, v1 = face["uv"]
        width, height = ATLAS_SIZE
        if not (0 <= min(u0, u1) < max(u0, u1) <= width and 0 <= min(v0, v1) < max(v0, v1) <= height):
            raise ValueError(f"UVs exceed atlas bounds for {cube['name']} {direction}")
        uv = [(u0, v0), (u0, v1), (u1, v1), (u1, v0)]
        shift = face.get("rotation", 0) // 90
        uv = uv[shift:] + uv[:shift]
        by_position = dict(zip(map(turn, face_vertices(bounds, direction)), uv))
        rotated = direction
        if direction in Y_TURN_FACES:
            rotated = Y_TURN_FACES[(Y_TURN_FACES.index(direction) + turns) % 4]
        faces[rotated] = [by_position[v] for v in face_vertices(normalized, rotated)]
    return normalized, angle, faces


def split_cube(bounds, origin, angle, cells, name):
    world_bounds = bounds
    if angle:
        sine, cosine = math.sin(math.radians(angle)), math.cos(math.radians(angle))
        local_x = [point[0] - origin[0] for point in bounds]
        local_z = [point[2] - origin[2] for point in bounds]
        footprint = [(x * cosine + z * sine + origin[0], z * cosine - x * sine + origin[2])
                     for x, z in product(local_x, local_z)]
        xs, zs = zip(*footprint)
        world_bounds = ((min(xs), bounds[0][1], min(zs)), (max(xs), bounds[1][1], max(zs)))
    chamber_maximum = (24, 64, 24)
    if any(lo < start - EPSILON or hi > end + EPSILON
           for lo, hi, start, end in zip(*world_bounds, (-24, 0, -24), chamber_maximum)):
        raise ValueError(f"Cube crosses the 3×4×3 export bounds: {name}")

    flat_axes = {axis for axis, (lo, hi) in enumerate(zip(*world_bounds)) if lo == hi}
    for part, minimum in enumerate(cells):
        maximum = tuple(start + 16 for start in minimum)
        lower = tuple(max(lo, start) for lo, start in zip(world_bounds[0], minimum))
        upper = tuple(min(hi, end) for hi, end in zip(world_bounds[1], maximum))
        if any(hi - lo <= EPSILON for axis, (lo, hi) in enumerate(zip(lower, upper)) if axis not in flat_axes):
            continue
        # A plane on a shared cell boundary belongs to the cell on its positive side.
        if any(lower[axis] != upper[axis] or upper[axis] == maximum[axis] < chamber_maximum[axis]
               for axis in flat_axes):
            continue
        if angle:
            if any(abs(clipped[axis] - full[axis]) > EPSILON
                   for clipped, full in zip((lower, upper), world_bounds) for axis in (0, 2)):
                raise ValueError(f"Rotated cube crosses a horizontal cell boundary: {name}")
            # A horizontal slice of a Y-rotated box keeps its original X/Z bounds and pivot.
            lower = (bounds[0][0], lower[1], bounds[0][2])
            upper = (bounds[1][0], upper[1], bounds[1][2])
        yield part, (lower, upper)


def clipped_uv(bounds, piece, direction, uv):
    """Interpolate the original UV rectangle over the remaining part of the face."""
    first, second, _, fourth = face_vertices(bounds, direction)
    u_axis = next(i for i in range(3) if fourth[i] != first[i])
    v_axis = next(i for i in range(3) if second[i] != first[i])
    result = []
    for vertex in face_vertices(piece, direction):
        u = (vertex[u_axis] - first[u_axis]) / (fourth[u_axis] - first[u_axis])
        v = (vertex[v_axis] - first[v_axis]) / (second[v_axis] - first[v_axis])
        result.append(tuple(a + u * (d - a) + v * (b - a) for a, b, d in zip(uv[0], uv[1], uv[3])))
    return result


def make_face(uv):
    # Reversed bounds encode mirrors. Only quarter turns need a rotation field.
    turned = uv[0][0] != uv[1][0]
    if turned:
        uv = uv[-1:] + uv[:-1]
    width, height = ATLAS_SIZE
    u0, v0 = uv[0]
    u1, v1 = uv[2]
    coordinates = (u0 * 16 / width, v0 * 16 / height, u1 * 16 / width, v1 * 16 / height)
    face = {"texture": "#atlas", "uv": [round(value, 8) for value in coordinates]}
    if turned:
        face["rotation"] = 90
    return face


def add_piece(elements, faces, bounds, piece, origin, angle, offset, scale=1):
    def to_model(point):
        return [value * scale + shift for value, shift in zip(point, offset)]

    box_faces = {}
    for direction, uv in faces.items():
        axis, side = FACE_PLANES[direction]
        if piece[side][axis] == bounds[side][axis]:
            box_faces[direction] = make_face(clipped_uv(bounds, piece, direction, uv))
    if box_faces:
        element = {"from": to_model(piece[0]), "to": to_model(piece[1]), "faces": box_faces}
        if angle:
            element["rotation"] = {"angle": angle, "axis": "y", "origin": to_model(origin)}
        elements.append(element)


def make_model(elements):
    textures = {"particle": PARTICLE}
    model = {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": textures}
    if elements:
        textures["atlas"] = f"{NAMESPACE}:block/{NAME}/atlas"
        model["render_type"] = "minecraft:cutout"
        model["elements"] = elements
    return model


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
    block_elements = [[] for _ in cells]
    item_elements = []

    for cube in source["elements"]:
        if not cube.get("export", True):
            continue
        if cube["type"] != "cube" or cube.get("inflate", 0) or cube.get("stretch", [1, 1, 1]) != [1, 1, 1]:
            raise ValueError(f"Expected an uninflated cube for {cube['name']}")
        bounds, angle, faces = prepare_cube(cube)
        origin = cube["origin"]
        add_piece(item_elements, faces, bounds, bounds, origin, angle, (8, 0, 8), scale=0.25)
        for part, piece in split_cube(bounds, origin, angle, cells, cube["name"]):
            offset = tuple(-start for start in cells[part])
            add_piece(block_elements[part], faces, bounds, piece, origin, angle, offset)

    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    TEXTURE_DIR.mkdir(parents=True, exist_ok=True)
    (TEXTURE_DIR / ATLAS).write_bytes(base64.b64decode(atlas["source"].split(",", 1)[1]))

    variants = []
    for part, elements in enumerate(block_elements):
        model_name = f"part_{part}" if elements else "empty"
        write_json(MODEL_DIR / f"{model_name}.json", make_model(elements))
        for facing, rotation in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
            model = {"model": f"{NAMESPACE}:block/{NAME}/{model_name}"}
            if rotation:
                model["y"] = rotation
            variants.append(f'    "facing={facing},part={part}": {json.dumps(model)}')
    (ASSETS / "blockstates" / f"{NAME}.json").write_text(
        '{\n  "variants": {\n' + ",\n".join(variants) + "\n  }\n}\n", encoding="utf-8", newline="\n")

    item = make_model(item_elements)
    item["display"] = {
        "gui": {"rotation": [25, 35, 0], "scale": [0.8, 0.8, 0.8]},
        "fixed": {"scale": [0.75, 0.75, 0.75]},
    }
    write_json(ASSETS / "models/item" / f"{NAME}.json", item)


if __name__ == "__main__":
    main()
