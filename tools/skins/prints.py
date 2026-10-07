"""Makes the prints on T-shirts and the blood and dirt laid over a body, from text prompts alone.

The game has sixty prints for the chest of a T-shirt, each 64 dots a side, and one picture each of
blood and of dirt that it lays over a whole body where a mask says to. This makes the mod's own of
all of them, which FineSkins reads out of the jar in place of the game's. A print here is a design of
the mod's own on the theme the game's name gives it, a slice of pizza for a pizza place, a wolf
for a wolf: no brand, no mascot and no lettering is taken from the game's, whose pictures are
never read.

- A PRINT is made on a plain ground, and the ground round it is cut away: what joins the
  picture's border through nothing but the border's own colour is see-through.
- An OVERLAY is a photograph of dried blood, or of dirt, on white paper. How much of the body
  shows through it is how near white the photograph is there.

Stable Diffusion XL base 1.0, through the maker tools/materials/generate.py has. Run with the venv
that has torch and diffusers (see .lab/NOTES.md, "Generated materials"):
    python tools/skins/prints.py --out DIR [--only wolf blood ...] [--most 12]
Writes DIR/<name>.png and skips what is there already. With --pack it writes them as the mod reads
them, a JPEG with a .cut.png beside it that says what shows, into src/main/resources/skins, and
with --sheet FILE a sheet of all of them to look over.
"""
import argparse
import pathlib
import sys

import numpy
from PIL import Image, ImageDraw, ImageFilter

OUT = pathlib.Path(__file__).resolve().parents[2] / 'src' / 'main' / 'resources' / 'skins'
PRINT = ('die cut sticker of {}, bold flat colours, thick black outline, a single design in the middle with a wide empty white margin all round it, '
         'isolated on a plain pure white background, clean vector illustration')
PRINT_NEGATIVE = ('text, letters, words, writing, numbers, banner, ribbon, signature, watermark, photograph, t-shirt, clothing, person, mockup, frame, border, '
                  'round badge, cream paper, beige background, paper texture, halftone background, sunburst background, shadow, grey background, gradient, '
                  'pattern, several designs, blurry')
