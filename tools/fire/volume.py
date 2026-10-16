"""Makes the flames first person marches through (FlameVolume): a campfire simulated in Blender's
Mantaflow, kept as a loop of frames of how much flame there is in each cell of a box round it and
how hot its gas is. The loop is cut from the frames after the fire has grown and settled, and its
frames are crossfaded with the frames one loop on, so its last frame runs into its first.

Run headless with Blender 4.4, from the repository root:

    blender --background --factory-startup --threads 8 --python tools/fire/volume.py -- <work folder>

It writes src/main/resources/fire/flames.vol.gz, little endian:

    int    magic 0x4F564C46 ("FLVO"), version 1
    int    a frame's cells across (x), deep (y) and high (z); frames; frames a row, rows
    float  how far the flame rises from one frame to the next on the mean, in cells
    float  how high the flames reach on the mean, as a share of the box's height
    float  how wide their root is, as a share of the box's width
    float  frames a second at the simulation's own size
    float  a cell's size, in metres
    byte   flame, a cell each: the frames laid side by side in one texture of (across * frames a
           row) by (deep * rows) by high cells, x fastest
    byte   heat, the same at half the cells each way

The work folder keeps the simulation's cache, which is not made again while it holds every frame.
FIRE_OUT changes the folder the loop goes to.
"""
import gzip
import math
import os
import struct
import sys

import bpy
import numpy as np
import openvdb

ARGS = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
WORK = os.path.abspath(ARGS[0] if ARGS else "fire-work")
OUT = os.path.abspath(os.environ.get("FIRE_OUT") or os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "fire"))

# The simulation: its resolution along its longest side, and how many times finer its added detail is.
RESOLUTION, UPRES = 128, 2
# The domain, in metres: the fire's foot is at the origin, its flames rise along z.
DOMAIN_WIDE, DOMAIN_HIGH = 1.2, 2.4
# The loop: FRAMES frames from LOOP_START, laid ACROSS a row; the simulation runs a loop further for the crossfade.
FRAMES, ACROSS, LOOP_START = 32, 8, 58
# The loop's cells are the simulation's detail averaged over STEP of them each way.
STEP = 1
FRAMES_A_SECOND = 24.0
MAGIC, VERSION = 0x4F564C46, 1


def simulate(cache):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    last = LOOP_START + 2 * FRAMES
    scene.frame_start, scene.frame_end = 1, last
    scene.render.fps = int(FRAMES_A_SECOND)
    # The fuel burns in patches that drift as it burns, so the flames root in new places and lick up as tongues.
    patches = bpy.data.textures.new("fuel", "CLOUDS")
    patches.noise_scale = 0.35
    patches.noise_depth = 2
    patches.contrast = 1.6
    # Three logs crossed, as a campfire's are.
    for turn in (0, 60, 120):
        bpy.ops.mesh.primitive_cylinder_add(radius=0.045, depth=0.5, location=(0, 0, 0.06), vertices=10, rotation=(math.radians(90), 0, math.radians(turn)))
        log = bpy.context.object
        fluid = log.modifiers.new("Fluid", "FLUID")
        fluid.fluid_type = "FLOW"
        flow = fluid.flow_settings
        flow.flow_type = "FIRE"
        flow.flow_behavior = "INFLOW"
        flow.flow_source = "MESH"
        flow.fuel_amount = SETTINGS["fuel"]
        flow.surface_distance = 0.25
        flow.subframes = 2
        flow.use_initial_velocity = True
        flow.velocity_coord = (0.0, 0.0, SETTINGS["lift"])
        flow.use_texture = True
        flow.noise_texture = patches
        flow.texture_map_type = "AUTO"
        flow.texture_size = SETTINGS["patch"]
        flow.texture_offset = 0.0
        flow.keyframe_insert("texture_offset", frame=1)
        flow.texture_offset = SETTINGS["drift"] * last / FRAMES_A_SECOND
        flow.keyframe_insert("texture_offset", frame=last)
        for curve in log.animation_data.action.fcurves:
            for key in curve.keyframe_points:
                key.interpolation = "LINEAR"
        log.hide_render = True
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=(0, 0, DOMAIN_HIGH / 2 - 0.02))
    box = bpy.context.object
    box.scale = (DOMAIN_WIDE, DOMAIN_WIDE, DOMAIN_HIGH)
    domain = box.modifiers.new("Fluid", "FLUID")
    domain.fluid_type = "DOMAIN"
    d = domain.domain_settings
    d.domain_type = "GAS"
    d.resolution_max = RESOLUTION
    d.cache_directory = cache
    d.cache_type = "ALL"
    d.cache_data_format = "OPENVDB"
    d.cache_frame_start, d.cache_frame_end = 1, last
    d.use_adaptive_domain = False
    d.vorticity = SETTINGS["vorticity"]
    d.alpha = 1.0
    d.beta = SETTINGS["buoyancy"]
    d.burning_rate = SETTINGS["burn"]
    d.flame_smoke = 0.0
    d.flame_vorticity = SETTINGS["flame vorticity"]
    d.flame_ignition = SETTINGS["ignition"]
    d.flame_max_temp = SETTINGS["hottest"]
    d.use_noise = True
    d.noise_scale = UPRES
    d.noise_strength = SETTINGS["detail"]
    d.noise_pos_scale = 2.0
    d.use_dissolve_smoke = True
    d.dissolve_speed = 4
    bpy.context.view_layer.objects.active = box
    for obj in scene.objects:
        obj.select_set(obj == box)
    with bpy.context.temp_override(object=box, active_object=box, selected_objects=[box]):
        bpy.ops.fluid.bake_all()


