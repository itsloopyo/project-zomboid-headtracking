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
        Checks.run("flashOpensFromTheMuzzleAlongTheBarrelAwayFromTheShooter", MuzzleFlashTests::flashOpensFromTheMuzzleAlongTheBarrelAwayFromTheShooter);
        Checks.run("flashIsASmallBloomThatLeavesTheTargetInSight", MuzzleFlashTests::flashIsASmallBloomThatLeavesTheTargetInSight);
        Checks.run("aShotFlashesOnTheFirstFrameDrawnAfterItAndIsGoneByTheThird", MuzzleFlashTests::aShotFlashesOnTheFirstFrameDrawnAfterItAndIsGoneByTheThird);
        Checks.run("flashLightsTheWorldNoMoreThanTheSunDoes", MuzzleFlashTests::flashLightsTheWorldNoMoreThanTheSunDoes);
        Checks.run("switchedOffAShotHasNoFlashAndNoLight", MuzzleFlashTests::switchedOffAShotHasNoFlashAndNoLight);
        Checks.run("theRowIsTheSameWherePlayersCanFight", MuzzleFlashTests::theRowIsTheSameWherePlayersCanFight);
    }

    /**
     * The game draws no first person view, so no flash in one: a player without the mod sees what a player with the
     * row off sees. Off is the fair state where players can fight, and nothing there may hold the flash on or off
     * against the row (Online). The flash and its light read no state of the game's at all, the server's included.
     */
    private static void theRowIsTheSameWherePlayersCanFight() throws java.io.IOException {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/cameraunlock/zomboid/MuzzleFlash.java"));
        check(!source.contains("Online") && !source.contains("GameClient") && !source.contains("ServerOptions") && !source.contains("import zombie"),
                "whether a shot flashes is the MuzzleFlash row alone, on a server where players can fight as alone");
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
        MuzzleFlash flash = new MuzzleFlash();
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
        MuzzleFlash flash = new MuzzleFlash();
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

    private static void switchedOffAShotHasNoFlashAndNoLight() {
        boolean was = MuzzleFlash.on();
        try {
            MuzzleFlash.set(false);
            MuzzleFlash flash = new MuzzleFlash();
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
