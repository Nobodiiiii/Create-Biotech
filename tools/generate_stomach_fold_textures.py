"""Author the 16x16 stomach-fold block textures with deterministic pixel operations."""
from pathlib import Path
import math
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/create_biotech/textures/block"
PALETTE = [
    (135, 58, 81, 255),
    (160, 72, 96, 255),
    (181, 89, 113, 255),
    (198, 107, 128, 255),
    (217, 132, 149, 255),
]

side = Image.new("RGBA", (16, 16))
end = Image.new("RGBA", (16, 16))
ridge = [1, 2, 3, 4, 3, 2, 1, 0, 1, 2, 3, 4, 3, 2, 1, 0]
for y in range(16):
    shift = round(math.sin(y * math.tau / 16))
    for x in range(16):
        shade = ridge[(x + shift) % 16]
        if (x * 7 + y * 3) % 29 == 0 and shade in (2, 3):
            shade -= 1
        side.putpixel((x, y), PALETTE[shade])
        # Open wavy tissue bands rather than concentric wood-like rings.
        across = (y + round(2 * math.sin(x * math.tau / 16))) % 8
        end.putpixel((x, y), PALETTE[[1, 2, 3, 3, 4, 3, 2, 1][across]])

OUT.mkdir(parents=True, exist_ok=True)
side.save(OUT / "frog_stomach_fold.png")
end.save(OUT / "frog_stomach_fold_top.png")
print("Wrote two 16x16 RGBA stomach-fold textures")
