"""Generates the pictures the first person view is textured with, from text prompts alone.

Two sorts. A SURFACE is a picture of a material that joins up with itself at its edges. A SPRIG is
one branch tip in leaf on white, cut out of its ground: the crowns of trees and bushes are built of them.

Z-Image Turbo (Tongyi-MAI, Apache 2.0) at 1024, 9 steps, no guidance, as its model card gives. Its
denoiser is a transformer that sees the picture as a grid of patches with no edge wrapping round, so for
a surface it is shown the picture rolled round by a new amount at every step: once at each of the first
steps, where it lays the picture out, and four times at each after, rolled again by half its size across,
down and both, what it makes of each place taken from the four mixed, each counting for less the nearer
the place is to the edge of that view. A place on the edge of one view is in the middle of another.
Every convolution of the decoder wraps round the picture's edges too.

Run with the venv that has torch and diffusers (see .lab/NOTES.md, "Generated materials"):
    python tools/materials/generate.py --out DIR [--only bark-oak litter ...] [--seeds 1 2 3 4]
Writes DIR/<name>-<seed>.png. tools/materials/pack.py puts the chosen ones into the mod.
The surfaces in the mod are 2048: each chosen 1024 picture enlarged to 2048 round its edges (tiled three by three,
enlarged, the middle kept) and made again over that with its prompt and seed, --over ENLARGED --strengths 0.4.
"""
import argparse
import pathlib

import numpy
import torch
from diffusers import ZImageImg2ImgPipeline, ZImagePipeline
from PIL import Image

MODEL = 'Tongyi-MAI/Z-Image-Turbo'
# The commit of the model on the hub that every picture here was made with.
REVISION = 'f332072aa78be7aecdf3ee76d5c247082da564a6'
STEPS = 9
SHOT = (' A plain reference photograph for a texture library, taken straight on in soft even overcast light with no shadows falling across it,'
        ' in natural muted colours with fine film grain.')
