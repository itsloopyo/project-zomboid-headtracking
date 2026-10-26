package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.Random;

/** Tests of a square of rug drawn again: Rugs.drawn and what it is drawn from. */
final class RugTests {
    private RugTests() {}

    static void run() {
        Checks.run("rugSpritesAreToldByTheirSheet", RugTests::rugSpritesAreToldByTheirSheet);
        Checks.run("theSquareIsDrawnWhereFaceTexelsSayItIs", RugTests::theSquareIsDrawnWhereFaceTexelsSayItIs);
        Checks.run("aFrameDrawsASquaresSharpPictureOnceItHasOneAndItsReadOneUntilThen", RugTests::aFrameDrawsASquaresSharpPictureOnceItHasOneAndItsReadOneUntilThen);
        Checks.run("aNumberFromAnEndedRoundNamesNoSquare", RugTests::aNumberFromAnEndedRoundNamesNoSquare);
        Checks.run("theNearestSquaresHaveTheSharpPictures", RugTests::theNearestSquaresHaveTheSharpPictures);
        Checks.run("aPlaceIsMeasuredFromTheEye", RugTests::aPlaceIsMeasuredFromTheEye);
        Checks.run("aPlaceIsMeasuredFromTheNearestViewsEye", RugTests::aPlaceIsMeasuredFromTheNearestViewsEye);
        Checks.run("everyReaderOfARunsTextureGoesThroughBound", RugTests::everyReaderOfARunsTextureGoesThroughBound);
        Checks.run("aWovenRugIsItsFlatColoursWithoutTheSpeckle", RugTests::aWovenRugIsItsFlatColoursWithoutTheSpeckle);
        Checks.run("aBorderIsDrawnAsAStraightSharpLine", RugTests::aBorderIsDrawnAsAStraightSharpLine);
        Checks.run("theOutlineIsCutWhereHalfADotIsCovered", RugTests::theOutlineIsCutWhereHalfADotIsCovered);
        Checks.run("theStaircaseAtASquaresEdgeIsFilledAndWhatIsPastItCut", RugTests::theStaircaseAtASquaresEdgeIsFilledAndWhatIsPastItCut);
        Checks.run("furTakesItsStrandsFromTheGrainAndKeepsItsShade", RugTests::furTakesItsStrandsFromTheGrainAndKeepsItsShade);
        Checks.run("levelsHalveDownToOneDot", RugTests::levelsHalveDownToOneDot);
    }

    private static void rugSpritesAreToldByTheirSheet() {
        Checks.check(Rugs.kindOf("floors_rugs_01_84") == Rugs.WOVEN, "a medallion middle is woven");
        Checks.check(Rugs.kindOf("floors_rugs_02_3") == Rugs.FUR, "a shag rug is fur");
        Checks.check(Rugs.kindOf("rugs_animals_33") == Rugs.HIDE, "a cowhide is a hide");
        Checks.check(Rugs.kindOf("floors_interior_carpet_01_5") < 0 && Rugs.kindOf(null) < 0, "a carpet and no name are no rug");
        Checks.check(Rugs.material(Rugs.WOVEN, WorldScene.FLOOR_CARPET) == WorldScene.FLOOR_CARPET && Rugs.material(Rugs.FUR, WorldScene.FLOOR_CARPET) == WorldScene.FLOOR_CARPET,
                "woven and fur rugs keep the carpet's pile");
        Checks.check(Rugs.material(Rugs.HIDE, WorldScene.FLOOR_CARPET) == 0f, "a hide carries its hair in its picture alone");
    }

