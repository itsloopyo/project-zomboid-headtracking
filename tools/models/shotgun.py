"""Builds the mod's own model of a pump action shotgun, or of the same gun sawn off, and packs it for the jar.

Run headless with Blender 4.4, from the repository root:

    blender --background --factory-startup --python tools/models/shotgun.py -- PICTURES [--sawn-off] [--renders DIR] [--raw DIR [VIEW ...]] [--no-pack] [--cpu] [--mesh]

PICTURES is the folder tools/models/shotgun_textures.py wrote. The model is made here from
nothing: a twelve bore field gun with a ventilated rib, a walnut stock and fore-end and a
blued receiver, of no maker's pattern. With --sawn-off it is that gun cut down: its barrel,
rib and magazine short, with the bead put back at the new muzzle, and its stock sawn off
behind the wrist and shaped into a grip for the hand. It writes NAME.mesh.gz, the
triangles, under src/main/resources/models, and under src/textures/models, for
tools/textures/compress.py to pack for the jar, where NAME is shotgun or sawnoff:

    NAME.colour.gz       2048 x 2048 RGBA: the colour of the surface, and in alpha how much
                         of the light all round reaches it past the rest of the gun
    NAME.surface.gz      2048 x 2048 RGBA: the x and y of the surface's normal in the
                         triangle's own frame, how rough it is, and how much of a metal

--raw renders the parts as they are made, unbaked, for looking at the shape.

The model is in metres. Its barrel runs along +y and its top is +z, as the game's own
weapons are made, and its origin is in the hand that holds its grip. Three parts of it
move: the fore-end with its action bars slides back along y to work the action, and the
bolt goes back with it and uncovers the port. With them is a fired shell, about its own
middle and lying along y, for the mod to throw out of the port. The stock is a part of its
own too: with the sights up it lies under the eye, and the mod leaves it out. The sawn-off's
grip is in that part.

The mesh file is gzip over this, all little-endian:

    4 bytes   the ASCII letters CUWM
    uint32    format version, 4
    float32   x 3: where the muzzle is
    float32   x 3: the middle of the fore-end, which the hand that works it holds
    float32   x 3: the middle of the port a spent shell leaves by, on the gun's right side
    float32   how far back the fore-end and the bolt travel
    float32   how far over the middle of the bore the eye is with the sights lined up
    float32   how far behind the grip the eye is then, over the comb of the stock
    uint32    number of vertices
    uint32    x 5: how many indices the fixed part, the fore-end, the bolt, the shell and the stock have, in that order
    per vertex:
      float32 x 3   position
      int16 x 3     unit normal, 32767 for 1
      int16 x 3     unit tangent along the texture's u
      int16         1 or -1: which way the texture's v runs round the normal from the tangent
      uint16 x 2    place in the texture, 65535 for 1, v from the bottom
    uint32 per index
"""

import gzip
import math
import os
import struct
import sys

import bmesh
import bpy
import numpy
from mathutils import Matrix, Vector

HERE = os.path.dirname(os.path.abspath(__file__))
OUTPUT = os.path.join(HERE, "..", "..", "src", "main", "resources", "models")
TEXTURES = os.path.join(HERE, "..", "..", "src", "textures", "models")
TEXTURE = 2048

SAWN_OFF = "--sawn-off" in sys.argv
NAME = "sawnoff" if SAWN_OFF else "shotgun"

# Laid out with the bore along y through x = 0, z = 0 and the trigger at y = 0. GRIP is
# where the hand that holds the grip is, which the finished model has as its origin: so far
# under the bore that the muzzle is as far above the line ahead of the hand as the muzzle
# of the game's own shotgun is, which is how the mod points a weapon. The game's sawn-off
# has its muzzle 0.3249 ahead of the origin and 0.0267 over it: cut where its muzzle is on
# the same line from the hand, the gun is drawn at 0.5057 of the game's units to a metre,
# where the whole one is drawn at 0.4957.
FULL_MUZZLE_Y = 0.780
MUZZLE_Y = 0.5225 if SAWN_OFF else FULL_MUZZLE_Y
GRIP = Vector((0.0, -0.120, -0.0528))
RECEIVER_FRONT, RECEIVER_REAR, RECEIVER_HALF = 0.105, -0.085, 0.0205
RECEIVER_TOP, RECEIVER_BOTTOM = 0.023, -0.047
TUBE_Z, TUBE_R = -0.0305, 0.0125
# Where the magazine tube ends and its cap begins. The sawn-off's cap comes to just short of its muzzle.
TUBE_END = MUZZLE_Y - 0.0350 if SAWN_OFF else 0.6005
FORE_REAR, FORE_FRONT = 0.225, 0.420
TRAVEL = 0.094
BUTT = -0.352
FIXED, PUMP, BOLT, SHELL, BUTT_END = 0, 1, 2, 3, 4
# The shell is made well clear of the gun, so that neither shades the other, and is packed about its own middle.
SHELL_AT = Vector((0.0, 0.30, 0.40))
PORT = Vector((0.0190, 0.047, -0.002))
# Over the back of the rib, by enough to see the bead at its far end.
SIGHT_LINE = 0.0275
# A little behind where a cheek on the comb would have it: from there the back of the receiver is a hand's width in the
# picture and the rib runs away from it, where from over the comb it is a wall across the foot of the picture.
EYE_BEHIND_GRIP = 0.26


def barrel_radius(y):
    if y < 0.19:
        return 0.0135
    if y < 0.26:
        return 0.0135 + (0.0118 - 0.0135) * (y - 0.19) / 0.07
    return 0.0118 + (0.0108 - 0.0118) * (y - 0.26) / (FULL_MUZZLE_Y - 0.26)


def rib_top(y):
    return 0.0215 + (0.0183 - 0.0215) * (y - 0.110) / (0.778 - 0.110)


def smooth(t):
    t = min(max(t, 0.0), 1.0)
    return t * t * (3.0 - 2.0 * t)


def along(keys, count):
    """Stations through a list of keys, each a tuple of numbers, run smoothly from one to the next."""
    out = []
    last = len(keys) - 1
    for n in range(count):
        at = n / (count - 1) * last
        i = min(int(at), last - 1)
        t = at - i
        p0, p1, p2, p3 = keys[max(i - 1, 0)], keys[i], keys[i + 1], keys[min(i + 2, last)]
        out.append(tuple(0.5 * (2 * b + (c - a) * t + (2 * a - 5 * b + 4 * c - d) * t * t + (3 * b - a - 3 * c + d) * t * t * t)
                         for a, b, c, d in zip(p0, p1, p2, p3)))
    return out


def cuboid(bm, x0, x1, y0, y1, z0, z1):
    verts = [bm.verts.new((x, y, z)) for z in (z0, z1) for x, y in ((x0, y0), (x1, y0), (x1, y1), (x0, y1))]
    for a, b, c, d in ((3, 2, 1, 0), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)):
        bm.faces.new((verts[a], verts[b], verts[c], verts[d]))
    return verts


def lathe(bm, profile, to, segments=32):
    """Turns a profile of (radius, height) round its own upright, then moves the whole by a matrix. A radius of 0 is a point on the axis."""
    rings = []
    for radius, height in profile:
        if radius <= 1e-7:
            rings.append([bm.verts.new(to @ Vector((0.0, 0.0, height)))])
        else:
            rings.append([bm.verts.new(to @ Vector((radius * math.cos(2 * math.pi * i / segments), radius * math.sin(2 * math.pi * i / segments), height)))
                          for i in range(segments)])
    for a, b in zip(rings, rings[1:]):
        for i in range(segments):
            j = (i + 1) % segments
            if len(a) == 1 and len(b) == 1:
                continue
            if len(a) == 1:
                bm.faces.new((a[0], b[j], b[i]))
            elif len(b) == 1:
                bm.faces.new((a[i], a[j], b[0]))
            else:
                bm.faces.new((a[i], a[j], b[j], b[i]))
    for ring, turn in ((rings[0], -1), (rings[-1], 1)):
        if len(ring) > 1:
            bm.faces.new(ring[::turn])


def along_y(x=0.0, y=0.0, z=0.0):
    """Stands a turned part's upright along the barrel."""
    return Matrix.Translation((x, y, z)) @ Matrix.Rotation(math.radians(-90), 4, "X")


def along_x(x=0.0, y=0.0, z=0.0):
    return Matrix.Translation((x, y, z)) @ Matrix.Rotation(math.radians(90), 4, "Y")


def loft(bm, rings, caps=True):
    """Skins a run of closed rings, each a list of points, all of one length."""
    made = [[bm.verts.new(point) for point in ring] for ring in rings]
    count = len(made[0])
    for a, b in zip(made, made[1:]):
        for i in range(count):
            j = (i + 1) % count
            bm.faces.new((a[i], a[j], b[j], b[i]))
    if caps:
        ends = [bm.faces.new(made[0][::-1]), bm.faces.new(made[-1])]
        bmesh.ops.triangulate(bm, faces=ends, ngon_method="EAR_CLIP")
    return made


