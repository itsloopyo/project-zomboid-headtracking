package com.cameraunlock.zomboid;

import zombie.core.properties.PropertyContainer;
import zombie.core.textures.Texture;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;

/**
 * The kerbs of a chunk being built: the mod's own, between a street and the ground beside it. One to a frame of the
 * world's picture, and it writes into that frame's meshes.
 */
final class KerbBuilder {
    /** Two triangles of a strip between two ends: each corner is an end, and whether it is the upper edge of the strip there. */
    private static final int[] KERB_CORNERS = {0, 2, 3, 0, 3, 1};
    /**
     * The stone across, from its foot in the street to the back of its top in the verge: how far
     * out from the kerb's line each edge is, how far below the top, the texel's second number
     * there, the way it faces (out, up), and 1 where the height is below the ground's top rather
     * than the stone's. The face leans back a little, its arris is rounded and its top shows
     * between the street and the ground behind before it goes down into it. The foot and the
     * back go into the ground wherever the stone's top has settled to.
     */
    private static final float ROUND = 0.02f, TOP_WIDTH = 0.12f, SUNK = 0.02f;
    private static final float[][] PROFILE = {
            {ROUND + 0.012f, Terrain.KERB + SUNK, 0f, 1f, 0.12f, 1f},
            {ROUND, ROUND, 1f, 1f, 0.12f, 0f},
            {ROUND * 0.7071f, ROUND * 0.2929f, 1.5f, 0.7071f, 0.7071f, 0f},
            {0f, 0f, 2f, 0f, 1f, 0f},
            {-TOP_WIDTH, 0f, 2f, 0f, 1f, 0f},
            {-TOP_WIDTH, SUNK, 2f, -1f, 0f, 1f},
    };
    /** The last strip goes down from the back of the top into the ground, and faces it. */
    private static final int BACK = PROFILE.length - 2;
    /**
     * How far the top stands over the verge: never under it, so the verge never hides the arris.
     * Each cast length has settled by its own amount and leans a little along its run.
     */
    private static final float PROUD = 0.003f, SETTLED = 0.012f, TILT = 0.004f;
    /** Cast lengths of a kerb are this long, their joints where the shader draws them. */
    private static final float LENGTH = 1.5f;
    /** The mod's own kerb, of cast concrete. Its number is past those of what grows. */
    static final float KERB_STONE = 64f;

    private final WorldScene.Frame build;

    private final float[] kerbEnds = new float[4];
    private final float[] kerbFacing = new float[4];
    private final float[] kerbAt = new float[6];
    private final float[] kerbTop = new float[2];

    KerbBuilder(WorldScene.Frame build) {
        this.build = build;
    }

    /** For a square with no kerb to do with it. */
    float noKerb() {
        build.geometry.kerb = null;
        build.kerbed = build.kerbNorth = build.kerbWest = false;
        return 0f;
    }

