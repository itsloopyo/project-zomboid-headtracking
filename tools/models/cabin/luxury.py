"""The interior of the Mercia Lang 4000: a two-seat grand touring coupe of the early eighties. A leather-padded
dashboard over a fascia of walnut veneer, three large round instruments under a shallow hood, a four-spoke wheel
rimmed in leather, a console from the dash to between the seats with the automatic's selector in a wooden surround,
two leather buckets, and a carpeted luggage shelf under the fastback.
"""

import math

from mathutils import Matrix

from . import compact, kit, materials, sedan, shell, upholstery
from .kit import NEEDLE_FUEL, NEEDLE_HEAT, NEEDLE_REVS, NEEDLE_SPEED, SHELL, WHEEL, Builder, circle, lerp, rounded_rect, spline, v
from .sedan import FLOOR_Z, RIM, SEAT_Z, wheel_frame

SPEEDO = (v(0.560, 0.012, -0.280), 0.050)
REVS = (v(0.560, -0.118, -0.280), 0.050)
COMBI = (v(0.560, 0.130, -0.284), 0.036)
FACE_X = 0.515


def needles():
    return [(NEEDLE_SPEED, SPEEDO[0], SPEEDO[1], 0.92, sedan.SPEED_SWEEP), (NEEDLE_REVS, REVS[0], REVS[1], 0.92, (math.radians(-130), math.radians(130), 1.0)),
            (NEEDLE_FUEL, COMBI[0] + v(0, 0.012, 0), COMBI[1], 0.62, sedan.FUEL_SWEEP), (NEEDLE_HEAT, COMBI[0] + v(0, -0.012, 0), COMBI[1], 0.62, sedan.HEAT_SWEEP)]


def dash(house):
    """A deep leather-padded top, rolled over a hood before the driver, above a fascia that is wood across its whole
    width."""
    s = house.side
    inner = s["sill_half"] - 0.030
    rings = []
    for k in range(65):
        out = lerp(inner, -inner, k / 64)
        y = house.y(out)
        xf, zf = house.edge("foot", abs(out))
        xf -= 0.01
        top = max(zf + 0.005, -0.200)
        normal = [(xf + 0.05, zf - 0.07), (xf + 0.004, zf + 0.001), (lerp(xf, FACE_X, 0.5), top + 0.006), (FACE_X + 0.02, top + 0.004), (FACE_X, top - 0.004),
                  (FACE_X - 0.010, top - 0.020), (FACE_X - 0.010, top - 0.036), (FACE_X + 0.002, top - 0.044), (FACE_X + 0.006, -0.30),
                  (FACE_X + 0.010, -0.40), (FACE_X + 0.03, -0.44), (0.66, -0.46), (0.80, -0.36)]
        hood = [(xf + 0.05, zf - 0.07), (xf + 0.004, zf + 0.001), (0.60, -0.180), (0.505, -0.172), (0.490, -0.178), (0.484, -0.190), (0.492, -0.202),
                (0.55, -0.208), (0.572, -0.212), (0.572, -0.31), (FACE_X + 0.010, -0.40), (FACE_X + 0.03, -0.44), (0.66, -0.46), (0.80, -0.36)]
        w = sedan.smooth((0.29 - abs(y)) / 0.05) if abs(y) < 0.29 else 0.0
        rings.append([v(x, y, z) for (x, z) in sedan.blend_sections(normal, hood, w)])
    b = Builder()
    b.loft(rings, closed=True, caps=True)
    return b.finish("dashboard", "leather", SHELL, subdivide=2, coarsest=1, texels=1.5, sharp=50)


