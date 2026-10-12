package com.cameraunlock.zomboid;

/**
 * The mod's own models of the revolvers and of the toy cap pistol, drawn in the viewer's hands in
 * place of the game's: meshes, their colour and the shape and finish of their surfaces, made by
 * tools/models/revolver.py and kept in the jar (OwnGun says how each is held, lit and drawn).
 *
 * The game says how many rounds are in the cylinder and when the holder is loading or
 * unloading it, and the cylinder is worked from that, as OwnShotgun works the pump: on each shot
 * the hammer falls, the trigger comes forward again and the cylinder turns one chamber; to be
 * loaded the cylinder swings out on its crane, the ejector throws out what was fired, the
 * rounds go in one by one as the game puts them in, and it swings shut again.
 *
 * All of it is the render thread's.
 */
final class OwnRevolver extends OwnGun {
    /** The parts the file's layout names. */
    private static final int FIXED = 0, TRIGGER = 1, HAMMER = 2, CYLINDER = 3, CRANE = 4, EJECTOR = 5, ROUND = 6, CASE = 7;
    static final int PARTS = 8;
    /** Where each of the numbers that say how the parts move is in the file's list of them. */
    private static final int HAMMER_Y = 0, HAMMER_Z = 1, COCKED = 2, TRIGGER_Y = 3, TRIGGER_Z = 4, PULLED = 5, AXIS_X = 6, AXIS_Z = 7, TURN = 8,
            CHAMBERS = 9, CRANE_X = 10, CRANE_Z = 11, SWING = 12, PUSHED = 13, CHAMBER_X = 14, CHAMBER_Y = 15, CHAMBER_Z = 16, MOVES = 18;
    /** How fast what the ejector pushes out leaves the chambers, in metres a second of the model, along their length. */
    private static final float THROWN_OUT = 0.9f;

    /** Fired cases and rounds taken out: pushed out of the back of the cylinder, falling and turning slowly. */
    private final Thrown cases = new Thrown(Cylinder.MOST, 0.9f, 0f, 0f, 0f, 7.5f, 6f);
    private final Thrown rounds = new Thrown(Cylinder.MOST, 0.9f, 0f, 0f, 0f, 7.5f, 6f);
    final Cylinder cylinder = new Cylinder();
    private final float[] part = new float[16], part2 = new float[16], turn = new float[16], scratch = new float[16], at = new float[3];
    /** The model last worked, and the camera it was worked with, for what the cylinder throws out. */
    private OwnModel worked;
    private ViewCamera.Pose workedFrom;

    OwnRevolver(View view) {
        // Oiled walnut grips, and with the arms out the head is far from what the eye sees of them.
        super(view, 0.45f, 0.30f, 0f);
    }

    @Override
    void work(ViewCamera.Pose camera, float seconds) {
        ViewModel.Held held = view.viewModel.held;
        worked = drawn;
        workedFrom = camera;
        if (held != null && drawn.moves.length >= MOVES) {
            cylinder.step(held.item(), (int) drawn.moves[CHAMBERS], held.rounds(), held.doing(), seconds, this::thrown);
        }
        cases.fly(camera, seconds);
        rounds.fly(camera, seconds);
    }

    /** What the ejector pushes out of the chamber at a place in the order they fire, as it leaves. */
    private void thrown(int place, boolean live) {
        chamber(worked, place, part);
        ViewModelMath.place(part, 0f, 0f, 0f, at);
        (live ? rounds : cases).outAlong(workedFrom, model, at[0], at[1], at[2], 0f, -THROWN_OUT, 0f);
    }

    /**
     * Where the next round goes in, in the model's axes: the back of the chamber the game fills
     * next, wherever the cylinder is. Null while the cylinder is not swung out.
     */
    float[] loadingPort(OwnModel own) {
        if (own.moves.length < MOVES || cylinder.swing() < 0.5f || cylinder.loading() >= own.moves[CHAMBERS]) {
            return null;
        }
        chamber(own, cylinder.loading(), part);
        float[] port = new float[3];
        ViewModelMath.place(part, 0f, 0f, 0f, port);
        return port;
    }

