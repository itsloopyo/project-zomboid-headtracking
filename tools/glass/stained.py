"""The stained glass windows of the first person view: the game's own sprites of them, given again to Z-Image Turbo
(Tongyi-MAI, Apache 2.0) with a text prompt, which keeps each window's layout and gives its glass the colour, leading
and streaks of real glass. The dove window (location_community_church_small_01_114 and 115) has a priest painted into
its foot first, in the chunky pixels of the game's own art, where its sprite has flowers.

    python tools/glass/stained.py inputs DIR              the sprites, read out of the installed game, enlarged and readied
    python tools/glass/stained.py make DIR [--only N ...] [--strengths ...] [--seeds ...] [--variants ...]
                                                           DIR/<sprite>[-<variant>]-s<strength>-<seed>.png (the imagegen venv)
    python tools/glass/stained.py pack DIR PICKS           the picks into src/textures/materials/stained.png and
                                                           src/main/resources/materials/stained.txt

PICKS is a text file of lines "<sprite> <candidate file name>". The atlas keeps each sprite's own alpha outline, and
says what is glass and what is frame by the cover the picture reads it by (PipelineShaders, stained): the frame, read
off the game's sprite, is solid and drawn as the model drew it where that is near the sprite's colour, everything else inside the window is
glass at half cover, and its leading is the dark the model drew. The game's sprites and packs are read from the
install and nothing of them is kept in the repository but what the atlas is made of.
"""
import argparse
import io
import pathlib
import struct
import sys
from collections import deque

import numpy
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
PACK = pathlib.Path(r'C:\Program Files (x86)\Steam\steamapps\common\ProjectZomboid\media\texturepacks\Tiles2x.pack')
CHURCH, WINDOWS = 'location_community_church_small_01_', 'fixtures_windows_church_'
# StainedGlass.of, which a test holds this to.
SPRITES = [f'{CHURCH}{i}' for i in list(range(114, 120)) + list(range(122, 128))] + \
          [f'{WINDOWS}{i}' for i in list(range(0, 8)) + list(range(14, 20)) + [30, 31]]
MODEL = 'Tongyi-MAI/Z-Image-Turbo'
REVISION = 'f332072aa78be7aecdf3ee76d5c247082da564a6'
STEPS = 9
PROMPT = ('A gothic church stained glass window seen from inside, backlit by bright daylight, glowing rich saturated jewel colours, '
          'deep sapphire blue, ruby red, emerald green and amber gold, thick dark grey lead came lines between the pieces of glass, '
          'hand blown antique glass with streaks, seeds and bubbles, painted grisaille detail, sharp focus, highly detailed.')
PRIEST = (' Under the white dove, at the foot of the window, a priest in a white alb and a gold chasuble with a red stole, '
          'his face and hands painted on the glass, made of leaded pieces of glass like the rest of the window.')
# How many times larger a sprite is given to the model than the game paints it, at least, and the least its shorter side may be.
ENLARGE, LEAST = 7, 512
# How many times larger the atlas holds a sprite than the game paints it, and the clear texels kept round each.
ATLAS, GAP = 4, 16
GLASS, FRAME = 128, 240

# The priest, painted as the game paints its people: a texel of the sprite to a dot. Each variant is drawn on the wall's
# plane, a dot along the wall and a dot down, and laid on the sprite along its slope (sheared, a dot up for two along).
# Places are of the left light's foot in 114: x along the wall from its left edge, y down from 40 dots over its foot.
COLOURS = {'H': (245, 200, 70), 'h': (90, 55, 30), 's': (235, 190, 150), 'W': (245, 245, 235), 'G': (220, 160, 40),
           'R': (200, 30, 40), 'B': (130, 50, 30), 'p': (250, 245, 225), 'K': (45, 45, 50), 'g': (50, 140, 60),
           'b': (40, 90, 200), 'C': (250, 230, 120), 'Y': (255, 210, 60), 'w': (150, 100, 50)}