LEAFY = ' A plain reference photograph taken in bright daylight, in natural colours with fine film grain.'
# Asked for "the trunk of a tree, its bark filling the frame", it drew the whole trunk standing in front of a white wall.
BARK = 'A close photograph of a patch of tree bark seen from a few inches away, so close that it fills the whole frame from edge to edge. It is the bark of '
SURFACES = {
    'asphalt': 'Looking straight down at a patch of old weathered asphalt that fills the whole frame: fine dark grey aggregate with small pale '
               'flecks of stone and a few hairline cracks, worn evenly all over, with no markings and nothing lying on it.' + SHOT,
    'gravel': 'Looking straight down at a bed of compacted gravel that fills the whole frame: small pale grey angular crushed stones packed in grit '
              'and dust, spread evenly all over, with no larger rocks and nothing else on it.' + SHOT,
    'soil': 'Looking straight down at bare garden topsoil that fills the whole frame: fine dark brown crumbly earth with a few tiny stones, thin pale '
            'roots and scraps of dry grass, evenly spread.' + SHOT,
    'grass': 'Looking straight down at an ordinary small-town lawn that fills the whole frame: short mown grass with some clover leaves among it, a few '
             'dry yellowed blades, and patches a little lighter and darker than the rest, in the dusty muted greens of a lawn in July.' + SHOT,
    'shingles': 'Looking straight at a roof covered in grey asphalt shingles that fills the whole frame: rectangular tabs laid in perfectly straight '
                'horizontal rows, each row offset by half a tab from the one below, with a gritty mineral surface and light weathering.' + SHOT,
    'wood': 'Looking straight down at a worn varnished oak floor that fills the whole frame: long straight boards that each run from the top of the '
            'frame to the bottom with no end joints, warm wood grain, fine scratches, each board a slightly different tone.' + SHOT,
    'bark-oak': BARK + 'a mature oak tree: deep vertical furrows between thick corky '
                'ridges, grey brown with traces of green lichen.' + SHOT,
    'bark-linden': BARK + 'an old linden tree: many fine shallow vertical fissures '
                   'between long flat grey brown ridges that cross and join.' + SHOT,
    'bark-maple': BARK + 'a mature maple tree: deep irregular furrows and thick scaly '
                  'ridges, dark grey brown with patches of moss.' + SHOT,
    'bark-birch': BARK + 'a paper birch tree: chalky white bark with thin papery curls '
                  'peeling here and there, many short dark horizontal lenticel slits scattered across it, and a few rough black scars.' + SHOT,
    'bark-pine': BARK + 'a mature pine tree: deep vertical furrows and thick flaky '
                 'plates, reddish brown.' + SHOT,
    'bark-hemlock': BARK + 'an old spruce tree: dark brown with narrow vertical '
                    'furrows and small flaky ridges, with hints of red.' + SHOT,
    'bark-holly': BARK + 'a beech tree: smooth thin pale silver grey bark with faint '
                  'horizontal wrinkles and patches of pale lichen.' + SHOT,
    'bark-dogwood': BARK + 'a mature elm tree: deep vertical furrows and blocky '
                    'corky ridges broken by cross cracks, dark grey.' + SHOT,
    'planks': 'Looking straight at an old weathered wooden fence that fills the whole frame: vertical grey brown boards standing side by side, '
              'visible grain, knots, nail heads and small cracks.' + SHOT,
    'carpet': 'Looking straight down at plain beige wall-to-wall carpet seen close, filling the whole frame: a short dense cut pile of twisted yarn '
              'tufts, the same all over.' + SHOT,
    'vinyl': 'A close photograph of dark charcoal grey leather upholstery lying flat and filling the whole frame: a fine natural pebbled grain and a '
             'soft matte surface, the same all over.' + SHOT,
    'cloth': 'A close photograph of a flat swatch of grey upholstery fabric laid on a table and filling the whole frame: a tight basket weave '
             'of fine threads, perfectly flat, with no folds, seams or cushions.' + SHOT,
    'fur': 'Looking straight down into a thick white sheepskin rug seen close, filling the whole frame: dense soft curly wool locks in a deep shaggy '
           'pile, the same all over, with no edge of the rug in sight.' + SHOT,
    'hide': 'Looking straight down at a pale cream cowhide rug seen close, filling the whole frame: short smooth hair lying flat in one direction, '
            'every fine strand visible, plain cream all over with no spots, no folds and no edge of the rug in sight.' + SHOT,
    'paint': 'A close photograph of a painted interior plaster wall seen straight on, filling the whole frame: matte white emulsion paint with a '
             'fine even orange peel roller stipple and nothing else on it.' + SHOT,
    'jersey': 'A close photograph of plain grey cotton fabric laid flat and filling the whole frame: a fine even weave of soft threads, a little '
              'worn, with no folds.' + SHOT,
    'litter': 'Looking straight down at a forest floor that fills the whole frame, covered edge to edge in a dense layer of dry brown and orange '
              'fallen oak and beech leaves with a few small twigs.' + SHOT,
    'straw': 'Looking straight down at the floor of a barn stall that fills the whole frame, covered edge to edge in a thick even bed of loose dry '
             'golden wheat straw: thousands of long hollow stalks lying every which way across each other, with nothing else in it.' + SHOT,
    'rock': 'A close photograph of a rough grey limestone outcrop filling the whole frame: natural fractured stone with cracks, small ledges and '
            'patches of pale lichen, gritty and weathered.' + SHOT,
    'canopy': 'Looking straight into the dense outer foliage of a broadleaf tree: hundreds of overlapping fresh green leaves fill the whole frame '
              'edge to edge, with deep dark gaps between them.' + LEAFY,
    'hedge': 'Looking straight at a dense clipped privet hedge that fills the whole frame: hundreds of small glossy oval green leaves with deep '
             'dark gaps between them.' + LEAFY,
    'needles': 'Looking straight into the dense foliage of a pine tree: overlapping sprays of long green pine needles on brown twigs fill the '
               'whole frame edge to edge, with deep dark gaps between the sprays.' + LEAFY,
    'ivy': 'Looking straight at English ivy covering a surface completely: overlapping dark green lobed ivy leaves with pale veins fill the '
           'whole frame, with deep dark gaps between them.' + LEAFY,
}
ON_WHITE = (' It lies alone on a plain pure white background, seen from directly above. A botanical specimen photograph in even studio light'
            ' with no shadow under it.')
