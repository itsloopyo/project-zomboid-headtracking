package com.cameraunlock.zomboid;

/**
 * Where the parts of a firearm that a hand holds or works are, read from the firearm's own
 * mesh: the middle of its grip where the knuckles of the hand round it are and the line the
 * grip runs along, its underside ahead of the trigger where the other hand holds it, a pump's
 * fore-end, the magazine and the well it goes into, the rear of a handgun's slide, a bolt's
 * knob and the port rounds go in at.
 *
 * The mesh is read as its maker drew it from the side: its outline in the plane of its barrel
 * and its top, with the trigger guard a hole in it. The grip is what is behind that hole and
 * under it, and the hand's fingers lie along the edge of the grip that faces the hole.
 *
 * Every place is in the mesh's own axes and units, from its origin. Pure.
 */
final class WeaponShape {
    /** How finely the outline is read: this many cells along the way from the origin to the muzzle. */
    static final int CELLS_TO_MUZZLE = 400;
    /**
     * A firearm's outline is read in no more cells than this: a mesh that would need more is
     * drawn far beyond its muzzle and is no firearm the outline can be read from. The game's
     * firearms need 100 to 200 thousand, and the outline is read on the render thread the first
     * time a firearm is drawn.
     */
    static final int MOST_CELLS = 1_000_000;
    /** A hole in the outline smaller across than this, in metres, is a gap between parts, not a trigger guard a finger goes through. */
    static final float SMALLEST_GUARD = 0.008f;
    /** A guard further ahead of the origin than this, in metres, is not where the hand at the origin has its finger. */
    static final float GUARD_WITHIN = 0.15f;
    /** How far down the grip's front edge from under the guard the line of the grip is read, in metres: the first two fingers' width. */
    static final float STRAP_READ = 0.035f;
    /** Further back than this behind the guard, in metres, the edge under it is the stock's belly, not the grip's front. */
    static final float STRAP_BEHIND = 0.12f;
    /** What is thinner than this along the barrel, in metres, is a bar a finger goes round, not something a hand holds. */
    static final float THINNEST_HELD = 0.007f;
    /** The most cells the grip's front edge goes back from one row to the next: less steep than about seven degrees, it is no grip's edge. */
    static final int STEEPEST_STEP = 8;
    /** The furthest a grip's line leans from upright, forward or back, in radians. */
    static final float GRIP_LEANS_MOST = (float) Math.toRadians(50);
    /** How far down the grip's front edge the middle of the knuckles of the hand round it is, in metres: the first finger is on the trigger above it. */
    static final float KNUCKLES_BELOW = 0.022f;
    /** How thick a grip is taken to be from its front edge to its back, at the least and the most, in metres. */
    static final float THINNEST_GRIP = 0.02f, THICKEST_GRIP = 0.05f;
    /** How far ahead of the guard, in metres, the receiver's underside goes before the fore-end begins. */
    static final float FORE_AFTER = 0.03f;
    /** How far under the bore a magazine reaches past the guard's foot before it is one, in metres. */
    static final float MAGAZINE_DEEPER = 0.02f;
    /** How far above and below where a magazine is held its front edge is read for how it leans, in metres. */
    static final float LEANS_OVER = 0.02f;
    /** How far over the underside of a fore-end the middle of the hand under it is, at the most, in metres. */
    static final float FORE_HALF = 0.024f;
    /** A level run of underside rises and falls no more than this, in metres. */
    static final float LEVEL_WITHIN = 0.005f;
    /** A fore-end hangs within this of the lowest of the underside past the receiver, in metres: higher is a bare barrel. */
    static final float FORE_DEEP = 0.01f;
    /** How far ahead of where the underside is read from the fore-end may begin, in metres: the receiver is before it. */
    static final float RECEIVER_AHEAD = 0.06f;
    /** How far ahead of the rear of a slide the hand that racks it takes hold of it, and how far under its top, in metres. */
    static final float SLIDE_HELD = 0.025f, SLIDE_UNDER = 0.012f;

