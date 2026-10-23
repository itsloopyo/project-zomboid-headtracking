"""Writes which show each of the game's television broadcasts and video tapes is, for the mod to pick a set's pictures by.

    python tools/pictures/schedule.py "<game>/media" PATTERNS

Reads the game's own radio data and tapes on this machine and the English of their lines, tells each one's show by
the patterns in the file PATTERNS, and writes src/main/resources/pictures/shows.txt. That file holds the mod's names of
shows and the game's ids of broadcasts and tapes: none of the game's words go into it.

PATTERNS is a Python file kept outside the repository, because what tells a broadcast's show is pieces of the game's
own lines and titles. It sets NOTICE, a regular expression for a line a station says before an old programme once it
has stopped making new ones; AIRS, a list of (regular expression for a broadcast's or a tape's first lines, show), the
first that matches being the show; and TAPES, the same for a tape's title.

A line of it is tab separated: "show", a show's name and how many shots it has, the shows in the order the mod numbers
them; "channel", a frequency and the show of a broadcast on it the file does not list; "air", a broadcast's id and its
show; "tape", a tape's id and its show. The counts written here are the shots shows.py plans; pack.py, run after,
sets each to the shots it made.

Stops at a broadcast or a tape that is no show, so that a game update with a new programme in it is seen here.
"""
import collections
import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ElementTree

import runpy

import shows

OUT = pathlib.Path(__file__).resolve().parents[2] / 'src' / 'main' / 'resources' / 'pictures' / 'shows.txt'
# How many of its first lines say what a broadcast or a tape is.
OPENING = 5
INTERFERENCE = re.compile(r'^(\W|<[bf]zzt>)*$')
PATTERNS = {}


def of(opening):
    """The show that says those first lines, or None."""
    said = ' / '.join(opening)
    for pattern, show in PATTERNS['AIRS']:
        if re.search(pattern, said):
            return show
    # A broadcast most of whose opening is interference and the rest no show's is snow.
    return shows.SNOW if 2 * sum(1 for line in opening if INTERFERENCE.match(line)) > len(opening) else None


def broadcasts(media):
    """The id of each television broadcast that has lines, with its show."""
    with open(media / 'lua/shared/Translate/EN/RadioData.json', encoding='utf-8') as file:
        english = json.load(file)
    found, lost = {}, []
    for channel in ElementTree.parse(media / 'radio/RadioData.xml').getroot().iter('ChannelEntry'):
        if channel.get('cat') != 'Television':
            continue
        frequency = int(channel.get('freq'))
        if frequency not in shows.CHANNELS:
            raise SystemExit(f"the channel {channel.get('name')} on {frequency} has no show in shows.CHANNELS")
        for broadcast in channel.iter('BroadcastEntry'):
            lines = [english['RD_' + line.get('ID')] for line in broadcast.iter('LineEntry')]
            if not lines:
                continue
            # A station that has stopped making programmes says so and then shows an old one: the old one is the show.
            shown = [line for line in lines if not re.search(PATTERNS['NOTICE'], line)]
            show = of(shown[:OPENING]) if shown else None
            if show is None and len(shown) < len(lines):
                show = shows.EMERGENCY
            if show is None and frequency in shows.NEWS:
                show = shows.CHANNELS[frequency]
            if show is None:
                lost.append(f"{channel.get('name')}: {' / '.join(lines[:3])}")
            found[broadcast.get('ID')] = show
    return found, lost


def tapes(media):
    """The id of each video tape, with its show."""
    with open(media / 'lua/shared/Translate/EN/Recorded_Media.json', encoding='utf-8') as file:
        english = json.load(file)
    lua = (media / 'lua/shared/RecordedMedia/recorded_media.lua').read_text(encoding='utf-8')
    found, lost = {}, []
    blocks = re.findall(r'RecMedia\["([^"]+)"\]\s*=\s*\{(.*?)\n\t?\};?', lua, re.S)
    if len(blocks) != lua.count('RecMedia["'):
        raise SystemExit(f"recorded_media.lua has {lua.count('RecMedia[')} tapes and {len(blocks)} of them could be read")
    for identity, body in blocks:
        if not re.search(r'category\s*=\s*"[^"]*VHS"', body):
            continue
        title = english[re.search(r'title\s*=\s*"([^"]*)"', body).group(1)]
        show = next((show for pattern, show in PATTERNS['TAPES'] if re.search(pattern, title)), None)
        if show is None:
            show = of([title])
        if show is None:
            show = of([english[key] for key in re.findall(r'text\s*=\s*"([^"]+)"', body)[:OPENING]])
        if show is None:
            lost.append('tape: ' + title)
        found[identity] = show
    return found, lost


def main():
    media = pathlib.Path(sys.argv[1])
    PATTERNS.update(runpy.run_path(sys.argv[2]))
    aired, lost = broadcasts(media)
    taped, more = tapes(media)
    if lost or more:
        raise SystemExit('no show in the patterns for:\n  ' + '\n  '.join(lost + more))
    names = [shows.ANY, shows.SNOW] + list(shows.SHOWS)
    counts = {shows.ANY: shows.ANY_SHOTS, shows.SNOW: 0, **{key: len(shots) for key, shots in shows.SHOWS.items()}}
    used = collections.Counter(list(aired.values()) + list(taped.values()) + list(shows.CHANNELS.values()))
    unknown = sorted(set(used) - set(names))
    if unknown:
        raise SystemExit('the patterns name shows that shows.py has no shots of: ' + ' '.join(unknown))
    with open(OUT, 'w', encoding='utf-8', newline='\n') as out:
        for name in names:
            out.write(f'show\t{name}\t{counts[name]}\n')
        for frequency, show in sorted(shows.CHANNELS.items()):
            out.write(f'channel\t{frequency}\t{show}\n')
        for identity, show in sorted(aired.items()):
            out.write(f'air\t{identity}\t{show}\n')
        for identity, show in sorted(taped.items()):
            out.write(f'tape\t{identity}\t{show}\n')
    print(f'{len(names)} shows, {sum(counts.values())} shots, {len(aired)} broadcasts, {len(taped)} tapes')
    for name in names:
        print(f'  {name:14} {counts[name]} shots, {used[name]} times')


if __name__ == '__main__':
    main()
