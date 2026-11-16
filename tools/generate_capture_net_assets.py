"""Rebuild pixel textures and export art/capture_net.bbmodel (Pillow required)."""
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/create_biotech"
SOURCE = ROOT / "art/capture_net.bbmodel"
TEXTURES = ASSETS / "textures/item/capture_net"
TIERS = {"wooden": (137, 98, 58), "stone": (117, 123, 119), "iron": (184, 183, 190),
         "golden": (218, 165, 63), "diamond": (59, 182, 170), "netherite": (81, 69, 74)}
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


def tint(color, shade):
    return tuple(max(0, min(255, c + shade)) for c in color) + (255,)


def pixel_textures():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for tier, base in TIERS.items():
        atlas = Image.new("RGBA", (64, 32))
        swatches = [base, (147, 96, 36), (91, 89, 96), (181, 131, 58),
                    (227, 221, 201), (63, 163, 145), (79, 59, 44), (133, 143, 130)]
        for index, color in enumerate(swatches):
            ox, oy = (index % 4) * 16, (index // 4) * 16
            for y in range(16):
                for x in range(16):
                    shade = [13, 4, -12, -4][(x + y // 2) % 4]
                    if index == 1 or (index == 0 and tier == "wooden"):
                        shade = [-24, 5, 18, -8][x % 4] + [0, 4, -3, 0][(y // 3) % 4]
                    elif index == 4:
                        shade = [7, -4, 2][(x + y) % 3]
                    atlas.putpixel((ox + x, oy + y), tint(color, shade))
        for y in (2, 5, 8, 11, 14):
            for x in range(16):
                atlas.putpixel((32 + x, 16 + y), tint(swatches[6], -17 if x % 3 else 15))
        atlas.save(TEXTURES / f"{tier}.png")
    # Exact opaque colors sampled from Minecraft 1.21.1's cobweb.png.
    # One texel is one model pixel on every face; the mouth has no front quad.
    cobweb = Image.open(ROOT / "art/reference/cobweb.png").convert("RGBA")
    colors = list(dict.fromkeys(c for c in cobweb.get_flattened_data() if c[3] == 255))
    mesh = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            if x % 3 == 0 or y % 3 == 0:
                color = colors[(x // 3 + y // 3) % len(colors)]
                mesh.putpixel((x, y), color)
    mesh.save(TEXTURES / "mesh.png")


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

    for name in ("grip", "bag"):
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
            if names[uuid] != "grip" and any(k["channel"] == "position" for k in keyframes):
                raise ValueError("Only grip position tracks are supported by the renderer")
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
    pixel_textures()
    if SOURCE.exists():
        export_source()
    else:
        print("Textures ready. Save art/capture_net.bbmodel in Blockbench before exporting models.")
