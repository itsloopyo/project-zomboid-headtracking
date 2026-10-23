"""Carves the gallery's marble busts and statues out of a human body, for furniture.py to stand on their plinths.

Run headless with Blender 4.4, from the repository root:

    blender --background --factory-startup --python tools/models/sculpture.py -- DATA

DATA is the folder arms.py reads (MakeHuman's base mesh, its default rig and weights and the
male and female targets, out of MPFB 2's `data` folder, see THIRD-PARTY-NOTICES.md). The body is
shaped as a man or a woman, smoothed once by Catmull-Clark, and carved: hair and a beard as
marble has them, solid masses parted by grooves; cloth as a shell over the body or as a solid
hanging from it in folds. A bust is cut across the chest; a statue is posed through the rig.
Each is then collapsed to FIGURE_TRIANGLES, holding on to the face. furniture.py uses the same
figure for the plain copy it makes of every model: Low draws the plain copy at every distance, and
the pictures of these figures are taken close up at Low.

It writes tools/models/sculpture.bin.gz, which furniture.py reads, gzip over this, little-endian:

    4 bytes   the ASCII letters SCLP
    uint32    format version, 1
    uint32    number of figures
    per figure:
      uint8     length of the name, then that many ASCII bytes
      uint32    number of vertices
      uint32    number of triangles
      float32 x 3 per vertex: its place, in metres, z up, the figure facing -y
      uint32 x 3 per triangle: its vertices

A bust stands with the lowest point of its cut at z = 0; a statue with the soles of its feet there.
"""

import gzip
import json
import math
import os
import struct
import sys

import bmesh
import bpy
import numpy
from mathutils import Matrix, Quaternion, Vector, noise
from mathutils.bvhtree import BVHTree

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from arms import blender, joint, read_obj, read_target, shaped  # noqa: E402

OUTPUT = os.path.join(HERE, "sculpture.bin.gz")
FIGURE_TRIANGLES = 17500
MUSCLE = 0.5
# The figure's own body parts, with the eyes, which a marble head has as smooth balls.
KEPT = ("body", "helper-l-eye", "helper-r-eye")
TORSO_BONES = ("spine01", "spine02", "spine03", "spine04", "spine05", "breast.L", "breast.R", "clavicle.L", "clavicle.R",
               "pelvis.L", "pelvis.R", "neck01", "shoulder01.L", "shoulder01.R")
ARM_PARTS = ("arm", "wrist", "hand", "finger", "thumb", "metacarpal")


class Body:
    """The base mesh with its rig, as one shape."""

    def __init__(self, data, targets):
        self.places, _, self.faces, self.groups = read_obj(os.path.join(data, "base.obj"))
        self.rig = json.load(open(os.path.join(data, "rig.default.json"), encoding="utf-8"))
        self.weights = json.load(open(os.path.join(data, "weights.default.json"), encoding="utf-8"))["weights"]
        self.shape = shaped(self.places, *[(read_target(os.path.join(data, name + ".target")), weight) for name, weight in targets])

    def landmark(self, name):
        return numpy.array(blender(self.shape[sorted(self.groups[name])].mean(axis=0)))

    def made(self, name):
        """The body as an object with a vertex group for each bone, and its armature."""
        kept = [corners for group, corners in self.faces if group in KEPT]
        used = sorted({v for corners in kept for v, _ in corners})
        renumber = {old: new for new, old in enumerate(used)}
        mesh = bpy.data.meshes.new(name)
        mesh.from_pydata([blender(self.shape[v]) for v in used], [], [[renumber[v] for v, _ in corners] for corners in kept])
        mesh.update()
        body = bpy.data.objects.new(name, mesh)
        bpy.context.collection.objects.link(body)
        total = numpy.zeros(len(self.places))
        for pairs in self.weights.values():
            for index, weight in pairs:
                total[index] += weight
        given = numpy.zeros(len(used))
        for bone, pairs in self.weights.items():
            group = body.vertex_groups.new(name=bone)
            for index, weight in pairs:
                if index in renumber and weight > 0:
                    group.add([renumber[index]], weight / total[index], "REPLACE")
                    given[renumber[index]] += weight / total[index]
        # The eyes have no weights of their own; they go with the head.
        head = body.vertex_groups["head"]
        for new in range(len(used)):
            if given[new] < 0.999:
                head.add([new], 1.0 - given[new], "ADD")
        armature = bpy.data.armatures.new(name + "-rig")
        skeleton = bpy.data.objects.new(name + "-rig", armature)
        bpy.context.collection.objects.link(skeleton)
        bpy.context.view_layer.objects.active = skeleton
        bpy.ops.object.mode_set(mode="EDIT")
        for bone_name, said in self.rig.items():
            bone = armature.edit_bones.new(bone_name)
            bone.head = joint(said["head"], self.shape, self.groups)
            bone.tail = joint(said["tail"], self.shape, self.groups)
            bone.roll = said["roll"]
        for bone_name, said in self.rig.items():
            if said["parent"]:
                armature.edit_bones[bone_name].parent = armature.edit_bones[said["parent"]]
        bpy.ops.object.mode_set(mode="OBJECT")
        return body, skeleton


