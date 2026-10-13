package com.cameraunlock.zomboid;

/**
 * The mod's own models of the revolvers and of the toy cap pistol, drawn in the viewer's hands in
 * place of the game's: meshes, their colour and the shape and finish of their surfaces, made by
 * tools/models/revolver.py and kept in the jar (OwnGun says how each is held, lit and drawn).
 *
 * The game says how many rounds are in the cylinder, how many fired ones, and when the holder is
 * loading or unloading it, and the cylinder is worked from that, as OwnShotgun works the pump:
 * on each shot the hammer falls, the trigger comes forward again and the cylinder turns one
 * chamber; to be loaded the cylinder swings out on its crane, the ejector throws out what was
 * fired, the rounds go in one by one as the game puts them in, and it swings shut again.
 *
 * All of it is the render thread's.
 */
final class OwnRevolver extends OwnGun {
    /** The parts the file's layout names. */
    private static final int FIXED = 0, TRIGGER = 1, HAMMER = 2, CYLINDER = 3, CRANE = 4, EJECTOR = 5, ROUND = 6, CASE = 7;
    static final int PARTS = 8;
    /** Where each of the numbers that say how the parts move is in the file's list of them: see tools/models/revolver.py. */
    static final int HAMMER_Y = 0, HAMMER_Z = 1, COCKED = 2, TRIGGER_Y = 3, TRIGGER_Z = 4, PULLED = 5, AXIS_X = 6, AXIS_Z = 7, TURN = 8, CHAMBERS = 9,
            CRANE_X = 10, CRANE_Z = 11, SWING = 12, PUSHED = 13, CHAMBER_X = 14, CHAMBER_Y = 15, CHAMBER_Z = 16;
    /** How fast what the ejector pushes out leaves the chambers, in metres a second of the model, along their length. */
    private static final float THROWN_OUT = 0.9f;

    /** Fired cases and rounds taken out: pushed out of the back of the cylinder, falling and turning slowly. */
    private final Thrown cases = new Thrown(Cylinder.MOST, 0.9f, 0f, 0f, 0f, 7.5f, 6f);
    private final Thrown rounds = new Thrown(Cylinder.MOST, 0.9f, 0f, 0f, 0f, 7.5f, 6f);
    final Cylinder cylinder = new Cylinder();
    private final Places places = new Places();
    private final float[] part = new float[16], at = new float[3], port = new float[3];
    private final Cylinder.Throws thrower = this::thrown;
    /** The camera the cylinder was last worked with, for what it throws out. */
    private ViewCamera.Pose workedFrom;

    OwnRevolver(View view) {
        // Oiled walnut grips, and with the arms out the head is far from what the eye sees of them.
        super(view, 0.45f, 0.30f, 0f);
    }

    @Override
    void work(ViewCamera.Pose camera, float seconds) {
        ViewModel.Held held = view.viewModel.held;
        workedFrom = camera;
        if (held != null) {
            cylinder.step(held.itemId(), (int) drawn.moves[CHAMBERS], held.rounds(), held.spent(), held.doing(), seconds, thrower);
        }
        cases.fly(camera, seconds);
        rounds.fly(camera, seconds);
    }

    /** What the ejector pushes out of the chamber at a place in the order they fire, as it leaves. */
    private void thrown(int place, boolean live) {
        places.chamber(drawn.moves, cylinder, place, part);
        ViewModelMath.place(part, 0f, 0f, 0f, at);
        (live ? rounds : cases).outAlong(workedFrom, model, at[0], at[1], at[2], 0f, -THROWN_OUT, 0f);
    }

    /**
     * Where the next round goes in, in the model's axes: the back of the chamber the game fills
     * next, wherever the cylinder is, from the moment it begins to swing out. Null while it is
     * shut, or full. The array is this one's and is filled again on the next call.
     */
    float[] loadingPort(OwnModel own) {
        return places.port(own.moves, cylinder, port) ? port : null;
    }

    @Override
    void parts(Program drawing) {
        float[] moves = drawn.moves;
        part(FIXED);
        turnAbout(part, 0f, moves[HAMMER_Y], moves[HAMMER_Z], 1f, 0f, 0f, moves[COCKED] * cylinder.hammer());
        draw(drawing, part, HAMMER);
        turnAbout(part, 0f, moves[TRIGGER_Y], moves[TRIGGER_Z], 1f, 0f, 0f, moves[PULLED] * cylinder.trigger());
        draw(drawing, part, TRIGGER);
        places.crane(moves, cylinder, part);
        draw(drawing, part, CRANE);
        places.spun(moves, cylinder, 0f, part);
        draw(drawing, part, CYLINDER);
        places.ejector(moves, cylinder, part);
        draw(drawing, part, EJECTOR);
        int chambers = (int) moves[CHAMBERS];
        for (int place = 0; place < chambers; place++) {
            int holds = cylinder.holds(place);
            if (holds != Cylinder.EMPTY) {
                places.chamber(moves, cylinder, place, part);
                draw(drawing, part, holds == Cylinder.LIVE ? ROUND : CASE);
            }
        }
        drawing.set("uPart", Characters.IDENTITY);
        cases.draw(this, drawing, CASE);
        rounds.draw(this, drawing, ROUND);
    }

