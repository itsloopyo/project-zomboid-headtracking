package com.cameraunlock.zomboid;

import fmod.javafmod;
import java.util.Locale;
import zombie.characters.IsoPlayer;

/**
 * Which way the player's ears face.
 *
 * The game hears from where the character stands, facing the way its isometric
 * view looks: north-west on the map, level, whatever the character or the mouse
 * does. A sound is to the left when it is to the left on the screen. That is
 * right for its own view and wrong from the eye, where a zombie behind the
 * right shoulder was heard wherever the map put it.
 *
 * In first person the ears face the way the picture does, the head's turn and
 * tilt in it. While the camera flies between the two views they turn with it,
 * and how far out the game takes the camera to be, which its sounds are mixed
 * by, comes in to nothing with it. Where the ears are is left the game's own:
 * every sound a character makes is placed at its feet, the player's own among
 * them, so ears moved up to the eye or aside with a lean would hear their own
 * steps and shots from below or from one side.
 *
 * Logic thread only: the game sets its listener and its sound parameters there.
 */
public final class Hearing {
    private static final long TRACE_INTERVAL_NANOS = 1_000_000_000L;

    /** How far the camera has come from the game's view, 0 there and 1 at the eye. */
    private static float share;
    static float forwardX, forwardY, forwardUp;
    static float upX, upY, upUp;
    /** How far in the camera was when the game's listener was last set. */
    private static float heardAt;
    private static boolean turned;
    private static long lastTrace;
    private static boolean gapSaid;

    private Hearing() {}

    /** The ears face the game's own way. */
    static void rest() {
        share = 0f;
    }

    /**
     * The ears face the way the picture is drawn.
     *
     * @param view   the camera the frame is drawn from
     * @param tipped radians the camera looks down by for being the game's overhead one, which
     *               ears do not: the game's own face level. Nothing once the camera is at the eye.
     * @param in     how far the camera has come from the game's view
     */
    static void face(ViewCamera.Pose view, float tipped, float in) {
        float c = (float) Math.cos(tipped), s = (float) Math.sin(tipped);
        forwardX = view.forwardX * c + view.upX * s;
        forwardY = view.forwardY * c + view.upY * s;
        forwardUp = view.forwardUp * c + view.upUp * s;
        upX = view.upX * c - view.forwardX * s;
        upY = view.upY * c - view.forwardY * s;
        upUp = view.upUp * c - view.forwardUp * s;
        share = in;
    }

    /**
     * The game sets its listener as it moves the player, which it does not do while paused. The
     * view can still be switched then, and the camera flies: the listener is set from here, so
     * what is still sounding turns with it.
     */
    static void whilePaused(IsoPlayer player) {
        if (share != heardAt && player.soundListener != null) {
            player.soundListener.tick();
        }
    }

    /** Says once why first person is heard as the game's own view is on this build of the game, if it is. */
    static void sayGap() {
        if (!gapSaid) {
            gapSaid = true;
            String gap = Agent.hearingGap();
            if (gap != null) {
                Log.info("first person is heard as the game's own view is: " + gap);
            }
        }
    }

    /**
     * Called in place of the game's own call where it sets its listener, with what the game
     * would have set. The way it faces and its up are the picture's while the camera is off
     * the game's view. Both are in the game's map axes, as the game's own are.
     */
    public static void listener(int index, float x, float y, float z, float goingX, float goingY, float goingZ,
            float gameForwardX, float gameForwardY, float gameForwardZ, float gameUpX, float gameUpY, float gameUpZ) {
        heardAt = share;
        if (share == 0f) {
            if (turned) {
                turned = false;
                Log.info("hearing faces the game's own way again");
            }
            javafmod.FMOD_Studio_Listener3D(index, x, y, z, goingX, goingY, goingZ,
                    gameForwardX, gameForwardY, gameForwardZ, gameUpX, gameUpY, gameUpZ);
            return;
        }
        javafmod.FMOD_Studio_Listener3D(index, x, y, z, goingX, goingY, goingZ, forwardX, forwardY, forwardUp, upX, upY, upUp);
        if (!turned) {
            turned = true;
            Log.info("hearing faces the way the picture does");
        }
        long now = System.nanoTime();
        if (FirstPerson.TRACE && now - lastTrace > TRACE_INTERVAL_NANOS) {
            lastTrace = now;
            Log.info(String.format(Locale.ROOT, "hearing: %.2f of the way in, listener %d at (%.2f, %.2f, %.2f) facing (%.3f, %.3f, %.3f) with up (%.3f, %.3f, %.3f)",
                    share, index, x, y, z, forwardX, forwardY, forwardUp, upX, upY, upUp));
        }
    }

    /**
     * Called with how far out the game takes its camera to be, 0 at its nearest and 1 at its
     * furthest, as it hands that to its sounds. From the eye the camera is no way out at all.
     */
    public static float zoomedOut(float games) {
        return games * (1f - share);
    }
}
