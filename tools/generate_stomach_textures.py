"""Rebuild four stomach blocks from hand-authored 16x16 pixel maps.

Run with --preview to also write a nearest-neighbour material review sheet.
Pillow is the only dependency. No filtering or random noise is used in the assets.
"""

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/create_biotech/textures/block"

TISSUE = ("883e50", "a4495e", "bc5b70", "d47183", "e78b98", "f1a2aa", "f8bcb5")
CAP = ("903d4b", "a54250", "b94b55", "ca5a5f", "dc736d", "ec9380", "f4b196")
STEM = ("b87577", "ca8f88", "dda39b", "e9b9ad", "f0cabc", "f7d8c4", "ffe4ca")
GILLS = ("97545c", "ad686c", "c17e7b", "d2968d", "e4b0a0", "edc1ac", "f6d3b9")

# The digit maps are source art: one character is exactly one game pixel.
# Pillar side faces have longitudinal tissue; ends have interwoven fibers.
TEXTURES = {
    "frog_stomach_fold": (TISSUE, """
        1234543211234532
        1235543211234432
        1234654321123432
        1234554321123442
        1123445321123453
        1123454321123553
        1234543211234543
        1234432111234432
        2345432112345432
        2346532112355432
        2345532102345432
        1234432112344321
        1234532112345321
        1234543212345432
        1235543211234532
        1234543211234532
    """),
    "frog_stomach_fold_top": (TISSUE, """
        1234543212345432
        2345432123455321
        3455321234553212
        4543212345432123
        4543212345432123
        5432113454321234
        4321234543212345
        2123455321234553
        1234553212345532
        2345432123454321
        3454321234543212
        4543212345432123
        4543212345432123
        5432123454321234
        3212345432123454
        2123454321234543
    """),
    "frog_stomach_fungus_cap": (CAP, """
        3223332223344433
        3234433223455543
        2345543323456543
        2356654322344432
        2345543222333322
        2234432212332223
        3223321112232334
        4322221223323455
        5433232334434565
        5544333455433455
        4543223456542344
        4432123455432233
        3321122344321123
        3221233223211233
        2223344322223443
        2233454322234543
    """),
    "frog_stomach_fungus_stem": (STEM, """
        3445432345542344
        3445432345542344
        3455432346542344
        3454322345542344
        3454323455432344
        3444323455432344
        3444323455432344
        3444323455432344
        3445323455432344
        3445433455432344
        3445433455432344
        3445432455432344
        3445432345432344
        3445432345432344
        3445432345542344
        3445432345542344
    """),
    "frog_stomach_fungus_stem_top": (STEM, """
        3433455433444333
        4334565434544323
        4334554334554323
        4323443234543323
        4322332344433454
        4433333454334565
        5543345543334555
        6543456543233445
        5433455432333344
        4323344333445433
        3322333234555433
        3433332345654333
        4544333455543344
        4555433444333454
        3445432333233454
        3334432333344433
    """),
    "frog_stomach_fungus_gills": (GILLS, """
        1342134521342134
        1342134521342134
        1342134421342134
        2134213452134213
        2134213452134213
        2134213442134213
        2134123442134213
        2134123442134213
        1341234421342134
        1341234421342134
        1341234421342134
        1341234421342134
        1342134421342134
        1342134521342134
        1342134521342134
        1342134521342134
    """),
}

FOLD_TEXTURES = ("frog_stomach_fold", "frog_stomach_fold_top")


def render_texture(name):
    palette, source = TEXTURES[name]
    rows = source.split()
    if len(rows) != 16 or any(len(row) != 16 for row in rows):
        raise ValueError(f"{name} must contain exactly 16 rows of 16 pixels")
    colors = [tuple(bytes.fromhex(color)) + (255,) for color in palette]
    image = Image.new("RGBA", (16, 16))
    image.putdata([colors[int(pixel)] for row in rows for pixel in row])
    return image


def write_textures(names=None):
    OUT.mkdir(parents=True, exist_ok=True)
    for name in TEXTURES if names is None else names:
        path = OUT / f"{name}.png"
        render_texture(name).save(path, optimize=True)
        print(f"Wrote {path.relative_to(ROOT)}")


