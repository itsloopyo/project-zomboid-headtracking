"""Generates the pictures of wood and rubber that tools/models/shotgun.py lays on the shotgun.

Run with the Python of C:\\Data\\tools\\imagegen\\venv (torch, diffusers), from the repository root:

    python tools/models/shotgun_textures.py OUT_DIR [--only walnut ...] [--seed N]

Every picture is made from a text prompt alone with Stable Diffusion XL base 1.0, and wraps at
its edges so it can be repeated along a stock. The pictures are working
files: shotgun.py bakes them into the model's own texture, which is what the jar carries.
"""
import argparse
import pathlib

import torch
from diffusers import StableDiffusionXLPipeline

SIZE = 1024
STEPS = 34
PICTURES = {
    'walnut': ('extreme close-up photograph of a flat polished american black walnut gunstock, straight wood grain running horizontally '
               'across the whole frame, long dark streaks in rich warm reddish brown wood, fine open pores, hand rubbed oil finish, '
               'flat even studio lighting, seamless texture, highly detailed, 4k'),
    'rubber': ('extreme macro photograph of a flat sheet of matte black rubber, very fine even pebbled grain, slightly dusty, uniform, '
               'flat even studio lighting, seamless texture, highly detailed, 4k'),
}
NEGATIVE = {
    'walnut': 'knots, planks, boards, gaps, joints, floor, shadows, perspective, objects, text, watermark, blurry, painting, illustration, vignette, border, yellow, orange pine',
    'rubber': 'tread, pattern, holes, seams, shadows, perspective, objects, text, watermark, blurry, painting, illustration, vignette, border, shiny, glare',
}
SEEDS = {'walnut': 21, 'rubber': 2}


def seamless(module):
    """Convolutions that wrap round the picture's edges, so its left edge carries on from its right."""
    for layer in module.modules():
        if isinstance(layer, torch.nn.Conv2d):
            layer.padding_mode = 'circular'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('out')
    parser.add_argument('--only', nargs='*')
    parser.add_argument('--seed', type=int)
    args = parser.parse_args()
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    pipe = StableDiffusionXLPipeline.from_pretrained(
        'stabilityai/stable-diffusion-xl-base-1.0', torch_dtype=torch.float16, variant='fp16', use_safetensors=True)
    # The graphics card is shared: each part of the model is on it only while it runs.
    pipe.enable_model_cpu_offload()
    pipe.vae.enable_tiling()
    seamless(pipe.unet)
    seamless(pipe.vae)
    for name, prompt in PICTURES.items():
        if args.only and name not in args.only:
            continue
        seed = args.seed if args.seed is not None else SEEDS[name]
        image = pipe(prompt=prompt, negative_prompt=NEGATIVE[name], width=SIZE, height=SIZE, num_inference_steps=STEPS,
                     guidance_scale=6.0, generator=torch.Generator('cuda').manual_seed(seed)).images[0]
        path = out / (f'{name}.png' if args.seed is None else f'{name}-{seed}.png')
        image.save(path)
        print('made', path, flush=True)


if __name__ == '__main__':
    main()
