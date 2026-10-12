"""Upholstered pieces: a cushion, a seat back, a head restraint. Each is a box cage of quads whose seams are
creased edge loops sunk into the stuffing, rounded by subdivision, as upholstery is modelled by hand: the
panels between the seams pillow up and the seams stay sharp."""

import math

import bmesh
import bpy
from mathutils import Matrix, Vector

from .kit import PARTS, SHELL, Builder, apply_all, levels, rounded_rect


def cushion(name, material, origin, across, along, up, width, length, height, channels=6, margin=0.05, roll=0.035, crown=0.012,
            seam_depth=0.007, along_seams=(), bolster=0.0, texels=1.0, group=SHELL, subdivide=2, cross_channels=False, top_taper=0.0, narrow=0.0, dishes=()):
    """A cushion: width across, length along, height up from its base, its top pillowed between channels running
    along it inside a plain border margin wide, its edges rolled over a radius about roll.

    origin is the middle of its base's near edge (along = 0); across, along and up are its axes.
    along_seams are further seams across the cushion at those shares of its length (a seam behind a front roll).
    bolster raises the border above the insert by that much. cross_channels runs the channels across instead.
    top_taper thins it by that share over the last 40% of its length: a seat back drawn in to a roll at its top.
    narrow draws its sides in by that share over the far half of its length: a bucket's back towards the shoulders.
    dishes are places across it (shares of its width) where it is sat down into, a seat's worth wide.
    """
    across, along, up = Vector(across).normalized(), Vector(along).normalized(), Vector(up).normalized()
    origin = Vector(origin)
    # The lines of the cage across the cushion: the edge, the roll, the border, the channel seams, and back.
    xs = [0.0, roll * 0.5, margin]
    inner = width - 2 * margin
    for k in range(1, channels):
        xs.append(margin + inner * k / channels)
    xs += [width - margin, width - roll * 0.5, width]
    # Between each pair of seams, a line down the middle of the panel to let it pillow.
    full = []
    for a, b in zip(xs, xs[1:]):
        full.append(a)
        if b - a > 0.02:
            full.append((a + b) / 2)
    full.append(xs[-1])
    xs = full
    seam_x = set(round(margin + inner * k / channels, 6) for k in range(0, channels + 1))
    ys = [0.0, roll * 0.5, roll]
    seam_y = set()
    for share in along_seams:
        y = length * share
        seam_y.add(round(y, 6))
    steps = 4
    for k in range(1, steps):
        ys.append(roll + (length - 2 * roll) * k / steps)
    ys += [length - roll, length - roll * 0.5, length]
    for y in sorted(seam_y):
        ys.append(y)
    ys = sorted(set(round(y, 6) for y in ys))

    bm = bmesh.new()
    crease = bm.edges.layers.float.new("crease_edge")

    def thickness(y):
        t = max(0.0, min(1.0, (y / length - 0.6) / 0.4))
        return 1.0 - top_taper * t * t * (3.0 - 2.0 * t)

    def top_height(x, y):
        u = x / width
        w = y / length
        h = height + crown * math.sin(math.pi * u) * (1.0 - abs(2.0 * w - 1.0) ** 6)
        # Each channel pillows up between its seams, along the length the seams run.
        if margin < x < width - margin and band_lo < y < band_hi and channels > 1:
            k = (x - margin) / (inner / channels)
            ends = max(0.0, min(1.0, (band_hi - y) / 0.06, (y - band_lo) / 0.06))
            h += 0.009 * math.sin(math.pi * (k - math.floor(k))) * ends
        in_border = x < margin or x > width - margin
        if in_border:
            h += bolster
        for c in dishes:
            h -= 0.022 * math.exp(-((u - c) / 0.14) ** 2) * math.sin(math.pi * min(max(w, 0.0), 1.0))
        return h * thickness(y)

    # The channels' seams run between the bands a seam across marks off: one under half way along is the band at the
    # near edge (a cushion's front roll), one over half way the band at the far edge (a back's head band). Run on
    # into the rolled edge they notch it into a row of teeth.
    band_lo = max([length * a for a in along_seams if a < 0.5], default=roll)
    band_hi = min([length * a for a in along_seams if a >= 0.5], default=length - roll)
    nx, ny = len(xs), len(ys)
    top = [[None] * nx for _ in range(ny)]
    for j, y in enumerate(ys):
        for i, x in enumerate(xs):
            z = top_height(x, y)
            on_seam = (round(x, 6) in seam_x and band_lo - 1e-6 <= y <= band_hi + 1e-6) or (round(y, 6) in seam_y and margin <= x <= width - margin)
            if on_seam:
                if round(y, 6) in seam_y and margin <= x <= width - margin:
                    z -= seam_depth
                else:
                    fade = max(0.0, min(1.0, (band_hi - y) / 0.035, (y - band_lo) / 0.035))
                    z -= seam_depth * (fade if band_lo < band_hi else 1.0)
            # The edge rows roll down to where the side begins.
            edge = min(i, nx - 1 - i, j, ny - 1 - j)
            if edge == 0:
                z -= roll * 0.55
            elif edge == 1:
                z -= roll * 0.12
            t = max(0.0, min(1.0, (y / length - 0.45) / 0.55))
            squeeze = 1.0 - narrow * t * t * (3.0 - 2.0 * t)
            top[j][i] = bm.verts.new(origin + across * ((x - width / 2) * squeeze) + along * y + up * z)
    faces_top = []
    for j in range(ny - 1):
        for i in range(nx - 1):
            faces_top.append(bm.faces.new((top[j][i], top[j][i + 1], top[j + 1][i + 1], top[j + 1][i])))
    # The sides: from the top's outer loop down to the base, with one loop near the top to hold the roll.
    loop = [top[0][i] for i in range(nx)] + [top[j][nx - 1] for j in range(1, ny)] + [top[ny - 1][i] for i in range(nx - 2, -1, -1)] + [top[j][0] for j in range(ny - 2, 0, -1)]
    rings = [loop]
    for z in (height - roll * 1.1, roll * 0.3, 0.0):
        ring = []
        for v in loop:
            p = v.co - origin
            x, y = p.dot(across) + width / 2, p.dot(along)
            ring.append(bm.verts.new(origin + across * (x - width / 2) + along * y + up * (z * thickness(y) if z > roll else z)))
        rings.append(ring)
    for a, b in zip(rings, rings[1:]):
        n = len(a)
        for k in range(n):
            bm.faces.new((a[k], b[k], b[(k + 1) % n], a[(k + 1) % n]))
    # The base, closed with a fan of quads to its middle loop.
    base = rings[-1]
    middle = bm.verts.new(origin + along * (length / 2))
    for k in range(len(base)):
        try:
            bm.faces.new((base[(k + 1) % len(base)], base[k], middle))
        except ValueError:
            pass
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    # Seams: the creased loops along the channels and across at the along seams, and the border round the insert.
    for e in bm.edges:
        a, b = e.verts
        pa, pb = a.co - origin, b.co - origin
        xa, xb = round(pa.dot(across) + width / 2, 6), round(pb.dot(across) + width / 2, 6)
        ya, yb = round(pa.dot(along), 6), round(pb.dot(along), 6)
        # Each channel's seam creased only where it is sunk to its full depth, short of the seams across it.
        if xa == xb and xa in seam_x and a in top_set(top) and b in top_set(top) and band_lo + 0.035 - 1e-6 <= min(ya, yb) and max(ya, yb) <= band_hi - 0.035 + 1e-6:
            e[crease] = 1.0
        if ya == yb and ya in seam_y and a in top_set(top) and b in top_set(top) and margin <= xa <= width - margin:
            e[crease] = 1.0
    _TOP_CACHE.clear()
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    bm.free()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    m = obj.modifiers.new("subdivide", "SUBSURF")
    m.levels = levels(subdivide)
    m.render_levels = levels(subdivide)
    apply_all(obj)
    for p in obj.data.polygons:
        p.use_smooth = True
    obj["material"] = material
    obj["group"] = group
    obj["texels"] = texels
    PARTS.append(obj)
    return obj