def sweep(bm, path, half_wide, thick):
    """Draws a flat bar along a path of (y, z) in the plane the gun is symmetrical about. thick is a number or one for each point."""
    rings = []
    for n, (y, z) in enumerate(path):
        before, after = path[max(n - 1, 0)], path[min(n + 1, len(path) - 1)]
        way = Vector((after[0] - before[0], after[1] - before[1])).normalized()
        out = Vector((-way.y, way.x))
        half = (thick[n] if isinstance(thick, (list, tuple)) else thick) / 2
        rings.append([(sx * half_wide, y + sy * out.x * half, z + sy * out.y * half) for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1))])
    loft(bm, rings)


PARTS = []
PART_NAMES = []


def take(bm, name, material, group=FIXED, bevel=0.0, segments=2, sharp=30.0, subdivide=0, cut=(), texels=1.0):
    """Makes an object of a part: its hard edges rounded off, its faces smooth between them."""
    bmesh.ops.remove_doubles(bm, verts=bm.verts[:], dist=1e-6)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    bm.free()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    for cutter in cut:
        hole = obj.modifiers.new("cut", "BOOLEAN")
        hole.operation = "DIFFERENCE"
        hole.solver = "EXACT"
        hole.object = cutter
    if subdivide:
        smoothed = obj.modifiers.new("subdivision", "SUBSURF")
        smoothed.levels = subdivide
    if bevel:
        rounded = obj.modifiers.new("bevel", "BEVEL")
        rounded.width = bevel
        rounded.segments = segments
        rounded.limit_method = "ANGLE"
        rounded.angle_limit = math.radians(sharp)
        rounded.harden_normals = False
    done = bpy.data.meshes.new_from_object(obj.evaluated_get(bpy.context.evaluated_depsgraph_get()))
    obj.modifiers.clear()
    obj.data = done
    bpy.data.meshes.remove(mesh)
    done.shade_smooth()
    done.set_sharp_from_angle(angle=math.radians(sharp))
    flat = obj.modifiers.new("normals", "WEIGHTED_NORMAL")
    flat.keep_sharp = True
    flat.weight = 80
    done.materials.append(material_of(material))
    obj["group"] = group
    obj["texels"] = texels
    PARTS.append(obj)
    return obj


def cutter(bm, bevel=0.0):
    """Something to cut out of a part: an object that is not itself part of the gun."""
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    mesh = bpy.data.meshes.new("cutter")
    bm.to_mesh(mesh)
    bm.free()
    obj = bpy.data.objects.new("cutter", mesh)
    bpy.context.scene.collection.objects.link(obj)
    if bevel:
        rounded = obj.modifiers.new("bevel", "BEVEL")
        rounded.width = bevel
        rounded.segments = 4
        rounded.limit_method = "NONE"
        rounded.affect = "EDGES"
        mesh.attributes.new("bevel_weight_edge", "FLOAT", "EDGE")
        rounded.limit_method = "WEIGHT"
        for edge in mesh.edges:
            a, b = mesh.vertices[edge.vertices[0]].co, mesh.vertices[edge.vertices[1]].co
            # Only the edges that run across the gun: the corners of a port as it is seen from the side.
            mesh.attributes["bevel_weight_edge"].data[edge.index].value = 1.0 if abs(a.x - b.x) > 1e-5 else 0.0
    obj.hide_render = True
    obj.display_type = "WIRE"
    return obj


def barrel():
    bm = bmesh.new()
    profile = [(0.0, 0.098), (0.0135, 0.098), (0.0135, 0.19), (0.0118, 0.26)]
    profile += [(barrel_radius(y), y) for y in (0.36, 0.46, 0.56, 0.66, 0.76) if y < MUZZLE_Y - 0.01]
    end = round(barrel_radius(MUZZLE_Y), 5)
    profile += [(end, MUZZLE_Y - 0.0012), (round(end - 0.0007, 5), MUZZLE_Y), (0.00955, MUZZLE_Y), (0.0092, MUZZLE_Y - 0.0006), (0.0092, MUZZLE_Y - 0.09), (0.0, MUZZLE_Y - 0.09)]
    lathe(bm, profile, along_y(), segments=56)
    take(bm, "barrel", "blued", sharp=25.0)
    collar = bmesh.new()
    lathe(collar, [(0.0, RECEIVER_FRONT - 0.001), (0.0158, RECEIVER_FRONT - 0.001), (0.0158, RECEIVER_FRONT + 0.0045), (0.0150, RECEIVER_FRONT + 0.0060),
                   (0.0, RECEIVER_FRONT + 0.0060)], along_y(), segments=56)
    take(collar, "barrel collar", "blued", sharp=25.0)

    rib = bmesh.new()
    rear, front, half = 0.110, MUZZLE_Y - 0.002, 0.0038
    stations = [rear + (front - rear) * n / 12 for n in range(13)]
    loft(rib, [[(sx * half, y, rib_top(y) - (0.0021 if low else 0.0)) for sx, low in ((-1, True), (1, True), (1, False), (-1, False))] for y in stations])
    take(rib, "rib", "rib", bevel=0.0003, segments=1)

    posts = bmesh.new()
    y = 0.132
    while y < MUZZLE_Y - 0.01:
        cuboid(posts, -0.0019, 0.0019, y - 0.008, y + 0.008, barrel_radius(y) - 0.0015, rib_top(y) - 0.0018)
        y += 0.058
    take(posts, "rib posts", "blued", bevel=0.0004, segments=1)

    beads = bmesh.new()
    bead = MUZZLE_Y - 0.0085
    lathe(beads, [(0.0, 0.0), (0.0011, 0.0), (0.0011, 0.0012), (0.0019, 0.0020), (0.0021, 0.0032), (0.0015, 0.0046), (0.0, 0.0051)],
          Matrix.Translation((0.0, bead, rib_top(bead) - 0.0003)), segments=20)
    take(beads, "front bead", "brass", sharp=50.0)
    if SAWN_OFF:
        return
    middle = bmesh.new()
    lathe(middle, [(0.0, 0.0), (0.0008, 0.0), (0.0011, 0.0008), (0.0008, 0.0018), (0.0, 0.0021)], Matrix.Translation((0.0, 0.44, rib_top(0.44) - 0.0002)), segments=14)
    take(middle, "middle bead", "bright", sharp=50.0)


def magazine():
    tube = bmesh.new()
    stations = [y for y in (0.098, 0.2, 0.3, 0.35, 0.45, 0.55) if y < TUBE_END - 0.01] + [TUBE_END]
    lathe(tube, [(0.0, 0.098)] + [(TUBE_R, y) for y in stations] + [(0.0, TUBE_END)], along_y(z=TUBE_Z), segments=48)
    take(tube, "magazine tube", "blued")

    cap = bmesh.new()
    lathe(cap, [(radius, TUBE_END + y) for radius, y in ((0.0, -0.0010), (0.0139, -0.0010), (0.0143, 0.0), (0.0143, 0.0180), (0.0138, 0.0195), (0.0132, 0.0210),
                                                         (0.0132, 0.0235), (0.0118, 0.0285), (0.0085, 0.0320), (0.004, 0.0335), (0.0, 0.0337))],
          along_y(z=TUBE_Z), segments=48)
    take(cap, "magazine cap", "knurled", sharp=28.0)

    stud = bmesh.new()
    lathe(stud, [(0.0, 0.0), (0.0042, 0.0), (0.0042, 0.002), (0.0028, 0.003), (0.0028, 0.0075), (0.0046, 0.0085), (0.0046, 0.0125), (0.003, 0.014), (0.0, 0.014)],
          Matrix.Translation((0.0, TUBE_END + 0.0095, TUBE_Z - 0.0135)) @ Matrix.Rotation(math.radians(180), 4, "X"), segments=20)
    take(stud, "front stud", "blued", sharp=35.0)

    # The ring on the barrel that the magazine cap is screwed up against.
    ring = bmesh.new()
    front, back = TUBE_END - 0.0015, TUBE_END - 0.0195
    lathe(ring, [(0.0, back), (0.0156, back), (0.0160, back + 0.0006), (0.0160, front - 0.0006), (0.0156, front), (0.0, front)], along_y(z=TUBE_Z), segments=48)
    take(ring, "barrel ring", "blued", sharp=28.0)
    web = bmesh.new()
    rings = []
    for y in (back, front):
        rings.append([(-0.0078, y, TUBE_Z + 0.012), (0.0078, y, TUBE_Z + 0.012), (0.0056, y, -0.004), (-0.0056, y, -0.004)])
    loft(web, rings)
    take(web, "barrel ring web", "blued", bevel=0.0006)


def receiver_ring(y):
    slope = 1.0 - (1.0 - 0.743) * smooth((-0.045 - y) / 0.04)
    half = RECEIVER_HALF
    right = [(half - 0.0035, RECEIVER_BOTTOM), (half, RECEIVER_BOTTOM + 0.0035), (half, -0.012)]
    right += [(half * math.cos(math.radians(a)), 0.0025 + half * math.sin(math.radians(a))) for a in range(0, 90, 6)]
    points = right + [(0.0, RECEIVER_TOP)] + [(-x, z) for x, z in reversed(right)]
    return [(x, y, z if z <= -0.012 else -0.012 + (z + 0.012) * slope) for x, z in points]


