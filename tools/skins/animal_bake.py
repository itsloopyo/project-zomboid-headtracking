"""Bakes the skins of animals from the pictures tools/skins/animal_pictures.py made.

The game draws an animal with one picture laid out over its model like a hide pegged out flat, with
the parts that hang off it (an udder, the tail, the ears, the inside of the mouth) in pieces of their
own round the edge. This makes that picture anew for each of the game's skins. It reads the model for
where each dot of the picture lies on the animal and which way the animal faces there, and nothing of
the game's own picture.

- Hair, skin and horn are laid on from three sides by where a dot is on the animal, so they run
  unbroken over the joins of the layout.
- A breed's markings are laid by place on the animal: patches by noise, and what a breed has where
  (a white face, white legs, a white switch) by height and by length along the body.
- The nose, the eyes and the hooves are put where the model has them, measured off the model once and
  kept in KINDS.

    python tools/skins/animal_bake.py --game "<game folder>" --pictures DIR [--only Cow_BW_01 ...] [--preview DIR] [--size 2048]
Writes src/main/resources/skins/body/<the game's name in small letters>.jpg with a .cut.png beside it, and with --preview views of
each animal wearing its skin, lit from above.
"""
import argparse
import pathlib

import numpy
from PIL import Image, ImageFilter

import bake

OUT = pathlib.Path(__file__).resolve().parents[2] / 'src' / 'main' / 'resources' / 'skins' / 'body'
# Each kind of animal: the model its skins are made on, its family, and places on the model in its own units, read off views of
# the model with a grid laid over them. A skin is laid on every model of its kind (a calf wears a cow's, a piglet a sow's), and
# those share the layout. The head looks along -z.
#   muzzle: the middle of the front of the nose, and its half breadth, half height and depth back from the front
#   nostrils: how far either side of the middle and how high each nostril is, and its half breadth and half height
#   eye: ('side', height, along), on the side of the head there, or ('front', across, height), on the front of the head there;
#        then the radius of the eyeball and of the bare lid round it. None for a model whose eyes are pieces of their own.
#   hoof: up to what height a leg is hoof
#   beak: for a bird, where the beak begins, along the head
# Last, the other models that wear the same skins with a layout of their own: where the first leaves the picture bare and one of
# these has it, the dot is laid from that one, its place moved into the first's size.
KINDS = {
    'cow': ('CowBody', 'cattle', {'muzzle': ((0.0, 0.757, -0.925), (0.058, 0.042, 0.05)), 'nostrils': (0.024, 0.768, 0.013, 0.008),
                                  'eye': (('side', 0.865, -0.8), 0.011, 0.016), 'hoof': 0.045}, ()),
    'bull': ('Bull_Body', 'cattle', {'muzzle': ((0.0, 0.757, -0.928), (0.062, 0.044, 0.05)), 'nostrils': (0.026, 0.768, 0.013, 0.008),
                                     'eye': (('side', 0.872, -0.81), 0.011, 0.016), 'hoof': 0.045}, ()),
    'sow': ('FarmPig_SowBody', 'pig', {'muzzle': ((0.0, 0.345, -0.678), (0.03, 0.022, 0.025)), 'nostrils': (0.011, 0.349, 0.006, 0.009),
                                       'eye': (('front', 0.035, 0.398), 0.0045, 0.008), 'hoof': 0.02}, ()),
    'boar': ('FarmPig_BoarBody', 'pig', {'muzzle': ((0.0, 0.345, -0.695), (0.032, 0.023, 0.025)), 'nostrils': (0.012, 0.349, 0.006, 0.009),
                                         'eye': (('front', 0.037, 0.4), 0.0045, 0.008), 'hoof': 0.02}, ()),
    'ewe': ('Sheep_EweFleece', 'sheep', {'muzzle': ((0.0, 0.355, -0.397), (0.02, 0.012, 0.012)), 'nostrils': (0.008, 0.357, 0.004, 0.003),
                                         'eye': (('side', 0.394, -0.335), 0.006, 0.009), 'hoof': 0.02}, ('Sheep_RamFleece', 'Sheep_Lamb')),
    'doe': ('DeerDoe', 'deer', {'muzzle': ((0.0, 0.64, -0.775), (0.024, 0.016, 0.02)), 'nostrils': (0.012, 0.642, 0.006, 0.005),
                                'eye': (('side', 0.723, -0.687), 0.011, 0.016), 'hoof': 0.03}, ()),
    'buck': ('DeerStag', 'deer', {'muzzle': ((0.0, 0.676, -0.815), (0.026, 0.018, 0.02)), 'nostrils': (0.013, 0.678, 0.0065, 0.005),
                                  'eye': (('side', 0.767, -0.728), 0.012, 0.017), 'hoof': 0.03}, ()),
    'fawn': ('DeerFawn', 'deer', {'muzzle': ((0.0, 0.399, -0.445), (0.015, 0.01, 0.012)), 'nostrils': (0.007, 0.4, 0.0035, 0.003),
                                  'eye': (('side', 0.447, -0.401), 0.007, 0.01), 'hoof': 0.02}, ()),
    'hen': ('Chicken_Hen', 'bird', {'eye': (('side', 0.27, -0.109), 0.0038, 0.0055), 'beak': -0.13}, ()),
    'cockerel': ('Chicken_Cockrel', 'bird', {'eye': (('side', 0.27, -0.109), 0.0038, 0.0055), 'beak': -0.13}, ()),
    'chick': ('Chicken_Chick', 'bird', {'eye': (('side', 0.0709, -0.0604), 0.0028, 0.004), 'beak': -0.068}, ()),
    # A turkey's places are a gobbler's, and a poult's are worked out from them by height: see turkey.
    'turkey': ('Turkey', 'turkey', {'eye': (('side', 0.41, -0.224), 0.0045, 0.0065)}, ()),
    'poult': ('Turkey_Poult', 'turkey', {'eye': (('side', 0.41 * 0.45, -0.224 * 0.45), 0.0025, 0.0035)}, ()),
    'rabbit': ('Rab_Body', 'small', {'muzzle': ((0.0, 0.132, -0.112), (0.012, 0.008, 0.01)), 'nostrils': (0.005, 0.134, 0.003, 0.002),
                                     'eye': None}, ()),
    'raccoon': ('Raccoon_Body', 'small', {'muzzle': ((0.0, 0.19, -0.305), (0.012, 0.009, 0.01)), 'nostrils': (0.005, 0.192, 0.003, 0.002),
                                          'eye': (('front', 0.016, 0.212), 0.005, 0.007)}, ()),
    'rat': ('Rat_Body', 'small', {'muzzle': ((0.0, 0.077, -0.139), (0.006, 0.005, 0.006)), 'nostrils': (0.002, 0.078, 0.0012, 0.001),
                                  'eye': None}, ()),
    'mouse': ('Mouse_Body', 'small', {'muzzle': ((0.0, 0.031, -0.0735), (0.004, 0.003, 0.004)), 'nostrils': (0.0015, 0.0315, 0.0008, 0.0006),
                                      'eye': (('side', 0.0374, -0.0539), 0.0025, 0.0033)}, ()),
}
# Each of the game's skins: the kind of animal, the breed, and the seed of its patches.
SKINS = {
    'Cow_BW_01': ('cow', 'holstein', 1), 'Cow_BW_02': ('cow', 'holstein', 2), 'Cow_BW_03': ('cow', 'holstein', 3),
    'Cow_Brown_01': ('cow', 'simmental', 1), 'Cow_Brown_02': ('cow', 'simmental', 2), 'Cow_Brown_03': ('cow', 'simmental', 3),
    'Cow_Black': ('cow', 'angus', 0),
    'Bull_BW_01': ('bull', 'holstein', 4), 'Bull_Brown_01': ('bull', 'simmental', 4), 'Bull_Black': ('bull', 'angus', 0),
    'FarmPig': ('sow', 'landrace', 0), 'FarmPig_Black': ('sow', 'largeblack', 0),
    'FarmPig_Boar': ('boar', 'landrace', 0), 'FarmPig_Boar_Black': ('boar', 'largeblack', 0),
    'Sheep': ('ewe', 'suffolk', 0), 'Sheep_White': ('ewe', 'white', 0),
    'DeerDoe': ('doe', 'whitetailed', 0), 'DeerStag': ('buck', 'whitetailed', 0), 'DeerFawn': ('fawn', 'whitetailed', 0),
    'Chicken': ('hen', 'rhodeisland', 0), 'Chicken_White': ('hen', 'leghorn', 0),
    'Cockerel': ('cockerel', 'rhodeisland', 0), 'Cockerel_White': ('cockerel', 'leghorn', 0), 'Chick': ('chick', 'chick', 0),
    'Turkey': ('turkey', 'gobbler', 0), 'Turkey_Hen': ('turkey', 'hen', 0), 'Turkey_Poult': ('poult', 'poult', 0),
    # A kitten's model has the layout of a grown rabbit's.
    'Rabbit_Cottontail': ('rabbit', 'cottontail', 0), 'Rabbit_Appalachian': ('rabbit', 'appalachian', 0), 'Rabbit_Swamp': ('rabbit', 'swamp', 0),
    'Rabbit_Kitten_Cottontail': ('rabbit', 'cottontail', 1), 'Rabbit_Kitten_Appalachian': ('rabbit', 'appalachian', 1),
    'Rabbit_Kitten_Swamp': ('rabbit', 'swamp', 1),
    'Raccoon': ('raccoon', 'grey', 0), 'Rat': ('rat', 'grey', 0), 'Rat_White': ('rat', 'white', 0),
    'Mouse_Deer': ('mouse', 'deer', 0), 'Mouse_Golden': ('mouse', 'golden', 0), 'Mouse_White': ('mouse', 'white', 0),
}
# The seed of each picture that was chosen, of those made.
PICKED = {'coat-black': 7, 'coat-white': 7, 'coat-red': 7, 'skin-udder': 7, 'hoof': 7, 'pig-pink': 7, 'pig-black': 7, 'wool-cream': 7, 'coat-deer': 7, 'feathers-red': 1993,
          'feathers-white': 1993, 'bird-leg': 7, 'down-yellow': 7, 'fur-brown': 7, 'fur-grey': 7, 'fur-white': 7,
          'feathers-turkey': 1993}