    /** The mesh's axes: along the barrel to the muzzle, up to its top, and to the side. */
    final float[] along, up, side;
    /** The middle of the grip at the knuckles, in the mesh's axes, and the line of the grip from the little finger's end to the first finger's, of length one. */
    final float[] grip = new float[3], gripLine = new float[3];
    /** How thick the grip is from its front edge to its back at the knuckles, in the mesh's units. */
    final float gripThick;
    /** Where the trigger guard is: its back, its front, its foot and its top, along the barrel and up, in the mesh's units. */
    final float guardBack, guardFront, guardFoot, guardTop;
    /** The foot of the grip, where a handgun's magazine goes in, in the mesh's axes. */
    final float[] gripFoot = new float[3];
    /** How far up the bore is, along the barrel the muzzle is, in the mesh's units, and the mesh's units in a metre. */
    final float bore, muzzle, perMetre;
    /** The underside ahead of the guard: where along the barrel it begins and ends, and how far up it is at each step of `step` between. */
    final float foreFrom, foreTo, step;
    final float[] underside;
    /** The longest level run of the underside past the receiver, a pump's fore-end or a handguard, along the barrel: its back and its front. */
    final float levelFrom, levelTo;
    /** Whether something hangs under the receiver ahead of the guard, a magazine, and along the barrel its back, front and foot. */
    final boolean magazine;
    final float magazineBack, magazineFront, magazineFoot;
    /**
     * Of a magazine that hangs under the receiver: how far up half way down what shows of it is, how far
     * along the barrel its front edge is there, and how far ahead that edge comes for each unit further
     * down, as a curved magazine's does. Nought where there is no such magazine.
     */
    final float magazineMiddle, magazineEdge, magazineLeans;
    /** Where a hand takes hold of the rear of the slide, in the mesh's axes. */
    final float[] slide = new float[3];
    /** The part furthest out to either side over the trigger, a bolt's knob, in the mesh's axes. */
    final float[] knob = new float[3];

