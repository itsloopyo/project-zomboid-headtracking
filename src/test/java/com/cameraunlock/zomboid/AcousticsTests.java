package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.HashMap;
import java.util.Map;

/** Tests of how the walls muffle what is heard and how a space rings. */
final class AcousticsTests {
    private AcousticsTests() {}

    static void run() {
        Checks.run("aSoundInTheListenersRoomIsNotMuffled", AcousticsTests::aSoundInTheListenersRoomIsNotMuffled);
        Checks.run("aSoundBehindAWallLosesWhatTheWallTakes", AcousticsTests::aSoundBehindAWallLosesWhatTheWallTakes);
        Checks.run("aDoorwayNearByIsTheQuieterWayRound", AcousticsTests::aDoorwayNearByIsTheQuieterWayRound);
        Checks.run("aShutDoorTakesWhatADoorTakes", AcousticsTests::aShutDoorTakesWhatADoorTakes);
        Checks.run("aDoorwayFarOffLeavesTheWallTheQuieterWay", AcousticsTests::aDoorwayFarOffLeavesTheWallTheQuieterWay);
        Checks.run("outOfDoorsAHouseInTheWayIsGoneRound", AcousticsTests::outOfDoorsAHouseInTheWayIsGoneRound);
        Checks.run("aFloorMufflesAndAStairWellLetsSoundUp", AcousticsTests::aFloorMufflesAndAStairWellLetsSoundUp);
        Checks.run("twoWallsTakeTwiceAndNoSoundIsLostEntirely", AcousticsTests::twoWallsTakeTwiceAndNoSoundIsLostEntirely);
        Checks.run("theMoreMuffledTheLowerTheCutOff", AcousticsTests::theMoreMuffledTheLowerTheCutOff);
        Checks.run("roomsRingByTheirSizeTheirSurfacesAndTheirOpenings", AcousticsTests::roomsRingByTheirSizeTheirSurfacesAndTheirOpenings);
        Checks.run("theOpenAirRingsOnlyWithBuildingsRound", AcousticsTests::theOpenAirRingsOnlyWithBuildingsRound);
        Checks.run("roomsAreHardOrSoftByWhatTheMapCallsThem", AcousticsTests::roomsAreHardOrSoftByWhatTheMapCallsThem);
    }

    private static final float NEAR = 0.01f;

    /** A plan read for sound: an edge sight finds solid is a wall, any other open, unless said otherwise. */
    static final class Heard implements Acoustics.Walls {
        final RoomSightTests.Plan plan;
        private final Map<String, Integer> sounds = new HashMap<>();

        Heard(RoomSightTests.Plan plan) {
            this.plan = plan;
        }

        Heard sound(int x, int y, int z, boolean north, int kind) {
            sounds.put(x + "," + y + "," + z + "," + north, kind);
            return this;
        }

        @Override
        public int heard(int x, int y, int z, boolean north) {
            Integer said = sounds.get(x + "," + y + "," + z + "," + north);
            return said != null ? said : plan.edge(x, y, z, north) == RoomSight.SOLID ? Acoustics.WALL : Acoustics.OPEN;
        }

        @Override
        public long region(int x, int y, int z) {
            return plan.region(x, y, z);
        }

        @Override
        public int edge(int x, int y, int z, boolean north) {
            return plan.edge(x, y, z, north);
        }

        @Override
        public boolean floored(int x, int y, int z) {
            return plan.floored(x, y, z);
        }

        @Override
        public float floorUp(float x, float y, int z) {
            return plan.floorUp(x, y, z);
        }

        @Override
        public long building(int x, int y, int z) {
            return plan.building(x, y, z);
        }
    }

    private static Acoustics.Surroundings listen(Heard heard, float x, float y, int z) {
        Acoustics.Surroundings around = new Acoustics.Surroundings();
        around.listen(heard, heard.plan.openings(), x, y, z);
        return around;
    }

    /** Two rooms side by side, 0..4 and 5..9 across, 0..9 down, a wall between them on x = 5. */
    private static RoomSightTests.Plan twoRooms() {
        return new RoomSightTests.Plan().room(1, 0, 0, 0, 4, 9).room(2, 0, 5, 0, 9, 9);
    }

    private static void aSoundInTheListenersRoomIsNotMuffled() {
        Acoustics.Surroundings around = listen(new Heard(twoRooms()), 1.5f, 1.5f, 0);
        check(around.muffled(4.5f, 8.5f, 0f) == 0f, "a sound across the listener's own room is heard as it is");
    }

    private static void aSoundBehindAWallLosesWhatTheWallTakes() {
        Acoustics.Surroundings around = listen(new Heard(twoRooms()), 4.5f, 4.5f, 0);
        float muffled = around.muffled(5.5f, 4.5f, 0f);
        check(Math.abs(muffled - Acoustics.THROUGH[Acoustics.WALL]) < NEAR, "a sound a tile away through a wall with no way round loses the wall's 9 dB: " + muffled);
    }