def receiver():
    port = bmesh.new()
    cuboid(port, 0.006, 0.03, 0.010, 0.084, -0.0115, 0.0072)
    port_cut = cutter(port, bevel=0.0035)
    tunnel = bmesh.new()
    cuboid(tunnel, -0.0128, 0.0172, -0.040, 0.0845, -0.0118, 0.0075)
    tunnel_cut = cutter(tunnel)
    loading = bmesh.new()
    cuboid(loading, -0.0112, 0.0112, 0.034, 0.098, -0.06, -0.022)
    loading_cut = cutter(loading, bevel=0.0)

    body = bmesh.new()
    stations = [RECEIVER_REAR, -0.080, -0.074, -0.067, -0.060, -0.053, -0.045, -0.02, 0.01, 0.04, 0.07, RECEIVER_FRONT]
    loft(body, [receiver_ring(y) for y in stations])
    take(body, "receiver", "receiver", bevel=0.0005, segments=2, sharp=40.0, cut=(tunnel_cut, port_cut, loading_cut), texels=1.25)
    for used in (port_cut, tunnel_cut, loading_cut):
        bpy.data.objects.remove(used)

    bolt = bmesh.new()
    cuboid(bolt, -0.0122, 0.0165, 0.0105, 0.0838, -0.0110, 0.0067)
    take(bolt, "bolt", "bright", group=BOLT, bevel=0.0012, segments=3)
    extractor = bmesh.new()
    cuboid(extractor, 0.0160, 0.0171, 0.056, 0.0838, -0.0042, 0.0012)
    cuboid(extractor, 0.0160, 0.0169, 0.014, 0.050, -0.0085, -0.0070)
    take(extractor, "extractor", "blued", group=BOLT, bevel=0.0003, segments=1)

    # The carrier that lifts a shell from the magazine closes the port underneath.
    carrier = bmesh.new()
    cuboid(carrier, -0.0106, 0.0106, 0.036, 0.096, -0.0455, -0.0440)
    cuboid(carrier, -0.0030, 0.0030, 0.050, 0.090, -0.0462, -0.0450)
    take(carrier, "carrier", "bright", bevel=0.0005, segments=1)

    pins = bmesh.new()
    for y, z in ((-0.058, -0.027), (0.014, -0.034)):
        lathe(pins, [(0.0, -0.0208), (0.0024, -0.0208), (0.0028, -0.0204), (0.0028, 0.0204), (0.0024, 0.0208), (0.0, 0.0208)], along_x(0.0, y, z), segments=20)
    take(pins, "pins", "blued", sharp=30.0)


def trigger_group():
    plate = bmesh.new()
    cuboid(plate, -0.0100, 0.0100, -0.080, 0.0325, -0.0493, -0.045)
    take(plate, "trigger plate", "black", bevel=0.0012, segments=3)
    housing = bmesh.new()
    cuboid(housing, -0.0082, 0.0082, -0.0535, -0.0345, -0.0635, -0.046)
    take(housing, "safety housing", "black", bevel=0.0028, segments=4)

    guard = bmesh.new()
    path = along([(0.0315, -0.0470), (0.0345, -0.0560), (0.0320, -0.0680), (0.0230, -0.0790), (0.0080, -0.0845), (-0.0120, -0.0850), (-0.0300, -0.0815),
                  (-0.0420, -0.0730), (-0.0470, -0.0620), (-0.0480, -0.0500)], 44)
    sweep(guard, path, 0.0068, [0.0034 + 0.0016 * abs(n / 43 - 0.5) * 2 for n in range(44)])
    take(guard, "trigger guard", "black", bevel=0.0011, segments=3, sharp=50.0)

    trigger = bmesh.new()
    path = along([(0.0060, -0.0455), (0.0055, -0.0540), (0.0030, -0.0620), (-0.0005, -0.0690), (-0.0015, -0.0745), (0.0005, -0.0775)], 20)
    sweep(trigger, path, 0.0034, [0.0062 - 0.0030 * n / 19 for n in range(20)])
    take(trigger, "trigger", "blued", bevel=0.0010, segments=3, sharp=50.0)

    safety = bmesh.new()
    lathe(safety, [(0.0, -0.0108), (0.0030, -0.0108), (0.0035, -0.0102), (0.0035, 0.0118), (0.0030, 0.0124), (0.0, 0.0124)], along_x(0.0, -0.043, -0.0575), segments=24)
    take(safety, "safety", "blued", sharp=30.0)

    release = bmesh.new()
    cuboid(release, -0.0118, -0.0098, 0.0335, 0.0455, -0.0540, -0.0470)
    take(release, "action release", "blued", bevel=0.0008, segments=2)


STOCK = [
    # y, top, bottom, half width, how square, how narrow the comb is against the belly
    (-0.0830, 0.0122, -0.0465, 0.0200, 3.6, 0.98),
    (-0.0950, 0.0068, -0.0490, 0.0192, 2.9, 0.94),
    (-0.1150, -0.0030, -0.0550, 0.0181, 2.4, 0.90),
    (-0.1400, -0.0105, -0.0660, 0.0178, 2.2, 0.88),
    (-0.1600, -0.0140, -0.0820, 0.0182, 2.2, 0.86),
    (-0.1760, -0.0155, -0.0935, 0.0187, 2.3, 0.84),
    (-0.1880, -0.0162, -0.0985, 0.0190, 2.4, 0.82),
    (-0.2000, -0.0170, -0.1010, 0.0192, 2.4, 0.79),
    (-0.2150, -0.0185, -0.1035, 0.0195, 2.3, 0.75),
    (-0.2500, -0.0240, -0.1150, 0.0203, 2.3, 0.71),
    (-0.3000, -0.0320, -0.1390, 0.0211, 2.4, 0.71),
    (-0.3400, -0.0380, -0.1590, 0.0215, 2.6, 0.73),
    (BUTT, -0.0400, -0.1650, 0.0215, 2.7, 0.74),
]


def stock_ring(y, top, bottom, half, square, comb, count=56, grow=0.0):
    middle, tall = (top + bottom) / 2, (top - bottom) / 2
    ring = []
    for i in range(count):
        angle = 2 * math.pi * (i + 0.5) / count
        c, s = math.cos(angle), math.sin(angle)
        x = (half + grow) * math.copysign(abs(c) ** (2 / square), c)
        z = (tall + grow) * math.copysign(abs(s) ** (2 / square), s)
        if z > 0:
            x *= 1 - (1 - comb) * (z / (tall + grow)) ** 1.5
        ring.append((x, y, middle + z))
    return ring


def stock():
    wood = bmesh.new()
    stations = along(STOCK, 64)
    loft(wood, [stock_ring(*station) for station in stations])
    take(wood, "stock", "walnut", group=BUTT_END, bevel=0.0012, segments=2, sharp=50.0, texels=1.1)

    # Behind the wood: a white line, a hard black plate, and a pad of rubber rounded at the back.
    last = STOCK[-1]
    for name, material, layers in (
            ("spacer", "ivory", ((0.0, 0.0002), (-0.0016, 0.0002))),
            ("pad plate", "black", ((-0.0016, 0.0), (-0.0050, 0.0))),
            ("pad", "rubber", ((-0.0050, 0.0003), (-0.0200, 0.0003), (-0.0235, -0.0008), (-0.0258, -0.0030), (-0.0268, -0.0065)))):
        part = bmesh.new()
        loft(part, [stock_ring(BUTT + back, *last[1:], grow=grow) for back, grow in layers])
        take(part, name, material, group=BUTT_END, sharp=40.0)

    stud = bmesh.new()
    lathe(stud, [(0.0, 0.0), (0.0042, 0.0), (0.0042, 0.002), (0.0028, 0.003), (0.0028, 0.0075), (0.0046, 0.0085), (0.0046, 0.0125), (0.003, 0.014), (0.0, 0.014)],
          Matrix.Translation((0.0, -0.290, -0.1335)) @ Matrix.Rotation(math.radians(180 - 27), 4, "X"), segments=20)
    take(stud, "rear stud", "blued", group=BUTT_END, sharp=35.0)


# The sawn-off's grip, from where the stock meets the receiver down to its foot: each station
# is a point on its front edge and one on its back edge in (y, z), the cross section's half
# width, how square it is, and how narrow the back strap is against the front. It meets the
# receiver below its top, so that with the sights up the eye looks over the receiver's back
# and not into the end grain of the wood.
GRIP_FRONT = [(-0.0830, -0.0465), (-0.0720, -0.0500), (-0.0640, -0.0560), (-0.0640, -0.0660), (-0.0680, -0.0800), (-0.0770, -0.0960), (-0.0835, -0.1110),
              (-0.0900, -0.1245), (-0.0950, -0.1340)]
GRIP_BACK = [(-0.0830, -0.0080), (-0.0905, -0.0135), (-0.0985, -0.0215), (-0.1055, -0.0330), (-0.1110, -0.0460), (-0.1190, -0.0680), (-0.1255, -0.0895),
             (-0.1330, -0.1090), (-0.1460, -0.1250)]
