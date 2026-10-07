package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Unpacks BC7 blocks into four bytes a texel, for a graphics card that cannot read them
 * itself. A block is sixteen bytes that hold a square of four texels by four, in one of eight
 * layouts: the format is the one OpenGL's ARB_texture_compression_bptc sets out, and the
 * tables below are that specification's.
 */
final class Bc7 {
    private Bc7() {}

    /**
     * For each layout: how many sets its texels are in, the bits that say which split into sets,
     * the bits that say which of red, green and blue changes places with alpha, whether a bit
     * says which index colour takes, the bits of a colour and of an alpha at a set's two ends,
     * whether each end has a lowest bit of its own, whether each set's ends share one, and the
     * bits of a texel's first and second index.
     */
    private static final int[][] LAYOUTS = {
            {3, 4, 0, 0, 4, 0, 1, 0, 3, 0}, {2, 6, 0, 0, 6, 0, 0, 1, 3, 0}, {3, 6, 0, 0, 5, 0, 0, 0, 2, 0}, {2, 6, 0, 0, 7, 0, 1, 0, 2, 0},
            {1, 0, 2, 1, 5, 6, 0, 0, 2, 3}, {1, 0, 2, 0, 7, 8, 0, 0, 2, 2}, {1, 0, 0, 0, 7, 7, 1, 0, 4, 0}, {2, 6, 0, 0, 5, 5, 1, 0, 2, 0}};
    /** How far from a set's first end towards its second an index of two, three and four bits stands, in sixty-fourths. */
    private static final int[][] WEIGHTS = {null, null, {0, 21, 43, 64}, {0, 9, 18, 27, 37, 46, 55, 64},
            {0, 4, 9, 13, 17, 21, 26, 30, 34, 38, 43, 47, 51, 55, 60, 64}};
    /** Which set each texel of a block is in, for each split into two (a bit a texel) and into three (two bits a texel). */
    private static final int[] TWO = {
            0xCCCC, 0x8888, 0xEEEE, 0xECC8, 0xC880, 0xFEEC, 0xFEC8, 0xEC80,
            0xC800, 0xFFEC, 0xFE80, 0xE800, 0xFFE8, 0xFF00, 0xFFF0, 0xF000,
            0xF710, 0x008E, 0x7100, 0x08CE, 0x008C, 0x7310, 0x3100, 0x8CCE,
            0x088C, 0x3110, 0x6666, 0x366C, 0x17E8, 0x0FF0, 0x718E, 0x399C,
            0xAAAA, 0xF0F0, 0x5A5A, 0x33CC, 0x3C3C, 0x55AA, 0x9696, 0xA55A,
            0x73CE, 0x13C8, 0x324C, 0x3BDC, 0x6996, 0xC33C, 0x9966, 0x0660,
            0x0272, 0x04E4, 0x4E40, 0x2720, 0xC936, 0x936C, 0x39C6, 0x639C,
            0x9336, 0x9CC6, 0x817E, 0xE718, 0xCCF0, 0x0FCC, 0x7744, 0xEE22
    };
    private static final int[] THREE = {
            0xAA685050, 0x6A5A5040, 0x5A5A4200, 0x5450A0A8, 0xA5A50000, 0xA0A05050,
            0x5555A0A0, 0x5A5A5050, 0xAA550000, 0xAA555500, 0xAAAA5500, 0x90909090,
            0x94949494, 0xA4A4A4A4, 0xA9A59450, 0x2A0A4250, 0xA5945040, 0x0A425054,
            0xA5A5A500, 0x55A0A0A0, 0xA8A85454, 0x6A6A4040, 0xA4A45000, 0x1A1A0500,
            0x0050A4A4, 0xAAA59090, 0x14696914, 0x69691400, 0xA08585A0, 0xAA821414,
            0x50A4A450, 0x6A5A0200, 0xA9A58000, 0x5090A0A8, 0xA8A09050, 0x24242424,
            0x00AA5500, 0x24924924, 0x24499224, 0x50A50A50, 0x500AA550, 0xAAAA4444,
            0x66660000, 0xA5A0A5A0, 0x50A050A0, 0x69286928, 0x44AAAA44, 0x66666600,
            0xAA444444, 0x54A854A8, 0x95809580, 0x96969600, 0xA85454A8, 0x80959580,
            0xAA141414, 0x96960000, 0xAAAA1414, 0xA05050A0, 0xA0A5A5A0, 0x96000000,
            0x40804080, 0xA9A8A9A8, 0xAAAAAA44, 0x2A4A5254
    };
    /** The texel of the second and of the third set whose index is stored a bit short, for each split. The first set's is the block's first texel. */
    private static final byte[] SECOND_OF_TWO = {
            15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15,
            15, 2, 8, 2, 2, 8, 8, 15, 2, 8, 2, 2, 8, 8, 2, 2,
            15, 15, 6, 8, 2, 8, 15, 15, 2, 8, 2, 2, 2, 15, 15, 6,
            6, 2, 6, 8, 15, 15, 2, 2, 15, 15, 15, 15, 15, 2, 2, 15
    };
    private static final byte[] SECOND_OF_THREE = {
            3, 3, 15, 15, 8, 3, 15, 15, 8, 8, 6, 6, 6, 5, 3, 3,
            3, 3, 8, 15, 3, 3, 6, 10, 5, 8, 8, 6, 8, 5, 15, 15,
            8, 15, 3, 5, 6, 10, 8, 15, 15, 3, 15, 5, 15, 15, 15, 15,
            3, 15, 5, 5, 5, 8, 5, 10, 5, 10, 8, 13, 15, 12, 3, 3
    };
    private static final byte[] THIRD_OF_THREE = {
            15, 8, 8, 3, 15, 15, 3, 8, 15, 15, 15, 15, 15, 15, 15, 8,
            15, 8, 15, 3, 15, 8, 15, 8, 3, 15, 6, 10, 15, 15, 10, 8,
            15, 3, 15, 10, 10, 8, 9, 10, 6, 15, 8, 15, 3, 6, 6, 8,
            15, 3, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 3, 15, 15, 8
    };

