package com.cameraunlock.zomboid;

/**
 * The third person camera: where it stands behind the character, how far along the way out
 * from the eye it is, and the head's turn and lean carried onto it.
 *
 * Everything the player does in third person is first person's: the mouse turns the view the
 * aim, the movement keys and the reticle go by, from the character's eye. The camera stands
 * on a boom from a pivot over the character's head, out along the way the view looks, so the
 * character stays near the middle of the picture whichever way the mouse turns it, and comes
 * in over the right shoulder while the sights are up. The boom is shortened at once where a
 * wall would come between the pivot and the camera, and let out again slowly, as a lean is.
 * Where the walls leave the boom too short to see past the character's head, in a small room
 * or a passage, the camera comes in over the shoulder as it does for the sights.
 * The head turns the camera where it stands, as it turns the eye in first person, so a turn
 * of the head moves the picture as far in either view, and the lean moves the camera.
 *
 * Logic thread only.
 */
final class ThirdPerson {
    /** On foot: how far behind the pivot the camera stands, how far over it and how far to its right, in tiles. */
    static final float BEHIND = 2.4f, ABOVE = 0.3f, RIGHT = 0f;
    /** With the sights up: the same, in over the right shoulder. */
    static final float AIM_BEHIND = 1.1f, AIM_ABOVE = 0.12f, AIM_RIGHT = 0.5f;
    /**
     * The share of the boom on foot the walls leave the camera, at and under which it is all the
     * way in over the shoulder, and from which it starts to come in: tuned, not measured.
     */
    static final float CRAMPED_FULL = 0.35f, CRAMPED_FROM = 0.6f;
    /**
     * The time constants the camera goes in over the shoulder in a cramped place in, and back out
     * in: out slowly, as the boom is let out, or a doorway passed behind would swing it out and
     * back in.
     */
    static final float CRAMP_SECONDS = 0.25f, UNCRAMP_SECONDS = 1f;
    /** In a vehicle: how far behind its back end the camera stands, and how far over its roof the pivot is. */
    static final float VEHICLE_BEHIND = 3.6f, VEHICLE_ABOVE = 0.9f;
    /**
     * How far the camera is kept off a wall, a ceiling or the ground, in tiles: well over the
     * picture's near plane, so no wall comes inside it however the camera is turned.
     */
    static final float STANDOFF = 0.22f;
    /** How long the camera takes to go out from the eye to its place behind, or back in. */
    static final float SWING_SECONDS = 0.35f;
    /** How long it takes to come in over the shoulder as the sights go up, or out again. */
    static final float AIM_SECONDS = 0.15f;
    /** The time constant the boom is let out again in after a wall shortened it: as core's lean clamp releases. */
    static final float RELEASE_SECONDS = 0.2f;
    /** How much further back the camera stands at a sprint, in tiles, and how long it takes to go there or come back. */
    static final float SPRINT_BEHIND = 0.4f, SPRINT_SECONDS = 0.5f;
    /**
     * The time constants the pivot follows the character in, across the ground and up: a camera
     * held rigidly to a running body shook with every stride, and one that trails a little reads
     * as carried.
     */
    static final float FOLLOW_SECONDS = 0.06f, FOLLOW_UP_SECONDS = 0.12f;
    /** The furthest the pivot trails the character across the ground, in tiles, and how far the character goes in one frame to be put the pivot on at once: a teleport. */
    static final float MOST_TRAIL = 0.5f, JUMPS_PAST = 3f;
    /**
     * How long the camera takes to go from its place behind the character to its place behind a
     * vehicle they sit down in, or back as they get out, and the time constant the pivot follows in
     * meanwhile: the two places are several tiles apart, and anything quicker read as a cut.
     */
    static final float SEAT_SECONDS = 0.6f, SEAT_FOLLOW_SECONDS = 0.2f;
    /** How far up the mouse turns the view from behind, in radians: further, the camera goes under the character's feet and looks up through them. */
    static final float PITCH_UP = (float) Math.toRadians(55);
    /** In a moving vehicle, how long after the last look the camera swings back behind it, and the time constant it swings in. */
    static final float RECENTRE_AFTER_SECONDS = 1f, RECENTRE_SECONDS = 0.45f;
    /** The speed a vehicle has to go at, in km/h, for the camera to swing back behind it. */
    static final float RECENTRE_KMH = 4f;
    /** How far the camera behind a vehicle looks down once it has swung back, in radians. */
    static final float VEHICLE_PITCH = -0.14f;

