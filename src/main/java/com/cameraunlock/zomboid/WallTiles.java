package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.textures.Texture;

/**
 * How a tiled wall is laid, read off its sprite stood square on its wall.
 *
 * The game paints a bathroom's wall as courses of tiles sixty dots to a wall's length, each
 * joint a dark dot wide with a pale one beside it, and over the tiles, or under them, paint or a
 * strip of trim. Magnified to a wall a pace away a joint is a smear a finger wide. But the
 * sprite only has to say where its joints are and what colour each part of the wall is: up the
 * wall it is bands, each paint or tiles, with thin strips between them; across a band of tiles
 * the joints are dark columns a fixed step apart, and its courses the thin lines in it between
 * rows of one colour. The picture builds the wall as those, each tile and each joint a face of
 * its own with a straight edge.
 *
 * A course with a figure on it, a row of tiles with a flower or a diamond on each, keeps the
 * sprite's own dots inside its tiles. A wall with a door or a window in it says less than a
 * whole one about its courses: it is read, and its reading says so (whole), and the wall takes
 * the reading of a whole one of its sheet along the same line.
 */
final class WallTiles {
    /** The sheets whose walls are tiled. */
    private static final String[] SHEETS = {"walls_interior_bathroom"};
    /** How solid a dot is to be wall. */
    private static final int SOLID = Windows.FRAME_COVER;
    /** A run of rows of one colour no more than this share of the wall's height is a line, not a band. */
    private static final float THIN = 0.027f;
    /** Two colours no further apart than this in any of red, green and blue are one. */
    private static final float SAME = 0.06f;
    /** A column this much darker, on average, than the darker of the dots two either side of it is a joint. */
    private static final float JOINT = 0.035f;
    /** A dot this far from its band's colour is off it, and a course with more than this share of its dots off has a figure. */
    private static final float OFF = 0.12f, FIGURED = 0.008f;
    /** The share of the middle of a row that has to be wall for the row's colour to be read. */
    private static final float ROW_READ = 0.15f;
    /** Lines this near the top or the foot are the sprite's shading where the wall meets the ceiling or the floor: the band beside them runs on over them. */
    private static final float TOP_SHADE = 0.06f, FOOT_SHADE = 0.04f;
    /** A wall with this share of its middle unpainted has an opening in it. */
    private static final float OPENING = 0.003f;
    /** How far apart joints have to be, in dots, and the share of those found that have to keep to one step. */
    private static final int LEAST_STEP = 4;
    private static final float KEPT = 0.6f;

    /** One stretch of wall from foot to head, in levels. */
    static final class Band {
        float foot, head;
        float red, green, blue;
        /** Whether it is tiles, and of a band of tiles the joints' colour. */
        boolean tiled;
        float jointRed, jointGreen, jointBlue;
        /** Where along the wall each joint between two tiles is, its middle, as a share of the wall's length. */
        float[] joints = new float[0];
        /** How high each course joint is, its middle, in levels, from the foot up. */
        float[] courses = new float[0];
        /**
         * For each course from the foot up, one more than courses, whether its tiles show the
         * sprite's dots for a figure on them. Where more than a third of them have one, all do: a
         * figure set where four tiles meet straddles the course joints, and the half of it on
         * the course beside would be lost.
         */
        boolean[] figured = new boolean[1];
    }

    /** One sprite's wall: its bands from the foot up, the first starting at 0 and the last ending at 1. */
    static final class Wall {
        /** Whether the wall had no opening in it where it was read. */
        boolean whole;
        Band[] bands;
    }

    private record Asked(float originU, float originV, float scale, float left, float top, float width, float height) {}

    /** A sprite on one of the two edges: a corner's sprite stands on both, and is read for each. */
    private record Edge(Texture texture, boolean north) {}

    private static final Wall NONE = new Wall();
    private static final ConcurrentHashMap<Edge, Asked> asked = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Edge, Wall> known = new ConcurrentHashMap<>();

    private WallTiles() {}