# How much of the animal one picture of a surface covers, in the model's units: a cow is 1.45 of them long and a pig 0.95.
SPAN = {'coat': 0.12, 'skin': 0.12, 'hoof': 0.08, 'pig': 0.1}
# The colour a picture is brought to, on the whole: the model made a Simmental's red hair orange and a pig's skin hot pink.
COLOUR = {'coat-red': (0.47, 0.22, 0.11), 'coat-white': (0.86, 0.84, 0.8), 'coat-black': (0.07, 0.065, 0.065), 'hoof': (0.16, 0.15, 0.14),
          'pig-pink': (0.86, 0.66, 0.6), 'pig-black': (0.15, 0.14, 0.14), 'wool-cream': (0.8, 0.76, 0.68),
          'coat-deer': (0.5, 0.37, 0.26), 'down-yellow': (0.92, 0.8, 0.42)}
# How much of the strands of a coat picture is kept. Laid at full strength a short coat repeated across a flank as a grid of
# squares, where at a few paces a real one shows only a soft sheen.
COAT_GRAIN = 0.5
# How much of a coat picture's broad light and dark is kept: it is a picture of far less hair than a tile of it covers.
BROAD = 0.0
# The colours of the bare skin of a cow's nose: pink, the grey it is mottled with, and a black breed's.
NOSE_PINK = numpy.array([0.78, 0.55, 0.55], dtype=numpy.float32)
NOSE_GREY = numpy.array([0.42, 0.38, 0.4], dtype=numpy.float32)
NOSE_BLACK = numpy.array([0.09, 0.08, 0.085], dtype=numpy.float32)


def pieces(vertices, triangles):
    """The number of the piece of the model each triangle is part of: triangles that share a corner are one piece."""
    piece = list(range(len(vertices)))

    def root(i):
        while piece[i] != i:
            piece[i] = piece[piece[i]]
            i = piece[i]
        return i

    for a, b, c in triangles:
        piece[root(a)] = root(b)
        piece[root(c)] = root(b)
    return numpy.array([root(a) for a, _, _ in triangles])


def cattle_piece(count, middle, low, high):
    x, y, z = middle
    if high[1] < 0.02:
        return 'sole'
    if abs(x) < 0.03 and z > 0.4:
        return 'tail'
    if low[1] > 0.92:
        return 'horn'
    if y > 0.8 and abs(x) > 0.04:
        return 'ear' if count > 12 else 'inside ear'
    if z < -0.8:
        return 'mouth'
    if y < 0.46 and abs(x) < 0.09:
        return 'udder'
    return None


def pig_piece(count, middle, low, high):
    x, y, z = middle
    if high[1] < 0.01:
        return 'sole'
    if abs(x) < 0.03 and z > 0.18 and y > 0.4:
        return 'tail'
    if y > 0.37 and abs(x) > 0.02 and z < -0.45:
        return 'ear' if count > 12 else 'inside ear'
    if z < -0.6 and y < 0.34:
        return 'mouth'
    if y < 0.3 and abs(x) < 0.07:
        return 'udder'
    return None


def sheep_piece(count, middle, low, high):
    x, y, z = middle
    if high[1] < 0.01:
        return 'sole'
    if y < 0.21 and abs(x) < 0.05:
        return 'udder'
    if count >= 100:
        return 'body'
    if abs(x) < 0.05 and z > 0.17 and y > 0.25:
        return 'tail'
    if count >= 10 and y > 0.39 and abs(x) > 0.015 and z < -0.28:
        return 'ear' if count > 12 else 'inside ear'
    if count < 10 and z < -0.36 and abs(x) < 0.035:
        return 'mouth'
    if count < 10 and y > 0.3 and z < -0.24:
        return 'horn'
    if abs(x) > 0.08 and y < 0.16:
        return 'sole'
    return None


def deer_piece(count, middle, low, high):
    x, y, z = middle
    if count >= 100:
        return 'body'
    if high[1] < 0.01:
        return 'sole'
    if y < 0.1:
        return 'sole'
    if count >= 20 or low[1] > 0.9 or (abs(x) < 0.045 and y > 0.84):
        return 'antler'
    if count in (7, 12) and abs(x) > 0.04:
        return 'ear' if count == 12 else 'inside ear'
    return None


def bird_piece(count, middle, low, high):
    x, y, z = middle
    if y < 0.07 and high[1] < 0.075 and count >= 11:
        return 'leg'
    if z > 0.07:
        return 'tail'
    if z < -0.09 and y < 0.26:
        return 'wattle'
    if z < -0.08 and y > 0.27:
        return 'comb'
    return 'body'


