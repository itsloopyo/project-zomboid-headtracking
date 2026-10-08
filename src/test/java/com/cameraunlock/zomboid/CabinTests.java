package com.cameraunlock.zomboid;

import java.util.ArrayList;

/** Tests of the driver's place: what part of a body each of its triangles is, and how far its wheel turns. */
final class CabinTests {
    private CabinTests() {}

    static void run() {
        steeringWheelTurnsASixthOfATurnToFullLock();
        driverIsSeatedOnTheWheelsSide();
        bodyIsToldIntoSeatsDashDoorAndBonnet();
        mirroredBodyIsToldTheSame();
        bodyWithItsSeatsInItsShellHasNoSeatPieces();
        signOnTheRoofIsNotASeat();
        frontOfTheGlasshouseIsUnseenAndTheBackKept();
        passengersSitWhereTheScriptSeatsThemFromTheDriver();
        mirrorHangsFromTheVehicleWhicheverWayItFaces();
        mirrorLeavesOutWhatItCannotShow();
        cabinTipsAndRollsWithItsBody();
        eyeIsSeatedAlongTheVehiclesOwnAxes();
        viewTipsAndRollsWithTheVehicle();
        wheelIsTheRingBeforeTheDriverAndNotTheSpareOnTheBack();
    }

    /** A ring of 36 triangles standing across the vehicle, as a body has for its wheel, that far out from its middle across and up. Returns its first vertex. */
    private static int ring(Body body, float along, float across, float up, float wide, float tall) {
        int first = body.at.size() / 3;
        for (int k = 0; k < 18; k++) {
            double turn = Math.PI * 2 * k / 18;
            body.vertex(along, across + wide * 0.8f * (float) Math.cos(turn), up + tall * 0.8f * (float) Math.sin(turn), BACK);
            body.vertex(along, across + wide * (float) Math.cos(turn), up + tall * (float) Math.sin(turn), BACK);
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
            Body body = new Body(side);
            body.quad(new float[] {-1f, -0.8f, -0.5f, 1.2f, -0.8f, -0.5f, 1.2f, 0.8f, -0.5f, -1f, 0.8f, -0.5f}, UP, CabinParts.OTHER, false, false);
            // A spare on the back door, on the middle line and rounder than the wheel is, and a drum beside the driver's seat.
            // Both come before the wheel in the body, and the wheel is a little wider than it is tall.
            int spare = ring(body, -1.9f, 0f, 0.1f, 0.2f, 0.2f);
            ring(body, 0.1f, -0.1f, -0.3f, 0.2f, 0.19f);
            int wheel = ring(body, 0.5f, -0.32f, 0f, 0.13f, 0.12f);
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
            found = CabinParts.wheelOf(at, elements, null, 0f);
            check(found != null && found.length == 36 && found[0] == spare, "with no seat known it is the roundest ring, which is the spare");
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

    private static void mirrorLeavesOutWhatItCannotShow() {
        float tan = 0.3f, near = 2.6f, sight = 48f, reach = 6f;
        check(!Cabins.outOfMirror(20f, 0f, reach, tan, near, sight), "a chunk straight behind is in the mirror");
        check(Cabins.outOfMirror(-10f, 0f, reach, tan, near, sight), "one ahead of the vehicle is not");
        check(!Cabins.outOfMirror(-2f, 0f, reach, tan, near, sight), "one the mirror hangs over reaches into it");
        check(Cabins.outOfMirror(60f, 0f, reach, tan, near, sight) && !Cabins.outOfMirror(53f, 0f, reach, tan, near, sight),
                "one beyond its sight is out, and one that reaches back into its sight is not");
        check(Cabins.outOfMirror(20f, 13f, reach, tan, near, sight) && Cabins.outOfMirror(20f, -13f, reach, tan, near, sight),
                "one to either side of what it takes in is out");
        check(!Cabins.outOfMirror(20f, 12f, reach, tan, near, sight), "and one whose corner crosses the edge is not");
    }

    /** A body's matrix: its side, its up and its length in the world, each this long, column by column. */
    private static float[] stood(float[] side, float[] up, float[] length, float scale) {
        float[] stands = new float[16];
        for (int k = 0; k < 3; k++) {
            stands[k] = side[k] * scale;
            stands[4 + k] = up[k] * scale;
            stands[8 + k] = length[k] * scale;
        }
        stands[15] = 1f;
        return stands;
    }

    private static boolean are(float[] axes, float... wanted) {
        for (int k = 0; k < 9; k++) {
            if (!near(axes[k], wanted[k])) {
                return false;
            }
        }
        return true;
    }

    private static void cabinTipsAndRollsWithItsBody() {
        float[] axes = new float[9];
        float[] east = {1f, 0f, 0f}, north = {0f, 1f, 0f}, up = {0f, 0f, 1f}, west = {-1f, 0f, 0f}, south = {0f, -1f, 0f}, down = {0f, 0f, -1f};
        check(Cabins.axes(stood(south, up, east, 1.82f), faced(1f, 0f, 0f), axes) && are(axes, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
                "a level body facing east: ahead east, its left north, up straight up, whatever size it is drawn");
        check(Cabins.axes(stood(south, up, west, 1f), faced(1f, 0f, 0f), axes) && are(axes, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), "a body whose length runs to its back is the same");
        check(Cabins.axes(stood(north, up, east, 1f), faced(1f, 0f, 0f), axes) && are(axes, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), "and a body mirrored side to side");
        check(Cabins.axes(stood(east, up, north, 1f), faced(0f, 1f, 0f), axes) && are(axes, 0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f), "facing north its left is west");
        // Nose down by 5 degrees, facing east.
        float c = (float) Math.cos(Math.toRadians(5)), s = (float) Math.sin(Math.toRadians(5));
        check(Cabins.axes(stood(south, new float[] {s, 0f, c}, new float[] {c, 0f, -s}, 1.82f), faced(c, 0f, -s), axes) && are(axes, c, 0f, -s, 0f, 1f, 0f, s, 0f, c),
                "braking, the nose dips: ahead points down and the cabin's up leans forward with the body");
        // Rolled 5 degrees onto its right side, facing east: the left comes up.
        check(Cabins.axes(stood(new float[] {0f, -c, -s}, new float[] {0f, -s, c}, east, 1f), faced(1f, 0f, 0f), axes) && are(axes, 1f, 0f, 0f, 0f, c, s, 0f, -s, c),
                "in a bend the body rolls: the cabin's left rises and its up leans over, and ahead is as it was");
        // The physics is read a frame apart from the body: a few degrees between them is the same vehicle.
        check(Cabins.axes(stood(south, up, east, 1f), faced(c, s, 0f), axes) && are(axes, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), "the body's own length is taken, not the physics' word, where the two are a little apart");
        check(!Cabins.axes(stood(east, up, north, 1f), faced(1f, 0f, 0f), axes) && are(axes, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
                "a body turned across its vehicle is not taken for the vehicle: the cabin goes by the vehicle as the physics has it");
        // A crash turns a vehicle a long way between the physics being read and the body being drawn.
        float c40 = (float) Math.cos(Math.toRadians(40)), s40 = (float) Math.sin(Math.toRadians(40));
        check(Cabins.axes(stood(south, up, east, 1f), faced(c40, s40, 0f), axes) && are(axes, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), "forty degrees apart is still the body's own length");
        // A crash: on its right side, on its roof, and standing on its nose, where it has no heading on the map.
        check(Cabins.axes(stood(down, south, east, 1.82f), faced(1f, 0f, 0f), axes) && are(axes, 1f, 0f, 0f, 0f, 0f, 1f, 0f, -1f, 0f),
                "on its right side: the cabin's up lies flat along the ground and its left is straight up");
        check(Cabins.axes(stood(north, down, east, 1.82f), faced(1f, 0f, 0f), axes) && are(axes, 1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, -1f), "on its roof: the cabin's up is straight down and its left is to the south");
        check(Cabins.axes(stood(south, east, down, 1.82f), faced(0f, 0f, -1f), axes) && are(axes, 0f, 0f, -1f, 0f, 1f, 0f, 1f, 0f, 0f), "on its nose: ahead is straight down and the cabin's up is to the east");
        check(Cabins.axes(stood(south, east, up, 1.82f), faced(0f, 0f, -1f), axes) && are(axes, 0f, 0f, -1f, 0f, 1f, 0f, 1f, 0f, 0f), "the same for a body whose length runs to its back");
    }

    /** A vehicle facing this way with its up straight up, as the physics would have it: only the way it faces is read of it here. */
    private static float[] faced(float x, float y, float up) {
        float[] faces = new float[9];
        Cabins.axes(x, y, up, 0f, 0f, 1f, faces);
        return faces;
    }

    private static void eyeIsSeatedAlongTheVehiclesOwnAxes() {
        float[] axes = new float[9];
        Cabins.axes(1f, 0f, 0f, 0f, 0f, 1f, axes);
        check(are(axes, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), "a vehicle facing east and standing level: its left is north");
        Cabins.axes(0f, 1f, 0f, 0f, 0f, 1f, axes);
        check(are(axes, 0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f), "facing north its left is west");
        Cabins.axes(1f, 0f, 0f, 0f, 0f, -1f, axes);
        check(are(axes, 1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, -1f), "on its roof, facing east, its left is south");
        Cabins.axes(1f, 0f, 0f, 0f, -1f, 0f, axes);
        check(are(axes, 1f, 0f, 0f, 0f, 0f, 1f, 0f, -1f, 0f), "on its right side its left is straight up");
        // What the body's matrix gives and what the physics gives are the same three for the same vehicle.
        float[] body = new float[9];
        float c = (float) Math.cos(Math.toRadians(40)), s = (float) Math.sin(Math.toRadians(40));
        float[] length = {c, 0f, s}, high = {-s * c, s, c * c};
        float[] left = {high[1] * length[2] - high[2] * length[1], high[2] * length[0] - high[0] * length[2], high[0] * length[1] - high[1] * length[0]};
        Cabins.axes(length[0], length[1], length[2], high[0], high[1], high[2], axes);
        check(Cabins.axes(stood(new float[] {-left[0], -left[1], -left[2]}, high, length, 1.5f), axes, body) && are(body, axes),
                "nose up and rolled at once, the body's matrix and the physics give the same axes");
    }

    private static void viewTipsAndRollsWithTheVehicle() {
        float[] axes = new float[9], tilt = new float[9], lean = new float[9];
        float c = (float) Math.cos(Math.toRadians(30)), s = (float) Math.sin(Math.toRadians(30));
        // Level, heading north-east: nothing is turned.
        float h = (float) Math.sqrt(0.5);
        Cabins.axes(h, h, 0f, 0f, 0f, 1f, axes);
        Cabins.tilt(axes, h, h, tilt);
        check(are(tilt, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), "a level vehicle turns the view by nothing, whichever way it heads");
        // Heading east, rolled 30 degrees onto its right side: a view's right at yaw 0 is +y, and its up leans that way.
        Cabins.axes(1f, 0f, 0f, 0f, s, c, axes);
        Cabins.tilt(axes, 1f, 0f, tilt);
        ViewCamera.Pose view = new ViewCamera.Pose();
        ViewCamera.clean(view, 0f, 0f, 0f, 0f, 0f);
        ViewCamera.tilt(view, tilt);
        check(near(view.forwardX, 1f) && near(view.forwardY, 0f) && near(view.forwardUp, 0f), "rolled, a view straight ahead still looks straight ahead");
        check(near(view.upX, 0f) && near(view.upY, s) && near(view.upUp, c), "and its up is the vehicle's up");
        check(near(view.rightX, 0f) && near(view.rightY, c) && near(view.rightUp, -s), "and its right dips");
        // Looking out of the side window of a car on its right side is looking at the ground.
        Cabins.axes(1f, 0f, 0f, 0f, 1f, 0f, axes);
        Cabins.tilt(axes, 1f, 0f, tilt);
        ViewCamera.clean(view, 0f, 0f, 0f, (float) Math.toRadians(90), 0f);
        ViewCamera.tilt(view, tilt);
        check(near(view.forwardX, 0f) && near(view.forwardY, 0f) && near(view.forwardUp, -1f), "on its right side, the view out of the right window is straight down");
        // Nose down 30 degrees, heading north: straight ahead looks down the slope, and a lean forward goes down it.
        Cabins.axes(0f, c, -s, 0f, s, c, axes);
        Cabins.tilt(axes, 0f, 1f, tilt);
        float north = (float) Math.toRadians(90);
        ViewCamera.clean(view, 0f, 0f, 0f, north, 0f);
        ViewCamera.tilt(view, tilt);
        check(near(view.forwardX, 0f) && near(view.forwardY, c) && near(view.forwardUp, -s), "nose down, a view straight ahead looks down with the nose");
        HeadTracking.trackerToWorld(north, tilt, lean);
        check(near(-lean[2], 0f) && near(-lean[5], c) && near(-lean[8], -s), "and a lean forward, the tracker's z negative, goes down the slope with it");
        HeadTracking.trackerToWorld(north, null, lean);
        check(near(-lean[2], 0f) && near(-lean[5], 1f) && near(-lean[8], 0f), "on foot the same lean is level");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    private static void steeringWheelTurnsASixthOfATurnToFullLock() {
        check(Cabins.wheelTurn(0f) == 0f, "wheels straight, wheel straight");
        check(near(Cabins.wheelTurn(0.1f), 0.12f), "the wheel turns a fifth further than the game counts the steering");
        check(near(Cabins.wheelTurn(-0.25f), -0.3f), "and the same the other way");
        check(Math.abs(Math.toDegrees(Cabins.wheelTurn(0.9f)) - 62) < 0.5, "full lock is a sixth of a turn, 62 degrees");
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
