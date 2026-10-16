"""The interior of the Chevalier Dart, the Masterson Horizon and the small cars of their kind: an economy hatchback
of the early eighties. A low dashboard in grained hard plastic with the instruments in a pod stood on it, round
eyeball vents, an open tray before the passenger, a floor gear lever and a hand brake in a short console, two
low-backed buckets, long doors with a pull in the armrest, and a folding rear bench under the hatch's cover.
"""

import math

from mathutils import Vector

from . import materials, sedan, shell, upholstery
from .kit import NEEDLE_REVS, NEEDLE_SPEED, SHELL, WHEEL, Builder, circle, lerp, rounded_rect, smooth, spline, superellipse, v
from .sedan import COLUMN, FLOOR_Z, RIM, SEAT_Z, WHEEL_AT, wheel_frame

# The pod's instruments: the speedometer before the driver, the revs to its left, the fuel and heat dial to its right.
SPEEDO = (v(0.500, -0.004, -0.222), 0.050)
REVS = (v(0.500, 0.106, -0.225), 0.038)
COMBI = (v(0.500, -0.108, -0.226), 0.034)


def needles():
    return [(NEEDLE_SPEED, SPEEDO[0], SPEEDO[1], 0.92, sedan.SPEED_SWEEP),
            (NEEDLE_REVS, REVS[0], REVS[1], 0.92, (math.radians(-130), math.radians(130), 1.0)),
            *sedan.combi_needles(*COMBI)]


def dash(house):
    """A low dashboard: a narrow padded top from the windscreen's foot, a rolled edge, and a face that falls nearly
    straight to a lip over the driver's knees, the same all the way across."""
    s = house.side
    inner = s["sill_half"] - 0.030
    rings = []
    for k in range(57):
        out = lerp(inner, -inner, k / 56)
        y = house.y(out)
        xf, zf = house.edge("foot", abs(out))
        xf -= 0.01
        top_z = max(zf + 0.01, -0.205)
        section = [(xf + 0.05, zf - 0.07), (xf + 0.004, zf + 0.001), (lerp(xf, 0.53, 0.5), lerp(zf, top_z, 0.6)), (0.545, top_z), (0.528, top_z - 0.004),
                   (0.518, top_z - 0.016), (0.515, top_z - 0.034), (0.515, -0.30), (0.517, -0.36), (0.520, -0.40), (0.526, -0.425), (0.56, -0.44),
                   (0.66, -0.45), (0.80, -0.35)]
        rings.append([v(x, y, z) for (x, z) in section])
    b = Builder()
    b.loft(rings, closed=True, caps=True)
    return b.finish("dashboard", "plastic", SHELL, subdivide=2, coarsest=1, texels=1.4, sharp=50)


def pod():
    """The instruments' pod stood on the dash before the driver: a hooded box, its face set back under the hood."""
    parts = []
    # One loft from its capped front round the outside to the rim, then in along the hood's inner wall to the face,
    # so the hood is open towards the driver and the dials sit inside it.
    b = Builder()
    rings = []
    for x, width, height, lift in ((0.565, 0.32, 0.125, 0.0), (0.548, 0.32, 0.125, 0.008), (0.505, 0.326, 0.125, 0.008), (0.468, 0.334, 0.125, 0.008),
                                   (0.460, 0.330, 0.121, 0.008), (0.459, 0.306, 0.109, 0.006), (0.470, 0.302, 0.107, 0.006), (0.512, 0.300, 0.106, 0.004)):
        rings.append([v(x, u, -0.228 + w + lift * (w > 0)) for (u, w) in rounded_rect(width, height, 0.025, 6)])
    b.loft(rings, closed=True, caps=True)
    parts.append(b.finish("instrument pod", "plastic", SHELL, subdivide=2, texels=1.5))
    # The face inside it, black, behind the dials.
    b = Builder()
    b.slab(rounded_rect(0.296, 0.104, 0.02), v(0.508, 0.0, -0.227), (0, -1, 0), (0, 0, 1), 0.004)
    parts.append(b.finish("pod face", "black", SHELL, texels=1.5, smooth_shade=False))
    parts += sedan.instrument("speedometer", SPEEDO[0], SPEEDO[1], "speedometer")
    parts += sedan.instrument("revs", REVS[0], REVS[1], "revs")
    parts += sedan.instrument("fuel and heat", COMBI[0], COMBI[1], "fuel-heat")
    for group, pivot, radius, length, _ in needles():
        parts += sedan.needle(group, pivot, radius, length)
    return parts


