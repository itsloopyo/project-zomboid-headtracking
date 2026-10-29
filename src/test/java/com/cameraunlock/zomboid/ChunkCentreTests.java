package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.List;

/** Tests of where a chunk map keeps its centre: see ChunkCentre. */
final class ChunkCentreTests {
    private ChunkCentreTests() {}

    static void run() {
        Checks.run("aSwayOfMillimetresAcrossAChunkLineLeavesTheCentre", ChunkCentreTests::aSwayOfMillimetresAcrossAChunkLineLeavesTheCentre);
        Checks.run("aTilePastTheLineMovesTheCentre", ChunkCentreTests::aTilePastTheLineMovesTheCentre);
        Checks.run("aTeleportMovesTheCentreAtOnce", ChunkCentreTests::aTeleportMovesTheCentreAtOnce);
        Checks.run("aLongWalkEndsWithTheCentreTheGameWouldHaveAndMovesTheMapNoMoreOften", ChunkCentreTests::aLongWalkEndsWithTheCentreTheGameWouldHaveAndMovesTheMapNoMoreOften);
        Checks.run("eachMapKeepsItsOwnCentre", ChunkCentreTests::eachMapKeepsItsOwnCentre);
        Checks.run("aMethodWithAnotherCountOfCentresIsRefused", ChunkCentreTests::aMethodWithAnotherCountOfCentresIsRefused);
    }

    private static final String HELD = "com/cameraunlock/zomboid/SubjectHeldChunkMap", STOCK = "com/cameraunlock/zomboid/SubjectStockChunkMap";
    private static final CallSite.Method FLOOR = new CallSite.Method("com/cameraunlock/zomboid/ChunkCentreTests", "floor", "(F)I");
    private static final List<CallSite.Method> FILTERS = List.of(
            new CallSite.Method("com/cameraunlock/zomboid/ChunkCentreTests", "heldX", "(ILjava/lang/Object;F)I"),
            new CallSite.Method("com/cameraunlock/zomboid/ChunkCentreTests", "heldY", "(ILjava/lang/Object;F)I"));
    private static Class<?> held, stock;

    public static int floor(float value) {
        return (int) Math.floor(value);
    }

    public static int heldX(int centre, Object map, float x) throws ReflectiveOperationException {
        return ChunkCentre.held(map.getClass().getField("worldX").getInt(map), centre, x);
    }

    public static int heldY(int centre, Object map, float y) throws ReflectiveOperationException {
        return ChunkCentre.held(map.getClass().getField("worldY").getInt(map), centre, y);
    }