    private static void aDoorwayNearByIsTheQuieterWayRound() {
        // A doorway in the wall at its far end, the west edge of (5, 9): its middle is (5, 9.5).
        Heard heard = new Heard(twoRooms().opening(5, 9, 0, false, RoomSight.SEE));
        Acoustics.Surroundings around = listen(heard, 4.5f, 8.5f, 0);
        float muffled = around.muffled(5.5f, 8.5f, 0f);
        // Round: 1.118 to the doorway and 1.118 on, 1 straight, a turn: 0.8 * 1.236 + 1.5.
        float round = Acoustics.PER_TILE * (2f * (float) Math.hypot(0.5, 1.0) - 1f) + Acoustics.BEND;
        check(Math.abs(muffled - round) < 0.05f, "a sound across the wall goes round through the doorway beside it, losing " + round + " dB: " + muffled);
    }

    private static void aShutDoorTakesWhatADoorTakes() {
        Heard heard = new Heard(twoRooms().opening(5, 9, 0, false, RoomSight.SHUT)).sound(5, 9, 0, false, Acoustics.DOOR);
        Acoustics.Surroundings around = listen(heard, 4.5f, 8.5f, 0);
        float muffled = around.muffled(5.5f, 8.5f, 0f);
        float round = Acoustics.PER_TILE * (2f * (float) Math.hypot(0.5, 1.0) - 1f) + Acoustics.BEND + Acoustics.THROUGH[Acoustics.DOOR];
        check(Math.abs(muffled - round) < 0.05f, "through the shut door it loses the door's 6 dB on top, still less than the wall's 9: " + muffled);
    }

    private static void aDoorwayFarOffLeavesTheWallTheQuieterWay() {
        Heard heard = new Heard(twoRooms().opening(5, 9, 0, false, RoomSight.SEE));
        Acoustics.Surroundings around = listen(heard, 4.5f, 0.5f, 0);
        float muffled = around.muffled(5.5f, 0.5f, 0f);
        check(Math.abs(muffled - Acoustics.THROUGH[Acoustics.WALL]) < NEAR, "with the doorway nine tiles off, straight through the wall is the quieter way: " + muffled);
    }

    private static void outOfDoorsAHouseInTheWayIsGoneRound() {
        // A house 10..19 across, 0..9 down; the listener west of it, the sound east of it, both out of doors.
        Heard heard = new Heard(new RoomSightTests.Plan().room(1, 0, 10, 0, 19, 9));
        Acoustics.Surroundings around = listen(heard, 5.5f, 4.5f, 0);
        check(around.muffled(25.5f, 4.5f, 0f) == Acoustics.AROUND_A_BUILDING, "a house between two places out of doors takes what going round it does, not its two walls");
        check(around.muffled(5.5f, 20.5f, 0f) == 0f, "with nothing in between, a sound out of doors is heard as it is");
        check(around.muffled(5.5f, 20.5f, 1f) == 0f, "and up a level in the open air, a balcony or a roof, too: no floor stands between");
        // A line through a corner of the squares, x and y crossings at once, once walked past its end for ever.
        Acoustics.Surroundings open = listen(new Heard(new RoomSightTests.Plan().room(1, 0, 100, 100, 101, 101)), 2.5f, 2.5f, 0);
        check(open.muffled(5.0f, 0.0f, 0f) == 0f, "a line from a square's middle through a corner ends at the sound's square");
    }

    private static void aFloorMufflesAndAStairWellLetsSoundUp() {
        RoomSightTests.Plan plan = new RoomSightTests.Plan().room(1, 0, 0, 0, 9, 9).room(2, 1, 0, 0, 9, 9);
        Acoustics.Surroundings around = listen(new Heard(plan), 4.5f, 4.5f, 0);
        float through = around.muffled(4.5f, 4.5f, 1f);
        check(Math.abs(through - Acoustics.FLOOR) < NEAR, "a sound on the floor above loses the floor's 10 dB: " + through);
        plan.hole(4, 5, 1);
        around = listen(new Heard(plan), 4.5f, 4.5f, 0);
        float well = around.muffled(4.5f, 4.5f, 1f);
        check(well < through, "with the well of a stair beside the listener it comes down that way, quieter: " + well);
    }

    private static void twoWallsTakeTwiceAndNoSoundIsLostEntirely() {
        RoomSightTests.Plan plan = new RoomSightTests.Plan().room(1, 0, 0, 0, 4, 9).room(2, 0, 5, 0, 9, 9).room(3, 0, 10, 0, 14, 9);
        Acoustics.Surroundings around = listen(new Heard(plan), 4.5f, 4.5f, 0);
        float two = around.muffled(10.5f, 4.5f, 0f);
        check(Math.abs(two - 2f * Acoustics.THROUGH[Acoustics.WALL]) < NEAR, "through two walls a sound loses both: " + two);
        RoomSightTests.Plan many = new RoomSightTests.Plan();
        for (int i = 0; i < 6; i++) {
            many.room(i + 1, 0, i * 3, 0, i * 3 + 2, 9);
        }
        around = listen(new Heard(many), 0.5f, 4.5f, 0);
        check(around.muffled(16.5f, 4.5f, 0f) == Acoustics.MOST, "however many walls, a sound is muffled no further than MOST");
    }

