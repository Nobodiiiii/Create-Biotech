"""Anytime minimum non-overlapping rectangle partition of a fixed UV mapping.

This optimizes selection count, not ambiguous image-to-UV assignments. All six
runtime transforms are considered, irrespective of a pixel's old transform label.
"""
from __future__ import annotations

from dataclasses import dataclass
from heapq import heappop, heappush
from math import isfinite
from time import monotonic

from .compression import (
    CompressionResult, HORIZONTAL_DELTA, VERTICAL_DELTA, MappedPixel,
    _unique_pixels, compress_pixels,
)
from .sampling import inverse_transform


@dataclass(frozen=True)
class OptimizationResult:
    compressed: CompressionResult
    input_selections: int
    lower_bound: int
    optimal: bool
    timed_out: bool


@dataclass(frozen=True)
class _Selection:
    x: int
    y: int
    width: int
    height: int
    pixel: MappedPixel
    transform: str
    mask: int


class _BudgetExpired(Exception):
    pass


def optimize_selections(pixels: list[MappedPixel], *, time_limit_seconds: float = 2.0) -> OptimizationResult:
    """Preserve every source coordinate and destination; minimize regions + pixels.

    A feasible row-first partition is always available. A zero budget skips the
    branch-and-bound search; cheap full-rectangle/lower-bound proofs still apply.
    The deadline covers search, not input validation or initial partitioning.
    """
    if not isfinite(time_limit_seconds) or time_limit_seconds < 0:
        raise ValueError("optimization time limit must be finite and non-negative")
    unique = _unique_pixels(pixels)
    if any(min(p.destination_x, p.destination_y, p.source_x, p.source_y) < 0 for p in unique.values()):
        raise ValueError("optimization coordinates must be non-negative")
    incumbent = compress_pixels(list(unique.values()))
    original_count = best_count = _count(incumbent)
    if best_count <= 1:
        return OptimizationResult(incumbent, original_count, best_count, True, False)
    stride = max(p.destination_x for p in unique.values()) + 1
    points = {y * stride + x: pixel for (x, y), pixel in unique.items()}
    full_mask = sum(1 << index for index in points)
    bounds = {}

    def whole_rectangle(mask):
        indices = list(_indices(mask))
        first = points[indices[0]]
        x, y = first.destination_x, first.destination_y
        right = max(points[index].destination_x for index in indices) + 1
        bottom = points[indices[-1]].destination_y + 1
        width, height = right - x, bottom - y
        if width * height != len(indices):
            return None
        for transform in HORIZONTAL_DELTA:
            hx, hy = HORIZONTAL_DELTA[transform]
            vx, vy = VERTICAL_DELTA[transform]
            if all(p.slot == first.slot and p.destination_x >= x
                   and p.source_x == first.source_x + (p.destination_x - x) * hx + (p.destination_y - y) * vx
                   and p.source_y == first.source_y + (p.destination_x - x) * hy + (p.destination_y - y) * vy
                   for p in (points[index] for index in indices)):
                return _Selection(x, y, width, height, first, transform, mask)
        return None

    def bound(mask):
        if mask in bounds:
            return bounds[mask]
        whole = whole_rectangle(mask)
        if whole is not None:
            bounds[mask] = (1, whole)
            return bounds[mask]
        coords = {(points[i].destination_x, points[i].destination_y) for i in _indices(mask)}
        rows, columns = {}, {}
        for x, y in coords:
            rows.setdefault(y, []).append(x)
            columns.setdefault(x, []).append(y)
        # A rectangle intersects any row/column in one contiguous run at most.
        run_bound = max(_runs(values) for values in [*rows.values(), *columns.values()])
        unseen, components = set(coords), 0
        while unseen:
            components += 1
            todo = [unseen.pop()]
            while todo:
                x, y = todo.pop()
                for neighbor in [(x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)]:
                    # Any valid rectangle has unit source-coordinate steps and
                    # a single slot. It cannot bridge incompatible components.
                    if neighbor in unseen and _adjacent_uv(unique[(x, y)], unique[neighbor]):
                        unseen.remove(neighbor)
                        todo.append(neighbor)
        bounds[mask] = (max(2, run_bound, components), None)
        return bounds[mask]

    initial_bound, whole = bound(full_mask)
    if whole is not None:
        compressed = _serialize((whole,))
        _verify_uvs(unique, compressed)
        return OptimizationResult(compressed, original_count, 1, True, False)
    if initial_bound == best_count:
        return OptimizationResult(incumbent, original_count, best_count, True, False)
    deadline = monotonic() + time_limit_seconds

    def check_time():
        if monotonic() >= deadline:
            raise _BudgetExpired

    def choices(mask):
        # In every non-overlapping partition, the first remaining row-major
        # pixel must be the top-left corner of its rectangle. Enumerating all
        # valid sizes here is exhaustive; restricting to maximal rectangles is not.
        index = (mask & -mask).bit_length() - 1
        first = points[index]
        x, y = first.destination_x, first.destination_y
        heap = []
        for order, transform in enumerate(HORIZONTAL_DELTA):
            hx, hy = HORIZONTAL_DELTA[transform]
            vx, vy = VERTICAL_DELTA[transform]
            limit, dy = stride - x, 0
            while limit:
                check_time()
                run = 0
                while run < limit:
                    position = (y + dy) * stride + x + run
                    pixel = points.get(position)
                    if (pixel is None or not mask & (1 << position) or pixel.slot != first.slot
                            or pixel.source_x != first.source_x + run * hx + dy * vx
                            or pixel.source_y != first.source_y + run * hy + dy * vy):
                        break
                    run += 1
                limit = run
                if limit:
                    heappush(heap, (-limit * (dy + 1), -limit, -(dy + 1), order, transform))
                dy += 1
        seen = set()
        while heap:
            check_time()
            _, negative_width, negative_height, order, transform = heappop(heap)
            width, height = -negative_width, -negative_height
            if width > 1:
                heappush(heap, (-(width - 1) * height, 1 - width, -height, order, transform))
            if (width, height) in seen:
                continue
            seen.add((width, height))
            rectangle_mask = sum(((1 << width) - 1) << ((y + dy) * stride + x) for dy in range(height))
            yield _Selection(x, y, width, height, first, transform, rectangle_mask)

    def children(remaining, selected):
        for rectangle in choices(remaining):
            yield remaining ^ rectangle.mask, (*selected, rectangle)

    # Lazy DFS frames avoid retaining thousands of full-sized bit masks for
    # unexplored sibling rectangles on large, almost-solid textures.
    stack = [iter(((full_mask, ()),))]
    reached = {}
    timed_out = False
    try:
        while stack:
            check_time()
            try:
                remaining, selected = next(stack[-1])
            except StopIteration:
                stack.pop()
                continue
            depth = len(selected)
            if reached.get(remaining, best_count) <= depth:
                continue
            reached[remaining] = depth
            lower, whole = bound(remaining)
            if depth + lower >= best_count:
                continue
            if whole is not None:
                incumbent = _serialize((*selected, whole))
                best_count = depth + 1
                if best_count == initial_bound:
                    break
                continue
            stack.append(children(remaining, selected))
    except _BudgetExpired:
        timed_out = True
    optimal = not timed_out or best_count == initial_bound
    _verify_uvs(unique, incumbent)
    return OptimizationResult(incumbent, original_count, best_count if optimal else initial_bound, optimal, timed_out)


