"""Generates the pictures the first person view is textured with, from text prompts alone.

Two sorts. A SURFACE is a picture of a material that joins up with itself at its edges, made at
1024 and then worked over at 2048 so that it has detail of its own at that size. A SPRIG is one
branch tip in leaf on white, cut out of its ground: the crowns of trees and bushes are built of them.

Stable Diffusion XL base 1.0, with every convolution of the denoiser wrapping round the picture's
edges for a surface. The picture is taken out of and put into the model's own space in pieces that
overlap and wrap, since done whole at 2048 it needs more memory than the card has.

Run with the venv that has torch and diffusers (see .lab/NOTES.md, "Generated materials"):
    python tools/materials/generate.py --out DIR [--only bark-oak litter ...] [--seeds 1993 7] [--size 2048]
Writes DIR/<name>-<seed>.png. tools/materials/pack.py puts the chosen ones into the mod.
"""
import argparse
import math
import pathlib

import numpy
import torch
from diffusers import StableDiffusionXLImg2ImgPipeline, StableDiffusionXLPipeline
from PIL import Image

MODEL = 'stabilityai/stable-diffusion-xl-base-1.0'
# The commit of the model on the hub that every picture here was made with.
REVISION = '462165984030d82259a11f4367a4eed129e94a7b'
EVEN = 'flat even overcast lighting, seamless texture, photorealistic, sharp focus, highly detailed, 8k'
BARK = 'close-up photograph of '
WHOLE = ' filling the whole frame, '
SURFACES = {
    'asphalt': 'top down photograph of old weathered asphalt road surface, fine dark aggregate with pale flecks of stone, hairline cracks, ' + EVEN,
    'gravel': 'top down photograph of a compacted gravel and crushed stone driveway, small pale angular stones and grit, ' + EVEN,
    'soil': 'top down photograph of bare garden topsoil, fine dark brown crumbly earth with a few tiny stones, roots and bits of dry grass, ' + EVEN,
    'grass': 'top down photograph of a lush mown lawn seen from directly above, short dense fresh green grass blades with a little clover, ' + EVEN,
    'shingles': 'front view photograph of a roof covered in rectangular grey asphalt shingles laid in perfectly horizontal rows, each row offset by half a shingle, granular mineral surface, slight weathering, ' + EVEN,
    'wood': 'top down photograph of worn varnished oak floor boards running straight, warm visible wood grain, fine scratches, ' + EVEN,
    'bark-oak': BARK + 'rough mature oak tree bark' + WHOLE + 'deep vertical furrows and thick corky ridges, grey brown with traces of green lichen, ' + EVEN,
    'bark-linden': BARK + 'rough old linden tree bark' + WHOLE + 'many fine shallow vertical fissures between long flat grey brown ridges that cross and join, ' + EVEN,
    'bark-maple': BARK + 'rough mature maple tree bark' + WHOLE + 'deep irregular furrows and thick scaly ridges, dark grey brown with patches of moss, ' + EVEN,
    'bark-birch': BARK + 'paper birch tree bark' + WHOLE + 'white and cream papery bark peeling in thin curls, dark horizontal lenticel lines, patches of tan, ' + EVEN,
    'bark-pine': BARK + 'rough mature pine tree bark' + WHOLE + 'deep vertical furrows and thick flaky ridges, reddish brown, ' + EVEN,
    'bark-hemlock': BARK + 'rough old spruce tree bark' + WHOLE + 'dark brown with narrow vertical furrows and small flaky ridges, hints of red, ' + EVEN,
    'bark-holly': BARK + 'smooth grey beech tree bark' + WHOLE + 'thin pale silver grey bark with faint horizontal wrinkles and patches of pale lichen, ' + EVEN,
    'bark-dogwood': BARK + 'rough mature elm tree bark' + WHOLE + 'deep vertical furrows and blocky corky ridges broken by cross cracks, dark grey, ' + EVEN,
    'planks': 'front view photograph of an old weathered wooden fence, vertical grey brown planks side by side, visible wood grain, knots, nail heads and cracks, ' + EVEN,
    'carpet': 'top down macro photograph of plain wall to wall carpet, short dense cut pile fibres, tufts of twisted yarn, uniform beige, ' + EVEN,
    'vinyl': 'close-up photograph of dark charcoal grey leather, fine natural pebbled grain, soft matte surface, uniform, ' + EVEN,
    'cloth': 'flat scan of grey sofa upholstery fabric stretched taut, tight basket weave of fine threads, lying perfectly flat, uniform, ' + EVEN,
    'fur': 'top down macro photograph of a thick white sheepskin rug, dense soft curly wool locks, deep shaggy pile, uniform, ' + EVEN,
    'hide': 'top down macro photograph of a cowhide rug, short smooth glossy hair lying flat in one direction, fine strands, uniform pale cream, ' + EVEN,
    'paint': 'macro photograph of a painted interior plaster wall seen straight on, matte white emulsion paint with a fine even orange peel roller stipple, uniform, ' + EVEN,
    'jersey': 'macro photograph of plain grey cotton fabric, fine even weave of threads, soft worn cloth, uniform, ' + EVEN,
    'litter': 'top down photograph of a forest floor seen from directly above, covered edge to edge in a dense layer of dry brown and orange fallen oak and beech leaves with a few small twigs, ' + EVEN,
    'rock': 'close-up photograph of a rough grey limestone rock face filling the whole frame, natural fractured stone with cracks, ledges and patches of pale lichen, ' + EVEN,
    'canopy': 'photograph looking straight into the dense outer foliage of a broadleaf tree, hundreds of overlapping fresh green leaves filling the whole frame edge to edge, deep dark gaps between the leaves, bright daylight, photorealistic, sharp focus, highly detailed, 8k',
    'hedge': 'front view photograph of a dense clipped privet hedge filling the whole frame, hundreds of small glossy oval green leaves, deep dark gaps between the leaves, bright daylight, photorealistic, sharp focus, highly detailed, 8k',
    'needles': 'photograph looking straight into the dense foliage of a pine tree, overlapping sprays of long green pine needles on brown twigs filling the whole frame edge to edge, deep dark gaps between the sprays, bright daylight, photorealistic, sharp focus, highly detailed, 8k',
    'ivy': 'front view photograph of english ivy covering a surface completely, overlapping glossy dark green lobed ivy leaves with pale veins filling the whole frame, deep dark gaps between the leaves, photorealistic, sharp focus, highly detailed, 8k',
}
NEGATIVE = 'shadows, perspective, horizon, objects, leaves, text, watermark, blurry, painting, illustration, vignette, border, tiles, lines'
OWN_NEGATIVE = {
    'litter': 'shadows, perspective, horizon, text, watermark, blurry, painting, illustration, vignette, border, tiles, lines, green grass, stones, mushrooms, moss',
    'rock': 'shadows, perspective, horizon, text, watermark, blurry, painting, illustration, vignette, border, tiles, lines, bricks, mortar, pebbles, leaves, objects, concrete, sand',
    'cloth': 'folds, wrinkles, creases, drapery, waves, ripples, stripes, shadows, perspective, text, watermark, blurry, painting, illustration, vignette, border, tiles, lines',
    'fur': 'edge of the rug, floor, folds, shadows, perspective, objects, text, watermark, blurry, painting, illustration, vignette, border, tiles, lines',
    'hide': 'spots, patches, edge of the rug, floor, folds, shadows, perspective, objects, text, watermark, blurry, painting, illustration, vignette, border, tiles, lines',
    'paint': 'cracks, stains, dirt, holes, stripes, ribs, brush strokes, wood grain, bricks, tiles, lines, shadows, perspective, text, watermark, blurry, painting, illustration, vignette, border',
    'grass': 'shadows, perspective, horizon, objects, fallen leaves, text, watermark, blurry, painting, illustration, vignette, border, tiles, lines, flowers, soil, path',
}
FOLIAGE_NEGATIVE = 'sky, trunk, branches, flowers, fruit, text, watermark, blurry, bokeh, depth of field, painting, illustration, vignette, border, brown, yellow, autumn, wall, bricks, ground'
BARK_NEGATIVE = 'perspective, horizon, objects, leaves, text, watermark, blurry, painting, illustration, vignette, border, tiles, sky, branches, whole tree, planks, sawn timber, diagonal, stones, scales, 3d render'
for _name in SURFACES:
    if _name.startswith('bark-'):
        OWN_NEGATIVE[_name] = BARK_NEGATIVE