    /** Draws a part moved by a matrix in the model's own axes. */
    private void draw(Program drawing, float[] moved, int which) {
        drawing.set("uPart", moved);
        part(which);
    }

    /** Where the moving parts are, from a model's moves and how the cylinder stands, as uPart takes them. Pure but for its scratch. */
    static final class Places {
        private final float[] swung = new float[16], turned = new float[16], slid = new float[16], spun = new float[16], placed = new float[16];

        /** The crane swung as far out as it is now, about its pin. */
        void crane(float[] moves, Cylinder cylinder, float[] out) {
            turnAbout(out, moves[CRANE_X], 0f, moves[CRANE_Z], 0f, 1f, 0f, moves[SWING] * cylinder.swing());
        }

        /** The cylinder as it is now, swung out with the crane and turned, and turned on by `more` from that. */
        void spun(float[] moves, Cylinder cylinder, float more, float[] out) {
            crane(moves, cylinder, swung);
            turnAbout(turned, moves[AXIS_X], 0f, moves[AXIS_Z], 0f, 1f, 0f, moves[TURN] * cylinder.turned() + more);
            ArmPose.multiply(swung, 0, turned, 0, out, 0);
        }

        /** The star and the rod: turned with the cylinder and pushed back out of it as far as the ejector is. */
        void ejector(float[] moves, Cylinder cylinder, float[] out) {
            spun(moves, cylinder, 0f, spun);
            moved(0f, -moves[PUSHED] * cylinder.pushed(), 0f, slid);
            ArmPose.multiply(spun, 0, slid, 0, out, 0);
        }

        /** What is in the chamber at a place in the order they fire, as it is now: its rim's back face at the origin, along y, and as far out as the ejector has pushed it. */
        void chamber(float[] moves, Cylinder cylinder, int place, float[] out) {
            spun(moves, cylinder, -moves[TURN] * place, spun);
            moved(moves[CHAMBER_X], moves[CHAMBER_Y] - moves[PUSHED] * cylinder.pushed(), moves[CHAMBER_Z], slid);
            ArmPose.multiply(spun, 0, slid, 0, out, 0);
        }

        /** Fills out with where the next round goes in, in the model's axes, and says whether there is one: not while shut or full. */
        boolean port(float[] moves, Cylinder cylinder, float[] out) {
            if (!cylinder.opening() || cylinder.loading() >= cylinder.chambers()) {
                return false;
            }
            chamber(moves, cylinder, cylinder.loading(), placed);
            ViewModelMath.place(placed, 0f, 0f, 0f, out);
            return true;
        }

        private static void moved(float x, float y, float z, float[] out) {
            java.util.Arrays.fill(out, 0f);
            out[0] = 1f;
            out[5] = 1f;
            out[10] = 1f;
            out[15] = 1f;
            out[12] = x;
            out[13] = y;
            out[14] = z;
        }
    }

    /**
     * What is in the cylinder and how its parts stand, worked out from what the game says of the
     * revolver frame by frame. Pure.
     *
     * The chambers are counted in the order they fire: the one under the hammer first. The game
     * fires the rounds in the cylinder one after another, so the live ones are the first and the
     * ones fired since it was last emptied are the last, the most recent last of all. A fired case
     * stays in its chamber until the ejector throws it out, whenever the game stops counting it.
     */
    static final class Cylinder {
        /** What a chamber holds. */
        static final int EMPTY = 0, LIVE = 1, SPENT = 2;
        /** The most chambers a cylinder has: the cap gun's eight. */
        static final int MOST = 8;
        /** After a shot: the hammer is down in this long, the cylinder turns over these, and the trigger comes forward over these, in seconds. */
        static final float HAMMER_FALLS = 0.03f, TURN_FROM = 0.02f, TURN_TAKES = 0.08f, TRIGGER_FROM = 0.06f, TRIGGER_TAKES = 0.14f;
        /** The cylinder swings out or in over this long, in seconds. */
        static final float SWING_TAKES = 0.22f;
        /** After it begins to swing out the ejector is pushed home over these, held, and let back over these, in seconds: what it pushes out is thrown at the end of the push. */
        static final float PUSH_FROM = 0.30f, PUSH_TAKES = 0.12f, PUSH_HELD = 0.08f, PUSH_BACK = 0.12f;
        private static final float NEVER = -1f;
        private static final int NOTHING_HELD = Integer.MIN_VALUE;