    @Override
    void parts(Program drawing) {
        float[] moves = drawn.moves;
        part(FIXED);
        turnAbout(part, 0f, moves[HAMMER_Y], moves[HAMMER_Z], 1f, 0f, 0f, moves[COCKED] * cylinder.hammer());
        draw(drawing, part, HAMMER);
        turnAbout(part, 0f, moves[TRIGGER_Y], moves[TRIGGER_Z], 1f, 0f, 0f, moves[PULLED] * cylinder.trigger());
        draw(drawing, part, TRIGGER);
        crane(drawn, part);
        draw(drawing, part, CRANE);
        spun(drawn, 0f, part);
        draw(drawing, part, CYLINDER);
        moved(0f, -moves[PUSHED] * cylinder.pushed(), 0f, scratch);
        times(part, scratch, turn);
        draw(drawing, turn, EJECTOR);
        int chambers = (int) moves[CHAMBERS];
        for (int place = 0; place < chambers; place++) {
            int holds = cylinder.holds(place);
            if (holds != Cylinder.EMPTY) {
                chamber(drawn, place, part);
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

    /** The crane swung as far out as it is now, about its pin. */
    private void crane(OwnModel own, float[] out) {
        float[] moves = own.moves;
        turnAbout(out, moves[CRANE_X], 0f, moves[CRANE_Z], 0f, 1f, 0f, moves[SWING] * cylinder.swing());
    }

    /** The cylinder as it is now, swung out with the crane and turned, and turned on by `more` from that. */
    private void spun(OwnModel own, float more, float[] out) {
        float[] moves = own.moves;
        crane(own, scratch);
        turnAbout(turn, moves[AXIS_X], 0f, moves[AXIS_Z], 0f, 1f, 0f, moves[TURN] * cylinder.turned() + more);
        times(scratch, turn, out);
    }

    /** What is in the chamber at a place in the order they fire, as it is now: its rim's back face at the origin, along y, and as far out as the ejector has pushed it. */
    private void chamber(OwnModel own, int place, float[] out) {
        float[] moves = own.moves;
        spun(own, -moves[TURN] * place, part2);
        moved(moves[CHAMBER_X], moves[CHAMBER_Y] - moves[PUSHED] * cylinder.pushed(), moves[CHAMBER_Z], scratch);
        times(part2, scratch, out);
    }

    static void moved(float x, float y, float z, float[] out) {
        java.util.Arrays.fill(out, 0f);
        out[0] = 1f;
        out[5] = 1f;
        out[10] = 1f;
        out[15] = 1f;
        out[12] = x;
        out[13] = y;
        out[14] = z;
    }

    /** out = a times b, each sixteen floats by columns. out may not be either. */
    static void times(float[] a, float[] b, float[] out) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) {
                    sum += a[k * 4 + row] * b[column * 4 + k];
                }
                out[column * 4 + row] = sum;
            }
        }
    }

    /**
     * What is in the cylinder and how its parts stand, worked out from what the game says of the
     * revolver frame by frame. Pure.
     *
     * The chambers are counted in the order they fire: the one under the hammer first. The game
     * fires the rounds in the cylinder one after another, so the live ones are the first, as
     * many as the game says, and the ones fired since it was last emptied are the last, the most
     * recent last of all.
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
        /** After it has swung out the ejector is pushed home over these, held, and let back over these, in seconds: what it pushes out is thrown at the end of the push. */
        static final float PUSH_FROM = 0.30f, PUSH_TAKES = 0.12f, PUSH_HELD = 0.08f, PUSH_BACK = 0.12f;
        private static final float NEVER = -1f;

        /** Called with a chamber's place in the order they fire and whether what it throws out is a live round. */
        interface Throws {
            void out(int place, boolean live);
        }

        private String item;
        private int chambers = 6, rounds, spent;
        private float sinceShot = NEVER, swung, opened = NEVER;
        private boolean open, pushedOut;

        /**
         * Moves it on by a frame.
         *
         * @param item     the game's name for what is held
         * @param chambers how many chambers the cylinder has
         * @param loaded   how many rounds the game says are in it
         * @param doing    what the holder is doing to it: one of ArmActs' doings
         * @param seconds  how long the frame was
         * @param throwing told of each thing the ejector throws out
         */
        void step(String item, int chambers, int loaded, int doing, float seconds, Throws throwing) {
            boolean opening = doing == ArmActs.LOADING || doing == ArmActs.UNLOADING;
            if (!item.equals(this.item)) {
                this.item = item;
                this.chambers = chambers;
                rounds = Math.min(loaded, chambers);
                spent = 0;
                sinceShot = NEVER;
                open = opening;
                swung = opening ? 1f : 0f;
                opened = opening ? PUSH_FROM + PUSH_TAKES : NEVER;
                pushedOut = opening;
                return;
            }
            loaded = Math.min(loaded, chambers);
            if (!opening && !open && loaded < rounds) {
                // Fired: the chamber under the hammer is spent, and the next turns up under it.
                spent = Math.min(spent + rounds - loaded, chambers - loaded);
                sinceShot = 0f;
            } else if (sinceShot >= 0f) {
                sinceShot += seconds;
                if (sinceShot > TRIGGER_FROM + TRIGGER_TAKES) {
                    sinceShot = NEVER;
                }
            }
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
                    // What was fired falls out; on unloading what was not fired goes with it, as the game takes it out.
                    for (int place = chambers - spent; place < chambers; place++) {
                        throwing.out(place, false);
                    }
                    spent = 0;
                }
                if (doing == ArmActs.UNLOADING && loaded < rounds) {
                    for (int place = loaded; place < rounds; place++) {
                        throwing.out(place, true);
                    }
                }
            } else {
                opened = NEVER;
            }
            rounds = loaded;
            spent = Math.min(spent, chambers - rounds);
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
