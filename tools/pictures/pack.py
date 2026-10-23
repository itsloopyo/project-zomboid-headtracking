"""Puts the pictures picked (catalogue.SEEDS) into the mod's jar resources, with the index of what shows each.

    python tools/pictures/pack.py DIR [--partial]

Each picture catalogue.py hangs somewhere is cut to its shape, lettered where it is a poster with a name on it, made no
larger than it is worth at the size it hangs, and saved as src/main/resources/pictures/<name>.jpg. With --partial the
pictures not picked yet are left out, and the sprites that would show them keep the game's own painting.

The index lists each picture with its size, and its subject where catalogue.subject gives one; then what shows each;
then catalogue.POOLS, a line a pool: @subject, a tab, the kind of room, a tab and its pictures with a space between.

What a television shows (catalogue.SHOTS) is cut four wide for three high, and the count of each show's shots in
shows.txt (schedule.py) is set to how many of them, from the first, were made: a show with none shows the bars. The bars a set shows with nothing coming
in, screen-bars and for a set with no colour screen-grey-bars, are drawn here.

The lettering is set in fonts of this machine's Windows (Impact and Georgia). Only the letters' shapes as
drawn go into a picture.
"""
import pathlib
import shutil
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFilter, ImageFont

import catalogue
import signs

OUT = pathlib.Path(__file__).resolve().parents[2] / 'src' / 'main' / 'resources' / 'pictures'
FONTS = pathlib.Path('C:/Windows/Fonts')
QUALITY = 86
# The longest side a picture is kept at, by what it is: a snapshot in a frame a hand across is worth fewer pixels than a hoarding.
LONGEST = {'family': 768, 'painting': 1024, 'board': 1024, 'certificate': 1024, 'music': 768, 'film': 1344, 'sign': signs.LONGEST}
SMALL = 768
# How many times as wide as it is high each shape cut from square art is.
SHAPES = {'tall': 2 / 3, 'wide': 3 / 2, 'broad': 16 / 9}
# A television's picture, in pixels: more than a tube of 1993 could show.
SCREEN = (640, 480)
# A tube's picture is soft and is seen through its lines: it is kept smaller in the jar than a print on a wall.
SCREEN_QUALITY = 78


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size)


def cut(image, shape):
    """The middle of a picture, as wide for its height as the shape says."""
    wide, high = image.size
    if shape > wide / high:
        keep = round(wide / shape)
        top = (high - keep) // 2
        return image.crop((0, top, wide, top + keep))
    keep = round(high * shape)
    left = (wide - keep) // 2
    return image.crop((left, 0, left + keep, high))


def fitted(draw, text, name, widest, largest):
    """The font at the largest size at which the text is no wider than widest."""
    size = largest
    while size > 8 and draw.textlength(text, font=font(name, size)) > widest:
        size -= 2
    return font(name, size)


def shaded(image, top, bottom, most):
    """Darkens a band of the picture, most at one edge of it and not at all at the other, for lettering to be read on."""
    wide, high = image.size
    shade = Image.new('L', (1, high), 0)
    for y in range(high):
        if top <= y < bottom:
            shade.putpixel((0, y), round(most * (y - top) / (bottom - top)))
        elif y >= bottom and bottom > top:
            shade.putpixel((0, y), most)
    if top > bottom:
        for y in range(high):
            shade.putpixel((0, y), round(most * max(0, top - y) / top))
    return Image.composite(Image.new('RGB', image.size, (0, 0, 0)), image, shade.resize(image.size))


