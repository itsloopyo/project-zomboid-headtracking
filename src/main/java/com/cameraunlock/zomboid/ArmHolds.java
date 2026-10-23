package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * Where the mod's own hands go on what the viewer holds, and the arms behind them: the right
 * hand round the grip of a firearm or the handle of something swung, and the left, on a
 * firearm held in both, under its fore-end. While the weapon's action is worked or it is
 * loaded, each hand goes to the part of the weapon that a hand works: the bolt's knob, the
 * slide, the magazine in its well, the port the rounds go in at.
 *
 * Everything is in the axes of the view the frame is drawn from, ViewModelMath's: x to the
 * right, y up, z towards the viewer.
 */
final class ArmHolds {
    private ArmHolds() {}

    /** Where a body's shoulders are from its eye, in metres: to the side, below, and behind. */
    static final float[] SHOULDER = {0.19f, -0.22f, 0.07f};
    /**
     * The same behind a long gun, the right's then the left's: the body is turned to the gun,
     * with its butt at the right shoulder and the left shoulder brought round to beside the
     * eye. No further: the arm ends at its shoulder, and a shoulder ahead of the eye comes
     * into the picture when the head is tipped down.
     */
    static final float[][] LONG_SHOULDERS = {{0.18f, -0.22f, 0.10f}, {-0.15f, -0.22f, -0.02f}};
    /**
     * The left shoulder behind a long gun with the sights up, where the head is laid on the stock
     * and the shoulder is further out from the eye. From LONG_SHOULDERS' place the forearm ran
     * back under the gun's middle and went 10 to 18 mm into a magazine or a sawn-off's grip;
     * from here it passes to the left of them.
     */
    static final float[] LONG_LEFT_SHOULDER_AIMED = {-0.21f, -0.22f, -0.02f};
    /** How near a long gun's grip the hand under its fore-end comes when the arm reaches no further along it, in metres. */
    static final float NEAREST_FORE = 0.16f;
    /**
     * How much of its whole length the arm to a fore-end is let reach. Short of straight its
     * elbow hangs under the line from the shoulder to the hand, which from the eye is under
     * the foot of the picture.
     */
    static final float REACH_EASY = 0.96f;
    /**
     * Where each forearm seems to come from, the right's then the left's, in metres from the
     * eye: below the foot of the picture and out to its side, so that an arm rises into the
     * picture from its lower corner and is seen along its length.
     */
    static final float[][] FROM = {{0.34f, -0.56f, 0.12f}, {-0.42f, -0.52f, -0.05f}};
    /**
     * Where the left forearm seems to come from under a long gun's fore-end: below the gun, a
     * little to the right of the left shoulder, so the elbow hangs under the fore-end and the
     * forearm rises to it as a shooter's does.
     */
    static final float[] LONG_FROM = {0.08f, -0.90f, -0.40f};
    /**
     * How the right hand goes round a grip. The hand's pose closes round something as deep as
     * a grip about a point level with its knuckles, and it is put on the grip's middle. A grip
     * lies across the palm aslant, from the web of the thumb to the heel of the hand, so within
     * the palm the knuckles lean back from the grip's line by at least this much, in radians,
     * and on a grip raked further forward by as much more as keeps them no further forward of
     * upright than this, in radians: a lever gun's straight wrist is raked 50 degrees. With the
     * knuckles along the grip the hand asked its forearm to turn along its length further than
     * a forearm turns, 100 to 170 degrees on the game's firearms, the rest was rolled into the
     * upper arm, and the arm came at the eye across the side of the picture; aslant, each
     * forearm comes up from under the picture and is turned no further than a forearm turns.
     * At the hip a handgun's first finger is this much of the way to the trigger from lying
     * along the frame, which with the knuckles aslant points it at the slide. On a long gun the
     * palm, which faces a handgun's grip square from its right, is turned this far back towards
     * the butt and then this far down the grip towards its foot, in radians, its knuckles over
     * the wrist and its fingers round under it.
     */
    static final float GRIP_ASLANT = (float) Math.toRadians(30), KNUCKLES_RAKE = (float) Math.toRadians(8), HANDGUN_HIP_TRIGGER = 0.5f,
            LONG_PALM_BACK = (float) Math.toRadians(28), LONG_PALM_DOWN = (float) Math.toRadians(15);
    /**
     * The left hand round a pistol with the right, as a pistol is held in both: on the
     * side of the grip the right palm leaves bare and round the right hand's fingers, this much
     * of the grip's thickness ahead of the grip's middle and this far to its left, in metres,
     * its knuckles leaning this far back from the grip's line, in radians, so that its forearm
     * comes up from under the picture as the right one does and does not lie across it, and its palm
     * turned this much towards the muzzle from facing the grip square, in parts of that, so that
     * its fingers go round the front of the right hand's and its thumb lies forward along the
     * frame's left side: turned the other way the thumb crossed the back of the grip.
     */
    static final float SUPPORT_AHEAD = 0.45f, SUPPORT_ASIDE = 0.024f, SUPPORT_LEANS = (float) Math.toRadians(30), SUPPORT_PALM_AHEAD = 0.35f;
    /** How many times the hand under a fore-end is brought back to where its arm reaches it: each time it may come under a higher or lower underside. */
    static final int REACH_PASSES = 4;
    /** How far a bolt's handle turns up to open it, in radians, and how far the bolt of a firearm of no particular make is drawn back, in metres. */
    static final float BOLT_LIFT = (float) Math.toRadians(55), BOLT_TRAVEL = 0.09f;
    /** A charging handle further out to the side of the bore than this, in metres, is on the gun's side and not on top of it. */
    static final float HANDLE_OUTSIDE = 0.015f;
    /** How far a handgun's slide is drawn back, in metres. */
    static final float SLIDE_TRAVEL = 0.03f;
    /** How much of the way from the grip to the muzzle the hand under a fore-end rests, on a firearm that does not say. */
    static final float FORE_SHARE = 0.42f;
    /**
     * How far behind the front edge of a magazine that hangs under the receiver the middle of the fist round it is, in metres:
     * the fingers come round that edge. Held by the magazine's middle, they went 10 to 14 mm into a long one's front.
     */
    static final float MAGAZINE_EDGE_BEHIND = 0.026f;
    /** How far under the guard's foot the middle of that fist is at the least, in metres: higher, its first finger went into a short magazine's receiver. */
    static final float MAGAZINE_UNDER = 0.05f;
    /** How far ahead the fingers of a hand on a flush magazine or a handgun's lean for each unit they run, up. */
    static final float MAGAZINE_FINGERS_UP = 0.1f;
    /**
     * Where a magazine that does not hang under the receiver goes in, and where rounds go in,
     * from the trigger guard, in metres: a flush magazine's well this far ahead of the guard, the
     * fist on it this far under the guard's foot (level with it, the fingers went 12 to 16 mm up
     * into the stock); a pump's port under the receiver this far ahead of the guard; a bolt's opening over the
     * receiver this far ahead of the knob; the breech of a gun that breaks open this far ahead of the guard, at the bore; a
     * revolver's cylinder this much of the guard's length back from its front and this far
     * under the bore.
     */
    static final float FLUSH_WELL = 0.04f, FLUSH_UNDER = 0.02f, PUMP_PORT = 0.035f, BOLT_PORT = 0.03f, BREECH = 0.015f, CYLINDER_BACK = 0.35f, CYLINDER_UNDER = 0.012f;
    /**
     * How far the far end of a handle leans from square to the fingers that hold it, in radians, at the least
     * and at the most. It lies the more aslant in the fist the nearer it points along the arm that holds it:
     * a fist at the end of a straight arm has what it swings lying on from the arm, not square across it.
     */
    static final float HANDLE_SLANT = (float) Math.toRadians(18), HANDLE_SLANT_MOST = (float) Math.toRadians(55);
    /**
     * How far the palm under a fore-end is turned from facing straight up towards the gun's
     * middle, in radians: it cups the fore-end from under and a little from the left, as far
     * round as a forearm turned palm up from an elbow under it takes it. Its fingers run on
     * along the arm's line. Facing straight up with its fingers squared across the barrel it
     * asked the forearm to turn 130 to 155 degrees from how it rests, further than palm up.
     */
    static final float SUPPORT_PALM_TURN = (float) Math.toRadians(30);
    /** How far behind the eye the line is that a body turns about, in metres. */
    static final float SPINE_BEHIND = 0.1f;
    /** How far down a handle from the right fist the left one is, in metres. */
    static final float LOWER_FIST = 0.095f;
    /** Where the left hand is when it is off the weapon, fetching a round or a magazine: below the picture, in metres from the eye. */
    static final float[] AWAY = {-0.20f, -0.52f, -0.30f};

