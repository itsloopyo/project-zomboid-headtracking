package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/**
 * Tests of the flash of a shot: where it is against the weapon and the eye, how much of the
 * picture it takes, how long it lasts and how much light it gives, and that the MuzzleFlash row
 * switched off leaves no flash and no light.
 */
final class MuzzleFlashTests {
    private MuzzleFlashTests() {}

    /** The picture of the rig and the default field of view: 65 degrees from the bottom to the top, 16 by 9. */
    private static final float TAN_HALF = (float) Math.tan(Math.toRadians(FieldOfView.DEFAULT_DEGREES / 2.0)), ASPECT = 16f / 9f;

    /**
     * Muzzles where ViewModel places them, in the view's axes and tiles, and the picture each is seen in: a long gun and a
     * handgun at the hip, pointed at a reticle 10 tiles off, and each with the sights up, its barrel along the aim under
     * the sight line, in the picture narrowed by aiming.
     */
    private record Held(String what, float[] muzzle, float[] pointing, float reach, float tanHalf, boolean aimed) {}

    /** How far each muzzle is from its grip in tiles: the game's pump gun, and its pistol at the size the mod's own hands hold it (ViewModel.HANDGUN_FITS). */
    private static final float LONG_REACH = 0.4461f * 1.5f, HANDGUN_REACH = 0.147f * 1.5f * ViewModel.HANDGUN_FITS;

    private static final Held[] HELD = {
        new Held("a long gun at the hip", new float[] {0.08f, -0.11f, -0.93f}, towards(new float[] {0.08f, -0.11f, -0.93f}, 0f, 0f, -10f), LONG_REACH, TAN_HALF, false),
        new Held("a long gun with the sights up", new float[] {0f, -0.045f, -1.0f}, new float[] {0f, 0f, -1f}, LONG_REACH, TAN_HALF * FieldOfView.AIMED_ZOOM, true),
        new Held("a handgun at the hip", new float[] {0.12f, -0.12f, -0.40f}, towards(new float[] {0.12f, -0.12f, -0.40f}, 0f, 0f, -10f), HANDGUN_REACH, TAN_HALF, false),
        new Held("a handgun with the sights up", new float[] {0f, -0.03f, -0.45f}, new float[] {0f, 0f, -1f}, HANDGUN_REACH, TAN_HALF * FieldOfView.AIMED_ZOOM, true),
    };

    static void run() {
        int mode = Quality.row(0);
        Quality.Picture custom = Quality.custom();
        Quality.set(0, Quality.HIGH);
        try {
            Checks.run("flashOpensFromTheMuzzleAlongTheBarrelAwayFromTheShooter", MuzzleFlashTests::flashOpensFromTheMuzzleAlongTheBarrelAwayFromTheShooter);
            Checks.run("theBloomIsAheadOfTheMuzzleAndNoneOfItBackOverTheBarrel", MuzzleFlashTests::theBloomIsAheadOfTheMuzzleAndNoneOfItBackOverTheBarrel);
            Checks.run("flashIsASmallBloomThatLeavesTheTargetInSight", MuzzleFlashTests::flashIsASmallBloomThatLeavesTheTargetInSight);
            Checks.run("aShotFlashesOnTheFirstFrameDrawnAfterItAndIsGoneByTheThird", MuzzleFlashTests::aShotFlashesOnTheFirstFrameDrawnAfterItAndIsGoneByTheThird);
            Checks.run("flashLightsTheWorldNoMoreThanTheSunDoes", MuzzleFlashTests::flashLightsTheWorldNoMoreThanTheSunDoes);
            Checks.run("rapidFireLightsTheWorldNoMoreThanThreeTimesASecond", MuzzleFlashTests::rapidFireLightsTheWorldNoMoreThanThreeTimesASecond);
            Checks.run("switchedOffAShotHasNoFlashAndNoLight", MuzzleFlashTests::switchedOffAShotHasNoFlashAndNoLight);
            Checks.run("aShotTakenWhileSwitchedOffIsNotFlashedWhenSwitchedOn", MuzzleFlashTests::aShotTakenWhileSwitchedOffIsNotFlashedWhenSwitchedOn);
            Checks.run("theGamesOwnLookHasNoFlashAndNoLight", MuzzleFlashTests::theGamesOwnLookHasNoFlashAndNoLight);
            Checks.run("theRowIsTheSameWherePlayersCanFight", MuzzleFlashTests::theRowIsTheSameWherePlayersCanFight);
        } finally {
            Quality.custom(custom);
            Quality.set(0, mode);
        }
    }

