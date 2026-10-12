"""What each part of an interior is made of, as Blender node graphs to be baked into the textures the mod
draws with: colour, roughness, metal and the fine relief of the surface.

A graph works in object coordinates, in tiles, so a grain or a weave keeps one size on every part.
"""

import math
import os

import bpy

from .kit import GLASS, MIRROR

# What the vehicle's trim is coloured: the mod tints the parts that take it (see TINTED) at run time.
# Baked here as a mid grey so that the tint has the whole range to work in.
TRIM_GREY = (0.42, 0.42, 0.42)


class Graph:
    """The nodes of one material, written as sums. A value is a socket or a number."""

    def __init__(self, name):
        self.material = bpy.data.materials.new(name)
        self.material.use_nodes = True
        self.tree = self.material.node_tree
        self.nodes = self.tree.nodes
        self.shader = self.nodes["Principled BSDF"]
        coords = self.nodes.new("ShaderNodeTexCoord")
        self.where = coords.outputs["Object"]
        self.x, self.y, self.z = self.split(self.where)
        self.normal_in = coords.outputs["Normal"]
        self.sockets = {}

    def put(self, socket, value):
        if isinstance(value, (int, float)):
            socket.default_value = value
        elif isinstance(value, tuple):
            socket.default_value = (*value, 1.0) if len(value) == 3 else value
        else:
            self.tree.links.new(value, socket)

    def split(self, vector):
        node = self.nodes.new("ShaderNodeSeparateXYZ")
        self.put(node.inputs[0], vector)
        return node.outputs[0], node.outputs[1], node.outputs[2]

    def combine(self, x, y, z):
        node = self.nodes.new("ShaderNodeCombineXYZ")
        for socket, value in zip(node.inputs, (x, y, z)):
            self.put(socket, value)
        return node.outputs[0]

    def math(self, operation, *values):
        node = self.nodes.new("ShaderNodeMath")
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
        node = self.nodes.new("ShaderNodeMapRange")
        node.interpolation_type = "SMOOTHSTEP"
        self.put(node.inputs["Value"], value)
        self.put(node.inputs["From Min"], low)
        self.put(node.inputs["From Max"], high)
        return node.outputs[0]

    def colour(self, rgb):
        node = self.nodes.new("ShaderNodeRGB")
        node.outputs[0].default_value = (*rgb, 1.0)
        return node.outputs[0]

    def scaled(self, colour, by):
        node = self.nodes.new("ShaderNodeVectorMath")
        node.operation = "SCALE"
        self.put(node.inputs[0], colour)
        self.put(node.inputs["Scale"], by)
        return node.outputs[0]

    def mix(self, share, first, second):
        node = self.nodes.new("ShaderNodeMix")
        node.data_type = "RGBA"
        named = {socket.identifier: socket for socket in node.inputs}
        self.put(named["Factor_Float"], share)
        self.put(named["A_Color"], first)
        self.put(named["B_Color"], second)
        return next(socket for socket in node.outputs if socket.identifier == "Result_Color")

    def stretched(self, sx, sy, sz):
        node = self.nodes.new("ShaderNodeVectorMath")
        node.operation = "MULTIPLY"
        self.put(node.inputs[0], self.where)
        node.inputs[1].default_value = (sx, sy, sz)
        return node.outputs[0]

    def noise(self, scale, detail=3.0, roughness=0.5, vector=None):
        node = self.nodes.new("ShaderNodeTexNoise")
        self.put(node.inputs["Vector"], vector if vector is not None else self.where)
        self.put(node.inputs["Scale"], scale)
        self.put(node.inputs["Detail"], detail)
        self.put(node.inputs["Roughness"], roughness)
        return node.outputs[0]

    def cells(self, scale, vector=None, feature="F1", randomness=1.0):
        node = self.nodes.new("ShaderNodeTexVoronoi")
        node.feature = feature
        self.put(node.inputs["Vector"], vector if vector is not None else self.where)
        self.put(node.inputs["Scale"], scale)
        self.put(node.inputs["Randomness"], randomness)
        return node.outputs["Distance"]

    def wave(self, scale, distortion, vector=None, kind="BANDS", direction="X", detail=2.0):
        node = self.nodes.new("ShaderNodeTexWave")
        node.wave_type = kind
        if kind == "BANDS":
            node.bands_direction = direction
        self.put(node.inputs["Vector"], vector if vector is not None else self.where)
        self.put(node.inputs["Scale"], scale)
        self.put(node.inputs["Distortion"], distortion)
        self.put(node.inputs["Detail"], detail)
        return node.outputs["Fac"]

    def image(self, path, vector):
        node = self.nodes.new("ShaderNodeTexImage")
        node.image = bpy.data.images.load(path, check_existing=True)
        node.extension = "CLIP"
        self.put(node.inputs[0], vector)
        return node.outputs["Color"], node.outputs["Alpha"]

    def edges(self, radius=0.0025):
        """Where the surface turns: one on an edge that stands out, rounded over radius."""
        rounded = self.nodes.new("ShaderNodeBevel")
        rounded.samples = 8
        rounded.inputs["Radius"].default_value = radius
        facing = self.nodes.new("ShaderNodeNewGeometry")
        turn = self.nodes.new("ShaderNodeVectorMath")
        turn.operation = "DOT_PRODUCT"
        self.put(turn.inputs[0], rounded.outputs[0])
        self.put(turn.inputs[1], facing.outputs["True Normal"])
        pointy = facing.outputs["Pointiness"]
        return self.times(self.step(self.math("SUBTRACT", 1.0, turn.outputs["Value"]), 0.01, 0.12), self.step(pointy, 0.5, 0.53)), rounded.outputs[0]

    def finish(self, colour, rough, metal, height, tint=0.0, glow=0.0, rounded=None):
        self.sockets = {"colour": colour, "rough": rough, "metal": metal, "tint": tint, "glow": glow}
        self.put(self.shader.inputs["Base Color"], colour)
        self.put(self.shader.inputs["Roughness"], rough)
        self.put(self.shader.inputs["Metallic"], metal)
        bump = self.nodes.new("ShaderNodeBump")
        bump.inputs["Distance"].default_value = 1.0
        self.put(bump.inputs["Height"], height)
        if rounded is not None:
            self.tree.links.new(rounded, bump.inputs["Normal"])
        self.tree.links.new(bump.outputs[0], self.shader.inputs["Normal"])
        self.glow_node = self.nodes.new("ShaderNodeEmission")
        self.baked = self.nodes.new("ShaderNodeTexImage")
        return self

    def show(self, which):
        """Has the material give out one of its own properties as light, to be baked, or with None be itself."""
        out = self.nodes["Material Output"].inputs["Surface"]
        if which is None:
            self.tree.links.new(self.shader.outputs[0], out)
            return
        for link in list(self.glow_node.inputs["Color"].links):
            self.tree.links.remove(link)
        value = self.sockets[which]
        if isinstance(value, (int, float)):
            self.glow_node.inputs["Color"].default_value = (value, value, value, 1.0)
        elif isinstance(value, tuple):
            self.glow_node.inputs["Color"].default_value = (*value, 1.0)
        else:
            self.tree.links.new(value, self.glow_node.inputs["Color"])
        self.tree.links.new(self.glow_node.outputs[0], out)


