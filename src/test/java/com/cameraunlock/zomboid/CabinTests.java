package com.cameraunlock.zomboid;

import java.util.ArrayList;

/** Tests of the driver's place: what part of a body each of its triangles is, and how far its wheel turns. */
final class CabinTests {
    private CabinTests() {}

    static void run() {
        steeringWheelTurnsTwiceTheGamesSteering();
        driverIsSeatedOnTheWheelsSide();
        bodyIsToldIntoSeatsDashDoorAndBonnet();
        mirroredBodyIsToldTheSame();
        bodyWithItsSeatsInItsShellHasNoSeatPieces();
        signOnTheRoofIsNotASeat();
        frontOfTheGlasshouseIsUnseenAndTheBackKept();
        passengersSitWhereTheScriptSeatsThemFromTheDriver();
        mirrorHangsFromTheVehicleWhicheverWayItFaces();
        wheelIsTheRingBeforeTheDriverAndNotTheSpareOnTheBack();
    }

    /** A ring of 36 triangles standing across the vehicle, as a body has for its wheel. Returns its first vertex. */
    private static int ring(Body body, float along, float across, float up, float radius) {
        int first = body.at.size() / 3;
        for (int k = 0; k < 18; k++) {
            double turn = Math.PI * 2 * k / 18;
            body.vertex(along, across + radius * 0.8f * (float) Math.cos(turn), up + radius * 0.8f * (float) Math.sin(turn), BACK);
            body.vertex(along, across + radius * (float) Math.cos(turn), up + radius * (float) Math.sin(turn), BACK);
        }
        for (int k = 0; k < 18; k++) {
            int a = first + k * 2, b = first + (k + 1) % 18 * 2;
            body.triangle(a, a + 1, b, -2, false);
            body.triangle(b, a + 1, b + 1, -2, false);
        }
        return first;
    }

    private static void wheelIsTheRingBeforeTheDriverAndNotTheSpareOnTheBack() {
        for (float side : new float[] {1f, -1f}) {
            Body body = saloon(side, true);
            int wheel = body.at.size() / 3 - 36;
            // A spare on the back door, on the middle line and rounder than the wheel is, and a drum beside the driver's seat.
            ring(body, -1.9f, 0f, 0.1f, 0.2f);
            ring(body, 0.1f, -0.1f, -0.3f, 0.2f);
            float[] at = new float[body.at.size()];
            for (int i = 0; i < at.length; i++) {
                at[i] = body.at.get(i);
            }
            int[] elements = new int[body.elements.size()];
            for (int i = 0; i < elements.length; i++) {
                elements[i] = body.elements.get(i);
            }
            int[] found = CabinParts.wheelOf(at, elements, new float[] {0.16f, 0.32f}, 0f);
            check(found != null && found.length == 36 && found[0] == wheel, "the wheel is the ring before the driver, on the driver's side");
            // With no seat known it is the first of the roundest.
            check(CabinParts.wheelOf(at, elements, null, 0f) != null, "and a body whose seats are not known still has a wheel");
        }
        check(CabinParts.wheelOf(new float[] {-1f, -0.8f, -0.5f, 1.2f, -0.8f, -0.5f, 1.2f, 0.8f, -0.5f, -1f, 0.8f, -0.5f}, new int[] {0, 1, 2, 0, 2, 3}, new float[] {0.16f, 0.32f}, 0f) == null,
                "a body with no ring has no wheel");
    }

    private static void passengersSitWhereTheScriptSeatsThemFromTheDriver() {
        // The wheel on the side the script counts as positive, then on the other.
        float[] beside = Cabins.fromDriver(0.32f, 0.16f, -0.32f, 0.16f, 0.323f);
        check(near(beside[0], 0f) && near(beside[1], -0.64f), "the front passenger is level with the driver and across the middle from the wheel");
        float[] mirrored = Cabins.fromDriver(0.32f, 0.16f, -0.32f, 0.16f, -0.323f);
        check(near(mirrored[1], 0.64f), "and the same in a body the picture counts the other way across");
        float[] behind = Cabins.fromDriver(0.32f, 0.16f, 0.32f, -0.59f, 0.323f);
        check(near(behind[0], -0.75f) && near(behind[1], 0f), "who sits behind the driver is straight behind");
    }

