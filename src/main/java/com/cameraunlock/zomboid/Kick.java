package com.cameraunlock.zomboid;

import java.util.Set;
import java.util.SplittableRandom;

/**
 * How a shot throws the weapon, and how it comes back: springs that a shot strikes. A struck
 * spring leaps from rest, stops short within a few hundredths of a second and swings back a
 * little past rest before it settles.
 *
 * It moves the weapon against the eye: muzzle up, back into the shoulder, over to one side.
 * Nothing the game simulates sees it: the shot leaves along the aim.
 *
 * How hard a firearm kicks is the game's own figure for it, the delay its recoil puts between
 * shots: 30 for a pump gun, 12 for a 9 mm pistol. A long gun with no stock is not braced
 * against the shoulder with the sights up, and kicks in the hands as it does at the hip.
 *
 * Logic thread.
 */
final class Kick {
    /** A spring of one degree of freedom. */
    static final class Spring {
        /** How fast it would swing undamped, radians a second, and its damping against what would stop it dead. */
        private final float rate, damping;
        /** The speed a strike gives it for each unit of the furthest it then goes. */
        private final float perPeak;
        /** It goes no further from rest than this, however fast strikes come. */
        private final float most;
        float at, speed;

        Spring(float rate, float damping, float most) {
            this.rate = rate;
            this.damping = damping;
            this.most = most;
            float decay = rate * damping, swing = rate * (float) Math.sqrt(1f - damping * damping);
            float peaksAt = (float) Math.atan2(swing, decay) / swing;
            perPeak = swing / ((float) Math.exp(-decay * peaksAt) * (float) Math.sin(swing * peaksAt));
        }

        /** Struck so that from rest it would go as far as `peak` before it turned back. */
        void strike(float peak) {
            speed += peak * perPeak;
        }

        /** Moves it on by a frame, exactly, whatever the frame's length. */
        void step(float seconds) {
            float decay = rate * damping, swing = rate * (float) Math.sqrt(1f - damping * damping);
            float fade = (float) Math.exp(-decay * seconds);
            float c = (float) Math.cos(swing * seconds), s = (float) Math.sin(swing * seconds);
            float x = at, v = speed;
            at = fade * (x * c + (v + decay * x) / swing * s);
            speed = fade * (v * c - (decay * v + rate * rate * x) / swing * s);
            if (Math.abs(at) > most) {
                at = Math.copySign(most, at);
                speed = 0f;
            }
        }

        void rest() {
            at = 0f;
            speed = 0f;
        }
    }

    /** How far a pump gun's shot throws the weapon at the hip and with the sights up: radians, and tiles back and up. */
    static final float WEAPON_PITCH = (float) Math.toRadians(14), WEAPON_PITCH_AIMED = (float) Math.toRadians(5);
    static final float WEAPON_BACK = 0.08f, WEAPON_BACK_AIMED = 0.02f;
    static final float WEAPON_UP = 0.02f, WEAPON_UP_AIMED = 0.006f;
    static final float WEAPON_ROLL = (float) Math.toRadians(4), WEAPON_ROLL_AIMED = (float) Math.toRadians(2);
    static final float WEAPON_YAW = (float) Math.toRadians(2), WEAPON_YAW_AIMED = (float) Math.toRadians(0.8);

    /** A pump gun's recoil delay: the kick above is this one's. */
    static final float PUMP_GUN_DELAY = 30f;
    /** A shot from a firearm that does no harm, a cap gun, only twitches it. */
    static final float TOY = 0.15f;

    /** The game's long guns with no stock to put to the shoulder: its sawn-off shotguns. */
    private static final Set<String> STOCKLESS = Set.of("Base.ShotgunSawnoff", "Base.DoubleBarrelShotgunSawnoff");

    /** Shots faster than the kick settles do not stack the weapon past this many of a hardest shot's throw. */
    private static final float MOST = 1.6f * 1.25f;
    /** What backMost has worked out, by recoil delay, harm and how far up the sights are. */
    private static final java.util.Map<Long, Float> BACK_MOST = new java.util.HashMap<>();