# The game's name for each print, in small letters, and what the mod's own shows.
PRINTS = {
    'buisness_fossoildecal': 'one single shiny black drop of oil',
    'buisness_fossoillogodecal': 'an oil derrick tower with a gush of black oil from its top',
    'buisness_gas2godecal': 'a red petrol pump with wings',
    'buisness_mccoysdecal': 'one single wood chopping axe with a red head',
    'buisness_pileocrepedecal': 'a tall stack of pancakes with butter and syrup',
    'buisness_pizzawhirleddecal': 'a slice of pepperoni pizza with dripping cheese',
    'buisness_spiffosdecal': 'a cheeseburger with a little flag on top, diner emblem',
    'buisness_spiffoslogodecal': 'a paper cup of fries and a milkshake, diner emblem',
    'buisness_tacodelpanchodecal': 'a crunchy taco wearing a sombrero',
    'buisness_thundergasdecal': 'a yellow lightning bolt in a blue shield',
    'buisness_valleystationdecal': 'two green hills with a rising sun and a road between them',
    'buisness_wokandrollsdecal': 'a bowl of noodles with chopsticks and steam',
    'culture_cardinaldecal': 'one single red cardinal bird standing',
    'culture_shamrockdecal': 'a green four leaf clover',
    'culture_usaflagdecal': 'a waving flag with red and white stripes and a blue corner of white stars',
    'culture_wolfdecal': 'the head of a grey wolf howling at a full moon',
    'ilovekydecal': 'a big red heart over a galloping horse silhouette',
    'rockdecal': 'an electric guitar with wings and flames',
    'spiffo1': 'a cheerful cartoon beaver chef holding a spatula, retro diner mascot',
    'spiffo2': 'a cheerful cartoon beaver waving, retro mascot',
    'spiffo3': 'a cartoon beaver head with a big grin, retro mascot',
    'spiffo4': 'a cartoon beaver in a chef hat giving a thumbs up, retro mascot',
    'spiffo5': 'a cartoon beaver holding a huge burger, retro mascot',
    'spiffo6': 'a cartoon beaver on roller skates carrying a tray, retro diner mascot',
    'spiffo7': 'a cartoon beaver head wearing sunglasses, retro mascot',
    'spiffo8': 'a sleepy cartoon beaver with a cup of coffee, retro mascot',
    'sport_alphadecal': 'a charging bull head in a shield, sports team emblem',
    'sport_baseballkydecal': 'two crossed baseball bats behind a baseball',
    'sport_baseballrangersdecal': 'a baseball with a ranger hat on it, sports team emblem',
    'sport_baseballzdecal': 'a baseball flying with a trail of flames',
    'sport_basketballdecal': 'a basketball going through a hoop',
    'sport_omegadecal': 'a fierce eagle head, sports team emblem',
    'tshirt_alt_1': 'a wilted sunflower in a cracked vase, alternative rock album art',
    'tshirt_alt_2': 'a melting smiley face, alternative rock album art',
    'tshirt_alt_3': 'a broken television with a flower growing out of it, alternative rock album art',
    'tshirt_blues_1': 'a blue acoustic guitar under a crescent moon',
    'tshirt_blues_2': 'a harmonica and a fedora hat',
    'tshirt_blues_3': 'a crossroads signpost at midnight with a crow on it',
    'tshirt_country_1': 'a cowboy boot with a spur and a horseshoe',
    'tshirt_country_2': 'a banjo with a red rose',
    'tshirt_country_3': 'a cowboy hat hanging on a wooden fence post at sunset',
    'tshirt_emd_1': 'a pair of headphones with neon sound waves',
    'tshirt_emd_2': 'a neon pink and blue equaliser shaped like a city skyline',
    'tshirt_emd_3': 'one single vinyl record with a neon pink label',
    'tshirt_hiphop_1': 'one single boombox radio',
    'tshirt_hiphop_2': 'one single gold microphone wearing a small crown',
    'tshirt_hiphop_3': 'one single spray paint can',
    'tshirt_indie_1': 'a paper boat on a wavy sea, simple line drawing',
    'tshirt_indie_2': 'a fox curled asleep in a ring of ferns, simple line drawing',
    'tshirt_indie_3': 'a cassette tape with mountains drawn on its label',
    'tshirt_metal_1': 'a horned goat skull with a pentagram of bones, heavy metal album art',
    'tshirt_metal_2': 'a skeleton hand rising from flames, heavy metal album art',
    'tshirt_metal_3': 'the head of a dragon breathing fire, heavy metal album art',
    'tshirt_punk_1': 'a skull with a tall pink mohawk and a safety pin',
    'tshirt_punk_2': 'one single black combat boot with untied laces',
    'tshirt_punk_3': 'a clenched fist holding a broken bottle, punk fanzine art',
    'tshirt_rock_1': 'a winged skull with a top hat, classic rock tour art',
    'tshirt_rock_2': 'a roaring tiger head with lightning, classic rock tour art',
    'tshirt_rock_3': 'a motorcycle with flames, classic rock tour art',
}
# A print that covers the whole chest and has no shape of its own.
WHOLE = {
    'tshirt_music_tiedye_1': ('a tie dye t-shirt fabric, a spiral of vivid rainbow colours from the middle, filling the whole frame edge to edge, photograph',
                              'text, letters, watermark, person, t-shirt shape, collar, sleeves, border, white background, blurry'),
}
OVERLAYS = {
    'bloodoverlay': ('forensic photograph of dried dark red blood on white paper, spatter, drips, smears and heavy pooled stains scattered over the whole sheet, '
                     'seen from straight above, flat even lighting, photorealistic, sharp focus',
                     'hands, objects, knife, text, ruler, label, watermark, shadows, perspective, painting, illustration, blurry, pink, bright red'),
    'grimeoverlay': ('photograph of thin smears of dry brown mud, dusty finger smudges and soot marks wiped across white paper, scattered thinly over the whole sheet, seen from straight above, '
                     'flat even lighting, photorealistic, sharp focus',
                     'pile, heap, clumps, hands, objects, plants, footprints, text, label, watermark, shadows, perspective, painting, illustration, blurry'),
}
# A print that came out filling its picture, with no ground to cut away: it fades out at its border as one that covers the chest does.
FILLS = ('culture_usaflagdecal', 'tshirt_country_3', 'tshirt_emd_2', 'tshirt_indie_1')
# How unlike the ground a print was made on a dot may be and still be ground, each channel 0 to 255.
GROUND_WITHIN = 34
SEED = 1993
# How much of a print's picture may show once its ground is cut away, and how little: more, and the ground was not
# plain or the design ran to the border; less, and there is no design.
MOST_OF_IT, LEAST_OF_IT = 0.8, 0.08
# The seed of each picture whose first came out with lettering, a second design or a ground that was not white.
OWN_SEEDS = {'buisness_fossoildecal': 33, 'buisness_fossoillogodecal': 7, 'buisness_mccoysdecal': 33, 'culture_cardinaldecal': 33, 'sport_baseballkydecal': 7, 'tshirt_alt_3': 7, 'tshirt_country_3': 21, 'tshirt_emd_2': 21, 'tshirt_emd_3': 33, 'tshirt_hiphop_1': 33, 'tshirt_hiphop_2': 33, 'tshirt_hiphop_3': 33, 'tshirt_indie_1': 21, 'tshirt_metal_3': 33, 'tshirt_punk_2': 33}


