"""Recolor Create 6.0.10's empty schematic pixel for pixel, preserving its shades."""

from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "ref/1.21.1/Create/src/main/resources/assets/create/textures/item/empty_schematic.png"
OUTPUT = ROOT / "src/main/resources/assets/create_biotech/textures/item/greenprint.png"

# Dark to light; the middle greens match this mod's liquid living slime palette.
PALETTE = {
    (46, 34, 79): (25, 53, 28),
    (60, 65, 104): (40, 89, 47),
    (82, 96, 142): (58, 119, 51),
    (93, 117, 187): (79, 158, 68),
    (123, 149, 228): (98, 182, 74),
    (159, 176, 232): (123, 203, 98),
    (195, 210, 245): (181, 227, 158),
    (232, 239, 253): (230, 249, 218),
}


def main():
    with Image.open(SOURCE) as source:
        image = source.convert("RGBA")
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = pixels[x, y]
            if a:
                pixels[x, y] = (*PALETTE[(r, g, b)], a)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUTPUT)
    print(OUTPUT)


if __name__ == "__main__":
    main()
