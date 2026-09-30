"""Render the production joint meshes offline; requires JDK 21, Pillow and NumPy.

Run after gradlew compileJava processResources. No game, window or graphics context is started.
"""
from pathlib import Path
import json
import os
import shutil
import subprocess

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/joint-icon-preview"
NAMES = [("neck", "颈关节"), ("shoulder", "肩关节"), ("elbow", "肘关节"), ("hip", "髋关节"), ("knee", "膝关节")]


def export():
    OUT.mkdir(parents=True, exist_ok=True)
    javac = Path(shutil.which("javac") or "javac")
    java = javac.with_name("java.exe" if os.name == "nt" else "java")
    version = dict(line.split("=", 1) for line in (ROOT / "gradle.properties").read_text().splitlines() if "=" in line)["neo_version"]
    libraries = (ROOT / "build/moddev/clientLegacyClasspath.txt").read_text().splitlines()
    paths = [ROOT / "build/classes/java/main", ROOT / f"build/moddev/artifacts/neoforge-{version}-merged.jar", *map(Path, libraries)]
    classpath = os.pathsep.join(p.as_posix() for p in paths)

    def run(executable, args, filename):
        argfile = OUT / filename
        argfile.write_text("\n".join('"' + str(arg).replace("\\", "/") + '"' for arg in args), encoding="utf-8")
        subprocess.run([str(executable), "@" + str(argfile)], cwd=ROOT, check=True)

    run(javac, ["-proc:none", "-encoding", "UTF-8", "-cp", classpath, "-d", OUT, Path(__file__).with_name("ExportJointIcons.java")], "javac.args")
    run(java, ["-Djava.awt.headless=true", "-cp", OUT.as_posix() + os.pathsep + classpath,
               "ExportJointIcons", ROOT, OUT / "meshes.json"], "java.args")


def raster(vertices, texture, lights, size):
    """Orthographic quad triangles, nearest skin sampling, depth buffer, vanilla diffuse lighting."""
    vertices = np.array(vertices, dtype=float)
    vertices[:, :2] *= size / 16
    canvas = np.zeros((size, size, 4), dtype=np.uint8)
    depth = np.full((size, size), -np.inf)
    for start in range(0, len(vertices), 4):
        quad = vertices[start:start + 4]
        normal = quad[0, 5:8]
        normal /= np.linalg.norm(normal)
        shade = min(1.0, 0.4 + 0.6 * np.maximum(0, lights @ normal).sum())
        for indices in [(0, 1, 2), (0, 2, 3)]:
            triangle = quad[list(indices)]
            a, b, c = triangle[:, :2]
            xmin, ymin = np.maximum(0, np.floor(triangle[:, :2].min(axis=0)).astype(int))
            xmax, ymax = np.minimum(size - 1, np.ceil(triangle[:, :2].max(axis=0)).astype(int))
            if xmax < xmin or ymax < ymin:
                continue
            yy, xx = np.mgrid[ymin:ymax + 1, xmin:xmax + 1].astype(float)
            xx += 0.5
            yy += 0.5
            divisor = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(divisor) < 1e-9:
                continue
            wa = ((b[1] - c[1]) * (xx - c[0]) + (c[0] - b[0]) * (yy - c[1])) / divisor
            wb = ((c[1] - a[1]) * (xx - c[0]) + (a[0] - c[0]) * (yy - c[1])) / divisor
            wc = 1 - wa - wb
            attributes = wa[..., None] * triangle[0] + wb[..., None] * triangle[1] + wc[..., None] * triangle[2]
            uv = np.floor(attributes[..., 3:5] * [texture.shape[1], texture.shape[0]]).astype(int)
            sampled = texture[uv[..., 1].clip(0, texture.shape[0] - 1), uv[..., 0].clip(0, texture.shape[1] - 1)].copy()
            sampled[..., :3] = np.clip(sampled[..., :3] * shade, 0, 255).astype(np.uint8)
            current = depth[ymin:ymax + 1, xmin:xmax + 1]
            visible = (wa >= -1e-7) & (wb >= -1e-7) & (wc >= -1e-7) & (attributes[..., 2] >= current) & (sampled[..., 3] >= 26)
            current[visible] = attributes[..., 2][visible]
            canvas[ymin:ymax + 1, xmin:xmax + 1][visible] = sampled[visible]
    return Image.fromarray(canvas)