def cut(image):
    """What of a print shows: all but the ground it was made on, which is whatever joins the picture's border and is of the border's colour."""
    grey = numpy.asarray(image.convert('RGB'), dtype=numpy.int16)
    # Asked for on white, a print comes on a pale grey that is lighter on one side than the other: the ground's
    # colour at a place is what the picture's border has there, carried across the picture as a slope.
    high, wide = grey.shape[:2]
    ys, xs = numpy.mgrid[0:high, 0:wide]
    edge = (xs < 6) | (xs >= wide - 6) | (ys < 6) | (ys >= high - 6)
    slope = numpy.stack([xs[edge], ys[edge], numpy.ones(edge.sum())], axis=1).astype(numpy.float64)
    fitted, *_ = numpy.linalg.lstsq(slope, grey[edge].astype(numpy.float64), rcond=None)
    ground = numpy.stack([xs, ys, numpy.ones_like(xs)], axis=2).astype(numpy.float64) @ fitted
    white = (numpy.abs(grey - ground).max(axis=2) <= GROUND_WITHIN).astype(numpy.uint8) * 255
    ground = Image.fromarray(white, 'L').copy()
    wide, high = ground.size
    for x in range(0, wide, 8):
        for y in (0, high - 1):
            if ground.getpixel((x, y)) == 255:
                ImageDraw.floodfill(ground, (x, y), 128)
    for y in range(0, high, 8):
        for x in (0, wide - 1):
            if ground.getpixel((x, y)) == 255:
                ImageDraw.floodfill(ground, (x, y), 128)
    shows = numpy.array(ground) != 128
    # A dot's breadth is taken off the edge, which is part white.
    return Image.fromarray((shows * 255).astype(numpy.uint8), 'L').filter(ImageFilter.MinFilter(3))


def soaked(image):
    """An overlay as a colour and how much of it shows: the stain's own colour, and as much of it as the photograph is far from white."""
    picture = numpy.asarray(image.convert('RGB'), dtype=numpy.float32) / 255.0
    shows = numpy.clip((1.0 - picture.min(axis=2) - 0.06) / 0.55, 0.0, 1.0)
    # On white paper a thin stain is the stain's colour let down with white: the white is taken back out.
    colour = numpy.clip((picture - (1.0 - shows[..., None])) / numpy.maximum(shows[..., None], 0.05), 0.0, 1.0)
    return (Image.fromarray((colour * 255).round().astype(numpy.uint8), 'RGB'), Image.fromarray((shows * 255).round().astype(numpy.uint8), 'L'))


def faded():
    """What of a print that covers the chest shows: all of its middle, fading out to nothing at its border."""
    ys, xs = numpy.mgrid[0:512, 0:512]
    far = numpy.sqrt(((xs - 255.5) / 256.0) ** 2 + ((ys - 255.5) / 256.0) ** 2)
    return Image.fromarray((numpy.clip((0.98 - far) / 0.3, 0.0, 1.0) * 255).round().astype(numpy.uint8), 'L')


def carried(image, shows):
    """
    A print with its own colours carried out over the ground that was cut away. Drawn small, a dot of it is mixed
    from those round it whether they show or not, and ground left white would give it a pale rim.
    """
    colour = numpy.asarray(image, dtype=numpy.float32)
    there = numpy.asarray(shows, dtype=numpy.float32) / 255.0
    out, known = colour * there[..., None], there.copy()
    for reach in (2, 4, 8, 16, 32, 64):
        spread = numpy.asarray(Image.fromarray(numpy.clip(known * 255, 0, 255).astype(numpy.uint8), 'L').filter(ImageFilter.GaussianBlur(reach)), dtype=numpy.float32) / 255.0
        tinted = numpy.stack([numpy.asarray(Image.fromarray(numpy.clip(out[..., c], 0, 255).astype(numpy.uint8), 'L').filter(ImageFilter.GaussianBlur(reach)),
                                            dtype=numpy.float32) for c in range(3)], axis=2)
        fill = (known < 0.5) & (spread > 0.02)
        out[fill] = tinted[fill] / spread[fill][..., None]
        known[fill] = 1.0
    return Image.fromarray(numpy.clip(out, 0, 255).round().astype(numpy.uint8), 'RGB')


