"""Builds the mod's own furniture models and packs them for the jar.

Run headless with Blender 4.4, from the repository root:

    blender --background --factory-startup --python tools/models/furniture.py

It writes src/main/resources/models/furniture.bin.gz and prints each model's
triangle count and bounds. Every model is made here from nothing: no mesh,
texture or measurement is read from the game.

A model is in metres, Z up, its origin on the floor at the centre of its
footprint, with +Y the head of a bed and the back of everything else, so its
front faces -Y. Each triangle belongs to one surface, which says what it is
made of; the mod gives a surface its colour and lays its grain on by where the
triangle is in the world, so there are no texture coordinates.

The file is gzip over this, all little-endian:

    4 bytes   the ASCII letters CUFM
    uint32    format version, 1
    uint32    number of models
    per model:
      uint8     length of the name, then that many ASCII bytes
      float32   x 6: the least x, y, z and the greatest x, y, z
      uint32    number of triangles
      per vertex, three to a triangle:
        float32 x 3   position
        float32 x 3   unit normal
        uint8         surface: an index into SURFACES below
        uint8         how much of the room's light reaches the vertex past the
                      rest of the model and the floor, 255 for all of it
"""

import gzip
import math
import os
import struct

import bmesh
import bpy
from mathutils import Matrix, Vector, noise
from mathutils.bvhtree import BVHTree

SURFACES = ["wood", "dark_wood", "cloth", "linen", "pillow", "metal", "paint"]

OUTPUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "models", "furniture.bin.gz")

# How far a vertex looks for whatever stands between it and the room's light, in metres.
OCCLUSION_REACH = 0.45
OCCLUSION_RAYS = 24
# A vertex nothing at all can see keeps this much light, so a deep joint is dark and not a hole.
OCCLUSION_FLOOR = 0.3


def cuboid(bm, x0, x1, y0, y1, z0, z1):
    prism(bm, ((x0 + x1) / 2, (y0 + y1) / 2, x1 - x0, y1 - y0, z0), ((x0 + x1) / 2, (y0 + y1) / 2, x1 - x0, y1 - y0, z1))


def prism(bm, low, high):
    """A box whose bottom and top are rectangles of their own, each given as centre x, centre y, width, depth, height above the floor."""
    rings = []
    for cx, cy, sx, sy, z in (low, high):
        rings.append([bm.verts.new((cx + dx * sx / 2, cy + dy * sy / 2, z)) for dx, dy in ((-1, -1), (1, -1), (1, 1), (-1, 1))])
    bm.faces.new(rings[0][::-1])
    bm.faces.new(rings[1])
    for i in range(4):
        j = (i + 1) % 4
        bm.faces.new((rings[0][i], rings[0][j], rings[1][j], rings[1][i]))


def grid_box(bm, xs, ys, zs, bottom=True):
    """A box cut into cells along each axis, as a cage for something soft. Returns its vertices by their place in the grid."""
    verts = {}
    last = (len(xs) - 1, len(ys) - 1, len(zs) - 1)

    def at(i, j, k):
        if (i, j, k) not in verts:
            verts[(i, j, k)] = bm.verts.new((xs[i], ys[j], zs[k]))
        return verts[(i, j, k)]

    for i in range(last[0]):
        for j in range(last[1]):
            if bottom:
                bm.faces.new((at(i, j, 0), at(i, j + 1, 0), at(i + 1, j + 1, 0), at(i + 1, j, 0)))
            bm.faces.new((at(i, j, last[2]), at(i + 1, j, last[2]), at(i + 1, j + 1, last[2]), at(i, j + 1, last[2])))
    for i in range(last[0]):
        for k in range(last[2]):
            bm.faces.new((at(i, 0, k), at(i + 1, 0, k), at(i + 1, 0, k + 1), at(i, 0, k + 1)))
            bm.faces.new((at(i, last[1], k), at(i, last[1], k + 1), at(i + 1, last[1], k + 1), at(i + 1, last[1], k)))
    for j in range(last[1]):
        for k in range(last[2]):
            bm.faces.new((at(0, j, k), at(0, j, k + 1), at(0, j + 1, k + 1), at(0, j + 1, k)))
            bm.faces.new((at(last[0], j, k), at(last[0], j + 1, k), at(last[0], j + 1, k + 1), at(last[0], j, k + 1)))
    return verts