SPRIGS = {
    'sprig-broadleaf': 'One small leafy branch cut from a linden tree: a thin brown twig with many fresh green heart shaped leaves along it.' + ON_WHITE,
    'sprig-maple': 'One small leafy branch cut from a maple tree: a thin brown twig with many fresh green lobed maple leaves along it.' + ON_WHITE,
    'sprig-oak': 'One small leafy branch cut from an oak tree: a thin brown twig with many fresh green lobed oak leaves along it.' + ON_WHITE,
    'sprig-pine': 'One small branch cut from a pine tree: a brown scaly twig thickly covered in long slender green pine needles.' + ON_WHITE,
    'sprig-hemlock': 'One small branch cut from a hemlock tree: a thin brown twig with flat feathery sprays of short dark green needles.' + ON_WHITE,
    'sprig-holly': 'One small leafy branch cut from a holly tree: a thin twig with many glossy dark green spiny holly leaves along it.' + ON_WHITE,
    'sprig-shrub': 'One small leafy branch cut from a boxwood shrub: a thin twig thickly covered in very many small glossy oval green leaves.' + ON_WHITE,
    'sprig-fern': 'One single green fern frond with many fine feathery leaflets, its straight stem running up the middle of the picture.' + ON_WHITE,
}


def wrapping(module, on):
    """Convolutions that wrap round the picture's edges, so the left edge continues the right, or plain ones again."""
    for layer in module.modules():
        if isinstance(layer, torch.nn.Conv2d):
            layer.padding_mode = 'circular' if on else 'zeros'


# How many of the first steps, where the model lays the picture out, show it the picture once, rolled round by a new
# amount each step, rather than four times.
LAID_OUT = 2


