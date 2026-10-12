"""The interior of the Chevalier D6 pickup, the Dash Bulldriver and the Franklin All-Terrain: a square-bodied
American truck of the early eighties, whose sport utility shared its dashboard. An upright dashboard with a deep pad
over a flat face, the instruments behind a rectangular bezel, rotary heater knobs, a big thin two-spoke wheel with
a broad horn pad, a column gear lever, a full-width vinyl bench, steel door tops painted with the cab, and a rubber
floor. The All-Terrain adds rear doors, a rear bench and a load space.
"""

import math

from mathutils import Matrix, Vector

from . import kit, materials, sedan, shell, upholstery
from .kit import NEEDLE_FUEL, NEEDLE_HEAT, NEEDLE_SPEED, SHELL, WHEEL, Builder, circle, lerp, rounded_rect, smooth, spline, v
from .sedan import FLOOR_Z, RIM, SEAT_Z, WHEEL_AT, wheel_frame

# The instruments behind the bezel: a large speedometer, the fuel and heat dial, the warning lamps.
SPEEDO = (v(0.474, 0.030, -0.259), 0.054)
COMBI = (v(0.474, -0.110, -0.259), 0.040)
LAMPS = (v(0.474, 0.168, -0.259), 0.034)
# The face of the dash stands this far ahead of the eye, nearly upright.
FACE_X = 0.495
# Round dials let straight into the steel face in chrome rings, as the off-roader has them, with the lamps beside.
ROUND = {"speedo": (v(0.495, 0.050, -0.287), 0.054), "combi": (v(0.495, -0.078, -0.289), 0.041), "lamps": (v(0.497, 0.165, -0.287), 0.034)}
BOXED = {"speedo": SPEEDO, "combi": COMBI, "lamps": LAMPS}


def needles(dials=BOXED):
    speedo, combi = dials["speedo"], dials["combi"]
    return [(NEEDLE_SPEED, speedo[0], speedo[1], 0.92, sedan.SPEED_SWEEP),
            (NEEDLE_FUEL, combi[0] + v(0, 0.013, 0), combi[1], 0.62, sedan.FUEL_SWEEP),
            (NEEDLE_HEAT, combi[0] + v(0, -0.013, 0), combi[1], 0.62, sedan.HEAT_SWEEP)]


def dash(house, material="padded", face_x=FACE_X, flat=False):
    """A deep padded top from the windscreen's foot, overhanging a flat upright face that runs from door to door; or,
    flat, a plain steel panel: a shelf from the windscreen's foot to a folded lip, and the face straight down."""
    s = house.side
    inner = s["sill_half"] - 0.030
    rings = []
    for k in range(41):
        out = lerp(inner, -inner, k / 40)
        xf, zf = house.edge("foot", abs(out))
        xf -= 0.01
        top = max(zf + 0.02, -0.180)
        if flat:
            section = [(xf + 0.06, zf - 0.08), (xf + 0.004, zf + 0.001), (face_x + 0.03, zf + 0.004), (face_x - 0.004, zf + 0.002), (face_x - 0.008, zf - 0.006),
                       (face_x - 0.008, zf - 0.026), (face_x + 0.004, zf - 0.03), (face_x + 0.004, -0.44), (face_x + 0.02, -0.46), (max(0.66, face_x + 0.05), -0.47),
                       (max(0.80, face_x + 0.10), -0.36)]
        else:
            section = [(xf + 0.06, zf - 0.08), (xf + 0.004, zf + 0.001), (lerp(xf, FACE_X, 0.5), top + 0.004), (FACE_X + 0.02, top + 0.004),
                       (FACE_X - 0.008, top - 0.002), (FACE_X - 0.020, top - 0.018), (FACE_X - 0.020, top - 0.040), (FACE_X - 0.004, top - 0.052),
                       (FACE_X + 0.008, top - 0.056), (FACE_X + 0.008, -0.40), (FACE_X + 0.012, -0.44), (FACE_X + 0.04, -0.455), (0.66, -0.47), (0.80, -0.36)]
        rings.append([v(x, house.y(out), z) for (x, z) in section])
    b = Builder()
    b.loft(rings, closed=True, caps=True)
    return b.finish("dashboard", material, SHELL, subdivide=2, coarsest=1, texels=1.4, sharp=50)


