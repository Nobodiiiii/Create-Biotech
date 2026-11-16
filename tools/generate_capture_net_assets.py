"""Rebuild pixel textures and export art/capture_net.bbmodel (Pillow required)."""
import base64
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/create_biotech"
SOURCE = ROOT / "art/capture_net.bbmodel"
TEXTURES = ASSETS / "textures/item/capture_net"
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


RAMPS = {
    "wooden": ("654628", "79532e", "906438", "a57843", "b78950", "c99b61"),
    "stone": ("595753", "6e6c67", "84817c", "96938d", "a5a29c", "b7b4ad"),
    "iron": ("606064", "828287", "a7a7ab", "bebec1", "d4d4d6", "e8e8e9"),
    "golden": ("916228", "b58530", "d8a53b", "e8bb48", "f5d36a", "ffe69a"),
    "diamond": ("236568", "288f92", "29b6b7", "3cceca", "75e3da", "b1f2e7"),
    "netherite": ("302b2c", "413a3c", "554a4d", "66595b", "7d6c6a", "96817a"),
}
HANDLE_RAMP = RAMPS["wooden"]


def paint_face(tier, name, direction, width, height):
    """Paint at native resolution; every island belongs to one complete cube face."""
    wooden = name == "wooden_handle" or tier == "wooden"
    ramp = HANDLE_RAMP if name == "wooden_handle" else RAMPS[tier]
    colors = [tuple(bytes.fromhex(c)) + (255,) for c in ramp]
    patch = Image.new("RGBA", (width, height))
    face_seed = ("north", "east", "south", "west", "up", "down").index(direction)
    part_seed = sum(map(ord, name))
    # Long, low-contrast clusters read as grain and reflections on one-pixel bars.
    grain = ((0, 0, 0, 1, 1, 1, 0, 0, 0, 0, -1, -1, 0),
             (0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0))
    mineral = (3, 3, 2, 2, 2, 3, 3, 3, 4, 4, 3, 3, 3)
    highlight_start = {"square_hoop_top": 2, "square_hoop_bottom": 6,
                       "square_hoop_left": 1, "square_hoop_right": 5}.get(name, 0)
    for y in range(height):
        for x in range(width):
            long = y if height > width else x
            cross = x if height > width else y
            if wooden:
                if direction in ("up", "down") and name == "wooden_handle":
                    level = ((3, 2), (2, 3))[y][x]
                else:
                    strand = grain[cross % 2]
                    level = (3 if cross == 0 else 2) + strand[(long + face_seed * 2) % len(strand)]
            elif tier == "stone":
                level = mineral[(long + cross * 3 + part_seed % 4) % len(mineral)]
                if direction in ("east", "west", "down"):
                    level -= 1
            elif direction in ("north", "south"):
                level = 4 if highlight_start <= long < highlight_start + 3 else 3
                if long >= max(width, height) - 2:
                    level = 2
                if name == "simple_metal_socket":
                    level = ((3, 4), (3, 4), (2, 3))[y][x]
            else:
                # A continuous lip defines the depth without striping the rim.
                depth = y if direction in ("up", "down") else x
                level = 3 if depth == 0 and direction != "down" else 2
            patch.putpixel((x, y), colors[max(0, min(len(colors) - 1, level))])
    return patch


def pixel_textures():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    source = json.loads(SOURCE.read_text(encoding="utf-8"))
    atlas_texture = next(t for t in source["textures"] if t["name"] != "mesh.png")
    atlas_size = (atlas_texture["uv_width"], atlas_texture["uv_height"])
    frame_cubes = [e for e in source["elements"] if e["name"] != "single_vertical_net_pouch"]
    for tier in TIERS:
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
                patch = paint_face(tier, cube["name"], direction, u2 - u1, v2 - v1)
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
        if cube.get("inflate", 0) or len(axes) > 1 or any(v not in (0, -45, -22.5, 22.5, 45) for v in rotation):
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
        result = {"name": cube["name"], "from": cube["from"], "to": cube["to"], "faces": faces}
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
    print(f"Exported {len(elements)} integer cuboids, 6 tiers and {len(source['animations'])} animation clips from {SOURCE.name}")


def export_animation(source, groups):
    """Export the exact linear Blockbench tracks; the renderer never scales cubes."""
    names = {g[0]["uuid"]: name for name, g in groups.items()}
    lines = ["package com.nobodiiiii.createbiotech.content.capturenet.client;", "",
             "// Generated by tools/generate_capture_net_assets.py from art/capture_net.bbmodel.",
             "// Edit animation keys and pivots in Blockbench, then run the exporter.",
             "final class CaptureNetAnimation {", "\tprivate CaptureNetAnimation() {}", ""]

    def number(value):
        return f"{float(value):g}f"

    for name in ("grip",):
        xyz = ", ".join(number(v - 8) for v in groups[name][0]["origin"])
        lines.append(f"\tstatic final float[] {name.upper()}_PIVOT = {{{xyz}}};")
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
            if names[uuid] != "grip":
                raise ValueError("Animate the shared grip only so the net mouth stays attached to the hoop")
            for channel in ("rotation", "position"):
                keys = sorted((k for k in keyframes if k["channel"] == channel), key=lambda k: k["time"])
                if not keys:
                    continue
                lines.append(f"\tstatic final float[][] {label}_{names[uuid].upper()}_{channel.upper()} = {{")
                for key in keys:
                    if key["interpolation"] != "linear" or len(key["data_points"]) != 1:
                        raise ValueError("Use single-point linear animation keys for exact runtime parity")
                    point = key["data_points"][0]
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