GRIP_SECTION = [(0.0200, 3.6, 0.98), (0.0192, 3.0, 0.94), (0.0184, 2.6, 0.90), (0.0178, 2.4, 0.88), (0.0175, 2.3, 0.88), (0.0175, 2.3, 0.88), (0.0177, 2.3, 0.88),
                (0.0181, 2.4, 0.88), (0.0186, 2.6, 0.90)]


def grip_ring(front, back, half, square, comb, count=56, grow=0.0, out=(0.0, 0.0)):
    """A cross section of the grip, in the plane through its front and back edge points and across the gun."""
    middle = ((front[0] + back[0]) / 2 + out[0], (front[1] + back[1]) / 2 + out[1])
    deep = (back[0] - front[0], back[1] - front[1])
    tall = math.hypot(*deep) / 2
    way = (deep[0] / (2 * tall), deep[1] / (2 * tall))
    ring = []
    for i in range(count):
        angle = 2 * math.pi * (i + 0.5) / count
        c, s = math.cos(angle), math.sin(angle)
        x = (half + grow) * math.copysign(abs(c) ** (2 / square), c)
        z = (tall + grow) * math.copysign(abs(s) ** (2 / square), s)
        if z > 0:
            x *= 1 - (1 - comb) * (z / (tall + grow)) ** 1.5
        ring.append((x, middle[0] + way[0] * z, middle[1] + way[1] * z))
    return ring


def grip_stub():
    """The stock sawn off behind the wrist, and what is left of it shaped down into a grip, with a cap on its foot."""
    stations = along([front + back + section for front, back, section in zip(GRIP_FRONT, GRIP_BACK, GRIP_SECTION)], 48)
    wood = bmesh.new()
    loft(wood, [grip_ring(s[0:2], s[2:4], *s[4:]) for s in stations])
    take(wood, "grip", "walnut", group=BUTT_END, bevel=0.0012, segments=2, sharp=50.0, texels=1.1)

    # The cap stands on the foot along the grip's length, which runs from the middle of the last station but one to the last.
    last, before = stations[-1], stations[-2]
    down = Vector(((last[0] + last[2] - before[0] - before[2]) / 2, (last[1] + last[3] - before[1] - before[3]) / 2)).normalized()
    cap = bmesh.new()
    loft(cap, [grip_ring(last[0:2], last[2:4], *last[4:], grow=grow, out=(down.x * along_it, down.y * along_it))
               for along_it, grow in ((-0.0004, 0.0002), (0.0030, 0.0002), (0.0042, -0.0008), (0.0050, -0.0030))])
    take(cap, "grip cap", "black", group=BUTT_END, sharp=40.0)


def fore_ring(y, scale):
    """The fore-end cut across: a trough round the magazine tube, open at the top where the barrel lies in it."""
    outer = [(0.0200, -0.0040), (0.0216, -0.0062), (0.0234, -0.0100), (0.0236, -0.0140), (0.0218, -0.0180), (0.0216, -0.0215), (0.0240, -0.0262),
             (0.0263, -0.0335), (0.0266, -0.0400), (0.0256, -0.0462), (0.0232, -0.0522), (0.0194, -0.0572), (0.0142, -0.0610), (0.0076, -0.0634)]
    middle = Vector((0.0, -0.034))
    shaped = [((x - middle.x) * scale + middle.x, (z - middle.y) * scale + middle.y) for x, z in outer]
    low = (0.0, (-0.0642 - middle.y) * scale + middle.y)
    inner = [(0.0139 * math.cos(math.radians(a)), 0.0139 * math.sin(math.radians(a))) for a in range(-163, -16, 7)]
    points = shaped + [low] + [(-x, z) for x, z in reversed(shaped)] + [(-0.0133, -0.0040)] + inner + [(0.0133, -0.0040)]
    return [(x, y, z) for x, z in points]


def fore_end():
    wood = bmesh.new()
    keys = [(FORE_REAR, 0.80), (FORE_REAR + 0.0015, 0.90), (FORE_REAR + 0.005, 0.955), (FORE_REAR + 0.012, 0.985), (FORE_REAR + 0.03, 1.0)]
    keys += [(FORE_REAR + 0.03 + (FORE_FRONT - FORE_REAR - 0.075) * n / 10, 1.0 + 0.012 * math.sin(math.pi * n / 10)) for n in range(1, 10)]
    keys += [(FORE_FRONT - 0.045, 1.0), (FORE_FRONT - 0.02, 0.96), (FORE_FRONT - 0.008, 0.91), (FORE_FRONT - 0.002, 0.85), (FORE_FRONT, 0.76)]
    loft(wood, [fore_ring(y, scale) for y, scale in keys])
    take(wood, "fore-end", "walnut", group=PUMP, bevel=0.0009, segments=2, sharp=42.0, texels=1.1)

    nut = bmesh.new()
    lathe(nut, [(0.0, FORE_FRONT - 0.004), (0.0162, FORE_FRONT - 0.004), (0.0162, FORE_FRONT + 0.0030), (0.0155, FORE_FRONT + 0.0040), (0.0, FORE_FRONT + 0.0040)],
          along_y(z=TUBE_Z), segments=40)
    take(nut, "fore-end nut", "blued", group=PUMP, sharp=28.0)

    bars = bmesh.new()
    for side in (-1, 1):
        low, high = sorted((side * 0.0136, side * 0.0156))
        cuboid(bars, low, high, 0.020, FORE_REAR + 0.03, -0.0300, -0.0215)
    take(bars, "action bars", "blued", group=PUMP, bevel=0.0004, segments=1)


PICTURES = {}
GRAIN = {}


def load_pictures(folder):
    """Reads the generated pictures and finds which way the lines in each run, so that they can be laid along the gun."""
    for name in ("walnut", "rubber"):
        image = bpy.data.images.load(os.path.join(os.path.abspath(folder), name + ".png"))
        PICTURES[name] = image
        pixels = numpy.empty(image.size[0] * image.size[1] * 4, dtype=numpy.float32)
        image.pixels.foreach_get(pixels)
        grey = pixels.reshape(image.size[1], image.size[0], 4)[..., :3].mean(axis=2)
        across = numpy.roll(grey, -1, axis=1) - numpy.roll(grey, 1, axis=1)
        up = numpy.roll(grey, -1, axis=0) - numpy.roll(grey, 1, axis=0)
        # The way the picture changes fastest is across its lines.
        steepest = 0.5 * math.atan2(2 * float((across * up).mean()), float((across * across).mean() - (up * up).mean()))
        GRAIN[name] = steepest + math.pi / 2
        print("%-8s lines run at %.1f degrees" % (name, math.degrees(GRAIN[name])))


