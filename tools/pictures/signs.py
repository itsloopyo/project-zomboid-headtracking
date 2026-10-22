"""Shop signs, notices and flags: what the game hangs on a wall as a lettered board some sixty dots a metre.

A sign says exactly what the game's says. The model that makes the mod's pictures spells nothing right, so no letter
here comes from it: the wording is set as type (pack.py's fonts, and Arial Black and Segoe Script from the same
Windows folder) over a ground that is generated, plain paint on sheet metal, a slate, hanging cloth, with no writing on
it. An emblem is generated alone, as a pale shape on a dark ground, and only its outline is taken.

SIGNS is key -> (metres wide, metres high, ground art, ground colour, what is on it). The size is that of the place
WorldScene.addHung hangs the picture in, which it works out from the game's sprite with WallHung.painted, and the
picture is made that shape, because a hung picture of another shape than its place has its sides cut off. A sign
whose two facings have places of two shapes is made between them. What is on it is a list, drawn in order, of places given as shares of the board from its left and its top:
    ('band', left, top, right, bottom, colour)
    ('line', left, top, right, bottom, colour, thick)        a rule round a rectangle, thick a share of the height
    ('text', words, font, colour, left, top, right, bottom)  set as large as fits the box, in the middle of it
    ('caps', words, font, colour, left, top, right, bottom)  the first letter of each word tall, the rest ruled under
    ('emblem', art, colour, left, top, right, bottom)
    ('flag',)                                                 the stars and stripes hung by its hoist, canton top left
"""
import math

from PIL import Image, ImageChops, ImageDraw, ImageFont, ImageOps

FONT_FOLDER = 'C:/Windows/Fonts/'
PER_METRE = 440
LONGEST, SHORTEST = 1536, 256

GROUNDS = {
    'sign-board': 'extreme close-up photograph of the middle of a flat sheet metal sign board, one '
                  'seamless smooth flat sheet, the plain light grey surface filling the whole frame and '
                  'running off every edge, even enamel paint, faint scratches, specks of dust and slight '
                  'weathering stains, flat even daylight, sharp focus',
    'sign-slate': 'extreme close-up photograph of the middle of a large black slate chalkboard, the flat '
                  'smooth surface filling the whole frame and running off every edge, wiped clean with '
                  'long straight level horizontal strokes of a felt eraser running from the left edge of '
                  'the picture to the right edge, a faint even haze of chalk dust spread evenly over the '
                  'whole surface, flat even shadowless light with the same brightness in every corner, '
                  'sharp focus',
    'sign-cloth': 'close-up photograph of plain white cotton flag cloth hanging down in soft vertical folds, fine woven texture, '
                  'even daylight, sharp focus',
}
EMBLEMS = {
    'sign-seahorse': 'flat white silhouette of a seahorse with a spiky crest rising out of a plain round cup on a saucer, the cup one smooth '
                     'solid white shape, simple bold vintage emblem, solid dark red background, centred, vector, high contrast',
}

WHITE, RED, INK = (240, 236, 226), (178, 44, 38), (30, 28, 28)
COFFEE = [('caps', 'SEAHORSE COFFEE', 'impact.ttf', WHITE, 0.035, 0.12, 0.80, 0.90), ('emblem', 'sign-seahorse', WHITE, 0.815, 0.10, 0.975, 0.90)]
WARNING = [('line', 0.012, 0.025, 0.988, 0.975, RED, 0.035), ('band', 0.05, 0.10, 0.95, 0.42, RED),
           ('text', 'WARNING', 'ariblk.ttf', WHITE, 0.09, 0.12, 0.91, 0.40), ('text', 'RESTRICTED', 'arialbd.ttf', INK, 0.10, 0.47, 0.90, 0.69),
           ('text', 'AREA', 'arialbd.ttf', INK, 0.10, 0.70, 0.90, 0.92)]
