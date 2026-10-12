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
        Checks.run("anAutomaticBurstHoldsTheMuzzleAboutAShotHigh", KickTests::anAutomaticBurstHoldsTheMuzzleAboutAShotHigh);
        Checks.run("aShotThrowsTheMuzzleUpAndBack", KickTests::aShotThrowsTheMuzzleUpAndBack);
        Checks.run("withTheSightsUpTheWeaponMovesLessAgainstTheEye", KickTests::withTheSightsUpTheWeaponMovesLessAgainstTheEye);
        Checks.run("aGunWithNoStockKicksAsAtTheHipWithTheSightsUp", KickTests::aGunWithNoStockKicksAsAtTheHipWithTheSightsUp);
        Checks.run("restLeavesTheWeaponWhereItIsHeld", KickTests::restLeavesTheWeaponWhereItIsHeld);
        Checks.run("aKickPausedStandsAndPlaysOnAfter", KickTests::aKickPausedStandsAndPlaysOnAfter);
        Checks.run("backMostIsAsFarAsShotsThrowTheGun", KickTests::backMostIsAsFarAsShotsThrowTheGun);
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

    /**
     * backMost is a gun's single hardest throw back, or more where shots come faster than a
     * kick settles; a gun with no stock is thrown back as far with the sights up as down.
     */
    private static void backMostIsAsFarAsShotsThrowTheGun() {
        for (int delay : new int[] {11, 12, 15, 17, 20, 23, 28, 30, 34}) {
            float strength = Kick.fired(delay, 1f), aimed = Kick.backMost(delay, 1f, 1f), hip = Kick.backMost(delay, 1f, 0f);
            check(aimed >= strength * Kick.WEAPON_BACK_AIMED - 1e-5f && aimed <= strength * Kick.WEAPON_BACK_AIMED * 1.05f,
                    "a recoil delay of " + delay + " throws the gun back " + aimed + " with the sights up, about one shot's " + strength * Kick.WEAPON_BACK_AIMED);
            check(hip > aimed, "and further at the hip, " + hip);
        }
        check(Kick.backMost(2, 1f, 1f) > Kick.fired(2, 1f) * Kick.WEAPON_BACK_AIMED * 1.5f, "shots faster than the kick settles stack");
        float sawnOff = Kick.backMost(30, 1f, Kick.braced("Base.ShotgunSawnoff", 1f));
        check(sawnOff == Kick.backMost(30, 1f, 0f), "a sawn-off is thrown back as far with the sights up as at the hip, " + sawnOff);
    }

    /** Paused, the view model is stepped by frames of no time at all: a kick stands where it is, and after plays out as if the pause had not been. */
    private static void aKickPausedStandsAndPlaysOnAfter() {
        Kick paused = new Kick(), straight = new Kick();
        paused.fire(1f, 0f);
        straight.fire(1f, 0f);
        for (int frame = 0; frame < 3; frame++) {
            paused.step(1f / 60f);
            straight.step(1f / 60f);
        }
        float back = paused.weaponBack.at, muzzle = paused.weaponPitch.at;
        for (int frame = 0; frame < 120; frame++) {
            paused.step(0f);
        }
        check(paused.weaponBack.at == back && paused.weaponPitch.at == muzzle, "paused for two seconds the weapon stands where it was: " + paused.weaponPitch.at + " against " + muzzle);
        check(Math.abs(muzzle) > Kick.WEAPON_PITCH * 0.3f, "and that is part way through the kick, not at rest: " + muzzle);
        for (int frame = 0; frame < 20; frame++) {
            paused.step(1f / 60f);
            straight.step(1f / 60f);
            check(paused.weaponPitch.at == straight.weaponPitch.at && paused.weaponBack.at == straight.weaponBack.at, "after the pause it goes on as a kick never paused, frame " + frame);
        }
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

    /**
     * The assault rifle fires a round every 0.08 s on automatic, faster than one kick settles:
     * each shot strikes the springs once more, and the burst holds the muzzle about as high as
     * one shot throws it, shaking, and never stacks it towards the springs' end stops.
     */
    private static void anAutomaticBurstHoldsTheMuzzleAboutAShotHigh() {
        float hard = Kick.fired(15, 1.6f);
        for (float aim : new float[] {0f, 1f}) {
            Kick one = new Kick();
            one.fire(hard, aim);
            float shot = 0f;
            for (int frame = 0; frame < 60; frame++) {
                one.step(1f / 60f);
                shot = Math.max(shot, one.weaponPitch.at);
            }
            Kick burst = new Kick();
            float most = 0f, held = 0f, next = 0f, time = 0f;
            int shots = 0, counted = 0;
            for (int frame = 0; frame < 150; frame++) {
                burst.step(1f / 60f);
                time += 1f / 60f;
                for (; shots < 30 && time >= next; shots++, next += 0.08f) {
                    burst.fire(hard, aim);
                }
                most = Math.max(most, burst.weaponPitch.at);
                if (time > 0.4f && shots < 30) {
                    held += burst.weaponPitch.at;
                    counted++;
                }
            }
            held /= counted;
            check(most < 1.6f * shot, "the burst throws the muzzle no higher than 1.6 times a shot does, aim " + aim + ": " + most / shot);
            check(held > 0.5f * shot && held < 1.1f * shot, "and holds it about a shot high once it is going: " + held / shot);
        }
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

    private static void aGunWithNoStockKicksAsAtTheHipWithTheSightsUp() {
        check(Kick.braced("Base.ShotgunSawnoff", 1f) == 0f && Kick.braced("Base.DoubleBarrelShotgunSawnoff", 0.6f) == 0f, "a sawn-off is held off the shoulder");
        check(Kick.braced("Base.Shotgun", 1f) == 1f && Kick.braced("Base.HuntingRifle", 0.4f) == 0.4f, "a gun with a stock is braced as far as its sights are up");
        Kick sawnOff = new Kick(), stocked = new Kick();
        sawnOff.fire(Kick.fired(30, 2f), Kick.braced("Base.ShotgunSawnoff", 1f));
        stocked.fire(Kick.fired(30, 2.2f), Kick.braced("Base.Shotgun", 1f));
        float[] flip = furthest(sawnOff.weaponPitch), shouldered = furthest(stocked.weaponPitch);
        check(flip[0] > shouldered[0] * 2f, "with the sights up the sawn-off's muzzle climbs far more than the stocked gun's: " + flip[0] + " against " + shouldered[0]);
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