for _name in ('canopy', 'hedge', 'needles', 'ivy'):
    OWN_NEGATIVE[_name] = FOLIAGE_NEGATIVE

# A prompt is cut off after 77 tokens, so these are kept short.
ON_WHITE = 'isolated on a pure white background, flat lay seen from above, even studio lighting, botanical specimen photograph, sharp focus, highly detailed'
SPRIGS = {
    'sprig-broadleaf': 'one small leafy branch cut from a linden tree, a thin brown twig with many fresh green heart shaped leaves attached all along it, ' + ON_WHITE,
    'sprig-maple': 'one small leafy branch cut from a maple tree, a thin brown twig with many fresh green lobed maple leaves attached all along it, ' + ON_WHITE,
    'sprig-oak': 'one small leafy branch cut from an oak tree, a thin brown twig with many fresh green lobed oak leaves attached all along it, ' + ON_WHITE,
    'sprig-pine': 'one small branch cut from a pine tree, a brown scaly twig thickly covered in long slender green pine needles, ' + ON_WHITE,
    'sprig-hemlock': 'one small branch cut from a hemlock fir tree, a thin brown twig with flat feathery sprays of short dark green needles, ' + ON_WHITE,
    'sprig-holly': 'one small leafy branch cut from a holly tree, a thin twig with many glossy dark green spiny holly leaves attached all along it, ' + ON_WHITE,
    'sprig-shrub': 'one small leafy branch cut from a boxwood shrub, a thin twig thickly covered in very many small glossy oval green leaves, ' + ON_WHITE,
    'sprig-fern': 'one single green fern frond with many fine feathery leaflets, its straight stem running up the middle of the picture, ' + ON_WHITE,
}
SPRIG_NEGATIVE = 'shadow, drop shadow, vignette, grey background, dark background, gradient, text, watermark, label, pot, vase, hands, flowers, berries, fruit, illustration, painting, drawing, blurry, bokeh, brown leaves, yellow leaves, autumn, several branches, frame, border'