def eyeball(centre, facing, radius=0.032):
    """A round vent that turns in its socket: a bezel, the ball, and the vanes across it."""
    facing = Vector(facing).normalized()
    parts = []
    b = Builder()
    b.lathe([(radius + 0.008, -0.004), (radius + 0.009, 0.002), (radius + 0.004, 0.006), (radius, 0.004), (radius, -0.01)], centre, facing, count=32, caps=False)
    parts.append(b.finish("vent bezel", "black", SHELL, subdivide=1, texels=1.6))
    b = Builder()
    # The ball, its face cut back into the round opening the air comes out of.
    hole = radius * 0.68
    b.lathe([(radius * 0.96, -0.012), (radius * 0.985, 0.0), (radius * 0.93, 0.006), (radius * 0.76, 0.010), (hole + 0.002, 0.009), (hole, 0.004), (hole, -0.010),
             (0.0, -0.010)], centre, facing, count=32)
    parts.append(b.finish("vent ball", "black", SHELL, subdivide=1, texels=1.6))
    # Thin straight vanes across the opening, inside it.
    b = Builder()
    side = facing.cross(v(0, 0, 1)).normalized()
    for k in range(4):
        h = lerp(-hole * 0.62, hole * 0.62, k / 3)
        b.slab(rounded_rect(2 * math.sqrt(hole ** 2 - h ** 2) - 0.001, 0.012, 0.002), centre - facing * 0.003 + v(0, 0, h), side, facing, 0.001, both=True)
    parts.append(b.finish("vent vanes", "black", SHELL, texels=1.6, smooth_shade=False))
    return parts


