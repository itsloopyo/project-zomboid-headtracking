package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests of what the eye has a line to and of what a build leaves out of a room: see Sightline.
 *
 * The house they walk through is a hall, squares 1 to 4 across and 1 to 4 down, and a bathroom
 * east of it, squares 5 and 6 across and 1 and 2 down. A wall stands between the two with a
 * door in it at square 5, 2, the bathroom has a window in its north wall at square 6, 1, and
 * the game has never shown the bathroom.
 */
final class SightlineTests {
    private SightlineTests() {}

    static void run() {
        Checks.run("aShutDoorHidesTheRoomAndAnOpenOneShowsWhatIsBehindIt", SightlineTests::aShutDoorHidesTheRoomAndAnOpenOneShowsWhatIsBehindIt);
        Checks.run("aLeanRoundTheDoorFrameOpensTheLine", SightlineTests::aLeanRoundTheDoorFrameOpensTheLine);
        Checks.run("aWindowShowsTheRoomAndADrawnCurtainHidesIt", SightlineTests::aWindowShowsTheRoomAndADrawnCurtainHidesIt);
        Checks.run("everythingTheEyeHasALineToIsBuiltOnTheWalkIn", SightlineTests::everythingTheEyeHasALineToIsBuiltOnTheWalkIn);
        Checks.run("whereTheGameShowsNoMoreARoomIsFilledAsTheGameShowsIt", SightlineTests::whereTheGameShowsNoMoreARoomIsFilledAsTheGameShowsIt);
        Checks.run("theGamesOwnViewLeavesNothingOut", SightlineTests::theGamesOwnViewLeavesNothingOut);
        Checks.run("aRoomNotShownIsFilledNearTheEyeAndEmptyFarOff", SightlineTests::aRoomNotShownIsFilledNearTheEyeAndEmptyFarOff);
        Checks.run("aLineThatEndsWhereItStartsIsClear", SightlineTests::aLineThatEndsWhereItStartsIsClear);
        Checks.run("theLightOfRoomsNotShownMovesInStepsAndHoldsBetweenThem", SightlineTests::theLightOfRoomsNotShownMovesInStepsAndHoldsBetweenThem);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** A thing standing on a square. */
    private record Prop(String name, int x, int y) {
        boolean inTheBathroom() {
            return x >= 5 && x <= 6 && y >= 1 && y <= 2;
        }
    }

    private static final List<Prop> PROPS = List.of(new Prop("sink", 5, 1), new Prop("toilet", 6, 2), new Prop("towel on the floor", 6, 1), new Prop("hall table", 1, 4));

    /** The house: the edges that hide what is beyond them, each the north or the west edge of a square. */
    private static final class House implements GridTrace.Edges {
        private final Set<String> hidden = new HashSet<>();

        House(boolean doorOpen, boolean curtainDrawn) {
            for (int x = 1; x <= 6; x++) {
                // The north walls of the hall and the bathroom, the south wall of the hall and of the bathroom.
                if (x != 6 || curtainDrawn) {
                    hidden.add(north(x, 1));
                }
                hidden.add(north(x, x <= 4 ? 5 : 3));
            }
            for (int y = 1; y <= 4; y++) {
                hidden.add(west(1, y));
                // Between the hall and what is east of it: the bathroom's wall with its door, then the hall's own east wall.
                if (y != 2 || !doorOpen) {
                    hidden.add(west(5, y));
                }
            }
            hidden.add(west(7, 1));
            hidden.add(west(7, 2));
        }

        private static String north(int x, int y) {
            return "n" + x + "," + y;
        }

        private static String west(int x, int y) {
            return "w" + x + "," + y;
        }

        @Override
        public boolean blocked(int fromX, int fromY, int toX, int toY) {
            return hidden.contains(fromY != toY ? north(fromX, Math.max(fromY, toY)) : west(Math.max(fromX, toX), fromY));
        }

        boolean clearTo(float eyeX, float eyeY, Prop prop) {
            return Sightline.clear(new GridTrace(), this, eyeX, eyeY, prop.x + 0.5f, prop.y + 0.5f);
        }
    }

    private static Prop prop(String name) {
        for (Prop each : PROPS) {
            if (each.name.equals(name)) {
                return each;
            }
        }
        throw new IllegalArgumentException(name);
    }

    private static void aShutDoorHidesTheRoomAndAnOpenOneShowsWhatIsBehindIt() {
        House shut = new House(false, false), open = new House(true, false);
        for (Prop each : PROPS) {
            if (each.inTheBathroom()) {
                check(!shut.clearTo(2.5f, 2.5f, each), "from the hall, the " + each.name + " is behind the shut door");
            }
        }
        check(shut.clearTo(2.5f, 2.5f, prop("hall table")), "the hall's own table is in sight from the hall");
        check(open.clearTo(2.5f, 2.5f, prop("toilet")), "with the door open the toilet across the doorway is in sight from the hall");
        check(!open.clearTo(2.5f, 4.3f, prop("sink")), "and the sink in the corner beside the door is not, from the far end of the hall");
    }

    private static void aLeanRoundTheDoorFrameOpensTheLine() {
        House open = new House(true, false);
        check(!open.clearTo(4.5f, 3.5f, prop("toilet")), "standing beside the doorway the frame is in the way of the toilet");
        check(open.clearTo(4.3f, 2.7f, prop("toilet")), "and with the eye leant into the doorway the line to it is open, though the body has not moved a square");
    }

    private static void aWindowShowsTheRoomAndADrawnCurtainHidesIt() {
        House bare = new House(false, false), curtained = new House(false, true);
        check(bare.clearTo(6.5f, -1.5f, prop("towel on the floor")), "from the garden the window shows what lies under it in the room");
        check(bare.clearTo(6.5f, -1.5f, prop("toilet")), "and what stands further into the room behind that");
        check(!bare.clearTo(4.4f, -1.5f, prop("sink")), "but not what stands behind the wall beside the window");
        check(!curtained.clearTo(6.5f, -1.5f, prop("toilet")), "and a drawn curtain hides the room");
    }

    /** What a build of the house holds, by Sightline's rule, with the game having shown everything but the bathroom. */
    private static List<Prop> built(boolean asTheGameShows, boolean gamesView) {
        return built(asTheGameShows, gamesView, true);
    }

    /** As addSquare decides it, with the chunk near enough to fill the rooms the game has not shown or not. */
    private static List<Prop> built(boolean asTheGameShows, boolean gamesView, boolean near) {
        List<Prop> built = new ArrayList<>();
        for (Prop each : PROPS) {
            boolean shown = !each.inTheBathroom();
            boolean leftOut = Sightline.leftOut(asTheGameShows, gamesView, true, shown);
            if (!Sightline.leftEmpty(leftOut, !gamesView && !shown, near)) {
                built.add(each);
            }
        }
        return built;
    }

    private static void everythingTheEyeHasALineToIsBuiltOnTheWalkIn() {
        List<Prop> built = built(false, false);
        int inSight = 0;
        for (boolean doorOpen : new boolean[] {false, true}) {
            House house = new House(doorOpen, false);
            // Down the hall to the door, through it and into the room, a tenth of a square at a time, and from the garden to the window.
            for (int step = 0; step <= 50; step++) {
                float eyeX = 1.5f + step * 0.1f, eyeY = 2.5f;
                if (!doorOpen && eyeX >= 5f) {
                    break;
                }
                for (Prop each : PROPS) {
                    if (house.clearTo(eyeX, eyeY, each)) {
                        inSight += each.inTheBathroom() ? 1 : 0;
                        check(built.contains(each), "the " + each.name + " is in sight from " + eyeX + ", " + eyeY + " and is built, the game not having shown its room");
                    }
                }
            }
            for (int step = 0; step <= 20; step++) {
                for (Prop each : PROPS) {
                    if (house.clearTo(6.5f, -2.5f + step * 0.1f, each)) {
                        inSight += each.inTheBathroom() ? 1 : 0;
                        check(built.contains(each), "the " + each.name + " is in sight through the window and is built");
                    }
                }
            }
        }
        check(inSight > 50, "the walk had the bathroom's things in sight: " + inSight + " times");
    }

    private static void whereTheGameShowsNoMoreARoomIsFilledAsTheGameShowsIt() {
        List<Prop> built = built(true, false);
        check(!built.contains(prop("toilet")) && !built.contains(prop("sink")) && !built.contains(prop("towel on the floor")),
                "where players can fight, what stands in a room the game has not shown is left out");
        check(built.contains(prop("hall table")), "and what stands in a room it has shown is built");
        check(!Sightline.leftOut(true, false, false, false), "and nothing out of doors is left out, shown or not");
    }

    private static void aRoomNotShownIsFilledNearTheEyeAndEmptyFarOff() {
        check(built(false, false, true).contains(prop("toilet")), "near the eye the toilet in the room the game has not shown is built");
        List<Prop> far = built(false, false, false);
        check(!far.contains(prop("toilet")) && !far.contains(prop("sink")), "far off what stands in that room is not");
        check(far.contains(prop("hall table")), "and what stands in a room the game has shown is, however far off");
        check(built(false, true, false).size() == PROPS.size(), "as the game's own view nothing is left out far off either");
        check(!built(true, false, true).contains(prop("toilet")), "where players can fight the room is empty however near");
    }

    private static void theGamesOwnViewLeavesNothingOut() {
        check(built(true, true).size() == PROPS.size() && built(false, true).size() == PROPS.size(),
                "as the game's own view a room not shown is hidden by the engine's light and nothing is left out of it");
    }

    private static void theLightOfRoomsNotShownMovesInStepsAndHoldsBetweenThem() {
        check(Sightline.stepped(0.887f, -1f) == 0.875f, "the first light found is kept on the nearest step: " + Sightline.stepped(0.887f, -1f));
        float kept = 0.875f;
        int moves = 0;
        // A light that wanders about what is kept, as the squares in sight change, moves nothing.
        for (int i = 0; i < 200; i++) {
            float found = 0.875f + 0.05f * (float) Math.sin(i * 0.7);
            float next = Sightline.stepped(found, kept);
            moves += next == kept ? 0 : 1;
            kept = next;
        }
        check(moves == 0, "a light within a step of the one kept leaves it as it is: moved " + moves + " times");
        check(Sightline.stepped(0.5f, 0.875f) == 0.5f, "and one a room's worth darker is taken");
        check(Sightline.stepped(0.875f + Sightline.LIGHT_STEP, 0.875f) == 0.9375f, "as is one a whole step off");
        check(Sightline.stepped(0f, -1f) == 0f && Sightline.stepped(0f, 0.25f) == 0f, "no light at all is a light like any other");
    }

    private static void aLineThatEndsWhereItStartsIsClear() {
        House shut = new House(false, false);
        check(Sightline.clear(new GridTrace(), shut, 5.5f, 1.5f, 5.5f, 1.5f), "a thing at the eye is in sight");
        check(Sightline.clear(new GridTrace(), shut, 5.2f, 1.2f, 5.8f, 1.8f), "and so is one on the eye's own square");
    }
}
