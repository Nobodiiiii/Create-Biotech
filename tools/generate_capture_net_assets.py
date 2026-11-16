"""Rebuild pixel textures and export art/capture_net.bbmodel (Pillow required)."""
import base64
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/create_biotech"
SOURCE = ROOT / "art/capture_net.bbmodel"
TEXTURES = ASSETS / "textures/item/capture_net"
MATERIALS = ROOT / "art/reference/capture_net_materials"
TIERS = ("wooden", "stone", "iron", "golden", "diamond", "netherite")
DISPLAY = {
    "thirdperson_righthand": {"rotation": [-22.5, 90, 0], "translation": [0, 3.25, -1.5], "scale": [0.8] * 3},
    "thirdperson_lefthand": {"rotation": [-22.5, -90, 0], "translation": [0, 3.25, -1.5], "scale": [0.8] * 3},
    "firstperson_righthand": {"rotation": [-4.5, 100.25, 10], "translation": [1, 2, 1], "scale": [0.75] * 3},
    "firstperson_lefthand": {"rotation": [-4.5, -100.25, -10], "translation": [1, 2, 1], "scale": [0.75] * 3},
    "gui": {"rotation": [-29.13, 157.28, 38.71], "translation": [-0.64, 0.33, 0], "scale": [0.556] * 3},
    "ground": {"rotation": [-90, 0, 0], "translation": [0, 2, 0], "scale": [0.6] * 3},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, -1], "scale": [0.65] * 3},
}