class Graph:
    """The nodes of one material, written as sums. A value is a socket or a number."""

    def __init__(self, name):
        self.material = bpy.data.materials.new(name)
        self.material.use_nodes = True
        self.tree = self.material.node_tree
        self.shader = self.tree.nodes["Principled BSDF"]
        place = self.tree.nodes.new("ShaderNodeTexCoord")
        self.where = place.outputs["Object"]
        self.x, self.y, self.z = self.split(self.where)
        self.nx, self.ny, self.nz = self.split(place.outputs["Normal"])
        self.pointiness = self.tree.nodes.new("ShaderNodeNewGeometry").outputs["Pointiness"]

    def put(self, socket, value):
        if isinstance(value, (int, float)):
            socket.default_value = value
        else:
            self.tree.links.new(value, socket)

    def split(self, vector):
        node = self.tree.nodes.new("ShaderNodeSeparateXYZ")
        self.put(node.inputs[0], vector)
        return node.outputs[0], node.outputs[1], node.outputs[2]

    def math(self, operation, *values):
        node = self.tree.nodes.new("ShaderNodeMath")
        node.operation = operation
        for socket, value in zip(node.inputs, values):
            self.put(socket, value)
        return node.outputs[0]

    def add(self, *values):
        total = values[0]
        for value in values[1:]:
            total = self.math("ADD", total, value)
        return total

    def times(self, *values):
        total = values[0]
        for value in values[1:]:
            total = self.math("MULTIPLY", total, value)
        return total

    def step(self, value, low, high):
        """Nought at low and below, one at high and above, smooth between."""
        node = self.tree.nodes.new("ShaderNodeMapRange")
        node.interpolation_type = "SMOOTHSTEP"
        self.put(node.inputs["Value"], value)
        self.put(node.inputs["From Min"], low)
        self.put(node.inputs["From Max"], high)
        return node.outputs[0]

    def teeth(self, value):
        """A row of ridges one apart: nought in each groove, one on each crest."""
        return self.times(self.math("ABSOLUTE", self.math("SUBTRACT", self.math("FRACT", value), 0.5)), 2.0)

    def colour(self, red, green, blue):
        node = self.tree.nodes.new("ShaderNodeRGB")
        node.outputs[0].default_value = (red, green, blue, 1.0)
        return node.outputs[0]

    def scaled(self, colour, by):
        node = self.tree.nodes.new("ShaderNodeVectorMath")
        node.operation = "SCALE"
        self.put(node.inputs[0], colour)
        self.put(node.inputs["Scale"], by)
        return node.outputs[0]

    def tinted(self, colour, tint):
        node = self.tree.nodes.new("ShaderNodeVectorMath")
        node.operation = "MULTIPLY"
        self.put(node.inputs[0], colour)
        self.put(node.inputs[1], tint)
        return node.outputs[0]

    def mix(self, share, first, second):
        node = self.tree.nodes.new("ShaderNodeMix")
        node.data_type = "RGBA"
        named = {socket.identifier: socket for socket in node.inputs}
        self.put(named["Factor_Float"], share)
        self.put(named["A_Color"], first)
        self.put(named["B_Color"], second)
        return next(socket for socket in node.outputs if socket.identifier == "Result_Color")

    def grey(self, colour):
        node = self.tree.nodes.new("ShaderNodeRGBToBW")
        self.put(node.inputs[0], colour)
        return node.outputs[0]

    def laid(self, name, span):
        """A picture laid with its lines along the barrel, span metres to a repeat. It is drawn out through the gun
        on a slant, so that it is not smeared on any face but one that slants the same way."""
        angle = GRAIN[name]
        over = self.add(self.times(self.z, 0.82), self.times(self.x, 0.57))
        u = self.math("SUBTRACT", self.times(self.y, math.cos(angle) / span), self.times(over, math.sin(angle) / span))
        v = self.add(self.times(self.y, math.sin(angle) / span), self.times(over, math.cos(angle) / span))
        place = self.tree.nodes.new("ShaderNodeCombineXYZ")
        self.put(place.inputs[0], u)
        self.put(place.inputs[1], v)
        node = self.tree.nodes.new("ShaderNodeTexImage")
        node.image = PICTURES[name]
        self.put(node.inputs[0], place.outputs[0])
        return node.outputs["Color"]

    def boxed(self, name, span):
        node = self.tree.nodes.new("ShaderNodeTexImage")
        node.image = PICTURES[name]
        node.projection = "BOX"
        node.projection_blend = 0.3
        self.put(node.inputs[0], self.scaled(self.where, 1.0 / span))
        return node.outputs["Color"]

    def noise(self, scale, detail=3.0):
        node = self.tree.nodes.new("ShaderNodeTexNoise")
        self.put(node.inputs["Vector"], self.where)
        self.put(node.inputs["Scale"], scale)
        self.put(node.inputs["Detail"], detail)
        return node.outputs[0]

    def streaks(self):
        """The fine lines a part is left with by being polished along its length: about nought, a quarter either way."""
        drawn = self.tree.nodes.new("ShaderNodeVectorMath")
        drawn.operation = "MULTIPLY"
        self.put(drawn.inputs[0], self.where)
        drawn.inputs[1].default_value = (1100.0, 22.0, 1100.0)
        node = self.tree.nodes.new("ShaderNodeTexNoise")
        self.put(node.inputs["Vector"], drawn.outputs[0])
        node.inputs["Scale"].default_value = 1.0
        node.inputs["Detail"].default_value = 2.0
        return self.math("SUBTRACT", node.outputs[0], 0.5)

    def worn(self, most=1.0):
        """Where the finish is rubbed through: along the edges that stand out, brokenly, and thinly here and there on the flats."""
        # An edge is where the surface, rounded off over a couple of millimetres, faces another way than it does itself.
        rounded = self.tree.nodes.new("ShaderNodeBevel")
        rounded.samples = 8
        rounded.inputs["Radius"].default_value = 0.0022
        facing = self.tree.nodes.new("ShaderNodeNewGeometry")
        turn = self.tree.nodes.new("ShaderNodeVectorMath")
        turn.operation = "DOT_PRODUCT"
        self.put(turn.inputs[0], rounded.outputs[0])
        self.put(turn.inputs[1], facing.outputs["True Normal"])
        edge = self.times(self.step(self.math("SUBTRACT", 1.0, turn.outputs["Value"]), 0.012, 0.11), self.step(self.pointiness, 0.498, 0.512))
        broken = self.step(self.noise(240.0, 4.0), 0.36, 0.62)
        thin = self.times(self.step(self.noise(30.0, 5.0), 0.62, 0.86), self.step(self.noise(500.0, 4.0), 0.40, 0.75), 0.16)
        return self.math("MINIMUM", self.times(self.add(self.times(edge, broken, 0.85), thin), most), 1.0)

    def panel(self, y, z, half_long, half_high, corner, turn):
        """How far a place is outside a rounded panel on the side of the gun, in metres: less than nought within it."""
        dy, dz = self.math("SUBTRACT", self.y, y), self.math("SUBTRACT", self.z, z)
        along_it = self.add(self.times(dy, math.cos(turn)), self.times(dz, math.sin(turn)))
        up_it = self.math("SUBTRACT", self.times(dz, math.cos(turn)), self.times(dy, math.sin(turn)))
        qa = self.math("SUBTRACT", self.math("ABSOLUTE", along_it), half_long - corner)
        qb = self.math("SUBTRACT", self.math("ABSOLUTE", up_it), half_high - corner)
        outside = self.math("SQRT", self.add(self.math("POWER", self.math("MAXIMUM", qa, 0.0), 2.0), self.math("POWER", self.math("MAXIMUM", qb, 0.0), 2.0)))
        inside = self.math("MINIMUM", self.math("MAXIMUM", qa, qb), 0.0)
        return self.math("SUBTRACT", self.add(outside, inside), corner)

    def finish(self, colour, rough, metal, height):
        self.sockets = {"colour": colour, "rough": rough, "metal": metal}
        self.put(self.shader.inputs["Base Color"], colour)
        self.put(self.shader.inputs["Roughness"], rough)
        self.put(self.shader.inputs["Metallic"], metal)
        bump = self.tree.nodes.new("ShaderNodeBump")
        bump.inputs["Distance"].default_value = 1.0
        self.put(bump.inputs["Height"], height)
        self.tree.links.new(bump.outputs[0], self.shader.inputs["Normal"])
        self.glow = self.tree.nodes.new("ShaderNodeEmission")
        self.baked = self.tree.nodes.new("ShaderNodeTexImage")
        return self

    def show(self, which):
        """Has the material give out one of its own properties as light, to be baked, or with None be itself again."""
        out = self.tree.nodes["Material Output"].inputs["Surface"]
        if which is None:
            self.tree.links.new(self.shader.outputs[0], out)
            return
        for link in list(self.glow.inputs["Color"].links):
            self.tree.links.remove(link)
        value = self.sockets[which]
        if isinstance(value, (int, float)):
            self.glow.inputs["Color"].default_value = (value, value, value, 1.0)
        else:
            self.tree.links.new(value, self.glow.inputs["Color"])
        self.tree.links.new(self.glow.outputs[0], out)


def steel(g, colour, rough, wear=1.0, bare=(0.42, 0.42, 0.43)):
    """Polished steel under a finish: the lines of its polishing, and bare metal where the finish is rubbed through."""
    lines = g.streaks()
    rubbed = g.worn(wear)
    finish = g.scaled(g.colour(*colour), g.add(1.0, g.times(lines, 0.5)))
    return g.mix(rubbed, finish, g.colour(*bare)), g.add(rough, g.times(lines, 0.30), g.times(rubbed, 0.06)), lines


def blued(g, rough=0.30):
    colour, roughness, lines = steel(g, (0.030, 0.033, 0.044), rough)
    return g.finish(colour, roughness, 1.0, g.times(lines, 0.00002))


def rib(g):
    colour, roughness, lines = steel(g, (0.024, 0.026, 0.032), 0.55, wear=0.5)
    # The top of the rib is cut across with fine lines so that it does not shine in the eye.
    top = g.step(g.nz, 0.6, 0.9)
    return g.finish(colour, roughness, 1.0, g.times(top, g.teeth(g.times(g.y, 1.0 / 0.0011)), 0.00013))


def knurled(g):
    colour, roughness, lines = steel(g, (0.030, 0.033, 0.044), 0.36)
    band = g.times(g.step(g.y, TUBE_END, TUBE_END + 0.0007), g.math("SUBTRACT", 1.0, g.step(g.y, TUBE_END + 0.0173, TUBE_END + 0.0180)))
    round_it = g.math("ARCTAN2", g.x, g.math("SUBTRACT", g.z, TUBE_Z))
    return g.finish(colour, roughness, 1.0, g.times(band, g.teeth(g.times(round_it, 40 / (2 * math.pi))), 0.0003))


def bright(g):
    lines = g.streaks()
    return g.finish(g.scaled(g.colour(0.52, 0.52, 0.535), g.add(1.0, g.times(lines, 0.4))), g.add(0.24, g.times(lines, 0.4)), 1.0, g.times(lines, 0.00003))


def black(g):
    """An alloy part in a hard black coat, which wears to the pale metal at its edges."""
    rubbed = g.worn(0.3)
    grain = g.noise(2600.0, 1.0)
    return g.finish(g.mix(rubbed, g.colour(0.016, 0.016, 0.018), g.colour(0.50, 0.50, 0.52)), g.add(0.40, g.times(grain, 0.10)), rubbed, g.times(grain, 0.00002))


def brass(g):
    return g.finish(g.colour(0.83, 0.61, 0.27), 0.27, 1.0, 0.0)


