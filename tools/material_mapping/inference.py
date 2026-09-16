"""Rectangle-first UV inference, exact by default with opt-in RGB tolerance."""
from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from typing import Mapping, Sequence

from PIL import Image

from .compression import HORIZONTAL_DELTA, VERTICAL_DELTA, MappedPixel
from .sampling import validate_target_size
from .sampling import _integer_scale, inverse_transform
from .rgb_matching import RgbMatcher, RgbPolicy


ZERO = (0, 0, 0, 0)
TRANSPOSE = {
    "identity": None,
    "rotate_90": Image.Transpose.ROTATE_270,
    "rotate_180": Image.Transpose.ROTATE_180,
    "rotate_270": Image.Transpose.ROTATE_90,
    "mirror_x": Image.Transpose.FLIP_LEFT_RIGHT,
    "mirror_y": Image.Transpose.FLIP_TOP_BOTTOM,
}


@dataclass(frozen=True)
class SourceCandidate:
    slot: str
    source: tuple[int, int, int, int]
    transform: str


@dataclass(frozen=True)
class InferenceIssue:
    destination: tuple[int, int, int, int]
    reason: str
    candidates: tuple[SourceCandidate, ...] = ()
    search_truncated: bool = False
    resolution_blocker: str | None = None


@dataclass
class InferenceResult:
    pixels: list[MappedPixel]
    issues: list[InferenceIssue]
    background_pixels: int
    unused_pixels: int = 0