def pose(skeleton, turns, bends):
    """turns: bone -> (axis, degrees) list, each a turn in the armature's axes of that bone and all under it, on top of its
    parent's. bends: bone -> (axis, degrees) list in the bone's own axes, for fingers that curl."""
    absolute = {}
    for bone in sorted(skeleton.data.bones, key=lambda b: len(b.parent_recursive)):
        parent = absolute.get(bone.parent.name, Quaternion()) if bone.parent else Quaternion()
        own = Quaternion()
        for axis, degrees in turns.get(bone.name, []):
            own = Quaternion(Vector(axis), math.radians(degrees)) @ own
        absolute[bone.name] = own @ parent
        rest = bone.matrix_local.to_3x3().to_quaternion()
        local = rest.inverted() @ parent.inverted() @ absolute[bone.name] @ rest
        for axis, degrees in bends.get(bone.name, []):
            local = local @ Quaternion(Vector(axis), math.radians(degrees))
        held = skeleton.pose.bones[bone.name]
        held.rotation_mode = "QUATERNION"
        held.rotation_quaternion = local
    bpy.context.view_layer.update()


def evaluated_copy(obj, name):
    """obj with its modifiers applied, as a new object that keeps its vertex groups."""
    depsgraph = bpy.context.evaluated_depsgraph_get()
    mesh = bpy.data.meshes.new_from_object(obj.evaluated_get(depsgraph), preserve_all_data_layers=True, depsgraph=depsgraph)
    out = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(out)
    for group in obj.vertex_groups:
        out.vertex_groups.new(name=group.name)
    return out


def subdivided(body):
    smooth = body.modifiers.new("smooth", "SUBSURF")
    smooth.levels = 1
    out = evaluated_copy(body, body.name + "-smooth")
    body.modifiers.remove(smooth)
    return out


def posed_copy(rest, skeleton, turns, bends):
    skin = rest.modifiers.new("skin", "ARMATURE")
    skin.object = skeleton
    pose(skeleton, turns, bends)
    out = evaluated_copy(rest, rest.name + "-posed")
    rest.modifiers.remove(skin)
    return out


def curled(degrees, thumb, sides=("L", "R")):
    """Each finger's three joints bent by degrees about the bone's own x, and the thumb's by thumb."""
    out = {}
    for side in sides:
        for f in range(1, 6):
            for j in range(1, 4):
                out["finger%d-%d.%s" % (f, j, side)] = [((1, 0, 0), thumb if f == 1 else degrees)]
    return out


def points(obj):
    out = numpy.empty(len(obj.data.vertices) * 3)
    obj.data.vertices.foreach_get("co", out)
    return out.reshape(-1, 3)


def normals(obj):
    obj.data.update()
    out = numpy.empty(len(obj.data.vertices) * 3)
    obj.data.vertex_normals.foreach_get("vector", out)
    return out.reshape(-1, 3)


def moved(obj, by):
    obj.data.vertices.foreach_set("co", (points(obj) + by).ravel())
    obj.data.update()


def step(x, a, b):
    t = numpy.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def gaussian(at, centre, spread):
    d = (at - numpy.array(centre)) / numpy.array(spread)
    return numpy.exp(-0.5 * (d * d).sum(axis=1))