    /** 0 at the eye, 1 behind, and even in time between. */
    private float out;
    /** 0 on foot at the hip, 1 with the sights up, eased. */
    private float aimed;
    /** 0 where the walls leave the boom on foot its whole length, 1 where they leave too little to see past the head, eased; and whether it has been measured since the camera was last placed. */
    private float cramped;
    private boolean crampKnown;
    /** 0 at a walk or a run, 1 at a sprint, eased. */
    private float sprinted;
    /** Where the pivot is, trailing the character, and whether it has been put anywhere since the camera was last at the eye. */
    private double followX, followY, followUp;
    private boolean followed;
    /** Where the pivot was last asked to follow: how far that moves in a frame tells a teleport from a run. */
    private double wantedX, wantedY, wantedUp;
    /** 0 as the character sits down or gets up, 1 once the camera has reached its new place, even in time; and how far back and up it stood before. */
    private float seat = 1f, seatFromBehind, seatFromAbove, seatFromRight;
    /** Whether the camera has been placed since it was last at the eye or cut, over a seat or not, and how far back, up and right it last stood. */
    private boolean seatKnown, seated;
    private float lastBehind, lastAbove, lastRight;
    /** How far out along the boom the walls let the camera go, as last let out: for the clean camera, and for the one with the head's lean in it. */
    private final Boom clean = new Boom(), tracked = new Boom();

    /**
     * How far out along one camera's boom the walls let it go, in tiles. Kept as a distance and
     * not a share of the boom: the boom grows and shrinks as the sights come up and go down and
     * as the character gets in and out of a vehicle, and a share kept across that moved the
     * camera nearer or further from a wall that had not moved.
     */
    private static final class Boom {
        float room, length;
        boolean placed;

        /**
         * Shortened at once, let out slowly: a camera that sprang back the moment a wall passed
         * jumped as the character walked past a door frame. A boom that grows takes its room with
         * it, so in the open the camera keeps up with a turn of the view or the sights going down,
         * and only what a wall had held back is let out slowly.
         */
        float take(float allowed, float boom, float seconds) {
            if (!placed) {
                room = allowed;
            } else {
                room += Math.max(boom - length, 0f);
                room = allowed < room ? allowed : room + (allowed - room) * (1f - (float) Math.exp(-seconds / RELEASE_SECONDS));
            }
            length = boom;
            placed = true;
            return room;
        }

        void forget() {
            placed = false;
        }
    }

    /**
     * How a wall stops the camera: how far it can go from (x, y) along a unit vector before it is
     * within the margin of one, capped at reach. The boom is up over the floor of the level at
     * (x, y) and rises by rise for each tile along the ground, so a doorway it crosses over the
     * door's head stops it as the wall does.
     */
    interface Walls {
        float room(float x, float y, float directionX, float directionY, float reach, float margin, float up, float rise);
    }

    /**
     * Moves the camera towards its place behind the character or in to the eye, and over the
     * shoulder or off it, over a frame of the given length.
     */
    void advance(boolean behind, boolean aiming, boolean sprinting, float seconds) {
        out = behind ? Math.min(out + seconds / SWING_SECONDS, 1f) : Math.max(out - seconds / SWING_SECONDS, 0f);
        aimed = aiming ? Math.min(aimed + seconds / AIM_SECONDS, 1f) : Math.max(aimed - seconds / AIM_SECONDS, 0f);
        sprinted = sprinting ? Math.min(sprinted + seconds / SPRINT_SECONDS, 1f) : Math.max(sprinted - seconds / SPRINT_SECONDS, 0f);
        seat = Math.min(seat + seconds / SEAT_SECONDS, 1f);
    }

    /**
     * Says whether the character sits this frame, before the pivot follows: on the frame they sit
     * down or get up the camera sets off from how far back, up and right it last stood to its new place,
     * over SEAT_SECONDS.
     */
    void seat(boolean sits) {
        if (seatKnown && sits != seated) {
            seat = 0f;
            seatFromBehind = lastBehind;
            seatFromAbove = lastAbove;
            seatFromRight = lastRight;
        }
        seatKnown = true;
        seated = sits;
    }

