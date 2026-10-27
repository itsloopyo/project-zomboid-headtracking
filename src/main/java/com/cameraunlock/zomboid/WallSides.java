package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import zombie.iso.sprite.IsoSprite;

/**
 * What the two sides of a wall show.
 *
 * The game has one sprite for a wall, painted for the side its own view sees: the south face
 * of a wall on a square's north edge, the east face of one on its west edge. So the walls on
 * the north and west of a house carry the wallpaper of the room behind them, and those on its
 * south and east, which stand on the squares just outside it, carry its siding. The skin of a
 * wall on the side its sprite was painted for shows the sprite. The other skin keeps the
 * sprite's outline, its windows and doors, and is painted over in the colour of the surface
 * that side has: out of doors the finish of the room behind it, which is the sprite most of
 * that room's south and east walls show, and indoors the sprite most of that room's own walls
 * show. See WallFinish.
 */
final class WallSides {
    /** How a skin is painted over: plain paint, or boards lapped one over the next. */
    static final int FLAT = 1, SIDING = 2;
    /** How a wall that shows its own sprite is laid over it: bricks, boards lapped along it, cut stone, or cast blocks. See Masonry and laid. */
    static final int BRICK = 4, LAPPED = 5, STONE = 6, BLOCK = 7;

    /** A wall of upright boards, which the picture makes board by board in the colour given. It has no colour of joints, and is never laid over another wall's sprite. */
    static final int BOARDED = 8;

    /**
     * How a wall the game says is of brick, cinder block or stone (its MaterialType) is laid, or 0
     * for any other. Its sprite is still read for its colours: a cinder block shed's sprite read
     * as boards lapped along it (location_community_school_01_0..2, 5427..5430,6759).
     */
    static int madeOf(IsoSprite sprite) {
        return madeOf(sprite.getProperties().get("MaterialType"));
    }

    static int madeOf(String materialType) {
        return materialType == null ? 0 : switch (materialType) {
            case "Brick" -> BRICK;
            case "Cinderblock" -> BLOCK;
            case "Stone" -> STONE;
            default -> 0;
        };
    }

    /** how, a wall's laying (laid), with lapped boards made the masonry the game says it is, its colours kept. */
    static int asMade(int how, int masonry) {
        return masonry != 0 && (how & 15) == LAPPED ? how & ~15 | masonry : how;
    }

    private static final int BODY_LEVELS = 31, JOINT_LEVELS = 15;
    private static final int OVER = 8;
    /** In place of a colour: the triangles are the panes of windows, which the picture draws after everything else. No colour packs to this, a painted one being FLAT or SIDING with a colour above it. */
    static final int PANES = 3;
    /**
     * In place of a colour: the triangles are the game's blood, which the picture draws after
     * everything else that is laid, each splat mixed over the ones under it: see BloodBuilder.
     * No colour packs to this, nothing being painted in a ninth way.
     */
    static final int BLOOD = 9;
    /**
     * Plain paint as FLAT is, in a colour kept as finely as laid keeps a wall's (laid with this
     * and no joints): a part of a wall made of many colours, a tile or a joint, is painted in its
     * own and lit as the place is. Lit as a shade of a coarser colour it was not: a room's light
     * is taken down by its daylight channel by channel (see the world program's given), and a
     * blue band of tiles lit a few hundredths redder came out purple.
     */
    static final int FINE = 10;
    /**
     * A band of tiles, laid as one face (laid with this and the joints' colour): the picture
     * lays the joints itself where the face's texture coordinates are whole numbers, one a joint
     * along the wall and one a course line up it. See WallTiles.stretches.
     */
    static final int TILED = 11;

    /**
     * The top bit of a key that multiplies its sprite by a colour, kept to a 255th of each
     * channel (see multiplied): the game's tint of a sprite, which is the colour of the thing
     * and not of the light on it. The picture is told -1 for how it is painted, below every
     * other way, so that nothing else a key can mean is done to it.
     */
    private static final int MULTIPLIED = 1 << 31;
    /**
     * In place of a colour: the triangles are the game's grime over a wall, a mist of dots the game's
     * view sees a few to a pixel, which a near eye saw as crawling worms, so the picture draws grime
     * of its own there: see Stains.grime. No key packs to this, a multiplied one keeping its low four bits clear.
     */
    static final int STAIN = MULTIPLIED | 1;
    /** The low four bits of a key slats gives. */
    private static final int SLATS = 2;

