"""Builds the mod's own arms, with hands that have fingers, and packs them for the jar.

Run headless with Blender 4.4, from the repository root:

    blender --background --factory-startup --python tools/models/arms.py -- DATA [--renders DIR] [--no-pack] [--mesh]

DATA is a folder holding these files out of the MPFB 2 add-on's `data` folder (MakeHuman's
base mesh and what goes with it, which its makers give away under CC0, see
THIRD-PARTY-NOTICES.md): `base.obj`, `rig.default.json`, `weights.default.json` and, named
`male.target`, `female.target` and `muscle.target`, the unpacked macro targets
`caucasian-male-young`, `caucasian-female-young` and `universal-male-young-maxmuscle-averageweight`,
and `mpfb_fingernails.jpg` out of its `data/textures`, which says where on the base mesh the nails are.
`--mesh` writes the mesh alone, in seconds; without it the skin is baked too, on the processor, in a few minutes.

The right arm is cut out of the base mesh from the shoulder down, with the bones of the
default rig that move it and their weights, smoothed once, and posed here for each way a
hand holds something. The left arm is the right one mirrored, which the mod does.

It writes, under src/textures/models for tools/textures/compress.py to pack for the jar,
arms.colour.gz and arms.surface.gz, which `pictures` describes, and under
src/main/resources/models arms.mesh.gz. All of that is little-endian:

    int   magic 0x41524D53, version
    int   bones, vertices, indices, poses, parts
    bones:    for each, a byte of its name's length and the name, an int of its parent
              (-1 for the first), then sixteen floats: the matrix from the bone's own axes
              to the model's, as it rests, by columns
    vertices: for each, three floats of place, three of normal, four of tangent (the last its
              handedness), two of texture place, three of the female arm's place less the
              male's, four bytes of bones and four of their weights, which add up to 255,
              and a float of how far along the arm it is: 0 at the shoulder, 1 at the elbow,
              2 at the wrist, 3 at the finger tips
    indices:  ints, three to a triangle
    parts:    for each, a byte of its name's length and the name, then two ints: its first
              index and how many it has. They are `arm`, the bare arm; `sleeve`, what is worn
              over it from the shoulder to the wrist; and `glove`, what is worn over the hand
    poses:    for each, a byte of its name's length and the name, then for each bone four
              floats: the quaternion (w, x, y, z) that turns the bone from rest, in its own
              axes, then three floats: the middle of what the hand holds so, with the hand at rest
    lines:    three times three floats, each of length one: the way the hand at rest runs to
              its finger tips, the way across it to the thumb's side, and the way out of its palm

Lengths are metres. The model's axes are the base mesh's as Blender has them: x to the
figure's left, y to its back, z up, and the arm rests held out to the side and a little
forward, as the base mesh has it.
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
from mathutils import Euler, Matrix, Quaternion, Vector

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "src", "main", "resources", "models")
PICTURES = os.path.join(HERE, "..", "..", "src", "textures", "models")

SIDE = "R"
# For each bone, the matrix that carries a corner of the woman's arm from her bone to the man's.
CARRY = {}
ARM_BONES = ["upperarm01", "upperarm02", "lowerarm01", "lowerarm02", "wrist",
             "metacarpal1", "metacarpal2", "metacarpal3", "metacarpal4",
             "finger1-1", "finger1-2", "finger1-3", "finger2-1", "finger2-2", "finger2-3",
             "finger3-1", "finger3-2", "finger3-3", "finger4-1", "finger4-2", "finger4-3",
             "finger5-1", "finger5-2", "finger5-3"]
# What of the body's weight a corner has to give to the arm's bones to be part of the arm.
ARM_SHARE = 0.5
MUSCLE = 0.35
WEIGHTS_EVENED = 4


def blender(point):
    """A point of the base mesh, which is in decimetres with y up, as Blender has it: metres, z up."""
    return Vector((point[0] * 0.1, -point[2] * 0.1, point[1] * 0.1))


def read_obj(path):
    places, uvs, faces, groups = [], [], [], {}
    group = None
    with open(path, encoding="utf-8") as file:
        for line in file:
            part = line.split()
            if not part:
                continue
            if part[0] == "v":
                places.append([float(part[1]), float(part[2]), float(part[3])])
            elif part[0] == "vt":
                uvs.append((float(part[1]), float(part[2])))
            elif part[0] == "g":
                group = part[1]
            elif part[0] == "f":
                corners = [tuple(int(n) - 1 for n in corner.split("/")[:2]) for corner in part[1:]]
                faces.append((group, corners))
                groups.setdefault(group, set()).update(corner[0] for corner in corners)
    return numpy.array(places), uvs, faces, groups


def read_target(path):
    moves = {}
    with open(path, encoding="utf-8") as file:
        for line in file:
            part = line.split()
            if len(part) == 4 and not part[0].startswith("#"):
                moves[int(part[0])] = (float(part[1]), float(part[2]), float(part[3]))
    return moves


def shaped(places, *targets):
    out = places.copy()
    for moves, weight in targets:
        for index, move in moves.items():
            out[index] += numpy.array(move) * weight
    return out


def joint(end, places, groups):
    if end["strategy"] == "CUBE":
        cube = sorted(groups[end["cube_name"]])
        return blender(places[cube].mean(axis=0))
    if end["strategy"] == "VERTEX":
        return blender(places[end["vertex_index"]])
    if end["strategy"] == "MEAN":
        return blender(places[end["vertex_indices"]].mean(axis=0))
    raise SystemExit("a bone end is placed by " + end["strategy"] + ", which this does not know")


def build(data):
    places, uvs, faces, groups = read_obj(os.path.join(data, "base.obj"))
    male = shaped(places, (read_target(os.path.join(data, "male.target")), 1.0), (read_target(os.path.join(data, "muscle.target")), MUSCLE))
    female = shaped(places, (read_target(os.path.join(data, "female.target")), 1.0))
    with open(os.path.join(data, "rig.default.json"), encoding="utf-8") as file:
        rig = json.load(file)
    with open(os.path.join(data, "weights.default.json"), encoding="utf-8") as file:
        weights = json.load(file)["weights"]

    names = [bone + "." + SIDE for bone in ARM_BONES]
    share = numpy.zeros(len(places))
    total = numpy.zeros(len(places))
    for bone, pairs in weights.items():
        for index, weight in pairs:
            total[index] += weight
            if bone in names:
                share[index] += weight
    share = numpy.divide(share, total, out=numpy.zeros_like(share), where=total > 0)

    kept = [corners for group, corners in faces if group == "body" and all(share[v] >= ARM_SHARE for v, _ in corners)]
    used = sorted({v for corners in kept for v, _ in corners})
    renumber = {old: new for new, old in enumerate(used)}

    mesh = bpy.data.meshes.new("arm")
    mesh.from_pydata([blender(male[v]) for v in used], [], [[renumber[v] for v, _ in corners] for corners in kept])
    mesh.update()
    layer = mesh.uv_layers.new(name="base")
    at = 0
    for corners in kept:
        for _, uv in corners:
            layer.data[at].uv = uvs[uv]
            at += 1
    arm = bpy.data.objects.new("arm", mesh)
    bpy.context.collection.objects.link(arm)
    # The female arm rides along as a shape of the same mesh, so that smoothing treats both alike.
    arm.shape_key_add(name="male")
    key = arm.shape_key_add(name="female")
    for new, old in enumerate(used):
        key.data[new].co = blender(female[old])

    for name in names:
        arm.vertex_groups.new(name=name)
    first = arm.vertex_groups[names[0]]
    given = numpy.zeros(len(used))
    for bone, pairs in weights.items():
        if bone in names:
            for index, weight in pairs:
                if index in renumber and weight > 0:
                    arm.vertex_groups[bone].add([renumber[index]], weight / total[index], "REPLACE")
                    given[renumber[index]] += weight / total[index]
    # What a corner near the shoulder gave to the body's bones goes to the upper arm.
    for new in range(len(used)):
        if given[new] < 0.999:
            first.add([new], 1.0 - given[new], "ADD")

    # The web between the thumb and the first finger hangs half on the thumb's second bone and half on the
    # first finger's bone in the palm, with next to nothing between: with the thumb brought round it is drawn
    # out into flaps. The weights are evened out with their neighbours' a few times over, which spreads them.
    beside = [set() for _ in mesh.vertices]
    for edge in mesh.edges:
        beside[edge.vertices[0]].add(edge.vertices[1])
        beside[edge.vertices[1]].add(edge.vertices[0])
    hung = [{names[g.group]: g.weight for g in corner.groups} for corner in mesh.vertices]
    for _ in range(WEIGHTS_EVENED):
        evened = []
        for index, own in enumerate(hung):
            mixed = {bone: weight * 0.5 for bone, weight in own.items()}
            for other in beside[index]:
                for bone, weight in hung[other].items():
                    mixed[bone] = mixed.get(bone, 0.0) + weight * 0.5 / len(beside[index])
            evened.append(mixed)
        hung = evened
    for index, own in enumerate(hung):
        whole = sum(own.values())
        for bone, weight in own.items():
            arm.vertex_groups[bone].add([index], weight / whole, "REPLACE")

    def bones_of(label, shape):
        armature = bpy.data.armatures.new(label)
        made = bpy.data.objects.new(label, armature)
        bpy.context.collection.objects.link(made)
        bpy.context.view_layer.objects.active = made
        bpy.ops.object.mode_set(mode="EDIT")
        for name in names:
            said = rig[name]
            bone = armature.edit_bones.new(name)
            bone.head = joint(said["head"], shape, groups)
            bone.tail = joint(said["tail"], shape, groups)
            bone.roll = said["roll"]
            if said["parent"] in armature.edit_bones:
                bone.parent = armature.edit_bones[said["parent"]]
        bpy.ops.object.mode_set(mode="OBJECT")
        return made

    skeleton = bones_of("rig", male)
    # The woman's arm has bones of its own lengths. Her arm is drawn on the man's, each corner
    # carried from her bones to his, so it is her arm's shape at his arm's reach.
    hers = bones_of("rig-female", female)
    global CARRY
    CARRY = {name: skeleton.data.bones[name].matrix_local @ hers.data.bones[name].matrix_local.inverted() for name in names}
    bpy.data.objects.remove(hers)

    smooth = arm.modifiers.new("smooth", "SUBSURF")
    smooth.levels = 1
    smooth.render_levels = 1
    skin = arm.modifiers.new("skin", "ARMATURE")
    skin.object = skeleton
    for polygon in mesh.polygons:
        polygon.use_smooth = True
    return arm, skeleton, names


FINGERS = (2, 3, 4, 5)

# How a hand holds each kind of thing, in degrees. For each of the four fingers, index
# first: how far it is bent at the knuckle, at its middle joint and at its last, and how far
# it is spread towards the thumb's side. For the thumb: how far it is brought round under
# the palm, how far in towards the index finger, how far its root is swung towards the
# finger tips on the palm's side, and how far it is bent at its two joints.
# Last, how far the ring and little fingers' side of the palm is cupped.
POSES = {
    "open": {"fingers": [(8, 12, 6, 4), (10, 14, 8, 0), (12, 16, 8, -3), (14, 18, 8, -7)], "thumb": (0, 10, 0, 11, 20), "cup": 0},
    # Round the grip of a pistol or the wrist of a stock, the first finger laid along the frame and the thumb
    # lying forward along the near side: brought over the top, it goes through whatever the top of the weapon is.
    "grip": {"fingers": [(6, 14, 8, 4), (26, 82, 46, 0), (30, 84, 48, 0), (34, 86, 50, 0)], "thumb": (12, 36, 0, 6, 4), "cup": 10},
    # The first finger on the trigger, and the thumb lifted off the palm and swung in and forward so that, over the top of a
    # handgun's grip, it lies forward along the frame on the far side above the other hand's: laid as in "grip" it ran up
    # across the back of the grip in the eye's line.
    "trigger": {"fingers": [(24, 60, 28, 2), (26, 82, 46, 0), (30, 84, 48, 0), (34, 86, 50, 0)], "thumb": (-15, 60, 20, 10, 5), "cup": 10},
    # Under a fore-end, which lies in the palm with the fingers up its far side and the thumb up its near side.
    "fore": {"fingers": [(40, 72, 38, 2), (42, 74, 40, 0), (44, 76, 40, 0), (46, 78, 42, -2)], "thumb": (10, 20, -10, 15, 10), "cup": 12},
    # A fist round a handle, the thumb lying up it: wrapped over, it goes through a handle any thicker than the fist was made for.
    "handle": {"fingers": [(48, 72, 38, 0), (52, 74, 40, 0), (56, 76, 42, 0), (60, 78, 44, 0)], "thumb": (16, 34, 0, 8, 6), "cup": 14},
    # Something small held between the thumb and the first two fingers: a cartridge, a bolt's knob.
    "pinch": {"fingers": [(52, 40, 18, 0), (56, 44, 20, 0), (64, 84, 50, 0), (68, 88, 54, 0)], "thumb": (40, 30, 0, 20, 15), "cup": 8},
    # The knob of a bolt, in the crook of the first two fingers with the thumb under it.
    "knob": {"fingers": [(58, 44, 22, 3), (60, 48, 24, 0), (68, 86, 50, 0), (72, 90, 54, 0)], "thumb": (46, 16, 0, 16, 12), "cup": 8},
    # Over the top of a pistol's slide, which is pinched between the heel of the thumb and the fingers.
    "slide": {"fingers": [(58, 52, 22, 0), (60, 54, 24, 0), (62, 56, 24, 0), (64, 58, 26, 0)], "thumb": (30, 25, 0, 10, 5), "cup": 6},
    # Round a magazine, held by its sides with its foot in the palm.
    "magazine": {"fingers": [(40, 74, 40, 0), (42, 76, 42, 0), (44, 78, 44, 0), (48, 82, 46, 0)], "thumb": (25, 30, -10, 20, 20), "cup": 10},
}


class Hand:
    """The hand's own lines at rest, in the model's axes, from the rig's joints."""

    def __init__(self, skeleton):
        bones = skeleton.data.bones
        self.bones = bones
        wrist = bones["wrist." + SIDE].head_local
        first, last = bones["finger2-1." + SIDE].head_local, bones["finger5-1." + SIDE].head_local
        middle = bones["finger3-1." + SIDE].head_local
        self.wrist = wrist.copy()
        self.along = (middle - wrist).normalized()
        across = first - last
        self.across = (across - self.along * across.dot(self.along)).normalized()
        # Out of the palm. The fingers rest a little curled, towards the palm: that says which side it is.
        self.out = self.along.cross(self.across).normalized()
        curl = (bones["finger3-3." + SIDE].tail_local - bones["finger3-1." + SIDE].head_local).normalized()
        if curl.dot(self.out) < 0:
            self.out = -self.out
        self.knuckles = (first + last) / 2
        self.hinge = {}
        for finger in (1,) + FINGERS:
            # A finger bends about the line square to its first and last bones as they rest, a little curled.
            one = self.way("finger%d-%d" % (finger, 2 if finger == 1 else 1))
            three = self.way("finger%d-3" % finger)
            self.hinge[finger] = one.cross(three).normalized()
        # The hand rests a little curled, and a pose says how far each joint is bent from straight.
        self.rests = {}
        for finger in (1,) + FINGERS:
            chain = ["finger1-1", "finger1-2", "finger1-3"] if finger == 1 else ["metacarpal%d" % (finger - 1)] + ["finger%d-%d" % (finger, n) for n in (1, 2, 3)]
            for before, after in zip(chain, chain[1:]):
                self.rests[after] = self.bent(self.way(before), self.way(after), self.hinge[finger])

    @staticmethod
    def bent(before, after, hinge):
        """How far, in degrees, one line is turned from another about a hinge."""
        a = (before - hinge * before.dot(hinge)).normalized()
        b = (after - hinge * after.dot(hinge)).normalized()
        return math.degrees(math.atan2(a.cross(b).dot(hinge), a.dot(b)))

    def way(self, bone):
        bone = self.bones[bone + "." + SIDE]
        return (bone.tail_local - bone.head_local).normalized()

    def at(self, along, across, out):
        """A point given from the middle of the knuckles: towards the finger tips, towards the thumb's side, out of the palm."""
        return self.knuckles + self.along * along + self.across * across + self.out * out

    def turn(self, bone, *turns):
        """The quaternion, in a bone's own axes, of turns about lines of the model, the first done first."""
        rest = self.bones[bone + "." + SIDE].matrix_local.to_quaternion()
        result = Quaternion()
        for axis, degrees in turns:
            result = Quaternion(rest.inverted() @ axis, math.radians(degrees)) @ result
        return result