def fascia(house):
    parts = []
    s = house.side
    mid = house.mid
    # A band of walnut across the face under the vents, broken by the console's head, the instruments' hood and the
    # glove box, the lights' switch and dimmer set in the driver's.
    gy = house.y(-0.45)
    for y0, y1 in ((house.y(s["sill_half"] - 0.05), 0.30), (-0.30, gy + 0.16)):
        b = Builder()
        b.slab(rounded_rect(abs(y1 - y0), 0.052, 0.015, 6), v(FACE_X - 0.005, (y0 + y1) / 2, -0.326), (0, -1, 0), (0, 0, 1), -0.006)
        parts.append(b.finish("veneer", "wood", SHELL, bevel=0.002, texels=1.2))
        parts.append(sedan.surround("veneer surround", "leather", v(FACE_X - 0.005, (y0 + y1) / 2, -0.326), (0, -1, 0), (0, 0, 1), abs(y1 - y0), 0.052, 0.006,
                                    lip=0.006, radius=0.015))
    b = Builder()
    b.lathe([(0.0, 0.0), (0.014, 0.0), (0.014, 0.010), (0.011, 0.016), (0.0, 0.017)], v(FACE_X - 0.011, 0.38, -0.326), v(-1, 0, 0), count=24)
    parts.append(b.finish("light switch", "chrome", SHELL, subdivide=1, texels=2.0))
    for dy in (0.0, 0.03):
        b = Builder()
        b.slab(rounded_rect(0.008, 0.026, 0.003), v(FACE_X - 0.012, 0.46 + dy, -0.326), (0, -1, 0), (0, 0, 1), 0.006)
        parts.append(b.finish("dimmer wheel", "black", SHELL, bevel=0.002, texels=2.0))
    for name, (c, r), picture in (("speedometer", SPEEDO, "speedometer"), ("revs", REVS, "revs"), ("fuel and heat", COMBI, "fuel-heat")):
        parts += sedan.instrument(name, c, r, picture)
    for group, pivot, radius, length, _ in needles():
        parts += sedan.needle(group, pivot, radius, length)
    b = Builder()
    b.slab(rounded_rect(0.40, 0.125, 0.02), v(0.574, 0.005, -0.282), (0, -1, 0), (0, 0, 1), 0.004)
    parts.append(b.finish("instrument panel", "black", SHELL, texels=1.5, smooth_shade=False))
    # The instruments' housing: a frame round the panel under the hood's brow, centred on the column.
    parts.append(sedan.surround("instrument surround", "leather", v(0.548, 0.005, -0.282), (0, -1, 0), (0, 0, 1), 0.40, 0.125, 0.024, lip=0.012, radius=0.02))
    # Round vents, chromed, at the ends and either side of the middle.
    for out in (s["sill_half"] - 0.14, -(s["sill_half"] - 0.14)):
        parts += compact.eyeball(v(FACE_X - 0.002, house.y(out), -0.255), (-1, 0, 0), 0.03)
    for dy in (-0.07, 0.07):
        parts += compact.eyeball(v(FACE_X - 0.002, mid + dy, -0.255), (-1, 0, 0), 0.026)
    # The demister's grille along the windscreen's foot, on the pad's top.
    parts += sedan.demisters(house, FACE_X)
    # The glove box's lid in walnut, with a chrome lock.
    # Its lid laid flush onto the face's curve inside a dark shut line (cabins.lay_on_face), the lock a flush bezel.
    b = Builder()
    b.panel(rounded_rect(0.28, 0.10, 0.012, 6), v(0.40, gy, -0.29), (0, -1, 0), (0, 0, 1))
    lid = b.finish("glove box", "wood", SHELL, solidify=0.001, texels=1.0)
    lid["lay"] = 0.0016
    parts.append(lid)
    b = Builder()
    b.panel(rounded_rect(0.288, 0.108, 0.016, 6), v(0.40, gy, -0.29), (0, -1, 0), (0, 0, 1))
    line = b.finish("glove box shut line", "black", SHELL, solidify=0.004, texels=1.0, smooth_shade=False)
    line["lay"] = 0.0005
    parts.append(line)
    b = Builder()
    b.lathe([(0.0, 0.0), (0.0125, 0.0), (0.0125, 0.002), (0.010, 0.003), (0.0, 0.003)], v(0.40, gy, -0.258), v(-1, 0, 0), count=20)
    lock = b.finish("glove box lock", "chrome", SHELL, texels=2.0)
    lock["lay"] = 0.0046
    parts.append(lock)
    return parts


