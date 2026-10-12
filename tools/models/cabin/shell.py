"""The glasshouse of a cabin, fitted to where its body has its glass: the panes, the pillars, the roof's
lining and the tops of the doors. See bodies.py for the measures it is built on."""

import math

from mathutils import Vector

from .kit import FLOOR_Z, GLASS, SHELL, Builder, lerp, mirror_y, rounded_rect, smooth, spline, v


def moulding(half, depth, count=14, back=0.004):
    """The section of a moulded trim strip: a broad, nearly flat face rounded off at its edges, its back flat."""
    out = []
    for k in range(count + 1):
        u = -half + 2 * half * k / count
        t = abs(u) / half
        out.append((u, depth * (1.0 - t ** 4) ** 0.5))
    return out + [(half, -back), (-half, -back)]


class House:
    """Places on a body's glasshouse, worked out from its measures. Across is counted out from the middle
    line, positive on the driver's side; the frame's y is middle + across for the driver's side."""

    def __init__(self, spec):
        self.spec = spec
        self.mid = spec["middle"]
        self.screen = spec["screen"]
        self.side = spec["side"]
        self.back = spec["back"]

    def y(self, out, driver=True):
        return self.mid + out if driver else self.mid - out

    # The windscreen's foot and top edges are each a gentle curve across: further forward at the middle.
    def edge(self, which, out, glass=None):
        """Where an edge of the windscreen (or of the back window, glass="back") is at out across from the middle:
        along and up. Each edge is a gentle curve, further out at the middle, and higher there where its measures
        give a height at the middle beside the one at the corners."""
        edge = (glass or self.screen)[which]
        corner_x, middle_x, z, half = edge[:4]
        z_middle = edge[4] if len(edge) > 4 else z
        t = min(abs(out) / half, 1.0)
        return lerp(middle_x, corner_x, t * t), lerp(z_middle, z, t * t)

    def back_edge_top(self, out):
        return self.edge("top", out, self.back)[0]

    def screen_at(self, out, up):
        """A place on the windscreen: out across from the middle (to the corners at its half width), up from 0 at its
        foot to 1 at its top."""
        fx, fz = self.edge("foot", out * self.screen["foot"][3])
        tx, tz = self.edge("top", out * self.screen["top"][3])
        half = lerp(self.screen["foot"][3], self.screen["top"][3], up)
        return v(lerp(fx, tx, up), self.mid + out * half, lerp(fz, tz, up))

    def back_at(self, out, up):
        b = self.back
        fx, fz = self.edge("foot", out * b["foot"][3], b)
        tx, tz = self.edge("top", out * b["top"][3], b)
        half = lerp(b["foot"][3], b["top"][3], up)
        return v(lerp(fx, tx, up), self.mid + out * half, lerp(fz, tz, up))

    def glass_out(self, z, sill_half=None):
        """How far out the side glass is at a height: it leans in towards the roof."""
        s = self.side
        t = (z - s["sill_z"]) / (s["top_z"] - s["sill_z"])
        return lerp(s["sill_half"] if sill_half is None else sill_half, s["top_half"], t)

    def quarter_x(self, z, which):
        """Where a quarter window's front or rear edge is at a height."""
        s, q = self.side, self.spec["quarter"]
        t = (z - s["sill_z"]) / (s["top_z"] - s["sill_z"])
        return lerp(q[which + "_foot"], q[which + "_top"], t)

    def a_pillar_x(self, z):
        """Where the windscreen's corner is along at a height: on the line from its foot's corner to its top's."""
        foot, top = self.screen["foot"], self.screen["top"]
        return lerp(foot[0], top[0], (z - foot[2]) / (top[2] - foot[2]))

    def screen_x(self, out, z):
        """How far along the windscreen is at a height, out across from the middle."""
        fx, fz = self.edge("foot", out)
        tx, tz = self.edge("top", out)
        return lerp(fx, tx, (z - fz) / (tz - fz))

    def c_pillar_x(self, z):
        s = self.side
        t = (z - s["sill_z"]) / (s["top_z"] - s["sill_z"])
        return lerp(s["rear_foot"], s["rear_top"], t)

    def roof_base(self):
        """How high the lining is at its sides: at the top of the side glass, or where a body's windscreen stands
        higher than its side glass, as on a pickup's cab, at the windscreen's top."""
        return max(self.side["top_z"], self.screen["top"][2], self.back["top"][2] if self.back else -1.0)

    def roof_z(self, out):
        """The lining of the roof: a shallow dome over the heads, meeting the top of the side glass."""
        s = self.side
        t = min(abs(out) / s["top_half"], 1.0)
        return self.roof_base() + self.spec.get("dome", 0.028) * (1.0 - t * t) - 0.004


