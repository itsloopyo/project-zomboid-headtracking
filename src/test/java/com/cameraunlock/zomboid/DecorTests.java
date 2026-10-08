package com.cameraunlock.zomboid;

/** Tests of how the mod's own picture is laid in a frame or over a sheet on a wall. */
final class DecorTests {
    private DecorTests() {}

    static void run() {
        pictureOfTheFramesShapeFillsItCornerToCorner();
        tallPictureInASquareFrameKeepsItsWidthAndTheUpperPartOfItsHeight();
        widePictureInATallFrameShowsItsMiddle();
        squaresOfALongPictureShowItsPartsInOrder();
        paintedFrameLeavesARailRoundThePictureThatLooksOutsideIt();
        everyFrameHasAPaneInsideItsMoulding();
        everyTelevisionHasGlassTheShapeOfATubeOnItsFront();
        jarHoldsAPictureForEverythingATelevisionShows();
        aShowCutsRoundItsShotsAndMovesAcrossEachOne();
        aBroadcastNobodyListedIsItsChannelsShow();
    }

    private static float u(float[] laid, float x) {
        return laid[0] + x * laid[2];
    }

    private static float v(float[] laid, float z) {
        return laid[1] + z * laid[5];
    }

    private static void pictureOfTheFramesShapeFillsItCornerToCorner() {
        float[] laid = new float[6];
        Decor.lay(-0.3f, 0.3f, 0.1f, 1.0f, 0.6f, 0.9f, 0f, 2f / 3f, laid);
        check(near(u(laid, -0.3f), 0f) && near(u(laid, 0.3f), 1f), "the picture's left edge is at the rectangle's left and its right at the right");
        check(near(v(laid, 1.0f), 0f) && near(v(laid, 0.1f), 1f), "the picture's top is at the rectangle's top and its foot at the foot");
        check(laid[3] == 0f && laid[4] == 0f, "a picture hangs square: across it is across the model and up it is up");
    }

    private static void tallPictureInASquareFrameKeepsItsWidthAndTheUpperPartOfItsHeight() {
        float[] laid = new float[6];
        Decor.lay(0f, 1f, 0f, 1f, 0.5f, 0.5f, 0f, 0.5f, laid);
        check(near(u(laid, 0f), 0f) && near(u(laid, 1f), 1f), "all of its width is shown");
        float top = v(laid, 1f), foot = v(laid, 0f);
        check(near(foot - top, 0.5f), "half of its height is shown, so it is not squashed: " + (foot - top));
        check(top > 0f && top < 0.25f, "and the half shown is above the middle, where a sitter's head is: from " + top);
    }

    private static void widePictureInATallFrameShowsItsMiddle() {
        float[] laid = new float[6];
        Decor.lay(-1f, 1f, 0f, 4f, 0.4f, 0.8f, 0f, 2f, laid);
        check(near(v(laid, 4f), 0f) && near(v(laid, 0f), 1f), "all of its height is shown");
        check(near(u(laid, -1f), 0.375f) && near(u(laid, 1f), 0.625f), "and the middle quarter of its width");
    }

    private static void squaresOfALongPictureShowItsPartsInOrder() {
        // Two squares' lengths of one picture, each a model a metre wide about its own middle, the picture twice as wide as high.
        float[] first = new float[6], second = new float[6];
        Decor.lay(-0.5f, 1.5f, 0f, 1f, 2f, 1f, 0f, 2f, first);
        Decor.lay(-1.5f, 0.5f, 0f, 1f, 2f, 1f, 0f, 2f, second);
        check(near(u(first, -0.5f), 0f) && near(u(first, 0.5f), 0.5f), "the left square shows the left half");
        check(near(u(second, -0.5f), 0.5f) && near(u(second, 0.5f), 1f), "and the right square the right half, starting where the left one stops");
    }