def draw_cube(draw, top, side, center, upper):
    """Pixel quads with fixed face shading: a material mockup, not a game render."""
    def face(texture, origin, along, down, shade):
        for y in range(16):
            for x in range(16):
                px = origin[0] + along[0] * x + down[0] * y
                py = origin[1] + along[1] * x + down[1] * y
                points = [(px, py), (px + along[0], py + along[1]),
                          (px + along[0] + down[0], py + along[1] + down[1]),
                          (px + down[0], py + down[1])]
                color = tuple(round(c * shade) for c in texture.getpixel((x, y))[:3])
                draw.polygon(points, fill=color)

    face(side, (center - 96, upper + 48), (6, 3), (0, 6), 0.84)
    face(side, (center, upper + 96), (6, -3), (0, 6), 0.68)
    face(top, (center, upper), (6, 3), (-6, 3), 1.0)


def write_preview(path):
    board = Image.new("RGB", (1200, 1060), "#23252a")
    draw = ImageDraw.Draw(board)
    font_path = Path("C:/Windows/Fonts/msyh.ttc")
    if not font_path.exists():
        font_path = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")

    def font(size):
        return ImageFont.truetype(str(font_path), size) if font_path.exists() else ImageFont.load_default()

    draw.text((32, 23), "FROG STOMACH / MATERIAL STUDY", font=font(27), fill="#f2d1c5")
    draw.text((32, 65), "4 blocks  /  6 textures  /  16 x 16 pixels", font=font(17), fill="#b3aaa8")
    materials = (
        ("01 / STOMACH FOLD", "frog_stomach_fold_top", "frog_stomach_fold"),
        ("02 / FUNGUS STEM", "frog_stomach_fungus_stem_top", "frog_stomach_fungus_stem"),
        ("03 / FUNGUS CAP", "frog_stomach_fungus_cap", "frog_stomach_fungus_cap"),
        ("04 / FUNGUS GILLS", "frog_stomach_fungus_gills", "frog_stomach_fungus_gills"),
    )
    for i, (label, top, side) in enumerate(materials):
        x = i * 294 + 12
        draw.rounded_rectangle((x, 111, x + 281, 363), radius=9, fill="#2e3035")
        draw_cube(draw, render_texture(top), render_texture(side), x + 141, 125)
        draw.text((x + 17, 331), label, font=font(16), fill="#f0dad1")

    draw.text((32, 389), "PIXEL SHEET", font=font(18), fill="#f2d1c5")
    order = [("FOLD / SIDE", "frog_stomach_fold"), ("FOLD / END", "frog_stomach_fold_top"),
             ("STEM / SIDE", "frog_stomach_fungus_stem"), ("STEM / END", "frog_stomach_fungus_stem_top"),
             ("CAP", "frog_stomach_fungus_cap"), ("GILLS", "frog_stomach_fungus_gills")]
    for i, (label, name) in enumerate(order):
        x = 32 + i * 196
        board.paste(render_texture(name).resize((160, 160), Image.Resampling.NEAREST), (x, 429))
        draw.text((x, 603), label, font=font(14), fill="#d1c8c5")

    draw.text((32, 655), "REPEAT / 3 x 3 TILES", font=font(18), fill="#f2d1c5")
    for i, (_, _, side) in enumerate(materials):
        tile = render_texture(side).resize((80, 80), Image.Resampling.NEAREST)
        x = 32 + i * 294
        for row in range(3):
            for col in range(3):
                board.paste(tile, (x + col * 80, 696 + row * 80))
    draw.text((32, 981), "Hand-authored pixels / nearest-neighbour enlargement / illustrative face lighting",
              font=font(17), fill="#a99e9b")
    path.parent.mkdir(parents=True, exist_ok=True)
    board.save(path)
    print(f"Wrote preview: {path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preview", type=Path, help="Optional output PNG material review sheet")
    args = parser.parse_args()
    write_textures()
    if args.preview:
        write_preview(args.preview)