def pose(skeleton, hand, name):
    said = POSES[name]
    turns = {bone.name: Quaternion() for bone in skeleton.pose.bones}
    for finger, (knuckle, middle, last, spread) in zip(FINGERS, said["fingers"]):
        hinge = hand.hinge[finger]
        turns["finger%d-1.%s" % (finger, SIDE)] = hand.turn("finger%d-1" % finger, (hinge, knuckle - hand.rests["finger%d-1" % finger]), (hand.out, -spread))
        turns["finger%d-2.%s" % (finger, SIDE)] = hand.turn("finger%d-2" % finger, (hinge, middle - hand.rests["finger%d-2" % finger]))
        turns["finger%d-3.%s" % (finger, SIDE)] = hand.turn("finger%d-3" % finger, (hinge, last - hand.rests["finger%d-3" % finger]))
    under, towards, forward, middle, last = said["thumb"]
    turns["finger1-1." + SIDE] = hand.turn("finger1-1", (hand.along, -under), (hand.out, towards), (hand.across, forward))
    turns["finger1-2." + SIDE] = hand.turn("finger1-2", (hand.hinge[1], middle - hand.rests["finger1-2"]))
    turns["finger1-3." + SIDE] = hand.turn("finger1-3", (hand.hinge[1], last - hand.rests["finger1-3"]))
    turns["metacarpal3." + SIDE] = hand.turn("metacarpal3", (hand.along, -said["cup"] * 0.5))
    turns["metacarpal4." + SIDE] = hand.turn("metacarpal4", (hand.along, -said["cup"]))
    for bone in skeleton.pose.bones:
        bone.rotation_mode = "QUATERNION"
        bone.rotation_quaternion = turns[bone.name]
    bpy.context.view_layer.update()
    return turns


