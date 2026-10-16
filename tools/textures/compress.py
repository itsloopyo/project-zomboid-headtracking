"""Makes the pictures the mod's jar holds for the graphics card out of the ones in src/textures.

    python tools/textures/compress.py [name ...] [--all] [--cpu]       (Python with numpy and Pillow, as the other tools here)

A name is an output without its folder or ending (bark, asphalt, shotgun.colour). With none,
every output whose sources have changed since it was made is made again; with --all, every one.
--cpu has the encoder leave the graphics card alone, for while a game or another job has it.

Each output is one file under src/main/resources, read by Pictures.java:

    "CUBC"            4 bytes
    int32 format      0: four bytes a texel (red, green, blue, alpha)   7: BC7 blocks
    int32 width, height, layers, levels
    int32 srgb        1 where the colour is what a screen shows and the card is to make it linear
    float32 gain      what the picture is multiplied by to be 1 on average over what of it is solid
    then each level from the largest to one texel, and within a level each layer, with nothing between

all little-endian. The smaller copies are made here: a graphics card makes none of a compressed
picture. Each is the mean of the four texels it stands for, which is what a card made of these
pictures before they were compressed, so a surface far off looks as it did. Where the colour is
a screen's the mean is taken of the light, not of the numbers.

made.txt, beside this file, lists every output with the SHA-256 of it, how it was made (its
format, whether its colour is a screen's, and the encoder) and the SHA-256 of each source it was
made from. The mod's tests fail when a file on disk is not the one listed, so a source cannot
change and its output stay.

The encoder is Microsoft's texconv, a build-time tool that is not in the repository and not in
the jar: the pinned release is fetched to tools/textures/.encoder the first time and checked
against its SHA-256 every time. Its BC7 encoder runs on the graphics card, so the same source
packed on another card or driver may come out as different bytes of the same quality: an
output is remade when its sources or its recipe change, not to see whether it would differ.

bc7_fixture.py, beside this file, made the blocks and texels the mod's test of Bc7.java reads.
"""
import gzip
import hashlib
import pathlib
import struct
import subprocess
import sys
import tempfile
import urllib.request

import numpy
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCES = ROOT / 'src' / 'textures'
OUT = ROOT / 'src' / 'main' / 'resources'
MADE = pathlib.Path(__file__).resolve().parent / 'made.txt'
ENCODER = pathlib.Path(__file__).resolve().parent / '.encoder' / 'texconv.exe'
ENCODER_URL = 'https://github.com/microsoft/DirectXTex/releases/download/may2026/texconv.exe'
ENCODER_SHA256 = 'dcfdec10244e02cf5037fba089c55fb7e1326b1c8181742d77d15fa5cb5eef06'

RAW, BC7 = 0, 7
# The order Flora counts the barks in: Flora.BARKS, which a test holds this to.
BARKS = ['bark-oak', 'bark-linden', 'bark-maple', 'bark-birch', 'bark-pine', 'bark-hemlock', 'bark-holly', 'bark-dogwood']
# The materials with a picture of heights beside them (tools/materials/heights.py), which goes in as the fourth number of each texel.
RAISED = ['asphalt', 'gravel', 'soil', 'shingles', 'wood', 'planks']
PLAIN = ['grass', 'paint', 'carpet', 'vinyl', 'cloth', 'jersey', 'litter', 'rock']
CUT_OUT = ['leaves', 'frond', 'ivy', 'sprigs']
MODELS = ['shotgun', 'sawnoff', 'rifle', 'revolver', 'magnum', 'snubnose', 'capgun', 'pistol', 'pistol2', 'pistol3', 'dbarrel', 'arms']
# The models whose pictures are twice as wide as high (tools/models/gunsmith.py, WIDE): every other is square.
WIDE = {'rifle'}
# The vehicle interiors (tools/models/cabins.py), each a colour and a surface of its own.
CABINS = ['carnormal', 'carstationwagon', 'smallcar02', 'smallcar', 'suv', 'pickuptruck', 'vanseats', 'van', 'carluxury', 'moderncar',
          'moderncar02', 'sportscar', 'racecar', 'offroad', 'stepvan']
# The weapons' colour is read by the card as what a screen shows (OwnGun), and so is the interiors'. The arms' is not (OwnArms).
SRGB = {'shotgun.colour', 'sawnoff.colour', 'rifle.colour', 'revolver.colour', 'magnum.colour', 'snubnose.colour', 'capgun.colour', 'pistol.colour', 'pistol2.colour', 'pistol3.colour', 'dbarrel.colour'} | {f'{cabin}.colour' for cabin in CABINS}
# Kept four bytes a texel, for what BC7 was measured to cost them. The form is three unrelated numbers a texel, which blocks
# hold badly (33 dB). The frond's leaflets are a few texels wide and lose their edges (30 dB over what of it shows). The
# gravel's heights come out grainy (35 dB), and a surface's depth is read off them.
UNPACKED = {'sprigs-form', 'frond', 'gravel'}