def ivory(g):
    return g.finish(g.colour(0.72, 0.69, 0.60), 0.45, 0.0, 0.0)


def rubber(g):
    grain = g.grey(g.boxed("rubber", 0.09))
    return g.finish(g.scaled(g.colour(0.020, 0.019, 0.018), g.add(0.6, g.times(grain, 1.5))), 0.80, 0.0, g.times(grain, 0.0006))


CHECKER_PITCH = 0.0017


def walnut(g):
    figure = g.laid("walnut", 0.30)
    light = g.grey(figure)
    colour = g.tinted(g.mix(0.6, figure, g.tinted(figure, figure)), g.colour(1.45, 1.16, 0.98))
    # Cut into the sides of the grip and of the fore-end, a panel of fine diamonds for the hand to hold by.
    side = g.step(g.math("ABSOLUTE", g.nx), 0.30, 0.55)
    if SAWN_OFF:
        grip = g.panel(-0.0980, -0.0840, 0.034, 0.0140, 0.008, math.radians(69.5))
    else:
        grip = g.panel(-0.150, -0.045, 0.032, 0.0165, 0.010, math.radians(24))
    fore = g.panel((FORE_REAR + FORE_FRONT) / 2, -0.0395, 0.070, 0.0108, 0.005, 0.0)
    nearest = g.math("MINIMUM", grip, fore)
    cut = g.times(g.math("SUBTRACT", 1.0, g.step(nearest, -0.0003, 0.0003)), side)
    line = g.times(g.math("SUBTRACT", 1.0, g.step(g.math("ABSOLUTE", g.add(nearest, 0.0016)), 0.0003, 0.0007)), side)
    one = g.teeth(g.add(g.times(g.y, 0.866 / CHECKER_PITCH), g.times(g.z, 0.5 / CHECKER_PITCH)))
    other = g.teeth(g.math("SUBTRACT", g.times(g.y, 0.866 / CHECKER_PITCH), g.times(g.z, 0.5 / CHECKER_PITCH)))
    diamond = g.math("MINIMUM", one, other)
    height = g.add(g.times(light, 0.00006), g.times(cut, g.math("SUBTRACT", g.times(diamond, 0.00050), 0.00030)), g.times(line, -0.00030))
    colour = g.scaled(colour, g.math("SUBTRACT", 1.0, g.times(cut, g.math("SUBTRACT", 0.50, g.times(diamond, 0.38)))))
    rough = g.add(0.27, g.times(g.math("SUBTRACT", 1.0, light), 0.16), g.times(cut, 0.25))
    return g.finish(colour, rough, 0.0, height)


def hull(g):
    """The plastic body of a shell, ribbed along its length."""
    round_it = g.math("ARCTAN2", g.x, g.math("SUBTRACT", g.z, SHELL_AT.z))
    ribs = g.teeth(g.times(round_it, 30 / (2 * math.pi)))
    return g.finish(g.scaled(g.colour(0.42, 0.025, 0.02), g.add(0.85, g.times(ribs, 0.2))), 0.42, 0.0, g.times(ribs, 0.00012))


MAKERS = {"hull": hull, "blued": blued, "receiver": lambda g: blued(g, 0.27), "rib": rib, "knurled": knurled, "bright": bright, "black": black, "brass": brass,
          "ivory": ivory, "rubber": rubber, "walnut": walnut}
GRAPHS = {}


def material_of(name):
    if name not in GRAPHS:
        GRAPHS[name] = MAKERS[name](Graph(name))
    return GRAPHS[name].material


def shell():
    """A fired shell: a brass head with its rim, and the plastic body open at the mouth where the crimp unfolded."""
    to = along_y(SHELL_AT.x, SHELL_AT.y, SHELL_AT.z)
    head = bmesh.new()
    lathe(head, [(0.0, -0.0350), (0.0040, -0.0350), (0.0040, -0.0342), (0.0108, -0.0342), (0.0112, -0.0338), (0.0112, -0.0328), (0.0103, -0.0322),
                 (0.0103, -0.0190), (0.0, -0.0190)], to, segments=28)
    take(head, "shell head", "brass", group=SHELL, sharp=30.0)
    body = bmesh.new()
    lathe(body, [(0.0, -0.0195), (0.0100, -0.0195), (0.0100, 0.0338), (0.0097, 0.0350), (0.0090, 0.0350), (0.0090, -0.0150), (0.0, -0.0150)], to, segments=28)
    take(body, "shell body", "hull", group=SHELL, sharp=30.0)


def build():
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj)
    shell()
    barrel()
    magazine()
    receiver()
    trigger_group()
    if SAWN_OFF:
        grip_stub()
    else:
        stock()
    fore_end()
    depsgraph = bpy.context.evaluated_depsgraph_get()
    total = 0
    for obj in PARTS:
        triangles = sum(len(polygon.vertices) - 2 for polygon in obj.evaluated_get(depsgraph).data.polygons)
        total += triangles
        print("%-18s %6d triangles" % (obj.name, triangles))
    print("%-18s %6d triangles" % ("in all", total))



# Each tube's axis, where along it its two lengths part, and the radius it is unrolled at.
TUBES = {"barrel": (0.0, 0.31 if SAWN_OFF else 0.46, 0.0122), "magazine tube": (TUBE_Z, 0.29 if SAWN_OFF else 0.35, TUBE_R)}


def select(obj):
    for other in bpy.context.view_layer.objects:
        other.select_set(other == obj)
    bpy.context.view_layer.objects.active = obj


def unwrap_tube(mesh, axis_z, halfway, radius):
    """A tube is unrolled whole: its length along u and the way round it along v, in two lengths that part at a ring of
    its vertices, with its ends apart."""
    bm = bmesh.new()
    bm.from_mesh(mesh)
    layer = bm.loops.layers.uv.verify()
    for face in bm.faces:
        middle = face.calc_center_median()
        out = Vector((middle.x, 0.0, middle.z - axis_z))
        if abs(face.normal.y) > 0.7:
            for loop in face.loops:
                loop[layer].uv = (loop.vert.co.x + (10.0 if middle.y > halfway else 5.0), loop.vert.co.z)
            continue
        round_middle = math.atan2(middle.x, middle.z - axis_z)
        inward = face.normal.dot(out) < 0.0
        for loop in face.loops:
            at = loop.vert.co
            turn = math.atan2(at.x, at.z - axis_z)
            while turn - round_middle > math.pi:
                turn -= 2 * math.pi
            while turn - round_middle < -math.pi:
                turn += 2 * math.pi
            # One radius for the whole of it: a triangle unrolled at a radius of its own shares no corner with the next.
            loop[layer].uv = (at.y * (0.5 if inward else 1.0), turn * (0.5 if inward else 1.0) * radius + (0.2 if middle.y > halfway else 0.0) + (0.5 if inward else 0.0))
    bm.to_mesh(mesh)
    bm.free()


def unwrap():
    """Gives every part a place in the texture, at one size to the metre but for what a part is given over the rest."""
    depsgraph = bpy.context.evaluated_depsgraph_get()
    for obj in PARTS:
        cut_up = obj.modifiers.new("triangles", "TRIANGULATE")
        cut_up.keep_custom_normals = True
        depsgraph.update()
        mesh = bpy.data.meshes.new_from_object(obj.evaluated_get(depsgraph), preserve_all_data_layers=True, depsgraph=depsgraph)
        obj.modifiers.clear()
        old = obj.data
        obj.data = mesh
        bpy.data.meshes.remove(old)
        group = mesh.attributes.new("group", "INT", "FACE")
        group.data.foreach_set("value", [obj["group"]] * len(mesh.polygons))
        part = mesh.attributes.new("part", "INT", "FACE")
        part.data.foreach_set("value", [PARTS.index(obj)] * len(mesh.polygons))
        # Under one name in every part, or joining them keeps each part's in a layer of its own.
        mesh.uv_layers.new(name="UVMap")
        if obj.name in TUBES:
            unwrap_tube(mesh, *TUBES[obj.name])
            scale = obj["texels"]
        else:
            select(obj)
            bpy.ops.object.mode_set(mode="EDIT")
            bpy.ops.mesh.select_all(action="SELECT")
            bpy.ops.uv.smart_project(angle_limit=math.radians(66), island_margin=0.0, area_weight=0.0, scale_to_bounds=False)
            bpy.ops.object.mode_set(mode="OBJECT")
            mesh = obj.data
            uv = numpy.empty(len(mesh.loops) * 2)
            mesh.uv_layers.active.data.foreach_get("uv", uv)
            uv = uv.reshape(-1, 3, 2)
            flat = 0.5 * numpy.abs(numpy.cross(uv[:, 1] - uv[:, 0], uv[:, 2] - uv[:, 0])).sum()
            scale = math.sqrt(sum(polygon.area for polygon in mesh.polygons) / flat) * obj["texels"]
        mesh = obj.data
        uv = numpy.empty(len(mesh.loops) * 2)
        mesh.uv_layers.active.data.foreach_get("uv", uv)
        mesh.uv_layers.active.data.foreach_set("uv", uv * scale)
        if "--stats" in sys.argv:
            placed = (uv * scale).reshape(-1, 2)
            print("%-18s scale %10.4f spans %.3f by %.3f" % (obj.name, scale, *(placed.max(axis=0) - placed.min(axis=0))), flush=True)

    PART_NAMES.extend(obj.name for obj in PARTS)
    for obj in bpy.context.view_layer.objects:
        obj.select_set(obj in PARTS)
    bpy.context.view_layer.objects.active = PARTS[0]
    bpy.ops.object.join()
    gun = bpy.context.view_layer.objects.active
    gun.name = NAME
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.select_all(action="SELECT")
    bpy.ops.uv.pack_islands(rotate=True, margin_method="FRACTION", margin=0.0028, shape_method="CONCAVE")
    bpy.ops.object.mode_set(mode="OBJECT")
    mesh = gun.data
    uv = numpy.empty(len(mesh.loops) * 2)
    mesh.uv_layers.active.data.foreach_get("uv", uv)
    uv = uv.reshape(-1, 3, 2)
    flat = 0.5 * numpy.abs(numpy.cross(uv[:, 1] - uv[:, 0], uv[:, 2] - uv[:, 0])).sum()
    print("the texture is %.0f%% used, at %.0f dots to the metre" % (flat * 100, TEXTURE * math.sqrt(flat / sum(polygon.area for polygon in mesh.polygons))))
    return gun