    private WeaponShape(float[] along, float[] up, float bore, float muzzle, float perMetre, Outline outline, Hole guard) {
        this.along = along;
        this.up = up;
        side = new float[3];
        ArmPose.cross(along, up, side);
        this.bore = bore;
        this.muzzle = muzzle;
        this.perMetre = perMetre;
        float cell = outline.cell;
        guardBack = outline.a(guard.backColumn);
        guardFront = outline.a(guard.frontColumn + 1);
        guardFoot = outline.u(guard.footRow);
        guardTop = outline.u(guard.topRow + 1);

        // The grip's front edge: behind the guard, the first thing thick enough to hold met going back from it, row by row down.
        // Under the guard first. Where that finds no edge, as under a lever's loop that hangs below the grip, it is read from the hole's own rows.
        float[] edgeA = new float[outline.rows], edgeU = new float[outline.rows];
        int found = edge(outline, guard, false, perMetre, edgeA, edgeU);
        if (found < 3) {
            found = edge(outline, guard, true, perMetre, edgeA, edgeU);
        }
        if (found < 3) {
            throw new IllegalStateException("the grip has no front edge under the trigger guard: " + found + " rows of it");
        }
        // The line through the edge, least squares, pointing up.
        float meanA = 0f, meanU = 0f;
        for (int i = 0; i < found; i++) {
            meanA += edgeA[i] / found;
            meanU += edgeU[i] / found;
        }
        float aa = 0f, au = 0f, uu = 0f;
        for (int i = 0; i < found; i++) {
            float da = edgeA[i] - meanA, du = edgeU[i] - meanU;
            aa += da * da;
            au += da * du;
            uu += du * du;
        }
        float angle = 0.5f * (float) Math.atan2(2f * au, aa - uu);
        float lineA = (float) Math.cos(angle), lineU = (float) Math.sin(angle);
        if (lineU < 0f) {
            lineA = -lineA;
            lineU = -lineU;
        }
        // A straight stock's belly runs back nearly level, and a fist round its wrist still has its knuckles up the wrist, not along it.
        float leans = Math.clamp((float) Math.atan2(lineA, lineU), -GRIP_LEANS_MOST, GRIP_LEANS_MOST);
        lineA = (float) Math.sin(leans);
        lineU = (float) Math.cos(leans);
        // The knuckles are down the edge from its top, and the grip's middle is half its thickness in from them.
        float topA = meanA + lineA * ((edgeA[0] - meanA) * lineA + (edgeU[0] - meanU) * lineU);
        float topU = meanU + lineU * ((edgeA[0] - meanA) * lineA + (edgeU[0] - meanU) * lineU);
        float kneeA = topA - lineA * KNUCKLES_BELOW * perMetre, kneeU = topU - lineU * KNUCKLES_BELOW * perMetre;
        // Square to the line, towards the back of the grip.
        float inA = -lineU, inU = lineA;
        if (inA > 0f) {
            inA = -inA;
            inU = -inU;
        }
        float thick = outline.through(kneeA, kneeU, inA, inU, THICKEST_GRIP * perMetre);
        gripThick = Math.clamp(thick, THINNEST_GRIP * perMetre, THICKEST_GRIP * perMetre);
        place(kneeA + inA * gripThick / 2f, kneeU + inU * gripThick / 2f, 0f, grip);
        for (int axis = 0; axis < 3; axis++) {
            gripLine[axis] = along[axis] * lineA + up[axis] * lineU;
        }
        // Down the grip's line from its middle to where it ends.
        float down = outline.through(kneeA + inA * gripThick / 2f, kneeU + inU * gripThick / 2f, -lineA, -lineU, 0.3f * perMetre);
        place(kneeA + inA * gripThick / 2f - lineA * down, kneeU + inU * gripThick / 2f - lineU * down, 0f, gripFoot);

        // The underside, column by column, ahead of the guard.
        int first = guard.frontColumn + 1;
        int last = Math.min(outline.column(muzzle), outline.columns - 1);
        float[] low = new float[outline.columns];
        for (int c = 0; c < outline.columns; c++) {
            low[c] = outline.lowest(c);
        }
        // A magazine hangs further under than the guard's foot, within reach of it.
        int magBack = -1, magFront = -1;
        float magFoot = Float.POSITIVE_INFINITY;
        int reach = Math.min(last, first + (int) (0.25f * perMetre / cell));
        for (int c = first; c <= reach; c++) {
            if (low[c] < guardFoot - MAGAZINE_DEEPER * perMetre) {
                if (magBack < 0) {
                    magBack = c;
                }
                if (magFront < 0 || c == magFront + 1 || c - magFront <= 2) {
                    magFront = c;
                    magFoot = Math.min(magFoot, low[c]);
                }
            } else if (magBack >= 0 && c > magFront + 2) {
                break;
            }
        }
        magazine = magBack >= 0;
        magazineBack = magazine ? outline.a(magBack) : 0f;
        magazineFront = magazine ? outline.a(magFront + 1) : 0f;
        magazineFoot = magazine ? magFoot : 0f;
        if (magazine) {
            magazineMiddle = (guardFoot + magFoot) / 2f;
            int inside = outline.column((magazineBack + magazineFront) / 2f);
            magazineEdge = outline.edge(inside, magazineMiddle);
            // Read a little above and below, within what shows of it.
            float over = Math.min(LEANS_OVER * perMetre, (guardFoot - magFoot) / 4f);
            magazineLeans = (outline.edge(inside, magazineMiddle - over) - outline.edge(inside, magazineMiddle + over)) / (2f * over);
        } else {
            magazineMiddle = 0f;
            magazineEdge = 0f;
            magazineLeans = 0f;
        }
        int foreFirst = Math.max(first + (int) (FORE_AFTER * perMetre / cell), magazine ? magFront + 1 + (int) (0.02f * perMetre / cell) : 0);
        step = cell;
        if (foreFirst >= last) {
            foreFrom = outline.a(last);
            foreTo = foreFrom;
            underside = new float[] {Float.isNaN(low[last]) ? bore : low[last]};
        } else {
            foreFrom = outline.a(foreFirst) + cell / 2f;
            foreTo = outline.a(last) + cell / 2f;
            underside = new float[last - foreFirst + 1];
            for (int c = foreFirst; c <= last; c++) {
                underside[c - foreFirst] = Float.isNaN(low[c]) ? bore : low[c];
            }
        }
        // The longest level run of it, past the receiver: a pump's fore-end, a handguard, a fore-stock's flat.
        float within = LEVEL_WITHIN * perMetre;
        int skip = Math.min((int) (RECEIVER_AHEAD * perMetre / step), underside.length - 1), from = skip, to = skip;
        // Only as low as the fore-end is: a bare barrel ahead of it runs level for longer.
        float lowest = Float.POSITIVE_INFINITY;
        for (int i = skip; i < underside.length; i++) {
            lowest = Math.min(lowest, underside[i]);
        }
        for (int i = skip; i < underside.length; i++) {
            if (underside[i] > lowest + FORE_DEEP * perMetre) {
                continue;
            }
            float least = underside[i], most = underside[i];
            int j = i;
            while (j + 1 < underside.length && underside[j + 1] <= lowest + FORE_DEEP * perMetre && Math.max(most, underside[j + 1]) - Math.min(least, underside[j + 1]) <= within) {
                j++;
                least = Math.min(least, underside[j]);
                most = Math.max(most, underside[j]);
            }
            if (j - i > to - from) {
                from = i;
                to = j;
            }
        }
        levelFrom = foreFrom + from * step;
        levelTo = foreFrom + to * step;

        // The rear of the slide: the rearmost column that reaches the bore's height.
        int rear = 0;
        while (rear < outline.columns - 1 && !(outline.highest(rear) >= bore)) {
            rear++;
        }
        float slideA = outline.a(rear) + SLIDE_HELD * perMetre;
        place(slideA, outline.highest(Math.min(outline.column(slideA), outline.columns - 1)) - SLIDE_UNDER * perMetre, 0f, slide);
        place(outline.knobA, outline.knobU, outline.knobS, knob);
    }

