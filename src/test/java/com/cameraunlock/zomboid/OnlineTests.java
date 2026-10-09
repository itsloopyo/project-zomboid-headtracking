package com.cameraunlock.zomboid;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.util.Locale;

/** What the mod does differently on a server, as far as it can be worked out without one. */
public final class OnlineTests {
    private OnlineTests() {}

    static void run() {
        Checks.run("keysAheadAreToldFromKeysAside", OnlineTests::keysAheadAreToldFromKeysAside);
        Checks.run("onlyAServerThatLetsPlayersFightHoldsAnythingBack", OnlineTests::onlyAServerThatLetsPlayersFightHoldsAnythingBack);
        Checks.run("aViewIsOnAPersonFromFeetToHead", OnlineTests::aViewIsOnAPersonFromFeetToHead);
        try {
            aCallIsHandedToItsReplacement();
        } catch (AssertionError failed) {
            throw failed;
        } catch (Throwable thrown) {
            throw new AssertionError("a rewritten class could not be made or called", thrown);
        }
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void onlyAServerThatLetsPlayersFightHoldsAnythingBack() {
        Servers.in(Servers.Where.ALONE, () -> check(!Online.joined() && !Online.contested() && Online.leanCarriesTheShot() && !Online.bodyFacesTheView(),
                "alone the lean carries the shot and the body walks as it likes"));
        Servers.in(Servers.Where.FRIENDLY, () -> check(Online.joined() && !Online.contested() && Online.leanCarriesTheShot() && !Online.bodyFacesTheView(),
                "on a server where players cannot hurt each other nothing is held back either"));
        Servers.in(Servers.Where.CONTESTED, () -> check(Online.joined() && Online.contested() && !Online.leanCarriesTheShot() && Online.bodyFacesTheView(),
                "on one where they can, the lean does not carry the shot and the body faces the view"));
        Servers.in(Servers.Where.ALONE, () -> {
            zombie.network.ServerOptions.getInstance().pvp.setValue(true);
            check(!Online.contested(), "a game played alone is no fight whatever its own server options say");
        });
    }

    private static void keysAheadAreToldFromKeysAside() {
        // As the game hands them over: forward is up the screen, and one key is 1.95 long.
        check(!Online.aside(0f, -1.95f), "forward is ahead");
        check(!Online.aside(1.38f, -1.38f), "forward with the right key is ahead");
        check(!Online.aside(-1.38f, -1.38f), "forward with the left key is ahead");
        check(Online.aside(1.95f, 0f), "the right key alone is aside");
        check(Online.aside(-1.95f, 0f), "the left key alone is aside");
        check(Online.aside(0f, 1.95f), "back is aside");
        check(Online.aside(1.38f, 1.38f), "back with a sideways key is aside");
        check(!Online.aside(0f, 0f), "no key is no walk at all");
    }

    private static void aViewIsOnAPersonFromFeetToHead() {
        ViewCamera.Pose level = new ViewCamera.Pose();
        ViewCamera.clean(level, 10f, 20f, 1.6f, 0f, 0f);
        check(Math.abs(NameTags.passes(level, 14f, 20f, 0f) - 4f) < 1e-4f, "someone straight ahead is looked at, as far off as they stand");
        check(Math.abs(NameTags.passes(level, 14f, 20.5f, 0f) - 4f) < 1e-4f, "and with the view on their shoulder");
        check(Float.isNaN(NameTags.passes(level, 14f, 20.8f, 0f)), "someone the view passes beside is not");
        check(Float.isNaN(NameTags.passes(level, 6f, 20f, 0f)), "nor someone behind");
        check(Float.isNaN(NameTags.passes(level, 14f, 20f, 2f)), "nor someone whose feet are above the view");
        check(Float.isNaN(NameTags.passes(level, 14f, 20f, -1f)), "nor someone whose head is below it");

        ViewCamera.Pose up = new ViewCamera.Pose();
        ViewCamera.clean(up, 10f, 20f, 1.6f, 0f, (float) Math.toRadians(30));
        check(Float.isNaN(NameTags.passes(up, 14f, 20f, 0f)), "a view raised over their head is not on them");
        check(!Float.isNaN(NameTags.passes(up, 10.5f, 20f, 0f)), "and is on someone near enough to be under it");

        ViewCamera.Pose down = new ViewCamera.Pose();
        ViewCamera.clean(down, 10f, 20f, 1.6f, 0f, (float) Math.toRadians(-90));
        check(Float.isNaN(NameTags.passes(down, 10f, 20f, 0f)), "a view straight down is on nobody");
    }

    /** What the replaced call is handed to: the object it was made on, then what it took. */
    public static String shout(StringBuilder said, int from) {
        return said.substring(from).toUpperCase(Locale.ROOT);
    }

    private static void aCallIsHandedToItsReplacement() throws Throwable {
        ClassDesc builder = ClassDesc.of("java.lang.StringBuilder"), string = ConstantDescs.CD_String;
        MethodTypeDesc substring = MethodTypeDesc.of(string, ConstantDescs.CD_int);
        byte[] subject = ClassFile.of().build(ClassDesc.of("com.cameraunlock.zomboid.SubjectWithACallOnAnObject"), type -> type
                .withFlags(ClassFile.ACC_PUBLIC)
                .withMethodBody("say", MethodTypeDesc.of(string), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                    code.new_(builder);
                    code.dup();
                    code.ldc("a word");
                    code.invokespecial(builder, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void, string));
                    code.iconst_2();
                    code.invokevirtual(builder, "substring", substring);
                    code.areturn();
                }));
        CallSite.Method within = new CallSite.Method("com/cameraunlock/zomboid/SubjectWithACallOnAnObject", "say", "()Ljava/lang/String;");
        CallSite.Method call = new CallSite.Method("java/lang/StringBuilder", "substring", "(I)Ljava/lang/String;");
        CallSite.Method hook = new CallSite.Method("com/cameraunlock/zomboid/OnlineTests", "shout", "(Ljava/lang/StringBuilder;I)Ljava/lang/String;");
        Class<?> replaced = MethodHandles.lookup().defineClass(CallSite.replaceEach(subject, within, call, hook, 1));
        check("WORD".equals(replaced.getMethod("say").invoke(null)), "the replacement is given the object and what the call took, and its answer is the call's");
        boolean refused = false;
        try {
            CallSite.replaceEach(subject, within, call, hook, 2);
        } catch (IllegalStateException expected) {
            refused = true;
        }
        check(refused, "a method with fewer of the call than expected is rejected");
    }
}
