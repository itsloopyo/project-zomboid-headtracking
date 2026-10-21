"""Bakes the skins of zombies and of the living from the pictures tools/skins/generate.py made.

The game draws a body with one picture laid out over its body model: the front of the torso here,
an arm there, the face in a patch near the top. This makes that picture anew for each sex, skin
tone and state of decay of a zombie, and for each sex and skin tone of the living. It reads the
body model for where each dot of the picture lies on the body and which way the body faces there,
and nothing of the game's own picture.

- Skin is laid on from three sides by where a dot is on the body, so it runs unbroken over the
  joins of the layout.
- Settled blood, veins, rot and wounds come in patches that grow with the state of decay, and the same patch
  is in the same place on the body in every state.
- The face is a picture of one from straight ahead, laid on the front of the head by the head's
  crown, nose and chin, and the body's skin takes its colour from the face's.
- Briefs, and a bra for a woman, are matte cotton, painted on the dead and the living alike, as the game's own skins have them.
- The two sheets of the body model that a skirt is painted on are left see-through, as the game's own skin leaves them.
- The living have clean skin and clean underwear, the game's five tones, and for a man the hair on chest, arms and legs that the
  game's second skin of each tone has.

    python tools/skins/bake.py --game "<game folder>" --pictures DIR [--only M_ZedBody01 MaleBody03a ...] [--preview DIR] [--size 1024]
Writes src/main/resources/skins/body/<the game's name in small letters>.jpg, and with --preview a view of each body wearing its skin.
What of a skin is see-through, the skirt's sheets, the mod works out from the game's own model as a save loads (SkinCuts).
"""
import argparse
import pathlib
import re

import numpy
from PIL import Image, ImageFilter

OUT = pathlib.Path(__file__).resolve().parents[2] / 'src' / 'main' / 'resources' / 'skins' / 'body'
MODELS = {'M': 'MaleBody', 'F': 'FemaleBody'}
# Dots of the picture to one of the model's units (a body is 0.98 of them tall) at 2048, about.
DENSITY = 1900.0
# How much of the body one tile of a surface covers, in the model's units.
# How much of the fine light and dark of the skin's picture is kept: it is a picture of far less skin than a tile of it covers.
GRAIN = 0.35
SPAN = {'skin': 0.07, 'rot': 0.24, 'gore': 0.16, 'cotton': 0.3}
# What skin is multiplied by where blood has settled under it, at the edge of a patch and at its heart, and along a vein.
YELLOWED = numpy.array([0.97, 0.94, 0.74], dtype=numpy.float32)
PURPLED = numpy.array([0.7, 0.56, 0.74], dtype=numpy.float32)
VEIN = numpy.array([0.62, 0.66, 0.82], dtype=numpy.float32)
# The seed of each picture tools/skins/generate.py made that is baked, of those made: a face's states of decay are made from
# the first face picked and carry its seed. Every tone of a sex is one seed, so that no tone is a different sort of face.
PICKED = {'skin': 1, 'rot': 3, 'gore': 1, 'cotton': 1}
PICKED.update({f'face-{sex}{tone}': 3 for sex in 'mf' for tone in range(1, 5)})
PICKED.update({f'live-{sex}{tone}': 2 for sex in 'mf' for tone in range(1, 6)})
# The head of each body model, looked at from in front (the face looks along -z): half the breadth of
# the skull at the eyes, and the heights of the crown, the tip of the nose and the chin.
HEAD = {'M': (0.053, 0.98, 0.879, 0.818), 'F': (0.051, 0.968, 0.871, 0.815)}
# The same of each first face in its picture, as parts of the picture's size, with the middle of the
# face before them: middle, half the breadth of the head at the eyes, crown, tip of the nose, chin.
# Measured by eye on the pictures made. Every tone of a sex is one seed, framed alike, and the states of
# decay made from a first face keep its measures.
SEEN = {f'face-m{tone}': (0.500, 0.300, 0.030, 0.610, 0.920) for tone in range(1, 5)}
SEEN.update({f'face-f{tone}': (0.500, 0.290, 0.035, 0.600, 0.900) for tone in range(1, 5)})
# The faces of the living, measured the same way.
SEEN.update({f'live-m{tone}': (0.500, 0.272, 0.052, 0.598, 0.866) for tone in range(1, 6)})
SEEN.update({'live-f1': (0.500, 0.265, 0.060, 0.585, 0.840), 'live-f2': (0.500, 0.266, 0.058, 0.588, 0.845),
             'live-f3': (0.502, 0.268, 0.055, 0.590, 0.850), 'live-f4': (0.502, 0.271, 0.055, 0.592, 0.855),
             'live-f5': (0.502, 0.275, 0.055, 0.595, 0.860)})