def dash_details(house, face_x=FACE_X, glove=True, demister=True, dials=BOXED, demister_frac=0.4, stack_drop=0.0):
    """The instruments in a box standing out of the face (out of a flat dash, a pod on its own), the heater and the
    radio in the middle, the glove box, the vents and the demister."""
    parts = []
    mid = house.mid
    s = house.side
    face = face_x + 0.008
    # The instruments stand out of the face in a box: a rectangular bezel round its front, its sides back to the
    # face, and the black panel the dials are set in. Round dials are let into the face itself.
    if dials is BOXED:
        parts += instrument_box(house, face)
    for name, (c, r), picture in (("speedometer", dials["speedo"], "speedometer"), ("fuel and heat", dials["combi"], "fuel-heat")):
        parts += sedan.instrument(name, c, r, picture)
    c = dials["lamps"][0]
    if dials is ROUND:
        # The lights' and wipers' pull switches on a plate of their own beside the speedometer.
        b = Builder()
        b.slab(rounded_rect(0.06, 0.09, 0.008), c, (0, -1, 0), (0, 0, 1), 0.003)
        parts.append(b.finish("switch plate", "black", SHELL, bevel=0.001, texels=2.0))
        for dz in (0.022, -0.022):
            b = Builder()
            b.lathe([(0.0, 0.0), (0.011, 0.0), (0.011, 0.012), (0.009, 0.016), (0.0, 0.017)], c + v(-0.003, 0, dz), v(-1, 0, 0), count=20)
            parts.append(b.finish("pull switch", "chrome", SHELL, subdivide=1, texels=2.0))
        for group, pivot, radius, length, _ in needles(dials):
            parts += sedan.needle(group, pivot, radius, length)
        parts += controls(house, face, glove, demister_frac)
        return parts
    # The warning lamps in an upright strip of their own, not a dial.
    b = Builder()
    b.slab(rounded_rect(0.05, 0.085, 0.008), c + v(0.002, 0, 0), (0, -1, 0), (0, 0, 1), 0.003)
    materials.DIALS["dial lamps-strip"] = (c, 0.025, 0.0425)
    parts.append(b.finish("lamps face", "dial lamps-strip", SHELL, texels=6.0, smooth_shade=False))
    parts.append(sedan.surround("lamps surround", "chrome", c - v(0.001, 0, 0), (0, -1, 0), (0, 0, 1), 0.05, 0.085, 0.003, lip=0.004))
    for group, pivot, radius, length, _ in needles(dials):
        parts += sedan.needle(group, pivot, radius, length)
    stack_from = len(kit.PARTS)
    parts += controls(house, face, glove, demister_frac)
    # Under a flat dash's folded lip the stack stands lower, so the lip does not cover its top.
    if stack_drop:
        for p in kit.PARTS[stack_from:]:
            if p.name.startswith(("stack", "heater", "radio", "ash tray")):
                p.data.transform(Matrix.Translation((0.0, 0.0, -stack_drop)))
    return parts


