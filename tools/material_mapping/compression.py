from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, order=True)
class MappedPixel:
    destination_x: int
    destination_y: int
    slot: str
    source_x: int
    source_y: int
    transform: str = "identity"


@dataclass(frozen=True)
class CompressionResult:
    regions: list[dict]
    pixels: list[dict]


@dataclass
class _Run:
    x: int
    y: int
    width: int
    height: int
    slot: str
    transform: str
    source_x: int
    source_y: int
    members: list[MappedPixel]


HORIZONTAL_DELTA = {
    "identity": (1, 0),
    "rotate_90": (0, -1),
    "rotate_180": (-1, 0),
    "rotate_270": (0, 1),
    "mirror_x": (-1, 0),
    "mirror_y": (1, 0),
}
VERTICAL_DELTA = {
    "identity": (0, 1),
    "rotate_90": (1, 0),
    "rotate_180": (0, -1),
    "rotate_270": (-1, 0),
    "mirror_x": (0, 1),
    "mirror_y": (0, -1),
}


def compress_pixels(pixels: list[MappedPixel]) -> CompressionResult:
    unique = _unique_pixels(pixels)
    horizontal = _horizontal_runs(sorted(unique.values(), key=lambda pixel: (
        pixel.destination_y, pixel.destination_x, pixel.slot,
        pixel.transform, pixel.source_y, pixel.source_x)))
    merged = _merge_vertical(horizontal)
    regions: list[dict] = []
    sparse: list[dict] = []
    for run in sorted(merged, key=lambda item: (
            item.y, item.x, item.slot, item.source_y, item.source_x, item.transform)):
        if run.width * run.height == 1:
            pixel = run.members[0]
            sparse.append({
                "source_slot": pixel.slot,
                "source": [pixel.source_x, pixel.source_y],
                "destination": [pixel.destination_x, pixel.destination_y],
            })
            continue
        source_x = min(pixel.source_x for pixel in run.members)
        source_y = min(pixel.source_y for pixel in run.members)
        source_width = max(pixel.source_x for pixel in run.members) - source_x + 1
        source_height = max(pixel.source_y for pixel in run.members) - source_y + 1
        regions.append({
            "source_slot": run.slot,
            "source": [source_x, source_y, source_width, source_height],
            "destination": [run.x, run.y, run.width, run.height],
            "transform": run.transform,
        })
    return CompressionResult(regions, sparse)


def _unique_pixels(pixels: list[MappedPixel]) -> dict[tuple[int, int], MappedPixel]:
    result: dict[tuple[int, int], MappedPixel] = {}
    for pixel in pixels:
        key = pixel.destination_x, pixel.destination_y
        previous = result.get(key)
        if previous is not None and previous != pixel:
            raise ValueError(
                f"conflicting writes at destination {pixel.destination_x},{pixel.destination_y}")
        result[key] = pixel
    return result


def _horizontal_runs(pixels: list[MappedPixel]) -> list[_Run]:
    result: list[_Run] = []
    current: _Run | None = None
    previous: MappedPixel | None = None
    for pixel in pixels:
        delta = HORIZONTAL_DELTA.get(pixel.transform)
        if delta is None:
            raise ValueError(f"unknown mapping transform {pixel.transform}")
        extends = (
            current is not None and previous is not None
            and pixel.destination_y == previous.destination_y
            and pixel.destination_x == previous.destination_x + 1
            and pixel.slot == previous.slot
            and pixel.transform == previous.transform
            and pixel.source_x == previous.source_x + delta[0]
            and pixel.source_y == previous.source_y + delta[1]
        )
        if not extends:
            current = _Run(
                pixel.destination_x, pixel.destination_y, 0, 1,
                pixel.slot, pixel.transform, pixel.source_x, pixel.source_y, [])
            result.append(current)
        current.width += 1
        current.members.append(pixel)
        previous = pixel
    return result


def _merge_vertical(runs: list[_Run]) -> list[_Run]:
    result: list[_Run] = []
    active: dict[tuple[int, int, str, str], _Run] = {}
    for run in sorted(runs, key=lambda item: (item.y, item.x, item.slot, item.transform)):
        key = run.x, run.width, run.slot, run.transform
        previous = active.get(key)
        delta = VERTICAL_DELTA[run.transform]
        extends = (
            previous is not None
            and run.y == previous.y + previous.height
            and run.source_x == previous.source_x + delta[0] * previous.height
            and run.source_y == previous.source_y + delta[1] * previous.height
        )
        if extends:
            previous.height += 1
            previous.members.extend(run.members)
        else:
            result.append(run)
            active[key] = run
    return result


"""Model-format-independent cost of mapping rectangles intersecting quad UVs."""

def geometry_cost(rectangles, regions, pixels):
    """Count fixed exported affine-grid runs clipped independently to each face.

    Mirrors UvPlanCompiler's horizontal affine runs / equal-width vertical merge,
    not a globally optimal face partition. Sparse pixels have identity edge UVs.
    """
    from .sampling import inverse_transform

    mapped, selections = [], []
    for region in regions:
        sx, sy, sw, sh = region["source"]
        x, y, width, height = region["destination"]
        transform = region.get("transform", "identity")
        selections.append((x, y, width, height))
        for dy in range(height):
            for dx in range(width):
                px, py = inverse_transform(transform, dx, dy, sw, sh)
                mapped.append(MappedPixel(x + dx, y + dy, region["source_slot"], sx + px, sy + py, transform))
    for pixel in pixels:
        x, y = pixel["destination"]
        sx, sy = pixel["source"]
        selections.append((x, y, 1, 1))
        mapped.append(MappedPixel(x, y, pixel["source_slot"], sx, sy))
    counts, raw = [], 0
    for x, y, width, height in rectangles:
        raw += sum(x < sx + sw and sx < x + width and y < sy + sh and sy < y + height
                   for sx, sy, sw, sh in selections)
        clipped = [p for p in mapped if x <= p.destination_x < x + width and y <= p.destination_y < y + height]
        merged = compress_pixels(clipped)
        counts.append(len(merged.regions) + len(merged.pixels))
    return {
        "scope": "fixed_exported_mapping_affine_grid",
        "original_faces": len(rectangles), "output_fragments": sum(counts),
        "max_pieces_per_face": max(counts, default=0),
        "additional_quads": sum(max(0, count - 1) for count in counts),
        "raw_mapping_intersections": raw,
    }


