"""Makes the flames first person draws: a fire simulated in Blender's Mantaflow and rendered, seen
from the side, as a loop of greyscale frames of how much flame there is, laid in a grid.

Two fires are simulated from emitters of different shapes, and their loops are the red and the
green of one picture; FireDraw gives them their colour. Each loop is cut from frames
LOOP_START .. LOOP_START + 2 * FRAMES and crossfaded so its last frame runs into its first.

Run headless with Blender 4.4, from the repository root:

    blender --background --factory-startup --threads 4 --python tools/fire/flames.py -- <work folder> [resolution] [samples]

It writes src/main/resources/fire/flames.png. The work folder holds the bake caches and the frames;
frames already there are not made again. FIRE_KINDS (disc,logs) picks the fires, so the two can
be made by two runs at once into one work folder and laid together by a third. FIRE_FUEL,
FIRE_BURN, FIRE_LIFT and FIRE_BUOYANCY change the fire, and FIRE_OUT where the picture goes.

The shipped picture: resolution 112, 24 samples, the defaults.
"""
import math
import os
import sys

import bpy
import numpy as np

ARGS = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
WORK = os.path.abspath(ARGS[0] if ARGS else "fire-work")
RESOLUTION = int(ARGS[1]) if len(ARGS) > 1 else 96
SAMPLES = int(ARGS[2]) if len(ARGS) > 2 else 24
OUT = os.environ.get("FIRE_OUT") or os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "fire", "flames.png")

# The loop: FRAMES frames, cut from after the fire has grown and settled.
FRAMES = 64
LOOP_START = 70
# A frame of the grid, in pixels, and how many frames a row holds.
FRAME_WIDE, FRAME_HIGH, ACROSS = 128, 256, 8
# For each fire, how much fuel the emitter gives, how fast it burns, how fast its gas leaves it and how
# hard hot gas rises: chosen so the flames reach a little over half the frame and stand clear of its sides
# at RESOLUTION 112. The simulation is not the same at another resolution.
SETTINGS = {"disc": (2.2, 0.5, 3.0, 3.5), "logs": (1.6, 0.6, 2.4, 3.0)}


def settings(kind):
    fuel, burn, lift, buoyancy = SETTINGS[kind]
    return (float(os.environ.get("FIRE_FUEL", fuel)), float(os.environ.get("FIRE_BURN", burn)),
            float(os.environ.get("FIRE_LIFT", lift)), float(os.environ.get("FIRE_BUOYANCY", buoyancy)))
# The domain, in Blender metres: the fire's foot is at the origin, its flames rise along z.
DOMAIN_WIDE, DOMAIN_HIGH = 1.2, 2.4


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.frame_start = 1
    scene.frame_end = LOOP_START + 2 * FRAMES
    return scene


def emitter(kind, fuel, lift):
    """The fuel the fire burns from: a disc, or three logs crossed as a campfire's are."""
    if kind == "disc":
        bpy.ops.mesh.primitive_cylinder_add(radius=0.18, depth=0.14, location=(0, 0, 0.07), vertices=24)
        parts = [bpy.context.object]
    else:
        parts = []
        for turn in (0, 60, 120):
            bpy.ops.mesh.primitive_cylinder_add(radius=0.045, depth=0.5, location=(0, 0, 0.06), vertices=10,
                                                rotation=(math.radians(90), 0, math.radians(turn)))
            parts.append(bpy.context.object)
    for part in parts:
        fluid = part.modifiers.new("Fluid", "FLUID")
        fluid.fluid_type = "FLOW"
        flow = fluid.flow_settings
        flow.flow_type = "FIRE"
        flow.flow_behavior = "INFLOW"
        flow.flow_source = "MESH"
        flow.fuel_amount = fuel
        flow.surface_distance = 0.25
        flow.subframes = 2
        # Pushed up out of the fuel, as hot gas leaves it: the flames lick up instead of spreading out.
        flow.use_initial_velocity = True
        flow.velocity_coord = (0.0, 0.0, lift)
        # The fuel is not rendered: only the flame is in the loop.
        part.hide_render = True
    return parts


def domain(cache, burn, buoyancy):
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=(0, 0, DOMAIN_HIGH / 2 - 0.02))
    box = bpy.context.object
    box.scale = (DOMAIN_WIDE, DOMAIN_WIDE, DOMAIN_HIGH)
    fluid = box.modifiers.new("Fluid", "FLUID")
    fluid.fluid_type = "DOMAIN"
    settings = fluid.domain_settings
    settings.domain_type = "GAS"
    settings.resolution_max = RESOLUTION
    settings.cache_directory = cache
    settings.cache_type = "ALL"
    settings.cache_data_format = "OPENVDB"
    settings.cache_frame_start = 1
    settings.cache_frame_end = LOOP_START + 2 * FRAMES
    settings.use_adaptive_domain = False
    settings.vorticity = 0.2
    settings.alpha = 1.0
    settings.beta = buoyancy
    settings.burning_rate = burn
    settings.flame_smoke = 0.0
    settings.flame_vorticity = 1.4
    settings.flame_ignition = 1.5
    settings.flame_max_temp = 3.0
    settings.use_noise = True
    settings.noise_scale = 2
    settings.noise_strength = 1.2
    settings.noise_pos_scale = 2.0
    settings.use_dissolve_smoke = True
    settings.dissolve_speed = 4
    return box