    private static void aShotTakenWhileSwitchedOffIsNotFlashedWhenSwitchedOn() {
        boolean was = MuzzleFlash.on();
        try {
            MuzzleFlash.set(false);
            MuzzleFlash flash = new MuzzleFlash(0);
            long now = System.nanoTime(), frame = 16_666_667L;
            flash.fire();
            check(flash.strength(now) == 0f, "switched off, a shot has no flash");
            MuzzleFlash.set(true);
            check(flash.strength(now + frame) == 0f, "switched on in the next frame with no new shot, the shot taken while off is not flashed");
            flash.fire();
            check(flash.strength(now + 2 * frame) == 1f, "the next shot flashes whole");
        } finally {
            MuzzleFlash.set(was);
        }
    }

    /** Low and Automatic at Stock are the game's own look (Quality.plain), and the game draws no flash. */
    private static void theGamesOwnLookHasNoFlashAndNoLight() {
        ViewCamera.Pose camera = new ViewCamera.Pose();
        ViewCamera.clean(camera, 10f, 20f, 1.6f, 0f, 0f);
        float[] light = new float[7];
        long frame = 16_666_667L;
        for (String mode : new String[] {"Low"}) {
            Quality.set(0, Quality.LOW);
            check(Quality.plain(0), mode + " is the game's own look");
            MuzzleFlash flash = new MuzzleFlash(0);
            flash.place(camera, HELD[0].muzzle, HELD[0].pointing);
            long now = System.nanoTime();
            flash.fire();
            check(flash.strength(now) == 0f, mode + ": a shot has no flash");
            flash.light(light, now);
            check(light[3] == 0f, mode + ": and lights nothing");
            Quality.set(0, Quality.HIGH);
            check(flash.strength(now + frame) == 0f, mode + " left for High with no new shot: the shot taken in " + mode + " is not flashed");
            flash.fire();
            check(flash.strength(now + 2 * frame) == 1f, mode + " left for High: the next shot flashes whole");
        }
    }

    /**
     * The game draws no first person view, so no flash in one: a player without the mod sees what a player with the
     * row off sees. Off is the fair state where players can fight, and nothing there may hold the flash on or off
     * against the row (Online). The flash and its light read no state of the game's at all, the server's included.
     */
    private static void theRowIsTheSameWherePlayersCanFight() throws java.io.IOException {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/cameraunlock/zomboid/MuzzleFlash.java"));
        check(!source.contains("Online") && !source.contains("GameClient") && !source.contains("ServerOptions") && !source.contains("import zombie"),
                "whether a shot flashes is the MuzzleFlash row and the graphics mode alone, on a server where players can fight as alone");
    }

    private static float[] towards(float[] from, float x, float y, float z) {
        float dx = x - from[0], dy = y - from[1], dz = z - from[2];
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        return new float[] {dx / length, dy / length, dz / length};
    }

    private static void flashOpensFromTheMuzzleAlongTheBarrelAwayFromTheShooter() {
        float[] plume = new float[MuzzleFlash.PLUME];
        for (Held held : HELD) {
            MuzzleFlash.plume(held.muzzle, held.pointing, held.reach, plume);
            check(plume[0] == held.muzzle[0] && plume[1] == held.muzzle[1] && plume[2] == held.muzzle[2], held.what + ": the flash starts at the muzzle");
            float dx = plume[3] - plume[0], dy = plume[4] - plume[1], dz = plume[5] - plume[2];
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            check(length > 0.03f, held.what + ": the flash reaches out ahead of the muzzle, " + length + " tiles");
            check(length > 0f && (dx * held.pointing[0] + dy * held.pointing[1] + dz * held.pointing[2]) / length > 0.999f,
                    held.what + ": along the line of the barrel");
            // The whole flash is further from the eye than the muzzle, so the weapon, which is between them, is drawn over it.
            check(-plume[5] > -plume[2], held.what + ": the far end of the flash is further from the eye than the muzzle");
        }
    }