ORANS = [
    '.....HHHH.......',
    '....HhhhhH......',
    '.s..HhsshH..s...',
    '.s..HssssH..s...',
    '.WW.HssssH.WW...',
    '.WW..ssss..WW...',
    '.GWWGGRRGGWWG...',
    '..GGGGRRGGGG....',
    '..GGGGRRGGGG....',
    '...GGGRRGGG.....',
    '...GGGRRGGG.....',
    '...GGGRRGGG.....',
    '...GGGRRGGG.....',
    '...GGGRRGGG.....',
    '...WGGRRGGW.....',
    '...WWGRRGWW.....',
    '..WWWWRRWWWW....',
    '..WWWWRRWWWW....',
    '..WWWWWWWWWW....',
    '..WWWWWWWWWW....',
    '..WWWWWWWWWW....',
    '..WWWWWWWWWW....',
    '..WWWWWWWWWW....',
    '.WWWWWWWWWWWW...',
    '.WWWWWWWWWWWW...',
    '..KK......KK....',
    'gggggggggggggggg',
    'gggggggggggggggg',
]
READING = [
    '.....HHHH.......',
    '....HhhhhH......',
    '....HhsshH......',
    '....HssssH......',
    '....HssssH......',
    '.....ssss.......',
    '...WGGRRGGW.....',
    '..WGGGRRGGGW....',
    '..WGGGRRGGGW....',
    '..WGsBBBBsGW....',
    '..WGBppppBGW....',
    '..WGBppppBGW....',
    '...GGBBBBGG.....',
    '...GGGRRGGG.....',
    '...GGGRRGGG.....',
    '...WGGRRGGW.....',
    '..WWWWRRWWWW....',
    '..WWWWRRWWWW....',
    '..WWWWWWWWWW....',
    '..WWWWWWWWWW....',
    '..WWWWWWWWWW....',
    '..WWWWWWWWWW....',
    '..WWWWWWWWWW....',
    '.WWWWWWWWWWWW...',
    '.WWWWWWWWWWWW...',
    '..KK......KK....',
    'gggggggggggggggg',
    'gggggggggggggggg',
]
LECTERN = [
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
    '..C........C....',
    '..Y........Y....',
    '..w........w....',
    '..w.pppppp.w....',
    '..w.ppKpppp.....',
    '..wBBBBBBBBw....',
    '..w..wwww..w....',
    '..w..wwww..w....',
    '.....wwww.......',
    '.....wwww.......',
    '.....wwww.......',
    '.....wwww.......',
    '.....wwww.......',
    '.....wwww.......',
    '.....wwww.......',
    '.....wwww.......',
    '....wwwwww......',
    '...wwwwwwww.....',
    '................',
    'gggggggggggggggg',
    'gggggggggggggggg',
]
CHALICE = [
    '................',
    '................',
    '................',
    '.......YY.......',
    '......YYYY......',
    '.......YY.......',
    '.......YY.......',
    '....YYYYYYYY....',
    '.....YYYYYY.....',
    '......YYYY......',
    '.......YY.......',
    '.......YY.......',
    '......YYYY......',
    '.....YYYYYY.....',
    '..RRRRRRRRRRRR..',
    '..RWWWWWWWWWWR..',
    '..RWWWWWWWWWWR..',
    '..RWWWWWWWWWWR..',
    '..RWWWWWWWWWWR..',
    '..RWWWWWWWWWWR..',
    '..RWWWWWWWWWWR..',
    '..RWWWWWWWWWWR..',
    '..RWWWWWWWWWWR..',
    '..RWWWWWWWWWWR..',
    '..RWWWWWWWWWWR..',
    '................',
    'gggggggggggggggg',
    'gggggggggggggggg',
]
# Each variant: what stands in the left light and in the right.
VARIANTS = {'orans': (ORANS, LECTERN), 'reading': (READING, CHALICE), 'altar': (CHALICE, [r[::-1] for r in ORANS])}
# The foot of each light of 114 on its left edge (x, y of the dot under the bottom row), and its breadth along the wall.
LIGHTS_114 = [(11, 176, 18), (32, 165, 20)]
# How many dots high a figure stands, its art stretched to it.
TALL = 44


