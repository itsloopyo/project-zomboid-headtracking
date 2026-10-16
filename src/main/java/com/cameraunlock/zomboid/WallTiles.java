package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
 * rows of one colour. The picture builds the wall as those bands, and lays the joints on them.
 *
 * A course with a figure on it, a row of tiles with a flower or a diamond on each, keeps the
 * sprite's own dots inside its tiles. A wall with a door or a window in it says less than a
 * whole one about its courses: its reading says so (whole), and the wall takes the reading of a
 * whole one of its sheet along the same line.
 *
 * Tiles laid over a plain wall, a kitchen's splashback or a bathroom's panel, are read the same
 * way, and each tile is where the overlay paints it: a panel cut round a window or stepped down
 * beside a counter is its tiles there and nowhere else, and a tile its outline cuts is cut to it.
 *
 * Every measure in dots below is for a wall stood square 64 dots long, the game's tiles at the
 * scale it draws them in its own view; a wall read at another scale has them in proportion.
 */
final class WallTiles {
    /** The sheets whose walls are tiled, and of tiles laid over a plain wall. */
    private static final String[] SHEETS = {"walls_interior_bathroom", "walls_interior_detailing_bathroom"};
    /** How many dots long the wall the measures in dots are for is. */
    private static final float DOTS_ALONG = 64f, DOTS_UP = 192f;
    /** A run of rows of one colour no more than this share of the wall's height is a line, not a band. */
    private static final float THIN = 0.027f;
    /**
     * Two colours are one where no one of red, green and blue is further apart than this share
     * of the brighter's brightness, nor than LEAST_SAME: a course line on dark tiles is as much
     * darker than its tiles, as a share, as one on white ones.
     */
    private static final float SAME_SHARE = 0.075f, LEAST_SAME = 0.02f;
    /** A column this much darker, on average, than the darker of the dots either side of it is part of a joint. */
    private static final float JOINT = 0.035f;
    /** A dot this far from its band's colour is off it. */
    private static final float OFF = 0.12f;
    /** A tile with at least this many dots off its band's colour has a figure on it, and a course with this many such tiles. */
    private static final int FIGURE_DOTS = 3, FIGURED_TILES = 2;
    /**
     * A row with more of its dots than ACROSS_A_ROW off the band's colour, some in every tile
     * it crosses and none in a run longer than DITHERED dots, is a line across it, the dithered
     * edge of a course line, and no figure: a figure is solid, a row of them where tiles meet as
     * much as a line is. So is one with more than SOLID_ROW off, whatever they are, the pale
     * bevel along a course line's edge.
     */
    private static final float ACROSS_A_ROW = 0.33f, SOLID_ROW = 0.6f;
    private static final int DITHERED = 2;
    /** The share of the middle of a row that has to be wall for the row's colour to be read. */
    private static final float ROW_READ = 0.15f;
    /** Lines this near the top or the foot are the sprite's shading where the wall meets the ceiling or the floor: the band beside them runs on over them. */
    private static final float TOP_SHADE = 0.06f, FOOT_SHADE = 0.04f;
    /** A wall with this share of its middle unpainted has an opening in it. */
    private static final float OPENING = 0.003f;
    /** The share of a tile's place an overlay has to paint for the tile to be there, and of a line of dots across it for the line to be part of it. */
    private static final float TILE_PAINTED = 0.25f, LINE_PAINTED = 0.5f;
    /**
     * Over a wall, lines between two runs of one colour more than this many times THIN thick are
     * no course joint: a border of rosettes the sprite draws as short runs of colour, 23 of 192
     * rows in walls_interior_detailing_bathroom_01's, is not.
     */
    private static final int JOINT_ROWS = 3;
    /** A strip with more than this share of its dots off its own colour shows the sprite's dots: a row of figured tiles too thin to have joints of its own read. */
    private static final float STRIP_FIGURED = 0.15f;
    /**
     * Over a wall, a run of at least PATTERN_RUN bands and strips each no taller than PATTERN_ROW
     * levels, one of them with a figure, is a pattern laid over the whole of it (a harlequin, a
     * mosaic of coloured squares): the rows of a diagonal or a mixed pattern read as bands of their
     * own, and their joints as no grid. It shows the overlay's own dots.
     */
    private static final float PATTERN_ROW = 0.1f;
    private static final int PATTERN_RUN = 3;
    /** How many dots short of the wall's far end the game's sprites stop: a tile painted to within this of it runs on to the end. */
    private static final int SHORT_END = 6;
    /** How far apart joints have to be, in dots, and the share of those found that have to keep to one step. */
    private static final int LEAST_STEP = 4;
    private static final float KEPT = 0.6f;

    /** One stretch of wall from foot to head, in levels. */
    static final class Band {
        float foot, head;
        float red, green, blue;
        /** Whether it is tiles, and of a band of tiles the joints' colour. A band that is no tiles is one course. */
        boolean tiled;
        float jointRed, jointGreen, jointBlue;
        /** Where along the wall each joint between two tiles is, its middle, as a share of the wall's length: phase, phase + step, and on. */
        float[] joints = new float[0];
        float step, phase;
        /** How high each course joint is, its middle, in levels, from the foot up. */
        float[] courses = new float[0];
        /**
         * For each course from the foot up, one more than courses, whether its tiles show the
         * sprite's dots for a figure on them. A figure set where four tiles meet straddles a
         * course joint, and both courses have it.
         */
        boolean[] figured = new boolean[1];
        /** What stretches made of the band, and for what halfUp: a wall is read once, and laid the same at every build of it. */
        private volatile Laid laid;
        /**
         * For tiles laid over a wall, each tile, four numbers for each, course by course from
         * the foot up and along each course from the wall's start: where along the wall it
         * starts and ends, as shares of the wall's length, and how high its foot and head are, in
         * levels. Its place between the middles of the joints round it, or the band's or the
         * wall's own edge where nothing is beside it, cut to what the overlay paints of it, and a
         * start below 0 where it paints none of it. A band that is no tiles is one. Null for a
         * wall's own bands, which are whole.
         */
        float[] cells;

        /** How many tiles a course of the band has. */
        int across() {
            return joints.length + 1;
        }

