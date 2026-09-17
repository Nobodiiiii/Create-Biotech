"""Public CLI boundaries and package independence, exercised in fresh processes."""
import ast
import json
from pathlib import Path
import subprocess
import sys

import pytest
from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
TOOLS = ROOT / "tools"
CORE = TOOLS / "material_mapping/cli.py"


def arguments(folder, source):
    return ["infer", "--sample", str(source), "--source", f"all={source}", "--source-grid", "all=1x1",
            "--target-json", str(folder / "target.json"), "--preview", str(folder / "preview.png"),
            "--report", str(folder / "report.json"), "--unresolved-mask", str(folder / "mask.png"),
            "--rgb-rmse", "3", "--rgb-max-error", "6"]


@pytest.mark.parametrize("with_model", [False, True])
def test_material_cli_runs_outside_repository_without_importing_blockbench(tmp_path, with_model):
    assert CORE.is_file(), "Automatic UV must have its own CLI outside blockbench"
    source = tmp_path / "source.png"
    Image.new("RGBA", (1, 1), (50, 60, 70, 255)).save(source)
    code = '''
import importlib.abc, runpy, sys
class NoBlockbench(importlib.abc.MetaPathFinder):
    def find_spec(self, fullname, path=None, target=None):
        if fullname == 'tools.blockbench' or fullname.startswith('tools.blockbench.'):
            raise AssertionError('Pure image inference imported Blockbench: ' + fullname)
sys.meta_path.insert(0, NoBlockbench())
script, *args = sys.argv[1:]
sys.argv = [script, *args]
runpy.run_path(script, run_name='__main__')
'''
    args = arguments(tmp_path / "out", source)
    if with_model:
        model = tmp_path / "model.bbmodel"
        model.write_text(json.dumps({
            "resolution": {"width": 1, "height": 1},
            "elements": [{"faces": {"north": {"uv": [0, 0, 1, 1]}}}],
        }), encoding="utf-8")
        args.extend(["--model", str(model)])
    result = subprocess.run([sys.executable, "-B", "-c", code, str(CORE), *args],
                            cwd=tmp_path, capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    assert json.loads((tmp_path / "out/target.json").read_text())["size"] == [1, 1]


def test_script_and_module_entrypoints_produce_identical_outputs(tmp_path):
    source = tmp_path / "source.png"
    Image.new("RGBA", (1, 1), (50, 60, 70, 255)).save(source)
    for command, name in [([str(CORE)], "script"), (["-m", "tools.material_mapping"], "module")]:
        result = subprocess.run([sys.executable, "-B", *command, *arguments(tmp_path / name, source)],
                                cwd=ROOT, capture_output=True, text=True)
        assert result.returncode == 0, result.stdout + result.stderr
    for name in ["target.json", "preview.png", "report.json", "mask.png"]:
        assert (tmp_path / "script" / name).read_bytes() == (tmp_path / "module" / name).read_bytes()


def test_tool_modules_use_the_consolidated_layout():
    for module in ["cli", "cli_support", "compression", "inference", "rgb_matching", "sampling",
                   "selection_optimizer", "source_bounds", "model_uv"]:
        assert (TOOLS / f"material_mapping/{module}.py").is_file(), module
    for retired in ["commands", "inference_report", "geometry", "schema"]:
        assert not (TOOLS / f"material_mapping/{retired}.py").exists(), retired

    assert not (TOOLS / "blockbench").exists()
    assert (TOOLS / "test-resources/fixtures/models/spider_body.bbmodel").is_file()

    production = [
        path for path in TOOLS.rglob("*.py")
        if "tests" not in path.parts and path.name != "conftest.py"
    ]
    assert len(production) <= 12


def test_consolidated_modules_do_not_redefine_top_level_functions_or_classes():
    production = [
        path for path in TOOLS.rglob("*.py")
        if "tests" not in path.parts and path.name != "conftest.py"
    ]
    duplicates = []
    for path in production:
        definitions = [
            node.name for node in ast.parse(path.read_text(encoding="utf-8-sig")).body
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef))
        ]
        repeated = sorted({name for name in definitions if definitions.count(name) > 1})
        if repeated:
            duplicates.append(f"{path.relative_to(TOOLS)}: {', '.join(repeated)}")
    assert not duplicates, "duplicate top-level definitions:\n" + "\n".join(duplicates)


def test_shared_resources_are_not_owned_by_blockbench():
    assert (TOOLS / "test-resources/fixtures/connected_spider/spider_andesite_authored.png").is_file()
    assert not (TOOLS / "blockbench/test-resources").exists()
    assert (TOOLS / "resourcepacks/connected_spider/pack.mcmeta").is_file()
    assert not (TOOLS / "blockbench/examples/connected_spider").exists()
    assert "tools/test-resources" in (ROOT / "build.gradle").read_text(encoding="utf-8")
    assert "tools/blockbench/test-resources" not in (ROOT / "build.gradle").read_text(encoding="utf-8")


def test_material_module_entrypoint():
    result = subprocess.run([sys.executable, "-B", "-m", "tools.material_mapping", "--help"],
                            cwd=ROOT, capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    assert "infer" in result.stdout and "init" not in result.stdout
