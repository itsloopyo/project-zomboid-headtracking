package com.cameraunlock.zomboid;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import zombie.iso.IsoChunk;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoObjectType;

final class MarkTests {
    private MarkTests() {}

    static void run() {
        Checks.run("aChunkThatIsToldHasAnotherNumber", MarkTests::aChunkThatIsToldHasAnotherNumber);
        Checks.run("aBoxOfChunksIsToldWhole", MarkTests::aBoxOfChunksIsToldWhole);
        Checks.run("aLightIsMarkedApartFromThings", MarkTests::aLightIsMarkedApartFromThings);
        Checks.run("aChunkSharingItsPlaceIsNotToldWithAnother", MarkTests::aChunkSharingItsPlaceIsNotToldWithAnother);
        Checks.run("aChunkLetGoOfIsNeverReadAsUntold", MarkTests::aChunkLetGoOfIsNeverReadAsUntold);
        Checks.run("aChunkKeptReadsWhatItDidWhenOneInItsPlaceIsLetGoOf", MarkTests::aChunkKeptReadsWhatItDidWhenOneInItsPlaceIsLetGoOf);
        Checks.run("aChunkAnotherViewKeepsIsNotLetGoOf", MarkTests::aChunkAnotherViewKeepsIsNotLetGoOf);
        Checks.run("aChunkLoadedIsMarkedForWhatMayBeBuiltOnIt", MarkTests::aChunkLoadedIsMarkedForWhatMayBeBuiltOnIt);
        Checks.run("aFireLitOrPutOutIsMarkedOnItsChunk", MarkTests::aFireLitOrPutOutIsMarkedOnItsChunk);
        Checks.run("dirtStampedIsMarkedOnTheSquaresBesideIt", MarkTests::dirtStampedIsMarkedOnTheSquaresBesideIt);
        Checks.run("aSquareMadeOnALoadedChunkIsMarkedOnItsLevel", MarkTests::aSquareMadeOnALoadedChunkIsMarkedOnItsLevel);
        Checks.run("aSquareALoadMakesBesideItIsMarkedOnlyForWhatMayBeBuiltOnIt", MarkTests::aSquareALoadMakesBesideItIsMarkedOnlyForWhatMayBeBuiltOnIt);
        Checks.run("aTallyIsCountedIntoAnother", MarkTests::aTallyIsCountedIntoAnother);
        Checks.run("aMethodOfAnObjectHandsItselfOverBeforeAnythingElse", MarkTests::aMethodOfAnObjectHandsItselfOverBeforeAnythingElse);
        Checks.run("aStaticMethodIsRefused", MarkTests::aStaticMethodIsRefused);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void aLightIsMarkedApartFromThings() {
        zombie.iso.IsoChunk chunk = new zombie.iso.IsoChunk();
        chunk.wx = 300;
        chunk.wy = 400;
        chunk.loaded = true;
        int all = ChunkMarks.around(300, 400), beside = ChunkMarks.around(301, 401), things = ChunkMarks.thingsAround(300, 400), far = ChunkMarks.around(302, 400);
        ChunkMarks.marked(chunk, 32L);
        check(ChunkMarks.around(300, 400) == all + 1 && ChunkMarks.around(301, 401) == beside + 1, "a light marked on a chunk is a mark round it and round the chunks beside it");
        check(ChunkMarks.thingsAround(300, 400) == things, "and none for things");
        check(ChunkMarks.around(302, 400) == far, "a chunk two away does not count it");
    }

    private static void aChunkLoadedIsMarkedForWhatMayBeBuiltOnIt() {
        IsoChunk chunk = new IsoChunk();
        chunk.wx = 700;
        chunk.wy = 702;
        int round = ChunkMarks.structuresIn(699, 701, 701, 703), fourth = ChunkMarks.structuresIn(700, 702, 700, 702, 4, 4);
        int loose = ChunkMarks.structuresLoose(700, 702, 700, 702), far = ChunkMarks.structuresIn(702, 702, 702, 702);
        int all = ChunkMarks.around(700, 702), things = ChunkMarks.thingsAround(700, 702), beside = ChunkMarks.around(701, 703), farAll = ChunkMarks.around(702, 702);
        ChunkMarks.loaded(chunk);
        check(ChunkMarks.structuresIn(699, 701, 701, 703) == round + 1, "a chunk loaded is marked for what may be part of a building");
        check(ChunkMarks.structuresIn(700, 702, 700, 702, 4, 4) == fourth + 1 && ChunkMarks.structuresLoose(700, 702, 700, 702) == loose + 1,
                "with no level, so that every level of it is read again");
        check(ChunkMarks.structuresIn(702, 702, 702, 702) == far, "a chunk two away is not marked");
        check(ChunkMarks.around(700, 702) == all + 1 && ChunkMarks.thingsAround(700, 702) == things + 1 && ChunkMarks.around(701, 703) == beside + 1,
                "and for what is on it, so that the builds of it and of the chunks round it are read again");
        check(ChunkMarks.around(702, 702) == farAll, "but not the build of a chunk two away");
    }

    private static void aChunkThatIsToldHasAnotherNumber() {
        int at = ChunkMarks.told(500, 700), beside = ChunkMarks.told(501, 700);
        ChunkMarks.tell(500, 700);
        check(ChunkMarks.told(500, 700) != at, "a chunk that is told has another number");
        check(ChunkMarks.told(501, 700) == beside, "the chunk beside it has the number it had");
        // The table is 64 chunks a side and the loaded map at most 19, so the chunk 64 on is never loaded with this one.
        check(ChunkMarks.slot(500, 700) == ChunkMarks.slot(500 + ChunkMarks.SIDE, 700 - ChunkMarks.SIDE), "a chunk shares its place with the one a table's width on");
        check(ChunkMarks.slot(-1, 0) != ChunkMarks.slot(0, 0) && ChunkMarks.slot(-1, 0) == ChunkMarks.slot(63, 0), "and chunks west of the map's origin have places of their own");
    }

    private static void aBoxOfChunksIsToldWhole() {
        int inside = ChunkMarks.told(900, 902), corner = ChunkMarks.told(901, 899), outside = ChunkMarks.told(902, 900);
        ChunkMarks.tell(899, 899, 901, 902);
        check(ChunkMarks.told(900, 902) != inside && ChunkMarks.told(901, 899) != corner, "every chunk of a box that is told has another number");
        check(ChunkMarks.told(902, 900) == outside, "a chunk beside the box is not told");
    }

    private static void aChunkSharingItsPlaceIsNotToldWithAnother() {
        ChunkMarks.tell(500, 700);
        int at = ChunkMarks.told(500, 700);
        ChunkMarks.tell(500 + ChunkMarks.SIDE, 700);
        ChunkMarks.tell(500, 700 - ChunkMarks.SIDE, 500, 700 - ChunkMarks.SIDE);
        check(ChunkMarks.told(500, 700) == at, "a chunk is not told when the one a table's width on is");
        check(ChunkMarks.told(500 + ChunkMarks.SIDE, 700) != at && ChunkMarks.told(500 + ChunkMarks.SIDE, 700) != 0, "and that one is");
    }

    private static void aChunkLetGoOfIsNeverReadAsUntold() {
        ChunkMarks.tell(400, 400);
        ChunkMarks.tell(430, 430);
        int on = ChunkMarks.told(400, 400), off = ChunkMarks.told(430, 430);
        ChunkMarks.keepTellings(0, 390, 390, 21);
        check(ChunkMarks.told(400, 400) == on, "a chunk a view keeps keeps its number");
        check(ChunkMarks.told(430, 430) == off, "one no view keeps is let go of and reads what it read: a build made since it was told is still good");
        int beside = ChunkMarks.told(430 - ChunkMarks.SIDE, 430);
        check(beside == off, "a chunk in the same place that never was told reads it too: a place reads no less than the last telling let go of in it");
        ChunkMarks.tell(430, 430);
        check(ChunkMarks.told(430, 430) != off, "and told again it has a number it never had");
    }

    private static void aChunkKeptReadsWhatItDidWhenOneInItsPlaceIsLetGoOf() {
        int kept = ChunkMarks.told(401, 402);
        ChunkMarks.tell(401 + ChunkMarks.SIDE, 402);
        ChunkMarks.keepTellings(0, 390, 390, 21);
        check(ChunkMarks.told(401, 402) == kept, "a chunk the view keeps reads the number it read before one in its place was let go of");
        check(ChunkMarks.told(401 + ChunkMarks.SIDE, 402) != kept, "and the one let go of does not read it");
    }

    private static void aChunkAnotherViewKeepsIsNotLetGoOf() {
        ChunkMarks.keepTellings(1, 600, 600, 21);
        ChunkMarks.tell(605, 610);
        int told = ChunkMarks.told(605, 610), place = ChunkMarks.told(605 + ChunkMarks.SIDE, 610);
        ChunkMarks.keepTellings(0, 390, 390, 21);
        check(ChunkMarks.told(605, 610) == told && ChunkMarks.told(605 + ChunkMarks.SIDE, 610) == place,
                "the second player's chunks keep their tellings when the first player's view lets go of what it does not keep");
    }

    private static void aFireLitOrPutOutIsMarkedOnItsChunk() {
        TestMap map = new TestMap();
        IsoObject barbecue = map.cell.square(1134 * 8 + 3, 1150 * 8 + 3, 1).add(map.sprite("appliances_cooking_01_35", IsoObjectType.normal));
        int things = ChunkMarks.thingsAround(1134, 1150), built = ChunkMarks.structuresIn(1134, 1150, 1134, 1150);
        ChunkMarks.burning(barbecue, false, true);
        check(ChunkMarks.thingsAround(1134, 1150) == things + 1, "a barbecue lit is marked on its chunk");
        check(ChunkMarks.structuresIn(1134, 1150, 1134, 1150) == built, "and not as a part of a building");
        ChunkMarks.burning(barbecue, true, true);
        check(ChunkMarks.thingsAround(1134, 1150) == things + 1, "one lit again that was lit is not marked");
        ChunkMarks.burning(barbecue, true, false);
        check(ChunkMarks.thingsAround(1134, 1150) == things + 2, "one put out is");
    }

    private static void dirtStampedIsMarkedOnTheSquaresBesideIt() {
        TestMap map = new TestMap();
        int x = 1136 * 8, y = 1150 * 8 + 4;
        IsoGridSquare square = map.cell.square(x, y, 0);
        map.cell.square(x, y - 1, 0);
        map.cell.square(x - 1, y, 0);
        int things = ChunkMarks.thingsAround(1136, 1150);
        int here = ChunkMarks.structuresIn(1136, 1150, 1136, 1150, 0, 0), beside = ChunkMarks.structuresIn(1135, 1150, 1135, 1150, 0, 0);
        ChunkMarks.stamped(square);
        check(ChunkMarks.structuresIn(1136, 1150, 1136, 1150, 0, 0) == here + 2,
                "dirt stamped on a square is marked on its level of the chunk for it and for the square north of it, the two of the five the map has in that chunk");
        check(ChunkMarks.structuresIn(1135, 1150, 1135, 1150, 0, 0) == beside + 1, "and for the square west of it, in the chunk beside");
        check(ChunkMarks.thingsAround(1136, 1150) == things + 3, "each a mark for what is on a square");
    }

    private static void aSquareMadeOnALoadedChunkIsMarkedOnItsLevel() {
        TestMap map = new TestMap();
        // The square a chunk loading south of it puts on its edge, holding nothing.
        IsoGridSquare made = map.cell.square(1146 * 8 + 3, 1150 * 8 + 7, 1);
        int things = ChunkMarks.thingsAround(1146, 1150), built = ChunkMarks.structuresIn(1146, 1150, 1146, 1150, 1, 1);
        int ground = ChunkMarks.structuresIn(1146, 1150, 1146, 1150, 0, 0);
        ChunkMarks.made(made);
        check(ChunkMarks.thingsAround(1146, 1150) == things + 1 && ChunkMarks.structuresIn(1146, 1150, 1146, 1150, 1, 1) == built + 1,
                "a square the game puts on a loaded chunk is marked on its level, as a part of a building: a chunk loading gives the squares it makes beside it their rooms");
        check(ChunkMarks.structuresIn(1146, 1150, 1146, 1150, 0, 0) == ground, "and on that level alone");
        IsoGridSquare filling = map.cell.square(1146 * 8 + 4, 1150 * 8 + 7, 1);
        filling.filling(true);
        ChunkMarks.made(filling);
        check(ChunkMarks.thingsAround(1146, 1150) == things + 1 && ChunkMarks.structuresIn(1146, 1150, 1146, 1150, 1, 1) == built + 1,
                "one put on a chunk the map is still filling is not marked: its load is");
    }

    private static void aSquareALoadMakesBesideItIsMarkedOnlyForWhatMayBeBuiltOnIt() {
        TestMap map = new TestMap();
        IsoChunk loading = new IsoChunk();
        loading.wx = 1160;
        loading.wy = 1150;
        loading.loaded = true;
        // On the south edge of the chunk north of the one loading, and two squares further into it.
        IsoGridSquare edge = map.cell.square(1160 * 8 + 3, 1150 * 8 - 1, 1), inside = map.cell.square(1160 * 8 + 3, 1150 * 8 - 3, 1);
        int built = ChunkMarks.structuresIn(1160, 1149, 1160, 1149, 1, 1), twoAway = ChunkMarks.thingsAround(1160, 1148);
        ChunkMarks.loading(loading);
        ChunkMarks.made(edge);
        check(ChunkMarks.structuresIn(1160, 1149, 1160, 1149, 1, 1) == built + 1,
                "a square the load of a chunk puts on the edge of the chunk beside it is marked on its level, for what may be built on it");
        check(ChunkMarks.thingsAround(1160, 1148) == twoAway, "and not for what is on it: no build of a chunk two from the load reads it, and those that do have the load's own mark");
        ChunkMarks.made(inside);
        check(ChunkMarks.thingsAround(1160, 1148) == twoAway + 1, "one put two squares further in while the load runs is marked as any other");
        ChunkMarks.loaded(loading);
        twoAway = ChunkMarks.thingsAround(1160, 1148);
        ChunkMarks.made(edge);
        check(ChunkMarks.thingsAround(1160, 1148) == twoAway + 1, "and once the load is done, so is one on the edge");
    }

    private static void aTallyIsCountedIntoAnother() {
        OpenLight one = new OpenLight(), other = new OpenLight(), both = new OpenLight();
        float[] light = new float[3];
        for (int i = 0; i < 5; i++) {
            one.add(0.2f, 0.2f, 0.2f);
        }
        for (int i = 0; i < 7; i++) {
            other.add(0.8f, 0.7f, 0.6f);
        }
        new OpenLight().addTo(both);
        check(both.isEmpty(), "a tally with nothing counted adds nothing");
        one.addTo(both);
        both.commonest(light);
        check(Math.abs(light[0] - 0.2f) < 1e-6f, "one tally counted in gives its own light");
        other.addTo(both);
        both.commonest(light);
        check(Math.abs(light[0] - 0.8f) < 1e-6f && Math.abs(light[2] - 0.6f) < 1e-6f, "seven squares of the second outnumber five of the first");
        one.addTo(both);
        both.commonest(light);
        check(Math.abs(light[0] - 0.2f) < 1e-6f, "and the first counted in again outnumbers them");
    }

    private static Object marker;
    private static long markedWith;
    private static int markedBefore;

    public static void marked(Object levels, long flags) throws ReflectiveOperationException {
        marker = levels;
        markedWith = flags;
        // What the method itself does has not been done yet.
        markedBefore = levels.getClass().getField("done").getInt(levels);
    }

    private static byte[] subject(ClassDesc owner, int flags) {
        MethodTypeDesc invalidate = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_int, ConstantDescs.CD_long);
        boolean ofAnObject = (flags & ClassFile.ACC_STATIC) == 0;
        return ClassFile.of().build(owner, type -> type
                .withFlags(ClassFile.ACC_PUBLIC)
                .withField("done", ConstantDescs.CD_int, ClassFile.ACC_PUBLIC)
                .withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, code -> code
                        .aload(0).invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void).return_())
                .withMethodBody("invalidateLevel", invalidate, flags, code -> {
                    if (ofAnObject) {
                        code.aload(0).iload(1).putfield(owner, "done", ConstantDescs.CD_int);
                    }
                    code.return_();
                }));
    }

    private static void aMethodOfAnObjectHandsItselfOverBeforeAnythingElse() throws Throwable {
        ClassDesc owner = ClassDesc.of("com.cameraunlock.zomboid.SubjectMarkingALevel"), tests = ClassDesc.of("com.cameraunlock.zomboid.MarkTests");
        CallSite.Method within = new CallSite.Method("com/cameraunlock/zomboid/SubjectMarkingALevel", "invalidateLevel", "(IJ)V");
        byte[] patched = CallSite.insertAtStartOfInstanceMethod(subject(owner, ClassFile.ACC_PUBLIC), within, code -> code.aload(0).lload(2)
                .invokestatic(tests, "marked", MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object, ConstantDescs.CD_long)));
        Class<?> defined = MethodHandles.lookup().defineClass(patched);
        Object levels = defined.getConstructor().newInstance();
        defined.getMethod("invalidateLevel", int.class, long.class).invoke(levels, 3, 256L);
        check(marker == levels && markedWith == 256L, "the hook is handed the object and the flags the method was called with");
        check(markedBefore == 0 && defined.getField("done").getInt(levels) == 3, "before the method does what it did, which it still does");
    }

    private static void aStaticMethodIsRefused() {
        ClassDesc owner = ClassDesc.of("com.cameraunlock.zomboid.SubjectMarkingNothing");
        CallSite.Method within = new CallSite.Method("com/cameraunlock/zomboid/SubjectMarkingNothing", "invalidateLevel", "(IJ)V");
        byte[] subject = subject(owner, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC);
        try {
            CallSite.insertAtStartOfInstanceMethod(subject, within, code -> code.aload(0).pop());
        } catch (IllegalStateException refused) {
            return;
        }
        throw new AssertionError("a static method by the name took a patch that loads the object it has not got");
    }
}