    /**
     * Reads the grip's front edge, row by row down, into the arrays given, and says how many rows of it there are.
     *
     * @param fromHole whether the rows begin at the guard's top, beside the hole, and not under it
     */
    private static int edge(Outline outline, Hole guard, boolean fromHole, float perMetre, float[] edgeA, float[] edgeU) {
        float cell = outline.cell;
        int behind = (int) (STRAP_BEHIND * perMetre / cell), thin = Math.max(2, (int) (THINNEST_HELD * perMetre / cell));
        int found = 0;
        float walked = 0f;
        for (int row = fromHole ? guard.topRow : guard.footRow - 1; row >= 0 && walked < STRAP_READ * perMetre; row--) {
            int column = guard.backColumn;
            if (!fromHole && found == 0 && outline.solid(row, column)) {
                // The guard's own bar under the hole.
                continue;
            }
            // Passing over what is too thin to be held, a guard's or a lever's bar, to the first thing that is.
            int at = column;
            while (at > column - behind && at >= 0) {
                if (outline.solid(row, at)) {
                    int run = 0;
                    while (run <= thin && outline.solid(row, at - run)) {
                        run++;
                    }
                    if (run > thin) {
                        break;
                    }
                    at -= run;
                } else {
                    at--;
                }
            }
            if (at <= column - behind || at < 0) {
                if (found >= 3) {
                    break;
                }
                found = 0;
                walked = 0f;
                continue;
            }
            float edge = outline.a(at) + cell;
            if (found > 0 && edgeA[found - 1] - edge > STEEPEST_STEP * cell) {
                if (found >= 3) {
                    // A step back: past the foot of a grip, onto the stock.
                    break;
                }
                // What came before was the guard's own back, hanging under it: the grip begins here.
                found = 0;
                walked = 0f;
            }
            edgeA[found] = edge;
            edgeU[found] = outline.u(row) + cell / 2f;
            if (found > 0) {
                walked += (float) Math.hypot(edgeA[found] - edgeA[found - 1], edgeU[found] - edgeU[found - 1]);
            }
            found++;
        }
        return found;
    }

    /** A place in the mesh's axes from how far it is along the barrel, up and to the side. */
    void place(float a, float u, float s, float[] out) {
        for (int axis = 0; axis < 3; axis++) {
            out[axis] = along[axis] * a + up[axis] * u + side[axis] * s;
        }
    }

    /** How far up the underside ahead of the guard is at a place along the barrel, held to the ends of what was read. */
    float under(float a) {
        float at = (a - foreFrom) / step;
        int i = Math.clamp((int) Math.floor(at), 0, underside.length - 1);
        int j = Math.min(i + 1, underside.length - 1);
        float t = Math.clamp(at - i, 0f, 1f);
        return underside[i] + (underside[j] - underside[i]) * t;
    }