def group_values(obj, name):
    if name not in obj.vertex_groups:
        return numpy.zeros(len(obj.data.vertices))
    index = obj.vertex_groups[name].index
    out = numpy.zeros(len(obj.data.vertices))
    for v in obj.data.vertices:
        for g in v.groups:
            if g.group == index:
                out[v.index] = g.weight
    return out


def summed(obj, names):
    return sum(group_values(obj, name) for name in names)


def armish(obj):
    """How much of each vertex goes with an arm or a hand."""
    out = numpy.zeros(len(obj.data.vertices))
    for group in obj.vertex_groups:
        if any(part in group.name for part in ARM_PARTS):
            out = numpy.maximum(out, group_values(obj, group.name))
    return out


def evened(obj, values, times, share=0.5):
    """values (one per vertex) averaged with their neighbours' times over."""
    edges = numpy.empty(len(obj.data.edges) * 2, dtype=numpy.int64)
    obj.data.edges.foreach_get("vertices", edges)
    a, b = edges[0::2], edges[1::2]
    count = numpy.zeros(len(values))
    numpy.add.at(count, a, 1)
    numpy.add.at(count, b, 1)
    out = values.astype(float).copy()
    for _ in range(times):
        total = numpy.zeros(len(values))
        numpy.add.at(total, a, out[b])
        numpy.add.at(total, b, out[a])
        out = (1 - share) * out + share * total / numpy.maximum(count, 1)
    return out


def locks(at, weight, size, height, seed, stretch=(1.0, 1.0, 1.0), groove=0.35):
    """Locks of hair as marble shows them: cells size across, each a raised mass, with a groove between each two."""
    out = numpy.zeros(len(at))
    offset = Vector((seed * 7.31, seed * 3.17, seed * 5.53))
    scale = numpy.array(stretch) * size
    for i in numpy.nonzero(weight > 0.0)[0]:
        d = noise.voronoi(Vector(at[i] / scale) + offset)[0]
        t = min((d[1] - d[0]) / groove, 1.0)
        out[i] = weight[i] * height * (t * (2 - t)) * (1.0 - 0.35 * min(d[0], 1.0))
    return out


def joined(target, others):
    bpy.ops.object.select_all(action="DESELECT")
    for o in others + [target]:
        o.select_set(True)
    bpy.context.view_layer.objects.active = target
    bpy.ops.object.join()
    return target


def hidden_removed(body, keep):
    """Removes the faces of body whose every corner keep says is hidden."""
    bm = bmesh.new()
    bm.from_mesh(body.data)
    bm.verts.ensure_lookup_table()
    bmesh.ops.delete(bm, geom=[f for f in bm.faces if all(not keep[v.index] for v in f.verts)], context="FACES")
    bm.to_mesh(body.data)
    bm.free()
    body.data.update()


def trunk_of(posed):
    """A copy of the posed body without its arms, for cloth to hang from the body alone."""
    trunk = evaluated_copy(posed, "trunk")
    hidden_removed(trunk, armish(posed) < 0.3)
    return trunk


def ball(name, place, radii, seed, size, height, segments=(16, 10)):
    """A ball of marble cloth or hair, its surface in locks."""
    thing = bpy.data.objects.new(name, bpy.data.meshes.new(name))
    bpy.context.collection.objects.link(thing)
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=segments[0], v_segments=segments[1], radius=1.0)
    bmesh.ops.transform(bm, matrix=place @ Matrix.Diagonal((*radii, 1.0)), verts=bm.verts)
    bm.to_mesh(thing.data)
    bm.free()
    at = points(thing)
    moved(thing, normals(thing) * locks(at, numpy.ones(len(at)), size, height, seed, (1.0, 1.0, 0.7), 0.3)[:, None])
    return thing