    private static void paintedFrameLeavesARailRoundThePictureThatLooksOutsideIt() {
        // A board two tiles by one, each a metre of the model, a rail of a tenth of a tile, the picture a little squarer than what is left.
        float[] laid = new float[6];
        Decor.lay(0f, 2f, 0f, 1f, 2f, 1f, 0.1f, 2f, laid);
        check(near(u(laid, 0.1f), 0f) && near(u(laid, 1.9f), 1f), "the picture starts and stops a rail inside the board's ends");
        check(near(v(laid, 0.9f), 0f) && near(v(laid, 0.1f), 1f), "and a rail inside its top and its foot");
        check(u(laid, 0f) < 0f && u(laid, 2f) > 1f && v(laid, 1f) < 0f && v(laid, 0f) > 1f, "the rail itself looks outside the picture on every side");
    }

    private static void everyFrameHasAPaneInsideItsMoulding() {
        for (String name : new String[] {"frame_tall", "frame_square", "frame_wide", "canvas"}) {
            Models.Model model = Models.get(name);
            float[] pane = Decor.pane(model);
            check(pane[0] >= model.minX && pane[1] <= model.maxX && pane[2] >= model.minZ && pane[3] <= model.maxZ, name + "'s pane is within it");
            check(pane[1] - pane[0] > 0.8f * model.width() && pane[3] - pane[2] > 0.8f * model.height(), name + "'s pane is most of its face");
        }
    }

    private static void everyTelevisionHasGlassTheShapeOfATubeOnItsFront() {
        float[][] shapes = {{4f, 3f}, {16f, 9f}, {4f, 3f}};
        String[] names = {"tv", "tv_wide", "tv_console"};
        for (int i = 0; i < names.length; i++) {
            Models.Model model = Models.get(names[i]);
            float[] glass = Screens.glass(model);
            float wide = glass[1] - glass[0], high = glass[3] - glass[2];
            check(Math.abs(wide / high - shapes[i][0] / shapes[i][1]) < 0.01f, names[i] + "'s glass is " + (int) shapes[i][0] + " wide for " + (int) shapes[i][1]
                    + " high, and it is " + wide + " by " + high);
            check(glass[0] > model.minX && glass[1] < model.maxX && glass[2] > model.minZ && glass[3] < model.maxZ, names[i] + "'s glass is within its case");
            // A cabinet's knobs stand out a little further than its glass, beside it.
            check(glass[4] <= model.minY + 0.01f, names[i] + "'s glass is at the front of it, so that a picture stood proud of the glass is proud of the set");
            // A set of twenty inches and more: a tube a hand high is no television. A cabinet has its knobs and its speaker beside the tube.
            check(high >= 0.3f, names[i] + "'s glass is the height of a television's, and it is " + high);
            check(names[i].equals("tv_console") || wide * high > 0.5f * model.width() * model.height(), names[i] + "'s glass is most of its front");
        }
        Models.Model sheet = Models.get("lit_screen");
        check(near(sheet.minX, -0.5f) && near(sheet.maxX, 0.5f) && near(sheet.minZ, 0f) && near(sheet.maxZ, 1f),
                "the sheet a lit set's picture is on is a metre each way, so that its scale is the glass's size");
        check(sheet.first[Models.SCREEN + 1] - sheet.first[Models.SCREEN] == sheet.surface.length, "and all of it is screen");
    }

    private static Shows shows() {
        try (java.io.InputStream in = Decor.class.getResourceAsStream("/pictures/shows.txt")) {
            return Shows.read(new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)), "shows.txt");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static void jarHoldsAPictureForEverythingATelevisionShows() {
        Shows shows = shows();
        java.util.List<String> names = new java.util.ArrayList<>(java.util.List.of("screen-bars", "screen-grey-bars"));
        for (int show = 0; show < shows.count(); show++) {
            check(show == Shows.SNOW || shows.shots(show) > 0, "the show " + shows.name(show) + " has a shot");
            for (int shot = 0; shot < shows.shots(show); shot++) {
                names.add(shows.picture(show, shot));
            }
        }
        check(shows.count() > 80 && names.size() > 400, "the jar's table has the game's shows in it: " + shows.count() + " shows, " + names.size() + " pictures");
        for (String name : names) {
            check(Math.abs(Decor.wideForHigh(Decor.named(name)) - Screens.WIDE_FOR_HIGH) < 1e-3f, name + " is four wide for three high in the jar's index");
            check(Decor.class.getResource("/pictures/" + name + ".jpg") != null, "the jar holds " + name + ".jpg");
        }
    }