def outputs():
    """Each output's name, its folder, and its layers, each a list of source files under src/textures."""
    made = {'bark': ('materials', [[f'materials/{bark}.jpg'] for bark in BARKS])}
    for name in RAISED:
        made[name] = ('materials', [[f'materials/{name}.jpg', f'materials/{name}.high.jpg']])
    for name in PLAIN:
        made[name] = ('materials', [[f'materials/{name}.jpg']])
    for name in CUT_OUT + ['sprigs-form']:
        made[name] = ('materials', [[f'materials/{name}.png']])
    for model in MODELS:
        for which in ('colour', 'surface'):
            made[f'{model}.{which}'] = ('models', [[f'models/{model}.{which}.gz']])
    made['cabins-detail.surface'] = ('cabins', [[f'cabins/detail.surface.{k}.gz'] for k in range(8)])
    for cabin in CABINS:
        for which in ('colour', 'surface'):
            made[f'{cabin}.{which}'] = ('cabins', [[f'cabins/{cabin}.{which}.gz']])
    return made


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def encoder():
    if not ENCODER.exists():
        ENCODER.parent.mkdir(exist_ok=True)
        print('fetching', ENCODER_URL)
        with urllib.request.urlopen(ENCODER_URL) as response:
            ENCODER.write_bytes(response.read())
    if sha256(ENCODER) != ENCODER_SHA256:
        raise SystemExit(f'{ENCODER} is not the pinned release: delete it and run again')
    return ENCODER


def layer(sources):
    """A layer's texels, four bytes each, as Pictures.java put them together when it read the sources itself, and its gain."""
    first = SOURCES / sources[0]
    if first.suffix == '.gz':
        raw = gzip.decompress(first.read_bytes())
        # A model's pictures are square, or twice as wide as high for a gun in WIDE (tools/models/gunsmith.py, WIDE).
        across = 2 if first.name.split('.')[0] in WIDE else 1
        high = int(round((len(raw) / 4 / across) ** 0.5))
        if high * high * across * 4 != len(raw):
            raise SystemExit(f'{first} is not a picture of four bytes a texel {across} times as wide as high')
        return numpy.frombuffer(raw, dtype=numpy.uint8).reshape(high, high * across, 4).copy(), 1.0
    texels = numpy.array(Image.open(first).convert('RGBA'))
    solid = texels[..., 3] > 127
    light = (texels[..., :3][solid].astype(numpy.int64) * [299, 587, 114]).sum()
    if light == 0:
        raise SystemExit(f'{first} is black or cut away all over')
    gain = 255.0 * 1000.0 * int(solid.sum()) / int(light)
    if len(sources) > 1:
        heights = numpy.asarray(Image.open(SOURCES / sources[1]).convert('RGB'))
        if heights.shape[:2] != texels.shape[:2]:
            raise SystemExit(f'{sources[1]} is not the size of {sources[0]}')
        texels[..., 3] = heights[..., 0]
    return texels, gain


TO_LINEAR = numpy.array([(v / 255 / 12.92 if v <= 10 else ((v / 255 + 0.055) / 1.055) ** 2.4) for v in range(256)], dtype=numpy.float64)


def to_screen(linear):
    return numpy.where(linear <= 0.0031308, linear * 12.92, 1.055 * numpy.power(numpy.maximum(linear, 1e-12), 1 / 2.4) - 0.055)


