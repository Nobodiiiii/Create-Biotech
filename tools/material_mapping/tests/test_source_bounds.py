"""Choose compact source bounding boxes, not independent first hits or union area."""
from itertools import product
import random

import pytest

from tools.material_mapping.compression import MappedPixel
from tools.material_mapping.inference import InferenceIssue, InferenceResult, SourceCandidate
from tools.material_mapping.source_bounds import resolve_min_bounds


def candidate(x, y=0, width=1, height=1, slot="all", transform="identity"):
    return SourceCandidate(slot, (x, y, width, height), transform)


def issue(destination, *choices, truncated=False):
    return InferenceIssue(destination, "ambiguous", tuple(choices), truncated)


def mapping(result):
    return {(p.destination_x, p.destination_y): (p.slot, p.source_x, p.source_y) for p in result.pixels}


def test_external_bounds_includes_gaps_and_fixed_uv_not_just_covered_area():
    fixed = MappedPixel(0, 0, "all", 10, 10, "identity")
    raw = InferenceResult([fixed], [issue((1, 0, 1, 1), candidate(0, 0), candidate(11, 10))], 0)
    resolved, report = resolve_min_bounds(raw)
    assert mapping(resolved) == {(0, 0): ("all", 10, 10), (1, 0): ("all", 11, 10)}
    assert report["input_area"] == 121 and report["output_area"] == 2
    assert report["source_bounds"] == {"all": [10, 10, 2, 1]}
    assert report["optimal"] and report["lower_bound"] == 2
    assert not resolved.issues
    assert len(raw.pixels) == 1 and len(raw.issues) == 1  # do not mutate diagnostics input


def test_global_choices_escape_first_hit_greedy_and_single_region_local_minimum():
    raw = InferenceResult([], [issue((0, 0, 1, 1), candidate(0), candidate(10)),
                               issue((1, 0, 1, 1), candidate(4), candidate(11))], 0)
    resolved, report = resolve_min_bounds(raw)
    assert mapping(resolved) == {(0, 0): ("all", 10, 0), (1, 0): ("all", 11, 0)}
    assert report["input_area"] == 5 and report["output_area"] == 2
    assert report["optimal"] and report["lower_bound"] == 2
    assert len(report["selected_regions"]) == 2


def test_multiple_slots_have_separate_boxes_and_total_area():
    fixed = [MappedPixel(0, 0, "a", 100, 100, "identity"), MappedPixel(1, 0, "b", 0, 0, "identity")]
    raw = InferenceResult(fixed, [issue((2, 0, 1, 1), candidate(0, slot="a"), candidate(1, slot="b"))], 0)
    resolved, report = resolve_min_bounds(raw)
    assert mapping(resolved)[2, 0] == ("b", 1, 0)
    assert report["source_bounds"] == {"a": [100, 100, 1, 1], "b": [0, 0, 2, 1]}
    assert report["output_area"] == 3 and report["optimal"]


def test_rotated_candidates_keep_whole_rectangle_coordinates():
    raw = InferenceResult([MappedPixel(3, 0, "all", 4, 4, "identity")], [
        issue((0, 0, 2, 3), candidate(0, 0, 3, 2, transform="rotate_90"),
              candidate(4, 5, 3, 2, transform="rotate_90"))], 0)
    resolved, report = resolve_min_bounds(raw)
    assert [mapping(resolved)[x, y][1:] for y in range(3) for x in range(2)] == [
        (4, 6), (4, 5), (5, 6), (5, 5), (6, 6), (6, 5)]
    assert report["output_area"] == 9 and report["source_bounds"] == {"all": [4, 4, 3, 3]}


def test_zero_search_budget_returns_a_feasible_plan_without_false_optimality():
    raw = InferenceResult([], [issue((0, 0, 1, 1), candidate(0), candidate(10)),
                               issue((1, 0, 1, 1), candidate(4), candidate(11))], 0)
    resolved, report = resolve_min_bounds(raw, time_limit_seconds=0)
    assert len(resolved.pixels) == 2 and not resolved.issues
    assert report["output_area"] == 5 and report["lower_bound"] <= 2
    assert not report["optimal"] and report["timed_out"]


def test_unmatched_background_unused_and_no_decisions_are_preserved():
    raw = InferenceResult([MappedPixel(0, 0, "all", 5, 5, "identity")],
                          [InferenceIssue((1, 0, 1, 1), "unmatched")], 4, 7)
    resolved, report = resolve_min_bounds(raw)
    assert resolved == raw and report["status"] == "not_applicable"
    assert report["output_area"] == 1


def test_truncated_candidates_must_not_be_optimized_as_if_complete():
    raw = InferenceResult([], [issue((0, 0, 1, 1), candidate(0), candidate(10), truncated=True)], 0)
    with pytest.raises(ValueError, match="complete"):
        resolve_min_bounds(raw)


@pytest.mark.parametrize("seconds", [-1, float("nan"), float("inf")])
def test_invalid_budget_is_rejected(seconds):
    with pytest.raises(ValueError, match="finite.*non-negative"):
        resolve_min_bounds(InferenceResult([], [], 0), time_limit_seconds=seconds)


def test_small_random_problems_match_independent_exhaustive_oracle():
    rng = random.Random(12873)
    for _ in range(25):
        groups = [[candidate(rng.randrange(8), rng.randrange(6), slot=rng.choice(["a", "b"]))
                   for _ in range(3)] for _ in range(4)]
        raw = InferenceResult([], [issue((i, 0, 1, 1), *group) for i, group in enumerate(groups)], 0)
        areas = []
        for choices in product(*groups):
            area = 0
            for slot in {c.slot for c in choices}:
                boxes = [c.source for c in choices if c.slot == slot]
                area += ((max(x + w for x, y, w, h in boxes) - min(x for x, y, w, h in boxes))
                         * (max(y + h for x, y, w, h in boxes) - min(y for x, y, w, h in boxes)))
            areas.append(area)
        resolved, report = resolve_min_bounds(raw)
        assert report["output_area"] == min(areas)
        assert report["optimal"] and report["lower_bound"] == min(areas)
        assert len(resolved.pixels) == 4
        repeated, again = resolve_min_bounds(raw)
        assert mapping(repeated) == mapping(resolved) and again == report