def dash_details(house):
    parts = []
    mid = house.mid
    s = house.side
    # Round vents at each end of the face and a pair in its middle.
    for out in (s["sill_half"] - 0.115, -(s["sill_half"] - 0.115)):
        parts += eyeball(v(0.513, house.y(out), -0.255), (-1, 0, 0))
    for dy in (-0.06, 0.06):
        parts += eyeball(v(0.513, mid + dy, -0.244), (-1, 0, 0), 0.028)
    # The radio and the heater's levers in a panel under the middle vents.
    b = Builder()
    b.slab(rounded_rect(0.20, 0.10, 0.008), v(0.513, mid, -0.345), (0, -1, 0), (0, 0, 1), 0.006)
    parts.append(b.finish("centre panel", "black", SHELL, bevel=0.002, texels=1.6))
    parts.append(sedan.surround("centre panel surround", "plastic", v(0.513 - 0.006, mid, -0.345), (0, -1, 0), (0, 0, 1), 0.20, 0.10, 0.006, lip=0.006, radius=0.012))
    b = Builder()
    b.slab(rounded_rect(0.17, 0.042, 0.004), v(0.508, mid, -0.322), (0, -1, 0), (0, 0, 1), 0.006)
    parts.append(b.finish("radio face", "black", SHELL, bevel=0.0015, texels=2.5))
    b = Builder()
    b.slab(rounded_rect(0.08, 0.016, 0.002), v(0.5015, mid, -0.317), (0, -1, 0), (0, 0, 1), 0.0015)
    parts.append(b.finish("radio window", "lens", SHELL, texels=3.0, smooth_shade=False))
    for dy in (-0.066, 0.066):
        b = Builder()
        b.lathe([(0.0, 0.0), (0.0095, 0.0), (0.0095, 0.009), (0.008, 0.014), (0.0, 0.015)], v(0.502, mid + dy, -0.322), v(-1, 0, 0), count=24)
        parts.append(b.finish("radio knob", "chrome", SHELL, texels=2.0, sharp=40))
    # Its five preset buttons under the dial's window.
    for k in range(5):
        b = Builder()
        b.slab(rounded_rect(0.014, 0.008, 0.002), v(0.5005, mid - 0.03 + k * 0.015, -0.333), (0, -1, 0), (0, 0, 1), 0.004)
        parts.append(b.finish("radio button", "chrome", SHELL, bevel=0.001, texels=2.0))
    # The heater's three levers, each in a slot of its own in a recessed panel under the radio.
    parts.append(sedan.surround("heater surround", "plastic", v(0.508, mid, -0.372), (0, -1, 0), (0, 0, 1), 0.17, 0.034, 0.004, lip=0.004))
    for k, at in enumerate((0.25, 0.75, 0.5)):
        y = mid + lerp(0.06, -0.06, k / 2)
        b = Builder()
        b.slab(rounded_rect(0.042, 0.005, 0.002), v(0.5075, y, -0.372), (0, -1, 0), (0, 0, 1), 0.001)
        parts.append(b.finish("heater slot", "black", SHELL, texels=2.0, smooth_shade=False))
        b = Builder()
        b.slab(rounded_rect(0.010, 0.014, 0.002), v(0.505, y + lerp(-0.018, 0.018, at), -0.372), (0, -1, 0), (0, 0, 1), 0.010)
        parts.append(b.finish("heater lever", "chrome", SHELL, bevel=0.001, texels=2.0))
    # The glove box before the passenger: its lid laid flush onto the face's curve inside a shut line
    # (cabins.lay_on_face), a push button at its top edge.
    ty = house.y(-0.37)
    b = Builder()
    b.panel(rounded_rect(0.27, 0.11, 0.014, 6), v(0.35, ty, -0.34), (0, -1, 0), (0, 0, 1))
    lid = b.finish("glove box", "plastic", SHELL, solidify=0.001, texels=1.0)
    lid["lay"] = 0.0016
    parts.append(lid)
    b = Builder()
    b.panel(rounded_rect(0.278, 0.118, 0.018, 6), v(0.35, ty, -0.34), (0, -1, 0), (0, 0, 1))
    line = b.finish("glove box shut line", "black", SHELL, solidify=0.004, texels=1.0, smooth_shade=False)
    line["lay"] = 0.0005
    parts.append(line)
    b = Builder()
    b.slab(rounded_rect(0.035, 0.014, 0.004), v(0.35, ty, -0.298), (0, -1, 0), (0, 0, 1), 0.003)
    button = b.finish("glove box button", "chrome", SHELL, bevel=0.001, texels=2.0)
    button["lay"] = 0.0046
    parts.append(button)
    # The demister grille along the foot of the windscreen.
    parts += sedan.demisters(house, 0.515)
    return parts


def steering_wheel():
    """A slim rim on two spokes that fall away below the middle to an oblong horn pad."""
    m = wheel_frame()
    parts = []
    b = Builder()
    n = 64
    rings = []
    for k in range(n):
        a = 2 * math.pi * k / n
        p = v(0.0, RIM * math.sin(a), RIM * math.cos(a))
        radial = v(0, p.y, p.z).normalized()
        rings.append([p + radial * (0.0105 * math.sin(2 * math.pi * j / 14)) + v(1, 0, 0) * (0.0125 * math.cos(2 * math.pi * j / 14)) for j in range(14)])
    made = [[b.vert(q) for q in ring] for ring in rings]
    for i in range(n):
        a, c = made[i], made[(i + 1) % n]
        for j in range(14):
            k = (j + 1) % 14
            b.face([a[j], a[k], c[k], c[j]])
    rim = b.finish("rim", "wheel", WHEEL, subdivide=1, texels=3.0, sharp=60)
    rim.data.transform(m)
    parts.append(rim)
    for side in (1, -1):
        parts.append(sedan.blade_spoke(v(0.034, side * 0.035, -0.022), v(0.002, side * 0.130, -0.135), 0.045, 0.03, 0.012, "wheel"))
    parts.append(sedan.horn_pad(0.17, 0.11, "wheel", drop=0.02))
    return parts


