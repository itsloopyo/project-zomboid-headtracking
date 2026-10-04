package com.cameraunlock.zomboid;

import java.util.HashMap;

/**
 * What each triangle of a vehicle's body is, told from its shape alone: a seat, the dashboard,
 * the card of a door, or the bodywork ahead of the windscreen. The game's bodies are one mesh
 * in one material, but their seats and steering wheel are loose pieces of it, and the rest is
 * placed by the wheel, the glass and where the script seats its passengers.
 *
 * Everything here is measured in tiles along the vehicle, across it and up.
 */
final class CabinParts {
    private CabinParts() {}

    static final int OTHER = 0, SEAT = 1, DASH = 2, DOOR = 3, BONNET = 4, KINDS = 5;
    /** A pane, or what stands outside one: looked through or past, so no part of the cabin. */
    static final byte GLASS = -1;

    /** How many triangles the wheel is in every body. */
    private static final int WHEEL_TRIANGLES = 36;
    /** Where the foot of the windscreen and the sills are from the wheel's middle in the standard car, for a body whose glass is not known. */
    private static final float FOOT_AHEAD = 0.37f, FOOT_ABOVE = 0.07f, GLASS_OUT = 0.41f;
    /**
     * The dashboard begins this far past the wheel's middle, runs this far past the foot of the
     * screen where its board slants away to the floor, and is this deep below the wheel's middle.
     */
    private static final float DASH_FROM = 0.05f, DASH_PAST = 0.1f, DASH_DEEP = 0.45f;
    /** How far above the wheel's middle the top of a seat's back may be. */
    private static final float SEAT_TOP = 0.35f;
    /** A door mirror or its arm is a loose piece of no more triangles than this. */
    private static final int MIRROR_TRIANGLES = 24;
    /** Where a door mirror stands on a body that has none of its own: back from the foot of the screen, out from the glass and above the sill. */
    private static final float MIRROR_BACK = 0.16f, MIRROR_OUT = 0.12f, MIRROR_UP = 0.03f;

    static final class Found {
        /** For each triangle, one of the kinds or GLASS. */
        final byte[] kinds;
        /** How many loose pieces are seats. With none, the seats are part of the shell and are not told from it. */
        int seatPieces;
        /** The foot of the windscreen, along and up, and the foot of the driver's window. */
        float footAlong, footUp, sillUp;
        /** How far the foot of the driver's window is from the vehicle's middle line, and where that line is. */
        float glassOut, middleAcross;
        /** The top of the dashboard. */
        float dashTop;
        /** The middle of the mirror on the driver's door: along, across and up. */
        float[] mirror;

        Found(int triangles) {
            kinds = new byte[triangles];
        }
    }

    /**
     * Where the script's seats are in the body's own frame. The script counts across the
     * vehicle one way and the model may count it the other: the driver, the first seat, is
     * on the side the wheel is.
     *
     * @param offsets across, up and along for each seat, from the model's middle, as the script has them
     * @return along and across for each seat
     */
    static float[] seats(float[] offsets, float middleAlong, float middleAcross, float wheelAcross) {
        if (offsets.length == 0) {
            // A script that seats nobody inside: there are no seats to find.
            return offsets;
        }
        float way = Math.signum(wheelAcross - middleAcross) * Math.signum(offsets[0]);
        if (way == 0f) {
            way = 1f;
        }
        float[] seats = new float[offsets.length / 3 * 2];
        for (int seat = 0; seat < seats.length / 2; seat++) {
            seats[seat * 2] = middleAlong + offsets[seat * 3 + 2];
            seats[seat * 2 + 1] = middleAcross + way * offsets[seat * 3];
        }
        return seats;
    }

    private record Place(int along, int across, int up) {}

