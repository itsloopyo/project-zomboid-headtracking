"""Notices: the sheaves of paper the game pins to a wall as one sprite some forty dots across, made again sheet by sheet.

Each sheet is set as type in fonts of this machine's Windows over paper drawn here from noise, so every word on it reads
as written. A photograph on a sheet is one of the portraits shot in the game for the family pictures (catalogue.SEEDS),
copied grey as a notice on a wall is. Nothing is generated.

COLLAGES is key -> (metres across, metres high, sheets). The size is that of the place WorldScene.addHung finds the
key's sprite paints, measured on the sprite facing east. A sheet is (name, box, maker): the box is where it lies on that
place as shares across from the viewer's left and down from its top, and the sheets are listed from the one against the
wall out, as the game's sprite lays them over each other. maker(wide, high, made) draws it at that many pixels, where
made(name) is the art picked for that name, or None where it is not made yet: then the sheet is None.
"""
import calendar
import random

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont, ImageOps

FONT_FOLDER = 'C:/Windows/Fonts/'
PER_METRE = 1800
LONGEST = 768
INK, BLUE_INK, RED = (34, 32, 30), (36, 52, 120), (176, 36, 30)


def font(name, size):
    return ImageFont.truetype(FONT_FOLDER + name, max(size, 6))


def fitted(draw, text, name, wide, high):
    """The font at the largest size at which the text fits the box."""
    size = high
    while size > 6:
        face = font(name, size)
        left, top, right, bottom = draw.textbbox((0, 0), text, font=face)
        if right - left <= wide and bottom - top <= high:
            return face
        size -= 1
    return font(name, 6)


def centred(draw, text, name, fill, box):
    left, top, right, bottom = box
    face = fitted(draw, text, name, right - left, bottom - top)
    ink = draw.textbbox((0, 0), text, font=face)
    draw.text(((left + right - ink[0] - ink[2]) / 2, (top + bottom - ink[1] - ink[3]) / 2), text, font=face, fill=fill)


def wrapped(draw, text, face, wide):
    lines = []
    for paragraph in text.split('\n'):
        line = ''
        for word in paragraph.split(' '):
            tried = (line + ' ' + word).strip()
            if line and draw.textlength(tried, font=face) > wide:
                lines.append(line)
                line = word
            else:
                line = tried
        lines.append(line)
    return lines


def written(draw, text, name, fill, box, largest, spacing=1.25, centre=False):
    """Text wrapped to the box at the largest size up to largest at which all of it fits, from its top."""
    left, top, right, bottom = box
    size = largest
    while True:
        face = font(name, size)
        lines = wrapped(draw, text, face, right - left)
        if len(lines) * size * spacing <= bottom - top or size <= 6:
            break
        size -= 1
    y = top
    for line in lines:
        x = left if not centre else (left + right - draw.textlength(line, font=face)) / 2
        draw.text((x, y), line, font=face, fill=fill)
        y += size * spacing


