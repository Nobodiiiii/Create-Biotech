"""Author the 16x16 stomach-fungus gill texture with deterministic pixel operations."""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/create_biotech/textures/block/frog_stomach_fungus_gills.png"
PALETTE = [
    (132, 104, 77, 255),
    (165, 139, 103, 255),
    (195, 171, 130, 255),
    (222, 204, 163, 255),
]

image = Image.new("RGBA", (16, 16))
for y in range(16):
    for x in range(16):
        stripe = min(3, 1 + ((x + 1) % 4 != 0) + ((x * 5 + y * 3) % 31 == 0))
        shade = max(0, stripe - (y in (7, 15)))
        image.putpixel((x, y), PALETTE[shade])

OUT.parent.mkdir(parents=True, exist_ok=True)
image.save(OUT)
print(f"Wrote {OUT.relative_to(ROOT)} as a 16x16 RGBA texture")