def flame_material(box):
    """How much flame there is, as light of one colour: the loop keeps the amount, FireDraw the colour."""
    material = bpy.data.materials.new("flame")
    material.use_nodes = True
    nodes = material.node_tree.nodes
    links = material.node_tree.links
    nodes.clear()
    out = nodes.new("ShaderNodeOutputMaterial")
    emission = nodes.new("ShaderNodeEmission")
    attribute = nodes.new("ShaderNodeAttribute")
    attribute.attribute_name = "flame"
    emission.inputs["Color"].default_value = (1, 1, 1, 1)
    links.new(attribute.outputs["Fac"], emission.inputs["Strength"])
    links.new(emission.outputs["Emission"], out.inputs["Volume"])
    box.data.materials.append(material)


def camera(scene):
    bpy.ops.object.camera_add(location=(0, -6, DOMAIN_HIGH / 2), rotation=(math.radians(90), 0, 0))
    cam = bpy.context.object
    cam.data.type = "ORTHO"
    cam.data.ortho_scale = DOMAIN_HIGH
    scene.camera = cam
    render = scene.render
    render.engine = "CYCLES"
    render.resolution_x = FRAME_WIDE
    render.resolution_y = FRAME_HIGH
    render.resolution_percentage = 100
    render.film_transparent = False
    render.image_settings.file_format = "OPEN_EXR"
    render.image_settings.color_depth = "32"
    scene.cycles.device = "CPU"
    scene.cycles.samples = SAMPLES
    scene.cycles.volume_step_rate = 0.5
    scene.cycles.use_denoising = False
    scene.view_settings.view_transform = "Standard"
    world = bpy.data.worlds.new("black")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.0
    scene.world = world


def bake(box):
    bpy.context.view_layer.objects.active = box
    for obj in bpy.context.scene.objects:
        obj.select_set(obj == box)
    with bpy.context.temp_override(object=box, active_object=box, selected_objects=[box]):
        bpy.ops.fluid.bake_all()


def render_frames(scene, folder):
    frames = []
    os.makedirs(folder, exist_ok=True)
    for frame in range(LOOP_START, LOOP_START + 2 * FRAMES):
        path = os.path.join(folder, "f%04d.exr" % frame)
        if not os.path.exists(path):
            scene.frame_set(frame)
            scene.render.filepath = path
            bpy.ops.render.render(write_still=True)
        image = bpy.data.images.load(path)
        pixels = np.array(image.pixels[:], dtype=np.float32).reshape(FRAME_HIGH, FRAME_WIDE, 4)[::-1, :, 0]
        bpy.data.images.remove(image)
        frames.append(pixels)
        print("flames: frame", frame, "max", float(pixels.max()), flush=True)
    return frames


def smoothed(frame):
    """A frame with the render's grain taken out: twice over each way, a pixel's own half and a quarter of each neighbour's."""
    for _ in range(2):
        frame = frame.copy()
        frame[1:-1, :] = frame[1:-1, :] * 0.5 + (frame[:-2, :] + frame[2:, :]) * 0.25
        frame[:, 1:-1] = frame[:, 1:-1] * 0.5 + (frame[:, :-2] + frame[:, 2:]) * 0.25
    return frame


def loop(frames):
    """FRAMES frames whose last runs into its first: frame k is the sim's k + FRAMES crossfaded into its k."""
    out = []
    for k in range(FRAMES):
        w = k / FRAMES
        out.append(frames[k + FRAMES] * (1 - w) + frames[k] * w)
    return out


def simulate(kind):
    scene = reset()
    fuel, burn, lift, buoyancy = settings(kind)
    emitter(kind, fuel, lift)
    box = domain(os.path.join(WORK, kind, "cache"), burn, buoyancy)
    flame_material(box)
    camera(scene)
    folder = os.path.join(WORK, kind, "frames")
    # Frames already rendered in the work folder are kept, and with all of them there nothing is baked again.
    rendered = all(os.path.exists(os.path.join(folder, "f%04d.exr" % frame)) for frame in range(LOOP_START, LOOP_START + 2 * FRAMES))
    if not rendered:
        bake(box)
    return loop([smoothed(frame) for frame in render_frames(scene, folder)])


def main():
    os.makedirs(WORK, exist_ok=True)
    kinds = os.environ.get("FIRE_KINDS", "disc,logs").split(",")
    channels = [simulate(kind) for kind in kinds]
    # One scale for both, so a frame's brightness is how much flame there is, the same in each loop.
    top = max(float(np.percentile(np.stack(c), 99.9)) for c in channels)
    rows = FRAMES // ACROSS
    grid = np.zeros((rows * FRAME_HIGH, ACROSS * FRAME_WIDE, 3), dtype=np.float32)
    for channel, frames in enumerate(channels):
        for k, frame in enumerate(frames):
            y, x = (k // ACROSS) * FRAME_HIGH, (k % ACROSS) * FRAME_WIDE
            grid[y:y + FRAME_HIGH, x:x + FRAME_WIDE, channel] = np.clip(frame / top, 0, 1)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    image = bpy.data.images.new("flames", ACROSS * FRAME_WIDE, rows * FRAME_HIGH, alpha=False)
    rgba = np.ones((rows * FRAME_HIGH, ACROSS * FRAME_WIDE, 4), dtype=np.float32)
    # Stored as the square root of the amount: dim flame keeps its steps in eight bits.
    rgba[..., :3] = np.sqrt(grid)
    image.pixels = rgba[::-1].ravel()
    image.filepath_raw = OUT
    image.file_format = "PNG"
    image.save()
    print("flames: wrote", os.path.abspath(OUT), "scale", top, flush=True)


main()
