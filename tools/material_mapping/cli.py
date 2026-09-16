"""Automatic material mapping CLI with optional cube face UV input."""
from __future__ import annotations
import argparse
import json
import sys
from math import isfinite
from pathlib import Path
from typing import Sequence

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from tools.material_mapping.sampling import render_definition, render_pixels
from tools.material_mapping.cli_support import (
    _parse_sources, _parse_grids, _same_path, _computed_cost, _png_bytes, _publish_outputs, _json
)


def main(argv: Sequence[str] | None = None) -> int:
    parser = _parser()
    try:
        args = parser.parse_args(list(argv) if argv is not None else None)
        if args.command == "infer":
            return _infer(args)
        parser.error("missing command")
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="cli.py", description="Material mapping and automatic UV authoring")
    commands = parser.add_subparsers(dest="command")
    infer = commands.add_parser("infer", help="infer PNG mappings (exact by default); unresolved areas produce a draft")
    infer.add_argument("--sample", required=True, type=Path)
    infer.add_argument("--model", type=Path, help="optional Blockbench cube model supplying target face UVs")
    infer.add_argument("--source", action="append", required=True, metavar="SLOT=PNG")
    infer.add_argument("--source-grid", action="append", default=[], metavar="SLOT=WIDTHxHEIGHT")
    infer.add_argument("--target-json", required=True, type=Path)
    infer.add_argument("--preview", required=True, type=Path)
    infer.add_argument("--report", required=True, type=Path)
    infer.add_argument("--unresolved-mask", required=True, type=Path)
    infer.add_argument("--max-candidates", type=int, default=16)
    infer.add_argument("--ambiguity-policy", choices=("report", "min-bounds"), default="report",
                       help="keep ambiguous drafts (default), or minimize source bounding rectangle area")
    infer.add_argument("--bounds-seconds", type=float, default=2.0,
                       help="joint source-bounds search budget, excluding candidate collection (default: 2)")
    infer.add_argument("--rgb-rmse", type=float, default=0.0,
                       help="rectangle RMS RGB Euclidean distance; 0 keeps exact mode")
    infer.add_argument("--rgb-max-error", type=float, default=0.0,
                       help="maximum per-pixel RGB distance; required with rgb-rmse, >= rgb-rmse")
    infer.add_argument("--rgb-ambiguity-margin", type=float, default=1.0,
                       help="RMS gap defining the ambiguous candidate set (default: 1)")
    infer.add_argument("--optimize-seconds", type=float, default=2.0,
                       help="selection-count search budget in seconds (default: 2; 0 skips search)")
    return parser


