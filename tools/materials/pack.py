"""Puts generated pictures (tools/materials/generate.py) into src/textures/materials, which
tools/textures/compress.py packs for the mod's jar.

    python tools/materials/pack.py sprigs DIR linden=sprig-broadleaf-7 maple=... (one for each of SPRIGS)
    python tools/materials/pack.py surface DIR bark=bark-1993 grass=grass-7 ...
    python tools/materials/pack.py form

A sprig is cut out of its white ground, cleaned, and set into one picture with the others, four
to a row, in the order the mod's Flora counts them. A surface is saved as it is, its light drawn
in to its SPREAD where it has one. form reads the packed sprigs and writes sprigs-form.png beside
them: which way each leaf of each sprig is turned out of its card, and how much light it lets
through.
"""
import pathlib
import sys

import numpy
from PIL import Image

OUT = pathlib.Path(__file__).resolve().parents[2] / 'src' / 'textures' / 'materials'
SPRIGS = ['linden', 'maple', 'birch', 'pine', 'hemlock', 'holly', 'shrub', 'fern']
TILE = 1024
# What every sprig's leaves are on average once packed. The world shader divides by it, so a sprig gives a crown its leaves' light and dark and not its own green.
MEAN = numpy.array([0.26, 0.42, 0.12], dtype=numpy.float32)
# How much a surface's light may vary over its mean, as a standard deviation: the strengths the
# world shader lays it at were set for that. A picture with more is drawn in to it round its mean.
SPREAD = {'carpet': 0.092}


def cut(image):
    """Alpha from how far a pixel is from the white ground: what has colour, and what is dark."""
    rgb = numpy.asarray(image.convert('RGB')).astype(numpy.float32) / 255.0
    most, least = rgb.max(axis=2), rgb.min(axis=2)
    vivid = (most - least) / numpy.maximum(most, 1e-3)
    dark = 1.0 - most
    alpha = numpy.maximum(numpy.clip((vivid - 0.16) / 0.14, 0.0, 1.0), numpy.clip((dark - 0.45) / 0.15, 0.0, 1.0))
    return rgb, alpha


def touching(solid, seed):
    """Everything of solid joined to seed."""
    reached = seed & solid
    while True:
        grown = reached.copy()
        grown[1:] |= reached[:-1]
        grown[:-1] |= reached[1:]
        grown[:, 1:] |= reached[:, :-1]
        grown[:, :-1] |= reached[:, 1:]
        grown &= solid
        if (grown == reached).all():
            return reached
        reached = grown


