"""Makes the pictures that hang on walls in the first person view, from the text prompts in catalogue.py alone.

Z-Image Turbo (Tongyi-MAI, Apache 2.0) at about a million pixels in the picture's shape, 9 steps, no guidance, as its model
card gives. Run with the venv that has torch and diffusers (see .lab/NOTES.md, "Generated materials"):
    python tools/pictures/generate.py --out DIR [--only family-00 ...] [--starting family- boxing-] [--most 20] [--candidates 4]
        [--shard 2/5] [--seeds 1 3]
Writes DIR/<name>/<seed>.png for seeds 1 to --candidates, and skips what is there already, so a long list is made a few at
a time. --shard k/n makes every n-th job from the k-th, so n machines can share a list; --seeds makes only those
candidates. The seed picked for each picture
goes into catalogue.SEEDS, and tools/pictures/pack.py puts them into the mod.
"""
import argparse
import pathlib

import torch
from diffusers import ZImagePipeline

import catalogue

MODEL = 'Tongyi-MAI/Z-Image-Turbo'
# The commit of the model on the hub that every picture here was made with.
REVISION = 'f332072aa78be7aecdf3ee76d5c247082da564a6'
PIXELS = 1024 * 1024


def size(width, height):
    """The catalogue's shape at about a million pixels, each side a multiple of 64."""
    scale = (PIXELS / (width * height)) ** 0.5
    return round(width * scale / 64) * 64, round(height * scale / 64) * 64


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', required=True)
    parser.add_argument('--only', nargs='*', default=[])
    parser.add_argument('--starting', nargs='*', default=[])
    parser.add_argument('--most', type=int, default=0)
    parser.add_argument('--candidates', type=int, default=4)
    parser.add_argument('--shard', default='1/1')
    parser.add_argument('--seeds', nargs='*', type=int, default=[])
    args = parser.parse_args()
    out = pathlib.Path(args.out)

    unknown = [name for name in args.only if name not in catalogue.ART]
    if unknown:
        raise SystemExit('no such picture: ' + ' '.join(unknown))
    chosen = bool(args.only or args.starting)
    jobs = [(name, seed) for name in catalogue.ART
            if not chosen or name in args.only or any(name.startswith(start) for start in args.starting)
            for seed in (args.seeds or range(1, args.candidates + 1))]
    # Shared out before what is made is left out: each machine knows only its own, and the shares must not move.
    shard, shards = (int(part) for part in args.shard.split('/'))
    jobs = [(name, seed) for name, seed in jobs[shard - 1::shards] if not (out / name / f'{seed}.png').exists()]
    if args.most:
        jobs = jobs[:args.most]
    if not jobs:
        print('nothing to make', flush=True)
        return

    pipe = ZImagePipeline.from_pretrained(MODEL, revision=REVISION, torch_dtype=torch.bfloat16)
    pipe.set_progress_bar_config(disable=True)
    # The words are worked out once with the text model on the card, which then leaves it to the denoiser.
    pipe.text_encoder.to('cuda')
    words = {}
    with torch.no_grad():
        for name in dict.fromkeys(name for name, _ in jobs):
            words[name] = pipe.encode_prompt(prompt=catalogue.ART[name][0], device='cuda', do_classifier_free_guidance=False)[0]
    pipe.text_encoder.to('cpu')
    torch.cuda.empty_cache()
    # In bfloat16 the denoiser is 12 GB, which with the desktop's share overfills a 16 GB card and spills into system
    # memory: 49 s a picture. Kept in 8 bit and widened a layer at a time, it is half the size.
    if torch.cuda.get_device_properties(0).total_memory < 24 * 2 ** 30:
        pipe.transformer.enable_layerwise_casting(storage_dtype=torch.float8_e4m3fn, compute_dtype=torch.bfloat16)
    pipe.transformer.to('cuda')
    pipe.vae.to('cuda')

    for name, seed in jobs:
        _, _, width, height = catalogue.ART[name]
        width, height = size(width, height)
        generator = torch.Generator('cuda').manual_seed(seed)
        with torch.no_grad():
            picture = pipe(prompt_embeds=words[name], width=width, height=height, num_inference_steps=9, guidance_scale=0.0,
                           generator=generator).images[0]
        (out / name).mkdir(parents=True, exist_ok=True)
        picture.save(out / name / f'{seed}.png')
        print('made', name, seed, flush=True)


if __name__ == '__main__':
    main()
