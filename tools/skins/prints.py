"""Makes the blood and dirt the game lays over a whole body, from text prompts alone.

The game has one picture each of blood and of dirt that it lays over a whole body where a mask says
to. This makes the mod's own of both, which FineSkins reads out of the jar in place of the game's.
An OVERLAY is a photograph of dried blood, or of dirt, on white paper, made to join up with itself at its
edges so that no edge of the paper or of a stain runs straight across it. How much of it shows is how
far it darkens the paper there, let down to cover as much of the body as the game's own.

Z-Image Turbo, through the maker tools/materials/generate.py has. Run with the venv that has torch
and diffusers (see .lab/NOTES.md, "Generated materials"):
    python tools/skins/prints.py --out DIR [--only bloodoverlay ...] [--seeds 1 2 3 4]
Writes DIR/<name>-<seed>.png and skips what is there already. With --pack it writes the seed CHOSEN of
each as the mod reads them, a JPEG with a .cut.png beside it that says what shows, into
src/main/resources/skins, and with --sheet FILE a sheet of every picture made to look over.
"""
import argparse
import pathlib
import sys

import numpy
from PIL import Image, ImageFilter

OUT = pathlib.Path(__file__).resolve().parents[2] / 'src' / 'main' / 'resources' / 'skins'
OVERLAYS = {
    'bloodoverlay': 'Looking straight down at plain white paper seen close, so that it fills the whole frame and runs out of it on every side. '
                    'Dried dark red blood is spread unevenly over it: broad wiped smears, streaks, runs and drips, soaked patches and a few small '
                    'spatters, with ragged natural edges and of every strength from thin pale pink to nearly black, and clean white paper between '
                    'them. The stains run off the edges of the frame. A forensic photograph taken in flat even light with no shadows.',
    'grimeoverlay': 'Looking straight down at plain white paper seen close, so that it fills the whole frame and runs out of it on every side. '
                    'Dirt is spread unevenly over it: broad wiped smears of dry brown mud and grey soot, dusty patches and streaks of grime of '
                    'every strength from a faint film of dust to thick dark dirt, with soft feathered edges and clean white paper between them. '
                    'The dirt runs off the edges of the frame. A plain photograph taken in flat even light with no shadows.',
}
# The seed of each overlay that the mod packs, of those made.
CHOSEN = {'bloodoverlay': 3, 'grimeoverlay': 2}


def paper(picture):
    """
    The paper's own colour at each dot, under the stains as well: a smooth surface fitted to the paper alone. The
    photograph's paper is a pale grey that darkens towards the corners: measured against pure white, that grey
    would be a thin blue-grey stain over the whole sheet.
    """
    high, wide = picture.shape[:2]
    ys, xs = numpy.mgrid[0:high:4, 0:wide:4]
    x, y = xs.ravel() / wide - 0.5, ys.ravel() / high - 0.5
    basis = numpy.stack([numpy.ones_like(x), x, y, x * x, x * y, y * y], axis=1)
    seen = picture[::4, ::4].reshape(-1, 3)
    grey = seen.mean(axis=1)
    # The paper is the lightest of the picture: fitted, and fitted again to what is not darker than the fit, down to the paper.
    keep = grey >= numpy.percentile(grey, 50)
    for _ in range(8):
        fitted, *_ = numpy.linalg.lstsq(basis[keep], seen[keep], rcond=None)
        keep = grey >= (basis @ fitted).mean(axis=1) - 0.02
    ys, xs = numpy.mgrid[0:high, 0:wide]
    x, y = xs / wide - 0.5, ys / high - 0.5
    whole = numpy.stack([numpy.ones_like(x), x, y, x * x, x * y, y * y], axis=2)
    return numpy.clip(whole @ fitted, 0.05, 1.0)