def wrapping(module, on):
    """Convolutions that wrap round the image's edges, so the left edge continues the right, or plain ones again."""
    for layer in module.modules():
        if isinstance(layer, torch.nn.Conv2d):
            layer.padding_mode = 'circular' if on else 'zeros'


def window(length, device):
    """Weights across a piece that, laid every half a piece, add up to 1 everywhere."""
    t = (torch.arange(length, device=device, dtype=torch.float32) + 0.5) / length
    return torch.sin(math.pi * t) ** 2


def in_pieces(run, source, piece, margin, scale, wrap):
    """
    Puts source through run a piece at a time and joins the results. Each piece is given margin
    more on every side to see by, which is cut off what comes back, and the pieces are laid every
    half a piece and mixed across their overlap. With wrap the picture's far edge is what lies
    beyond its near one; without, what lies beyond an edge is the edge mirrored.
    """
    _, _, high, wide = source.shape
    if high <= piece and wide <= piece and not wrap:
        return run(source)
    # The last piece may start anywhere up to the far edge, so there is a whole piece's room beyond it.
    near, far = piece // 2 + margin, piece + margin
    padded = torch.nn.functional.pad(source, (near, far, near, far), mode='circular' if wrap else 'reflect')
    out = None
    weight = None
    w = window(int(piece * scale), source.device)
    w2 = w[:, None] * w[None, :]
    half = piece // 2
    # Pieces start half a piece before the picture's edge, so the edge too is covered twice.
    for top in range(-half, high, half):
        for left in range(-half, wide, half):
            y, x = top + half, left + half
            cut = padded[:, :, y:y + piece + 2 * margin, x:x + piece + 2 * margin]
            made = run(cut).float()
            m = int(margin * scale)
            made = made[:, :, m:m + int(piece * scale), m:m + int(piece * scale)]
            if out is None:
                out = torch.zeros(made.shape[0], made.shape[1], int((high + 2 * piece) * scale), int((wide + 2 * piece) * scale), device=made.device)
                weight = torch.zeros(1, 1, out.shape[2], out.shape[3], device=made.device)
            oy, ox = int((top + half) * scale), int((left + half) * scale)
            out[:, :, oy:oy + made.shape[2], ox:ox + made.shape[3]] += made * w2
            weight[:, :, oy:oy + made.shape[2], ox:ox + made.shape[3]] += w2
    out = out / weight.clamp(min=1e-4)
    h = int(half * scale)
    return out[:, :, h:h + int(high * scale), h:h + int(wide * scale)]


