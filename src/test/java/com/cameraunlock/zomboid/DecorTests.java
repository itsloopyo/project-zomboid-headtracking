package com.cameraunlock.zomboid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tests of how the mod's own picture is laid in a frame or over a sheet on a wall. */
final class DecorTests {
    private DecorTests() {}

    static void run() {
        Checks.run("pictureOfTheFramesShapeFillsItCornerToCorner", DecorTests::pictureOfTheFramesShapeFillsItCornerToCorner);
        Checks.run("tallPictureInASquareFrameKeepsItsWidthAndTheUpperPartOfItsHeight", DecorTests::tallPictureInASquareFrameKeepsItsWidthAndTheUpperPartOfItsHeight);
        Checks.run("widePictureInATallFrameShowsItsMiddle", DecorTests::widePictureInATallFrameShowsItsMiddle);
        Checks.run("squaresOfALongPictureShowItsPartsInOrder", DecorTests::squaresOfALongPictureShowItsPartsInOrder);
        Checks.run("paintedFrameLeavesARailRoundThePictureThatLooksOutsideIt", DecorTests::paintedFrameLeavesARailRoundThePictureThatLooksOutsideIt);
        Checks.run("everyFrameHasAPaneInsideItsMoulding", DecorTests::everyFrameHasAPaneInsideItsMoulding);
        Checks.run("everyTelevisionHasGlassTheShapeOfATubeOnItsFront", DecorTests::everyTelevisionHasGlassTheShapeOfATubeOnItsFront);
        Checks.run("jarHoldsAPictureForEverythingATelevisionShows", DecorTests::jarHoldsAPictureForEverythingATelevisionShows);
        Checks.run("aShowCutsRoundItsShotsAndMovesAcrossEachOne", DecorTests::aShowCutsRoundItsShotsAndMovesAcrossEachOne);
        Checks.run("aBroadcastNobodyListedIsItsChannelsShow", DecorTests::aBroadcastNobodyListedIsItsChannelsShow);
        Checks.run("aShopsSignShowsAPictureTheShapeOfItsBoard", DecorTests::aShopsSignShowsAPictureTheShapeOfItsBoard);
        Checks.run("everyPictureInTheJarReadsAsItsColoursDotForDot", DecorTests::everyPictureInTheJarReadsAsItsColoursDotForDot);
        Checks.run("everyRoomOnTheMapIsTheKindToolsPicturesGivesIt", DecorTests::everyRoomOnTheMapIsTheKindToolsPicturesGivesIt);
        Checks.run("aRoomThatSaysNothingTakesItsKindFromItsBuilding", DecorTests::aRoomThatSaysNothingTakesItsKindFromItsBuilding);
        Checks.run("aPoolGivesEachPictureOfABuildingItsOwn", DecorTests::aPoolGivesEachPictureOfABuildingItsOwn);
        Checks.run("theIndexListsSubjectsAndPools", DecorTests::theIndexListsSubjectsAndPools);
    }

