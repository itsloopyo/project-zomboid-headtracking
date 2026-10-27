package com.cameraunlock.zomboid;

/**
 * The mod's own models of the over and under double barrelled shotgun and of the same gun
 * sawn off, drawn in the viewer's hands in place of the game's: made by
 * tools/models/dbarrel.py, both from one texture (OwnGun says how each is held, lit and drawn).
 *
 * It is worked as the game works its own. The game draws a model of the gun broken open while
 * it is being loaded, and its own closed one again as it snaps it shut: the top lever goes over
 * and the barrels drop open about the hinge while the game's open model is drawn, and close as
 * the closed one is drawn again. The fired shells in the chambers are thrown out back over the
 * shoulder as the barrels come fully open, the hand that loads pushes a shell into each empty
 * chamber, and a shot fires the lower barrel first.
 *
 * All of it is the render thread's but {@link Barrels}, which is pure.
 */
final class OwnDouble extends OwnGun {
    /** The parts the file's layout names, and how many it has. */
    private static final int FIXED = 0, BARRELS = 1, LEVER = 2, SPENT = 3, LIVE = 4, STOCK = 5;
    static final int PARTS = 6;
    /** No part slides back toward the eye as the action is worked. */
    static final int SLIDES = OwnModel.NO_PART;
    /** The numbers in the file that say how the parts move: tools/models/dbarrel.py writes them in this order. */
    static final int HINGE_Y = 0, HINGE_Z = 1, OPENS = 2, LEVER_Y = 3, LEVER_TURNS = 4, BREECH_Y = 5, LOWER_Z = 6, UPPER_Z = 7, SHELL_LONG = 8;
    static final int MOVES = SHELL_LONG + 1;
    /** The ending the game gives the name of its model of a gun broken open. */
    private static final String OPEN_MODEL = "_OPEN";

    /** How long the barrels take to drop open, and to be snapped shut, in seconds to all but 1/e of the way. */
    private static final float OPENING = 0.07f, SHUTTING = 0.035f;
    /** The top lever goes over before the barrels drop, and back once they are shut. */
    private static final float LEVER_GOES = 0.03f;
    /** The ejectors throw the fired shells out once the barrels are this far open, and the extractors have drawn every shell back this far, in metres. */
    static final float THROWN_AT = 0.85f, EXTRACTED = 0.006f;
    /** How far from the eye the stock is first drawn with the sights up, in the model's metres. */
    private static final float NOT_DRAWN_WITHIN = 0.22f;

    /** Spent shells: thrown out back over the shoulder by the ejectors' springs, a little to the right, tumbling. */
    private final Thrown shells = new Thrown(2, 1.0f, 0.25f, 1.6f, 1.9f, 7.5f, 14f);
    private final Barrels barrels = new Barrels();
    private float open, lever, cut;
    private final float[] part = new float[16], port = new float[3];

    OwnDouble(View view) {
        // Oiled wood seen along its length does not turn into a mirror of the sky, as the rifle's.
        super(view, 1f, 0.30f, 0f);
    }

    @Override
    void placed(float aim) {
        cut = ViewModelMath.smooth(aim) * NOT_DRAWN_WITHIN * metre();
    }

    @Override
    void work(ViewCamera.Pose camera, float seconds) {
        ViewModel.Held held = view.viewModel.held;
        boolean broken = gameModel != null && gameModel.endsWith(OPEN_MODEL);
        lever = ViewModelMath.ease(lever, broken ? 1f : 0f, seconds, LEVER_GOES);
        // The barrels drop once the lever is over, and the lever goes back once they are shut.
        float before = open;
        open = ViewModelMath.ease(open, broken && lever > 0.5f ? 1f : 0f, seconds, broken ? OPENING : SHUTTING);
        if (!broken && open > 0.02f) {
            lever = Math.max(lever, open);
        }
        if (held != null) {
            barrels.step(held.itemId(), held.rounds(), held.doing(), held.round(), open > 0.5f);
        }
        if (before < THROWN_AT && open >= THROWN_AT) {
            int thrown = barrels.ejected();
            float[] moves = drawn.moves;
            for (int which = 0; which < 2; which++) {
                if ((thrown & 1 << which) != 0) {
                    // Where the shell's middle is now, in the model's axes: what is thrown is drawn about that point.
                    inChamber(moves, which, open, -EXTRACTED, part);
                    float y = moves[BREECH_Y] + moves[SHELL_LONG] * 0.5f, z = moves[LOWER_Z];
                    shells.out(camera, model, part[4] * y + part[8] * z + part[12], part[5] * y + part[9] * z + part[13], part[6] * y + part[10] * z + part[14]);
                }
            }
        }
        shells.fly(camera, seconds);
    }