def side_pane(house, x_back, x_front_fn, driver=True, rows=8, cols=12, front_fn=None, back_fn=None, sill_half=None):
    """A side pane between two edges along the vehicle, from the sill to the top, following the glass's lean."""
    s = house.side
    b = Builder()
    rings = []
    for r in range(rows + 1):
        z = lerp(s["sill_z"], s["top_z"], r / rows)
        x0 = back_fn(z) if back_fn else x_back
        x1 = front_fn(z) if front_fn else x_front_fn
        out = house.glass_out(z, sill_half)
        rings.append([v(lerp(x0, x1, c / cols), house.y(out, driver), z) for c in range(cols + 1)])
    b.loft(rings, closed=False, caps=False)
    return b


def panes(house):
    """The glass, as the mod draws it."""
    s = house.side
    out = []
    # The windscreen.
    b = Builder()
    rings = []
    for r in range(13):
        rings.append([house.screen_at(lerp(-1, 1, c / 24), r / 12) for c in range(25)])
    b.loft(rings, closed=False, caps=False)
    out.append(b.finish("windscreen", "glass", GLASS, smooth_shade=True, sharp=80))
    for driver in (True, False):
        b = side_pane(house, s["b"][0], None, driver, front_fn=house.a_pillar_x)
        out.append(b.finish("front pane" + (" driver" if driver else " passenger"), "glass", GLASS, smooth_shade=False))
        if s.get("rear_foot") is not None:
            b = side_pane(house, None, s["b"][1], driver, back_fn=house.c_pillar_x)
            out.append(b.finish("rear pane" + (" driver" if driver else " passenger"), "glass", GLASS, smooth_shade=False))
        if "quarter" in house.spec:
            b = side_pane(house, None, None, driver, front_fn=lambda z: house.quarter_x(z, "front"), back_fn=lambda z: house.quarter_x(z, "rear"),
                          sill_half=house.spec["quarter"]["sill_half"])
            out.append(b.finish("quarter pane" + (" driver" if driver else " passenger"), "glass", GLASS, smooth_shade=False))
    if house.back is not None:
        b = Builder()
        rings = []
        for r in range(9):
            rings.append([house.back_at(lerp(-1, 1, c / 20), r / 8) for c in range(21)])
        b.loft(rings, closed=False, caps=False)
        out.append(b.finish("back window", "glass", GLASS, smooth_shade=True, sharp=80))
    return out


def lining(house, seams=True):
    """The roof's lining: from the rail over the windscreen to the one over the back window, between the rails
    over the doors, a little inside the glass, with a rounded edge where it turns down to meet each."""
    s = house.side
    b = Builder()
    rows, cols = 28, 22
    # It runs under the trim over each glass's top edge, so that no gap shows between them.
    front_x = lambda out: house.edge("top", out)[0] + 0.004
    back_x = (lambda out: house.back_edge_top(out) - 0.004) if house.back else (lambda out: s["rear_top"] - 0.02)
    grid = []
    for r in range(rows + 1):
        t = r / rows
        ring = []
        for c in range(cols + 1):
            u = lerp(-1, 1, c / cols)
            out = u * (s["top_half"] - 0.004)
            x = lerp(back_x(out * 0.98), front_x(out * 0.98), t)
            ring.append(v(x, house.mid + out, house.roof_z(out)))
        grid.append(ring)
    b.loft(grid, closed=False, caps=False)
    part = b.finish("roof lining", "lining", SHELL, solidify=-0.008, texels=0.7)
    # The cloth's listing: a seam across it every third of a metre, as a car's headliner is stretched over its bows.
    length = front_x(0.0) - back_x(0.0)
    if seams and house.back is not None:
        count = int(length / 0.32)
        for k in range(1, count + 1):
            t = k / (count + 1)
            b = Builder()
            path = []
            for c in range(31):
                out = lerp(-1, 1, c / 30) * (s["top_half"] - 0.008)
                path.append(v(lerp(back_x(out * 0.98), front_x(out * 0.98), t), house.mid + out, house.roof_z(out) - 0.0083))
            b.sweep(path, rounded_rect(0.008, 0.002, 0.0008), ups=[v(0, 0, -1)] * len(path))
            b.finish("roof lining seam", "lining", SHELL, texels=0.7)
    return part