def halved(level, srgb):
    """The next smaller copy: each texel the mean of the ones it stands for, in linear light where the card reads the colour so."""
    high, wide = level.shape[:2]
    down, across = (2 if high > 1 else 1), (2 if wide > 1 else 1)
    grouped = level.reshape(high // down, down, wide // across, across, 4)
    count = down * across
    if not srgb:
        return ((grouped.astype(numpy.uint32).sum(axis=(1, 3)) + count // 2) // count).astype(numpy.uint8)
    out = numpy.empty((high // down, wide // across, 4), dtype=numpy.uint8)
    out[..., :3] = numpy.clip(to_screen(TO_LINEAR[grouped[..., :3]].mean(axis=(1, 3))) * 255.0 + 0.5, 0, 255).astype(numpy.uint8)
    out[..., 3] = (grouped[..., 3].astype(numpy.uint32).sum(axis=(1, 3)) + count // 2) // count
    return out


def levels_of(texels, srgb):
    levels = [texels]
    while levels[-1].shape[0] > 1 or levels[-1].shape[1] > 1:
        levels.append(halved(levels[-1], srgb))
    return levels


def write_dds(path, texels):
    high, wide = texels.shape[:2]
    form = struct.pack('<II4sIIIII', 32, 0x41, b'\0\0\0\0', 32, 0xff, 0xff00, 0xff0000, 0xff000000)
    head = struct.pack('<4sIIIIIII44s', b'DDS ', 124, 0x100f, high, wide, wide * 4, 0, 1, b'\0' * 44) + form + struct.pack('<IIIII', 0x1000, 0, 0, 0, 0)
    path.write_bytes(head + texels.tobytes())


def blocks(pictures):
    """Each picture as BC7 blocks. One a block's breadth or less across is filled out to a whole block with its own edge."""
    with tempfile.TemporaryDirectory() as folder:
        folder = pathlib.Path(folder)
        (folder / 'out').mkdir()
        names = []
        for index, texels in enumerate(pictures):
            high, wide = texels.shape[:2]
            whole = numpy.pad(texels, ((0, -high % 4), (0, -wide % 4), (0, 0)), mode='edge')
            names.append(f'{index}.dds')
            write_dds(folder / names[-1], whole)
        card = ['-nogpu'] if '--cpu' in sys.argv else []
        ran = subprocess.run([str(encoder()), '-nologo', '-y', '-f', 'BC7_UNORM', '-m', '1', '-dx10'] + card + ['-o', str(folder / 'out')]
                             + [str(folder / name) for name in names], capture_output=True, text=True)
        if ran.returncode != 0:
            raise SystemExit(ran.stdout + ran.stderr)
        made = []
        for name, texels in zip(names, pictures):
            data = (folder / 'out' / name).read_bytes()
            size = -(-texels.shape[0] // 4) * -(-texels.shape[1] // 4) * 16
            # "DDS ", its header of 124 bytes and the DX10 header of 20, then the blocks.
            if data[84:88] != b'DX10' or struct.unpack_from('<I', data, 128)[0] != 98 or len(data) != 148 + size:
                raise SystemExit(f'the encoder did not write BC7 blocks for {name}')
            made.append(data[148:])
        return made


def make(name, folder, layers):
    srgb = name in SRGB
    form = RAW if name in UNPACKED else BC7
    read = [layer(sources) for sources in layers]
    chains = [levels_of(texels, srgb) for texels, _ in read]
    high, wide = read[0][0].shape[:2]
    for sources, (texels, _) in zip(layers, read):
        if texels.shape[:2] != (high, wide):
            raise SystemExit(f'{sources[0]} is not the size of {layers[0][0]}')
    # Level by level, and within a level layer by layer: the order of the file.
    ordered = [chain[level] for level in range(len(chains[0])) for chain in chains]
    packed = [texels.tobytes() for texels in ordered] if form == RAW else blocks(ordered)
    out = OUT / folder / f'{name}.bc'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(struct.pack('<4s6If', b'CUBC', form, wide, high, len(layers), len(chains[0]), int(srgb), read[0][1]) + b''.join(packed))
    print(f'{out.relative_to(ROOT)}: {wide} x {high}, {len(layers)} layer(s), {len(chains[0])} levels, {"BC7" if form == BC7 else "four bytes a texel"}, {out.stat().st_size} bytes')
    return out


def recipe(name):
    """How an output is made, apart from its sources: one that was made another way is made again."""
    if name in UNPACKED:
        return 'plain'
    return ('bc7-screen' if name in SRGB else 'bc7') + '-texconv-' + ENCODER_SHA256[:16]


def listed():
    made = {}
    if MADE.exists():
        output = None
        for line in MADE.read_text().splitlines():
            if line.startswith('\t'):
                source, digest = line[1:].split('\t')
                made[output][2][source] = digest
            else:
                output, digest, how = line.split('\t')
                made[output] = (digest, how, {})
    return made


def main():
    every = outputs()
    asked = [argument for argument in sys.argv[1:] if not argument.startswith('--')]
    for name in asked:
        if name not in every:
            raise SystemExit(f'no output is called {name}: ' + ' '.join(sorted(every)))
    made = listed()
    for name, (folder, layers) in every.items():
        key = f'{folder}/{name}.bc'
        sources = {source: sha256(SOURCES / source) for group in layers for source in group}
        stale = key not in made or made[key][1:] != (recipe(name), sources) or not (OUT / key).exists() or sha256(OUT / key) != made[key][0]
        if name in asked or '--all' in sys.argv or (not asked and stale):
            made[key] = (sha256(make(name, folder, layers)), recipe(name), sources)
    for key in list(made):
        if key.split('/')[1][:-3] not in every:
            del made[key]
    MADE.write_text(''.join(f'{key}\t{digest}\t{how}\n' + ''.join(f'\t{source}\t{each}\n' for source, each in sources.items())
                            for key, (digest, how, sources) in sorted(made.items())), newline='\n')


if __name__ == '__main__':
    main()
