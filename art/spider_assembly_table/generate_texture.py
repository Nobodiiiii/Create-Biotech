"""Draw the encased spider atlas at one texel per Minecraft model unit.

Run with Python + Pillow. No resampling or anti-aliasing is used in the atlas.
The 64 x 32 UV layout is Minecraft 1.21.1 SpiderModel.createSpiderBodyLayer().
Create 6.0.10's andesite casing is the local visual/palette reference; all
panels below are drawn for the spider's actual face dimensions.
"""

from pathlib import Path
import json

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "src/main/resources/assets/create_biotech/textures/entity/spider_assembly_table/spider_andesite_encased.png"
PREVIEW = ROOT / "build/art-preview/spider_assembly_table"

# Quiet green-grey andesite, worn timber, and the spider's amber lenses.
PALETTE = {
    "ink": "#343b39", "dark": "#454b48", "edge": "#555c57",
    "steel0": "#656d65", "steel1": "#7b857c", "steel2": "#929e92",
    "shine": "#b1bbae", "wood0": "#533c25", "wood1": "#694c2c",
    "wood2": "#7d5c34", "wood3": "#916d40", "wood4": "#a07c4b",
    "lens0": "#704b2c", "lens1": "#bd813e", "lens2": "#e7b657",
    "lens3": "#ffe4a0",
    # Exact metal shades from Create 6.0.10 andesite_casing.png.
    "casing_outer": "#60635f", "casing_outer_dark": "#505351",
    "casing_corner": "#404543", "casing_inner": "#9aa49d",
    "casing_inner_dark": "#828784", "casing_rivet": "#a8b3ab",
}


def color(name):
    value = PALETTE[name].lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def put(image, x, y, name):
    image.putpixel((x, y), color(name))


def rect(image, box, name):
    ImageDraw.Draw(image).rectangle(box, fill=color(name))