class Wrapped:
    """
    The denoiser shown the latent picture rolled round four ways, and what it makes of each place mixed from the four by how
    far the place is from the edge of each. Rolled round a new way between steps instead, the picture kept each step's edge as
    a faint straight line, which showed on carpet, paint and leather; laid out wider with itself wrapped round past its edges,
    every place the model could see twice came out as a jumble. Shown four ways from the first step, rolled the same four ways
    at every step, the model put the middle of a photograph, a blot or a bright spot, at the middle of each view, so the
    picture came out the same thing four times over half its size: the four views are rolled round by a new amount every step,
    and the layout is the model's own, from LAID_OUT steps of one view.
    """

    def __init__(self, plain, seed):
        self.plain = plain
        self.chance = torch.Generator().manual_seed(seed)
        self.step = 0

    def __call__(self, latents, *given, **named):
        high, wide = latents[0].shape[-2:]
        # Two latent dots a side are one of the transformer's patches: a picture is only ever rolled by whole patches.
        start = (2 * int(torch.randint(high // 2, (1,), generator=self.chance)), 2 * int(torch.randint(wide // 2, (1,), generator=self.chance)))
        self.step += 1
        if self.step <= LAID_OUT:
            out = self.plain([torch.roll(each, start, dims=(-2, -1)) for each in latents], *given, **named)
            return ([torch.roll(each, (-start[0], -start[1]), dims=(-2, -1)) for each in out[0]],) + tuple(out[1:])
        # Across each view a weight of sin squared, which with the view rolled half way adds up to one at every place.
        down = torch.sin(torch.pi * (torch.arange(high, device=latents[0].device) + 0.5) / high) ** 2
        across = torch.sin(torch.pi * (torch.arange(wide, device=latents[0].device) + 0.5) / wide) ** 2
        weight = down[:, None] * across[None, :]
        mixed = None
        # Half of either side is a whole number of patches.
        for half in ((0, 0), (high // 2, 0), (0, wide // 2), (high // 2, wide // 2)):
            shift = (start[0] + half[0], start[1] + half[1])
            out = self.plain([torch.roll(each, shift, dims=(-2, -1)) for each in latents], *given, **named)
            back = [torch.roll(each.float() * weight, (-shift[0], -shift[1]), dims=(-2, -1)) for each in out[0]]
            mixed = back if mixed is None else [m + b for m, b in zip(mixed, back)]
        return ([m.to(out[0][0].dtype) for m in mixed],) + tuple(out[1:])


class Maker:
    def __init__(self):
        self.pipe = ZImagePipeline.from_pretrained(MODEL, revision=REVISION, torch_dtype=torch.bfloat16)
        self.pipe.set_progress_bar_config(disable=True)
        self.again = ZImageImg2ImgPipeline(**self.pipe.components)
        self.again.set_progress_bar_config(disable=True)
        self.vae = self.pipe.vae
        self.plain = self.pipe.transformer.forward

    def words(self, prompts):
        """What the model makes of each prompt, worked out once with the text model on the card, which then leaves it to the denoiser."""
        self.pipe.text_encoder.to('cuda')
        out = {}
        with torch.no_grad():
            for name, prompt in prompts.items():
                out[name] = self.pipe.encode_prompt(prompt=prompt, device='cuda', do_classifier_free_guidance=False)[0]
        self.pipe.text_encoder.to('cpu')
        torch.cuda.empty_cache()
        # In bfloat16 the denoiser is 12 GB, which overfills a 16 GB card shared with a desktop: kept in 8 bit and widened a layer at a time.
        if torch.cuda.get_device_properties(0).total_memory < 24 * 2 ** 30:
            self.pipe.transformer.enable_layerwise_casting(storage_dtype=torch.float8_e4m3fn, compute_dtype=torch.bfloat16)
        self.pipe.transformer.to('cuda')
        self.vae.to('cuda')
        return out

    @torch.no_grad()
    def make(self, words, seed, width, height, wrap):
        return self.finished(self.pipe, dict(width=width, height=height), words, seed, wrap)

    @torch.no_grad()
    def over(self, words, seed, source, strength, wrap):
        """The source picture given to the model part noised and finished at its own size: its layout kept, its detail the model's."""
        source = source.convert('RGB')
        return self.finished(self.again, dict(image=source, strength=strength, width=source.width, height=source.height), words, seed, wrap)

    def finished(self, pipe, given, words, seed, wrap):
        wrapping(self.vae, wrap)
        self.pipe.transformer.forward = Wrapped(self.plain, seed) if wrap else self.plain
        latents = pipe(prompt_embeds=words, num_inference_steps=STEPS, guidance_scale=0.0, generator=torch.Generator('cuda').manual_seed(seed),
                       output_type='latent', **given).images
        latents = latents.to(self.vae.dtype) / self.vae.config.scaling_factor + self.vae.config.shift_factor
        picture = self.vae.decode(latents, return_dict=False)[0].float().clamp(-1, 1)
        pixels = ((picture[0].permute(1, 2, 0).cpu().numpy() + 1.0) * 127.5).round().clip(0, 255).astype(numpy.uint8)
        return Image.fromarray(pixels, 'RGB')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', required=True)
    parser.add_argument('--only', nargs='*')
    parser.add_argument('--seeds', nargs='*', type=int, default=[1, 2, 3, 4])
    parser.add_argument('--size', type=int, default=1024)
    # A surface made over a picture of its own (tools/materials/paint.py) rather than from noise: that picture, and how much of it is noised away.
    parser.add_argument('--over')
    parser.add_argument('--strengths', nargs='*', type=float, default=[0.5])
    args = parser.parse_args()
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    prompts = dict(SURFACES, **SPRIGS)
    unknown = [name for name in args.only or [] if name not in prompts]
    if unknown:
        raise SystemExit('no such picture: ' + ' '.join(unknown))
    wanted = {name: prompt for name, prompt in prompts.items() if not args.only or name in args.only}
    maker = Maker()
    words = maker.words(wanted)
    if args.over:
        source = Image.open(args.over)
        for name in wanted:
            for seed in args.seeds:
                for strength in args.strengths:
                    path = out / f'{name}-{seed}-{round(strength * 100)}.png'
                    if not path.exists():
                        maker.over(words[name], seed, source, strength, True).save(path)
                        print('made', path.name, flush=True)
        return
    for name in wanted:
        for seed in args.seeds:
            path = out / f'{name}-{seed}.png'
            if path.exists():
                continue
            maker.make(words[name], seed, args.size, args.size, name in SURFACES).save(path)
            print('made', path.name, flush=True)


if __name__ == '__main__':
    main()