def _infer(args: argparse.Namespace) -> int:
    from PIL import Image
    from tools.material_mapping.inference import infer_mapping
    from tools.material_mapping.selection_optimizer import optimize_selections
    from tools.material_mapping.compression import geometry_cost
    from tools.material_mapping.rgb_matching import RgbPolicy, sample_error

    policy = RgbPolicy(args.rgb_rmse, args.rgb_max_error, args.rgb_ambiguity_margin)
    if not isfinite(args.bounds_seconds) or args.bounds_seconds < 0:
        raise ValueError("bounds optimization time limit must be finite and non-negative")
    compact = args.ambiguity_policy == "min-bounds"
    source_paths = _parse_sources(args.source)
    grids = _parse_grids(args.source_grid, list(source_paths))
    if grids.keys() != source_paths.keys():
        raise ValueError("provide an explicit source grid (--source-grid) for every --source slot")
    output_paths = [args.target_json, args.preview, args.report, args.unresolved_mask]
    input_paths = [args.sample, *source_paths.values()]
    if args.model is not None:
        input_paths.append(args.model)
    for index, path in enumerate(output_paths):
        if any(_same_path(path, other) for other in output_paths[:index]):
            raise ValueError("inference output paths must be distinct")
        if any(_same_path(path, other) for other in input_paths):
            raise ValueError("inference outputs must not overwrite an input file")
    with Image.open(args.sample) as image:
        sample = image.convert("RGBA")
    sources = {}
    for slot, path in source_paths.items():
        with Image.open(path) as image:
            sources[slot] = image.convert("RGBA")
    rectangles = None
    if args.model is not None:
        from tools.material_mapping.model_uv import face_rectangles
        rectangles = face_rectangles(args.model, sample.size)
    result = infer_mapping(sample, sources, grids, candidate_limit=args.max_candidates, face_rectangles=rectangles,
                           rgb_rmse=policy.rmse, rgb_max_error=policy.max_error,
                           rgb_ambiguity_margin=policy.ambiguity_margin, collect_all_candidates=compact)
    bounds_report = None
    if compact:
        from tools.material_mapping.source_bounds import resolve_min_bounds
        result, bounds_report = resolve_min_bounds(result, time_limit_seconds=args.bounds_seconds)
    report, mask, expected = build_diagnostics(result, sample, args.max_candidates)
    if bounds_report is not None:
        report["source_bounds_optimization"] = bounds_report
    direct = render_pixels(result.pixels, sample.size, sources, grids)
    report["matching"] = {
        "mode": "rgb_tolerant" if policy.enabled else "exact_rgba", "alpha": "exact",
        "rgb_rmse_limit": policy.rmse, "rgb_max_error_limit": policy.max_error,
        "rgb_ambiguity_margin": policy.ambiguity_margin,
        "metric": "sqrt(mean(dr^2+dg^2+db^2))",
        "candidate_search": ("all_peak_eligible_positions" if policy.enabled else
                             "all_exact_anchor_positions" if compact else "exact_anchor_until_display_limit"),
        "ambiguity_policy": args.ambiguity_policy,
    }
    report["sample_error"] = sample_error(result.pixels, sample, direct)
    if report["sample_error"]["rgb_max_error"] > policy.max_error:
        raise ValueError("inference source sampling exceeds RGB peak limit")
    if not policy.enabled and direct.tobytes() != expected.tobytes():
        raise ValueError("inference source sampling differs from resolved sample RGBA")
    optimization = optimize_selections(result.pixels, time_limit_seconds=args.optimize_seconds)
    compressed = optimization.compressed
    if rectangles is not None:
        report["model_geometry_cost"] = geometry_cost(rectangles, compressed.regions, compressed.pixels)
    report["selection_optimization"] = {
        "scope": "fixed_resolved_uv_mapping",
        "status": "not_applicable" if not result.pixels else "optimal" if optimization.optimal else "best_found",
        "input_selections": optimization.input_selections,
        "output_selections": len(compressed.regions) + len(compressed.pixels),
        "lower_bound": optimization.lower_bound,
        "optimal": optimization.optimal if result.pixels else None,
        "timed_out": optimization.timed_out,
        "time_limit_seconds": args.optimize_seconds,
    }
    outputs = []
    if result.pixels:
        definition = {
            "schema": 1, "size": list(sample.size),
            "source_grids": {slot: list(grids[slot]) for slot in sorted(grids)},
            "regions": compressed.regions, "pixels": compressed.pixels,
            "computed_cost": _computed_cost(compressed.regions, compressed.pixels),
        }
        if rectangles is not None:
            cost = report["model_geometry_cost"]
            definition["computed_cost"] = {
                "max_pieces_per_source_quad": cost["max_pieces_per_face"],
                "additional_quads": cost["additional_quads"],
            }
        preview = render_definition(definition, sources)
        outputs.append((args.target_json, _json(definition).encode("utf-8")))
    else:
        # Java rejects a target with no mappings. Preserve any previous target
        # and explicitly report that only diagnostic outputs were published.
        preview = Image.new("RGBA", sample.size, (0, 0, 0, 0))
    if (preview.mode != direct.mode or preview.size != direct.size
            or preview.tobytes() != direct.tobytes()):
        raise ValueError("inference preview round-trip differs from direct source RGBA")
    outputs.extend([
        (args.preview, _png_bytes(preview)),
        (args.report, _json(report).encode("utf-8")),
        (args.unresolved_mask, _png_bytes(mask)),
    ])
    _publish_outputs(outputs)
    target_status = "target written" if result.pixels else "no target written (previous target preserved)"
    print(f"{report['status']}: {report['resolved_pixels']} resolved, "
          f"{report['ambiguous_pixels']} ambiguous, {report['unmatched_pixels']} unmatched, "
          f"{report['unused_pixels']} unused; {target_status}")
    if bounds_report is not None:
        print(f"source bounds area: {bounds_report['input_area']} -> {bounds_report['output_area']}; "
              f"lower bound {bounds_report['lower_bound']}; {bounds_report['status']} "
              "(sum of per-slot bounding rectangles, fixed candidate sets)")
    if rectangles is not None:
        cost = report["model_geometry_cost"]
        print(f"model faces: {cost['original_faces']} -> {cost['output_fragments']} fragments; "
              f"max {cost['max_pieces_per_face']} pieces/face; {cost['additional_quads']} additional quads")
    if result.pixels:
        print(f"selections: {optimization.input_selections} -> "
              f"{report['selection_optimization']['output_selections']}; "
              f"lower bound {optimization.lower_bound}; {report['selection_optimization']['status']} "
              "(fixed resolved UV mapping)")
    return 3 if result.issues else 0