    /**
     * @param at       along, across and up for each vertex
     * @param facing   the way each vertex's surface faces, in the same frame
     * @param elements three vertices a triangle
     * @param glass    whether each triangle is a pane
     * @param wheel    the middle of the steering wheel
     * @param seats    along and across for each seat
     */
    static Found classify(float[] at, float[] facing, int[] elements, boolean[] glass, float[] wheel, float[] seats) {
        int vertices = at.length / 3, triangles = elements.length / 3;
        Found found = new Found(triangles);

        // Vertices at one place are one vertex, however many faces meet there.
        HashMap<Place, Integer> places = new HashMap<>();
        int[] parent = new int[vertices];
        float least = Float.MAX_VALUE, most = -Float.MAX_VALUE;
        for (int i = 0; i < vertices; i++) {
            Place place = new Place(Math.round(at[i * 3] * 20000f), Math.round(at[i * 3 + 1] * 20000f), Math.round(at[i * 3 + 2] * 20000f));
            Integer first = places.putIfAbsent(place, i);
            parent[i] = first == null ? i : first;
            least = Math.min(least, at[i * 3 + 1]);
            most = Math.max(most, at[i * 3 + 1]);
        }
        for (int t = 0; t < triangles; t++) {
            int a = root(parent, elements[t * 3]);
            parent[root(parent, elements[t * 3 + 1])] = a;
            parent[root(parent, elements[t * 3 + 2])] = a;
        }
        found.middleAcross = (least + most) / 2f;
        float wheelSide = Math.signum(wheel[1] - found.middleAcross);

        // Each loose piece: how many triangles, and the box round it.
        int[] count = new int[vertices];
        float[] box = new float[vertices * 6];
        for (int i = 0; i < vertices; i++) {
            for (int axis = 0; axis < 3; axis++) {
                box[i * 6 + axis] = Float.MAX_VALUE;
                box[i * 6 + 3 + axis] = -Float.MAX_VALUE;
            }
        }
        int[] piece = new int[triangles];
        int shell = -1;
        for (int t = 0; t < triangles; t++) {
            int r = root(parent, elements[t * 3]);
            piece[t] = r;
            count[r]++;
            if (shell < 0 || count[r] > count[shell]) {
                shell = r;
            }
            for (int corner = 0; corner < 3; corner++) {
                int v = elements[t * 3 + corner];
                for (int axis = 0; axis < 3; axis++) {
                    box[r * 6 + axis] = Math.min(box[r * 6 + axis], at[v * 3 + axis]);
                    box[r * 6 + 3 + axis] = Math.max(box[r * 6 + 3 + axis], at[v * 3 + axis]);
                }
            }
        }
        int wheelPiece = -1;
        float nearest = 0.05f;
        for (int r = 0; r < vertices; r++) {
            if (count[r] != WHEEL_TRIANGLES) {
                continue;
            }
            float off = 0f;
            for (int axis = 0; axis < 3; axis++) {
                off = Math.max(off, Math.abs((box[r * 6 + axis] + box[r * 6 + 3 + axis]) / 2f - wheel[axis]));
            }
            if (off < nearest) {
                nearest = off;
                wheelPiece = r;
            }
        }

        // A seat is a loose piece, not the shell and not most of the body, that a passenger sits in.
        boolean[] seatPiece = new boolean[vertices];
        boolean[] seated = new boolean[seats.length / 2];
        float seatLow = Float.MAX_VALUE, seatHigh = -Float.MAX_VALUE;
        for (int r = 0; r < vertices; r++) {
            if (count[r] == 0 || r == shell || r == wheelPiece || count[r] > triangles / 4) {
                continue;
            }
            // A light bar or a taxi's sign stands over the seats too, on the roof. A seat
            // starts below the wheel and ends not far above it.
            if (box[r * 6 + 2] > wheel[2] || box[r * 6 + 5] > wheel[2] + SEAT_TOP) {
                continue;
            }
            for (int seat = 0; seat < seated.length; seat++) {
                float along = seats[seat * 2], across = seats[seat * 2 + 1];
                if (along > box[r * 6] - 0.03f && along < box[r * 6 + 3] + 0.03f && across > box[r * 6 + 1] - 0.03f && across < box[r * 6 + 4] + 0.03f) {
                    seated[seat] = true;
                    if (!seatPiece[r]) {
                        seatPiece[r] = true;
                        found.seatPieces++;
                        seatLow = Math.min(seatLow, box[r * 6 + 2]);
                        seatHigh = Math.max(seatHigh, box[r * 6 + 5]);
                    }
                }
            }
        }

        // The glass: the windscreen leans back, so its foot is its lowest and foremost edge.
        // The driver's window stands at the side, and its foot is the sill.
        float[] middle = new float[3], faces = new float[3];
        boolean screen = false, window = false;
        float footUp = Float.MAX_VALUE, sillUp = Float.MAX_VALUE;
        for (int t = 0; t < triangles; t++) {
            if (!glass[t]) {
                continue;
            }
            measure(at, facing, elements, t, middle, faces);
            boolean ends = Math.abs(faces[1]) < 0.5f;
            if (ends && middle[0] > wheel[0] - 0.25f) {
                screen = true;
                footUp = Math.min(footUp, lowest(at, elements, t));
            } else if (!ends && Math.signum(middle[1] - found.middleAcross) == wheelSide && middle[0] > wheel[0] - 0.6f) {
                window = true;
                sillUp = Math.min(sillUp, lowest(at, elements, t));
            }
        }
        float footAlong = -Float.MAX_VALUE, glassOut = 0f;
        for (int t = 0; t < triangles; t++) {
            if (!glass[t]) {
                continue;
            }
            measure(at, facing, elements, t, middle, faces);
            boolean ends = Math.abs(faces[1]) < 0.5f;
            for (int corner = 0; corner < 3; corner++) {
                int v = elements[t * 3 + corner];
                if (ends && middle[0] > wheel[0] - 0.25f && at[v * 3 + 2] <= footUp + 0.04f) {
                    footAlong = Math.max(footAlong, at[v * 3]);
                } else if (!ends && Math.signum(middle[1] - found.middleAcross) == wheelSide && middle[0] > wheel[0] - 0.6f && at[v * 3 + 2] <= sillUp + 0.04f) {
                    glassOut = Math.max(glassOut, Math.abs(at[v * 3 + 1] - found.middleAcross));
                }
            }
        }
        found.footAlong = screen ? footAlong : wheel[0] + FOOT_AHEAD;
        found.footUp = screen ? footUp : wheel[2] + FOOT_ABOVE;
        found.sillUp = window ? sillUp : found.footUp;
        found.glassOut = window ? glassOut : Math.abs(wheel[1] - found.middleAcross) + GLASS_OUT;

        // The body's own door mirrors are small loose pieces outside the windows, by the sills.
        // They are seen past, like the glass before them, and the driver's says where the
        // picture's own is to stand.
        boolean[] outside = new boolean[vertices];
        float[] mirror = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int r = 0; r < vertices; r++) {
            if (count[r] == 0 || count[r] > MIRROR_TRIANGLES || r == wheelPiece || seatPiece[r]) {
                continue;
            }
            float nearSide = Math.min(Math.abs(box[r * 6 + 1] - found.middleAcross), Math.abs(box[r * 6 + 4] - found.middleAcross));
            boolean oneSide = Math.signum(box[r * 6 + 1] - found.middleAcross) == Math.signum(box[r * 6 + 4] - found.middleAcross);
            if (!oneSide || nearSide < found.glassOut - 0.05f || box[r * 6 + 2] < found.sillUp - 0.12f || box[r * 6 + 5] > found.sillUp + 0.35f
                    || box[r * 6] < wheel[0] - 0.2f || box[r * 6 + 3] > found.footAlong + 0.1f) {
                continue;
            }
            outside[r] = true;
            if (Math.signum(box[r * 6 + 1] - found.middleAcross) == wheelSide) {
                for (int axis = 0; axis < 3; axis++) {
                    mirror[axis] = Math.min(mirror[axis], box[r * 6 + axis]);
                    mirror[3 + axis] = Math.max(mirror[3 + axis], box[r * 6 + 3 + axis]);
                }
            }
        }
        if (mirror[0] <= mirror[3]) {
            found.mirror = new float[] {(mirror[0] + mirror[3]) / 2f, (mirror[1] + mirror[4]) / 2f, (mirror[2] + mirror[5]) / 2f};
        } else {
            found.mirror = new float[] {found.footAlong - MIRROR_BACK, found.middleAcross + wheelSide * (found.glassOut + MIRROR_OUT), found.sillUp + MIRROR_UP};
        }