SIGNS = {
    'Sign|Coffee Low': (2.98, 0.71, 'sign-board', (158, 62, 36), COFFEE),
    'Sign|Exit': (0.52, 0.33, 'sign-board', (206, 204, 184), [('line', 0.0, 0.0, 1.0, 1.0, (96, 96, 92), 0.07),
                                                             ('text', 'EXIT', 'arialbd.ttf', (196, 60, 26), 0.10, 0.16, 0.90, 0.84)]),
    'Sign|For Lease': (0.84, 0.54, 'sign-board', (182, 54, 54), [('line', 0.025, 0.035, 0.975, 0.965, WHITE, 0.03),
                                                                ('text', 'FOR', 'impact.ttf', WHITE, 0.2, 0.10, 0.8, 0.47),
                                                                ('text', 'LEASE', 'impact.ttf', WHITE, 0.1, 0.50, 0.9, 0.91)]),
    'Sign|Warning': (0.78, 0.42, 'sign-board', (240, 240, 238), WARNING),
    'Sign|Laundromat': (3.52, 0.44, 'sign-board', (226, 224, 214), [('band', 0.012, 0.08, 0.215, 0.92, (238, 206, 110)),
                                                                   ('text', 'COIN', 'ariblk.ttf', (62, 72, 168), 0.03, 0.10, 0.20, 0.50),
                                                                   ('text', 'OP', 'ariblk.ttf', (62, 72, 168), 0.06, 0.50, 0.17, 0.90),
                                                                   ('text', 'Laundromat', 'segoescb.ttf', (52, 54, 150), 0.25, 0.02, 0.975, 0.98)]),
    'Sign|Pie Menu': (0.98, 1.21, 'sign-slate', (62, 62, 60), [('line', 0.0, 0.0, 1.0, 1.0, (176, 104, 40), 0.045),
                                                              ('text', 'Specials', 'segoesc.ttf', (226, 224, 216), 0.10, 0.06, 0.90, 0.24)]),
    'Sign|Bail Bonds': (2.89, 0.48, 'sign-board', (204, 200, 180), [('line', 0.0, 0.0, 1.0, 1.0, (44, 44, 40), 0.07),
                                                                  ('text', 'BAIL BONDS', 'ariblk.ttf', (16, 16, 16), 0.05, 0.20, 0.95, 0.84)]),
    'Sign|Legal Services': (2.89, 0.48, 'sign-board', (208, 204, 184), [('line', 0.0, 0.0, 1.0, 1.0, (44, 44, 40), 0.07),
                                                                      ('text', 'LEGAL SERVICES', 'georgia.ttf', (44, 42, 40), 0.04, 0.22, 0.96, 0.82)]),
    'Sign|Property': (0.81, 0.80, 'sign-board', (238, 240, 242), [('band', 0.07, 0.10, 0.93, 0.115, INK), ('band', 0.07, 0.885, 0.93, 0.90, INK),
                                                                ('text', 'U.S. ARMY', 'arialbd.ttf', INK, 0.08, 0.18, 0.92, 0.32),
                                                                ('text', 'PROPERTY', 'arialbd.ttf', INK, 0.08, 0.35, 0.92, 0.49),
                                                                ('text', 'NO', 'arialbd.ttf', INK, 0.4, 0.60, 0.6, 0.69),
                                                                ('text', 'TRESPASSING', 'arialbd.ttf', INK, 0.10, 0.73, 0.90, 0.83)]),
    'Flag|Stars n Stripes': (0.81, 1.10, 'sign-cloth', (255, 255, 255), [('flag',)]),
}

# A sign the game has under a second name, painted on a board of the same shape.
SAME = {'Sign|Coffee High': 'Sign|Coffee Low'}

# What the catalogue takes from here: the art to make, and which key shows which picture.
ART = {name: (prompt, 1024, 1024) for name, prompt in {**GROUNDS, **EMBLEMS}.items()}
PICTURES = {'sign-' + key.split('|')[1].lower().replace(' ', '-'): key for key in SIGNS}
HUNG = [(key, name) for name, key in PICTURES.items()] + [(key, 'sign-' + SAME[key].split('|')[1].lower().replace(' ', '-')) for key in SAME]


def size(key):
    wide, high = SIGNS[key][0] * PER_METRE, SIGNS[key][1] * PER_METRE
    scale = min(LONGEST / max(wide, high), max(1.0, SHORTEST / min(wide, high)))
    return round(wide * scale), round(high * scale)


def grain(art, wide, high):
    """The ground's light and dark about its own mean, a piece of the art the size of the board at a board's scale."""
    grey = ImageOps.grayscale(art)
    side = max(wide, high)
    grey = grey.resize((side, side), Image.LANCZOS).crop((0, 0, wide, high))
    mean = sum(grey.resize((1, 1), Image.BOX).getdata())
    return grey.point(lambda v: max(0, min(255, round(128 + (v - mean) * 0.9))))


