package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** Tests of how a shot throws the weapon and how it comes back. */
final class KickTests {
    private KickTests() {}

    static void run() {
        Checks.run("aStruckSpringGoesAsFarAsAskedAndTurnsBackSoon", KickTests::aStruckSpringGoesAsFarAsAskedAndTurnsBackSoon);
        Checks.run("aSpringMovesTheSameHoweverTheTimeIsCut", KickTests::aSpringMovesTheSameHoweverTheTimeIsCut);
        Checks.run("aKickHasSettledWithinHalfASecond", KickTests::aKickHasSettledWithinHalfASecond);
        Checks.run("aPumpGunKicksHarderThanARifleAndARifleThanAPistol", KickTests::aPumpGunKicksHarderThanARifleAndARifleThanAPistol);
        Checks.run("shotsFasterThanTheKickSettlesDoNotStackItWithoutEnd", KickTests::shotsFasterThanTheKickSettlesDoNotStackItWithoutEnd);
        Checks.run("aShotThrowsTheMuzzleUpAndBack", KickTests::aShotThrowsTheMuzzleUpAndBack);
        Checks.run("withTheSightsUpTheWeaponMovesLessAgainstTheEye", KickTests::withTheSightsUpTheWeaponMovesLessAgainstTheEye);
        Checks.run("restLeavesTheWeaponWhereItIsHeld", KickTests::restLeavesTheWeaponWhereItIsHeld);
    }

    /** The furthest from rest a spring goes in its first second, frames of 1/240 s, and when. */
    private static float[] furthest(Kick.Spring spring) {
        float most = 0f, when = 0f;
        for (int frame = 1; frame <= 240; frame++) {
            spring.step(1f / 240f);
            if (Math.abs(spring.at) > Math.abs(most)) {
                most = spring.at;
                when = frame / 240f;
            }
        }
        return new float[] {most, when};
    }

    private static void aStruckSpringGoesAsFarAsAskedAndTurnsBackSoon() {
        Kick kick = new Kick();
        kick.weaponPitch.strike(0.1f);
        float[] peak = furthest(kick.weaponPitch);
        check(Math.abs(peak[0] - 0.1f) < 0.001f, "it goes as far as asked: " + peak[0]);
        check(peak[1] > 0.02f && peak[1] < 0.07f, "and is there within a few hundredths of a second: " + peak[1]);
    }

    private static void aSpringMovesTheSameHoweverTheTimeIsCut() {
        Kick.Spring at30 = new Kick().weaponBack, at144 = new Kick().weaponBack;
        at30.strike(0.05f);
        at144.strike(0.05f);
        for (int frame = 0; frame < 3; frame++) {
            at30.step(1f / 30f);
        }
        for (int frame = 0; frame < 14; frame++) {
            at144.step(0.1f / 14f);
        }
        check(Math.abs(at30.at - at144.at) < 1e-5f && Math.abs(at30.speed - at144.speed) < 1e-3f,
                "a tenth of a second in three frames or fourteen ends at the same place: " + at30.at + " against " + at144.at);
    }

    private static void aKickHasSettledWithinHalfASecond() {
        Kick kick = new Kick();
        kick.fire(1f, 0f);
        for (int frame = 0; frame < 30; frame++) {
            kick.step(1f / 60f);
        }
        check(Math.abs(kick.weaponPitch.at) < Kick.WEAPON_PITCH * 0.05f, "the muzzle is back within a twentieth: " + kick.weaponPitch.at);
        check(Math.abs(kick.weaponBack.at) < Kick.WEAPON_BACK * 0.05f, "the weapon is back where it was held: " + kick.weaponBack.at);
    }

    /** The game's recoil delays: Shotgun 30, HuntingRifle 23, AssaultRifle 15, Pistol 12, and the cap gun's 11 with no harm. */
    private static void aPumpGunKicksHarderThanARifleAndARifleThanAPistol() {
        float pump = Kick.fired(30, 2.2f), rifle = Kick.fired(23, 2f), carbine = Kick.fired(15, 1.6f), pistol = Kick.fired(12, 1f);
        check(Math.abs(pump - 1f) < 1e-6f, "a pump gun is the kick's own measure: " + pump);
        check(pump > rifle && rifle > carbine && carbine > pistol, "harder as the game's delay is longer: " + pump + " " + rifle + " " + carbine + " " + pistol);
        check(pistol > 0.4f, "a pistol still kicks enough to feel: " + pistol);
        check(Kick.fired(11, 0f) == Kick.TOY, "a cap gun only twitches");
    }

    private static void shotsFasterThanTheKickSettlesDoNotStackItWithoutEnd() {
        Kick kick = new Kick();
        float most = 0f;
        for (int frame = 0; frame < 120; frame++) {
            kick.fire(1.2f, 0f);
            kick.step(1f / 60f);
            most = Math.max(most, Math.abs(kick.weaponBack.at));
        }
        check(most <= Kick.WEAPON_BACK * 2f + 1e-6f, "a shot every frame for two seconds keeps the weapon within twice a shot's throw: " + most);
    }

    private static void aShotThrowsTheMuzzleUpAndBack() {
        Kick kick = new Kick();
        kick.fire(1f, 0f);
        for (int frame = 0; frame < 3; frame++) {
            kick.step(1f / 60f);
        }
        check(kick.weaponPitch.at > 0f && kick.weaponBack.at > 0f && kick.weaponUp.at > 0f, "the muzzle goes up and the weapon back and up");
    }

    private static void withTheSightsUpTheWeaponMovesLessAgainstTheEye() {
        Kick hip = new Kick(), aimed = new Kick();
        hip.fire(1f, 0f);
        aimed.fire(1f, 1f);
        float[] atHip = furthest(hip.weaponPitch), sighted = furthest(aimed.weaponPitch);
        check(sighted[0] < atHip[0] * 0.5f, "the muzzle climbs less against the eye with the sights up: " + sighted[0] + " against " + atHip[0]);
    }

    private static void restLeavesTheWeaponWhereItIsHeld() {
        Kick kick = new Kick();
        kick.fire(1f, 0.5f);
        kick.step(0.02f);
        kick.rest();
        check(kick.weaponPitch.at == 0f && kick.weaponBack.at == 0f && kick.weaponUp.at == 0f && kick.weaponRoll.at == 0f && kick.weaponYaw.at == 0f,
                "nothing is left thrown");
    }
}
