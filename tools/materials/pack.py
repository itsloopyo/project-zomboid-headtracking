"""Puts generated pictures (tools/materials/generate.py) into the mod's jar resources.

    python tools/materials/pack.py sprigs DIR linden=sprig-broadleaf-7 maple=... (one for each of SPRIGS)
    python tools/materials/pack.py surface DIR bark=bark-1993 grass=grass-7 ...

A sprig is cut out of its white ground, cleaned, and set into one picture with the others, four
to a row, in the order the mod's Flora counts them. A surface is saved as it is.
"""
import pathlib
import sys

import numpy
from PIL import Image

OUT = pathlib.Path(__file__).resolve().parents[2] / 'src' / 'main' / 'resources' / 'materials'
SPRIGS = ['linden', 'maple', 'birch', 'pine', 'hemlock', 'holly', 'shrub', 'fern']
TILE = 1024
# What every sprig's leaves are on average once packed. The world shader divides by it, so a sprig gives a crown its leaves' light and dark and not its own green.
MEAN = numpy.array([0.26, 0.42, 0.12], dtype=numpy.float32)


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


def spread(rgb, alpha):
    """What is cut away takes the colour of the nearest leaf, so no white is mixed in at an edge when the picture is seen small."""
    # A pixel on a leaf's edge is part ground: only what is well inside a leaf has a colour to give.
    weight = numpy.clip((alpha - 0.8) / 0.2, 0.0, 1.0)
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
    rgb, alpha = cut(Image.open(path).resize((TILE, TILE), Image.LANCZOS))
    rgb, alpha = whole(rgb, alpha)
    solid = alpha > 0.5
    if solid.sum() < 0.04 * solid.size:
        raise SystemExit(f'{path} is nearly all ground: {solid.mean():.3f} of it is sprig')
    # Brought to the common mean by its green leaves alone: a twig is not what the crown is coloured by.
    leaves = solid & (rgb[..., 1] > rgb[..., 0])
    rgb = numpy.clip(rgb * (MEAN / rgb[leaves].mean(axis=0)), 0.0, 1.0)
    print(f'{path.name}: {solid.mean():.2f} of the card is sprig')
    return numpy.concatenate([spread(rgb, alpha), alpha[..., None]], axis=2)


def main():
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
            Image.open(folder / (pick + '.png')).convert('RGB').save(OUT / (name + '.jpg'), quality=94, subsampling=0)
            print('wrote', OUT / (name + '.jpg'), (OUT / (name + '.jpg')).stat().st_size, 'bytes')
    else:
        raise SystemExit('sprigs or surface')


if __name__ == '__main__':
    main()
