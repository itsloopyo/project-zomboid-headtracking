package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.textures.Texture;

/**
 * The stone or brick faced on a fireplace's chimney breast, fixtures_fireplaces_01_8 to 13, 16 to 18, 24 to 29 and 35 to
 * 37, laid by the picture as the masonry of a wall is (see facBrick and facStone in the world's shader) in place of the
 * sprite stretched over the facing's slab, a few dots to a stone. Masonry reads a wall's courses off its sprite, and
 * these are too short for that and the stone too irregular: which sprite is stone and which brick goes by its place on
 * the sheet, and only the colours are read. The stone's joints are darker than its faces and the bricks' mortar is
 * lighter than the bricks. Read with the sprite's colours: see SpriteColours.
 */
final class HearthFacings {
    static final String SHEET = "fixtures_fireplaces_01_";
    /** A dot this much brighter than the usual colour, or darker for stone, from 0 to 1, is a joint. */
    private static final float JOINT_APART = 0.08f;
    /** A dot is part of the facing from this much cover up, of 255. */
    private static final int PAINTED = 128;
    private static final float RED = 0.299f, GREEN = 0.587f, BLUE = 0.114f;

    /** For each facing's sprite read: its key as WallSides.laid gives it, or 0 for one shown as the game painted it. */
    private static final ConcurrentHashMap<Texture, Integer> known = new ConcurrentHashMap<>();

    private HearthFacings() {}

    /** How a facing at that place on the sheet is laid, WallSides.BRICK or STONE, or 0 for a place that is no facing. */
    static int way(int place) {
        return place >= 8 && place <= 10 || place >= 27 && place <= 29 ? WallSides.STONE
                : place >= 11 && place <= 13 || place >= 16 && place <= 18 || place >= 24 && place <= 26 || place >= 35 && place <= 37 ? WallSides.BRICK : 0;
    }

    /** How the picture lays a facing's sprite, as WallSides.laid gives it, 0 for as painted. Null until a frame has been drawn since it was first asked for. */
    static Integer of(Texture texture) {
        Integer laid = known.get(texture);
        if (laid == null) {
            SpriteColours.of(texture);
        }
        return laid;
    }

    /** Reads the sprite where it is a facing: see Masonry.read. */
    static void read(Texture texture, ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        int place = texture.getName() == null ? -1 : Sheets.place(texture.getName(), SHEET);
        int way = place < 0 ? 0 : way(place);
        if (way != 0) {
            known.put(texture, measure(sheet, width, left, top, right, bottom, way));
        }
    }

    /** As of(), for the rectangle of a sheet a facing's sprite is, laid the given way. */
    static int measure(ByteBuffer sheet, int width, int left, int top, int right, int bottom, int way) {
        float[] usual = new float[4];
        if (!WallSides.usualColour(sheet, width, left, top, right, bottom, usual)) {
            return 0;
        }
        float sign = way == WallSides.STONE ? -1f : 1f;
        long red = 0, green = 0, blue = 0;
        int count = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int at = (y * width + x) * 4;
                if ((sheet.get(at + 3) & 0xff) < PAINTED) {
                    continue;
                }
                int r = sheet.get(at) & 0xff, g = sheet.get(at + 1) & 0xff, b = sheet.get(at + 2) & 0xff;
                if (sign * ((RED * r + GREEN * g + BLUE * b) / 255f - usual[3]) > JOINT_APART) {
                    red += r;
                    green += g;
                    blue += b;
                    count++;
                }
            }
        }
        if (count == 0) {
            return 0;
        }
        return WallSides.laid(usual[0], usual[1], usual[2], red / (255f * count), green / (255f * count), blue / (255f * count), way);
    }
}