def flat(colour):
    material = bpy.data.materials.new("flat")
    material.diffuse_color = colour
    material.roughness = 0.6
    return material


def box(hand, name, middle, long, wide, deep, tilt=0.0):
    """Something to hold, for the stills: its length along the knuckles, its width towards the finger tips, its depth out of the palm."""
    mesh = bpy.data.meshes.new(name)
    block = bmesh.new()
    bmesh.ops.create_cube(block, size=1.0)
    bmesh.ops.scale(block, vec=(wide, long, deep), verts=block.verts)
    block.to_mesh(mesh)
    block.free()
    thing = bpy.data.objects.new(name, mesh)
    frame = Matrix((hand.along, hand.across, hand.out)).transposed().to_4x4() @ Matrix.Rotation(math.radians(tilt), 4, "Z")
    frame.translation = hand.at(*middle)
    thing.matrix_world = frame
    mesh.materials.append(flat((0.08, 0.09, 0.1, 1.0)))
    bpy.context.collection.objects.link(thing)
    return thing


def rod(hand, name, middle, long, radius, tilt=0.0):
    mesh = bpy.data.meshes.new(name)
    block = bmesh.new()
    bmesh.ops.create_cone(block, cap_ends=True, segments=32, radius1=radius, radius2=radius, depth=long)
    block.to_mesh(mesh)
    block.free()
    thing = bpy.data.objects.new(name, mesh)
    frame = Matrix((hand.along, hand.out, hand.across)).transposed().to_4x4() @ Matrix.Rotation(math.radians(tilt), 4, "Y")
    frame.translation = hand.at(*middle)
    thing.matrix_world = frame
    mesh.materials.append(flat((0.08, 0.09, 0.1, 1.0)))
    bpy.context.collection.objects.link(thing)
    return thing