    private static void everyPictureInTheJarReadsAsItsColoursDotForDot() {
        List<String[]> pictures = new ArrayList<>();
        try (InputStream in = Decor.class.getResourceAsStream("/pictures/index.txt")) {
            BufferedReader lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = lines.readLine(); line != null; line = lines.readLine()) {
                if (!line.startsWith("=") && !line.startsWith("+") && !line.startsWith("@")) {
                    pictures.add(line.split("\t"));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        check(pictures.size() > 600, "the jar lists its pictures: " + pictures.size());
        check(!Quality.smallerPictures(), "the tests read pictures at full size");
        for (String[] picture : pictures) {
            String path = "/pictures/" + picture[0] + ".jpg";
            Decor.Read read = null;
            try {
                read = Decor.pixels(path, new float[] {Integer.parseInt(picture[1]), Integer.parseInt(picture[2])});
                java.awt.image.BufferedImage image;
                try (InputStream in = Decor.class.getResourceAsStream(path)) {
                    image = javax.imageio.ImageIO.read(in);
                }
                boolean same = read.wide() == image.getWidth() && read.high() == image.getHeight();
                int[] colours = new int[read.wide()];
                byte[] got = new byte[read.wide() * 4];
                for (int y = 0; y < read.high() && same; y++) {
                    image.getRGB(0, y, read.wide(), 1, colours, 0, read.wide());
                    read.memory().bytes.get(y * got.length, got);
                    for (int x = 0; x < read.wide() && same; x++) {
                        int argb = colours[x];
                        same = got[x * 4] == (byte) (argb >> 16) && got[x * 4 + 1] == (byte) (argb >> 8) && got[x * 4 + 2] == (byte) argb && got[x * 4 + 3] == (byte) 0xff;
                    }
                }
                check(same, path + " reads dot for dot as the colours Java reads it as");
            } catch (IOException e) {
                check(false, path + " could not be read: " + e.getMessage());
            } finally {
                if (read != null) {
                    read.memory().free();
                }
            }
        }
    }

    private static void aShopsSignShowsAPictureTheShapeOfItsBoard() {
        // The coffee shop's sign is three squares long and under a quarter as high: a picture of another shape would lose its ends.
        int low = Decor.of("location_restaurant_seahorse_01_67", "Sign", "Coffee Low", true);
        check(low != Decor.NONE, "the sign the game calls Coffee Low shows a picture of the mod's");
        check(low != Decor.NONE && Math.abs(Decor.wideForHigh(low) - 2.98f / 0.71f) < 0.05f, "and it is the shape of the board");
        check(Decor.of("location_restaurant_seahorse_01_72", "Sign", "Coffee High", true) == low, "the same sign under the game's other name for it shows the same picture");
        check(Decor.of("walls_decoration_01_16", "Flag", "Stars n Stripes", false) != Decor.NONE, "a flag hung on a wall shows one too");
        check(Decor.of("signs_one-off_08_93", "Sign", "", false) == Decor.NONE, "and a sign the mod has no picture for shows none");
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
        try (InputStream in = Decor.class.getResourceAsStream("/pictures/shows.txt")) {
            return Shows.read(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)), "shows.txt");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void jarHoldsAPictureForEverythingATelevisionShows() {
        Shows shows = shows();
        List<String> names = new ArrayList<>(List.of("screen-bars", "screen-grey-bars"));
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
        check(shows.name(shows.aired(200, null)).equals("news-desk"), "a news channel with nothing said of its broadcast shows its news desk");
        check(shows.aired(200, "no such broadcast") == shows.aired(200, null), "and so it does for a broadcast the table does not list");
        check(shows.aired(98765, null) == Shows.ANY && shows.taped("no such tape") == Shows.ANY, "a channel or a tape the table does not list is the show of no one programme");
        check(shows.shots(Shows.SNOW) == 0 && shows.shots(Shows.ANY) > 0, "interference has no shots and the show of no one programme has");
    }

    private static void everyRoomOnTheMapIsTheKindToolsPicturesGivesIt() throws IOException {
        // A room of each name on the shipped map in each sort of building it is in, with the kind tools/pictures/rooms.py gives it.
        List<String> rooms = Files.readAllLines(Path.of("src", "test", "data", "rooms.txt"), StandardCharsets.UTF_8);
        check(rooms.size() > 1000, "the test has the map's rooms: " + rooms.size());
        int wrong = 0;
        for (String line : rooms) {
            String[] parts = line.split("\t");
            List<String> building = new ArrayList<>();
            for (String name : parts[2].split(" ")) {
                building.add(Decor.said(name));
            }
            String kind = Decor.kind(Decor.said(parts[1]), building);
            if (!kind.equals(parts[0]) && wrong++ < 20) {
                check(false, parts[1] + " in a building of " + parts[2] + " is " + parts[0] + ", and Decor makes it " + kind);
            }
        }
        check(wrong == 0, "every room is the kind tools/pictures gives it: " + wrong + " are not");
    }

    private static void aRoomThatSaysNothingTakesItsKindFromItsBuilding() {
        check(Decor.kind(Decor.said("pizzawhirled"), List.of()).equals("restaurant"), "a pizza shop is a restaurant");
        check(Decor.kind(Decor.said("Gallery"), List.of()).equals("stage"), "a name is read without its capitals");
        check(Decor.kind(Decor.said("prisoncells"), List.of()).equals("prison") && Decor.kind(Decor.said("photoroom"), List.of()).equals("photostudio"),
                "prison cells and a photo studio are kinds of their own, which keep the family photographs");
        List<String> restaurant = says("office", "hall", "restaurantdining", "restaurantkitchen");
        check(Decor.kind(Decor.said("office"), restaurant).equals("restaurant") && Decor.kind(Decor.said("hall"), restaurant).equals("restaurant"),
                "the office and the hall of a restaurant are the restaurant's");
        List<String> house = says("bedroom", "kitchen", "livingroom", "office");
        check(Decor.kind(Decor.said("office"), house).equals("home") && Decor.kind(Decor.said("kitchen"), house).equals("home"), "a home's office and kitchen are the home's");
        List<String> flatOverShop = says("bedroom", "kitchen", "livingroom", "office", "grocery");
        check(Decor.kind(Decor.said("office"), flatOverShop).equals("shop") && Decor.kind(Decor.said("kitchen"), flatOverShop).equals("home"),
                "over a shop, the office is the shop's and the kitchen the flat's");
        List<String> officeBlock = says("office", "meetingroom", "breakroom", "cafeteria", "hall");
        check(Decor.kind(Decor.said("office"), officeBlock).equals("office") && Decor.kind(Decor.said("hall"), officeBlock).equals("restaurant"),
                "an office block with a cafeteria keeps its offices");
        check(Decor.kind(Decor.said("hall"), says("gym", "cafeteria", "hall")).equals("gym"), "a tie goes to the kind the building is, not its canteen");
        check(Decor.said("no such room name").equals(Decor.UNSAID), "a room name another map has says nothing of its building");
    }

    private static List<String> says(String... names) {
        List<String> said = new ArrayList<>();
        for (String name : names) {
            said.add(Decor.said(name));
        }
        return said;
    }

    private static void aPoolGivesEachPictureOfABuildingItsOwn() {
        int[] pool = {10, 11, 12, 13, 14, 15, 16, 17};
        for (int building = 0; building < 50; building++) {
            int x = 6000 + building * 37, y = 5000 + building * 53;
            Set<Integer> shown = new HashSet<>();
            for (int ordinal = 0; ordinal < pool.length; ordinal++) {
                shown.add(Decor.pick(pool, ordinal, x, y));
                check(Decor.pick(pool, ordinal, x, y) == Decor.pick(pool, ordinal, x, y), "a pick is the same every time");
            }
            check(shown.size() == pool.length, "eight pictures of one building show the pool's eight: " + shown);
        }
        Set<Integer> firsts = new HashSet<>();
        for (int building = 0; building < 50; building++) {
            firsts.add(Decor.pick(pool, 0, 6456 + building * 11, 5252 - building * 7));
        }
        check(firsts.size() == pool.length, "the same picture in fifty buildings shows every picture of the pool: " + firsts);
    }

    private static void theIndexListsSubjectsAndPools() throws IOException {
        String good = "family-00\t525\t768\tfamily\nfamily-01\t525\t768\tfamily\npainting-lakeside\t1024\t427\npool-restaurant-00\t525\t768\n"
                + "=walls_decoration_02_0\tfamily-00\n@family\trestaurant\tpool-restaurant-00 painting-lakeside\n";
        Decor.Index read = Decor.read(new BufferedReader(new StringReader(good)), "test");
        check("family".equals(read.subjects.get(1)) && read.subjects.get(2) == null, "a picture line carries its subject where it has one");
        check(read.ordinals.get(0) == 0 && read.ordinals.get(1) == 1, "each picture of a subject is numbered among them in order");
        int[] pool = read.pools.get("family\trestaurant");
        check(pool != null && pool.length == 2 && pool[0] == 3 && pool[1] == 2, "a pool lists its pictures by number");
        for (String bad : new String[] {good + "@family\tbar\tno-such-picture\n", "@family\tbar\tfamily-00\nfamily-00\t525\t768\tfamily\n", good + "@family\trestaurant\tfamily-01\n"}) {
            boolean threw = false;
            try {
                Decor.read(new BufferedReader(new StringReader(bad)), "test");
            } catch (IOException expected) {
                threw = true;
            }
            check(threw, "an index whose pool names a picture not listed before it, or pools one subject twice in a kind, is refused");
        }
        Decor.Index jar;
        try (InputStream in = Decor.class.getResourceAsStream("/pictures/index.txt")) {
            jar = Decor.read(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)), "/pictures/index.txt");
        }
        for (int picture = 0; picture < jar.names.size(); picture++) {
            String name = jar.names.get(picture), subject = jar.subjects.get(picture);
            check(name.startsWith("family-") == "family".equals(subject) && name.startsWith("certificate-") == "certificate".equals(subject),
                    name + " has the subject of its name: " + subject);
        }
        check(jar.pools.containsKey("certificate\trestaurant") && jar.pools.containsKey("family\tmedical"), "the jar pools certificates for a restaurant and family photographs for a clinic");
        for (String pooled : jar.pools.keySet()) {
            check(Decor.KINDS.contains(pooled.split("\t")[1]), "the jar pools pictures for a kind of room there is: " + pooled);
        }
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-5f;
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}