class Maker:
    def __init__(self):
        self.pipe = StableDiffusionXLPipeline.from_pretrained(MODEL, revision=REVISION, torch_dtype=torch.float16, variant='fp16', use_safetensors=True)
        self.pipe.set_progress_bar_config(disable=True)
        self.again = StableDiffusionXLImg2ImgPipeline(**self.pipe.components)
        self.again.set_progress_bar_config(disable=True)
        self.vae = self.pipe.vae
        self.factor = self.vae.config.scaling_factor

    def words(self, prompts):
        """What the model makes of each prompt and its negative, worked out once with the text models on the card, which then leave it."""
        self.pipe.text_encoder.to('cuda')
        self.pipe.text_encoder_2.to('cuda')
        out = {}
        with torch.no_grad():
            for name, (prompt, negative) in prompts.items():
                out[name] = self.pipe.encode_prompt(prompt=prompt, negative_prompt=negative, device='cuda', do_classifier_free_guidance=True)
        self.pipe.text_encoder.to('cpu')
        self.pipe.text_encoder_2.to('cpu')
        torch.cuda.empty_cache()
        self.pipe.unet.to('cuda')
        # The half-precision decoder overflows to black: it runs in single.
        self.vae.to('cuda', torch.float32)
        return out

    def given(self, words):
        prompt, negative, pooled, negative_pooled = words
        return dict(prompt_embeds=prompt, negative_prompt_embeds=negative, pooled_prompt_embeds=pooled, negative_pooled_prompt_embeds=negative_pooled)

    @torch.no_grad()
    def shown(self, latents, wrap):
        """The picture the model's own numbers stand for, -1 to 1."""
        return in_pieces(lambda cut: self.vae.decode(cut / self.factor).sample, latents.float(), 96, 16, 8, wrap).clamp(-1, 1)

    @torch.no_grad()
    def taken(self, picture, wrap):
        return in_pieces(lambda cut: self.vae.encode(cut).latent_dist.mean * self.factor, picture.float(), 768, 128, 1 / 8, wrap)

    @torch.no_grad()
    def make(self, words, seed, size, wrap, guidance):
        wrapping(self.pipe.unet, wrap)
        generator = torch.Generator('cuda').manual_seed(seed)
        latents = self.pipe(**self.given(words), width=1024, height=1024, num_inference_steps=32, guidance_scale=guidance, generator=generator,
                            output_type='latent').images
        picture = self.shown(latents, wrap)
        if size > 1024:
            # Enlarged as a picture, then given back to the model part noised, to be finished at the larger size.
            large = torch.nn.functional.interpolate(picture, size=(size, size), mode='bicubic', align_corners=False).clamp(-1, 1)
            latents = self.taken(large, wrap).to(torch.float16)
            latents = self.again(**self.given(words), image=latents, strength=0.42, num_inference_steps=30, guidance_scale=guidance, generator=generator,
                                 output_type='latent').images
            picture = self.shown(latents, wrap)
        return self.pixels(picture)

    @torch.no_grad()
    def over(self, words, seed, source, strength, wrap, guidance):
        """The source picture given to the model part noised and finished at its own size: its layout kept, its detail the model's."""
        wrapping(self.pipe.unet, wrap)
        generator = torch.Generator('cuda').manual_seed(seed)
        picture = torch.from_numpy(numpy.asarray(source.convert('RGB'), dtype=numpy.float32) / 127.5 - 1.0).permute(2, 0, 1)[None].to('cuda')
        latents = self.taken(picture, wrap).to(torch.float16)
        latents = self.again(**self.given(words), image=latents, strength=strength, num_inference_steps=40, guidance_scale=guidance, generator=generator,
                             output_type='latent').images
        return self.pixels(self.shown(latents, wrap))

    @staticmethod
    def pixels(picture):
        pixels = ((picture[0].permute(1, 2, 0).cpu().numpy() + 1.0) * 127.5).round().clip(0, 255).astype(numpy.uint8)
        return Image.fromarray(pixels, 'RGB')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', required=True)
    parser.add_argument('--only', nargs='*')
    parser.add_argument('--seeds', nargs='*', type=int, default=[1993])
    parser.add_argument('--size', type=int, default=2048)
    parser.add_argument('--sprig-size', type=int, default=1024)
    # A surface made over a picture of its own (tools/materials/paint.py) rather than from noise: that picture, and how much of it is noised away.
    parser.add_argument('--over')
    parser.add_argument('--strengths', nargs='*', type=float, default=[0.5])
    args = parser.parse_args()
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    wanted = {}
    for name, prompt in SURFACES.items():
        if not args.only or name in args.only:
            wanted[name] = (prompt, OWN_NEGATIVE.get(name, NEGATIVE))
    for name, prompt in SPRIGS.items():
        if not args.only or name in args.only:
            wanted[name] = (prompt, SPRIG_NEGATIVE)
    maker = Maker()
    words = maker.words(wanted)
    if args.over:
        source = Image.open(args.over)
        for name in wanted:
            for seed in args.seeds:
                for strength in args.strengths:
                    path = out / f'{name}-{seed}-{round(strength * 100)}.png'
                    if not path.exists():
                        maker.over(words[name], seed, source, strength, True, 6.0).save(path)
                        print('made', path.name, flush=True)
        return
    for name in wanted:
        surface = name in SURFACES
        for seed in args.seeds:
            path = out / f'{name}-{seed}.png'
            if path.exists():
                continue
            image = maker.make(words[name], seed, args.size if surface else args.sprig_size, surface, 6.0 if surface else 7.0)
            image.save(path)
            print('made', path.name, flush=True)


if __name__ == '__main__':
    main()
