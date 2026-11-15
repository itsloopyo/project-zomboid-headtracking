"""Props of appliances: what TileOverlays lays over fridges, cookers, washers and dryers (appliances_01), made.

    blender --background --factory-startup --python tools/models/props/appliances.py

What each overlay paints, measured off the pictures over the tiles the mod stands a model on:

- fridges (appliances_01_0..23): sheets of paper, notes and drawings pinned with magnets, bare magnets and letter magnets
  on the front, and boxes, tins and a gift box on the top. Each is made as a prop on the fridge.
- cookers (24..39, 44..47): a tea towel hung over the oven's handle, or lying at the back of the hob. The stains the others
  paint on the hob are not made: a stain is a soft decal, and a mark is a small block, so a scatter of them reads as debris.
  The dark tint of 44..47 is at most 61 of 255 and is not made, nor is 28's. One paints the oven door ajar: the oven with its
  door left off and the door a prop turned on its foot.
- the combo washer dryer (48..51, drawn as front_loader): 48 and 49 paint the door ajar. 50 and 51 paint rust spots on its
  cap, which are not made, for the same reason.
- top-loading washers (52, 53) paint the lid raised a little. 54 and 55 lie over a front facing north or west, which the
  picture does not show, so nothing is made. Dryers (60, 61) paint the door ajar, which is not made: the door is the body's own enamel and, ajar within the footprint, cannot be told from the shut one in first person, and 60's grime is not made either.
- 30 has no picture in the game's sheets.

A picture's pixels are placed on the model by their share of the sprite's front face, of its depth and of its height: the
mod's fridge is narrower than the game's sprite, so the raw projection would put a note painted at the sprite's edge off
the model. A colour is read where the picture paints it: the dye point of each prop is the point of the host that the
game's own projection puts on a pixel of the item.

Magnets and letter magnets are made small, as magnets are: the pictures paint them as big as a thumb. Everything else is
the picture's size as a share of the face, a sheet no bigger than a large page.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bmesh  # noqa: E402
import props  # noqa: E402
from mathutils import Matrix  # noqa: E402
from props import Prop, Swap  # noqa: E402

F = props.furniture
BATCH = "appliances"

HOSTS = {
    "fridge": F.fridge,
    "oven": F.oven,
    "washing_machine": F.washing_machine,
    "front_loader": F.front_loader,
}

# As furniture.fridge builds it: the face of its doors, and the top of its body.
FRIDGE_FACE, FRIDGE_TOP = -0.30 - 0.045, 1.70
STAND_OFF = 0.0005
MAGNET_RADIUS, MAGNET_HIGH = 0.015, 0.006
LETTER = (0.036, 0.042, 0.004)
SHEET_THICK = 0.0012
INK_THICK = 0.0006
SHEET_WIDEST, SHEET_TALLEST = 0.28, 0.36
# The biggest a thing standing on a fridge's top is: the pictures paint them bigger than a fridge top holds.
TOP_SIDE_MOST, TOP_HIGH_MOST, CAN_WIDEST = 0.30, 0.32, 0.06

# How far a door the game paints ajar stands open, in degrees.
FRONT_LOADER_AJAR, LID_AJAR, OVEN_AJAR = 12.0, 10.0, 10.0

# The cooker's handle as furniture.oven builds it: the bar's front and back faces and its top.
OVEN_BAR_FRONT = F.OVEN_FRONT - 0.022 - 0.04
OVEN_BAR_BACK = OVEN_BAR_FRONT + 0.016
OVEN_BAR_TOP = 0.705
# Where the dial panel stands at the back of the hob.
HOB_BACK = F.OVEN_BACK - 0.07

# Per overlay, in the fridge's own metres from the middle of its footprint on its floor: what is pinned to its front as
# (kind, x, z, dye point) for a magnet or a letter magnet and (kind, x, z, width, height, dye point) for a sheet, and what
# stands on its top as (kind, x, y, size, dye point). The dye point is where the game's own projection puts a pixel of
# the thing, which is where its colour is read.
FRIDGE_FACE_ITEMS = {
    0: [
        ('magnet', 0.085, 1.485, (-0.343, 1.119)),
        ('magnet', 0.129, 1.318, (-0.291, 0.984)),
        ('magnet', -0.049, 1.043, (-0.586, 0.694)),
        ('tile', -0.075, 1.366, (-0.569, 1.013)),
        ('sheet', 0.062, 1.377, 0.138, 0.208, (-0.291, 1.055)),
        ('sheet', -0.088, 0.85, 0.262, 0.36, (-0.673, 0.63)),
    ],
    1: [
        ('magnet', -0.071, 0.793, (0.326, 0.46)),
        ('sheet', -0.123, 1.341, 0.049, 0.05, (0.274, 0.977)),
        ('sheet', -0.129, 1.284, 0.061, 0.065, (0.256, 0.942)),
        ('tile', 0.239, 0.925, (0.759, 0.623)),
        ('tile', 0.16, 0.889, (0.638, 0.588)),
        ('sheet', -0.074, 1.001, 0.098, 0.172, (0.378, 0.666)),
        ('tile', -0.141, 0.99, (0.239, 0.708)),
        ('sheet', -0.018, 0.771, 0.037, 0.072, (0.412, 0.453)),
        ('sheet', -0.061, 0.703, 0.172, 0.237, (0.291, 0.389)),
    ],
    2: [
        ('magnet', -0.096, 0.876, (-0.621, 0.566)),
        ('magnet', 0.213, 1.072, (-0.169, 0.736)),
        ('magnet', -0.001, 0.849, (-0.482, 0.538)),
        ('magnet', -0.079, 0.59, (-0.569, 0.234)),
        ('magnet', 0.047, 0.53, (-0.43, 0.191)),
        ('magnet', -0.106, 0.412, (-0.638, 0.064)),
        ('magnet', -0.031, 0.42, (-0.534, 0.064)),
        ('magnet', -0.114, 0.335, (-0.621, -0.014)),
        ('magnet', 0.012, 0.399, (-0.43, 0.064)),
        ('tile', -0.131, 1.295, (-0.673, 0.97)),
        ('tile', -0.025, 1.323, (-0.516, 0.963)),
        ('tile', -0.119, 1.019, (-0.638, 0.63)),
        ('sheet', -0.075, 0.979, 0.137, 0.143, (-0.621, 0.581)),
        ('sheet', 0.162, 0.868, 0.238, 0.36, (-0.343, 0.68)),
        ('sheet', -0.137, 0.567, 0.037, 0.065, (-0.69, 0.212)),
        ('sheet', -0.119, 0.47, 0.1, 0.172, (-0.655, 0.17)),
        ('sheet', 0.006, 0.495, 0.175, 0.208, (-0.482, 0.198)),
        ('tile', -0.138, 0.344, (-0.655, 0.028)),
        ('tile', 0.038, 0.463, (-0.36, 0.134)),
        ('tile', 0.175, 0.477, (-0.256, 0.177)),
        ('sheet', -0.056, 0.355, 0.1, 0.115, (-0.586, -0.014)),
        ('sheet', 0.031, 0.359, 0.1, 0.136, (-0.395, 0.021)),
    ],
    3: [('magnet', -0.051, 1.459, (0.378, 1.105)), ('magnet', -0.142, 1.355, (0.239, 1.006)), ('sheet', -0.068, 1.399, 0.16, 0.208, (0.36, 1.14))],
    4: [
        ('magnet', -0.193, 1.371, (-0.603, 1.126)),
        ('magnet', 0.07, 1.237, (-0.274, 1.006)),
        ('sheet', -0.186, 1.286, 0.229, 0.221, (-0.655, 1.076)),
        ('sheet', 0.05, 1.071, 0.271, 0.35, (-0.326, 0.984)),
    ],
    5: [
        ('magnet', -0.219, 1.193, (0.013, 0.97)),
        ('magnet', -0.293, 0.954, (-0.073, 0.736)),
        ('sheet', -0.066, 1.279, 0.058, 0.079, (0.187, 1.084)),
        ('sheet', -0.182, 1.039, 0.262, 0.343, (0.083, 0.97)),
        ('sheet', 0.168, 0.829, 0.058, 0.093, (0.464, 0.644)),
    ],
    6: [
        ('magnet', -0.245, 1.354, (-0.621, 1.105)),
        ('magnet', 0.164, 1.443, (-0.117, 1.211)),
        ('magnet', -0.043, 1.254, (-0.36, 1.041)),
        ('magnet', -0.06, 1.137, (-0.378, 0.935)),
        ('magnet', 0.164, 1.198, (-0.117, 0.97)),
        ('magnet', 0.064, 1.017, (-0.256, 0.786)),
        ('magnet', -0.005, 0.789, (-0.291, 0.559)),
        ('magnet', -0.098, 0.42, (-0.43, 0.191)),
        ('magnet', 0.14, 0.436, (-0.135, 0.212)),
        ('magnet', 0.084, 0.298, (-0.187, 0.078)),
        ('sheet', -0.171, 1.229, 0.229, 0.321, (-0.551, 1.147)),
        ('sheet', 0.164, 1.386, 0.157, 0.15, (-0.1, 1.161)),
        ('tile', 0.136, 1.218, (-0.169, 1.006)),
        ('tile', 0.029, 1.111, (-0.274, 0.878)),
        ('tile', 0.114, 1.146, (-0.169, 0.906)),
        ('tile', 0.193, 1.186, (-0.031, 0.949)),
        ('sheet', 0.121, 0.886, 0.28, 0.35, (-0.117, 0.8)),
        ('sheet', -0.05, 0.75, 0.043, 0.079, (-0.395, 0.531)),
        ('sheet', -0.221, 0.336, 0.186, 0.207, (-0.621, 0.17)),
        ('sheet', -0.057, 0.271, 0.28, 0.36, (-0.447, 0.071)),
        ('sheet', 0.157, 0.289, 0.229, 0.286, (-0.239, -0.043)),
    ],
    7: [
        ('magnet', -0.107, 1.407, (0.204, 1.176)),
        ('sheet', -0.0, 1.346, 0.277, 0.243, (0.326, 1.211)),
        ('sheet', 0.182, 1.225, 0.175, 0.2, (0.482, 1.02)),
        ('sheet', -0.124, 1.246, 0.28, 0.36, (0.222, 0.97)),
        ('tile', -0.124, 0.857, (0.117, 0.659)),
    ],
    8: [
        ('magnet', 0.172, 1.176, (-0.117, 0.928)),
        ('tile', 0.14, 1.262, (-0.169, 1.02)),
        ('sheet', 0.159, 1.08, 0.165, 0.23, (-0.031, 0.906)),
        ('sheet', 0.051, 0.925, 0.153, 0.179, (-0.326, 0.729)),
    ],
    9: [
        ('magnet', 0.081, 1.36, (0.412, 1.133)),
        ('magnet', -0.257, 1.3, (-0.021, 1.069)),
        ('sheet', 0.091, 1.246, 0.143, 0.214, (0.499, 0.956)),
        ('sheet', -0.24, 1.154, 0.181, 0.3, (0.048, 0.999)),
    ],
    10: [
        ('magnet', 0.098, 1.312, (-0.222, 1.055)),
        ('magnet', 0.235, 1.381, (-0.013, 1.098)),
        ('magnet', 0.034, 1.213, (-0.308, 0.949)),
        ('magnet', 0.293, 1.338, (0.056, 1.084)),
        ('sheet', 0.019, 1.255, 0.064, 0.093, (-0.343, 1.02)),
        ('sheet', 0.229, 1.284, 0.127, 0.136, (-0.117, 1.055)),
        ('sheet', 0.076, 1.133, 0.204, 0.323, (-0.274, 0.977)),
    ],
    11: [
        ('magnet', 0.118, 1.141, (0.482, 0.892)),
        ('sheet', 0.117, 1.332, 0.143, 0.171, (0.569, 1.112)),
        ('tile', 0.071, 1.296, (0.447, 1.02)),
        ('sheet', 0.279, 1.179, 0.052, 0.079, (0.69, 0.963)),
        ('tile', 0.156, 1.154, (0.516, 0.935)),
        ('tile', 0.169, 1.082, (0.516, 0.836)),
        ('tile', 0.097, 1.121, (0.395, 0.843)),
        ('sheet', 0.143, 0.939, 0.091, 0.186, (0.534, 0.701)),
        ('sheet', 0.078, 0.921, 0.065, 0.15, (0.378, 0.722)),
    ],
    12: [
        ('magnet', 0.008, 1.32, (-0.447, 1.006)),
        ('magnet', 0.069, 1.065, (-0.36, 0.701)),
        ('magnet', -0.081, 0.918, (-0.569, 0.559)),
        ('magnet', 0.037, 0.609, (-0.412, 0.283)),
        ('sheet', -0.062, 1.338, 0.162, 0.201, (-0.638, 1.013)),
        ('sheet', 0.069, 0.897, 0.275, 0.36, (-0.482, 0.736)),
        ('sheet', -0.094, 0.757, 0.25, 0.36, (-0.603, 0.446)),
    ],
    13: [],
    14: [('magnet', 0.04, 0.892, (-0.412, 0.581)), ('sheet', -0.037, 0.922, 0.162, 0.23, (-0.569, 0.673))],
    15: [
        ('magnet', 0.15, 0.764, (0.638, 0.432)),
        ('sheet', -0.098, 1.313, 0.049, 0.05, (0.308, 0.977)),
        ('sheet', -0.098, 1.255, 0.049, 0.065, (0.274, 0.906)),
        ('sheet', 0.147, 0.972, 0.098, 0.172, (0.69, 0.68)),
        ('tile', -0.025, 1.029, (0.395, 0.701)),
        ('tile', 0.086, 0.961, (0.551, 0.68)),
        ('tile', -0.018, 0.965, (0.395, 0.616)),
        ('tile', -0.092, 0.968, (0.291, 0.659)),
        ('sheet', 0.203, 0.742, 0.037, 0.072, (0.725, 0.425)),
        ('sheet', 0.203, 0.631, 0.086, 0.151, (0.725, 0.326)),
        ('sheet', 0.117, 0.681, 0.086, 0.222, (0.586, 0.368)),
    ],
    16: [
        ('magnet', -0.183, 1.323, (-0.742, 0.97)),
        ('magnet', -0.131, 1.252, (-0.655, 0.906)),
        ('magnet', -0.08, 0.924, (-0.586, 0.581)),
        ('sheet', -0.037, 1.417, 0.037, 0.057, (-0.534, 1.084)),
        ('sheet', -0.188, 1.255, 0.113, 0.194, (-0.759, 0.991)),
        ('sheet', 0.119, 0.882, 0.05, 0.065, (-0.308, 0.552)),
        ('sheet', -0.075, 0.76, 0.262, 0.36, (-0.725, 0.623)),
        ('sheet', -0.006, 0.656, 0.125, 0.23, (-0.464, 0.389)),
    ],
    17: [
        ('magnet', 0.237, 1.362, (0.777, 1.027)),
        ('magnet', 0.166, 0.414, (0.673, 0.078)),
        ('sheet', 0.246, 1.305, 0.147, 0.136, (0.707, 1.013)),
        ('tile', 0.0, 0.968, (0.43, 0.659)),
        ('sheet', 0.166, 0.832, 0.209, 0.337, (0.777, 0.559)),
        ('sheet', -0.08, 0.8, 0.184, 0.244, (0.412, 0.538)),
        ('sheet', -0.117, 0.714, 0.111, 0.158, (0.256, 0.418)),
        ('sheet', 0.172, 0.481, 0.196, 0.337, (0.638, 0.262)),
        ('sheet', 0.147, 0.384, 0.123, 0.143, (0.586, 0.099)),
    ],
    18: [
        ('magnet', 0.01, 0.954, (-0.447, 0.595)),
        ('magnet', 0.232, 0.992, (-0.187, 0.644)),
        ('tile', -0.15, 0.828, (-0.707, 0.503)),
        ('sheet', -0.012, 0.846, 0.138, 0.208, (-0.43, 0.489)),
        ('sheet', 0.175, 0.814, 0.237, 0.359, (-0.326, 0.63)),
    ],
    19: [
        ('sheet', -0.08, 0.864, 0.184, 0.215, (0.412, 0.609)),
        ('sheet', -0.086, 0.721, 0.196, 0.23, (0.343, 0.439)),
        ('sheet', 0.055, 0.606, 0.16, 0.201, (0.586, 0.283)),
    ],
    20: [
        ('magnet', -0.197, 1.393, (-0.586, 1.133)),
        ('magnet', -0.066, 1.154, (-0.395, 0.956)),
        ('magnet', 0.157, 1.168, (-0.117, 0.942)),
        ('sheet', -0.221, 1.236, 0.271, 0.35, (-0.655, 1.147)),
        ('sheet', 0.071, 1.207, 0.2, 0.321, (-0.117, 1.013)),
        ('tile', 0.164, 0.407, (-0.117, 0.163)),
    ],
    21: [
        ('magnet', 0.251, 1.533, (0.586, 1.303)),
        ('magnet', 0.147, 1.421, (0.447, 1.204)),
        ('magnet', 0.266, 1.269, (0.603, 1.041)),
        ('sheet', 0.233, 1.471, 0.19, 0.207, (0.621, 1.317)),
        ('sheet', -0.007, 1.489, 0.175, 0.186, (0.343, 1.346)),
        ('sheet', 0.233, 1.214, 0.16, 0.193, (0.638, 1.055)),
        ('sheet', -0.08, 1.268, 0.28, 0.36, (0.36, 0.928)),
    ],
    22: [
        ('magnet', 0.079, 1.036, (-0.222, 0.814)),
        ('magnet', 0.186, 0.954, (-0.083, 0.715)),
        ('sheet', -0.114, 1.311, 0.2, 0.229, (-0.499, 1.169)),
        ('tile', 0.171, 1.439, (-0.117, 1.225)),
        ('sheet', 0.043, 1.261, 0.2, 0.229, (-0.343, 0.977)),
        ('tile', 0.243, 1.343, (-0.031, 1.119)),
        ('sheet', 0.05, 1.05, 0.1, 0.093, (-0.274, 0.85)),
        ('sheet', 0.086, 0.971, 0.2, 0.264, (-0.187, 0.843)),
    ],
    23: [
        ('magnet', -0.2, 1.583, (0.048, 1.367)),
        ('magnet', -0.044, 1.15, (0.222, 0.942)),
        ('sheet', -0.029, 1.475, 0.073, 0.071, (0.256, 1.254)),
        ('sheet', -0.19, 1.507, 0.16, 0.25, (-0.004, 1.246)),
        ('sheet', -0.015, 0.961, 0.28, 0.36, (0.343, 0.921)),
    ],
}
FRIDGE_TOP_ITEMS = {
    1: [('box', -0.203, 0.091, (0.154, 0.154, 0.194), (-0.455, 1.955))],
    2: [('box', -0.194, 0.04, (0.119, 0.119, 0.222), (-0.204, 1.87)), ('box', 0.106, 0.129, (0.312, 0.312, 0.287), (-0.204, 1.87))],
    3: [('can', 0.221, 0.124, (0.086, 0.215), (0.135, 1.955)), ('gift', -0.111, 0.148, (0.264, 0.264, 0.265), (-0.559, 2.026))],
    4: [('box', -0.243, 0.009, (0.136, 0.136, 0.221), (-0.152, 1.919)), ('box', 0.1, 0.112, (0.357, 0.357, 0.286), (-0.152, 1.919))],
    6: [('box', -0.014, 0.056, (0.257, 0.257, 0.143), (-0.239, 1.799))],
    9: [('box', 0.149, 0.082, (0.272, 0.272, 0.157), (-0.264, 2.075))],
    12: [
        ('gift', -0.187, 0.169, (0.238, 0.238, 0.273), (-0.395, 1.905)),
        ('can', 0.094, 0.258, (0.1, 0.201), (0.368, 2.019)),
        ('can', 0.044, 0.073, (0.106, 0.151), (0.056, 1.863)),
    ],
    13: [('box', 0.184, 0.14, (0.209, 0.209, 0.33), (-0.004, 2.068))],
    17: [('box', -0.141, 0.099, (0.246, 0.246, 0.301), (-0.507, 2.047))],
    18: [('can', -0.225, 0.209, (0.106, 0.222), (-0.291, 1.962)), ('box', 0.181, 0.089, (0.256, 0.256, 0.165), (-0.083, 1.735))],
    21: [('can', 0.277, 0.036, (0.102, 0.214), (0.117, 1.976)), ('box', -0.088, 0.063, (0.284, 0.284, 0.264), (-0.525, 2.111))],
    22: [
        ('box', -0.129, -0.028, (0.164, 0.164, 0.279), (-0.343, 1.827)),
        ('can', 0.136, 0.121, (0.157, 0.186), (0.282, 1.884)),
        ('can', 0.121, 0.372, (0.079, 0.221), (0.542, 2.104)),
    ],
}

# Per overlay, the tea towel hung over the oven's handle as (x, width, length, dye point), and the one lying at the back
# of the hob as (x, dye point). The world shader takes a dye's colour from nine texels 6 apart round the point, so the point
# is a pixel of the towel whose nine are all the towel's. Where the towel is narrower than that the dye point is None and the
# towel is linen's own pale cloth colour.
OVEN_HANG = {
    24: (0.236, 0.215, 0.388, (0.610, 0.194)),
    26: (0.069, 0.260, 0.228, (0.397, 0.268)),
    25: (0.099, 0.260, 0.400, (-0.217, 0.234)),
    34: (0.237, 0.130, 0.361, None),
    35: (-0.166, 0.247, 0.400, (-0.528, 0.294)),
    36: (-0.099, 0.215, 0.348, (0.135, 0.268)),
    44: (0.165, 0.208, 0.395, (0.463, 0.121)),
    45: (-0.106, 0.260, 0.400, (-0.414, 0.141)),
}
OVEN_LIE = {
    27: (0.219, (0.537, 1.184)),
    31: (-0.223, None),
    39: (0.200, (0.537, 1.118)),
    32: (-0.240, None),
}

# The overlays that paint a door or a lid ajar.
FRONT_LOADER_DOORS, LID_UP, OVEN_DOOR = (48, 49), (52, 53), (33,)


def overlay(n):
    return "appliances_01_%d" % n


def tilt(n, i):
    return math.radians((n * 11 + i * 37) % 13 - 6)


def make_sheet(name, at, x, z, w, h, angle, host):
    model = F.Model(name, centred=False)
    w, h = min(w, SHEET_WIDEST), min(h, SHEET_TALLEST)
    cx, cz = x + at.x, z + at.z
    y1 = FRIDGE_FACE - STAND_OFF
    y0 = y1 - SHEET_THICK
    paper, ink = bmesh.new(), bmesh.new()
    F.cuboid(paper, cx - w / 2, cx + w / 2, y0, y1, cz - h / 2, cz + h / 2)
    if w >= 0.09 and h >= 0.1:
        for i in range(int((h - 0.04) / 0.022)):
            zc = cz + h / 2 - 0.03 - i * 0.022
            run = w * (0.8 if i % 3 != 2 else 0.5)
            F.cuboid(ink, cx - w * 0.4, cx - w * 0.4 + run, y0 - INK_THICK, y0, zc - 0.0007, zc + 0.0007)
    turn = Matrix.Rotation(angle, 3, "Y")
    for bm in (paper, ink):
        bmesh.ops.rotate(bm, cent=(cx, y0, cz), matrix=turn, verts=bm.verts[:])
    model.hard(paper, "plastic", bevel=0.0004, segments=1)
    if ink.verts:
        model.hard(ink, "rubber", bevel=0.0002, segments=1)
    else:
        ink.free()
    props.stands_on(model, host)
    return model


def make_magnet(name, at, x, z, host):
    model = F.Model(name, centred=False)
    bm = bmesh.new()
    r, h = MAGNET_RADIUS, MAGNET_HIGH
    F.lathe(bm, [(0.0, 0.0), (r, 0.0), (r, h - 0.0015), (r - 0.0015, h), (0.0, h)], segments=14,
            to=F.along_y(x + at.x, FRIDGE_FACE - SHEET_THICK - STAND_OFF, z + at.z))
    model.turned(bm, "plastic")
    props.stands_on(model, host)
    return model


def make_letter(name, at, x, z, host):
    model = F.Model(name, centred=False)
    bm = bmesh.new()
    w, h, t = LETTER
    y1 = FRIDGE_FACE - SHEET_THICK - STAND_OFF
    F.cuboid(bm, x + at.x - w / 2, x + at.x + w / 2, y1 - t, y1, z + at.z - h / 2, z + at.z + h / 2)
    model.hard(bm, "plastic", bevel=0.001, segments=1)
    props.stands_on(model, host)
    return model


def make_box(name, at, x, y, w, d, h, gift, host):
    model = F.Model(name, centred=False)
    cx, cy = x + at.x, y + at.y
    w, d, h = min(w, TOP_SIDE_MOST), min(d, TOP_SIDE_MOST), min(h, TOP_HIGH_MOST)
    cx = max(-0.35 + w / 2, min(0.35 - w / 2, cx))
    low, high = FRIDGE_TOP, FRIDGE_TOP + h
    body, seam, tie = bmesh.new(), bmesh.new(), bmesh.new()
    F.cuboid(body, cx - w / 2, cx + w / 2, cy - d / 2, cy + d / 2, low, high)
    if gift:
        F.cuboid(tie, cx - 0.006, cx + 0.006, cy - d / 2 - 0.001, cy + d / 2 + 0.001, low, high + 0.001)
        F.cuboid(tie, cx - w / 2 - 0.001, cx + w / 2 + 0.001, cy - 0.006, cy + 0.006, low, high + 0.001)
    else:
        F.cuboid(seam, cx - 0.004, cx + 0.004, cy - d / 2 + 0.004, cy + d / 2 - 0.004, high, high + 0.0005)
    model.hard(body, "straw", bevel=0.004, segments=2)
    if gift:
        model.hard(tie, "linen", bevel=0.001, segments=1)
    else:
        model.hard(seam, "rubber", bevel=0.0002, segments=1)
    props.stands_on(model, host)
    return model


def make_can(name, at, x, y, r, h, host):
    model = F.Model(name, centred=False)
    cx, cy = x + at.x, y + at.y
    r, h = min(r, CAN_WIDEST), min(h, TOP_HIGH_MOST)
    low = FRIDGE_TOP
    body, lid = bmesh.new(), bmesh.new()
    F.lathe(body, [(0.0, low), (r, low), (r, low + h - 0.004), (0.0, low + h - 0.004)], cx=cx, cy=cy, segments=20)
    F.lathe(lid, [(r * 0.94, low + h - 0.004), (r * 0.94, low + h), (0.0, low + h)], cx=cx, cy=cy, segments=20)
    model.turned(body, "metal")
    model.turned(lid, "steel")
    props.stands_on(model, host)
    return model


def make_hung_cloth(name, at, x, w, length, dyed, host):
    model = F.Model(name, centred=False)
    cx = x + at.x
    top = OVEN_BAR_TOP + 0.006
    front, fold, behind = bmesh.new(), bmesh.new(), bmesh.new()
    F.cuboid(front, cx - w / 2, cx + w / 2, OVEN_BAR_FRONT - 0.006, OVEN_BAR_FRONT, top - length, top)
    F.cuboid(fold, cx - w / 2, cx + w / 2, OVEN_BAR_FRONT - 0.006, OVEN_BAR_BACK + 0.004, OVEN_BAR_TOP, top)
    F.cuboid(behind, cx - w / 2, cx + w / 2, OVEN_BAR_BACK + 0.001, OVEN_BAR_BACK + 0.007, top - length * 0.6, top)
    for bm in (front, fold, behind):
        model.hard(bm, "cloth" if dyed else "linen", bevel=0.0015, segments=1)
    props.stands_on(model, host)
    return model


def make_lying_cloth(name, at, x, dyed, host):
    model = F.Model(name, centred=False)
    cx, cy = x + at.x, 0.16 + at.y
    cloth = bmesh.new()
    F.cuboid(cloth, cx - 0.10, cx + 0.10, cy - 0.07, cy + 0.07, F.OVEN_HOB, F.OVEN_HOB + 0.014)
    model.hard(cloth, "cloth" if dyed else "linen", bevel=0.005, segments=2)
    props.stands_on(model, host)
    return model


def oven_door_prop(name, swapped):
    model = F.Model(name, centred=False)
    door, glass, trim = bmesh.new(), bmesh.new(), bmesh.new()
    F.oven_door(door, glass, trim)
    turn = Matrix.Rotation(math.radians(OVEN_AJAR), 3, "X")
    for bm in (door, glass, trim):
        bmesh.ops.rotate(bm, cent=(0.0, F.OVEN_FRONT - 0.022, 0.165), matrix=turn, verts=bm.verts[:])
    model.hard(door, "enamel", bevel=0.01, segments=2)
    model.hard(glass, "screen", bevel=0.006)
    model.hard(trim, "steel", bevel=0.005)
    props.stands_on(model, swapped)
    return model


def front_loader_door_prop(name, swapped):
    model = F.Model(name, centred=False)
    door, glass, latch = bmesh.new(), bmesh.new(), bmesh.new()
    F.front_loader_door(door, glass, latch)
    turn = Matrix.Rotation(math.radians(-FRONT_LOADER_AJAR), 3, "Z")
    for bm in (door, glass, latch):
        bmesh.ops.rotate(bm, cent=(-F.FRONT_LOADER_DOOR, F.FRONT_LOADER_FRONT, 0.0), matrix=turn, verts=bm.verts[:])
    model.turned(door, "metal", sharp=40.0)
    model.turned(glass, "screen", sharp=50.0)
    model.hard(latch, "paint", bevel=0.004, segments=1)
    props.stands_on(model, swapped)
    return model


def lid_prop(name, swapped):
    model = F.Model(name, centred=False)
    lid = bmesh.new()
    F.washer_lid(lid)
    turn = Matrix.Rotation(math.radians(-LID_AJAR), 3, "X")
    bmesh.ops.rotate(lid, cent=(0.0, F.LAUNDRY_BACK - 0.16, F.LAUNDRY_TOP), matrix=turn, verts=lid.verts[:])
    model.hard(lid, "enamel", bevel=0.006, segments=2)
    props.stands_on(model, swapped)
    return model


def build():
    models, entries = [], []
    made = set()
    hosts = {name: make() for name, make in HOSTS.items()}
    ats = {name: props.origin(host) for name, host in hosts.items()}

    def keep(model):
        if model.name not in made:
            made.add(model.name)
            models.append(model)
        return model

    def over(surfaces, at, dye):
        return ("over", surfaces, (dye[0] + at.x, at.y, dye[1] + at.z))

    fridge, at = hosts["fridge"], ats["fridge"]
    for n in range(24):
        for i, item in enumerate(FRIDGE_FACE_ITEMS.get(n, ())):
            kind = item[0]
            name = "%s_%s_%d_%d" % (BATCH, kind, n, i)
            if kind == "magnet":
                keep(make_magnet(name, at, item[1], item[2], fridge))
            elif kind == "tile":
                keep(make_letter(name, at, item[1], item[2], fridge))
            else:
                keep(make_sheet(name, at, item[1], item[2], item[3], item[4], tilt(n, i), fridge))
            entries.append(Prop(overlay(n), "fridge", name, over(("plastic",), at, item[-1])))
        for i, (kind, x, y, size, dye) in enumerate(FRIDGE_TOP_ITEMS.get(n, ())):
            name = "%s_top%s_%d_%d" % (BATCH, kind, n, i)
            if kind == "can":
                keep(make_can(name, at, x, y, size[0], size[1], fridge))
                entries.append(Prop(overlay(n), "fridge", name, over(("metal",), at, dye)))
            else:
                keep(make_box(name, at, x, y, size[0], size[1], size[2], kind == "gift", fridge))
                entries.append(Prop(overlay(n), "fridge", name, over(("straw",), at, dye)))

    oven, at = hosts["oven"], ats["oven"]
    for n, (x, w, length, dye) in sorted(OVEN_HANG.items()):
        name = "%s_oven_cloth_%d" % (BATCH, n)
        keep(make_hung_cloth(name, at, x, w, length, dye is not None, oven))
        entries.append(Prop(overlay(n), "oven", name, "plain" if dye is None else over(("cloth",), at, dye)))
    for n, (x, dye) in sorted(OVEN_LIE.items()):
        name = "%s_oven_towel_%d" % (BATCH, n)
        keep(make_lying_cloth(name, at, x, dye is not None, oven))
        entries.append(Prop(overlay(n), "oven", name, "plain" if dye is None else over(("cloth",), at, dye)))
    for n in OVEN_DOOR:
        swapped = keep(F.oven("%s_oven_door_off" % BATCH, door_off=True))
        entries.append(Swap(overlay(n), "oven", swapped.name))
        name = "%s_oven_door" % BATCH
        keep(oven_door_prop(name, swapped))
        entries.append(Prop(overlay(n), "oven", name, "host"))
    swapped = keep(F.front_loader("%s_front_loader_door_off" % BATCH, door_off=True))
    door = keep(front_loader_door_prop("%s_front_loader_door" % BATCH, swapped)).name
    for n in FRONT_LOADER_DOORS:
        entries.append(Swap(overlay(n), "front_loader", swapped.name))
        entries.append(Prop(overlay(n), "front_loader", door, "host"))
    swapped = keep(F.washing_machine("%s_washing_machine_lid_off" % BATCH, lid_off=True))
    lid = keep(lid_prop("%s_washing_machine_lid" % BATCH, swapped)).name
    for n in LID_UP:
        entries.append(Swap(overlay(n), "washing_machine", swapped.name))
        entries.append(Prop(overlay(n), "washing_machine", lid, "host"))

    return models, entries


props.write(BATCH, os.path.abspath(__file__), build, {name: (lambda make=make: make()) for name, make in HOSTS.items()})