    /**
     * How far a square of the ground lies below its corners, which is by a kerb where it is on
     * the street's side of one. A kerb does not keep to the edges of the squares, so a square
     * it crosses is in two parts, and geometry is told of it: the square then lies at the
     * height of what it is itself, street or land, and the part beyond the kerb a kerb lower
     * or higher.
     */
    float kerbOver(byte kind, int localX, int localY) {
        boolean street = false, land = false, onLine = false;
        float beyondX = 0f, beyondY = 0f;
        int beyond = 0;
        for (int p = 0; p <= Kerbs.FINE; p++) {
            for (int q = 0; q <= Kerbs.FINE; q++) {
                float value = build.kerbs[(localX * Kerbs.FINE + p) * Kerbs.POINTS + localY * Kerbs.FINE + q];
                street |= value > 0f;
                land |= value < 0f;
                onLine |= value == 0f;
                if (value != 0f && value > 0f != (kind == Terrain.STREET)) {
                    beyondX += p;
                    beyondY += q;
                    beyond++;
                }
            }
        }
        build.geometry.kerb = null;
        if (!street || !land) {
            // A kerb along the square's edge stands on the street's square.
            build.kerbed = street && onLine;
            build.kerbNorth = build.kerbed && Kerbs.alongEdge(build.kerbs, localX, localY, true);
            build.kerbWest = build.kerbed && Kerbs.alongEdge(build.kerbs, localX, localY, false);
            return street ? Terrain.KERB : 0f;
        }
        build.kerbed = true;
        build.kerbNorth = Kerbs.alongEdge(build.kerbs, localX, localY, true);
        build.kerbWest = Kerbs.alongEdge(build.kerbs, localX, localY, false);
        build.geometry.kerb = build.kerbs;
        build.geometry.kerbX = localX;
        build.geometry.kerbY = localY;
        build.geometry.kerbStreet = kind == Terrain.STREET;
        // The ground beyond the kerb is the ground of the square beside this one that is nearest that part.
        build.beyondKerb = -1;
        float nearest = Float.MAX_VALUE;
        int own = (localX + 1) * WorldScene.LIGHT_SPAN + localY + 1;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                int at = own + di * WorldScene.LIGHT_SPAN + dj;
                byte beside = build.groundKind[at];
                boolean other = build.geometry.kerbStreet ? beside == Terrain.NATURAL || beside == Terrain.MADE : beside == Terrain.STREET;
                if (!other || build.groundTexture[at] == null) {
                    continue;
                }
                float awayX = beyondX / beyond / Kerbs.FINE - 0.5f - di, awayY = beyondY / beyond / Kerbs.FINE - 0.5f - dj;
                if (awayX * awayX + awayY * awayY < nearest) {
                    nearest = awayX * awayX + awayY * awayY;
                    build.beyondKerb = at;
                }
            }
        }
        return build.geometry.kerbStreet ? Terrain.KERB : 0f;
    }

    /**
     * The face of the kerb on a square: a strip standing along the line where the kerbs'
     * field crosses nothing, from the land's height down to the street's. Each block between
     * four points of the field is two triangles, and the line runs straight through each.
     */
    void addKerb(int x, int y, int localX, int localY) {
        SpriteGeometry.Mesh mesh = WorldScene.solid.get(WorldScene.PLAIN);
        // The colour given is the light on it. What colour the thing is, the picture knows by
        // what it is made of.
        mesh.open = build.frame.open + 32f * KERB_STONE;
        for (int p = 0; p < Kerbs.FINE; p++) {
            for (int q = 0; q < Kerbs.FINE; q++) {
                int at = (localX * Kerbs.FINE + p) * Kerbs.POINTS + localY * Kerbs.FINE + q;
                float here = build.kerbs[at], across = build.kerbs[at + Kerbs.POINTS], opposite = build.kerbs[at + Kerbs.POINTS + 1], along = build.kerbs[at + 1];
                boolean street = here > 0f;
                if (street == across > 0f && street == opposite > 0f && street == along > 0f) {
                    continue;
                }
                addKerbLength(mesh, x, y, localX, localY, p, q, here, 1, 0, across, 1, 1, opposite);
                addKerbLength(mesh, x, y, localX, localY, p, q, here, 1, 1, opposite, 0, 1, along);
            }
        }
    }

    /** The length of kerb in one triangle of a block of the field: its corner (p, q) and two more, each with the field there. */
    private void addKerbLength(SpriteGeometry.Mesh mesh, int x, int y, int localX, int localY, int p, int q,
            float here, int p1, int q1, float first, int p2, int q2, float second) {
        int ends = 0;
        ends = kerbEnd(ends, 0, 0, here, p1, q1, first);
        ends = kerbEnd(ends, p1, q1, first, p2, q2, second);
        ends = kerbEnd(ends, p2, q2, second, 0, 0, here);
        if (ends != 2 || kerbEnds[0] == kerbEnds[2] && kerbEnds[1] == kerbEnds[3]) {
            return;
        }
        float[] lift = build.geometry.lift;
        // The square's own height is the street's where it is street, or all on the street's side.
        float raise = build.geometry.kerb == null || build.geometry.kerbStreet ? Terrain.KERB : 0f;
        for (int end = 0; end < 2; end++) {
            float a = (p + kerbEnds[2 * end]) / Kerbs.FINE, b = (q + kerbEnds[2 * end + 1]) / Kerbs.FINE;
            float top = (lift[0] * (1f - a) + lift[1] * a) * (1f - b) + (lift[3] * (1f - a) + lift[2] * a) * b + raise;
            // It faces the street, and round a corner turns with the line.
            float step = 1f / Kerbs.FINE;
            float towardsX = Kerbs.at(build.kerbs, localX + a + step, localY + b) - Kerbs.at(build.kerbs, localX + a - step, localY + b);
            float towardsY = Kerbs.at(build.kerbs, localX + a, localY + b + step) - Kerbs.at(build.kerbs, localX + a, localY + b - step);
            float length = (float) Math.sqrt(towardsX * towardsX + towardsY * towardsY);
            if (length < 1e-4f) {
                // Hard against a house the field is level on the house's side, and the length's own lie says which way it faces.
                towardsX = kerbEnds[3] - kerbEnds[1];
                towardsY = kerbEnds[0] - kerbEnds[2];
                float streetP = here > 0f ? 0f : first > 0f ? p1 : p2, streetQ = here > 0f ? 0f : first > 0f ? q1 : q2;
                if ((streetP - kerbEnds[0]) * towardsX + (streetQ - kerbEnds[1]) * towardsY < 0f) {
                    towardsX = -towardsX;
                    towardsY = -towardsY;
                }
                length = (float) Math.sqrt(towardsX * towardsX + towardsY * towardsY);
            }
            kerbFacing[2 * end] = towardsX / length;
            kerbFacing[2 * end + 1] = towardsY / length;
            kerbAt[3 * end] = x + a;
            kerbAt[3 * end + 1] = y + b;
            kerbAt[3 * end + 2] = top + settled(x + a, y + b, Math.abs(kerbFacing[2 * end]) > Math.abs(kerbFacing[2 * end + 1]));
            kerbTop[end] = top;
        }
        float back = topBack(behind, kerbAt, kerbFacing);
        for (int strip = 0; strip + 1 < PROFILE.length; strip++) {
            for (int corner : KERB_CORNERS) {
                int end = corner >> 1;
                float[] edge = PROFILE[strip + (corner & 1)];
                float outX = kerbFacing[2 * end], outY = kerbFacing[2 * end + 1];
                // The way it faces: a strip's own where its edges differ (the back), else the edge's, so the arris is lit round.
                float[] facing = strip == BACK ? PROFILE[BACK + 1] : edge;
                mesh.face(outX * facing[3], outY * facing[3], facing[4]);
                float up = (edge[5] > 0f ? kerbTop[end] : kerbAt[3 * end + 2]) - edge[1];
                float out = edge[0] == -TOP_WIDTH ? -back : edge[0];
                // The texel's first number says which of the map's two ways the kerb runs more along there, for its joints.
                mesh.add(kerbAt[3 * end] + outX * out, kerbAt[3 * end + 1] + outY * out, up,
                        Math.abs(outX) > Math.abs(outY) ? 0f : 1f, edge[2], build.frame.red, build.frame.green, build.frame.blue);
            }
        }
    }

    /** What stands behind a kerb, on the ground floor. */
    interface Behind {
        boolean room(int x, int y);

        /** Whether a wall stands on the north edge of square (x, y), or the west edge. */
        boolean walled(int x, int y, boolean north);
    }

    private final Behind behind = new Behind() {
        @Override
        public boolean room(int x, int y) {
            IsoGridSquare square = build.cell.getGridSquare(x, y, 0);
            return square != null && square.getRoomID() != -1L;
        }

        @Override
        public boolean walled(int x, int y, boolean north) {
            return Hoods.walled(build.cell.getGridSquare(x, y, 0), north);
        }
    };

    /**
     * How far back from its line a length of kerb lays its top, for the whole length: its ends at at (x, y, height for
     * each) and facing ((x, y) for each). Where either end is against a house's wall with a room behind it, the top
     * stops at the line, inside the wall: laid back from it, it would lie on the room's floor, lit as the street is.
     * A room open to the street with no wall keeps its kerb.
     */
    static float topBack(Behind behind, float[] at, float[] facing) {
        for (int end = 0; end < 2; end++) {
            if (againstWall(behind, at[3 * end], at[3 * end + 1], facing[2 * end], facing[2 * end + 1])) {
                return 0f;
            }
        }
        return TOP_WIDTH;
    }

    /** Whether the top laid back from (x, y) facing (facingX, facingY) would be in a room, past a wall the top's width or less away. */
    private static boolean againstWall(Behind behind, float x, float y, float facingX, float facingY) {
        if (!behind.room((int) Math.floor(x - facingX * TOP_WIDTH), (int) Math.floor(y - facingY * TOP_WIDTH))) {
            return false;
        }
        int column = Math.round(x), row = Math.round(y);
        return Math.abs(x - column) <= TOP_WIDTH && behind.walled(column, (int) Math.floor(y), false)
                || Math.abs(y - row) <= TOP_WIDTH && behind.walled((int) Math.floor(x), row, true);
    }

    /**
     * How far the kerb's top stands over the verge at a place, by the length of kerb it is on:
     * a length runs along y where the kerb faces more across x, and the step between two lengths
     * falls in the one block of the field the joint is in.
     */
    static float settled(float x, float y, boolean runsAlongY) {
        float along = (runsAlongY ? y : x) / LENGTH, across = runsAlongY ? x : y;
        float length = (float) Math.floor(along);
        float row = Math.round(across);
        return PROUD + TILT + SETTLED * grain(length, row) + TILT * (2f * grain(length, row + 977f) - 1f) * (2f * (along - length) - 1f);
    }

    private static float grain(float x, float y) {
        int h = (int) x * 73856093 ^ (int) y * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xffff) / 65535f;
    }

    /** Notes where the kerb's line crosses one side of a triangle, if it does: between a point on the street's side and one that is not. */
    private int kerbEnd(int ends, int p0, int q0, float from, int p1, int q1, float to) {
        if (from > 0f == to > 0f || ends == 2) {
            return ends;
        }
        float t = from / (from - to);
        kerbEnds[2 * ends] = p0 + (p1 - p0) * t;
        kerbEnds[2 * ends + 1] = q0 + (q1 - q0) * t;
        return ends + 1;
    }

    /**
     * The ground beyond a kerb that crosses a square: the pavement's on the part of a street's
     * square the kerb leaves standing, and the street's on the part of a pavement's square it
     * takes. It is the ground of the square beside, laid here as far as the kerb.
     */
    void addBeyondKerb(int x, int y) {
        IsoObject floor = build.groundObject[build.beyondKerb];
        Texture texture = build.groundTexture[build.beyondKerb];
        PropertyContainer properties = floor.getSprite().getProperties();
        build.frameOf(floor, texture);
        build.frame.material = Ground.floor(properties.get("FloorMaterial"), properties.get("FootstepMaterial"), floor.getSprite().getName());
        build.geometry.kerbKeep = build.geometry.kerbStreet ? SpriteGeometry.LAND : SpriteGeometry.STREET;
        int flat = build.flatOf(texture, Flats.FLOOR, 0f, 0f, 0f);
        build.frame.flat = flat != 0 ? SpriteGeometry.FLAT_FLOOR : 0;
        // The picture's own wood is laid on a boarded floor only as the sprite's boards (see addBoards), which a floor a kerb cuts is not built as: it shows its sprite.
        // Where the picture lays none of its own textures a wooden floor stays wood, which is how its board joints and its gloss are drawn.
        if (build.frame.material == WorldScene.WOOD && Own.textures(build.playerIndex)) {
            build.frame.material = WorldScene.TILE;
        }
        build.geometry.addFace(WorldScene.solid.get(flat != 0 ? flat : texture.getID()), build.frame, x, y, 0, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
        build.frame.flat = 0;
        build.geometry.kerbKeep = SpriteGeometry.BOTH;
        build.frame.material = 0f;
    }
}