    /**
     * Unpacks a picture wide by high out of the blocks that begin at from, which run row by
     * row over the picture rounded up to whole blocks, into to from at: four bytes a texel,
     * red first, row by row.
     */
    static void unpack(ByteBuffer packed, int from, int wide, int high, ByteBuffer to, int at) {
        ByteBuffer blocks = packed.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int[][] ends = new int[6][4];
        int[] first = new int[16], second = new int[16], texel = new int[4];
        for (int top = 0; top < high; top += 4) {
            for (int left = 0; left < wide; left += 4, from += 16) {
                long low = blocks.getLong(from), upper = blocks.getLong(from + 8);
                int which = Long.numberOfTrailingZeros(low & 0xff | 0x100);
                // A first byte of nothing is no layout: such a block is nothing all over.
                int[] layout = which < 8 ? LAYOUTS[which] : null;
                int sets = 1, split = 0, turned = 0, chosen = 0, indexBits = 0, secondBits = 0;
                if (layout != null) {
                    sets = layout[0];
                    int bit = which + 1;
                    split = bits(low, upper, bit, layout[1]);
                    bit += layout[1];
                    turned = bits(low, upper, bit, layout[2]);
                    bit += layout[2];
                    chosen = bits(low, upper, bit, layout[3]);
                    bit += layout[3];
                    int colourBits = layout[4], alphaBits = layout[5];
                    for (int part = 0; part < 4; part++) {
                        int count = part < 3 ? colourBits : alphaBits;
                        for (int end = 0; end < sets * 2; end++) {
                            ends[end][part] = bits(low, upper, bit, count);
                            bit += count;
                        }
                    }
                    if (layout[6] + layout[7] > 0) {
                        int sharing = layout[6] > 0 ? 1 : 2;
                        for (int end = 0; end < sets * 2; end += sharing) {
                            int lowest = bits(low, upper, bit++, 1);
                            for (int with = end; with < end + sharing; with++) {
                                for (int part = 0; part < (alphaBits > 0 ? 4 : 3); part++) {
                                    ends[with][part] = ends[with][part] << 1 | lowest;
                                }
                            }
                        }
                        colourBits++;
                        alphaBits += alphaBits > 0 ? 1 : 0;
                    }
                    for (int end = 0; end < sets * 2; end++) {
                        for (int part = 0; part < 3; part++) {
                            int raised = ends[end][part] << 8 - colourBits;
                            ends[end][part] = raised | raised >> colourBits;
                        }
                        int raised = ends[end][3] << 8 - alphaBits;
                        ends[end][3] = alphaBits > 0 ? raised | raised >> alphaBits : 255;
                    }
                    indexBits = layout[8];
                    secondBits = layout[9];
                    int secondOf = sets == 2 ? SECOND_OF_TWO[split] : sets == 3 ? SECOND_OF_THREE[split] : -1;
                    int thirdOf = sets == 3 ? THIRD_OF_THREE[split] : -1;
                    for (int each = 0; each < 16; each++) {
                        int count = each == 0 || each == secondOf || each == thirdOf ? indexBits - 1 : indexBits;
                        first[each] = bits(low, upper, bit, count);
                        bit += count;
                    }
                    for (int each = 0; each < 16 && secondBits > 0; each++) {
                        int count = each == 0 ? secondBits - 1 : secondBits;
                        second[each] = bits(low, upper, bit, count);
                        bit += count;
                    }
                }
                for (int each = 0; each < 16; each++) {
                    int x = left + (each & 3), y = top + (each >> 2);
                    if (x >= wide || y >= high) {
                        continue;
                    }
                    int place = at + (y * wide + x) * 4;
                    if (layout == null) {
                        to.putInt(place, 0);
                        continue;
                    }
                    int set = sets == 1 ? 0 : sets == 2 ? TWO[split] >> each & 1 : THREE[split] >> each * 2 & 3;
                    int[] one = ends[set * 2], other = ends[set * 2 + 1];
                    // With two indices a texel, colour takes the first and alpha the second, or the other way about where the block says so.
                    int colour = secondBits > 0 && chosen == 1 ? WEIGHTS[secondBits][second[each]] : WEIGHTS[indexBits][first[each]];
                    int alpha = secondBits == 0 ? colour : chosen == 1 ? WEIGHTS[indexBits][first[each]] : WEIGHTS[secondBits][second[each]];
                    for (int part = 0; part < 4; part++) {
                        int weight = part < 3 ? colour : alpha;
                        texel[part] = (64 - weight) * one[part] + weight * other[part] + 32 >> 6;
                    }
                    if (turned > 0) {
                        int kept = texel[3];
                        texel[3] = texel[turned - 1];
                        texel[turned - 1] = kept;
                    }
                    to.put(place, (byte) texel[0]).put(place + 1, (byte) texel[1]).put(place + 2, (byte) texel[2]).put(place + 3, (byte) texel[3]);
                }
            }
        }
    }

    /** Count bits of a block, eight at most, from bit at of its 128, the lowest of its first byte being bit 0. */
    private static int bits(long low, long upper, int at, int count) {
        long from = at >= 64 ? upper >>> at - 64 : at == 0 ? low : low >>> at | upper << 64 - at;
        return (int) (from & (1L << count) - 1);
    }
}
