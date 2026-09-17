"""Pure source sampling and schema-1 PNG diagnostics; no Blockbench dependency."""
from __future__ import annotations
import re
from typing import Mapping
from collections.abc import Sequence
from PIL import Image
from .compression import MappedPixel

def render_pixels(
        pixels: list[MappedPixel], size: tuple[int, int],
        sources: Mapping[str, Image.Image], grids: Mapping[str, tuple[int, int]]) -> Image.Image:
    output = Image.new("RGBA", size, (0, 0, 0, 0))
    written: dict[tuple[int, int], tuple[int, int, int, int]] = {}
    for pixel in pixels:
        source = sources[pixel.slot]
        grid = grids[pixel.slot]
        scale = _integer_scale(source.size, grid, pixel.slot)
        # Match Java SourceSlotImage.sample: nearest-neighbour at the logical cell center.
        offset = scale // 2
        color = source.getpixel((pixel.source_x * scale + offset, pixel.source_y * scale + offset))
        destination = pixel.destination_x, pixel.destination_y
        previous = written.get(destination)
        if previous is not None and previous != color:
            raise ValueError(f"conflicting preview colors at {destination[0]},{destination[1]}")
        written[destination] = color
        output.putpixel(destination, color)
    return output



def render_definition(
        definition: dict, sources: Mapping[str, Image.Image]) -> Image.Image:
    grids = {slot: tuple(grid) for slot, grid in definition["source_grids"].items()}
    mapped: list[MappedPixel] = []
    for region in definition.get("regions", []):
        sx, sy, sw, sh = region["source"]
        dx, dy, dw, dh = region["destination"]
        transform = region.get("transform", "identity")
        for local_y in range(dh):
            for local_x in range(dw):
                source_local_x, source_local_y = inverse_transform(
                    transform, local_x, local_y, sw, sh)
                mapped.append(MappedPixel(
                    dx + local_x, dy + local_y, region["source_slot"],
                    sx + source_local_x, sy + source_local_y, transform))
    for pixel in definition.get("pixels", []):
        mapped.append(MappedPixel(
            pixel["destination"][0], pixel["destination"][1], pixel["source_slot"],
            pixel["source"][0], pixel["source"][1], "identity"))
    return render_pixels(mapped, tuple(definition["size"]), sources, grids)



def inverse_transform(
        transform: str, x: int, y: int, source_width: int, source_height: int) -> tuple[int, int]:
    if transform == "identity":
        return x, y
    if transform == "rotate_90":
        return y, source_height - 1 - x
    if transform == "rotate_180":
        return source_width - 1 - x, source_height - 1 - y
    if transform == "rotate_270":
        return source_width - 1 - y, x
    if transform == "mirror_x":
        return source_width - 1 - x, y
    if transform == "mirror_y":
        return x, source_height - 1 - y
    raise ValueError(f"unknown mapping transform {transform}")



def _integer_scale(actual: tuple[int, int], grid: tuple[int, int], slot: str) -> int:
    if grid[0] <= 0 or grid[1] <= 0 or actual[0] % grid[0] or actual[1] % grid[1]:
        raise ValueError(f"source {slot} size {actual[0]}x{actual[1]} is not an integer grid scale")
    scale_x, scale_y = actual[0] // grid[0], actual[1] // grid[1]
    if scale_x != scale_y or scale_x <= 0:
        raise ValueError(f"source {slot} has a non-uniform grid scale")
    return scale_x



"""Runtime schema limits, independent of any model editor or source image size."""
MAX_TARGET_DIMENSION = 256


def validate_target_size(size: tuple[int, int]) -> None:
    width, height = size
    if not (1 <= width <= MAX_TARGET_DIMENSION and 1 <= height <= MAX_TARGET_DIMENSION):
        raise ValueError(
            f"target resolution {width}x{height} must be within 1..{MAX_TARGET_DIMENSION} on each axis")



