"""Makes the strands of fur and hair Rugs lays over a fur rug and a hide from generated pictures
(tools/materials/generate.py, 'fur' and 'hide'), into src/main/resources/rugs.

    python tools/materials/grain.py DIR fur=fur-7 hide=hide-1993

Only the fine grain of a picture is kept: its light over a blur of itself. What is broader than
BROAD of the picture, a tuft or a stripe the generator lays across the whole of it, is taken
out, since the picture is laid once a tile and would repeat across a rug of many squares. The
picture is wrapped round its edges when blurred, as it tiles. Saved grey at SIDE, its mean at
half way, the grain's standard deviation SPREAD of the whole range; Rugs takes each dot over the mean.
"""
import pathlib
import sys

import numpy
from PIL import Image

OUT = pathlib.Path(__file__).resolve().parents[2] / 'src' / 'main' / 'resources' / 'rugs'
SIDE = 1024
BROAD = 1 / 32
SPREAD = 0.12


def grain(path):
    image = Image.open(path).convert('L').resize((SIDE, SIDE), Image.LANCZOS)
    lit = numpy.asarray(image, dtype=numpy.float64) / 255.0
    # Blurred by its frequencies, which wraps it round its edges as it tiles.
    sigma = SIDE * BROAD
    f = numpy.fft.fftfreq(SIDE)
    keep = numpy.exp(-2.0 * numpy.pi ** 2 * sigma ** 2 * (f[:, None] ** 2 + f[None, :] ** 2))
    blurred = numpy.real(numpy.fft.ifft2(numpy.fft.fft2(lit) * keep))
    fine = lit - blurred
    fine /= max(float(fine.std()), 1e-6)
    return Image.fromarray(numpy.clip(0.5 + SPREAD * fine, 0.0, 1.0) * 255.0).convert('L')


def main():
    folder, picks = pathlib.Path(sys.argv[1]), dict(pick.split('=') for pick in sys.argv[2:])
    OUT.mkdir(parents=True, exist_ok=True)
    for name, pick in picks.items():
        grain(folder / (pick + '.png')).save(OUT / (name + '.jpg'), quality=92)
        print('wrote', OUT / (name + '.jpg'), (OUT / (name + '.jpg')).stat().st_size, 'bytes')


if __name__ == '__main__':
    main()
