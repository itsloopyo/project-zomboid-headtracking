"""Makes the pictures the skins of zombies and of the living are baked from, from text prompts alone.

Two sorts. A SURFACE is a close picture of skin, of what has become of it, or of cloth, that joins
up with itself at its edges. A FACE is one bald head seen from straight ahead. The first face of a
zombie of a sex and a skin tone is made from its prompt alone; the three states of decay after it
are that same picture given back to the model part noised with a prompt that says how far gone it
is, so the eyes and the mouth stay where they were and one measuring of them serves all four. The
face of the living of a sex and a tone, live-m1 to live-f5, is made from its prompt alone. Every
tone of a sex is asked for in the same words but for the skin, so that no tone is drawn as a
different sort of face from the others.

Z-Image Turbo, through the maker tools/materials/generate.py has. Run with the venv that has torch and
diffusers (see .lab/NOTES.md, "Generated materials"):
    python tools/skins/generate.py --out DIR [--only skin face-m1-0 ...] [--starting face-m] [--seeds 1 2 3 4]
Writes DIR/<name>-<seed>.png and skips what is there already. A state of decay is made only from the
first face bake.PICKED names, and is written with that face's seed. tools/skins/bake.py lays them on the bodies.
"""
import argparse
import pathlib
import sys

from PIL import Image

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / 'materials'))
import generate as materials  # noqa: E402
import bake  # noqa: E402

FLAT = (' It fills the whole frame from edge to edge and is the same all over, with nothing else in sight. A plain reference photograph in'
        ' flat even light with no shadows, with natural colours and fine film grain.')
SURFACES = {
    'skin': ('A close macro photograph of a small flat patch of pale human skin on a forearm, lit evenly from straight in front: soft pores, fine '
             'vellus hairs and a few tiny freckles, an even tone, with no folds, creases or shading across it.' + FLAT),
    'rot': 'A close photograph of necrotic gangrenous skin: black and dark brown dry cracked scabs, peeling at their edges.' + FLAT,
    'gore': 'A close forensic photograph of a raw open flesh wound: torn dark red muscle tissue and clotted dark blood, wet.' + FLAT,
    'cotton': 'A close photograph of plain white cotton underwear fabric laid flat: a fine matte jersey knit with a soft brushed surface and no '
              'sheen, with no lace, no print and no seams.' + FLAT,
}

SEXES = {'m': 'man', 'f': 'woman'}
# What a zombie's skin tone is called, palest first: the game has four.
TONES = {
    1: 'very pale grey white bloodless skin',
    2: 'pale waxy cream skin',
    3: 'sallow yellowish olive skin',
    4: 'very dark brown skin gone dull and ashen',
}
# How far gone, as the game counts it: just turned, then three states of decay.
STATES = {
    0: 'Recently dead: the face is slack, with dark hollows round the eyes and a little dried blood at the corner of the mouth.',
    1: 'Several days dead: sunken cheeks, dark veins showing under the skin, bruised hollows round the eyes and dried blood at the mouth.',
    2: 'Rotting: a sunken face with patches of skin torn and hanging loose, open sores, dark veins and dried blood down the chin.',
    3: 'Badly rotted: blackened peeling skin, one cheek torn open to the dark flesh and the ordinary teeth behind it, deep wounds and caked blood.',
}
# How much of a first face is noise again when each state of decay is made from it: enough to rot it, not enough to move its eyes.
AGAIN = {1: 0.64, 2: 0.7, 3: 0.76}
PASSPORT = (' Plain grey background. A real photograph taken in flat even studio light, showing natural human skin with its pores, fine lines'
            ' and small blemishes.')


def face(sex, tone, state):
    return (f'A passport photograph of the face of an ordinary middle aged {SEXES[sex]} who has died and become a walking corpse, seen from '
            'straight in front: a bald head with no hair at all, centred and facing the camera, the face symmetrical and of ordinary '
            f'proportions, with {TONES[tone]}. {STATES[state]} Both eyes are milky white and clouded over with no pupils to be seen, the lips '
            'are pale grey and dry, and the mouth hangs slightly open.' + PASSPORT)


FACES = {f'face-{sex}{tone}-{state}': (sex, tone, state) for sex in SEXES for tone in TONES for state in STATES}

# The game has five skin tones for the living, palest first.
LIVING_TONES = {
    1: 'very fair pale pink skin',
    2: 'fair light beige skin',
    3: 'light olive tan skin',
    4: 'medium brown skin',
    5: 'dark brown skin',
}


def living(sex, tone):
    return (f'A passport photograph of an ordinary {SEXES[sex]} thirty five years old with a cleanly shaved bald head, seen from straight in '
            'front: the head centred and facing the camera, the face symmetrical and of ordinary proportions, with '
            f'{LIVING_TONES[tone]} and natural eyebrows. A calm neutral expression, the mouth closed and the eyes open looking into the '
            'camera, with no make-up and no jewellery.' + PASSPORT)


LIVING = {f'live-{sex}{tone}': (sex, tone) for sex in SEXES for tone in LIVING_TONES}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', required=True)
    parser.add_argument('--only', nargs='*', default=[])
    parser.add_argument('--starting', nargs='*', default=[])
    parser.add_argument('--seeds', nargs='*', type=int, default=[1, 2, 3, 4], help='for the surfaces, first faces and faces of the living')
    args = parser.parse_args()
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    names = list(SURFACES) + list(FACES) + list(LIVING)
    unknown = [name for name in args.only if name not in names]
    if unknown:
        raise SystemExit('no such picture: ' + ' '.join(unknown))
    chosen = bool(args.only or args.starting)
    wanted = []
    for name in names:
        if chosen and name not in args.only and not any(name.startswith(start) for start in args.starting):
            continue
        if name in FACES and FACES[name][2] > 0:
            first = name[:-2]
            if first not in bake.PICKED:
                print(f'{name}: no first face of {first} is picked in bake.PICKED, so it is not made', flush=True)
                continue
            seeds = [bake.PICKED[first]]
        else:
            seeds = args.seeds
        wanted += [(name, seed) for seed in seeds if not (out / f'{name}-{seed}.png').exists()]
    # A state of decay is made from its first face, which has to be there first.
    wanted.sort(key=lambda job: job[0] in FACES and FACES[job[0]][2] > 0)
    if not wanted:
        print('nothing to make', flush=True)
        return

    prompts = {}
    for name, _ in wanted:
        prompts[name] = SURFACES[name] if name in SURFACES else living(*LIVING[name]) if name in LIVING else face(*FACES[name])
    maker = materials.Maker()
    words = maker.words(prompts)
    for name, seed in wanted:
        if name in FACES and FACES[name][2] > 0:
            state = FACES[name][2]
            first = Image.open(out / f'{name[:-2]}-0-{seed}.png')
            image = maker.over(words[name], seed + state, first, AGAIN[state], False)
        else:
            image = maker.make(words[name], seed, 1024, 1024, name in SURFACES)
        image.save(out / f'{name}-{seed}.png')
        print('made', f'{name}-{seed}', flush=True)


if __name__ == '__main__':
    main()
