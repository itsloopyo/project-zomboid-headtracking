package com.cameraunlock.zomboid;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * What people wear, made finer than the game paints it. The game's picture of a garment, a head
 * of hair or a mark on the skin is 64 to 512 dots across: enough to say what colour each part is,
 * not what it is made of, and from near by every dot of it is a square. Here it is enlarged so
 * that the edges between its colours stay sharp and run smoothly, and the paint within them is
 * evened out, and then the weave, the knit, the grain or the strands of what it is made of are
 * laid over it from a photograph of that material, of the mod's own. The game's picture is read
 * from the player's own files as the game runs, and nothing of it is in the mod.
 *
 * No game class and no graphics call: the fabric is read out of the jar, and a garment's name is put to CharacterDraw.wearOf.
 */
final class Tailor {
    private Tailor() {}

    /** The picture the game painted is this many dots across, at most, when it is worth making finer. */
    static final int COARSE = 512;
    /** How many dots across the finer picture is made, where the game's enlarges to it a whole number of times. */
    static final int FINE = 1024;
    /** A picture under this many dots across is a colour, not a picture: the game has earrings of one dot. */
    static final int LEAST = 8;
    /** The most times a picture is enlarged: a head of hair's is 64 dots. */
    static final int MOST_TIMES = 8;
    /** How much of a fabric's light and dark is laid on: all of it reads as a photograph pasted on, none as paint. */
    static final float LAID = 0.85f;
    /** How unlike two colours may be, as a part of the whole range, and still be evened out with each other. */
    private static final float ALIKE = 0.11f;
    /**
     * How far round a place the dots of the game's picture count towards it, as twice the square
     * of that many dots. A colour and an outline are taken from this much of the picture, not
     * from the four dots round a place, so the steps of a slanting edge come out as a slant.
     */
    private static final float SOFT = 1.5f;
    /** Where between see-through and solid the edge of a garment is cut, and how broad the cut is. */
    private static final float EDGE = 0.5f, EDGE_BREADTH = 0.3f;

    /** A material's photograph and how many times it repeats across a picture of the game's. */
    record Fabric(String name, int repeats) {}

    static final Fabric PLAIN = new Fabric(null, 0);
    private static final Fabric WEAVE = new Fabric("weave", 5), TWILL = new Fabric("twill", 5), KNIT = new Fabric("knit", 5),
            WOOL = new Fabric("wool", 7), LEATHER = new Fabric("leather", 4), SLEEK = new Fabric("sleek", 6), CANVAS = new Fabric("canvas", 5),
            HAIR = new Fabric("hair", 3);

    /**
     * What the picture at this path under a media/textures folder is made finer with: a fabric,
     * PLAIN for a picture that is enlarged and has nothing laid on it (a wound, a stain, a shirt's
     * print, a pair of glasses), or null for one that is left as the game has it. A body's skin
     * is the mod's own or the game's, never this. Nor is a mask, which says where something
     * shows by being see-through or not: the game reads the least trace of one as all of it, so
     * one made finer says something else.
     */
    static Fabric of(String relative) {
        String path = relative.toLowerCase(Locale.ROOT);
        int folder = path.indexOf('/'), last = path.lastIndexOf('/');
        String first = folder < 0 ? "" : path.substring(0, folder), name = path.substring(last + 1, path.length() - 4);
        if (masks(path, name)) {
            return null;
        }
        switch (first) {
            case "body":
                return path.startsWith("body/makeup/") || path.startsWith("body/stubble/") ? PLAIN : null;
            case "bodydmg", "shirtdecals":
                return PLAIN;
            case "bloodtextures":
                return name.contains("overlay") ? PLAIN : null;
            case "patches":
                return made(name, WEAVE);
            case "clothes":
                if (has(path, "/jewellery/", "/armor/", "/hazmat/", "glasses", "goggles")) {
                    return PLAIN;
                }
                if (path.startsWith("clothes/bag/")) {
                    return has(name, "case", "box", "cooler", "basket", "sprayer") ? PLAIN : made(name, CANVAS);
                }
                return made(name, WEAVE);
            case "":
                if (starts(name, "f_hair", "hair", "beard")) {
                    return HAIR;
                }
                if (has(name, "glasses", "goggles", "eyepatch", "holster") || starts(name, "necklace", "ring", "choker", "wristwatch", "medal", "bracelet",
                        "earring", "nosering", "tattoo")) {
                    return PLAIN;
                }
                return has(name, "clothing", "trouser", "shirt", "vest", "jacket", "scarf", "shorts", "skirt", "jumper", "hoodie", "blouse", "suit")
                        || starts(name, "dress", "tie", "raccoonhat", "showercap") ? made(name, WEAVE) : null;
            default:
                return null;
        }
    }