def baked(cache):
    last = LOOP_START + 2 * FRAMES
    return all(os.path.exists(os.path.join(cache, part, "fluid_%s_%04d.vdb" % (part, frame))) for part in ("data", "noise") for frame in (1, last))


def grid(cache, frame, part, name, shape):
    cells = np.zeros(shape, np.float32)
    openvdb.read(os.path.join(cache, part, "fluid_%s_%04d.vdb" % (part, frame)), name).copyToArray(cells, (0, 0, 0))
    return cells


def loop(cache):
    """The loop's flame and heat, (frames, x, y, z) each, 0 to 1, and the box it was cut to."""
    base = (RESOLUTION // 2, RESOLUTION // 2, RESOLUTION)
    fine = tuple(n * UPRES for n in base)
    frames = range(LOOP_START, LOOP_START + 2 * FRAMES)
    lo, hi = np.array([10 ** 9] * 3), np.array([-1] * 3)
    for frame in frames:
        a, b = openvdb.read(os.path.join(cache, "noise", "fluid_noise_%04d.vdb" % frame), "flame_noise").evalActiveVoxelBoundingBox()
        lo, hi = np.minimum(lo, a), np.maximum(hi, np.array(b) + 1)
    # Square across, centred on the fire, its foot at the domain's floor, a whole number of steps each way, and of an even number of loop cells.
    side = max(hi[0] - lo[0], hi[1] - lo[1])
    side += (-side) % (2 * STEP)
    middle = fine[0] // 2
    lo[:2] = middle - side // 2
    hi[:2] = lo[:2] + side
    lo[2] = 0
    hi[2] += (-hi[2]) % (2 * STEP)
    if lo[0] < 0 or hi[0] > fine[0] or hi[2] > fine[2]:
        raise SystemExit("volume: the flames reach the domain's side or top: box %s to %s of %s" % (lo, hi, fine))

    def cut(cells):
        cells = cells[lo[0]:hi[0], lo[1]:hi[1], lo[2]:hi[2]]
        x, y, z = cells.shape
        return cells.reshape(x // STEP, STEP, y // STEP, STEP, z // STEP, STEP).mean(axis=(1, 3, 5))

    flame, heat = [], []
    for frame in frames:
        flame.append(cut(grid(cache, frame, "noise", "flame_noise", fine)))
        hot = grid(cache, frame, "data", "temperature", base)
        heat.append(cut(np.repeat(np.repeat(np.repeat(hot, UPRES, 0), UPRES, 1), UPRES, 2)))
        print("volume: frame", frame, flush=True)
    flame, heat = np.stack(flame), np.stack(heat)
    share = (np.arange(FRAMES) / FRAMES)[:, None, None, None]
    flame = share * flame[:FRAMES] + (1 - share) * flame[FRAMES:]
    heat = share * heat[:FRAMES] + (1 - share) * heat[FRAMES:]
    # Each is 1 at the loop's own most, the few cells past it clipped: the simulation's own scale for neither is a light.
    top = float(np.percentile(flame[flame > 0.01], 99.9))
    hottest = float(np.percentile(heat[flame > 0.01], 99.9))
    print("volume: flame scaled from", top, "heat from", hottest, flush=True)
    return np.clip(flame / top, 0, 1), np.clip(heat / hottest, 0, 1)


def rise(flame):
    """
    How far the flame's detail moves up from one frame to the next on the mean, in cells: the shift
    that best lays one frame on the next, of what is left of each when the loop's mean is taken
    away, since the body of a fire stands where it is while its tongues rise through it.
    """
    flame = flame - flame.mean(axis=0)
    best, most = 0.0, -1.0
    for shift in np.arange(0.0, 12.0, 0.25):
        whole, part = int(shift), shift - int(shift)
        score = 0.0
        for f in range(FRAMES - 1):
            a, b = flame[f], flame[f + 1]
            moved = np.zeros_like(a)
            moved[:, :, whole:] = a[:, :, :a.shape[2] - whole]
            if part:
                later = np.zeros_like(a)
                later[:, :, whole + 1:] = a[:, :, :a.shape[2] - whole - 1]
                moved = moved * (1 - part) + later * part
            score += float((moved * b).sum())
        if score > most:
            best, most = shift, score
    return best


def reach(flame):
    """How high the flames reach on the mean, as a share of the box's height: where 95 of every 100 of the loop's flame lies below."""
    along = flame.sum(axis=(0, 1, 2))
    return float(np.searchsorted(np.cumsum(along) / along.sum(), 0.95) / flame.shape[3])


def root(flame):
    """How wide the flames' root is, as a share of the box's width: twice the radius holding 90 of every 100 of the flame in its lowest fifth."""
    low = flame[:, :, :, :flame.shape[3] // 5].sum(axis=(0, 3))
    x, y = np.meshgrid(np.arange(low.shape[0]) + 0.5 - low.shape[0] / 2, np.arange(low.shape[1]) + 0.5 - low.shape[1] / 2, indexing="ij")
    far = np.hypot(x, y).ravel()
    order = np.argsort(far)
    held = np.cumsum(low.ravel()[order]) / low.sum()
    return float(2 * far[order][np.searchsorted(held, 0.9)] / low.shape[0])


def laid(cells):
    """(frames, x, y, z) laid as one texture of (x * ACROSS) by (y * rows) by z, x fastest, as bytes."""
    frames, x, y, z = cells.shape
    rows = frames // ACROSS
    grid = cells.reshape(rows, ACROSS, x, y, z).transpose(4, 0, 3, 1, 2).reshape(z, rows * y, ACROSS * x)
    return np.round(grid * 255).astype(np.uint8).tobytes()


SETTINGS = {"fuel": 1.0, "burn": 0.75, "lift": 2.0, "buoyancy": 3.0, "vorticity": 0.4, "flame vorticity": 1.2, "ignition": 1.25, "hottest": 2.0,
            "detail": 1.2, "patch": 1.0, "drift": 2.0}


def main():
    # Mantaflow leaves its noise tile in the folder it runs in.
    os.makedirs(WORK, exist_ok=True)
    os.chdir(WORK)
    cache = os.path.join(WORK, "cache-%d-%d-%s" % (RESOLUTION, UPRES, "-".join("%g" % v for v in SETTINGS.values())))
    if not baked(cache):
        simulate(cache)
    flame, heat = loop(cache)
    halved = heat.reshape(heat.shape[0], heat.shape[1] // 2, 2, heat.shape[2] // 2, 2, heat.shape[3] // 2, 2).mean(axis=(2, 4, 6))
    frames, x, y, z = flame.shape
    cell = DOMAIN_HIGH / RESOLUTION / UPRES * STEP
    head = struct.pack("<8i5f", MAGIC, VERSION, x, y, z, frames, ACROSS, frames // ACROSS, rise(flame), reach(flame), root(flame), FRAMES_A_SECOND, cell)
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, "flames.vol.gz")
    # No name or time in the gzip's header, so the same loop makes the same file.
    with open(path, "wb") as file, gzip.GzipFile(filename="", mode="wb", fileobj=file, mtime=0) as out:
        out.write(head + laid(flame) + laid(halved))
    print("volume: wrote", os.path.abspath(path), "cells", x, y, z, "frames", frames, "header", struct.unpack("<8i5f", head), flush=True)


main()