def column(wipers=False):
    """The shroud, the indicator stalk on the left, the wiper stalk on the right where there is one, and the key."""
    m = wheel_frame()
    parts = []
    b = Builder()
    # Long enough to run on into a dash that stands well forward of the wheel, as a truck's does.
    path = [v(t, 0, 0) for t in (0.065, 0.10, 0.14, 0.19, 0.34)]
    secs = [rounded_rect(0.07, 0.064, 0.026), rounded_rect(0.078, 0.07, 0.024), rounded_rect(0.088, 0.078, 0.02), rounded_rect(0.096, 0.088, 0.02),
            rounded_rect(0.10, 0.092, 0.02)]
    b.sweep(path, lambda i: secs[i], ups=[v(0, 0, 1)] * 5)
    shroud = b.finish("column shroud", "black", SHELL, subdivide=2, texels=1.5)
    shroud.data.transform(m)
    parts.append(shroud)
    # An American column has its one stalk on the left and the lever that tilts it; others the wipers' on the right too.
    parts += sedan.stalk(1)
    if wipers:
        parts += sedan.stalk(-1, "wiper stalk")
    else:
        parts += sedan.tilt_lever()
    b = Builder()
    b.lathe([(0.0, 0.0), (0.011, 0.0), (0.011, 0.006), (0.009, 0.008), (0.0, 0.008)], v(0.15, -0.048, 0.01), v(0, -1, 0), count=20)
    lock = b.finish("key lock", "chrome", SHELL, texels=2.0)
    lock.data.transform(m)
    parts.append(lock)
    return parts


