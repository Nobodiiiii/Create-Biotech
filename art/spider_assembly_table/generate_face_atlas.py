from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "art" / "texture.png"
OUTPUT = (
    ROOT
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "create_biotech"
    / "textures"
    / "block"
    / "spider_assembly_table_face.png"
)

FACE_BOX = (12, 0, 20, 8)
ATLAS_SIZE = (32, 32)
SLOTS = (
    ("create:andesite_casing", (0, 0)),
    ("create:brass_casing", (8, 0)),
    ("create:copper_casing", (16, 0)),
    ("create:shadow_steel_casing", (0, 8)),
    ("create:refined_radiance_casing", (8, 8)),
    ("create:railway_casing", (16, 8)),
    ("create_biotech:asurine_casing", (0, 16)),
    ("create_biotech:biotech_casing", (8, 16)),
    ("create_biotech:explosion_proof_casing", (16, 16)),
)


def main() -> None:
    with Image.open(SOURCE) as source_image:
        source = source_image.convert("RGBA")

    face = source.crop(FACE_BOX)
    if face.size != (8, 8):
        raise ValueError(f"Expected an 8x8 source face, got {face.size}")

    atlas = Image.new("RGBA", ATLAS_SIZE, (0, 0, 0, 0))
    for _, position in SLOTS:
        atlas.paste(face, position)

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(OUTPUT)

    print(f"Wrote {OUTPUT.relative_to(ROOT)} ({atlas.width}x{atlas.height})")
    for casing, position in SLOTS:
        print(f"  {casing}: {position[0]},{position[1]}")


if __name__ == "__main__":
    main()
