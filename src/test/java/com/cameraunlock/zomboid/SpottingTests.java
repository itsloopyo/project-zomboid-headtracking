package com.cameraunlock.zomboid;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import zombie.iso.BuildingDef;
import zombie.iso.IsoCell;
import zombie.iso.IsoChunk;
import zombie.iso.IsoGridSquare;
import zombie.iso.RoomDef;
import zombie.iso.areas.IsoRoom;
import zombie.meta.Meta;
import zombie.network.GameClient;

/** The rooms Spotting has the game spot and the squares it has the game mark seen, from what the first person view sees. */
public final class SpottingTests {
    private SpottingTests() {}

    static void run() {
        Checks.run("aRoomInSightIsSpottedFromASquareThePlayerHasSeen", SpottingTests::aRoomInSightIsSpottedFromASquareThePlayerHasSeen);
        Checks.run("whetherARoomIsSpottedIsTheGamesToSay", SpottingTests::whetherARoomIsSpottedIsTheGamesToSay);
        Checks.run("aRoomTheGameCouldNotSpotFromWhereThePlayerStandsIsNotHandedToIt", SpottingTests::aRoomTheGameCouldNotSpotFromWhereThePlayerStandsIsNotHandedToIt);
        Checks.run("aRoomOfAnotherBuildingIsHandedToTheGameOnlyWithinTenTiles", SpottingTests::aRoomOfAnotherBuildingIsHandedToTheGameOnlyWithinTenTiles);
        Checks.run("aRoomOutOfSightIsLeftAlone", SpottingTests::aRoomOutOfSightIsLeftAlone);
        Checks.run("aSquareIsMarkedAsTheLightFindsItHasTurnedSeen", SpottingTests::aSquareIsMarkedAsTheLightFindsItHasTurnedSeen);
        Checks.run("aTurnAnotherReadFoundIsMarkedWhereTheViewSeesIt", SpottingTests::aTurnAnotherReadFoundIsMarkedWhereTheViewSeesIt);
        Checks.run("aTurnWaitingIsLetGoWithItsChunk", SpottingTests::aTurnWaitingIsLetGoWithItsChunk);
        Checks.run("theTurnsWaitingAreMarkedWhenThePictureStopsCovering", SpottingTests::theTurnsWaitingAreMarkedWhenThePictureStopsCovering);
        Checks.run("nothingIsCarriedIntoTheNextSave", SpottingTests::nothingIsCarriedIntoTheNextSave);
        Checks.run("aTurnFoundAfterTheTurnsWereHandedBackIsNotKept", SpottingTests::aTurnFoundAfterTheTurnsWereHandedBackIsNotKept);
        Checks.run("aRoundGoesOverWhatThePictureSees", SpottingTests::aRoundGoesOverWhatThePictureSees);
        Checks.run("aRoomSeenIntoWhileARoundGoesOnIsGoneOverByTheNext", SpottingTests::aRoomSeenIntoWhileARoundGoesOnIsGoneOverByTheNext);
        Checks.run("theLightLeavesRoomsAndMarksToTheRoundsOnlyUnderThePicture", SpottingTests::theLightLeavesRoomsAndMarksToTheRoundsOnlyUnderThePicture);
        Checks.run("aLightShapedLikeTheGamesHandsItsCallsToTheHooks", SpottingTests::aLightShapedLikeTheGamesHandsItsCallsToTheHooks);
        Checks.run("theOpenAirOfAChunkDrawnIsMarkedAndItsRoomsAreNot", SpottingTests::theOpenAirOfAChunkDrawnIsMarkedAndItsRoomsAreNot);
        Checks.run("aClientSpotsRoomsAndMarksNothing", SpottingTests::aClientSpotsRoomsAndMarksNothing);
        Checks.run("aSquareFoundSeenIsNotAskedAboutAgain", SpottingTests::aSquareFoundSeenIsNotAskedAboutAgain);
        Checks.run("aSquareARoomCouldBeSpottedFromIsAskedEveryRoundAndOneThatCouldOnlyTurnEveryFourth", SpottingTests::aSquareARoomCouldBeSpottedFromIsAskedEveryRoundAndOneThatCouldOnlyTurnEveryFourth);
        Checks.run("aChunkLoadedAgainIsAskedAboutAgain", SpottingTests::aChunkLoadedAgainIsAskedAboutAgain);
        Checks.run("aFramesShareOfARoundEndsOnceItsTimeIsUp", SpottingTests::aFramesShareOfARoundEndsOnceItsTimeIsUp);
        Checks.run("aRoomIsGoneOverAChunkAtATime", SpottingTests::aRoomIsGoneOverAChunkAtATime);
        Checks.run("theRoomsThatCouldBeSpottedAreGoneOverFirst", SpottingTests::theRoomsThatCouldBeSpottedAreGoneOverFirst);
        Checks.run("aRoundTheFramesWereTooSlowForGoesOnOverTheNext", SpottingTests::aRoundTheFramesWereTooSlowForGoesOnOverTheNext);
        Checks.run("noLevelThePictureReadsNoLightOnIsAskedAbout", SpottingTests::noLevelThePictureReadsNoLightOnIsAskedAbout);
        Checks.run("onlyTheChunksTheConeTakesInAreSeen", SpottingTests::onlyTheChunksTheConeTakesInAreSeen);
        Checks.run("passesComeFourTimesASecondWhateverTheFrames", SpottingTests::passesComeFourTimesASecondWhateverTheFrames);
        Checks.run("aCallReplacedHandsItsHookWhatItsMethodBelongsTo", SpottingTests::aCallReplacedHandsItsHookWhatItsMethodBelongsTo);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** The building every test's room is in, but for those a test puts in one of its own. */
    private static final BuildingDef HOUSE = new BuildingDef(1L, 0, 0, 1000, 1000);

    /** A room of a cell, its id, and its squares, w by h from x, y on a level. */
    private record Room(long id, RoomDef def, IsoGridSquare[] squares) {
        static Room of(IsoCell cell, long id, int x, int y, int w, int h, int level) {
            return of(cell, HOUSE, id, x, y, w, h, level);
        }

        /** The same, in a building. */
        static Room of(IsoCell cell, BuildingDef building, long id, int x, int y, int w, int h, int level) {
            RoomDef def = new RoomDef(level);
            def.building = building;
            def.rects.add(new RoomDef.RoomRect(x, y, w, h));
            IsoRoom room = new IsoRoom(def);
            IsoGridSquare[] squares = new IsoGridSquare[w * h];
            for (int i = 0; i < squares.length; i++) {
                squares[i] = cell.square(x + i % w, y + i / w, level);
                squares[i].room(room, id);
            }
            return new Room(id, def, squares);
        }
    }

    private static RoomSight.Seen inSight(Room... rooms) {
        RoomSight.Seen seen = new RoomSight.Seen();
        seen.clear();
        for (Room room : rooms) {
            seen.add(room.id());
        }
        return seen;
    }

    /** A player's spotting under the picture, handed each square the light finds turned seen as the game's light hands it there. */
    private static Spotting spotting() {
        return spotting(Spotting.SLICE_NANOS);
    }

    /** The same, with the time a frame's share of a round may take: none, and each frame's share is one unit, but for the frame that begins a round, which does none. */
    private static Spotting spotting(long sliceNanos) {
        Spotting spotting = new Spotting(0, sliceNanos);
        spotting.covering();
        IsoGridSquare.onTurn = square -> Spotting.squareSeenByLight(Meta.instance, square, spotting);
        return spotting;
    }

    private static void pass(IsoCell cell, RoomSight.Seen rooms, List<IsoChunk> chunks, Room... known) {
        pass(spotting(), cell, rooms, chunks, known);
    }

    /** A whole round, for a player on the first square of the first room known, in its building, or out of doors where no room is known. */
    private static void pass(Spotting spotting, IsoCell cell, RoomSight.Seen rooms, List<IsoChunk> chunks, Room... known) {
        IsoGridSquare at = known.length == 0 ? null : known[0].squares()[0];
        spotting.pass(cell, rooms, defs(known)::get, chunks, at == null ? 0f : at.getX() + 0.5f, at == null ? 0f : at.getY() + 0.5f, at == null ? 0f : at.getZ(),
                at == null ? null : known[0].def().getBuilding());
    }

    private static HashMap<Long, RoomDef> defs(Room... known) {
        HashMap<Long, RoomDef> defs = new HashMap<>();
        for (Room room : known) {
            defs.put(room.id(), room.def());
        }
        return defs;
    }

    /** The chunk drawn that holds a square. */
    private static IsoChunk drawn(IsoCell cell, IsoGridSquare square) {
        return cell.getChunkForGridSquare(square.getX(), square.getY(), square.getZ());
    }

    private static int roomChecks(Room room) {
        int checks = 0;
        for (IsoGridSquare square : room.squares()) {
            checks += square.roomChecks;
        }
        return checks;
    }

    private static int asked(IsoGridSquare... squares) {
        int asked = 0;
        for (IsoGridSquare square : squares) {
            asked += square.seenAsked;
        }
        return asked;
    }

    private static void aRoomInSightIsSpottedFromASquareThePlayerHasSeen() {
        IsoCell cell = new IsoCell();
        Room kitchen = Room.of(cell, 7L, 10, 10, 3, 2, 0);
        kitchen.squares()[4].seen(true);
        kitchen.squares()[4].inReach(true);
        kitchen.squares()[5].seen(true);
        kitchen.squares()[5].inReach(true);
        pass(cell, inSight(kitchen), List.of(), kitchen);
        check(kitchen.def().explored, "a room the view sees into is spotted from a square of it the player has seen");
        check(kitchen.squares()[4].roomChecks == 1 && roomChecks(kitchen) == 1, "from the first such square: once the room is explored the game makes nothing more of it");
        check(kitchen.squares()[0].roomChecks == 0, "and never from a square the player has not seen");
        Room upstairs = Room.of(cell, 18L, 10, 10, 1, 1, 2), downstairs = Room.of(cell, 19L, 10, 10, 1, 1, 1);
        upstairs.squares()[0].seen(true);
        upstairs.squares()[0].inReach(true);
        pass(cell, inSight(upstairs, downstairs), List.of(), upstairs, downstairs);
        check(upstairs.def().explored && !downstairs.def().explored, "a room is spotted from its squares on its own level, over the same place on another");
    }

    private static void whetherARoomIsSpottedIsTheGamesToSay() {
        IsoCell cell = new IsoCell();
        Room hall = Room.of(cell, 8L, 20, 20, 2, 2, 0);
        for (IsoGridSquare square : hall.squares()) {
            square.seen(true);
        }
        Spotting spotting = spotting();
        pass(spotting, cell, inSight(hall), List.of(), hall);
        check(!hall.def().explored && roomChecks(hall) == hall.squares().length,
                "a room seen out of the game's reach is asked about from every square seen, and the game leaves it unexplored");
        pass(spotting, cell, inSight(hall), List.of(), hall);
        check(roomChecks(hall) == 2 * hall.squares().length && asked(hall.squares()) == hall.squares().length,
                "and in the next round it is asked about again, as the player may have come nearer, without the squares found seen being asked about again");
    }

    private static void aRoomTheGameCouldNotSpotFromWhereThePlayerStandsIsNotHandedToIt() {
        IsoCell cell = new IsoCell();
        Room near = Room.of(cell, 40L, 400, 400, 2, 1, 0), upstairs = Room.of(cell, 41L, 402, 400, 1, 1, 1), far = Room.of(cell, 42L, 420, 400, 1, 1, 0),
                across = Room.of(cell, 43L, 440, 400, 1, 1, 0), beyond = Room.of(cell, 44L, 460, 400, 1, 1, 0);
        for (Room room : new Room[] {near, upstairs, far, across, beyond}) {
            for (IsoGridSquare square : room.squares()) {
                square.seen(true);
            }
        }
        Spotting spotting = spotting();
        RoomSight.Seen rooms = inSight(near, upstairs, far, across);
        HashMap<Long, RoomDef> defs = defs(near, upstairs, far, across);
        // Out of doors on the ground at 400.5, 400.5: the game spots from 10 tiles, so upstairs is a floor up, and far 19.5 tiles off and across 39.5 are out of its reach.
        spotting.pass(cell, rooms, defs::get, List.of(), 400.5f, 400.5f, 0f, null);
        check(roomChecks(near) == 2 && roomChecks(upstairs) == 0 && roomChecks(far) == 0 && roomChecks(across) == 0,
                "the game is handed only a room on the player's floor within its reach to spot");
        check(asked(upstairs.squares()) == 1 && asked(far.squares()) == 1, "and the others' squares are still asked about, for their turns");
        spotting.pass(cell, rooms, defs::get, List.of(), 400.5f, 400.5f, 0f, HOUSE);
        check(roomChecks(far) == 1 && roomChecks(across) == 1 && roomChecks(upstairs) == 0, "inside a building, rooms out to 50 tiles are handed to the game, on the player's floor");
        GameClient.client = true;
        try {
            spotting.pass(cell, inSight(beyond), defs(beyond)::get, List.of(), 400.5f, 400.5f, 0f, null);
        } finally {
            GameClient.client = false;
        }
        check(asked(beyond.squares()) == 0 && roomChecks(beyond) == 0, "and on a client, which marks nothing, a room the game could not spot is not asked about at all");
    }

    private static void aRoomOfAnotherBuildingIsHandedToTheGameOnlyWithinTenTiles() {
        IsoCell cell = new IsoCell();
        BuildingDef shop = new BuildingDef(2L, 520, 502, 2, 1);
        Room ours = Room.of(cell, 54L, 520, 500, 2, 1, 0), theirs = Room.of(cell, shop, 55L, 520, 502, 2, 1, 0);
        ours.squares()[0].seen(true);
        theirs.squares()[0].seen(true);
        RoomSight.Seen rooms = inSight(ours, theirs);
        HashMap<Long, RoomDef> defs = defs(ours, theirs);
        Spotting spotting = spotting();
        // Inside the house at 500.5, 500.5, both rooms 19.5 tiles off: the game spots a room of its own building from 50 tiles, any other from 10.
        for (int round = 0; round < 4; round++) {
            spotting.pass(cell, rooms, defs::get, List.of(), 500.5f, 500.5f, 0f, HOUSE);
        }
        check(roomChecks(ours) == 4 && roomChecks(theirs) == 0, "inside a building, a room of it 20 tiles off is handed to the game to spot, and one of another building is not");
        check(ours.squares()[1].seenAsked == 4 && theirs.squares()[1].seenAsked == 1,
                "and a square of the other building's room, which could only become seen, is asked every fourth round");
    }

    private static void aRoomOutOfSightIsLeftAlone() {
        IsoCell cell = new IsoCell();
        Room seen = Room.of(cell, 11L, 0, 0, 2, 2, 0), unseen = Room.of(cell, 12L, 4, 0, 2, 2, 0);
        for (Room room : new Room[] {seen, unseen}) {
            for (IsoGridSquare square : room.squares()) {
                square.seen(true);
                square.inReach(true);
                square.turns(true);
            }
        }
        Spotting spotting = spotting();
        spotting.turned(unseen.squares()[0]);
        int told = Meta.instance.told.size();
        pass(spotting, cell, inSight(seen), List.of(), seen, unseen);
        check(seen.def().explored && !unseen.def().explored, "only a room the view sees into is spotted, though the game has had both in the player's sight");
        check(Meta.instance.told.size() == told + seen.squares().length && !Meta.instance.told.contains(unseen.squares()[0]),
                "and only its squares are marked seen, though the light found a square of the other turned seen too");
        check(asked(unseen.squares()) == 0, "nor are the other's squares asked about");
    }

    private static void aSquareIsMarkedAsTheLightFindsItHasTurnedSeen() {
        IsoCell cell = new IsoCell();
        Room room = Room.of(cell, 13L, 40, 40, 3, 1, 0);
        room.def().explored = true;
        IsoGridSquare turning = room.squares()[0], steady = room.squares()[1], unseen = room.squares()[2];
        turning.seen(true);
        steady.seen(true);
        turning.turns(true);
        int told = Meta.instance.told.size();
        pass(cell, inSight(room), List.of(), room);
        check(Meta.instance.told.size() == told + 1 && Meta.instance.told.get(told) == turning, "a square the light finds has just turned seen as the round asks is marked");
        check(steady.seenAsked == 1 && !Meta.instance.told.contains(steady), "one seen before is asked about and not marked: the game marks a square as it turns seen");
        check(unseen.seenAsked == 1 && !Meta.instance.told.contains(unseen), "and one the player has not seen is asked about and not marked");
    }

    private static void aTurnAnotherReadFoundIsMarkedWhereTheViewSeesIt() {
        IsoCell cell = new IsoCell();
        Room inView = Room.of(cell, 16L, 60, 60, 1, 1, 0), outOfView = Room.of(cell, 17L, 62, 60, 1, 1, 0);
        IsoGridSquare indoors = inView.squares()[0], elsewhere = outOfView.squares()[0], street = cell.square(61, 62, 0), far = cell.square(70, 62, 0);
        Spotting spotting = spotting();
        for (IsoGridSquare square : new IsoGridSquare[] {indoors, elsewhere, street, far}) {
            square.seen(true);
            spotting.turned(square);
        }
        int told = Meta.instance.told.size();
        pass(spotting, cell, inSight(inView), List.of(drawn(cell, street)), inView, outOfView);
        check(Meta.instance.told.size() == told + 2 && Meta.instance.told.contains(indoors) && Meta.instance.told.contains(street),
                "a square another read of the light found turned seen is marked where the view sees it, in a room it sees into or in the open air of a chunk drawn");
        check(!Meta.instance.told.contains(elsewhere) && !Meta.instance.told.contains(far), "and not where it does not");
        pass(spotting, cell, inSight(inView, outOfView), List.of(drawn(cell, street), drawn(cell, far)), inView, outOfView);
        check(Meta.instance.told.size() == told + 4 && Meta.instance.told.contains(elsewhere) && Meta.instance.told.contains(far),
                "a turn the view did not see waits, and is marked once the view sees it, in a room or in the open air: the game marks a square only as it turns");
        pass(spotting, cell, inSight(inView, outOfView), List.of(drawn(cell, street), drawn(cell, far)), inView, outOfView);
        check(Meta.instance.told.size() == told + 4, "and a square marked, at once or once seen, is not marked again");
    }

    private static void aTurnWaitingIsLetGoWithItsChunk() {
        IsoCell cell = new IsoCell();
        IsoGridSquare square = cell.square(140, 140, 0), other = cell.square(150, 140, 0);
        square.seen(true);
        Spotting spotting = spotting();
        spotting.turned(square);
        int told = Meta.instance.told.size();
        pass(spotting, cell, inSight(), List.of(drawn(cell, other)));
        cell.reload(square.getX(), square.getY());
        pass(spotting, cell, inSight(), List.of(drawn(cell, square)));
        check(Meta.instance.told.size() == told && square.seenAsked == 1, "a turn that waited for the view is let go with its chunk, and the chunk loaded again is asked about afresh");
    }

    private static void theTurnsWaitingAreMarkedWhenThePictureStopsCovering() throws ReflectiveOperationException {
        IsoCell cell = new IsoCell();
        IsoGridSquare waited = cell.square(220, 220, 0), found = cell.square(230, 220, 0), elsewhere = cell.square(240, 220, 0);
        waited.seen(true);
        found.seen(true);
        Spotting spotting = spotting(0L);
        spotting.turned(waited);
        int told = Meta.instance.told.size();
        pass(spotting, cell, inSight(), List.of(drawn(cell, elsewhere)));
        spotting.turned(found);
        check(Meta.instance.told.size() == told, "a turn the view has not seen waits for it");
        WorldScene.Frame frame = new WorldScene(null).next();
        frame.aim(244f, 224f, 0f, 0f, -1f, false, 0f, 0f);
        for (int i = 0; i < 4; i++) {
            frame.shownBuilds().add(build(30 + i, 27, 1));
        }
        // A room by the eye the game could spot, whose square is asked about in every round that goes over it.
        Room den = Room.of(cell, 51L, 246, 226, 1, 1, 0);
        HashMap<Long, RoomDef> defs = defs(den);
        long start = 5_000_000_000L;
        spotting.look(inSight(den), frame, cell, defs::get, 244f, 224f, 0f, null, start);
        spotting.stoppedCovering(cell);
        check(Meta.instance.told.size() == told + 2 && Meta.instance.told.contains(waited) && Meta.instance.told.contains(found),
                "once the picture stops covering the view, every turn that waited for it is marked, as the game's own light marks a turn as it finds it");
        spotting.look(inSight(den), frame, cell, defs::get, 244f, 224f, 0f, null, start + 4_000_000L);
        check(den.squares()[0].seenAsked == 0, "and the round under way is closed, not carried on over what the picture saw");
        spotting.stoppedCovering(cell);
        check(Meta.instance.told.size() == told + 2, "and none is marked twice");
    }

    private static void nothingIsCarriedIntoTheNextSave() {
        IsoCell cell = new IsoCell();
        IsoGridSquare waited = cell.square(260, 260, 0), found = cell.square(270, 260, 0), seen = cell.square(280, 260, 0);
        for (IsoGridSquare square : new IsoGridSquare[] {waited, found, seen}) {
            square.seen(true);
        }
        Spotting spotting = spotting();
        spotting.turned(waited);
        pass(spotting, cell, inSight(), List.of(drawn(cell, seen)));
        spotting.turned(found);
        spotting.leftSave();
        int told = Meta.instance.told.size();
        pass(spotting, cell, inSight(), List.of(drawn(cell, waited), drawn(cell, found), drawn(cell, seen)));
        check(Meta.instance.told.size() == told, "a turn found in a save left, waiting for the view or not, is not marked in the next");
        check(seen.seenAsked == 2, "and a square found seen there is asked about afresh");
    }

    private static void aTurnFoundAfterTheTurnsWereHandedBackIsNotKept() {
        IsoCell cell = new IsoCell();
        IsoGridSquare late = cell.square(290, 290, 0), left = cell.square(300, 290, 0), again = cell.square(310, 290, 0);
        for (IsoGridSquare square : new IsoGridSquare[] {late, left, again}) {
            square.seen(true);
        }
        Spotting spotting = spotting();
        spotting.stoppedCovering(cell);
        int told = Meta.instance.told.size();
        Spotting.squareSeenByLight(Meta.instance, late, spotting);
        check(Meta.instance.told.size() == told + 1 && Meta.instance.told.get(told) == late,
                "a turn a read found under the picture that comes after the picture stopped covering is the game's to mark, as it would be from a read a moment later");
        spotting.leftSave();
        Spotting.squareSeenByLight(Meta.instance, left, spotting);
        spotting.covering();
        pass(spotting, cell, inSight(), List.of(drawn(cell, left)));
        check(Meta.instance.told.size() == told + 1, "one that comes after the save was left is let go, and not marked in the next");
        Spotting.squareSeenByLight(Meta.instance, again, spotting);
        check(Meta.instance.told.size() == told + 1, "once the picture covers a view again the turns are taken again");
        pass(spotting, cell, inSight(), List.of(drawn(cell, again)));
        check(Meta.instance.told.size() == told + 2 && Meta.instance.told.contains(again), "to be marked where the view sees them");
    }

    private static void aRoomSeenIntoWhileARoundGoesOnIsGoneOverByTheNext() throws ReflectiveOperationException {
        IsoCell cell = new IsoCell();
        Room garage = Room.of(cell, 34L, 330, 330, 1, 1, 0);
        garage.squares()[0].seen(true);
        WorldScene.Frame frame = new WorldScene(null).next();
        frame.aim(324f, 324f, 0f, 0f, -1f, false, 0f, 0f);
        for (int i = 0; i < 4; i++) {
            cell.square(320 + 8 * i, 320, 0);
            frame.shownBuilds().add(build(40 + i, 40, 1));
        }
        Spotting spotting = spotting(0L);
        HashMap<Long, RoomDef> defs = defs(garage);
        long start = 5_000_000_000L;
        spotting.look(inSight(), frame, cell, defs::get, 324f, 324f, 0f, null, start);
        spotting.look(inSight(garage), frame, cell, defs::get, 324f, 324f, 0f, null, start + 300_000_000L);
        frames(spotting, inSight(), frame, cell, defs, 324f, 324f, start + 304_000_000L);
        check(garage.squares()[0].roomChecks == 1, "a room the picture saw into for one frame while a round went on is gone over by the next round");
    }

    /** A build of the chunk at a place with as many runs as given, as the picture draws it. */
    private static WorldScene.ChunkMeshes build(int wx, int wy, int runs) throws ReflectiveOperationException {
        WorldScene.ChunkMeshes build = new WorldScene.ChunkMeshes(0);
        build.runs = runs;
        Field x = WorldScene.ChunkMeshes.class.getDeclaredField("wx"), y = WorldScene.ChunkMeshes.class.getDeclaredField("wy");
        x.setAccessible(true);
        y.setAccessible(true);
        x.setInt(build, wx);
        y.setInt(build, wy);
        return build;
    }

    /** Twenty frames of the picture four milliseconds apart from a time, each with the rooms it saw into lately, for a player on the ground at a place outside: a round begun in the first is done by the last. */
    private static void frames(Spotting spotting, RoomSight.Seen lately, WorldScene.Frame frame, IsoCell cell, HashMap<Long, RoomDef> defs, float x, float y, long from) {
        for (int i = 0; i < 20; i++) {
            spotting.look(lately, frame, cell, defs::get, x, y, 0f, null, from + i * 4_000_000L);
        }
    }

    private static void aRoundGoesOverWhatThePictureSees() throws ReflectiveOperationException {
        IsoCell cell = new IsoCell();
        // The eye in the chunk at 10, 10, facing east: one chunk ahead of it, one four behind, near enough to cast a shadow into the picture, and one ahead with nothing built.
        IsoGridSquare here = cell.square(84, 84, 0), ahead = cell.square(92, 84, 0), behind = cell.square(52, 84, 0), bare = cell.square(100, 84, 0);
        Room kitchen = Room.of(cell, 31L, 86, 86, 1, 1, 0), hall = Room.of(cell, 32L, 60, 60, 1, 1, 0);
        kitchen.squares()[0].seen(true);
        WorldScene.Frame frame = new WorldScene(null).next();
        frame.aim(84f, 84f, 1f, 0f, (float) Math.cos(Math.toRadians(45)), false, 0f, 0f);
        frame.shownBuilds().addAll(List.of(build(10, 10, 3), build(10, 10, 3), build(11, 10, 2), build(6, 10, 4), build(12, 10, 0)));
        Spotting spotting = spotting();
        long start = 5_000_000_000L;
        HashMap<Long, RoomDef> defs = defs(kitchen, hall);
        frames(spotting, inSight(kitchen), frame, cell, defs, 84f, 84f, start);
        check(here.seenAsked == 1 && ahead.seenAsked == 1, "a round goes over the open air of the chunks the picture draws ahead, and of one drawn twice once");
        check(behind.seenAsked == 0 && bare.seenAsked == 0, "and not of one it draws behind for its shadow, nor of one with nothing built");
        check(kitchen.squares()[0].roomChecks == 1 && hall.squares()[0].seenAsked == 0, "it spots the rooms the picture saw into lately, and no other");
        frames(spotting, inSight(), frame, cell, defs, 84f, 84f, start + 3_000_000_000L);
        check(kitchen.squares()[0].roomChecks == 2, "a room the picture saw into is gone over for a while after, as the game may find its squares seen late");
        frames(spotting, inSight(), frame, cell, defs, 84f, 84f, start + 4_100_000_000L);
        check(kitchen.squares()[0].roomChecks == 2, "and not once four seconds have gone by since the last frame that saw into it");
        check(here.seenAsked == 1 && ahead.seenAsked == 1, "a square out of doors not yet seen, which could only turn seen, is not asked about in the two rounds after the first");
        here.seen(true);
        spotting.turned(here);
        frame.roomsSeen = new RoomSight.Seen();
        frames(spotting, inSight(), frame, cell, defs, 84f, 84f, start + 5_000_000_000L);
        check(Meta.instance.told.contains(here),
                "where the picture lists the rooms it sees and sees the open air all round, as from out of doors, the chunks are gone over, and a square turned seen there is marked");
        ahead.seen(true);
        spotting.turned(ahead);
        frame.roomsSeen.airFrom(new RoomSight(), false);
        frames(spotting, inSight(), frame, cell, defs, 84f, 84f, start + 6_000_000_000L);
        check(!Meta.instance.told.contains(ahead), "and where it sees none of the open air, from indoors, they are not, and a square turned seen there waits");
    }

    private static void theLightLeavesRoomsAndMarksToTheRoundsOnlyUnderThePicture() {
        IsoCell cell = new IsoCell();
        Room room = Room.of(cell, 33L, 160, 160, 1, 1, 0);
        IsoGridSquare indoors = room.squares()[0], street = cell.square(162, 160, 0);
        Spotting.roomSeenByLight(indoors, 2, null);
        check(indoors.roomChecks == 1 && indoors.roomCheckedFor == 2, "outside the first person picture the light spots a room as the game does, for the light's own player");
        Spotting spotting = spotting();
        Spotting.roomSeenByLight(indoors, 2, spotting);
        check(indoors.roomChecks == 1, "and under it spots none");
        int told = Meta.instance.told.size();
        Spotting.squareSeenByLight(Meta.instance, street, null);
        check(Meta.instance.told.size() == told + 1 && Meta.instance.told.get(told) == street, "outside the picture a square the light finds turned seen is marked at once, as the game does");
        Spotting.squareSeenByLight(Meta.instance, street, spotting);
        check(Meta.instance.told.size() == told + 1, "under it the light marks nothing");
        street.seen(true);
        pass(spotting, cell, inSight(), List.of(drawn(cell, street)));
        check(Meta.instance.told.size() == told + 2 && Meta.instance.told.get(told + 1) == street, "and hands the square to the rounds, which mark it where the view sees it");
    }

    /** Where a light shaped like the game's hands its calls: see aLightShapedLikeTheGamesHandsItsCallsToTheHooks. */
    private static final List<String> hooked = new ArrayList<>();

    public static void roomSeenByLight(IsoGridSquare square, int playerIndex) {
        hooked.add("room at " + square.getX() + " for player " + playerIndex);
    }

    public static void squareSeenByLight(Meta meta, IsoGridSquare square, int playerIndex) {
        hooked.add("marked at " + square.getX() + " for player " + playerIndex + (meta == Meta.instance ? "" : " on another record"));
    }

    /**
     * A class shaped like the game's light (LightingJNI.JNILighting) where it spots and marks: a
     * player of its own, and a light worked out by updateFBORenderChunk, which spots once and
     * marks once, and by update, which spots in as many places as given and marks once.
     */
    private static byte[] lightShaped(ClassDesc owner, int spotsInUpdate) {
        ClassDesc square = ClassDesc.of("zombie.iso.IsoGridSquare"), meta = ClassDesc.of("zombie.meta.Meta");
        MethodTypeDesc spot = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_int), mark = MethodTypeDesc.of(ConstantDescs.CD_void, square);
        Consumer<CodeBuilder> spots = code -> code.getstatic(owner, "square", square).aload(0)
                .getfield(owner, "playerIndex", ConstantDescs.CD_int).invokevirtual(square, "checkRoomSeen", spot);
        Consumer<CodeBuilder> marks = code -> code.getstatic(meta, "instance", meta).getstatic(owner, "square", square)
                .invokevirtual(meta, "dealWithSquareSeen", mark);
        return ClassFile.of().build(owner, type -> type
                .withFlags(ClassFile.ACC_PUBLIC)
                .withField("playerIndex", ConstantDescs.CD_int, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL)
                .withField("square", square, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC)
                .withMethodBody(ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_int), ClassFile.ACC_PUBLIC, code -> code
                        .aload(0).invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                        .aload(0).iload(1).putfield(owner, "playerIndex", ConstantDescs.CD_int).return_())
                .withMethodBody("updateFBORenderChunk", ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, code -> {
                    spots.accept(code);
                    marks.accept(code);
                    code.return_();
                })
                .withMethodBody("update", ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, code -> {
                    for (int i = 0; i < spotsInUpdate; i++) {
                        spots.accept(code);
                    }
                    marks.accept(code);
                    code.return_();
                }));
    }

    private static void aLightShapedLikeTheGamesHandsItsCallsToTheHooks() throws Throwable {
        ClassDesc owner = ClassDesc.of("com.cameraunlock.zomboid.LightShaped");
        String hooks = "com/cameraunlock/zomboid/SpottingTests";
        Class<?> light = MethodHandles.lookup().defineClass(Agent.lightLeavingSpotting(lightShaped(owner, 2), "com/cameraunlock/zomboid/LightShaped", hooks));
        IsoGridSquare square = new IsoCell().square(7, 3, 0);
        light.getField("square").set(null, square);
        Object lightOf = light.getConstructor(int.class).newInstance(2);
        int told = Meta.instance.told.size();
        hooked.clear();
        light.getMethod("updateFBORenderChunk").invoke(lightOf);
        check(hooked.equals(List.of("room at 7 for player 2", "marked at 7 for player 2")), "the light's spotting and marking go to the hooks, with the light's own player: " + hooked);
        hooked.clear();
        light.getMethod("update").invoke(lightOf);
        check(hooked.equals(List.of("room at 7 for player 2", "room at 7 for player 2", "marked at 7 for player 2")), "and in the older way of working out light, each of them: " + hooked);
        check(square.roomChecks == 0 && Meta.instance.told.size() == told, "and none of the game's own calls is left");
        boolean refused = false;
        try {
            Agent.lightLeavingSpotting(lightShaped(owner, 1), "com/cameraunlock/zomboid/LightShaped", hooks);
        } catch (IllegalStateException unlike) {
            refused = true;
        }
        check(refused, "a light that spots in more or fewer places than the game's is refused, and its patch with it");
    }

    private static void theOpenAirOfAChunkDrawnIsMarkedAndItsRoomsAreNot() {
        IsoCell cell = new IsoCell();
        IsoGridSquare street = cell.square(81, 82, 0), alley = cell.square(83, 82, 0), beyond = cell.square(90, 82, 0), roof = cell.square(82, 81, 1);
        Room shop = Room.of(cell, 14L, 84, 84, 1, 1, 0);
        for (IsoGridSquare square : new IsoGridSquare[] {street, alley, beyond, roof, shop.squares()[0]}) {
            square.seen(true);
            square.turns(true);
        }
        alley.seen(false);
        int told = Meta.instance.told.size();
        pass(cell, inSight(), List.of(drawn(cell, street)), shop);
        check(Meta.instance.told.size() == told + 2 && Meta.instance.told.contains(street) && Meta.instance.told.contains(roof),
                "of a chunk drawn, a square of open air the light finds turned seen is marked, on any level");
        check(alley.seenAsked == 1, "one they have not seen is asked about and left");
        check(beyond.seenAsked == 0, "a square of a chunk not drawn is not asked about");
        check(shop.squares()[0].seenAsked == 0, "nor a room's square on the chunk drawn: a room's squares are asked about as it is seen into");
    }

    private static void aClientSpotsRoomsAndMarksNothing() {
        IsoCell cell = new IsoCell();
        Room room = Room.of(cell, 15L, 50, 50, 1, 1, 0), explored = Room.of(cell, 20L, 52, 52, 1, 1, 0);
        explored.def().explored = true;
        IsoGridSquare square = room.squares()[0], street = cell.square(52, 50, 0);
        for (IsoGridSquare seen : new IsoGridSquare[] {square, street, explored.squares()[0]}) {
            seen.seen(true);
        }
        square.inReach(true);
        int told = Meta.instance.told.size();
        GameClient.client = true;
        try {
            pass(cell, inSight(room, explored), List.of(drawn(cell, street)), room, explored);
        } finally {
            GameClient.client = false;
        }
        check(room.def().explored, "a client spots a room it sees into, as the game's own light has it do");
        check(Meta.instance.told.size() == told && street.seenAsked == 0 && explored.squares()[0].seenAsked == 0,
                "and asks nothing that could only mark a square seen: the server keeps what was seen when");
    }

    private static void aSquareFoundSeenIsNotAskedAboutAgain() {
        IsoCell cell = new IsoCell();
        IsoGridSquare seen = cell.square(100, 100, 0), unseen = cell.square(101, 100, 0);
        seen.seen(true);
        Spotting spotting = spotting();
        List<IsoChunk> drawn = List.of(drawn(cell, seen));
        for (int round = 0; round < 4; round++) {
            pass(spotting, cell, inSight(), drawn);
        }
        check(seen.seenAsked == 1, "a square found seen is not asked about again: a square seen stays seen, so no turn can come there");
        check(unseen.seenAsked == 1, "one not seen, which could only turn seen, is asked about in the first round to go over its chunk and not in the three after");
        unseen.seen(true);
        unseen.turns(true);
        int told = Meta.instance.told.size();
        pass(spotting, cell, inSight(), drawn);
        check(unseen.seenAsked == 2 && Meta.instance.told.size() == told + 1 && Meta.instance.told.get(told) == unseen,
                "but in the fourth after that, as it may yet turn, and is marked as the light finds it has turned");
    }

    private static void aSquareARoomCouldBeSpottedFromIsAskedEveryRoundAndOneThatCouldOnlyTurnEveryFourth() {
        IsoCell cell = new IsoCell();
        Room den = Room.of(cell, 49L, 600, 600, 1, 1, 0), study = Room.of(cell, 50L, 602, 600, 1, 1, 0);
        study.def().explored = true;
        IsoGridSquare street = cell.square(604, 600, 0);
        Spotting spotting = spotting();
        for (int round = 0; round < 5; round++) {
            pass(spotting, cell, inSight(den, study), List.of(drawn(cell, street)), den, study);
        }
        check(den.squares()[0].seenAsked == 5, "a square of a room the game could spot from it is asked about in every round, so rooms fill as they did");
        check(study.squares()[0].seenAsked == 2 && street.seenAsked == 2,
                "one that could only turn seen, in an explored room or out of doors, in the first round to go over its chunk and every fourth after: the owner's ruling of 29 October 2026");
    }

    private static void aChunkLoadedAgainIsAskedAboutAgain() {
        IsoCell cell = new IsoCell();
        IsoGridSquare square = cell.square(120, 120, 0);
        square.seen(true);
        Spotting spotting = spotting();
        pass(spotting, cell, inSight(), List.of(drawn(cell, square)));
        cell.reload(square.getX(), square.getY());
        pass(spotting, cell, inSight(), List.of(drawn(cell, square)));
        check(square.seenAsked == 2, "what was found seen of a chunk is let go with it, and the chunk loaded again at its place is asked about again");
    }

    private static void aFramesShareOfARoundEndsOnceItsTimeIsUp() {
        IsoCell cell = new IsoCell();
        IsoGridSquare[] corners = new IsoGridSquare[4];
        for (int i = 0; i < corners.length; i++) {
            corners[i] = cell.square(200 + 8 * i, 200, 0);
        }
        Spotting spotting = spotting();
        spotting.begin(cell, inSight(), defs()::get, List.of(drawn(cell, corners[0]), drawn(cell, corners[1]), drawn(cell, corners[2]), drawn(cell, corners[3])),
                IsoChunk::getMinLevel, IsoChunk::getMaxLevel);
        boolean done = spotting.work(cell, 0L);
        check(!done && corners[0].seenAsked == 1 && asked(corners) == 1, "a frame's share of a round whose time is up stops after the unit it is on, and does that one");
        done = spotting.work(cell, 0L) | spotting.work(cell, 0L);
        check(!done && asked(corners) == 3, "each frame's share takes up where the last left off");
        check(spotting.work(cell, 0L) && asked(corners) == 4, "and the last says the round's units are all done");
        spotting.begin(cell, inSight(), defs()::get, List.of(drawn(cell, corners[0]), drawn(cell, corners[1]), drawn(cell, corners[2]), drawn(cell, corners[3])),
                IsoChunk::getMinLevel, IsoChunk::getMaxLevel);
        check(spotting.work(cell, Long.MAX_VALUE), "a share with time to spare goes on to the end of the round");
    }

    private static void aRoomIsGoneOverAChunkAtATime() {
        IsoCell cell = new IsoCell();
        // Twelve tiles square from 4, 4: four tiles in the chunk at 0, 0, eight in those at 1, 0 and 0, 1, and a whole chunk at 1, 1.
        Room hall = Room.of(cell, 45L, 4, 4, 12, 12, 0);
        hall.def().explored = true;
        Spotting spotting = spotting();
        spotting.begin(cell, inSight(hall), defs(hall)::get, List.of(), IsoChunk::getMinLevel, IsoChunk::getMaxLevel);
        List<Integer> pieces = new ArrayList<>();
        boolean done = false;
        for (int before = 0; !done; before = asked(hall.squares())) {
            done = spotting.work(cell, 0L);
            pieces.add(asked(hall.squares()) - before);
        }
        check(pieces.equals(List.of(16, 32, 32, 64)), "a room's rectangle is gone over a piece in each chunk at a time: " + pieces);
        boolean once = true;
        for (IsoGridSquare square : hall.squares()) {
            once &= square.seenAsked == 1;
        }
        check(once, "and each of its squares once");
    }

    private static void theRoomsThatCouldBeSpottedAreGoneOverFirst() {
        IsoCell cell = new IsoCell();
        Room far = Room.of(cell, 46L, 500, 500, 1, 1, 0), near = Room.of(cell, 47L, 560, 500, 1, 1, 0), explored = Room.of(cell, 48L, 562, 500, 1, 1, 0);
        explored.def().explored = true;
        for (Room room : new Room[] {far, near, explored}) {
            room.squares()[0].seen(true);
        }
        Spotting spotting = spotting();
        spotting.stand(560.5f, 502.5f, 0f, null);
        spotting.begin(cell, inSight(far, explored, near), defs(far, near, explored)::get, List.of(), IsoChunk::getMinLevel, IsoChunk::getMaxLevel);
        spotting.work(cell, 0L);
        check(near.squares()[0].seenAsked == 1 && near.squares()[0].roomChecks == 1 && far.squares()[0].seenAsked == 0 && explored.squares()[0].seenAsked == 0,
                "the first piece a round goes over is of a room the game could spot from where the player stands, whatever the order the rooms came in");
    }

    private static void aRoundTheFramesWereTooSlowForGoesOnOverTheNext() {
        IsoCell cell = new IsoCell();
        // A hall four chunks long that the game could spot from the player inside, so its squares are asked about in every round: a piece of eight in each chunk.
        Room hall = Room.of(cell, 52L, 200, 200, 32, 1, 0);
        HashMap<Long, RoomDef> defs = defs(hall);
        WorldScene.Frame frame = new WorldScene(null).next();
        frame.aim(204f, 200.5f, 0f, 0f, -1f, false, 0f, 0f);
        Spotting spotting = spotting(0L);
        long start = 5_000_000_000L;
        spotting.look(inSight(hall), frame, cell, defs::get, 204f, 200.5f, 0f, HOUSE, start);
        check(asked(hall.squares()) == 0, "the frame that begins a round, with no time left once it has, does none of it");
        spotting.look(inSight(hall), frame, cell, defs::get, 204f, 200.5f, 0f, HOUSE, start + 1_000_000_000L);
        check(asked(hall.squares()) == 8,
                "a frame with no time left does one unit of the round under way, though the round's quarter second is long gone, and does not finish it");
        spotting.look(inSight(hall), frame, cell, defs::get, 204f, 200.5f, 0f, HOUSE, start + 1_004_000_000L);
        spotting.look(inSight(hall), frame, cell, defs::get, 204f, 200.5f, 0f, HOUSE, start + 1_008_000_000L);
        spotting.look(inSight(hall), frame, cell, defs::get, 204f, 200.5f, 0f, HOUSE, start + 1_012_000_000L);
        check(asked(hall.squares()) == 32, "the round goes on over the next frames until it is done");
        spotting.look(inSight(hall), frame, cell, defs::get, 204f, 200.5f, 0f, HOUSE, start + 1_016_000_000L);
        spotting.look(inSight(hall), frame, cell, defs::get, 204f, 200.5f, 0f, HOUSE, start + 1_020_000_000L);
        check(asked(hall.squares()) == 40, "and the next begins once it is");
    }

    private static void noLevelThePictureReadsNoLightOnIsAskedAbout() {
        IsoCell cell = new IsoCell();
        IsoGridSquare ground = cell.square(300, 300, 0), roof = cell.square(301, 300, 1), top = cell.square(302, 300, 2);
        Room attic = Room.of(cell, 21L, 304, 304, 1, 1, 2);
        Spotting spotting = spotting();
        spotting.begin(cell, inSight(attic), defs(attic)::get, List.of(drawn(cell, ground)), chunk -> 0, chunk -> 1);
        spotting.work(cell, Long.MAX_VALUE);
        check(ground.seenAsked == 1 && roof.seenAsked == 1, "the levels of a chunk the picture reads light on are asked about");
        check(top.seenAsked == 0 && attic.squares()[0].seenAsked == 0, "and none it does not, of the open air or of a room: an asking there could mark the chunk for its light");
    }

    private static void onlyTheChunksTheConeTakesInAreSeen() {
        WorldScene.Frame frame = new WorldScene(null).next();
        // The eye at the middle of the chunk at 10, 10, facing east down a cone 45 degrees either side.
        frame.aim(84f, 84f, 1f, 0f, (float) Math.cos(Math.toRadians(45)), false, 0f, 0f);
        check(frame.faces(10, 10) && frame.faces(11, 10) && frame.faces(14, 11), "the eye's own chunk and those ahead of it are seen");
        check(!frame.faces(6, 10) && !frame.faces(10, 6), "and not one four chunks behind or to the side, though near enough that its shadow is drawn");
        frame.aim(84f, 84f, 1f, 0f, (float) Math.cos(Math.toRadians(45)), true, -1f, 0f);
        check(frame.faces(6, 10), "a driver's mirror takes in what is straight behind");
        frame.aim(84f, 84f, 0f, 0f, -1f, false, 0f, 0f);
        check(frame.faces(6, 10) && frame.faces(10, 6), "and a view that faces no way along the ground takes in all round");
    }

    private static void passesComeFourTimesASecondWhateverTheFrames() {
        Spotting spotting = new Spotting(0);
        long start = 5_000_000_000L;
        check(spotting.due(start), "the first frame makes a round");
        int passes = 1;
        for (long at = start + 4_000_000L; at < start + 1_000_000_000L; at += 4_000_000L) {
            passes += spotting.due(at) ? 1 : 0;
        }
        check(passes == 4, String.format(Locale.ROOT, "at 250 frames a second a second has four rounds, not %d", passes));
        check(spotting.due(start + 2_000_000_000L), "and a frame after a long wait makes one at once");
    }

    /** What the replaced call is handed to: the object it was made on, what it took, and the object whose method made it. */
    public static String said(StringBuilder words, int from, Object speaker) throws ReflectiveOperationException {
        return words.substring(from) + " by " + speaker.getClass().getField("name").get(speaker);
    }

    private static void aCallReplacedHandsItsHookWhatItsMethodBelongsTo() throws Throwable {
        ClassDesc owner = ClassDesc.of("com.cameraunlock.zomboid.SubjectSpeaking"), builder = ClassDesc.of("java.lang.StringBuilder");
        ClassDesc string = ConstantDescs.CD_String;
        byte[] subject = ClassFile.of().build(owner, type -> type
                .withFlags(ClassFile.ACC_PUBLIC)
                .withField("name", string, ClassFile.ACC_PUBLIC)
                .withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, code -> code
                        .aload(0).invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void).return_())
                .withMethodBody("say", MethodTypeDesc.of(string), ClassFile.ACC_PUBLIC, code -> {
                    code.new_(builder);
                    code.dup();
                    code.ldc("a word");
                    code.invokespecial(builder, ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void, string));
                    code.iconst_2();
                    code.invokevirtual(builder, "substring", MethodTypeDesc.of(string, ConstantDescs.CD_int));
                    code.areturn();
                }));
        CallSite.Method within = new CallSite.Method("com/cameraunlock/zomboid/SubjectSpeaking", "say", "()Ljava/lang/String;");
        CallSite.Method call = new CallSite.Method("java/lang/StringBuilder", "substring", "(I)Ljava/lang/String;");
        CallSite.Method hook = new CallSite.Method("com/cameraunlock/zomboid/SpottingTests", "said", "(Ljava/lang/StringBuilder;ILjava/lang/Object;)Ljava/lang/String;");
        Class<?> replaced = MethodHandles.lookup().defineClass(CallSite.replaceEach(subject, within, call, code -> CallSite.passThis(code, hook), 1));
        Object speaker = replaced.getConstructor().newInstance();
        replaced.getField("name").set(speaker, "the subject");
        check("word by the subject".equals(replaced.getMethod("say").invoke(speaker)),
                "the hook is handed the object the call was made on, what the call took and the object whose method made it, and its answer is the call's");
    }
}