    private static void theMoreMuffledTheLowerTheCutOff() {
        check(Acoustics.cutoff(0f) == 20000f, "a sound not muffled is not cut");
        check(Acoustics.cutoff(Acoustics.THROUGH[Acoustics.GLASS]) > Acoustics.cutoff(Acoustics.THROUGH[Acoustics.WALL]), "glass lets more of the top through than a wall");
        check(Math.abs(Acoustics.cutoff(Acoustics.THROUGH[Acoustics.WALL]) - 884f) < 1f, "through a wall what is above about 880 Hz goes");
        check(Acoustics.cutoff(Acoustics.MOST) == 250f, "and no cut-off is lower than 250 Hz: " + Acoustics.cutoff(Acoustics.MOST));
    }

    private static void roomsRingByTheirSizeTheirSurfacesAndTheirOpenings() {
        Acoustics.Ring bathroom = new Acoustics.Ring(), soft = new Acoustics.Ring(), warehouse = new Acoustics.Ring(), open = new Acoustics.Ring();
        Acoustics.room(6, Acoustics.absorption("bathroom"), 0f, bathroom);
        Acoustics.room(6, Acoustics.absorption("bedroom"), 0f, soft);
        Acoustics.room(600, Acoustics.absorption("warehouse"), 0f, warehouse);
        Acoustics.room(6, Acoustics.absorption("bathroom"), 2f, open);
        check(bathroom.decayMs > soft.decayMs * 2f, "a tiled room rings longer than a soft one of its size: " + bathroom.decayMs + " and " + soft.decayMs);
        check(bathroom.wetDb > soft.wetDb, "and louder");
        check(warehouse.decayMs > bathroom.decayMs * 1.5f, "a warehouse rings longer than a bathroom: " + warehouse.decayMs);
        check(warehouse.earlyMs > bathroom.earlyMs * 2f, "and its surfaces answer later, under a ceiling a level high: " + warehouse.earlyMs + " and " + bathroom.earlyMs);
        check(open.decayMs < bathroom.decayMs, "a doorway left open lets the ringing out sooner: " + open.decayMs);
        check(soft.highCutHz < bathroom.highCutHz && soft.hfDecayPercent < bathroom.hfDecayPercent, "soft things take the top off the ringing");
        for (Acoustics.Ring ring : new Acoustics.Ring[] {bathroom, soft, warehouse, open}) {
            check(ring.decayMs >= 150f && ring.decayMs <= 4000f && ring.wetDb >= -22f && ring.wetDb <= -3f, "every room's ring is in its bounds: " + ring);
        }
        check(warehouse.wetDb > -6f, "a warehouse rings loud enough to be heard over a shot's own tail: " + warehouse.wetDb);
        check(open.wetDb < bathroom.wetDb, "an open doorway lets some of the ringing out: " + open.wetDb + " and " + bathroom.wetDb);
        for (int squares : new int[] {4, 12, 40, 200, 2000}) {
            Acoustics.Ring hard = new Acoustics.Ring(), medium = new Acoustics.Ring(), softer = new Acoustics.Ring();
            Acoustics.room(squares, Acoustics.absorption("garage"), 0f, hard);
            Acoustics.room(squares, Acoustics.absorption("hall"), 0f, medium);
            Acoustics.room(squares, Acoustics.absorption("bedroom"), 0f, softer);
            check(hard.wetDb >= medium.wetDb && medium.wetDb > softer.wetDb && (hard.wetDb > medium.wetDb || hard.wetDb == -3f), squares + " squares: harder rings louder, or both at the most, " + hard.wetDb + ", " + medium.wetDb + ", " + softer.wetDb);
        }
    }

    private static void theOpenAirRingsOnlyWithBuildingsRound() {
        Acoustics.Ring field = new Acoustics.Ring(), street = new Acoustics.Ring();
        Acoustics.outdoors(0f, 30f, field);
        Acoustics.outdoors(0.75f, 8f, street);
        check(field.wetDb == -80f, "open ground does not ring");
        check(street.wetDb > -24f && street.decayMs > field.decayMs, "between buildings it does: " + street);
        check(Math.abs(street.earlyMs - 2f * 8f / 343f * 1000f) < 0.5f, "coming back from the buildings eight tiles off after the sound has gone there and back: " + street.earlyMs);
    }

    private static void roomsAreHardOrSoftByWhatTheMapCallsThem() {
        check(Acoustics.absorption("bathroom") < Acoustics.absorption("kitchenstorage") + 0.001f, "a bathroom is hard");
        check(Acoustics.absorption("bedroom") > Acoustics.absorption("hall"), "a bedroom is softer than a hall");
        check(Acoustics.absorption(null) == Acoustics.absorption("hall"), "a room with no name is neither");
    }
}