    /**
     * Whether a picture is a mask: in a folder named for masks, or named mask. One folder of the
     * game's masks, a round necked jumper's, is named for neither.
     */
    private static boolean masks(String path, String name) {
        int last = path.lastIndexOf('/');
        return last >= 0 && path.substring(0, last).contains("mask") || path.contains("/jumper/roundneck/") || name.equals("mask") || name.startsWith("mask_")
                || name.equals("patchesmask");
    }

    /** What a garment is made of, by its picture's own name: a folder of the game's holds shirts and T-shirts, or shoes and socks. */
    private static Fabric made(String name, Fabric otherwise) {
        if (has(name, "hair", "beard")) {
            return HAIR;
        }
        if (has(name, "dishglove", "medicalglove", "longglove", "shellsuit", "wellies", "garbage", "tarp")) {
            return SLEEK;
        }
        if (has(name, "cow", "calf", "sheepskin", "leather", "hide")) {
            return LEATHER;
        }
        if (has(name, "bandana", "rag", "camo") && has(name, "mask", "glove")) {
            return WEAVE;
        }
        if (has(name, "burlap", "berlap", "apron", "padded", "canvas", "dungaree", "ghillie", "boilersuit")) {
            return CANVAS;
        }
        if (has(name, "jumper", "sweater", "scarf", "beany", "beanie", "winterhat", "wool", "legwarmer", "bathrobe")) {
            return WOOL;
        }
        if (has(name, "tights")) {
            return KNIT;
        }
        // A short sleeved shirt is no pair of shorts.
        return switch (CharacterDraw.wearOf("", name.replace("shortsleeve", ""), false)) {
            case CharacterDraw.LEATHER -> LEATHER;
            case CharacterDraw.SLEEK -> SLEEK;
            case CharacterDraw.KNIT -> KNIT;
            case CharacterDraw.TWILL -> TWILL;
            default -> otherwise;
        };
    }