def console(house):
    """The short console over the tunnel, rising at its front into the foot of the centre panel as one moulding: a
    coin tray in its face, the gear lever in a square gaiter in a bezel, and the hand brake behind it in its slot,
    its grip and button at the end nearest the driver's hand."""
    parts = []
    mid = house.mid
    b = Builder()
    rings = []
    # Between the seats no wider than the space they leave, with a finger's width clear of each.
    room = 2 * abs(mid) - seat_width(house) - 0.025
    for x, top, w in ((0.535, -0.385, 0.215), (0.50, -0.40, 0.215), (0.44, -0.47, 0.205), (0.34, -0.57, 0.195), (0.18, -0.62, 0.185), (-0.05, -0.635, 0.18),
                      (-0.22, -0.64, 0.17)):
        w = min(w, room) if x < 0.30 else w
        h = top - (FLOOR_Z + 0.02)
        rings.append([v(x, mid + u, FLOOR_Z + 0.02 + h * 0.5 + wv) for (u, wv) in rounded_rect(w, h, 0.03, 4)])
    b.loft(rings, closed=True, caps=True)
    parts.append(b.finish("console", "plastic", SHELL, subdivide=1, texels=0.9))
    # The coin tray let into its sloping face.
    slope = v(0.65, 0, 0.76).normalized()
    parts.append(sedan.surround("coin tray", "black", v(0.475, mid, -0.43), (0, -1, 0), slope, 0.12, 0.045, 0.004, lip=0.006))
    # The gear lever: a square gaiter of leather in a black bezel on the console's top.
    base = v(0.27, mid, -0.598)
    b = Builder()
    rings = []
    # Gathered in folds as it narrows up to the lever.
    for k in range(15):
        t = k / 14
        size = lerp(0.125, 0.024, t ** 0.7) * (1.0 - 0.07 * (k % 2) * (0 < k < 14))
        rings.append([base + v(u, w, 0.115 * t ** 1.2) for (u, w) in rounded_rect(size * 1.12, size, size * 0.18, 4)])
    b.loft(rings, closed=True, caps=True)
    parts.append(b.finish("gear gaiter", "vinyl", SHELL, subdivide=1, texels=1.2))
    parts.append(sedan.surround("gear bezel", "black", base + v(0, 0, 0.004), (1, 0, 0), (0, 1, 0), 0.14, 0.125, 0.004, lip=0.008))
    top = base + v(-0.035, 0.0, 0.19)
    b = Builder()
    b.sweep(spline([base + v(-0.012, 0, 0.10), base + v(-0.026, 0, 0.16), top], 10), lambda i: circle(0.0075, 12), ups=[v(1, 0, 0)] * 10)
    parts.append(b.finish("gear lever", "chrome", SHELL, texels=1.5))
    b = Builder()
    b.lathe([(0.0, -0.005), (0.016, 0.0), (0.021, 0.012), (0.021, 0.025), (0.016, 0.036), (0.0, 0.04)], top, v(-0.2, 0, 1), count=24)
    parts.append(b.finish("gear knob", "black", SHELL, subdivide=1, texels=2.0))
    # The hand brake: pivoted at its front in a slot in the console, its grip rising back towards the driver, the
    # button in the grip's end.
    pivot = v(0.10, mid, -0.625)
    end = pivot + v(-0.24, 0, 0.07)
    b = Builder()
    b.slab(rounded_rect(0.26, 0.03, 0.012), pivot + v(-0.12, 0, 0.003), (1, 0, 0), (0, 1, 0), -0.006)
    parts.append(b.finish("hand brake slot", "black", SHELL, bevel=0.002, texels=1.4))
    b = Builder()
    b.sweep(spline([pivot, pivot + v(-0.10, 0, 0.035), end], 10), lambda i: rounded_rect(lerp(0.018, 0.032, i / 9), lerp(0.02, 0.034, i / 9), 0.01),
            ups=[v(0, 0, 1)] * 10)
    parts.append(b.finish("hand brake", "black", SHELL, subdivide=1, texels=1.4))
    b = Builder()
    b.lathe([(0.0, 0.0), (0.008, 0.0), (0.008, 0.008), (0.0, 0.009)], end + v(-0.001, 0, 0.001), v(-1, 0, 0.3), count=16)
    parts.append(b.finish("hand brake button", "chrome", SHELL, texels=1.4))
    return parts