        /** Where in cells the tile of that course and that place along it starts. */
        int cell(int course, int tile) {
            return (course * across() + tile) * 4;
        }

        /** Whether the tile of that course and place is there: off the band is never. */
        boolean has(int course, int tile) {
            return course >= 0 && course <= courses.length && tile >= 0 && tile < across() && cells[cell(course, tile)] >= 0f;
        }
    }

    private record Laid(float halfUp, ArrayList<Stretch> stretches) {}

    /**
     * Courses of a band laid as one face, first to last from the foot up, from low to high in
     * levels. Plain, the picture lays their joints, its texture coordinates up the face footV at
     * low and perLevel more each level higher: whole numbers at each course line inside or on
     * its edge, and never near one at an edge that is the band's own. With a figure, one course
     * that shows the sprite's dots, laid tile by tile.
     */
    static final class Stretch {
        int first, last;
        float low, high, footV, perLevel;
        boolean figured;
    }

    /** How far apart in levels two course lines may be from where an even step puts them, and how near a whole number a band's own edge may come. */
    private static final float EVEN = 0.008f, CLEAR_OF_A_LINE = 0.2f;

    /**
     * The stretches a band of tiles is laid as, from the foot up: each run of plain courses whose
     * lines are evenly apart one face, each course with a figure one of its own. A course line
     * between a plain course and a course with a figure is drawn whole on the plain side: that
     * face runs on halfUp past it, and the course with the figure starts halfUp past it.
     * The same list at every asking for the same halfUp: it is not to be changed.
     */
    static ArrayList<Stretch> stretches(Band band, float halfUp) {
        Laid was = band.laid;
        if (was != null && was.halfUp() == halfUp) {
            return was.stretches();
        }
        ArrayList<Stretch> laid = layStretches(band, halfUp);
        band.laid = new Laid(halfUp, laid);
        return laid;
    }

    private static ArrayList<Stretch> layStretches(Band band, float halfUp) {
        ArrayList<Stretch> laid = new ArrayList<>();
        int courses = band.courses.length;
        int first = 0;
        while (first <= courses) {
            Stretch stretch = new Stretch();
            stretch.first = first;
            if (band.figured[first]) {
                stretch.last = first;
                stretch.figured = true;
                stretch.low = first == 0 ? band.foot : band.courses[first - 1] + halfUp;
                stretch.high = first == courses ? band.head : band.courses[first] - halfUp;
                laid.add(stretch);
                first++;
                continue;
            }
            int last = first;
            while (last < courses && !band.figured[last + 1] && even(band, first, last + 1)) {
                last++;
            }
            // A run whose own edge, at the band's foot or head, comes near a whole number gives up the course at that edge to a face of its own.
            while (last > first && !clear(band, first, last)) {
                last = first == 0 && !clearAt(band, first, last, band.foot) ? first : last - 1;
            }
            stretch.last = last;
            stretch.low = first == 0 ? band.foot : band.courses[first - 1] - (band.figured[first - 1] ? halfUp : 0f);
            stretch.high = last == courses ? band.head : band.courses[last] + (last < courses && band.figured[last + 1] ? halfUp : 0f);
            float[] lines = lines(band, first, last);
            if (lines.length >= 2) {
                float step = (lines[lines.length - 1] - lines[0]) / (lines.length - 1);
                stretch.perLevel = 1f / step;
                stretch.footV = (stretch.low - lines[0]) / step;
            } else if (lines.length == 1) {
                // One line: it is 0, and the far edges are within half a step of it.
                float reach = 2f * Math.max(lines[0] - (first == 0 ? band.foot : band.courses[first - 1]), (last == courses ? band.head : band.courses[last]) - lines[0]);
                stretch.perLevel = 1f / reach;
                stretch.footV = (stretch.low - lines[0]) / reach;
            } else {
                // No line: a quarter to three quarters of a step, and none drawn.
                float bottom = first == 0 ? band.foot : band.courses[first - 1], top = last == courses ? band.head : band.courses[last];
                stretch.perLevel = 0.5f / (top - bottom);
                stretch.footV = 0.25f + (stretch.low - bottom) * stretch.perLevel;
            }
            laid.add(stretch);
            first = last + 1;
        }
        return laid;
    }

    /** The heights of the course lines on and inside the courses from first to last, from the foot up. */
    private static float[] lines(Band band, int first, int last) {
        int courses = band.courses.length;
        int from = first == 0 ? 0 : first - 1, to = last == courses ? courses - 1 : last;
        return to < from ? new float[0] : Arrays.copyOfRange(band.courses, from, to + 1);
    }