def console(house, skin="leather", trim="wood", face_x=0.511, turn=0.0):
    """The console from the dash to between the seats: the radio and the heater in its head, the selector and its
    gate in walnut, an armrest at its end."""
    parts = []
    mid = house.mid
    b = Builder()
    rings = []
    # Low past the selector, then up into a box between the seats whose lid is the armrest.
    profile = ((0.60, 0.30, 0.24), (0.50, 0.29, 0.24), (0.42, 0.235, 0.23), (0.30, 0.22, 0.22), (0.10, 0.21, 0.21), (0.02, 0.235, 0.20), (-0.06, 0.30, 0.20),
               (-0.30, 0.31, 0.20), (-0.36, 0.29, 0.19))
    for x, h, w in profile:
        rings.append([v(x, mid + u, FLOOR_Z + 0.04 + h * 0.5 + wv) for (u, wv) in rounded_rect(w, h, 0.02, 6)])

    def top_at(x):
        for (x0, h0, _), (x1, h1, _) in zip(profile, profile[1:]):
            if x1 <= x <= x0:
                return FLOOR_Z + 0.04 + lerp(h1, h0, (x - x1) / (x0 - x1))
        raise ValueError(x)
    b.loft(rings, closed=True, caps=True)
    parts.append(b.finish("console", skin, SHELL, subdivide=1, texels=0.9))
    stack_from = len(kit.PARTS)
    if turn:
        b = Builder()
        b.slab(rounded_rect(0.196, 0.196, 0.018), v(face_x + 0.05, mid, -0.385), (0, -1, 0), (0, 0, 1), 0.05)
        parts.append(b.finish("console pod", skin, SHELL, bevel=0.006, bevel_segments=3, texels=1.0))
    # The stack's face from the dash down into the console: the radio, the climate panel with its knobs, and the
    # ash tray's lid with the lighter beside it, each let into the face.
    b = Builder()
    b.slab(rounded_rect(0.18, 0.18, 0.012), v(face_x, mid, -0.385), (0, -1, 0), (0, 0, 1), 0.006)
    parts.append(b.finish("console face", trim, SHELL, bevel=0.002, texels=1.4))
    parts.append(sedan.surround("console face surround", skin, v(face_x - 0.0060, mid, -0.385), (0, -1, 0), (0, 0, 1), 0.18, 0.18, 0.006, lip=0.012))
    b = Builder()
    b.slab(rounded_rect(0.15, 0.042, 0.004), v(face_x - 0.0050, mid, -0.332), (0, -1, 0), (0, 0, 1), 0.006)
    parts.append(b.finish("radio face", "black", SHELL, bevel=0.0015, texels=2.5))
    parts.append(sedan.surround("radio surround", "black", v(face_x - 0.0110, mid, -0.332), (0, -1, 0), (0, 0, 1), 0.15, 0.042, 0.006, lip=0.004))
    b = Builder()
    b.slab(rounded_rect(0.07, 0.013, 0.002), v(face_x - 0.0115, mid, -0.3225), (0, -1, 0), (0, 0, 1), 0.0015)
    parts.append(b.finish("radio window", "lens", SHELL, texels=3.0, smooth_shade=False))
    b = Builder()
    for k in range(5):
        b.slab(rounded_rect(0.0125, 0.008, 0.002), v(face_x - 0.011, mid + lerp(0.034, -0.034, k / 4), -0.3405), (0, -1, 0), (0, 0, 1), 0.004)
    parts.append(b.finish("radio buttons", "chrome" if trim == "wood" else "plastic", SHELL, bevel=0.001, texels=2.5))
    for dy in (-0.058, 0.058):
        b = Builder()
        b.lathe([(0.0, 0.0), (0.0075, 0.0), (0.0075, 0.005), (0.0065, 0.008), (0.0, 0.0085)], v(face_x - 0.011, mid + dy, -0.332), v(-1, 0, 0), count=24)
        parts.append(b.finish("radio knob", "chrome" if trim == "wood" else "black", SHELL, texels=2.0))
    b = Builder()
    b.slab(rounded_rect(0.15, 0.05, 0.004), v(face_x - 0.0050, mid, -0.386), (0, -1, 0), (0, 0, 1), 0.006)
    parts.append(b.finish("climate panel", "black", SHELL, bevel=0.0015, texels=2.0))
    parts.append(sedan.surround("climate surround", "black", v(face_x - 0.0110, mid, -0.386), (0, -1, 0), (0, 0, 1), 0.15, 0.05, 0.006, lip=0.004))
    if trim == "wood":
        # The automatic climate control's head: the temperature's window over a row of push buttons.
        b = Builder()
        b.slab(rounded_rect(0.055, 0.026, 0.003), v(face_x - 0.0115, mid + 0.035, -0.386), (0, -1, 0), (0, 0, 1), 0.0015)
        parts.append(b.finish("climate display", "lens", SHELL, texels=3.0, smooth_shade=False))
        b = Builder()
        for k in range(4):
            for r in range(2):
                b.slab(rounded_rect(0.012, 0.012, 0.002), v(face_x - 0.0115, mid - 0.012 - k * 0.016, -0.379 - r * 0.016), (0, -1, 0), (0, 0, 1), 0.004)
        parts.append(b.finish("climate button", "chrome", SHELL, bevel=0.001, texels=2.0))
    else:
        for k in range(3):
            b = Builder()
            b.lathe([(0.0, 0.0), (0.012, 0.0), (0.012, 0.012), (0.010, 0.016), (0.0, 0.017)], v(face_x - 0.0110, mid + lerp(0.05, -0.05, k / 2), -0.386), v(-1, 0, 0),
                    count=24)
            parts.append(b.finish("heater knob", "black", SHELL, texels=2.0))
    b = Builder()
    b.slab(rounded_rect(0.10, 0.03, 0.004), v(face_x - 0.0050, mid + 0.02, -0.44), (0, -1, 0), (0, 0, 1), 0.004)
    parts.append(b.finish("ash tray", "plastic", SHELL, bevel=0.0012, texels=1.5))
    b = Builder()
    b.slab(rounded_rect(0.06, 0.006, 0.002), v(face_x - 0.009, mid + 0.02, -0.427), (0, -1, 0), (0, 0, 1), 0.005)
    parts.append(b.finish("ash tray pull", "chrome", SHELL, bevel=0.001, texels=1.5))
    b = Builder()
    b.lathe([(0.0, 0.0), (0.011, 0.0), (0.011, 0.006), (0.0, 0.008)], v(face_x - 0.0080, mid - 0.06, -0.44), v(-1, 0, 0), count=20)
    parts.append(b.finish("lighter", "chrome", SHELL, texels=2.0))
    # Turned towards the driver about its far edge, which stays against the face, so the driver's side comes out of
    # the dash rather than the far side going into it.
    if turn:
        pivot = v(face_x, mid - 0.09, 0.0)
        m = Matrix.Translation(pivot) @ Matrix.Rotation(math.radians(turn), 4, "Z") @ Matrix.Translation(-pivot)
        for p in kit.PARTS[stack_from:]:
            p.data.transform(m)
    # The automatic's selector: a gate plate in the console's top with its slot and the strip of P R N D 2 1 beside
    # it, and a T-handled lever with a button for the thumb.
    gate = v(0.22, mid, top_at(0.22) + 0.004)
    b = Builder()
    b.slab(rounded_rect(0.20, 0.09, 0.02), gate, (1, 0, 0), (0, 1, 0), 0.004)
    parts.append(b.finish("selector gate", trim, SHELL, bevel=0.002, texels=1.4))
    parts.append(sedan.surround("selector gate surround", skin, gate + v(0, 0, 0.004), (1, 0, 0), (0, 1, 0), 0.20, 0.09, 0.004, lip=0.006))
    b = Builder()
    b.slab(rounded_rect(0.15, 0.012, 0.005), gate + v(0, 0, 0.0045), (1, 0, 0), (0, 1, 0), 0.001)
    parts.append(b.finish("selector slot", "black", SHELL, texels=2.0, smooth_shade=False))
    b = Builder()
    b.slab(rounded_rect(0.13, 0.018, 0.004), gate + v(0, -0.028, 0.0045), (1, 0, 0), (0, 1, 0), 0.003)
    parts.append(b.finish("selector letters", "lens", SHELL, texels=3.0, smooth_shade=False))
    # The lever in drive, a little behind the slot's middle.
    foot = gate + v(0.05, 0, 0.004)
    head = foot + v(-0.025, 0, 0.12 if trim == "wood" else 0.15)
    b = Builder()
    b.sweep(spline([foot, foot + v(-0.01, 0, 0.06), head], 8), lambda i: rounded_rect(lerp(0.028, 0.02, i / 7), lerp(0.02, 0.016, i / 7), 0.006), ups=[v(1, 0, 0)] * 8)
    parts.append(b.finish("selector", "black", SHELL, subdivide=1, texels=1.6))
    if trim != "wood":
        # A family car's lever: an upright grip with the thumb's button in its driver's side.
        b = Builder()
        b.bar(head + v(0.002, 0, -0.035), head + v(-0.004, 0, 0.035), rounded_rect(0.034, 0.042, 0.014), lambda p, u, w: p + v(w, u, 0), ends=0.012)
        parts.append(b.finish("selector handle", "black", SHELL, subdivide=2, texels=2.0))
        b = Builder()
        b.slab(rounded_rect(0.012, 0.022, 0.005), head + v(-0.001, 0.0175, 0.012), (1, 0, 0), (0, 0, 1), 0.005)
        parts.append(b.finish("selector button", "plastic", SHELL, bevel=0.0015, texels=2.0))
    else:
        b = Builder()
        b.bar(head + v(0, 0.045, 0.012), head + v(0, -0.045, 0.012), rounded_rect(0.034, 0.03, 0.012), lambda p, u, w: p + v(u, 0, w), ends=0.014)
        parts.append(b.finish("selector handle", skin, SHELL, subdivide=2, texels=2.0))
        b = Builder()
        b.lathe([(0.0, 0.0), (0.008, 0.0), (0.008, 0.006), (0.0, 0.007)], head + v(0, 0.040, 0.014), v(0, 1, 0), count=16)
        parts.append(b.finish("selector button", "chrome", SHELL, texels=2.0))
    # A lidded bin in the console's top between the stack and the gate.
    # Lying on the top's slope there.
    slope = v(0.02, 0, top_at(0.395) - top_at(0.375)).normalized()
    bin_at = v(0.385, mid, top_at(0.385) - 0.0005)
    b = Builder()
    b.slab(rounded_rect(0.09, 0.075, 0.012), bin_at, slope, (0, 1, 0), 0.0015)
    parts.append(b.finish("console bin shut line", "black", SHELL, texels=1.6, smooth_shade=False))
    b = Builder()
    b.slab(rounded_rect(0.084, 0.069, 0.010), bin_at + v(0, 0, 0.0004), slope, (0, 1, 0), 0.0018)
    parts.append(b.finish("console bin lid", skin if trim != "wood" else trim, SHELL, bevel=0.001, texels=1.4))
    # A tray for coins between the selector and the box, and the armrest over the box.
    b = Builder()
    b.slab(rounded_rect(0.07, 0.10, 0.015), v(0.075, mid, top_at(0.075) - 0.004), (1, 0, 0), (0, 1, 0), 0.003)
    parts.append(b.finish("coin tray", "black", SHELL, bevel=0.001, texels=1.4))
    parts.append(sedan.surround("coin tray surround", skin, v(0.075, mid, top_at(0.075) + 0.003), (1, 0, 0), (0, 1, 0), 0.07, 0.10, 0.003, lip=0.005))
    parts.append(upholstery.block("console armrest", "vinyl" if skin == "plastic" else skin, v(-0.19, mid, FLOOR_Z + 0.04 + 0.335), (0, 1, 0), (1, 0, 0), (0, 0, 1), 0.20,
                                  0.30, 0.055, texels=0.9))
    return parts


