"""Draws the faces of the instruments in the mod's vehicle interiors, for tools/models/cabins.py to lay
onto the dials it bakes.

    python tools/models/cabin_pictures.py DIR

Each face is two square PNGs: NAME.png, white markings on black, and NAME-glow.png, white where the face
glows when the instruments are lit. The figures are set in Inter and DejaVu Sans Mono (SIL Open Font
License 1.1 and the Bitstream Vera licence, see THIRD-PARTY-NOTICES.md), read from Blender 4.4's own copies,
which are found where Blender installs by default or in the folder the BLENDER_FONTS environment variable
names. Nothing here is a maker's design: the faces are laid out as gauges of the early eighties commonly were.
"""

import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

FONTS = os.environ.get("BLENDER_FONTS", r"C:\Program Files\Blender Foundation\Blender 4.4\4.4\datafiles\fonts")
SIZE = 1024
# Drawn at four times the size and brought down; a round face's radius is C.
C = SIZE * 2
FACE = (8, 8, 9)
WHITE = (238, 236, 228)
ORANGE = (236, 120, 40)
RED = (214, 46, 30)


def font(px, mono=False):
    return ImageFont.truetype(os.path.join(FONTS, "DejaVuSansMono.woff2" if mono else "Inter.woff2"), px)


class Face:
    """A face being drawn: its colour and what of it glows, side by side."""

    def __init__(self, wide=C * 2, high=C * 2):
        self.colour = Image.new("RGB", (wide, high), FACE)
        self.glow = Image.new("L", (wide, high), 0)
        self.c = ImageDraw.Draw(self.colour)
        self.g = ImageDraw.Draw(self.glow)

    def line(self, points, colour, width, glows=True):
        self.c.line(points, fill=colour, width=width)
        if glows:
            self.g.line(points, fill=255, width=width)

    def text(self, xy, words, size, colour=WHITE, glows=True, mono=False):
        self.c.text(xy, words, font=font(size, mono), fill=colour, anchor="mm")
        if glows:
            self.g.text(xy, words, font=font(size, mono), fill=255, anchor="mm")

    def box(self, corners, colour, outline_width=0, glows=False):
        if outline_width:
            self.c.rectangle(corners, outline=colour, width=outline_width)
            if glows:
                self.g.rectangle(corners, outline=255, width=outline_width)
        else:
            self.c.rectangle(corners, fill=colour)
            if glows:
                self.g.rectangle(corners, fill=255)

    def polygon(self, points, colour, glows=False):
        self.c.polygon(points, fill=colour)
        if glows:
            self.g.polygon(points, fill=255)

    def ellipse(self, corners, colour, glows=True):
        self.c.ellipse(corners, fill=colour)
        if glows:
            self.g.ellipse(corners, fill=255)

    def save(self, folder, name, size):
        self.colour.resize(size, Image.LANCZOS).save(os.path.join(folder, name + ".png"))
        self.glow.resize(size, Image.LANCZOS).save(os.path.join(folder, name + "-glow.png"))


def at(angle_from_up, radius, centre=(C, C)):
    """A point radius from centre at an angle clockwise from straight up, in degrees."""
    a = math.radians(angle_from_up)
    return centre[0] + radius * math.sin(a), centre[1] - radius * math.cos(a)


def tick(face, angle, inner, outer, width, colour, centre=(C, C)):
    face.line([at(angle, inner, centre), at(angle, outer, centre)], colour, width)


def speedometer():
    """0 to 85 miles an hour over 260 degrees, a ring of kilometres within it, the odometer's window below."""
    face = Face()
    full, sweep = 85.0, 130.0
    for mph in range(0, 86, 5):
        a = -sweep + 2 * sweep * mph / full
        major = mph % 10 == 0
        tick(face, a, 1640 if major else 1730, 1900, 26 if major else 14, WHITE)
        if major:
            face.text(at(a, 1420), str(mph), 230)
    for kmh in range(0, 141, 20):
        a = -sweep + 2 * sweep * (kmh / 1.609) / full
        tick(face, a, 1060, 1160, 12, ORANGE)
        face.text(at(a, 920), str(kmh), 110, ORANGE)
    face.text((C, C - 380), "MPH", 150)
    face.text((C, C - 230), "km/h", 90, ORANGE)
    # The odometer: six drums in a window, the last white on black for the tenths.
    left, top, cell, high = C - 330, C + 470, 110, 170
    face.box([left - 14, top - 14, left + 6 * cell + 14, top + high + 14], (34, 34, 36))
    for k, digit in enumerate("047312"):
        x0 = left + k * cell
        drum, ink = ((220, 218, 210), (14, 14, 15)) if k == 5 else ((14, 14, 15), (220, 218, 210))
        face.box([x0 + 4, top, x0 + cell - 4, top + high], drum)
        face.text((x0 + cell / 2, top + high / 2), digit, 150, ink, glows=k < 5, mono=True)
    return face