def sprites():
    """Each sprite of SPRITES out of the game's pack, trimmed as the pack holds it, RGBA."""
    data = PACK.read_bytes()
    if data[:4] != b'PZPK':
        raise SystemExit(f'{PACK} is not a pack this reads')
    at = 8
    pages = struct.unpack_from('<i', data, at)[0]
    at += 4
    found = {}
    for _ in range(pages):
        n = struct.unpack_from('<i', data, at)[0]
        at += 4 + n
        entries, _mask = struct.unpack_from('<ii', data, at)
        at += 8
        subs = []
        for _ in range(entries):
            n = struct.unpack_from('<i', data, at)[0]
            name = data[at + 4:at + 4 + n].decode('latin1')
            at += 4 + n
            subs.append((name, struct.unpack_from('<8i', data, at)))
            at += 32
        size = struct.unpack_from('<i', data, at)[0]
        at += 4
        wanted = [(name, rect) for name, rect in subs if name in SPRITES]
        if wanted:
            page = Image.open(io.BytesIO(data[at:at + size])).convert('RGBA')
            for name, (x, y, w, h, *_rest) in wanted:
                found[name] = page.crop((x, y, x + w, y + h))
        at += size
    missing = [name for name in SPRITES if name not in found]
    if missing:
        raise SystemExit('the pack has no ' + ', '.join(missing))
    return found


