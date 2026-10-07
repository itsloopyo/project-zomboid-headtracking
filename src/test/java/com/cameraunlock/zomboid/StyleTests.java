package com.cameraunlock.zomboid;

/** Tests of the Style row: the order its hotkey goes through, and that a style changes nothing but what the last pass makes of the light. */
final class StyleTests {
    private StyleTests() {}

    static void run() {
        Style before = Style.current();
        try {
            f10GoesRoundTheThree();
            vividIsThePictureAsItWas();
            eachStyleAfterVividIsQuieter();
        } finally {
            Style.set(before.ordinal());
        }
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static void f10GoesRoundTheThree() {
        Style.set(Style.VIVID.ordinal());
        check(Style.current() == Style.VIVID, "the row asked for is the style in use");
        check(Style.VIVID.next() == Style.NATURAL, "the hotkey goes from Vivid to Natural");
        check(Style.NATURAL.next() == Style.BLEAK, "from Natural to Bleak");
        check(Style.BLEAK.next() == Style.VIVID, "and from Bleak round to Vivid");
        check(Style.words().equals("Vivid,Natural,Bleak"), "the row's words in CameraUnlock.ini are the styles in their order");
    }

    private static void vividIsThePictureAsItWas() {
        Style v = Style.VIVID;
        check(v.curve == 0.34f && v.saturation == 1.08f && v.vignette == 0.32f && v.bloom == 0.1f && v.flare == 1f,
                "Vivid has the curve, colour, corners, glow and glare the picture had before there were styles");
        check(v.shade[0] == 0.96f && v.shade[1] == 0.99f && v.shade[2] == 1.05f
                && v.sunlit[0] == 1.05f && v.sunlit[1] == 1.01f && v.sunlit[2] == 0.95f, "and its cool shade and warm sun");
    }

    private static void eachStyleAfterVividIsQuieter() {
        Style n = Style.NATURAL, b = Style.BLEAK;
        check(n.saturation == 1f && n.shade[0] == 1f && n.shade[2] == 1f && n.sunlit[0] == 1f && n.sunlit[2] == 1f,
                "Natural leaves the colours as they are lit, with no tint");
        check(n.curve < Style.VIVID.curve && b.curve < n.curve, "the contrast comes down from one to the next");
        check(b.saturation < n.saturation && b.flare == 0f, "and Bleak is washed out, with no glare in the lens");
    }
}
