package com.cameraunlock.zomboid;

/** The count every test class keeps together: a failed check is written and the run goes on to the next, and one tally is printed last. */
final class Checks {
    private Checks() {}

    /** A test, which may throw anything. */
    interface Test {
        void run() throws Throwable;
    }

    /** A loop that only a failed check used to end fails without end: past this many the run is given up, with its tally. */
    private static final int MOST_FAILURES = 1000;
    private static int checks, failures;

    static void check(boolean held, String what) {
        checks++;
        if (!held) {
            failures++;
            System.out.println("  [FAIL] " + what);
            if (failures >= MOST_FAILURES) {
                System.out.println("  given up after " + failures + " failures: the tests after this one were not run");
                tally();
            }
        }
    }

    /** Runs one test. What it throws is a failure of its own, and ends that test alone. */
    static void run(String name, Test test) {
        int before = failures;
        try {
            test.run();
        } catch (Throwable thrown) {
            checks++;
            failures++;
            System.out.println("  [FAIL] " + name + " threw:");
            thrown.printStackTrace(System.out);
        }
        if (failures != before) {
            System.out.println("  in " + name);
        }
    }

    /** Prints the one tally and ends the process, with 1 if any check failed. */
    static void tally() {
        System.out.println(failures == 0 ? "tests: all " + checks + " checks passed" : "tests: " + failures + " of " + checks + " checks FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