def shaped(pictures, name):
    """A print at the size the mod reads it, and what of it shows. A print whose ground could not be told from it is refused."""
    image = Image.open(pictures / f'{name}.png').convert('RGB').resize((512, 512), Image.LANCZOS)
    if name in WHOLE or name in FILLS:
        return image, faded()
    shows = cut(image)
    share = numpy.asarray(shows).mean() / 255.0
    if share > MOST_OF_IT or share < LEAST_OF_IT:
        raise SystemExit(f'{name}: {share:.2f} of the picture shows after its ground is cut away, so it is no single design on a plain ground: make it again')
    return carried(image, shows), shows


def pack(pictures):
    for name in list(PRINTS) + list(WHOLE):
        image, shows = shaped(pictures, name)
        folder = OUT / 'shirtdecals'
        folder.mkdir(parents=True, exist_ok=True)
        image.save(folder / f'{name}.jpg', quality=90)
        shows.save(folder / f'{name}.cut.png', optimize=True)
    for name in OVERLAYS:
        colour, shows = soaked(Image.open(pictures / f'{name}.png'))
        folder = OUT / 'bloodtextures'
        folder.mkdir(parents=True, exist_ok=True)
        colour.save(folder / f'{name}.jpg', quality=90)
        shows.save(folder / f'{name}.cut.png', optimize=True)
    print('packed', len(PRINTS) + len(WHOLE), 'prints and', len(OVERLAYS), 'overlays', flush=True)


def sheet(pictures, path):
    """Every picture made so far on one sheet as it would be packed, each print on grey with what is cut away gone. Says which are refused."""
    names = [name for name in list(PRINTS) + list(WHOLE) + list(OVERLAYS) if (pictures / f'{name}.png').exists()]
    across = 8
    out = Image.new('RGB', (across * 256, ((len(names) + across - 1) // across) * 256), (110, 110, 118))
    refused = []
    for at, name in enumerate(names):
        if name in OVERLAYS:
            image, shows = soaked(Image.open(pictures / f'{name}.png').convert('RGB').resize((512, 512), Image.LANCZOS))
        else:
            try:
                image, shows = shaped(pictures, name)
            except SystemExit:
                refused.append(name)
                image, shows = Image.open(pictures / f'{name}.png').convert('RGB').resize((512, 512), Image.LANCZOS), Image.new('L', (512, 512), 255)
        out.paste(image.resize((256, 256), Image.LANCZOS), ((at % across) * 256, (at // across) * 256), shows.resize((256, 256), Image.LANCZOS))
    out.save(path, quality=88)
    print('sheet of', len(names), 'refused:', ' '.join(refused), flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', required=True)
    parser.add_argument('--only', nargs='*', default=[])
    parser.add_argument('--most', type=int, default=0)
    parser.add_argument('--seed', type=int, default=SEED, help='any but the first is written as <name>-<seed>.png, to choose between')
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
    prompts = {name: (PRINT.format(what), PRINT_NEGATIVE) for name, what in PRINTS.items()}
    prompts.update(WHOLE)
    prompts.update(OVERLAYS)
    unknown = [name for name in args.only if name not in prompts]
    if unknown:
        raise SystemExit('no such picture: ' + ' '.join(unknown))
    tail = '' if args.seed == SEED else f'-{args.seed}'
    seeds = OWN_SEEDS if args.seed == SEED else {}
    wanted = [name for name in prompts if (not args.only or name in args.only) and not (out / f'{name}{tail}.png').exists()]
    if args.most:
        wanted = wanted[:args.most]
    if not wanted:
        print('nothing to make', flush=True)
        return
    sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / 'materials'))
    import generate as materials
    maker = materials.Maker()
    words = maker.words({name: prompts[name] for name in wanted})
    for name in wanted:
        seed = seeds.get(name, args.seed)
        maker.make(words[name], seed, 1024, False, 7.0).save(out / f'{name}{tail}.png')
        print('made', name, flush=True)


if __name__ == '__main__':
    main()