def revs():
    """The engine's speed, 0 to 7 thousand turns a minute over 260 degrees, red from 5500."""
    face = Face()
    full, sweep = 7.0, 130.0
    for k in range(0, 71):
        rpm = k / 10.0
        a = -sweep + 2 * sweep * rpm / full
        major = k % 10 == 0
        half = k % 5 == 0
        if not (major or half or k % 2 == 0):
            continue
        red = rpm >= 5.5
        tick(face, a, 1640 if major else 1720 if half else 1790, 1900, 26 if major else 16 if half else 10, RED if red else WHITE)
        if major:
            face.text(at(a, 1420), str(int(rpm)), 230, RED if red else WHITE)
    # The red zone as a band under its ticks.
    for k in range(55, 71):
        a = -sweep + 2 * sweep * (k / 10.0) / full
        face.line([at(a, 1905), at(a + 2.6, 1905)], RED, 40)
    face.text((C, C - 380), "RPM", 150)
    face.text((C, C - 230), "x1000", 90)
    return face


# A fuel and heat dial's needles turn about pivots this share of its radius either side of the middle and sweep 80
# degrees each, pointing outwards (tools/models/cabin/sedan.py, COMBI_PIVOT and FUEL_SWEEP, HEAT_SWEEP).
COMBI_PIVOT = 0.30
COMBI_ENDS = (130, 50)


def pair_scales(face, left_marks, right_marks):
    """The two scales of a fuel and heat dial, each about its own pivot: five ticks at the needle's tip, the ends
    named just beyond them. marks: (bottom word, top word, which ticks are red)."""
    for side, (low, high, red) in ((-1, left_marks), (1, right_marks)):
        pivot = (C + side * C * COMBI_PIVOT, C)
        bottom, top = side * COMBI_ENDS[0], side * COMBI_ENDS[1]
        for k in range(5):
            a = bottom + (top - bottom) * k / 4
            tick(face, a, C * (0.47 if k % 2 == 0 else 0.51), C * 0.58, 30 if k % 2 == 0 else 18, RED if k in red else WHITE, pivot)
        for k, words in ((0, low), (4, high)):
            a = bottom + (top - bottom) * k / 4 + side * (13 if k == 0 else -13)
            face.text(at(a, C * 0.50, pivot), words, 190, RED if k in red else WHITE)


def fuel_and_heat():
    """Fuel on the left, empty below and full above, heat on the right, cold below and hot above, each over its
    own needle's sweep, with a pump and a thermometer under the pivots."""
    face = Face()
    pair_scales(face, ("E", "F", (0,)), ("C", "H", (4,)))
    # A pump: its body with a window, the hose out of its side and down to the nozzle hung on it.
    px, py = C - 330, C + 760
    face.box([px - 70, py - 120, px + 70, py + 130], WHITE, glows=True)
    face.box([px - 42, py - 92, px + 42, py - 20], FACE)
    face.line([(px + 70, py - 60), (px + 120, py - 60), (px + 135, py - 40), (px + 135, py + 70)], WHITE, 20)
    face.box([px - 95, py + 130, px + 95, py + 150], WHITE, glows=True)
    # A thermometer standing in waves.
    tx, ty = C + 330, C + 730
    face.line([(tx, ty - 140), (tx, ty + 60)], WHITE, 30)
    face.ellipse([tx - 52, ty + 30, tx + 52, ty + 134], WHITE)
    for k in range(3):
        face.line([(tx + 30, ty - 110 + k * 50), (tx + 75, ty - 110 + k * 50)], WHITE, 14)
    face.line([(tx - 150 + i * 10, ty + 170 + 18 * math.sin(i * math.pi / 5)) for i in range(31)], WHITE, 14)
    return face


def oil_and_volts():
    """Oil pressure on the left, nothing below and full above, the battery's volts on the right, low below and high
    above, laid out as the fuel and heat dial is."""
    face = Face()
    pair_scales(face, ("0", "80", (0,)), ("10", "16", (0, 4)))
    face.text((C - 330, C + 760), "OIL", 130)
    face.text((C + 330, C + 760), "VOLTS", 120)
    return face