# The colour of the skin of each of the game's four tones of zombie, 0 to 1, read off the game's own skins as one colour
# each. A zombie's face is brought towards it just as a face of the living is towards its tone, every tone alike.
ZOMBIE_TONE = {1: (0.87, 0.84, 0.79), 2: (0.84, 0.80, 0.72), 3: (0.72, 0.72, 0.57), 4: (0.45, 0.455, 0.42)}
# The colour of the skin of each of the game's five tones of the living, 0 to 1: read off the game's own skins as one
# colour each, so that a character is as dark here, and as warm, as where the player chose the tone. A face's picture is
# made that light, and its colour brought part of the way to that one.
TONE = {1: (0.98, 0.886, 0.725), 2: (0.937, 0.788, 0.553), 3: (0.812, 0.659, 0.467), 4: (0.522, 0.369, 0.239), 5: (0.349, 0.255, 0.141)}
# How far a face's own colour gives way to the tone's: the game's are stronger in colour than skin is.
TONED = 0.55
# The most a face's picture is lightened to be that light: more than this and its highlights burn out.
LIGHTENED_MOST = 1.18
# What skin is multiplied by under body hair at its thickest.
HAIRED = numpy.array([0.5, 0.42, 0.36], dtype=numpy.float32)


def model(path):
    """The vertices, triangles, layout places and facings of a DirectX text model."""
    text = pathlib.Path(path).read_text(errors='replace')
    body = text[re.search(r'\n\s*Mesh\s+\S*\s*\{', text).end():]
    tokens = [t for t in re.split(r'[;,\s]+', body.split('MeshNormals')[0].strip()) if t and t not in '{}']
    count = int(tokens[0])
    at = 1 + count * 3
    vertices = numpy.array(tokens[1:at], dtype=numpy.float64).reshape(count, 3)
    faces = int(tokens[at])
    at += 1
    triangles = []
    for _ in range(faces):
        n = int(tokens[at])
        corners = [int(t) for t in tokens[at + 1:at + 1 + n]]
        at += 1 + n
        triangles += [(corners[0], corners[k], corners[k + 1]) for k in range(1, n - 1)]

    def block(name, width):
        found = re.search(name + r'\s*\S*\s*\{', body)
        numbers = [t for t in re.split(r'[;,\s]+', body[found.end():].split('}')[0].strip()) if t]
        return numpy.array(numbers[1:1 + int(numbers[0]) * width], dtype=numpy.float64).reshape(-1, width)

    return vertices, numpy.array(triangles), block('MeshTextureCoords', 2), block('MeshNormals', 3)


def skirt(vertices, triangles):
    """
    The triangles of a body model that are no part of the body: two sheets that hang from the hips to
    the shins, front and back, for the game to paint a skirt or a dress on. They are the pieces of the
    model with a point on the body's middle line below the fork of the legs, where a body has nothing.
    A skin leaves them see-through.
    """
    piece = list(range(len(vertices)))

    def root(i):
        while piece[i] != i:
            piece[i] = piece[piece[i]]
            i = piece[i]
        return i

    for a, b, c in triangles:
        piece[root(a)] = root(b)
        piece[root(c)] = root(b)
    hung = {root(i) for i, (x, y, _) in enumerate(vertices) if abs(x) < 0.002 and y < 0.3}
    return numpy.array([root(a) in hung for a, _, _ in triangles])