def steering_wheel(rim="leather", spokes="leather", boss_skin="leather", pad=(0.24, 0.17), drop=0.01, angles=(80, -80, 135, -135), round_boss=False):
    """A thick rim on four broad padded spokes, the upper pair near level to keep clear of the instruments, to a
    large padded horn pad in the middle; or, round_boss, a race wheel's round boss with a small horn button in it."""
    m = wheel_frame()
    parts = []
    b = Builder()
    n = 64
    rings = []
    for k in range(n):
        a = 2 * math.pi * k / n
        p = v(0.0, RIM * math.sin(a), RIM * math.cos(a))
        radial = v(0, p.y, p.z).normalized()
        rings.append([p + radial * (0.0135 * math.sin(2 * math.pi * j / 16)) + v(1, 0, 0) * (0.0155 * math.cos(2 * math.pi * j / 16)) for j in range(16)])
    made = [[b.vert(q) for q in ring] for ring in rings]
    for i in range(n):
        a, c = made[i], made[(i + 1) % n]
        for j in range(16):
            k = (j + 1) % 16
            b.face([a[j], a[k], c[k], c[j]])
    rim_part = b.finish("rim", rim, WHEEL, subdivide=1, texels=3.0, sharp=60)
    rim_part.data.transform(m)
    parts.append(rim_part)
    for angle in angles:
        a = math.radians(angle)
        end = v(0.003, (RIM - 0.01) * math.sin(a), (RIM - 0.01) * math.cos(a))
        start = v(0.04, pad[0] * 0.4 * math.sin(a), pad[1] * 0.4 * math.cos(a) - drop)
        parts.append(sedan.blade_spoke(start, end, 0.05, 0.03, 0.012, spokes))
    parts.append(sedan.horn_pad(pad[0], pad[1], boss_skin, drop, squareness=2.0 if round_boss else 3.2))
    if round_boss:
        b = Builder()
        b.lathe([(0.0, 0.0), (0.0175, 0.0), (0.0175, 0.004), (0.015, 0.007), (0.0, 0.008)], v(0.003, 0, -drop), v(-1, 0, 0), count=32)
        button = b.finish("horn button", "chrome", WHEEL, subdivide=1, texels=2.0)
        button.data.transform(m)
        parts.append(button)
    return parts