    /**
     * The weapon as the view has it: where the right hand holds it, the way it points and the
     * way its top faces, each of length one, for a firearm where its mesh is, and for something
     * swung in both hands how far along it from the right fist the left one is, in metres: less
     * than nought nearer the end the hands hold. Whether it is a spear, whose left arm comes to its
     * shaft from under the picture in every way it is held: see SPEAR_SHOULDER.
     */
    record Weapon(boolean firearm, boolean handgun, float[] grip, float[] pointing, float[] over, Placed placed, float second, boolean spear) {
        Weapon(boolean firearm, boolean handgun, float[] grip, float[] pointing, float[] over, Placed placed) {
            this(firearm, handgun, grip, pointing, over, placed, -LOWER_FIST, false);
        }
    }

    /**
     * A firearm's mesh where the view has it. Whoever places the arms each frame keeps one and
     * fills it again.
     */
    static final class Placed {
        private WeaponShape shape;
        private final float[] toView = new float[16], still = new float[16];
        private float slid, foreRest, travel, pinAlong = Float.NaN, pinUp;
        /** Where rounds go in, in the mesh's axes, where the mesh's model says, or null for the shape to say. */
        private float[] port;
        /** Where the hand takes hold of a handle that is drawn straight back to charge the gun, in the mesh's axes, or null. */
        private float[] handle;

        Placed() {}

        Placed(WeaponShape shape, float[] toView, float[] still, float slid, float foreRest, float travel) {
            set(shape, toView, still, slid, foreRest, travel);
        }

        /**
         * @param shape    what the mesh says of where hands go on it
         * @param toView   the matrix from the mesh to the view, by columns, copied
         * @param still    the same with the weapon held still: without the kick of a shot, the sway of a step, the dip of a landing or a pump's stroke, copied
         * @param slid     how far back a pump's fore-end is drawn now, in the mesh's units
         * @param foreRest where along the barrel the hand under the fore-end rests, in the mesh's units, or not a number for the shape to say
         * @param travel   how far back the bolt is drawn, or a handgun's slide, in metres
         */
        Placed set(WeaponShape shape, float[] toView, float[] still, float slid, float foreRest, float travel) {
            this.shape = shape;
            System.arraycopy(toView, 0, this.toView, 0, 16);
            System.arraycopy(still, 0, this.still, 0, 16);
            this.slid = slid;
            this.foreRest = foreRest;
            this.travel = travel;
            port = null;
            handle = null;
            pinAlong = Float.NaN;
            return this;
        }

        /** Says that the gun is charged by a handle drawn straight back, held at handle in the mesh's axes, or with null that it is not. */
        Placed charged(float[] mesh) {
            handle = mesh;
            return this;
        }

        float[] handle() {
            return handle;
        }

        /** Says where rounds go in, in the mesh's axes, or with null leaves it to the shape. */
        Placed port(float[] mesh) {
            port = mesh;
            return this;
        }

        float[] port() {
            return port;
        }

        /** Says where the pin a lever turns about is, along the barrel and over it in the mesh's units: else it turns about the front of the guard. */
        Placed pinned(float along, float up) {
            pinAlong = along;
            pinUp = up;
            return this;
        }

        float pinAlong() {
            return pinAlong;
        }

        float pinUp() {
            return pinUp;
        }

        WeaponShape shape() {
            return shape;
        }

        float[] toView() {
            return toView;
        }

        float[] still() {
            return still;
        }

        float slid() {
            return slid;
        }

        float foreRest() {
            return foreRest;
        }

        float travel() {
            return travel;
        }

        /** A place of the mesh's in the view. */
        float[] at(float[] mesh) {
            float[] out = new float[3];
            ViewModelMath.place(toView, mesh[0], mesh[1], mesh[2], out);
            return out;
        }

        /** A place given along the barrel, up and to the side in the mesh's units, in the view. */
        float[] at(float a, float u, float s) {
            float[] mesh = new float[3];
            shape.place(a, u, s, mesh);
            return at(mesh);
        }

        float[] stillAt(float a, float u, float s) {
            float[] mesh = new float[3], out = new float[3];
            shape.place(a, u, s, mesh);
            ViewModelMath.place(still, mesh[0], mesh[1], mesh[2], out);
            return out;
        }

        /** A way of the mesh's in the view, of length one. */
        float[] way(float[] mesh) {
            float[] out = new float[3];
            ViewModelMath.turned(toView, mesh, out);
            ArmPose.unit(out[0], out[1], out[2], out);
            return out;
        }

        /** How long one of the mesh's units is in the view. */
        float unit() {
            float[] out = new float[3];
            ViewModelMath.turned(toView, shape.along, out);
            return (float) Math.sqrt(ArmPose.dot(out, out));
        }

        /** Where along the barrel the hand under the fore-end rests: on a pump its fore-end's middle, and on another as far out as most hold one. */
        float foreRests(int action) {
            if (!Float.isNaN(foreRest)) {
                return foreRest;
            }
            if (action == ArmActs.PUMP) {
                return (shape.levelFrom + shape.levelTo) / 2f;
            }
            return Math.clamp(FORE_SHARE * shape.muzzle, shape.foreFrom, shape.foreTo);
        }

        /** How far up from the underside at a place along the barrel the middle of the hand under it is, in the mesh's units: no higher than the bore. */
        float overUnder(float a) {
            float under = shape.under(a);
            return under + Math.clamp(shape.bore - under, 0f, WeaponShape.FORE_HALF * shape.perMetre);
        }
    }

    /**
     * What is being done to the weapon.
     *
     * @param aim    0 at the hip, 1 with the sights up: the first finger goes to the trigger
     * @param both   whether the left hand is on the weapon too
     * @param action how the weapon's action is worked and how it is loaded: one of ArmActs' kinds
     * @param cycled how long ago the shot was that the action is being worked after, in seconds, or less than nought
     * @param doing  what the holder is doing to it besides holding it: one of ArmActs' doings
     * @param done   for how long, in seconds
     * @param round  how far through loading one round the hand is, 0 to 1
     * @param pace   how long the game is taken to be over it, in seconds
     * @param seated how long ago the game finished loading a magazine, in seconds, or less than nought
     * @param clip   whether the weapon has a magazine in it
     * @param out    how long ago the magazine left the weapon while it is being loaded or unloaded, in seconds, or less than nought if none has
     * @param fetching how long a magazine has been on its way to the weapon, in seconds, or less than nought
     * @param fetchTakes how long the game is taken to be over that, in seconds
     * @param turned how far the body is turned to the right behind what it swings, in radians: the shoulders go round with it
     */
    record Doing(float aim, boolean both, int action, float cycled, int doing, float done, float round, float pace, float seated,
            boolean clip, float out, float fetching, float fetchTakes, float turned) {}

    /** What the left hand carries to the weapon or away from it: nothing, a magazine, a shotgun's shell or a cartridge. */
    static final int NOTHING = 0, MAGAZINE = 1, SHELL = 2, CARTRIDGE = 3;

    /** What the left hand carries, and the matrix that puts it in the hand: its length along y, in metres, to the view. */
    static final class Carried {
        int what;
        final float[] at = new float[16];
    }

    /** A hand on something: where the middle of what it holds is, the way across it to its thumb's side, the way out of its palm, and how its fingers are bent. */
    private static final class Hold {
        final float[] at = new float[3], across = new float[3], out = new float[3];
        final float[] weights;
        /** Whether what is held is a handle, round about the line across the hand, which a fist may be anywhere round. */
        boolean round;

        Hold(ArmModel model) {
            weights = new float[model.poses.length];
        }

        /** A hold at a place, its palm facing `palm` and its fingers running the way of `fingers`, in a pose. */
        Hold set(float[] place, float[] palm, float[] fingers, boolean left, int pose) {
            System.arraycopy(place, 0, at, 0, 3);
            ArmPose.unit(palm[0], palm[1], palm[2], out);
            // The thumb's side is square to both, on the side that hand has its thumb.
            if (left) {
                ArmPose.cross(out, fingers, across);
            } else {
                ArmPose.cross(fingers, out, across);
            }
            Arrays.fill(weights, 0f);
            weights[pose] = 1f;
            return this;
        }