def lettered(image, text, name, size, centre, baseline, fill, shadow=True):
    draw = ImageDraw.Draw(image)
    face = font(name, size) if isinstance(size, int) else size
    if shadow:
        soft = Image.new('L', image.size, 0)
        ImageDraw.Draw(soft).text((centre, baseline), text, font=face, anchor='ms', fill=255)
        soft = soft.filter(ImageFilter.GaussianBlur(max(face.size // 12, 2)))
        image.paste((0, 0, 0), mask=soft.point(lambda v: min(255, v * 2)))
        draw = ImageDraw.Draw(image)
    draw.text((centre, baseline), text, font=face, anchor='ms', fill=fill)


def one_sheet(art, title, line):
    """A film's poster as it hangs by a cinema's door: the art, the film's name across its foot and a line at its head."""
    image = cut(art, SHAPES['tall'])
    wide, high = image.size
    image = shaded(image, round(high * 0.55), round(high * 0.86), 215)
    image = shaded(image, round(high * 0.14), 0, 150)
    draw = ImageDraw.Draw(image)
    lettered(image, title, 'impact.ttf', fitted(draw, title, 'impact.ttf', wide * 0.88, high // 6), wide // 2, round(high * 0.875), (244, 232, 204))
    lettered(image, line, 'georgiab.ttf', fitted(draw, line, 'georgiab.ttf', wide * 0.6, high // 28), wide // 2, round(high * 0.07), (236, 226, 204))
    draw = ImageDraw.Draw(image)
    draw.line((wide * 0.2, high * 0.905, wide * 0.8, high * 0.905), fill=(200, 190, 168), width=max(high // 400, 1))
    lettered(image, 'KNOX PICTURES  .  1993', 'georgia.ttf', fitted(draw, 'KNOX PICTURES  .  1993', 'georgia.ttf', wide * 0.5, high // 44),
             wide // 2, round(high * 0.945), (190, 182, 164), shadow=False)
    return image


def hoarding(art, title, line):
    """The same film on a hoarding several squares long: the name large across the lower part, the line over it."""
    image = cut(art, SHAPES['broad'])
    wide, high = image.size
    image = shaded(image, round(high * 0.45), round(high * 0.9), 205)
    draw = ImageDraw.Draw(image)
    lettered(image, title, 'impact.ttf', fitted(draw, title, 'impact.ttf', wide * 0.84, high // 4), wide // 2, round(high * 0.86), (244, 232, 204))
    lettered(image, line, 'georgiab.ttf', fitted(draw, line, 'georgiab.ttf', wide * 0.4, high // 16), wide // 2, round(high * 0.94), (236, 226, 204))
    return image


def diploma(art, title, school, phrase='has conferred upon the bearer the degree of', foot='with all the rights, honours and privileges thereto pertaining'):
    image = art.copy()
    wide, high = image.size
    ink = (38, 30, 24)
    draw = ImageDraw.Draw(image)
    lettered(image, school, 'georgiab.ttf', fitted(draw, school, 'georgiab.ttf', wide * 0.6, high // 9), wide // 2, round(high * 0.3), ink, shadow=False)
    lettered(image, phrase, 'georgiai.ttf', high // 28, wide // 2, round(high * 0.41), ink, shadow=False)
    lettered(image, title, 'georgiab.ttf', fitted(draw, title, 'georgiab.ttf', wide * 0.56, high // 6), wide // 2, round(high * 0.56), ink, shadow=False)
    lettered(image, foot, 'georgiai.ttf', high // 34, wide // 2, round(high * 0.65), ink, shadow=False)
    return image


def gig(shape):
    """A band's or a singer's poster: the whole picture on a black sheet, the name in the black above it and a line below,
    or with no name the picture alone in the middle of the sheet.

    Lettered over the picture, the name lands on a face wherever heads are high in the frame. Cut to the sheet's shape, a
    band loses the players at either end.
    """
    def laid(art, title='', line=''):
        across = max(art.size)
        wide, high = (across, round(across / SHAPES[shape])) if SHAPES[shape] < 1 else (round(across * SHAPES[shape]), across)
        image = Image.new('RGB', (wide, high), (18, 16, 14))
        if not title:
            margin = round(min(wide, high) * 0.06)
            scale = min((wide - 2 * margin) / art.width, (high - 2 * margin) / art.height)
            shown = art.resize((round(art.width * scale), round(art.height * scale)), Image.LANCZOS)
            image.paste(shown, ((wide - shown.width) // 2, (high - shown.height) // 2))
            return image
        head, foot = round(high * 0.2), round(high * 0.08)
        scale = min(wide / art.width, (high - head - foot) / art.height)
        shown = art.resize((round(art.width * scale), round(art.height * scale)), Image.LANCZOS)
        image.paste(shown, ((wide - shown.width) // 2, head + (high - head - foot - shown.height) // 2))
        draw = ImageDraw.Draw(image)
        lettered(image, title, 'impact.ttf', fitted(draw, title, 'impact.ttf', wide * 0.88, round(head * 0.7)), wide // 2, round(head * 0.8), (250, 236, 190), shadow=False)
        lettered(image, line, 'georgiab.ttf', fitted(draw, line, 'georgiab.ttf', wide * 0.62, round(foot * 0.5)), wide // 2, high - round(foot * 0.3), (240, 232, 214), shadow=False)
        return image
    return laid


LAYOUTS = {'tall': one_sheet, 'broad': hoarding, 'diploma': diploma, 'gig-tall': gig('tall'), 'gig-wide': gig('wide')}


def bars(colours, steps):
    """The bars a set shows with nothing coming in: upright bars of each colour, and under them steps from black to white."""
    wide, high = SCREEN
    image = Image.new('RGB', SCREEN)
    draw = ImageDraw.Draw(image)
    for i, colour in enumerate(colours):
        draw.rectangle((round(wide * i / len(colours)), 0, round(wide * (i + 1) / len(colours)), high), fill=colour)
    for i in range(steps):
        tone = round(255 * i / (steps - 1))
        draw.rectangle((round(wide * i / steps), round(high * 0.75), round(wide * (i + 1) / steps), high), fill=(tone, tone, tone))
    # A tube is not sharp from one bar to the next.
    return image.filter(ImageFilter.GaussianBlur(1.2))


def screens(folder):
    """Every picture a television shows by its name, the ones whose art is not made yet left out."""
    full, most = 255, 191
    shown = {'screen-bars': bars([(most, most, most), (most, most, 0), (0, most, most), (0, most, 0), (most, 0, most), (most, 0, 0), (0, 0, most)], 4)}
    shown['screen-grey-bars'] = bars([(tone, tone, tone) for tone in (full, most, 128, 64, 0)], 4)
    for name, source in catalogue.SHOTS.items():
        art = made(folder, source)
        if art is not None:
            shown[name] = cut(art, SCREEN[0] / SCREEN[1]).resize(SCREEN, Image.LANCZOS)
    return shown


def made(folder, name):
    """The art picked for that name, or None where none is picked yet."""
    if name not in catalogue.SEEDS:
        return None
    source, picked = catalogue.SEEDS[name]
    return Image.open(folder / source / name / f'{picked}.png').convert('RGB')


def picture(folder, name):
    """The finished picture of that name, or None where its art is not made yet."""
    if name in signs.PICTURES:
        return signs.picture(signs.PICTURES[name], lambda art: made(folder, art))
    if name in catalogue.LETTERED:
        base, layout, *words = catalogue.LETTERED[name]
        art = made(folder, base)
        return None if art is None else LAYOUTS[layout](art, *words)
    if name in catalogue.ART:
        art = made(folder, name)
        return art if art is None or name not in catalogue.SHAPED else cut(art, catalogue.SHAPED[name])
    base, shape = name.rsplit('-', 1)
    art = made(folder, base)
    return None if art is None else cut(art, SHAPES[shape])


def counted(sizes):
    """Sets the count of each show's shots in shows.txt to how many of screen-<show>-0 and on were made."""
    table = OUT / 'shows.txt'
    lines = table.read_text(encoding='utf-8').splitlines()
    for at, line in enumerate(lines):
        kind, show, _ = line.split('\t')
        if kind == 'show':
            made_of = 0
            while f'screen-{show}-{made_of}' in sizes:
                made_of += 1
            lines[at] = f'show\t{show}\t{made_of}'
    table.write_text(''.join(line + '\n' for line in lines), encoding='utf-8', newline='\n')


def main():
    arguments = [a for a in sys.argv[1:] if a != '--partial']
    partial = len(arguments) != len(sys.argv) - 1
    folder = pathlib.Path(arguments[0])
    # Made in a folder of its own and moved in only once nothing is missing, so a run
    # that stops leaves them as they were.
    made_into = pathlib.Path(tempfile.mkdtemp(prefix='pictures-'))
    sizes = {}
    missing = []
    total = 0
    pooled = [name for pool in catalogue.POOLS.values() for name in pool]
    for name in dict.fromkeys([shown for _, shown in catalogue.HUNG] + pooled):
        image = picture(folder, name)
        if image is None:
            missing.append(name)
            continue
        longest = LONGEST.get(name.split('-')[0], SMALL)
        if max(image.size) > longest:
            scale = longest / max(image.size)
            image = image.resize((round(image.width * scale), round(image.height * scale)), Image.LANCZOS)
        image.save(made_into / f'{name}.jpg', quality=QUALITY)
        total += (made_into / f'{name}.jpg').stat().st_size
        sizes[name] = image.size
    shown = screens(folder)
    missing += [name for name in catalogue.SHOTS if name not in shown]
    for name, image in shown.items():
        image.save(made_into / f'{name}.jpg', quality=SCREEN_QUALITY)
        total += (made_into / f'{name}.jpg').stat().st_size
        sizes[name] = image.size
    if missing and not partial:
        shutil.rmtree(made_into)
        raise SystemExit('not made yet: ' + ' '.join(missing))
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob('*.jpg'):
        old.unlink()
    for new in made_into.glob('*.jpg'):
        shutil.move(new, OUT / new.name)
    made_into.rmdir()
    with open(OUT / 'index.txt', 'w', encoding='utf-8', newline='\n') as index:
        for name, (wide, high) in sizes.items():
            subject = catalogue.subject(name)
            index.write(f'{name}\t{wide}\t{high}' + (f'\t{subject}\n' if subject else '\n'))
        for key, name in catalogue.HUNG:
            if name in sizes:
                index.write(f"{'+' if key in catalogue.LONG else '='}{key}\t{name}\n")
        for (subject, kind), pool in catalogue.POOLS.items():
            made_of = [name for name in pool if name in sizes]
            if made_of:
                index.write(f"@{subject}\t{kind}\t{' '.join(made_of)}\n")
    counted(sizes)
    print(f'{len(sizes)} pictures, {total / 1e6:.1f} MB, {len(missing)} not made yet')


if __name__ == '__main__':
    main()