    @Override
    void parts(Program drawing) {
        float[] moves = drawn.moves;
        part(FIXED);
        // The lever is on the top: held up the model is a mirror image, so its turn to the right is the other way round its upright.
        turnAbout(part, 0f, moves[LEVER_Y], 0f, 0f, 0f, 1f, -lever * moves[LEVER_TURNS]);
        drawing.set("uPart", part);
        part(LEVER);
        turnAbout(part, 0f, moves[HINGE_Y], moves[HINGE_Z], 1f, 0f, 0f, -open * moves[OPENS]);
        drawing.set("uPart", part);
        part(BARRELS);
        for (int which = 0; which < 2; which++) {
            int holds = barrels.holds(which);
            if (holds != Barrels.EMPTY) {
                inChamber(moves, which, open, -EXTRACTED * Math.min(open / THROWN_AT, 1f), part);
                drawing.set("uPart", part);
                part(holds == Barrels.FIRED ? SPENT : LIVE);
            }
        }
        int going = barrels.going();
        if (going >= 0) {
            shellGoingIn(moves, going, open, barrels.pushed(), part);
            drawing.set("uPart", part);
            part(LIVE);
        }
        drawing.set("uPart", Characters.IDENTITY);
        drawing.set("uCut", cut);
        part(STOCK);
        drawing.set("uCut", 0f);
        centred(moves, part);
        drawing.set("uPart", part);
        shells.draw(this, drawing, SPENT);
        drawing.set("uPart", Characters.IDENTITY);
    }

    @Override
    float[] loadingPort(OwnModel own) {
        int into = barrels.next();
        if (drawn == null || into < 0 || open < 0.3f) {
            return null;
        }
        // Where the shell the hand pushes in has its rim as the hand puts it to the chamber: ArmHolds pushes the hand on from there.
        rim(drawn.moves, into, open, 0f, part, port);
        return port;
    }

    /** Into out: a shell, as the file has it in the lower chamber, moved into chamber which, back along the bore by back metres, and turned with the barrels open by opened. */
    static void inChamber(float[] moves, int which, float opened, float back, float[] out) {
        turnAbout(out, 0f, moves[HINGE_Y], moves[HINGE_Z], 1f, 0f, 0f, -opened * moves[OPENS]);
        float up = which == 1 ? moves[UPPER_Z] - moves[LOWER_Z] : 0f;
        // out times a move of (0, back, up): the move goes first.
        out[12] += out[4] * back + out[8] * up;
        out[13] += out[5] * back + out[9] * up;
        out[14] += out[6] * back + out[10] * up;
    }

    /**
     * Into out: a shell being pushed into chamber which, pushed home as far as pushed says: 0 put
     * to the chamber, its nose in it, and 1 home, as far as ArmHolds pushes the hand that has it.
     */
    static void shellGoingIn(float[] moves, int which, float opened, float pushed, float[] out) {
        inChamber(moves, which, opened, -(1f - pushed) * ArmActs.ROUND_PUSHED - EXTRACTED, out);
    }

    /** Into out, three floats: the middle of the rim of a shell being pushed into chamber which, as shellGoingIn places it, in the model's axes. at is sixteen floats to work in. */
    static void rim(float[] moves, int which, float opened, float pushed, float[] at, float[] out) {
        shellGoingIn(moves, which, opened, pushed, at);
        float y = moves[BREECH_Y], z = moves[LOWER_Z];
        out[0] = at[4] * y + at[8] * z + at[12];
        out[1] = at[5] * y + at[9] * z + at[13];
        out[2] = at[6] * y + at[10] * z + at[14];
    }

    /** Into out: a move that puts the middle of a shell, as the file has it, at the model's origin, for what is thrown to tumble about. */
    static void centred(float[] moves, float[] out) {
        System.arraycopy(Characters.IDENTITY, 0, out, 0, 16);
        out[13] = -(moves[BREECH_Y] + moves[SHELL_LONG] * 0.5f);
        out[14] = -moves[LOWER_Z];
    }