    /**
     * A map of the shape IsoChunkMap.ProcessChunkPos has: the centre worked out as the floor of the
     * place over 8, x then y, and the map moved, and the moves counted, when either differs.
     */
    private static byte[] subject(String name) {
        ClassDesc self = ClassDesc.ofInternalName(name), tests = ClassDesc.of("com.cameraunlock.zomboid.ChunkCentreTests");
        MethodTypeDesc floors = MethodTypeDesc.ofDescriptor("(F)I");
        return ClassFile.of().build(self, type -> type
                .withFlags(ClassFile.ACC_PUBLIC)
                .withField("worldX", ConstantDescs.CD_int, ClassFile.ACC_PUBLIC)
                .withField("worldY", ConstantDescs.CD_int, ClassFile.ACC_PUBLIC)
                .withField("moves", ConstantDescs.CD_int, ClassFile.ACC_PUBLIC)
                .withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, code -> code
                        .aload(0).invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void).return_())
                .withMethodBody("place", MethodTypeDesc.ofDescriptor("(FF)V"), ClassFile.ACC_PUBLIC, code -> {
                    Label move = code.newLabel();
                    code.fload(1).ldc(8f).fdiv().invokestatic(tests, "floor", floors).istore(3)
                            .fload(2).ldc(8f).fdiv().invokestatic(tests, "floor", floors).istore(4)
                            .iload(3).aload(0).getfield(self, "worldX", ConstantDescs.CD_int).if_icmpne(move)
                            .iload(4).aload(0).getfield(self, "worldY", ConstantDescs.CD_int).if_icmpne(move)
                            .return_()
                            .labelBinding(move)
                            .aload(0).iload(3).putfield(self, "worldX", ConstantDescs.CD_int)
                            .aload(0).iload(4).putfield(self, "worldY", ConstantDescs.CD_int)
                            .aload(0).dup().getfield(self, "moves", ConstantDescs.CD_int).iconst_1().iadd().putfield(self, "moves", ConstantDescs.CD_int)
                            .return_();
                }));
    }

    private static synchronized void define() throws ReflectiveOperationException {
        if (held == null) {
            held = MethodHandles.lookup().defineClass(CallSite.filterFlooredQuotients(subject(HELD), new CallSite.Method(HELD, "place", "(FF)V"), 8f, FLOOR, FILTERS));
            stock = MethodHandles.lookup().defineClass(subject(STOCK));
        }
    }

    /**
     * A map put where the game puts one as a save loads, with its centre on the chunk the place is in:
     * one rewritten to keep its centre, or one left with the game's rule.
     */
    private static Object map(boolean keepsCentre, float x, float y) throws ReflectiveOperationException {
        define();
        Class<?> kind = keepsCentre ? held : stock;
        Object map = kind.getConstructor().newInstance();
        kind.getField("worldX").setInt(map, floor(x / 8f));
        kind.getField("worldY").setInt(map, floor(y / 8f));
        return map;
    }

    private static void place(Object map, float x, float y) throws ReflectiveOperationException {
        Method place = map.getClass().getMethod("place", float.class, float.class);
        place.invoke(map, x, y);
    }

    private static int field(Object map, String name) throws ReflectiveOperationException {
        return map.getClass().getField(name).getInt(map);
    }

    private static String centre(Object map) throws ReflectiveOperationException {
        return field(map, "worldX") + "," + field(map, "worldY");
    }

    private static void aSwayOfMillimetresAcrossAChunkLineLeavesTheCentre() throws Throwable {
        // At 12800, 1530 as the rig's teleport leaves a player: x on the line between chunks 1599 and 1600.
        Object map = map(true, 12800f, 1530f);
        for (int tick = 0; tick < 2000; tick++) {
            float sway = 0.003f + 0.005f * (tick % 3) / 2f;
            place(map, 12800f + (tick % 2 == 0 ? -sway : sway), 1528f + (tick % 4 < 2 ? -sway : sway));
        }
        check(centre(map).equals("1600,191") && field(map, "moves") == 0,
                "3 to 8 mm either side of the lines at x 12800 and y 1528 leave the centre where it is: " + centre(map) + ", moved " + field(map, "moves"));
        Object stocked = map(false, 12800f, 1530f);
        for (int tick = 0; tick < 20; tick++) {
            place(stocked, 12800f + (tick % 2 == 0 ? -0.005f : 0.005f), 1530f);
        }
        check(field(stocked, "moves") == 20, "where the game's own rule moves the map on every crossing: " + field(stocked, "moves"));
    }

    private static void aTilePastTheLineMovesTheCentre() throws Throwable {
        Object map = map(true, 12800f, 1530f);
        place(map, 12799.05f, 1530f);
        check(centre(map).equals("1600,191"), "0.95 of a tile past the line the centre stays: " + centre(map));
        place(map, 12799f, 1530f);
        check(centre(map).equals("1599,191") && field(map, "moves") == 1, "a tile past it, it moves: " + centre(map));
        place(map, 12800.95f, 1530f);
        check(centre(map).equals("1599,191"), "back across the line by 0.95 of a tile, it stays: " + centre(map));
        place(map, 12801f, 1530f);
        check(centre(map).equals("1600,191") && field(map, "moves") == 2, "a tile past the line the other way, it moves back: " + centre(map));
        place(map, 12801f, 1535.5f);
        place(map, 12801f, 1537f);
        check(centre(map).equals("1600,192") && field(map, "moves") == 3, "and the same holds on y: " + centre(map) + ", moved " + field(map, "moves"));
    }

    private static void aTeleportMovesTheCentreAtOnce() throws Throwable {
        Object map = map(true, 12800f, 1530f);
        place(map, 10774.5625f, 9762.4316f);
        check(centre(map).equals("1346,1220") && field(map, "moves") == 1, "a teleport takes the centre to where the game puts it in one tick: " + centre(map));
        place(map, 10776.5f, 9762.4316f);
        check(centre(map).equals("1346,1220"), "from there, half a tile past the next line leaves it: " + centre(map));
        place(map, 10777f, 9762.4316f);
        check(centre(map).equals("1347,1220"), "and a tile past it moves it: " + centre(map));
    }

    private static void aLongWalkEndsWithTheCentreTheGameWouldHaveAndMovesTheMapNoMoreOften() throws Throwable {
        Object map = map(true, 12800f, 1530f), stocked = map(false, 12800f, 1530f);
        float fromX = 12800f, fromY = 1530f, toX = 13004.5f, toY = 1300.5f;
        int steps = 8000;
        for (int step = 1; step <= steps; step++) {
            float along = step / (float) steps, sway = (step % 2 == 0 ? 0.004f : -0.004f);
            float x = fromX + (toX - fromX) * along + sway, y = fromY + (toY - fromY) * along - sway;
            place(map, x, y);
            place(stocked, x, y);
        }
        place(map, toX, toY);
        place(stocked, toX, toY);
        check(centre(map).equals(centre(stocked)) && centre(map).equals("1625,162"),
                "a walk of 300 tiles ends with the centre the game's rule gives: " + centre(map) + " against " + centre(stocked));
        // 25 chunk lines crossed on x and 29 on y: at least 29 moves, the two axes moving together where they cross close.
        check(field(map, "moves") >= 29 && field(map, "moves") <= field(stocked, "moves"),
                "having moved the map " + field(map, "moves") + " times where the game's rule moved it " + field(stocked, "moves"));
    }

    private static void eachMapKeepsItsOwnCentre() throws Throwable {
        Object first = map(true, 12800f, 1530f), second = map(true, 12804f, 1530f);
        place(first, 12799.5f, 1530f);
        place(second, 12799.5f, 1530f);
        check(centre(first).equals("1600,191"), "half a tile past the line the first player's map keeps its centre: " + centre(first));
        check(centre(second).equals("1600,191"), "and so does the second's, from its own: " + centre(second));
        place(second, 12798.5f, 1530f);
        check(centre(first).equals("1600,191") && centre(second).equals("1599,191"),
                "the second walking on moves its own map and not the first's: " + centre(first) + " and " + centre(second));
    }

    private static void aMethodWithAnotherCountOfCentresIsRefused() {
        boolean refused = false;
        try {
            CallSite.filterFlooredQuotients(subject("com/cameraunlock/zomboid/SubjectRefused"),
                    new CallSite.Method("com/cameraunlock/zomboid/SubjectRefused", "place", "(FF)V"), 8f, FLOOR, FILTERS.subList(0, 1));
        } catch (IllegalStateException expected) {
            refused = expected.getMessage().contains("found 2");
        }
        check(refused, "a method that works out two centres where one is expected is not rewritten");
    }
}
