"""Makes the close pictures of cloth, leather and hair that what people wear is surfaced with, from text prompts alone.

The game paints what a person wears at 64 to 512 dots: enough to say what colour each part of a garment
is, not what it is made of. The mod keeps the game's picture for the first and lays one of these
over it for the second (Tailor.java). Each is a photograph of one material from close by, lit
flat, that joins up with itself at its edges. Only its light and dark is used, so its colour does
not matter, and it is asked for in a pale grey so that neither end of it is lost.

Stable Diffusion XL base 1.0, through the maker tools/materials/generate.py has. Run with the venv
that has torch and diffusers (see .lab/NOTES.md, "Generated materials"):
    python tools/skins/fabrics.py --out DIR [--only weave hair ...] [--seed 7]
Writes DIR/<name>.png at 1024, or DIR/<name>-<seed>.png for a seed other than the first, to choose
between, and skips what is there already. With --pack it writes the chosen ones, as one channel at
1024, into src/main/resources/fabric instead of making any.
"""
import argparse
import pathlib
import sys

import numpy
from PIL import Image

OUT = pathlib.Path(__file__).resolve().parents[2] / 'src' / 'main' / 'resources' / 'fabric'
EVEN = 'filling the whole frame edge to edge, laid perfectly flat, flat even shadowless lighting, seamless texture, photorealistic, sharp focus, highly detailed'
FABRICS = {
    'weave': 'extreme macro photograph of plain pale grey cotton shirt fabric, tight plain weave, every warp and weft thread visible, ' + EVEN,
    'twill': 'flatbed scanner image of pale grey denim jeans fabric pressed flat against the glass, fine diagonal twill lines, cotton threads visible, ' + EVEN,
    'knit': 'extreme macro photograph of pale grey cotton jersey t-shirt fabric, fine machine knit, tiny v shaped stitches in vertical columns, ' + EVEN,
    'wool': 'flatbed scanner image of a pale grey machine knitted wool sweater pressed flat against the glass, small even stocking stitch, rows of tiny v shaped stitches, ' + EVEN,
    'leather': 'flatbed scanner image of smooth pale grey cowhide jacket leather pressed flat against the glass, very fine natural grain, tiny pores and hairline creases, matte, ' + EVEN,
    'sleek': 'flatbed scanner image of pale grey nylon windbreaker fabric pressed flat against the glass, very fine smooth tight weave, ' + EVEN,
    # Asked for as ripstop nylon, and it came out as this.
    'canvas': 'extreme macro photograph of pale grey ripstop nylon fabric, very fine tight weave with a faint square grid of thicker threads, ' + EVEN,
    'hair': 'macro photograph of long straight pale blonde human hair combed straight down, thousands of fine parallel strands running vertically from top to bottom, ' + EVEN,
}
NEGATIVE = ('folds, creases, wrinkles, drapery, seam, stitching line, button, label, hem, edge of the cloth, shadows, perspective, objects, pattern, print, stripes, '
            'text, watermark, blurry, painting, illustration, drawing, vignette, border, 3d render')
OWN_NEGATIVE = {
    'hair': 'face, scalp, skin, ear, parting, curls, waves, braid, hair tie, comb, shadows, perspective, text, watermark, blurry, painting, illustration, drawing, vignette, border, 3d render',
    'leather': 'folds, seam, stitching, button, shadows, perspective, objects, scales, petals, crocodile, snake, cracked, pebbles, text, watermark, blurry, painting, illustration, vignette, border, 3d render',
    'wool': 'chunky, thick rope, cables, braids, chain, holes, folds, seam, button, shadows, perspective, objects, text, watermark, blurry, painting, illustration, vignette, border, 3d render',
}
FIRST_SEED = 1993
# The seed each fabric in the mod came out of, where the first gave folds, a pattern or nothing at all.
CHOSEN = {'wool': 7, 'leather': 21, 'sleek': 7}
# How far a fabric's light and dark stand from its middle, as a part of the middle, taken all together.
CONTRAST = 0.3


def pack(pictures):
    """The chosen pictures as the mod reads them: light and dark only, evened out so that no part of one is lighter than another."""
    OUT.mkdir(parents=True, exist_ok=True)
    for name in FABRICS:
        chosen = f'{name}-{CHOSEN[name]}.png' if name in CHOSEN else f'{name}.png'
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
    parser.add_argument('--seed', type=int, default=FIRST_SEED)
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
    tail = '' if args.seed == FIRST_SEED else f'-{args.seed}'
    wanted = [name for name in FABRICS if (not args.only or name in args.only) and not (out / f'{name}{tail}.png').exists()]
    if not wanted:
        print('nothing to make', flush=True)
        return
    sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / 'materials'))
    import generate as materials
    maker = materials.Maker()
    words = maker.words({name: (FABRICS[name], OWN_NEGATIVE.get(name, NEGATIVE)) for name in wanted})
    for name in wanted:
        maker.make(words[name], args.seed, 1024, True, 6.0).save(out / f'{name}{tail}.png')
        print('made', name, flush=True)


if __name__ == '__main__':
    main()