def instrument_box(house, face):
    """The box round the instruments, its outer end drawn in on a narrow cab so the windscreen's pillar lands on the
    dash and the end vent keeps its room beside it."""
    parts = []
    outboard = min(0.215, house.y(house.side["sill_half"] - 0.135))
    cy, wide = (outboard - 0.17) / 2, outboard + 0.17
    b = Builder()
    rings = [[v(x, cy + u, -0.252 + w) for (u, w) in rounded_rect(wide, 0.142, 0.012, 4)] for x in (0.462, 0.470, face + 0.004)]
    b.loft(rings, closed=True, caps=False)
    parts.append(b.finish("instrument box", "plastic", SHELL, subdivide=1, texels=1.2))
    b = Builder()
    outer = rounded_rect(wide + 0.02, 0.162, 0.016, 6)
    inner = rounded_rect(wide - 0.02, 0.128, 0.008, 6)
    fo = [b.vert(v(0.460, cy + u, -0.252 + w)) for (u, w) in outer]
    fi = [b.vert(v(0.464, cy + u, -0.252 + w)) for (u, w) in inner]
    fb = [b.vert(v(0.470, cy + u, -0.252 + w)) for (u, w) in outer]
    n = len(outer)
    for i in range(n):
        j = (i + 1) % n
        b.face([fo[i], fo[j], fi[j], fi[i]])
        b.face([fb[i], fb[j], fo[j], fo[i]])
    parts.append(b.finish("instrument bezel", "chrome", SHELL, texels=1.5, sharp=40))
    b = Builder()
    b.slab(rounded_rect(wide - 0.02, 0.128, 0.008), v(0.484, cy, -0.252), (0, -1, 0), (0, 0, 1), 0.004)
    parts.append(b.finish("instrument panel", "black", SHELL, texels=1.5, smooth_shade=False))
    return parts