    private static final int LEVELS = 15;
    private static final int BANDS = 32;
    private static final int PAINTED = 128;
    private static final float RED = 0.299f, GREEN = 0.587f, BLUE = 0.114f;

    private WallSides() {}

    /** What each of a set of walls shows, as numbers, and which number most of them agree on. */
    static final class Votes {
        /** Each a place in a list, so none is negative. */
        private int[] cast = new int[16];
        private int count;

        void add(int vote) {
            if (count == cast.length) {
                cast = Arrays.copyOf(cast, count * 2);
            }
            cast[count++] = vote;
        }

        boolean isEmpty() {
            return count == 0;
        }

        /** The vote cast most often, and of two cast equally often the one cast first. -1 when none was cast. */
        int commonest() {
            int highest = -1;
            for (int i = 0; i < count; i++) {
                highest = Math.max(highest, cast[i]);
            }
            int[] times = new int[highest + 1];
            for (int i = 0; i < count; i++) {
                times[cast[i]]++;
            }
            int best = -1, most = 0;
            for (int i = 0; i < count; i++) {
                if (times[cast[i]] > most) {
                    most = times[cast[i]];
                    best = cast[i];
                }
            }
            return best;
        }
    }

    /**
     * What each room of one level of a building shows outside, as numbers. A room shows what
     * most of its own outside walls show. One with none shows what the nearest outside wall
     * seen from it through the building shows.
     */
    static final class Outsides {
        private final HashMap<Long, Votes> shown = new HashMap<>();
        /** For each room how far off the nearest wall seen from it is, and what that shows. */
        private final HashMap<Long, int[]> seen = new HashMap<>();

        /** An outside wall of the room's own shows that. */
        void shows(long room, int finish) {
            shown.computeIfAbsent(room, unused -> new Votes()).add(finish);
        }

        /** From the room an outside wall that shows that is seen, so many squares off. */
        void sees(long room, int far, int finish) {
            int[] nearest = seen.get(room);
            if (nearest == null || far < nearest[0]) {
                seen.put(room, new int[] {far, finish});
            }
        }

        /** What the room shows outside, and -1 for a room with no outside wall of its own that sees none. */
        int of(long room) {
            Votes own = shown.get(room);
            if (own != null) {
                return own.commonest();
            }
            int[] nearest = seen.get(room);
            return nearest == null ? -1 : nearest[1];
        }
    }

    /**
     * The name of the sprite so many places on from the named one on its sheet: a sprite is
     * named for its sheet and its place on it, "walls_exterior_house_01_17". The name itself
     * where it does not end in a number.
     */
    static String sheetmate(String name, int on) {
        int cut = name.lastIndexOf('_') + 1;
        // Nine digits at most: a mod's name may run on past what an int holds, and no sheet has so many places.
        if (cut == name.length() || name.length() - cut > 9) {
            return name;
        }
        for (int i = cut; i < name.length(); i++) {
            if (name.charAt(i) < '0' || name.charAt(i) > '9') {
                return name;
            }
        }
        return name.substring(0, cut) + (Integer.parseInt(name.substring(cut)) + on);
    }

    /**
     * A colour to paint a skin over in, each part from 0 to 1, and how it is painted, FLAT or
     * SIDING, as one number that is never 0.
     *
     * Every different number is one more texture change and one more draw each frame, since
     * the colour is told to the picture once for all the triangles that share it. Rounded to
     * sixteen steps a channel, the walls of a street come to a few dozen colours and not to
     * one for every sprite. A house's far skins all take their colour from the same sprite, so
     * the rounding is the same along a whole wall and never shows as a step.
     */
    static int key(float red, float green, float blue, int how) {
        return level(red) << 12 | level(green) << 8 | level(blue) << 4 | how;
    }

    /**
     * As key, for a wall the picture lays: the colour of its bricks, boards or stones, the
     * colour of its joints, and BRICK, LAPPED, STONE or BLOCK. The first colour is kept twice as
     * finely as a painted skin's. A wall is many sprites, with a window in one and a door in
     * the next, each read on its own, and two that came a sixteenth apart would show as a step
     * down the wall where they meet.
     */
    static int laid(float red, float green, float blue, float jointRed, float jointGreen, float jointBlue, int how) {
        return part(jointRed, JOINT_LEVELS) << 27 | part(jointGreen, JOINT_LEVELS) << 23 | part(jointBlue, JOINT_LEVELS) << 19
                | part(red, BODY_LEVELS) << 14 | part(green, BODY_LEVELS) << 9 | part(blue, BODY_LEVELS) << 4 | how;
    }

