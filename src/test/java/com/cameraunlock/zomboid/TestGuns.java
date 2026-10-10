package com.cameraunlock.zomboid;

import java.util.ArrayList;

/**
 * Firearms made of boxes for the tests, in metres, with y along the barrel, z up and x to the
 * side, as the game's long guns are made: a pistol whose grip is raked back, as a pistol's is,
 * and a long gun with a stock, a fore-end under its barrel and a magazine ahead of its guard.
 * Each has a trigger guard that closes round a hole.
 */
final class TestGuns {
    private TestGuns() {}

    /** A mesh: its vertices, three floats each, its triangles, and its muzzle. */
    record Mesh(float[] places, int[] corners, float[] muzzle) {}

    /** Where the pistol's grip is: its front edge at the top and at the foot, along the barrel and up. */
    static final float GRIP_FRONT_TOP = 0.035f, GRIP_FRONT_FOOT = 0.017f, GRIP_TOP = -0.004f, GRIP_FOOT = -0.075f, GRIP_THICK = 0.032f;
    /** The pistol's bore over its origin and its muzzle ahead of it. */
    static final float PISTOL_BORE = 0.026f, PISTOL_MUZZLE = 0.17f;

    static Mesh pistol() {
        Builder b = new Builder();
        // Slide and frame over the grip and the guard.
        b.box(-0.02f, PISTOL_MUZZLE, -0.004f, 0.036f, 0.012f);
        // The grip, raked: its front edge runs from the top to the foot back by this much.
        float rake = GRIP_FRONT_TOP - GRIP_FRONT_FOOT;
        b.slanted(GRIP_FRONT_TOP - GRIP_THICK, GRIP_FRONT_TOP, GRIP_TOP, GRIP_FOOT, -rake, 0.013f);
        // The guard: a bar under the hole from the grip to its front, and the front bar up to the frame.
        b.box(GRIP_FRONT_TOP - 0.012f, 0.078f, -0.028f, -0.024f, 0.006f);
        b.box(0.074f, 0.078f, -0.028f, -0.004f, 0.006f);
        return b.mesh(new float[] {0f, PISTOL_MUZZLE, PISTOL_BORE});
    }

    /** Where the long gun's parts are: the guard, the fore-end, the magazine and the muzzle, along the barrel; their tops and feet up. */
    static final float LONG_GUARD_BACK = 0.03f, LONG_GUARD_FRONT = 0.07f, FORE_FROM = 0.24f, FORE_TO = 0.5f, FORE_FOOT = -0.03f, LONG_MUZZLE = 0.9f,
            KNOB_OUT = 0.045f, MAGAZINE_BACK = 0.1f, MAGAZINE_FRONT = 0.14f, MAGAZINE_FOOT = -0.15f, LONG_BORE = 0.03f;

    static Mesh longGun(boolean magazine) {
        Builder b = new Builder();
        // Receiver and barrel.
        b.box(-0.04f, 0.22f, -0.01f, 0.05f, 0.016f);
        b.box(0.22f, LONG_MUZZLE, 0.02f, 0.04f, 0.009f);
        // The stock, its wrist under the receiver's back raked down behind the guard, and its butt.
        b.slanted(-0.03f, LONG_GUARD_BACK - 0.004f, -0.01f, -0.06f, -0.04f, 0.015f);
        b.box(-0.4f, -0.04f, -0.09f, 0.03f, 0.018f);
        // A bolt's knob out to the side over the guard.
        b.beside(0f, 0.012f, 0.032f, 0.02f, 0f, KNOB_OUT, 0.008f);
        // The guard.
        b.box(LONG_GUARD_BACK - 0.004f, LONG_GUARD_FRONT + 0.004f, -0.035f, -0.031f, 0.006f);
        b.box(LONG_GUARD_FRONT, LONG_GUARD_FRONT + 0.004f, -0.035f, -0.01f, 0.006f);
        b.box(LONG_GUARD_BACK - 0.004f, LONG_GUARD_BACK, -0.035f, -0.01f, 0.006f);
        // The fore-end under the barrel.
        b.box(FORE_FROM, FORE_TO, FORE_FOOT, 0.025f, 0.02f);
        if (magazine) {
            b.box(MAGAZINE_BACK, MAGAZINE_FRONT, MAGAZINE_FOOT, -0.009f, 0.012f);
        }
        return b.mesh(new float[] {0f, LONG_MUZZLE, LONG_BORE});
    }