def infer_mapping(
        sample: Image.Image, sources: Mapping[str, Image.Image],
        grids: Mapping[str, tuple[int, int]], *, candidate_limit: int = 16,
        face_rectangles: Sequence[tuple[int, int, int, int]] | None = None,
        rgb_rmse: float = 0.0, rgb_max_error: float = 0.0,
        rgb_ambiguity_margin: float = 1.0, collect_all_candidates: bool = False) -> InferenceResult:
    """Match whole rectangles first; bisect misses, but never guess among matches.

    Uniqueness is relative to each tested rectangle, not proof of the author's UV
    intent. Fully zero RGBA pixels are background; hidden RGB remains significant.
    Default candidates contain one extra entry for honest truncated diagnostics.
    collect_all_candidates retains every eligible candidate for joint resolution;
    candidate_limit then controls neither search nor model reconciliation.
    """
    validate_target_size(sample.size)
    policy = RgbPolicy(rgb_rmse, rgb_max_error, rgb_ambiguity_margin)
    if not sources or set(sources) != set(grids):
        raise ValueError("provide a source and explicit source grid for every slot")
    if not isinstance(candidate_limit, int) or candidate_limit < 1:
        raise ValueError("--max-candidates must be a positive integer")
    sample = sample.convert("RGBA")
    colors = set(_rgba_pixels(sample)) - {ZERO}
    if not colors:
        raise ValueError("empty sample: all pixels have zero RGBA")
    logical = {}
    index = defaultdict(list)
    for slot in sorted(sources):
        grid = grids[slot]
        if len(grid) != 2 or any(type(n) is not int or n <= 0 for n in grid):
            raise ValueError(f"source {slot} grid must have two positive integers")
        source = sources[slot].convert("RGBA")
        scale = _integer_scale(source.size, grid, slot)
        if scale != 1:
            normalized = Image.new("RGBA", grid)
            normalized.putdata([
                source.getpixel((x * scale + scale // 2, y * scale + scale // 2))
                for y in range(grid[1]) for x in range(grid[0])])
            source = normalized
        logical[slot] = source
        for position, color in enumerate(_rgba_pixels(source)):
            if color in colors:
                index[color].append((slot, position % grid[0], position // grid[0]))

    matcher = RgbMatcher(logical, colors, policy, candidate_limit,
                         collect_all_candidates=collect_all_candidates) if policy.enabled else None
    cache = {}

    def candidates(patch):
        if matcher is not None:
            return matcher.candidates(patch)
        width, height = patch.size
        data = patch.tobytes()
        key = (width, height, data)
        if key in cache:
            return cache[key]
        pixels = list(_rgba_pixels(patch))
        anchor = min(range(len(pixels)), key=lambda i: len(index[pixels[i]]))
        ax, ay = anchor % width, anchor // width
        matches, seen = [], set()
        for transform, transpose in TRANSPOSE.items():
            sw, sh = (height, width) if transform in {"rotate_90", "rotate_270"} else (width, height)
            ox, oy = inverse_transform(transform, ax, ay, sw, sh)
            for slot, x, y in index[pixels[anchor]]:
                sx, sy = x - ox, y - oy
                source = logical[slot]
                if sx < 0 or sy < 0 or sx + sw > source.width or sy + sh > source.height:
                    continue
                first_x, first_y = inverse_transform(transform, 0, 0, sw, sh)
                # Compare coordinate maps, not transform names: a 1x1 rotation is
                # identical, but a symmetric multi-pixel mirror is a real ambiguity.
                signature = (slot, sx + first_x, sy + first_y,
                             HORIZONTAL_DELTA[transform] if width > 1 else (0, 0),
                             VERTICAL_DELTA[transform] if height > 1 else (0, 0))
                if signature in seen:
                    continue
                seen.add(signature)
                cropped = source.crop((sx, sy, sx + sw, sy + sh))
                if transpose is not None:
                    cropped = cropped.transpose(transpose)
                if cropped.tobytes() == data:
                    matches.append(SourceCandidate(slot, (sx, sy, sw, sh), transform))
                    if not collect_all_candidates and len(matches) >= max(2, candidate_limit + 1):
                        cache[key] = (tuple(matches), True)
                        return cache[key]
        cache[key] = (tuple(matches), False)
        return cache[key]

    result = InferenceResult([], [], sum(color == ZERO for color in _rgba_pixels(sample)))

    def split(x, y, width, height):
        if width >= height:
            half = width // 2
            visit(x, y, half, height)
            visit(x + half, y, width - half, height)
        else:
            half = height // 2
            visit(x, y, width, half)
            visit(x, y + half, width, height - half)

    def visit(x, y, width, height):
        patch = sample.crop((x, y, x + width, y + height))
        active = [(i % width, i // width) for i, color in enumerate(_rgba_pixels(patch)) if color != ZERO]
        if not active:
            return
        left, top = min(p[0] for p in active), min(p[1] for p in active)
        right, bottom = max(p[0] for p in active) + 1, max(p[1] for p in active) + 1
        if (left, top, right, bottom) != (0, 0, width, height):
            visit(x + left, y + top, right - left, bottom - top)
            return
        if len(active) != width * height:
            # Never sample background from the source: a different material could
            # otherwise fill formerly transparent holes with unrelated colors.
            split(x, y, width, height)
            return
        positions = matcher.positions if matcher is not None else index
        if not any(positions[color] for color in _rgba_pixels(patch)):
            result.issues.append(InferenceIssue((x, y, width, height), "unmatched"))
            return
        matches, truncated = candidates(patch)
        if len(matches) == 1:
            match = matches[0]
            sx, sy, sw, sh = match.source
            for dy in range(height):
                for dx in range(width):
                    px, py = inverse_transform(match.transform, dx, dy, sw, sh)
                    result.pixels.append(MappedPixel(x + dx, y + dy, match.slot,
                                                     sx + px, sy + py, match.transform))
        elif matches:
            result.issues.append(InferenceIssue((x, y, width, height), "ambiguous", matches, truncated))
        elif width == height == 1:
            result.issues.append(InferenceIssue((x, y, 1, 1), "unmatched"))
        else:
            split(x, y, width, height)

    if face_rectangles is None:
        visit(0, 0, sample.width, sample.height)
    else:
        # A source index/cache is shared, but each face supplies independent
        # evidence. Never let traversal order overwrite a shared destination.
        faces = sorted(set(face_rectangles), key=lambda r: (r[1], r[0], r[2], r[3]))
        if not faces:
            raise ValueError("model must contain at least one non-empty face UV")
        evidence = []
        used = set()
        for x, y, width, height in faces:
            if (any(type(n) is not int for n in (x, y, width, height))
                    or min(x, y) < 0 or min(width, height) <= 0
                    or x + width > sample.width or y + height > sample.height):
                raise ValueError("model face UV must be an integer rectangle within sample bounds")
            used.update((x + dx, y + dy) for dy in range(height) for dx in range(width))
            result = InferenceResult([], [], 0)
            visit(x, y, width, height)
            evidence.append(result)
        result = _combine_faces(evidence, sample, used, candidate_limit, collect_all_candidates)
    result.pixels.sort(key=lambda p: (p.destination_y, p.destination_x))
    result.issues.sort(key=lambda issue: (issue.destination[1], issue.destination[0]))
    return result


def _combine_faces(evidence, sample, used, candidate_limit, collect_all_candidates=False):
    resolved, unresolved = defaultdict(dict), defaultdict(list)
    transform_order = {name: index for index, name in enumerate(TRANSPOSE)}
    for face in evidence:
        for pixel in face.pixels:
            coordinate = pixel.destination_x, pixel.destination_y
            source = pixel.slot, pixel.source_x, pixel.source_y
            previous = resolved[coordinate].get(source)
            if previous is None or transform_order[pixel.transform] < transform_order[previous.transform]:
                resolved[coordinate][source] = pixel
        for issue in face.issues:
            x, y, width, height = issue.destination
            for dy in range(height):
                for dx in range(width):
                    unresolved[x + dx, y + dy].append(issue)
    result = InferenceResult([], [], sum(sample.getpixel(p) == ZERO for p in used),
                             sample.width * sample.height - len(used))
    # Preserve intact issue rectangles when possible, instead of bloating a
    # uniform ambiguous face into thousands of identical one-pixel diagnostics.
    remaining = {p for p in used if sample.getpixel(p) != ZERO}
    for coordinate, sources in resolved.items():
        if len(sources) == 1:
            result.pixels.append(next(iter(sources.values())))
            remaining.remove(coordinate)
    all_issues = {issue for face in evidence for issue in face.issues}
    for issue in sorted(all_issues, key=lambda i: (i.destination[1], i.destination[0], i.destination[2:])):
        x, y, width, height = issue.destination
        area = {(x + dx, y + dy) for dy in range(height) for dx in range(width)}
        if area <= remaining and all(not resolved[p] and len(set(unresolved[p])) == 1 for p in area):
            result.issues.append(issue)
            remaining.difference_update(area)
    for x, y in sorted(remaining, key=lambda p: (p[1], p[0])):
        matches = {SourceCandidate(slot, (sx, sy, 1, 1), "identity")
                   for slot, sx, sy in resolved[x, y]}
        issues = unresolved[x, y]
        for issue in issues:
            ix, iy, _, _ = issue.destination
            for candidate in issue.candidates:
                sx, sy, sw, sh = candidate.source
                px, py = inverse_transform(candidate.transform, x - ix, y - iy, sw, sh)
                matches.add(SourceCandidate(candidate.slot, (sx + px, sy + py, 1, 1), "identity"))
        ordered = sorted(matches, key=lambda c: (c.slot, c.source, c.transform))
        truncated = any(i.search_truncated for i in issues) or (
            not collect_all_candidates and len(ordered) > candidate_limit + 1)
        reason = "ambiguous" if len(resolved[x, y]) > 1 or any(i.reason == "ambiguous" for i in issues) else "unmatched"
        # Projection loses the original whole-rectangle choice constraints.
        # Keep these as diagnostics; independently choosing each pixel could
        # collapse an entire face to one texel and violate rectangle RMS limits.
        result.issues.append(InferenceIssue((x, y, 1, 1), reason,
                                            tuple(ordered if collect_all_candidates else ordered[:candidate_limit + 1]),
                                            truncated,
                                            "overlapping_face_evidence" if reason == "ambiguous" else None))
    return result


def _rgba_pixels(image: Image.Image):
    """Iterate full RGBA tuples without depending on Pillow version-specific data APIs."""
    channels = iter(image.tobytes())
    return zip(channels, channels, channels, channels)
