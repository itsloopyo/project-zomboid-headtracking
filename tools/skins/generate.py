"""Makes the pictures the zombies' skins are baked from, from text prompts alone.

Two sorts. A SURFACE is a close picture of skin, of what has become of it, or of cloth, that joins
up with itself at its edges. A FACE is one bald head seen from straight ahead. The first face of a
sex and a skin tone is made from its prompt alone; the three states of decay after it are that
same picture given back to the model part noised with a prompt that says how far gone it is, so
the eyes and the mouth stay where they were and one measuring of them serves all four.

Stable Diffusion XL base 1.0, through the maker tools/materials/generate.py has. Run with the venv
that has torch and diffusers (see .lab/NOTES.md, "Generated materials"):
    python tools/skins/generate.py --out DIR [--only skin-1 face-m1-0 ...] [--starting face-m] [--most 12]
Writes DIR/<name>.png and skips what is there already. tools/skins/bake.py lays them on the bodies.
"""
import argparse
import pathlib
import sys

import numpy
import torch
from PIL import Image

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / 'materials'))
import generate as materials  # noqa: E402

EVEN = 'flat even lighting, seamless texture, photorealistic, sharp focus'
# What a skin tone is called on a stretch of skin, and on a face.
TONES = {
    1: ('very pale grey white bloodless', 'very pale grey white bloodless skin'),
    2: ('pale waxy cream', 'pale waxy cream skin'),
    3: ('sallow yellowish olive', 'sallow yellowish olive skin'),
    4: ('very dark brown', 'African, very dark brown black skin gone ashen'),
}
SURFACES = {
    'skin': 'macro photograph of human skin texture filling the entire frame edge to edge, pale skin, soft pores, fine vellus hairs, a few tiny freckles, even tone, ' + EVEN,
    'rot': 'macro photograph of necrotic gangrenous skin filling the entire frame edge to edge, black and dark brown dry cracked scabs, peeling, ' + EVEN,
}
# Asked to join up with themselves these came out as patterns (a net, a giraffe's hide), so they are
# made as plain photographs and tools/skins/bake.py joins their edges.
PLAIN = {
    'gore': 'forensic close-up photograph of a raw flesh wound filling the frame, torn dark red muscle tissue, clotted dark blood, wet, flat even lighting, photorealistic, sharp focus',
    'cotton': 'close-up photograph of plain white cotton underwear fabric laid flat filling the frame, fine jersey knit texture, flat even lighting, photorealistic, sharp focus',
}
SURFACES.update(PLAIN)
SURFACE_NEGATIVE = ('face, eye, mouth, hand, fingers, nipple, navel, body, back, arm, outline, background, flowers, petals, blisters, cells, honeycomb, '
                    'reptile, elephant, pattern, shadows, text, watermark, blurry, painting, illustration, drawing, vignette, border, 3d render, plastic')
OWN_NEGATIVE = {
    'gore': 'face, eye, mouth, hand, fingers, knife, plate, bone, text, watermark, blurry, painting, illustration, drawing, vignette, border, 3d render',
    'cotton': 'face, skin, body, seam, label, lace, folds, creases, spots, holes, net, print, shadows, text, watermark, blurry, painting, illustration, vignette, border',
}

SEXES = {'m': 'man', 'f': 'woman'}
# How far gone, as the game counts it: just turned, then three states of decay.
STATES = {
    0: 'recently dead, slack face, dark hollows round the eyes, a little dried blood at the mouth',
    1: 'days dead, sunken cheeks, dark veins under the skin, bruised hollows round the eyes, dried blood at the mouth',
    2: 'rotting, sunken face, torn skin hanging loose, open sores, dark veins, dried blood down the chin',
    3: 'badly rotted corpse, blackened peeling skin, cheek torn open to dark flesh and teeth, deep wounds, caked blood',
}
FACE_NEGATIVE = ('hair, beard, hat, hood, glasses, earrings, jewellery, makeup, smile, profile, three quarter view, tilted head, looking away, hands, '
                 'shadows, dramatic lighting, mask, picture frame, border, vignette, black and white, monochrome, sepia, antique photograph, painting, illustration, drawing, cartoon, 3d render, text, watermark, blurry')