def seats(house):
    parts = []
    lean = math.radians(18)
    parts += compact.bucket(0.27, -0.16, 0.0, 0.53, SEAT_Z + 0.01, -0.16, lean, "driver seat", channels=7)
    parts += compact.bucket(0.27, -0.16, 2 * house.mid, 0.53, SEAT_Z + 0.01, -0.16, lean, "passenger seat", channels=7)
    for p in parts:
        if p["material"] in ("cloth", "vinyl"):
            p["material"] = "leather"
    return parts


def doors(house):
    s = house.side
    parts = []
    for driver in (True, False):
        card = sedan.door_card(house, 0.50, s["b"][0] + 0.02, driver, power=True, pleats=6)
        for p in card:
            if p["material"] == "cloth":
                p["material"] = "leather"
        parts += card
        # A walnut strip along the door, level with the fascia's.
        b = Builder()
        y = house.y(s["sill_half"] - 0.058, driver)
        b.slab(rounded_rect(0.41 - s["b"][0], 0.025, 0.0125, 6), v((s["b"][0] + 0.51) / 2, y, s["sill_z"] - 0.0625), (1, 0, 0), (0, 0, 1), 0.006)
        parts.append(b.finish("door veneer", "wood", SHELL, bevel=0.002, texels=1.0))
    return parts


