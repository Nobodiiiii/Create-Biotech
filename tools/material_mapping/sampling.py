"""Pure source sampling and schema-1 PNG diagnostics; no Blockbench dependency."""
from __future__ import annotations
from typing import Mapping
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