def rail_over(points, width, depth, name, material="trim", texels=1.0):
    """A trim strip along points: a rounded section width across, depth deep, its back on the line."""
    b = Builder()
    path = spline(points, max(12, len(points) * 6))
    section = [(width / 2 * math.cos(a), depth * max(math.sin(a), 0.0) - 0.002) for a in [math.pi * k / 10 for k in range(11)]]
    section += [(-width / 2, -0.004), (width / 2, -0.004)]
    b.sweep(path, section)
    return b


def pillars(house, dash="padded"):
    """The trim over the glass's edges: the windscreen's pillars and the rail over it, the rails over the doors,
    the pillars behind the front and rear doors, and the rail over the back window. dash is the dashboard's
    material, which a car's end cap round each windscreen pillar's foot is moulded in."""
    s = house.side
    out = []
    for driver in (True, False):
        side = 1 if driver else -1
        # The windscreen's pillar: a moulded D-section, wide at its foot where it meets the dashboard, up to the
        # windscreen's top corner, which on a truck stands higher than the side glass. A flat-faced body (a truck's
        # or a van's) has it broad and shallow; a jeep's windscreen frame has a narrow flat-faced steel post.
        post = house.spec.get("pillar") == "post"
        flat = post or house.spec.get("pillar") == "flat"
        b = Builder()
        n = 16
        path, ups, scales = [], [], []
        for k in range(n + 1):
            t = k / n
            z = lerp(max(s["sill_z"], house.screen["foot"][2]) - 0.01, max(s["top_z"], house.screen["top"][2]) + 0.004, t)
            x = house.a_pillar_x(z)
            o = house.glass_out(min(z, s["top_z"])) - 0.012
            # Its face reaches just past the glass's corner, covering the edge: the face stands square to the
            # pillar, which leans back, so a width across it is longer along.
            foot, top = house.screen["foot"], house.screen["top"]
            stretch = math.hypot(top[0] - foot[0], top[2] - foot[2]) / abs(top[2] - foot[2])
            half = 0.03 if post else 0.044
            path.append(v(x - (half * (1.0 if post else lerp(1.0, 1.15, t) if flat else lerp(1.25, 0.74, t)) - 0.008) * stretch, house.y(o, driver), z))
            ups.append(v(-0.15, -side * 1.0, 0.0))
            # Narrower over its last stretch, where it tucks under the roof's lining: square to a raked pillar its
            # width stands up past the glass's top.
            # A car's tapers from broad at its foot to narrow at the roof.
            # A steel post runs straight up into the header; trims narrow over their last stretch to tuck under the lining.
            scales.append((1.0 if post else (lerp(1.0, 1.15, t) if flat else lerp(1.25, 0.74, t)) * (1.0 - 0.6 * max(0.0, (t - 0.8) / 0.2)), 1.0))
        # A flat body's is a square-edged steel section; a car's a moulded D.
        if post:
            section = [(u, w + 0.012) for (u, w) in rounded_rect(0.06, 0.03, 0.005, 3)]
        elif flat:
            section = [(u, w + 0.014) for (u, w) in rounded_rect(0.07, 0.035, 0.010, 5)]
        else:
            section = moulding(0.044, 0.019)
        b.sweep(path, section, ups=ups, scales=scales)
        part = b.finish("a pillar" + (" driver" if driver else " passenger"), "trim", SHELL, subdivide=1, texels=1.2)
        out.append(part)
        if not flat and not house.spec.get("race") and house.spec.get("pillar_cap", True):
            # The dash's end cap cupping the trim's foot, so the trim's end never shows standing on the dash.
            b = Builder()
            # A sleeve standing just outside the trim's face and edges, its back on the glass like the trim's.
            sleeve = moulding(0.044 + 0.003, 0.019 + 0.004)
            # Short, so that where the dash falls away towards the glass it does not stand tall up the pillar's flank.
            b.sweep([path[0].lerp(path[1], 0.25), path[1]], sleeve, ups=ups[:2], scales=scales[:2])
            out.append(b.finish("a pillar foot cap" + (" driver" if driver else " passenger"), dash, SHELL, subdivide=1, texels=1.2))
        # The rail over the door, from the windscreen's pillar back to the back window's.
        rear = (house.back["top"][0] + 0.01) if house.back else s["rear_top"]
        pts = [v(x, house.y(house.glass_out(s["top_z"]) - 0.006, driver), s["top_z"] + 0.006) for x in [rear] + ([s["rear_top"] - 0.02] if s.get("rear_top") is not None else []) + [s["b"][1], s["b"][0], house.screen["top"][0]]
               if x <= rear or x > rear + 0.03]
        b = Builder()
        path = spline(pts, 40)
        b.sweep(path, moulding(0.030, 0.009), ups=[v(0, -side * 0.6, -0.8)] * len(path))
        out.append(b.finish("roof rail" + (" driver" if driver else " passenger"), "trim", SHELL, subdivide=0, texels=0.8))
        # Where the lining stands higher than the side glass, a trimmed band closes the gap over each door.
        rise = house.roof_base() - s["top_z"]
        if rise > 0.015:
            b = Builder()
            x_back = (house.back["top"][0] + 0.01) if house.back else s["rear_top"]
            x_front = house.screen["top"][0] - 0.01
            grid = []
            for r in range(4):
                z = s["top_z"] + rise * r / 3 - 0.002
                out_r = house.glass_out(s["top_z"]) - 0.008 - 0.012 * r / 3
                grid.append([v(lerp(x_back, x_front, c / 12), house.y(out_r, driver), z) for c in range(13)])
            b.loft(grid, closed=False, caps=False)
            out.append(b.finish("door header" + (" driver" if driver else " passenger"), "trim", SHELL, solidify=0.01, texels=0.6))
        # The pillar behind the front door: wide, with the seat belt's guide on it.
        b = Builder()
        bx = (s["b"][0] + s["b"][1]) / 2
        path, ups = [], []
        # From the floor, closing the gap between the doors' trim below the glass, up to the rail.
        for k in range(15):
            z = lerp(FLOOR_Z + 0.03, s["top_z"] + 0.01, k / 14)
            path.append(v(bx, house.y(house.glass_out(min(max(z, s["sill_z"]), s["top_z"])) - 0.014 - (0.04 if z < s["sill_z"] - 0.05 else 0.0), driver), z))
            ups.append(v(0, -side, 0))
        # Wide enough to close the space between the front door's back edge and what follows it.
        # Deeper in its crown, and narrowing a little from the waist to the roof as a moulded trim does.
        # A fastback's runs on nearly full width to the rail, meeting the trim over its quarter glass there.
        narrow = 0.95 if house.spec.get("c_pillar") == "fastback" else 0.72
        scales = [(lerp(1.0, narrow, max(0.0, (p.z - s["sill_z"]) / (s["top_z"] - s["sill_z"]))), 1.0) for p in path]
        b.sweep(path, moulding(max(0.085, (s["b"][0] - s["b"][1]) / 2 + 0.025), 0.027), ups=ups, scales=scales)
        out.append(b.finish("b pillar" + (" driver" if driver else " passenger"), "trim", SHELL, subdivide=1, texels=0.9))
    out += rear_pillars(house)
    # The rail over the windscreen.
    pts = [house.screen_at(u, 1.0) + v(-0.022, 0, -0.004) for u in (-1.0, -0.6, -0.2, 0.2, 0.6, 1.0)]
    b = Builder()
    path = spline(pts, 36)
    b.sweep(path, moulding(0.034, 0.010), ups=[v(-0.5, 0, -0.85)] * len(path))
    out.append(b.finish("header", "trim", SHELL, subdivide=0, texels=0.8))
    gap = max(house.roof_z(house.screen["top"][3] * u) - house.screen_at(u, 1.0).z for u in (-1.0, -0.5, 0.0, 0.5, 1.0))
    if gap > 0.015:
        b = Builder()
        grid = []
        for r in range(4):
            ring = []
            for c in range(17):
                u = lerp(-1.0, 1.0, c / 16)
                p = house.screen_at(u, 1.0)
                top = house.roof_z(house.screen["top"][3] * u)
                ring.append(v(p.x - 0.025, p.y, lerp(p.z, top, r / 3) - 0.002))
            grid.append(ring)
        b.loft(grid, closed=False, caps=False)
        out.append(b.finish("header band", "trim", SHELL, solidify=0.01, texels=0.6))
    return out