    /** The key of a sprite drawn multiplied by a colour, each channel from 0 to 1: 0, no key, for white, which leaves the sprite as it is. */
    static int multiplied(float red, float green, float blue) {
        int packed = part(red, 255) << 20 | part(green, 255) << 12 | part(blue, 255) << 4;
        return packed == (255 << 20 | 255 << 12 | 255 << 4) ? 0 : MULTIPLIED | packed;
    }

    /**
     * The key of a shut roller door, the colour most of its sprite is, each channel from 0 to 1:
     * the picture lays the door's slats itself where the sprite paints them, so they are sharp
     * up close and run on in one line from one sprite of the door to the next. The picture is
     * told 17 for how it is painted.
     */
    static int slats(float red, float green, float blue) {
        return MULTIPLIED | part(red, 255) << 20 | part(green, 255) << 12 | part(blue, 255) << 4 | SLATS;
    }

    /** Whether a key is one slats gives. */
    static boolean slatted(int key) {
        return (key & MULTIPLIED) != 0 && (key & 15) == SLATS;
    }

    /** Whether a garage door's sprite is a shut roller door, of slats one over the next: the green and the grey of the garage sheet, and the trucks' grey. */
    static boolean rollerDoor(String name) {
        if (name == null) {
            return false;
        }
        int garage = Sheets.place(name, "walls_garage_01_"), trucks = Sheets.place(name, "industry_trucks_01_");
        return garage >= 16 && garage <= 21 || garage >= 48 && garage <= 53 || trucks >= 32 && trucks <= 37;
    }

    /**
     * As a number laid gives, for a skin on the far side of a wall from the one its sprite was
     * painted for: the sprite is the room's wallpaper, and gives the skin its outline alone.
     */
    static int over(int laid) {
        return laid | OVER;
    }

    /** How much lighter or darker one colour may be than another, as a ratio, and how far from it once made as light, summed over red, green and blue, for the two to be one paint. */
    private static final float MOST_LIGHTER = 1.5f, SAME_PAINT = 0.12f;

    /**
     * Whether two colours, each red, green and blue from 0 to 1, are one paint seen in two
     * lights: the one is the other made lighter or darker by no more than half.
     */
    static boolean oneColourInTwoLights(float[] one, float[] other) {
        float sum = other[0] + other[1] + other[2];
        if (sum <= 0f) {
            return false;
        }
        float lighter = (one[0] + one[1] + one[2]) / sum;
        return lighter <= MOST_LIGHTER && lighter >= 1f / MOST_LIGHTER
                && Math.abs(one[0] - lighter * other[0]) + Math.abs(one[1] - lighter * other[1]) + Math.abs(one[2] - lighter * other[2]) <= SAME_PAINT;
    }

    /** A colour's part as a laid key keeps a joint's: see laid. */
    static float asJoint(float value) {
        return part(value, JOINT_LEVELS) / (float) JOINT_LEVELS;
    }

    private static int part(float value, int levels) {
        return Math.round(Math.clamp(value, 0f, 1f) * levels);
    }

    /** Whether a key is one laid gives. */
    private static boolean isLaid(int key) {
        return (key & 15) >= BRICK;
    }

    /** Into out the colour of the joints of a wall the picture lays, or 0 for any other key. */
    static void joint(int key, float[] out) {
        boolean laid = isLaid(key);
        out[0] = laid ? (key >> 27 & JOINT_LEVELS) / (float) JOINT_LEVELS : 0f;
        out[1] = laid ? (key >> 23 & JOINT_LEVELS) / (float) JOINT_LEVELS : 0f;
        out[2] = laid ? (key >> 19 & JOINT_LEVELS) / (float) JOINT_LEVELS : 0f;
    }

    private static int level(float value) {
        return Math.round(Math.clamp(value, 0f, 1f) * LEVELS);
    }

    /** One channel of a colour as key keeps it. */
    static float kept(float value) {
        return level(value) / (float) LEVELS;
    }