def controls(house, face, glove, demister_frac):
    parts = []
    mid = house.mid
    s = house.side
    # One moulded bezel across the middle of the face, standing a little proud of it, the heater's panel over the
    # radio set into it, and the ash tray's closed drawer along its foot.
    cy = mid + 0.02
    front = face - 0.007
    b = Builder()
    b.slab(rounded_rect(0.24, 0.175, 0.012), v(face + 0.002, cy, -0.3375), (0, -1, 0), (0, 0, 1), 0.009)
    parts.append(b.finish("stack bezel", "plastic", SHELL, bevel=0.003, texels=1.4))
    b = Builder()
    b.slab(rounded_rect(0.20, 0.05, 0.006), v(front + 0.004, cy, -0.28), (0, -1, 0), (0, 0, 1), 0.0035)
    parts.append(b.finish("heater panel", "black", SHELL, bevel=0.001, texels=2.0))
    # Two levers sliding along slots across it, one over the other, and the fan's knob at its end.
    for z, at in ((-0.2715, 0.3), (-0.2885, 0.7)):
        b = Builder()
        b.slab(rounded_rect(0.11, 0.005, 0.002), v(front + 0.0003, cy + 0.02, z), (0, -1, 0), (0, 0, 1), 0.0008)
        parts.append(b.finish("heater slot", "black", SHELL, texels=2.0, smooth_shade=False))
        b = Builder()
        b.slab(rounded_rect(0.012, 0.024, 0.004), v(front, cy + 0.02 + lerp(0.05, -0.05, at), z), (0, -1, 0), (0, 0, 1), 0.007)
        parts.append(b.finish("heater lever", "black", SHELL, bevel=0.0015, texels=2.0))
    b = Builder()
    b.lathe([(0.0, 0.0), (0.013, 0.0), (0.013, 0.012), (0.010, 0.018), (0.0, 0.019)], v(front + 0.001, cy - 0.075, -0.28), v(-1, 0, 0), count=24)
    parts.append(b.finish("heater knob", "black", SHELL, subdivide=1, texels=2.0))
    # A step between them, and the radio.
    b = Builder()
    b.slab(rounded_rect(0.215, 0.004, 0.0015), v(front + 0.0005, cy, -0.3125), (0, -1, 0), (0, 0, 1), 0.002)
    parts.append(b.finish("stack divider", "black", SHELL, texels=1.4, smooth_shade=False))
    b = Builder()
    # The radio on a faceplate of its own standing proud of the bezel, its dial window between the knobs.
    b.slab(rounded_rect(0.18, 0.05, 0.004), v(front + 0.0005, cy, -0.345), (0, -1, 0), (0, 0, 1), 0.0045)
    parts.append(b.finish("radio face", "black", SHELL, bevel=0.0015, texels=2.5))
    b = Builder()
    b.slab(rounded_rect(0.10, 0.018, 0.002), v(front - 0.0039, cy, -0.345), (0, -1, 0), (0, 0, 1), 0.0012)
    parts.append(b.finish("radio window", "lens", SHELL, texels=3.0, smooth_shade=False))
    for dy in (-0.072, 0.072):
        b = Builder()
        b.lathe([(0.0, 0.0), (0.009, 0.0), (0.009, 0.008), (0.0075, 0.012), (0.0, 0.0125)], v(front - 0.0039, cy + dy, -0.345), v(-1, 0, 0), count=24)
        parts.append(b.finish("radio knob", "chrome", SHELL, texels=2.0, sharp=40))
    b = Builder()
    b.slab(rounded_rect(0.22, 0.042, 0.006), v(front + 0.0002, cy, -0.398), (0, -1, 0), (0, 0, 1), 0.0015)
    parts.append(b.finish("ash tray shut line", "black", SHELL, texels=1.5, smooth_shade=False))
    b = Builder()
    b.slab(rounded_rect(0.214, 0.036, 0.005), v(front, cy, -0.398), (0, -1, 0), (0, 0, 1), 0.0015)
    parts.append(b.finish("ash tray", "plastic", SHELL, bevel=0.0008, texels=1.5))
    b = Builder()
    b.slab(rounded_rect(0.08, 0.008, 0.003), v(front - 0.0015, cy, -0.384), (0, -1, 0), (0, 0, 1), 0.006)
    parts.append(b.finish("ash tray pull", "black", SHELL, bevel=0.002, texels=1.5))
    parts += sedan.demisters(house, face - 0.008, frac=demister_frac)
    for k, dy in enumerate((0.19, 0.24)):
        b = Builder()
        b.lathe([(0.0, 0.0), (0.004, 0.0), (0.004, 0.012), (0.012, 0.014), (0.012, 0.026), (0.0, 0.028)], v(face - 0.002, dy, -0.36), v(-1, 0, 0), count=20)
        parts.append(b.finish("pull knob", "chrome" if k == 0 else "black", SHELL, subdivide=1, texels=2.0))
    gy = house.y(-0.44)
    if not glove:
        return parts
    # A trim band across the face before the passenger, over the glove box's top line.
    y0, y1 = mid - 0.13, house.y(-(s["sill_half"] - 0.15))
    # Laid onto the face's own curve (cabins.lay_on_face).
    b = Builder()
    b.panel(rounded_rect(abs(y1 - y0), 0.018, 0.006, 4), v(0.30, (y0 + y1) / 2, -0.238), (0, -1, 0), (0, 0, 1))
    band = b.finish("face trim band", "chrome", SHELL, solidify=0.002, texels=0.8)
    band["lay"] = 0.003
    parts.append(band)
    # The glove box's door flush in the face inside a dark shut line, its button let into its top.
    b = Builder()
    b.slab(rounded_rect(0.30, 0.12, 0.01), v(face - 0.001, gy, -0.31), (0, -1, 0), (0, 0, 1), 0.003)
    parts.append(b.finish("glove box", "plastic", SHELL, bevel=0.001, texels=1.0))
    b = Builder()
    b.slab(rounded_rect(0.306, 0.126, 0.013), v(face + 0.002, gy, -0.31), (0, -1, 0), (0, 0, 1), 0.002)
    parts.append(b.finish("glove box shut line", "black", SHELL, texels=1.0, smooth_shade=False))
    b = Builder()
    b.slab(rounded_rect(0.04, 0.014, 0.004), v(face - 0.005, gy, -0.265), (0, -1, 0), (0, 0, 1), 0.003)
    parts.append(b.finish("glove box button", "chrome", SHELL, bevel=0.001, texels=2.0))
    # Slatted vents at each end of the face and either side of the middle.
    for out in (s["sill_half"] - 0.085, -(s["sill_half"] - 0.085)):
        parts += sedan.vent(v(face - 0.012, house.y(out), -0.255), v(0, 1, 0), 0.08, 0.045)
    for dy in (-0.07, 0.11):
        parts += sedan.vent(v(face - 0.020, mid + dy, -0.226), v(0, 1, 0), 0.10, 0.032)
    # The demister's grille along the foot of the windscreen.
    return parts