def small_piece(count, middle, low, high):
    """The rules for a rabbit, a raccoon, a rat and a mouse, each told by where its head is: they do not overlap."""
    x, y, z = middle
    if count >= 50 and not (abs(x) < 0.02 and z > 0.05 and y < 0.07):
        return 'body'
    if high[1] < 0.01 or (high[1] < 0.03 and count >= 14):
        return 'foot'
    if abs(x) < 0.02 and z > 0.03 and y < 0.07:
        return 'tail'
    if (count == 18 or (count == 10 and y > 0.12)) and -0.12 < z < -0.07 and 0.08 < y < 0.16:
        return 'eyeball'
    if count <= 2 and y < 0.12 and (z < -0.11 or (-0.064 < z < -0.055 and y < 0.045)):
        return 'whisker'
    if (y > 0.16 and z > -0.07) or (y > 0.2 and z > -0.23) or (0.083 < y < 0.11 and -0.1 < z < -0.088) or (y > 0.037 and -0.05 < z < -0.02):
        return 'ear'
    return 'body'


def turkey_piece(count, middle, low, high):
    """A turkey is a body and two hundred feathers, each a piece: all are painted by place."""
    return 'body'


PIECE = {'turkey': turkey_piece, 'cattle': cattle_piece, 'pig': pig_piece, 'sheep': sheep_piece, 'deer': deer_piece, 'bird': bird_piece, 'small': small_piece}


def parts(family, vertices, triangles):
    """What each triangle is part of, by its piece: the body, an udder or a sheath, the tail, an ear and the inside of one, a horn, the inside of the mouth, a sole."""
    piece = pieces(vertices, triangles)
    named = numpy.empty(len(triangles), dtype=object)
    body = numpy.bincount(piece).argmax()
    for number in numpy.unique(piece):
        mine = piece == number
        corners = vertices[numpy.unique(triangles[mine].ravel())]
        name = 'body' if number == body else PIECE[family](int(mine.sum()), corners.mean(axis=0), corners.min(axis=0), corners.max(axis=0))
        if name is None:
            raise SystemExit(f'a piece of the model is nothing known: {mine.sum()} triangles about {corners.mean(axis=0).round(3)}')
        named[mine] = name
    return named


def smooth(vertices, triangles):
    """
    Which way the animal faces at each corner: the faces round it, weighed by their size, with corners at the same place taken
    together. The model's own are not used: read by corner they jumped from one triangle to the next, and a coat laid by them
    changed sides along every edge of the layout.
    """
    key = numpy.round(vertices / 1e-5).astype(numpy.int64)
    _, same = numpy.unique(key, axis=0, return_inverse=True)
    same = same.ravel()
    a, b, c = vertices[triangles[:, 0]], vertices[triangles[:, 1]], vertices[triangles[:, 2]]
    face = numpy.cross(b - a, c - a)
    total = numpy.zeros((same.max() + 1, 3))
    for corner in range(3):
        numpy.add.at(total, same[triangles[:, corner]], face)
    out = total[same]
    return out / numpy.maximum(numpy.linalg.norm(out, axis=1, keepdims=True), 1e-12)


def laid_out(vertices, triangles, places, facings, size, first=None):
    """
    For each dot of the picture: where on the animal it lies, which way the animal faces there, whether it lies on the animal,
    and the triangle it is in. Where two triangles share a dot, the one laid first keeps it, and the triangles marked first are
    laid first: a rat's whiskers and a mouse's are laid on part of the picture of its back.
    """
    where = numpy.zeros((size, size, 3), dtype=numpy.float32)
    facing = numpy.zeros((size, size, 3), dtype=numpy.float32)
    on = numpy.zeros((size, size), dtype=bool)
    held = numpy.zeros((size, size), dtype=bool)
    which = numpy.full((size, size), -1, dtype=numpy.int32)
    order = numpy.arange(len(triangles)) if first is None else numpy.concatenate([numpy.flatnonzero(first), numpy.flatnonzero(~first)])
    for number in order:
        a, b, c = triangles[number]
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
        take = inside & ~held[top:bottom + 1, left:right + 1] & (strictly | ~on[top:bottom + 1, left:right + 1])
        weights = numpy.stack([w0, w1, w2], axis=2)[take]
        where[top:bottom + 1, left:right + 1][take] = weights @ vertices[[a, b, c]]
        facing[top:bottom + 1, left:right + 1][take] = weights @ facings[[a, b, c]]
        on[top:bottom + 1, left:right + 1] |= take
        held[top:bottom + 1, left:right + 1] |= take & strictly
        which[top:bottom + 1, left:right + 1][take] = number
    length = numpy.linalg.norm(facing, axis=2, keepdims=True)
    return where, facing / numpy.maximum(length, 1e-6), on, which


def tile(pictures, name, span, density, broad):
    """
    A picture made to join up with itself and sized to cover span of the model, with only broad of its broad light and dark kept,
    and its colour brought to the one COLOUR gives it where it has one.
    """
    dots = max(64, int(round(span * density)))
    joined = numpy.asarray(bake.joined(Image.open(pictures / f'{name}-{PICKED[name]}.png').convert('RGB')))
    # Made smaller three by three and the middle kept: made smaller alone, its edges are filtered as ends and show as seams.
    image = Image.fromarray(numpy.tile(joined, (3, 3, 1))).resize((dots * 3, dots * 3), Image.LANCZOS).crop((dots, dots, dots * 2, dots * 2))
    own = numpy.asarray(image).astype(numpy.float32) / 255.0
    thrice = Image.fromarray(numpy.tile(numpy.asarray(image), (3, 3, 1)))
    blurred = numpy.asarray(thrice.filter(ImageFilter.GaussianBlur(dots / 6.0))).astype(numpy.float32)[dots:2 * dots, dots:2 * dots] / 255.0
    mean = own.reshape(-1, 3).mean(axis=0)
    made = own / numpy.maximum(blurred, 1e-3) * (mean + broad * (blurred - mean))
    if name.startswith('coat-'):
        made = mean + (made - mean) * COAT_GRAIN
    if name in COLOUR:
        made = made / mean * numpy.array(COLOUR[name], dtype=numpy.float32)
    return numpy.clip(made, 0.0, 1.0)


def laid(picture, where, facing, span, stretch=1.0):
    """
    A surface laid on from three sides, each side counting by how squarely the animal faces it. Stretched along y, it hangs in
    strands. It is laid twice, the second time larger and moved, and the two mixed by noise over the animal: laid once, the
    squares it repeats in showed across a flank.
    """
    dots = picture.shape[0]
    weight = numpy.abs(facing) ** 4
    weight = weight / numpy.maximum(weight.sum(axis=2, keepdims=True), 1e-6)
    mean = picture.reshape(-1, 3).mean(axis=0)
    layers = []
    for size, shift in ((1.0, 0.0), (1.37, 0.43)):
        scale = dots / (span * size)
        x, y, z = where[..., 0] * scale + shift * dots, where[..., 1] * scale / stretch, where[..., 2] * scale + shift * dots
        layers.append(bake.sampled(picture, z, -y) * weight[..., 0:1] + bake.sampled(picture, x, z) * weight[..., 1:2]
                      + bake.sampled(picture, x, -y) * weight[..., 2:3])
    share = bake.ramp(0.35, 0.65, bake.cloudy(where, 9.0, 77, 3))[..., None]
    mixed = layers[0] * (1 - share) + layers[1] * share
    # Two pictures mixed are flatter than either: the grain is given back its strength.
    return mean + (mixed - mean) * (1.0 + 0.6 * (1 - numpy.abs(2 * share - 1)))