def hanging_cloth(body, top, hem, centre, clear, flare, folds, depth, seed, around=96, rows=56, thick=0.012, clear_low=None):
    """Cloth that falls from top to hem round the body: at each height as far out as the body is there or anywhere above
    it, flaring out towards the hem, in folds that deepen as they fall."""
    tree = BVHTree.FromObject(body, bpy.context.evaluated_depsgraph_get())
    radii = numpy.zeros((rows, around))
    heights = numpy.linspace(top, hem, rows)
    out = numpy.zeros(around)
    low = clear if clear_low is None else clear_low
    for r, z in enumerate(heights):
        fall = (top - z) / (top - hem)
        for k in range(around):
            angle = 2 * math.pi * k / around
            way = Vector((math.cos(angle), math.sin(angle), 0.0))
            hit = tree.ray_cast(Vector((centre[0], centre[1], z)) + way * 0.6, -way, 0.6)
            reach = 0.6 - hit[3] if hit[0] is not None else 0.0
            out[k] = max(out[k], reach + clear + (low - clear) * fall)
        radii[r] = out + flare * fall * fall
    # Evened round, so the cloth does not follow every bump it hangs from.
    for _ in range(3):
        radii = 0.5 * radii + 0.25 * (numpy.roll(radii, 1, axis=1) + numpy.roll(radii, -1, axis=1))
    bm = bmesh.new()
    grid = []
    for r, z in enumerate(heights):
        fall = (top - z) / (top - hem)
        ring = []
        for k in range(around):
            angle = 2 * math.pi * k / around
            turn = angle * folds / 2 + 0.9 * noise.noise(Vector((angle * 1.5, z * 2.5, seed)))
            rad = radii[r, k] + depth * (0.25 + 0.75 * fall) * (2.0 * abs(math.cos(turn)) - 1.0)
            ring.append(bm.verts.new((centre[0] + rad * math.cos(angle), centre[1] + rad * math.sin(angle), z)))
        grid.append(ring)
    for r in range(rows - 1):
        for k in range(around):
            j = (k + 1) % around
            bm.faces.new((grid[r][k], grid[r][j], grid[r + 1][j], grid[r + 1][k]))
    cloth = bpy.data.objects.new("cloth", bpy.data.meshes.new("cloth"))
    bpy.context.collection.objects.link(cloth)
    bm.to_mesh(cloth.data)
    bm.free()
    solid = cloth.modifiers.new("thick", "SOLIDIFY")
    solid.thickness = thick
    solid.offset = -1.0
    out_cloth = evaluated_copy(cloth, "cloth-solid")
    bpy.data.objects.remove(cloth)
    return out_cloth


class Head:
    """Where hair and a beard are drawn: the man's head, into whose frame another body's head is carried."""

    def __init__(self, man, shape):
        self.man, self.shape = man.landmark("joint-head"), shape.landmark("joint-head")
        self.size = (numpy.linalg.norm(man.landmark("joint-head-2") - self.man)
                     / numpy.linalg.norm(shape.landmark("joint-head-2") - self.shape))

    def at(self, real):
        return self.man + (real - self.shape) * self.size

    def back(self, place):
        return self.shape + (numpy.array(place) - self.man) / self.size


def carve_head(rest, head, kind, seed):
    """Hair, and a beard, carved into the rest body: kind is beard, youth or knot. Returns the knot of hair, or None."""
    at = head.at(points(rest))
    x, y, z = at[:, 0], at[:, 1], at[:, 2]
    hang = numpy.zeros_like(at)
    if kind == "knot":
        # Drawn back from a low brow and up off the nape.
        line = numpy.interp(y, [-0.16, -0.12, -0.09, -0.06, -0.03, 0.0, 0.05], [0.885, 0.878, 0.85, 0.83, 0.815, 0.80, 0.80])
        hair = evened(rest, step(z - line, -0.003, 0.008) * step(z, 0.7, 0.72), 4)
        grow = hair * (0.012 + 0.012 * step(y, -0.06, 0.04)) + locks(at, hair, 0.03, 0.011, seed, (0.7, 1.6, 0.6), 0.25)
    else:
        line = numpy.interp(y, [-0.16, -0.12, -0.09, -0.06, -0.03, 0.0, 0.05], [0.895, 0.888, 0.862, 0.835, 0.81, 0.775, 0.755])
        hair = evened(rest, step(z - line, -0.003, 0.008) * step(z, 0.7, 0.72), 4)
        grow = hair * 0.014 + locks(at, hair, 0.026 if kind == "beard" else 0.021, 0.015, seed, (1.0, 1.0, 0.8))
        if kind == "beard":
            top = numpy.interp(y, [-0.18, -0.15, -0.12, -0.09, -0.06, -0.03], [0.772, 0.778, 0.79, 0.805, 0.825, 0.845])
            low = numpy.interp(y, [-0.17, -0.12, -0.09, -0.06, -0.03], [0.67, 0.672, 0.685, 0.71, 0.75])
            beard = step(top - z, -0.003, 0.006) * step(z - low, -0.004, 0.012) * step(-y, -0.01, 0.025) * step(z, 0.64, 0.66)
            lips = gaussian(at, (0.0, -0.168, 0.748), (0.022, 0.03, 0.0065)) * step(-y, 0.14, 0.155)
            beard = evened(rest, numpy.clip(beard - 2.0 * lips, 0.0, 1.0), 4)
            chin = step(0.75 - z, 0.0, 0.05)
            grow += beard * (0.009 + 0.008 * chin) + locks(at, beard, 0.022, 0.012, seed + 1, (0.8, 1.0, 1.6))
            hang[:, 2] = -0.02 * beard * chin
            hang[:, 1] = -0.006 * beard * chin
    grow = evened(rest, grow, 1, 0.25)
    moved(rest, (normals(rest) * grow[:, None] + hang) / head.size)
    if kind != "knot":
        return None
    s = head.size
    knot = ball("knot", Matrix.Translation(tuple(head.back((0.0, 0.062, 0.865)))) @ Matrix.Rotation(math.radians(-25), 4, "X"),
                (0.05 / s, 0.036 / s, 0.04 / s), seed + 2, 0.022 / s, 0.012 / s, (28, 18))
    knot.vertex_groups.new(name="head").add(list(range(len(knot.data.vertices))), 1.0, "REPLACE")
    return knot