        /** Called with a chamber's place in the order they fire and whether what it throws out is a live round. */
        interface Throws {
            void out(int place, boolean live);
        }

        private int held = NOTHING_HELD, chambers = 6, rounds, spent, spentSaid;
        private float sinceShot = NEVER, swung, opened = NEVER;
        private boolean open, pushedOut;

        /**
         * Moves it on by a frame.
         *
         * @param item     the game's id of the revolver held: another, even of the same kind, is read afresh
         * @param chambers how many chambers its cylinder has
         * @param loaded   how many rounds the game says it holds
         * @param fired    how many of them the game says are fired: ISReloadWeaponAction.onShoot adds one with each shot, and a reload clears them
         * @param doing    what the holder is doing to it: one of ArmActs' doings
         * @param seconds  how long the frame was
         * @param throwing told of each thing the ejector throws out
         */
        void step(int item, int chambers, int loaded, int fired, int doing, float seconds, Throws throwing) {
            boolean opening = doing == ArmActs.LOADING || doing == ArmActs.UNLOADING;
            if (item != held) {
                // Taken in hand: as the game has it, fired cases and all.
                held = item;
                this.chambers = chambers;
                spent = Math.clamp(fired, 0, chambers);
                spentSaid = fired;
                rounds = live(loaded, spent);
                sinceShot = NEVER;
                open = opening;
                swung = opening ? 1f : 0f;
                opened = opening ? PUSH_FROM + PUSH_TAKES : NEVER;
                pushedOut = opening;
                return;
            }
            if (!open && !opening && fired > spentSaid) {
                // Fired: the chamber under the hammer is spent, and the next turns up under it. A round taken out with no shot is not one.
                spent = Math.min(spent + fired - spentSaid, chambers);
                sinceShot = 0f;
            } else if (sinceShot >= 0f) {
                sinceShot += seconds;
                if (sinceShot > TRIGGER_FROM + TRIGGER_TAKES) {
                    sinceShot = NEVER;
                }
            }
            spentSaid = fired;
            if (opening && !open) {
                opened = 0f;
                pushedOut = false;
                sinceShot = NEVER;
            }
            open = opening;
            swung = Math.clamp(swung + (open ? seconds : -seconds) / SWING_TAKES, 0f, 1f);
            if (open) {
                opened += seconds;
                if (!pushedOut && opened >= PUSH_FROM + PUSH_TAKES) {
                    pushedOut = true;
                    for (int place = chambers - spent; place < chambers; place++) {
                        throwing.out(place, false);
                    }
                    spent = 0;
                }
            } else {
                opened = NEVER;
            }
            int now = live(loaded, spent);
            if (doing == ArmActs.UNLOADING) {
                // What was not fired goes out as the game takes it.
                for (int place = now; place < rounds; place++) {
                    throwing.out(place, true);
                }
            }
            rounds = now;
        }

        /** How many live rounds are drawn: what the game counts, in the chambers the fired cases still in them leave. */
        private int live(int loaded, int cases) {
            return Math.clamp(loaded, 0, chambers - cases);
        }

        /** What the chamber at a place in the order they fire holds. */
        int holds(int place) {
            if (place < rounds) {
                return LIVE;
            }
            return place >= chambers - spent ? SPENT : EMPTY;
        }

        /** The place of the chamber the next round goes into. */
        int loading() {
            return rounds;
        }

        int chambers() {
            return chambers;
        }

        /** Whether it is out of the frame or on its way out or back. */
        boolean opening() {
            return open || swung > 0f;
        }

        /** How far the hammer is cocked: 1 at the shot, falling to 0. */
        float hammer() {
            return sinceShot < 0f ? 0f : 1f - ViewModelMath.smooth(sinceShot / HAMMER_FALLS);
        }

        /** How far the trigger is pulled: 1 at the shot, coming forward to 0. */
        float trigger() {
            return sinceShot < 0f ? 0f : 1f - ViewModelMath.smooth((sinceShot - TRIGGER_FROM) / TRIGGER_TAKES);
        }

        /** How far the cylinder is turned from where its chambers are counted from, in chambers: -1 at a shot, the chamber fired still under the hammer, coming to 0. */
        float turned() {
            return sinceShot < 0f ? 0f : ViewModelMath.smooth((sinceShot - TURN_FROM) / TURN_TAKES) - 1f;
        }

        /** How far the cylinder is swung out, 0 shut to 1 out. */
        float swing() {
            return ViewModelMath.smooth(swung);
        }

        /** How far the ejector is pushed, 0 home to 1 right out. */
        float pushed() {
            if (opened < 0f) {
                return 0f;
            }
            return ViewModelMath.smooth((opened - PUSH_FROM) / PUSH_TAKES) - ViewModelMath.smooth((opened - PUSH_FROM - PUSH_TAKES - PUSH_HELD) / PUSH_BACK);
        }
    }
}