def cells(where, grain, seed):
    """
    How near each place is to the edge of a cell of a pattern of cells over the animal, 1 on an edge and 0 at the middle of a
    cell: the furrows of the bare skin of a nose, a cell to every 1 / grain of the model's units.
    """
    p = where * grain
    low = numpy.floor(p).astype(numpy.int64)
    first = numpy.full(where.shape[:2], 9.0, dtype=numpy.float32)
    second = numpy.full(where.shape[:2], 9.0, dtype=numpy.float32)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                ix, iy, iz = low[..., 0] + dx, low[..., 1] + dy, low[..., 2] + dz
                middle = numpy.stack([ix + bake.scattered(ix, iy, iz, seed), iy + bake.scattered(ix, iy, iz, seed + 1),
                                      iz + bake.scattered(ix, iy, iz, seed + 2)], axis=2)
                d = numpy.linalg.norm(p - middle, axis=2)
                second = numpy.where(d < first, first, numpy.minimum(second, d))
                first = numpy.minimum(first, d)
    return bake.ramp(0.18, 0.0, second - first)


def ellipse(where, middle, half):
    """How far a place is inside an ellipsoid, 1 at its middle and 0 at its edge and beyond."""
    d = numpy.sqrt(sum(((where[..., axis] - middle[axis]) / half[axis]) ** 2 for axis in range(3)))
    return numpy.clip(1.0 - d, 0.0, 1.0)


def met(vertices, triangles, across, along, start, end):
    """The furthest a line through the model along one axis, at fixed values of the other two, meets its surface towards end from start."""
    other = [axis for axis in range(3) if axis != along]
    best = None
    for a, b, c in triangles:
        p = vertices[[a, b, c]]
        u, v = p[:, other[0]], p[:, other[1]]
        area = (v[1] - v[2]) * (u[0] - u[2]) + (u[2] - u[1]) * (v[0] - v[2])
        if abs(area) < 1e-12:
            continue
        w0 = ((v[1] - v[2]) * (across[0] - u[2]) + (u[2] - u[1]) * (across[1] - v[2])) / area
        w1 = ((v[2] - v[0]) * (across[0] - u[2]) + (u[0] - u[2]) * (across[1] - v[2])) / area
        w2 = 1.0 - w0 - w1
        if min(w0, w1, w2) < 0:
            continue
        at = w0 * p[0, along] + w1 * p[1, along] + w2 * p[2, along]
        if (at - start) * (end - start) >= 0 and (best is None or abs(at - start) > abs(best - start)):
            best = at
    return best


def eye_places(vertices, triangles, eye):
    """Where each eye is: as far out as the model's surface goes at the place KINDS gives, on the side of the head or on its front."""
    how, first, second = eye
    places = []
    for side in (-1.0, 1.0):
        if how == 'side':
            # A line across the head at that height and length: the eye is where it leaves the head on this side.
            x = met(vertices, triangles, (first, second), 0, 0.0, side)
            place = None if x is None else (x, first, second)
        else:
            # A line along the head at that place across and height: the eye is where it leaves the front of the head.
            z = met(vertices, triangles, (side * first, second), 2, 0.0, -1.0)
            place = None if z is None else (side * first, second, z)
        if place is None:
            raise SystemExit(f'no surface of the head at the eye {eye}')
        places.append(numpy.array(place, dtype=numpy.float32))
    return places


def nose(colour, where, facing, landmarks, picture, part, ragged):
    """The bare skin of the nose with its two nostrils, laid over the colour."""
    middle, half = landmarks['muzzle']
    bare = bake.ramp(0.0, 0.25, ellipse(where, middle, half) + 0.08 * (ragged - 0.5)) * (part == 'body')
    colour = colour * (1 - bare[..., None]) + picture * bare[..., None]
    across, high, wide, tall = landmarks['nostrils']
    front = bake.ramp(-0.3, -0.7, facing[..., 2])
    for side in (-1.0, 1.0):
        # A nostril is a comma: rounder towards the middle of the nose, drawn out towards the side.
        hole = ellipse(where, (side * across, high, middle[2]), (wide, tall, 0.03))
        hole = numpy.maximum(hole, ellipse(where, (side * (across + wide), high + tall * 0.7, middle[2]), (wide * 0.7, tall * 0.6, 0.03)))
        colour = colour * (1 - 0.82 * (bake.ramp(0.0, 0.35, hole) * front)[..., None])
    return colour


def eyes(colour, where, places, ball, lid):
    """A dark glossy eyeball in a lid of bare dark skin at each place."""
    for place in places:
        reach = numpy.linalg.norm(where - place, axis=2)
        inside = bake.ramp(ball, ball * 0.7, reach)
        rim = bake.ramp(lid, ball * 1.05, reach) * (1 - inside)
        iris = numpy.array([0.12, 0.07, 0.04], dtype=numpy.float32) * (0.6 + 0.4 * bake.ramp(0.0, ball, reach))[..., None]
        colour = colour * (1 - rim[..., None] * 0.75)
        colour = colour * (1 - inside[..., None]) + iris * inside[..., None]
    return colour


def hooves(colour, where, part, picture, height, ragged):
    shod = numpy.maximum(bake.ramp(height + 0.006, height - 0.006, where[..., 1] + 0.01 * (ragged - 0.5)) * (part == 'body'), part == 'sole')[..., None]
    return colour * (1 - shod) + picture * shod


def markings(breed, seed, where, facing, part):
    """How white a cow's coat is at each dot, 0 to 1, for the breed: the rest is the breed's own colour."""
    x, y, z = where[..., 0], where[..., 1], where[..., 2]
    # Patches with ragged edges: broad noise for their shapes and fine noise for the hairs along their edges.
    shapes = bake.cloudy(where, 3.2, 100 + seed, 4)
    ragged = shapes + 0.035 * (bake.cloudy(where, 90.0, 300 + seed, 2) - 0.5)
    head = bake.ramp(-0.66, -0.72, z)
    leg = bake.ramp(0.3, 0.2, y)
    belly = bake.ramp(-0.35, -0.75, facing[..., 1]) * bake.ramp(-0.62, -0.5, z) * bake.ramp(0.36, 0.3, z)
    switch = (part == 'tail') * bake.ramp(0.5, 0.4, y)
    if breed == 'angus':
        return numpy.zeros(x.shape, dtype=numpy.float32)
    if breed == 'holstein':
        # About half white, a little more on the later seeds; the head dark but for a blaze down the front of the face.
        white = bake.ramp(0.495 - 0.012 * seed, 0.505 - 0.012 * seed, ragged)
        blaze = bake.ramp(0.03, 0.012, numpy.abs(x)) * bake.ramp(-0.35, -0.7, facing[..., 2]) * bake.ramp(0.74, 0.8, y) * head
        white = white * (1.0 - 0.8 * head) + blaze
        white = numpy.maximum(white, numpy.maximum(leg * bake.ramp(0.1, 0.16, y + 0.06 * ragged), belly))
        return numpy.clip(numpy.maximum(white, switch), 0.0, 1.0)
    # Simmental: a white face, white below the knee, on the belly and at the end of the tail, and a few white patches on the body.
    white = bake.ramp(0.6 + 0.02 * seed, 0.61 + 0.02 * seed, ragged) * (1.0 - head)
    face = head * bake.ramp(-0.4, -0.75, facing[..., 2] - 0.2 * numpy.abs(facing[..., 0]))
    goggles = sum(ellipse(where, (side * 0.08, 0.865, -0.79), (0.05, 0.03, 0.05)) for side in (-1.0, 1.0))
    face = face * (1.0 - bake.ramp(0.0, 0.25, goggles))
    white = numpy.maximum(white, numpy.maximum(face, numpy.maximum(leg, belly)))
    return numpy.clip(numpy.maximum(white, switch), 0.0, 1.0)