    /**
     * What is in the two chambers, the lower first, worked out from what the game says of the gun
     * frame by frame. Pure.
     *
     * The game counts only the rounds in the gun: it counts no fired shells in a gun that does not
     * keep them (ISReloadWeaponAction.onShoot adds to the spent rounds only of a gun that removes
     * them by hand), so a shot is a round fewer while the gun is not being unloaded. A shot fires
     * the lower barrel while it is loaded, and the fired shell stays in its chamber until the
     * barrels come open. The game counts the rounds in all at once at the end of its loading,
     * so the shells the loading hand pushes in, one with each of its strokes, are drawn in the
     * chambers until it does.
     */
    static final class Barrels {
        /** What a chamber holds. */
        static final int EMPTY = 0, LOADED = 1, FIRED = 2;
        /**
         * The loading hand puts a shell to the chamber, and has it home, at these shares of a
         * stroke, of ArmActs' round: those ArmHolds pushes the hand on over from the port.
         */
        static final float PUT_TO = 0.45f, HOME = ArmActs.ROUND_IN;
        private static final int NOTHING_HELD = Integer.MIN_VALUE;
        private final int[] chamber = new int[2];
        private int held = NOTHING_HELD, rounds, strokes;
        private float round;
        private boolean loading;

        /**
         * Moves it on by a frame.
         *
         * @param item    the game's id of the gun held: another, even of the same kind, is read afresh
         * @param loaded  how many rounds the game says it holds
         * @param doing   what the holder is doing to it: one of ArmActs' doings
         * @param stroke  how far through a stroke the loading hand is, as ArmActs.round has it
         * @param open    whether the barrels are open
         */
        void step(int item, int loaded, int doing, float stroke, boolean open) {
            int now = Math.clamp(loaded, 0, 2);
            if (item != held) {
                // Taken in hand: its rounds from the upper chamber down, as a gun fired from its lower barrel first is left.
                held = item;
                chamber[1] = now >= 1 ? LOADED : EMPTY;
                chamber[0] = now >= 2 ? LOADED : EMPTY;
                rounds = now;
                strokes = 0;
                round = stroke;
                loading = false;
                return;
            }
            for (; now < rounds; rounds--) {
                boolean shot = doing != ArmActs.UNLOADING;
                int which = shot ? chamber[0] == LOADED ? 0 : 1 : chamber[1] == LOADED ? 1 : 0;
                chamber[which] = shot ? FIRED : EMPTY;
            }
            for (; now > rounds; rounds++) {
                chamber[chamber[0] == LOADED ? 1 : 0] = LOADED;
            }
            boolean nowLoading = doing == ArmActs.LOADING && open;
            if (nowLoading && loading) {
                // A stroke puts its shell in as the hand pushes it home, where it lets go of it: a frame may pass home on both sides of the wrap.
                if (stroke < round) {
                    strokes += (round < HOME ? 1 : 0) + (stroke >= HOME ? 1 : 0);
                } else if (round < HOME && stroke >= HOME) {
                    strokes++;
                }
            }
            if (!nowLoading) {
                strokes = 0;
            }
            loading = nowLoading;
            round = stroke;
        }

        /** The fired shells the ejectors throw out as the barrels come open: a bit for each chamber, the lower's first. They are empty after. */
        int ejected() {
            int thrown = 0;
            for (int which = 0; which < 2; which++) {
                if (chamber[which] == FIRED) {
                    chamber[which] = EMPTY;
                    thrown |= 1 << which;
                }
            }
            return thrown;
        }

        /** What a chamber holds as it is drawn: a shell the loading hand has pushed home is loaded before the game counts it. */
        int holds(int which) {
            if (chamber[which] != EMPTY || !loading) {
                return chamber[which];
            }
            int before = which == 1 && chamber[0] == EMPTY ? 1 : 0;
            return strokes > before ? LOADED : EMPTY;
        }

        /** The chamber the next shell goes into, or -1 with both filled. */
        int next() {
            for (int which = 0; which < 2; which++) {
                if (holds(which) == EMPTY) {
                    return which;
                }
            }
            return -1;
        }

        /** The chamber a shell is being pushed into this frame, or -1. */
        int going() {
            int into = next();
            return loading && into >= 0 && round > PUT_TO && round < HOME ? into : -1;
        }

        /** How far home the shell being pushed in is: 0 put to the chamber, 1 home. */
        float pushed() {
            return ViewModelMath.smooth((round - PUT_TO) / (HOME - PUT_TO));
        }
    }
}