def transfer_lever(base):
    """The four-wheel drive's transfer lever standing out of the tunnel in a folded rubber boot, a ball knob on top."""
    parts = []
    b = Builder()
    b.lathe([(0.065, -0.004), (0.065, 0.004), (0.055, 0.008), (0.052, 0.016), (0.053, 0.024), (0.040, 0.034), (0.041, 0.042), (0.027, 0.054), (0.027, 0.061), (0.014, 0.072),
             (0.011, 0.078)], base,
            v(-0.2, 0, 1), count=20)
    parts.append(b.finish("transfer boot", "rubber", SHELL, subdivide=1, texels=1.0))
    top = base + v(-0.05, 0, 0.22)
    b = Builder()
    b.sweep(spline([base + v(-0.01, 0, 0.06), base + v(-0.02, 0, 0.13), top], 8), lambda i: circle(0.0085, 12), ups=[v(1, 0, 0)] * 8)
    parts.append(b.finish("transfer lever", "black", SHELL, texels=1.2))
    b = Builder()
    b.lathe([(0.0, -0.004), (0.014, 0.0), (0.019, 0.014), (0.016, 0.028), (0.0, 0.034)], top, v(-0.2, 0, 1), count=20)
    parts.append(b.finish("transfer knob", "black", SHELL, subdivide=1, texels=1.6))
    return parts


def steering_wheel():
    """A big thin rim on two straight spokes that meet a broad, soft horn pad."""
    m = wheel_frame()
    parts = []
    b = Builder()
    n = 64
    rings = []
    for k in range(n):
        a = 2 * math.pi * k / n
        p = v(0.0, RIM * math.sin(a), RIM * math.cos(a))
        radial = v(0, p.y, p.z).normalized()
        rings.append([p + radial * (0.0110 * math.sin(2 * math.pi * j / 14)) + v(1, 0, 0) * (0.0135 * math.cos(2 * math.pi * j / 14)) for j in range(14)])
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
        b = Builder()
        path = spline([v(0.040, side * 0.07, -0.005), v(0.024, side * 0.12, -0.008), v(0.004, side * (RIM - 0.008), -0.010)], 10)
        b.sweep(path, lambda i: rounded_rect(0.008, lerp(0.05, 0.034, i / 9), 0.004), ups=[v(-0.3, 0, 1)] * 10)
        sp = b.finish("spoke", "wheel", WHEEL, subdivide=1, texels=2.0, sharp=50)
        sp.data.transform(m)
        parts.append(sp)
    b = Builder()
    b.lathe([(0.0, 0.085), (0.04, 0.085), (0.055, 0.06), (0.06, 0.035), (0.0, 0.034)], v(0, 0, -0.005), v(1, 0, 0), count=32)
    hub = b.finish("hub", "black", WHEEL, subdivide=1, texels=1.5)
    hub.data.transform(m)
    parts.append(hub)
    b = Builder()
    rings = []
    for k in range(6):
        t = k / 5
        rings.append([v(lerp(0.052, 0.028, t), u, w - 0.005) for (u, w) in rounded_rect(lerp(0.16, 0.17, t) * (1 - 0.1 * t * t), lerp(0.11, 0.12, t) * (1 - 0.1 * t * t), 0.035, 8)])
    b.loft(rings, closed=True, caps=True)
    pad = b.finish("horn pad", "padded", WHEEL, subdivide=2, texels=2.5, sharp=60)
    pad.data.transform(m)
    parts.append(pad)
    return parts