def mark_face(obj, head):
    """A vertex group, face, of how hard the collapse is to hold on to each vertex: the face's features are small."""
    group = obj.vertex_groups.new(name="face")
    for i, w in enumerate(gaussian(head.at(points(obj)), (0.0, -0.15, 0.79), (0.06, 0.06, 0.07))):
        group.add([i], float(w), "REPLACE")


def bust(man, shape, female, seed):
    """Head and shoulders cut across the chest in a curve and up each side, as a classical bust is: a bearded man, or a
    woman with her hair up in a knot and a chiton gathered at her neck."""
    body, _ = shape.made("bust")
    rest = subdivided(body)
    head = Head(man, shape)
    knot = carve_head(rest, head, "knot" if female else "beard", seed)
    neck_drop = man.landmark("joint-neck")[2] - shape.landmark("joint-neck")[2]
    if female:
        real = points(rest)
        at = head.at(real)
        x, y = at[:, 0], at[:, 1]
        zt = real[:, 2] + neck_drop
        neck = 0.62 - 0.04 * step(-y, 0.0, 0.1) * (1 - step(numpy.abs(x), 0.0, 0.09))
        cloth = evened(rest, step(neck - zt, -0.004, 0.008), 3)
        rim = numpy.exp(-0.5 * ((zt - neck + 0.006) / 0.004) ** 2) * step(neck - zt, -0.02, 0.0)
        folds = numpy.sin((x + numpy.sign(x) * (0.62 - zt) * 0.5) / 0.009) ** 2
        moved(rest, normals(rest) * (cloth * (0.008 + 0.011 * folds * step(0.61 - zt, 0.0, 0.04)) + 0.009 * rim)[:, None])
        joined(rest, [knot])
    half, a, c = (0.17, 2.6, 0.44 - neck_drop) if female else (0.19, 2.4, 0.425)
    bm = bmesh.new()
    bm.from_mesh(rest.data)
    cuts = [((half, 0, 0), (-1, 0, 0)), ((-half, 0, 0), (1, 0, 0))]
    for x0 in (-0.16, -0.1, -0.05, 0.0, 0.05, 0.1, 0.16):
        cuts.append(((x0, 0.0, c + a * x0 * x0), (-2 * a * x0, 0.0, 1.0)))
    for point, normal in cuts:
        bmesh.ops.bisect_plane(bm, geom=bm.verts[:] + bm.edges[:] + bm.faces[:], plane_co=Vector(point), plane_no=-Vector(normal), clear_outer=True)
    bmesh.ops.holes_fill(bm, edges=bm.edges[:], sides=0)
    bmesh.ops.triangulate(bm, faces=[f for f in bm.faces if len(f.verts) > 4])
    bm.to_mesh(rest.data)
    bm.free()
    rest.data.update()
    mark_face(rest, head)
    return rest