def laid_out(vertices, triangles, places, facings, size):
    """
    For each dot of the picture: where on the body it lies, which way the body faces there, whether it
    lies on the body at all, and whether it lies on the sheets a skirt is painted on.
    """
    where = numpy.zeros((size, size, 3), dtype=numpy.float32)
    facing = numpy.zeros((size, size, 3), dtype=numpy.float32)
    on = numpy.zeros((size, size), dtype=bool)
    sheet = numpy.zeros((size, size), dtype=bool)
    sheets = skirt(vertices, triangles)
    for number, (a, b, c) in enumerate(triangles):
        x, y = places[[a, b, c], 0] * size, places[[a, b, c], 1] * size
        left, right = int(max(0, numpy.floor(x.min() - 1))), int(min(size - 1, numpy.ceil(x.max() + 1)))
        top, bottom = int(max(0, numpy.floor(y.min() - 1))), int(min(size - 1, numpy.ceil(y.max() + 1)))
        area = (y[1] - y[2]) * (x[0] - x[2]) + (x[2] - x[1]) * (y[0] - y[2])
        if abs(area) < 1e-9 or right < left or bottom < top:
            continue
        xs, ys = numpy.meshgrid(numpy.arange(left, right + 1) + 0.5, numpy.arange(top, bottom + 1) + 0.5)
        w0 = ((y[1] - y[2]) * (xs - x[2]) + (x[2] - x[1]) * (ys - y[2])) / area
        w1 = ((y[2] - y[0]) * (xs - x[2]) + (x[0] - x[2]) * (ys - y[2])) / area
        w2 = 1.0 - w0 - w1
        # A dot's breadth past the triangle's edge too, so no line of bare picture is left between two triangles.
        slack = 1.0 / max(1.0, numpy.sqrt(abs(area)))
        inside = (w0 >= -slack) & (w1 >= -slack) & (w2 >= -slack)
        strictly = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
        take = inside & (strictly | ~on[top:bottom + 1, left:right + 1])
        weights = numpy.stack([w0, w1, w2], axis=2)[take]
        where[top:bottom + 1, left:right + 1][take] = weights @ vertices[[a, b, c]]
        facing[top:bottom + 1, left:right + 1][take] = weights @ facings[[a, b, c]]
        on[top:bottom + 1, left:right + 1] |= take
        sheet[top:bottom + 1, left:right + 1][take] = sheets[number]
    length = numpy.linalg.norm(facing, axis=2, keepdims=True)
    facing = facing / numpy.maximum(length, 1e-6)
    return where, facing, on & ~sheet, sheet


def scattered(ix, iy, iz, seed):
    n = (ix.astype(numpy.uint64) * numpy.uint64(374761393) + iy.astype(numpy.uint64) * numpy.uint64(668265263)
         + iz.astype(numpy.uint64) * numpy.uint64(2246822519) + numpy.uint64(seed * 144665 + 12345))
    n = (n ^ (n >> numpy.uint64(13))) * numpy.uint64(1274126177)
    n = n ^ (n >> numpy.uint64(16))
    return (n & numpy.uint64(0xFFFF)).astype(numpy.float32) / 65535.0


# What has been worked out for a body model already: the same for every tone and state of decay of it.
# Each entry holds the layout it was worked out over, so that layout is never freed and no later
# layout can be given its id.
KEPT = {}


def cloudy(where, grain, seed, layers=4):
    """Patchy noise over the body, 0 to 1, the same at the same place on the body whatever the layout."""
    key = ('cloudy', id(where), grain, seed, layers)
    if key not in KEPT:
        KEPT[key] = where, clouded(where, grain, seed, layers)
    return KEPT[key][1]


def clouded(where, grain, seed, layers):
    total = numpy.zeros(where.shape[:2], dtype=numpy.float32)
    reach, share = 0.0, 1.0
    for layer in range(layers):
        p = (where + 2.0) * grain * (2 ** layer)
        low = numpy.floor(p).astype(numpy.int64)
        t = p - low
        t = t * t * (3.0 - 2.0 * t)
        value = 0.0
        for dx in (0, 1):
            for dy in (0, 1):
                for dz in (0, 1):
                    weight = ((t[..., 0] if dx else 1 - t[..., 0]) * (t[..., 1] if dy else 1 - t[..., 1]) * (t[..., 2] if dz else 1 - t[..., 2]))
                    value = value + weight * scattered(low[..., 0] + dx, low[..., 1] + dy, low[..., 2] + dz, seed + layer * 31)
        total += value * share
        reach += share
        share *= 0.5
    return total / reach


