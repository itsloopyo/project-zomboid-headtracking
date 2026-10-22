"""Writes catalogue.SEEDS from a folder of the pictures picked, each a byte for byte copy of one candidate.

    python tools/pictures/seeds.py PICKED MODEL=DIR [MODEL=DIR ...]

PICKED holds <name>.png for each picture picked. Each DIR is a generate.py --out folder of that model, holding
<name>/<seed>.png. The candidate a picked picture is a copy of is found by its bytes, so the list can be written again
as more are picked. A picture that matches no candidate, or more than one, stops the run.
"""
import hashlib
import pathlib
import re
import sys

import catalogue

CATALOGUE = pathlib.Path(__file__).resolve().parent / 'catalogue.py'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    if len(sys.argv) < 3 or not all('=' in a for a in sys.argv[2:]):
        raise SystemExit(__doc__)
    picked = pathlib.Path(sys.argv[1])
    sources = [(model, pathlib.Path(folder)) for model, folder in (a.split('=', 1) for a in sys.argv[2:])]
    seeds = {}
    for path in sorted(picked.glob('*.png')):
        name = path.stem
        if name not in catalogue.ART:
            raise SystemExit(f'{path}: no such picture in the catalogue')
        wanted = digest(path)
        found = [(model, int(candidate.stem)) for model, folder in sources
                 for candidate in (folder / name).glob('*.png') if digest(candidate) == wanted]
        if len(found) != 1:
            raise SystemExit(f'{path}: matches {len(found)} candidates, {found}')
        seeds[name] = found[0]
    lines = ''.join(f"    '{name}': ('{model}', {seed}),\n" for name, (model, seed) in seeds.items())
    text = CATALOGUE.read_text(encoding='utf-8')
    block = re.compile(r'^SEEDS = \{\}\n|^SEEDS = \{\n.*?^\}\n', re.S | re.M)
    if len(block.findall(text)) != 1:
        raise SystemExit(f'{CATALOGUE}: expected one SEEDS block')
    CATALOGUE.write_text(block.sub(lambda _: 'SEEDS = {\n' + lines + '}\n', text), encoding='utf-8', newline='\n')
    print(f'{len(seeds)} pictures picked of {len(catalogue.ART)}')


if __name__ == '__main__':
    main()
