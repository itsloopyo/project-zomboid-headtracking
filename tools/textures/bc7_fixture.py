"""Makes what the mod's test of Bc7.java reads: BC7 blocks, and the texels a reference decoder unpacks them to.

    python tools/textures/bc7_fixture.py

Writes src/test/data/bc7/blocks.bin, 1024 blocks that are 128 of each of BC7's eight layouts with
every other bit drawn at random (the same every run), and texels.bin, the picture 128 texels
square that texconv, the encoder compress.py uses, unpacks them to: four bytes a texel.
"""
import pathlib
import struct
import subprocess
import tempfile

import numpy

import compress

OUT = compress.ROOT / 'src' / 'test' / 'data' / 'bc7'
SIDE = 128


def main():
    blocks = numpy.random.default_rng(7).integers(0, 256, size=(SIDE // 4 * SIDE // 4, 16), dtype=numpy.uint8)
    for index in range(len(blocks)):
        # A block's layout is the place of the lowest bit set in its first byte.
        layout = index % 8
        blocks[index, 0] = (int(blocks[index, 0]) << layout + 1 | 1 << layout) & 0xff
    with tempfile.TemporaryDirectory() as folder:
        folder = pathlib.Path(folder)
        (folder / 'out').mkdir()
        # "DDS ", its header, and the DX10 header that says BC7 (98).
        head = (struct.pack('<4sIIIIIII44s', b'DDS ', 124, 0x81007, SIDE, SIDE, blocks.size, 0, 1, b'\0' * 44)
                + struct.pack('<II4sIIIII', 32, 4, b'DX10', 0, 0, 0, 0, 0) + struct.pack('<IIIII', 0x1000, 0, 0, 0, 0) + struct.pack('<IIIII', 98, 3, 0, 1, 0))
        (folder / 'blocks.dds').write_bytes(head + blocks.tobytes())
        ran = subprocess.run([str(compress.encoder()), '-nologo', '-y', '-f', 'R8G8B8A8_UNORM', '-m', '1', '-dx10', '-o', str(folder / 'out'), str(folder / 'blocks.dds')],
                             capture_output=True, text=True)
        if ran.returncode != 0:
            raise SystemExit(ran.stdout + ran.stderr)
        unpacked = (folder / 'out' / 'blocks.dds').read_bytes()
    if len(unpacked) != 148 + SIDE * SIDE * 4:
        raise SystemExit('the reference decoder did not write four bytes a texel')
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / 'blocks.bin').write_bytes(blocks.tobytes())
    (OUT / 'texels.bin').write_bytes(unpacked[148:])
    print('wrote', OUT)


if __name__ == '__main__':
    main()