    /** Whether a sprite of that name is of a sheet of tiled walls. */
    static boolean tiledSheet(String name) {
        if (name == null) {
            return false;
        }
        for (String sheet : SHEETS) {
            if (name.startsWith(sheet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The tiles the wall sprite paints on the north or west edge of its square. Null until a
     * frame has been drawn since it was first asked for, and for a sprite that paints no tiles
     * on that edge, or too little wall to tell.
     */
    static Wall of(Texture texture, SpriteGeometry.Frame frame, boolean north) {
        Edge edge = new Edge(texture, north);
        Wall wall = known.get(edge);
        if (wall != null) {
            return wall == NONE ? null : wall;
        }
        if (asked.putIfAbsent(edge, new Asked(frame.originU, frame.originV, frame.scale, frame.left, frame.top, frame.width, frame.height)) == null) {
            SpriteColours.readAgain(texture);
        }
        return null;
    }

    /** Whether the sprite has still to be read for that edge. */
    static boolean unread(Texture texture, boolean north) {
        return !known.containsKey(new Edge(texture, north));
    }

    /** Reads the sprite for each edge it was asked for on: see Windows.read. */
    static void read(Texture texture, ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        for (int side = 0; side < 2; side++) {
            Edge edge = new Edge(texture, side == 0);
            Asked wanted = asked.get(edge);
            if (wanted == null || known.containsKey(edge)) {
                continue;
            }
            int across = Windows.across(wanted.scale), high = Windows.high(wanted.scale);
            int[] at = new int[across * high];
            byte[] dots = Windows.laid(sheet, width, left, top, right, bottom, edge.north(), false, wanted.originU, wanted.originV, wanted.scale,
                    wanted.left, wanted.top, wanted.width, wanted.height, at);
            float[] rgb = new float[across * high * 3];
            boolean[] solid = new boolean[across * high];
            for (int i = 0; i < dots.length; i++) {
                solid[i] = dots[i] == Windows.FRAME;
                if (solid[i]) {
                    rgb[i * 3] = (sheet.get(at[i]) & 0xff) / 255f;
                    rgb[i * 3 + 1] = (sheet.get(at[i] + 1) & 0xff) / 255f;
                    rgb[i * 3 + 2] = (sheet.get(at[i] + 2) & 0xff) / 255f;
                }
            }
            Wall wall = read(rgb, solid, across, high);
            if (FirstPerson.TRACE) {
                Log.info("wall tiles " + texture.getName() + (edge.north() ? " north" : " west") + (wall == null ? " none" : (wall.whole ? " whole, " : " with an opening, ") + said(wall)));
            }
            if (wall == null) {
                known.put(edge, NONE);
                continue;
            }
            known.put(edge, wall);
        }
    }

    private static String said(Wall wall) {
        StringBuilder text = new StringBuilder();
        for (Band band : wall.bands) {
            text.append(String.format("[%.3f to %.3f %s", band.foot, band.head, band.tiled ? band.joints.length + " joints, courses " + Arrays.toString(band.courses) : "plain"));
            text.append(band.tiled ? ", figured " + Arrays.toString(band.figured) + "] " : "] ");
        }
        return text.toString();
    }

    private static float bright(float[] rgb, int at) {
        return 0.299f * rgb[at * 3] + 0.587f * rgb[at * 3 + 1] + 0.114f * rgb[at * 3 + 2];
    }

    /** The colour most of the given dots have: the mean of the fullest band of brightness and the one either side of it, as Windows.colour. */
    private static boolean modal(float[] rgb, int[] dots, int count, float[] into) {
        if (count == 0) {
            return false;
        }
        int[] bands = new int[Windows.BANDS];
        for (int k = 0; k < count; k++) {
            bands[Math.min((int) (bright(rgb, dots[k]) * Windows.BANDS), Windows.BANDS - 1)]++;
        }
        int fullest = 0;
        for (int b = 1; b < Windows.BANDS; b++) {
            fullest = bands[b] > bands[fullest] ? b : fullest;
        }
        float red = 0f, green = 0f, blue = 0f;
        int held = 0;
        for (int k = 0; k < count; k++) {
            if (Math.abs(Math.min((int) (bright(rgb, dots[k]) * Windows.BANDS), Windows.BANDS - 1) - fullest) <= 1) {
                red += rgb[dots[k] * 3];
                green += rgb[dots[k] * 3 + 1];
                blue += rgb[dots[k] * 3 + 2];
                held++;
            }
        }
        into[0] = red / held;
        into[1] = green / held;
        into[2] = blue / held;
        return true;
    }

    private static float apart(float[] a, float[] b) {
        return Math.max(Math.abs(a[0] - b[0]), Math.max(Math.abs(a[1] - b[1]), Math.abs(a[2] - b[2])));
    }

    /** A run of rows of one colour, from start to before end, top row first. */
    private static final class Run {
        int start, end;
        final float[] colour = new float[3];
        /** Pairs of rows, start and before end, of the lines across the run that are course joints. */
        final ArrayList<int[]> courses = new ArrayList<>();
    }

    /**
     * The tiles on a wall stood square: rgb holds a red, green and blue for each of across dots
     * a row, the top row first, and solid says which dots are wall. Null for a wall with no tiles
     * on it, or with too little wall to read.
     */
    static Wall read(float[] rgb, boolean[] solid, int across, int high) {
        // An opening: wall missing from its middle. Its frame is a few dots round it, which are no part of the tiles.
        int r0 = Math.round(high * 0.05f), r1 = Math.round(high * 0.97f), c0 = Math.round(across * 0.06f), c1 = Math.round(across * 0.94f);
        int holeTop = high, holeBottom = -1, holeLeft = across, holeRight = -1, holes = 0;
        for (int row = r0; row < r1; row++) {
            for (int column = c0; column < c1; column++) {
                if (!solid[row * across + column]) {
                    holes++;
                    holeTop = Math.min(holeTop, row);
                    holeBottom = Math.max(holeBottom, row);
                    holeLeft = Math.min(holeLeft, column);
                    holeRight = Math.max(holeRight, column);
                }
            }
        }
        boolean whole = holes <= OPENING * (r1 - r0) * (c1 - c0);
        if (!whole) {
            solid = solid.clone();
            int margin = Math.max(2, Math.round(across * 0.1f));
            for (int row = Math.max(holeTop - margin, 0); row <= Math.min(holeBottom + margin, high - 1); row++) {
                for (int column = Math.max(holeLeft - margin, 0); column <= Math.min(holeRight + margin, across - 1); column++) {
                    solid[row * across + column] = false;
                }
            }
        }
        // Each row's colour, over its middle, where the sprite's corner post and the wall's end are not.
        int inner0 = across / 8, inner1 = across - across / 8;
        int[] dots = new int[across];
        float[][] rows = new float[high][];
        for (int row = 0; row < high; row++) {
            int count = 0;
            for (int column = inner0; column < inner1; column++) {
                if (solid[row * across + column]) {
                    dots[count++] = row * across + column;
                }
            }
            float[] colour = new float[3];
            rows[row] = count > ROW_READ * (inner1 - inner0) && modal(rgb, dots, count, colour) ? colour : null;
        }
        // Runs of rows of one colour. A thin run is a line; a line between two runs of one colour is a course joint in one band.
        ArrayList<Run> bands = new ArrayList<>();
        ArrayList<Run> lines = new ArrayList<>();
        int thin = Math.max(1, Math.round(THIN * high));
        int row = 0;
        while (row < high) {
            if (rows[row] == null) {
                row++;
                continue;
            }
            Run run = new Run();
            run.start = row;
            System.arraycopy(rows[row], 0, run.colour, 0, 3);
            while (row < high && rows[row] != null && apart(rows[row], run.colour) <= SAME) {
                row++;
            }
            run.end = row;
            if (run.end - run.start <= thin) {
                lines.add(run);
                continue;
            }
            Run last = bands.isEmpty() ? null : bands.get(bands.size() - 1);
            if (last != null && last.end == run.start && apart(last.colour, run.colour) <= 2f * SAME) {
                last.end = run.end;
            } else if (last != null && apart(last.colour, run.colour) <= SAME && allLines(lines, last.end, run.start)) {
                last.courses.add(new int[] {last.end, run.start});
                last.end = run.end;
            } else {
                bands.add(run);
            }
        }
        if (bands.isEmpty()) {
            return null;
        }
        // The sprite's shading under the ceiling and over the floor is no strip of its own.
        if (bands.get(0).start <= Math.round(TOP_SHADE * high)) {
            bands.get(0).start = 0;
        }
        Run lowest = bands.get(bands.size() - 1);
        if (lowest.end >= high - Math.round(FOOT_SHADE * high)) {
            lowest.end = high;
        }
        // Each band and each line between two bands, from the top down.
        ArrayList<Band> made = new ArrayList<>();
        boolean anyTiled = false;
        float[] colour = new float[3];
        for (int k = 0; k < bands.size(); k++) {
            Run run = bands.get(k);
            if (k > 0 && bands.get(k - 1).end < run.start) {
                // A strip of trim between two bands: the mean of what the lines between them are.
                int from = bands.get(k - 1).end, to = run.start, count = 0;
                float red = 0f, green = 0f, blue = 0f;
                for (int r = from; r < to; r++) {
                    if (rows[r] != null) {
                        red += rows[r][0];
                        green += rows[r][1];
                        blue += rows[r][2];
                        count++;
                    }
                }
                if (count == 0) {
                    return null;
                }
                Band strip = new Band();
                strip.head = 1f - (float) from / high;
                strip.foot = 1f - (float) to / high;
                strip.red = red / count;
                strip.green = green / count;
                strip.blue = blue / count;
                made.add(strip);
            }
            Band band = new Band();
            band.head = 1f - (float) run.start / high;
            band.foot = 1f - (float) run.end / high;
            band.red = run.colour[0];
            band.green = run.colour[1];
            band.blue = run.colour[2];
            int[] joints = joints(rgb, solid, across, run.start, run.end);
            if (joints != null) {
                anyTiled = true;
                band.tiled = true;
                band.joints = new float[joints.length];
                for (int j = 0; j < joints.length; j++) {
                    band.joints[j] = (joints[j] + 0.5f) / across;
                }
                band.courses = new float[run.courses.size()];
                for (int c = 0; c < band.courses.length; c++) {
                    int[] line = run.courses.get(run.courses.size() - 1 - c);
                    band.courses[c] = 1f - (line[0] + line[1]) / 2f / high;
                }
                jointColour(rgb, solid, across, run.start, run.end, joints, band);
                band.figured = figured(rgb, solid, across, run, joints, inner0, inner1);
            }
            made.add(band);
        }
        if (!anyTiled) {
            return null;
        }
        Wall wall = new Wall();
        wall.whole = whole;
        wall.bands = new Band[made.size()];
        for (int k = 0; k < made.size(); k++) {
            wall.bands[k] = made.get(made.size() - 1 - k);
        }
        // The lowest band starts at the foot and the highest ends at the top: what is between the ends and the first band read is the band's.
        wall.bands[0].foot = 0f;
        wall.bands[wall.bands.length - 1].head = 1f;
        return wall;
    }

    /** Whether every row from start to before end belongs to a line. */
    private static boolean allLines(ArrayList<Run> lines, int start, int end) {
        for (int row = start; row < end; row++) {
            boolean in = false;
            for (Run line : lines) {
                in |= row >= line.start && row < line.end;
            }
            if (!in) {
                return false;
            }
        }
        return true;
    }

    /**
     * The joints across a band of rows, as columns, every one of a fixed step from the first:
     * those found and those the step puts where the sprite has no wall to read, at its ends.
     * Null where the band has no joints that keep to one step.
     */
    static int[] joints(float[] rgb, boolean[] solid, int across, int start, int end) {
        float[] dark = new float[across];
        for (int column = 2; column < across - 2; column++) {
            float sum = 0f;
            int count = 0;
            for (int row = start; row < end; row++) {
                int at = row * across + column;
                if (solid[at] && solid[at - 2] && solid[at + 2]) {
                    // Darker than what is on both sides of it: the edge between two stripes of paint is darker than one side only.
                    sum += Math.min(bright(rgb, at - 2), bright(rgb, at + 2)) - bright(rgb, at);
                    count++;
                }
            }
            dark[column] = count >= 0.5f * (end - start) ? sum / count : 0f;
        }
        int[] found = new int[across];
        int count = 0;
        for (int column = 0; column < across; column++) {
            if (dark[column] > JOINT && (column == 0 || dark[column - 1] <= dark[column])) {
                found[count++] = column;
            }
        }
        if (count < 2) {
            return null;
        }
        // The step between two joints found side by side, and the joint the rest are counted from, that most of them keep to.
        int bestStep = 0, bestKept = 0, phase = 0;
        for (int k = 1; k < count; k++) {
            int step = found[k] - found[k - 1];
            if (step < LEAST_STEP) {
                continue;
            }
            for (int anchor = 0; anchor < count; anchor++) {
                int kept = 0;
                for (int j = 0; j < count; j++) {
                    int off = Math.floorMod(found[j] - found[anchor], step);
                    kept += off <= 1 || off >= step - 1 ? 1 : 0;
                }
                if (kept >= 2 && kept >= KEPT * count && kept > bestKept) {
                    bestStep = step;
                    bestKept = kept;
                    phase = Math.floorMod(found[anchor], step);
                }
            }
        }
        if (bestStep == 0) {
            return null;
        }
        int[] joints = new int[(across - 1 - phase) / bestStep + 1];
        for (int j = 0; j < joints.length; j++) {
            joints[j] = phase + j * bestStep;
        }
        return joints;
    }

    /** The colour of a band's joints: the mean of the dots down its joint columns. */
    private static void jointColour(float[] rgb, boolean[] solid, int across, int start, int end, int[] joints, Band band) {
        float red = 0f, green = 0f, blue = 0f;
        int count = 0;
        for (int joint : joints) {
            for (int row = start; row < end; row++) {
                int at = row * across + joint;
                if (solid[at]) {
                    red += rgb[at * 3];
                    green += rgb[at * 3 + 1];
                    blue += rgb[at * 3 + 2];
                    count++;
                }
            }
        }
        band.jointRed = count == 0 ? band.red * 0.8f : red / count;
        band.jointGreen = count == 0 ? band.green * 0.8f : green / count;
        band.jointBlue = count == 0 ? band.blue * 0.8f : blue / count;
    }

    /**
     * For each course of a band, from the foot up, whether it shows a figure: more of its dots
     * than FIGURED off the band's colour, its joints and the rows by its course lines left out.
     * All of them where more than a third do.
     */
    private static boolean[] figured(float[] rgb, boolean[] solid, int across, Run run, int[] joints, int inner0, int inner1) {
        boolean[] keep = new boolean[across];
        Arrays.fill(keep, inner0, inner1, true);
        for (int joint : joints) {
            for (int column = joint - 1; column <= joint + 1; column++) {
                if (column >= 0 && column < across) {
                    keep[column] = false;
                }
            }
        }
        int courses = run.courses.size() + 1, count = 0;
        boolean[] figured = new boolean[courses];
        float[] dot = new float[3];
        for (int c = 0; c < courses; c++) {
            int from = (c == 0 ? run.start : run.courses.get(c - 1)[1]) + 2, to = (c == courses - 1 ? run.end : run.courses.get(c)[0]) - 2;
            int off = 0, all = 0;
            for (int row = from; row < to; row++) {
                for (int column = 0; column < across; column++) {
                    int at = row * across + column;
                    if (keep[column] && solid[at]) {
                        dot[0] = rgb[at * 3];
                        dot[1] = rgb[at * 3 + 1];
                        dot[2] = rgb[at * 3 + 2];
                        off += apart(dot, run.colour) > OFF ? 1 : 0;
                        all++;
                    }
                }
            }
            // Courses are counted from the top here, and kept from the foot up.
            figured[courses - 1 - c] = all > 0 && off > FIGURED * all;
            count += figured[courses - 1 - c] ? 1 : 0;
        }
        if (3 * count > courses) {
            Arrays.fill(figured, true);
        }
        return figured;
    }
}
