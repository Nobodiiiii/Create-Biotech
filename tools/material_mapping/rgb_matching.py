"""Authoring-only RGB rectangle scoring; alpha and spatial UVs stay exact."""
from bisect import insort
from collections import defaultdict
from dataclasses import dataclass
from math import isfinite, sqrt

from .compression import HORIZONTAL_DELTA, VERTICAL_DELTA
from .sampling import inverse_transform


MAX_RGB_DISTANCE = sqrt(3 * 255 ** 2)


@dataclass(frozen=True)
class RgbPolicy:
    rmse: float = 0.0
    max_error: float = 0.0
    ambiguity_margin: float = 1.0

    def __post_init__(self):
        values = (self.rmse, self.max_error, self.ambiguity_margin)
        if any(not isfinite(v) or not 0 <= v <= MAX_RGB_DISTANCE for v in values):
            raise ValueError(f"RGB limits and ambiguity margin must be finite in 0..{MAX_RGB_DISTANCE:g}")
        if self.rmse > self.max_error or (self.rmse == 0) != (self.max_error == 0):
            raise ValueError("RGB limits must both be zero (exact), or 0 < rgb-rmse <= rgb-max-error")

    @property
    def enabled(self):
        return self.rmse > 0


def rgb_squared(left, right):
    return sum((left[i] - right[i]) ** 2 for i in range(3))


class RgbMatcher:
    """Search eligible positions; keep bounded diagnostics or all close candidates.

    The peak constraint makes a rare-color anchor a lossless search filter.
    The display limit never terminates candidate search or conceals a better UV.
    """

    def __init__(self, sources, colors, policy, candidate_limit, *, collect_all_candidates=False):
        from .inference import _rgba_pixels
        self.policy = policy
        self.limit = None if collect_all_candidates else max(2, candidate_limit + 1)
        self.sources = {slot: (image.width, image.height, list(_rgba_pixels(image)))
                        for slot, image in sources.items()}
        by_color = defaultdict(list)
        for slot, (width, _, pixels) in self.sources.items():
            for i, color in enumerate(pixels):
                by_color[color].append((slot, i % width, i // width))
        self.errors, self.positions = {}, {}
        for color in colors:
            errors = {other: rgb_squared(color, other) for other in by_color if color[3] == other[3]}
            errors = {other: error for other, error in errors.items() if error <= policy.max_error ** 2}
            self.errors[color] = errors
            self.positions[color] = [p for other in errors for p in by_color[other]]
        self.cache = {}

    def candidates(self, patch):
        from .inference import SourceCandidate, TRANSPOSE, _rgba_pixels
        width, height = patch.size
        key = (width, height, patch.tobytes())
        if key in self.cache:
            return self.cache[key]
        pixels = list(_rgba_pixels(patch))
        order = sorted(range(len(pixels)), key=lambda i: len(self.positions[pixels[i]]))
        anchor = order[0]
        ax, ay = anchor % width, anchor // width
        samples = [(i % width, i // width, self.errors[pixels[i]]) for i in order]
        total_limit = self.policy.rmse ** 2 * len(pixels)
        ranked, seen = [], set()
        discarded_min = float("inf")
        for transform_index, transform in enumerate(TRANSPOSE):
            sw, sh = (height, width) if transform in {"rotate_90", "rotate_270"} else (width, height)
            ox, oy = inverse_transform(transform, ax, ay, sw, sh)
            fx, fy = inverse_transform(transform, 0, 0, sw, sh)
            hx, hy = HORIZONTAL_DELTA[transform]
            vx, vy = VERTICAL_DELTA[transform]
            for slot, x, y in self.positions[pixels[anchor]]:
                sx, sy = x - ox, y - oy
                source_width, source_height, source = self.sources[slot]
                if sx < 0 or sy < 0 or sx + sw > source_width or sy + sh > source_height:
                    continue
                signature = (slot, sx + fx, sy + fy,
                             (hx, hy) if width > 1 else (0, 0),
                             (vx, vy) if height > 1 else (0, 0))
                if signature in seen:
                    continue
                seen.add(signature)
                origin = (sy + fy) * source_width + sx + fx
                dx, dy = hx + hy * source_width, vx + vy * source_width
                total = 0
                for px, py, errors in samples:
                    error = errors.get(source[origin + px * dx + py * dy])
                    if error is None:
                        break  # alpha or peak mismatch
                    total += error
                    if total > total_limit:
                        break
                else:
                    # Deterministic ordering for diagnostics, not a tie breaker
                    # that silently makes different UV coordinates equivalent.
                    entry = (total, slot, sy, sx, transform_index, sw, sh)
                    if self.limit is None:
                        ranked.append(entry)
                    else:
                        insort(ranked, entry)
                        if len(ranked) > self.limit:
                            discarded_min = min(discarded_min, ranked.pop()[0])
        if self.limit is None:
            ranked.sort()
        if not ranked:
            answer = ((), False)
        elif len(ranked) == 1 or (sqrt(ranked[1][0] / len(pixels))
                                 > sqrt(ranked[0][0] / len(pixels)) + self.policy.ambiguity_margin + 1e-12):
            total, slot, sy, sx, ti, sw, sh = ranked[0]
            answer = ((SourceCandidate(slot, (sx, sy, sw, sh), tuple(TRANSPOSE)[ti]),), False)
        else:
            cutoff = (sqrt(ranked[0][0] / len(pixels)) + self.policy.ambiguity_margin) ** 2 * len(pixels)
            close = [r for r in ranked if r[0] <= cutoff + 1e-9]
            answer = (tuple(SourceCandidate(slot, (sx, sy, sw, sh), tuple(TRANSPOSE)[ti])
                            for _, slot, sy, sx, ti, sw, sh in close), discarded_min <= cutoff + 1e-9)
        self.cache[key] = answer
        return answer


def sample_error(pixels, sample, direct):
    """Measure only resolved pixels; unresolved transparent output is not a match."""
    errors = []
    for pixel in pixels:
        xy = pixel.destination_x, pixel.destination_y
        expected, actual = sample.getpixel(xy), direct.getpixel(xy)
        if expected[3] != actual[3]:
            raise ValueError("inference source sampling differs from sample Alpha")
        errors.append(rgb_squared(expected, actual))
    return {"evaluated_pixels": len(errors), "approximate_pixels": sum(e != 0 for e in errors),
            "rgb_rmse": sqrt(sum(errors) / len(errors)) if errors else 0.0,
            "rgb_max_error": sqrt(max(errors, default=0))}