def compose():
    data = json.loads((OUT / "meshes.json").read_text())
    texture = np.array(Image.open(OUT / "steve.png").convert("RGBA"))
    lights = np.array(data["lights"])
    font_path = Path("C:/Windows/Fonts/msyh.ttc")
    def font(size):
        return ImageFont.truetype(str(font_path), size) if font_path.exists() else ImageFont.load_default(size=size)

    sheet = Image.new("RGB", (1140, 722), "#20252b")
    draw = ImageDraw.Draw(sheet)
    draw.text((32, 20), "史蒂夫 · 五种关节特写", font=font(26), fill="#f0ece1")
    draw.text((32, 62), "运行时几何 / 原版皮肤 UV / 共用物品显示变换 / 离线软件光栅预览", font=font(16), fill="#aab8bc")
    report = {}
    for i, (name, label) in enumerate(NAMES):
        x = 30 + i * 222
        draw.rounded_rectangle((x, 110, x + 204, 690), radius=8, fill="#30373f")
        draw.text((x + 20, 124), label, font=font(22), fill="#f0ece1")
        draw.rectangle((x + 14, 168, x + 189, 343), fill="#b6b6b6")
        preview = raster(data[name], texture, lights, 176)
        sheet.paste(preview, (x + 14, 168), preview)
        preview.save(OUT / f"{name}_joint.png")
        for j, size in enumerate([16, 32, 48]):
            icon = raster(data[name], texture, lights, size)
            icon.save(OUT / f"{name}_joint_{size}.png")
            enlarged = icon.resize((96, 96), Image.Resampling.NEAREST)
            yy = 365 + j * 104
            draw.rectangle((x + 14, yy, x + 113, yy + 99), fill="#8b8b8b")
            sheet.paste(enlarged, (x + 16, yy + 2), enlarged)
            draw.text((x + 126, yy + 24), f"{size} px", font=font(17), fill="#d2dbdd")
            draw.text((x + 126, yy + 48), f"GUI ×{size // 16}", font=font(14), fill="#aab8bc")
        v = np.array(data[name])
        lo, hi = v[:, :2].min(axis=0), v[:, :2].max(axis=0)
        report[name] = {"bounds_gui_pixels": [lo.tolist(), hi.tolist()], "center": ((lo + hi) / 2).tolist(), "vertices": len(v)}
    sheet.save(OUT / "joint-icons-contact-sheet.png")
    (OUT / "framing.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))
    print(OUT / "joint-icons-contact-sheet.png")
    compose_wheel(data, texture, lights, font)


def compose_wheel(data, texture, lights, font):
    chain = Image.open(OUT / "chain.png").convert("RGBA")
    widgets = Image.open(ROOT / "ref/1.21.1/Create/src/main/resources/assets/create/textures/gui/widgets.png").convert("RGBA")
    slot = widgets.crop((0, 68, 22, 90))
    highlight = widgets.crop((27, 67, 51, 91))

    def item(name, size):
        icon = chain.resize((size, size), Image.Resampling.NEAREST)
        mesh = np.array(data[name], dtype=float)
        mesh[:, :3] *= data["badge"]["scale"]
        mesh[:, :2] += data["badge"]["offset"]
        mesh[:, 2] += data["badge"]["depth"]
        icon.alpha_composite(raster(mesh, texture, lights, size))
        icon.save(OUT / f"selected_{name}_{size}.png")
        return icon

    wheel = Image.new("RGBA", (600, 522), "#292e36")
    cx, cy = 100, 79
    # Each slot is 22 GUI pixels with an unscaled 16-pixel item, exactly as in the shared screen.
    # Render at GUI scale 3; GUI slot textures are behind the item geometry in the depth buffer.
    for i, (name, _) in enumerate(NAMES):
        location = data["wheel"]["slots"][i]
        x, y = location["x"] + cx, location["y"] + cy
        offset = data["wheel"]["iconOffset"]
        background = slot.resize((66, 66), Image.Resampling.NEAREST)
        wheel.paste(background, (x * 3, y * 3), background)
        if name == "knee":
            wheel.alpha_composite(highlight.resize((72, 72), Image.Resampling.NEAREST), ((x - 1) * 3, (y - 1) * 3))
        wheel.alpha_composite(raster(data[name], texture, lights, 48), ((x + offset) * 3, (y + offset) * 3))
    wheel.alpha_composite(slot.resize((66, 66), Image.Resampling.NEAREST), ((cx - 11) * 3, (cy - 11) * 3))
    wheel.alpha_composite(item("knee", 48), ((cx - 8) * 3, (cy - 8) * 3))
    wheel_draw = ImageDraw.Draw(wheel)
    wheel_draw.text((300, 476), "膝关节", font=font(24), fill="#ffaa00", anchor="mm")

    sheet = Image.new("RGB", (1140, 710), "#20252b")
    draw = ImageDraw.Draw(sheet)
    draw.text((28, 22), "关节轮盘 · 锁链与选中角标", font=font(26), fill="#f0ece1")
    draw.text((28, 66), "离线预览 · 运行时几何、轮盘布局与角标参数 · 原版锁链和 Create 槽位纹理", font=font(16), fill="#aab8bc")
    draw.text((28, 109), "轮盘中的特写：16×16 GUI 像素（GUI ×3）", font=font(18), fill="#d2dbdd")
    sheet.paste(wheel, (24, 154), wheel)
    draw.text((655, 109), "选择后的物品栏 / JEI 图标", font=font(18), fill="#d2dbdd")
    for row, size in enumerate([16, 32, 48]):
        y = 165 + row * 170
        draw.text((655, y), f"GUI ×{size // 16}  ·  {size} px", font=font(16), fill="#aab8bc")
        for i, (name, label) in enumerate(NAMES):
            x = 655 + i * 94
            draw.rectangle((x, y + 35, x + 83, y + 118), fill="#8b8b8b")
            icon = item(name, size)
            factor = 4 if size == 16 else 2 if size == 32 else 1
            icon = icon.resize((size * factor, size * factor), Image.Resampling.NEAREST)
            margin = (84 - icon.width) // 2
            sheet.paste(icon, (x + margin, y + 35 + margin), icon)
            draw.text((x + 42, y + 136), label, font=font(14), fill="#d2dbdd", anchor="mm")
    sheet.save(OUT / "joint-wheel-preview.png")
    print("Wheel geometry:", data["wheel"])
    print(OUT / "joint-wheel-preview.png")


if __name__ == "__main__":
    export()
    compose()