def bucket(x_front, x_crease, y, width, z_top, back_top, lean, name, head="posts", pedestal=False, tip=0, channels=4):
    """A low-backed bucket: a cushion and a back each with bolsters raised either side of a channelled middle, and
    its head restraint on posts; or (head "integrated") a tall back of one piece with its head section above the
    channels, or (head "none") no head restraint at all. On a pedestal it stands on one post, as a van's does."""
    parts = []
    rise = math.radians(7)
    along = v(-math.cos(rise), 0.0, -math.sin(rise))
    length = x_front - x_crease
    base = z_top - 0.11
    parts.append(upholstery.cushion(name + " cushion", "cloth", v(x_front, y, base + length * math.sin(rise)), (0, 1, 0), along, (0, 0, 1), width, length,
                                    0.11, channels=channels, margin=0.085, roll=0.035, crown=0.008, seam_depth=0.010, along_seams=(0.18,), bolster=0.038, texels=1.1))
    up_back = v(-math.sin(lean), 0.0, math.cos(lean))
    facing = v(math.cos(lean), 0.0, math.sin(lean))
    tall = back_top + (0.24 if head == "integrated" else 0.0)
    height = (tall - z_top) / math.cos(lean)
    origin = v(x_crease - 0.10 * math.cos(lean) + 0.01, y, z_top - 0.02)
    seams = (0.66, 0.9) if head == "integrated" else (0.85,)
    parts.append(upholstery.cushion(name + " back", "cloth", origin, (0, 1, 0), up_back, facing, width * 0.96, height, 0.10, channels=channels, margin=0.08,
                                    roll=0.035, crown=0.01, seam_depth=0.010, along_seams=seams, bolster=0.042, texels=1.0, top_taper=0.25, narrow=0.32 if head == "integrated" else 0.22))
    parts += upholstery.back_panel(name, "vinyl", origin, up_back, facing, width * 0.92, height, pocket=name.startswith(("driver", "passenger")) and not pedestal,
                                   narrow=0.32 if head == "integrated" else 0.22)
    if tip:
        # A two door's back tips forward for the way into the rear: its release lever in a cup on the outboard shoulder.
        shoulder = origin + up_back * (height * 0.92) + v(0, tip * (width * 0.48 - 0.04), 0) + facing * 0.03
        b = Builder()
        b.slab(rounded_rect(0.09, 0.04, 0.012), shoulder, (0, 1, 0), facing, 0.008, both=True)
        parts.append(b.finish(name + " release cup", "black", SHELL, bevel=0.003, texels=1.4))
        b = Builder()
        b.slab(rounded_rect(0.065, 0.022, 0.008), shoulder + up_back * 0.008, (0, 1, 0), facing, 0.012, both=True)
        parts.append(b.finish(name + " release", "plastic", SHELL, bevel=0.003, texels=1.4))
    if head == "posts":
        top = origin + up_back * height + facing * 0.045
        parts.append(upholstery.block(name + " head rest", "cloth", top + up_back * 0.12, (0, 1, 0), up_back, facing, 0.23, 0.14, 0.08, texels=0.9, taper=0.08))
        for dy in (-0.06, 0.06):
            b = Builder()
            b.lathe([(0.0055, 0.0), (0.0055, 0.09)], top + v(0, dy, 0) - up_back * 0.03, up_back, count=12)
            parts.append(b.finish(name + " head rest post", "chrome", SHELL, texels=1.0))
            b = Builder()
            b.lathe([(0.0, 0.0), (0.011, 0.0), (0.011, 0.012), (0.0, 0.013)], top + v(0, dy, 0) - up_back * 0.012, up_back, count=16)
            parts.append(b.finish(name + " head rest collar", "black", SHELL, texels=1.0))
    # The runners it slides on, and the frame under it; or a pedestal down to the floor.
    b = Builder()
    b.slab(rounded_rect(length - 0.03, width - 0.06, 0.02), v((x_front + x_crease) / 2, y, base - 0.06), (1, 0, 0), (0, 1, 0), 0.07)
    parts.append(b.finish(name + " base", "black", SHELL, bevel=0.006, texels=0.5))
    if pedestal:
        b = Builder()
        b.lathe([(0.12, 0.0), (0.11, 0.012), (0.045, 0.02), (0.04, 0.03), (0.04, base - 0.07 - FLOOR_Z), (0.05, base - 0.06 - FLOOR_Z)],
                v((x_front + x_crease) / 2, y, FLOOR_Z), v(0, 0, 1), count=24)
        parts.append(b.finish(name + " pedestal", "black", SHELL, subdivide=1, texels=0.6))
    return parts


def seat_width(house):
    """The buckets as wide as the space between them leaves after the console's room."""
    return min(0.50, 2 * abs(house.mid) - 0.17)


def seats(house):
    s = house.side
    parts = []
    lean = math.radians(16)
    width = seat_width(house)
    parts += bucket(0.26, -0.15, 0.0, width, SEAT_Z + 0.02, -0.17, lean, "driver seat", tip=1)
    parts += bucket(0.26, -0.15, 2 * house.mid, width, SEAT_Z + 0.02, -0.17, lean, "passenger seat", tip=-1)
    inner = s["sill_half"] - 0.065
    rear = house.spec["seats"][2][0]
    parts += sedan.bench(rear + 0.24, rear - 0.20, house.y(inner), house.y(-inner), SEAT_Z + 0.03, -0.19, math.radians(20), "rear seat", pleats=10,
                         headrest=False)
    return parts