def bake(gun):
    """Bakes what the materials work out into pictures laid out as the texture is: each a float array, rows from the bottom."""
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    use_card(scene)
    scene.cycles.samples = 16
    scene.render.bake.margin = 12
    scene.render.bake.use_clear = True
    world = bpy.data.worlds.new("bake")
    world.light_settings.distance = 0.05
    scene.world = world
    target = bpy.data.images.new("bake", TEXTURE, TEXTURE, float_buffer=True, is_data=True)
    for graph in GRAPHS.values():
        graph.baked.image = target
        graph.tree.nodes.active = graph.baked
    select(gun)
    out = {}

    def taken():
        pixels = numpy.empty(TEXTURE * TEXTURE * 4, dtype=numpy.float32)
        target.pixels.foreach_get(pixels)
        return pixels.reshape(TEXTURE, TEXTURE, 4)[..., :3].copy()

    for which in ("colour", "rough", "metal"):
        for graph in GRAPHS.values():
            graph.show(which)
        bpy.ops.object.bake(type="EMIT")
        out[which] = taken()
        print("baked", which, flush=True)
    for graph in GRAPHS.values():
        graph.show(None)
    bpy.ops.object.bake(type="NORMAL", normal_space="TANGENT")
    out["normal"] = taken()
    print("baked normal", flush=True)
    scene.cycles.samples = 192
    bpy.ops.object.bake(type="AO")
    out["light"] = taken()
    print("baked light", flush=True)
    return out


def use_card(scene):
    if "--cpu" in sys.argv:
        scene.cycles.device = "CPU"
        return
    preferences = bpy.context.preferences.addons["cycles"].preferences
    preferences.compute_device_type = "OPTIX"
    preferences.get_devices()
    for device in preferences.devices:
        device.use = device.type == "OPTIX"
    scene.cycles.device = "GPU"


def encoded(linear):
    """The sRGB coding of a linear colour, which keeps the steps between dark shades small."""
    linear = numpy.clip(linear, 0.0, 1.0)
    return numpy.where(linear <= 0.0031308, linear * 12.92, 1.055 * numpy.power(linear, 1 / 2.4) - 0.055)


def bytes_of(array):
    return (numpy.clip(array, 0.0, 1.0) * 255.0 + 0.5).astype(numpy.uint8)


def pictures_of(baked):
    colour = numpy.concatenate([encoded(baked["colour"]), baked["light"][..., :1]], axis=2)
    surface = numpy.concatenate([baked["normal"][..., :2], baked["rough"][..., :1], baked["metal"][..., :1]], axis=2)
    return bytes_of(colour), bytes_of(surface)