    /**
     * A firearm mesh's axes, from its muzzle attachment: the attachment is turned about x, then
     * y, then z, and its own z is the barrel. A handgun's mesh has its top on its -z, a long
     * gun's on its +z: the muzzle is over the grip.
     *
     * @param along the answer: the way along the barrel, of length one
     * @param up    the answer: the way to the top, of length one and square to `along`
     * @return false if the barrel runs along the top, which leaves no way up square to it
     */
    static boolean axes(float rotateX, float rotateY, float muzzleZ, float[] along, float[] up) {
        double aboutX = Math.toRadians(rotateX), aboutY = Math.toRadians(rotateY);
        along[0] = (float) Math.sin(aboutY);
        along[1] = (float) (-Math.sin(aboutX) * Math.cos(aboutY));
        along[2] = (float) (Math.cos(aboutX) * Math.cos(aboutY));
        float top = muzzleZ < 0f ? -1f : 1f, tipped = along[2] * top;
        return ArmPose.unit(-along[0] * tipped, -along[1] * tipped, top - along[2] * tipped, up);
    }

    /**
     * Reads a firearm's mesh.
     *
     * @param places   the mesh's vertices, three floats each
     * @param corners  its triangles, three vertices each
     * @param along    the way along the barrel to the muzzle, of length one
     * @param up       the way to the top of the firearm, of length one and square to `along`
     * @param muzzle   the muzzle, in the mesh's axes
     * @param perMetre the mesh's units in a metre of a firearm the size firearms are
     * @return the shape, or null if the outline has no trigger guard a finger goes through, with `why` saying so
     */
    static WeaponShape read(float[] places, int[] corners, float[] along, float[] up, float[] muzzle, float perMetre, StringBuilder why) {
        float muzzleA = ArmPose.dot(muzzle, along), boreU = ArmPose.dot(muzzle, up);
        if (!(muzzleA > 0f)) {
            why.append("its muzzle is not ahead of its origin");
            return null;
        }
        float[] side = new float[3];
        ArmPose.cross(along, up, side);
        float minA = Float.POSITIVE_INFINITY, maxA = Float.NEGATIVE_INFINITY, minU = Float.POSITIVE_INFINITY, maxU = Float.NEGATIVE_INFINITY;
        for (int v = 0; v < places.length / 3; v++) {
            float a = places[v * 3] * along[0] + places[v * 3 + 1] * along[1] + places[v * 3 + 2] * along[2];
            float u = places[v * 3] * up[0] + places[v * 3 + 1] * up[1] + places[v * 3 + 2] * up[2];
            if (!Float.isFinite(a) || !Float.isFinite(u)) {
                why.append("vertex ").append(v).append(" has a place that is not a number");
                return null;
            }
            minA = Math.min(minA, a);
            maxA = Math.max(maxA, a);
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
        }
        float cell = muzzleA / CELLS_TO_MUZZLE;
        if ((maxA - minA) / cell * ((maxU - minU) / cell) > MOST_CELLS) {
            why.append("it is ").append(Math.round((maxA - minA) / muzzleA)).append(" times as long as its muzzle is from its grip and ")
                    .append(Math.round((maxU - minU) / muzzleA)).append(" times as tall: no firearm's shape");
            return null;
        }
        Outline outline = new Outline(places, corners, along, up, side, cell);
        Hole guard = outline.guard(boreU, SMALLEST_GUARD * perMetre, GUARD_WITHIN * perMetre);
        if (guard == null) {
            why.append("its outline has no trigger guard: no hole under its bore, ahead of its grip, a finger goes through");
            return null;
        }
        // The knob: the furthest to either side over the trigger guard and a little behind it.
        float bestS = 0f;
        float backA = outline.a(guard.backColumn) - 0.06f * perMetre, frontA = outline.a(guard.frontColumn) + 0.12f * perMetre;
        float footU = outline.u(guard.topRow);
        for (int v = 0; v < places.length / 3; v++) {
            float a = places[v * 3] * along[0] + places[v * 3 + 1] * along[1] + places[v * 3 + 2] * along[2];
            float u = places[v * 3] * up[0] + places[v * 3 + 1] * up[1] + places[v * 3 + 2] * up[2];
            float s = places[v * 3] * side[0] + places[v * 3 + 1] * side[1] + places[v * 3 + 2] * side[2];
            if (a >= backA && a <= frontA && u >= footU && Math.abs(s) > Math.abs(bestS)) {
                bestS = s;
                outline.knobA = a;
                outline.knobU = u;
                outline.knobS = s;
            }
        }
        try {
            return new WeaponShape(along, up, boreU, muzzleA, perMetre, outline, guard);
        } catch (IllegalStateException unread) {
            why.append(unread.getMessage());
            return null;
        }
    }

