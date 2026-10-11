package com.cameraunlock.zomboid;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.physics.Bullet;
import zombie.core.physics.RagdollBodyPart;

/**
 * The push a shot gives the body it drops.
 *
 * When a hit puts a character into its ragdoll, the game pushes one body part of it once, on the
 * ragdoll's first update. It pushes level along the line from the shooter's feet to the
 * character's feet, plus a fixed lift, at the part's middle, so nothing a shot does turns a limb:
 * a shot in the knee and one in the chest throw the body the same way. Bullet's impulse takes a
 * place on the part as well, which the game always gives as the middle.
 *
 * A first person shot is known better: the line it flew and where on the part it went in. The
 * push goes along that line, so a shot from above drives the body down and one from below lifts
 * it, and it is given where the shot went in, so it turns what it hits: the head snaps back from
 * a shot in the face, a leg is knocked from under the body. And the legs give way: pushed alone,
 * a ragdoll tips over on its heels as the stiff body it stood as (measured, side on: the knees
 * never bent on a shotgun kill), where a body shot dead folds at the knees and drops.
 *
 * Bullet's axes are the game's x and y with up between them: x, up, y, in tiles, a level being
 * {@link ViewCamera#LEVEL_HEIGHT} tiles up. The picture's units are the same tiles.
 */
public final class HitImpulse {
    private HitImpulse() {}

    /** How long after a shot the push the game gives the body it hit is still that shot's. The ragdoll starts the update after the hit. */
    static final long FRESH_NANOS = 400_000_000L;
    /** The push against the game's for that part and round: measured side on, the game's own laid a body at the feet it stood on whatever the gun. */
    static final float STRENGTH = 1.5f;
    /** The kick each shin is given towards the shooter, and the pull down on the pelvis, in the units of the game's pushes. */
    static final float SHIN_KICK = 15f, PELVIS_DROP = 30f;

    /** A shot: which way it flew, where it went in from the part's middle, and when. */
    record Shot(float forwardX, float forwardY, float forwardUp, float leverX, float leverY, float leverUp, int part, long at) {}

    private static final ConcurrentHashMap<Integer, Shot> shots = new ConcurrentHashMap<>();

    /** Called as a first person shot is found to be on a part of the character with this id. */
    static void aimed(int id, ViewCamera.Pose aim, HitPart hit, int part) {
        long now = System.nanoTime();
        // A shot that dropped nobody is never asked for again.
        shots.values().removeIf(shot -> now - shot.at >= FRESH_NANOS);
        shots.put(id, new Shot(aim.forwardX, aim.forwardY, aim.forwardUp,
                hit.hitX - hit.middleX, hit.hitY - hit.middleY, hit.hitUp - hit.middleUp, part, now));
    }

    /**
     * Called in place of the game's Bullet.applyImpulse from its ragdoll hit reaction. The buffer
     * is the game's: the push x, up, y, then the place on the part x, up, y.
     */
    public static void apply(int id, int part, float[] buffer) {
        Shot shot = shots.remove(id);
        boolean ours = shot != null && shot.part == part && System.nanoTime() - shot.at < FRESH_NANOS;
        if (ours) {
            along(shot, buffer);
        }
        if (FirstPerson.TRACE) {
            Log.info(String.format(Locale.ROOT, "ragdoll push on %d %s: %s, (%.2f, %.2f, %.2f) at (%.3f, %.3f, %.3f)", id,
                    RagdollBodyPart.values()[part].name(), ours ? "along the shot" : "the game's own",
                    buffer[0], buffer[1], buffer[2], buffer[3], buffer[4], buffer[5]));
        }
        Bullet.applyImpulse(id, part, buffer);
        if (ours) {
            giveWay(id, shot);
        }
    }

    /** Turns the game's push into one along the shot, {@link #STRENGTH} times as strong, at the place the shot went in. */
    static void along(Shot shot, float[] buffer) {
        float strength = STRENGTH * (float) Math.sqrt(buffer[0] * buffer[0] + buffer[1] * buffer[1] + buffer[2] * buffer[2]);
        buffer[0] = shot.forwardX * strength;
        buffer[1] = shot.forwardUp * strength;
        buffer[2] = shot.forwardY * strength;
        buffer[3] = shot.leverX;
        buffer[4] = shot.leverUp;
        buffer[5] = shot.leverY;
    }

    /** The shins kicked towards the shooter and the pelvis pulled down, so the knees fold under the body. */
    private static void giveWay(int id, Shot shot) {
        float[] kick = shinKick(shot);
        if (kick != null) {
            Bullet.applyImpulse(id, RagdollBodyPart.BODYPART_LEFT_LOWER_LEG.ordinal(), kick);
            Bullet.applyImpulse(id, RagdollBodyPart.BODYPART_RIGHT_LOWER_LEG.ordinal(), kick.clone());
        }
        Bullet.applyImpulse(id, RagdollBodyPart.BODYPART_PELVIS.ordinal(), new float[] {0f, -PELVIS_DROP, 0f, 0f, 0f, 0f});
    }

    /** The push on each shin, level and towards the shooter, in Bullet's axes; none for a shot straight down or up. */
    static float[] shinKick(Shot shot) {
        float level = (float) Math.hypot(shot.forwardX, shot.forwardY);
        if (level < 1e-3f) {
            return null;
        }
        return new float[] {-shot.forwardX / level * SHIN_KICK, 0f, -shot.forwardY / level * SHIN_KICK, 0f, 0f, 0f};
    }
}