"""Authoring-only diagnostics, deliberately separate from the runtime schema."""
from PIL import Image

from tools.material_mapping.inference import InferenceResult, ZERO


ISSUE_COLORS = {"ambiguous": (255, 191, 0, 255), "unmatched": (255, 0, 0, 255)}


def build_diagnostics(result: InferenceResult, sample: Image.Image, candidate_limit: int):
    mask = Image.new("RGBA", sample.size, ZERO)
    rgba = sample.convert("RGBA")
    expected = Image.new("RGBA", sample.size, ZERO)
    for pixel in result.pixels:
        coordinate = pixel.destination_x, pixel.destination_y
        expected.putpixel(coordinate, rgba.getpixel(coordinate))
    counts = {reason: 0 for reason in ISSUE_COLORS}
    regions = []
    for issue in result.issues:
        x, y, width, height = issue.destination
        box = (x, y, x + width, y + height)
        mask.paste(ISSUE_COLORS[issue.reason], box)
        expected.paste(ZERO, box)
        counts[issue.reason] += width * height
        regions.append({
            "destination": list(issue.destination), "reason": issue.reason,
            "candidate_count": len(issue.candidates),
            "candidate_count_is_lower_bound": issue.search_truncated,
            "candidates_truncated": issue.search_truncated or len(issue.candidates) > candidate_limit,
            "candidates": [
                {"source_slot": candidate.slot, "source": list(candidate.source),
                 "transform": candidate.transform}
                for candidate in issue.candidates[:candidate_limit]],
            **({"resolution_blocker": issue.resolution_blocker} if issue.resolution_blocker else {}),
        })
    if (len(result.pixels) + result.background_pixels + result.unused_pixels
            + sum(counts.values()) != sample.width * sample.height):
        raise ValueError("inference coverage does not account for the full sample")
    report = {
        "schema": 1, "kind": "casted_materials_inference_report",
        "status": "draft" if result.issues else "complete",
        "size": list(sample.size), "target_written": bool(result.pixels),
        "resolved_pixels": len(result.pixels), "background_pixels": result.background_pixels,
        "unused_pixels": result.unused_pixels,
        "ambiguous_pixels": counts["ambiguous"], "unmatched_pixels": counts["unmatched"],
        "mask_colors": {reason: list(color) for reason, color in ISSUE_COLORS.items()},
        "unresolved_regions": regions,
    }
    return report, mask, expected


if __name__ == "__main__":
    raise SystemExit(main())