def luggage(house):
    """The carpeted shelf behind the seats under the fastback: a padded bulkhead behind the seats, the wheels' tubs in
    its back corners, the quarters' trim with a roll along their tops, and the deck up to the back window."""
    s, b_ = house.side, house.back
    parts = []
    inner = s["sill_half"] - 0.06
    shelf_z = SEAT_Z - 0.02
    b = Builder()
    b.slab([(-0.36, house.y(inner)), (b_["foot"][0] + 0.02, house.y(inner)), (b_["foot"][0] + 0.02, house.y(-inner)), (-0.36, house.y(-inner))],
           v(0, 0, shelf_z - 0.02), (1, 0, 0), (0, 1, 0), 0.02)
    parts.append(b.finish("luggage shelf", "carpet", SHELL, bevel=0.004, texels=0.5))
    # The bulkhead: from the floor behind the seats up past the shelf, raked back, padded and rolled over at its top.
    rake = math.radians(12)
    up = v(-math.sin(rake), 0.0, math.cos(rake))
    facing = v(math.cos(rake), 0.0, math.sin(rake))
    parts.append(upholstery.cushion("bulkhead", "carpet", v(-0.40, house.mid, FLOOR_Z + 0.02), (0, 1, 0), up, -facing, 2 * inner - 0.02,
                                    shelf_z + 0.20 - FLOOR_Z, 0.03, channels=1, margin=0.05, roll=0.015, crown=0.006, seam_depth=0.0,
                                    texels=0.5, subdivide=1))
    quarter = []
    for driver in (True, False):
        # Up to the sill: above it the pillar behind the side glass is the shell's.
        b = Builder()
        grid = []
        for r in range(7):
            z = lerp(FLOOR_Z, s["sill_z"] + 0.01, r / 6)
            out = house.glass_out(max(z, s["sill_z"])) - 0.03 - 0.02 * (1 - r / 6)
            x_front = s["b"][1] - 0.01
            grid.append([v(lerp(b_["foot"][0] + 0.03, x_front, c / 10), house.y(out, driver), z) for c in range(11)])
        # Rolled over at its top towards the glass.
        top_out = house.glass_out(s["sill_z"]) - 0.03
        for dz, dout in ((0.016, 0.006), (0.020, 0.013)):
            grid.append([v(lerp(b_["foot"][0] + 0.03, x_front, c / 10), house.y(top_out + dout, driver), s["sill_z"] + dz) for c in range(11)])
        b.loft(grid, closed=False, caps=False)
        parts.append(b.finish("rear quarter", "trim", SHELL, solidify=0.012, subdivide=1, texels=0.5))
        quarter.append(house.glass_out(s["sill_z"]) - 0.05)
    # A pair of chrome luggage rails running fore and aft along the deck's floor on feet.
    for rail_y in (house.mid + 0.225, house.mid - 0.225):
        b = Builder()
        x0, x1 = -0.46, -0.96
        b.sweep(spline([v(x0, rail_y, shelf_z), v(x0 - 0.03, rail_y, shelf_z + 0.035), v(x1 + 0.03, rail_y, shelf_z + 0.035), v(x1, rail_y, shelf_z)], 16), circle(0.006, 10),
                ups=[v(0, 0, 1)] * 16)
        parts.append(b.finish("luggage rail", "chrome", SHELL, texels=1.0))
        for end in (x0, x1):
            b = Builder()
            b.slab(rounded_rect(0.03, 0.03, 0.008), v(end, rail_y, shelf_z + 0.001), (1, 0, 0), (0, 1, 0), 0.004)
            parts.append(b.finish("luggage rail foot", "chrome", SHELL, bevel=0.0015, texels=1.0))
    # A moulded panel on each quarter wall with a speaker in it.
    for driver in (True, False):
        side = 1 if driver else -1
        qy = house.y(house.glass_out(s["sill_z"]) - 0.048, driver)
        # High on the panel, the speaker well clear of the wheel's housing below it.
        qx, qz = (s["b"][1] + b_["foot"][0]) / 2 + 0.05, s["sill_z"] - 0.095
        b = Builder()
        b.slab(rounded_rect(0.40, 0.14, 0.03), v(qx, qy, qz), (1, 0, 0), (0, 0, 1), side * 0.015)
        parts.append(b.finish("quarter panel", "trim", SHELL, bevel=0.005, bevel_segments=3, texels=0.6))
        b = Builder()
        b.slab(circle(0.05, 32), v(qx + 0.08, qy - side * 0.015, qz), (1, 0, 0), (0, 0, 1), side * 0.003)
        parts.append(b.finish("quarter speaker", "grille", SHELL, texels=1.4))
        b = Builder()
        b.lathe([(0.048, 0.0), (0.058, 0.0), (0.061, 0.004), (0.057, 0.008), (0.050, 0.007)], v(qx + 0.08, qy - side * 0.015, qz), v(0, -side, 0), count=32, caps=False)
        parts.append(b.finish("quarter speaker ring", "plastic", SHELL, subdivide=1, texels=1.4))
    for driver, wall in zip((True, False), quarter):
        parts.append(sedan.wheelhouse(house, wall, shelf_z, 0.75, 0.26, 0.23, driver, flat=True, soft=True))
    # The deck behind the shelf, carpeted up to the back window's foot, rolled over at its top.
    xb = b_["foot"][0] + 0.025
    wide = s["sill_half"] - 0.04
    lean = math.radians(12)
    up = v(math.sin(lean), 0.0, math.cos(lean))
    rise = (b_["foot"][2] - 0.04 - shelf_z) / math.cos(lean)
    parts.append(upholstery.cushion("rear deck", "carpet", v(xb, house.mid, shelf_z), (0, 1, 0), up, (math.cos(lean), 0.0, -math.sin(lean)), 2 * wide, rise, 0.035,
                                    channels=1, margin=0.05, roll=0.03, crown=0.015, seam_depth=0.0, texels=0.5, subdivide=1))
    top = v(xb, house.mid, shelf_z) + up * rise
    b = Builder()
    b.sweep([top + v(0.03, house.y(wide - 0.01) - house.mid, 0.0), top + v(0.03, house.y(-(wide - 0.01)) - house.mid, 0.0)], circle(0.035, 16),
            ups=[v(0, 0, 1)] * 2)
    parts.append(b.finish("rear deck roll", "carpet", SHELL, texels=0.5))
    return parts


def build(spec):
    house = shell.House(spec)
    shell.panes(house)
    shell.lining(house)
    shell.pillars(house, dash="leather")
    dash(house)
    fascia(house)
    steering_wheel()
    compact.column()
    console(house)
    seats(house)
    doors(house)
    sedan.floor(house, x_back=-0.40, tunnel_high=0.16)
    sedan.pedals()
    sedan.visors_and_mirror(house)
    sedan.door_mirrors(house, aero=True)
    luggage(house)
    sedan.belts(house)
    return house


def info(house, mirrors):
    return sedan.info(house, mirrors, needles(), glow_at=(0.56, 0.0, -0.26))
