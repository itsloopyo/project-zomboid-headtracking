"""Makes the pictures that hang on walls in the first person view, from the text prompts in catalogue.py alone.

Stable Diffusion XL base 1.0 at about a million pixels, the size it was trained at. Run with the venv that has torch and
diffusers (see .lab/NOTES.md, "Generated materials"):
    python tools/pictures/generate.py --out DIR [--only family-00 music-03 ...] [--starting family- boxing-] [--most 20]
Writes DIR/<name>-<seed>.png, the seed 1993 or the one catalogue.py gives the picture, and skips what is there already, so a long list is made a few at a time.
tools/pictures/pack.py puts them into the mod.
"""
import argparse
import pathlib

import numpy
import torch
from diffusers import StableDiffusionXLPipeline
from PIL import Image

import catalogue

MODEL = 'stabilityai/stable-diffusion-xl-base-1.0'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', required=True)
    parser.add_argument('--only', nargs='*', default=[])
    parser.add_argument('--starting', nargs='*', default=[])
    parser.add_argument('--most', type=int, default=0)
    args = parser.parse_args()
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    unknown = [name for name in args.only if name not in catalogue.ART]
    if unknown:
        raise SystemExit('no such picture: ' + ' '.join(unknown))
    chosen = bool(args.only or args.starting)
    wanted = [name for name in catalogue.ART
              if (not chosen or name in args.only or any(name.startswith(start) for start in args.starting))
              and not (out / f'{name}-{catalogue.SEEDS.get(name, 1993)}.png').exists()]
    if args.most:
        wanted = wanted[:args.most]
    if not wanted:
        print('nothing to make', flush=True)
        return

    pipe = StableDiffusionXLPipeline.from_pretrained(MODEL, torch_dtype=torch.float16, variant='fp16', use_safetensors=True)
    pipe.set_progress_bar_config(disable=True)
    # The words are worked out once with the text models on the card, which then leave it to the denoiser.
    pipe.text_encoder.to('cuda')
    pipe.text_encoder_2.to('cuda')
    words = {}
    with torch.no_grad():
        for name in wanted:
            prompt, negative, _, _ = catalogue.ART[name]
            words[name] = pipe.encode_prompt(prompt=prompt, negative_prompt=negative, device='cuda', do_classifier_free_guidance=True)
    pipe.text_encoder.to('cpu')
    pipe.text_encoder_2.to('cpu')
    torch.cuda.empty_cache()
    pipe.unet.to('cuda')
    # The half-precision decoder overflows to black: it runs in single.
    pipe.vae.to('cuda', torch.float32)
    factor = pipe.vae.config.scaling_factor

    for name in wanted:
        _, _, width, height = catalogue.ART[name]
        prompt, negative, pooled, negative_pooled = words[name]
        seed = catalogue.SEEDS.get(name, 1993)
        generator = torch.Generator('cuda').manual_seed(seed)
        with torch.no_grad():
            latents = pipe(prompt_embeds=prompt, negative_prompt_embeds=negative, pooled_prompt_embeds=pooled,
                           negative_pooled_prompt_embeds=negative_pooled, width=width, height=height, num_inference_steps=36,
                           guidance_scale=6.5, generator=generator, output_type='latent').images
            picture = pipe.vae.decode(latents.float() / factor).sample.clamp(-1, 1)
        pixels = ((picture[0].permute(1, 2, 0).cpu().numpy() + 1.0) * 127.5).round().clip(0, 255).astype(numpy.uint8)
        Image.fromarray(pixels, 'RGB').save(out / f'{name}-{seed}.png')
        print('made', name, flush=True)


if __name__ == '__main__':
    main()
