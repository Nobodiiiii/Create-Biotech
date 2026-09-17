"""Deployment regression tests; all file operations use temporary instances."""

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "biotech_quickplay", Path(__file__).resolve().parents[1] / "test.py"
)
launcher = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = launcher
SPEC.loader.exec_module(launcher)


class QuickPlayModsTest(unittest.TestCase):
    def test_launcher_build_needs_no_external_library_or_embedding_parameters(self):
        for offline in (False, True):
            with self.subTest(offline=offline), patch.object(launcher.subprocess, "run") as run:
                launcher.run_build(offline=offline)
                expected = [str(launcher.PROJECT_ROOT / "gradlew.bat")]
                if offline:
                    expected.append("--offline")
                expected.extend(("build", "syncQuickPlayMods"))
                self.assertEqual(run.call_args.args[0], expected)
                self.assertEqual(run.call_args.kwargs["cwd"], launcher.PROJECT_ROOT)
                self.assertTrue(run.call_args.kwargs["check"])

    def test_native_module_deployment_removes_legacy_library_and_preserves_unrelated_mods(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            staged, mods = root / "staged", root / "mods"
            staged.mkdir()
            mods.mkdir()
            runtime = {
                "create-1.21.1-6.0.10.jar": b"new create",
                "vanillin-neoforge-1.21.1-1.1.3.jar": b"new vanillin",
                "jei-1.21.1-neoforge-19.39.0.jar": b"new jei",
            }
            for name, contents in runtime.items():
                (staged / name).write_bytes(contents)
            for name in ("create-1.21.1-6.0.9.jar", "vanillin-neoforge-1.21.1-1.0.0.jar",
                         "jei-1.21.1-neoforge-19.0.0.jar", "casted-materials-1.21.1-0.1.0.jar"):
                (mods / name).write_bytes(b"stale managed jar")
            preserved = {
                "another-mod.jar": b"unrelated",
                "casted-materials-1.20.1-0.1.0.jar": b"other Minecraft version",
                "create-1.20.1-6.0.0.jar": b"other Create profile",
                "sable-1.21.1.jar": b"preserve Sable",
            }
            for name, contents in preserved.items():
                (mods / name).write_bytes(contents)

            with patch.object(launcher, "QUICKPLAY_MODS_DIR", staged):
                launcher.copy_runtime_mods(mods, {"minecraft_version": "1.21.1"})

            self.assertEqual({path.name: path.read_bytes() for path in mods.iterdir()}, runtime | preserved)

    def test_native_module_rejects_staged_external_library_before_removing_installed_mods(self):
        for library in ("casted-materials-1.21.1-0.1.0.jar", "casted-materials-1.20.1-0.1.0.jar"):
            with self.subTest(library=library), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                staged, mods = root / "staged", root / "mods"
                staged.mkdir()
                mods.mkdir()
                (staged / "create-1.21.1-6.0.10.jar").write_bytes(b"new create")
                (staged / library).write_bytes(b"stale staged library")
                old = {"casted-materials-1.21.1-0.0.9.jar": b"old library",
                       "create-1.21.1-6.0.9.jar": b"old create"}
                for name, contents in old.items():
                    (mods / name).write_bytes(contents)
                with patch.object(launcher, "QUICKPLAY_MODS_DIR", staged):
                    with self.assertRaisesRegex(ValueError, "standalone Casted Materials"):
                        launcher.copy_runtime_mods(mods, {"minecraft_version": "1.21.1"})
                self.assertEqual({path.name: path.read_bytes() for path in mods.iterdir()}, old)

    def test_missing_or_empty_staging_does_not_delete_installed_mods(self):
        for exists in (False, True):
            with self.subTest(staging_exists=exists), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                staged, mods = root / "staged", root / "mods"
                mods.mkdir()
                if exists:
                    staged.mkdir()
                library = mods / "casted-materials-1.21.1-0.1.0.jar"
                library.write_bytes(b"keep until deployment is ready")
                create = mods / "create-1.21.1-6.0.9.jar"
                create.write_bytes(b"keep old Create")
                with patch.object(launcher, "QUICKPLAY_MODS_DIR", staged):
                    with self.assertRaises(FileNotFoundError):
                        launcher.copy_runtime_mods(mods, {"minecraft_version": "1.21.1"})
                self.assertEqual(library.read_bytes(), b"keep until deployment is ready")
                self.assertEqual(create.read_bytes(), b"keep old Create")


if __name__ == "__main__":
    unittest.main()