def ball(hand, name, middle, radius):
    mesh = bpy.data.meshes.new(name)
    block = bmesh.new()
    bmesh.ops.create_uvsphere(block, u_segments=24, v_segments=16, radius=radius)
    block.to_mesh(mesh)
    block.free()
    thing = bpy.data.objects.new(name, mesh)
    thing.location = hand.at(*middle)
    mesh.materials.append(flat((0.08, 0.09, 0.1, 1.0)))
    bpy.context.collection.objects.link(thing)
    return thing


def held(hand, name):
    """What each pose is shown holding in the stills. The mod places the real thing."""
    if name in ("grip", "trigger"):
        return box(hand, name, HOLDS[name], 0.12, 0.046, 0.04, 8)
    if name == "fore":
        return rod(hand, name, (-0.012, 0.0, 0.03), 0.3, 0.024)
    if name == "handle":
        return rod(hand, name, HOLDS[name], 0.3, 0.021)
    if name == "knob":
        return ball(hand, name, HOLDS[name], 0.011)
    if name == "slide":
        return box(hand, name, (0.0, 0.0, 0.03), 0.16, 0.027, 0.03)
    if name == "magazine":
        return box(hand, name, (-0.004, 0.0, 0.026), 0.12, 0.03, 0.022)
    return None


def renders(arm, skeleton, folder, only):
    os.makedirs(folder, exist_ok=True)
    scene = bpy.context.scene
    scene.render.engine = "BLENDER_WORKBENCH"
    scene.display.shading.light = "STUDIO"
    scene.display.shading.show_cavity = True
    scene.display.shading.cavity_type = "BOTH"
    scene.display.shading.show_shadows = False
    scene.display.shading.color_type = "MATERIAL"
    scene.render.resolution_x = 800
    scene.render.resolution_y = 800
    scene.world = bpy.data.worlds.new("grey")
    scene.world.color = (0.2, 0.2, 0.22)
    if not arm.data.materials:
        arm.data.materials.append(flat((0.82, 0.62, 0.5, 1.0)))
    camera = bpy.data.objects.new("camera", bpy.data.cameras.new("camera"))
    camera.data.lens = 95
    bpy.context.collection.objects.link(camera)
    scene.camera = camera
    hand = Hand(skeleton)
    middle = hand.at(0.01, 0.0, 0.01)
    views = {"back": -hand.out, "palm": hand.out, "thumb": hand.across, "little": -hand.across, "tips": hand.along + hand.out * 0.3,
             "three": hand.across * 0.6 - hand.out * 0.5 + hand.along * 0.6}
    print("at rest the joints are bent", {name: round(bend) for name, bend in hand.rests.items()})
    for name in only or POSES:
        pose(skeleton, hand, name)
        thing = held(hand, name)
        for view, way in views.items():
            look = -way.normalized()
            up = hand.along if abs(look.dot(hand.along)) < 0.9 else hand.across
            side = look.cross(up).normalized()
            camera.matrix_world = Matrix((side, side.cross(look), -look)).transposed().to_4x4()
            camera.location = middle - look * 0.62
            scene.render.filepath = os.path.join(folder, name + "-" + view + ".png")
            bpy.ops.render.render(write_still=True)
        if thing is not None:
            bpy.data.objects.remove(thing)


HOLDS = {
    # Where what a pose holds lies in the hand: its middle from the middle of the knuckles
    # (towards the finger tips, towards the thumb's side, out of the palm), in metres.
    "open": (0.0, 0.0, 0.03), "grip": (0.012, 0.0, 0.029), "trigger": (0.012, 0.0, 0.029), "fore": (-0.012, 0.0, 0.03),
    "handle": (-0.004, 0.0, 0.026), "pinch": (0.03, 0.02, 0.04), "knob": (0.004, 0.014, 0.052), "slide": (0.0, 0.0, 0.03), "magazine": (-0.004, 0.0, 0.026),
}


