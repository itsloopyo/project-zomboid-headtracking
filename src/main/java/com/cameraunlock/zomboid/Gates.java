package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.Arrays;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * The gates in the map's fences, read off their sprites and built as the fence beside them is.
 * A gate of boards (the game's WoodGateSmall and WoodGate) is a leaf of boards on ledges, built
 * as a door of boards is. A gate of chain link is the fence's wire in a frame of the fence's
 * galvanised rail, and a farm gate is its rails and uprights of tube in the colour its sprite
 * paints. A post the sprite paints at an end of the gate stands as a post. The game's iron,
 * branch and pole gates, and the log gates, stay their sprites. With the mod's buildings off every
 * gate is the game's sprite.
 *
 * The sprite is read as a door's is (Doors), laid flat on its edge: the post is the run of dots
 * at an end of what it paints that stands higher than the leaf, the leaf is the rest, its rails
 * are the rows painted nearly all the way across it, and its uprights the columns painted nearly
 * all the way up it.
 */
final class Gates {
    private Gates() {}

    static final int NONE = 0, BOARDS = 1, LINK = 2, PIPES = 3;

    /** How much higher than the leaf's head a run of dots at an end of the sprite has to stand to be its post, in levels: 3 cm. */
    static final float POST_OVER = 0.012f;
    /** The widest a post is read, as a share of the square's edge: wider, it is the leaf's own end standing up. */
    static final float POST_WIDEST = 0.3f;
    /** How much of a row across the leaf, or of a column up it, has to be painted for it to be a rail, or an upright. */
    static final float FULL = 0.8f;
    /** The narrowest a leaf is, as a share of the square's edge. */
    static final float NARROWEST = 0.2f;

    private static final String SHEET = "fixtures_doors_fences_01_";

    /** The kind of gate a sprite on the sheet of fence gates is, by its number there and the sound the game opens it with: see the class. */
    static int of(String name, String doorSound) {
        int place = name == null ? -1 : Sheets.place(name, SHEET);
        if (place < 0) {
            return NONE;
        }
        if ("WoodGateSmall".equals(doorSound) || "WoodGate".equals(doorSound)) {
            return BOARDS;
        }
        if ("FarmGate".equals(doorSound)) {
            return PIPES;
        }
        // Read off the sheet: the small gate of 16 to 19, the double gates of 64 to 79 and the tall one of 128 to 131 are
        // chain link. The game's other MetalGate sprites are branches lashed together (24 to 29, 80 to 95).
        return place >= 16 && place <= 19 || place >= 64 && place <= 79 || place >= 128 && place <= 131 ? LINK : NONE;
    }

    /** Where along its edge a shot crosses a gate the mod builds: its leaf, its post, or the rest of the edge, which the gate leaves open. */
    static final int LEAF = 0, POST = 1, OPEN = 2;

    /**
     * The gate the mod builds on a square's north or west edge, shut or open, or null: none with the mod's buildings
     * off, and none where a wall of its own stands on the edge as well.
     */
    static IsoObject builtOn(IsoGridSquare square, boolean north) {
        if (!Own.buildings()) {
            return null;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            IsoSprite sprite = object.getSprite();
            if (!DoorGlass.isDoor(object) || sprite == null || (object instanceof IsoDoor door ? door.getNorth() : ((IsoThumpable) object).getNorth()) != north
                    || of(sprite.getName(), sprite.getProperties().get("DoorSound")) == NONE) {
                continue;
            }
            IsoObject wall = WorldScene.wallOn(square, north);
            return wall == null || wall == object ? object : null;
        }
        return null;
    }

    /** Where along its edge a shot crossing a gate's edge `along` it meets it: see met(Doors.Shape, boolean, float). */
    static int met(IsoObject gate, boolean north, float along) {
        IsoSprite shut = WorldScene.shutSprite(gate);
        boolean open = gate instanceof IsoDoor door ? door.IsOpen() : ((IsoThumpable) gate).IsOpen();
        return met(shut == null ? null : Doors.known(shut.getTextureForCurrentFrame(gate.getDir()), north), open, along);
    }

    /**
     * Where along its edge a shot crossing it `along` it meets a gate whose shut sprite reads as shut: its post where
     * the sprite paints one, standing whether the gate is shut or open; its leaf shut; the rest of the edge, open. A
     * gate not measured yet is its leaf all along the edge shut, as a door is (DoorGlass.onLeaf), and open all along it open.
     */
    static int met(Doors.Shape shut, boolean open, float along) {
        if (shut == null) {
            return open ? OPEN : LEAF;
        }
        if (along >= shut.postStart && along <= shut.postEnd) {
            return POST;
        }
        return !open && along >= shut.leaf.start && along <= shut.leaf.end ? LEAF : OPEN;
    }

    /** What a gate's post is made of, as Penetration and Impacts name it: a chain link or farm gate's steel, a wooden gate's wood. */
    static String postMaterial(IsoObject gate) {
        IsoSprite sprite = gate.getSprite();
        return of(sprite.getName(), sprite.getProperties().get("DoorSound")) == BOARDS ? "Wood" : "Metal_Light";
    }

    /**
     * Whether the post a chain link gate's sprite paints is the fence's: the small and the tall gate, whose wire the game
     * names bars, stand in the fence as a length of it does, and the fence stands its own post at the corner it shares
     * with them (WallBuilder.addChainLink). The double gates are wire, and the fence leaves their posts to them.
     */
    static boolean fencePosts(String name) {
        int place = name == null ? -1 : Sheets.place(name, SHEET);
        return place >= 16 && place <= 19 || place >= 128 && place <= 131;
    }

    /**
     * Reads a gate off its sprite laid flat on its edge (Windows.laid): dots holds rows wide dots long, high of them,
     * a level's height, the square's across of them in each row. The post, where there is one, is cleared from dots,
     * so what is read of the leaf after this is the leaf's own. The leaf comes back as a window's outline is, in
     * shares of the row and levels; its post, rails and uprights go into parts. Null where too little is painted to
     * be a leaf.
     */
    static Windows.Shape measure(ByteBuffer sheet, byte[] dots, int[] at, int wide, int high, int across, Doors.Shape parts) {
        // How high each column is painted at all, and painted solid: the post is told by its solid top, as the leaf's head
        // is, since a gate's wire and the edge of a board are painted half seen through.
        int[] top = new int[wide], solid = new int[wide], bottom = new int[wide];
        int first = -1, last = -1;
        for (int column = 0; column < wide; column++) {
            top[column] = solid[column] = -1;
            for (int row = 0; row < high; row++) {
                byte dot = dots[row * wide + column];
                if (dot != Windows.EMPTY) {
                    top[column] = top[column] < 0 ? row : top[column];
                    bottom[column] = row;
                }
                if (dot == Windows.FRAME && solid[column] < 0) {
                    solid[column] = row;
                }
            }
            if (top[column] >= 0) {
                first = first < 0 ? column : first;
                last = column;
            }
        }
        if (first < 0) {
            return null;
        }
        int head = medianTop(solid, first, last);
        if (head < 0) {
            return null;
        }
        int above = head - Math.round(POST_OVER * high), widest = Math.round(POST_WIDEST * across);
        int postFrom = -1, postTo = -1;
        int run = postRun(solid, first, last, 1, above);
        if (run > 0 && run <= widest) {
            postFrom = first;
            postTo = first + run;
        } else {
            run = postRun(solid, last, first, -1, above);
            if (run > 0 && run <= widest) {
                postFrom = last + 1 - run;
                postTo = last + 1;
            }
        }
        if (postFrom >= 0) {
            int postTop = high;
            long red = 0, green = 0, blue = 0;
            int count = 0;
            for (int column = postFrom; column < postTo; column++) {
                postTop = Math.min(postTop, solid[column]);
                for (int row = 0; row < high; row++) {
                    int dot = row * wide + column;
                    if (dots[dot] == Windows.FRAME) {
                        red += sheet.get(at[dot]) & 0xff;
                        green += sheet.get(at[dot] + 1) & 0xff;
                        blue += sheet.get(at[dot] + 2) & 0xff;
                        count++;
                    }
                    dots[dot] = Windows.EMPTY;
                }
                top[column] = solid[column] = -1;
            }
            parts.postStart = (float) postFrom / wide;
            parts.postEnd = (float) postTo / wide;
            parts.postTop = 1f - (float) postTop / high;
            parts.postRed = red / (255f * count);
            parts.postGreen = green / (255f * count);
            parts.postBlue = blue / (255f * count);
        }
        while (first <= last && top[first] < 0) {
            first++;
        }
        while (last >= first && top[last] < 0) {
            last--;
        }
        if (last - first + 1 < NARROWEST * across) {
            return null;
        }
        int headRow = medianTop(solid, first, last), footRow = 0;
        if (headRow < 0) {
            return null;
        }
        for (int column = first; column <= last; column++) {
            footRow = top[column] >= 0 ? Math.max(footRow, bottom[column]) : footRow;
        }
        Windows.Shape leaf = new Windows.Shape();
        leaf.start = (float) first / wide;
        leaf.end = (float) (last + 1) / wide;
        leaf.head = 1f - (float) headRow / high;
        leaf.foot = 1f - (float) (footRow + 1) / high;
        int columns = last - first + 1, rows = footRow - headRow + 1;
        float[] rails = new float[2 * rows];
        int railCount = 0;
        for (int row = headRow, from = -1; row <= footRow + 1; row++) {
            int painted = 0;
            for (int column = first; row <= footRow && column <= last; column++) {
                painted += dots[row * wide + column] != Windows.EMPTY ? 1 : 0;
            }
            boolean full = row <= footRow && painted >= FULL * columns;
            if (full && from < 0) {
                from = row;
            } else if (!full && from >= 0) {
                rails[railCount++] = 1f - (float) row / high;
                rails[railCount++] = 1f - (float) from / high;
                from = -1;
            }
        }
        float[] stiles = new float[2 * columns];
        int stileCount = 0;
        for (int column = first, from = -1; column <= last + 1; column++) {
            int painted = 0;
            for (int row = headRow; column <= last && row <= footRow; row++) {
                painted += dots[row * wide + column] != Windows.EMPTY ? 1 : 0;
            }
            boolean full = column <= last && painted >= FULL * rows;
            if (full && from < 0) {
                from = column;
            } else if (!full && from >= 0) {
                stiles[stileCount++] = (float) from / wide;
                stiles[stileCount++] = (float) column / wide;
                from = -1;
            }
        }
        parts.rails = Arrays.copyOf(rails, railCount);
        parts.stiles = Arrays.copyOf(stiles, stileCount);
        return leaf;
    }

    /** The row most of the columns from first to last that are painted solid at all are painted solid from, their median top; -1 where none is. */
    private static int medianTop(int[] top, int first, int last) {
        int[] tops = new int[last - first + 1];
        int count = 0;
        for (int column = first; column <= last; column++) {
            if (top[column] >= 0) {
                tops[count++] = top[column];
            }
        }
        if (count == 0) {
            return -1;
        }
        Arrays.sort(tops, 0, count);
        return tops[count / 2];
    }

    /** How many columns from `from`, stepping by step towards `to`, are painted solid from higher than row `above`: the post's run, or 0. */
    private static int postRun(int[] top, int from, int to, int step, int above) {
        int run = 0;
        for (int column = from; column != to + step && top[column] >= 0 && top[column] < above; column += step) {
            run++;
        }
        return run;
    }
}