    /** The lines of the bloom's fragment stage that the mirror below follows. */
    private static final String[] BLOOM_LINES = {
        "const int STEPS = 8;",
        "float t = float(i) / float(STEPS - 1);",
        "vec3 off = ray * (dot(ray, q) / rr) - q;",
        "float wide = uWidest * (0.35 + 0.65 * smoothstep(0.0, 0.4, t)) * (1.0 - 0.4 * t) * petals;",
        "float here = (1.0 - t * t) * (exp(-3.0 * far2 / (wide * wide)) + 0.12 * exp(-2.5 * far2 / (uGlow * uGlow)))",
        "* (1.0 - smoothstep(0.7 * uGlow * uGlow, uGlow * uGlow, far2));",
        "vec3 seen = uAlong - ray * (dot(ray, uAlong) / rr);",
        "float aslant = length(seen) / length(uAlong);",
        "vec3 fromMuzzle = ray * (dot(ray, uFrom) / rr) - uFrom;",
        "float forwards = mix(1.0, smoothstep(-0.25 * uGlow, 0.05 * uGlow, dot(fromMuzzle, seen) / max(length(seen), 1e-9)), smoothstep(0.1, 0.3, aslant));",
    };

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /** Where a ray passes a point: the ray's nearest point to it, less the point. */
    private static float[] passes(float[] ray, float[] q) {
        float k = dot(ray, q) / dot(ray, ray);
        return new float[] {ray[0] * k - q[0], ray[1] * k - q[1], ray[2] * k - q[2]};
    }

    /** The bloom's light along a ray from the eye, as the fragment stage has it, with the petals at their least. */
    private static float bloom(float[] plume, float[] ray) {
        float[] from = {plume[0], plume[1], plume[2]}, along = {plume[3] - plume[0], plume[4] - plume[1], plume[5] - plume[2]};
        float glowReach = plume[7], light = 0f;
        for (int i = 0; i < 8; i++) {
            float t = i / 7f;
            float[] off = passes(ray, new float[] {from[0] + along[0] * t, from[1] + along[1] * t, from[2] + along[2] * t});
            float wide = plume[6] * (0.35f + 0.65f * smoothstep(0f, 0.4f, t)) * (1f - 0.4f * t);
            float far2 = dot(off, off);
            float here = (1f - t * t) * ((float) Math.exp(-3f * far2 / (wide * wide)) + 0.12f * (float) Math.exp(-2.5f * far2 / (glowReach * glowReach)))
                    * (1f - smoothstep(0.7f * glowReach * glowReach, glowReach * glowReach, far2));
            light = Math.max(light, here);
        }
        float k = dot(ray, along) / dot(ray, ray);
        float[] seen = {along[0] - ray[0] * k, along[1] - ray[1] * k, along[2] - ray[2] * k};
        float seenLength = (float) Math.sqrt(dot(seen, seen)), aslant = seenLength / (float) Math.sqrt(dot(along, along));
        float back = smoothstep(-0.25f * glowReach, 0.05f * glowReach, dot(passes(ray, from), seen) / Math.max(seenLength, 1e-9f));
        float forwards = 1f + (back - 1f) * smoothstep(0.1f, 0.3f, aslant);
        return light * forwards;
    }

