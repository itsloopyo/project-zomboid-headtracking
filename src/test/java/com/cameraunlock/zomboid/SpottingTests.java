package com.cameraunlock.zomboid;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
        Checks.run("aRoomOutOfSightIsLeftAlone", SpottingTests::aRoomOutOfSightIsLeftAlone);
        Checks.run("aSquareIsMarkedAsTheLightFindsItHasTurnedSeen", SpottingTests::aSquareIsMarkedAsTheLightFindsItHasTurnedSeen);
        Checks.run("aTurnAnotherReadFoundIsMarkedWhereTheViewSeesIt", SpottingTests::aTurnAnotherReadFoundIsMarkedWhereTheViewSeesIt);
        Checks.run("nothingIsCarriedIntoTheNextSave", SpottingTests::nothingIsCarriedIntoTheNextSave);
        Checks.run("theOpenAirOfAChunkDrawnIsMarkedAndItsRoomsAreNot", SpottingTests::theOpenAirOfAChunkDrawnIsMarkedAndItsRoomsAreNot);
        Checks.run("aClientSpotsRoomsAndMarksNothing", SpottingTests::aClientSpotsRoomsAndMarksNothing);
        Checks.run("passesComeFourTimesASecondWhateverTheFrames", SpottingTests::passesComeFourTimesASecondWhateverTheFrames);
        Checks.run("aCallReplacedHandsItsHookWhatItsMethodBelongsTo", SpottingTests::aCallReplacedHandsItsHookWhatItsMethodBelongsTo);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static final int HOUR = 100;

    /** A room of a cell, its id, and its squares, w by h from x, y on a level. */
    private record Room(long id, RoomDef def, IsoGridSquare[] squares) {
        static Room of(IsoCell cell, long id, int x, int y, int w, int h, int level) {
            RoomDef def = new RoomDef(level);
            def.rects.add(new RoomDef.RoomRect(x, y, w, h));
            IsoRoom room = new IsoRoom(def);
            IsoGridSquare[] squares = new IsoGridSquare[w * h];
            for (int i = 0; i < squares.length; i++) {
                squares[i] = cell.square(x + i % w, y + i / w, level);
                squares[i].room(room, id);
                squares[i].hourLastSeen = HOUR;
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

    /** A player's spotting, handed each square the light finds turned seen as the game's light hands it under the picture. */
    private static Spotting spotting() {
        Spotting spotting = new Spotting(0);
        IsoGridSquare.onTurn = spotting::turned;
        return spotting;
    }

    private static void pass(IsoCell cell, RoomSight.Seen rooms, List<IsoChunk> chunks, Room... known) {
        pass(spotting(), cell, rooms, chunks, known);
    }

    private static void pass(Spotting spotting, IsoCell cell, RoomSight.Seen rooms, List<IsoChunk> chunks, Room... known) {
        HashMap<Long, RoomDef> defs = new HashMap<>();
        for (Room room : known) {
            defs.put(room.id(), room.def());
        }
        spotting.pass(cell, HOUR, rooms, defs::get, chunks);
    }

    private static int roomChecks(Room room) {
        int checks = 0;
        for (IsoGridSquare square : room.squares()) {
            checks += square.roomChecks;
        }
        return checks;
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
    }

    private static void whetherARoomIsSpottedIsTheGamesToSay() {
        IsoCell cell = new IsoCell();
        Room hall = Room.of(cell, 8L, 20, 20, 2, 2, 0);
        for (IsoGridSquare square : hall.squares()) {
            square.seen(true);
        }
        pass(cell, inSight(hall), List.of(), hall);
        check(!hall.def().explored && roomChecks(hall) == hall.squares().length,
                "a room seen out of the game's reach is asked about from every square seen, and the game leaves it unexplored");
        int asked = 0;
        Room spotted = Room.of(cell, 9L, 30, 30, 2, 2, 0);
        spotted.def().explored = true;
        for (IsoGridSquare square : spotted.squares()) {
            square.seen(true);
        }
        pass(cell, inSight(spotted), List.of(), spotted);
        for (IsoGridSquare square : spotted.squares()) {
            asked += square.seenAsked;
        }
        check(roomChecks(spotted) == 0 && asked == 0, "an explored room whose squares are marked this hour is not asked about, and nor are its squares");
    }

    private static void aRoomOutOfSightIsLeftAlone() {
        IsoCell cell = new IsoCell();
        Room seen = Room.of(cell, 11L, 0, 0, 2, 2, 0), unseen = Room.of(cell, 12L, 4, 0, 2, 2, 0);
        for (Room room : new Room[] {seen, unseen}) {
            for (IsoGridSquare square : room.squares()) {
                square.seen(true);
                square.inReach(true);
                square.turns(true);
                square.hourLastSeen = HOUR - 1;
            }
        }
        Spotting spotting = spotting();
        spotting.turned(unseen.squares()[0]);
        int told = Meta.instance.told.size();
        pass(spotting, cell, inSight(seen), List.of(), seen, unseen);
        check(seen.def().explored && !unseen.def().explored, "only a room the view sees into is spotted, though the game has had both in the player's sight");
        check(Meta.instance.told.size() == told + seen.squares().length && !Meta.instance.told.contains(unseen.squares()[0]),
                "and only its squares are marked seen, though the light found a square of the other turned seen too");
        check(unseen.squares()[1].seenAsked == 0, "nor are the other's squares asked about");
    }

    private static void aSquareIsMarkedAsTheLightFindsItHasTurnedSeen() {
        IsoCell cell = new IsoCell();
        Room room = Room.of(cell, 13L, 40, 40, 4, 1, 0);
        room.def().explored = true;
        IsoGridSquare turning = room.squares()[0], steady = room.squares()[1], already = room.squares()[2], unseen = room.squares()[3];
        for (IsoGridSquare square : new IsoGridSquare[] {turning, steady, already}) {
            square.seen(true);
        }
        turning.turns(true);
        already.turns(true);
        turning.hourLastSeen = steady.hourLastSeen = unseen.hourLastSeen = HOUR - 1;
        int told = Meta.instance.told.size();
        pass(cell, inSight(room), List.of(), room);
        check(Meta.instance.told.size() == told + 1 && Meta.instance.told.get(told) == turning, "a square the light finds has just turned seen as the pass asks is marked");
        check(steady.seenAsked == 1, "one seen before is asked about and not marked: the game marks a square as it turns seen");
        check(already.seenAsked == 0, "one marked in this hour is not asked about");
        check(unseen.seenAsked == 1 && !Meta.instance.told.contains(unseen), "and one the player has not seen is asked about and not marked");
    }

    private static void aTurnAnotherReadFoundIsMarkedWhereTheViewSeesIt() {
        IsoCell cell = new IsoCell();
        Room inView = Room.of(cell, 16L, 60, 60, 1, 1, 0), outOfView = Room.of(cell, 17L, 62, 60, 1, 1, 0);
        IsoGridSquare indoors = inView.squares()[0], elsewhere = outOfView.squares()[0], street = cell.square(61, 62, 0), far = cell.square(70, 62, 0);
        Spotting spotting = spotting();
        for (IsoGridSquare square : new IsoGridSquare[] {indoors, elsewhere, street, far}) {
            square.seen(true);
            square.hourLastSeen = HOUR - 1;
            spotting.turned(square);
        }
        int told = Meta.instance.told.size();
        pass(spotting, cell, inSight(inView), List.of(street.getChunk()), inView, outOfView);
        check(Meta.instance.told.size() == told + 2 && Meta.instance.told.contains(indoors) && Meta.instance.told.contains(street),
                "a square another read of the light found turned seen is marked where the view sees it, in a room it sees into or in the open air of a chunk drawn");
        check(!Meta.instance.told.contains(elsewhere) && !Meta.instance.told.contains(far), "and left where it does not");
        pass(spotting, cell, inSight(inView, outOfView), List.of(street.getChunk(), far.getChunk()), inView, outOfView);
        check(Meta.instance.told.size() == told + 2, "a turn is gone with the pass that came after it, whether it was marked or not");
    }

    private static void nothingIsCarriedIntoTheNextSave() {
        IsoCell cell = new IsoCell();
        Room room = Room.of(cell, 20L, 260, 260, 1, 1, 0);
        IsoGridSquare square = room.squares()[0];
        square.seen(true);
        Spotting spotting = spotting();
        spotting.turned(square);
        spotting.leftSave();
        int told = Meta.instance.told.size();
        pass(spotting, cell, inSight(room), List.of(), room);
        check(Meta.instance.told.size() == told, "a turn found in a save left is not marked in the next, though the view sees its place");
    }

    private static void theOpenAirOfAChunkDrawnIsMarkedAndItsRoomsAreNot() {
        IsoCell cell = new IsoCell();
        IsoGridSquare street = cell.square(81, 82, 0), alley = cell.square(83, 82, 0), beyond = cell.square(90, 82, 0);
        Room shop = Room.of(cell, 14L, 84, 84, 1, 1, 0);
        for (IsoGridSquare square : new IsoGridSquare[] {street, alley, beyond, shop.squares()[0]}) {
            square.seen(true);
            square.turns(true);
            square.hourLastSeen = HOUR - 1;
        }
        alley.seen(false);
        int told = Meta.instance.told.size();
        pass(cell, inSight(), List.of(street.getChunk()), shop);
        check(Meta.instance.told.size() == told + 1 && Meta.instance.told.get(told) == street, "of a chunk drawn, a square of open air the light finds turned seen is marked");
        check(alley.seenAsked == 1, "one they have not seen is asked about and left");
        check(beyond.seenAsked == 0, "a square of a chunk not drawn is not asked about");
        check(shop.squares()[0].seenAsked == 0, "nor a room's square on the chunk drawn: a room's squares are asked about as it is seen into");
    }

    private static void aClientSpotsRoomsAndMarksNothing() {
        IsoCell cell = new IsoCell();
        Room room = Room.of(cell, 15L, 50, 50, 1, 1, 0);
        IsoGridSquare square = room.squares()[0], street = cell.square(52, 50, 0);
        for (IsoGridSquare seen : new IsoGridSquare[] {square, street}) {
            seen.seen(true);
            seen.hourLastSeen = HOUR - 1;
        }
        square.inReach(true);
        int told = Meta.instance.told.size();
        GameClient.client = true;
        try {
            pass(cell, inSight(room), List.of(street.getChunk()), room);
        } finally {
            GameClient.client = false;
        }
        check(room.def().explored, "a client spots a room it sees into, as the game's own light has it do");
        check(Meta.instance.told.size() == told && street.seenAsked == 0, "and marks no square seen: the server keeps what was seen when");
    }

    private static void passesComeFourTimesASecondWhateverTheFrames() {
        Spotting spotting = new Spotting(0);
        long start = 5_000_000_000L;
        check(spotting.due(start), "the first frame makes a pass");
        int passes = 1;
        for (long at = start + 4_000_000L; at < start + 1_000_000_000L; at += 4_000_000L) {
            passes += spotting.due(at) ? 1 : 0;
        }
        check(passes == 4, String.format(Locale.ROOT, "at 250 frames a second a second has four passes, not %d", passes));
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
