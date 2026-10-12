"""Makes the pictures the skins of animals are baked from, from text prompts alone.

Every one is a close picture of a coat, a skin or a horn filling the frame, made as a plain photograph:
asked to join up with itself a surface comes out as a pattern, so tools/skins/animal_bake.py joins its
edges instead. Where a breed is patched, the patches are the bake's, laid by place on the body.

Stable Diffusion XL base 1.0, through the maker tools/materials/generate.py has. Run with the venv that
has torch and diffusers (see .lab/NOTES.md, "Generated materials"):
    python tools/skins/animal_pictures.py --out DIR [--only coat-black ...] [--starting coat-] [--seeds 1993 7]
Writes DIR/<name>-<seed>.png, 1024 square, and skips what is there already. A tile of a surface is laid at about a hundred dots
across in a skin, so a larger picture would only be made smaller again.
"""
import argparse
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / 'materials'))
import generate as materials  # noqa: E402

MACRO = 'macro photograph of '
WHOLE = ' filling the whole frame edge to edge, uniform texture, flat even overcast lighting, photorealistic, sharp focus, highly detailed'
# Each asks for a material and not for the animal it comes from: asked for "the coat on the flank of a cow", the model drew the cow.
SURFACES = {
    'coat-black': MACRO + 'short dense glossy black hair, black hair-on cowhide, fine straight hairs lying flat all running one way' + WHOLE,
    'coat-white': MACRO + 'short dense clean white hair, white hair-on cowhide, fine straight hairs lying flat all running one way' + WHOLE,
    'coat-red': MACRO + 'short dense copper red brown hair, red brown hair-on cowhide, fine straight hairs lying flat all running one way' + WHOLE,
    'skin-udder': MACRO + 'soft pink skin with sparse fine short white hairs and soft wrinkles' + WHOLE,
    'hoof': MACRO + 'dark grey black horn keratin, fine parallel growth ridges, dry and dusty' + WHOLE,
    'pig-pink': MACRO + 'pink pigskin with sparse fine pale bristles and faint wrinkles' + WHOLE,
    'pig-black': MACRO + 'dark grey pigskin with coarse sparse black bristles' + WHOLE,
    'wool-cream': MACRO + 'thick cream white raw sheep wool fleece, dense crimped locks' + WHOLE,
    'coat-deer': MACRO + 'short greyish brown deer hair, hair-on deerskin, fine hairs lying flat all running one way' + WHOLE,
    'feathers-red': MACRO + 'overlapping glossy reddish brown hen feathers in rows' + WHOLE,
    'feathers-white': MACRO + 'overlapping clean white hen feathers in rows' + WHOLE,
    'feathers-turkey': MACRO + 'overlapping bronze iridescent turkey feathers in rows, dark bands and pale tips' + WHOLE,
    'down-yellow': MACRO + 'pale yellow fluffy marabou fibres, soft wispy down' + WHOLE,
    'bird-leg': MACRO + 'yellow scaly skin of a chicken leg, small overlapping scales' + WHOLE,
    'fur-brown': MACRO + 'soft brown agouti rabbit fur, fine dense hairs' + WHOLE,
    'fur-grey': MACRO + 'grey raccoon fur, dense underfur with longer black tipped guard hairs' + WHOLE,
    'fur-white': MACRO + 'soft clean white rat fur, fine dense hairs' + WHOLE,
}
NEGATIVE = ('animal, cow, pig, sheep, bird, face, head, eye, ear, nose, nostril, mouth, legs, feet, chain, collar, tag, rope, grass, field, fence, sky, '
            'background, person, hand, object, text, watermark, blurry, painting, illustration, drawing, vignette, border, 3d render, plastic')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', required=True)
    parser.add_argument('--only', nargs='*', default=[])
    parser.add_argument('--starting', nargs='*', default=[])
    parser.add_argument('--seeds', nargs='*', type=int, default=[1993])
    args = parser.parse_args()
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    unknown = [name for name in args.only if name not in SURFACES]
    if unknown:
        raise SystemExit('no such picture: ' + ' '.join(unknown))
    chosen = bool(args.only or args.starting)
    wanted = [(name, seed) for name in SURFACES for seed in args.seeds
              if (not chosen or name in args.only or any(name.startswith(start) for start in args.starting))
              and not (out / f'{name}-{seed}.png').exists()]
    if not wanted:
        print('nothing to make', flush=True)
        return
    maker = materials.Maker()
    words = maker.words({name: (SURFACES[name], NEGATIVE) for name, _ in wanted})
    for name, seed in wanted:
        maker.make(words[name], seed, 1024, False, 6.0).save(out / f'{name}-{seed}.png')
        print('made', f'{name}-{seed}', flush=True)


if __name__ == '__main__':
    main()