def band(width, height, below, radius, sweep, marks, label, colour_of=lambda k: WHITE, label_size=150):
    """A wide face whose needle turns about a pivot below it: width and height in metres, the pivot below its lower
    edge, the scale on an arc of radius about it over sweep degrees either side of up. marks: (share, text, major)."""
    wide = C * 2
    scale = wide / width
    high = int(height * scale)
    face = Face(wide, high)
    pivot = (wide / 2, high + below * scale)
    r = radius * scale
    for k, (share, text, major) in enumerate(marks):
        a = -sweep + 2 * sweep * share
        tick(face, a, r - (0.012 if major else 0.007) * scale, r, 26 if major else 14, colour_of(share), pivot)
        if text:
            face.text(at(a, r - 0.022 * scale, pivot), text, int(0.011 * scale), colour_of(share))
    face.text((wide / 2, high - 0.012 * scale), label, label_size)
    return face, (wide, high)


def speedometer_band():
    """The saloon's speedometer: 0 to 85 miles an hour along an arc across a wide face, its needle pivoted below it."""
    marks = [(mph / 85.0, str(mph) if mph % 10 == 0 else "", mph % 10 == 0) for mph in range(0, 86, 5)]
    return band(0.24, 0.085, 0.05, 0.11, 50.0, marks, "MPH")


def fuel_band():
    marks = [(k / 4.0, "E" if k == 0 else "F" if k == 4 else "", k % 2 == 0) for k in range(5)]
    return band(0.085, 0.055, 0.02, 0.06, 40.0, marks, "FUEL", lambda share: RED if share == 0 else WHITE, 110)


def heat_band():
    marks = [(k / 4.0, "C" if k == 0 else "H" if k == 4 else "", k % 2 == 0) for k in range(5)]
    return band(0.085, 0.055, 0.02, 0.06, 40.0, marks, "TEMP", lambda share: RED if share == 1 else WHITE, 110)


def lamps():
    """Warning lamps behind a dark face: each a word in its colour, printed dark and lit only when it warns, which a
    car at rest never does, and the turn arrows."""
    face = Face()
    for words, y in (("BRAKE", -560), ("OIL", -200), ("TEMP", 160), ("GEN", 520)):
        face.text((C, C + y), words, 230, tuple(int(c * 0.30) for c in RED), glows=False)
    for side in (-1, 1):
        cx = C + side * 620
        pts = [(cx + side * 150, C), (cx - side * 30, C - 150), (cx - side * 30, C - 60), (cx - side * 150, C - 60), (cx - side * 150, C + 60),
               (cx - side * 30, C + 60), (cx - side * 30, C + 150)]
        face.polygon(pts, (18, 52, 22))
    return face


def lamp_strip():
    """The same lamps stacked in a narrow upright strip, as a truck's cluster has them beside its dials."""
    face = Face(C * 6 // 5, C * 2)
    # Each lamp behind a smoked lens of its own, divided from the next, its word legible while it is unlit.
    for words, y in (("BRAKE", -720), ("OIL", -240), ("TEMP", 240), ("GEN", 720)):
        face.box(((60, C + y - 210), (C * 6 // 5 - 60, C + y + 210)), (70, 66, 66))
        face.text((C * 3 // 5, C + y), words, 210, (186, 184, 178), glows=False)
    return face


def gears():
    """The column lever's letters in a strip: park, reverse, neutral, drive, second, first."""
    face = Face(C * 2, C // 4)
    for k, letter in enumerate("PRND21"):
        face.text((C * 2 * (k + 0.5) / 6, C // 8), letter, 340, RED if letter == "P" else WHITE)
    return face


def main():
    folder = sys.argv[1]
    os.makedirs(folder, exist_ok=True)
    for name, make, size in (("speedometer", speedometer, (SIZE, SIZE)), ("revs", revs, (SIZE, SIZE)), ("fuel-heat", fuel_and_heat, (SIZE, SIZE)), ("oil-volts", oil_and_volts, (SIZE, SIZE)), ("lamps", lamps, (SIZE, SIZE)), ("lamps-strip", lamp_strip, (SIZE * 3 // 5, SIZE)),
                             ("gears", gears, (SIZE, SIZE // 8))):
        make().save(folder, name, size)
        print("drew", name)
    # The wide faces keep their own shape: the picture's height to width is the face's.
    for name, make in (("speedometer-band", speedometer_band), ("fuel-band", fuel_band), ("heat-band", heat_band)):
        face, (wide, high) = make()
        face.save(folder, name, (SIZE, max(1, int(SIZE * high / wide))))
        print("drew", name)


main()