    private static void mirrorHangsFromTheVehicleWhicheverWayItFaces() {
        float[] house = Cabins.house("Base.CarNormal", Cabins.STANDARD_MEASURES);
        float[] east = new float[3], north = new float[3];
        Cabins.mirrorAt(new float[] {10f, 20f, 1f}, house, 1f, 0f, east);
        Cabins.mirrorAt(new float[] {10f, 20f, 1f}, house, 0f, 1f, north);
        check(east[0] > 10f && north[1] > 20f, "the mirror is ahead of the wheel");
        check(near(east[0] - 10f, north[1] - 20f) && near(east[1] - 20f, -(north[0] - 10f)), "and turns with the vehicle");
        check(near(east[2], north[2]) && east[2] > 1.2f, "above the wheel, the same height either way");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    private static void steeringWheelTurnsTwiceTheGamesSteering() {
        check(Cabins.wheelTurn(0f) == 0f, "wheels straight, wheel straight");
        check(near(Cabins.wheelTurn(0.1f), 0.2f), "the wheel turns twice as far as the game counts the steering");
        check(near(Cabins.wheelTurn(-0.25f), -0.5f), "and the same the other way");
        check(Math.abs(Cabins.wheelTurn(0.9f)) < Math.PI * 0.6, "full lock is a little over a quarter turn");
    }

    /** The script's seats: across, up and along from the model's middle. Front left, front right, rear left, rear right. */
    private static final float[] SCRIPT_SEATS = {0.32f, -0.25f, 0.16f, -0.32f, -0.25f, 0.16f, 0.32f, -0.25f, -0.59f, -0.32f, -0.25f, -0.59f};

    private static void driverIsSeatedOnTheWheelsSide() {
        float[] seats = CabinParts.seats(SCRIPT_SEATS, 0.02f, 0.01f, -0.31f);
        check(near(seats[0], 0.18f) && near(seats[1], -0.31f), "the driver sits behind a wheel on the low side");
        check(near(seats[3], 0.33f) && near(seats[4], -0.57f) && near(seats[5], -0.31f), "and the rest follow, the rear behind the front");
        seats = CabinParts.seats(SCRIPT_SEATS, 0f, 0f, 0.32f);
        check(near(seats[1], 0.32f) && near(seats[3], -0.32f), "and behind one on the high side");
    }

    /** A body put together a quad at a time, with what each of its triangles ought to be told as. */
    private static final class Body {
        final ArrayList<Float> at = new ArrayList<>(), facing = new ArrayList<>();
        final ArrayList<Integer> elements = new ArrayList<>(), kinds = new ArrayList<>();
        final ArrayList<Boolean> glass = new ArrayList<>();
        final float side;

        /** side is 1 for a wheel on the low side of across and -1 for the body mirrored. */
        Body(float side) {
            this.side = side;
        }

        int vertex(float along, float across, float up, float[] faces) {
            at.add(along);
            at.add(across * side);
            at.add(up);
            facing.add(faces[0]);
            facing.add(faces[1] * side);
            facing.add(faces[2]);
            return at.size() / 3 - 1;
        }

        void triangle(int a, int b, int c, int kind, boolean pane) {
            elements.add(a);
            elements.add(b);
            elements.add(c);
            kinds.add(kind);
            glass.add(pane);
        }

        /** Four corners, each along, across and up, and the way the quad faces. Tied quads are joined into the one shell. */
        void quad(float[] corners, float[] faces, int kind, boolean pane, boolean tied) {
            int first = vertex(corners[0], corners[1], corners[2], faces);
            for (int corner = 1; corner < 4; corner++) {
                vertex(corners[corner * 3], corners[corner * 3 + 1], corners[corner * 3 + 2], faces);
            }
            triangle(first, first + 1, first + 2, kind, pane);
            triangle(first, first + 2, first + 3, kind, pane);
            if (tied) {
                // A sliver from the quad down to two places on the floor: what it is told as does not matter.
                int corner = vertex(corners[0], corners[1], corners[2], faces);
                triangle(corner, vertex(-1f, -0.8f, -0.5f, faces), vertex(-1f, 0.8f, -0.5f, faces), -2, false);
            }
        }

        CabinParts.Found told(float[] seats) {
            float[] places = new float[at.size()], ways = new float[at.size()];
            for (int i = 0; i < places.length; i++) {
                places[i] = at.get(i);
                ways[i] = facing.get(i);
            }
            int[] indices = new int[elements.size()];
            boolean[] panes = new boolean[glass.size()];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = elements.get(i);
            }
            for (int i = 0; i < panes.length; i++) {
                panes[i] = glass.get(i);
            }
            return CabinParts.classify(places, ways, indices, panes, new float[] {0.5f, -0.32f * side, 0f}, seats);
        }

        void checkTold(CabinParts.Found found, String what) {
            for (int t = 0; t < kinds.size(); t++) {
                check(kinds.get(t) == -2 || found.kinds[t] == kinds.get(t), what + ": triangle " + t + " is told as " + found.kinds[t] + ", not " + kinds.get(t));
            }
        }
    }

    private static final float[] UP = {0f, 0f, 1f}, DOWN = {0f, 0f, -1f}, BACK = {-1f, 0f, 0f}, HIGH = {0f, 1f, 0f}, LOW = {0f, -1f, 0f};