        /**
         * This hold taken `share` of the way to another. The hand is turned from the one way
         * round to the other by the one turn that does it, a share of that turn at a time: its
         * lines taken each a share of the way would pass through a hand facing nowhere a wrist goes.
         */
        Hold towards(Hold other, float share) {
            float[] from = frame(across, out), to = frame(other.across, other.out);
            // The turn from the one to the other, and the line it is about.
            float[] turn = new float[9];
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    turn[column * 3 + row] = to[row] * from[column] + to[3 + row] * from[3 + column] + to[6 + row] * from[6 + column];
                }
            }
            float cos = Math.clamp((turn[0] + turn[4] + turn[8] - 1f) / 2f, -1f, 1f);
            float[] about = {turn[5] - turn[7], turn[6] - turn[2], turn[1] - turn[3]};
            if (cos > -0.999f && ArmPose.unit(about[0], about[1], about[2], about)) {
                float[] part = new float[9];
                ArmPose.about(about, (float) Math.acos(cos) * share, part);
                float[] a = {from[0], from[1], from[2]}, o = {from[3], from[4], from[5]};
                ArmPose.turned(part, a, across);
                ArmPose.turned(part, o, out);
            } else if (cos <= -0.999f) {
                // Half a turn has no one line to be about: the lines go straight across.
                for (int axis = 0; axis < 3; axis++) {
                    across[axis] += (other.across[axis] - across[axis]) * share;
                    out[axis] += (other.out[axis] - out[axis]) * share;
                }
            }
            for (int axis = 0; axis < 3; axis++) {
                at[axis] += (other.at[axis] - at[axis]) * share;
            }
            for (int pose = 0; pose < weights.length; pose++) {
                weights[pose] += (other.weights[pose] - weights[pose]) * share;
            }
            round = share < 0.5f ? round : other.round;
            return this;
        }

        /** The hand's three lines, square to each other and of length one: across it, out of its palm, and the third. */
        static float[] frame(float[] across, float[] out) {
            float[] a = new float[3], o = new float[3], third = new float[3];
            ArmPose.unit(across[0], across[1], across[2], a);
            float along = ArmPose.dot(out, a);
            ArmPose.unit(out[0] - a[0] * along, out[1] - a[1] * along, out[2] - a[2] * along, o);
            ArmPose.cross(a, o, third);
            return new float[] {a[0], a[1], a[2], o[0], o[1], o[2], third[0], third[1], third[2]};
        }
    }

    /**
     * Where the left hand was last, kept by whoever places the arms from frame to frame, so
     * that a hand whose hold changes to one that is not beside it crosses over to it and is
     * not simply there. Holds are kept in the weapon's own axes: a hand on a fore-end moves
     * with the weapon and is not behind it.
     */
    static final class Settled {
        /** How long ago the frame before this was, in seconds. */
        float seconds;
        private Hold aimed, shown, from;
        private float crossed = 1f;
    }

    /** The weapon's own axes in the view, and where its parts are given from. */
    private static final class Axes {
        final float[] ahead, up = new float[3], right = new float[3], grip;
        final float scale;

        Axes(Weapon weapon, float scale) {
            ahead = weapon.pointing();
            grip = weapon.grip();
            this.scale = scale;
        }

        /** A place given from the grip, in metres: ahead, up, and to the right. */
        float[] at(float a, float u, float r) {
            float[] out = new float[3];
            for (int axis = 0; axis < 3; axis++) {
                out[axis] = grip[axis] + (ahead[axis] * a + up[axis] * u + right[axis] * r) * scale;
            }
            return out;
        }

        /** A hold of the view's, in the weapon's own axes and metres, and back. */
        Hold within(Hold hold, ArmModel model) {
            Hold out = copy(new Hold(model), hold);
            float[] off = {hold.at[0] - grip[0], hold.at[1] - grip[1], hold.at[2] - grip[2]};
            float[][] lines = {off, hold.across, hold.out}, into = {out.at, out.across, out.out};
            for (int line = 0; line < 3; line++) {
                float by = line == 0 ? 1f / scale : 1f;
                into[line][0] = ArmPose.dot(lines[line], ahead) * by;
                into[line][1] = ArmPose.dot(lines[line], up) * by;
                into[line][2] = ArmPose.dot(lines[line], right) * by;
            }
            return out;
        }

        Hold seen(Hold hold, ArmModel model) {
            Hold out = copy(new Hold(model), hold);
            System.arraycopy(at(hold.at[0], hold.at[1], hold.at[2]), 0, out.at, 0, 3);
            System.arraycopy(way(hold.across[0], hold.across[1], hold.across[2]), 0, out.across, 0, 3);
            System.arraycopy(way(hold.out[0], hold.out[1], hold.out[2]), 0, out.out, 0, 3);
            return out;
        }

        /** A way, in parts of ahead, up and to the right. */
        float[] way(float a, float u, float r) {
            float[] out = new float[3];
            for (int axis = 0; axis < 3; axis++) {
                out[axis] = ahead[axis] * a + up[axis] * u + right[axis] * r;
            }
            return out;
        }
    }

    /**
     * How far back a pistol's slide is drawn by the hand that racks it, 0 to 1, a share of the
     * way through racking it: drawn back, and let go to run forward of itself.
     */
    static float slideRacked(float done) {
        return between(done, 0.3f, SLIDE_LET_GO) - between(done, SLIDE_LET_GO, 0.56f);
    }

    /**
     * How far over the slide the hand that racks it is held, in metres, and how far out to the
     * left of the slide and up it goes on its way there and back, in metres and in parts of the
     * way: on a straight way between the grip and the slide the hand went 11 to 19 mm through
     * the frame, and its fingers 11 to 16 mm into the slide's top lower on it.
     */
    static final float SLIDE_ABOVE = 0.008f, SLIDE_ROUND = 0.07f, SLIDE_BESIDE = 0.6f;
    /**
     * How far under a handgun's grip the middle of the fist that takes its magazine is, in
     * metres: the whole fist under the grip's foot. At the foot its first finger went 6 to 9 mm
     * up into the grip.
     */
    static final float HANDGUN_MAGAZINE_UNDER = 0.055f;

    /** Racking, the hand lets go of the slide this far through it. */
    static final float SLIDE_LET_GO = 0.5f;

    /**
     * How far a bolt's knob is turned up about the bore while it is worked, in radians, given how
     * far through its turn the hand is: a bolt gun's is turned up before it is drawn back; a
     * self-loader's handle, which the game reloads as a magazine fed bolt action, is not turned.
     */
    static float knobTurn(int action, float turnedThrough) {
        return action == ArmActs.BOLT_SINGLE ? BOLT_LIFT * turnedThrough : 0f;
    }

    /** The right hand on a bolt's knob, turned up about the bore by `turned` and drawn back `drawnBack` of the bolt's travel. */
    private static Hold onKnob(ArmModel model, Placed placed, Axes axes, float turned, float drawnBack) {
        WeaponShape shape = placed.shape();
        // The knob turns up about the bore from whichever side it is on, and is drawn back.
        float knobA = ArmPose.dot(shape.knob, shape.along), knobU = ArmPose.dot(shape.knob, shape.up) - shape.bore, knobS = ArmPose.dot(shape.knob, shape.side);
        float out = Math.abs(knobS), side = Math.signum(knobS);
        float o = out * (float) Math.cos(turned) - knobU * (float) Math.sin(turned);
        float u = out * (float) Math.sin(turned) + knobU * (float) Math.cos(turned);
        float[] knob = placed.at(knobA - placed.travel() * shape.perMetre * drawnBack, shape.bore + u, side * o);
        // The palm is against the knob's outside, a little under it, and the fingers run forward and up past it. Under it with the
        // fingers run forward flat, the hand asked its forearm to turn 80 to 103 degrees.
        return new Hold(model).set(knob, axes.way(0f, 0.3f, -1f), axes.way(0.7f, 0.6f, -0.3f), false, model.pose("knob"));
    }

    /** 0 before `from`, 1 after `to`, and eased between. */
    private static float between(float time, float from, float to) {
        return ViewModelMath.smooth((time - from) / (to - from));
    }

    /**
     * Places the arms on a weapon.
     *
     * @param scale   the model's metres to tiles
     * @param fromAim the matrix from the aim's axes, which the shoulders are fixed in, to the view's
     * @param skins   the answer, right then left: for each bone the matrix ArmPose gives
     * @param said    the answer, right then left: what ArmPose says of each arm
     * @param carried the answer: what the left hand carries
     * @param settled where the left hand was last frame, and how long ago that was
     * @return how many arms are placed: 2 with the left hand on the weapon or busy with it, 1 without, and 0 if an arm cannot be placed
     */
    static int place(ArmModel model, float scale, Weapon weapon, Doing doing, float[] fromAim, float[][] skins, float[][] said, Carried carried, Settled settled) {
        Axes axes = new Axes(weapon, scale);
        float tipped = ArmPose.dot(weapon.over(), axes.ahead);
        if (!ArmPose.unit(weapon.over()[0] - axes.ahead[0] * tipped, weapon.over()[1] - axes.ahead[1] * tipped, weapon.over()[2] - axes.ahead[2] * tipped, axes.up)) {
            return 0;
        }
        ArmPose.cross(axes.ahead, axes.up, axes.right);
        Hold right = right(model, weapon, axes, doing, fromAim);
        if (!solve(model, weapon, scale, doing.turned(), doing.aim(), right, fromAim, 0, skins, said)) {
            return 0;
        }
        carried.what = NOTHING;
        Hold left = left(model, weapon, axes, doing, fromAim);
        if (left == null) {
            settled.aimed = null;
            return 1;
        }
        Hold aimed = axes.within(left, model);
        if (settled.aimed != null) {
            float[] before = Hold.frame(settled.aimed.across, settled.aimed.out), now = Hold.frame(aimed.across, aimed.out);
            boolean jumped = ArmPose.apart(settled.aimed.at, aimed.at) > ArmActs.JUMPS
                    || before[3] * now[3] + before[4] * now[4] + before[5] * now[5] < ArmActs.JUMPS_TURNING
                    || before[0] * now[0] + before[1] * now[1] + before[2] * now[2] < ArmActs.JUMPS_TURNING;
            if (jumped) {
                settled.from = settled.shown;
                settled.crossed = 0f;
            }
            if (settled.crossed < 1f) {
                settled.crossed = Math.min(settled.crossed + settled.seconds / ArmActs.CROSSES_IN, 1f);
            }
        } else {
            settled.crossed = 1f;
        }
        settled.aimed = aimed;
        settled.shown = settled.crossed < 1f ? copy(new Hold(model), settled.from).towards(aimed, ViewModelMath.smooth(settled.crossed)) : aimed;
        left = axes.seen(settled.shown, model);
        int carries = carries(doing);
        if (carries != NOTHING) {
            // A magazine stands in the fist, its top out of the thumb's side. A round lies along the fingers.
            float[] long3 = new float[3], out3 = new float[3], third = new float[3];
            ArmPose.unit(left.out[0], left.out[1], left.out[2], out3);
            if (carries == MAGAZINE) {
                // Up the line it stands on in its well, which leans from the knuckles' as much as they lean on its front edge.
                float[] fingers = new float[3], up3 = new float[3];
                ArmPose.cross(left.across, out3, fingers);
                float leans = magazineLeans(weapon.placed().shape(), doing.action());
                for (int row = 0; row < 3; row++) {
                    up3[row] = left.across[row] + fingers[row] * leans;
                }
                float along = ArmPose.dot(up3, out3);
                ArmPose.unit(up3[0] - out3[0] * along, up3[1] - out3[1] * along, up3[2] - out3[2] * along, long3);
            } else {
                ArmPose.cross(left.across, out3, long3);
                ArmPose.unit(long3[0], long3[1], long3[2], long3);
            }
            ArmPose.cross(out3, long3, third);
            float back = carries == MAGAZINE ? MAGAZINE_LONG / 2f : ROUND_BEHIND;
            for (int row = 0; row < 3; row++) {
                carried.at[row] = out3[row] * scale;
                carried.at[4 + row] = long3[row] * scale;
                carried.at[8 + row] = third[row] * scale;
                carried.at[12 + row] = left.at[row] - long3[row] * back * scale;
            }
            carried.at[3] = 0f;
            carried.at[7] = 0f;
            carried.at[11] = 0f;
            carried.at[15] = 1f;
            carried.what = carries;
        }
        return solve(model, weapon, scale, doing.turned(), doing.aim(), left, fromAim, 1, skins, said) ? 2 : 0;
    }

    /**
     * Places both arms on hands busy with a timed action (TimedActs), each hand given in the
     * view: where the middle of what it holds is, the way its fingers run and the way out of
     * its palm. Each hand is laid exactly as given, so that what the game has in it can be put
     * in it by the same lines: it is not turned back as a hand on a weapon is, and TimedActs
     * gives no hand more turn than a forearm takes. False if either arm cannot be placed.
     *
     * @param scale   the model's metres to tiles
     * @param fromAim the matrix from the aim's axes, which the shoulders are fixed in, to the view's
     * @param skins   the answer, right then left: for each bone the matrix ArmPose gives
     * @param said    the answer, right then left: what ArmPose says of each arm
     */
    static boolean placeTask(ArmModel model, float scale, TimedActs.Hand[] hands, float[] fromAim, float[][] skins, float[][] said) {
        float[] shoulder = new float[3], from = new float[3], across = new float[3];
        for (int side = 0; side < 2; side++) {
            TimedActs.Hand hand = hands[side];
            float[] weights = new float[model.poses.length];
            weights[model.pose(TimedActs.POSE_NAMES[hand.pose])] = 1f;
            // The thumb's side is square to the fingers and the palm, on the side that hand has its thumb.
            if (side == 1) {
                ArmPose.cross(hand.out, hand.fingers, across);
            } else {
                ArmPose.cross(hand.fingers, hand.out, across);
            }
            starts(false, side, scale, 0f, 0f, fromAim, shoulder, from);
            if (!ArmPose.solve(model, scale, weights, hand.at, across, hand.out, shoulder, from, side == 1, false, skins[side], said[side])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Where the middle of the fist round a magazine in its well is, up and along the barrel, in the mesh's units. On one that
     * hangs under the receiver: half way down what shows of it, or lower where that leaves the fist's top in the receiver, and
     * behind its front edge there. Where none hangs under it: under the receiver just ahead of the guard.
     */
    static float magazineUp(WeaponShape shape) {
        return shape.magazine ? Math.min(shape.magazineMiddle, shape.guardFoot - MAGAZINE_UNDER * shape.perMetre) : shape.guardFoot - FLUSH_UNDER * shape.perMetre;
    }

    static float magazineAlong(WeaponShape shape) {
        if (!shape.magazine) {
            return shape.guardFront + FLUSH_WELL * shape.perMetre;
        }
        return shape.magazineEdge + shape.magazineLeans * (shape.magazineMiddle - magazineUp(shape)) - MAGAZINE_EDGE_BEHIND * shape.perMetre;
    }

    /** How far up the fingers on a magazine lean for each unit they run ahead: as its front edge leans back, where it hangs under the receiver. */
    static float magazineLeans(WeaponShape shape, int action) {
        return action != ArmActs.HANDGUN && shape.magazine ? shape.magazineLeans : MAGAZINE_FINGERS_UP;
    }

    /** How long a magazine is, and how far behind where it is pinched a round's base is, in metres. */
    static final float MAGAZINE_LONG = 0.115f, ROUND_BEHIND = 0.02f;

    /** What the left hand has in it at this moment of what is being done. */
    static int carries(Doing doing) {
        int action = doing.action();
        boolean magazine = action == ArmActs.HANDGUN || action == ArmActs.BOLT;
        if (magazine) {
            if (doing.doing() != ArmActs.LOADING && doing.doing() != ArmActs.UNLOADING) {
                return doing.seated() >= 0f && doing.seated() < ArmActs.HOME_IN ? MAGAZINE : NOTHING;
            }
            // The one that came out until it is out of sight, and then the one on its way in. One in the weapon is the weapon's.
            boolean old = doing.out() >= 0f && doing.out() < ArmActs.TAKEN_IN;
            return !doing.clip() && (old || doing.fetching() >= 0f) ? MAGAZINE : NOTHING;
        }
        if (doing.doing() != ArmActs.LOADING) {
            return NOTHING;
        }
        if (doing.round() >= ArmActs.ROUND_IN) {
            return NOTHING;
        }
        return action == ArmActs.PUMP || action == ArmActs.DOUBLE || action == ArmActs.DOUBLE_SAWN ? SHELL : CARTRIDGE;
    }

    /** The right hand: on the grip or the handle, and on a rifle's bolt or its lever while that is worked. */
    private static Hold right(ArmModel model, Weapon weapon, Axes axes, Doing doing, float[] fromAim) {
        Hold hold = new Hold(model);
        System.arraycopy(weapon.grip(), 0, hold.at, 0, 3);
        if (!weapon.firearm()) {
            // A fist holds a handle a little aslant, its far end leaning on towards the fingers.
            float slant = slant(weapon, axes, hold.at, 0, doing.turned(), fromAim);
            float cos = (float) Math.cos(slant), sin = (float) Math.sin(slant);
            float[] fingers = new float[3];
            ArmPose.cross(axes.ahead, axes.right, fingers);
            for (int axis = 0; axis < 3; axis++) {
                hold.across[axis] = axes.ahead[axis] * cos - fingers[axis] * sin;
                hold.out[axis] = -axes.right[axis];
            }
            hold.weights[model.pose("handle")] = 1f;
            hold.round = true;
            return hold;
        }
        // Round the grip the mesh has, along its line, the palm on its right side.
        Placed placed = weapon.placed();
        WeaponShape shape = placed.shape();
        float[] line = placed.way(shape.gripLine), front = new float[3];
        float along = ArmPose.dot(axes.ahead, line);
        ArmPose.unit(axes.ahead[0] - line[0] * along, axes.ahead[1] - line[1] * along, axes.ahead[2] - line[2] * along, front);
        float back = weapon.handgun() ? 0f : LONG_PALM_BACK, down = weapon.handgun() ? 0f : LONG_PALM_DOWN;
        float cos = (float) Math.cos(back), sin = (float) Math.sin(back), downCos = (float) Math.cos(down), downSin = (float) Math.sin(down);
        for (int axis = 0; axis < 3; axis++) {
            hold.out[axis] = -(axes.right[axis] * cos + front[axis] * sin) * downCos - line[axis] * downSin;
        }
        float rake = (float) Math.atan2(Math.abs(ArmPose.dot(line, axes.ahead)), Math.abs(ArmPose.dot(line, axes.up)));
        aslant(line, hold.out, front, Math.max(GRIP_ASLANT, rake - KNUCKLES_RAKE), hold.across);
        float raised = ViewModelMath.smooth(doing.aim());
        float hipTrigger = weapon.handgun() ? HANDGUN_HIP_TRIGGER : 0f;
        raised = hipTrigger + (1f - hipTrigger) * raised;
        if (weapon.handgun()) {
            // A handgun's grip is held by its outside: its front edge on the right side, where the pistol poses wrap it from.
            float forward = shape.gripThick / 2f * placed.unit(), aside = shape.gripWide * placed.unit();
            for (int axis = 0; axis < 3; axis++) {
                hold.at[axis] += front[axis] * forward + axes.right[axis] * aside;
            }
            hold.weights[model.pose("pistol")] = 1f - raised;
            hold.weights[model.pose("pistol trigger")] = raised;
            return hold;
        }
        hold.weights[model.pose("grip")] = 1f - raised;
        hold.weights[model.pose("trigger")] = raised;
        float cycled = doing.cycled();
        if (placed.handle() != null) {
            if (doing.doing() == ArmActs.RACKING) {
                onChargingHandle(model, placed, axes, doing, hold);
            }
        } else if (cycled >= 0f && doing.action() == ArmActs.BOLT_SINGLE) {
            // The hand goes to the knob as the bolt is about to be worked, turns it up, draws
            // it back, shoves it home, turns it down, and goes back to the grip.
            float[] worked = new float[2];
            OwnRifle.worked(Math.max(cycled - ArmActs.BOLT_AFTER, 0f), worked);
            float on = between(cycled, ArmActs.BOLT_REACHED_FROM, ArmActs.BOLT_AFTER)
                    - between(cycled, ArmActs.BOLT_AFTER + OwnRifle.WORKED_FOR, ArmActs.BOLT_AFTER + OwnRifle.WORKED_FOR + ArmActs.BOLT_LEFT_IN);
            hold.towards(onKnob(model, placed, axes, knobTurn(ArmActs.BOLT_SINGLE, worked[0]), worked[1]), on);
        } else if (doing.action() == ArmActs.BOLT && doing.doing() == ArmActs.RACKING) {
            // A gun the game reloads as a magazine fed bolt action is a self-loader: its handle is drawn straight back
            // and let go, as a pistol's slide is, and the hand leaves it.
            float through = doing.done() / Math.max(doing.pace(), ArmActs.SHORTEST);
            float on = between(through, 0f, 0.3f) - between(through, 0.6f, 0.95f);
            // Once it lets go, the hand stays back where it was as it leaves, and the handle runs home on its own.
            hold.towards(onKnob(model, placed, axes, knobTurn(ArmActs.BOLT, 1f), through < SLIDE_LET_GO ? slideRacked(through) : 1f), on);
        } else if (cycled >= 0f && doing.action() == ArmActs.LEVER) {
            // The hand in the lever's loop swings it down and forward about its pin and back, turning with it. Turned the other way
            // about the pin, the hand went up over the receiver and asked its forearm to turn 155 to 180 degrees.
            float swung = OwnLever.swung(cycled) * ArmActs.LEVER_SWINGS;
            // About the lever's own pin where the mesh says where it is, else about the front of the guard.
            float[] pin = Float.isNaN(placed.pinAlong()) ? placed.at(shape.guardFront, shape.guardTop, 0f) : placed.at(placed.pinAlong(), placed.pinUp(), 0f);
            float[] turn = new float[9], arm = new float[3], turnedArm = new float[3];
            ArmPose.about(axes.right, swung, turn);
            for (int axis = 0; axis < 3; axis++) {
                arm[axis] = hold.at[axis] - pin[axis];
            }
            ArmPose.turned(turn, arm, turnedArm);
            for (int axis = 0; axis < 3; axis++) {
                hold.at[axis] = pin[axis] + turnedArm[axis];
            }
            ArmPose.turned(turn, hold.across.clone(), hold.across);
            ArmPose.turned(turn, hold.out.clone(), hold.out);
        }
        return hold;
    }

    /**
     * The right hand on a charging handle while the game racks the gun: it reaches the handle,
     * draws it straight back as a slide is racked, lets it fly home and goes back to the grip,
     * all as shares of how long the game takes over it, so that a short rack ends as cleanly as
     * a long one.
     */
    private static void onChargingHandle(ArmModel model, Placed placed, Axes axes, Doing doing, Hold hold) {
        WeaponShape shape = placed.shape();
        // A charging handle: the hand reaches it, draws it straight back as a slide is racked, lets it fly home and
        // goes back to the grip. One out on the right is taken as a bolt's knob is; one on top from above, the palm
        // forward and the fingers across to the left.
        float through = doing.done() / Math.max(doing.pace(), ArmActs.SHORTEST);
        float on = between(through, 0f, 0.3f) - between(through, 0.6f, 0.95f);
        float drawn = through < SLIDE_LET_GO ? slideRacked(through) : 1f;
        float[] grab = placed.handle(), drawnTo = new float[3];
        for (int axis = 0; axis < 3; axis++) {
            drawnTo[axis] = grab[axis] - shape.along[axis] * placed.travel() * drawn;
        }
        float[] at = placed.at(drawnTo);
        boolean outside = Math.abs(ArmPose.dot(grab, shape.side)) > HANDLE_OUTSIDE;
        Hold onHandle = outside ? new Hold(model).set(at, axes.way(0f, 0.3f, -1f), axes.way(0.7f, 0.6f, -0.3f), false, model.pose("knob"))
                : new Hold(model).set(at, axes.way(0.6f, -0.8f, 0f), axes.way(0f, -0.2f, -1f), false, model.pose("knob"));
        hold.towards(onHandle, on);
    }

    /** The left hand, or null when it is not on the weapon nor busy with it. */
    private static Hold left(ArmModel model, Weapon weapon, Axes axes, Doing doing, float[] fromAim) {
        Hold hold = null;
        if (doing.both() && !weapon.firearm()) {
            // Something swung in both hands: the left fist is on the handle under the right, the same way round.
            float slant = slant(weapon, axes, axes.at(weapon.second(), 0f, 0f), 1, doing.turned(), fromAim);
            float cos = (float) Math.cos(slant), sin = (float) Math.sin(slant);
            float[] fingers = new float[3], slanted = new float[3];
            ArmPose.cross(axes.ahead, axes.right, fingers);
            for (int axis = 0; axis < 3; axis++) {
                slanted[axis] = fingers[axis] * cos + axes.ahead[axis] * sin;
            }
            Hold fist = new Hold(model).set(axes.at(weapon.second(), 0f, 0f), axes.right, slanted, true, model.pose("handle"));
            fist.round = true;
            return fist;
        }
        if (!weapon.firearm()) {
            return null;
        }
        Placed placed = weapon.placed();
        WeaponShape shape = placed.shape();
        if (doing.both() && !weapon.handgun()) {
            hold = foreEnd(model, weapon, axes, doing, fromAim);
        } else if (doing.both()) {
            hold = round(model, weapon, axes);
        }
        boolean seating = doing.seated() >= 0f && doing.doing() != ArmActs.LOADING;
        if (doing.doing() == ArmActs.HOLDING && !seating) {
            return hold;
        }
        float[] off = new float[3];
        ViewModelMath.place(fromAim, AWAY[0] * axes.scale, AWAY[1] * axes.scale, AWAY[2] * axes.scale, off);
        // Off the weapon the hand is closed on what it fetches or takes away, or about to be, its palm towards the body's middle and
        // a little up, its fingers forward. Palm up, it asked its forearm to turn 105 to 123 degrees.
        int action = doing.action();
        boolean magazine = action == ArmActs.HANDGUN || action == ArmActs.BOLT;
        Hold away = new Hold(model).set(off, axes.way(0f, 0.3f, 1f), axes.way(1f, 0.2f, 0f), true,
                model.pose(doing.doing() == ArmActs.RACKING ? "open" : magazine ? "magazine" : "pinch"));
        // Where the hand is when this is over: back on the weapon, or off it.
        Hold rest = hold != null ? hold : away;
        // How far through what the game is doing, by how long it is taken to take: 1 when it should be over.
        float done = doing.done() / Math.max(doing.pace(), ArmActs.SHORTEST);
        Hold now = new Hold(model);
        // A magazine is held by its foot and goes in and comes out along its own line: a handgun's up its grip, another's up into the receiver.
        float[] well = new float[3], down = new float[3];
        if (action == ArmActs.HANDGUN) {
            float[] line = placed.way(shape.gripLine);
            for (int axis = 0; axis < 3; axis++) {
                down[axis] = -line[axis];
            }
            System.arraycopy(along(placed.at(shape.gripFoot), down, HANDGUN_MAGAZINE_UNDER * axes.scale), 0, well, 0, 3);
        } else {
            System.arraycopy(placed.at(magazineAlong(shape), magazineUp(shape), 0f), 0, well, 0, 3);
            for (int axis = 0; axis < 3; axis++) {
                down[axis] = -axes.up[axis];
            }
        }
        // The fist's knuckles lie up the magazine's front edge, which a curved one leans back.
        float[] fingers = axes.way(1f, magazineLeans(shape, action), 0f);
        if (seating) {
            // The game has the magazine in: the hand, which has been holding it under the well, pushes it home and lets go.
            float under = (1f - between(doing.seated(), 0f, ArmActs.HOME_IN)) * ArmActs.MAGAZINE_DRAWN;
            Hold onMagazine = new Hold(model).set(along(well, down, under * axes.scale), axes.way(0f, 0f, 1f), fingers, true, model.pose("magazine"));
            return copy(now, onMagazine).towards(rest, between(doing.seated(), 0.3f, ArmActs.SEATED_FOR));
        }
        if (doing.doing() == ArmActs.RACKING) {
            if (action != ArmActs.HANDGUN) {
                // A pump's fore-end is slid by the hand that is on it, and a bolt is the right hand's.
                return hold;
            }
            // Over the top of the slide from behind and to the left, drawn back with it, let go.
            float back = slideRacked(done) * placed.travel();
            Hold onSlide = new Hold(model).set(along(along(placed.at(shape.slide), axes.ahead, -back * axes.scale), axes.up, SLIDE_ABOVE * axes.scale),
                    axes.way(0f, -1f, 0.1f), axes.way(0.3f, 0.15f, 0.94f), true, model.pose("slide"));
            float on = between(done, 0f, 0.3f) - between(done, 0.6f, 0.95f);
            // By way of the slide's left, turned to it there and brought in: turned on the way straight to it, the hand went through the frame.
            Hold beside = copy(new Hold(model), onSlide);
            System.arraycopy(along(along(onSlide.at, axes.right, -SLIDE_ROUND * axes.scale), axes.up, SLIDE_ROUND / 2f * axes.scale), 0, beside.at, 0, 3);
            return on < SLIDE_BESIDE ? copy(now, rest).towards(beside, on / SLIDE_BESIDE) : copy(now, beside).towards(onSlide, (on - SLIDE_BESIDE) / (1f - SLIDE_BESIDE));
        }
        if (magazine) {
            // The magazine is held as a handle is, its top out of the thumb's side of the fist. Where it is, the game says of the weapon.
            if (doing.clip()) {
                // In the weapon: the hand goes to it and takes hold of it, to draw it out when the game lets it go.
                Hold inWell = new Hold(model).set(well, axes.way(0f, 0f, 1f), fingers, true, model.pose("magazine"));
                return copy(now, rest).towards(inWell, between(doing.done(), 0f, 0.3f));
            }
            float out = doing.out();
            if (out >= 0f && (out < ArmActs.TAKEN_IN || doing.fetching() < 0f)) {
                // Out of the weapon: drawn clear of the well and taken away below, and the hand back to where it rests if no other is to come.
                float drawn = between(out, 0f, ArmActs.DRAWN_CLEAR_IN) * ArmActs.MAGAZINE_DRAWN;
                Hold clear = new Hold(model).set(along(well, down, drawn * axes.scale), axes.way(0f, 0f, 1f), fingers, true, model.pose("magazine"));
                copy(now, clear).towards(away, between(out, ArmActs.DRAWN_CLEAR_IN, ArmActs.TAKEN_IN));
                return now.towards(rest, between(out, ArmActs.TAKEN_IN, ArmActs.TAKEN_IN + 0.3f));
            }
            // Another is brought up under the well, to be there well before the game is done, which may be sooner than it was last time,
            // and held there until it is: see `seating`.
            float began = out >= 0f ? ArmActs.TAKEN_IN : 0f;
            float through = (doing.fetching() - began) / Math.max(doing.fetchTakes() - began, ArmActs.SHORTEST);
            Hold under = new Hold(model).set(along(well, down, ArmActs.MAGAZINE_DRAWN * axes.scale), axes.way(0f, 0f, 1f), fingers, true,
                    model.pose("magazine"));
            return copy(now, away).towards(under, between(through, 0.15f, 0.7f));
        }
        if (doing.doing() == ArmActs.UNLOADING) {
            return hold;
        }
        // One round at a time: fetched from below, brought to the port under the palm's thumb, pushed in.
        float round = doing.round();
        float pushed = between(round, 0.45f, 0.6f) * ArmActs.ROUND_PUSHED;
        float[] port = port(placed, action), from = loadedFrom(action);
        Hold atPort = new Hold(model).set(along(along(along(port, axes.ahead, (pushed + from[0]) * axes.scale), axes.up, from[1] * axes.scale), axes.right, from[2] * axes.scale),
                axes.way(from[3], from[4], from[5]), axes.way(from[6], from[7], from[8]), true, model.pose("pinch"));
        float on = between(round, 0f, 0.42f) - between(round, 0.62f, 1f);
        return copy(now, away).towards(atPort, on);
    }

    /**
     * Where the hand that loads a round is from the port, in metres ahead, up and to the right,
     * then the way out of its palm and the way its fingers run, each in parts of ahead, up and to
     * the right. It is outside the gun on the side the port opens to, its palm towards it: under a
     * pump's port and to its left, palm up; to the right of a lever gun's gate, which is in the
     * right of its receiver; over a bolt gun's opening or a double's open breech; beside a
     * revolver's cylinder on its left, where 5 mm nearer the middle finger went 6 to 7 mm into
     * it. Every long gun's was beside the receiver on its left, palm to the right, and went 11
     * to 19 mm into it: through a lever gun's receiver to the gate,
     * into the top of a bolt gun's and a double's breech, and under a pump's port the fingers,
     * curled towards a palm facing straight up, went up into the receiver.
     */
    private static float[] loadedFrom(int action) {
        return switch (action) {
            case ArmActs.PUMP -> LOADS_UNDER;
            case ArmActs.LEVER -> LOADS_RIGHT;
            case ArmActs.BOLT_SINGLE, ArmActs.DOUBLE, ArmActs.DOUBLE_SAWN -> LOADS_OVER;
            default -> LOADS_LEFT;
        };
    }

    static final float[] LOADS_UNDER = {-0.05f, -0.045f, -0.03f, -0.2f, 1f, 0.6f, 1f, -0.2f, 0.4f},
            LOADS_RIGHT = {-0.03f, -0.01f, 0.03f, -0.1f, 1f, -0.25f, 1f, 0f, 0f},
            LOADS_OVER = {-0.03f, 0.05f, -0.03f, -0.2f, -1f, 0.4f, 1f, 0f, 0f},
            LOADS_LEFT = {-0.03f, 0f, -0.045f, 0.1f, 0.2f, 1f, 1f, 0.3f, 0f};

    /** A place moved a way by a distance. */
    private static float[] along(float[] from, float[] way, float by) {
        return new float[] {from[0] + way[0] * by, from[1] + way[1] * by, from[2] + way[2] * by};
    }

    /** Where rounds go into a firearm loaded a round at a time, in the view. */
    private static float[] port(Placed placed, int action) {
        WeaponShape shape = placed.shape();
        float metre = shape.perMetre;
        return switch (action) {
            case ArmActs.PUMP -> {
                float a = shape.guardFront + PUMP_PORT * metre;
                yield placed.at(a, shape.under(a), 0f);
            }
            case ArmActs.BOLT_SINGLE -> placed.at(ArmPose.dot(shape.knob, shape.along) + BOLT_PORT * metre, shape.bore, 0f);
            case ArmActs.REVOLVER -> placed.port() != null ? placed.at(placed.port())
                    : placed.at(shape.guardFront - (shape.guardFront - shape.guardBack) * CYLINDER_BACK, shape.bore - CYLINDER_UNDER * metre, 0f);
            // A lever gun's rounds go in by the gate in the side of its receiver, and a double's at its open chamber with the barrels
            // down, where its model has them.
            case ArmActs.LEVER, ArmActs.DOUBLE, ArmActs.DOUBLE_SAWN -> placed.port() != null ? placed.at(placed.port()) : placed.at(shape.guardFront + BREECH * metre, shape.bore, 0f);
            default -> placed.at(shape.guardFront + BREECH * metre, shape.bore, 0f);
        };
    }

    /**
     * The left hand under a long gun's fore-end, where it rests on the fore-end or as far out
     * along it as the arm reaches, and with the fore-end of a pump drawn back by as much.
     */
    private static Hold foreEnd(ArmModel model, Weapon weapon, Axes axes, Doing doing, float[] fromAim) {
        Placed placed = weapon.placed();
        float rests = placed.foreRests(doing.action());
        Hold hold = new Hold(model);
        // The hand is turned to its arm and brought back to where the arm reaches. The underside
        // it is then under may be higher or lower, so that is done again from there, and the
        // hand turned to the arm it ends with.
        for (int pass = 0; pass < REACH_PASSES; pass++) {
            float a = rests - placed.slid();
            System.arraycopy(placed.at(a, placed.overUnder(a), 0f), 0, hold.at, 0, 3);
            underForeEnd(model, weapon, axes, doing.aim(), hold, fromAim);
            float back = withinReach(model, weapon, axes, doing.aim(), hold, fromAim, placed.stillAt(rests, placed.overUnder(rests), 0f)) / placed.unit();
            if (back <= 0f) {
                break;
            }
            rests -= back;
            if (pass == REACH_PASSES - 1) {
                a = rests - placed.slid();
                System.arraycopy(placed.at(a, placed.overUnder(a), 0f), 0, hold.at, 0, 3);
                underForeEnd(model, weapon, axes, doing.aim(), hold, fromAim);
            }
        }
        return hold;
    }

    /**
     * The left hand round the right on a handgun's grip, as a handgun is held in both: its palm
     * on the side of the grip the right palm leaves bare, its fingers over the right hand's
     * under the guard, its thumb along the frame towards the muzzle.
     */
    private static Hold round(ArmModel model, Weapon weapon, Axes axes) {
        Placed placed = weapon.placed();
        WeaponShape shape = placed.shape();
        float[] line = placed.way(shape.gripLine), ahead = new float[3];
        float along = ArmPose.dot(axes.ahead, line);
        ArmPose.unit(axes.ahead[0] - line[0] * along, axes.ahead[1] - line[1] * along, axes.ahead[2] - line[2] * along, ahead);
        float thick = shape.gripThick * placed.unit();
        Hold hold = new Hold(model);
        for (int axis = 0; axis < 3; axis++) {
            hold.at[axis] = weapon.grip()[axis] + ahead[axis] * thick * SUPPORT_AHEAD - axes.right[axis] * SUPPORT_ASIDE * axes.scale;
        }
        ArmPose.unit(axes.right[0] + ahead[0] * SUPPORT_PALM_AHEAD, axes.right[1] + ahead[1] * SUPPORT_PALM_AHEAD, axes.right[2] + ahead[2] * SUPPORT_PALM_AHEAD, hold.out);
        aslant(line, hold.out, ahead, SUPPORT_LEANS, hold.across);
        // Every finger round the right hand's, none along the frame: closed as a fist round a handle they went 13 to 19 mm into the grip.
        hold.weights[model.pose("pistol support")] = 1f;
        return hold;
    }

    /**
     * The line across a hand round a grip: the grip's line laid in the palm, leaning back by
     * `lean` from there within the palm, its top away from `front`. Leant across the palm it
     * would have the palm turned and tipped when the hand is laid along it.
     */
    private static void aslant(float[] line, float[] palm, float[] front, float lean, float[] across) {
        float[] laid = new float[3], back = new float[3];
        float into = ArmPose.dot(line, palm);
        ArmPose.unit(line[0] - palm[0] * into, line[1] - palm[1] * into, line[2] - palm[2] * into, laid);
        ArmPose.cross(palm, laid, back);
        float way = ArmPose.dot(back, front) > 0f ? -1f : 1f;
        float cos = (float) Math.cos(lean), sin = (float) Math.sin(lean) * way;
        for (int axis = 0; axis < 3; axis++) {
            across[axis] = laid[axis] * cos + back[axis] * sin;
        }
    }

    /** Lays the left hand under a fore-end, where the hold is, its fingers on along the arm that comes to it from its shoulder. */
    private static void underForeEnd(ArmModel model, Weapon weapon, Axes axes, float aim, Hold hold, float[] fromAim) {
        float[] shoulder = new float[3], from = new float[3], arm = new float[3], palm = new float[3], fingers = new float[3];
        starts(weapon, 1, axes.scale, 0f, aim, fromAim, shoulder, from);
        ArmPose.unit(hold.at[0] - shoulder[0], hold.at[1] - shoulder[1], hold.at[2] - shoulder[2], arm);
        float cos = (float) Math.cos(SUPPORT_PALM_TURN), sin = (float) Math.sin(SUPPORT_PALM_TURN);
        for (int axis = 0; axis < 3; axis++) {
            palm[axis] = axes.up[axis] * cos + axes.right[axis] * sin;
        }
        float into = ArmPose.dot(arm, palm);
        ArmPose.unit(arm[0] - palm[0] * into, arm[1] - palm[1] * into, arm[2] - palm[2] * into, fingers);
        hold.set(hold.at.clone(), palm, fingers, true, model.pose("fore"));
    }

    /**
     * How far back along the barrel the hand under a fore-end has to come for its arm to reach
     * it easily, in the view's lengths, and no nearer the grip than NEAREST_FORE. A hand left
     * further out than the arm is long has the shoulder brought out after it, into the
     * picture. It is settled by where the fore-end is with the weapon held still, `steady`:
     * the hand has one place on the fore-end and goes with it as a shot kicks the weapon, a
     * step sways it and a pump's fore-end is drawn back.
     */
    private static float withinReach(ArmModel model, Weapon weapon, Axes axes, float aim, Hold hold, float[] fromAim, float[] steady) {
        float[] shoulder = new float[3], from = new float[3], wrist = new float[3];
        starts(weapon, 1, axes.scale, 0f, aim, fromAim, shoulder, from);
        if (!ArmPose.wrist(model, axes.scale, hold.weights, hold.at, hold.across, hold.out, from, true, hold.round, wrist)) {
            return 0f;
        }
        float reach = ArmPose.whole(model) * axes.scale * REACH_EASY;
        float[] off = {wrist[0] + steady[0] - hold.at[0] - shoulder[0], wrist[1] + steady[1] - hold.at[1] - shoulder[1], wrist[2] + steady[2] - hold.at[2] - shoulder[2]};
        float along = ArmPose.dot(off, axes.ahead), over = ArmPose.dot(off, off) - reach * reach;
        if (over <= 0f) {
            return 0f;
        }
        // Back to where the wrist is within reach, or if it is nowhere on the barrel, to where it is nearest.
        float within = along * along - over;
        float back = within > 0f ? along - (float) Math.sqrt(within) : along;
        float[] grip = weapon.grip();
        float spare = (hold.at[0] - grip[0]) * axes.ahead[0] + (hold.at[1] - grip[1]) * axes.ahead[1] + (hold.at[2] - grip[2]) * axes.ahead[2] - NEAREST_FORE * axes.scale;
        return Math.clamp(back, 0f, Math.max(spare, 0f));
    }

    /** How far aslant a handle lies in the fist that holds it at a place, by how nearly it points along the arm that comes to it there. */
    private static float slant(Weapon weapon, Axes axes, float[] at, int side, float turned, float[] fromAim) {
        float[] shoulder = new float[3], from = new float[3], arm = new float[3];
        starts(weapon, side, axes.scale, turned, 0f, fromAim, shoulder, from);
        if (!ArmPose.unit(at[0] - from[0], at[1] - from[1], at[2] - from[2], arm)) {
            return HANDLE_SLANT;
        }
        return Math.clamp((float) (Math.PI / 2 - Math.acos(Math.clamp(ArmPose.dot(arm, axes.ahead), -1f, 1f))), HANDLE_SLANT, HANDLE_SLANT_MOST);
    }

    /**
     * Places both arms in a shove or a stamp with no weapon in their hands.
     *
     * @param hands   where each hand is, right then left, as BareHands.hand gives it
     * @param poses   each hand's pose in the model, right then left
     * @param fromAim the matrix from the aim's axes, which the hands are given in, to the view's
     * @return false if an arm cannot be placed
     */
    static boolean bare(ArmModel model, float scale, float[][] hands, int[] poses, float[] fromAim, float[][] skins, float[][] said) {
        Weapon none = new Weapon(false, false, null, null, null, null);
        float[] at = new float[3], palm = new float[3], fingers = new float[3];
        for (int side = 0; side < 2; side++) {
            float[] hand = hands[side];
            ViewModelMath.place(fromAim, hand[0] * scale, hand[1] * scale, hand[2] * scale, at);
            ViewModelMath.turned(fromAim, new float[] {hand[3], hand[4], hand[5]}, palm);
            ViewModelMath.turned(fromAim, new float[] {hand[6], hand[7], hand[8]}, fingers);
            Hold hold = new Hold(model).set(at, palm, fingers, side == 1, poses[side]);
            if (!solve(model, none, scale, 0f, 0f, hold, fromAim, side, skins, said)) {
                return false;
            }
        }
        return true;
    }

    /** Where an arm on this weapon starts and where its forearm is to seem to come from, in the view, with the weapon raised `aim` of the way to the sights. */
    private static void starts(Weapon weapon, int side, float scale, float turned, float aim, float[] fromAim, float[] shoulder, float[] from) {
        starts(weapon.firearm() && !weapon.handgun(), side, scale, turned, aim, fromAim, shoulder, from);
        if (side == 1 && weapon.spear()) {
            turnedPlace(SPEAR_SHOULDER, turned, scale, fromAim, shoulder);
            turnedPlace(SPEAR_FROM, turned, scale, fromAim, from);
        }
    }

    /**
     * Where the left arm's shoulder is, and where its forearm seems to come from, to a spear's shaft out ahead of the right
     * hand, in metres from the eye: both well under the picture, as a body lunging leans, in every way a spear is held, at
     * rest, thrust, stabbed down and shoved, where the left wrist is bent 14 to 28 degrees and the arm rises to the shaft
     * from the foot of the picture. From the shoulder beside the
     * eye and FROM's corner, the upper arm came across the lower left of the picture as the spear was thrust, cut off by
     * the near plane, and the shirt's sleeve on it showed as a dark flap.
     */
    static final float[] SPEAR_SHOULDER = {-0.22f, -0.60f, 0.10f}, SPEAR_FROM = {-0.28f, -0.68f, -0.10f};

    private static void starts(boolean longGun, int side, float scale, float turned, float aim, float[] fromAim, float[] shoulder, float[] from) {
        float sign = side == 1 ? -1f : 1f;
        float[] at = longGun ? longShoulder(side, aim) : new float[] {SHOULDER[0] * sign, SHOULDER[1], SHOULDER[2]};
        turnedPlace(at, turned, scale, fromAim, shoulder);
        turnedPlace(longGun && side == 1 ? LONG_FROM : FROM[side], turned, scale, fromAim, from);
    }

    /** Where a shoulder behind a long gun is, the right's then the left's, in metres from the eye, with the gun raised `aim` of the way to the sights. */
    static float[] longShoulder(int side, float aim) {
        if (side == 0) {
            return LONG_SHOULDERS[0];
        }
        float raised = ViewModelMath.smooth(aim);
        float[] at = new float[3];
        for (int axis = 0; axis < 3; axis++) {
            at[axis] = LONG_SHOULDERS[1][axis] + (LONG_LEFT_SHOULDER_AIMED[axis] - LONG_SHOULDERS[1][axis]) * raised;
        }
        return at;
    }

    /** A place of the body's, in metres from the eye, with the body turned to the right about its spine, in the view. */
    private static void turnedPlace(float[] at, float turned, float scale, float[] fromAim, float[] out) {
        float cos = (float) Math.cos(turned), sin = (float) Math.sin(turned);
        float x = at[0], z = at[2] - SPINE_BEHIND;
        ViewModelMath.place(fromAim, (x * cos - z * sin) * scale, at[1] * scale, (SPINE_BEHIND + x * sin + z * cos) * scale, out);
    }

    private static Hold copy(Hold into, Hold from) {
        System.arraycopy(from.at, 0, into.at, 0, 3);
        System.arraycopy(from.across, 0, into.across, 0, 3);
        System.arraycopy(from.out, 0, into.out, 0, 3);
        System.arraycopy(from.weights, 0, into.weights, 0, from.weights.length);
        into.round = from.round;
        return into;
    }

    /**
     * How many times a hand that asks its forearm to turn further than a forearm turns is turned
     * back and placed again, each time its elbow may go elsewhere, and how far short of what a
     * forearm turns it is turned back to, in radians, so that where the elbow then goes does not
     * take it over again.
     */
    static final int YIELD_PASSES = 3;
    static final float YIELD_SPARE = (float) Math.toRadians(2);

    /**
     * Places an arm on a hold. A hand that asks its forearm to turn further than a forearm turns
     * is turned back about the line its own forearm would run along by as much as it asks over,
     * as a hand shifts its grip on a weapon tipped over in it: the upper arm is not rolled to make
     * up the rest, and the wrist is bent no further for it. The hand under a fore-end keeps its
     * palm square under it, as a fore-end lies in the palm.
     */
    private static boolean solve(ArmModel model, Weapon weapon, float scale, float turned, float aim, Hold hold, float[] fromAim, int side, float[][] skins, float[][] said) {
        float[] shoulder = new float[3], from = new float[3];
        starts(weapon, side, scale, turned, aim, fromAim, shoulder, from);
        float[] turn = new float[9];
        for (int pass = 0; ; pass++) {
            if (!ArmPose.solve(model, scale, hold.weights, hold.at, hold.across, hold.out, shoulder, from, side == 1, hold.round, skins[side], said[side])) {
                return false;
            }
            float asked = said[side][ArmPose.ASKED];
            if (Math.abs(asked) <= ArmPose.TWIST_MOST || pass == YIELD_PASSES || hold.weights[model.pose("fore")] > 0.5f) {
                return true;
            }
            float[] held = {said[side][ArmPose.HELD], said[side][ArmPose.HELD + 1], said[side][ArmPose.HELD + 2]};
            // The left arm is solved as the right one in a mirror, which turns the other way about the same line.
            ArmPose.about(held, (Math.signum(asked) * (ArmPose.TWIST_MOST - YIELD_SPARE) - asked) * (side == 1 ? -1f : 1f), turn);
            ArmPose.turned(turn, hold.across.clone(), hold.across);
            ArmPose.turned(turn, hold.out.clone(), hold.out);
        }
    }
}