def spread(low, high, cells):
    return [low + (high - low) * i / cells for i in range(cells + 1)]


def crease(bm, verts, amount):
    """Holds the cage's outer edges against the smoothing, so a cushion keeps the shape of a box with soft edges."""
    layer = bm.edges.layers.float.get("crease_edge") or bm.edges.layers.float.new("crease_edge")
    last = [max(key[axis] for key in verts) for axis in range(3)]
    place = {vert: key for key, vert in verts.items()}
    for edge in bm.edges:
        a, b = place[edge.verts[0]], place[edge.verts[1]]
        on_limit = sum(1 for axis in range(3) if a[axis] == b[axis] and a[axis] in (0, last[axis]))
        if on_limit >= 2:
            edge[layer] = amount


class Model:
    def __init__(self, name):
        self.name = name
        self.vertices = []

    def hard(self, bm, surface, bevel=0.004, segments=2):
        """A part of wood or metal: every edge rounded off, its flat faces kept flat."""
        def modifiers(obj):
            rounded = obj.modifiers.new("bevel", "BEVEL")
            rounded.width = bevel
            rounded.segments = segments
            rounded.limit_method = "ANGLE"
            rounded.angle_limit = math.radians(30)
            flat = obj.modifiers.new("normals", "WEIGHTED_NORMAL")
            flat.mode = "FACE_AREA"
            flat.weight = 100
            flat.keep_sharp = True
        self._take(bm, surface, modifiers)

    def soft(self, bm, surface, levels=2):
        """A part of cloth: the cage smoothed into a rounded body."""
        def modifiers(obj):
            smoothed = obj.modifiers.new("subdivision", "SUBSURF")
            smoothed.levels = levels
            smoothed.subdivision_type = "CATMULL_CLARK"
        self._take(bm, surface, modifiers)

    def _take(self, bm, surface, modifiers):
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
        mesh = bpy.data.meshes.new("part")
        bm.to_mesh(mesh)
        bm.free()
        mesh.shade_smooth()
        obj = bpy.data.objects.new("part", mesh)
        bpy.context.scene.collection.objects.link(obj)
        modifiers(obj)
        shaped = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
        done = shaped.to_mesh()
        done.calc_loop_triangles()
        normals = done.corner_normals
        index = SURFACES.index(surface)
        for triangle in done.loop_triangles:
            for corner, vertex in zip(triangle.loops, triangle.vertices):
                self.vertices.append((Vector(done.vertices[vertex].co), Vector(normals[corner].vector), index))
        shaped.to_mesh_clear()
        bpy.data.objects.remove(obj)
        bpy.data.meshes.remove(mesh)

    def finish(self):
        """Stands the model on the floor over the middle of its footprint and works out what each vertex can see of the room."""
        low = Vector([min(v[0][axis] for v in self.vertices) for axis in range(3)])
        high = Vector([max(v[0][axis] for v in self.vertices) for axis in range(3)])
        move = Vector(((low.x + high.x) / 2, (low.y + high.y) / 2, low.z))
        self.vertices = [(position - move, normal, surface) for position, normal, surface in self.vertices]
        self.low, self.high = low - move, high - move

        points = [position for position, _, _ in self.vertices]
        faces = [(i, i + 1, i + 2) for i in range(0, len(points), 3)]
        # The floor the piece stands on shades its underside as the rest of it does.
        ground = len(points)
        points += [Vector((-9, -9, 0)), Vector((9, -9, 0)), Vector((9, 9, 0)), Vector((-9, 9, 0))]
        faces.append((ground, ground + 1, ground + 2, ground + 3))
        tree = BVHTree.FromPolygons(points, faces)
        rays = []
        for i in range(OCCLUSION_RAYS):
            # Spread so that each ray stands for the same share of the light a surface takes in.
            radius = math.sqrt((i + 0.5) / OCCLUSION_RAYS)
            turn = i * math.pi * (3 - math.sqrt(5))
            rays.append((radius * math.cos(turn), radius * math.sin(turn), math.sqrt(1 - radius * radius)))
        known = {}
        self.light = []
        for position, normal, _ in self.vertices:
            key = (round(position.x, 4), round(position.y, 4), round(position.z, 4), round(normal.x, 1), round(normal.y, 1), round(normal.z, 1))
            if key not in known:
                side = normal.orthogonal().normalized()
                other = normal.cross(side)
                start = position + normal * 0.004
                hidden = 0.0
                for x, y, z in rays:
                    hit = tree.ray_cast(start, side * x + other * y + normal * z, OCCLUSION_REACH)
                    if hit[0] is not None:
                        hidden += 1 - hit[3] / OCCLUSION_REACH
                known[key] = OCCLUSION_FLOOR + (1 - OCCLUSION_FLOOR) * (1 - hidden / OCCLUSION_RAYS)
            self.light.append(known[key])

    def packed(self):
        name = self.name.encode("ascii")
        out = [struct.pack("<B", len(name)), name, struct.pack("<6f", *self.low, *self.high), struct.pack("<I", len(self.vertices) // 3)]
        for (position, normal, surface), light in zip(self.vertices, self.light):
            out.append(struct.pack("<6fBB", *position, *normal, surface, round(light * 255)))
        return b"".join(out)


def bed(name, width, length, pillows):
    model = Model(name)
    half, head, foot = width / 2, length / 2, -length / 2
    top = 0.54

    frame = bmesh.new()
    for side in (-1, 1):
        cuboid(frame, side * (half + 0.015) - 0.02, side * (half + 0.015) + 0.02, foot - 0.02, head + 0.02, 0.17, 0.37)
        prism(frame, (side * (half + 0.01), head + 0.04, 0.05, 0.05, 0.0), (side * (half + 0.01), head + 0.04, 0.07, 0.07, 1.08))
        prism(frame, (side * (half + 0.01), foot - 0.04, 0.05, 0.05, 0.0), (side * (half + 0.01), foot - 0.04, 0.07, 0.07, 0.56))
    cuboid(frame, -half, half, foot - 0.055, foot - 0.025, 0.17, 0.50)
    cuboid(frame, -half, half, head + 0.03, head + 0.055, 0.17, 0.98)
    cuboid(frame, -half - 0.03, half + 0.03, head + 0.01, head + 0.07, 0.98, 1.04)
    model.hard(frame, "wood", bevel=0.006)

    mattress = bmesh.new()
    cuboid(mattress, -half, half, foot, head, 0.30, top)
    model.hard(mattress, "linen", bevel=0.035, segments=3)

    # The duvet lies from the foot to where it is turned down short of the pillows, and
    # hangs over both sides and the foot.
    turned = head - 0.64
    over = half + 0.035
    duvet = bmesh.new()
    cage = grid_box(duvet, spread(-over, over, max(6, round(width * 5))), spread(foot - 0.035, turned, 11), [0.37, top + 0.05], bottom=False)
    for (i, j, k), vert in cage.items():
        at = vert.co
        if k == 1:
            at.z += 0.014 * noise.noise(Vector((at.x * 2.6, at.y * 2.6, 0.3))) + 0.007 * noise.noise(Vector((at.x * 7.0, at.y * 7.0, 4.1)))
        else:
            hang = 0.012 * noise.noise(Vector((at.x * 5.0, at.y * 5.0, 9.7)))
            at.x += math.copysign(hang, at.x)
            at.z += 0.02 * noise.noise(Vector((at.x * 6.0, at.y * 6.0, 2.2)))
    model.soft(duvet, "cloth", levels=1)

    # Turned down, the duvet shows its pale underside as a band across the bed.
    band = bmesh.new()
    cage = grid_box(band, spread(-over - 0.008, over + 0.008, max(6, round(width * 5))), [turned - 0.30, turned - 0.15, turned + 0.015], [0.41, top + 0.085], bottom=False)
    for (i, j, k), vert in cage.items():
        if k == 1:
            vert.co.z += 0.008 * noise.noise(Vector((vert.co.x * 4.0, vert.co.y * 4.0, 6.5)))
    model.soft(band, "linen", levels=1)

    for n in range(pillows):
        centre = 0.0 if pillows == 1 else (n - 0.5) * (half + 0.02)
        lean = math.radians((-5, 4)[n % 2])
        wide, deep, thick = min(0.36, half - 0.09), 0.23, 0.075
        pillow = bmesh.new()
        cage = grid_box(pillow, spread(-wide, wide, 3), spread(-deep, deep, 2), [-thick, 0.0, thick])
        for (i, j, k), vert in cage.items():
            at = vert.co
            if k != 1:
                # The two faces are drawn in from the seam, which leaves the pillow thin at its
                # edge and full in the middle.
                at.x *= 0.72
                at.y *= 0.66
            turned_x = at.x * math.cos(lean) - at.y * math.sin(lean)
            turned_y = at.x * math.sin(lean) + at.y * math.cos(lean)
            at.x, at.y, at.z = centre + turned_x, head - 0.31 + turned_y, top + thick - 0.012 + at.z
        model.soft(pillow, "pillow", levels=2)
    return model


def wardrobe():
    model = Model("wardrobe")
    half, front, back = 0.90, -0.27, 0.29

    body = bmesh.new()
    cuboid(body, -half + 0.02, half - 0.02, front + 0.03, back, 0.0, 0.09)
    cuboid(body, -half, half, front, back, 0.09, 1.92)
    cuboid(body, -half - 0.02, half + 0.02, front - 0.02, back, 1.90, 1.94)
    cuboid(body, -half - 0.045, half + 0.045, front - 0.045, back, 1.94, 2.0)
    model.hard(body, "wood", bevel=0.008)

    doors = bmesh.new()
    gap, rail, proud = 0.006, 0.085, front - 0.024
    for side in (-1, 1):
        inner, outer = side * gap, side * (half - 0.012)
        low, high = min(inner, outer), max(inner, outer)
        cuboid(doors, low, high, front - 0.012, front, 0.11, 1.89)
        cuboid(doors, low, low + rail, proud, front - 0.004, 0.11, 1.89)
        cuboid(doors, high - rail, high, proud, front - 0.004, 0.11, 1.89)
        for z in (0.11, 0.80, 1.89 - rail):
            cuboid(doors, low + rail - 0.002, high - rail + 0.002, proud, front - 0.004, z, z + rail)
        # A raised field in each of the door's two panels.
        for z0, z1 in ((0.11 + rail + 0.06, 0.80 - 0.06), (0.80 + rail + 0.06, 1.89 - rail - 0.06)):
            cuboid(doors, low + rail + 0.06, high - rail - 0.06, front - 0.02, front - 0.008, z0, z1)
    model.hard(doors, "wood", bevel=0.005)

    handles = bmesh.new()
    for side in (-1, 1):
        x = side * 0.055
        cuboid(handles, x - 0.007, x + 0.007, proud - 0.035, proud - 0.021, 0.93, 1.09)
        for z in (0.95, 1.06):
            cuboid(handles, x - 0.005, x + 0.005, proud - 0.024, proud + 0.002, z, z + 0.01)
    model.hard(handles, "metal", bevel=0.003)
    return model


def dining_chair():
    model = Model("dining_chair")
    half, seat = 0.20, 0.44

    def rake(z):
        """How far back the chair's back stands at height z."""
        return 0.19 + max(z - seat, 0.0) * 0.14

    frame = bmesh.new()
    for side in (-1, 1):
        prism(frame, (side * half, -0.19, 0.026, 0.026, 0.0), (side * half, -0.19, 0.04, 0.04, seat))
        prism(frame, (side * (half - 0.005), 0.21, 0.026, 0.026, 0.0), (side * half, rake(seat), 0.04, 0.04, seat))
        prism(frame, (side * half, rake(seat), 0.04, 0.04, seat), (side * half, rake(0.96), 0.034, 0.028, 0.96))
        cuboid(frame, side * half - 0.011, side * half + 0.011, -0.17, 0.17, 0.37, seat)
        cuboid(frame, side * half - 0.009, side * half + 0.009, -0.17, 0.18, 0.17, 0.195)
    cuboid(frame, -half + 0.02, half - 0.02, -0.201, -0.179, 0.37, seat)
    cuboid(frame, -half + 0.02, half - 0.02, 0.179, 0.201, 0.37, seat)
    cuboid(frame, -half + 0.009, half - 0.009, -0.01, 0.01, 0.17, 0.195)
    model.hard(frame, "wood", bevel=0.004, segments=1)

    back = bmesh.new()
    prism(back, (0.0, rake(0.85), 2 * half - 0.03, 0.022, 0.85), (0.0, rake(0.945), 2 * half - 0.03, 0.022, 0.945))
    prism(back, (0.0, rake(0.56), 2 * half - 0.03, 0.02, 0.56), (0.0, rake(0.60), 2 * half - 0.03, 0.02, 0.60))
    for x in (-0.10, 0.0, 0.10):
        prism(back, (x, rake(0.60), 0.045, 0.012, 0.595), (x, rake(0.85), 0.045, 0.012, 0.855))
    model.hard(back, "wood", bevel=0.004, segments=1)

    board = bmesh.new()
    cuboid(board, -half - 0.025, half + 0.025, -0.225, 0.20, seat, seat + 0.022)
    model.hard(board, "wood", bevel=0.007)

    pad = bmesh.new()
    cage = grid_box(pad, spread(-half, half, 2), spread(-0.20, 0.17, 2), [seat + 0.018, seat + 0.05])
    cage[(1, 1, 1)].co.z -= 0.014
    crease(pad, cage, 0.45)
    model.soft(pad, "cloth", levels=2)
    return model


def small_table():
    model = Model("small_table")
    half, top = 0.45, 0.75

    under = bmesh.new()
    for x in (-1, 1):
        for y in (-1, 1):
            prism(under, (x * (half - 0.065), y * (half - 0.065), 0.036, 0.036, 0.0), (x * (half - 0.065), y * (half - 0.065), 0.058, 0.058, top - 0.03))
    for side in (-1, 1):
        cuboid(under, side * (half - 0.065) - 0.011, side * (half - 0.065) + 0.011, -half + 0.09, half - 0.09, top - 0.115, top - 0.03)
        cuboid(under, -half + 0.09, half - 0.09, side * (half - 0.065) - 0.011, side * (half - 0.065) + 0.011, top - 0.115, top - 0.03)
    model.hard(under, "wood", bevel=0.004, segments=1)

    board = bmesh.new()
    cuboid(board, -half, half, -half, half, top - 0.03, top)
    model.hard(board, "wood", bevel=0.011, segments=3)
    return model


def cushion(model, x0, x1, y0, y1, z0, z1, crown, lean=0.0):
    """A cushion: a box with soft edges, fuller in the middle of its face, its top leant back by lean."""
    part = bmesh.new()
    cage = grid_box(part, spread(x0, x1, 2), spread(y0, y1, 2), spread(z0, z1, 2) if lean else [z0, z1])
    for (i, j, k), vert in cage.items():
        vert.co.y += lean * (vert.co.z - z0) / (z1 - z0)
    if lean:
        cage[(1, 0, 1)].co.y -= crown
    else:
        cage[(1, 1, 1)].co.z += crown
    crease(part, cage, 0.5)
    model.soft(part, "cloth", levels=2)


def sofa():
    model = Model("sofa")
    half, front, back, arm = 0.95, -0.44, 0.44, 0.17
    inner = half - arm

    feet = bmesh.new()
    for x in (-1, 1):
        for y in (front + 0.07, back - 0.07):
            prism(feet, (x * (half - 0.08), y, 0.04, 0.04, 0.0), (x * (half - 0.08), y, 0.065, 0.065, 0.08))
    model.hard(feet, "dark_wood", bevel=0.004, segments=1)

    base = bmesh.new()
    cage = grid_box(base, [-half + 0.01, 0.0, half - 0.01], [front + 0.02, back], [0.075, 0.31])
    crease(base, cage, 0.75)
    model.soft(base, "cloth", levels=2)

    for side in (-1, 1):
        low, high = sorted((side * inner, side * half))
        part = bmesh.new()
        cage = grid_box(part, [low, high], spread(front, back, 2), [0.075, 0.38, 0.63])
        crease(part, cage, 0.35)
        model.soft(part, "cloth", levels=2)

    rest = bmesh.new()
    cage = grid_box(rest, [-inner - 0.01, 0.0, inner + 0.01], [0.24, back], [0.075, 0.5, 0.86])
    for (i, j, k), vert in cage.items():
        vert.co.y += 0.05 * (vert.co.z - 0.075) / 0.785
    crease(rest, cage, 0.4)
    model.soft(rest, "cloth", levels=2)

    for side in (-1, 1):
        low, high = sorted((side * 0.006, side * (inner - 0.004)))
        cushion(model, low, high, front - 0.015, 0.25, 0.30, 0.455, 0.022)
        cushion(model, low + 0.01, high - 0.01, 0.10, 0.27, 0.44, 0.80, 0.03, lean=0.075)
    return model


def bedside_cabinet():
    model = Model("bedside_cabinet")
    half, front, back = 0.25, -0.20, 0.21

    body = bmesh.new()
    for x in (-1, 1):
        for y in (front + 0.04, back - 0.04):
            prism(body, (x * (half - 0.04), y, 0.028, 0.028, 0.0), (x * (half - 0.04), y, 0.042, 0.042, 0.1))
    cuboid(body, -half, half, front, back, 0.09, 0.63)
    model.hard(body, "wood", bevel=0.006)

    board = bmesh.new()
    cuboid(board, -half - 0.02, half + 0.02, front - 0.022, back, 0.63, 0.66)
    model.hard(board, "wood", bevel=0.009, segments=3)

    drawers = bmesh.new()
    for z0, z1 in ((0.115, 0.40), (0.415, 0.605)):
        cuboid(drawers, -half + 0.018, half - 0.018, front - 0.016, front, z0, z1)
    model.hard(drawers, "wood", bevel=0.005)

    knobs = bmesh.new()
    for z in (0.2575, 0.51):
        bmesh.ops.create_uvsphere(knobs, u_segments=10, v_segments=6, radius=0.016, matrix=Matrix.Translation((0.0, front - 0.038, z)))
        cuboid(knobs, -0.006, 0.006, front - 0.03, front - 0.014, z - 0.006, z + 0.006)
    model.hard(knobs, "metal", bevel=0.002, segments=1)
    return model


def main():
    models = [bed("double_bed", 1.80, 2.00, 2), bed("single_bed", 0.90, 1.90, 1), wardrobe(), dining_chair(), small_table(), sofa(), bedside_cabinet()]
    data = [b"CUFM", struct.pack("<II", 1, len(models))]
    for model in models:
        model.finish()
        data.append(model.packed())
        print("%-16s %5d triangles  x %.3f to %.3f  y %.3f to %.3f  z %.3f to %.3f" % (
            model.name, len(model.vertices) // 3, model.low.x, model.high.x, model.low.y, model.high.y, model.low.z, model.high.z))
    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "wb") as raw:
        # No name and no time in the gzip header, so the same models pack to the same bytes.
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as packed:
            packed.write(b"".join(data))
    print("wrote %s, %d bytes" % (os.path.normpath(OUTPUT), os.path.getsize(OUTPUT)))


main()