def trimmed_between(edge_a, edge_b, name, rows=10, cols=8, bulge=0.012, widest=0.0, ceiling=None, mid=0.0, subdivide=1, steady=False):
    """A trimmed panel spanning between two edges, each a function from 0 at its foot to 1 at its top giving a place,
    bowed in towards the cabin by bulge across its middle. Where the edges are nearer than widest, each is moved out
    to make it that wide, lapping over the glass beside it as a pillar's trim does; ceiling, where given, keeps every
    place under the roof's lining above it."""
    b = Builder()
    grid = []
    # Where the edges meet the panel would pinch to nothing and twist: it is widened along the way it runs where
    # its edges stand furthest apart.
    widest_row = max((edge_b(r / rows) - edge_a(r / rows) for r in range(rows + 1)), key=lambda d: d.length)
    way = widest_row.normalized()
    for r in range(rows + 1):
        t = r / rows
        a, c = edge_a(t), edge_b(t)
        span = (c - a).length
        if widest and span < widest * 1.5:
            # Blended in as the edges close, so the panel turns smoothly from its own way to the widest row's.
            # Steady, it stays in its own plane where its edges close rather than turning to the widest row's way, so a
            # pillar whose glass edges meet at its foot does not fan out over what lies below.
            k = 1.0 if steady and span > 0.005 else smooth(span / (widest * 1.5))
            own = (c - a) / span if span > 1e-6 else way
            direction = (own * k + way * (1 - k)).normalized()
            middle = (a + c) / 2
            reach = max(span, widest) / 2
            a, c = middle - direction * reach, middle + direction * reach
        row = []
        # Steady, the panel bends round the body's corner between its edges, from the first edge's x to the second's
        # side, as the trim follows the shell, instead of running straight across as a twisted sheet.
        corner = (a + c) / 2
        if steady:
            corner = corner.lerp(v(a.x, c.y, (a.z + c.z) / 2), 0.55)
        for k in range(cols + 1):
            u = k / cols
            p = a.lerp(corner, u).lerp(corner.lerp(c, u), u)
            # In towards the middle line and forward into the cabin, most at the panel's middle.
            # A full crown that rolls over at each edge, not a shallow arc.
            p = p + v(0.0, -math.copysign(1.0, p.y - mid) * bulge * math.sin(math.pi * u) ** 0.5, 0.0)
            row.append(v(p.x, p.y, min(p.z, ceiling(p))) if ceiling else p)
        grid.append(row)
    b.loft(grid, closed=False, caps=False)
    return b.finish(name, "trim", SHELL, solidify=0.012, subdivide=subdivide, texels=0.6)