def cattle(kind, breed, seed, maps, pictures, density, places):
    where, facing, on, part = maps
    landmarks = KINDS[kind][2]
    black, white_coat = tile(pictures, 'coat-black', SPAN['coat'], density, BROAD), tile(pictures, 'coat-white', SPAN['coat'], density, BROAD)
    own = tile(pictures, 'coat-red', SPAN['coat'], density, BROAD) if breed == 'simmental' else black
    white = markings(breed, seed, where, facing, part)[..., None]
    coat = laid(own, where, facing, SPAN['coat']) * (1 - white) + laid(white_coat, where, facing, SPAN['coat']) * white
    # The switch at the end of the tail hangs in long strands.
    switch = ((part == 'tail') * bake.ramp(0.5, 0.42, where[..., 1]))[..., None]
    hung = laid(own, where, facing, SPAN['coat'], 6.0) * (1 - white) + laid(white_coat, where, facing, SPAN['coat'], 6.0) * white
    colour = (coat * (1 - switch) + hung * switch) * (0.93 + 0.14 * bake.cloudy(where, 6.0, 41, 3))[..., None]

    skin_tile = tile(pictures, 'skin-udder', SPAN['skin'], density, 0.6)
    skin = laid(skin_tile, where, facing, SPAN['skin'])
    udder = (part == 'udder')[..., None]
    if kind == 'cow':
        # The teats darker than the bag they hang from.
        teat = bake.ramp(0.3, 0.26, where[..., 1])[..., None]
        colour = numpy.where(udder, skin * (1 - 0.25 * teat), colour)
    else:
        colour = numpy.where(udder, colour * 0.6 + skin * 0.4, colour)
    colour = numpy.where((part == 'inside ear')[..., None], colour * 0.45 + skin * 0.55 * 0.85, colour)
    colour = numpy.where((part == 'mouth')[..., None], skin * numpy.array([0.75, 0.5, 0.52], dtype=numpy.float32), colour)

    hoof = laid(tile(pictures, 'hoof', SPAN['hoof'], density, 0.0), where, facing, SPAN['hoof'])
    ragged = bake.cloudy(where, 90.0, 5, 2)
    colour = hooves(colour, where, part, hoof, landmarks['hoof'], ragged)
    # A horn is pale at its root and darkens to its tip.
    horn = (part == 'horn')[..., None]
    if horn.any():
        out = bake.ramp(0.06, 0.15, numpy.abs(where[..., 0]))[..., None]
        pale = hoof * numpy.array([2.6, 2.4, 2.0], dtype=numpy.float32)
        colour = numpy.where(horn, numpy.clip(pale * (1 - out) + hoof * 0.8 * out, 0.0, 1.0), colour)

    # The nose is bare skin in fine furrows, pink with grey mottling on a pale or a red breed and near black on a black one.
    mottled = bake.ramp(0.45, 0.6, bake.cloudy(where, 60.0, 9, 3))[..., None]
    tint = (NOSE_BLACK if breed == 'angus' else NOSE_PINK * (1 - 0.45 * mottled) + NOSE_GREY * 0.45 * mottled)
    picture = skin / skin_tile.reshape(-1, 3).mean(axis=0) * tint * (1 - 0.35 * cells(where, 260.0, 11))[..., None]
    colour = nose(colour, where, facing, landmarks, picture, part, ragged)
    colour = eyes(colour, where, places, *landmarks['eye'][1:])
    # Under the belly, between the legs and under the jaw the hair is in its own shade.
    return colour * (1 - 0.18 * bake.ramp(-0.2, -0.9, facing[..., 1])[..., None])


def pig(kind, breed, seed, maps, pictures, density, places):
    where, facing, on, part = maps
    landmarks = KINDS[kind][2]
    pink = breed == 'landrace'
    skin = laid(tile(pictures, 'pig-pink' if pink else 'pig-black', SPAN['pig'], density, 0.0), where, facing, SPAN['pig'])
    colour = skin
    rosy = numpy.array([1.0, 0.82, 0.8] if pink else [0.85, 0.8, 0.8], dtype=numpy.float32)
    colour = numpy.where(numpy.isin(part, ('inside ear', 'udder'))[..., None], skin * rosy, colour)
    colour = numpy.where((part == 'mouth')[..., None], skin * numpy.array([0.8, 0.55, 0.55], dtype=numpy.float32), colour)
    ragged = bake.cloudy(where, 160.0, 5, 2)
    hoof = laid(tile(pictures, 'hoof', SPAN['hoof'] * 0.6, density, 0.0), where, facing, SPAN['hoof'] * 0.6)
    if pink:
        hoof = numpy.clip(hoof * numpy.array([2.2, 1.9, 1.8], dtype=numpy.float32), 0.0, 1.0)
    colour = hooves(colour, where, part, hoof, landmarks['hoof'], ragged)
    snout = skin * numpy.array([1.0, 0.8, 0.8] if pink else [0.75, 0.72, 0.72], dtype=numpy.float32) * (1 - 0.2 * cells(where, 700.0, 11))[..., None]
    colour = nose(colour, where, facing, landmarks, snout, part, ragged)
    colour = eyes(colour, where, places, *landmarks['eye'][1:])
    return colour * (1 - 0.15 * bake.ramp(-0.2, -0.9, facing[..., 1])[..., None])


def sheep(kind, breed, seed, maps, pictures, density, places):
    where, facing, on, part = maps
    landmarks = KINDS[kind][2]
    suffolk = breed == 'suffolk'
    wool = laid(tile(pictures, 'wool-cream', 0.08, density, 0.0), where, facing, 0.08)
    short = tile(pictures, 'coat-black', SPAN['coat'] * 0.5, density, 0.0)
    if not suffolk:
        short = numpy.clip(tile(pictures, 'coat-white', SPAN['coat'] * 0.5, density, 0.0) * numpy.array([0.93, 0.89, 0.8], dtype=numpy.float32), 0.0, 1.0)
    hair = laid(short, where, facing, SPAN['coat'] * 0.5)
    ragged = bake.cloudy(where, 120.0, 5, 2)
    # The face and the legs are short hair; the wool stops behind the poll and above the knee in a ragged edge.
    bare = numpy.maximum(bake.ramp(-0.3, -0.325, where[..., 2] + 0.012 * (ragged - 0.5)), bake.ramp(0.19, 0.15, where[..., 1] + 0.02 * (ragged - 0.5)))
    bare = numpy.where(numpy.isin(part, ('ear', 'inside ear')), 1.0, bare)[..., None]
    colour = wool * (1 - bare) + hair * bare
    skin = laid(tile(pictures, 'skin-udder', SPAN['skin'] * 0.5, density, 0.6), where, facing, SPAN['skin'] * 0.5)
    inner = skin * (numpy.array([0.35, 0.3, 0.3], dtype=numpy.float32) if suffolk else numpy.array([0.95, 0.8, 0.78], dtype=numpy.float32))
    colour = numpy.where((part == 'inside ear')[..., None], colour * 0.4 + inner * 0.6, colour)
    colour = numpy.where((part == 'udder')[..., None], colour * 0.5 + skin * 0.5, colour)
    colour = numpy.where((part == 'mouth')[..., None], skin * numpy.array([0.6, 0.4, 0.42], dtype=numpy.float32), colour)
    hoof = laid(tile(pictures, 'hoof', SPAN['hoof'] * 0.5, density, 0.0), where, facing, SPAN['hoof'] * 0.5)
    colour = hooves(colour, where, part, hoof, landmarks['hoof'], ragged)
    colour = numpy.where((part == 'horn')[..., None], numpy.clip(hoof * numpy.array([3.4, 3.1, 2.6], dtype=numpy.float32), 0.0, 1.0), colour)
    nose_skin = skin * (numpy.array([0.25, 0.23, 0.24], dtype=numpy.float32) if suffolk else numpy.array([0.85, 0.65, 0.65], dtype=numpy.float32))
    colour = nose(colour, where, facing, landmarks, nose_skin, part, ragged)
    colour = eyes(colour, where, places, *landmarks['eye'][1:])
    return colour * (1 - 0.15 * bake.ramp(-0.2, -0.9, facing[..., 1])[..., None])


