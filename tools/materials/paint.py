"""Makes the picture of a painted surface that the first person view lays over walls and doors.

Paint put on with a roller dries to a fine even stipple with no direction to it. The picture is
made from filtered noise, so it repeats without a seam and has no ribs or brush strokes for a
wall to be ruled with when it is seen close.

Run: python tools/materials/paint.py
"""
import pathlib

import numpy
from PIL import Image

SIZE = 1024
# The stipple's bumps are a few texels across: at the scale the view lays the picture, a few millimetres.
BUMP_TEXELS = 5.0
CONTRAST = 0.05


def main():
    rng = numpy.random.default_rng(1993)
    noise = numpy.fft.fft2(rng.standard_normal((SIZE, SIZE)))
    fx = numpy.fft.fftfreq(SIZE)[None, :]
    fy = numpy.fft.fftfreq(SIZE)[:, None]
    f = numpy.sqrt(fx * fx + fy * fy)
    peak = 1.0 / BUMP_TEXELS
    band = numpy.exp(-((f - peak) / (peak * 0.45)) ** 2)
    # A little unevenness over a hand's breadth, as a roller leaves.
    band += 0.35 * numpy.exp(-(f / (peak * 0.08)) ** 2)
    stipple = numpy.real(numpy.fft.ifft2(noise * band))
    stipple = (stipple - stipple.mean()) / stipple.std()
    grey = numpy.clip(0.5 + CONTRAST * stipple, 0.0, 1.0)
    rgb = numpy.repeat((grey * 255.0 + 0.5).astype(numpy.uint8)[:, :, None], 3, axis=2)
    out = pathlib.Path(__file__).resolve().parents[2] / 'src' / 'textures' / 'materials' / 'paint.jpg'
    Image.fromarray(rgb, 'RGB').save(out, quality=94, subsampling=0)
    print('wrote', out, out.stat().st_size, 'bytes')


if __name__ == '__main__':
    main()