def trimmed_corner(side_edge, back_edge, sign, name, side_width=0.08, back_width=0.07, rows=10):
    """The trim round a back corner where the side glass and the back window meet: an L in plan, lapping forward over
    the side glass and in over the back window, rounded at the corner."""
    b = Builder()
    grid = []
    for r in range(rows + 1):
        t = r / rows
        on_side, on_back = side_edge(t), back_edge(t)
        corner = v(min(on_side.x, on_back.x), on_side.y if abs(on_side.y) > abs(on_back.y) else on_back.y, (on_side.z + on_back.z) / 2)
        points = [on_side + v(side_width, 0, 0), on_side + v(side_width * 0.4, 0, 0), corner + v(0.014, -sign * 0.014, 0),
                  on_back + v(0, -sign * back_width * 0.4, 0), on_back + v(0, -sign * back_width, 0)]
        grid.append(spline(points, 10))
    b.loft(grid, closed=False, caps=False)
    return b.finish(name, "trim", SHELL, solidify=0.012, subdivide=1, texels=0.6)


def rear_pillars(house):
    """The trim behind the side glass: the pillar between the last side window and the back window (or the quarter
    window, and then the corner pillar behind that), and the rail over the back window."""
    s, back = house.side, house.back
    if back is None:
        return []
    parts = []
    # A fastback's trim runs up past the glass's top, the ceiling keeping it under the lining, to tuck under the rails.
    low, high = s["sill_z"] - 0.06, s["top_z"] + (0.004 if house.spec.get("c_pillar") == "fastback" else -0.006)
    for driver in (True, False):
        sign = 1 if driver else -1

        def on_side(x_fn, sill_half=None):
            def edge(t):
                z = lerp(low, high, t)
                return v(x_fn(min(max(z, s["sill_z"]), s["top_z"])), house.y(house.glass_out(min(max(z, s["sill_z"]), s["top_z"]), sill_half) - 0.012, driver), z)
            return edge

        def back_edge(t):
            p = house.back_at(sign * 1.0, t)
            return p + v(0.012, -sign * 0.004, 0.0)

        def level_back_edge(t):
            # Where the back window's foot stands well above the side glass's, the rows below it hold to its foot at the
            # side's height, so the trim runs level across there and does not twist; above it the edge runs on to its top.
            z = lerp(low, high, t)
            foot = house.back_at(sign * 1.0, 0.0)
            if z < foot.z:
                return v(foot.x, foot.y, z) + v(0.012, -sign * 0.004, 0.0)
            return house.back_at(sign * 1.0, (z - foot.z) / (high - foot.z)) + v(0.012, -sign * 0.004, 0.0)
        # Where the last side window ends: the rear door's, or the front door's where there is no rear door.
        if s.get("rear_foot") is not None:
            last = on_side(house.c_pillar_x)
        else:
            last = on_side(lambda z: s["b"][1])
        name = " driver" if driver else " passenger"
        if "quarter" in house.spec:
            q = house.spec["quarter"]
            parts.append(trimmed_between(on_side(lambda z: house.quarter_x(z, "front"), q["sill_half"]), last, "c pillar" + name, bulge=0.006, widest=0.10, mid=house.mid))
            parts.append(trimmed_corner(on_side(lambda z: house.quarter_x(z, "rear"), q["sill_half"]), back_edge, sign, "d pillar" + name, side_width=0.12,
                                        back_width=0.10))
        elif house.spec.get("c_pillar") == "corner":
            # A hard top's moulded corner: the same L at every height, lapping the side glass and the back window.
            parts.append(trimmed_corner(last, level_back_edge, sign, "c pillar" + name, side_width=0.09, back_width=0.08))
        elif s.get("rear_foot") is not None or back["foot"][0] > s["b"][1] - 0.9:
            # Further back than that it is no pillar but a van's box, whose walls are built with it.
            steady = s.get("rear_foot") is None or house.spec.get("c_pillar") == "fastback"
            level = not steady and house.back_at(sign * 1.0, 0.0).z - low > 0.18
            parts.append(trimmed_between(level_back_edge if level else back_edge, last, "c pillar" + name, bulge=0.02, widest=0.18, mid=house.mid,
                                         steady=steady,
                                         ceiling=lambda p: house.roof_z(abs(p.y - house.mid)) - 0.008))
    # The rail over the back window, and a band up to the lining where the roof stands above it.
    pts = [house.back_at(u, 1.0) + v(0.022, 0, -0.016) for u in (-1.0, -0.6, -0.2, 0.2, 0.6, 1.0)]
    pts = [v(p.x, p.y, min(p.z, house.roof_z(abs(p.y - house.mid)) - 0.016)) for p in pts]
    b = Builder()
    path = spline(pts, 36)
    b.sweep(path, moulding(0.034, 0.010), ups=[v(0.5, 0, -0.85)] * len(path))
    parts.append(b.finish("back header", "trim", SHELL, subdivide=0, texels=0.8))
    # Where the lining's dome stands above the rail, a trimmed band closes the gap between them.
    gap = max(house.roof_z(back["top"][3] * u) - house.back_at(u, 1.0).z for u in (-1.0, -0.5, 0.0, 0.5, 1.0))
    if gap > 0.012:
        b = Builder()
        grid = []
        # A cove: upright off the rail and turning forward into the lining, as the cloth is drawn down over its bow.
        for r in range(7):
            angle = math.pi / 2 * r / 6
            ring = []
            for c in range(17):
                u = lerp(-1.0, 1.0, c / 16)
                p = house.back_at(u, 1.0)
                top = house.roof_z(back["top"][3] * abs(u))
                low = min(p.z, top)
                rise = top - low
                ring.append(v(p.x + 0.025 + rise * (1.0 - math.cos(angle)), p.y, low + rise * math.sin(angle) - 0.002))
            grid.append(ring)
        b.loft(grid, closed=False, caps=False)
        parts.append(b.finish("back header band", "trim", SHELL, solidify=0.01, texels=0.6))
    return parts