    /**
     * Seen in the game (fixplayer, runs flash1 and flash2): at the hip the shotgun's and the hunting rifle's bloom lay round the
     * muzzle on every side, back over the barrel as far as on the side away from it, a pale fan with straight sides where the
     * quad it is drawn in cut its glow off; with the sights up, where the tongue is seen nearly end on, it was a ring of glow
     * round a dark middle over the target. The fire leaves forwards: behind the muzzle there is none, none reaches the quad's
     * edge, and end on it is brightest in the middle.
     */
    private static void theBloomIsAheadOfTheMuzzleAndNoneOfItBackOverTheBarrel() throws java.io.IOException {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/cameraunlock/zomboid/MuzzleFlash.java"));
        for (String line : BLOOM_LINES) {
            check(source.contains(line), "the bloom's fragment stage has the line the test mirrors: " + line);
        }
        float[] plume = new float[MuzzleFlash.PLUME];
        // Seen across: a long gun turned well aside, its barrel 50 degrees off the line of sight.
        float[] m = {0.1f, -0.1f, -0.6f}, p = {0.766f, 0f, -0.643f};
        MuzzleFlash.plume(m, p, LONG_REACH, plume);
        float[] side = across(m, p);
        float glowReach = plume[7];
        float[] behind = new float[3], ahead = new float[3];
        for (int axis = 0; axis < 3; axis++) {
            behind[axis] = m[axis] - p[axis] * 0.04f + side[axis] * 0.3f * glowReach;
            ahead[axis] = m[axis] + p[axis] * 0.03f + side[axis] * 0.2f * glowReach;
        }
        check(bloom(plume, behind) < 0.002f, "seen across, beside the barrel 4 cm back from the muzzle there is no bloom: " + bloom(plume, behind));
        check(bloom(plume, ahead) > 0.05f, "seen across, just ahead of the muzzle there is: " + bloom(plume, ahead));
        for (Held held : HELD) {
            MuzzleFlash.plume(held.muzzle, held.pointing, held.reach, plume);
            float[] muzzle = held.muzzle, reach = across(muzzle, held.pointing);
            glowReach = plume[7];
            // These are all seen nearly end on: brightest where the muzzle is seen, and dimmer only going out from it.
            float middle = bloom(plume, muzzle), last = middle;
            check(middle > 0.5f, held.what + ": where the muzzle is seen the bloom is bright: " + middle);
            for (float out = 0.25f; out <= 1.0f; out += 0.25f) {
                float[] ray = {muzzle[0] + reach[0] * out * glowReach, muzzle[1] + reach[1] * out * glowReach, muzzle[2] + reach[2] * out * glowReach};
                float here = bloom(plume, ray);
                check(here <= last + 1e-4f, held.what + ": the bloom only dims going out from the muzzle, " + here + " at " + out + " of its reach after " + last);
                last = here;
            }
            for (int end = 0; end < 6; end += 3) {
                float[] ray = new float[3];
                for (int axis = 0; axis < 3; axis++) {
                    ray[axis] = plume[end + axis] + reach[axis] * 1.05f * glowReach;
                }
                check(bloom(plume, ray) < 0.002f, held.what + ": past the glow's reach there is none, so the quad's edge cuts nothing: " + bloom(plume, ray));
            }
        }
    }

    /** A unit way square to the barrel and to the line of sight to the muzzle. */
    private static float[] across(float[] muzzle, float[] pointing) {
        float[] side = {pointing[1] * muzzle[2] - pointing[2] * muzzle[1], pointing[2] * muzzle[0] - pointing[0] * muzzle[2], pointing[0] * muzzle[1] - pointing[1] * muzzle[0]};
        float length = (float) Math.sqrt(dot(side, side));
        return new float[] {side[0] / length, side[1] / length, side[2] / length};
    }

    private static void flashIsASmallBloomThatLeavesTheTargetInSight() {
        float[] plume = new float[MuzzleFlash.PLUME], box = new float[4];
        for (Held held : HELD) {
            MuzzleFlash.plume(held.muzzle, held.pointing, held.reach, plume);
            check(MuzzleFlash.box(plume, held.tanHalf, ASPECT, box), held.what + ": the flash is before the eye");
            // The box is in the picture's own measure, -1 to 1 each way.
            float tall = (box[3] - box[1]) / 2f, wide = (box[2] - box[0]) / 2f;
            check(tall < 0.12f && wide < 0.12f, held.what + ": the flash takes " + tall + " of the picture's height and " + wide + " of its width");
            if (held.aimed) {
                check(box[3] < 0f, held.what + ": the flash stays under the middle of the picture, where the target is (its top at " + box[3] + ")");
            }
        }
    }