        found.dashTop = -Float.MAX_VALUE;
        for (int t = 0; t < triangles; t++) {
            if (glass[t] || outside[piece[t]]) {
                found.kinds[t] = GLASS;
                continue;
            }
            if (seatPiece[piece[t]]) {
                found.kinds[t] = SEAT;
                continue;
            }
            if (piece[t] == wheelPiece) {
                continue;
            }
            measure(at, facing, elements, t, middle, faces);
            float out = middle[1] - found.middleAcross;
            boolean inward = -faces[1] * Math.signum(out) > 0.6f;
            if (inward && Math.abs(out) > 0.6f * found.glassOut && middle[2] <= found.sillUp + 0.02f && middle[2] >= wheel[2] - 0.6f
                    && middle[0] < found.footAlong && middle[0] > wheel[0] - 2.2f) {
                found.kinds[t] = DOOR;
            } else if (middle[0] >= wheel[0] + DASH_FROM && middle[0] <= found.footAlong + DASH_PAST && middle[2] <= found.footUp + 0.06f && middle[2] >= wheel[2] - DASH_DEEP
                    && Math.abs(out) <= found.glassOut + 0.02f && (faces[0] < -0.3f || faces[2] > 0.5f)) {
                // Past the wheel and before the screen, between the doors, facing the seats or the roof:
                // the shelf under the screen, the panel the instruments are in and the board below it.
                found.kinds[t] = DASH;
                for (int corner = 0; corner < 3; corner++) {
                    found.dashTop = Math.max(found.dashTop, at[elements[t * 3 + corner] * 3 + 2]);
                }
            } else if (middle[0] > found.footAlong && faces[2] > 0.25f && middle[2] > found.footUp - 0.35f && middle[2] < found.footUp + 0.1f) {
                // What faces the sky ahead of the screen. The insides of the wheel arches are there too, and face down.
                found.kinds[t] = BONNET;
            } else if (found.seatPieces > 0 && middle[2] >= seatLow && middle[2] <= seatHigh) {
                // A bench that is part of the shell, where a passenger sits whom no loose piece seats.
                for (int seat = 0; seat < seated.length; seat++) {
                    if (!seated[seat] && Math.abs(middle[1] - seats[seat * 2 + 1]) < 0.3f && middle[0] > seats[seat * 2] - 0.35f && middle[0] < seats[seat * 2] + 0.3f) {
                        found.kinds[t] = SEAT;
                    }
                }
            }
        }
        if (found.dashTop == -Float.MAX_VALUE) {
            found.dashTop = found.footUp;
        }
        return found;
    }

    /** The middle of a triangle and the way it faces, as its vertices say. */
    private static void measure(float[] at, float[] facing, int[] elements, int t, float[] middle, float[] faces) {
        for (int axis = 0; axis < 3; axis++) {
            middle[axis] = 0f;
            faces[axis] = 0f;
            for (int corner = 0; corner < 3; corner++) {
                int v = elements[t * 3 + corner];
                middle[axis] += at[v * 3 + axis] / 3f;
                faces[axis] += facing[v * 3 + axis];
            }
        }
        float length = Math.max((float) Math.sqrt(faces[0] * faces[0] + faces[1] * faces[1] + faces[2] * faces[2]), 1e-6f);
        for (int axis = 0; axis < 3; axis++) {
            faces[axis] /= length;
        }
    }

    private static float lowest(float[] at, int[] elements, int t) {
        return Math.min(at[elements[t * 3] * 3 + 2], Math.min(at[elements[t * 3 + 1] * 3 + 2], at[elements[t * 3 + 2] * 3 + 2]));
    }

    private static int root(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }
}