def doors(house):
    """Long front doors, and behind them the trim of each side round the rear bench."""
    s = house.side
    parts = []
    for driver in (True, False):
        parts += sedan.door_card(house, 0.50, s["b"][0] + 0.02, driver)
        # The side beside the rear bench, under the quarter window.
        b = Builder()
        grid = []
        for r in range(6):
            z = lerp(FLOOR_Z + 0.02, s["sill_z"] - 0.005, r / 5)
            out = s["sill_half"] - 0.06 - 0.03 * (1 - r / 5)
            grid.append([v(lerp(s["b"][1] - 0.01, s["rear_foot"] + 0.02, c / 10), house.y(out, driver), z) for c in range(11)])
        b.loft(grid, closed=False, caps=False)
        parts.append(b.finish("rear side trim", "trim", SHELL, solidify=0.015, texels=0.5))
        # A rolled cap along its top under the quarter glass.
        b = Builder()
        cap_out = s["sill_half"] - 0.06 - 0.008
        b.sweep([v(s["b"][1] - 0.02, house.y(cap_out, driver), s["sill_z"] + 0.004), v(s["rear_foot"] + 0.03, house.y(cap_out, driver), s["sill_z"] + 0.004)],
                rounded_rect(0.04, 0.022, 0.01), ups=[v(0, 0, 1)] * 2)
        parts.append(b.finish("rear side cap", "trim", SHELL, subdivide=1, texels=0.5))
        # An armrest moulded out of it, flat on top and tucked back into the trim under it, ending clear of the bench's
        # back, with an ash tray let into its front.
        side = 1 if driver else -1
        face = s["sill_half"] - 0.075
        az = s["sill_z"] - 0.20
        sec = [(side * 0.0, 0.025), (side * -0.052, 0.025), (side * -0.059, 0.021), (side * -0.062, 0.013), (side * -0.062, -0.022), (side * -0.058, -0.030),
               (side * -0.035, -0.044), (side * 0.0, -0.056)]
        x0, x1 = s["b"][1] - 0.04, s["b"][1] - 0.29
        b = Builder()
        b.bar(v(x1, house.y(face, driver), az), v(x0, house.y(face, driver), az), sec, lambda p, u, w: p + v(0, u, w), inset=0.96, ends=0.006)
        parts.append(b.finish("rear armrest", "vinyl", SHELL, bevel=0.004, bevel_segments=2, texels=0.8))
        b = Builder()
        b.slab(rounded_rect(0.06, 0.03, 0.006), v(x0 - 0.05, house.y(face - 0.028, driver), az + 0.0255), (1, 0, 0), (0, 1, 0), 0.002)
        parts.append(b.finish("rear ash tray", "chrome", SHELL, bevel=0.001, texels=1.6))
        parts += sedan.vent_bar(house, driver, latch=False)
    return parts


def pedals():
    """Clutch, brake and accelerator: the car has a gear lever on the floor."""
    parts = []
    parts += sedan.hung_pedal("clutch pedal", v(0.66, 0.19, -0.60), (0.065, 0.055), v(0.58, 0.19, -0.45))
    parts += sedan.hung_pedal("brake pedal", v(0.66, 0.08, -0.60), (0.07, 0.055), v(0.58, 0.08, -0.45))
    parts += sedan.accelerator(v(0.70, -0.16, -0.63))
    return parts


def build(spec):
    house = shell.House(spec)
    shell.panes(house)
    shell.lining(house)
    shell.pillars(house, dash="plastic")
    dash(house)
    pod()
    dash_details(house)
    steering_wheel()
    column(wipers=True)
    console(house)
    seats(house)
    doors(house)
    sedan.floor(house)
    pedals()
    sedan.visors_and_mirror(house)
    sedan.door_mirrors(house, aero=True)
    sedan.back_shelf(house, corners_cut=True)
    sedan.belts(house)
    return house


def info(house, mirrors):
    return sedan.info(house, mirrors, needles(), glow_at=(0.50, -0.02, -0.20))