def chiton_woman(man, woman):
    """A draped woman, her arms raised to her hair, a chiton belted under her breast and falling to her feet."""
    body, skeleton = woman.made("woman")
    rest = subdivided(body)
    head = Head(man, woman)
    knot = carve_head(rest, head, "knot", 5)
    mark_face(rest, head)
    real = points(rest)
    x, y, z = real[:, 0], real[:, 1], real[:, 2]
    front = real[(numpy.abs(x) < 0.15) & (z > 0.1) & (z < 0.45)]
    girdle = front[front[:, 1].argmin()][2] - 0.065
    neckline = (woman.landmark("joint-neck")[2] - 0.075 + 0.03 * step(y, -0.05, 0.05)
                - 0.025 * step(-y, 0.0, 0.1) * (1 - step(numpy.abs(x), 0.0, 0.08)))
    bodice = evened(rest, step(numpy.clip(summed(rest, TORSO_BONES[:-2]), 0, 1), 0.3, 0.7) * step(neckline - z, -0.003, 0.008)
                    * step(z - girdle, -0.07, -0.05), 3)
    rim = numpy.exp(-0.5 * ((z - neckline + 0.005) / 0.004) ** 2) * step(neckline - z, -0.02, 0.0) * bodice
    belt = numpy.exp(-0.5 * ((z - girdle) / 0.006) ** 2) * bodice
    folds = numpy.sin((x + numpy.sign(x) * (neckline - z) * 0.35) / 0.008) ** 2
    moved(rest, normals(rest) * (bodice * (0.007 + 0.009 * folds * step(neckline - 0.02 - z, 0.0, 0.04)) + 0.006 * rim + 0.006 * belt)[:, None])
    joined(rest, [knot])
    turns = {
        "upperarm01.L": [((0, 1, 0), -128), ((1, 0, 0), -26)],
        "lowerarm01.L": [((0, 1, 0), -88), ((1, 0, 0), -15)],
        "wrist.L": [((0, 1, 0), 15), ((1, 0, 0), 75)],
        "upperarm01.R": [((0, 1, 0), 128), ((1, 0, 0), -26)],
        "lowerarm01.R": [((0, 1, 0), 88), ((1, 0, 0), -15)],
        "wrist.R": [((0, 1, 0), -15), ((1, 0, 0), 75)],
        "neck01": [((0, 0, 1), -12), ((1, 0, 0), 6)],
        "upperleg01.R": [((1, 0, 0), -8)],
        "lowerleg01.R": [((1, 0, 0), 14)],
    }
    posed = posed_copy(rest, skeleton, turns, curled(45, 15))
    at = points(posed)
    floor = at[:, 2].min()
    hips = at[(at[:, 2] > girdle - 0.35) & (at[:, 2] < girdle - 0.2)]
    trunk = trunk_of(posed)
    skirt = hanging_cloth(trunk, girdle - 0.03, floor + 0.035, (0.0, float(hips[:, 1].mean())), 0.0, 0.13, 22, 0.012, 52,
                          clear_low=0.012)
    bpy.data.objects.remove(trunk)
    hidden_removed(posed, ~((at[:, 2] > floor + 0.11) & (at[:, 2] < girdle - 0.04)))
    return joined(posed, [skirt])


