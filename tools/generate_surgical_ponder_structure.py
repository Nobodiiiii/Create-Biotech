"""Import Ponderer's unmodified XL native base (9 x 9).

The editable JavaScene steps place the surgical tables above this shared base.
"""
from pathlib import Path
import shutil


def build():
    root = Path(__file__).resolve().parents[1]
    source = root.parent / "ponderer/Common/src/main/resources/data/ponderer/default_structures/basic_xl.nbt"
    target = root / "src/main/resources/assets/create_biotech/ponder/generated/create_biotech/surgical_table/basic_xl.nbt"
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)
    print(f"Imported native basic_xl: {target}")


if __name__ == "__main__":
    build()