def write(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(content, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


BLOCK_TEXTURES = {
    "wooden": "oak_planks",
    "stone": "stone",
    "iron": "iron_block",
    "golden": "gold_block",
    "diamond": "diamond_block",
    "netherite": "netherite_block",
}
# Interior strips retain each block's grain/reflections without its outer border.
# Coordinates are native 16x16 source pixels, not colors to be repainted.
PLANK_STRIPS = ((1, 1), (2, 5), (3, 9), (1, 13))
BLOCK_STRIPS = ((3, 1), (6, 2), (9, 1), (12, 2))
HORIZONTAL_BLOCK_STRIPS = ((1, 3), (2, 6), (1, 9), (2, 12))
PARTS = ("wooden_handle", "simple_metal_socket", "square_hoop_top",
         "square_hoop_bottom", "square_hoop_left", "square_hoop_right")
FACES = ("north", "east", "south", "west", "up", "down")
# Preserve the current wooden frame's six-tone placement, including its hand-edited pixels.
# Keep the former iron ramp's gray-violet hue, lifted above the stone frame,
# while retaining a darker highlight than the pale mesh.
IRON_WOOD_RAMP = ("785c32", "8c6d3c", "967441", "9f844d", "af8f55", "b8945f")
IRON_RAMP = ("707075", "828288", "94949b", "a3a3ab", "b0b0b8", "bcbcc4")
IRON_RECOLOR = {tuple(bytes.fromhex(wood)) + (255,): tuple(bytes.fromhex(iron)) + (255,)
                for wood, iron in zip(IRON_WOOD_RAMP, IRON_RAMP)}


def crop_face(blocks, tier, name, direction, width, height):
    """Copy a contiguous block-texture crop at 1:1 scale, only rotating whole texels."""
    material = BLOCK_TEXTURES[tier]
    if name == "wooden_handle":
        material = "stripped_oak_log_top" if direction in ("up", "down") else "stripped_oak_log"
    block = blocks[material]
    strip = (PARTS.index(name) + FACES.index(direction)) % len(BLOCK_STRIPS)
    long, cross = max(width, height), min(width, height)
    if material == "oak_planks":
        # Follow one plank along its grain rather than crossing the dark plank seams.
        u, v = PLANK_STRIPS[strip]
        crop_width, crop_height = long, cross
        rotate = height > width
    elif material in ("stone", "iron_block"):
        u, v = HORIZONTAL_BLOCK_STRIPS[strip]
        crop_width, crop_height = long, cross
        rotate = height > width
    else:
        # The other block materials retain their vertical source strips.
        u, v = BLOCK_STRIPS[strip]
        crop_width, crop_height = cross, long
        rotate = width > height
    if u + crop_width > block.width or v + crop_height > block.height:
        raise ValueError(f"{name}/{direction}: block-texture crop exceeds the source image")
    patch = block.crop((u, v, u + crop_width, v + crop_height))
    if rotate:
        patch = patch.transpose(Image.Transpose.ROTATE_90)
    if patch.size != (width, height):
        raise ValueError(f"{name}/{direction}: cropped face must retain its native pixel size")
    return patch


def pixel_textures(tiers=TIERS):
    TEXTURES.mkdir(parents=True, exist_ok=True)
    source = json.loads(SOURCE.read_text(encoding="utf-8"))
    atlas_texture = next(t for t in source["textures"] if t["name"] != "mesh.png")
    atlas_size = (atlas_texture["uv_width"], atlas_texture["uv_height"])
    frame_cubes = [e for e in source["elements"] if e["name"] != "single_vertical_net_pouch"]
    blocks = {name: Image.open(MATERIALS / f"{name}.png").convert("RGBA")
              for name in (*BLOCK_TEXTURES.values(), "stripped_oak_log", "stripped_oak_log_top")}
    iron_pattern = Image.open(MATERIALS / "iron_wood_pattern.png").convert("RGBA") if "iron" in tiers else None
    for tier in tiers:
        atlas = Image.new("RGBA", atlas_size)
        occupied = set()
        for cube in frame_cubes:
            for direction, face in cube["faces"].items():
                u1, v1, u2, v2 = face["uv"]
                if not (all(v == int(v) for v in face["uv"]) and 0 <= u1 < u2 <= atlas.width
                        and 0 <= v1 < v2 <= atlas.height):
                    raise ValueError("Pack each frame face into its own integer UV island before painting")
                pixels = {(u, v) for u in range(u1, u2) for v in range(v1, v2)}
                if occupied & pixels:
                    raise ValueError("Frame UV islands must not overlap")
                occupied.update(pixels)
                if tier == "iron" and cube["name"] != "wooden_handle":
                    patch = iron_pattern.crop(face["uv"])
                    patch.putdata([IRON_RECOLOR[color] for color in patch.get_flattened_data()])
                else:
                    patch = crop_face(blocks, tier, cube["name"], direction, u2 - u1, v2 - v1)
                atlas.paste(patch, (u1, v1))
                # Duplicate the edge texels into one-pixel gutters for atlas filtering.
                for u in range(-1, patch.width + 1):
                    for v in range(-1, patch.height + 1):
                        if 0 <= u < patch.width and 0 <= v < patch.height:
                            continue
                        dest = (u1 + u, v1 + v)
                        if 0 <= dest[0] < atlas.width and 0 <= dest[1] < atlas.height:
                            atlas.putpixel(dest, patch.getpixel((max(0, min(patch.width - 1, u)),
                                                               max(0, min(patch.height - 1, v)))))
        atlas.save(TEXTURES / f"{tier}.png")
    # Exact opaque colors sampled from Minecraft 1.21.1's cobweb.png.
    # One texel is one model pixel on every face; the mouth has no front quad.
    cobweb = Image.open(ROOT / "art/reference/cobweb.png").convert("RGBA")
    colors = sorted(set(c for c in cobweb.get_flattened_data() if c[3] == 255), key=lambda c: sum(c[:3]))
    shadow, thread, knot = colors
    mesh = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            if x % 3 == 0 or y % 3 == 0:
                color = thread
                if x % 3 == 0 and y % 3 == 0:
                    color = knot
                elif (x // 3 + 2 * (y // 3)) % 5 == 0:
                    color = shadow
                mesh.putpixel((x, y), color)
    mesh.save(TEXTURES / "mesh.png")
    # Keep the embedded editor textures identical to the exported PNGs.
    for texture in source["textures"]:
        texture["source"] = "data:image/png;base64," + base64.b64encode(
            (TEXTURES / texture["name"]).read_bytes()).decode("ascii")
    SOURCE.write_text(json.dumps(source, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")


def export_source():
    source = json.loads(SOURCE.read_text(encoding="utf-8"))
    elements = {e["uuid"]: e for e in source["elements"]}
    groups = {}
    group_defs = {g["uuid"]: g for g in source.get("groups", [])}

    def visit(node):
        if isinstance(node, str):
            return
        group = group_defs.get(node["uuid"], node)
        groups[group["name"]] = (group, [c for c in node["children"] if isinstance(c, str)])
        for child in node["children"]:
            visit(child)

    for node in source["outliner"]:
        visit(node)
    texture_ids, texture_sizes = {}, {}
    for index, texture in enumerate(source["textures"]):
        label = "mesh" if texture["name"] == "mesh.png" else "atlas"
        texture_ids[index] = label
        texture_ids[texture["uuid"]] = label
        texture_sizes[label] = (texture["uv_width"], texture["uv_height"])

    def export_cube(cube):
        sizes = [b - a for a, b in zip(cube["from"], cube["to"])]
        x, y, z = sizes
        face_sizes = {"north": (x, y), "south": (x, y), "east": (z, y),
                      "west": (z, y), "up": (x, z), "down": (x, z)}
        is_net = all(texture_ids.get(f.get("texture")) == "mesh"
                     for f in cube["faces"].values() if f.get("texture") is not None)
        for start, end in zip(cube["from"], cube["to"]):
            if start != int(start) or end != int(end) or end < start:
                raise ValueError(f"{cube['name']}: coordinates and sizes must be whole pixels")
        if sizes.count(0) > (1 if is_net else 0):
            raise ValueError(f"{cube['name']}: only the net panels may have one zero-thickness axis")
        rotation = cube.get("rotation", [0, 0, 0])
        axes = [i for i, v in enumerate(rotation) if v]
        inflate = cube.get("inflate", 0)
        if inflate and (not is_net or inflate != 0.125):
            raise ValueError(f"{cube['name']}: only the net may use the 1/8-pixel static seam inflation")
        if len(axes) > 1 or any(v not in (0, -45, -22.5, 22.5, 45) for v in rotation):
            raise ValueError(f"{cube['name']}: use uninflated Java-compatible cuboid rotations")
        faces = {}
        for direction, face in cube["faces"].items():
            if face.get("texture") is None:
                continue
            texture = texture_ids[face["texture"]]
            width, height = texture_sizes[texture]
            u1, v1, u2, v2 = face["uv"]
            if any(v != int(v) for v in face["uv"]) or (abs(u2 - u1), abs(v2 - v1)) != face_sizes[direction]:
                raise ValueError(f"{cube['name']}/{direction}: UVs must use exactly one texel per model pixel")
            uv = [v * 16 / (width if i % 2 == 0 else height) for i, v in enumerate(face["uv"])]
            faces[direction] = {"uv": uv, "texture": "#" + texture}
            if face.get("rotation", 0):
                faces[direction]["rotation"] = face["rotation"]
        # Blockbench inflation is static geometry. Embed the net sides in the hoop rather than
        # leaving them coplanar with its inner walls; keep the source UV islands on whole texels.
        result = {"name": cube["name"], "from": [v - inflate for v in cube["from"]],
                  "to": [v + inflate for v in cube["to"]], "faces": faces}
        if axes:
            axis = axes[0]
            result["rotation"] = {"angle": rotation[axis], "axis": "xyz"[axis], "origin": cube["origin"]}
        return result

    for group_name, basename in (("frame", None), ("bag", "bag")):
        cubes = [export_cube(elements[c]) for c in groups[group_name][1]]
        for tier in TIERS if group_name == "frame" else ("iron",):
            textures = {"atlas": f"create_biotech:item/capture_net/{tier}",
                        "mesh": "create_biotech:item/capture_net/mesh",
                        "particle": f"create_biotech:item/capture_net/{tier}"}
            write(ASSETS / f"models/item/capture_net/{basename or tier + '_frame'}.json", {
                "credit": "Made with Blockbench / Create: Biotech", "parent": "minecraft:block/block",
                "ambientocclusion": False, "textures": textures, "elements": cubes,
            })
    for tier in TIERS:
        write(ASSETS / f"models/item/{tier}_capture_net.json", {
            "parent": "builtin/entity", "gui_light": "front",
            "textures": {"particle": f"create_biotech:item/capture_net/{tier}"}, "display": DISPLAY,
        })
    export_animation(source, groups)
    print(f"Exported {len(elements)} cuboids, 6 tiers and {len(source['animations'])} animation clips from {SOURCE.name}")


def export_animation(source, groups):
    """Export the exact linear Blockbench tracks; the renderer never scales cubes."""
    names = {g[0]["uuid"]: name for name, g in groups.items()}
    lines = ["package com.nobodiiiii.createbiotech.content.capturenet.client;", "",
             "// Generated by tools/generate_capture_net_assets.py from art/capture_net.bbmodel.",
             "// Edit animation keys and pivots in Blockbench, then run the exporter.",
             "final class CaptureNetAnimation {", "\tprivate CaptureNetAnimation() {}", ""]

    def number(value):
        return f"{float(value):g}f"

    for name in ("grip", "bag"):
        xyz = ", ".join(number(v - 8) for v in groups[name][0]["origin"])
        lines.append(f"\tstatic final float[] {name.upper()}_PIVOT = {{{xyz}}};")
    bag_ids = set(groups["bag"][1])
    tip_depth = max(e["to"][2] + e.get("inflate", 0) for e in source["elements"] if e["uuid"] in bag_ids)
    tip_depth -= groups["bag"][0]["origin"][2]
    if tip_depth <= 0:
        raise ValueError("The net far wall must extend behind the bag pivot")
    lines.append(f"\tstatic final float BAG_TIP_DEPTH = {number(tip_depth)};")
    for animation in source["animations"]:
        label = animation["name"].upper()
        lines += ["", f"\tstatic final float {label}_LENGTH = {number(animation['length'])};"]
        for uuid, animator in animation["animators"].items():
            keyframes = animator.get("keyframes", [])
            if not keyframes:
                continue
            if uuid not in names:
                raise ValueError("Unsupported animation target")
            if any(k["channel"] not in ("rotation", "position") for k in keyframes):
                raise ValueError("Only rigid rotation/position animation is supported; do not scale the pixel cuboids")
            target = "bag" if animation["name"] == "occupied" else "grip"
            if names[uuid] != target:
                raise ValueError(f"{animation['name']}: animate only {target}")
            for channel in ("rotation", "position"):
                keys = sorted((k for k in keyframes if k["channel"] == channel), key=lambda k: k["time"])
                if not keys:
                    continue
                lines.append(f"\tstatic final float[][] {label}_{names[uuid].upper()}_{channel.upper()} = {{")
                for key in keys:
                    if key["interpolation"] != "linear" or len(key["data_points"]) != 1:
                        raise ValueError("Use single-point linear animation keys for exact runtime parity")
                    point = key["data_points"][0]
                    if target == "bag":
                        # X drives far-wall sway, reduced towards the mouth by the renderer.
                        # Keep the inflated root buried in the rim even at the combined extrema.
                        limits = (4.5, 0, 0) if channel == "rotation" else (2, 0.05, 0.375)
                        if any(abs(float(point[axis])) > limit for axis, limit in zip(("x", "y", "z"), limits)):
                            raise ValueError(f"Occupied bag {channel} exceeds the embedded-mouth motion limits")
                    values = [key["time"]] + [float(point[a]) for a in ("x", "y", "z")]
                    lines.append("\t\t{" + ", ".join(number(v) for v in values) + "},")
                lines.append("\t};")
    lines += ["", "\tstatic float sample(float[][] keys, float seconds, int axis) {",
              "\t\tif (seconds <= keys[0][0]) return keys[0][axis + 1];",
              "\t\tfor (int i = 1; i < keys.length; i++) {", "\t\t\tif (seconds > keys[i][0]) continue;",
              "\t\t\tfloat[] a = keys[i - 1], b = keys[i];",
              "\t\t\tfloat alpha = (seconds - a[0]) / (b[0] - a[0]);",
              "\t\t\treturn a[axis + 1] + alpha * (b[axis + 1] - a[axis + 1]);", "\t\t}",
              "\t\treturn keys[keys.length - 1][axis + 1];", "\t}", "}", ""]
    dest = ROOT / "src/main/java/com/nobodiiiii/createbiotech/content/capturenet/client/CaptureNetAnimation.java"
    dest.write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    if not SOURCE.exists():
        raise SystemExit("Save art/capture_net.bbmodel with independent frame UV islands in Blockbench before exporting.")
    pixel_textures()
    export_source()
