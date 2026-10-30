package com.cameraunlock.zomboid;

/** Tests of the Style row: the order its hotkey goes through, and that a style changes nothing but what the last pass makes of the light. */
final class StyleTests {
    private StyleTests() {}

    static void run() {
        Style before = Style.of(0);
        try {
            Checks.run("f8GoesRoundTheStyles", StyleTests::f8GoesRoundTheStyles);
            Checks.run("vividKeepsTheGradeItHadBeforeThereWereStyles", StyleTests::vividKeepsTheGradeItHadBeforeThereWereStyles);
            Checks.run("eachStyleAfterVividIsQuieter", StyleTests::eachStyleAfterVividIsQuieter);
            Checks.run("everyStyleSetsItsOwnRange", StyleTests::everyStyleSetsItsOwnRange);
            Checks.run("warmAndColdLeanTheirWay", StyleTests::warmAndColdLeanTheirWay);
            Checks.run("aChangeOfStyleFades", StyleTests::aChangeOfStyleFades);
        } finally {
            Style.set(0, before.ordinal());
        }
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void f8GoesRoundTheStyles() {
        Style.set(0, Style.VIVID.ordinal());
        check(Style.of(0) == Style.VIVID, "the row asked for is the style in use");
        check(Style.VIVID.next() == Style.NATURAL, "the hotkey goes from Vivid to Natural");
        check(Style.NATURAL.next() == Style.BLEAK, "from Natural to Bleak");
        check(Style.BLEAK.next() == Style.WARM, "from Bleak to Warm");
        check(Style.WARM.next() == Style.COLD, "from Warm to Cold");
        check(Style.COLD.next() == Style.VIVID, "and from Cold round to Vivid");
        check(Style.words().equals("Vivid,Natural,Bleak,Warm,Cold"), "the row's words in CameraUnlock.ini are the styles in their order");
    }

    private static void vividKeepsTheGradeItHadBeforeThereWereStyles() {
        Style v = Style.VIVID;
        check(v.curve == 0.34f && v.saturation == 1.08f && v.vignette == 0.32f && v.bloom == 0.1f && v.flare == 1f,
                "Vivid has the curve, colour, corners, glow and glare the picture had before there were styles");
        check(v.shade[0] == 0.96f && v.shade[1] == 0.99f && v.shade[2] == 1.05f
                && v.sunlit[0] == 1.05f && v.sunlit[1] == 1.01f && v.sunlit[2] == 0.95f, "and its cool shade and warm sun");
    }

    private static void everyStyleSetsItsOwnRange() {
        for (Style each : Style.values()) {
            check(each.levels > 0f && each.levels <= 1f && each.compress > 0f && each.clarity > 0f && each.sharpen > 0f,
                    each.label + " sets the frame's dark and bright, brings its parts together, sets each pixel apart and sharpens");
        }
        check(Style.BLEAK.vibrance < Style.NATURAL.vibrance && Style.NATURAL.vibrance < Style.VIVID.vibrance,
                "the weak colours are filled least in Bleak and most in Vivid");
    }

    private static void warmAndColdLeanTheirWay() {
        Style w = Style.WARM, c = Style.COLD;
        check(w.shade[0] > w.shade[2] && w.sunlit[0] > w.sunlit[2] && w.sunlit[0] - w.sunlit[2] > Style.VIVID.sunlit[0] - Style.VIVID.sunlit[2],
                "Warm is red over blue in shade and sun, the sun more so than Vivid's");
        check(c.shade[2] > c.shade[0] && c.sunlit[2] > c.sunlit[0] && c.shade[2] - c.shade[0] > Style.VIVID.shade[2] - Style.VIVID.shade[0],
                "Cold is blue over red in shade and sun, the shade more so than Vivid's");
    }

    private static void aChangeOfStyleFades() {
        Style.Fade fade = new Style.Fade();
        check(fade.toward(Style.VIVID, 0L).curve == Style.VIVID.curve, "a view's first style is drawn at once");
        float half = fade.toward(Style.COLD, 1_000_000_000L).sunlit[2];
        check(half == Style.VIVID.sunlit[2], "a new style starts from the old one");
        half = fade.toward(Style.COLD, 1_400_000_000L).sunlit[2];
        check(Math.abs(half - (Style.VIVID.sunlit[2] + Style.COLD.sunlit[2]) / 2f) < 1e-5f, "is halfway at 0.4 s: " + half);
        float back = fade.toward(Style.WARM, 1_400_000_000L).sunlit[2];
        check(back == half, "a style asked for mid fade starts from where the picture is, with no jump");
        check(fade.toward(Style.WARM, 2_200_000_000L).sunlit[2] == Style.WARM.sunlit[2] && fade.saturation == Style.WARM.saturation,
                "and has arrived after 0.8 s");
    }

    private static void eachStyleAfterVividIsQuieter() {
        Style n = Style.NATURAL, b = Style.BLEAK;
        check(n.saturation == 1f && n.shade[0] == 1f && n.shade[2] == 1f && n.sunlit[0] == 1f && n.sunlit[2] == 1f,
                "Natural leaves the colours as they are lit, with no tint");
        check(n.curve < Style.VIVID.curve && b.curve < n.curve, "the contrast comes down from one to the next");
        check(b.saturation < n.saturation && b.flare == 0f, "and Bleak is washed out, with no glare in the lens");
    }
}