    /**
     * A face takes its texels at FROM + SPAN times where on the square it is (SpriteGeometry,
     * FLAT_RUG): a hide that covers exactly its square has to be drawn over exactly those texels,
     * and a face reaching REACH past the square ends at the picture's edge.
     */
    private static void theSquareIsDrawnWhereFaceTexelsSayItIs() {
        int n = Rugs.READ;
        float[] colour = new float[n * n * 3], cover = new float[n * n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                boolean in = i >= Rugs.MARGIN && i < Rugs.MARGIN + Rugs.SIDE && j >= Rugs.MARGIN && j < Rugs.MARGIN + Rugs.SIDE;
                cover[j * n + i] = in ? 1f : 0f;
                colour[(j * n + i) * 3] = 120f;
            }
        }
        byte[] dots = Rugs.drawn(new Rugs.Read(colour, cover), Rugs.HIDE, null, 0);
        int side = Rugs.DRAWN, wrong = 0;
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                float u = (x + 0.5f) / side, v = (y + 0.5f) / side;
                float a = (u - Rugs.FROM) / Rugs.SPAN, b = (v - Rugs.FROM) / Rugs.SPAN;
                boolean onSquare = a > 0f && a < 1f && b > 0f && b < 1f;
                float edge = Math.min(Math.min(Math.abs(a), Math.abs(1f - a)), Math.min(Math.abs(b), Math.abs(1f - b))) * Rugs.SIDE;
                boolean drawn = (dots[(y * side + x) * 4 + 3] & 0xff) == 255;
                wrong += drawn != onSquare && edge > 0.6f ? 1 : 0;
            }
        }
        Checks.check(wrong == 0, "the square is drawn where its face's texels put it, within a dot read: " + wrong + " dots off");
        Checks.check(Math.abs(Rugs.FROM - Rugs.REACH * Rugs.SPAN) < 1e-6f, "a face reaching its most past the square ends at the picture's edge");
    }

    private static void aFrameDrawsASquaresSharpPictureOnceItHasOneAndItsReadOneUntilThen() {
        Rugs.forget();
        Rugs.Square square = Rugs.adopted(7);
        Checks.check(ChunkBuffers.bound(square.number) == 7, "a run keyed by the square's number is drawn with its picture as read");
        square.sharp = 9;
        Checks.check(ChunkBuffers.bound(square.number) == 9, "and with its sharp picture once it has one");
        Checks.check(ChunkBuffers.bound(12) == 12 && ChunkBuffers.bound(0) == 0, "any other key is the texture's own name");
        int[] given = Rugs.forget();
        Arrays.sort(given);
        Checks.check(Arrays.equals(given, new int[] {7, 9}), "ending the round gives back both pictures: " + Arrays.toString(given));
    }

    private static void aNumberFromAnEndedRoundNamesNoSquare() {
        Rugs.forget();
        int old = Rugs.adopted(3).number;
        Rugs.forget();
        Rugs.Square next = Rugs.adopted(4);
        Checks.check(next.number != old, "the next round numbers on: " + old + " then " + next.number);
        boolean refused = false;
        try {
            Rugs.texture(old);
        } catch (IllegalStateException expected) {
            refused = true;
        }
        Checks.check(refused, "a number kept past the end of its round is refused, not taken for a square of the next");
        Checks.check(Rugs.texture(next.number) == 4, "and the next round's square is found by its own");
        Rugs.forget();
    }

    private static void theNearestSquaresHaveTheSharpPictures() {
        boolean[] wanted = Rugs.nearestOf(new float[] {50f, 4f, 900f, 9f, 4f}, 3);
        Checks.check(Arrays.equals(wanted, new boolean[] {false, true, false, true, true}), "the three nearest: " + Arrays.toString(wanted));
        boolean[] all = Rugs.nearestOf(new float[] {5f, 1f}, 48);
        Checks.check(all[0] && all[1], "every square, where there are fewer than slots");
    }

    private static void aPlaceIsMeasuredFromTheEye() {
        java.util.Set<Long> places = new java.util.HashSet<>();
        places.add(Rugs.place(10654, 9367, 0));
        places.add(Rugs.place(10660, 9300, 1));
        float d = Rugs.distanceTo(places, 10654.5f, 9369.5f, 0f);
        Checks.check(Math.abs(d - 4f) < 1e-3f, "the nearer place, two tiles off: " + d);
        java.util.Set<Long> under = java.util.Set.of(Rugs.place(3, 4, -1));
        float below = Rugs.distanceTo(under, 3.5f, 4.5f, 0f);
        Checks.check(Math.abs(below - ViewCamera.LEVEL_HEIGHT * ViewCamera.LEVEL_HEIGHT) < 1e-3f, "a place a level below is a level off: " + below);
    }

    private static void aPlaceIsMeasuredFromTheNearestViewsEye() {
        java.util.Set<Long> places = java.util.Set.of(Rugs.place(500, 500, 0));
        Rugs.eyesRanked();
        Rugs.seeFrom(0, 100.5f, 100.5f, 0f);
        Rugs.seeFrom(1, 500.5f, 503.5f, 0f);
        float d = Rugs.distanceToEyes(places);
        Checks.check(Math.abs(d - 9f) < 1e-3f, "a square by the second view's eye is three tiles off, whichever view ranks: " + d);
        Rugs.eyesRanked();
        Rugs.seeFrom(0, 100.5f, 100.5f, 0f);
        float far = Rugs.distanceToEyes(places);
        Checks.check(Math.abs(far - 2f * 400f * 400f) < 1f, "a view that has drawn no frame since the last ranking is not measured from: " + far);
        Rugs.eyesRanked();
    }

    /**
     * A run's key is a texture's name or a square of rug's number, so whatever binds it binds
     * ChunkBuffers.bound of it. Every line of the mod that reads a run's or a range's key is listed
     * here as bookkeeping, or goes through bound: a new reader that binds the key itself fails.
     */
    private static void everyReaderOfARunsTextureGoesThroughBound() throws java.io.IOException {
        java.util.List<String> keeping = java.util.List.of(
                "WorldScene.java:runTexture = Arrays.copyOf(runTexture, runs * 2);",
                "WorldScene.java:runTexture[runs] = mesh.texture;",
                "ChunkBuffers.java:int slot = of.slot(build.runTexture[run], build.runTint[run]);",
                "ChunkBuffers.java:rangeTexture[ranges] = runs.texture[slot];",
                "ChunkBuffers.java:int slot = slot(build.runTexture[run], build.runTint[run]);",
                "ChunkBuffers.java:texture[slot] = build.runTexture[run];");
        int unresolved = 0;
        StringBuilder found = new StringBuilder();
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java/com/cameraunlock/zomboid")).filter(java.nio.file.Files::isRegularFile)) {
            for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) files::iterator) {
                for (String line : java.nio.file.Files.readAllLines(file)) {
                    String code = line.strip();
                    boolean reads = code.matches(".*\\b(runTexture|rangeTexture)\\[.*") || code.matches(".*\\bruns\\.texture\\[.*");
                    if (!reads || code.startsWith("//") || code.startsWith("*") || code.contains("bound(")) {
                        continue;
                    }
                    if (code.startsWith("private int[] rangeTexture") || code.startsWith("int[] runTexture") || code.startsWith("rangeTexture = new int")) {
                        continue;
                    }
                    if (!keeping.contains(file.getFileName() + ":" + code)) {
                        unresolved++;
                        found.append(' ').append(file.getFileName()).append(": ").append(code);
                    }
                }
            }
        }
        Checks.check(unresolved == 0, "every reader of a run's texture binds it through ChunkBuffers.bound:" + found);
    }

    /** A square read as two colours split at a column, with the game's speckle over both: measured on its rugs, a few steps of 255 a channel. */
    private static Rugs.Read split(int at, long seed) {
        int n = Rugs.READ;
        float[] colour = new float[n * n * 3], cover = new float[n * n];
        Random random = new Random(seed);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int dot = j * n + i;
                cover[dot] = 1f;
                float[] base = i < at ? new float[] {150f, 60f, 50f} : new float[] {220f, 200f, 150f};
                for (int c = 0; c < 3; c++) {
                    colour[dot * 3 + c] = base[c] + (float) random.nextGaussian() * 2.5f;
                }
            }
        }
        return new Rugs.Read(colour, cover);
    }

    private static void aWovenRugIsItsFlatColoursWithoutTheSpeckle() {
        Rugs.Read read = split(70, 7);
        float[] palette = Rugs.palette(read.colour(), read.cover());
        Checks.check(palette.length == 6, "two colours found, not the speckle: " + palette.length / 3);
        byte[] dots = Rugs.drawn(read, Rugs.WOVEN, null, 0);
        int side = Rugs.DRAWN, odd = 0;
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                int at = (y * side + x) * 4;
                if (Math.abs(x - 70 * Rugs.UP) <= 2 * Rugs.UP) {
                    continue;
                }
                int red = dots[at] & 0xff;
                odd += Math.abs(red - (x < 70 * Rugs.UP ? 150 : 220)) > 3 ? 1 : 0;
            }
        }
        Checks.check(odd == 0, "away from the border every dot drawn is one of the two colours: " + odd + " are not");
    }

    private static void aBorderIsDrawnAsAStraightSharpLine() {
        byte[] dots = Rugs.drawn(split(70, 11), Rugs.WOVEN, null, 0);
        int side = Rugs.DRAWN, least = side, most = 0, widest = 0;
        for (int y = 0; y < side; y++) {
            int first = -1, last = -1;
            for (int x = 0; x < side; x++) {
                int red = dots[(y * side + x) * 4] & 0xff;
                if (red > 152 && red < 218) {
                    first = first < 0 ? x : first;
                    last = x;
                }
            }
            int at = 0;
            while (at < side && (dots[(y * side + at) * 4] & 0xff) < 185) {
                at++;
            }
            least = Math.min(least, at);
            most = Math.max(most, at);
            widest = Math.max(widest, first < 0 ? 0 : last - first + 1);
        }
        Checks.check(most - least <= 1, "the border is at the same place on every row: from " + least + " to " + most);
        Checks.check(Math.abs(least - 70 * Rugs.UP) <= 1, "and where the colours meet in the sprite: " + least);
        Checks.check(widest <= 3, "and goes from one colour to the other within three dots drawn: " + widest);
    }

    private static void theOutlineIsCutWhereHalfADotIsCovered() {
        int n = Rugs.READ;
        float[] colour = new float[n * n * 3], cover = new float[n * n];
        float middle = n / 2f, radius = 50f;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                float d = (float) Math.hypot(i + 0.5f - middle, j + 0.5f - middle);
                cover[j * n + i] = Math.clamp(radius + 0.5f - d, 0f, 1f);
                colour[(j * n + i) * 3] = cover[j * n + i] > 0f ? 200f : 0f;
            }
        }
        byte[] dots = Rugs.drawn(new Rugs.Read(colour, cover), Rugs.HIDE, null, 0);
        int side = Rugs.DRAWN, wrong = 0, dark = 0;
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                float d = (float) Math.hypot((x + 0.5f) / Rugs.UP - middle, (y + 0.5f) / Rugs.UP - middle);
                int at = (y * side + x) * 4;
                boolean in = (dots[at + 3] & 0xff) == 255;
                wrong += in != d < radius && Math.abs(d - radius) > 0.6f ? 1 : 0;
                dark += in && (dots[at] & 0xff) < 190 ? 1 : 0;
            }
        }
        Checks.check(wrong == 0, "a round rug's outline is its circle within a dot read: " + wrong + " dots off it");
        Checks.check(dark == 0, "and its edge is not darkened by the empty dots beside it: " + dark);
    }

    private static void theStaircaseAtASquaresEdgeIsFilledAndWhatIsPastItCut() {
        int n = Rugs.READ, m = Rugs.MARGIN;
        float[] colour = new float[n * n * 3], cover = new float[n * n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int a = i - m, b = j - m;
                // The diamond's teeth: two dots short of the west edge on every other row, and spilling past the east edge.
                boolean in = a >= ((b & 1) == 0 ? 2 : 0) && a < Rugs.SIDE + ((b & 1) == 0 ? 3 : 0) && b >= 0 && b < Rugs.SIDE;
                cover[j * n + i] = in ? 1f : 0f;
                colour[(j * n + i) * 3] = 180f;
            }
        }
        Rugs.Read woven = Rugs.straightened(new Rugs.Read(colour, cover), Rugs.WOVEN);
        int short1 = 0, past = 0;
        for (int b = 0; b < Rugs.SIDE; b++) {
            short1 += woven.cover()[(b + m) * n + m] < 0.5f ? 1 : 0;
            past += woven.cover()[(b + m) * n + m + Rugs.SIDE] > 0f ? 1 : 0;
        }
        Checks.check(short1 == 0, "every row of a woven rug runs to the square's west edge: " + short1 + " stop short");
        Checks.check(past == 0, "and none past its east edge: " + past);
        Rugs.Read fur = Rugs.straightened(new Rugs.Read(colour, cover), Rugs.FUR);
        int tufts = 0;
        for (int b = 0; b < Rugs.SIDE; b++) {
            tufts += fur.cover()[(b + m) * n + m + Rugs.SIDE] > 0f ? 1 : 0;
        }
        Checks.check(tufts == Rugs.SIDE / 2, "a fur rug keeps what stands past it: " + tufts);
    }

    private static void furTakesItsStrandsFromTheGrainAndKeepsItsShade() {
        int n = Rugs.READ, grainSide = 64;
        float[] colour = new float[n * n * 3], cover = new float[n * n];
        java.util.Arrays.fill(cover, 1f);
        java.util.Arrays.fill(colour, 100f);
        float[] grain = new float[grainSide * grainSide];
        for (int at = 0; at < grain.length; at++) {
            grain[at] = at % 2 == 0 ? 0.5f : 1.5f;
        }
        byte[] dots = Rugs.drawn(new Rugs.Read(colour, cover), Rugs.FUR, grain, grainSide);
        int side = Rugs.DRAWN;
        long sum = 0;
        int least = 255, most = 0;
        for (int at = 0; at < side * side; at++) {
            int red = dots[at * 4] & 0xff;
            sum += red;
            least = Math.min(least, red);
            most = Math.max(most, red);
        }
        float mean = (float) sum / (side * side);
        Checks.check(Math.abs(mean - 100f) < 2f, "the strands leave the rug's own shade as it was: " + mean);
        Checks.check(most - least > 40, "and show across it: from " + least + " to " + most);
    }

    private static void levelsHalveDownToOneDot() {
        int side = 10;
        byte[] dots = new byte[side * side * 4];
        java.util.Arrays.fill(dots, (byte) 200);
        byte[][] levels = Rugs.levels(dots, side);
        Checks.check(levels.length == 4, "10, 5, 2 and 1 dots across: " + levels.length);
        Checks.check(levels[3].length == 4 && (levels[3][0] & 0xff) == 200, "the last is one dot of the same colour");
    }
}