    /** Whether the course lines on and inside the courses from first to last are evenly apart. */
    private static boolean even(Band band, int first, int last) {
        float[] lines = lines(band, first, last);
        if (lines.length < 3) {
            return true;
        }
        float step = (lines[lines.length - 1] - lines[0]) / (lines.length - 1);
        for (int k = 1; k < lines.length - 1; k++) {
            if (Math.abs(lines[k] - (lines[0] + k * step)) > EVEN) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether neither of the band's own edges that the courses from first to last reach comes
     * near a whole number laid evenly from their lines. An edge a step or more past the outermost
     * line has the lines between drawn on: a whole wall's band runs on through the game's shading
     * at the ceiling and the floor, and the game's bathroom walls are tiled up to the ceiling
     * with the shade painted over their top rows. Tiles over a wall are laid a course at a time
     * (overlayFaces) and never by this.
     */
    private static boolean clear(Band band, int first, int last) {
        return (first > 0 || clearAt(band, first, last, band.foot)) && (last < band.courses.length || clearAt(band, first, last, band.head));
    }

    private static boolean clearAt(Band band, int first, int last, float edge) {
        float[] lines = lines(band, first, last);
        if (lines.length < 2) {
            return true;
        }
        float v = (edge - lines[0]) / ((lines[lines.length - 1] - lines[0]) / (lines.length - 1));
        return Math.abs(v - Math.round(v)) >= CLEAR_OF_A_LINE;
    }

    /** One sprite's wall: its bands from the foot up, for a wall the first starting at 0 and the last ending at 1. */
    static final class Wall {
        /** Whether the wall had no opening in it where it was read. A wall with one may have no bands read. */
        boolean whole;
        Band[] bands;
    }

    private record Asked(float originU, float originV, float scale, float left, float top, float width, float height) {}

    private static final Wall NONE = new Wall();
    /**
     * By sprite, for each of the two edges, north then west, as a wall and as tiles laid over
     * one: a corner's sprite stands on both edges, and a sprite of a sheet of tiles can be a
     * wall on one square and laid over a wall on another. Each is read for each: only tiles over
     * a wall have their cells, and a wall's reading has strips and an opening an overlay's has not.
     */
    private static final List<ConcurrentHashMap<Texture, Asked>> asked = List.of(new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    private static final List<ConcurrentHashMap<Texture, Wall>> known = List.of(new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>());

    private static int way(boolean north, boolean overlay) {
        return (north ? 0 : 1) + (overlay ? 2 : 0);
    }

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
     * The tiles the sprite paints on the north or west edge of its square, a wall or tiles laid
     * over one (overlay). Null until a frame has been drawn since it was first asked for, and
     * for a whole wall or an overlay that paints no tiles on that edge, or too little to tell.
     */
    static Wall of(Texture texture, SpriteGeometry.Frame frame, boolean north, boolean overlay) {
        int way = way(north, overlay);
        Wall wall = known.get(way).get(texture);
        if (wall != null) {
            return wall == NONE ? null : wall;
        }
        if (asked.get(way).putIfAbsent(texture, new Asked(frame.originU, frame.originV, frame.scale, frame.left, frame.top, frame.width, frame.height)) == null) {
            SpriteColours.readAgain(texture);
        }
        return null;
    }

    /** Tests only: that the sprite reads as wall on that edge, as a wall or laid over one, as though it had been read. */
    static void knownForTests(Texture texture, boolean north, boolean overlay, Wall wall) {
        known.get(way(north, overlay)).put(texture, wall == null ? NONE : wall);
    }

    /**
     * Whether a whole wall's reading can stand for a wall with an opening in it: each band the
     * wall reads of itself has, at its middle, a band of the whole wall of its colour, and tiles
     * where it has tiles. A wall that read nothing of itself takes any.
     */
    static boolean agrees(Wall own, Wall whole) {
        if (own == null) {
            return true;
        }
        for (Band band : own.bands) {
            float middle = (band.foot + band.head) / 2f;
            Band there = null;
            for (Band other : whole.bands) {
                there = other.foot <= middle && middle <= other.head ? other : there;
            }
            if (there == null) {
                return false;
            }
            float apart = Math.max(Math.abs(band.red - there.red), Math.max(Math.abs(band.green - there.green), Math.abs(band.blue - there.blue)));
            float brighter = Math.max(0.299f * band.red + 0.587f * band.green + 0.114f * band.blue, 0.299f * there.red + 0.587f * there.green + 0.114f * there.blue);
            if (band.tiled && !there.tiled || apart > 2f * Math.max(LEAST_SAME, SAME_SHARE * brighter)) {
                return false;
            }
        }
        return true;
    }

    /** Whether the sprite has still to be read for that edge, as a wall or laid over one. */
    static boolean unread(Texture texture, boolean north, boolean overlay) {
        return !known.get(way(north, overlay)).containsKey(texture);
    }

    /** Reads the sprite for each edge, and each way, it was asked for on: see Windows.read. */
    static void read(Texture texture, ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        for (int way = 0; way < 4; way++) {
            boolean north = way % 2 == 0, overlay = way >= 2;
            Asked wanted = asked.get(way).get(texture);
            if (wanted == null || known.get(way).containsKey(texture)) {
                continue;
            }
            int across = Windows.across(wanted.scale), high = Windows.high(wanted.scale);
            int[] at = new int[across * high];
            byte[] dots = Windows.laid(sheet, width, left, top, right, bottom, north, false, wanted.originU, wanted.originV, wanted.scale,
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
            Wall wall = read(rgb, solid, across, high, overlay);
            if (FirstPerson.TRACE) {
                Log.info("wall tiles " + texture.getName() + (north ? " north" : " west") + (overlay ? " over a wall" : "") + (wall == null ? " none" : said(wall)));
            }
            known.get(way).put(texture, wall == null ? NONE : wall);
        }
    }

    /** What a reading says, for the log. */
    static String said(Wall wall) {
        StringBuilder text = new StringBuilder(wall.whole ? "whole, " : "with an opening, ");
        for (Band band : wall.bands) {
            text.append(String.format(java.util.Locale.ROOT, "[%.3f to %.3f %.2f %.2f %.2f %s", band.foot, band.head, band.red, band.green, band.blue,
                    (band.tiled ? band.joints.length + " joints, courses " + Arrays.toString(band.courses) : "plain") + ", figured " + Arrays.toString(band.figured)));
            text.append("] ");
        }
        return text.toString();
    }

    private static float bright(float[] rgb, int at) {
        return 0.299f * rgb[at * 3] + 0.587f * rgb[at * 3 + 1] + 0.114f * rgb[at * 3 + 2];
    }

    private static float bright(float[] colour) {
        return 0.299f * colour[0] + 0.587f * colour[1] + 0.114f * colour[2];
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

    /** Whether two colours are one: see SAME_SHARE. */
    static boolean same(float[] a, float[] b, float times) {
        return apart(a, b) <= times * Math.max(LEAST_SAME, SAME_SHARE * Math.max(bright(a), bright(b)));
    }

    /** A run of rows of one colour, from start to before end, top row first, and the rows it was read over before its ends were run on over the shading. */
    private static final class Run {
        int start, end, readStart, readEnd;
        final float[] colour = new float[3];
        /** Pairs of rows, start and before end, of the lines across the run that are course joints. */
        final ArrayList<int[]> courses = new ArrayList<>();
    }

    /** As read, for a wall. */
    static Wall read(float[] rgb, boolean[] solid, int across, int high) {
        return read(rgb, solid, across, high, false);
    }

    /**
     * The tiles on a wall stood square, or laid over one (overlay): rgb holds a red, green and
     * blue for each of across dots a row, the top row first, and solid says which dots are
     * painted. Null for a whole wall or an overlay with no tiles on it, or with too little
     * painted to read. A wall with an opening is given whatever was read of it, bands or none,
     * and says it is not whole; tiles laid over a wall have no opening of their own, and what
     * they leave bare is the wall's.
     */
    static Wall read(float[] rgb, boolean[] solid, int across, int high, boolean overlay) {
        float alongScale = across / DOTS_ALONG, upScale = high / DOTS_UP;
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
        boolean whole = overlay || holes <= OPENING * (r1 - r0) * (c1 - c0);
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
            while (row < high && rows[row] != null && same(rows[row], run.colour, 1f)) {
                row++;
            }
            run.end = row;
            if (run.end - run.start <= thin) {
                lines.add(run);
                continue;
            }
            Run last = bands.isEmpty() ? null : bands.get(bands.size() - 1);
            if (last != null && last.end == run.start && same(last.colour, run.colour, 2f)) {
                last.end = run.end;
            } else if (last != null && same(last.colour, run.colour, 1f) && allLines(lines, last.end, run.start)
                    && !(overlay && (run.start - last.end > JOINT_ROWS * thin || figureBetween(rgb, solid, across, last.end, run.start, last.colour, lines)))) {
                last.courses.add(new int[] {last.end, run.start});
                last.end = run.end;
            } else {
                bands.add(run);
            }
        }
        if (bands.isEmpty() && whole) {
            return null;
        }
        for (Run run : bands) {
            run.readStart = run.start;
            run.readEnd = run.end;
            median(rows, run);
        }
        if (overlay) {
            bands = byGrid(bands, rgb, solid, across, alongScale);
        }
        // The sprite's shading under the ceiling and over the floor is no strip of its own.
        if (!bands.isEmpty() && bands.get(0).start <= Math.round(TOP_SHADE * high)) {
            bands.get(0).start = 0;
        }
        if (!bands.isEmpty() && bands.get(bands.size() - 1).end >= high - Math.round(FOOT_SHADE * high)) {
            bands.get(bands.size() - 1).end = high;
        }
        // Each band and each line between two bands, from the top down, and for each the rows it was read from, its joint columns and its course lines.
        ArrayList<Band> made = new ArrayList<>();
        ArrayList<int[]> madeRows = new ArrayList<>(), madeJoints = new ArrayList<>();
        ArrayList<ArrayList<int[]>> madeLines = new ArrayList<>();
        boolean anyTiled = false;
        // Over a wall the lines above its first band and below its last, a panel's capping and its foot, are strips of it: a wall's are its shading.
        int firstRow = 0, lastRow = high;
        if (overlay && !bands.isEmpty()) {
            while (firstRow < high && !painted(solid, across, firstRow)) {
                firstRow++;
            }
            while (lastRow > 0 && !painted(solid, across, lastRow - 1)) {
                lastRow--;
            }
            strip(made, madeRows, madeJoints, madeLines, rows, overlay, rgb, solid, across, firstRow, bands.get(0).start, high);
        }
        for (int k = 0; k < bands.size(); k++) {
            Run run = bands.get(k);
            if (k > 0 && bands.get(k - 1).end < run.start) {
                strip(made, madeRows, madeJoints, madeLines, rows, overlay, rgb, solid, across, bands.get(k - 1).end, run.start, high);
            }
            Band band = new Band();
            band.head = 1f - (float) run.start / high;
            band.foot = 1f - (float) run.end / high;
            band.red = run.colour[0];
            band.green = run.colour[1];
            band.blue = run.colour[2];
            int[] grid = grid(rgb, solid, across, run.readStart, run.readEnd, alongScale);
            int[] columns = new int[0];
            if (grid != null) {
                anyTiled = true;
                band.tiled = true;
                int phase = grid[0], step = grid[1];
                band.phase = (phase + 0.5f) / across;
                band.step = (float) step / across;
                band.joints = new float[(across - 1 - phase) / step + 1];
                columns = new int[band.joints.length];
                for (int j = 0; j < band.joints.length; j++) {
                    columns[j] = phase + j * step;
                    band.joints[j] = band.phase + j * band.step;
                }
                band.courses = new float[run.courses.size()];
                for (int c = 0; c < band.courses.length; c++) {
                    int[] line = run.courses.get(run.courses.size() - 1 - c);
                    band.courses[c] = 1f - (line[0] + line[1]) / 2f / high;
                }
                jointColour(rgb, solid, across, run.readStart, run.readEnd, columns, band);
                band.figured = figured(rgb, solid, across, high, run, columns, band, inner0, inner1, alongScale, upScale, false);
            } else {
                // A band of paint with a figure on it, a row of tiles whose joints the figure hides, shows the sprite's dots too. Over a wall
                // a band with no joints between bands of tiles is a pattern however faint (quilting drawn a shade off white): any dot not its colour counts.
                band.figured = figured(rgb, solid, across, high, run, new int[0], band, inner0, inner1, alongScale, upScale, overlay);
            }
            made.add(band);
            madeRows.add(new int[] {run.start, run.end});
            madeJoints.add(columns);
            madeLines.add(grid == null ? new ArrayList<>() : run.courses);
        }
        if (overlay && !bands.isEmpty()) {
            strip(made, madeRows, madeJoints, madeLines, rows, overlay, rgb, solid, across, bands.get(bands.size() - 1).end, lastRow, high);
            patterns(made, madeRows, madeJoints, madeLines, rgb, solid, across);
        }
        if (!anyTiled && whole) {
            return null;
        }
        Wall wall = new Wall();
        wall.whole = whole;
        wall.bands = new Band[made.size()];
        for (int k = 0; k < made.size(); k++) {
            wall.bands[k] = made.get(made.size() - 1 - k);
        }
        if (whole && !overlay) {
            // The lowest band starts at the foot and the highest ends at the top: what is between the ends and the first band read is the band's.
            wall.bands[0].foot = 0f;
            wall.bands[wall.bands.length - 1].head = 1f;
        }
        if (overlay) {
            for (int k = 0; k < made.size(); k++) {
                cells(made.get(k), madeRows.get(k), madeJoints.get(k), madeLines.get(k), solid, across, high, alongScale);
            }
        }
        return wall;
    }

    /**
     * A run's colour, each of red, green and blue the middle one of its rows' that are not its
     * course lines: its first row's, which it was found by, can carry the edge of a figure next to it.
     */
    private static void median(float[][] rows, Run run) {
        float[][] taken = new float[3][run.end - run.start];
        int count = 0;
        for (int row = run.start; row < run.end; row++) {
            boolean line = false;
            for (int[] course : run.courses) {
                line |= row >= course[0] && row < course[1];
            }
            if (rows[row] != null && !line) {
                for (int k = 0; k < 3; k++) {
                    taken[k][count] = rows[row][k];
                }
                count++;
            }
        }
        for (int k = 0; k < 3; k++) {
            float[] values = Arrays.copyOf(taken[k], count);
            Arrays.sort(values);
            run.colour[k] = values[count / 2];
        }
    }

    /**
     * Whether the rows from from to before to, lines between two runs of colour, hold a figure:
     * more than STRIP_FIGURED of their dots neither that colour nor the colour of any of the
     * lines in them. Course joints and their bevels are those colours; a row of figured tiles too
     * thin to read as a band, a border of rosettes, is not.
     */
    private static boolean figureBetween(float[] rgb, boolean[] solid, int across, int from, int to, float[] colour, ArrayList<Run> lines) {
        float[] dot = new float[3];
        int dots = 0, off = 0;
        for (int row = from; row < to; row++) {
            for (int column = 0; column < across; column++) {
                int at = row * across + column;
                if (!solid[at]) {
                    continue;
                }
                dot[0] = rgb[at * 3];
                dot[1] = rgb[at * 3 + 1];
                dot[2] = rgb[at * 3 + 2];
                boolean apartFromAll = apart(dot, colour) > OFF;
                for (Run line : lines) {
                    apartFromAll &= line.end <= from || line.start >= to || apart(dot, line.colour) > OFF;
                }
                dots++;
                off += apartFromAll ? 1 : 0;
            }
        }
        return off > STRIP_FIGURED * dots;
    }

    /**
     * Each run cut at the course lines where its joints change: a panel of small tiles over
     * large ones of the same colour, or of quilting over tiles, is one run of colour with two
     * grids, or one and none. A course's joints are read over its own rows; the courses next to
     * one another with the same joints, or none, are one run, and the course line between two
     * runs is left a line between them.
     */
    private static ArrayList<Run> byGrid(ArrayList<Run> runs, float[] rgb, boolean[] solid, int across, float alongScale) {
        ArrayList<Run> out = new ArrayList<>();
        int slack = Math.max(1, Math.round(alongScale));
        for (Run run : runs) {
            int courses = run.courses.size() + 1;
            int[][] grids = new int[courses][];
            for (int c = 0; c < courses; c++) {
                int from = c == 0 ? run.readStart : run.courses.get(c - 1)[1], to = c == courses - 1 ? run.readEnd : run.courses.get(c)[0];
                grids[c] = grid(rgb, solid, across, from, to, alongScale);
            }
            int first = 0;
            for (int c = 1; c <= courses; c++) {
                if (c < courses && sameGrid(grids[c], grids[first], slack)) {
                    continue;
                }
                Run part = new Run();
                System.arraycopy(run.colour, 0, part.colour, 0, 3);
                part.start = first == 0 ? run.start : run.courses.get(first - 1)[1];
                part.end = c == courses ? run.end : run.courses.get(c - 1)[0];
                part.readStart = first == 0 ? run.readStart : part.start;
                part.readEnd = c == courses ? run.readEnd : part.end;
                for (int line = first; line < c - 1; line++) {
                    part.courses.add(run.courses.get(line));
                }
                out.add(part);
                first = c;
            }
        }
        return out;
    }

    /** Whether two courses' joints, phase then step, are the same within slack dots, or both none. */
    private static boolean sameGrid(int[] a, int[] b, int slack) {
        if (a == null || b == null) {
            return a == b;
        }
        return Math.abs(a[1] - b[1]) <= slack && Math.abs(Math.floorMod(a[0] - b[0] + a[1] / 2, a[1]) - a[1] / 2) <= slack;
    }

    /**
     * A strip of trim from row from to before row to, if any of those rows is read: the mean of
     * what they are, and over a wall (overlay) with a figure where more than STRIP_FIGURED of its
     * dots are off that. Added to made with the rows it was read from, no joints and no course lines.
     */
    private static void strip(ArrayList<Band> made, ArrayList<int[]> madeRows, ArrayList<int[]> madeJoints, ArrayList<ArrayList<int[]>> madeLines,
            float[][] rows, boolean overlay, float[] rgb, boolean[] solid, int across, int from, int to, int high) {
        int count = 0;
        float red = 0f, green = 0f, blue = 0f;
        for (int r = from; r < to; r++) {
            if (overlay) {
                // Over a wall, every painted dot: rows of a pattern with no one colour most of them are no less painted, and left out they would be a hole.
                for (int at = r * across; at < (r + 1) * across; at++) {
                    if (solid[at]) {
                        red += rgb[at * 3];
                        green += rgb[at * 3 + 1];
                        blue += rgb[at * 3 + 2];
                        count++;
                    }
                }
            } else if (rows[r] != null) {
                red += rows[r][0];
                green += rows[r][1];
                blue += rows[r][2];
                count++;
            }
        }
        if (count == 0) {
            return;
        }
        Band strip = new Band();
        strip.head = 1f - (float) from / high;
        strip.foot = 1f - (float) to / high;
        strip.red = red / count;
        strip.green = green / count;
        strip.blue = blue / count;
        float[] colour = {strip.red, strip.green, strip.blue}, dot = new float[3];
        int dots = 0, off = 0;
        for (int at = from * across; overlay && at < to * across; at++) {
            if (solid[at]) {
                dot[0] = rgb[at * 3];
                dot[1] = rgb[at * 3 + 1];
                dot[2] = rgb[at * 3 + 2];
                dots++;
                off += apart(dot, colour) > OFF ? 1 : 0;
            }
        }
        strip.figured[0] = off > STRIP_FIGURED * dots;
        made.add(strip);
        madeRows.add(new int[] {from, to});
        madeJoints.add(new int[0]);
        madeLines.add(new ArrayList<>());
    }

    /** Whether a row stood square is painted along as much as a row has to be to be read: see ROW_READ. */
    private static boolean painted(boolean[] solid, int across, int row) {
        int count = 0;
        for (int at = row * across; at < (row + 1) * across; at++) {
            count += solid[at] ? 1 : 0;
        }
        return count > ROW_READ * across;
    }

    /**
     * Each run of PATTERN_RUN or more bands and strips of made, top down and touching, each no
     * taller than PATTERN_ROW and one with a figure, made one band that shows the overlay's dots:
     * see PATTERN_ROW. Its colour is the mean of its painted dots, each band and strip of it having some.
     */
    private static void patterns(ArrayList<Band> made, ArrayList<int[]> madeRows, ArrayList<int[]> madeJoints, ArrayList<ArrayList<int[]>> madeLines,
            float[] rgb, boolean[] solid, int across) {
        int k = 0;
        while (k < made.size()) {
            int last = k;
            boolean figure = false;
            while (last < made.size() && made.get(last).head - made.get(last).foot <= PATTERN_ROW
                    && (last == k || madeRows.get(last - 1)[1] == madeRows.get(last)[0])) {
                figure |= made.get(last).figured[0] || made.get(last).tiled && anyOf(made.get(last).figured);
                last++;
            }
            if (last - k < PATTERN_RUN || !figure) {
                k = Math.max(k + 1, last);
                continue;
            }
            int from = madeRows.get(k)[0], to = madeRows.get(last - 1)[1], count = 0;
            Band pattern = new Band();
            pattern.head = made.get(k).head;
            pattern.foot = made.get(last - 1).foot;
            pattern.figured[0] = true;
            for (int at = from * across; at < to * across; at++) {
                if (solid[at]) {
                    pattern.red += rgb[at * 3];
                    pattern.green += rgb[at * 3 + 1];
                    pattern.blue += rgb[at * 3 + 2];
                    count++;
                }
            }
            pattern.red /= count;
            pattern.green /= count;
            pattern.blue /= count;
            for (int i = k; i < last; i++) {
                made.remove(k);
                madeRows.remove(k);
                madeJoints.remove(k);
                madeLines.remove(k);
            }
            made.add(k, pattern);
            madeRows.add(k, new int[] {from, to});
            madeJoints.add(k, new int[0]);
            madeLines.add(k, new ArrayList<>());
            k++;
        }
    }

    private static boolean anyOf(boolean[] flags) {
        for (boolean flag : flags) {
            if (flag) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fills in the cells of a band of tiles laid over a wall: each tile's place between the
     * middles of the joints round it, cut to what of it the overlay paints. rows is where the band
     * was read, top row and the row after its last, joints its joint columns and lines its course
     * lines top down, each a first row and the row after its last, all in dots.
     */
    private static void cells(Band band, int[] rows, int[] joints, ArrayList<int[]> lines, boolean[] solid, int across, int high, float alongScale) {
        int courses = band.courses.length, tiles = joints.length + 1, shortEnd = Math.round(SHORT_END * alongScale);
        band.cells = new float[(courses + 1) * tiles * 4];
        for (int fromTop = 0; fromTop <= courses; fromTop++) {
            int course = courses - fromTop;
            // Rows of the course, inside its course lines, and how high its foot and head are where tiles meet the next course or the band ends.
            int top = fromTop == 0 ? rows[0] : lines.get(fromTop - 1)[1], bottom = fromTop == courses ? rows[1] : lines.get(fromTop)[0];
            float head = course == courses ? band.head : band.courses[course], foot = course == 0 ? band.foot : band.courses[course - 1];
            for (int tile = 0; tile < tiles; tile++) {
                int left = tile == 0 ? 0 : joints[tile - 1] + 1, right = tile == tiles - 1 ? across : joints[tile];
                // A joint read on the wall's first column leaves no tile before it: the tile after it runs from the start.
                float start = tile == 0 || tile == 1 && joints[0] == 0 ? 0f : band.joints[tile - 1], end = tile == tiles - 1 ? 1f : band.joints[tile];
                int at = band.cell(course, tile);
                band.cells[at] = start;
                band.cells[at + 1] = end;
                band.cells[at + 2] = foot;
                band.cells[at + 3] = head;
                if (tile > 0 && left >= across - shortEnd) {
                    // Wholly in the short end the sprite leaves bare: there as the tile before it is, and as high.
                    int before = band.cell(course, tile - 1);
                    band.cells[at] = band.cells[before] < 0f ? -1f : start;
                    band.cells[at + 2] = band.cells[before + 2];
                    band.cells[at + 3] = band.cells[before + 3];
                    continue;
                }
                int painted = 0;
                for (int row = top; row < bottom; row++) {
                    for (int column = left; column < right; column++) {
                        painted += solid[row * across + column] ? 1 : 0;
                    }
                }
                if (right <= left || bottom <= top || painted < TILE_PAINTED * (right - left) * (bottom - top)) {
                    band.cells[at] = -1f;
                    continue;
                }
                // The first and last column, and row, of the place that are mostly painted: up to the joint, or to the outline cut through the tile.
                int first = -1, last = -1;
                for (int column = left; column < right; column++) {
                    int count = 0;
                    for (int row = top; row < bottom; row++) {
                        count += solid[row * across + column] ? 1 : 0;
                    }
                    if (count >= LINE_PAINTED * (bottom - top)) {
                        first = first < 0 ? column : first;
                        last = column;
                    }
                }
                int highest = -1, lowest = -1;
                for (int row = top; row < bottom; row++) {
                    int count = 0;
                    for (int column = left; column < right; column++) {
                        count += solid[row * across + column] ? 1 : 0;
                    }
                    if (count >= LINE_PAINTED * (right - left)) {
                        highest = highest < 0 ? row : highest;
                        lowest = row;
                    }
                }
                if (first < 0 || highest < 0) {
                    band.cells[at] = -1f;
                    continue;
                }
                band.cells[at] = first <= left + 2 ? start : (float) first / across;
                band.cells[at + 1] = last >= right - 2 || last >= across - shortEnd ? end : (last + 1f) / across;
                band.cells[at + 2] = lowest >= bottom - 2 ? foot : 1f - (lowest + 1f) / high;
                band.cells[at + 3] = highest <= top + 1 ? head : 1f - (float) highest / high;
            }
        }
    }

    /**
     * One face of tiles laid over a wall: from start to stop along it, as shares of its length,
     * and from low to high in levels. JOINTED is one colour with its joints laid by the picture,
     * its texture coordinates up the face footV at foot and perLevel more each level higher;
     * PICTURE shows the overlay's own dots; PLAIN is one colour.
     */
    static final class Face {
        static final int JOINTED = 0, PICTURE = 1, PLAIN = 2;
        final Band band;
        final int kind;
        final float start, stop, low, high, foot, footV, perLevel;

        Face(Band band, int kind, float start, float stop, float low, float high, float foot, float footV, float perLevel) {
            this.band = band;
            this.kind = kind;
            this.start = start;
            this.stop = stop;
            this.low = low;
            this.high = high;
            this.foot = foot;
            this.footV = footV;
            this.perLevel = perLevel;
        }
    }

    /**
     * The faces tiles laid over a wall are built of: each course as runs of the tiles side by
     * side in it that are as high as each other, each run one face; where no tile is beside a
     * tile, at the overlay's outline, the face stops half a joint short of the joint's middle
     * (halfAlong, a share of the wall's length, halfUp in levels), so none is drawn there. A
     * course with a figure is each tile's own dots.
     */
    static ArrayList<Face> overlayFaces(Wall over, float halfAlong, float halfUp) {
        ArrayList<Face> faces = new ArrayList<>();
        for (Band band : over.bands) {
            int courses = band.courses.length, across = band.across();
            for (int course = 0; course <= courses; course++) {
                boolean figured = band.figured[band.tiled ? course : 0];
                // Up the course, whole numbers on the course lines that are its own edges, and none at an edge that is the band's.
                float foot = course == 0 ? band.foot : band.courses[course - 1], head = course == courses ? band.head : band.courses[course];
                boolean lineUnder = course > 0, lineOver = course < courses;
                float footV = lineUnder ? 0f : lineOver ? 0.5f : 0.25f, headV = lineOver ? 1f : lineUnder ? 0.5f : 0.75f;
                int kind = figured ? Face.PICTURE : band.tiled ? Face.JOINTED : Face.PLAIN;
                int tile = 0;
                while (tile < across) {
                    if (!band.has(course, tile)) {
                        tile++;
                        continue;
                    }
                    int at = band.cell(course, tile), last = tile;
                    while (last + 1 < across && band.has(course, last + 1) && !figured
                            && band.cells[band.cell(course, last + 1) + 2] == band.cells[at + 2] && band.cells[band.cell(course, last + 1) + 3] == band.cells[at + 3]
                            && band.cells[band.cell(course, last) + 1] == band.cells[band.cell(course, last + 1)]) {
                        last++;
                    }
                    int end = band.cell(course, last);
                    float start = band.cells[at] + (tile > 0 && !band.has(course, tile - 1) && band.cells[at] == band.joints[tile - 1] ? halfAlong : 0f);
                    float stop = band.cells[end + 1] - (last < across - 1 && !band.has(course, last + 1) && band.cells[end + 1] == band.joints[last] ? halfAlong : 0f);
                    float low = band.cells[at + 2] + (course > 0 && !band.has(course - 1, tile) && band.cells[at + 2] == foot ? halfUp : 0f);
                    float high = band.cells[at + 3] - (course < courses && !band.has(course + 1, tile) && band.cells[at + 3] == head ? halfUp : 0f);
                    if (stop > start && high > low) {
                        faces.add(new Face(band, kind, start, stop, low, high, foot, footV, (headV - footV) / (head - foot)));
                    }
                    tile = last + 1;
                }
            }
        }
        return faces;
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
     * The joints across a band of rows, as the column of the first (its phase) and the step to
     * each next: those found, and those the step puts where the sprite has no wall to read, at
     * its ends. A joint read as two or more dark columns side by side is one, at their middle.
     * Null where the band has no joints that keep to one step.
     */
    static int[] grid(float[] rgb, boolean[] solid, int across, int start, int end, float scale) {
        int beside = Math.max(2, Math.round(2f * scale)), slack = Math.max(1, Math.round(scale));
        float[] dark = new float[across];
        for (int column = beside; column < across - beside; column++) {
            float sum = 0f;
            int count = 0;
            for (int row = start; row < end; row++) {
                int at = row * across + column;
                if (solid[at] && solid[at - beside] && solid[at + beside]) {
                    // Darker than what is on both sides of it: the edge between two stripes of paint is darker than one side only.
                    sum += Math.min(bright(rgb, at - beside), bright(rgb, at + beside)) - bright(rgb, at);
                    count++;
                }
            }
            dark[column] = count >= 0.5f * (end - start) ? sum / count : 0f;
        }
        int[] found = new int[across];
        int count = 0;
        for (int column = 0; column < across; column++) {
            if (dark[column] > JOINT) {
                int first = column;
                while (column + 1 < across && dark[column + 1] > JOINT) {
                    column++;
                }
                found[count++] = (first + column) / 2;
            }
        }
        if (count < 2) {
            return null;
        }
        // The step between two joints found side by side, and the joint the rest are counted from, that most of them keep to.
        int bestStep = 0, bestKept = 0, phase = 0;
        for (int k = 1; k < count; k++) {
            int step = found[k] - found[k - 1];
            if (step < LEAST_STEP * scale) {
                continue;
            }
            for (int anchor = 0; anchor < count; anchor++) {
                int kept = 0;
                for (int j = 0; j < count; j++) {
                    int off = Math.floorMod(found[j] - found[anchor], step);
                    kept += off <= slack || off >= step - slack ? 1 : 0;
                }
                if (kept >= 2 && kept >= KEPT * count && kept > bestKept) {
                    bestStep = step;
                    bestKept = kept;
                    phase = Math.floorMod(found[anchor], step);
                }
            }
        }
        return bestStep == 0 ? null : new int[] {phase, bestStep};
    }

    /** The colour of a band's joints: the mean of the dots down its joint columns where it was read. Those found were painted. */
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
        // The joints were found on columns of these same dots that are painted: none read is a fault in the reading, not a wall.
        if (count == 0) {
            throw new IllegalStateException("no dot of the " + joints.length + " joints between rows " + start + " and " + end + " is painted");
        }
        // As finely as the picture keeps a band's joints (WallSides.laid): the joints painted face by face in a course with a figure, kept
        // more finely (FINE), then come within 1/62 of the ones the picture lays, where a joint read between levels came up to 1/20 apart.
        band.jointRed = WallSides.asJoint(red / count);
        band.jointGreen = WallSides.asJoint(green / count);
        band.jointBlue = WallSides.asJoint(blue / count);
    }

    /**
     * For each course of a band, from the foot up, whether it shows a figure: FIGURED_TILES of
     * its tiles or more each with FIGURE_DOTS of their dots or more that are neither the band's
     * colour nor its joints'. A course runs from the course line under it to the one over it, so
     * a figure set where four tiles meet, half either side of a line, is on both. Left out: where the sprite shades or edges the wall at the ceiling and
     * the floor, and a dithered line across the wall: see ACROSS_A_ROW. A band with no joints is one course, its quarters along the wall taken for its tiles.
     */
    private static boolean[] figured(float[] rgb, boolean[] solid, int across, int high, Run run, int[] joints, Band band, int inner0, int inner1,
            float alongScale, float upScale, boolean faint) {
        int least = Math.max(FIGURE_DOTS, Math.round(FIGURE_DOTS * alongScale * upScale));
        // Which tile each column is in, -1 at the sprite's ends.
        int[] tileOf = new int[across];
        for (int column = 0; column < across; column++) {
            int tile = 0;
            while (tile < joints.length && column > joints[tile]) {
                tile++;
            }
            if (joints.length == 0) {
                tile = Math.clamp((column - inner0) * 4L / Math.max(1, inner1 - inner0), 0, 3);
            }
            tileOf[column] = column < inner0 || column >= inner1 ? -1 : tile;
        }
        int courses = joints.length == 0 ? 1 : run.courses.size() + 1;
        boolean[] figured = new boolean[courses];
        int[] offIn = new int[Math.max(joints.length + 1, 4)], offInRow = new int[offIn.length];
        boolean[] tileHas = new boolean[offIn.length];
        for (int column = 0; column < across; column++) {
            if (tileOf[column] >= 0) {
                tileHas[tileOf[column]] = true;
            }
        }
        float[] joint = {band.jointRed, band.jointGreen, band.jointBlue};
        // A dot of the joints' colour is a joint only on a joint's column: off them it is a figure in that colour, a diagonal grid or quilting.
        boolean[] onJoint = new boolean[across];
        int slack = Math.max(1, Math.round(alongScale));
        for (int column : joints) {
            for (int c = Math.max(0, column - slack); c <= Math.min(across - 1, column + slack); c++) {
                onJoint[c] = true;
            }
        }
        int shadeTop = Math.round(TOP_SHADE * high), shadeFoot = high - Math.round(FOOT_SHADE * high);
        float[] dot = new float[3];
        // A line across the wall is a row or two: more rows like it one after another are a figure, a border of rosettes.
        int lineRows = Math.max(2, Math.round(THIN * high));
        for (int c = 0; c < courses; c++) {
            int from = c == 0 ? run.readStart : run.courses.get(c - 1)[1];
            int to = c == courses - 1 ? run.readEnd : run.courses.get(c)[0];
            Arrays.fill(offIn, 0);
            int first = Math.max(from, shadeTop), last = Math.min(to, shadeFoot);
            int[][] offs = new int[Math.max(0, last - first)][];
            boolean[] lineRow = new boolean[offs.length];
            for (int row = first; row < last; row++) {
                Arrays.fill(offInRow, 0);
                int offInAll = 0, kept = 0, inRun = 0, longest = 0;
                for (int column = 0; column < across; column++) {
                    int at = row * across + column;
                    boolean off = false;
                    if (tileOf[column] >= 0 && solid[at]) {
                        dot[0] = rgb[at * 3];
                        dot[1] = rgb[at * 3 + 1];
                        dot[2] = rgb[at * 3 + 2];
                        off = (faint ? !same(dot, run.colour, 1f) : apart(dot, run.colour) > OFF)
                                && (joints.length == 0 || apart(dot, joint) > OFF || !onJoint[column]);
                        offInRow[tileOf[column]] += off ? 1 : 0;
                        offInAll += off ? 1 : 0;
                        kept++;
                    }
                    inRun = off ? inRun + 1 : 0;
                    longest = Math.max(longest, inRun);
                }
                int crossed = 0, marked = 0;
                for (int tile = 0; tile < offIn.length; tile++) {
                    crossed += tileHas[tile] ? 1 : 0;
                    marked += tileHas[tile] && offInRow[tile] > 0 ? 1 : 0;
                }
                lineRow[row - first] = offInAll > SOLID_ROW * kept || offInAll > ACROSS_A_ROW * kept && marked == crossed && longest <= DITHERED * alongScale;
                offs[row - first] = offInRow.clone();
            }
            for (int at = 0; at < offs.length; ) {
                int end = at + 1;
                while (end < offs.length && lineRow[end] == lineRow[at]) {
                    end++;
                }
                if (!lineRow[at] || end - at > lineRows) {
                    for (int row = at; row < end; row++) {
                        for (int tile = 0; tile < offIn.length; tile++) {
                            offIn[tile] += offs[row][tile];
                        }
                    }
                }
                at = end;
            }
            int tiles = 0;
            for (int off : offIn) {
                tiles += off >= least ? 1 : 0;
            }
            // Courses are counted from the top here, and kept from the foot up.
            figured[courses - 1 - c] = tiles >= FIGURED_TILES;
        }
        return figured;
    }
}