def _material_texture_candidates(texture_id: str, material: str) -> list[str]:
    namespace, path = texture_id.split(":", 1) if ":" in texture_id else ("minecraft", texture_id)
    material_namespace, material_path = material.split(":", 1) if ":" in material else ("minecraft", material)
    folder = path.rsplit("/", 1)[0] + "/" if "/" in path else ""
    filename = path[len(folder):]
    prefix = next((role for role in ("eye_", "body_") if filename.startswith(role)), None)
    if prefix is None:
        return []
    material_folder, separator, material_name = material_path.rpartition("/")
    variant = material_folder + separator + prefix + material_name
    return [f"{namespace}:{folder}{material_namespace}/{variant}", f"{namespace}:{folder}{variant}"]


def render_dedicated_body(size: tuple[int, int], folder: str | None,
                          textures: Mapping[str, Image.Image], material: str | None) -> Image.Image | None:
    """A valid casing-specific body replaces UV output; absent/invalid bodies leave UV generation enabled."""
    if folder is None:
        return None
    if not isinstance(folder, str) or not folder:
        raise ValueError("body_textures must be a resource directory id")
    namespace, path = folder.split(":", 1) if ":" in folder else ("minecraft", folder)
    namespace = namespace or "minecraft"
    path = path.rstrip("/")
    if not re.fullmatch(r"[a-z0-9_.-]+", namespace) or not re.fullmatch(r"[a-z0-9/._-]+", path):
        raise ValueError("body_textures must be a non-empty resource directory id")
    if material is None:
        return None
    for candidate in _material_texture_candidates(f"{namespace}:{path}/body_", material):
        if candidate not in textures:
            continue
        output = Image.new("RGBA", size)
        try:
            _apply_texture_layer(output, {"grid": list(size)}, textures[candidate].convert("RGBA"))
        except ValueError:
            continue
        return output
    return None


def apply_texture_layers(
        base: Image.Image,
        layers: Sequence[dict],
        textures: Mapping[str, Image.Image],
        *, material: str | None = None, material_index: int | None = None,
) -> Image.Image:
    """Apply texture layers by ascending index, preserving declaration ties."""
    if material_index is not None and (type(material_index) is not int or material_index < 0):
        raise ValueError("material_index must be a non-negative integer")
    output = base.convert("RGBA").copy()
    for layer in layers:
        if not isinstance(layer, dict) or type(layer.get("index")) is not int or not -(2**31) <= layer["index"] < 2**31:
            raise ValueError("layer index must be an exact 32-bit integer")
        if ("texture" in layer) == ("model" in layer):
            raise ValueError("layer requires exactly one of texture or model")
    ordered = sorted(enumerate(layers), key=lambda item: (item[1]["index"], item[0]))
    for _, layer in ordered:
        texture_id = layer.get("texture")
        if texture_id is None:  # A flat PNG cannot represent an attached model.
            continue
        variants = layer.get("material_variants", False)
        if type(variants) is not bool:
            raise ValueError("material_variants must be boolean")
        if variants and material:
            namespace, path = texture_id.split(":", 1) if ":" in texture_id else ("minecraft", texture_id)
            folder = path.rsplit("/", 1)[0] + "/" if "/" in path else ""
            filename = path[len(folder):]
            prefix = next((role for role in ("eye_", "body_") if filename.startswith(role)), None)
            candidates = _material_texture_candidates(texture_id, material)
            grid = _pair(layer.get("grid", output.size), "grid")
            for candidate in candidates:
                if candidate not in textures:
                    continue
                try:
                    _integer_scale(textures[candidate].size, grid, candidate)
                except ValueError:
                    continue
                texture_id = candidate
                break
            else:
                # Only eyes cycle. Body appearances are produced by the UV mapping, not a generic pool.
                if prefix == "eye_":
                    pool = []
                    for candidate, image in textures.items():
                        candidate_ns, candidate_path = candidate.split(":", 1) if ":" in candidate else ("minecraft", candidate)
                        start = folder + "eye_"
                        digits = candidate_path[len(start):]
                        if candidate_ns != namespace or not candidate_path.startswith(start) or not digits or any(c not in "0123456789" for c in digits):
                            continue
                        try:
                            _apply_texture_layer(output.copy(), layer, image.convert("RGBA"))
                        except ValueError:
                            continue
                        pool.append((int(digits), candidate_path, candidate))
                    pool.sort()
                    if pool:
                        if material_index is None and len(pool) > 1:
                            raise ValueError("multiple eye candidates require --material-index from the synchronized material palette")
                        texture_id = pool[(material_index or 0) % len(pool)][2]

        if texture_id not in textures:
            raise ValueError(f"missing layer texture: {texture_id}")
        _apply_texture_layer(output, layer, textures[texture_id].convert("RGBA"))
    return output