def whole(rgb, alpha):
    """Leaves that run off the picture are cut straight across. They are taken out, unless the sprig is one piece that does."""
    solid = alpha > 0.3
    edge = numpy.zeros_like(solid)
    edge[0] = edge[-1] = edge[:, 0] = edge[:, -1] = True
    off = touching(solid, edge)
    if off.sum() > 0.5 * solid.sum():
        # One branch from the edge inward covers little of the picture, and is kept as it is.
        if solid.mean() < 0.3:
            return rgb, alpha
        if True:
            # Leaves edge to edge with the ground showing between them. A second layer of the same leaves, turned and moved
            # and in the shade of the first, lies under it, and a clump is cut out of the two, its outline wandering as a
            # clump of leaves does.
            size = alpha.shape[0]
            under_rgb = numpy.roll(numpy.rot90(rgb), (size // 3, size // 5), axis=(0, 1)) * 0.6
            under_alpha = numpy.roll(numpy.rot90(alpha), (size // 3, size // 5), axis=(0, 1))
            rgb = rgb * alpha[..., None] + under_rgb * (1.0 - alpha[..., None])
            top = alpha
            alpha = alpha + under_alpha * (1.0 - alpha)
            y, x = numpy.mgrid[0:size, 0:size].astype(numpy.float32) / size - 0.5
            round_ = numpy.arctan2(y, x)
            rim = 0.44 + 0.045 * numpy.sin(3.0 * round_ + 1.0) + 0.05 * numpy.sin(7.0 * round_ + 4.0) + 0.02 * numpy.sin(17.0 * round_ + 2.0)
            inside = rim - numpy.hypot(x, y)
            # The layer underneath stops short of the clump's edge, where it would be a dark rim round it.
            alpha = numpy.where(inside * size < 40.0, numpy.minimum(alpha, top), alpha)
            return rgb, alpha * numpy.clip(inside * size / 3.0, 0.0, 1.0)
    return rgb, numpy.where(off, 0.0, alpha)


# How many pixels in from a leaf's edge its colour is taken as its own.
RIM = 3.0


def vivid(rgb):
    return (rgb.max(axis=2) - rgb.min(axis=2)) / numpy.maximum(rgb.max(axis=2), 1e-3)


def spread(rgb, weight):
    """The picture with what has no weight given the colour of the nearest of what has."""
    colour = rgb * weight[..., None]
    levels = [(colour, weight)]
    while levels[-1][1].shape[0] > 1:
        c, w = levels[-1]
        h, v = c.shape[0] // 2, c.shape[1] // 2
        levels.append((c.reshape(h, 2, v, 2, 3).sum(axis=(1, 3)), w.reshape(h, 2, v, 2).sum(axis=(1, 3))))
    filled = levels[-1][0] / numpy.maximum(levels[-1][1][..., None], 1e-6)
    for c, w in reversed(levels[:-1]):
        up = numpy.repeat(numpy.repeat(filled, 2, axis=0), 2, axis=1)
        known = numpy.clip(w * 4.0, 0.0, 1.0)[..., None]
        filled = numpy.where(w[..., None] > 1e-4, c / numpy.maximum(w[..., None], 1e-6), up) * known + up * (1.0 - known)
    return filled


def sprig(path):
    from scipy import ndimage
    rgb, alpha = cut(Image.open(path).resize((TILE, TILE), Image.LANCZOS))
    rgb, alpha = whole(rgb, alpha)
    solid = alpha > 0.5
    if solid.sum() < 0.04 * solid.size:
        raise SystemExit(f'{path} is nearly all ground: {solid.mean():.3f} of it is sprig')
    # Brought to the common mean by its green leaves alone: a twig is not what the crown is coloured by.
    leaves = solid & (rgb[..., 1] > rgb[..., 0])
    rgb = numpy.clip(rgb * (MEAN / rgb[leaves].mean(axis=0)), 0.0, 1.0)
    print(f'{path.name}: {solid.mean():.2f} of the card is sprig')
    # The outermost pixels of a leaf are part ground, and so is the shadow a leaf threw on the ground beside it: pale and
    # grey, and seen against a dark wood a white line round every leaf. What is paler than the leaf by it takes its colour.
    deep = ndimage.distance_transform_edt(solid)
    inner = spread(rgb, numpy.clip(deep - RIM, 0.0, 1.0) * numpy.clip((vivid(rgb) - 0.25) / 0.1, 0.0, 1.0))
    paler = numpy.clip((vivid(inner) - vivid(rgb) - 0.06) / 0.12, 0.0, 1.0)
    rgb = rgb + (inner - rgb) * paler[..., None]
    # What is cut away takes the colour of the nearest leaf, so no white is mixed in at an edge when the picture is seen small.
    # A pixel on a leaf's edge is part ground: only what is well inside a leaf has a colour to give.
    return numpy.concatenate([spread(rgb, numpy.clip((alpha - 0.8) / 0.2, 0.0, 1.0)), alpha[..., None]], axis=2)


# Lengths below are pixels of a sprig's tile as it is packed.
# How far in from its edge a leaf goes on rising, how high it stands in its middle, and how high its veins and folds are for their brightness.
DOME_RUN, DOME_HIGH, DETAIL_HIGH = 16.0, 16.0, 16.0
# The least and the most a whole leaf is tipped out of the card, as a slope.
TILT_LEAST, TILT_MOST = 0.3, 0.9
# Leaves that touch are not always told apart, and a leaf is not flat from end to end: the whole sprig is also bent
# gently this way and that, over about this many pixels and by about this slope.
BEND_RUN, BEND_STEEP = 30.0, 0.3
# A patch smaller than this is no leaf to tip.
LEAF_LEAST = 150
# How much light a twig lets through, and a leaf with another under it. A single leaf lets through 1.
THROUGH_TWIG, THROUGH_TWO = 0.0, 0.35


def form(tile, seed):
    """One sprig's leaves as a surface: the slope of each pixel across and down, and how much light passes."""
    from scipy import ndimage
    rgb, alpha = tile[..., :3], tile[..., 3]
    solid = alpha > 0.5
    light = rgb @ numpy.array([0.299, 0.587, 0.114], dtype=numpy.float32)
    # A leaf ends where the picture is cut away or its brightness steps: at the leaf beside or under it.
    typical = numpy.median(light[solid])
    inside = solid & (ndimage.gaussian_gradient_magnitude(light, 1.5) < 0.085 * typical) & (light > 0.5 * typical)
    dome = 1.0 - numpy.exp(-ndimage.distance_transform_edt(inside) / DOME_RUN)
    detail = light - ndimage.gaussian_filter(light, 5.0)
    height = ndimage.gaussian_filter(DOME_HIGH * dome + DETAIL_HIGH * detail, 1.0)
    down, across = numpy.gradient(height)
    # Each leaf is tipped its own way, all of it together.
    leaves, count = ndimage.label(ndimage.binary_opening(inside, iterations=3))
    sizes = ndimage.sum_labels(numpy.ones_like(leaves), leaves, numpy.arange(count + 1))
    random = numpy.random.default_rng(seed)
    way, steep = random.uniform(0.0, 2.0 * numpy.pi, count + 1), random.uniform(TILT_LEAST, TILT_MOST, count + 1)
    steep[0] = 0.0
    steep[sizes < LEAF_LEAST] = 0.0
    tipped = leaves > 0
    weight = numpy.maximum(ndimage.gaussian_filter(tipped.astype(numpy.float32), 3.0), 1e-3)
    tip_across = ndimage.gaussian_filter((numpy.cos(way) * steep)[leaves].astype(numpy.float32), 3.0) / weight
    tip_down = ndimage.gaussian_filter((numpy.sin(way) * steep)[leaves].astype(numpy.float32), 3.0) / weight
    bend = [ndimage.gaussian_filter(random.standard_normal(light.shape).astype(numpy.float32), BEND_RUN) for _ in range(2)]
    x, y = tip_across - across + bend[0] * (BEND_STEEP / bend[0].std()), tip_down - down + bend[1] * (BEND_STEEP / bend[1].std())
    length = numpy.sqrt(x * x + y * y + 1.0)
    twig = solid & (rgb[..., 0] > rgb[..., 1] * 1.05)
    under = solid & (light < 0.55 * typical)
    through = ndimage.gaussian_filter(numpy.where(twig, THROUGH_TWIG, numpy.where(under, THROUGH_TWO, 1.0)).astype(numpy.float32), 1.5)
    print(f'  {count} leaves, {int((sizes[1:] >= LEAF_LEAST).sum())} of them tipped, steepest slope {numpy.hypot(x, y)[solid].max():.2f}')
    return numpy.stack([0.5 + 0.5 * x / length, 0.5 + 0.5 * y / length, through], axis=2)


def main():
    if sys.argv[1:] == ['form']:
        sheet = numpy.asarray(Image.open(OUT / 'sprigs.png').convert('RGBA')).astype(numpy.float32) / 255.0
        formed = numpy.zeros((sheet.shape[0], sheet.shape[1], 3), dtype=numpy.float32)
        for index, name in enumerate(SPRIGS):
            where = (slice(index // 4 * TILE, (index // 4 + 1) * TILE), slice(index % 4 * TILE, (index % 4 + 1) * TILE))
            print(name)
            formed[where] = form(sheet[where], index)
        # Half the sprigs' size: a leaf's turn and curl are broad things.
        high, wide = formed.shape[0] // 2, formed.shape[1] // 2
        formed = formed.reshape(high, 2, wide, 2, 3).mean(axis=(1, 3))
        Image.fromarray((formed * 255.0 + 0.5).astype(numpy.uint8), 'RGB').save(OUT / 'sprigs-form.png', optimize=True)
        print('wrote', OUT / 'sprigs-form.png', (OUT / 'sprigs-form.png').stat().st_size, 'bytes')
        return
    kind, folder, picks = sys.argv[1], pathlib.Path(sys.argv[2]), dict(pick.split('=') for pick in sys.argv[3:])
    OUT.mkdir(parents=True, exist_ok=True)
    if kind == 'sprigs':
        if sorted(picks) != sorted(SPRIGS):
            raise SystemExit('one picture for each of ' + ' '.join(SPRIGS))
        rows = (len(SPRIGS) + 3) // 4
        sheet = numpy.zeros((rows * TILE, 4 * TILE, 4), dtype=numpy.float32)
        for index, name in enumerate(SPRIGS):
            sheet[index // 4 * TILE:(index // 4 + 1) * TILE, index % 4 * TILE:(index % 4 + 1) * TILE] = sprig(folder / (picks[name] + '.png'))
        Image.fromarray((sheet * 255.0 + 0.5).astype(numpy.uint8), 'RGBA').save(OUT / 'sprigs.png', optimize=True)
        print('wrote', OUT / 'sprigs.png', (OUT / 'sprigs.png').stat().st_size, 'bytes')
    elif kind == 'surface':
        for name, pick in picks.items():
            picture = Image.open(folder / (pick + '.png')).convert('RGB')
            if name in SPREAD:
                rgb = numpy.asarray(picture, dtype=numpy.float32)
                light = rgb @ numpy.array([0.299, 0.587, 0.114], dtype=numpy.float32)
                mean = light.mean()
                has = (light / mean).std()
                if has > SPREAD[name]:
                    rgb = mean + (rgb - mean) * (SPREAD[name] / has)
                picture = Image.fromarray(numpy.clip(rgb + 0.5, 0, 255).astype(numpy.uint8), 'RGB')
            picture.save(OUT / (name + '.jpg'), quality=94, subsampling=0)
            print('wrote', OUT / (name + '.jpg'), (OUT / (name + '.jpg')).stat().st_size, 'bytes')
    else:
        raise SystemExit('sprigs, surface or form')


if __name__ == '__main__':
    main()