    private static boolean has(String named, String... words) {
        for (String word : words) {
            if (named.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static boolean starts(String named, String... words) {
        for (String word : words) {
            if (named.startsWith(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The dots of a picture as it was painted, alpha in the top byte. Asked for as colours, a
     * picture stored as greys is handed over lightened, so one of those is read number by number.
     */
    static int[] dots(BufferedImage image) {
        int wide = image.getWidth(), high = image.getHeight();
        if (image.getColorModel().getColorSpace().getType() != ColorSpace.TYPE_GRAY) {
            return image.getRGB(0, 0, wide, high, null, 0, wide);
        }
        int bands = image.getRaster().getNumBands(), deep = image.getRaster().getSampleModel().getSampleSize(0) - 8;
        int[] grey = image.getRaster().getSamples(0, 0, wide, high, 0, (int[]) null);
        int[] shows = bands > 1 ? image.getRaster().getSamples(0, 0, wide, high, 1, (int[]) null) : null;
        int[] dots = new int[wide * high];
        for (int at = 0; at < dots.length; at++) {
            int light = deep > 0 ? grey[at] >> deep : grey[at] << -deep;
            int solid = shows == null ? 255 : deep > 0 ? shows[at] >> deep : shows[at] << -deep;
            dots[at] = solid << 24 | light << 16 | light << 8 | light;
        }
        return dots;
    }

    /**
     * Whether a picture ends at an edge, as a garment does, and does not fade out, as a stain or a
     * veil does: nearly every dot of it is either solid or see-through.
     */
    static boolean edged(int[] source) {
        int partly = 0, shown = 0;
        for (int dot : source) {
            int solid = dot >>> 24;
            shown += solid > 0 ? 1 : 0;
            partly += solid > 24 && solid < 231 ? 1 : 0;
        }
        return partly * 20 <= shown;
    }

    /**
     * How many times a picture of the game's of this size is enlarged, or 0 for one fine enough as
     * it is or too small to be a picture of anything. In a graphics mode that reads the mod's
     * pictures at half size it is made half as large.
     */
    static int times(int wide, int high, boolean small) {
        int longest = Math.max(wide, high), times = Math.min(MOST_TIMES, (small ? FINE / 2 : FINE) / Math.max(1, longest));
        return longest > COARSE || longest < LEAST || times < 2 ? 0 : times;
    }

    private static final HashMap<String, byte[]> fabrics = new HashMap<>();
    static final int FABRIC_SIZE = 1024;

    /** A fabric's light and dark out of the jar, FABRIC_SIZE dots a side, 128 for its middle. */
    static synchronized byte[] light(String name) throws IOException {
        byte[] light = fabrics.get(name);
        if (light == null) {
            String path = "/fabric/" + name + ".jpg";
            try (InputStream in = Tailor.class.getResourceAsStream(path)) {
                if (in == null) {
                    throw new IOException("the jar has no " + path);
                }
                BufferedImage image = ImageIO.read(in);
                if (image == null || image.getWidth() != FABRIC_SIZE || image.getHeight() != FABRIC_SIZE) {
                    throw new IOException(path + " is not a picture of " + FABRIC_SIZE + " dots a side");
                }
                // The numbers as the file has them: asked for as colours, a picture of one channel is handed over lightened.
                light = new byte[FABRIC_SIZE * FABRIC_SIZE];
                int[] row = new int[FABRIC_SIZE];
                for (int y = 0; y < FABRIC_SIZE; y++) {
                    image.getRaster().getSamples(0, y, FABRIC_SIZE, 1, 0, row);
                    for (int x = 0; x < FABRIC_SIZE; x++) {
                        light[y * FABRIC_SIZE + x] = (byte) row[x];
                    }
                }
            }
            fabrics.put(name, light);
        }
        return light;
    }

    /**
     * A fabric's light and dark as it lies on a picture this many dots a side that it repeats
     * across so many times: one repeat of it, each dot the mean of what of the fabric falls in
     * it, as a multiplier about 1.
     */
    static float[] tile(byte[] light, int size, int across, int repeats) {
        int tile = Math.max(1, across / repeats);
        float[] laid = new float[tile * tile];
        for (int y = 0; y < tile; y++) {
            int top = y * size / tile, bottom = Math.max(top + 1, (y + 1) * size / tile);
            for (int x = 0; x < tile; x++) {
                int left = x * size / tile, right = Math.max(left + 1, (x + 1) * size / tile);
                int total = 0;
                for (int v = top; v < bottom; v++) {
                    for (int u = left; u < right; u++) {
                        total += light[v * size + u] & 0xff;
                    }
                }
                float mean = total / (float) ((bottom - top) * (right - left)) / 127.5f;
                laid[y * tile + x] = 1f + (mean - 1f) * LAID;
            }
        }
        return laid;
    }

    /**
     * Enlarges a picture and lays a fabric on it.
     *
     * @param source the game's picture, a dot an int, alpha in the top byte
     * @param times how many times larger the result is each way
     * @param laid one repeat of the fabric as tile gives it, or null to lay none
     * @param edged whether the picture ends at an edge, which is then cut clean, or fades out and is left to
     * @param out takes the result, four bytes a dot: red, green, blue, alpha
     */
    static void fine(int[] source, int wide, int high, int times, float[] laid, boolean edged, ByteBuffer out) {
        int tile = laid == null ? 0 : (int) Math.round(Math.sqrt(laid.length));
        // How much each of the four dots across a place counts by how far off it is, for each place between two dots.
        float[] near = new float[times * 4];
        for (int phase = 0; phase < times; phase++) {
            float at = (phase + 0.5f) / times - 0.5f;
            float within = at - (float) Math.floor(at);
            for (int tap = 0; tap < 4; tap++) {
                float off = tap - 1 - within;
                near[phase * 4 + tap] = (float) Math.exp(-off * off / SOFT);
            }
        }
        float[] alike = new float[256 * 3 + 1];
        for (int apart = 0; apart < alike.length; apart++) {
            float part = apart / 255f / ALIKE;
            alike[apart] = (float) Math.exp(-part * part / 2f);
        }
        for (int y = 0; y < high * times; y++) {
            int y0 = (int) Math.floor((y + 0.5f) / times - 0.5f);
            int phaseY = y % times;
            for (int x = 0; x < wide * times; x++) {
                int x0 = (int) Math.floor((x + 0.5f) / times - 0.5f);
                int phaseX = x % times;
                // How solid the picture is here read plainly, from the four dots round the place.
                float sx = (x + 0.5f) / times - 0.5f - x0, sy = (y + 0.5f) / times - 0.5f - y0;
                float plainly = ((source[clamp(y0, high) * wide + clamp(x0, wide)] >>> 24) * (1f - sx) * (1f - sy)
                        + (source[clamp(y0, high) * wide + clamp(x0 + 1, wide)] >>> 24) * sx * (1f - sy)
                        + (source[clamp(y0 + 1, high) * wide + clamp(x0, wide)] >>> 24) * (1f - sx) * sy
                        + (source[clamp(y0 + 1, high) * wide + clamp(x0 + 1, wide)] >>> 24) * sx * sy) / 255f;
                // The colour here as the dots round about give it, each by how near and how solid it is, and how solid it is here.
                float red = 0f, green = 0f, blue = 0f, solid = 0f, all = 0f;
                for (int tapY = 0; tapY < 4; tapY++) {
                    int row = clamp(y0 - 1 + tapY, high) * wide;
                    float nearY = near[phaseY * 4 + tapY];
                    for (int tapX = 0; tapX < 4; tapX++) {
                        int dot = source[row + clamp(x0 - 1 + tapX, wide)];
                        float by = nearY * near[phaseX * 4 + tapX], share = by * (dot >>> 24) / 255f;
                        red += share * (dot >> 16 & 0xff);
                        green += share * (dot >> 8 & 0xff);
                        blue += share * (dot & 0xff);
                        solid += share;
                        all += by;
                    }
                }
                solid /= all;
                red /= all;
                green /= all;
                blue /= all;
                float r, g, b;
                if (solid < 1e-4f) {
                    r = g = b = 0f;
                } else {
                    red /= solid;
                    green /= solid;
                    blue /= solid;
                    // Evened out with the dots round about that are of nearly this colour, and with none that are not: an edge stays an edge.
                    float sumR = 0f, sumG = 0f, sumB = 0f, sum = 0f;
                    for (int tapY = 0; tapY < 4; tapY++) {
                        int row = clamp(y0 - 1 + tapY, high) * wide;
                        float nearY = near[phaseY * 4 + tapY];
                        for (int tapX = 0; tapX < 4; tapX++) {
                            int dot = source[row + clamp(x0 - 1 + tapX, wide)];
                            int dr = dot >> 16 & 0xff, dg = dot >> 8 & 0xff, db = dot & 0xff;
                            int apart = (int) (Math.abs(dr - red) + Math.abs(dg - green) + Math.abs(db - blue));
                            float share = nearY * near[phaseX * 4 + tapX] * alike[apart] * (dot >>> 24);
                            sumR += share * dr;
                            sumG += share * dg;
                            sumB += share * db;
                            sum += share;
                        }
                    }
                    if (sum > 1e-4f) {
                        red = sumR / sum;
                        green = sumG / sum;
                        blue = sumB / sum;
                    }
                    float cloth = tile == 0 ? 1f : laid[y % tile * tile + x % tile];
                    r = red * cloth;
                    g = green * cloth;
                    b = blue * cloth;
                }
                // Taken from so much of the picture, a line or a spot one dot broad is under half solid everywhere and would
                // be cut away whole: the plain reading, which is nearly solid along its middle, keeps it.
                float cut = Math.max(0f, Math.min(1f, (Math.max(solid, plainly) - EDGE) / EDGE_BREADTH + 0.5f));
                float shows = edged ? cut * cut * (3f - 2f * cut) : solid;
                out.put((byte) Math.min(255f, r + 0.5f)).put((byte) Math.min(255f, g + 0.5f)).put((byte) Math.min(255f, b + 0.5f))
                        .put((byte) (shows * 255f + 0.5f));
            }
        }
    }

    private static int clamp(int at, int size) {
        return at < 0 ? 0 : at >= size ? size - 1 : at;
    }
}
