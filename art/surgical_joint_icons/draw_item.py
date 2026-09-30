"""Author the 16px mechanical joint sprite with exact, unfiltered pixels."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
TEXTURE = ROOT / "src/main/resources/assets/create_biotech/textures/item/surgical_joint.png"


def draw_item():
    icon = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(icon)
    # Steel clevis ends; a bent silhouette leaves the badge quadrant clear.
    d.polygon([(8, 4), (11, 1), (13, 1), (14, 2), (14, 4), (10, 8)], fill="#303a3b")
    d.polygon([(9, 4), (11, 2), (13, 2), (13, 4), (10, 7)], fill="#76847f")
    d.line([(9, 4), (11, 2), (12, 2)], fill="#d4d8bd", width=1)
    d.line([(11, 5), (13, 3)], fill="#a4afa0", width=1)
    d.point((12, 3), fill="#394545")
    d.polygon([(3, 8), (7, 9), (6, 12), (4, 14), (2, 14), (1, 13), (1, 11)], fill="#303a3b")
    d.polygon([(3, 9), (6, 9), (5, 12), (3, 13), (2, 12), (2, 11)], fill="#76847f")
    d.line([(3, 9), (2, 11), (2, 12)], fill="#d4d8bd", width=1)
    d.line([(5, 10), (4, 12), (3, 13)], fill="#a4afa0", width=1)
    d.point((3, 12), fill="#394545")
    # Offset rear bearing plate gives the hinge thickness and a warm shadow.
    d.polygon([(4, 3), (8, 3), (11, 6), (11, 8), (8, 11), (5, 11), (2, 8), (2, 5)], fill="#493a2e")
    d.polygon([(4, 4), (8, 4), (10, 6), (10, 8), (8, 10), (5, 10), (3, 8), (3, 5)], fill="#8b5537")
    # Front brass race. Stair-stepped contour is intentionally hand-pixelled.
    rows = [
        "...ooo...",
        "..ohhho..",
        ".ohgggso.",
        "ohg...gso",
        "ohg...gso",
        "ogg...sbo",
        ".osgssbo.",
        "..osbbo..",
        "...ooo...",
    ]
    palette = {"o": "#715035", "h": "#fff0b1", "g": "#dfb663", "s": "#bd8948", "b": "#98603d"}
    for y, row in enumerate(rows, 2):
        for x, pixel in enumerate(row, 2):
            if pixel != ".":
                d.point((x, y), fill=palette[pixel])
    # Recessed steel axle cap, lit from the same upper-left as Create items.
    d.rectangle((5, 5, 7, 7), fill="#394545")
    d.line([(5, 6), (5, 5), (6, 5)], fill="#a4afa0")
    d.point((5, 5), fill="#e3e5ca")
    d.point((6, 6), fill="#76847f")
    icon.save(TEXTURE)
    return icon


def preview(icon):
    sheet = Image.new("RGB", (760, 360), "#20252b")
    d = ImageDraw.Draw(sheet)
    font = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 21)
    small = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 15)
    d.text((24, 18), "机械关节 · 黄铜轴承与金属连杆", font=font, fill="#f0ece1")
    d.text((24, 53), "原始 16×16 像素贴图 / 最近邻显示 / 未选择类型", font=small, fill="#aab8bc")
    for x, bg in [(24, "#8b8b8b"), (224, "#292e36")]:
        d.rectangle((x, 99, x + 175, 274), fill=bg)
        enlarged = icon.resize((160, 160), Image.Resampling.NEAREST)
        sheet.paste(enlarged, (x + 8, 107), enlarged)
    for i, scale in enumerate([1, 2, 3, 4]):
        x = 435 + i * 76
        size = 16 * scale
        d.rectangle((x, 137, x + 69, 206), fill="#8b8b8b")
        im = icon.resize((size, size), Image.Resampling.NEAREST)
        sheet.paste(im, (x + (70-size)//2, 137+(70-size)//2), im)
        d.text((x + 9, 224), f"GUI ×{scale}", font=small, fill="#d2dbdd")
    d.text((24, 308), "右下留白供选中关节特写显示；轴承和两端连接孔保持可辨。", font=small, fill="#aab8bc")
    sheet.save(Path(__file__).with_name("item-preview.png"))


if __name__ == "__main__":
    preview(draw_item())