    /**
     * The furthest a gun is thrown back toward the eye by shots fired with it as fast as they
     * come, in tiles: a shot every `recoilDelay` sixtieths of a second (the game takes 0.625 times
     * GameTime's multiplier off the delay each update). Worked out by firing them, once for each
     * gun. Logic thread.
     *
     * @param aim how far up the sights are, as braced gives it: nought for a gun with no stock
     */
    static float backMost(int recoilDelay, float maxDamage, float aim) {
        long key = (long) recoilDelay << 33 | (maxDamage > 0f ? 1L << 32 : 0L) | Float.floatToIntBits(aim) & 0xFFFFFFFFL;
        return BACK_MOST.computeIfAbsent(key, unused -> {
            Kick kick = new Kick();
            float every = Math.max(recoilDelay, 1) / 60f, since = every, strength = fired(recoilDelay, maxDamage), most = 0f;
            for (int step = 0; step < 2400; step++) {
                if (since >= every) {
                    kick.fire(strength, aim);
                    since -= every;
                }
                kick.step(1f / 240f);
                since += 1f / 240f;
                most = Math.max(most, kick.weaponBack.at);
            }
            return most;
        });
    }

    final Spring weaponPitch = new Spring(24f, 0.42f, WEAPON_PITCH * MOST);
    final Spring weaponBack = new Spring(32f, 0.55f, WEAPON_BACK * MOST);
    final Spring weaponUp = new Spring(24f, 0.42f, WEAPON_UP * MOST);
    final Spring weaponRoll = new Spring(20f, 0.5f, WEAPON_ROLL * MOST);
    final Spring weaponYaw = new Spring(20f, 0.5f, WEAPON_YAW * MOST);
    private final Spring[] springs = {weaponPitch, weaponBack, weaponUp, weaponRoll, weaponYaw};

    /** Which way each shot throws the weapon over is not the same twice running. */
    private final SplittableRandom side = new SplittableRandom(0x6b69636bL);

    /** How hard a firearm kicks against a pump gun, from the game's recoil delay for it and the most harm it does. */
    static float fired(int recoilDelay, float maxDamage) {
        if (maxDamage <= 0f) {
            return TOY;
        }
        return (float) Math.pow(Math.clamp(recoilDelay / PUMP_GUN_DELAY, 0.2f, 1.25f), 0.75);
    }

    /**
     * How far up the sights are as far as a shot's kick goes, for a firearm of the game's type: a
     * gun with no stock is held off the shoulder and kicks as it does at the hip, its muzzle
     * thrown up and back in the hands.
     */
    static float braced(String fullType, float aim) {
        return STOCKLESS.contains(fullType) ? 0f : aim;
    }

    /**
     * A shot.
     *
     * @param strength how hard the weapon kicks: see fired
     * @param aim      0 at the hip, 1 with the sights up
     */
    void fire(float strength, float aim) {
        float over = side.nextBoolean() ? 1f : -1f;
        float rollBy = over * (0.6f + 0.4f * (float) side.nextDouble());
        float yawBy = (float) side.nextDouble(-1.0, 1.0);
        weaponPitch.strike(strength * lerp(WEAPON_PITCH, WEAPON_PITCH_AIMED, aim));
        weaponBack.strike(strength * lerp(WEAPON_BACK, WEAPON_BACK_AIMED, aim));
        weaponUp.strike(strength * lerp(WEAPON_UP, WEAPON_UP_AIMED, aim));
        weaponRoll.strike(strength * rollBy * lerp(WEAPON_ROLL, WEAPON_ROLL_AIMED, aim));
        weaponYaw.strike(strength * yawBy * lerp(WEAPON_YAW, WEAPON_YAW_AIMED, aim));
    }

    void step(float seconds) {
        for (Spring spring : springs) {
            spring.step(seconds);
        }
    }

    void rest() {
        for (Spring spring : springs) {
            spring.rest();
        }
    }

    private static float lerp(float from, float to, float by) {
        return from + (to - from) * by;
    }
}