def paper(wide, high, tone, seed, aged=0.0):
    """A sheet of paper of that colour: its fibres, a little unevenness, and with age, stains and darker edges."""
    rng = random.Random(seed)
    fine = Image.effect_noise((wide, high), 24).filter(ImageFilter.GaussianBlur(0.6))
    broad = Image.effect_noise((max(wide // 16, 2), max(high // 16, 2)), 40).resize((wide, high), Image.BICUBIC).filter(ImageFilter.GaussianBlur(4))
    shade = ImageChops.add(ImageChops.multiply(fine, Image.new('L', (wide, high), 90)), ImageChops.multiply(broad, Image.new('L', (wide, high), 70)))
    image = Image.new('RGB', (wide, high), tone)
    image = Image.composite(image, Image.new('RGB', (wide, high), tuple(round(c * 0.9) for c in tone)), shade.point(lambda v: 255 - v))
    if aged:
        stains = Image.new('L', (wide, high), 0)
        draw = ImageDraw.Draw(stains)
        for _ in range(rng.randint(3, 6)):
            x, y, r = rng.uniform(0, wide), rng.uniform(0, high), rng.uniform(0.08, 0.3) * min(wide, high)
            draw.ellipse((x - r, y - r, x + r, y + r), fill=round(rng.uniform(60, 140) * aged))
        stains = stains.filter(ImageFilter.GaussianBlur(min(wide, high) * 0.05))
        image = Image.composite(Image.new('RGB', (wide, high), (150, 124, 72)), image, stains.point(lambda v: v // 2))
        edge = Image.new('L', (wide, high), 0)
        border = max(round(min(wide, high) * 0.06), 2)
        ImageDraw.Draw(edge).rectangle((border, border, wide - border, high - border), fill=255)
        edge = ImageOps.invert(edge.filter(ImageFilter.GaussianBlur(border)))
        image = Image.composite(Image.new('RGB', (wide, high), (128, 104, 66)), image, edge.point(lambda v: round(v * 0.55 * aged)))
    return image


def photocopied(art, wide, high):
    """A photograph as a notice carries it: grey, a little hard, cut to the box, with the copier's speckle over it."""
    grey = ImageOps.autocontrast(art.convert('L'), cutoff=2)
    scale = max(wide / grey.width, high / grey.height)
    grey = grey.resize((max(round(grey.width * scale), wide), max(round(grey.height * scale), high)), Image.LANCZOS)
    left, top = (grey.width - wide) // 2, round((grey.height - high) * 0.3)
    grey = grey.crop((left, top, left + wide, top + high))
    grey = ImageOps.autocontrast(grey, cutoff=1).point(lambda v: round(30 + 205 * (v / 255) ** 1.15))
    speckle = Image.effect_noise((wide, high), 30).point(lambda v: 255 if v > 200 else 0)
    grey = Image.composite(Image.new('L', (wide, high), 70), grey, speckle.point(lambda v: v // 6)).filter(ImageFilter.GaussianBlur(0.6))
    return grey.convert('RGB')


def pinned(image, where, colour):
    """A drawing pin through the sheet at that share of it."""
    draw = ImageDraw.Draw(image)
    r = max(round(min(image.size) * 0.035), 3)
    x, y = round(where[0] * image.width), round(where[1] * image.height)
    draw.ellipse((x - r + 2, y - r + 3, x + r + 2, y + r + 3), fill=(60, 56, 50))
    draw.ellipse((x - r, y - r, x + r, y + r), fill=colour)
    draw.ellipse((x - r * 0.45, y - r * 0.55, x - r * 0.05, y - r * 0.15), fill=tuple(min(255, c + 90) for c in colour))
    return image


def taped(image, corners=(0, 1)):
    """Strips of clear tape across the top corners: a little paler and yellower where they lie."""
    w, h = image.size
    tape = Image.new('L', (w, h), 0)
    draw = ImageDraw.Draw(tape)
    long, wide = round(min(w, h) * 0.3), round(min(w, h) * 0.08)
    for corner in corners:
        x = 0 if corner == 0 else w
        sign = 1 if corner == 0 else -1
        draw.polygon([(x - sign * wide, wide), (x + sign * long * 0.7, -long * 0.3), (x + sign * long * 0.7 + sign * wide, wide * 0.4),
                      (x, long * 0.7)], fill=70)
    return Image.composite(Image.new('RGB', (w, h), (236, 228, 196)), image, tape)


def wanted(face, name, said, seed):
    def make(wide, high, made):
        art = made(face)
        if art is None:
            return None
        image = paper(wide, high, (232, 228, 214), seed)
        draw = ImageDraw.Draw(image)
        m = round(wide * 0.08)
        centred(draw, 'WANTED', 'impact.ttf', INK, (m, round(high * 0.03), wide - m, round(high * 0.15)))
        centred(draw, 'FOR QUESTIONING', 'arialbd.ttf', INK, (m, round(high * 0.155), wide - m, round(high * 0.2)))
        top, bottom = round(high * 0.22), round(high * 0.62)
        image.paste(photocopied(art, wide - 2 * m, bottom - top), (m, top))
        draw.rectangle((m, top, wide - m, bottom), outline=INK, width=max(wide // 160, 1))
        centred(draw, name, 'arialbd.ttf', INK, (m, round(high * 0.65), wide - m, round(high * 0.71)))
        written(draw, said, 'arial.ttf', INK, (m, round(high * 0.74), wide - m, round(high * 0.97)), round(high * 0.045), centre=True)
        return pinned(pinned(image, (0.06, 0.03), (60, 60, 64)), (0.94, 0.03), (60, 60, 64))
    return make


def typed(title, body, tone=(236, 234, 226), seed=0, aged=0.0, pins=((0.5, 0.03),), pin=(190, 40, 34), face='cour.ttf', head='courbd.ttf'):
    def make(wide, high, made):
        image = paper(wide, high, tone, seed, aged)
        draw = ImageDraw.Draw(image)
        m = round(wide * 0.09)
        y = round(high * 0.07)
        if title:
            centred(draw, title, head, INK, (m, y, wide - m, y + round(high * 0.09)))
            y += round(high * 0.13)
        written(draw, body, face, INK if not aged else (58, 52, 44), (m, y, wide - m, high - m), round(min(high * 0.09, wide * 0.08)))
        for at in pins:
            pinned(image, at, pin)
        return image
    return make


def badge(draw, cx, cy, r, fill, rim):
    """A sheriff's star in a ring."""
    import math
    points = []
    for k in range(14):
        a = math.pi / 2 + k * math.pi / 7
        d = r if k % 2 == 0 else r * 0.62
        points.append((cx + d * math.cos(a), cy - d * math.sin(a)))
    draw.polygon(points, fill=fill, outline=rim)
    draw.ellipse((cx - r * 0.42, cy - r * 0.42, cx + r * 0.42, cy + r * 0.42), outline=rim, width=max(round(r * 0.05), 1))


def sheriff(wide, high, made):
    image = paper(wide, high, (210, 82, 46), 11)
    draw = ImageDraw.Draw(image)
    m = round(wide * 0.07)
    centred(draw, "KNOX COUNTY SHERIFF'S DEPARTMENT", 'arialbd.ttf', (250, 240, 226), (m, round(high * 0.04), wide - m, round(high * 0.1)))
    badge(draw, wide / 2, high * 0.38, min(wide, high) * 0.26, (226, 176, 70), (120, 70, 20))
    centred(draw, 'K C S D', 'arialbd.ttf', (120, 70, 20), (wide * 0.4, high * 0.35, wide * 0.6, high * 0.41))
    centred(draw, 'NEIGHBORHOOD WATCH', 'impact.ttf', (250, 240, 226), (m, round(high * 0.62), wide - m, round(high * 0.69)))
    centred(draw, 'Report anything suspicious. Call 555-0142', 'arial.ttf', (250, 240, 226), (m, round(high * 0.7), wide - m, round(high * 0.74)))
    draw.rectangle((0, round(high * 0.78), wide, high), fill=(44, 44, 46))
    centred(draw, 'POLICE', 'impact.ttf', (232, 232, 228), (m, round(high * 0.8), wide - m, round(high * 0.97)))
    return image


def coloured(title, lines, tone, ink, seed, title_font='impact.ttf', body_font='arialbd.ttf', pin=None, star=False):
    """A bright flyer: a heading over a few short lines, and a starburst behind a dancer where star is set."""
    def make(wide, high, made):
        image = paper(wide, high, tone, seed)
        draw = ImageDraw.Draw(image)
        m = round(wide * 0.08)
        if star:
            import math
            cx, cy, r = wide * 0.5, high * 0.55, min(wide, high) * 0.42
            points = [(cx + (r if k % 2 == 0 else r * 0.55) * math.cos(k * math.pi / 8), cy + (r if k % 2 == 0 else r * 0.55) * math.sin(k * math.pi / 8))
                      for k in range(16)]
            draw.polygon(points, fill=(232, 176, 30))
            # A dancer, arms up and one leg out, in the middle of the star.
            s = r * 0.5
            w = max(round(s * 0.18), 2)
            draw.ellipse((cx - s * 0.16, cy - s * 0.95, cx + s * 0.16, cy - s * 0.63), fill=(52, 38, 20))
            for a, b in (((0, -0.6), (0, 0.15)), ((0, -0.5), (-0.55, -0.95)), ((0, -0.5), (0.5, -0.2)), ((0, 0.15), (-0.35, 0.85)),
                         ((0, 0.15), (0.7, 0.45))):
                draw.line((cx + a[0] * s, cy + a[1] * s, cx + b[0] * s, cy + b[1] * s), fill=(52, 38, 20), width=w)
            centred(draw, title, title_font, ink, (m, round(high * 0.03), wide - m, round(high * 0.14)))
            written(draw, lines, body_font, ink, (m, round(high * 0.88), wide - m, high - m // 2), round(high * 0.045), centre=True)
        else:
            centred(draw, title, title_font, ink, (m, round(high * 0.07), wide - m, round(high * 0.3)))
            written(draw, lines, body_font, ink, (m, round(high * 0.36), wide - m, high - m), round(high * 0.09), centre=True)
        if pin:
            pinned(image, (0.5, 0.05), pin)
        return image
    return make


def sticky(text, seed, tone=(240, 222, 104), pin=None):
    def make(wide, high, made):
        image = paper(wide, high, tone, seed)
        # The glued strip along the top is a little darker.
        shade = Image.new('L', (wide, high), 0)
        ImageDraw.Draw(shade).rectangle((0, 0, wide, round(high * 0.16)), fill=36)
        image = Image.composite(Image.new('RGB', (wide, high), (180, 150, 40)), image, shade)
        draw = ImageDraw.Draw(image)
        m = round(wide * 0.1)
        written(draw, text, 'Inkfree.ttf', BLUE_INK, (m, round(high * 0.2), wide - m, high - m // 2), round(high * 0.3), spacing=1.1)
        if pin:
            pinned(image, (0.5, 0.07), pin)
        return image
    return make


def cat(image, box):
    """A white cat sitting on an orange ground with a ball of blue wool, as a wall calendar's picture of the month."""
    draw = ImageDraw.Draw(image)
    left, top, right, bottom = box
    draw.rectangle(box, fill=(222, 98, 50))
    w, h = right - left, bottom - top
    white = (238, 234, 228)

    def at(x, y):
        return left + x * w, top + y * h
    draw.rectangle((*at(0, 0.82), *at(1, 1)), fill=(196, 80, 40))
    draw.ellipse((*at(0.66, 0.66), *at(0.86, 0.92)), fill=(70, 102, 170))
    draw.arc((*at(0.6, 0.7), *at(0.82, 0.96)), 200, 300, fill=(110, 140, 200), width=max(round(w * 0.012), 1))
    draw.ellipse((*at(0.22, 0.42), *at(0.6, 0.9)), fill=white)
    draw.ellipse((*at(0.27, 0.12), *at(0.55, 0.48)), fill=white)
    draw.polygon([at(0.29, 0.25), at(0.3, 0.04), at(0.39, 0.16)], fill=white)
    draw.polygon([at(0.53, 0.25), at(0.52, 0.04), at(0.43, 0.16)], fill=white)
    draw.arc((*at(0.42, 0.6), *at(0.72, 0.96)), 200, 340, fill=white, width=max(round(w * 0.04), 2))
    for x in (0.36, 0.46):
        draw.ellipse((*at(x - 0.025, 0.25), *at(x + 0.025, 0.3)), fill=(70, 130, 190))
    draw.polygon([at(0.39, 0.34), at(0.43, 0.34), at(0.41, 0.365)], fill=(210, 130, 130))


def calendar_page(year, month, ringed, seed, picture=None):
    """A month of a wall calendar, and over it, where picture is given, the month's picture drawn by picture(image, box) on the upper half."""
    def make(wide, high, made):
        image = paper(wide, high, (240, 240, 236), seed)
        m = round(wide * 0.06)
        head = 0
        if picture:
            head = round(high * 0.5)
            picture(image, (m, m, wide - m, head))
        draw = ImageDraw.Draw(image)
        page = high - head
        centred(draw, calendar.month_name[month].upper() + ' ' + str(year), 'arialbd.ttf', INK,
                (m, head + round(page * 0.06), wide - m, head + round(page * 0.15)))
        top, bottom = head + round(page * 0.18), high - m
        cols, rows = 7, 7
        cw, rh = (wide - 2 * m) / cols, (bottom - top) / rows
        for k, day in enumerate('SMTWTFS'):
            centred(draw, day, 'arialbd.ttf', RED if k == 0 else INK, (m + k * cw, top, m + (k + 1) * cw, top + rh * 0.7))
        weeks = calendar.Calendar(firstweekday=6).monthdayscalendar(year, month)
        line = max(wide // 300, 1)
        for r in range(len(weeks) + 1):
            draw.line((m, top + (r + 1) * rh, wide - m, top + (r + 1) * rh), fill=(150, 150, 146), width=line)
        for c in range(cols + 1):
            draw.line((m + c * cw, top + rh, m + c * cw, top + (len(weeks) + 1) * rh), fill=(150, 150, 146), width=line)
        face = font('arial.ttf', round(rh * 0.3))
        for r, week in enumerate(weeks):
            for c, day in enumerate(week):
                if day:
                    draw.text((m + c * cw + cw * 0.08, top + (r + 1) * rh + rh * 0.06), str(day), font=face, fill=RED if c == 0 else INK)
                if day == ringed:
                    x, y = m + c * cw + cw / 2, top + (r + 1) * rh + rh / 2
                    draw.ellipse((x - cw * 0.55, y - rh * 0.55, x + cw * 0.55, y + rh * 0.55), outline=(206, 40, 30), width=max(round(cw * 0.08), 2))
        return pinned(image, (0.5, 0.012), (190, 40, 34))
    return make


def tear_off(title, body, number, seed):
    """A flyer with its foot cut into tabs, each with the number on it, turned on its side."""
    def make(wide, high, made):
        image = paper(wide, high, (240, 238, 232), seed)
        draw = ImageDraw.Draw(image)
        m = round(wide * 0.07)
        foot = round(high * 0.62)
        centred(draw, title, 'arialbd.ttf', INK, (m, round(high * 0.06), wide - m, round(high * 0.22)))
        written(draw, body, 'arial.ttf', INK, (m, round(high * 0.26), wide - m, foot - m // 2), round(high * 0.07), centre=True)
        tabs = 7
        tw = wide / tabs
        for k in range(tabs):
            x = round(k * tw)
            if k:
                draw.line((x, foot, x, high), fill=(120, 118, 112), width=max(wide // 300, 1))
            tab = Image.new('RGB', (high - foot - 4, round(tw) - 4), (240, 238, 232))
            centred(ImageDraw.Draw(tab), number, 'arial.ttf', INK, (2, 2, tab.width - 2, tab.height - 2))
            image.paste(tab.rotate(90, expand=True), (x + 2, foot + 2))
        draw.line((0, foot, wide, foot), fill=(150, 148, 140), width=max(wide // 300, 1))
        return pinned(image, (0.5, 0.06), (40, 90, 170))
    return make


def poster(title, lines, foot, tone, band, seed, aged=0.0):
    """A big printed poster: a coloured band with the heading, lines under it, a foot line, with age faded and stained."""
    def make(wide, high, made):
        image = paper(wide, high, tone, seed, aged)
        draw = ImageDraw.Draw(image)
        m = round(wide * 0.07)
        faded = tuple(round(c + (220 - c) * aged * 0.45) for c in band)
        draw.rectangle((m, m, wide - m, round(high * 0.3)), fill=faded)
        centred(draw, title, 'impact.ttf', (244, 236, 220), (m * 2, m * 1.4, wide - m * 2, round(high * 0.3) - m * 0.4))
        ink = tuple(round(c + (200 - c) * aged * 0.4) for c in INK)
        written(draw, lines, 'georgiab.ttf', ink, (m, round(high * 0.35), wide - m, round(high * 0.8)), round(high * 0.07), centre=True)
        centred(draw, foot, 'georgia.ttf', ink, (m, round(high * 0.84), wide - m, round(high * 0.92)))
        return image
    return make


SAID = 'Last seen in Muldraugh. If you know where he is, call the Knox County Sheriff, 555-0142.'
COLLAGES = {
    'Notices|Wanted': (0.718, 0.861, [
        ('notice-wanted-0', (0.01, 0.01, 0.43, 0.49), wanted('family-05', 'DALE ARNOLD PRUITT', SAID, 1)),
        ('notice-wanted-1', (0.58, 0.03, 0.99, 0.49), wanted('family-11', 'RANDALL "RANDY" COBB', SAID, 2)),
        ('notice-wanted-2', (0.01, 0.52, 0.43, 0.99), wanted('family-19', 'TRAVIS LEE HATCHER', SAID, 3)),
        ('notice-wanted-3', (0.50, 0.52, 0.92, 0.99), wanted('family-20', 'WAYNE EDWARD DOSS', SAID, 4)),
    ]),
    'Notices|Advertisement': (0.453, 0.785, [
        ('notice-advert-0', (0.07, 0.21, 0.99, 0.99), sheriff),
        ('notice-advert-1', (0.01, 0.01, 0.52, 0.41), typed('NOTICE', 'This office is closed Saturdays and Sundays until further '
                                                          'notice. For anything that cannot wait, call the desk at 555-0142 or come to the '
                                                          'side door after 6 p.m.', seed=12)),
    ]),
    'Notices|Collection of': (0.735, 0.823, [
        ('notice-collection-0', (0.37, 0.31, 0.56, 0.68), coloured('YARD SALE', 'Sat 9-4\n114 Elm St', (196, 36, 72), (250, 240, 232), 21)),
        ('notice-collection-1', (0.01, 0.43, 0.48, 0.94), coloured('AEROBICS!', 'Tues & Thurs 7 pm, Community Hall', (150, 96, 170),
                                                                  (252, 246, 236), 22, star=True)),
        ('notice-collection-2', (0.01, 0.01, 0.37, 0.43), typed('CHURCH SUPPER', 'First Baptist Church\nSunday 6 p.m.\nBring a dish to share.'
                                                                  ' All welcome.', seed=23, pins=(), face='georgia.ttf', head='georgiab.ttf')),
        ('notice-collection-3', (0.42, 0.07, 0.56, 0.2), sticky('Ask for Bev', 24, tone=(214, 140, 60))),
        ('notice-collection-4', (0.42, 0.2, 0.56, 0.32), sticky('Free kittens', 25, tone=(110, 124, 216))),
        ('notice-collection-5', (0.55, 0.3, 0.89, 0.66), typed('', 'Babysitter available evenings and weekends. CPR trained. Good '
                                                                  'references. Call Amy, 555-0177', tone=(236, 232, 190), seed=26, pins=())),
        ('notice-collection-6', (0.63, 0.02, 0.99, 0.4), coloured('LAWNS', 'Mowed and edged\n$10 a yard\nCall Jake 555-0193', (118, 196, 96),
                                                                  (30, 40, 26), 27)),
        ('notice-collection-7', (0.42, 0.7, 0.76, 0.97), tear_off('GUITAR LESSONS', 'All ages. Beginners welcome.', '555-0116', 28)),
    ]),
    'Notices|Paper': (0.578, 0.816, [
        ('notice-paper-0', (0.3, 0.3, 0.7, 0.6), coloured('BAKE SALE', 'Fri at the school gym', (110, 146, 214), (250, 248, 240), 31)),
        ('notice-paper-1', (0.05, 0.01, 0.48, 0.38), typed('LOST', 'Brown leather wallet near the gas station on Main St. Reward. '
                                                         'Call 555-0128', seed=32, pins=((0.15, 0.04), (0.85, 0.04)))),
        ('notice-paper-2', (0.52, 0.06, 0.95, 0.4), coloured('PIE SUPPER', 'VFW Hall\nSat 5 pm', (150, 36, 40), (250, 226, 120), 33, pin=(220, 190, 60))),
        ('notice-paper-3', (0.01, 0.53, 0.48, 0.99), typed('MEETING', 'The water board meets the first Monday of the month at 7 p.m. in '
                                                          'the fire hall. Residents are asked to attend.', seed=34, pin=(60, 150, 70))),
        ('notice-paper-4', (0.52, 0.48, 0.99, 0.99), typed('FOR SALE', '1979 pickup, runs good, new tires. $900 or best offer. See at the '
                                                          'feed store or call 555-0151', seed=35)),
    ]),
    'Notices|Random': (0.844, 0.676, [
        ('notice-random-0', (0.01, 0.01, 0.46, 0.94), calendar_page(1993, 7, 9, 41, picture=cat)),
        ('notice-random-1', (0.53, 0.28, 0.82, 0.86), typed('TO DO', '- call landlord\n- pick up prescription\n- gas\n- bank before Fri\n'
                                                          '- milk, eggs, bread', seed=43, pins=())),
        ('notice-random-2', (0.49, 0.01, 0.71, 0.24), sticky('Dentist Tues 10:30', 44)),
        ('notice-random-3', (0.74, 0.07, 0.97, 0.3), sticky('Mom called!', 45)),
        ('notice-random-4', (0.74, 0.71, 0.99, 0.98), sticky('rent due the 1st', 46)),
    ]),
    'Notices|Weathered': (0.734, 0.817, [
        ('notice-weathered-0', (0.01, 0.01, 0.83, 0.98), poster('COUNTY FAIR', 'Livestock, rides\nand music\nAugust 12-15', 'Knox County Fairgrounds',
                                                                (232, 226, 206), (176, 46, 40), 51, aged=0.9)),
        ('notice-weathered-1', (0.78, 0.55, 0.99, 0.99), typed('', 'Free firewood. You haul. Call 555-0187', seed=52, aged=0.8, pins=())),
    ]),
    'Notices|Battered': (0.687, 0.670, [
        ('notice-battered-0', (0.01, 0.01, 0.47, 0.6), typed('NOTICE', 'No parking in front of these doors at any time. Vehicles will '
                                                            'be towed at owner\'s expense.', seed=61, aged=1.0, pins=())),
        ('notice-battered-1', (0.56, 0.38, 0.99, 0.99), typed('', 'Help wanted. Apply inside. Ask for the manager.', seed=62, aged=1.0, pins=())),
    ]),
}

PICTURES = {name: (key, box, maker) for key, (_, _, sheets) in COLLAGES.items() for name, box, maker in sheets}
SHEETS = [(key, name, box) for key, (_, _, sheets) in COLLAGES.items() for name, box, _ in sheets]


def picture(name, made):
    """The finished sheet of that name, at PER_METRE no longer than LONGEST, or None where its art is not made yet."""
    key, box, maker = PICTURES[name]
    across, high, _ = COLLAGES[key]
    wide, tall = (box[2] - box[0]) * across, (box[3] - box[1]) * high
    scale = min(PER_METRE, LONGEST / max(wide, tall))
    return maker(round(wide * scale), round(tall * scale), made)
