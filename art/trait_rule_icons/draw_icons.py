"""Author 18px JEI rule sprites from exact pixel masks; requires Pillow."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
TEXTURES = ROOT / "src/main/resources/assets/create_biotech/textures/gui/sprites/jei/trait_rule"
SIZE = 18
PALETTE = {".": (0, 0, 0, 0), "#": (0, 0, 0, 255), "W": (255, 255, 255, 255)}

# Presence adapts Nikoichu's CC0 checkbox, preserving its original pixels
# centered without filtering or scaling.
MASKS = {
    "presence": [
        "..................",
        "..................",
        "..##############..",
        "..#WWWWWWWW###W#..",
        "..#W########WW##..",
        "..#W####.##WW##...",
        "..#W#WW###WW###...",
        "..#W##WW#WW##W#...",
        "..#W###WWWW##W#...",
        "..#W#.#WWW###W#...",
        "..#W#.##WW#.#W#...",
        "..#W#..#W##.#W#...",
        "..#W#########W#...",
        "..#WWWWWWWWWWW#...",
        "..#############...",
        "..................",
        "..................",
        "..................",
    ],
    "special": [
        "..................",
        "......#...........",
        ".....#W#.....#....",
        ".....#W#....#W#...",
        "....#WWW#....#....",
        "..##WWWWW##.......",
        ".#WWWWWWWWW#......",
        "..##WWWWW##.......",
        "....#WWW#.........",
        ".....#W#..........",
        ".....#W#.....#....",
        "......#.....#W#...",
        "...........#WWW#..",
        "............#W#...",
        ".............#....",
        "..................",
        "..................",
        "..................",
    ],
}


def from_mask(rows):
    if len(rows) != SIZE or any(len(row) != SIZE for row in rows):
        raise ValueError("Every sprite mask must be exactly 18 by 18 pixels")
    image = Image.new("RGBA", (SIZE, SIZE))
    image.putdata([PALETTE[pixel] for row in rows for pixel in row])
    return image


def threshold():
    """A solid fill bar reaching a protruding threshold; no battery cap."""
    image = Image.new("RGBA", (SIZE, SIZE))
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 6, 16, 12), fill=PALETTE["#"])
    draw.rectangle((2, 7, 15, 11), outline=PALETTE["W"])
    draw.rectangle((3, 8, 9, 10), fill=PALETTE["W"])
    # Outline separates the marker from the fill; its extension reads at GUI scale 1.
    draw.rectangle((9, 2, 11, 15), fill=PALETTE["#"])
    draw.line((10, 3, 10, 14), fill=PALETTE["W"])
    return image


def scaled():
    """A filled pie with a missing upper-right quarter, drawn at native pixels."""
    image = Image.new("RGBA", (SIZE, SIZE))
    draw = ImageDraw.Draw(image)
    draw.pieslice((2, 2, 15, 15), 0, 270, fill=PALETTE["W"], outline=PALETTE["#"])
    return image


def preview(icons):
    sheet = Image.new("RGB", (986, 400), "#20252b")
    draw = ImageDraw.Draw(sheet)
    font_path = Path("C:/Windows/Fonts/msyh.ttc")

    def font(size):
        return ImageFont.truetype(str(font_path), size) if font_path.exists() else ImageFont.load_default(size=size)

    def centered(text, x, y, size, color):
        width = draw.textbbox((0, 0), text, font=font(size))[2]
        draw.text((x - width // 2, y), text, font=font(size), fill=color)

    draw.text((22, 15), "仿生特性 · 规则图标", font=font(24), fill="#f1eee5")
    draw.text((22, 53), "18×18 透明底 · 黑白像素 · 上方放大 6 倍，下方为 GUI 实际整数缩放", font=font(14), fill="#abb6bf")
    labels = [
        ("presence", "存在型", "有载体即满足"),
        ("threshold", "门槛型", "填充达到阈值"),
        ("scaled", "比例型", "占比越高，效果越强"),
        ("special", "特殊型", "性情与智力独立判定"),
    ]
    for index, (key, title, detail) in enumerate(labels):
        x = 18 + index * 242
        center = x + 112
        draw.rounded_rectangle((x, 88, x + 223, 382), radius=8, fill="#2d333a", outline="#414a53")
        centered(title, center, 100, 20, "#f1eee5")
        icon = icons[key]
        enlarged = icon.resize((108, 108), Image.Resampling.NEAREST)
        sheet.paste(enlarged, (center - 54, 135), enlarged)
        centered(detail, center, 252, 15, "#c6cdd0")
        draw.rectangle((x + 12, 286, x + 211, 352), fill="#8b8b8b")
        for scale in (1, 2, 3):
            px = x + 46 + (scale - 1) * 66
            side = SIZE * scale
            image = icon.resize((side, side), Image.Resampling.NEAREST)
            sheet.paste(image, (px - side // 2, 319 - side // 2), image)
            centered(f"×{scale}", px, 358, 13, "#abb6bf")
    sheet.save(HERE / "preview.png")


if __name__ == "__main__":
    sprites = {name: from_mask(mask) for name, mask in MASKS.items()}
    sprites["threshold"] = threshold()
    sprites["scaled"] = scaled()
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for name, image in sprites.items():
        image.save(TEXTURES / f"{name}.png", optimize=True)
    preview(sprites)