def grain(g, cell, depth, wobble=0.25):
    """Pebbled leather or vinyl: cells cell across, each a soft dome, depth deep."""
    warped = g.add(g.where, g.scaled(g.combine(g.noise(1.0 / (cell * 9)), g.noise(1.0 / (cell * 9) + 3.1), g.noise(1.0 / (cell * 9) + 7.3)), cell * wobble * 4))
    d = g.cells(1.0 / cell, vector=warped, feature="F1")
    return g.times(g.math("SUBTRACT", 1.0, g.step(d, 0.15, 0.7)), depth)


def trimmed(g, base, rough, cell, depth, tint=1.0, wear=0.15):
    edge, rounded = g.edges(0.003)
    h = grain(g, cell, depth)
    variation = g.add(0.94, g.times(g.noise(9.0, 3.0), 0.12))
    colour = g.scaled(g.colour(base), variation)
    colour = g.mix(g.times(edge, wear), colour, g.scaled(g.colour(base), 1.25))
    r = g.add(rough, g.times(g.noise(30.0), 0.08), g.times(edge, -0.12))
    return g.finish(colour, r, 0.0, h, tint=tint, rounded=rounded)


def make(name, pictures):
    g = Graph(name)
    if name == "padded":
        return trimmed(g, TRIM_GREY, 0.58, 0.0028, 0.00018)
    if name == "leather":
        # Hide: a larger, softer grain than vinyl's, a little sheen, and the creases of being sat in.
        g2 = trimmed(g, TRIM_GREY, 0.48, 0.0032, 0.00022, wear=0.2)
        return g2
    if name in ("vinyl", "wheel"):
        return trimmed(g, TRIM_GREY, 0.50 if name == "vinyl" else 0.42, 0.0022 if name == "vinyl" else 0.0016, 0.00014)
    if name == "trim":
        return trimmed(g, TRIM_GREY, 0.62, 0.0009, 0.00006, wear=0.05)
    if name == "plastic":
        return trimmed(g, TRIM_GREY, 0.52, 0.0008, 0.00005, wear=0.06)
    if name == "black":
        return trimmed(g, (0.035, 0.035, 0.038), 0.46, 0.0008, 0.00005, tint=0.0, wear=0.25)
    if name == "cloth":
        # A velour: fine nap, a faint woven rib, darker where it is pressed.
        rib = g.wave(1.0 / 0.0042, 1.2, kind="BANDS", direction="Z")
        nap = g.noise(900.0, 2.0, 0.6)
        h = g.add(g.times(rib, 0.00010), g.times(nap, 0.00006))
        colour = g.scaled(g.colour(TRIM_GREY), g.add(0.88, g.times(nap, 0.18), g.times(g.noise(6.0, 2.0), 0.10)))
        return g.finish(colour, g.add(0.92, g.times(nap, 0.06)), 0.0, h, tint=1.0)
    if name == "carpet":
        fibre = g.noise(1400.0, 4.0, 0.8)
        tuft = g.cells(1.0 / 0.0025)
        h = g.add(g.times(fibre, 0.0003), g.times(g.math("SUBTRACT", 1.0, tuft), 0.0002))
        colour = g.scaled(g.colour((0.30, 0.30, 0.30)), g.add(0.7, g.times(fibre, 0.5)))
        return g.finish(colour, 0.98, 0.0, h, tint=1.0)
    if name == "lining":
        felt = g.noise(500.0, 5.0, 0.7)
        h = g.times(felt, 0.00008)
        colour = g.scaled(g.colour((0.66, 0.64, 0.60)), g.add(0.93, g.times(felt, 0.12)))
        return g.finish(colour, 0.94, 0.0, h, tint=0.35)
    if name == "chrome":
        edge, rounded = g.edges(0.002)
        pits = g.step(g.noise(260.0, 4.0), 0.62, 0.75)
        colour = g.colour((0.86, 0.87, 0.88))
        return g.finish(colour, g.add(0.10, g.times(pits, 0.25), g.times(g.noise(40.0), 0.05)), 1.0, g.times(pits, 0.00002), rounded=rounded)
    if name == "mat":
        # A rubber mat: ribs across it, worn smooth and dusty where the heels rest.
        ribs = g.wave(1.0 / 0.012, 0.0, kind="BANDS", direction="Y")
        dust = g.step(g.noise(14.0, 3.0), 0.45, 0.8)
        colour = g.mix(dust, g.colour((0.035, 0.035, 0.037)), g.colour((0.09, 0.085, 0.08)))
        return g.finish(colour, g.add(0.7, g.times(dust, 0.2)), 0.0, g.times(ribs, 0.0012))
    if name == "floor rubber":
        return g.finish(g.colour((0.045, 0.045, 0.047)), 0.85, 0.0, 0.0)
    if name == "race cloth":
        return g.finish(g.colour((0.035, 0.035, 0.04)), 0.9, 0.0, 0.0)
    if name == "cage":
        return g.finish(g.colour((0.70, 0.70, 0.72)), 0.3, 0.0, 0.0)
    if name == "painted":
        return g.finish(g.colour(TRIM_GREY), 0.32, 0.0, 0.0, tint=1.0)
    if name == "rubber":
        h = g.times(g.wave(1.0 / 0.004, 0.0, kind="BANDS", direction="X"), 0.0003)
        return g.finish(g.colour((0.028, 0.028, 0.03)), 0.82, 0.0, h)
    if name == "grille":
        # Round holes on a square pitch.
        pitch = 0.0035
        p = g.scaled(g.where, 1.0 / pitch)
        d = g.cells(1.0, vector=p, randomness=0.0)
        hole = g.step(d, 0.30, 0.22)
        return g.finish(g.mix(hole, g.colour((0.045, 0.045, 0.05)), g.colour((0.005, 0.005, 0.005))), g.add(0.55, g.times(hole, 0.4)), 0.0,
                        g.times(hole, -0.0006))
    if name == "webbing":
        rib = g.wave(1.0 / 0.0012, 0.4, kind="BANDS", direction="Z")
        return g.finish(g.scaled(g.colour(TRIM_GREY), g.add(0.85, g.times(rib, 0.15))), 0.85, 0.0, g.times(rib, 0.00008), tint=1.0)
    if name == "wood":
        # A wood-grained film: long wavy grain along the strip, darker figure through it.
        stretched = g.stretched(6.0, 120.0, 120.0)
        figure = g.wave(14.0, 9.0, vector=stretched, kind="BANDS", direction="X", detail=4.0)
        fine = g.noise(80.0, 4.0, vector=stretched)
        light, dark = g.colour((0.36, 0.17, 0.07)), g.colour((0.13, 0.055, 0.022))
        colour = g.mix(g.add(g.times(figure, 0.7), g.times(fine, 0.3)), dark, light)
        return g.finish(colour, 0.32, 0.0, g.times(fine, 0.00002))
    if name.startswith("dial "):
        centre, half_w, half_h = DIALS[name]
        picture = name[5:]
        u = g.math("SUBTRACT", 0.5, g.times(g.math("SUBTRACT", g.y, centre.y), 0.5 / half_w))
        w = g.add(0.5, g.times(g.math("SUBTRACT", g.z, centre.z), 0.5 / half_h))
        place = g.combine(u, w, 0.0)
        colour, _ = g.image(os.path.join(PICTURES_DIR[0], picture + ".png"), place)
        glow, _ = g.image(os.path.join(PICTURES_DIR[0], picture + "-glow.png"), place)
        lit = g.math("MAXIMUM", g.split(glow)[0], 0.0)
        return g.finish(colour, 0.32, 0.0, 0.0, glow=lit)
    if name == "gauge":
        return g.finish(g.colour((0.025, 0.026, 0.028)), 0.32, 0.0, 0.0)
    if name == "needle":
        return g.finish(g.colour((0.95, 0.28, 0.06)), 0.4, 0.0, 0.0, glow=1.0)
    if name == "lens":
        return g.finish(g.colour((0.01, 0.012, 0.012)), 0.06, 0.0, 0.0)
    if name == "lamp":
        frost = g.noise(600.0, 3.0)
        return g.finish(g.colour((0.82, 0.80, 0.74)), g.add(0.35, g.times(frost, 0.2)), 0.0, g.times(frost, 0.00003))
    raise SystemExit("no material " + name)