    /** Builds a mesh of boxes. */
    static final class Builder {
        final ArrayList<Float> places = new ArrayList<>();
        final ArrayList<Integer> corners = new ArrayList<>();

        /** A box from `back` to `front` along y and `foot` to `top` up z, `half` either side of the middle in x. */
        void box(float back, float front, float foot, float top, float half) {
            slanted(back, front, top, foot, 0f, half);
        }

        /** A box whose foot is moved along y by `shift` against its top: back and front are its top's. */
        void slanted(float back, float front, float top, float foot, float shift, float half) {
            beside(back, front, top, foot, shift, 0f, half);
        }

        /** The same, its middle `middle` to the side. */
        void beside(float back, float front, float top, float foot, float shift, float middle, float half) {
            int first = places.size() / 3;
            for (int corner = 0; corner < 8; corner++) {
                boolean atFoot = (corner & 2) != 0;
                float y = ((corner & 1) != 0 ? front : back) + (atFoot ? shift : 0f);
                places.add(middle + ((corner & 4) != 0 ? half : -half));
                places.add(y);
                places.add(atFoot ? foot : top);
            }
            int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
            for (int[] face : faces) {
                corners.add(first + face[0]);
                corners.add(first + face[1]);
                corners.add(first + face[2]);
                corners.add(first + face[0]);
                corners.add(first + face[2]);
                corners.add(first + face[3]);
            }
        }

        Mesh mesh(float[] muzzle) {
            float[] p = new float[places.size()];
            for (int i = 0; i < p.length; i++) {
                p[i] = places.get(i);
            }
            return new Mesh(p, corners.stream().mapToInt(Integer::intValue).toArray(), muzzle);
        }
    }

    /** A mesh's shape, read with y along, z up, and a metre to a unit. */
    static WeaponShape shape(Mesh mesh) {
        StringBuilder why = new StringBuilder();
        WeaponShape shape = WeaponShape.read(mesh.places(), mesh.corners(), new float[] {0f, 1f, 0f}, new float[] {0f, 0f, 1f}, mesh.muzzle(), 1f, why);
        if (shape == null) {
            throw new IllegalStateException("the test gun has no shape: " + why);
        }
        return shape;
    }

    /**
     * A shape placed in a view: its grip at `grip`, its barrel along `pointing`, its top along
     * `over`, a metre of it `scale` long in the view, held still, its fore-end not slid.
     */
    static ArmHolds.Placed placed(WeaponShape shape, float[] grip, float[] pointing, float[] over, float scale, float slid, float foreRest) {
        float[] right = new float[3], toView = new float[16];
        ArmPose.cross(pointing, over, right);
        for (int axis = 0; axis < 3; axis++) {
            // Mesh x is the side, y along the barrel, z up.
            toView[axis] = right[axis] * scale;
            toView[4 + axis] = pointing[axis] * scale;
            toView[8 + axis] = over[axis] * scale;
        }
        toView[15] = 1f;
        float[] at = new float[3];
        ViewModelMath.place(toView, shape.grip[0], shape.grip[1], shape.grip[2], at);
        for (int axis = 0; axis < 3; axis++) {
            toView[12 + axis] = grip[axis] - at[axis];
        }
        float[] still = toView.clone();
        // The fore-end's slide is on the mesh: the still matrix is the same, the hand is moved by `slid`.
        return new ArmHolds.Placed(shape, toView, still, slid, foreRest, ArmHolds.BOLT_TRAVEL);
    }
}