    /** A saloon: a shell with its floor, dash, doors, bonnet, glass and rear bench, and a wheel and a front bench as loose pieces. */
    private static Body saloon(float side, boolean looseSeats) {
        Body body = new Body(side);
        body.quad(new float[] {-1f, -0.8f, -0.5f, 1.2f, -0.8f, -0.5f, 1.2f, 0.8f, -0.5f, -1f, 0.8f, -0.5f}, UP, CabinParts.OTHER, false, false);
        body.quad(new float[] {0.85f, -0.7f, -0.2f, 0.85f, 0.7f, -0.2f, 0.85f, 0.7f, 0.1f, 0.85f, -0.7f, 0.1f}, BACK, CabinParts.DASH, false, true);
        // The driver's door: its card faces in, its skin out.
        body.quad(new float[] {-0.2f, -0.78f, -0.4f, 0.8f, -0.78f, -0.4f, 0.8f, -0.78f, 0.05f, -0.2f, -0.78f, 0.05f}, HIGH, CabinParts.DOOR, false, true);
        body.quad(new float[] {-0.2f, -0.8f, -0.4f, 0.8f, -0.8f, -0.4f, 0.8f, -0.8f, 0.05f, -0.2f, -0.8f, 0.05f}, LOW, CabinParts.OTHER, false, true);
        body.quad(new float[] {0.95f, -0.7f, 0.05f, 2.2f, -0.7f, 0.05f, 2.2f, 0.7f, 0.05f, 0.95f, 0.7f, 0.05f}, UP, CabinParts.BONNET, false, true);
        // The inside of a wheel arch, under the bonnet.
        body.quad(new float[] {1.2f, -0.7f, -0.1f, 1.8f, -0.7f, -0.1f, 1.8f, -0.4f, -0.1f, 1.2f, -0.4f, -0.1f}, DOWN, CabinParts.OTHER, false, true);
        body.quad(new float[] {0.9f, -0.7f, 0.1f, 0.9f, 0.7f, 0.1f, 0.6f, 0.7f, 0.6f, 0.6f, -0.7f, 0.6f}, new float[] {-0.86f, 0f, -0.5f}, CabinParts.GLASS, true, true);
        body.quad(new float[] {-0.1f, -0.78f, 0.08f, 0.8f, -0.78f, 0.08f, 0.8f, -0.7f, 0.5f, -0.1f, -0.7f, 0.5f}, HIGH, CabinParts.GLASS, true, true);
        body.quad(new float[] {-0.8f, -0.6f, -0.28f, -0.4f, -0.6f, -0.28f, -0.4f, 0.6f, -0.28f, -0.8f, 0.6f, -0.28f}, UP, CabinParts.SEAT, false, true);
        // The front bench: a cushion and a back, joined along the back's foot.
        int kind = looseSeats ? CabinParts.SEAT : -2;
        body.quad(new float[] {-0.1f, -0.6f, -0.3f, 0.45f, -0.6f, -0.3f, 0.45f, 0.6f, -0.3f, -0.1f, 0.6f, -0.3f}, UP, kind, false, !looseSeats);
        body.quad(new float[] {-0.1f, -0.6f, -0.3f, -0.1f, 0.6f, -0.3f, -0.1f, 0.6f, 0.3f, -0.1f, -0.6f, 0.3f}, new float[] {1f, 0f, 0f}, kind, false, !looseSeats);
        // A door mirror outside each front window: a face and a top, loose of the shell.
        for (float out : new float[] {-1f, 1f}) {
            body.quad(new float[] {0.76f, out * 0.95f, 0.04f, 0.76f, out * 0.82f, 0.04f, 0.76f, out * 0.82f, 0.16f, 0.76f, out * 0.95f, 0.16f}, BACK, CabinParts.GLASS, false, false);
            body.quad(new float[] {0.68f, out * 0.95f, 0.16f, 0.76f, out * 0.95f, 0.16f, 0.76f, out * 0.82f, 0.16f, 0.68f, out * 0.82f, 0.16f}, UP, CabinParts.GLASS, false, false);
        }
        // The wheel: a ring of 36 triangles standing across the vehicle.
        int first = body.at.size() / 3;
        for (int k = 0; k < 18; k++) {
            double turn = Math.PI * 2 * k / 18;
            body.vertex(0.5f, -0.32f + 0.1f * (float) Math.cos(turn), 0.1f * (float) Math.sin(turn), BACK);
            body.vertex(0.5f, -0.32f + 0.13f * (float) Math.cos(turn), 0.13f * (float) Math.sin(turn), BACK);
        }
        for (int k = 0; k < 18; k++) {
            int a = first + k * 2, b = first + (k + 1) % 18 * 2;
            body.triangle(a, a + 1, b, CabinParts.UNSEEN, false);
            body.triangle(b, a + 1, b + 1, CabinParts.UNSEEN, false);
        }
        return body;
    }