    private static void aShotFlashesOnTheFirstFrameDrawnAfterItAndIsGoneByTheThird() {
        MuzzleFlash flash = new MuzzleFlash(0);
        check(flash.strength(System.nanoTime()) == 0f, "no shot, no flash");
        flash.fire();
        // A frame that comes a long while after the shot, as at a low frame rate, still shows it once, whole.
        long first = System.nanoTime() + 100_000_000L, frame = 16_666_667L;
        check(flash.strength(first) == 1f, "the first frame drawn after the shot has the whole flash");
        float second = flash.strength(first + frame);
        check(second > 0f && second < 1f, "the next frame at 60 a second has less of it: " + second);
        check(flash.strength(first + 2 * frame) == 0f, "and the one after that none");
        check(flash.strength(first + 3 * frame) == 0f, "nor any later");
        flash.fire();
        check(flash.strength(first + 4 * frame) == 1f, "the next shot flashes whole again");
    }

    private static void flashLightsTheWorldNoMoreThanTheSunDoes() {
        MuzzleFlash flash = new MuzzleFlash(0);
        ViewCamera.Pose camera = new ViewCamera.Pose();
        ViewCamera.clean(camera, 10f, 20f, 1.6f, 0f, 0f);
        flash.place(camera, HELD[0].muzzle, HELD[0].pointing);
        flash.fire();
        float[] light = new float[7];
        flash.light(light, System.nanoTime());
        check(light[3] > 0f && light[3] <= 2.1f, "at the shot the flash lights what is beside it no more than the sun at its height does: " + light[3]);
        // The light is just ahead of the muzzle, so the weapon's own end is lit from in front.
        float ahead = (light[0] - camera.eyeX) * camera.forwardX + (light[1] - camera.eyeY) * camera.forwardY + (light[2] - camera.eyeUp) * camera.forwardUp;
        check(ahead > -HELD[0].muzzle[2], "the light is ahead of the muzzle: " + ahead + " tiles before the eye");
    }

    /**
     * A flash over a large part of the picture more than three times a second can bring on a seizure. The light of a
     * shot can be that in the dark, so however fast the shots come it lights the world three times a second at most;
     * the bloom is small and shows with every shot.
     */
    private static void rapidFireLightsTheWorldNoMoreThanThreeTimesASecond() {
        MuzzleFlash flash = new MuzzleFlash(0);
        ViewCamera.Pose camera = new ViewCamera.Pose();
        ViewCamera.clean(camera, 10f, 20f, 1.6f, 0f, 0f);
        flash.place(camera, HELD[0].muzzle, HELD[0].pointing);
        float[] light = new float[7];
        long start = System.nanoTime(), frame = 16_666_667L;
        int lit = 0, bloomed = 0;
        // Twenty shots a second for two seconds, a shot every third frame at 60 a second.
        for (int i = 0; i < 120; i++) {
            long now = start + i * frame;
            if (i % 3 == 0) {
                flash.fire();
            }
            flash.light(light, now);
            if (flash.strength(now) == 1f) {
                bloomed++;
                if (light[3] > 0f) {
                    lit++;
                }
            }
        }
        check(bloomed == 40, "every shot shows its bloom: " + bloomed + " of 40");
        check(lit == 6, "two seconds of shots light the world six times, three a second: " + lit);
        flash.light(light, start + 120 * frame + MuzzleFlash.LIT_EVERY);
        flash.fire();
        flash.light(light, start + 121 * frame + MuzzleFlash.LIT_EVERY);
        check(light[3] > 0f, "a shot after a pause lights the world at once");
    }

    private static void switchedOffAShotHasNoFlashAndNoLight() {
        boolean was = MuzzleFlash.on();
        try {
            MuzzleFlash.set(false);
            MuzzleFlash flash = new MuzzleFlash(0);
            ViewCamera.Pose camera = new ViewCamera.Pose();
            ViewCamera.clean(camera, 10f, 20f, 1.6f, 0f, 0f);
            flash.place(camera, HELD[0].muzzle, HELD[0].pointing);
            flash.fire();
            long now = System.nanoTime();
            check(flash.strength(now) == 0f, "switched off, a shot has no flash");
            float[] light = new float[7];
            flash.light(light, now);
            check(light[3] == 0f, "and lights nothing");
            MuzzleFlash.set(true);
            flash.fire();
            check(flash.strength(now) == 1f, "switched on again, the next shot flashes");
        } finally {
            MuzzleFlash.set(was);
        }
    }
}