_TOP_CACHE = {}


def top_set(top):
    key = id(top)
    if key not in _TOP_CACHE:
        _TOP_CACHE[key] = set(v for row in top for v in row)
    return _TOP_CACHE[key]


def block(name, material, centre, across, along, up, width, length, height, subdivide=2, texels=1.0, group=SHELL, taper=0.0):
    """A rounded block, a cage of three quads each way smoothed: a head restraint, an armrest's pad. taper narrows its
    top by that share."""
    across, along, up = Vector(across).normalized(), Vector(along).normalized(), Vector(up).normalized()
    centre = Vector(centre)
    bm = bmesh.new()
    n = 3
    verts = {}
    for i in range(n + 1):
        for j in range(n + 1):
            for k in range(n + 1):
                if 0 < i < n and 0 < j < n and 0 < k < n:
                    continue
                u, v, w = i / n - 0.5, j / n - 0.5, k / n - 0.5
                squeeze = 1.0 - taper * (w + 0.5)
                verts[(i, j, k)] = bm.verts.new(centre + across * (u * width * squeeze) + along * (v * length) + up * (w * height))
    for (i, j, k), _ in list(verts.items()):
        for axis in range(3):
            if (i, j, k)[axis] not in (0, n):
                continue
            a, b = [d for d in range(3) if d != axis]
            idx = [i, j, k]
            if idx[a] < n and idx[b] < n:
                c = []
                for da, db in ((0, 0), (1, 0), (1, 1), (0, 1)):
                    q = list(idx)
                    q[a] += da
                    q[b] += db
                    c.append(verts[tuple(q)])
                try:
                    bm.faces.new(c)
                except ValueError:
                    pass
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    bm.free()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    m = obj.modifiers.new("subdivide", "SUBSURF")
    m.levels = levels(subdivide)
    m.render_levels = levels(subdivide)
    apply_all(obj)
    for p in obj.data.polygons:
        p.use_smooth = True
    obj["material"] = material
    obj["group"] = group
    obj["texels"] = texels
    PARTS.append(obj)
    return obj


def back_panel(name, material, origin, up_back, facing, width, height, pocket=False, texels=1.0, narrow=0.0):
    """The panel sewn over a seat back's rear, pillowed a little inside a border, and a map pocket across its lower
    half where pocket is asked for. origin is the middle of the back's foot on its rear face."""
    parts = [cushion(name + " back panel", material, origin, (0, 1, 0), up_back, -Vector(facing), width * 0.97, height * 0.98, 0.026, channels=1,
                     margin=0.05, roll=0.02, crown=0.03, seam_depth=0.010, along_seams=(0.58,), texels=texels, subdivide=1, narrow=narrow)]
    if pocket:
        up_back, facing = Vector(up_back).normalized(), Vector(facing).normalized()
        b = Builder()
        b.slab(rounded_rect(width * 0.70, height * 0.30, 0.02), origin + up_back * (height * 0.27) - facing * 0.024, (0, -1, 0), up_back, 0.010)
        parts.append(b.finish(name + " map pocket", material, SHELL, bevel=0.004, texels=texels))
    return parts