    /**
     * What the picture is told for a range of triangles: into out the colour, then FLAT or
     * SIDING. All 0 for a key of 0, which is no colour, -1 after the colour for one that
     * multiplies its sprite, and 17 for a shut roller door's (slats).
     */
    static void tint(int key, float[] out) {
        if (key == 0) {
            out[0] = out[1] = out[2] = out[3] = 0f;
            return;
        }
        if (key == STAIN) {
            out[0] = out[1] = out[2] = 0f;
            out[3] = 16f;
            return;
        }
        if ((key & MULTIPLIED) != 0) {
            out[0] = (key >> 20 & 255) / 255f;
            out[1] = (key >> 12 & 255) / 255f;
            out[2] = (key >> 4 & 255) / 255f;
            out[3] = slatted(key) ? 17f : -1f;
            return;
        }
        if (isLaid(key)) {
            out[0] = (key >> 14 & BODY_LEVELS) / (float) BODY_LEVELS;
            out[1] = (key >> 9 & BODY_LEVELS) / (float) BODY_LEVELS;
            out[2] = (key >> 4 & BODY_LEVELS) / (float) BODY_LEVELS;
            out[3] = key & 15;
            return;
        }
        out[0] = (key >> 12 & 15) / (float) LEVELS;
        out[1] = (key >> 8 & 15) / (float) LEVELS;
        out[2] = (key >> 4 & 15) / (float) LEVELS;
        out[3] = key & 15;
    }

    /**
     * The colour most of a sprite is painted in: into out its red, green and blue and its
     * brightness, each from 0 to 1. The sprite is the rectangle from (left, top) to before
     * (right, bottom) of a picture width pixels wide, held as bytes of red, green, blue and
     * cover. False for a rectangle with nothing painted in it.
     *
     * Not the mean of all of it: a wall with a window in it is part frame and part glass, and
     * boards have dark lines between them. The painted pixels are sorted by brightness into
     * bands, and the colour is the mean of the fullest band and the one either side of it.
     */
    static boolean usualColour(ByteBuffer pixels, int width, int left, int top, int right, int bottom, float[] out) {
        int[] bands = new int[BANDS];
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int at = (y * width + x) * 4;
                if ((pixels.get(at + 3) & 0xff) >= PAINTED) {
                    bands[band(pixels, at)]++;
                }
            }
        }
        int fullest = 0;
        for (int band = 1; band < BANDS; band++) {
            if (bands[band] > bands[fullest]) {
                fullest = band;
            }
        }
        if (bands[fullest] == 0) {
            return false;
        }
        long red = 0, green = 0, blue = 0;
        int count = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int at = (y * width + x) * 4;
                if ((pixels.get(at + 3) & 0xff) >= PAINTED && Math.abs(band(pixels, at) - fullest) <= 1) {
                    red += pixels.get(at) & 0xff;
                    green += pixels.get(at + 1) & 0xff;
                    blue += pixels.get(at + 2) & 0xff;
                    count++;
                }
            }
        }
        out[0] = red / (255f * count);
        out[1] = green / (255f * count);
        out[2] = blue / (255f * count);
        out[3] = RED * out[0] + GREEN * out[1] + BLUE * out[2];
        return true;
    }

    /**
     * The mean colour of all a sprite paints, into out from its fifth place: red, green and
     * blue from 0 to 1. For a sheet that stands in for the whole of a sprite seen from far
     * enough that its lines are lost in it, a roof's tiles with the dark between them. After
     * usualColour has said the rectangle has something painted in it.
     */
    static void meanColour(ByteBuffer pixels, int width, int left, int top, int right, int bottom, float[] out) {
        long red = 0, green = 0, blue = 0;
        int count = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int at = (y * width + x) * 4;
                if ((pixels.get(at + 3) & 0xff) >= PAINTED) {
                    red += pixels.get(at) & 0xff;
                    green += pixels.get(at + 1) & 0xff;
                    blue += pixels.get(at + 2) & 0xff;
                    count++;
                }
            }
        }
        out[MEAN] = red / (255f * count);
        out[MEAN + 1] = green / (255f * count);
        out[MEAN + 2] = blue / (255f * count);
    }

    /** Where in what SpriteColours keeps of a sprite its mean colour starts: after its usual colour and brightness. */
    static final int MEAN = 4;

    private static int band(ByteBuffer pixels, int at) {
        float bright = RED * (pixels.get(at) & 0xff) + GREEN * (pixels.get(at + 1) & 0xff) + BLUE * (pixels.get(at + 2) & 0xff);
        return Math.min((int) (bright * BANDS / 256f), BANDS - 1);
    }
}
