package com.cameraunlock.zomboid;

import java.util.Arrays;
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

    /**
     * UNSEEN is what of the body the picture builds its own in place of: its steering wheel, its
     * pillars and the front of its roof. It is not drawn, and what the game painted of it is
     * not taken for what stands behind it.
     */
    static final int OTHER = 0, SEAT = 1, DASH = 2, DOOR = 3, BONNET = 4, UNSEEN = 5, KINDS = 6;
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
    /**
     * The picture builds the front of a cabin's glasshouse itself: its pillars, the rail over
     * the screen and the roof. What the body has of its own there, above the sills and ahead of
     * this far behind the wheel's middle, is seen past. The picture's own pillar behind the
     * driver's door stands ahead of the line.
     */
    static final float BUILT_FROM = Cabins.EYE_BEHIND + 0.25f;
    /** A seat's back that is part of the shell stands this far in from the glass, at least, and behind the wheel. */
    private static final float SEAT_INSIDE = 0.05f, SEAT_BEHIND = 0.05f;

    static final class Found {
        /** For each triangle, one of the kinds or GLASS. */
        final byte[] kinds;
        /** How many loose pieces are seats. With none, the seats are part of the shell and are not told from it. */
        int seatPieces;
        /** The foot of the windscreen, along and up, and the foot of the driver's window. */
        float footAlong, footUp, sillUp;
        /** How far the foot of the driver's window is from the vehicle's middle line, and where that line is. */
        float glassOut, middleAcross;
        /** The top of the dashboard, and how far along its shelf begins on the side of the seats. */
        float dashTop, dashBack;
        /** For each triangle that is a pane, whether it is one of the front of the glasshouse, which the picture builds. */
        final boolean[] front;

        Found(int triangles) {
            kinds = new byte[triangles];
            front = new boolean[triangles];
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

    /** A steering wheel is before its driver by no more than this, in tiles, and at least this much of the driver's own way out from the middle line. */
    private static final float WHEEL_BEFORE = 1f, WHEEL_OUT = 0.5f;
    /** A wheel a vehicle mod models is in line with the driver: no further across from the seat than this share of the seat's own way out. */
    private static final float WHEEL_OFF_SEAT = 0.4f;
    /** How far across a modelled wheel may stand from a driver who sits on the middle line, as on a tractor. */
    private static final float WHEEL_OFF_CENTRE = 0.1f;

    /**
     * Which of a body's vertices are its steering wheel: a loose ring of 36 triangles, as wide
     * as it is tall. A body may carry other pieces of as many triangles, a spare wheel on its
     * back or a drum of cable, so where the driver's seat is known the wheel is one that
     * stands before that seat and on its side of the vehicle. Of those, or of them all where
     * none does or the seat is not known, it is the roundest. Null for a body with no such piece.
     *
     * @param at         along, across and up for each vertex
     * @param elements   three vertices a triangle
     * @param seat       along and how far out from the middle line the driver sits, or null
     * @param middleLine where across the vehicle's middle line is
     */
    static int[] wheelOf(float[] at, int[] elements, float[] seat, float middleLine) {
        int vertices = at.length / 3, triangles = elements.length / 3;
        int[] parent = pieces(at, elements);
        int[] count = new int[vertices];
        for (int t = 0; t < triangles; t++) {
            count[root(parent, elements[t * 3])]++;
        }
        int best = -1;
        float roundest = Float.MAX_VALUE;
        boolean bestPlaced = false;
        for (int piece = 0; piece < vertices; piece++) {
            if (count[piece] != WHEEL_TRIANGLES) {
                continue;
            }
            float[] low = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, high = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (int i = 0; i < vertices; i++) {
                if (root(parent, i) == piece) {
                    for (int axis = 0; axis < 3; axis++) {
                        low[axis] = Math.min(low[axis], at[i * 3 + axis]);
                        high[axis] = Math.max(high[axis], at[i * 3 + axis]);
                    }
                }
            }
            float[] sides = {high[0] - low[0], high[1] - low[1], high[2] - low[2]};
            Arrays.sort(sides);
            float unlike = sides[2] / Math.max(sides[1], 1e-6f);
            float before = (low[0] + high[0]) / 2f - (seat == null ? 0f : seat[0]), out = Math.abs((low[1] + high[1]) / 2f - middleLine);
            boolean placed = seat != null && before > 0f && before < WHEEL_BEFORE && out > WHEEL_OUT * seat[1];
            if (placed && !bestPlaced || placed == bestPlaced && unlike < roundest) {
                best = piece;
                roundest = unlike;
                bestPlaced = placed;
            }
        }
        if (best < 0) {
            return null;
        }
        int[] found = new int[vertices];
        int size = 0;
        for (int i = 0; i < vertices; i++) {
            if (root(parent, i) == best) {
                found[size++] = i;
            }
        }
        return Arrays.copyOf(found, size);
    }

    /** A modelled steering wheel is a loose piece of at least this many triangles, this wide across the vehicle in tiles. */
    private static final int ROUND_TRIANGLES = 24;
    private static final float ROUND_LEAST = 0.15f, ROUND_MOST = 0.6f;
    /** How far the face of a modelled wheel may lean from upright, towards lying flat, as a van's or a bus's does. The face is tried leant either way, as a model's along may run either way. */
    private static final float ROUND_MOST_LEAN = (float) Math.toRadians(80);

    /**
     * Which vertices of a vehicle mod's interior model are its steering wheel, modelled as the
     * mod's maker made it and not as the game's 36 triangle ring. A steering wheel faces the
     * driver: it is a flat round loose piece, as wide across the vehicle as it is tall in its own
     * face, which leans about the line across the vehicle anywhere from upright to most of
     * the way flat. It stands before the driver's seat and in line with it. The dials in the
     * dashboard are as round and flat but smaller. Of those that are, it is the widest. Null for
     * a model with none.
     *
     * @param at         along, across and up for each vertex
     * @param elements   three vertices a triangle
     * @param seat       along, and across from the middle line, where the driver sits
     * @param middleLine where across the vehicle's middle line is
     */
    static int[] roundWheelOf(float[] at, int[] elements, float[] seat, float middleLine) {
        int vertices = at.length / 3, triangles = elements.length / 3;
        int[] parent = pieces(at, elements);
        int[] count = new int[vertices];
        for (int t = 0; t < triangles; t++) {
            count[root(parent, elements[t * 3])]++;
        }
        int[] start = new int[vertices + 1];
        for (int i = 0; i < vertices; i++) {
            start[root(parent, i) + 1]++;
        }
        for (int i = 0; i < vertices; i++) {
            start[i + 1] += start[i];
        }
        int[] members = new int[vertices], filled = Arrays.copyOf(start, vertices);
        for (int i = 0; i < vertices; i++) {
            members[filled[root(parent, i)]++] = i;
        }
        int best = -1;
        float widest = 0f;
        for (int piece = 0; piece < vertices; piece++) {
            if (count[piece] < ROUND_TRIANGLES) {
                continue;
            }
            float[] low = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, high = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (int k = start[piece]; k < start[piece + 1]; k++) {
                for (int axis = 0; axis < 3; axis++) {
                    low[axis] = Math.min(low[axis], at[members[k] * 3 + axis]);
                    high[axis] = Math.max(high[axis], at[members[k] * 3 + axis]);
                }
            }
            float wide = high[1] - low[1];
            float midAlong = (low[0] + high[0]) / 2f, midAcross = (low[1] + high[1]) / 2f, midUp = (low[2] + high[2]) / 2f;
            float before = midAlong - seat[0], off = midAcross - middleLine - seat[1];
            if (wide < ROUND_LEAST || wide > ROUND_MOST || wide <= widest || before <= 0f || before >= WHEEL_BEFORE || Math.abs(off) > Math.max(WHEEL_OFF_SEAT * Math.abs(seat[1]), WHEEL_OFF_CENTRE)) {
                continue;
            }
            boolean round = true;
            for (int k = start[piece]; k < start[piece + 1] && round; k++) {
                int i = members[k];
                float a = at[i * 3] - midAlong, c = at[i * 3 + 1] - midAcross, u = at[i * 3 + 2] - midUp;
                round = a * a + c * c + u * u <= 0.36f * wide * wide;
            }
            if (!round) {
                continue;
            }
            // A wheel's middle is dished towards its column, so the piece is thin only against
            // how wide it is, and as tall in that face as it is wide.
            float[] face = faceOf(at, Arrays.copyOfRange(members, start[piece], start[piece + 1]));
            float thinnest = face[1], tall = face[2];
            if (thinnest < 0.6f * wide && tall > 0.75f * wide && tall < 1.3f * wide) {
                best = piece;
                widest = wide;
            }
        }
        if (best < 0) {
            return null;
        }
        int[] found = new int[start[best + 1] - start[best]];
        System.arraycopy(members, start[best], found, 0, found.length);
        return found;
    }

    /**
     * The face of a round flat piece that leans about the line across the vehicle: the lean,
     * tried a few degrees at a time, at which the piece is thinnest along the way it faces, as
     * radians from upright, a lean up turning that way ahead to point up; how thin it is that
     * way; how tall it is in that face; how far its middle stands ahead of its rim along that
     * way, the dish a steering wheel has towards its column; how far along that way the rim
     * stands from the middle of the box the piece fills; and that middle, along, across and up.
     * All in tiles.
     *
     * @param at    along, across and up for each vertex
     * @param piece the piece's vertices
     */
    static float[] faceOf(float[] at, int[] piece) {
        float thinnest = Float.MAX_VALUE, tall = 0f, best = 0f;
        for (float lean = -ROUND_MOST_LEAN; lean <= ROUND_MOST_LEAN + 1e-4f; lean += (float) Math.toRadians(5)) {
            float faceAlong = (float) Math.cos(lean), faceUp = (float) Math.sin(lean);
            float nearest = Float.MAX_VALUE, furthest = -Float.MAX_VALUE, lowest = Float.MAX_VALUE, highest = -Float.MAX_VALUE;
            for (int i : piece) {
                float a = at[i * 3], u = at[i * 3 + 2];
                float deep = a * faceAlong + u * faceUp, rise = u * faceAlong - a * faceUp;
                nearest = Math.min(nearest, deep);
                furthest = Math.max(furthest, deep);
                lowest = Math.min(lowest, rise);
                highest = Math.max(highest, rise);
            }
            if (furthest - nearest < thinnest) {
                thinnest = furthest - nearest;
                tall = highest - lowest;
                best = lean;
            }
        }
        // The dish: the mean depth of what is within a third of the radius of the middle in
        // the face, less that of what is past two thirds of it.
        float faceAlong = (float) Math.cos(best), faceUp = (float) Math.sin(best);
        float[] low = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, high = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i : piece) {
            for (int axis = 0; axis < 3; axis++) {
                low[axis] = Math.min(low[axis], at[i * 3 + axis]);
                high[axis] = Math.max(high[axis], at[i * 3 + axis]);
            }
        }
        float midA = (low[0] + high[0]) / 2f, midC = (low[1] + high[1]) / 2f, midU = (low[2] + high[2]) / 2f;
        float radius = (high[1] - low[1]) / 2f;
        float hub = 0f, rim = 0f;
        int hubs = 0, rims = 0;
        for (int i : piece) {
            float a = at[i * 3] - midA, c = at[i * 3 + 1] - midC, u = at[i * 3 + 2] - midU;
            float deep = a * faceAlong + u * faceUp, rise = u * faceAlong - a * faceUp;
            float out = (float) Math.sqrt(c * c + rise * rise);
            if (out < radius / 3f) {
                hub += deep;
                hubs++;
            } else if (out > radius * 2f / 3f) {
                rim += deep;
                rims++;
            }
        }
        float rimDeep = rims > 0 ? rim / rims : 0f;
        float dish = hubs > 0 && rims > 0 ? hub / hubs - rimDeep : 0f;
        return new float[] {best, thinnest, tall, dish, rimDeep, midA, midC, midU};
    }

    /**
     * The places on a window pane the cabin's glass is laid out by (CabinGlass, GlassRain), in
     * tiles, as the mod's own interiors lay out theirs (tools/models/cabin/make.py glass_places):
     * on the windscreen, across from its middle with the driver's side below nought, and up its
     * slant from CabinModel.WIPERS_BELOW under its foot; on a side window, along the vehicle
     * from 7 tiles behind the pane's middle; on any other, across from its middle; on both of
     * those, up from the pane's foot. Then how far the pane runs either way of its middle, how
     * far up it, and 1 where it is modelled as a slab with both its faces, or 0; then a place for
     * each vertex, two numbers each.
     *
     * @param at           along, across and up for each vertex
     * @param elements     the pane's triangles
     * @param driverAcross how far across the driver sits, as at counts it
     */
    static float[] paneFace(float[] at, int[] elements, boolean screen, float driverAcross) {
        int vertices = at.length / 3;
        // The pane's way: its faces summed, each turned to agree with those before it.
        float nx = 0f, ny = 0f, nz = 0f;
        float[] faces = new float[elements.length];
        for (int t = 0; t + 2 < elements.length; t += 3) {
            int a = elements[t] * 3, b = elements[t + 1] * 3, c = elements[t + 2] * 3;
            float ux = at[b] - at[a], uy = at[b + 1] - at[a + 1], uz = at[b + 2] - at[a + 2];
            float vx = at[c] - at[a], vy = at[c + 1] - at[a + 1], vz = at[c + 2] - at[a + 2];
            float fx = uy * vz - uz * vy, fy = uz * vx - ux * vz, fz = ux * vy - uy * vx;
            faces[t] = fx;
            faces[t + 1] = fy;
            faces[t + 2] = fz;
            float sign = fx * nx + fy * ny + fz * nz < 0f ? -1f : 1f;
            nx += sign * fx;
            ny += sign * fy;
            nz += sign * fz;
        }
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 0f) {
            nx /= length;
            ny /= length;
            nz /= length;
        } else {
            nx = 1f;
        }
        // A slab has as much of its face turned one way as the other.
        float one = 0f, other = 0f;
        for (int t = 0; t + 2 < elements.length; t += 3) {
            float along = faces[t] * nx + faces[t + 1] * ny + faces[t + 2] * nz;
            if (along > 0f) {
                one += along;
            } else {
                other -= along;
            }
        }
        boolean slab = Math.min(one, other) > 0.25f * Math.max(one, other);
        // Up the windscreen's slant is the vehicle's up less what of it is along the pane's way.
        float sx = -nz * nx, sy = -nz * ny, sz = 1f - nz * nz;
        float slantLength = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        boolean side = Math.abs(ny) >= Math.abs(nx);
        boolean[] used = new boolean[vertices];
        for (int element : elements) {
            used[element] = true;
        }
        float least = Float.MAX_VALUE, most = -Float.MAX_VALUE, foot = Float.MAX_VALUE, top = -Float.MAX_VALUE;
        float[] out = new float[4 + vertices * 2];
        for (int i = 0; i < vertices; i++) {
            float x = at[i * 3], y = at[i * 3 + 1], z = at[i * 3 + 2];
            float level = screen || !side ? y : x;
            float up = screen && slantLength > 0f ? (x * sx + y * sy + z * sz) / slantLength : z;
            out[4 + i * 2] = level;
            out[4 + i * 2 + 1] = up;
            if (used[i]) {
                least = Math.min(least, level);
                most = Math.max(most, level);
                foot = Math.min(foot, up);
                top = Math.max(top, up);
            }
        }
        float middle = (least + most) / 2f;
        float turn = screen && driverAcross > middle ? -1f : 1f;
        float shift = screen || !side ? -middle : 7f - middle;
        float rise = screen ? -foot - CabinModel.WIPERS_BELOW : -foot;
        for (int i = 0; i < vertices; i++) {
            out[4 + i * 2] = (out[4 + i * 2] + shift) * turn;
            out[4 + i * 2 + 1] += rise;
        }
        out[0] = (most - least) / 2f;
        out[1] = top - foot;
        out[2] = slab ? 1f : 0f;
        return out;
    }

    /**
     * Where a driver's hand closes round the rim of a steering wheel as wide across as given,
     * from its middle: on the middle of the rim, which is a little in from its outer edge.
     */
    static float gripRadius(float wide) {
        return wide / 2f * (1f - HALF_RIM);
    }

    /**
     * How much of a steering wheel's outer radius is half the thickness of its rim, as the mod's
     * saloon interior has it (tools/models/cabin/sedan.py: the rim's middle 0.188 out, its tube
     * 0.0118 either side of that).
     */
    static final float HALF_RIM = 0.0118f / (0.188f + 0.0118f);

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

        int[] parent = pieces(at, elements);
        float least = Float.MAX_VALUE, most = -Float.MAX_VALUE;
        for (int i = 0; i < vertices; i++) {
            least = Math.min(least, at[i * 3 + 1]);
            most = Math.max(most, at[i * 3 + 1]);
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
        // They are seen past, like the glass before them: the picture builds its own on the
        // pillars it builds.
        boolean[] outside = new boolean[vertices];
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
        }

        found.dashTop = -Float.MAX_VALUE;
        float builtFrom = wheel[0] - BUILT_FROM, belt = Math.min(found.sillUp, found.footUp);
        for (int t = 0; t < triangles; t++) {
            float foremost = Math.max(at[elements[t * 3] * 3], Math.max(at[elements[t * 3 + 1] * 3], at[elements[t * 3 + 2] * 3]));
            if (glass[t] || outside[piece[t]]) {
                found.kinds[t] = GLASS;
                found.front[t] = glass[t] && foremost > builtFrom;
                continue;
            }
            if (seatPiece[piece[t]]) {
                found.kinds[t] = SEAT;
                continue;
            }
            if (piece[t] == wheelPiece) {
                // The picture draws a wheel of its own where the body's six-sided one is.
                found.kinds[t] = UNSEEN;
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
            } else if (middle[2] > belt + 0.02f && foremost > builtFrom && middle[0] <= found.footAlong + 0.05f
                    && (outermost(at, elements, t, found.middleAcross) > found.glassOut - SEAT_INSIDE || foremost > wheel[0] - SEAT_BEHIND
                            || highest(at, elements, t) > wheel[2] + SEAT_TOP + 0.03f)) {
                // The body's own pillars, the frames of its front windows and the front of its roof.
                found.kinds[t] = UNSEEN;
            } else if (!inward && -faces[1] * Math.signum(out) < -0.5f && Math.abs(out) > found.glassOut - 0.08f && middle[2] > belt - 0.12f && foremost > builtFrom
                    && middle[0] <= found.footAlong + 0.05f) {
                // The lip of a front door's outer skin, which from the seat would show over the door's top.
                found.kinds[t] = UNSEEN;
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
        // The shelf under the screen begins where the highest of the dashboard is nearest the seats.
        found.dashBack = found.footAlong;
        for (int t = 0; t < triangles; t++) {
            if (found.kinds[t] != DASH) {
                continue;
            }
            for (int corner = 0; corner < 3; corner++) {
                int v = elements[t * 3 + corner];
                if (at[v * 3 + 2] >= found.dashTop - 0.03f) {
                    found.dashBack = Math.min(found.dashBack, at[v * 3]);
                }
            }
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

    /** How far out from the middle line the corner of a triangle furthest from it is. */
    private static float outermost(float[] at, int[] elements, int t, float middleAcross) {
        return Math.max(Math.abs(at[elements[t * 3] * 3 + 1] - middleAcross),
                Math.max(Math.abs(at[elements[t * 3 + 1] * 3 + 1] - middleAcross), Math.abs(at[elements[t * 3 + 2] * 3 + 1] - middleAcross)));
    }

    private static float highest(float[] at, int[] elements, int t) {
        return Math.max(at[elements[t * 3] * 3 + 2], Math.max(at[elements[t * 3 + 1] * 3 + 2], at[elements[t * 3 + 2] * 3 + 2]));
    }

    private static float lowest(float[] at, int[] elements, int t) {
        return Math.min(at[elements[t * 3] * 3 + 2], Math.min(at[elements[t * 3 + 1] * 3 + 2], at[elements[t * 3 + 2] * 3 + 2]));
    }

    /**
     * The loose pieces of a body: for each vertex one that root takes to the same vertex as every
     * other of its piece. Vertices at one place are one vertex, however many faces meet there.
     */
    private static int[] pieces(float[] at, int[] elements) {
        int vertices = at.length / 3, triangles = elements.length / 3;
        HashMap<Place, Integer> places = new HashMap<>();
        int[] parent = new int[vertices];
        for (int i = 0; i < vertices; i++) {
            Integer first = places.putIfAbsent(new Place(Math.round(at[i * 3] * 20000f), Math.round(at[i * 3 + 1] * 20000f), Math.round(at[i * 3 + 2] * 20000f)), i);
            parent[i] = first == null ? i : first;
        }
        for (int t = 0; t < triangles; t++) {
            int a = root(parent, elements[t * 3]);
            parent[root(parent, elements[t * 3 + 1])] = a;
            parent[root(parent, elements[t * 3 + 2])] = a;
        }
        return parent;
    }

    private static int root(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }
}