def frame_of(texels, church):
    """Which dots of a sprite are its frame: pale grey or stone dots joined to its outline, not through glass or lead."""
    rgb = texels[..., :3].astype(numpy.float64) / 255
    cover = texels[..., 3] / 255
    light = rgb @ [0.299, 0.587, 0.114]
    grey = rgb.max(-1) - rgb.min(-1) < 0.22
    # The church sheet paints white flowers and doves as solid as its grey frame, and whiter than it.
    framed = (cover > 0.85) & grey & ((light > 0.45) & (light < 0.85) if church else light > 0.3)
    inside = cover > 0.1
    high, wide = cover.shape
    seen = numpy.zeros_like(framed)
    queue = deque()
    for y in range(high):
        for x in range(wide):
            if framed[y, x] and any(not (0 <= y + dy < high and 0 <= x + dx < wide) or not inside[y + dy, x + dx]
                                    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                seen[y, x] = True
                queue.append((y, x))
    while queue:
        y, x = queue.popleft()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            yy, xx = y + dy, x + dx
            if 0 <= yy < high and 0 <= xx < wide and framed[yy, xx] and not seen[yy, xx]:
                seen[yy, xx] = True
                queue.append((yy, xx))
    return seen


def scale_of(sprite):
    return max(ENLARGE, -(-LEAST // min(sprite.size)))


def paint(texels, frame, art, left, foot, wide, flip):
    """Lays a figure on the sprite's dots, standing on the light's foot and TALL dots high, sheared along the wall's slope,
    with blue glass round it in place of whatever the light held there. The frame is left as it is."""
    for v in range(TALL):
        row = art[v * len(art) // TALL]
        row = row[::-1] if flip else row
        for u, key in enumerate(row[:wide]):
            x = left + u
            y = foot - TALL + v - (u // 2 if not flip else (wide - 1 - u) // 2)
            if 0 <= y < texels.shape[0] and 0 <= x < texels.shape[1] and texels[y, x, 3] > 25 and not frame[y, x]:
                texels[y, x, :3] = COLOURS['b' if key == '.' else key]


def with_priest(name, texels, variant):
    """The dove window with the variant's figures in the feet of its two lights in place of its flowers."""
    out = texels.copy()
    frame = frame_of(texels, True)
    left_art, right_art = VARIANTS[variant]
    if name == f'{CHURCH}114':
        for (left, foot, wide), art in zip(LIGHTS_114, (left_art, right_art)):
            paint(out, frame, art, left, foot, wide, False)
    else:
        # 115 is 114 seen on the other wall: the same window, mirrored.
        width = out.shape[1]
        for (left, foot, wide), art in zip(LIGHTS_114, (right_art, left_art)):
            paint(out, frame, art, width - left - wide, foot, wide, True)
    return out


def readied(texels, scale):
    """The sprite as the model is given it: its glass pushed to jewel colours, enlarged, on a dark ground cut to the model's grid."""
    rgba = texels.astype(numpy.float32) / 255
    rgb, cover = rgba[..., :3], rgba[..., 3]
    glass = (cover < 0.75) & (cover > 0.1) & (rgb.mean(-1) > 0.2)
    grey = rgb.mean(-1, keepdims=True)
    deep = numpy.clip(grey + (rgb - grey) * 3.0, 0, 1) ** 1.6
    rgb = numpy.where(glass[..., None], deep, rgb)
    rgb = numpy.where(cover[..., None] < 0.1, 0.05, rgb)
    high, wide = cover.shape
    big = (wide * scale, high * scale)
    ground = Image.new('RGB', ((big[0] + 15) // 16 * 16, (big[1] + 15) // 16 * 16), (12, 12, 12))
    ground.paste(Image.fromarray((rgb * 255).astype(numpy.uint8)).resize(big, Image.LANCZOS), (0, 0))
    return ground


def short(name):
    return name.replace(CHURCH, 'c').replace(WINDOWS, 'w')


def inputs(out):
    out.mkdir(parents=True, exist_ok=True)
    for name, sprite in sprites().items():
        texels = numpy.array(sprite)
        sprite.save(out / f'{short(name)}-sprite.png')
        if name in (f'{CHURCH}114', f'{CHURCH}115'):
            for variant in VARIANTS:
                readied(with_priest(name, texels, variant), scale_of(sprite)).save(out / f'{short(name)}-{variant}-in.png')
        else:
            readied(texels, scale_of(sprite)).save(out / f'{short(name)}-in.png')


def make(out, only, strengths, seeds, variants):
    import torch
    from diffusers import ZImageImg2ImgPipeline
    pipe = ZImageImg2ImgPipeline.from_pretrained(MODEL, revision=REVISION, torch_dtype=torch.bfloat16)
    pipe.set_progress_bar_config(disable=True)
    given = sorted(path for path in out.glob('*-in.png') if not only or any(path.name.startswith(n + '-') for n in only))
    if variants:
        given = [path for path in given if path.stem.count('-') == 1 or path.stem.split('-')[1] in variants]
    prompts = {'plain': PROMPT, 'priest': PROMPT + PRIEST}
    pipe.text_encoder.to('cuda')
    with torch.no_grad():
        words = {k: pipe.encode_prompt(prompt=p, device='cuda', do_classifier_free_guidance=False)[0] for k, p in prompts.items()}
    pipe.text_encoder.to('cpu')
    torch.cuda.empty_cache()
    # In bfloat16 the denoiser is 12 GB, which overfills a 16 GB card shared with a desktop: kept in 8 bit and widened a layer at a time.
    if torch.cuda.get_device_properties(0).total_memory < 24 * 2 ** 30:
        pipe.transformer.enable_layerwise_casting(storage_dtype=torch.float8_e4m3fn, compute_dtype=torch.bfloat16)
    pipe.transformer.to('cuda')
    pipe.vae.to('cuda')
    for path in given:
        source = Image.open(path).convert('RGB')
        stem = path.stem[:-3]
        for strength in strengths:
            for seed in seeds:
                target = out / f'{stem}-s{round(strength * 100)}-{seed}.png'
                if target.exists():
                    continue
                with torch.no_grad():
                    picture = pipe(prompt_embeds=words['priest' if stem.count('-') else 'plain'], image=source, strength=strength,
                                   width=source.width, height=source.height, num_inference_steps=STEPS, guidance_scale=0.0,
                                   generator=torch.Generator('cuda').manual_seed(seed)).images[0]
                picture.save(target)
                print('made', target.name, flush=True)


def atlas_piece(name, sprite, candidate):
    """A sprite as the atlas holds it: the candidate's glass, the game's frame, the game's outline."""
    texels = numpy.array(sprite)
    high, wide = texels.shape[:2]
    size = (wide * ATLAS, high * ATLAS)
    scale = scale_of(sprite)
    made = Image.open(candidate).convert('RGB').crop((0, 0, wide * scale, high * scale)).resize(size, Image.LANCZOS)
    frame = Image.fromarray(frame_of(texels, name.startswith(CHURCH)).astype(numpy.uint8) * 255).resize(size, Image.BILINEAR)
    frame = numpy.asarray(frame) > 127
    outline = numpy.asarray(Image.fromarray(texels[..., 3]).resize(size, Image.BILINEAR)) > 25
    game = numpy.asarray(sprite.convert('RGB').resize(size, Image.LANCZOS))
    out = numpy.zeros((size[1], size[0], 4), numpy.uint8)
    made = numpy.asarray(made)
    # The model draws the frame sharper than the sprite enlarged, and sometimes a bar a little off where the sprite has
    # it: the frame is the model's where it is near the sprite's colour, and the sprite's where the model put glass.
    near = numpy.abs(made.astype(numpy.int16) - game.astype(numpy.int16)).mean(-1) < 40
    out[..., :3] = numpy.where((frame & ~near)[..., None], game, made)
    out[..., 3] = numpy.where(outline, numpy.where(frame, FRAME, GLASS), 0)
    # What is cut away takes the colour of the frame, so the smaller copies do not draw a dark rim round the window.
    out[~outline, :3] = game[~outline]
    return out


def pack(folder, picks):
    chosen = {}
    for line in picks.read_text().splitlines():
        if line.strip():
            name, file = line.split()
            chosen[name] = folder / file
    missing = [name for name in SPRITES if name not in chosen]
    if missing:
        raise SystemExit('no pick for ' + ', '.join(missing))
    pieces = {name: atlas_piece(name, sprite, chosen[name]) for name, sprite in sprites().items()}
    # Shelves, tallest first, in an atlas 4096 wide.
    side = 4096
    places, x, y, shelf = {}, GAP, GAP, 0
    for name in sorted(pieces, key=lambda n: -pieces[n].shape[0]):
        high, wide = pieces[name].shape[:2]
        if x + wide + GAP > side:
            x, y, shelf = GAP, y + shelf + GAP, 0
        places[name] = (x, y, wide, high)
        x += wide + GAP
        shelf = max(shelf, high)
    tall = 1
    while tall < y + shelf + GAP:
        tall *= 2
    atlas = numpy.zeros((tall, side, 4), numpy.uint8)
    for name, (px, py, wide, high) in places.items():
        atlas[py:py + high, px:px + wide] = pieces[name]
    Image.fromarray(atlas, 'RGBA').save(ROOT / 'src' / 'textures' / 'materials' / 'stained.png')
    lines = [f'{side} {tall}'] + [f'{name} {px} {py} {wide} {high}' for name, (px, py, wide, high) in sorted(places.items())]
    (ROOT / 'src' / 'main' / 'resources' / 'materials' / 'stained.txt').write_text('\n'.join(lines) + '\n', newline='\n')
    print(f'atlas {side} x {tall}, {len(places)} sprites')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('what', choices=['inputs', 'make', 'pack'])
    parser.add_argument('folder')
    parser.add_argument('picks', nargs='?')
    parser.add_argument('--only', nargs='*')
    parser.add_argument('--strengths', nargs='*', type=float, default=[0.45, 0.6])
    parser.add_argument('--seeds', nargs='*', type=int, default=[1, 2])
    parser.add_argument('--variants', nargs='*')
    args = parser.parse_args()
    folder = pathlib.Path(args.folder)
    if args.what == 'inputs':
        inputs(folder)
    elif args.what == 'make':
        make(folder, [short(n) if n.startswith((CHURCH, WINDOWS)) else n for n in args.only or []], args.strengths, args.seeds, args.variants)
    else:
        if not args.picks:
            sys.exit('pack needs the file of picks')
        pack(folder, pathlib.Path(args.picks))


if __name__ == '__main__':
    main()