    /** How far back the camera stands, on its way from where it stood before a seat change to where it is wanted. */
    float seatedBehind(float wanted) {
        lastBehind = seatFromBehind + (wanted - seatFromBehind) * smooth(seat);
        return lastBehind;
    }

    /** How far up it stands, on the same way. */
    float seatedAbove(float wanted) {
        lastAbove = seatFromAbove + (wanted - seatFromAbove) * smooth(seat);
        return lastAbove;
    }

    /** How far right it stands, on the same way. */
    float seatedRight(float wanted) {
        lastRight = seatFromRight + (wanted - seatFromRight) * smooth(seat);
        return lastRight;
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    /** Puts the camera at one end with no way between. */
    void cut(boolean behind) {
        out = behind ? 1f : 0f;
        unplaced();
    }

    /** How far out of the eye the camera is: 0 at it, 1 at its place, setting off and arriving slowly. */
    float share() {
        return out * out * (3f - 2f * out);
    }

    /**
     * Measures how far the walls let the camera out on foot at the hip, behind the pivot along
     * the way `look` looks, whatever boom it has now: over the shoulder its own boom is short,
     * and measured by that it would never go back out. Eases the cramped share towards what that
     * room says over a frame, or puts it there at once after a cut.
     */
    void cramp(ViewCamera.Pose look, double pivotX, double pivotY, double pivotUp, Walls walls, float levelFloor, float ceiling, float seconds) {
        float awayX = -look.forwardX * BEHIND + look.rightX * RIGHT, awayY = -look.forwardY * BEHIND + look.rightY * RIGHT;
        float awayUp = -look.forwardUp * BEHIND + look.rightUp * RIGHT + ABOVE;
        float flat = (float) Math.hypot(awayX, awayY);
        float left = 1f;
        if (flat > 1e-4f) {
            float rise = (float) (Math.min(pivotUp + awayUp, ceiling - STANDOFF) - pivotUp) / flat;
            left = Math.min(walls.room((float) pivotX, (float) pivotY, awayX / flat, awayY / flat, flat, STANDOFF, (float) pivotUp - levelFloor, rise), flat) / flat;
        }
        float wanted = 1f - smooth(Math.clamp((left - CRAMPED_FULL) / (CRAMPED_FROM - CRAMPED_FULL), 0f, 1f));
        float constant = wanted > cramped ? CRAMP_SECONDS : UNCRAMP_SECONDS;
        cramped = crampKnown ? cramped + (wanted - cramped) * (1f - (float) Math.exp(-seconds / constant)) : wanted;
        crampKnown = true;
    }

    /** The share of the way a cramped place has brought the camera in over the shoulder, setting off slowly. */
    float crampShare() {
        return smooth(cramped);
    }

    /** The share of the way a sprint has taken the camera back, setting off and arriving slowly. */
    float sprintShare() {
        return sprinted * sprinted * (3f - 2f * sprinted);
    }

    /**
     * Moves the pivot towards the point it follows over a frame, trailing it by no more than
     * MOST_TRAIL across the ground, and puts it there at once the first time or after a jump.
     */
    void follow(double x, double y, double up, float seconds) {
        boolean seating = seat < 1f;
        // Sitting down in a long vehicle the place followed moves further than a teleport: it is glided to all the same.
        boolean jumped = !seating && (Math.hypot(x - wantedX, y - wantedY) > JUMPS_PAST || Math.abs(up - wantedUp) > JUMPS_PAST);
        wantedX = x;
        wantedY = y;
        wantedUp = up;
        if (!followed || jumped) {
            followX = x;
            followY = y;
            followUp = up;
            followed = true;
            return;
        }
        double dx = x - followX, dy = y - followY;
        double apart = Math.hypot(dx, dy);
        double trail = apart * Math.exp(-seconds / (seating ? SEAT_FOLLOW_SECONDS : FOLLOW_SECONDS));
        if (!seating) {
            trail = Math.min(trail, MOST_TRAIL);
        }
        double scale = apart > 0.0 ? trail / apart : 0.0;
        followX = x - dx * scale;
        followY = y - dy * scale;
        followUp = up + (followUp - up) * Math.exp(-seconds / (seating ? SEAT_FOLLOW_SECONDS : FOLLOW_UP_SECONDS));
    }

    double followX() {
        return followX;
    }

    double followY() {
        return followY;
    }

    double followUp() {
        return followUp;
    }

    /**
     * The yaw a camera behind a vehicle has after a frame of swinging back behind it, the
     * shorter way round, from the yaw it had.
     */
    static float swungBehind(float yaw, float heading, float seconds) {
        float off = (float) Math.IEEEremainder(heading - yaw, 2 * Math.PI);
        return yaw + off * (1f - (float) Math.exp(-seconds / RECENTRE_SECONDS));
    }

    /** The pitch it has after the same frame of tipping to VEHICLE_PITCH. */
    static float tippedBehind(float pitch, float seconds) {
        return pitch + (VEHICLE_PITCH - pitch) * (1f - (float) Math.exp(-seconds / RECENTRE_SECONDS));
    }

    /** Whether the camera is at the eye, and the picture first person's. */
    boolean atTheEye() {
        return out == 0f;
    }

    /** Whether the camera is all the way out behind the character. */
    boolean behind() {
        return out == 1f;
    }

    /** The share of the way the sights have brought the camera in over the shoulder, setting off and arriving slowly. */
    float aimShare() {
        return aimed * aimed * (3f - 2f * aimed);
    }

    /**
     * Writes the two cameras. Both look where `look` does from where the boom puts them. The
     * clean one has nothing of the head in it, and is what the aim is made from, so head
     * tracking moves no shot. The tracked one is what the picture is drawn from: the head's
     * turn is how `tracked` is turned from `clean`, taken in their axes and given to the camera
     * in its own, and the head's lean is how far `tracked`'s eye is from `clean`'s, given to the
     * camera before the walls are met.
     *
     * @param look     the clean view the mouse has set, which the cameras look along: its eye is unused
     * @param clean    the clean view the head's turn is measured from
     * @param tracked  that view with the head's turn and lean in it
     * @param pivotX   where the boom turns about, with pivotY and pivotUp: over the character's head, or over the vehicle sat in
     * @param behind   how far behind the pivot the cameras stand, all the way out
     * @param above    how far over the pivot they stand
     * @param right    how far to the pivot's right
     * @param floor    the height under the cameras they are kept off
     * @param ceiling  the height over them they are kept under, or positive infinity in the open
     * @param seconds  how long the frame is, for a boom let out after a wall
     */
    void camera(ViewCamera.Pose look, ViewCamera.Pose clean, ViewCamera.Pose tracked, double pivotX, double pivotY, double pivotUp,
            float behind, float above, float right, Walls walls, float levelFloor, float floor, float ceiling, float seconds,
            ViewCamera.Pose cleanCamera, ViewCamera.Pose camera) {
        float s = share();
        float backX = (-look.forwardX * behind + look.rightX * right) * s, backY = (-look.forwardY * behind + look.rightY * right) * s;
        float backUp = (-look.forwardUp * behind + look.rightUp * right + above) * s;
        cleanCamera.set(look);
        place(cleanCamera, this.clean, pivotX, pivotY, pivotUp, backX, backY, backUp, walls, levelFloor, floor, ceiling, seconds);
        camera.set(look);
        turnedLike(camera, clean, tracked);
        place(camera, this.tracked, pivotX, pivotY, pivotUp, backX + (float) (tracked.exactX() - clean.exactX()),
                backY + (float) (tracked.exactY() - clean.exactY()), backUp + (float) (tracked.exactUp() - clean.exactUp()), walls, levelFloor, floor, ceiling,
                seconds);
    }

    /**
     * Puts a camera as far along the boom from the pivot as the walls let it, and between the
     * floor and the ceiling. The walls are told how high the boom is over the floor of the level,
     * which on a stair is under the floor the camera is kept over.
     */
    private static void place(ViewCamera.Pose camera, Boom boom, double pivotX, double pivotY, double pivotUp, float awayX, float awayY, float awayUp,
            Walls walls, float levelFloor, float floor, float ceiling, float seconds) {
        float flat = (float) Math.hypot(awayX, awayY);
        float allowed = flat;
        if (flat > 1e-4f) {
            // The line the camera is put on: under the ceiling, it rises no higher than the camera will stand.
            float rise = (float) (Math.min(pivotUp + awayUp, ceiling - STANDOFF) - pivotUp) / flat;
            allowed = Math.min(walls.room((float) pivotX, (float) pivotY, awayX / flat, awayY / flat, flat, STANDOFF, (float) pivotUp - levelFloor, rise), flat);
        }
        float room = flat > 1e-4f ? Math.min(boom.take(allowed, flat, seconds) / flat, 1f) : 1f;
        double up = Math.max(Math.min(pivotUp + awayUp * room, ceiling - STANDOFF), floor + STANDOFF);
        camera.eyeAt(pivotX + awayX * room, pivotY + awayY * room, up);
    }

    /** Over how much height the push of offTheEave fades in, in tiles. */
    static final float EAVE_FADE = 0.1f;

    /**
     * How much further than STANDOFF a camera up over the floor of its level is held off a wall:
     * a roof runs out past the wall's top by its eave and gutter, and its edge hangs down by the
     * eave's drop at a slope of one, the fascia and the gutter; over a gable end its rake runs
     * out all the way up. From that height up the camera is held as far out as offWalls holds
     * anything, and the push fades in over EAVE_FADE below it, so a camera sinking past the
     * eave is not moved in a step.
     */
    static float offTheEave(float up) {
        float inside = (up - WorldQuery.ROOFS_FROM) / EAVE_FADE + 1f;
        return Math.clamp(inside, 0f, 1f) * (WorldQuery.MOST_HELD_OFF - STANDOFF);
    }

    /** Forgets the walls the booms were last shortened by, and where the pivot was: after a cut, or with the camera at the eye. */
    void unplaced() {
        clean.forget();
        tracked.forget();
        followed = false;
        seatKnown = false;
        crampKnown = false;
        seat = 1f;
    }

    /**
     * Turns `camera`'s axes as `tracked`'s are turned from `clean`'s: each of tracked's axes
     * read in clean's axes and set out again in the camera's. Kept square to each other.
     */
    static void turnedLike(ViewCamera.Pose camera, ViewCamera.Pose clean, ViewCamera.Pose tracked) {
        float rx = camera.rightX, ry = camera.rightY, ru = camera.rightUp;
        float ux = camera.upX, uy = camera.upY, uu = camera.upUp;
        float fx = camera.forwardX, fy = camera.forwardY, fu = camera.forwardUp;
        float fr = dot(tracked.forwardX, tracked.forwardY, tracked.forwardUp, clean.rightX, clean.rightY, clean.rightUp);
        float fa = dot(tracked.forwardX, tracked.forwardY, tracked.forwardUp, clean.upX, clean.upY, clean.upUp);
        float ff = dot(tracked.forwardX, tracked.forwardY, tracked.forwardUp, clean.forwardX, clean.forwardY, clean.forwardUp);
        float ur = dot(tracked.upX, tracked.upY, tracked.upUp, clean.rightX, clean.rightY, clean.rightUp);
        float ua = dot(tracked.upX, tracked.upY, tracked.upUp, clean.upX, clean.upY, clean.upUp);
        float uf = dot(tracked.upX, tracked.upY, tracked.upUp, clean.forwardX, clean.forwardY, clean.forwardUp);
        float forwardX = rx * fr + ux * fa + fx * ff, forwardY = ry * fr + uy * fa + fy * ff, forwardUp = ru * fr + uu * fa + fu * ff;
        float upX = rx * ur + ux * ua + fx * uf, upY = ry * ur + uy * ua + fy * uf, upUp = ru * ur + uu * ua + fu * uf;
        float length = (float) Math.sqrt(forwardX * forwardX + forwardY * forwardY + forwardUp * forwardUp);
        forwardX /= length;
        forwardY /= length;
        forwardUp /= length;
        float along = upX * forwardX + upY * forwardY + upUp * forwardUp;
        upX -= along * forwardX;
        upY -= along * forwardY;
        upUp -= along * forwardUp;
        length = (float) Math.sqrt(upX * upX + upY * upY + upUp * upUp);
        upX /= length;
        upY /= length;
        upUp /= length;
        camera.forwardX = forwardX;
        camera.forwardY = forwardY;
        camera.forwardUp = forwardUp;
        camera.upX = upX;
        camera.upY = upY;
        camera.upUp = upUp;
        // Right is up across forward, as ViewCamera.clean makes it.
        camera.rightX = upY * forwardUp - upUp * forwardY;
        camera.rightY = upUp * forwardX - upX * forwardUp;
        camera.rightUp = upX * forwardY - upY * forwardX;
    }

    private static float dot(float ax, float ay, float aUp, float bx, float by, float bUp) {
        return ax * bx + ay * by + aUp * bUp;
    }
}
