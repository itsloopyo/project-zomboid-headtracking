"""Makes the start of the picture of a painted surface that the first person view lays over walls and doors.

Paint put on with a roller dries to a fine even stipple with no direction to it. This makes that
stipple as filtered noise, which repeats without a seam and has no ribs or brush strokes for a
wall to be ruled with when it is seen close. Noise of one size of bump read as soft blobs with a
face to the wall, so the picture the mod packs is this one worked over by the model
(tools/materials/generate.py --only paint --over), which keeps its layout and gives it the
grain of real paint at every size down to a texel; tools/materials/pack.py then draws its light
in to the spread the world shader was set for.

Run: python tools/materials/paint.py OUT.png
"""
import pathlib
import sys

import numpy
from PIL import Image

SIZE = 2048
# The stipple's bumps are a few texels across: at the scale the view lays the picture, two or three millimetres.
BUMP_TEXELS = 4.0
# Paler and with more in it than the packed picture: the model is given a white wall it can see the stipple on.
LIGHT, CONTRAST = 0.78, 0.09


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
    grey = numpy.clip(LIGHT + CONTRAST * stipple, 0.0, 1.0)
    rgb = numpy.repeat((grey * 255.0 + 0.5).astype(numpy.uint8)[:, :, None], 3, axis=2)
    out = pathlib.Path(sys.argv[1])
    Image.fromarray(rgb, 'RGB').save(out)
    print('wrote', out, out.stat().st_size, 'bytes')


if __name__ == '__main__':
    main()