# How thick the game's own overlay lies over each eighth of its picture each way, rows from the top, as its shader
# shows it (a stain of alpha a as 1 - (1 - a)^3), and the colour it lays there, 0 to 1. The game multiplies the skin
# by an overlay, and a body's skin puts the face at the top middle: an overlay that lies thicker there than the
# game's darkens every face the game marks at all. The mod's own are made as thick as the game's, place by place.
# For blood the game's shader puts a red of its own in place of the overlay's: of blood's colour only the green and
# blue, which the game's has none of, are the overlay's.
GAME_OVERLAY = {
    'bloodoverlay': ([[0.14, 0.22, 0.22, 0.26, 0.29, 0.21, 0.27, 0.32], [0.18, 0.2, 0.21, 0.46, 0.28, 0.2, 0.34, 0.3],
                      [0.26, 0.31, 0.31, 0.6, 0.52, 0.4, 0.46, 0.23], [0.24, 0.3, 0.46, 0.55, 0.67, 0.59, 0.31, 0.2],
                      [0.09, 0.18, 0.6, 0.59, 0.53, 0.63, 0.19, 0.05], [0.44, 0.36, 0.45, 0.49, 0.43, 0.44, 0.52, 0.38],
                      [0.43, 0.33, 0.37, 0.24, 0.28, 0.27, 0.27, 0.37], [0.37, 0.28, 0.34, 0.11, 0.17, 0.32, 0.26, 0.28]],
                     (0.287, 0.0, 0.0)),
    'grimeoverlay': ([[0.25, 0.11, 0.43, 0.12, 0.09, 0.26, 0.05, 0.18], [0.34, 0.37, 0.11, 0.11, 0.31, 0.14, 0.43, 0.53],
                      [0.34, 0.46, 0.33, 0.31, 0.58, 0.11, 0.53, 0.44], [0.31, 0.4, 0.61, 0.26, 0.44, 0.53, 0.41, 0.27],
                      [0.09, 0.1, 0.6, 0.26, 0.42, 0.37, 0.08, 0.13], [0.0, 0.03, 0.58, 0.49, 0.34, 0.8, 0.06, 0.0],
                      [0.0, 0.0, 0.42, 0.35, 0.37, 0.46, 0.38, 0.0], [0.0, 0.03, 0.69, 0.66, 0.33, 0.81, 0.15, 0.0]],
                     (0.363, 0.246, 0.138)),
}
# The squares the overlay's thickness is matched over, each way, and how many times it is matched again. The whole
# sheet is let down as far as it takes and steepened by MOST_SHEET at most, past which the paper's grain shows as
# specks, and a square only by between LEAST_STEEP and MOST_STEEP of that,
# laid softly: matched square by square over 64 squares with no such bound, a square with little stain in it had the
# paper's grain steepened into a solid block and one under a big stain had its middle let down, so the stains came
# out cut into steps along the squares.
MATCHED_OVER, MATCHINGS = 8, 24
LEAST_STEEP, MOST_STEEP, MOST_SHEET = 0.6, 1.7, 3.0


def shown(alpha):
    """How much of a stain the game's shader shows for an alpha of the overlay's."""
    return 1.0 - (1.0 - alpha) ** 3


def laid(cells, high, wide):
    """A grid of numbers laid smoothly over a picture of this size, with no edge of a square of it to be seen."""
    return numpy.asarray(Image.fromarray(numpy.asarray(cells, dtype=numpy.float32), 'F').resize((wide, high), Image.BICUBIC), dtype=numpy.float32)


# How far a dot may be darker than its paper and still be paper: the grain of the paper and the photograph's noise.
PAPER_GRAIN = 0.04
# How much of the paper's light a stain takes away where it is solid, nothing of the paper through it.
STAIN_SOLID = 0.55
# The most and least a channel of a stain's colour is scaled to come to the game's colour: a channel the game has none
# of keeps a little, so the stain is not flat.
LEAST_GAIN, MOST_GAIN = 0.25, 4.0