def seats(house):
    """A full-width bench across the cab, its back up to the shoulders, and in the All-Terrain a rear bench too."""
    s = house.side
    parts = []
    inner = s["sill_half"] - 0.06
    parts += sedan.bench(0.27, -0.16, house.y(inner - 0.02), house.y(-(inner - 0.02)), SEAT_Z + 0.03, -0.16, math.radians(15), "front seat", pleats=12,
                         headrest=False)
    if len(house.spec["seats"]) > 2:
        rear = house.spec["seats"][2][0]
        # Out to the rear doors' cards, so no slot shows at its ends.
        out = s["sill_half"] - 0.066
        parts += sedan.bench(rear + 0.25, rear - 0.20, house.y(out), house.y(-out), SEAT_Z + 0.04, -0.19, math.radians(18), "rear seat", pleats=12, headrest=False)
    return parts


def door_card(house, x_front, x_back, driver):
    """A truck's door: the steel of its top painted with the cab, under it a moulded panel with an armrest, the
    chrome handle and the window's winder."""
    s = house.side
    parts = []
    side = 1 if driver else -1
    gl = s["sill_half"]

    def y(out):
        return house.y(out, driver)
    # The painted steel along the top, from the glass in to the panel.
    # Its section: from the glass's channel the steel rolls over inwards and falls to the panel below.
    section = [(0.004, 0.000), (0.012, 0.003), (0.024, 0.002), (0.036, -0.004), (0.046, -0.016), (0.052, -0.034), (0.054, -0.060), (0.055, -0.100)]
    b = Builder()
    grid = [[v(lerp(x_front + 0.09, x_back + 0.01, c / 16), y(gl - inward), s["sill_z"] + down) for c in range(17)] for inward, down in section]
    b.loft(grid, closed=False, caps=False)
    parts.append(b.finish("door steel", "painted", SHELL, solidify=0.008, subdivide=1, texels=0.6))
    parts += sedan.door_card(house, x_front, x_back, driver, roll=False)
    parts += sedan.vent_bar(house, driver)
    return parts


def doors(house):
    s = house.side
    parts = []
    for driver in (True, False):
        parts += door_card(house, 0.48, s["b"][0] + 0.02, driver)
        if house.spec.get("doors", 2) == 4:
            parts += sedan.door_card(house, s["b"][1] - 0.02, s["rear_foot"] + 0.05, driver, rear=True)
    return parts


def cab_back(house):
    """A pickup's cab ends behind the bench: a painted wall up to the back window, and the window's frame."""
    if house.spec.get("doors", 2) == 4:
        return []
    b_ = house.back
    s = house.side
    parts = []
    inner = s["sill_half"] - 0.03
    b = Builder()
    xb = b_["foot"][0] + 0.02
    b.slab([(house.y(inner), FLOOR_Z), (house.y(-inner), FLOOR_Z), (house.y(-inner), b_["foot"][2] - 0.005), (house.y(inner), b_["foot"][2] - 0.005)],
           v(xb, 0, 0), (0, 1, 0), (0, 0, 1), 0.02)
    parts.append(b.finish("cab back", "painted", SHELL, bevel=0.004, texels=0.5))
    return parts


def build(spec):
    house = shell.House(spec)
    shell.panes(house)
    shell.lining(house)
    shell.pillars(house)
    dash(house)
    dash_details(house)
    steering_wheel()
    sedan.column()
    seats(house)
    doors(house)
    four = spec.get("doors", 2) == 4
    sedan.floor(house, x_back=-1.18 if four else spec["back"]["foot"][0] + 0.02, material="floor rubber", tunnel_high=0.13)
    sedan.pedals()
    sedan.visors_and_mirror(house)
    sedan.door_mirrors(house)
    if four:
        # The All-Terrain drives all four wheels: its transfer lever on the tunnel ahead of the bench, by the driver's knee.
        transfer_lever(v(0.37, house.mid + 0.10, FLOOR_Z + 0.14))
        sedan.back_shelf(house)
    cab_back(house)
    sedan.belts(house)
    return house


def info(house, mirrors, dials=BOXED):
    return sedan.info(house, mirrors, needles(dials), glow_at=(dials["speedo"][0].x, -0.03, -0.25))