def _indices(mask):
    while mask:
        bit = mask & -mask
        yield bit.bit_length() - 1
        mask ^= bit


def _runs(values):
    ordered = sorted(values)
    return 1 + sum(right != left + 1 for left, right in zip(ordered, ordered[1:]))


def _count(compressed):
    return len(compressed.regions) + len(compressed.pixels)


def _adjacent_uv(left, right):
    return (left.slot == right.slot
            and abs(left.source_x - right.source_x) + abs(left.source_y - right.source_y) == 1)


def _verify_uvs(original, compressed):
    """Colors alone cannot detect a changed UV on a repetitive source texture."""
    actual = {}

    def put(destination, source):
        if destination in actual:
            raise ValueError("optimized selections overlap")
        actual[destination] = source

    for region in compressed.regions:
        sx, sy, sw, sh = region["source"]
        x, y, width, height = region["destination"]
        for dy in range(height):
            for dx in range(width):
                px, py = inverse_transform(region["transform"], dx, dy, sw, sh)
                put((x + dx, y + dy), (region["source_slot"], sx + px, sy + py))
    for pixel in compressed.pixels:
        put(tuple(pixel["destination"]), (pixel["source_slot"], *pixel["source"]))
    expected = {destination: (p.slot, p.source_x, p.source_y) for destination, p in original.items()}
    if actual != expected:
        raise ValueError("optimized selections changed resolved UV coordinates or coverage")


def _serialize(selections):
    regions, pixels = [], []
    for selection in sorted(selections, key=lambda r: (r.y, r.x)):
        first = selection.pixel
        if selection.width * selection.height == 1:
            pixels.append({"source_slot": first.slot, "source": [first.source_x, first.source_y],
                           "destination": [selection.x, selection.y]})
            continue
        hx, hy = HORIZONTAL_DELTA[selection.transform]
        vx, vy = VERTICAL_DELTA[selection.transform]
        xs = [first.source_x + dx * hx + dy * vx
              for dx in [0, selection.width - 1] for dy in [0, selection.height - 1]]
        ys = [first.source_y + dx * hy + dy * vy
              for dx in [0, selection.width - 1] for dy in [0, selection.height - 1]]
        regions.append({"source_slot": first.slot,
                        "source": [min(xs), min(ys), max(xs) - min(xs) + 1, max(ys) - min(ys) + 1],
                        "destination": [selection.x, selection.y, selection.width, selection.height],
                        "transform": selection.transform})
    return CompressionResult(regions, pixels)