def youth(man):
    """A standing youth, his weight on his right leg, his left hand at his shoulder, a cloth knotted about his hips."""
    body, skeleton = man.made("youth")
    rest = subdivided(body)
    head = Head(man, man)
    carve_head(rest, head, "youth", 3)
    mark_face(rest, head)
    turns = {
        "upperarm01.R": [((0, 1, 0), -38), ((1, 0, 0), 4)],
        "lowerarm01.R": [((1, 0, 0), -12)],
        "upperarm01.L": [((0, 1, 0), 40), ((1, 0, 0), -10)],
        "lowerarm01.L": [((1, 0, 0), -125)],
        "spine01": [((0, 1, 0), 3)],
        "spine03": [((0, 1, 0), -5)],
        "neck01": [((0, 0, 1), -25), ((1, 0, 0), 8)],
        "upperleg01.L": [((1, 0, 0), -12), ((0, 1, 0), 4)],
        "lowerleg01.L": [((1, 0, 0), 24)],
        "foot.L": [((1, 0, 0), -10)],
        "upperleg01.R": [((0, 1, 0), -2)],
    }
    posed = posed_copy(rest, skeleton, turns, curled(30, 10))
    at = points(posed)
    centre = (0.0, float(at[(at[:, 2] > -0.05) & (at[:, 2] < 0.1)][:, 1].mean()))
    top, hem = 0.12, -0.2
    trunk = trunk_of(posed)
    cloth = hanging_cloth(trunk, top, hem, centre, 0.002, 0.02, 20, 0.011, 7, rows=32, clear_low=0.01)
    bpy.data.objects.remove(trunk)
    on = points(cloth)
    side = on[(on[:, 2] > top - 0.05) & (on[:, 2] < top - 0.01) & (on[:, 1] < centre[1])]
    pick = side[numpy.argmax(side[:, 0] - side[:, 1])]
    knot = ball("hip-knot", Matrix.Translation((pick[0] - 0.01, pick[1] + 0.01, top - 0.035)), (0.03, 0.035, 0.03), 7, 0.022, 0.008)
    hidden_removed(posed, ~((at[:, 2] > hem + 0.04) & (at[:, 2] < top - 0.03)))
    return joined(posed, [cloth, knot])


def orator(man):
    """A bearded man in a robe to his feet, a mantle across his chest from his left shoulder, his right hand raised
    before him and a staff in his left hand."""
    body, skeleton = man.made("orator")
    rest = subdivided(body)
    head = Head(man, man)
    carve_head(rest, head, "beard", 11)
    mark_face(rest, head)
    real = points(rest)
    x, y, z = real[:, 0], real[:, 1], real[:, 2]
    waist = 0.12
    neckline = man.landmark("joint-neck")[2] - 0.08 + 0.03 * step(y, -0.05, 0.05)
    torso = summed(rest, TORSO_BONES)
    tunic = evened(rest, step(numpy.clip(torso + summed(rest, ("upperarm01.L", "upperarm01.R")), 0, 1), 0.3, 0.7)
                   * step(neckline - z, -0.003, 0.008) * step(z - waist, -0.08, -0.06), 3)
    # The mantle crosses from the left shoulder to the right hip, front and back.
    across = (x - 0.17) * 0.865 - (z - 0.6) * 0.503
    mantle = evened(rest, step(0.075 - numpy.abs(across), 0.0, 0.012) * step(numpy.clip(torso, 0, 1), 0.2, 0.5)
                    * step(z - waist, -0.06, -0.03), 3)
    ridges = numpy.cos(across / 0.0125 * math.pi) ** 2
    falls = numpy.sin((x + numpy.sign(x) * (neckline - z) * 0.2) / 0.009) ** 2
    rim = numpy.exp(-0.5 * ((z - neckline + 0.006) / 0.005) ** 2) * tunic
    belt = numpy.exp(-0.5 * ((z - waist - 0.005) / 0.012) ** 2) * tunic
    moved(rest, normals(rest) * (tunic * (0.01 + 0.008 * falls * step(neckline - 0.03 - z, 0.0, 0.05)) + 0.006 * rim + 0.014 * belt
                                 + mantle * (0.022 + 0.012 * ridges))[:, None])
    turns = {
        "upperarm01.R": [((0, 1, 0), -40), ((1, 0, 0), -100), ((0, 0, 1), -25)],
        "lowerarm01.R": [((1, 0, 0), -15)],
        "upperarm01.L": [((0, 1, 0), 25)],
        "lowerarm01.L": [((1, 0, 0), -60)],
        "neck01": [((0, 0, 1), -14), ((1, 0, 0), -4)],
        "upperleg01.L": [((1, 0, 0), -6)],
        "lowerleg01.L": [((1, 0, 0), 12)],
    }
    bends = curled(15, 5, ("R",))
    bends.update(curled(80, 40, ("L",)))
    posed = posed_copy(rest, skeleton, turns, bends)
    at = points(posed)
    floor = at[:, 2].min()
    fist = at[summed(posed, ["finger%d-1.L" % f for f in (2, 3, 4, 5)]) > 0.5].mean(axis=0)
    hips = at[(at[:, 2] > waist - 0.15) & (at[:, 2] < waist - 0.05)]
    trunk = trunk_of(posed)
    robe = hanging_cloth(trunk, waist - 0.01, floor + 0.03, (0.0, float(hips[:, 1].mean())), 0.0, 0.09, 18, 0.014, 21, clear_low=0.012)
    bpy.data.objects.remove(trunk)
    hidden_removed(posed, ~((at[:, 2] > floor + 0.1) & (at[:, 2] < waist - 0.03) & (armish(posed) < 0.3)))
    staff = bpy.data.objects.new("staff", bpy.data.meshes.new("staff"))
    bpy.context.collection.objects.link(staff)
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=16, radius1=0.017, radius2=0.017, depth=1.0)
    height = at[:, 2].max() + 0.12 - floor
    bmesh.ops.transform(bm, matrix=Matrix.Translation((fist[0], fist[1], floor + height / 2)) @ Matrix.Diagonal((1.0, 1.0, height, 1.0)), verts=bm.verts)
    bm.to_mesh(staff.data)
    bm.free()
    return joined(posed, [robe, staff])


