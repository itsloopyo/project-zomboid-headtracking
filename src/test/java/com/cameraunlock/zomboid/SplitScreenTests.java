package com.cameraunlock.zomboid;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Arrays;

/** Tests of how two players share the screen, and of the rewrites that put it into the game's sums. */
final class SplitScreenTests {
    private static final int WIDTH = 1920, HEIGHT = 1080;
    private static final String SELF = "com/cameraunlock/zomboid/SplitScreenTests";
    private static final MethodTypeDesc INT_OF_INT = MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_int);

    private SplitScreenTests() {}

    static void run() {
        Checks.run("twoPlayersSideBySideAreAsTheGameHasThem", SplitScreenTests::twoPlayersSideBySideAreAsTheGameHasThem);
        Checks.run("twoPlayersStackedHaveTheWholeWidthAndHalfTheHeightEach", SplitScreenTests::twoPlayersStackedHaveTheWholeWidthAndHalfTheHeightEach);
        Checks.run("anyOtherNumberOfPlayersIsAsTheGameHasIt", SplitScreenTests::anyOtherNumberOfPlayersIsAsTheGameHasIt);
        try {
            aCountComparedIsFilteredAndOneUsedOtherwiseIsNot();
            aCountComparedIsCountedAndItsBranchRequired();
            theFirstOperandIsFiltered();
            theFirstArgumentIsFilteredBeforeAnythingRuns();
        } catch (AssertionError failed) {
            throw failed;
        } catch (Throwable thrown) {
            throw new AssertionError("a rewritten class could not be made or called", thrown);
        }
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /**
     * A player's part of the screen as the game works it out, left, top, width and height:
     * the width halved for more than one player and the height for more than two, the right
     * column for players 1 and 3 and the bottom row for 2 and 3.
     */
    private static int[] gamesSum(int across, int down, int seat) {
        return new int[] {seat == 1 || seat == 3 ? WIDTH / 2 : 0, seat >= 2 ? HEIGHT / 2 : 0, across > 1 ? WIDTH / 2 : WIDTH, down > 2 ? HEIGHT / 2 : HEIGHT};
    }

    private static int[] part(int layout, int players, int index) {
        return gamesSum(SplitScreen.across(layout, players), SplitScreen.down(layout, players), SplitScreen.seat(layout, players, index));
    }

    private static void twoPlayersSideBySideAreAsTheGameHasThem() {
        check(Arrays.equals(part(SplitScreen.VERTICAL, 2, 0), new int[] {0, 0, 960, 1080}), "side by side, player 1 has the left half: " + Arrays.toString(part(SplitScreen.VERTICAL, 2, 0)));
        check(Arrays.equals(part(SplitScreen.VERTICAL, 2, 1), new int[] {960, 0, 960, 1080}), "and player 2 the right half: " + Arrays.toString(part(SplitScreen.VERTICAL, 2, 1)));
        check(!SplitScreen.stacked(SplitScreen.VERTICAL, 2), "which is not one above the other");
    }

    private static void twoPlayersStackedHaveTheWholeWidthAndHalfTheHeightEach() {
        check(Arrays.equals(part(SplitScreen.HORIZONTAL, 2, 0), new int[] {0, 0, 1920, 540}), "one above the other, player 1 has the top half: " + Arrays.toString(part(SplitScreen.HORIZONTAL, 2, 0)));
        check(Arrays.equals(part(SplitScreen.HORIZONTAL, 2, 1), new int[] {0, 540, 1920, 540}), "and player 2 the bottom half: " + Arrays.toString(part(SplitScreen.HORIZONTAL, 2, 1)));
        check(SplitScreen.stacked(SplitScreen.HORIZONTAL, 2), "which is one above the other");
    }

    private static void anyOtherNumberOfPlayersIsAsTheGameHasIt() {
        for (int players : new int[] {1, 3, 4}) {
            check(!SplitScreen.stacked(SplitScreen.HORIZONTAL, players), players + " players are never one above the other");
            for (int index = 0; index < 4; index++) {
                check(Arrays.equals(part(SplitScreen.HORIZONTAL, players, index), gamesSum(players, players, index)),
                        "with " + players + " at the screen player " + (index + 1) + " has the part the game gives: " + Arrays.toString(part(SplitScreen.HORIZONTAL, players, index)));
            }
        }
        check(Arrays.equals(part(SplitScreen.HORIZONTAL, 1, 0), new int[] {0, 0, 1920, 1080}), "one player has the whole screen");
        check(Arrays.equals(part(SplitScreen.HORIZONTAL, 4, 3), new int[] {960, 540, 960, 540}), "and the fourth of four the bottom right quarter");
    }

    public static int tenTimes(int value) {
        return value * 10;
    }

    public static int oneMore(int value) {
        return value + 1;
    }

    /**
     * A class with a static int count and a method that answers 2 when the count is over
     * {@code against} and 1 when it is not, then adds the count itself to that.
     */
    private static byte[] subjectComparing(String name, int against, boolean branches) {
        ClassDesc self = ClassDesc.of("com.cameraunlock.zomboid." + name);
        return ClassFile.of().build(self, type -> type
                .withFlags(ClassFile.ACC_PUBLIC)
                .withField("count", ConstantDescs.CD_int, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC)
                .withMethodBody("sum", MethodTypeDesc.of(ConstantDescs.CD_int), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                    code.getstatic(self, "count", ConstantDescs.CD_int).loadConstant(against);
                    if (branches) {
                        code.ifThenElse(Opcode.IF_ICMPGT, over -> over.iconst_2(), under -> under.iconst_1());
                    } else {
                        code.iadd();
                    }
                    code.getstatic(self, "count", ConstantDescs.CD_int).iadd().ireturn();
                }));
    }

    private static void aCountComparedIsFilteredAndOneUsedOtherwiseIsNot() throws Throwable {
        String name = "SubjectComparingItsCount";
        CallSite.Method within = new CallSite.Method("com/cameraunlock/zomboid/" + name, "sum", "()I");
        byte[] rewritten = CallSite.filterStaticIntCompared(subjectComparing(name, 1, true), within, within.owner(), "count", 1,
                new CallSite.Method(SELF, "tenTimes", "(I)I"), 1);
        Class<?> defined = MethodHandles.lookup().defineClass(rewritten);
        Method sum = defined.getMethod("sum");
        defined.getField("count").setInt(null, 1);
        // Ten times one is over one where one is not, and the count added afterwards is the count as it is.
        check((int) sum.invoke(null) == 3, "the count that is compared goes through the filter and the one that is added does not: " + sum.invoke(null));
        defined.getField("count").setInt(null, 0);
        check((int) sum.invoke(null) == 1, "and what the filter hands back is what is compared: " + sum.invoke(null));
    }

    private static void aCountComparedIsCountedAndItsBranchRequired() {
        String name = "SubjectComparingItsCountTwice";
        CallSite.Method within = new CallSite.Method("com/cameraunlock/zomboid/" + name, "sum", "()I");
        CallSite.Method filter = new CallSite.Method(SELF, "tenTimes", "(I)I");
        try {
            CallSite.filterStaticIntCompared(subjectComparing(name, 1, true), within, within.owner(), "count", 1, filter, 2);
            throw new AssertionError("one comparison where two were expected was let through");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("expected 2") && expected.getMessage().contains("found 1"), "one comparison where two are expected is refused: " + expected.getMessage());
        }
        try {
            CallSite.filterStaticIntCompared(subjectComparing(name, 1, true), within, within.owner(), "count", 2, filter, 1);
            throw new AssertionError("a comparison with another constant was taken for the one asked for");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("found 0"), "a comparison with another constant is not the one asked for: " + expected.getMessage());
        }
        try {
            CallSite.filterStaticIntCompared(subjectComparing(name, 1, false), within, within.owner(), "count", 1, filter, 1);
            throw new AssertionError("a count added to the constant was taken for one compared with it");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("not by a branch"), "the count and the constant with no branch on them are refused: " + expected.getMessage());
        }
    }

    private static void theFirstOperandIsFiltered() throws Throwable {
        ClassDesc self = ClassDesc.of("com.cameraunlock.zomboid.SubjectTakingARemainder");
        MethodTypeDesc twoInts = MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_int, ConstantDescs.CD_int);
        byte[] subject = ClassFile.of().build(self, type -> type
                .withFlags(ClassFile.ACC_PUBLIC)
                .withMethodBody("left", twoInts, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> code.iload(0).iload(1).irem().ireturn())
                .withMethodBody("under", twoInts, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                    Label less = code.newLabel();
                    code.iload(0).iload(1).if_icmplt(less).iconst_0().ireturn().labelBinding(less).iconst_1().ireturn();
                }));
        CallSite.Method left = new CallSite.Method("com/cameraunlock/zomboid/SubjectTakingARemainder", "left", "(II)I");
        CallSite.Method under = new CallSite.Method("com/cameraunlock/zomboid/SubjectTakingARemainder", "under", "(II)I");
        CallSite.Method filter = new CallSite.Method(SELF, "oneMore", "(I)I");
        byte[] rewritten = CallSite.filterFirstOperandOfEach(CallSite.filterFirstOperandOfEach(subject, left, Opcode.IREM, filter, 1), under, Opcode.IF_ICMPLT, filter, 1);
        Class<?> defined = MethodHandles.lookup().defineClass(rewritten);
        check((int) defined.getMethod("left", int.class, int.class).invoke(null, 5, 4) == 2, "the number divided goes through the filter and the one it is divided by does not");
        check((int) defined.getMethod("under", int.class, int.class).invoke(null, 1, 2) == 0 && (int) defined.getMethod("under", int.class, int.class).invoke(null, 0, 2) == 1,
                "and so does the first of two numbers a branch compares");
        try {
            CallSite.filterFirstOperandOfEach(subject, left, Opcode.IREM, filter, 2);
            throw new AssertionError("one remainder where two were expected was let through");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("found 1"), "one remainder where two are expected is refused: " + expected.getMessage());
        }
    }

    private static void theFirstArgumentIsFilteredBeforeAnythingRuns() throws Throwable {
        ClassDesc self = ClassDesc.of("com.cameraunlock.zomboid.SubjectHandedANumber");
        byte[] subject = ClassFile.of().build(self, type -> type
                .withFlags(ClassFile.ACC_PUBLIC)
                .withMethodBody("<init>", MethodTypeDesc.of(ConstantDescs.CD_void), ClassFile.ACC_PUBLIC, code ->
                        code.aload(0).invokespecial(ConstantDescs.CD_Object, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void)).return_())
                .withMethodBody("handed", INT_OF_INT, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> code.iload(0).ireturn())
                .withMethodBody("ofAnObject", INT_OF_INT, ClassFile.ACC_PUBLIC, code -> code.iload(1).ireturn()));
        CallSite.Method filter = new CallSite.Method(SELF, "tenTimes", "(I)I");
        Class<?> defined = MethodHandles.lookup().defineClass(CallSite.filterFirstStaticIntArgument(subject,
                new CallSite.Method("com/cameraunlock/zomboid/SubjectHandedANumber", "handed", "(I)I"), filter));
        check((int) defined.getMethod("handed", int.class).invoke(null, 7) == 70, "the method works with what the filter made of the number it was handed");
        try {
            CallSite.filterFirstStaticIntArgument(subject, new CallSite.Method("com/cameraunlock/zomboid/SubjectHandedANumber", "ofAnObject", "(I)I"), filter);
            throw new AssertionError("a method of an object was rewritten as a static one");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("not static"), "a method of an object, whose first slot is the object, is refused: " + expected.getMessage());
        }
    }
}
