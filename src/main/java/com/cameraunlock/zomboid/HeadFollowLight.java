package com.cameraunlock.zomboid;

import java.util.Locale;
import zombie.iso.Vector2;

/**
 * A torch the character carries lights where the head is turned, not where the
 * aim points, and turns further than the view does: eyes that follow a head
 * turn end up past the centre of the screen, so a beam matched to the view
 * lands short of what they are on.
 *
 * The game asks for the beam's direction once, when it fills in the torch it
 * hands its lighting, and that one answer is what is turned here. Nothing of
 * the game's is written, so there is nothing to put back, and its vision cone,
 * aim and facing still read the look direction as it was.
 *
 * The beam lies on the ground, so it has a heading and nothing else. The
 * heading it is given is the one the view would have with the head's pose
 * multiplied, which at a multiplier of 1 is the middle of the picture.
 */
public final class HeadFollowLight {
    static final String PROPERTY = "cameraunlock.zomboid.lightMultiplier";
    /** cameraunlock-core's kDefaultLightMultiplier and kMaxLightMultiplier, which Java cannot name. */
    static final float DEFAULT_MULTIPLIER = 1.5f;
    static final float MAX_MULTIPLIER = 5f;

    private static final ViewCamera.Pose scaled = new ViewCamera.Pose();
    private static float multiplier = DEFAULT_MULTIPLIER;
    /** Radians. Written on the logic thread, read wherever the game updates its lights. */
    private static volatile float turn;

    private HeadFollowLight() {}

    static float multiplier() {
        return multiplier;
    }

    /**
     * Takes a multiplier from 0, which leaves the beam on the aim, to MAX_MULTIPLIER.
     * Anything else is refused and the one in use stands: running at the limit when
     * the setting says more would be a setting that does not do what it says.
     */
    static boolean setMultiplier(String text) {
        float value;
        try {
            value = Float.parseFloat(text.strip());
        } catch (NumberFormatException notANumber) {
            value = Float.NaN;
        }
        if (!(value >= 0f && value <= MAX_MULTIPLIER)) {
            Log.info(PROPERTY + " is \"" + text + "\", which is not a number from 0 to " + MAX_MULTIPLIER
                    + ": the light keeps its multiplier of " + multiplier);
            return false;
        }
        multiplier = value;
        return true;
    }

    /**
     * How far the heading of a view turns when the head's pose, multiplied, is
     * composed onto it the way the camera composes it. Radians, positive to the
     * right. A view pushed past straight up or down faces the other way, and so
     * does its heading.
     */
    static float headingTurn(ViewCamera.Pose clean, float headYaw, float headPitch, float headRoll, boolean worldYaw) {
        scaled.set(clean);
        ViewCamera.turn(scaled, headYaw * multiplier, headPitch * multiplier, headRoll * multiplier, worldYaw);
        return (float) Math.atan2(clean.forwardX * scaled.forwardY - clean.forwardY * scaled.forwardX,
                clean.forwardX * scaled.forwardX + clean.forwardY * scaled.forwardY);
    }

    /** The beam follows this frame's head pose, in radians, over the clean view. */
    static void follow(ViewCamera.Pose clean, float headYaw, float headPitch, float headRoll, boolean worldYaw) {
        turn = headingTurn(clean, headYaw, headPitch, headRoll, worldYaw);
    }

    /** The beam goes back to where the game points it. */
    static void release() {
        turn = 0f;
    }

    static float turn() {
        return turn;
    }

    /** Called where the game reads the look direction a carried torch shines along. */
    public static Vector2 direction(Vector2 look) {
        float by = turn;
        if (by == 0f) {
            return look;
        }
        float cos = (float) Math.cos(by);
        float sin = (float) Math.sin(by);
        return look.set(look.x * cos - look.y * sin, look.x * sin + look.y * cos);
    }

    static String describe() {
        return String.format(Locale.ROOT, "light turned %.2f at %.2fx", Math.toDegrees(turn), multiplier);
    }
}