def soaked(image, name):
    """An overlay as a colour and how much of it shows: the stain's own colour, and as much of it as it darkens the paper it is on."""
    picture = numpy.asarray(image.convert('RGB'), dtype=numpy.float32) / 255.0
    under = paper(picture)
    # How thick the stain is: one that takes STAIN_SOLID of the paper's light away is all stain.
    thick = numpy.clip((1.0 - (picture / under).min(axis=2) - PAPER_GRAIN) / STAIN_SOLID, 0.0, 1.0)
    # A thin stain is the stain's colour let down with the paper's: the paper is taken back out.
    colour = numpy.clip((picture - under * (1.0 - thick[..., None])) / numpy.maximum(thick[..., None], 0.05), 0.0, 1.0)
    cells, tint = GAME_OVERLAY[name]
    # As much of it shows as it is thick, let down or steepened place by place until it lies as thick as the game's.
    high, wide = thick.shape
    wanted = laid(cells, MATCHED_OVER, MATCHED_OVER)
    steep = numpy.ones((MATCHED_OVER, MATCHED_OVER), dtype=numpy.float32)
    for _ in range(MATCHINGS):
        shows = numpy.clip(thick * laid(steep, high, wide), 0.0, 1.0)
        lies = shown(shows).reshape(MATCHED_OVER, high // MATCHED_OVER, MATCHED_OVER, wide // MATCHED_OVER).mean(axis=(1, 3))
        steep = steep * numpy.clip((wanted + 0.01) / (lies + 0.01), 0.5, 2.0)
        sheet = numpy.exp(numpy.log(steep).mean())
        steep = min(sheet, MOST_SHEET) * numpy.clip(steep / sheet, LEAST_STEEP, MOST_STEEP)
    shows = numpy.clip(thick * laid(steep, high, wide), 0.0, 1.0)
    mean = (colour * shows[..., None]).sum(axis=(0, 1)) / shows.sum()
    colour = numpy.clip(colour * numpy.clip(numpy.array(tint) / numpy.maximum(mean, 1e-4), LEAST_GAIN, MOST_GAIN), 0.0, 1.0)
    # Drawn small, a dot of the overlay is mixed from those round it whether they show or not: the stain's colour is
    # carried out over the paper, or the paper's would rim every stain. An overlay's edge is soft, and a dot that
    # shows at all keeps its own colour.
    bare = shows < 0.05
    for reach in (2, 4, 8, 16, 32, 64):
        weight = numpy.asarray(Image.fromarray((shows * 255).round().astype(numpy.uint8), 'L').filter(ImageFilter.GaussianBlur(reach)), dtype=numpy.float32) / 255.0
        spread = numpy.stack([numpy.asarray(Image.fromarray((colour[..., c] * shows * 255).round().astype(numpy.uint8), 'L').filter(ImageFilter.GaussianBlur(reach)),
                                            dtype=numpy.float32) / 255.0 for c in range(3)], axis=2)
        fill = bare & (weight > 0.01)
        colour[fill] = numpy.clip(spread[fill] / weight[fill][..., None], 0.0, 1.0)
        bare &= ~fill
    return (Image.fromarray((colour * 255).round().astype(numpy.uint8), 'RGB'), Image.fromarray((shows * 255).round().astype(numpy.uint8), 'L'))


def chosen(pictures, name):
    if name not in CHOSEN:
        raise SystemExit(f'{name}: no seed of it is CHOSEN')
    return pictures / f'{name}-{CHOSEN[name]}.png'


def pack(pictures):
    for name in OVERLAYS:
        colour, shows = soaked(Image.open(chosen(pictures, name)), name)
        folder = OUT / 'bloodtextures'
        folder.mkdir(parents=True, exist_ok=True)
        colour.save(folder / f'{name}.jpg', quality=90)
        shows.save(folder / f'{name}.cut.png', optimize=True)
    print('packed', len(OVERLAYS), 'overlays', flush=True)


def sheet(pictures, path):
    """Every overlay made so far as it would be packed, a row to a name and a column to a seed."""
    made = {}
    for file in sorted(pictures.glob('*-*.png')):
        name, seed = file.stem.rsplit('-', 1)
        if name in OVERLAYS:
            made.setdefault(name, []).append(file)
    across = max(len(files) for files in made.values())
    out = Image.new('RGB', (across * 256, len(made) * 256), (110, 110, 118))
    for row, (name, files) in enumerate(made.items()):
        for column, file in enumerate(files):
            image, shows = soaked(Image.open(file).convert('RGB').resize((512, 512), Image.LANCZOS), name)
            out.paste(image.resize((256, 256), Image.LANCZOS), (column * 256, row * 256), shows.resize((256, 256), Image.LANCZOS))
    out.save(path, quality=88)
    print('sheet of', sum(len(files) for files in made.values()), flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', required=True)
    parser.add_argument('--only', nargs='*', default=[])
    parser.add_argument('--seeds', nargs='*', type=int, default=[1, 2, 3, 4])
    parser.add_argument('--pack', action='store_true')
    parser.add_argument('--sheet')
    args = parser.parse_args()
    out = pathlib.Path(args.out)
    if args.pack:
        pack(out)
        return
    if args.sheet:
        sheet(out, args.sheet)
        return
    out.mkdir(parents=True, exist_ok=True)
    unknown = [name for name in args.only if name not in OVERLAYS]
    if unknown:
        raise SystemExit('no such picture: ' + ' '.join(unknown))
    wanted = [(name, seed) for name in OVERLAYS if not args.only or name in args.only for seed in args.seeds
              if not (out / f'{name}-{seed}.png').exists()]
    if not wanted:
        print('nothing to make', flush=True)
        return
    sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / 'materials'))
    import generate as materials
    maker = materials.Maker()
    words = maker.words({name: OVERLAYS[name] for name in dict.fromkeys(name for name, _ in wanted)})
    for name, seed in wanted:
        maker.make(words[name], seed, 1024, 1024, True).save(out / f'{name}-{seed}.png')
        print('made', f'{name}-{seed}', flush=True)


if __name__ == '__main__':
    main()
