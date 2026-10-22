"""Makes the pictures the skins of animals are baked from, from text prompts alone.

Every one is a close picture of a coat, a skin or a horn filling the frame, made to join up with itself at
its edges as a surface of tools/materials/generate.py is. Where a breed is patched, the patches are
tools/skins/animal_bake.py's, laid by place on the body.

Z-Image Turbo, through the maker tools/materials/generate.py has. Run with the venv that has torch and
diffusers (see .lab/NOTES.md, "Generated materials"):
    python tools/skins/animal_pictures.py --out DIR [--only coat-black ...] [--starting coat-] [--seeds 1 2 3 4]
Writes DIR/<name>-<seed>.png, 1024 square, and skips what is there already. A tile of a surface is laid at about a hundred dots
across in a skin, so a larger picture would only be made smaller again.
"""
import argparse
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / 'materials'))
import generate as materials  # noqa: E402

CLOSE = 'A close photograph of '
WHOLE = ('. It fills the whole frame from edge to edge and is the same all over, with no animal, no edge and nothing else in sight. A plain'
         ' reference photograph in soft even overcast light with no shadows, in natural colours with fine film grain.')
# Each asks for a material and not for the animal it comes from: asked for "the coat on the flank of a cow", the model drew the cow,
# and for raccoon, rat, rabbit or pig fur or skin, a crowd of them.
SURFACES = {
    'coat-black': CLOSE + 'black hair-on cowhide: short dense glossy black hair, fine straight hairs lying flat and all running one way' + WHOLE,
    'coat-white': CLOSE + 'white hair-on cowhide: short dense clean white hair, fine straight hairs lying flat and all running one way' + WHOLE,
    'coat-red': CLOSE + 'red brown hair-on cowhide: short dense copper red brown hair, fine straight hairs lying flat and all running one way' + WHOLE,
    'skin-udder': CLOSE + 'soft pink skin with sparse fine short white hairs and soft wrinkles' + WHOLE,
    'hoof': CLOSE + 'dark grey black horn keratin with fine parallel growth ridges, dry and dusty' + WHOLE,
    'pig-pink': CLOSE + 'a flat taut stretch of smooth pale pink hide: soft and slightly mottled, with sparse fine pale bristles and faint shallow wrinkles' + WHOLE,
    'pig-black': CLOSE + 'a flat taut stretch of dark grey leathery hide with sparse coarse black bristles lying flat on it' + WHOLE,
    'wool-cream': CLOSE + 'thick cream white raw sheep wool fleece in dense crimped locks' + WHOLE,
    'coat-deer': CLOSE + 'hair-on deerskin: short greyish brown deer hair, fine hairs lying flat and all running one way' + WHOLE,
    'feathers-red': CLOSE + 'glossy reddish brown hen feathers overlapping in loose rows' + WHOLE,
    'feathers-white': CLOSE + 'clean white hen feathers overlapping in loose rows' + WHOLE,
    'feathers-turkey': CLOSE + ('the body feathers of a wild turkey overlapping like roof shingles: each rounded feather dark bronze with a coppery '
                                'green sheen, a black band near its end and a pale buff tip, every feather soft edged and a little different') + WHOLE,
    'down-yellow': CLOSE + 'pale yellow fluffy marabou fibres, soft wispy down' + WHOLE,
    'bird-leg': CLOSE + 'the yellow scaly skin of a chicken leg, small overlapping scales' + WHOLE,
    'fur-brown': CLOSE + 'a soft brown agouti fur pelt laid flat: fine dense hairs, each banded tan and dark brown, lying flat and all running one way' + WHOLE,
    'fur-grey': CLOSE + 'a dense grey fur pelt laid flat: a soft underfur with longer black tipped guard hairs, lying flat and all running one way' + WHOLE,
    'fur-white': CLOSE + 'a soft clean pure white fur pelt laid flat: fine dense short hairs lying flat and all running one way' + WHOLE,
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', required=True)
    parser.add_argument('--only', nargs='*', default=[])
    parser.add_argument('--starting', nargs='*', default=[])
    parser.add_argument('--seeds', nargs='*', type=int, default=[1, 2, 3, 4])
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
    words = maker.words({name: SURFACES[name] for name, _ in wanted})
    for name, seed in wanted:
        maker.make(words[name], seed, 1024, 1024, True).save(out / f'{name}-{seed}.png')
        print('made', f'{name}-{seed}', flush=True)


if __name__ == '__main__':
    main()