def deer(kind, breed, seed, maps, pictures, density, places):
    where, facing, on, part = maps
    landmarks = KINDS[kind][2]
    x, y, z = where[..., 0], where[..., 1], where[..., 2]
    tall = float(y[on].max())
    span = SPAN['coat'] * 0.7
    brown = laid(tile(pictures, 'coat-deer', span, density, 0.0), where, facing, span)
    white_coat = laid(tile(pictures, 'coat-white', span, density, 0.0), where, facing, span)
    ragged = bake.cloudy(where, 90.0, 5, 2)
    soft = 0.03 * (ragged - 0.5)
    # White under the belly and the tail, on the inside of the legs, under the jaw, in a band behind the nose and round the eyes.
    middle, half = landmarks['muzzle']
    belly = bake.ramp(-0.35, -0.7, facing[..., 1] + soft) * bake.ramp(0.62 * tall, 0.5 * tall, y)
    inside = bake.ramp(-0.2, -0.6, facing[..., 0] * numpy.sign(x) + soft) * bake.ramp(0.5 * tall, 0.4 * tall, y)
    head = bake.ramp(middle[2] + 0.2 * tall, middle[2] + 0.12 * tall, z)
    throat = head * bake.ramp(-0.2, -0.55, facing[..., 1] + soft)
    along = (z - float(z[on].min())) / float(numpy.ptp(z[on]))
    rump = bake.ramp(-0.2, 0.5, facing[..., 2] + soft) * bake.ramp(0.62 * tall, 0.7 * tall, y) * bake.ramp(0.6, 0.8, along)
    band = ellipse(where, (0.0, middle[1] + 0.3 * half[1], middle[2] + 0.04 * tall), (half[0] * 2.2, half[1] * 2.2, 0.03 * tall))
    band = bake.ramp(0.0, 0.2, band) * (1 - bake.ramp(0.0, 0.3, ellipse(where, middle, (half[0] * 1.3, half[1] * 1.3, half[2] * 2.0))))
    rings = sum(bake.ramp(0.0, 0.25, ellipse(where, place, (0.022 * tall / 0.8,) * 3)) for place in places)
    white = numpy.clip(numpy.maximum.reduce([belly, inside, throat, rump, band, rings]), 0.0, 1.0)
    if kind == 'fawn':
        # Spots in loose rows down the back and the upper flanks.
        spots = bake.ramp(0.62, 0.66, bake.cloudy(where, 140.0, 21, 1)) * bake.ramp(0.55 * tall, 0.75 * tall, y) * bake.ramp(-0.1, 0.3, facing[..., 1])
        white = numpy.maximum(white, spots * (1 - head))
    colour = brown * (1 - white[..., None]) + white_coat * white[..., None]
    # The back darker than the flanks.
    colour = colour * (1 - 0.15 * bake.ramp(0.3, 0.9, facing[..., 1]))[..., None]
    skin = laid(tile(pictures, 'skin-udder', SPAN['skin'] * 0.5, density, 0.6), where, facing, SPAN['skin'] * 0.5)
    colour = numpy.where((part == 'inside ear')[..., None], white_coat * 0.7 + skin * 0.3, colour)
    hoof = laid(tile(pictures, 'hoof', SPAN['hoof'] * 0.5, density, 0.0), where, facing, SPAN['hoof'] * 0.5)
    colour = hooves(colour, where, part, hoof, landmarks['hoof'], ragged)
    pale = numpy.clip(hoof * numpy.array([3.3, 2.8, 2.2], dtype=numpy.float32), 0.0, 1.0)
    colour = numpy.where((part == 'antler')[..., None], pale * (0.75 + 0.35 * bake.ramp(0.85 * tall, tall, y))[..., None], colour)
    colour = nose(colour, where, facing, landmarks, skin * numpy.array([0.12, 0.11, 0.12], dtype=numpy.float32), part, ragged)
    colour = eyes(colour, where, places, *landmarks['eye'][1:])
    return colour * (1 - 0.12 * bake.ramp(-0.2, -0.9, facing[..., 1])[..., None])


def bird(kind, breed, seed, maps, pictures, density, places):
    where, facing, on, part = maps
    landmarks = KINDS[kind][2]
    y, z = where[..., 1], where[..., 2]
    tall = float(y[on].max())
    span = 0.18 * tall
    if breed == 'chick':
        colour = laid(tile(pictures, 'down-yellow', span, density, 0.0), where, facing, span)
    else:
        colour = laid(tile(pictures, 'feathers-red' if breed == 'rhodeisland' else 'feathers-white', span, density, 0.3), where, facing, span)
        if kind == 'cockerel' and breed == 'rhodeisland':
            # A red cockerel's tail is black with a green sheen.
            colour = numpy.where((part == 'tail')[..., None], colour * numpy.array([0.16, 0.2, 0.15], dtype=numpy.float32), colour)
    skin = laid(tile(pictures, 'skin-udder', span, density, 0.6), where, facing, span)
    red = numpy.clip(skin * numpy.array([1.15, 0.2, 0.18], dtype=numpy.float32), 0.0, 1.0)
    if breed != 'chick':
        # The comb, the wattles and the bare face round the eye are red skin.
        face = numpy.maximum.reduce([bake.ramp(0.0, 0.3, ellipse(where, place, (0.03 * tall,) * 3)) for place in places])
        bare = numpy.maximum(face, numpy.isin(part, ('comb', 'wattle')))
        bare = numpy.maximum(bare, bake.ramp(0.285 * tall / 0.3, 0.29 * tall / 0.3, y) * bake.ramp(-0.09, -0.1, z))
        colour = colour * (1 - bare[..., None]) + red * bare[..., None]
    horn = numpy.clip(laid(tile(pictures, 'hoof', span, density, 0.0), where, facing, span) * numpy.array([5.0, 3.9, 2.2], dtype=numpy.float32), 0.0, 1.0)
    beak = bake.ramp(landmarks['beak'] + 0.002, landmarks['beak'] - 0.002, z)[..., None]
    colour = colour * (1 - beak) + horn * beak
    legs = numpy.clip(laid(tile(pictures, 'bird-leg', span * 0.5, density, 0.0), where, facing, span * 0.5) * numpy.array([1.0, 0.92, 0.7], dtype=numpy.float32), 0.0, 1.0)
    colour = numpy.where((part == 'leg')[..., None], legs, colour)
    # A hen's eye is orange round a black pupil; a chick's is black.
    for place in places:
        reach = numpy.linalg.norm(where - place, axis=2)
        ball, lid = landmarks['eye'][1:]
        iris = bake.ramp(ball, ball * 0.8, reach)[..., None]
        pupil = bake.ramp(ball * 0.5, ball * 0.4, reach)[..., None]
        ring = numpy.array([0.05, 0.04, 0.03] if breed == 'chick' else [0.85, 0.45, 0.1], dtype=numpy.float32)
        colour = colour * (1 - iris) + ring * iris
        colour = colour * (1 - pupil) + numpy.array([0.02, 0.02, 0.02], dtype=numpy.float32) * pupil
    return colour * (1 - 0.15 * bake.ramp(-0.2, -0.9, facing[..., 1])[..., None])