GRAPHS = {}
# What is drawn in one of the repeating close-up surfaces alone (detail.py): its surface, its colour (linear; the
# mod tints it as its material's tint says) and its roughness. These parts have no place in an interior's texture.
TILED = {
    "lining": ("felt", (0.66, 0.64, 0.60), 0.92),
    "carpet": ("carpet", (0.30, 0.30, 0.30), 0.98),
    "trim": ("plastic", TRIM_GREY, 0.62),
    "floor rubber": ("rubber", (0.045, 0.045, 0.047), 0.85),
    "painted": ("paint", TRIM_GREY, 0.32),
    "race cloth": ("velour", (0.035, 0.035, 0.04), 0.9),
    "cage": ("paint", (0.70, 0.70, 0.72), 0.3),
}
# The repeating surface each material with a place in the texture takes its fine relief from at arm's length.
DETAIL = {"leather": "vinyl", "padded": "vinyl", "vinyl": "vinyl", "wheel": "vinyl", "plastic": "plastic", "black": "plastic", "cloth": "velour", "chrome": "brushed",
          "rubber": "rubber", "mat": "rubber", "webbing": "velour"}
# The faces of the instruments: for each, its middle and its half width and half height on the face, which
# looks back along x at the driver. Set by the style as it builds them.
DIALS = {}
PICTURES_DIR = [None]


def dress_all(parts, pictures=None):
    for obj in parts:
        name = obj["material"]
        if obj["group"] in (GLASS, MIRROR):
            continue
        if name not in GRAPHS:
            GRAPHS[name] = make(name, pictures)
        obj.data.materials.clear()
        obj.data.materials.append(GRAPHS[name].material)
