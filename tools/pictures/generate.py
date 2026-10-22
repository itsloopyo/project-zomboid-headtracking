"""Makes the pictures that hang on walls in the first person view, from the text prompts in catalogue.py alone.

Two models, both Apache 2.0, each at about a million pixels in the picture's shape:
- qwen: Qwen-Image (Qwen), 40 steps with true classifier free guidance and the NEGATIVE prompt below.
- zimage: Z-Image Turbo (Tongyi-MAI), 9 steps and no guidance, as its model card gives, so it cannot take a negative.
Run with the venv that has torch and diffusers (see .lab/NOTES.md, "Generated materials"):
    python tools/pictures/generate.py --model qwen --out DIR [--only family-00 ...] [--starting family- boxing-]
        [--most 20] [--candidates 4] [--shard 2/5] [--seeds 1 3]
Writes DIR/<name>/<seed>.png and the prompt it was made from beside it as <seed>.txt, for seeds 1 to --candidates, and
skips what is there already, so a long list is made a few at a time. --shard k/n makes every n-th job from the k-th, so
n machines can share a list; --seeds makes only those candidates. The candidate picked for each picture goes into
catalogue.SEEDS, and tools/pictures/pack.py puts them into the mod.
"""
import argparse
import pathlib

import torch
from diffusers import QwenImagePipeline, ZImagePipeline

import catalogue

# The commit of each model on the hub that the pictures here were made with.
MODELS = {
    'qwen': (QwenImagePipeline, 'Qwen/Qwen-Image', '75e0b4be04f60ec59a75f475837eced720f823b6'),
    'zimage': (ZImagePipeline, 'Tongyi-MAI/Z-Image-Turbo', 'f332072aa78be7aecdf3ee76d5c247082da564a6'),
}
# What Qwen-Image is steered away from. The television entries keep a still from a broadcast from being drawn as the
# set that shows it.
NEGATIVE = ('logo, brand, lettering, text, words, numbers, watermark, signature, border, frame, white margin, black bars,'
            ' television set, monitor bezel, screen frame, rounded corners, vignette, deformed hands, extra fingers,'
            ' cloned identical faces, airbrushed skin, glossy, CGI, 3d render')
PIXELS = 1024 * 1024


def size(width, height):
    """The catalogue's shape at about a million pixels, each side a multiple of 64."""
    scale = (PIXELS / (width * height)) ** 0.5
    return round(width * scale / 64) * 64, round(height * scale / 64) * 64


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--model', required=True, choices=sorted(MODELS))
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

    pipeline, repo, revision = MODELS[args.model]
    pipe = pipeline.from_pretrained(repo, revision=revision, torch_dtype=torch.bfloat16)
    pipe.set_progress_bar_config(disable=True)
    # The words are worked out once with the text model on the card, which then leaves it to the denoiser.
    pipe.text_encoder.to('cuda')
    words = {}
    with torch.no_grad():
        for name in dict.fromkeys(name for name, _ in jobs):
            if args.model == 'qwen':
                words[name] = pipe.encode_prompt(prompt=catalogue.ART[name][0], device='cuda')
            else:
                words[name] = pipe.encode_prompt(prompt=catalogue.ART[name][0], device='cuda',
                                                 do_classifier_free_guidance=False)[0]
        negative = pipe.encode_prompt(prompt=NEGATIVE, device='cuda') if args.model == 'qwen' else None
    pipe.text_encoder.to('cpu')
    torch.cuda.empty_cache()
    # In bfloat16 Qwen-Image's denoiser is 40 GB and Z-Image's 12 GB, more than a 32 GB card or (with the desktop's
    # share) a 16 GB one holds: Z-Image spilled into system memory at 49 s a picture. Kept in 8 bit and widened a layer
    # at a time, each is half the size.
    limit = 48 if args.model == 'qwen' else 24
    if torch.cuda.get_device_properties(0).total_memory < limit * 2 ** 30:
        pipe.transformer.enable_layerwise_casting(storage_dtype=torch.float8_e4m3fn, compute_dtype=torch.bfloat16)
    pipe.transformer.to('cuda')
    pipe.vae.to('cuda')

    for name, seed in jobs:
        _, width, height = catalogue.ART[name]
        width, height = size(width, height)
        generator = torch.Generator('cuda').manual_seed(seed)
        with torch.no_grad():
            if args.model == 'qwen':
                picture = pipe(prompt_embeds=words[name][0], prompt_embeds_mask=words[name][1],
                               negative_prompt_embeds=negative[0], negative_prompt_embeds_mask=negative[1],
                               true_cfg_scale=4.0, num_inference_steps=40, width=width, height=height,
                               generator=generator).images[0]
            else:
                picture = pipe(prompt_embeds=words[name], width=width, height=height, num_inference_steps=9,
                               guidance_scale=0.0, generator=generator).images[0]
        (out / name).mkdir(parents=True, exist_ok=True)
        # Written aside and renamed, so a run stopped mid-save leaves no half picture that later runs would skip.
        picture.save(out / name / f'{seed}.part', format='PNG')
        (out / name / f'{seed}.part').replace(out / name / f'{seed}.png')
        # Prompts are reworded between rounds, so each candidate keeps the words it was made from.
        (out / name / f'{seed}.txt').write_text(catalogue.ART[name][0], encoding='utf-8')
        print('made', name, seed, flush=True)


if __name__ == '__main__':
    main()