def small(kind, breed, seed, maps, pictures, density, places):
    where, facing, on, part = maps
    landmarks = KINDS[kind][2]
    x, y, z = where[..., 0], where[..., 1], where[..., 2]
    # How tall the body is: a mouse's tail stands higher than its back.
    tall = float(y[part == 'body'].max())
    span = 0.25 * tall
    fur_name = {'grey': 'fur-grey', 'white': 'fur-white'}.get(breed, 'fur-brown')
    fur = laid(tile(pictures, fur_name, span, density, 0.0), where, facing, span)
    fur = fur * numpy.array({'appalachian': (0.72, 0.68, 0.64), 'swamp': (0.8, 0.8, 0.8), 'golden': (1.2, 0.92, 0.55)}.get(breed, (1.0, 1.0, 1.0)),
                            dtype=numpy.float32)
    if kind == 'rat' and breed == 'grey':
        fur = fur * numpy.array([0.85, 0.82, 0.78], dtype=numpy.float32)
    pale = numpy.clip(laid(tile(pictures, 'fur-white', span, density, 0.0), where, facing, span), 0.0, 1.0)
    ragged = bake.cloudy(where, 2.0 / span, 5, 2)
    soft = 0.04 * (ragged - 0.5)
    # Pale under the belly and the throat, and up the flanks of a mouse to a line.
    under = bake.ramp(-0.3, -0.65, facing[..., 1] + soft)
    if kind == 'mouse':
        under = numpy.maximum(under, bake.ramp(0.45 * tall, 0.38 * tall, y + soft * tall))
    white = under * (0.5 if breed in ('grey', 'white') else 1.0)
    colour = fur * (1 - white[..., None]) + pale * white[..., None]
    if kind == 'rabbit':
        # The cottontail's white tail.
        puff = ellipse(where, (0.0, 0.035, float(z[on].max()) - 0.005), (0.02, 0.025, 0.02))
        colour = colour * (1 - bake.ramp(0.0, 0.3, puff)[..., None]) + pale * bake.ramp(0.0, 0.3, puff)[..., None]
    if kind == 'raccoon':
        # The black mask across the eyes under a white brow, a white muzzle, black feet and a ringed tail.
        mask = sum(ellipse(where, place + numpy.array([0.012 * numpy.sign(place[0]), -0.004, 0.008], dtype=numpy.float32), (0.03, 0.013, 0.03))
                   for place in places)
        brow = sum(ellipse(where, place + numpy.array([0.006 * numpy.sign(place[0]), 0.016, 0.004], dtype=numpy.float32), (0.028, 0.007, 0.025))
                   for place in places)
        snout = ellipse(where, (0.0, 0.19, -0.29), (0.03, 0.02, 0.03)) * (1 - bake.ramp(0.0, 0.3, ellipse(where, (0.0, 0.19, -0.305), (0.014, 0.01, 0.015))))
        light = numpy.maximum(bake.ramp(0.0, 0.25, brow), bake.ramp(0.0, 0.3, snout))
        colour = colour * (1 - light[..., None]) + pale * light[..., None]
        colour = colour * (1 - 0.88 * bake.ramp(0.0, 0.25, mask + 0.1 * (ragged - 0.5)))[..., None]
        tail = bake.ramp(0.1, 0.13, z)
        rings = 0.5 + 0.5 * numpy.cos((z - 0.1) / 0.045 * 2 * numpy.pi)
        colour = colour * (1 - 0.85 * tail * bake.ramp(0.45, 0.6, rings + soft))[..., None]
        colour = colour * (1 - 0.8 * bake.ramp(0.06, 0.035, y))[..., None]
    skin = laid(tile(pictures, 'skin-udder', span, density, 0.6), where, facing, span)
    if kind in ('rat', 'mouse'):
        bare = numpy.isin(part, ('tail', 'foot'))
        colour = numpy.where(bare[..., None], skin, colour)
        colour = numpy.where((part == 'ear')[..., None], skin * 0.9, colour)
    if kind == 'rabbit':
        colour = numpy.where((part == 'ear')[..., None], colour * 0.9 + skin * 0.1, colour)
    colour = numpy.where((part == 'whisker')[..., None], numpy.array([0.06, 0.05, 0.05], dtype=numpy.float32), colour)
    pink = breed == 'white' or kind in ('rat', 'mouse')
    nose_skin = skin * (numpy.array([1.0, 0.85, 0.85] if pink else [0.55, 0.45, 0.45] if kind == 'rabbit' else [0.12, 0.11, 0.11], dtype=numpy.float32))
    colour = nose(colour, where, facing, landmarks, nose_skin, part, ragged)
    # An albino's eyes are red; the rest dark and glossy.
    eye_colour = numpy.array([0.55, 0.06, 0.08] if breed == 'white' else [0.04, 0.03, 0.03], dtype=numpy.float32)
    colour = numpy.where((part == 'eyeball')[..., None], eye_colour, colour)
    if places:
        colour = eyes(colour, where, places, *landmarks['eye'][1:])
        if breed == 'white':
            for place in places:
                inside = bake.ramp(landmarks['eye'][1], landmarks['eye'][1] * 0.7, numpy.linalg.norm(where - place, axis=2))[..., None]
                colour = colour * (1 - inside) + eye_colour * inside
    return colour * (1 - 0.12 * bake.ramp(-0.2, -0.9, facing[..., 1])[..., None])


def turkey(kind, breed, seed, maps, pictures, density, places):
    where, facing, on, part = maps
    landmarks = KINDS[kind][2]
    x, y, z = where[..., 0], where[..., 1], where[..., 2]
    # The gobbler's model is 0.45 tall to the top of its head; a poult's places are its places made smaller.
    k = float(y[on].max()) / 0.45
    span = 0.04 * k
    feathers = laid(tile(pictures, 'feathers-turkey', span, density, 0.3), where, facing, span)
    # A gobbler is dark bronze, a hen and a poult browner.
    feathers = feathers * numpy.array([0.6, 0.55, 0.5] if breed == 'gobbler' else [0.95, 0.88, 0.75], dtype=numpy.float32)
    ragged = bake.cloudy(where, 2.0 / span, 5, 2)
    pale = laid(tile(pictures, 'coat-white', span, density, 0.0), where, facing, span)
    # Flight feathers barred white and black out along the spread wings, and a rufous band at the end of the tail.
    wing = bake.ramp(0.12 * k, 0.16 * k, numpy.abs(x))
    bars = bake.ramp(0.4, 0.6, 0.5 + 0.5 * numpy.sin(numpy.abs(x) / (0.012 * k) * 2 * numpy.pi))
    colour = feathers * (1 - (wing * bars)[..., None]) + pale * (wing * bars)[..., None]
    tip = bake.ramp(0.2 * k, 0.24 * k, z + 0.01 * k * (ragged - 0.5))[..., None]
    colour = colour * (1 - tip) + feathers * numpy.array([1.9, 1.4, 1.0], dtype=numpy.float32) * tip
    skin = laid(tile(pictures, 'skin-udder', span, density, 0.6), where, facing, span)
    # The bare head and neck: blue over the face and crown, red down the wattle, pale pink behind. A hen's are duller and a
    # poult's head is feathered brown.
    head = bake.ramp(-0.16 * k, -0.18 * k, z) * bake.ramp(0.32 * k, 0.35 * k, y)
    blue = numpy.array([0.35, 0.5, 0.95] if breed == 'gobbler' else [0.5, 0.55, 0.7], dtype=numpy.float32)
    red = numpy.array([1.15, 0.22, 0.2] if breed == 'gobbler' else [0.9, 0.45, 0.42], dtype=numpy.float32)
    face = bake.ramp(-0.5, 0.2, -facing[..., 2] + 0.4 * facing[..., 1])
    wattle = bake.ramp(0.4 * k, 0.37 * k, y) * bake.ramp(-0.3, -0.7, facing[..., 2] - 0.5 * facing[..., 1])
    bare = skin * (blue * face[..., None] + numpy.array([1.0, 0.85, 0.85], dtype=numpy.float32) * (1 - face[..., None]))
    bare = bare * (1 - wattle[..., None]) + skin * red * wattle[..., None]
    if breed == 'poult':
        bare = laid(tile(pictures, 'fur-brown', span, density, 0.0), where, facing, span)
    colour = colour * (1 - head[..., None]) + numpy.clip(bare, 0.0, 1.0) * head[..., None]
    beak = bake.ramp(-0.245 * k, -0.255 * k, z) * head
    colour = colour * (1 - beak[..., None]) + skin * numpy.array([0.95, 0.85, 0.7], dtype=numpy.float32) * beak[..., None]
    legs = bake.ramp(0.12 * k, 0.1 * k, y)[..., None]
    colour = colour * (1 - legs) + skin * numpy.array([0.85, 0.72, 0.72], dtype=numpy.float32) * legs
    colour = eyes(colour, where, places, *landmarks['eye'][1:])
    return colour * (1 - 0.12 * bake.ramp(-0.2, -0.9, facing[..., 1])[..., None])


