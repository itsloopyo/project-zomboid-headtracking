package com.cameraunlock.zomboid;

import java.util.Locale;
import zombie.characters.IsoPlayer;
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
 * heading it is given is the one the view would have with the head's angles
 * as cameraunlock-core multiplies them for a light (LightMultiplier), which at
 * a multiplier of 1 is the middle of the picture.
 */
public final class HeadFollowLight {
    private static final ViewCamera.Pose scaled = new ViewCamera.Pose();
    /** Radians. Written on the logic thread, read wherever the game updates its lights. */
    private static volatile float turn;

    private HeadFollowLight() {}

    /**
     * How far the heading of a view turns when the light's angles are composed
     * onto it the way the camera composes the head's. Radians, positive to the
     * right. A view pushed past straight up or down faces the other way, and so
     * does its heading.
     */
    static float headingTurn(ViewCamera.Pose clean, float lightYaw, float lightPitch, float lightRoll, boolean worldYaw) {
        scaled.set(clean);
        ViewCamera.turn(scaled, lightYaw, lightPitch, lightRoll, worldYaw);
        return (float) Math.atan2(clean.forwardX * scaled.forwardY - clean.forwardY * scaled.forwardX,
                clean.forwardX * scaled.forwardX + clean.forwardY * scaled.forwardY);
    }

    /** The beam follows this frame's angles for a light, in radians, over the clean view. */
    static void follow(ViewCamera.Pose clean, float lightYaw, float lightPitch, float lightRoll, boolean worldYaw) {
        turn = headingTurn(clean, lightYaw, lightPitch, lightRoll, worldYaw);
    }

    /** The beam goes back to where the game points it. */
    static void release() {
        turn = 0f;
    }

    static float turn() {
        return turn;
    }

    /** Called where the game reads the look direction a torch shines along, with the player who carries it. */
    public static Vector2 direction(Vector2 look, IsoPlayer carrier) {
        // On a server the game fills in every player's torch here. Only the viewer's is turned.
        if (carrier != IsoPlayer.getInstance()) {
            return look;
        }
        // The game's look is the body's, and in first person the body faces the way it walks.
        FirstPerson.sightVector(look);
        float by = turn;
        if (by == 0f) {
            return look;
        }
        float cos = (float) Math.cos(by);
        float sin = (float) Math.sin(by);
        return look.set(look.x * cos - look.y * sin, look.x * sin + look.y * cos);
    }

    static String describe() {
        return String.format(Locale.ROOT, "light turned %.2f at %.2fx", Math.toDegrees(turn), HeadTracking.lightMultiplier());
    }
}