def _apply_texture_layer(output: Image.Image, layer: dict, texture: Image.Image) -> None:
    grid = _pair(layer.get("grid", output.size), "grid")
    if texture.width % grid[0] or texture.height % grid[1]:
        raise ValueError("layer texture dimensions must be an integer scale of grid")
    scale_x, scale_y = texture.width // grid[0], texture.height // grid[1]
    if scale_x != scale_y or scale_x <= 0:
        raise ValueError("layer texture dimensions must use a uniform integer scale")
    logical = Image.new("RGBA", grid)
    for y in range(grid[1]):
        for x in range(grid[0]):
            cell = texture.crop((x * scale_x, y * scale_x, (x + 1) * scale_x, (y + 1) * scale_x))
            pixels = [cell.getpixel((cx, cy)) for cy in range(cell.height) for cx in range(cell.width)]
            if any(pixel[3] != pixels[0][3] for pixel in pixels[1:]):
                raise ValueError("HD layer alpha must be uniform within its logical cell")
            if pixels[0][3] not in (0, 255):
                raise ValueError("layer texture requires cutout alpha (0 or 255)")
            logical.putpixel((x, y), cell.getpixel((scale_x // 2, scale_y // 2)))

    source = _rect(layer.get("source", [0, 0, *grid]), "source")
    destination = _rect(layer.get("destination", [0, 0, *output.size]), "destination")
    _inside(source, grid, "source")
    _inside(destination, output.size, "destination")
    sx, sy, sw, sh = source
    dx, dy, dw, dh = destination
    for y in range(dh):
        for x in range(dw):
            first_x, last_x = x * sw // dw, ((x + 1) * sw + dw - 1) // dw
            first_y, last_y = y * sh // dh, ((y + 1) * sh + dh - 1) // dh
            alpha = logical.getpixel((sx + first_x, sy + first_y))[3]
            if any(logical.getpixel((sx + px, sy + py))[3] != alpha
                   for py in range(first_y, last_y) for px in range(first_x, last_x)):
                raise ValueError("source alpha must agree within each destination texel")
            if alpha:
                px = (2 * sx * dw + (2 * x + 1) * sw) * scale_x // (2 * dw)
                py = (2 * sy * dh + (2 * y + 1) * sh) * scale_y // (2 * dh)
                output.putpixel((dx + x, dy + y), texture.getpixel((px, py)))



def _pair(value, name: str) -> tuple[int, int]:
    if not isinstance(value, (list, tuple)) or len(value) != 2 or any(type(item) is not int for item in value):
        raise ValueError(f"{name} must contain two integers")
    result = tuple(value)
    if min(result) <= 0:
        raise ValueError(f"{name} dimensions must be positive")
    return result


def _rect(value, name: str) -> tuple[int, int, int, int]:
    if not isinstance(value, (list, tuple)) or len(value) != 4 or any(type(item) is not int for item in value):
        raise ValueError(f"{name} must contain four integers")
    result = tuple(value)
    if result[0] < 0 or result[1] < 0 or result[2] <= 0 or result[3] <= 0:
        raise ValueError(f"{name} must have non-negative origin and positive size")
    return result


def _inside(rect, size, name: str) -> None:
    if rect[0] + rect[2] > size[0] or rect[1] + rect[3] > size[1]:
        raise ValueError(f"{name} rectangle exceeds bounds")