def collapsed(obj, triangles, keep):
    """obj collapsed to about this many triangles, holding harder on to where keep is high, as places and triangles."""
    copy = obj.copy()
    copy.data = obj.data.copy()
    bpy.context.collection.objects.link(copy)
    fewer = copy.modifiers.new("fewer", "DECIMATE")
    fewer.decimate_type = "COLLAPSE"
    fewer.use_collapse_triangulate = True
    fewer.ratio = min(1.0, triangles / sum(len(p.vertices) - 2 for p in copy.data.polygons))
    group = copy.vertex_groups.new(name="keep-less")
    for i, k in enumerate(keep):
        group.add([i], float(1.0 - 0.6 * k), "REPLACE")
    fewer.vertex_group = group.name
    depsgraph = bpy.context.evaluated_depsgraph_get()
    mesh = copy.evaluated_get(depsgraph).to_mesh()
    mesh.calc_loop_triangles()
    places = numpy.empty(len(mesh.vertices) * 3)
    mesh.vertices.foreach_get("co", places)
    corners = numpy.empty(len(mesh.loop_triangles) * 3, dtype=numpy.int64)
    mesh.loop_triangles.foreach_get("vertices", corners)
    copy.evaluated_get(depsgraph).to_mesh_clear()
    bpy.data.objects.remove(copy)
    return places.reshape(-1, 3), corners.reshape(-1, 3)


def packed(name, figure):
    keep = group_values(figure, "face")
    out = [struct.pack("<B", len(name)) + name.encode("ascii")]
    floor = points(figure)[:, 2].min()
    places, corners = collapsed(figure, FIGURE_TRIANGLES, keep)
    places[:, 2] -= floor
    out.append(struct.pack("<II", len(places), len(corners)) + places.astype("<f4").tobytes() + corners.astype("<u4").tobytes())
    print("%-16s %6d vertices %6d triangles  x %.3f to %.3f  y %.3f to %.3f  z 0 to %.3f" % (
        name, len(places), len(corners), places[:, 0].min(), places[:, 0].max(), places[:, 1].min(), places[:, 1].max(), places[:, 2].max()))
    return b"".join(out)


def main():
    arguments = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if not arguments:
        raise SystemExit(__doc__)
    data = arguments[0]
    man = Body(data, [("male", 1.0), ("muscle", MUSCLE)])
    woman = Body(data, [("female", 1.0)])
    makers = [("bust_male", lambda: bust(man, man, False, 3)), ("bust_female", lambda: bust(man, woman, True, 5)),
              ("statue_female", lambda: chiton_woman(man, woman)), ("statue_male", lambda: youth(man)),
              ("statue_orator", lambda: orator(man))]
    data_out = [b"SCLP", struct.pack("<II", 1, len(makers))]
    for name, make in makers:
        for thing in list(bpy.data.objects):
            bpy.data.objects.remove(thing)
        data_out.append(packed(name, make()))
    with open(OUTPUT, "wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as gz:
            gz.write(b"".join(data_out))
    print("wrote %s, %d bytes" % (os.path.normpath(OUTPUT), os.path.getsize(OUTPUT)))


if __name__ == "__main__":
    main()