class Reach:
    """How far along the arm a point is: 0 at the shoulder, 1 at the elbow, 2 at the wrist, 3 at the finger tips."""

    def __init__(self, skeleton, names):
        hand = Hand(skeleton)
        bones = skeleton.data.bones
        self.shoulder = bones["upperarm01." + SIDE].head_local
        self.elbow = bones["lowerarm01." + SIDE].head_local
        self.wrist = bones["wrist." + SIDE].head_local
        self.way = hand.along
        self.reach = (bones["finger3-3." + SIDE].tail_local - self.wrist).dot(hand.along)
        self.upper = {names.index(n + "." + SIDE) for n in ("upperarm01", "upperarm02")}
        self.fore = {names.index(n + "." + SIDE) for n in ("lowerarm01", "lowerarm02")}

    def along(self, place, most):
        shoulder, elbow, wrist = self.shoulder, self.elbow, self.wrist
        if most in self.upper:
            return max(0.0, min(1.0, (place - shoulder).dot(elbow - shoulder) / (elbow - shoulder).length_squared))
        if most in self.fore:
            return 1.0 + max(0.0, min(1.0, (place - elbow).dot(wrist - elbow) / (wrist - elbow).length_squared))
        return 2.0 + max(0.0, min(1.0, (place - wrist).dot(self.way) / self.reach))

    def of(self, arm, names, corner):
        groups = [group.name for group in arm.vertex_groups]
        most = max(corner.groups, key=lambda g: g.weight)
        return self.along(corner.co, names.index(groups[most.group]))


# Where a long sleeve's cuff is and where a glove's begins, as Reach counts.
CUFF_AT, GLOVE_FROM = 1.93, 1.86


def worn(arm, skeleton, names, name, keep, stands, smooths, rim):
    """
    Something worn over a length of the arm: the arm's own faces along that length, stood off
    the skin, with the shape of the muscles under it smoothed away as far as `smooths` says,
    and a rim of the cloth's thickness round its open ends.
    """
    reach = Reach(skeleton, names)
    thing = arm.copy()
    thing.data = arm.data.copy()
    thing.name = name
    bpy.context.collection.objects.link(thing)
    block = bmesh.new()
    block.from_mesh(thing.data)
    block.verts.ensure_lookup_table()
    deform = block.verts.layers.deform.verify()
    groups = [group.name for group in arm.vertex_groups]

    def along(vert):
        weights = vert[deform]
        most = max(weights.keys(), key=lambda g: weights[g])
        return reach.along(vert.co, names.index(groups[most]))

    gone = [face for face in block.faces if not all(keep(along(vert)) for vert in face.verts)]
    bmesh.ops.delete(block, geom=gone, context="FACES")
    block.to_mesh(thing.data)
    block.free()
    for modifier in list(thing.modifiers):
        thing.modifiers.remove(modifier)
    if smooths:
        smooth = thing.modifiers.new("even", "SMOOTH")
        smooth.factor = 0.6
        smooth.iterations = smooths
    out = thing.modifiers.new("stand", "DISPLACE")
    out.strength = stands
    out.mid_level = 0.0
    if smooths:
        # Cloth lies in folds: broad ones the width of a wrist and finer ones over them.
        for size, deep in ((0.045, 0.006), (0.016, 0.002)):
            folds = bpy.data.textures.new(name + "-folds", "CLOUDS")
            folds.noise_scale = size
            fold = thing.modifiers.new("folds", "DISPLACE")
            fold.texture = folds
            fold.texture_coords = "LOCAL"
            fold.strength = deep
            fold.mid_level = 0.5
    thick = thing.modifiers.new("rim", "SOLIDIFY")
    thick.thickness = rim
    thick.offset = -1.0
    thick.use_rim = True
    thick.use_rim_only = True
    round_off = thing.modifiers.new("smooth", "SUBSURF")
    round_off.levels = 1
    round_off.render_levels = 1
    skin_to = thing.modifiers.new("skin", "ARMATURE")
    skin_to.object = skeleton
    return thing


def evaluated(arm, skeleton, female):
    """The arm smoothed and at rest, as a mesh of triangles."""
    arm.data.shape_keys.key_blocks["female"].value = 1.0 if female else 0.0
    arm.modifiers["skin"].show_viewport = False
    bpy.context.view_layer.update()
    mesh = bpy.data.meshes.new_from_object(arm.evaluated_get(bpy.context.evaluated_depsgraph_get()))
    arm.modifiers["skin"].show_viewport = True
    block = bmesh.new()
    block.from_mesh(mesh)
    bmesh.ops.triangulate(block, faces=block.faces)
    block.to_mesh(mesh)
    block.free()
    return mesh


def corners(thing, skeleton, names, reach, vertices, indices):
    """Adds one thing's corners and triangles to the file's."""
    male, female = evaluated(thing, skeleton, False), evaluated(thing, skeleton, True)
    if len(male.vertices) != len(female.vertices):
        raise SystemExit("the two shapes of " + thing.name + " came out of smoothing with different corners")
    male.calc_tangents(uvmap="packed")
    groups = [group.name for group in thing.vertex_groups]
    along = reach.along
    uv = male.uv_layers["packed"].data
    found = {}
    first = len(indices)
    for loop in male.loops:
        corner = male.vertices[loop.vertex_index]
        key = (loop.vertex_index, round(uv[loop.index].uv.x, 6), round(uv[loop.index].uv.y, 6),
               round(loop.normal.x, 4), round(loop.normal.y, 4), round(loop.normal.z, 4))
        at = found.get(key)
        if at is None:
            weights = sorted(((names.index(groups[g.group]), g.weight) for g in corner.groups if g.weight > 0), key=lambda w: -w[1])[:4]
            total = sum(w for _, w in weights)
            shares = [int(round(w / total * 255)) for _, w in weights]
            shares[0] += 255 - sum(shares)
            while len(weights) < 4:
                weights.append((0, 0.0))
                shares.append(0)
            hers = female.vertices[loop.vertex_index].co
            hung = [(groups[g.group], g.weight) for g in corner.groups if g.weight > 0]
            whole = sum(weight for _, weight in hung)
            carried = Vector((0.0, 0.0, 0.0))
            for bone, weight in hung:
                carried += (CARRY[bone] @ hers) * (weight / whole)
            other = carried - corner.co
            at = len(vertices)
            found[key] = at
            vertices.append(struct.pack("<3f3f4f2f3f4B4Bf", *corner.co, *loop.normal, *loop.tangent, loop.bitangent_sign,
                                        uv[loop.index].uv.x, uv[loop.index].uv.y, *other, *(b for b, _ in weights), *shares,
                                        along(corner.co, weights[0][0])))
        indices.append(at)
    return first, len(indices) - first


