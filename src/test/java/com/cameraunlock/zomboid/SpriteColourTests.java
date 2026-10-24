package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.ArrayList;
import java.util.Arrays;
import zombie.core.textures.Texture;

/** Tests of the order in which the sprites asked for are read: see SpriteColours.next. */
final class SpriteColourTests {
    private SpriteColourTests() {}

    static void run() {
        Checks.run("aSpriteAskedNearTheEyeIsReadBeforeOnesAskedEarlierFarOff", SpriteColourTests::aSpriteAskedNearTheEyeIsReadBeforeOnesAskedEarlierFarOff);
        Checks.run("spritesAskedAsNearAreReadAsTheyWereAsked", SpriteColourTests::spritesAskedAsNearAreReadAsTheyWereAsked);
        Checks.run("aSpriteAskedFromNoPlaceIsReadFirst", SpriteColourTests::aSpriteAskedFromNoPlaceIsReadFirst);
        Checks.run("aSheetNotOnTheCardYetWaitsBehindEveryOther", SpriteColourTests::aSheetNotOnTheCardYetWaitsBehindEveryOther);
        Checks.run("eachViewsSpritesGoByItsOwnEye", SpriteColourTests::eachViewsSpritesGoByItsOwnEye);
    }

    private static float[] eyes(float... xy) {
        float[] eyes = new float[View.COUNT * 2];
        Arrays.fill(eyes, Float.NaN);
        System.arraycopy(xy, 0, eyes, 0, xy.length);
        return eyes;
    }

    private static SpriteColours.Ask ask(int sheet, int view, float x, float y) {
        return new SpriteColours.Ask(new Texture(sheet, 64, 128), view, x, y);
    }

    /** The clinic's wainscot, asked for as the eye came into the clinic, behind the sheets of the street the eye had left. */
    private static void aSpriteAskedNearTheEyeIsReadBeforeOnesAskedEarlierFarOff() {
        ArrayList<SpriteColours.Ask> waiting = new ArrayList<>();
        for (int sheet = 1; sheet <= 60; sheet++) {
            waiting.add(ask(sheet, 0, 6185f, 5343f));
        }
        waiting.add(ask(1035, 0, 6124f, 5244f));
        int next = SpriteColours.next(waiting, eyes(6124.5f, 5248.5f));
        check(waiting.get(next).texture().getID() == 1035, "the sheet read next is the wainscot's: " + waiting.get(next).texture().getID());
    }

    private static void spritesAskedAsNearAreReadAsTheyWereAsked() {
        ArrayList<SpriteColours.Ask> waiting = new ArrayList<>();
        waiting.add(ask(7, 0, 100f, 100f));
        waiting.add(ask(8, 0, 100f, 100f));
        check(SpriteColours.next(waiting, eyes(0f, 0f)) == 0, "the first asked of two as near");
    }

    private static void aSpriteAskedFromNoPlaceIsReadFirst() {
        ArrayList<SpriteColours.Ask> waiting = new ArrayList<>();
        waiting.add(ask(7, 0, 1f, 1f));
        waiting.add(ask(8, 0, Float.NaN, Float.NaN));
        check(SpriteColours.next(waiting, eyes(0f, 0f)) == 1, "a sprite asked for outside a chunk's build is read first");
        check(SpriteColours.next(waiting, eyes()) == 0, "and so is every sprite while the eye is not known, in the order they were asked");
    }

    private static void aSheetNotOnTheCardYetWaitsBehindEveryOther() {
        ArrayList<SpriteColours.Ask> waiting = new ArrayList<>();
        waiting.add(ask(7, 0, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY));
        waiting.add(ask(8, 0, 900f, 900f));
        check(SpriteColours.next(waiting, eyes(0f, 0f)) == 1, "a sheet put behind is read after one far off");
        waiting.remove(1);
        waiting.add(ask(9, 0, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY));
        check(SpriteColours.next(waiting, eyes(0f, 0f)) == 0, "and of those put behind, the first");
    }

    private static void eachViewsSpritesGoByItsOwnEye() {
        ArrayList<SpriteColours.Ask> waiting = new ArrayList<>();
        waiting.add(ask(7, 0, 50f, 50f));
        waiting.add(ask(8, 1, 500f, 500f));
        check(SpriteColours.next(waiting, eyes(0f, 0f, 501f, 501f)) == 1, "the second player's sprite, by the second player's eye");
    }
}