def wood(image, x0, y0, x1, y1, seed):
    """Small, staggered vertical grain clusters instead of pixel noise."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            column = (x - x0 + seed) % 5
            grain = (y - y0 + seed * 2) // 3
            tone = [2, 3, 2, 1, 2][column]
            if (column + grain + seed) % 7 == 0:
                tone = min(4, tone + 1)
            put(image, x, y, f"wood{tone}")
    # A one-texel shadow seats the timber behind its metal frame.
    rect(image, (x0, y0, x1, y0), "wood0")
    rect(image, (x0, y0, x0, y1), "wood1")


def frame(image, thickness=2):
    w, h = image.size
    # Like andesite casing: a dark outer rim and a light inner rim.
    rect(image, (0, 0, w - 1, 0), "casing_outer")
    rect(image, (0, 1, 0, h - 2), "casing_outer")
    rect(image, (w - 1, 1, w - 1, h - 2), "casing_outer_dark")
    rect(image, (0, h - 1, w - 1, h - 1), "casing_outer_dark")
    if thickness == 2:
        rect(image, (1, 1, w - 2, 1), "casing_inner")
        rect(image, (1, h - 2, w - 2, h - 2), "casing_inner_dark")
        rect(image, (1, 2, 1, h - 3), "casing_inner")
        rect(image, (w - 2, 2, w - 2, h - 3), "casing_inner_dark")
        # Four flush screw heads, each exactly one texel.
        for x, y in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
            put(image, x, y, "casing_rivet" if y == 1 else "casing_inner")
    for x, y in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        put(image, x, y, "casing_corner")


def casing(w, h, seed=0, strap=None):
    image = Image.new("RGBA", (w, h), color("steel0"))
    wood(image, 2, 2, w - 3, h - 3, seed)
    if strap == "across":
        mid = h // 2 - 1
        rect(image, (0, mid, w - 1, mid), "steel2")
        rect(image, (0, mid + 1, w - 1, mid + 1), "steel0")
        for x in (1, w - 2):
            put(image, x, mid, "shine")
            put(image, x, mid + 1, "dark")
    elif strap == "upright":
        mid = w // 2 - 1
        rect(image, (mid, 0, mid, h - 1), "steel1")
        rect(image, (mid + 1, 0, mid + 1, h - 1), "steel0")
        put(image, mid, 1, "shine")
        put(image, mid, h - 2, "steel2")
    # Keep strap junctions inside the frame's dark/light perimeter.
    frame(image)
    return image


def steel(w, h):
    image = Image.new("RGBA", (w, h), color("steel0"))
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            put(image, x, y, "steel1" if (x // 3 + y // 3) % 3 == 0 else "steel0")
    frame(image, 1)
    return image


def faceplate():
    image = steel(8, 8)
    rect(image, (1, 1, 6, 5), "dark")
    rect(image, (2, 2, 5, 4), "ink")
    # Six small lenses and two 2 x 2 main eyes follow the original spider.
    for x, y in ((1, 0), (6, 0), (2, 1), (5, 1), (0, 2), (7, 2)):
        put(image, x, y, "lens2")
    for x in (2, 4):
        put(image, x, 3, "lens3")
        put(image, x + 1, 3, "lens2")
        put(image, x, 4, "lens1")
        put(image, x + 1, 4, "lens0")
    rect(image, (1, 5, 6, 5), "steel1")
    for x in (2, 5):
        put(image, x, 6, "ink")
        put(image, x, 7, "steel0")
    return image


def connector(w, h):
    image = steel(w, h)
    x, y = w // 2 - 2, h // 2 - 2
    rect(image, (x, y, x + 3, y + 3), "dark")
    rect(image, (x, y, x + 3, y), "steel2")
    rect(image, (x, y, x, y + 3), "steel1")
    rect(image, (x + 1, y + 1, x + 2, y + 2), "ink")
    return image


def leg_strip(side):
    image = Image.new("RGBA", (16, 2), color("steel0"))
    for x in range(16):
        put(image, x, 0, "steel2" if side in ("up", "north") else "steel1")
        put(image, x, 1, "steel0" if side == "up" else "edge")
        if x in (3, 4, 5, 6, 9, 10, 11, 12):
            put(image, x, 0, "wood3" if x % 4 == 0 else "wood2")
            put(image, x, 1, "wood1" if side == "down" else "wood0")
    for x in (0, 15):
        put(image, x, 0, "edge")
        put(image, x, 1, "dark")
    for x in (1, 14):
        put(image, x, 0, "shine")
        put(image, x, 1, "steel1")
    return image


def cube_uv(u, v, w, h, d):
    # Names use the upright (Y-up) editor convention. In ModelPart the
    # first top rectangle is Direction.DOWN before the renderer flips Y.
    return {
        "up": (u + d, v, u + d + w, v + d),
        "down": (u + d + w, v, u + d + w * 2, v + d),
        "west": (u, v + d, u + d, v + d + h),
        "north": (u + d, v + d, u + d + w, v + d + h),
        "east": (u + d + w, v + d, u + d * 2 + w, v + d + h),
        "south": (u + d * 2 + w, v + d, u + d * 2 + w * 2, v + d + h),
    }


LAYOUT = {
    "head": cube_uv(32, 4, 8, 8, 8),
    "body": cube_uv(0, 0, 6, 6, 6),
    "abdomen": cube_uv(0, 12, 10, 8, 12),
    "leg": cube_uv(18, 0, 16, 2, 2),
}


def generate():
    atlas = Image.new("RGBA", (64, 32))
    painted = set()

    def apply(part, face, image):
        x0, y0, x1, y1 = LAYOUT[part][face]
        assert image.size == (x1 - x0, y1 - y0), (part, face, image.size)
        area = {(x, y) for x in range(x0, x1) for y in range(y0, y1)}
        assert not area & painted, (part, face, "overlapping UV islands")
        painted.update(area)
        atlas.paste(image, (x0, y0))

    apply("head", "north", faceplate())
    apply("head", "up", casing(8, 8, 0))
    apply("head", "down", connector(8, 8))
    apply("head", "south", connector(8, 8))
    for side in ("west", "east"):
        panel = casing(8, 8, 1)
        rect(panel, (3, 3, 4, 3), "wood0")
        rect(panel, (3, 5, 4, 5), "wood0")
        apply("head", side, panel)

    for face in LAYOUT["body"]:
        panel = steel(6, 6)
        if face in ("west", "east", "south", "north"):
            rect(panel, (1, 2, 4, 3), "dark")
            rect(panel, (2, 2, 3, 3), "wood2")
            put(panel, 1, 1, "shine")
            put(panel, 4, 4, "steel2")
        else:
            rect(panel, (2, 1, 3, 4), "steel2" if face == "up" else "edge")
            put(panel, 2, 2, "dark")
            put(panel, 3, 3, "dark")
        apply("body", face, panel)

    apply("abdomen", "up", casing(10, 12, 1, "across"))
    apply("abdomen", "north", casing(10, 8, 0))
    for side in ("west", "east"):
        apply("abdomen", side, casing(12, 8, 2, "upright"))
    rear = casing(10, 8, 1)
    rect(rear, (3, 2, 6, 5), "steel0")
    rect(rear, (3, 2, 6, 2), "steel2")
    rect(rear, (4, 3, 5, 4), "dark")
    put(rear, 4, 3, "steel1")
    apply("abdomen", "south", rear)
    belly = steel(10, 12)
    for y in (3, 6, 9):
        rect(belly, (2, y, 7, y), "dark")
        rect(belly, (2, y + 1, 7, y + 1), "steel1")
    for x in (1, 8):
        put(belly, x, 1, "shine")
        put(belly, x, 10, "steel2")
    apply("abdomen", "down", belly)

    for face in LAYOUT["leg"]:
        if face in ("west", "east"):
            cap = Image.new("RGBA", (2, 2), color("edge"))
            put(cap, 0, 0, "steel2")
            put(cap, 1, 1, "dark")
            apply("leg", face, cap)
        else:
            apply("leg", face, leg_strip(face))

    # Validate coverage before writing the runtime resource.
    assert len(painted) == 1328
    assert set(atlas.getchannel("A").tobytes()) == {0, 255}
    assert all(atlas.getpixel(p)[3] == 255 for p in painted)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(OUTPUT)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    atlas.resize((1024, 512), Image.Resampling.NEAREST).save(PREVIEW / "atlas_16x.png")
    (PREVIEW / "uv_layout.json").write_text(json.dumps(LAYOUT, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {OUTPUT.relative_to(ROOT)}: 64 x 32 RGBA; {len(painted)} mapped texels; 24 UV islands; 1:1 texel density.")


if __name__ == "__main__":
    generate()