PAINT = {'turkey': turkey, 'cattle': cattle, 'pig': pig, 'sheep': sheep, 'deer': deer, 'bird': bird, 'small': small}


def view(vertices, triangles, places, skin, side, dots):
    """The animal from one side, from in front or from behind, wearing its skin and lit from above and to one side."""
    across, depth, sign, toward = {'left': (2, 0, 1.0, 1.0), 'right': (2, 0, -1.0, -1.0), 'front': (0, 2, -1.0, -1.0), 'back': (0, 2, 1.0, 1.0)}[side]
    low, high = vertices.min(axis=0), vertices.max(axis=0)
    span = max(high[across] - low[across], high[1] - low[1]) * 1.04
    scale = dots / span
    image = numpy.full((dots, dots, 3), 0.18, dtype=numpy.float32)
    nearest = numpy.full((dots, dots), -1e9, dtype=numpy.float32)
    light = numpy.array([0.4, 0.8, -0.45], dtype=numpy.float32)
    light /= numpy.linalg.norm(light)
    size = skin.shape[0]
    for a, b, c in triangles:
        p, t = vertices[[a, b, c]], places[[a, b, c]]
        sx = (p[:, across] - (low[across] + high[across]) / 2) * sign * scale + dots / 2
        sy = ((low[1] + high[1]) / 2 - p[:, 1]) * scale + dots / 2
        sz = p[:, depth] * toward
        left, right = int(max(0, numpy.floor(sx.min()))), int(min(dots - 1, numpy.ceil(sx.max())))
        top, bottom = int(max(0, numpy.floor(sy.min()))), int(min(dots - 1, numpy.ceil(sy.max())))
        area = (sy[1] - sy[2]) * (sx[0] - sx[2]) + (sx[2] - sx[1]) * (sy[0] - sy[2])
        if abs(area) < 1e-9 or right < left or bottom < top:
            continue
        normal = numpy.cross(p[1] - p[0], p[2] - p[0])
        normal /= max(numpy.linalg.norm(normal), 1e-9)
        shade = 0.35 + 0.65 * abs(float(normal @ light))
        xs, ys = numpy.meshgrid(numpy.arange(left, right + 1) + 0.5, numpy.arange(top, bottom + 1) + 0.5)
        w0 = ((sy[1] - sy[2]) * (xs - sx[2]) + (sx[2] - sx[1]) * (ys - sy[2])) / area
        w1 = ((sy[2] - sy[0]) * (xs - sx[2]) + (sx[0] - sx[2]) * (ys - sy[2])) / area
        w2 = 1.0 - w0 - w1
        near = w0 * sz[0] + w1 * sz[1] + w2 * sz[2]
        take = (w0 >= 0) & (w1 >= 0) & (w2 >= 0) & (near > nearest[top:bottom + 1, left:right + 1])
        u = (w0 * t[0, 0] + w1 * t[1, 0] + w2 * t[2, 0]) * size - 0.5
        v = (w0 * t[0, 1] + w1 * t[1, 1] + w2 * t[2, 1]) * size - 0.5
        image[top:bottom + 1, left:right + 1][take] = bake.sampled(skin, u, v)[take] * shade
        nearest[top:bottom + 1, left:right + 1][take] = near[take]
    return image


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--game', required=True)
    parser.add_argument('--pictures', required=True)
    parser.add_argument('--only', nargs='*', default=[])
    parser.add_argument('--preview')
    parser.add_argument('--size', type=int, default=2048)
    args = parser.parse_args()
    unknown = [name for name in args.only if name not in SKINS]
    if unknown:
        raise SystemExit('no such skin: ' + ' '.join(unknown))
    pictures = pathlib.Path(args.pictures)
    OUT.mkdir(parents=True, exist_ok=True)
    skinned = pathlib.Path(args.game) / 'media' / 'models_X' / 'Skinned'
    for kind, (file, family, landmarks, also) in KINDS.items():
        names = [name for name, (each, _, _) in SKINS.items() if each == kind and (not args.only or name in args.only)]
        if not names:
            continue
        vertices, triangles, places, _ = bake.model(skinned / f'{file}.x')
        facings = smooth(vertices, triangles)
        named = parts(family, vertices, triangles)
        where, facing, on, which = laid_out(vertices, triangles, places, facings, args.size, named == 'body')
        part = numpy.where(which >= 0, named[numpy.maximum(which, 0)], None)
        low, high = vertices.min(axis=0), vertices.max(axis=0)
        for other in also:
            v, t, u, _ = bake.model(skinned / f'{other}.x')
            w, fa, o, wh = laid_out(v, t, u, smooth(v, t), args.size)
            fill = o & ~on
            scale = (high - low) / (v.max(axis=0) - v.min(axis=0))
            moved = low + (w - v.min(axis=0)) * scale
            where[fill], facing[fill], on[fill] = moved[fill], fa[fill], True
            # Its pieces are told apart in the first's size, where the rules for them were measured.
            part[fill] = parts(family, low + (v - v.min(axis=0)) * scale, t)[wh[fill]]
        eye = [] if landmarks['eye'] is None else eye_places(vertices, triangles[named == 'body'], landmarks['eye'][0])
        # Dots of the picture to one of the model's units, about: the hide spans about 0.75 of the picture across the animal's length.
        density = args.size * 0.75 / float(vertices[:, 2].max() - vertices[:, 2].min())
        for name in names:
            _, breed, seed = SKINS[name]
            skin = bake.spread(numpy.clip(PAINT[family](kind, breed, seed, (where, facing, on, part), pictures, density, eye), 0.0, 1.0), on)
            Image.fromarray((skin * 255.0).round().astype(numpy.uint8), 'RGB').save(OUT / f'{name.lower()}.jpg', quality=90, subsampling=0)
            # An animal's skin is opaque all over: the cut says so.
            Image.fromarray(numpy.full(on.shape, 255, dtype=numpy.uint8), 'L').save(OUT / f'{name.lower()}.cut.png', optimize=True)
            print('baked', name, flush=True)
            if args.preview:
                preview = pathlib.Path(args.preview)
                preview.mkdir(parents=True, exist_ok=True)
                shots = [view(vertices, triangles, places, skin, side, 900) for side in ('left', 'right', 'front', 'back')]
                Image.fromarray((numpy.concatenate(shots, axis=1) * 255.0).round().astype(numpy.uint8), 'RGB').save(preview / f'{name}.jpg', quality=88)


if __name__ == '__main__':
    main()
