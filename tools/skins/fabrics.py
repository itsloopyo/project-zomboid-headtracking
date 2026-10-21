"""Makes the close pictures of cloth, leather and hair that what people wear is surfaced with, from text prompts alone.

The game paints what a person wears at 64 to 512 dots: enough to say what colour each part of a garment
is, not what it is made of. The mod keeps the game's picture for the first and lays one of these
over it for the second (Tailor.java). Each is a photograph of one material from close by, lit
flat, that joins up with itself at its edges. Only its light and dark is used, so its colour does
not matter, and it is asked for in a pale grey so that neither end of it is lost.

Z-Image Turbo, through the maker tools/materials/generate.py has, joined up at its edges as a surface is.
Run with the venv that has torch and diffusers (see .lab/NOTES.md, "Generated materials"):
    python tools/skins/fabrics.py --out DIR [--only weave hair ...] [--seeds 1 2 3 4]
Writes DIR/<name>-<seed>.png at 1024, to choose between, and skips what is there already. With --pack it
writes the seed CHOSEN of each, as one channel at 1024, into src/main/resources/fabric instead of making any.
"""
import argparse
import pathlib
import sys

import numpy
from PIL import Image

OUT = pathlib.Path(__file__).resolve().parents[2] / 'src' / 'main' / 'resources' / 'fabric'
FLAT = (' It fills the whole frame from edge to edge and lies perfectly flat, with no folds, seams or edges in sight. A flatbed scanner'
        ' image in flat even light with no shadows.')
FABRICS = {
    'weave': 'A close view of plain pale grey cotton shirt fabric: a tight plain weave with every warp and weft thread visible.' + FLAT,
    'twill': 'A close view of pale grey denim cloth: fine diagonal twill lines running across it, with the cotton threads visible.' + FLAT,
    'knit': 'A close view of pale grey cotton jersey T-shirt fabric: a fine machine knit of tiny V shaped stitches in straight vertical columns.' + FLAT,
    # Named as a sweater, a jacket or jeans, the garment itself was drawn, laid out whole.
    'wool': 'A close view of pale grey knitted wool cloth: a small even stocking stitch, rows of tiny V shaped stitches in straight columns, plain '
            'all over with no cables, ridges or pattern.' + FLAT,
    'leather': 'A close view of smooth pale grey cowhide leather: a very fine natural grain with tiny pores and hairline creases, matte.' + FLAT,
    'sleek': 'Pale grey nylon windbreaker fabric pressed flat against the glass of a scanner: a very fine smooth tight weave, clean and even all '
             'over with no stains or marks.' + FLAT,
    'canvas': 'A close view of pale grey ripstop nylon fabric: a very fine tight weave with a faint square grid of thicker threads.' + FLAT,
    'hair': 'A close view of long straight pale blonde human hair combed straight down: thousands of fine parallel strands running vertically '
            'from the top of the frame to the bottom. The hair fills the whole frame from edge to edge, with no scalp, no parting and no ends in '
            'sight. A plain close photograph in flat even light with no shadows.',
}
# The seed each fabric in the mod came out of.
CHOSEN = {'weave': 1, 'twill': 2, 'knit': 1, 'wool': 2, 'leather': 1, 'sleek': 2, 'canvas': 1, 'hair': 2}
# How far a fabric's light and dark stand from its middle, as a part of the middle, taken all together.
CONTRAST = 0.3


def pack(pictures):
    """The chosen pictures as the mod reads them: light and dark only, evened out so that no part of one is lighter than another."""
    OUT.mkdir(parents=True, exist_ok=True)
    for name in FABRICS:
        if name not in CHOSEN:
            raise SystemExit(f'{name}: no seed of it is CHOSEN')
        chosen = f'{name}-{CHOSEN[name]}.png'
        light = numpy.asarray(Image.open(pictures / chosen).convert('L'), dtype=numpy.float32) / 255.0
        # What is broader than the threads is the photograph's lighting, not the cloth: it is taken out, wrapping as the picture does.
        size = light.shape[0]
        broad = numpy.fft.fft2(light)
        along = numpy.minimum(numpy.arange(size), size - numpy.arange(size))
        far = numpy.sqrt(along[:, None] ** 2 + along[None, :] ** 2)
        broad *= numpy.exp(-(far / 6.0) ** 2)
        even = light / numpy.maximum(numpy.real(numpy.fft.ifft2(broad)), 0.05)
        # The middle of it is written as half way up, and every fabric with as much light and dark as the next:
        # a photograph of pale cloth lit flat has very little, and one of a coarse knit a great deal.
        even = even / even.mean() - 1.0
        even = numpy.clip(0.5 + even * (CONTRAST / even.std()) * 0.5, 0.0, 1.0)
        Image.fromarray((even * 255).round().astype(numpy.uint8), 'L').save(OUT / f'{name}.jpg', quality=88)
        print('packed', name, round(float(even.std()), 3), flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', required=True)
    parser.add_argument('--only', nargs='*', default=[])
    parser.add_argument('--seeds', nargs='*', type=int, default=[1, 2, 3, 4])
    parser.add_argument('--pack', action='store_true')
    args = parser.parse_args()
    out = pathlib.Path(args.out)
    if args.pack:
        pack(out)
        return
    out.mkdir(parents=True, exist_ok=True)
    unknown = [name for name in args.only if name not in FABRICS]
    if unknown:
        raise SystemExit('no such fabric: ' + ' '.join(unknown))
    wanted = [(name, seed) for name in FABRICS if not args.only or name in args.only for seed in args.seeds if not (out / f'{name}-{seed}.png').exists()]
    if not wanted:
        print('nothing to make', flush=True)
        return
    sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / 'materials'))
    import generate as materials
    maker = materials.Maker()
    words = maker.words({name: FABRICS[name] for name in dict.fromkeys(name for name, _ in wanted)})
    for name, seed in wanted:
        maker.make(words[name], seed, 1024, 1024, True).save(out / f'{name}-{seed}.png')
        print('made', f'{name}-{seed}', flush=True)


if __name__ == '__main__':
    main()