    /** A hole in the outline: the columns and rows it spans, and how far along the barrel its middle is. */
    private record Hole(int backColumn, int frontColumn, int footRow, int topRow, float middleA) {}

    /** The mesh seen from its side: which cells of a grid along the barrel and up are inside it. */
    private static final class Outline {
        final float cell, fromA, fromU;
        final int columns, rows;
        final boolean[] inside;
        float knobA, knobU, knobS;

        Outline(float[] places, int[] corners, float[] along, float[] up, float[] side, float cell) {
            this.cell = cell;
            int count = places.length / 3;
            float[] a = new float[count], u = new float[count];
            float minA = Float.POSITIVE_INFINITY, maxA = Float.NEGATIVE_INFINITY, minU = Float.POSITIVE_INFINITY, maxU = Float.NEGATIVE_INFINITY;
            for (int v = 0; v < count; v++) {
                a[v] = places[v * 3] * along[0] + places[v * 3 + 1] * along[1] + places[v * 3 + 2] * along[2];
                u[v] = places[v * 3] * up[0] + places[v * 3 + 1] * up[1] + places[v * 3 + 2] * up[2];
                minA = Math.min(minA, a[v]);
                maxA = Math.max(maxA, a[v]);
                minU = Math.min(minU, u[v]);
                maxU = Math.max(maxU, u[v]);
            }
            // A border of empty cells all round, so that what is outside the outline is one piece.
            fromA = minA - 2f * cell;
            fromU = minU - 2f * cell;
            columns = (int) Math.ceil((maxA - fromA) / cell) + 3;
            rows = (int) Math.ceil((maxU - fromU) / cell) + 3;
            inside = new boolean[columns * rows];
            for (int t = 0; t + 2 < corners.length; t += 3) {
                fill(a[corners[t]], u[corners[t]], a[corners[t + 1]], u[corners[t + 1]], a[corners[t + 2]], u[corners[t + 2]]);
            }
        }

        float a(int column) {
            return fromA + column * cell;
        }

        float u(int row) {
            return fromU + row * cell;
        }

        int column(float a) {
            return Math.clamp((int) Math.floor((a - fromA) / cell), 0, columns - 1);
        }

        boolean solid(int row, int column) {
            return row >= 0 && row < rows && column >= 0 && column < columns && inside[row * columns + column];
        }

        /** The cells whose middles a triangle covers, and those its edges pass through, so that a thin part is not lost between cells. */
        private void fill(float a0, float u0, float a1, float u1, float a2, float u2) {
            int c0 = (int) Math.floor((Math.min(a0, Math.min(a1, a2)) - fromA) / cell), c1 = (int) Math.floor((Math.max(a0, Math.max(a1, a2)) - fromA) / cell);
            int r0 = (int) Math.floor((Math.min(u0, Math.min(u1, u2)) - fromU) / cell), r1 = (int) Math.floor((Math.max(u0, Math.max(u1, u2)) - fromU) / cell);
            float area = (a1 - a0) * (u2 - u0) - (a2 - a0) * (u1 - u0);
            if (area != 0f) {
                for (int r = r0; r <= r1; r++) {
                    float pu = fromU + (r + 0.5f) * cell;
                    for (int c = c0; c <= c1; c++) {
                        float pa = fromA + (c + 0.5f) * cell;
                        float w0 = (a1 - pa) * (u2 - pu) - (a2 - pa) * (u1 - pu);
                        float w1 = (a2 - pa) * (u0 - pu) - (a0 - pa) * (u2 - pu);
                        float w2 = (a0 - pa) * (u1 - pu) - (a1 - pa) * (u0 - pu);
                        if (area > 0f ? w0 >= 0f && w1 >= 0f && w2 >= 0f : w0 <= 0f && w1 <= 0f && w2 <= 0f) {
                            inside[r * columns + c] = true;
                        }
                    }
                }
            }
            line(a0, u0, a1, u1);
            line(a1, u1, a2, u2);
            line(a2, u2, a0, u0);
        }

        private void line(float a0, float u0, float a1, float u1) {
            int steps = (int) Math.ceil(Math.max(Math.abs(a1 - a0), Math.abs(u1 - u0)) / (cell * 0.5f)) + 1;
            for (int i = 0; i <= steps; i++) {
                float t = (float) i / steps;
                int c = (int) Math.floor((a0 + (a1 - a0) * t - fromA) / cell), r = (int) Math.floor((u0 + (u1 - u0) * t - fromU) / cell);
                inside[r * columns + c] = true;
            }
        }