    private static void bodyIsToldIntoSeatsDashDoorAndBonnet() {
        Body body = saloon(1f, true);
        CabinParts.Found found = body.told(CabinParts.seats(SCRIPT_SEATS, 0f, 0f, -0.32f));
        body.checkTold(found, "a saloon");
        check(found.seatPieces == 1, "the front bench is one loose piece, found " + found.seatPieces);
        check(near(found.footAlong, 0.9f) && near(found.footUp, 0.1f), "the windscreen's foot is its lowest, foremost edge");
        check(near(found.sillUp, 0.08f) && near(found.glassOut, 0.78f), "the sill is the foot of the driver's window, and its glass is that far out");
        check(near(found.middleAcross, 0f), "the middle line is half way between the sides");
        check(near(found.dashTop, 0.1f), "the dashboard's top is its highest place");
        check(near(found.mirror[0], 0.72f) && near(found.mirror[1], -0.885f) && near(found.mirror[2], 0.1f), "the driver's door mirror is where the body's own is");
    }

    private static void mirroredBodyIsToldTheSame() {
        Body body = saloon(-1f, true);
        CabinParts.Found found = body.told(CabinParts.seats(SCRIPT_SEATS, 0f, 0f, 0.32f));
        body.checkTold(found, "a saloon mirrored");
        check(found.seatPieces == 1 && near(found.glassOut, 0.78f), "with the driver's window on the other side");
        check(near(found.mirror[1], 0.885f), "and the driver's mirror with it");
    }

    private static void bodyWithItsSeatsInItsShellHasNoSeatPieces() {
        Body body = saloon(1f, false);
        CabinParts.Found found = body.told(CabinParts.seats(SCRIPT_SEATS, 0f, 0f, -0.32f));
        check(found.seatPieces == 0, "a bench that is part of the shell is no loose piece");
        for (byte kind : found.kinds) {
            check(kind != CabinParts.SEAT, "and nothing of such a body is told as a seat");
        }
    }

    private static void frontOfTheGlasshouseIsUnseenAndTheBackKept() {
        Body body = saloon(1f, true);
        int first = body.kinds.size();
        // A screen pillar leaning in from the sill to the roof, the roof over the front seats, and a pillar by the back window.
        body.quad(new float[] {0.9f, -0.78f, 0.1f, 0.85f, -0.78f, 0.1f, 0.55f, -0.6f, 0.6f, 0.6f, -0.6f, 0.6f}, HIGH, CabinParts.UNSEEN, false, false);
        body.quad(new float[] {-0.6f, -0.6f, 0.6f, 0.6f, -0.6f, 0.6f, 0.6f, 0.6f, 0.6f, -0.6f, 0.6f, 0.6f}, DOWN, CabinParts.UNSEEN, false, false);
        body.quad(new float[] {-0.9f, -0.78f, 0.1f, -0.8f, -0.78f, 0.1f, -0.75f, -0.6f, 0.6f, -0.8f, -0.6f, 0.6f}, HIGH, CabinParts.OTHER, false, false);
        CabinParts.Found found = body.told(CabinParts.seats(SCRIPT_SEATS, 0f, 0f, -0.32f));
        body.checkTold(found, "a saloon with pillars and a roof");
        boolean screen = false, side = false;
        for (int t = 0; t < first; t++) {
            if (body.glass.get(t) && found.front[t]) {
                screen = true;
            }
            side |= !body.glass.get(t) && found.front[t];
        }
        check(screen && !side, "the screen and the front windows are the front's panes, and nothing else is");
        check(near(found.dashBack, 0.85f), "the dashboard's shelf begins at its highest edge nearest the seats");
    }

    private static void signOnTheRoofIsNotASeat() {
        Body body = saloon(1f, true);
        body.quad(new float[] {0f, -0.4f, 0.75f, 0.3f, -0.4f, 0.75f, 0.3f, 0.4f, 0.75f, 0f, 0.4f, 0.75f}, UP, CabinParts.UNSEEN, false, false);
        body.quad(new float[] {0f, -0.4f, 0.75f, 0.3f, -0.4f, 0.75f, 0.3f, -0.4f, 0.85f, 0f, -0.4f, 0.85f}, LOW, CabinParts.UNSEEN, false, false);
        CabinParts.Found found = body.told(CabinParts.seats(SCRIPT_SEATS, 0f, 0f, -0.32f));
        body.checkTold(found, "a saloon with a sign on its roof");
        check(found.seatPieces == 1, "a loose piece over the seats, on the roof, is not a seat");
    }
}