def ramp(low, high, value):
    t = numpy.clip((value - low) / (high - low), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def portrait(pictures, name, state):
    """A zombie's face in a state of decay, or with no state the face of the living, 0 to 1, in the colour of its tone."""
    def opened(path):
        return numpy.asarray(Image.open(pictures / path).convert('RGB')).astype(numpy.float32) / 255.0
    if state is None:
        seen = first = opened(f'{name}-{PICKED[name]}.png')
        tone = TONE[int(name[-1])]
    else:
        seen, first = opened(f'{name}-{state}-{PICKED[name]}.png'), opened(f'{name}-0-{PICKED[name]}.png')
        tone = ZOMBIE_TONE[int(name[-1])]
    # Measured on the first face, so that every state of decay of it is brought to its tone alike.
    middle, half, crown, nose, chin = SEEN[name]
    high, wide = first.shape[:2]
    box = first[int(high * (crown + 0.2 * (nose - crown))):int(high * (chin - 0.25 * (chin - nose))),
                int(wide * (middle - 0.7 * half)):int(wide * (middle + 0.7 * half))]
    weigh = numpy.array([0.299, 0.587, 0.114], dtype=numpy.float32)
    own = numpy.percentile(box.reshape(-1, 3), 62, axis=0).astype(numpy.float32)
    tone = numpy.array(tone, dtype=numpy.float32)
    lighter = min(float(tone @ weigh) / float(own @ weigh), LIGHTENED_MOST)
    # What turns the face's colour into the tone's at the same lightness, taken part of the way.
    warmer = 1.0 + TONED * ((tone / float(tone @ weigh)) / (own / float(own @ weigh)) - 1.0)
    return numpy.clip(seen * lighter * warmer, 0.0, 1.0)


def sampled(image, x, y):
    """The picture at these places in dots, wrapping, mixed between the four dots round each place."""
    high, wide = image.shape[:2]
    x0, y0 = numpy.floor(x).astype(numpy.int64), numpy.floor(y).astype(numpy.int64)
    fx, fy = (x - x0)[..., None], (y - y0)[..., None]
    x1, y1 = (x0 + 1) % wide, (y0 + 1) % high
    x0, y0 = x0 % wide, y0 % high
    return (image[y0, x0] * (1 - fx) * (1 - fy) + image[y0, x1] * fx * (1 - fy) + image[y1, x0] * (1 - fx) * fy + image[y1, x1] * fx * fy)


def wrapped(pictures, name, where, facing, span, density, grain=False):
    key = ('wrapped', name, id(where), span, density, grain)
    if key not in KEPT:
        KEPT[key] = where, laid(pictures, name, where, facing, span, density, grain)
    return KEPT[key][1]


def laid(pictures, name, where, facing, span, density, grain):
    """
    A surface laid on the body from three sides, each side counting by how squarely the body faces it.
    With grain only what is fine in the picture is kept, about 1: its broad light and dark, which
    would be counted across a back as the picture comes round again, is divided out.
    """
    dots = max(64, int(round(span * density)))
    image = Image.open(pictures / f'{name}-{PICKED[name]}.png').convert('RGB')
    # Made smaller three by three and the middle kept: made smaller alone, its edges are filtered as ends and show as seams.
    image = Image.fromarray(numpy.tile(numpy.asarray(image), (3, 3, 1))).resize((dots * 3, dots * 3), Image.LANCZOS).crop((dots, dots, dots * 2, dots * 2))
    tile = numpy.asarray(image).astype(numpy.float32) / 255.0
    if grain:
        thrice = Image.fromarray(numpy.tile(numpy.asarray(image), (3, 3, 1)))
        broad = numpy.asarray(thrice.filter(ImageFilter.GaussianBlur(dots / 40.0))).astype(numpy.float32)[dots:2 * dots, dots:2 * dots] / 255.0
        tile = numpy.clip(1.0 + GRAIN * (tile / numpy.maximum(broad, 1e-3) - 1.0), 0.8, 1.2)
    scale = dots / span
    weight = numpy.abs(facing) ** 4
    weight = weight / numpy.maximum(weight.sum(axis=2, keepdims=True), 1e-6)
    x, y, z = where[..., 0] * scale, where[..., 1] * scale, where[..., 2] * scale
    return (sampled(tile, z, -y) * weight[..., 0:1] + sampled(tile, x, z) * weight[..., 1:2] + sampled(tile, x, -y) * weight[..., 2:3])


def spread(colour, on, rounds=24):
    """Carries the colour at the edge of each part of the layout out past it, so a smaller copy of the picture does not take in bare ground."""
    colour = colour.copy()
    filled = on.copy()
    mean = colour[on].mean(axis=0)
    for _ in range(rounds):
        total = numpy.zeros_like(colour)
        count = numpy.zeros(on.shape, dtype=numpy.float32)
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            moved = numpy.roll(filled, (dy, dx), axis=(0, 1))
            total += numpy.roll(colour, (dy, dx), axis=(0, 1)) * moved[..., None]
            count += moved
        new = ~filled & (count > 0)
        colour[new] = total[new] / count[new][:, None]
        filled |= new
    colour[~filled] = mean
    return colour


def baked(sex, tone, state, maps, pictures, density):
    where, facing, on, _ = maps
    x, y, z = where[..., 0], where[..., 1], where[..., 2]
    if f'face-{sex.lower()}{tone}' not in SEEN:
        raise SystemExit(f'face-{sex.lower()}{tone}-0.png has not been measured: add it to SEEN')
    # The skin's picture gives the grain. Its colour is the face's, so that head and body are one person's.
    # On the face as it was when just turned.
    tint = complexion(f'face-{sex.lower()}{tone}', 0, pictures)
    skin = wrapped(pictures, 'skin', where, facing, SPAN['skin'], density, grain=True) * tint
    # Broad unevenness, so the skin's tile is not counted across a back.
    colour = skin * (0.9 + 0.2 * cloudy(where, 5.0, 3))[..., None]

    # Blood settles and shows through dead skin: yellowed at the edge of a patch, purple at its heart.
    patches = cloudy(where, 4.0, 11)
    least = (0.7, 0.62, 0.58, 0.55)[state]
    edge = ramp(least, least + 0.07, patches)
    heart = ramp(least + 0.04, least + 0.14, patches) * (0.45 + 0.12 * state)
    halo = edge * (1 - ramp(0.0, 0.5, heart)) * 0.6
    colour = colour * (1 - halo[..., None] * (1 - YELLOWED)) * (1 - heart[..., None] * (1 - PURPLED))
    # Veins: thin dark lines where a patchy noise crosses its middle, in the parts of the body another noise picks.
    line = 1.0 - numpy.abs(2.0 * cloudy(where, 9.0, 71, layers=2) - 1.0)
    veined = ramp(0.955, 0.99, line) * ramp(0.42, 0.6, cloudy(where, 3.0, 5)) * (0.3 + 0.15 * state)
    colour = colour * (1 - veined[..., None] * (1 - VEIN))

    # Hands and feet go first: darker, and greyer, the further gone.
    ends = numpy.maximum(ramp(0.36, 0.47, numpy.abs(x)), ramp(0.1, 0.0, y))
    grey = colour.mean(axis=2, keepdims=True) * numpy.array([0.8, 0.82, 0.74], dtype=numpy.float32)
    gone = (ends * (0.2 + 0.18 * state))[..., None]
    colour = colour * (1 - gone) + grey * 0.7 * gone

    if state > 0:
        rot = wrapped(pictures, 'rot', where, facing, SPAN['rot'], density)
        places = cloudy(where, 6.5, 23) + 0.12 * ends
        edge = (1.0, 0.72, 0.63, 0.55)[state]
        rotted = ramp(edge, edge + 0.07, places)
        colour = colour * (1 - rotted[..., None]) + (rot * 0.75 + rot * tint * 0.6) * rotted[..., None]
        if state > 1:
            gore = wrapped(pictures, 'gore', where, facing, SPAN['gore'], density)
            open_edge = edge + (0.14, 0.1)[state - 2]
            torn = ramp(open_edge, open_edge + 0.035, places)
            # A wound's lip is dark and dry.
            lip = ramp(open_edge - 0.03, open_edge, places) * (1 - torn)
            colour = colour * (1 - 0.45 * lip[..., None])
            colour = colour * (1 - torn[..., None]) + gore * 0.8 * torn[..., None]

    colour = dressed(sex, state, colour, where, facing, pictures, density)
    colour = faced(sex, f'face-{sex.lower()}{tone}', state, colour, where, facing, on, pictures)
    return spread(numpy.clip(colour, 0.0, 1.0), on)


def alive(sex, tone, hairy, maps, pictures, density):
    """The skin of the living: clean, in clean underwear, with the face of its sex and tone, and for a hairy man the hair of his body."""
    where, facing, on, _ = maps
    x, y = where[..., 0], where[..., 1]
    name = f'live-{sex.lower()}{tone}'
    if name not in SEEN:
        raise SystemExit(f'{name}.png has not been measured: add it to SEEN')
    tint = complexion(name, None, pictures)
    skin = wrapped(pictures, 'skin', where, facing, SPAN['skin'], density, grain=True) * tint
    colour = skin * (0.94 + 0.12 * cloudy(where, 5.0, 3))[..., None]
    # Blood near the skin: a little redder at the knees, elbows, knuckles and soles, where a patchy noise picks.
    flushed = ramp(0.55, 0.8, cloudy(where, 7.0, 19)) * 0.1
    colour = colour * (1 - flushed[..., None] * (1 - numpy.array([1.0, 0.86, 0.84], dtype=numpy.float32)))
    if hairy:
        # Fine strands: a noise far finer than a finger, thick on the chest and the belly's middle line, the forearms and the shins.
        front = ramp(0.0, 0.4, -facing[..., 2])
        side = numpy.abs(x)
        chest = ramp(0.6, 0.66, y) * ramp(0.76, 0.7, y) * ramp(0.13, 0.07, side) * front
        line = ramp(0.5, 0.54, y) * ramp(0.62, 0.58, y) * ramp(0.03, 0.008, side) * front
        forearms = ramp(0.2, 0.26, side) * ramp(0.4, 0.34, side)
        shins = ramp(0.06, 0.1, y) * ramp(0.27, 0.22, y) * (side < 0.14)
        grown = numpy.maximum(numpy.maximum(chest, line), numpy.maximum(0.6 * forearms, 0.55 * shins))
        strands = ramp(0.38, 0.62, cloudy(where, 420.0, 83, layers=2)) * (0.55 + 0.45 * ramp(0.3, 0.6, cloudy(where, 30.0, 87, layers=2)))
        haired = (grown * strands * 0.5)[..., None]
        colour = colour * (1 - haired * (1 - HAIRED))
    colour = dressed(sex, None, colour, where, facing, pictures, density)
    colour = faced(sex, name, None, colour, where, facing, on, pictures)
    return spread(numpy.clip(colour, 0.0, 1.0), on)


def dressed(sex, state, colour, where, facing, pictures, density):
    """Underwear: worn and stained the further gone a zombie is, and clean on the living, whose state is None."""
    x, y, z = where[..., 0], where[..., 1], where[..., 2]
    cotton = wrapped(pictures, 'cotton', where, facing, SPAN['cotton'], density)
    cotton = 0.55 + 0.4 * cotton / cotton.mean()
    fork = FORK[sex]
    side = numpy.abs(x)
    # Briefs: from the fork up to the hips, cut away over each thigh.
    briefs = ramp(fork - 0.012 + 0.55 * side, fork - 0.004 + 0.55 * side, y) * ramp(fork + 0.105, fork + 0.097, y) * (side < 0.25)
    worn = briefs
    if sex == 'F':
        chest = CHEST
        front = facing[..., 2] < -0.1
        band = ramp(chest - 0.03, chest - 0.024, y) * ramp(chest + 0.012, chest + 0.006, y)
        cups = ramp(chest - 0.03, chest - 0.024, y) * ramp(chest + 0.05 - 0.2 * numpy.abs(side - 0.045), chest + 0.044 - 0.2 * numpy.abs(side - 0.045), y) * front
        worn = numpy.maximum(worn, numpy.maximum(band, cups) * (side < TORSO))
    grubby = 1.0 - (0.03 if state is None else 0.08 + 0.1 * state) * cloudy(where, 9.0, 41)
    stained = ramp(0.6, 0.75, cloudy(where, 7.0, 57)) * (0.0 if state is None else (0.0, 0.25, 0.5, 0.7)[state])
    cloth = cotton * numpy.array([0.8, 0.8, 0.78], dtype=numpy.float32) * grubby[..., None]
    cloth = cloth * (1 - stained[..., None]) + cloth * numpy.array([0.45, 0.22, 0.16], dtype=numpy.float32) * stained[..., None]
    return colour * (1 - worn[..., None]) + cloth * worn[..., None]


# The height of the fork of the legs, of a woman's chest, and half the breadth of her torso there, in the model's units.
FORK = {'M': 0.366, 'F': 0.372}
CHEST = 0.69
TORSO = 0.11


def complexion(name, state, pictures):
    """The colour of a face's skin: what its forehead and cheeks mostly are."""
    middle, half, crown, nose, chin = SEEN[name]
    photo = portrait(pictures, name, state)
    high, wide = photo.shape[:2]
    box = photo[int(high * (crown + 0.2 * (nose - crown))):int(high * (chin - 0.25 * (chin - nose))),
                int(wide * (middle - 0.7 * half)):int(wide * (middle + 0.7 * half))]
    # Above the middle of them: eyes, hollows and wounds are the dark of a face, and none of them its skin.
    return numpy.percentile(box.reshape(-1, 3), 62, axis=0).astype(numpy.float32)


def faced(sex, name, state, colour, where, facing, on, pictures):
    middle, half, crown, nose, chin = SEEN[name]
    skull, crown_at, nose_at, chin_at = HEAD[sex]
    photo = portrait(pictures, name, state)
    high, wide = photo.shape[:2]
    x, y = where[..., 0], where[..., 1]
    # From in front the body's +x is on the left of the picture.
    across = (middle - x * half / skull) * wide
    # A head of the model is not shaped as a head is: its jaw is longer and its brow lower. The one curve
    # through crown, nose and chin carries the face down it without a kink.
    down = (crown * (y - nose_at) * (y - chin_at) / ((crown_at - nose_at) * (crown_at - chin_at))
            + nose * (y - crown_at) * (y - chin_at) / ((nose_at - crown_at) * (nose_at - chin_at))
            + chin * (y - crown_at) * (y - nose_at) / ((chin_at - crown_at) * (chin_at - nose_at))) * high
    inside = (across > 1) & (across < wide - 2) & (down > 1) & (down < high - 2)
    seen = sampled(photo, numpy.clip(across, 0, wide - 1), numpy.clip(down, 0, high - 1))
    squarely = ramp(0.1, 0.45, -facing[..., 2])
    # Only what is inside the head's outline in the picture, an egg narrower at the jaw: beside it are the ears and the wall behind.
    mid = (crown + chin) * 0.5
    up = (down / high - mid) / ((chin - crown) * 0.5)
    jaw = 1.0 - 0.3 * numpy.clip((down / high - nose) / (chin - nose), 0.0, 1.0) ** 2
    out = numpy.sqrt(numpy.clip(((across / wide - middle) / (half * jaw)) ** 2 + numpy.clip(up, -9, 1.02) ** 2, 0, None))
    share = squarely * ramp(0.97, 0.8, out) * inside * on * (y > chin_at - 0.03)
    return colour * (1 - share[..., None]) + seen * share[..., None]


def view(vertices, triangles, places, skin, low, high, front, dots):
    """The body between two heights, wearing its skin, looked at from in front or behind, unlit."""
    scale = dots / (high - low)
    wide = int(dots * 0.62)
    image = numpy.zeros((dots, wide, 3), dtype=numpy.float32)
    depth = numpy.full((dots, wide), -1e9, dtype=numpy.float32)
    sign = -1.0 if front else 1.0
    size = skin.shape[0]
    sheets = skirt(vertices, triangles)
    for number, (a, b, c) in enumerate(triangles):
        p, t = vertices[[a, b, c]], places[[a, b, c]]
        if sheets[number] or p[:, 1].max() < low or p[:, 1].min() > high:
            continue
        sx, sy, sz = p[:, 0] * sign * scale + wide / 2, (high - p[:, 1]) * scale, p[:, 2] * sign
        left, right = int(max(0, numpy.floor(sx.min()))), int(min(wide - 1, numpy.ceil(sx.max())))
        top, bottom = int(max(0, numpy.floor(sy.min()))), int(min(dots - 1, numpy.ceil(sy.max())))
        area = (sy[1] - sy[2]) * (sx[0] - sx[2]) + (sx[2] - sx[1]) * (sy[0] - sy[2])
        if abs(area) < 1e-9 or right < left or bottom < top:
            continue
        xs, ys = numpy.meshgrid(numpy.arange(left, right + 1) + 0.5, numpy.arange(top, bottom + 1) + 0.5)
        w0 = ((sy[1] - sy[2]) * (xs - sx[2]) + (sx[2] - sx[1]) * (ys - sy[2])) / area
        w1 = ((sy[2] - sy[0]) * (xs - sx[2]) + (sx[0] - sx[2]) * (ys - sy[2])) / area
        w2 = 1.0 - w0 - w1
        near = w0 * sz[0] + w1 * sz[1] + w2 * sz[2]
        take = (w0 >= 0) & (w1 >= 0) & (w2 >= 0) & (near > depth[top:bottom + 1, left:right + 1])
        u = (w0 * t[0, 0] + w1 * t[1, 0] + w2 * t[2, 0]) * size - 0.5
        v = (w0 * t[0, 1] + w1 * t[1, 1] + w2 * t[2, 1]) * size - 0.5
        image[top:bottom + 1, left:right + 1][take] = sampled(skin, u, v)[take]
        depth[top:bottom + 1, left:right + 1][take] = near[take]
    return image


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--game', required=True)
    parser.add_argument('--pictures', required=True)
    parser.add_argument('--only', nargs='*', default=[])
    parser.add_argument('--preview')
    parser.add_argument('--size', type=int, default=1024)
    args = parser.parse_args()
    pictures = pathlib.Path(args.pictures)
    OUT.mkdir(parents=True, exist_ok=True)
    for sex, file in MODELS.items():
        names = [(f'{sex}_ZedBody0{tone}' + (f'_level{state}' if state else ''), tone, state) for tone in (1, 2, 3, 4) for state in (0, 1, 2, 3)]
        # The living: the game's five tones, and for a man a second skin of each with hair on his body. Their state is no number.
        names += [(f'{file}0{tone}{hair}', tone, 'hairy' if hair else 'living') for tone in (1, 2, 3, 4, 5) for hair in (('', 'a') if sex == 'M' else ('',))]
        names = [each for each in names if not args.only or each[0] in args.only]
        if not names:
            continue
        vertices, triangles, places, facings = model(pathlib.Path(args.game) / 'media' / 'models_X' / 'Skinned' / f'{file}.x')
        maps = laid_out(vertices, triangles, places, facings, args.size)
        for name, tone, state in names:
            density = DENSITY * args.size / 2048.0
            skin = baked(sex, tone, state, maps, pictures, density) if isinstance(state, int) else alive(sex, tone, state == 'hairy', maps, pictures, density)
            Image.fromarray((skin * 255.0).round().astype(numpy.uint8), 'RGB').save(OUT / f'{name.lower()}.jpg', quality=90, subsampling=0)
            print('baked', name, flush=True)
            if args.preview:
                preview = pathlib.Path(args.preview)
                preview.mkdir(parents=True, exist_ok=True)
                shots = [view(vertices, triangles, places, skin, 0.0, 1.0, True, 1200), view(vertices, triangles, places, skin, 0.0, 1.0, False, 1200),
                         view(vertices, triangles, places, skin, 0.78, 1.0, True, 1200)]
                Image.fromarray((numpy.concatenate(shots, axis=1) * 255.0).round().astype(numpy.uint8), 'RGB').save(preview / f'{name}.jpg', quality=88)


if __name__ == '__main__':
    main()