def pack(gun, colour, surface):
    mesh = gun.data
    mesh.calc_tangents(uvmap=mesh.uv_layers.active.name)
    corners = len(mesh.loops)
    index = numpy.empty(corners, dtype=numpy.int32)
    mesh.loops.foreach_get("vertex_index", index)
    place = numpy.empty(len(mesh.vertices) * 3, dtype=numpy.float32)
    mesh.vertices.foreach_get("co", place)
    place = place.reshape(-1, 3)[index] - numpy.array(GRIP, dtype=numpy.float32)
    normal = numpy.empty(corners * 3, dtype=numpy.float32)
    mesh.loops.foreach_get("normal", normal)
    tangent = numpy.empty(corners * 3, dtype=numpy.float32)
    mesh.loops.foreach_get("tangent", tangent)
    way = numpy.empty(corners, dtype=numpy.float32)
    mesh.loops.foreach_get("bitangent_sign", way)
    uv = numpy.empty(corners * 2, dtype=numpy.float32)
    mesh.uv_layers.active.data.foreach_get("uv", uv)
    group = numpy.empty(len(mesh.polygons), dtype=numpy.int32)
    mesh.attributes["group"].data.foreach_get("value", group)
    starts = numpy.empty(len(mesh.polygons), dtype=numpy.int32)
    mesh.polygons.foreach_get("loop_start", starts)

    rows = numpy.zeros(corners, dtype=[("place", "<f4", 3), ("normal", "<i2", 3), ("tangent", "<i2", 3), ("way", "<i2"), ("uv", "<u2", 2)])
    # The mod holds a weapon up as the game's own are made, which is the mirror image of how this one is laid out
    # here: its right side, with the port the spent shells leave by, has x less than nought in the file.
    mirror = numpy.array((-1.0, 1.0, 1.0), dtype=numpy.float32)
    corner_group = numpy.repeat(group, 3)
    place[corner_group == SHELL] -= numpy.array(SHELL_AT - GRIP, dtype=numpy.float32)
    rows["place"] = place * mirror
    rows["normal"] = numpy.round(normal.reshape(-1, 3) * mirror * 32767)
    rows["tangent"] = numpy.round(tangent.reshape(-1, 3) * mirror * 32767)
    rows["way"] = numpy.where(way < 0, 1, -1)
    rows["uv"] = numpy.round(numpy.clip(uv.reshape(-1, 2), 0.0, 1.0) * 65535)
    # A corner that is the same in every respect as another is one vertex.
    vertices, which = numpy.unique(rows.view([("all", "V%d" % rows.dtype.itemsize)]), return_inverse=True)
    which = which.reshape(-1)
    order = numpy.argsort(group, kind="stable")
    indices = numpy.concatenate([which[start:start + 3] for start in starts[order]]).astype("<u4")
    counts = [int((group == part).sum()) * 3 for part in (FIXED, PUMP, BOLT, SHELL, BUTT_END)]
    muzzle = Vector((0.0, MUZZLE_Y, 0.0)) - GRIP
    held = Vector((0.0, (FORE_REAR + FORE_FRONT) / 2, -0.034)) - GRIP
    port = (PORT - GRIP) * Vector((-1.0, 1.0, 1.0))
    head = b"CUWM" + struct.pack("<I3f3f3f3fI5I", 4, *muzzle, *held, *port, TRAVEL, SIGHT_LINE, EYE_BEHIND_GRIP, len(vertices), *counts)
    os.makedirs(OUTPUT, exist_ok=True)
    files = [(NAME + ".mesh.gz", head + vertices.tobytes() + indices.tobytes())]
    if colour is not None:
        files += [(NAME + ".colour.gz", colour.tobytes()), (NAME + ".surface.gz", surface.tobytes())]
    for name, data in files:
        path = os.path.join(OUTPUT if name.endswith(".mesh.gz") else TEXTURES, name)
        with gzip.open(path, "wb", compresslevel=9) as packed:
            packed.write(data)
        print("wrote %s, %d bytes" % (os.path.normpath(path), os.path.getsize(path)))
    print("%d vertices, %d triangles: %d fixed, %d fore-end, %d bolt, %d shell, %d stock" % (len(vertices), len(indices) // 3, *(count // 3 for count in counts)))


def dress(gun, colour, surface, folder):
    """Puts the baked pictures on the gun in place of the materials they were baked from, as the mod will draw it."""
    material = bpy.data.materials.new("baked")
    material.use_nodes = True
    tree = material.node_tree
    shader = tree.nodes["Principled BSDF"]

    def picture(name, array, is_colour):
        image = bpy.data.images.new(name, TEXTURE, TEXTURE, alpha=True, is_data=not is_colour)
        image.pixels.foreach_set((array.astype(numpy.float32) / 255.0).reshape(-1))
        image.filepath_raw = os.path.join(folder, name + ".png")
        image.file_format = "PNG"
        image.save()
        node = tree.nodes.new("ShaderNodeTexImage")
        node.image = image
        return node

    tint, shape = picture("texture-colour", colour, True), picture("texture-surface", surface, False)
    tree.links.new(tint.outputs["Color"], shader.inputs["Base Color"])
    parts = tree.nodes.new("ShaderNodeSeparateColor")
    tree.links.new(shape.outputs["Color"], parts.inputs[0])
    tree.links.new(parts.outputs[2], shader.inputs["Roughness"])
    tree.links.new(shape.outputs["Alpha"], shader.inputs["Metallic"])
    # The third part of the normal is not kept: it is what is left of a length of one.
    under = tree.nodes.new("ShaderNodeVectorMath")
    under.operation = "DOT_PRODUCT"
    centred = tree.nodes.new("ShaderNodeVectorMath")
    centred.operation = "MULTIPLY_ADD"
    centred.inputs[1].default_value = (2.0, 2.0, 0.0)
    centred.inputs[2].default_value = (-1.0, -1.0, 0.0)
    tree.links.new(shape.outputs["Color"], centred.inputs[0])
    tree.links.new(centred.outputs[0], under.inputs[0])
    tree.links.new(centred.outputs[0], under.inputs[1])
    rest = tree.nodes.new("ShaderNodeMath")
    rest.operation = "SUBTRACT"
    rest.inputs[0].default_value = 1.0
    tree.links.new(under.outputs["Value"], rest.inputs[1])
    root = tree.nodes.new("ShaderNodeMath")
    root.operation = "SQRT"
    tree.links.new(rest.outputs[0], root.inputs[0])
    halved = tree.nodes.new("ShaderNodeMath")
    halved.operation = "MULTIPLY_ADD"
    halved.inputs[1].default_value = 0.5
    halved.inputs[2].default_value = 0.5
    tree.links.new(root.outputs[0], halved.inputs[0])
    whole = tree.nodes.new("ShaderNodeCombineColor")
    tree.links.new(parts.outputs[0], whole.inputs[0])
    tree.links.new(parts.outputs[1], whole.inputs[1])
    tree.links.new(halved.outputs[0], whole.inputs[2])
    bent = tree.nodes.new("ShaderNodeNormalMap")
    tree.links.new(whole.outputs[0], bent.inputs["Color"])
    tree.links.new(bent.outputs[0], shader.inputs["Normal"])
    gun.data.materials.clear()
    gun.data.materials.append(material)
    for polygon in gun.data.polygons:
        polygon.material_index = 0


def render(folder, only=(), size=(1600, 900), samples=64):
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    use_card(scene)
    scene.cycles.samples = samples
    scene.cycles.use_denoising = True
    scene.render.resolution_x, scene.render.resolution_y = size
    scene.view_settings.view_transform = "Filmic"
    world = bpy.data.worlds.new("studio")
    world.use_nodes = True
    sky = world.node_tree.nodes.new("ShaderNodeTexSky")
    sky.sky_type = "NISHITA"
    sky.sun_elevation = math.radians(38)
    sky.sun_rotation = math.radians(130)
    sky.sun_intensity = 0.25
    world.node_tree.links.new(sky.outputs["Color"], world.node_tree.nodes["Background"].inputs["Color"])
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.35
    scene.world = world
    floor = bpy.data.meshes.new("floor")
    floor.from_pydata([(-4, -4, -0.2), (4, -4, -0.2), (4, 4, -0.2), (-4, 4, -0.2)], [], [(0, 1, 2, 3)])
    ground = bpy.data.objects.new("floor", floor)
    paper = bpy.data.materials.new("paper")
    paper.use_nodes = True
    paper.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.11, 0.11, 0.115, 1.0)
    floor.materials.append(paper)
    scene.collection.objects.link(ground)
    for place, energy, size_of in (((1.2, -0.6, 1.4), 60, 1.5), ((-1.5, 1.0, 0.8), 30, 2.0), ((0.2, 1.6, 0.4), 16, 1.0)):
        lamp = bpy.data.lights.new("lamp", "AREA")
        lamp.energy = energy
        lamp.size = size_of
        holder = bpy.data.objects.new("lamp", lamp)
        holder.location = place
        holder.rotation_euler = (Vector((0.0, 0.25, 0.0)) - Vector(place)).to_track_quat("-Z", "Y").to_euler()
        scene.collection.objects.link(holder)
    # The gun joined in one, or its parts as they were made.
    for obj in [bpy.data.objects[NAME]] if NAME in bpy.data.objects else PARTS:
        obj.location = -GRIP
    camera = bpy.data.objects.new("camera", bpy.data.cameras.new("camera"))
    scene.collection.objects.link(camera)
    scene.camera = camera
    folder = os.path.abspath(folder)
    os.makedirs(folder, exist_ok=True)
    # Half way from the butt to the muzzle, from the grip, and how far off the gun is seen whole.
    middle, off = (0.30, 1.5) if SAWN_OFF else (0.33, 2.15)
    views = {
        # name: where the camera is, what it looks at, focal length
        "right": ((off, middle, 0.10), (0.0, middle, -0.03), 60),
        "left": ((-off, middle, 0.10), (0.0, middle, -0.03), 60),
        "receiver": ((0.42, 0.02, 0.10), (0.0, 0.10, 0.03), 50),
        "receiver-left": ((-0.40, 0.30, 0.16), (0.0, 0.12, 0.03), 50),
        "under": ((0.30, 0.05, -0.16), (0.0, 0.18, 0.0), 45),
        "fore": ((0.45, 0.62, 0.16), (0.0, 0.42, 0.02), 50),
        "muzzle": ((0.22, MUZZLE_Y + 0.40, 0.16), (0.0, MUZZLE_Y - 0.16, 0.035), 55),
        "sights": ((0.0, -0.21, 0.0835), (0.0, 0.90, 0.0705), 40),
        "hip": ((-0.13, -0.36, 0.20), (0.02, 0.50, 0.02), 22),
        "stock": ((0.50, -0.42, 0.12), (0.0, -0.10, -0.03), 50),
    }
    for name, (place, target, lens) in views.items():
        if only and name not in only:
            continue
        camera.location = place
        camera.data.lens = lens
        camera.data.clip_start = 0.01
        camera.rotation_euler = (Vector(target) - Vector(place)).to_track_quat("-Z", "Y").to_euler()
        scene.render.filepath = os.path.join(folder, name + ".png")
        bpy.ops.render.render(write_still=True)



def main():
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if not args:
        raise SystemExit("say where the pictures from shotgun_textures.py are")
    load_pictures(args[0])
    build()
    if "--raw" in args:
        # The parts as they are made, under their own materials and unbaked: for looking at the shape.
        at = args.index("--raw")
        render(args[at + 1], [name for name in args[at + 2:] if not name.startswith("--")], samples=24)
        return
    gun = unwrap()
    if "--mesh" in args:
        # The triangles alone, which do not change with what is baked onto them.
        pack(gun, None, None)
        return
    if "--stats" in sys.argv:
        mesh = gun.data
        uv = numpy.empty(len(mesh.loops) * 2)
        mesh.uv_layers.active.data.foreach_get("uv", uv)
        uv = uv.reshape(-1, 3, 2)
        which = numpy.array([polygon.material_index for polygon in mesh.polygons])
        part = numpy.empty(len(mesh.polygons), dtype=numpy.int32)
        mesh.attributes["part"].data.foreach_get("value", part)
        only = os.environ.get("ONLY_PART")
        if only:
            uv = numpy.where((part == PART_NAMES.index(only))[:, None, None], uv, 0.0)
        picture = numpy.zeros((1024, 1024, 4), dtype=numpy.float32)
        picture[..., 3] = 1.0
        for share in numpy.linspace(0.0, 1.0, 200):
            for a, b in ((0, 1), (1, 2), (2, 0)):
                at = numpy.clip(((uv[:, a] * (1 - share) + uv[:, b] * share) * 1023).astype(int), 0, 1023)
                picture[at[:, 1], at[:, 0], 0] = 0.3 + 0.7 * ((which * 37) % 10) / 9.0
                picture[at[:, 1], at[:, 0], 1] = 0.3 + 0.7 * ((which * 53) % 7) / 6.0
                picture[at[:, 1], at[:, 0], 2] = 0.3 + 0.7 * ((which * 71) % 5) / 4.0
        image = bpy.data.images.new("layout", 1024, 1024)
        image.pixels.foreach_set(picture.reshape(-1))
        image.filepath_raw = os.path.abspath(".lab/shotgun/layout.png")
        image.file_format = "PNG"
        image.save()
        part = numpy.empty(len(mesh.polygons), dtype=numpy.int32)
        mesh.attributes["part"].data.foreach_get("value", part)
        areas = 0.5 * numpy.abs(numpy.cross(uv[:, 1] - uv[:, 0], uv[:, 2] - uv[:, 0]))
        names = [name for name in PART_NAMES]
        for n, name in enumerate(names):
            mine = uv[part == n].reshape(-1, 2)
            print("%-18s %5.2f%% of the texture, box %.3f %.3f to %.3f %.3f" % (name, areas[part == n].sum() * 100, *mine.min(axis=0), *mine.max(axis=0)))
        return
    colour, surface = pictures_of(bake(gun))
    if "--no-pack" not in args:
        pack(gun, colour, surface)
    if "--renders" in args:
        folder = os.path.abspath(args[args.index("--renders") + 1])
        os.makedirs(folder, exist_ok=True)
        dress(gun, colour, surface, folder)
        render(folder)
    if "--blend" in args:
        bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(args[args.index("--blend") + 1]))


main()
