"""Anytime candidate assignment minimizing the sum of source-slot bounding areas.

This selects among existing candidate rectangles, not among all possible image
partitions. Equal-footprint transforms are equivalent only for this objective;
their preferred representative keeps its original, complete coordinate mapping.
"""
from math import isfinite
from time import monotonic

from .compression import MappedPixel
from .inference import InferenceResult
from .sampling import inverse_transform


class _BudgetExpired(Exception):
    pass


def _union(left, right):
    if left is None:
        return right
    return (min(left[0], right[0]), min(left[1], right[1]),
            max(left[2], right[2]), max(left[3], right[3]))


def _area(bounds):
    return sum((box[2] - box[0]) * (box[3] - box[1]) for box in bounds if box is not None)


def _include(bounds, slot, box):
    merged = _union(bounds[slot], box)
    if merged == bounds[slot]:
        return bounds
    return bounds[:slot] + (merged,) + bounds[slot + 1:]


def resolve_min_bounds(result: InferenceResult, *, time_limit_seconds: float = 2.0):
    """Return a new inference result and authoring-only optimization diagnostics.

    Each independent ambiguous region chooses one whole candidate; fixed UVs
    never move. Projected overlapping-face conflicts remain drafts, since their
    pixel candidates no longer describe independent whole-rectangle choices.
    The search budget excludes preparation and the first feasible
    assignment. Candidate enumeration belongs to inference, not this solver.
    """
    if not isfinite(time_limit_seconds) or time_limit_seconds < 0:
        raise ValueError("bounds optimization time limit must be finite and non-negative")
    issues = [issue for issue in result.issues if issue.reason == "ambiguous" and not issue.resolution_blocker]
    excluded = [issue for issue in result.issues if issue.reason == "ambiguous" and issue.resolution_blocker]
    if any(issue.search_truncated or not issue.candidates for issue in issues):
        raise ValueError("bounds optimization requires complete non-empty candidate sets")
    slots = sorted({p.slot for p in result.pixels} | {c.slot for i in issues for c in i.candidates})
    slot_indices = {slot: index for index, slot in enumerate(slots)}
    base = (None,) * len(slots)
    for pixel in result.pixels:
        base = _include(base, slot_indices[pixel.slot],
                        (pixel.source_x, pixel.source_y, pixel.source_x + 1, pixel.source_y + 1))

    # Same slot/box has exactly the same contribution to every possible global
    # bound. Keep the first representative (RGB ranking then stable search order).
    groups = []
    for issue in issues:
        choices, seen = [], set()
        for candidate in issue.candidates:
            x, y, width, height = candidate.source
            footprint = (slot_indices[candidate.slot], (x, y, x + width, y + height))
            if footprint not in seen:
                seen.add(footprint)
                choices.append((*footprint, candidate))
        groups.append((issue, choices))
    groups.sort(key=lambda g: (len(g[1]), -g[0].destination[2] * g[0].destination[3],
                               g[0].destination[1], g[0].destination[0]))

    def bounds_for(selected, omit=-1):
        bounds = base
        for index, choice in enumerate(selected):
            if index != omit:
                slot, box, _ = groups[index][1][choice]
                bounds = _include(bounds, slot, box)
        return bounds

    best_choices = (0,) * len(groups)
    best_bounds = bounds_for(best_choices)
    input_area = best_area = _area(best_bounds)
    # Each group must fit somewhere in the final boxes. The maximum of its
    # cheapest expansion of the fixed boxes is a valid joint lower bound.
    lower_bound = max([_area(base)] + [min(_area(_include(base, slot, box))
                                          for slot, box, _ in choices) for _, choices in groups])
    deadline = monotonic() + time_limit_seconds
    timed_out = False

    def check_time():
        if monotonic() >= deadline:
            raise _BudgetExpired

    def improve(selected):
        nonlocal best_choices, best_bounds, best_area
        bounds = bounds_for(selected)
        area = _area(bounds)
        if area < best_area:
            best_choices, best_bounds, best_area = tuple(selected), bounds, area

    def cheapest(bounds, choices):
        best = None
        for index, (slot, box, _) in enumerate(choices):
            check_time()
            expanded = _include(bounds, slot, box)
            value = (_area(expanded), index)
            if best is None or value < best[0]:
                best = (value, expanded)
        return best[0][1], best[1]

    def branches(depth, bounds, selected):
        ranked, seen = [], set()
        for index, (slot, box, _) in enumerate(groups[depth][1]):
            check_time()
            expanded = _include(bounds, slot, box)
            area = _area(expanded)
            if area < best_area and expanded not in seen:
                seen.add(expanded)
                ranked.append((area, index, expanded))
        ranked.sort(key=lambda item: (item[0], item[1]))
        for _, index, expanded in ranked:
            yield depth + 1, expanded, (*selected, index)

    if best_area > lower_bound:
        try:
            # Cheap incumbents first. Opposite traversal orders and coordinate
            # descent help, but only the exhaustive search below proves optimality.
            for order in (range(len(groups)), reversed(range(len(groups)))):
                bounds, selected = base, [0] * len(groups)
                for index in order:
                    selected[index], bounds = cheapest(bounds, groups[index][1])
                improve(selected)
            changed = True
            while changed and best_area > lower_bound:
                changed = False
                for index, (_, choices) in enumerate(groups):
                    check_time()
                    selected = list(best_choices)
                    selected[index], _ = cheapest(bounds_for(selected, omit=index), choices)
                    previous = best_area
                    improve(selected)
                    changed |= best_area < previous
            stack = [iter(((0, base, ()),))]
            reached = set()
            while stack and best_area > lower_bound:
                check_time()
                try:
                    depth, bounds, selected = next(stack[-1])
                except StopIteration:
                    stack.pop()
                    continue
                state = depth, bounds
                if _area(bounds) >= best_area or state in reached:
                    continue
                reached.add(state)
                if depth == len(groups):
                    improve(selected)
                    continue
                # An unassigned group already requiring >= incumbent area rules
                # out this entire branch; no assumptions about overlap/union area.
                for _, choices in groups[depth:]:
                    _, expanded = cheapest(bounds, choices)
                    if _area(expanded) >= best_area:
                        break
                else:
                    stack.append(branches(depth, bounds, selected))
        except _BudgetExpired:
            timed_out = True
    optimal = not timed_out or best_area == lower_bound

    pixels = list(result.pixels)
    selections = []
    for (issue, choices), index in zip(groups, best_choices):
        candidate = choices[index][2]
        x, y, width, height = issue.destination
        sx, sy, sw, sh = candidate.source
        for dy in range(height):
            for dx in range(width):
                px, py = inverse_transform(candidate.transform, dx, dy, sw, sh)
                pixels.append(MappedPixel(x + dx, y + dy, candidate.slot,
                                          sx + px, sy + py, candidate.transform))
        selections.append({"destination": list(issue.destination), "candidate_count": len(issue.candidates),
                           "source_slot": candidate.slot, "source": list(candidate.source),
                           "transform": candidate.transform})
    pixels.sort(key=lambda p: (p.destination_y, p.destination_x))
    selections.sort(key=lambda s: (s["destination"][1], s["destination"][0]))
    report = {
        "objective": "sum_source_slot_bounding_rectangle_areas",
        "scope": "fixed_independent_candidate_regions",
        "status": "not_applicable" if not groups else "optimal" if optimal else "best_found",
        "input_area": input_area, "output_area": best_area,
        "lower_bound": best_area if optimal else lower_bound,
        "optimal": optimal if groups else None, "timed_out": timed_out,
        "time_limit_seconds": time_limit_seconds, "candidate_search_complete": True,
        "excluded_ambiguous_regions": len(excluded),
        "source_bounds": {slot: [box[0], box[1], box[2] - box[0], box[3] - box[1]]
                          for slot, box in zip(slots, best_bounds) if box is not None},
        "selected_regions": selections,
    }
    return InferenceResult(pixels, [i for i in result.issues if i.reason != "ambiguous" or i.resolution_blocker],
                           result.background_pixels, result.unused_pixels), report
