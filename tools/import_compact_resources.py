"""Import full belt blockstates and fish-release item models into editable templates.

Run: python tools/import_compact_resources.py
Then: gradlew generateCompactResources (also runs with processResources/build).
The three runtime paths are produced under build/generated/compactResources.
"""
from collections import OrderedDict
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/create_biotech'
SOURCES = ROOT / 'src/main/resource-templates'


def load(path):
    return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=OrderedDict)


def write(path, data, compact=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(data, ensure_ascii=False, indent=2)
    if compact:
        # Each entry is an independently editable template or transform.
        text = '{\n' + ',\n'.join('  ' + json.dumps(k) + ': ' + json.dumps(v, ensure_ascii=False)
                                  for k, v in data.items()) + '\n}'
    path.write_text(text + '\n', encoding='utf-8')


def import_resources():
    imported = []
    for name in ('power_belt', 'magma_belt'):
        path = ASSETS / 'blockstates' / (name + '.json')
        if not path.exists(): continue
        original = load(path)
        if set(original) != {'variants'}:
            raise ValueError('Unsupported blockstate structure: ' + str(path))
        grouped = OrderedDict()
        for variant, value in original['variants'].items():
            identity = json.dumps(value, sort_keys=True)
            grouped.setdefault(identity, {'value': value, 'variants': []})['variants'].append(variant)
        write(SOURCES / 'blockstates' / (name + '.json'),
              OrderedDict((str(i), group) for i, group in enumerate(grouped.values())), compact=True)
        imported.append(path)
    path = ASSETS / 'models/item/automatic_fish_release_machine.json'
    if path.exists():
        original = load(path)
        templates, children = OrderedDict(), OrderedDict()
        for name, child in original.pop('children').items():
            template = {k: v for k, v in child.items() if k != 'transform'}
            template_id = next((k for k, v in templates.items() if v == template), str(len(templates)))
            templates[template_id] = template
            entry = {'template': template_id}
            if 'transform' in child: entry['transform'] = child['transform']
            children[name] = entry
        directory = SOURCES / 'models/automatic_fish_release_machine'
        write(directory / 'model.json', original)
        write(directory / 'templates.json', templates, compact=True)
        write(directory / 'children.json', children, compact=True)
        imported.append(path)
    # All templates have been written before replacing these exact three paths.
    for path in imported:
        path.unlink()
    print('Imported', len(imported), 'full resources. Run gradlew generateCompactResources to expand them.')


if __name__ == '__main__':
    import_resources()