    private static void aShowCutsRoundItsShotsAndMovesAcrossEachOne() {
        Shows shows = shows();
        float[] cut = new float[Shows.CUT_FLOATS], next = new float[Shows.CUT_FLOATS];
        long step = 10_000_000L;
        for (int show = 0; show < shows.count(); show++) {
            int shots = shows.shots(show);
            if (shots == 0) {
                continue;
            }
            String name = shows.name(show);
            boolean[] seen = new boolean[shots];
            long held = 0, moved = 0, frames = 0;
            int cuts = 0;
            // From before the clock's nought to after it: its sign is no seam in a show's time.
            long from = -100_000_000_000L;
            shows.cut(show, from, cut);
            for (long at = from + step; at < 100_000_000_000L; at += step) {
                shows.cut(show, at, next);
                int shot = (int) next[Shows.SHOT];
                check(shot >= 0 && shot < shots, name + " shows one of its own shots");
                seen[shot] = true;
                float of = next[Shows.OF];
                check(of >= Shows.CLOSEST - 1e-4f && of <= 1f + 1e-4f, name + "'s picture is of no less of a shot than the closest and no more than all of it");
                check(next[Shows.ACROSS] >= of / 2f - 1e-4f && next[Shows.ACROSS] <= 1f - of / 2f + 1e-4f && next[Shows.DOWN] >= of / 2f - 1e-4f
                        && next[Shows.DOWN] <= 1f - of / 2f + 1e-4f, name + "'s picture never looks past the edge of its shot");
                boolean jumped = Math.abs(next[Shows.OF] - cut[Shows.OF]) > 0.02f || Math.abs(next[Shows.ACROSS] - cut[Shows.ACROSS]) > 0.02f
                        || Math.abs(next[Shows.DOWN] - cut[Shows.DOWN]) > 0.02f;
                if (shot != (int) cut[Shows.SHOT] || jumped) {
                    check(shots == 1 || shot != (int) cut[Shows.SHOT], name + " cuts to another shot where it has one");
                    check(cuts == 0 || held >= Shows.SHORTEST_NANOS - step && held <= Shows.LONGEST_NANOS + step, name + " holds a shot for two seconds to four and a half, and held one "
                            + held / 1e9);
                    cuts++;
                    held = 0;
                } else {
                    frames++;
                    moved += next[Shows.OF] != cut[Shows.OF] || next[Shows.ACROSS] != cut[Shows.ACROSS] || next[Shows.DOWN] != cut[Shows.DOWN] ? 1 : 0;
                }
                held += step;
                System.arraycopy(next, 0, cut, 0, cut.length);
            }
            for (boolean one : seen) {
                check(one, name + " shows every one of its shots in time");
            }
            check(cuts > 40, name + " goes on cutting, " + cuts + " times in 200 seconds");
            check(moved > frames * 0.98, name + "'s picture moves between one hundredth of a second and the next all the while a shot is held: " + moved + " of " + frames);
        }
    }

    private static void aBroadcastNobodyListedIsItsChannelsShow() {
        Shows shows = shows();
        check(shows.name(shows.aired(200, null)).equals("nnn-desk"), "a news channel with nothing said of its broadcast shows its news desk");
        check(shows.aired(200, "no such broadcast") == shows.aired(200, null), "and so it does for a broadcast the table does not list");
        check(shows.aired(98765, null) == Shows.ANY && shows.taped("no such tape") == Shows.ANY, "a channel or a tape the table does not list is the show of no one programme");
        check(shows.shots(Shows.SNOW) == 0 && shows.shots(Shows.ANY) > 0, "interference has no shots and the show of no one programme has");
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-5f;
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