def pack(things, skeleton, names, path):
    hand = Hand(skeleton)
    for bone in skeleton.pose.bones:
        bone.rotation_mode = "QUATERNION"
        bone.rotation_quaternion = Quaternion()
    bones = skeleton.data.bones
    reach = Reach(skeleton, names)
    shoulder, elbow, wrist = reach.shoulder, reach.elbow, reach.wrist
    vertices, indices = [], []
    parts = [(thing.name, *corners(thing, skeleton, names, reach, vertices, indices)) for thing in things]

    out = bytearray()
    out += struct.pack("<2i5i", 0x41524D53, 2, len(names), len(vertices), len(indices), len(POSES), len(parts))

    def named(name):
        said = name.encode("ascii")
        return struct.pack("<B", len(said)) + said

    for name in names:
        bone = bones[name]
        out += named(name[:-2])
        out += struct.pack("<i", names.index(bone.parent.name) if bone.parent else -1)
        out += struct.pack("<16f", *(bone.matrix_local[row][column] for column in range(4) for row in range(4)))
    out += b"".join(vertices)
    out += struct.pack("<%di" % len(indices), *indices)
    for name, first, count in parts:
        out += named(name)
        out += struct.pack("<2i", first, count)
    for name in POSES:
        turns = pose(skeleton, hand, name)
        out += named(name)
        for bone in names:
            out += struct.pack("<4f", *turns[bone])
        out += struct.pack("<3f", *hand.at(*HOLDS[name]))
    # The hand's own lines, which what it holds is laid along.
    for line in (hand.along, hand.across, hand.out):
        out += struct.pack("<3f", *line)
    with gzip.open(path, "wb", compresslevel=9) as file:
        file.write(bytes(out))
    print("wrote %s: %d bones, %d corners, %d triangles, %d poses, %d bytes" % (path, len(names), len(vertices), len(indices) // 3, len(POSES), len(out)))
    print("parts:", ", ".join("%s %d triangles" % (name, count // 3) for name, first, count in parts))
    print("lengths: upper arm %.4f, forearm %.4f, wrist to the middle finger's tip %.4f" % ((elbow - shoulder).length, (wrist - elbow).length, reach.reach))


TEXTURE = 2048
# The skin the pictures are painted as, in the coding a screen shows: the second palest of the
# game's five. The mod turns it to the skin the body wears.
SKIN = (0.937, 0.788, 0.553)


def linear(colour):
    return tuple(c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in colour)


def lay_out(arm):
    """A second layout of the texture, the arm's own: the base mesh's islands at one size of texel, packed to fill the square."""
    mesh = arm.data
    packed = mesh.uv_layers.new(name="packed")
    for at in range(len(packed.data)):
        packed.data[at].uv = mesh.uv_layers["base"].data[at].uv
    mesh.uv_layers.active = packed
    packed.active_render = True
    bpy.context.view_layer.objects.active = arm
    arm.select_set(True)
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.select_all(action="SELECT")
    bpy.ops.uv.average_islands_scale()
    bpy.ops.uv.pack_islands(rotate=True, margin=0.006)
    bpy.ops.object.mode_set(mode="OBJECT")


def mark(arm, skeleton):
    """Says of each corner how near it is to a knuckle, how much it is palm, how near a finger tip and how near the elbow."""
    hand = Hand(skeleton)
    bones = skeleton.data.bones
    mesh = arm.data
    marks = mesh.color_attributes.new(name="marks", type="FLOAT_COLOR", domain="POINT")
    joints = [bones["finger%d-%d.%s" % (finger, joint, SIDE)].head_local for finger in (1, 2, 3, 4, 5) for joint in (1, 2, 3)]
    tips = [bones["finger%d-3.%s" % (finger, SIDE)].tail_local for finger in (1, 2, 3, 4, 5)]
    elbow = bones["lowerarm01." + SIDE].head_local
    wrist = bones["wrist." + SIDE].head_local
    for corner in mesh.vertices:
        place, facing = corner.co, corner.normal
        beyond = (place - wrist).dot(hand.along)
        of_hand = max(0.0, min(1.0, beyond / 0.03 + 0.5))
        inward = facing.dot(hand.out)
        knuckle = max(math.exp(-((place - joint).length / 0.012) ** 2) for joint in joints) * max(0.0, min(1.0, 0.3 - inward * 1.4))
        palm = of_hand * max(0.0, min(1.0, inward * 1.6 + 0.35))
        tip = max(math.exp(-((place - end).length / 0.014) ** 2) for end in tips)
        point = math.exp(-((place - elbow).length / 0.05) ** 2)
        marks.data[corner.index].color = (knuckle, palm, tip, point)


def skin(data):
    """The skin as Cycles works it out: what `shown` is set to is what a bake of light given off takes."""
    material = bpy.data.materials.new("skin")
    material.use_nodes = True
    tree = material.node_tree
    nodes, links = tree.nodes, tree.links
    nodes.clear()

    def node(kind, **settings):
        made = nodes.new(kind)
        for name, value in settings.items():
            setattr(made, name, value)
        return made

    def value(number):
        made = node("ShaderNodeValue")
        made.outputs[0].default_value = number
        return made.outputs[0]

    def math_of(operation, a, b=None, clamp=False):
        made = node("ShaderNodeMath", operation=operation, use_clamp=clamp)
        for socket, given in zip(made.inputs, (a, b)):
            if given is None:
                continue
            if isinstance(given, (int, float)):
                socket.default_value = given
            else:
                links.new(given, socket)
        return made.outputs[0]

    def ramp(given, low, high):
        made = node("ShaderNodeMapRange", interpolation_type="SMOOTHSTEP")
        made.inputs["From Min"].default_value = low
        made.inputs["From Max"].default_value = high
        links.new(given, made.inputs["Value"])
        return made.outputs["Result"]

    def mixed(share, first, second):
        made = node("ShaderNodeMix", data_type="RGBA")
        for socket, given in ((made.inputs[0], share), (made.inputs[6], first), (made.inputs[7], second)):
            if isinstance(given, (int, float)):
                socket.default_value = given
            elif isinstance(given, tuple):
                socket.default_value = (*given, 1.0)
            else:
                links.new(given, socket)
        return made.outputs[2]

    def noise(scale, detail=4.0):
        made = node("ShaderNodeTexNoise")
        made.inputs["Scale"].default_value = scale
        made.inputs["Detail"].default_value = detail
        links.new(place, made.inputs["Vector"])
        return made.outputs["Fac"]

    place = node("ShaderNodeTexCoord").outputs["Object"]
    marks = node("ShaderNodeVertexColor", layer_name="marks")
    split = node("ShaderNodeSeparateColor")
    links.new(marks.outputs["Color"], split.inputs[0])
    knuckle, palm, tip = split.outputs[0], split.outputs[1], split.outputs[2]
    elbow = marks.outputs["Alpha"]
    base_uv = node("ShaderNodeUVMap", uv_map="base")
    nails = node("ShaderNodeTexImage")
    nails.image = bpy.data.images.load(os.path.join(data, "mpfb_fingernails.jpg"))
    nails.image.colorspace_settings.name = "Non-Color"
    links.new(base_uv.outputs[0], nails.inputs[0])
    nail = ramp(nails.outputs["Color"], 0.25, 0.6)

    plain = linear(SKIN)
    # Blotches the width of a thumb, a little redder or yellower, and a finer mottle over them.
    blotch = math_of("SUBTRACT", noise(38.0), 0.5)
    mottle = math_of("SUBTRACT", noise(260.0, 3.0), 0.5)
    red = mixed(math_of("ADD", 0.5, math_of("MULTIPLY", blotch, 1.5), clamp=True), tuple(c * f for c, f in zip(plain, (0.97, 1.03, 1.05))),
                tuple(c * f for c, f in zip(plain, (1.04, 0.95, 0.93))))
    shade = node("ShaderNodeMix", data_type="RGBA", blend_type="MULTIPLY")
    shade.inputs[0].default_value = 1.0
    links.new(red, shade.inputs[6])
    grain = math_of("ADD", 0.97, math_of("MULTIPLY", mottle, 0.08))
    spread = node("ShaderNodeCombineColor")
    for socket in spread.inputs[:3]:
        links.new(grain, socket)
    links.new(spread.outputs[0], shade.inputs[7])
    colour = shade.outputs[2]
    # Knuckles and finger tips are pinker, a palm is paler and pinker, an elbow darker.
    colour = mixed(math_of("MULTIPLY", knuckle, 0.55), colour, tuple(c * f for c, f in zip(plain, (1.0, 0.80, 0.78))))
    colour = mixed(math_of("MULTIPLY", tip, 0.3), colour, tuple(c * f for c, f in zip(plain, (1.03, 0.84, 0.84))))
    colour = mixed(math_of("MULTIPLY", palm, 0.4), colour, tuple(c * f for c, f in zip(plain, (1.08, 0.97, 1.02))))
    colour = mixed(math_of("MULTIPLY", elbow, 0.35), colour, tuple(c * f for c, f in zip(plain, (0.86, 0.80, 0.78))))
    colour = mixed(nail, colour, linear((0.88, 0.70, 0.66)))

    # How much of the way from the painted skin to the body's own a texel goes: a palm and a nail are pale on anyone.
    follows = math_of("SUBTRACT", math_of("SUBTRACT", 1.0, math_of("MULTIPLY", palm, 0.1)), math_of("MULTIPLY", nail, 0.45), clamp=True)
    rough = math_of("SUBTRACT", math_of("ADD", 0.56, math_of("MULTIPLY", mottle, 0.12)), math_of("MULTIPLY", nail, 0.3))

    # The skin's own relief: cells a millimetre across with furrows between, deeper and drawn across the finger at a knuckle.
    cells = node("ShaderNodeTexVoronoi", feature="DISTANCE_TO_EDGE")
    cells.inputs["Scale"].default_value = 700.0
    links.new(place, cells.inputs["Vector"])
    furrow = ramp(cells.outputs["Distance"], 0.0, 0.12)
    creases = node("ShaderNodeTexWave", wave_type="BANDS", bands_direction="Y")
    creases.inputs["Scale"].default_value = 260.0
    creases.inputs["Distortion"].default_value = 3.0
    creases.inputs["Detail"].default_value = 2.0
    links.new(place, creases.inputs["Vector"])
    height = math_of("ADD", math_of("MULTIPLY", furrow, 0.6), math_of("MULTIPLY", math_of("MULTIPLY", creases.outputs["Fac"], knuckle), 1.6))
    height = math_of("ADD", height, math_of("MULTIPLY", noise(1500.0, 2.0), 0.08))
    height = math_of("MULTIPLY", height, math_of("SUBTRACT", 1.0, nail))
    bump = node("ShaderNodeBump")
    bump.inputs["Strength"].default_value = 0.3
    bump.inputs["Distance"].default_value = 0.0008
    links.new(height, bump.inputs["Height"])

    surface = node("ShaderNodeBsdfPrincipled")
    links.new(colour, surface.inputs["Base Color"])
    links.new(rough, surface.inputs["Roughness"])
    links.new(bump.outputs["Normal"], surface.inputs["Normal"])
    glow = node("ShaderNodeEmission")
    out = node("ShaderNodeOutputMaterial")
    baked = node("ShaderNodeTexImage")
    spread_of = {}
    for name, socket in (("follows", follows), ("rough", rough)):
        combine = node("ShaderNodeCombineColor")
        for part in combine.inputs[:3]:
            links.new(socket, part)
        spread_of[name] = combine.outputs[0]

    def show(which):
        for link in list(out.inputs["Surface"].links):
            links.remove(link)
        if which is None:
            links.new(surface.outputs[0], out.inputs["Surface"])
            return
        for link in list(glow.inputs["Color"].links):
            links.remove(link)
        links.new(colour if which == "colour" else spread_of[which], glow.inputs["Color"])
        links.new(glow.outputs[0], out.inputs["Surface"])

    show(None)
    return material, baked, show


def bake(arm, skeleton, data):
    """Bakes the skin into pictures laid out as the arm's texture is: each a float array, rows from the bottom."""
    material, baked, show = skin(data)
    arm.data.materials.clear()
    arm.data.materials.append(material)
    for bone in skeleton.pose.bones:
        bone.rotation_mode = "QUATERNION"
        bone.rotation_quaternion = Quaternion()
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = 8
    scene.render.bake.margin = 10
    scene.render.bake.use_clear = True
    world = bpy.data.worlds.new("bake")
    world.light_settings.distance = 0.03
    scene.world = world
    target = bpy.data.images.new("bake", TEXTURE, TEXTURE, float_buffer=True, is_data=True)
    baked.image = target
    material.node_tree.nodes.active = baked
    for thing in bpy.context.selected_objects:
        thing.select_set(False)
    arm.select_set(True)
    bpy.context.view_layer.objects.active = arm
    out = {}

    def taken():
        pixels = numpy.empty(TEXTURE * TEXTURE * 4, dtype=numpy.float32)
        target.pixels.foreach_get(pixels)
        return pixels.reshape(TEXTURE, TEXTURE, 4)[..., :3].copy()

    for which in ("colour", "follows", "rough"):
        show(which)
        bpy.ops.object.bake(type="EMIT")
        out[which] = taken()
        print("baked", which, flush=True)
    show(None)
    bpy.ops.object.bake(type="NORMAL", normal_space="TANGENT")
    out["normal"] = taken()
    print("baked normal", flush=True)
    scene.cycles.samples = 96
    bpy.ops.object.bake(type="AO")
    out["light"] = taken()
    print("baked light", flush=True)
    return out


def encoded(colour):
    colour = numpy.clip(colour, 0.0, 1.0)
    return numpy.where(colour <= 0.0031308, colour * 12.92, 1.055 * numpy.power(colour, 1 / 2.4) - 0.055)


def pictures(baked, folder, preview):
    """
    arms.colour.gz: the skin's colour as a screen shows it, and how far it follows the body's own skin.
    arms.surface.gz: the way the surface faces off its own, across and along the texture, how smooth it is, and how open to the sky.
    Each is TEXTURE rows of TEXTURE texels of four bytes, from the bottom row up.
    """
    def bytes_of(array):
        return (numpy.clip(array, 0.0, 1.0) * 255.0 + 0.5).astype(numpy.uint8)

    colour = bytes_of(numpy.concatenate([encoded(baked["colour"]), baked["follows"][..., :1]], axis=2))
    surface = bytes_of(numpy.concatenate([baked["normal"][..., :2], 1.0 - baked["rough"][..., :1], baked["light"][..., :1]], axis=2))
    for name, array in (("arms.colour.gz", colour), ("arms.surface.gz", surface)):
        with gzip.open(os.path.join(folder, name), "wb", compresslevel=9) as packed:
            packed.write(array.tobytes())
        print("wrote", name, array.shape)
    if preview:
        os.makedirs(preview, exist_ok=True)
        for name, array in (("colour", colour[..., :3]), ("follows", colour[..., 3]), ("normal", surface[..., :3]), ("light", surface[..., 3])):
            picture = bpy.data.images.new(name, TEXTURE, TEXTURE)
            wide = numpy.ones((TEXTURE, TEXTURE, 4), dtype=numpy.float32)
            wide[..., :3] = (array if array.ndim == 3 else array[..., None]) / 255.0
            if name == "normal":
                wide[..., 2] = 1.0
            picture.pixels.foreach_set(wide.ravel())
            picture.filepath_raw = os.path.join(preview, "texture-" + name + ".png")
            picture.file_format = "PNG"
            picture.save()


def main():
    arguments = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if not arguments:
        raise SystemExit(__doc__)
    data = arguments[0]
    for thing in list(bpy.data.objects):
        bpy.data.objects.remove(thing)
    arm, skeleton, names = build(data)
    print("arm: %d corners, %d faces before smoothing" % (len(arm.data.vertices), len(arm.data.polygons)))
    lay_out(arm)
    mark(arm, skeleton)
    if "--no-pack" not in arguments:
        os.makedirs(OUT, exist_ok=True)
        sleeve = worn(arm, skeleton, names, "sleeve", lambda along: along < CUFF_AT, 0.007, 14, 0.004)
        glove = worn(arm, skeleton, names, "glove", lambda along: along > GLOVE_FROM, 0.0018, 0, 0.002)
        pack([arm, sleeve, glove], skeleton, names, os.path.join(OUT, "arms.mesh.gz"))
        for thing in (sleeve, glove):
            bpy.data.objects.remove(thing)
        if "--mesh" not in arguments:
            pictures(bake(arm, skeleton, data), PICTURES, arguments[arguments.index("--renders") + 1] if "--renders" in arguments else None)
    if "--renders" in arguments:
        renders(arm, skeleton, arguments[arguments.index("--renders") + 1], arguments[arguments.index("--only") + 1].split(",") if "--only" in arguments else None)


main()
