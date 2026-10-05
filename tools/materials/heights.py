"""How far each point of a material's picture stands out of its surface, read off the picture.

    python heights.py [name ...]        writes src/main/resources/materials/<name>.high.jpg

A photograph of gravel, of weathered boards or of a shingled roof is dark where the surface is
sunk: between the stones, in a crack, under the edge of a shingle. That holds only for dark that
is narrow. A board a shade browner than the next is not lower than it, so what changes over a
breadth of the picture is taken out first and only what is left counts. The picture repeats, so
all of it is done round its edges: the height repeats where the picture does.

White is the top of the surface and black its deepest point. The world shader reads it as the
fourth number of the material's texel (Pictures.load puts it there) and gives the surface depth.
"""

import pathlib
import sys

import numpy as np
from PIL import Image

MATERIALS = pathlib.Path(__file__).resolve().parents[2] / "src" / "main" / "resources" / "materials"

# For each picture: how many texels wide a dark thing may be and still be a hollow (broad), how
# many texels the result is smoothed over so the picture's grain is not read as pits (fine), and
# the share of the picture that is at the top, flat (flat): boards and tarmac are mostly top with
# cracks in them, a bed of stones is all humps and gaps. Sizes are for a picture 2048 across.
RECIPES = {
    "gravel": dict(broad=48.0, fine=2.5, flat=0.05),
    "soil": dict(broad=64.0, fine=3.0, flat=0.05),
    "asphalt": dict(broad=24.0, fine=2.0, flat=0.3),
    "planks": dict(broad=40.0, fine=2.0, flat=0.45),
    "wood": dict(broad=14.0, fine=1.6, flat=0.7),
    "shingles": dict(broad=36.0, fine=2.0, flat=0.5),
}


def blurred(field, sigma):
    """The field smoothed by a Gaussian of that many texels, going round the edges."""
    rows, columns = field.shape
    fy = np.fft.fftfreq(rows)[:, None]
    fx = np.fft.rfftfreq(columns)[None, :]
    kernel = np.exp(-2.0 * (np.pi * sigma) ** 2 * (fx * fx + fy * fy))
    return np.fft.irfft2(np.fft.rfft2(field) * kernel, s=field.shape)


def height(name):
    recipe = RECIPES[name]
    picture = Image.open(MATERIALS / f"{name}.jpg").convert("RGB")
    scale = picture.width / 2048.0
    rgb = np.asarray(picture, dtype=np.float64) / 255.0
    light = rgb @ np.array([0.299, 0.587, 0.114])
    # Light is judged as the eye does, by ratio: a crack in a dark board is as deep as one in a pale board.
    tone = np.log(light + 0.02)
    smooth = blurred(tone, recipe["fine"] * scale)
    relief = smooth - blurred(tone, recipe["broad"] * scale)
    # A second, broader reading at a third of the weight: the hollow between stones is wider than the crack in one.
    relief += 0.35 * (blurred(tone, 3.0 * recipe["fine"] * scale) - blurred(tone, 3.0 * recipe["broad"] * scale))
    low = np.percentile(relief, 1.0)
    top = np.percentile(relief, 100.0 * (1.0 - recipe["flat"]))
    high = np.clip((relief - low) / (top - low), 0.0, 1.0)
    # The floor of a hollow is rounded and its lip is not a cliff.
    high = high * high * (3.0 - 2.0 * high)
    out = MATERIALS / f"{name}.high.jpg"
    Image.fromarray(np.round(high * 255.0).astype(np.uint8), "L").save(out, quality=90)
    print(f"{out.name}: {picture.width} x {picture.height}, mean height {high.mean():.3f}")


if __name__ == "__main__":
    for each in sys.argv[1:] or RECIPES:
        height(each)