def fitted(text, name, wide, high):
    """The font at the largest size at which the text's ink fits the box, and that ink's box at the origin."""
    size = max(8, int(high * 1.6))
    while True:
        face = ImageFont.truetype(FONT_FOLDER + name, size)
        left, top, right, bottom = face.getbbox(text)
        if right - left <= wide and bottom - top <= high:
            return face, (left, top, right, bottom)
        if size <= 8:
            raise SystemExit('"%s" does not fit its box on the sign at any size' % text)
        size -= max(1, size // 40)


def emblem_mask(art, wide, high):
    """The pale shape of an emblem's art, cut to its own outline and fitted to the box."""
    red, green, blue = art.split()
    # Pale and without colour, whatever the ground: the least of the three is high only there.
    shape = ImageChops.darker(ImageChops.darker(red, green), blue).point(lambda v: 255 if v > 150 else 0)
    box = shape.getbbox()
    if box is None:
        raise SystemExit('an emblem came out with no pale shape in it')
    cut = shape.crop(box)
    scale = min(wide / cut.width, high / cut.height)
    return cut.resize((max(1, round(cut.width * scale)), max(1, round(cut.height * scale))), Image.LANCZOS)


def flag(wide, high):
    """The flag of 1993 hung down a wall by its hoist: stripes upright, the canton at the top left, fifty stars."""
    image = Image.new('RGB', (wide, high), (236, 232, 226))
    draw = ImageDraw.Draw(image)
    red, blue = (176, 34, 44), (44, 48, 104)
    for stripe in range(0, 13, 2):
        draw.rectangle((round(wide * stripe / 13), 0, round(wide * (stripe + 1) / 13) - 1, high), fill=red)
    # Hung so, the canton keeps the observer's left: seven stripes across and two fifths of the fly down.
    across, down = round(wide * 7 / 13), round(high * 0.4)
    draw.rectangle((0, 0, across - 1, down), fill=blue)
    # Its nine rows of six and five stars run down the hoist here: eleven rows of five and four.
    radius = across / 13 * 0.42
    for row in range(11):
        for column in range(9):
            if (row + column) % 2:
                continue
            centre_x = across * (column + 1) / 10
            centre_y = down * (row + 1) / 12
            points = []
            for point in range(10):
                reach = radius if point % 2 == 0 else radius * 0.4
                angle = math.pi * point / 5 - math.pi / 2
                points.append((centre_x + reach * math.cos(angle), centre_y + reach * math.sin(angle)))
            draw.polygon(points, fill=(240, 238, 232))
    return image


def picture(key, made):
    """The finished sign of a key. made(name) is the generated art of that name, or None where it is not made yet: then None."""
    _, _, ground, colour, parts = SIGNS[key]
    art = made(ground)
    if art is None:
        return None
    wide, high = size(key)
    scale = 3
    full = (wide * scale, high * scale)
    image = Image.new('RGB', full, colour)
    draw = ImageDraw.Draw(image)

    def box(left, top, right, bottom):
        return round(left * full[0]), round(top * full[1]), round(right * full[0]), round(bottom * full[1])

    for part in parts:
        if part[0] == 'band':
            draw.rectangle(box(*part[1:5]), fill=part[5])
        elif part[0] == 'line':
            draw.rectangle(box(*part[1:5]), outline=part[5], width=max(1, round(part[6] * full[1])))
        elif part[0] == 'text':
            left, top, right, bottom = box(*part[4:8])
            face, ink = fitted(part[1], part[2], right - left, bottom - top)
            draw.text(((left + right - ink[0] - ink[2]) / 2, (top + bottom - ink[1] - ink[3]) / 2), part[1], font=face, fill=part[3])
        elif part[0] == 'caps':
            left, top, right, bottom = box(*part[4:8])
            words = part[1].split(' ')
            # The rest of each word is three quarters the height of its first letter, hung from the same top, with a rule under it.
            small = 0.74
            tall = bottom - top
            while True:
                first = ImageFont.truetype(FONT_FOLDER + part[2], tall)
                rest = ImageFont.truetype(FONT_FOLDER + part[2], max(8, round(tall * small)))
                gap = tall * 0.22
                widths = [(first.getlength(word[0]), rest.getlength(word[1:])) for word in words]
                total = sum(a + b for a, b in widths) + gap * (len(words) - 1)
                cap = first.getbbox('H')
                if total <= right - left and cap[3] - cap[1] <= bottom - top:
                    break
                tall -= max(1, tall // 40)
            cap_small = rest.getbbox('H')
            at = left + (right - left - total) / 2
            head = top + (bottom - top - (cap[3] - cap[1])) / 2
            for word, (lead, tail) in zip(words, widths):
                draw.text((at, head - cap[1]), word[0], font=first, fill=part[3])
                draw.text((at + lead, head - cap_small[1]), word[1:], font=rest, fill=part[3])
                under = head + (cap_small[3] - cap_small[1]) + (cap[3] - cap[1]) * 0.07
                draw.rectangle((at + lead + tall * 0.02, under, at + lead + tail, under + (cap[3] - cap[1]) * 0.075), fill=part[3])
                draw.rectangle((at + lead + tall * 0.02, under + (cap[3] - cap[1]) * 0.115, at + lead + tail, under + (cap[3] - cap[1]) * 0.15), fill=part[3])
                at += lead + tail + gap
        elif part[0] == 'emblem':
            shape_art = made(part[1])
            if shape_art is None:
                return None
            left, top, right, bottom = box(*part[3:7])
            mask = emblem_mask(shape_art, right - left, bottom - top)
            image.paste(part[2], (left + (right - left - mask.width) // 2, top + (bottom - top - mask.height) // 2), mask)
            draw = ImageDraw.Draw(image)
        elif part[0] == 'flag':
            image = flag(*full)
            draw = ImageDraw.Draw(image)
        else:
            raise ValueError('a sign has a part of no known kind: ' + part[0])
    image = image.resize((wide, high), Image.LANCZOS)
    light = grain(art, wide, high)
    return ImageChops.overlay(image, Image.merge('RGB', (light, light, light)))