        /** The lowest of a column that is inside the outline, or not a number for an empty column. */
        float lowest(int column) {
            for (int r = 0; r < rows; r++) {
                if (inside[r * columns + column]) {
                    return u(r);
                }
            }
            return Float.NaN;
        }

        float highest(int column) {
            for (int r = rows - 1; r >= 0; r--) {
                if (inside[r * columns + column]) {
                    return u(r + 1);
                }
            }
            return Float.NaN;
        }

        /** How far along the barrel the front edge of what is solid at a height is, going forward from a column inside it. */
        float edge(int column, float u) {
            int row = (int) Math.floor((u - fromU) / cell);
            if (!solid(row, column)) {
                throw new IllegalStateException("the magazine is not solid where it is held, " + u + " up at column " + column);
            }
            while (solid(row, column + 1)) {
                column++;
            }
            return a(column + 1);
        }

        /** How far from a place, going a way, the outline runs on unbroken: what is outside at the start is passed over first, for at most a few cells. */
        float through(float a, float u, float wayA, float wayU, float most) {
            float stride = cell * 0.5f, gone = 0f, in = 0f;
            boolean entered = false;
            while (gone < most) {
                boolean solid = solid((int) Math.floor((u - fromU) / cell), (int) Math.floor((a - fromA) / cell));
                if (solid) {
                    entered = true;
                    in += stride;
                } else if (entered || gone > 4f * cell) {
                    break;
                }
                a += wayA * stride;
                u += wayU * stride;
                gone += stride;
            }
            return in;
        }

        /** The trigger guard: of the holes under the bore whose middles are ahead of the origin, big enough for a finger and near enough, the nearest the origin. */
        Hole guard(float bore, float smallest, float within) {
            int[] label = new int[inside.length];
            int[] stack = new int[inside.length];
            // What is outside the outline is reached from its border.
            int top = 0;
            for (int c = 0; c < columns; c++) {
                stack[top++] = c;
                label[c] = -1;
            }
            Hole best = null;
            top = flood(label, stack, top, -1, null);
            for (int start = 0; start < inside.length; start++) {
                if (inside[start] || label[start] != 0) {
                    continue;
                }
                stack[0] = start;
                label[start] = 1;
                long[] span = {columns, -1, rows, -1, 0, 0, 0};
                flood(label, stack, 1, 1, span);
                long cells = span[4];
                float middleA = a(0) + (span[5] / (float) cells + 0.5f) * cell, middleU = u(0) + (span[6] / (float) cells + 0.5f) * cell;
                float wide = (span[1] - span[0] + 1) * cell, tall = (span[3] - span[2] + 1) * cell;
                if (middleU >= bore || middleA <= 0f || middleA > within || wide < smallest || tall < smallest) {
                    continue;
                }
                if (best == null || middleA < best.middleA) {
                    best = new Hole((int) span[0], (int) span[1], (int) span[2], (int) span[3], middleA);
                }
            }
            return best;
        }

        /** Labels every empty cell reached from those on the stack. */
        private int flood(int[] label, int[] stack, int top, int mark, long[] span) {
            while (top > 0) {
                int at = stack[--top];
                int r = at / columns, c = at % columns;
                if (span != null) {
                    span[0] = Math.min(span[0], c);
                    span[1] = Math.max(span[1], c);
                    span[2] = Math.min(span[2], r);
                    span[3] = Math.max(span[3], r);
                    span[4]++;
                    span[5] += c;
                    span[6] += r;
                }
                if (c > 0) {
                    top = reach(label, stack, top, at - 1, mark);
                }
                if (c < columns - 1) {
                    top = reach(label, stack, top, at + 1, mark);
                }
                if (r > 0) {
                    top = reach(label, stack, top, at - columns, mark);
                }
                if (r < rows - 1) {
                    top = reach(label, stack, top, at + columns, mark);
                }
            }
            return top;
        }

        /** Labels an empty cell not yet reached and puts it on the stack. */
        private int reach(int[] label, int[] stack, int top, int cell, int mark) {
            if (!inside[cell] && label[cell] == 0) {
                label[cell] = mark;
                stack[top++] = cell;
            }
            return top;
        }
    }
}
