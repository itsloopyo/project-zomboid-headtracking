package com.cameraunlock.zomboid;

/** Tests of what a fault in the mod switches off: see Faults. */
final class FaultsTests {
    private FaultsTests() {}

    static void run() {
        Checks.run("everyClassAPartOrARowIsMadeOfIsTheMods", FaultsTests::everyClassAPartOrARowIsMadeOfIsTheMods);
        Checks.run("aFaultInAPartsCodeSwitchesThatPartOffAndNotTheFile", FaultsTests::aFaultInAPartsCodeSwitchesThatPartOffAndNotTheFile);
        Checks.run("aFaultInCodeThePartsShareIsNoPartsWhateverCalledIt", FaultsTests::aFaultInCodeThePartsShareIsNoPartsWhateverCalledIt);
        Checks.run("aFaultFromABuildThreadIsTracedThroughWhatItCaused", FaultsTests::aFaultFromABuildThreadIsTracedThroughWhatItCaused);
        Checks.run("aFaultInModelsIsThePropsWhereThePropsAsked", FaultsTests::aFaultInModelsIsThePropsWhereThePropsAsked);
        Checks.run("aFaultNoPartOwnsSwitchesNothingOff", FaultsTests::aFaultNoPartOwnsSwitchesNothingOff);
        Checks.run("onlyFaultsThatKeepComingAreTooMany", FaultsTests::onlyFaultsThatKeepComingAreTooMany);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static final String PACKAGE = "com.cameraunlock.zomboid.";

    /** A fault thrown from those classes of the mod's, the first nearest the throw, under the game's frame. */
    private static RuntimeException thrownIn(String... classes) {
        RuntimeException thrown = new ArrayIndexOutOfBoundsException("Index 8 out of bounds for length 8");
        StackTraceElement[] trace = new StackTraceElement[classes.length + 1];
        for (int i = 0; i < classes.length; i++) {
            trace[i] = new StackTraceElement(classes[i].contains(".") ? classes[i] : PACKAGE + classes[i], "method", null, 1);
        }
        trace[classes.length] = new StackTraceElement("zombie.gameStates.IngameState", "renderFrameInternal", null, 1232);
        thrown.setStackTrace(trace);
        return thrown;
    }

    /** Runs a test and puts back what faults have switched off. */
    private static void mending(Runnable test) {
        try {
            test.run();
        } finally {
            for (Part part : Part.values()) {
                TestMap.write(part, "broken", false);
            }
        }
    }

    private static void everyClassAPartOrARowIsMadeOfIsTheMods() {
        for (String name : Faults.PARTS.keySet()) {
            check(loads(name), name + " is a class of the mod's");
        }
        for (Part part : Faults.PARTS.values()) {
            check(OptionsLayout.label(part.key) != null, part.key + " has a name on the options screen to be said by");
        }
        check(!Faults.PARTS.containsKey("FirstPerson") && !Faults.PARTS.containsKey("Pipeline") && !Faults.PARTS.containsKey("WorldScene"),
                "the view's own classes are no part's");
    }

    private static boolean loads(String name) {
        try {
            Class.forName(PACKAGE + name, false, FaultsTests.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException missing) {
            return false;
        }
    }

    private static void aFaultInAPartsCodeSwitchesThatPartOffAndNotTheFile() {
        mending(() -> {
            boolean row = Part.HIT_DIRECTION.row();
            int before = Faults.switchedOff();
            String off = Faults.switchOff(thrownIn("HitArcs", "HitDirection", "FirstPerson", "Hooks"));
            check(OptionsLayout.label(Part.HIT_DIRECTION.key).equals(off), "a fault in the hit glow's code is the hit glow's: " + off);
            check(!Part.HIT_DIRECTION.on() && Part.HIT_DIRECTION.broken(), "which is off");
            check(Part.HIT_DIRECTION.row() == row, "and the row the file is written from is as it was");
            check(Faults.switchedOff() == before + 1 && Faults.names().endsWith(off), "and the views are told: " + Faults.names());
            check(Faults.switchOff(thrownIn("HitDirection", "FirstPerson")) == null, "a part that throws once it is off is no help off, and the fault is the view's");
            check(Faults.switchOff(thrownIn("ThirdPerson$Boom", "FirstPerson")) != null && !Part.THIRD_PERSON.on(),
                    "a class inside a part's class is the part's: third person goes, and first person with it stays");
        });
        check(Part.HIT_DIRECTION.on(), "mended after the test");
    }

    private static void aFaultInCodeThePartsShareIsNoPartsWhateverCalledIt() {
        mending(() -> {
            check(Faults.switchOff(thrownIn("WorldQuery", "ThirdPerson", "FirstPerson")) == null && Part.THIRD_PERSON.on(),
                    "a fault in the walls the boom asks about is the view's, and third person stays");
            check(Faults.switchOff(thrownIn("java.util.ArrayList", "HitDirection", "FirstPerson")) != null && !Part.HIT_DIRECTION.on(),
                    "a fault in the platform's code is the part's of the mod's class that called it");
        });
    }

    private static void aFaultFromABuildThreadIsTracedThroughWhatItCaused() {
        mending(() -> {
            RuntimeException handedBack = thrownOnABuildThread(thrownIn("Props", "FurnishingBuilder", "WorldScene"));
            check(handedBack.getStackTrace()[0].getClassName().endsWith(".BuildThreads") && handedBack.getCause() != null,
                    "BuildThreads hands back what a build thread threw from its own frames: " + handedBack.getStackTrace()[0]);
            String off = Faults.switchOff(handedBack);
            check(off != null && !Part.FURNITURE_DECOR.on(), "a fault in the props on a build thread is the props', and not the frame's: " + off);
            check(Faults.switchOff(thrownOnABuildThread(thrownIn("WorldScene", "Props"))) == null,
                    "and a fault in the view's code on a build thread is still the view's, whatever called it");
            check(Faults.switchOff(thrownOnABuildThread(new IllegalStateException("no mod frame"))) == null,
                    "and one with none of the mod's code in it is no part's, BuildThreads' own frames aside");
        });
    }

    /** What BuildThreads throws on the thread that waits, where a build it ran on a build thread threw that. */
    private static RuntimeException thrownOnABuildThread(RuntimeException thrown) {
        java.util.concurrent.ExecutorService thread = BuildThreads.one("tests' build thread");
        try {
            BuildThreads.runAndWait(thread, () -> {
                throw thrown;
            });
        } catch (RuntimeException handedBack) {
            return handedBack;
        } finally {
            thread.shutdownNow();
        }
        throw new AssertionError("a build that threw on a build thread was waited on as if it had not");
    }

    private static void aFaultInModelsIsThePropsWhereThePropsAsked() {
        mending(() -> {
            check(Faults.switchOff(thrownIn("Models", "Props", "FurnishingBuilder")) == null && Part.FURNITURE_DECOR.on(),
                    "a fault inside Models that no Models.get or has gave Props is the view's");
            check(Faults.switchOff(thrownIn("Models", "FurnishingBuilder", "Props")) == null && Part.FURNITURE_DECOR.on(),
                    "and a model the furniture asks for is the view's, though the props called the furniture");
            String off = Faults.switchOff(asked("get", "Props"));
            check(off != null && !Part.FURNITURE_DECOR.on(), "a model the props ask for and the jar has not is the props': " + off);
        });
        mending(() -> check(Faults.switchOff(asked("has", "Props")) != null && !Part.FURNITURE_DECOR.on(),
                "and so is the jar's models failing to read when the props asked first"));
    }

    /** A fault thrown out of Models' method of that name, asked by that class. */
    private static RuntimeException asked(String method, String by) {
        RuntimeException thrown = new IllegalStateException("the jar's models have none called counters_x");
        thrown.setStackTrace(new StackTraceElement[] {new StackTraceElement(PACKAGE + "Models", "all", null, 1),
                new StackTraceElement(PACKAGE + "Models", method, null, 1), new StackTraceElement(PACKAGE + by, "add", null, 1),
                new StackTraceElement(PACKAGE + "FurnishingBuilder", "addFurniture", null, 1),
                new StackTraceElement("zombie.gameStates.IngameState", "renderFrameInternal", null, 1232)});
        return thrown;
    }

    private static void aFaultNoPartOwnsSwitchesNothingOff() {
        mending(() -> {
            int before = Faults.switchedOff();
            check(Faults.switchOff(thrownIn("WorldQuery", "FirstPerson", "Hooks")) == null, "the view's own code is no part's");
            check(Faults.switchOff(thrownIn("java.util.ArrayList", "FirstPerson")) == null, "nor the platform's under it");
            check(Faults.switchOff(thrownIn("WorldScene", "HitDirection")) == null && Part.HIT_DIRECTION.on(), "nor a part's that called it");
            check(Faults.switchedOff() == before, "and nothing is said");
        });
    }

    private static void onlyFaultsThatKeepComingAreTooMany() {
        Faults.Tally tally = new Faults.Tally();
        long second = 1_000_000_000L;
        boolean any = false;
        for (int i = 0; i < 40; i++) {
            any |= tally.tooMany(i * 2 * second);
        }
        check(!any, "a fault every two seconds is a frame left out now and then");
        Faults.Tally often = new Faults.Tally();
        int fault = 0;
        while (!often.tooMany(fault * second / 10) && fault < 100) {
            fault++;
        }
        check(fault == Faults.Tally.MOST - 1, "ten a second are too many by the tenth: " + (fault + 1));
    }
}
