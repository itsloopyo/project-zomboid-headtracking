package com.cameraunlock.zomboid;

import java.util.Random;

/** Tests of a square of rug drawn again: Rugs.drawn and what it is drawn from. */
final class RugTests {
    private RugTests() {}

    static void run() {
        Checks.run("rugSpritesAreToldByTheirSheet", RugTests::rugSpritesAreToldByTheirSheet);
        Checks.run("theSquareSitsInThePictureWithTheSameMarginAllRound", RugTests::theSquareSitsInThePictureWithTheSameMarginAllRound);
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
        Checks.check(Rugs.material(Rugs.WOVEN, WorldScene.CARPET) == WorldScene.CARPET && Rugs.material(Rugs.FUR, WorldScene.CARPET) == WorldScene.CARPET,
                "woven and fur rugs keep the carpet's pile");
        Checks.check(Rugs.material(Rugs.HIDE, WorldScene.CARPET) == 0f, "a hide carries its hair in its picture alone");
    }

    private static void theSquareSitsInThePictureWithTheSameMarginAllRound() {
        Checks.check(Math.abs(2f * Rugs.FROM + Rugs.SPAN - 1f) < 1e-6f, "margin, square and margin fill the picture: " + (2f * Rugs.FROM + Rugs.SPAN));
        Checks.check(Math.abs(Rugs.FROM - Rugs.REACH * Rugs.SPAN) < 1e-6f, "a face reaching its most past the square ends at the picture's edge");
        Checks.check(Rugs.DRAWN == Rugs.READ * Rugs.UP, "drawn UP times as fine as read");
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