# How much of a first face is noise again when each state of decay is made from it: enough to rot it, not enough to move its eyes.
AGAIN = {1: 0.64, 2: 0.7, 3: 0.76}


def face(sex, tone, state):
    return (f'passport photograph of a zombie, front view of the face of a bald dead {SEXES[sex]}, walking corpse, {TONES[tone][1]}, {STATES[state]}, '
            'cloudy white dead eyes, mouth slightly open, head centred and facing the camera, symmetrical, '
            'flat even lighting, plain grey background, photorealistic, sharp focus, skin pores')


FACES = {f'face-{sex}{tone}-{state}': (sex, tone, state) for sex in SEXES for tone in TONES for state in STATES}
# One seed for each sex, so the four tones of it are framed alike. The fourth woman came out of hers as an old brown photograph.
SEEDS = {'m': 1993, 'f': 7}
OWN_SEEDS = {'face-f4': 1993}


def pixels(picture):
    return Image.fromarray(((picture[0].permute(1, 2, 0).cpu().numpy() + 1.0) * 127.5).round().clip(0, 255).astype(numpy.uint8), 'RGB')


@torch.no_grad()
def decayed(maker, words, first, seed, again):
    """A first face given back to the model part noised, to come out as the words say."""
    materials.wrapping(maker.pipe.unet, False)
    picture = torch.from_numpy(numpy.asarray(first.convert('RGB')).astype(numpy.float32) / 127.5 - 1.0).permute(2, 0, 1)[None].to('cuda')
    latents = maker.taken(picture, False).to(torch.float16)
    latents = maker.again(**maker.given(words), image=latents, strength=again, num_inference_steps=40, guidance_scale=7.0,
                          generator=torch.Generator('cuda').manual_seed(seed), output_type='latent').images
    return pixels(maker.shown(latents, False))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', required=True)
    parser.add_argument('--only', nargs='*', default=[])
    parser.add_argument('--starting', nargs='*', default=[])
    parser.add_argument('--most', type=int, default=0)
    parser.add_argument('--seed', type=int, default=1993, help='for the surfaces; any but 1993 is written as <name>-<seed>.png, to choose between')
    args = parser.parse_args()
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    names = list(SURFACES) + list(FACES)
    tail = '' if args.seed == 1993 else f'-{args.seed}'
    unknown = [name for name in args.only if name not in names]
    if unknown:
        raise SystemExit('no such picture: ' + ' '.join(unknown))
    chosen = bool(args.only or args.starting)
    wanted = [name for name in names
              if (not chosen or name in args.only or any(name.startswith(start) for start in args.starting))
              and not (out / f'{name}{tail if name in SURFACES else ""}.png').exists()]
    # A state of decay is made from its first face, which has to be there.
    wanted = [name for name in wanted
              if name in SURFACES or FACES[name][2] == 0 or (out / f'{name[:-1]}0.png').exists() or f'{name[:-1]}0' in wanted]
    wanted.sort(key=lambda name: name in FACES and FACES[name][2] > 0)
    if args.most:
        wanted = wanted[:args.most]
    if not wanted:
        print('nothing to make', flush=True)
        return

    prompts = {}
    for name in wanted:
        prompts[name] = (SURFACES[name], OWN_NEGATIVE.get(name, SURFACE_NEGATIVE)) if name in SURFACES else (face(*FACES[name]), FACE_NEGATIVE)
    maker = materials.Maker()
    words = maker.words(prompts)
    for name in wanted:
        if name in SURFACES:
            image = maker.make(words[name], args.seed, 1024, name not in PLAIN, 6.0)
        else:
            sex, _, state = FACES[name]
            if state == 0:
                image = maker.make(words[name], OWN_SEEDS.get(name[:-2], SEEDS[sex]), 1024, False, 7.0)
            else:
                image = decayed(maker, words[name], Image.open(out / f'{name[:-1]}0.png'), OWN_SEEDS.get(name[:-2], SEEDS[sex]) + state, AGAIN[state])
        image.save(out / f'{name}{tail if name in SURFACES else ""}.png')
        print('made', name, flush=True)


if __name__ == '__main__':
    main()
