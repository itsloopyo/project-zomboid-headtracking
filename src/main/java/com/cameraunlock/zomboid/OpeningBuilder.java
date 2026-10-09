package com.cameraunlock.zomboid;

import java.util.Arrays;
import zombie.core.textures.Texture;
import zombie.iso.IsoGridSquare;
import zombie.iso.SpriteDetails.IsoObjectType;

/**
 * What fills the openings of the walls of a chunk being built: a door's leaf, a window's frame, bars, glass and shards,
 * and the curtain hung at one. One to a frame of the world's picture, and it writes into that frame's meshes.
 */
final class OpeningBuilder {
    /** Half the thickness of a door's leaf, in tiles. */
    private static final float DOOR_HALF = 0.022f;
    /** How high up a door its width is read, in levels, and how far in from its edge the colour of its edges is taken, in frame pixels at tile scale 1. */
    private static final float DOOR_READ_AT = 0.4f, DOOR_STILE = 2.5f;
    private static final int SPAN_FLOATS = 4;
    /** Cloth the picture has hung in folds of its own. */
    private static final float DRAPE = 69f;
    /** How far a curtain hangs clear of the wall's skin, how thick its rod is and how far the rod runs past the cloth at each end, in tiles. */
    private static final float DRAPE_CLEAR = 0.04f, ROD_THICK = 0.022f, ROD_PAST = 0.04f;
    /** How wide a fold is in a curtain drawn across and the narrowest one in a curtain pulled back, and how deep each is, in tiles. */
    private static final float FOLD_WIDE = 0.055f, FOLD_NARROW = 0.022f, FOLD_DEEP = 0.016f, FOLD_DEEP_BACK = 0.024f;
    /** How many strips a fold is made of, how much of a curtain's height its hem is and how much darker. */
    private static final int FOLD_STRIPS = 6;
    private static final float HEM = 0.07f, HEM_SHADE = 0.86f;
    /** The colour of a curtain rod. */
    private static final float ROD_RED = 0.2f, ROD_GREEN = 0.16f, ROD_BLUE = 0.13f;
    /** How far a moulding round a door's panel stands out of the leaf and how wide it is, in tiles. */
    private static final float MOULDING_PROUD = 0.011f, MOULDING_WIDE = 0.024f;
    /** A room's light is the same from every side, and shows no relief: a moulding is drawn this much darker than the leaf, as its own shadow makes it, and a panel's field this much. */
    private static final float MOULDING_SHADE = 0.8f, FIELD_SHADE = 0.93f;
    /** Where the panels of a door are, as shares of the part of the leaf they are on: across, then up a whole leaf, then up what is below a light. */
    private static final float[] PANELS_ACROSS = {0.14f, 0.455f, 0.545f, 0.86f};
    private static final float[] PANELS_UP = {0.1f, 0.37f, 0.45f, 0.8f, 0.86f, 0.95f}, PANELS_UP_BELOW = {0.16f, 0.86f};
    /** A boarded door: the gap between two boards, how far its ledges stand out and how high each is, in tiles, and where up the leaf they are. */
    private static final float BOARD_GAP = 0.005f, BOARD_USUAL = 0.16f, LEDGE_PROUD = 0.014f, LEDGE_HIGH = 0.085f;
    private static final float[] LEDGES_AT = {0.12f, 0.5f, 0.88f};
    /** How much darker than the boards the open joint between two is. */
    private static final float JOINT_SHADE = 0.4f;
    /** A door's knob: how high up it is in levels, how far in from the leaf's end as a share of the leaf, how thick and how far out, in tiles. */
    private static final float KNOB_HIGH = 0.4f, KNOB_IN = 0.1f, KNOB_ROUND = 0.017f, KNOB_OUT = 0.034f, KNOB_NECK = 0.4f;
    /** The picture's number for bare metal: see the furniture's surfaces in the world's fragment program. */
    private static final float BARE_METAL = 21f;
    /** How far a window's frame stands out of the wall's skins and laps over the edge of its opening, in tiles. */
    private static final float FRAME_PROUD = 0.006f, FRAME_LAP = 0.012f;
    /** A bar is this much as deep as it is wide, between the least and the most, in tiles. */
    private static final float BAR_DEPTH = 0.55f, BAR_THINNEST = 0.007f, BAR_DEEPEST = 0.022f;
    /** How far the sill stands out of the wall out of doors, how far it runs past the frame at each end and how thick it is, in tiles. */
    private static final float SILL_OUT = 0.035f, SILL_PAST = 0.02f, SILL_THICK = 0.022f;
    /** A frame that comes down to the floor has a threshold and no sill: how high its foot has to be for a sill, in levels. */
    private static final float SILL_LOWEST = 0.08f;
    /** How wide a shard of a broken pane is at the bar that holds it, at least and at most, in tiles. */
    private static final float SHARD_NARROWEST = 0.012f, SHARD_WIDEST = 0.085f;
    /** How far to either side of the middle of its foot a shard's point may stand, in widths of the foot. */
    private static final float SHARD_LEAN = 1.3f;
    /** A frame redder than it is blue by this much is bare wood, and anything else is painted. */
    private static final float WOODEN = 0.12f;
    /** A member of a frame wider than this each way, in tiles, is a piece of the wall the window is in and keeps the wall's own picture on its faces. */
    private static final float WALL_MEMBER = 0.14f;
    /** A number from 0 to 1 that is the same every time for the same seed. */
    private static float chance(int seed) {
        seed = (seed ^ seed >>> 15) * 0x2C1B3C6D;
        seed = (seed ^ seed >>> 12) * 0x297A2D39;
        return ((seed ^ seed >>> 15) >>> 8) / (float) (1 << 24);
    }

    private final WorldScene.Frame build;

    /** Where the window being built is: the corner of its square its edge starts from, the edge's direction, and its foot. */
    private float windowX, windowY, windowAlongX, windowAlongY, windowFoot;
    /** Which side of the window is being added: 0 its own square's, 1 the far one. */
    private int windowLit;

    OpeningBuilder(WorldScene.Frame build) {
        this.build = build;
    }

    /**
     * A door as a leaf with a thickness: its sprite on both faces, and its two edges and its
     * top closed in the colour of its stile. Shut, it hangs in the middle of the wall's
     * thickness, behind the doorway's reveals. Open, it stands clear of the wall it has swung
     * back against. False, adding nothing, for a sprite with no door painted across the edge.
     */
    boolean addDoor(SpriteGeometry.Mesh mesh, Texture texture, int x, int y, int z, float a, float b, float da, float db) {
        int at = da != 0f ? 0 : SPAN_FLOATS;
        float[] span = WorldScene.doorSpans.get(texture);
        if (span == null) {
            span = new float[2 * SPAN_FLOATS];
            Arrays.fill(span, Float.NaN);
            WorldScene.doorSpans.put(texture, span);
        }
        if (span[at] != build.frame.originU || span[at + 1] != build.frame.originV) {
            span[at] = build.frame.originU;
            span[at + 1] = build.frame.originV;
            int pixels = Math.round(Math.abs(SpriteGeometry.frameU(build.frame, a + da, b + db) - SpriteGeometry.frameU(build.frame, a, b)));
            int first = -1, last = -1;
            for (int pixel = 0; pixel < pixels; pixel++) {
                if (build.wallBuilder.paintedOnWall(texture, a, b, da, db, (pixel + 0.5f) / pixels, DOOR_READ_AT, 0f)) {
                    first = first < 0 ? pixel : first;
                    last = pixel + 1;
                }
            }
            span[at + 2] = first < 0 ? -1f : (float) first / pixels;
            span[at + 3] = (float) last / pixels;
        }
        float from = span[at + 2], to = span[at + 3];
        if (from < 0f) {
            return false;
        }
        float top = build.wallBuilder.doorTop(texture, a, b, da, db);
        float out = build.doorSwung ? WorldScene.WALL_HALF + WorldScene.HUNG_CLEAR + DOOR_HALF : 0f;
        for (int side = -1; side <= 1; side += 2) {
            build.geometry.shownA = db * (out + side * DOOR_HALF);
            build.geometry.shownB = da * (out + side * DOOR_HALF);
            build.geometry.addFace(mesh, build.frame, x, y, z, a, b, 0f, da, db, 0f, 0f, 0f, top);
        }
        build.geometry.shownA = build.geometry.shownB = 0f;
        // A tile along a wall is 32 frame pixels across at tile scale 1.
        float stile = from + DOOR_STILE / 32f;
        if (!build.wallBuilder.paintedOnWall(texture, a, b, da, db, stile, DOOR_READ_AT, 0f)) {
            return true;
        }
        float u = SpriteGeometry.textureU(build.frame, a + da * stile, b + db * stile), v = SpriteGeometry.textureV(build.frame, a + da * stile, b + db * stile, DOOR_READ_AT);
        mesh.open = build.frame.open + 32f * build.frame.material;
        mesh.sway = 0f;
        float nearX = db * (out - DOOR_HALF), nearY = da * (out - DOOR_HALF), farX = db * (out + DOOR_HALF), farY = da * (out + DOOR_HALF);
        float x0 = x + a + da * from, y0 = y + b + db * from, x1 = x + a + da * to, y1 = y + b + db * to;
        float foot = z * ViewCamera.LEVEL_HEIGHT + build.geometry.liftAt(a + da * 0.5f, b + db * 0.5f), head = foot + top * ViewCamera.LEVEL_HEIGHT;
        float[] c = build.reveal;
        for (int end = 0; end < 2; end++) {
            float ex = end == 0 ? x0 : x1, ey = end == 0 ? y0 : y1;
            WorldScene.set(c, 0, ex + nearX, ey + nearY, foot);
            WorldScene.set(c, 1, ex + farX, ey + farY, foot);
            WorldScene.set(c, 2, ex + farX, ey + farY, head);
            WorldScene.set(c, 3, ex + nearX, ey + nearY, head);
            SpriteGeometry.addQuad(mesh, c, u, v, build.frame.red, build.frame.green, build.frame.blue);
        }
        WorldScene.set(c, 0, x0 + nearX, y0 + nearY, head);
        WorldScene.set(c, 1, x0 + farX, y0 + farY, head);
        WorldScene.set(c, 2, x1 + farX, y1 + farY, head);
        WorldScene.set(c, 3, x1 + nearX, y1 + nearY, head);
        SpriteGeometry.addQuad(mesh, c, u, v, build.frame.red, build.frame.green, build.frame.blue);
        return true;
    }

    /**
     * A curtain, hung: a rod over the window and the cloth below it in folds, drawn across in
     * one length or pulled back to the two ends as the game's sprite has it, in the sprite's
     * colour. Cloth rolled up over the window is a roll. Where it hangs is measured off the
     * sprite: see Drapes. False, adding nothing, for a sprite not measured yet or with no
     * cloth on its edge.
     */
    boolean addCurtain(Texture texture, IsoObjectType type, int x, int y, int z) {
        boolean north = type == IsoObjectType.curtainN || type == IsoObjectType.curtainS;
        boolean far = type == IsoObjectType.curtainS || type == IsoObjectType.curtainE;
        Drapes.Shape shape = Drapes.of(texture, build.frame, north, far);
        if (shape == null) {
            if (Drapes.unread(texture)) {
                build.building.signed = false;
            }
            return false;
        }
        windowX = x + (far && !north ? 1 : 0);
        windowY = y + (far && north ? 1 : 0);
        windowAlongX = north ? 1f : 0f;
        windowAlongY = north ? 0f : 1f;
        windowFoot = z * ViewCamera.LEVEL_HEIGHT + (build.geometry.lift[0] + build.geometry.lift[1] + build.geometry.lift[2] + build.geometry.lift[3]) / 4f;
        float level = ViewCamera.LEVEL_HEIGHT, into = far ? -1f : 1f, out = into * (WorldScene.WALL_HALF + DRAPE_CLEAR);
        float red = build.frame.red, green = build.frame.green, blue = build.frame.blue;
        SpriteGeometry.Mesh cloth = WorldScene.solid.get(WorldScene.PLAIN, WallSides.key(shape.red, shape.green, shape.blue, WallSides.FLAT));
        cloth.sway = 0f;
        cloth.open = build.frame.open + 32f * DRAPE;
        float[] c = build.reveal;
        if (shape.hangs.length == 0) {
            // Rolled up and tied: a roll as thick as the sprite paints it, along the window's head.
            float middle = (shape.foot + shape.head) / 2f, round = Math.min((shape.head - shape.foot) * level / 2f, 0.11f);
            for (int side = 0; side < 8; side++) {
                double a0 = side * Math.PI / 4.0, a1 = (side + 1) * Math.PI / 4.0;
                float d0 = out + round * (float) Math.cos(a0), h0 = middle + round * (float) Math.sin(a0) / level;
                float d1 = out + round * (float) Math.cos(a1), h1 = middle + round * (float) Math.sin(a1) / level;
                windowPoint(c, 0, shape.start, h0, d0);
                windowPoint(c, 1, shape.end, h0, d0);
                windowPoint(c, 2, shape.end, h1, d1);
                windowPoint(c, 3, shape.start, h1, d1);
                SpriteGeometry.addPlainQuad(cloth, c, red, green, blue);
            }
            return true;
        }
        float whole = shape.end - shape.start;
        int seed = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        for (int hung = 0; hung < shape.hangs.length; hung += 2) {
            float t0 = shape.hangs[hung], t1 = shape.hangs[hung + 1], wide = t1 - t0;
            boolean across = wide > 0.6f * whole;
            // Cloth pulled back is the same cloth in less room: its folds are narrower and deeper.
            float pitch = across ? FOLD_WIDE : Math.max(FOLD_NARROW, wide / 5f), deep = across ? FOLD_DEEP : FOLD_DEEP_BACK;
            int folds = Math.max(2, Math.round(wide / pitch)), strips = folds * FOLD_STRIPS;
            float hem = shape.foot + (shape.head - shape.foot) * HEM;
            float before = 0f, beforeFoot = 0f;
            for (int strip = 0; strip <= strips; strip++) {
                int fold = strip / FOLD_STRIPS;
                // No two folds hang quite alike, and cloth spreads a little towards its foot.
                float much = deep * (0.65f + 0.7f * chance(seed + hung * 31 + fold));
                float turn = (float) Math.sin(2.0 * Math.PI * strip / FOLD_STRIPS + 6.0 * chance(seed + hung));
                float here = out + into * much * turn, hereFoot = out + into * much * 1.3f * turn;
                if (strip > 0) {
                    float a = t0 + wide * (strip - 1) / strips, b = t0 + wide * strip / strips;
                    windowPoint(c, 0, a, hem, beforeFoot);
                    windowPoint(c, 1, b, hem, hereFoot);
                    windowPoint(c, 2, b, shape.head, here);
                    windowPoint(c, 3, a, shape.head, before);
                    SpriteGeometry.addPlainQuad(cloth, c, red, green, blue);
                    windowPoint(c, 0, a, shape.foot, beforeFoot);
                    windowPoint(c, 1, b, shape.foot, hereFoot);
                    windowPoint(c, 2, b, hem, hereFoot);
                    windowPoint(c, 3, a, hem, beforeFoot);
                    SpriteGeometry.addPlainQuad(cloth, c, red * HEM_SHADE, green * HEM_SHADE, blue * HEM_SHADE);
                }
                before = here;
                beforeFoot = hereFoot;
            }
        }
        SpriteGeometry.Mesh rod = WorldScene.solid.get(WorldScene.PLAIN, WallSides.key(ROD_RED, ROD_GREEN, ROD_BLUE, WallSides.FLAT));
        rod.sway = 0f;
        rod.open = build.frame.open + 32f * WorldScene.WOOD;
        float half = ROD_THICK / 2f;
        for (int side = 0; side < 4; side++) {
            float d0 = out + into * (FOLD_DEEP_BACK + half) + (side == 0 || side == 3 ? -half : half), d1 = out + into * (FOLD_DEEP_BACK + half) + (side < 2 ? half : -half);
            float h0 = shape.head + (side < 2 ? -half : half) / level, h1 = shape.head + (side == 1 || side == 2 ? half : -half) / level;
            windowPoint(c, 0, shape.start - ROD_PAST, h0, d0);
            windowPoint(c, 1, shape.end + ROD_PAST, h0, d0);
            windowPoint(c, 2, shape.end + ROD_PAST, h1, d1);
            windowPoint(c, 3, shape.start - ROD_PAST, h1, d1);
            SpriteGeometry.addPlainQuad(rod, c, red, green, blue);
        }
        return true;
    }

    /**
     * A door, built: a leaf as thick as a door in the colour of the game's sprite, with glass
     * in its lights, mouldings round its panels or boards on ledges where the sprite draws
     * them, and a knob on each face. Its outline, its lights and how it is worked are measured
     * off the sprite: see Doors. False, adding nothing, for a sprite not measured yet or one
     * that is no leaf with four straight sides.
     */
    boolean addBuiltDoor(Texture texture, int x, int y, int z, float da, float db) {
        boolean north = da != 0f;
        Doors.Shape door = Doors.of(texture, build.frame, north);
        if (door == null) {
            if (Doors.unread(texture)) {
                build.building.signed = false;
            }
            return false;
        }
        Windows.Shape leaf = door.leaf;
        float out = build.doorSwung ? WorldScene.WALL_HALF + WorldScene.HUNG_CLEAR + DOOR_HALF : 0f;
        windowX = x + db * out;
        windowY = y + da * out;
        windowAlongX = da;
        windowAlongY = db;
        windowFoot = z * ViewCamera.LEVEL_HEIGHT + (build.geometry.lift[0] + build.geometry.lift[1] + build.geometry.lift[2] + build.geometry.lift[3]) / 4f;
        // A door shut in its doorway has a room or the street on each face. Swung open it stands in its own square.
        int ownLight = (build.waterLocalX + 1) * WorldScene.LIGHT_SPAN + build.waterLocalY + 1, beyondLight = north ? ownLight - 1 : ownLight - WorldScene.LIGHT_SPAN;
        boolean same = build.doorSwung, unseen = !same && build.lightOpen[beyondLight] < 0;
        float[] red = {build.frame.red, same ? build.frame.red : unseen ? build.unseenLight.r : build.lightRed[beyondLight]};
        float[] green = {build.frame.green, same ? build.frame.green : unseen ? build.unseenLight.g : build.lightGreen[beyondLight]};
        float[] blue = {build.frame.blue, same ? build.frame.blue : unseen ? build.unseenLight.b : build.lightBlue[beyondLight]};
        float[] open = {build.frame.open, same ? build.frame.open : unseen ? 1f : build.lightOpen[beyondLight]};

        boolean wooden = door.kind == Doors.PLANKED || leaf.red - leaf.blue > WOODEN;
        SpriteGeometry.Mesh wood = WorldScene.solid.get(WorldScene.PLAIN, WallSides.key(leaf.red, leaf.green, leaf.blue, WallSides.FLAT));
        SpriteGeometry.Mesh joint = WorldScene.solid.get(WorldScene.PLAIN, WallSides.key(leaf.red * JOINT_SHADE, leaf.green * JOINT_SHADE, leaf.blue * JOINT_SHADE, WallSides.FLAT));
        SpriteGeometry.Mesh metal = WorldScene.solid.get(WorldScene.PLAIN);
        wood.sway = joint.sway = metal.sway = 0f;
        float level = ViewCamera.LEVEL_HEIGHT, wide = leaf.end - leaf.start;
        float inStart = leaf.start + leaf.startWide, inEnd = leaf.end - leaf.endWide;
        // What is below the lowest light, or the whole leaf where there is none, is where panels are.
        float panelled = leaf.head;
        for (int pane = 0; pane < leaf.panes.length; pane += 5) {
            panelled = Math.min(panelled, leaf.panes[pane + 2]);
        }
        float[] up = panelled < leaf.head ? PANELS_UP_BELOW : PANELS_UP;
        float knob = door.handleAtEnd ? leaf.end - KNOB_IN * wide : leaf.start + KNOB_IN * wide;
        for (int side = 0; side < 2; side++) {
            float face = (side == 0 ? 1f : -1f) * DOOR_HALF;
            float r = red[side], g = green[side], b = blue[side];
            wood.open = joint.open = open[side] + 32f * (wooden ? WorldScene.WOOD : WorldScene.PAINT);
            if (door.kind == Doors.PLANKED) {
                // Boards side by side with an open joint between each two, on a back a little behind them.
                float board = door.board > 0.08f ? door.board : BOARD_USUAL;
                int boards = Math.max(2, Math.round(wide / board));
                addJoinery(joint, leaf.start, leaf.end, leaf.foot, leaf.head, face * 0.7f, r, g, b);
                for (int i = 0; i < boards; i++) {
                    float from = leaf.start + wide * i / boards + (i == 0 ? 0f : BOARD_GAP / 2f), to = leaf.start + wide * (i + 1) / boards - (i == boards - 1 ? 0f : BOARD_GAP / 2f);
                    addJoinery(wood, from, to, leaf.foot, leaf.head, face, r, g, b);
                }
                for (float at : LEDGES_AT) {
                    float middle = leaf.foot + (leaf.head - leaf.foot) * at, half = LEDGE_HIGH / (2f * level);
                    addJoinery(wood, leaf.start + 0.02f, leaf.end - 0.02f, middle - half, middle + half, face + Math.signum(face) * LEDGE_PROUD, r, g, b);
                }
            } else {
                if (leaf.startWide > 0f) {
                    addJoinery(wood, leaf.start, inStart, leaf.foot, leaf.head, face, r, g, b);
                }
                if (leaf.endWide > 0f) {
                    addJoinery(wood, inEnd, leaf.end, leaf.foot, leaf.head, face, r, g, b);
                }
                if (leaf.headHigh > 0f) {
                    addJoinery(wood, inStart, inEnd, leaf.head - leaf.headHigh, leaf.head, face, r, g, b);
                }
                if (leaf.footHigh > 0f) {
                    addJoinery(wood, inStart, inEnd, leaf.foot, leaf.foot + leaf.footHigh, face, r, g, b);
                }
                for (int bar = 0; bar < leaf.bars.length; bar += 4) {
                    addJoinery(wood, leaf.bars[bar], leaf.bars[bar + 1], leaf.bars[bar + 2], leaf.bars[bar + 3], face, r, g, b);
                }
            }
            if (door.kind == Doors.PANELLED && panelled - leaf.foot > 0.15f) {
                float proud = face + Math.signum(face) * MOULDING_PROUD, high = panelled - leaf.foot, thick = MOULDING_WIDE / level;
                for (int column = 0; column < PANELS_ACROSS.length; column += 2) {
                    float from = leaf.start + wide * PANELS_ACROSS[column], to = leaf.start + wide * PANELS_ACROSS[column + 1];
                    for (int row = 0; row < up.length; row += 2) {
                        float foot = leaf.foot + high * up[row], head = leaf.foot + high * up[row + 1];
                        float mr = r * MOULDING_SHADE, mg = g * MOULDING_SHADE, mb = b * MOULDING_SHADE;
                        addJoinery(wood, from, from + MOULDING_WIDE, foot, head, proud, mr, mg, mb);
                        addJoinery(wood, to - MOULDING_WIDE, to, foot, head, proud, mr, mg, mb);
                        addJoinery(wood, from + MOULDING_WIDE, to - MOULDING_WIDE, foot, foot + thick, proud, mr, mg, mb);
                        addJoinery(wood, from + MOULDING_WIDE, to - MOULDING_WIDE, head - thick, head, proud, mr, mg, mb);
                        // The field of the panel, raised in its middle.
                        addJoinery(wood, from + 2f * MOULDING_WIDE, to - 2f * MOULDING_WIDE, foot + 2f * thick, head - 2f * thick,
                                face + Math.signum(face) * MOULDING_PROUD * 0.5f, r * FIELD_SHADE, g * FIELD_SHADE, b * FIELD_SHADE);
                    }
                }
            }
            // The knob: a neck and a head of eight sides.
            metal.open = open[side] + 32f * BARE_METAL;
            float[] c = build.reveal;
            for (int part = 0; part < 2; part++) {
                float round = part == 0 ? KNOB_ROUND * KNOB_NECK : KNOB_ROUND;
                float from = face + Math.signum(face) * (part == 0 ? 0f : KNOB_OUT * 0.45f), to = face + Math.signum(face) * (part == 0 ? KNOB_OUT * 0.45f : KNOB_OUT);
                for (int turn = 0; turn < 8; turn++) {
                    double a0 = turn * Math.PI / 4.0, a1 = (turn + 1) * Math.PI / 4.0;
                    float t0 = knob + round * (float) Math.cos(a0), h0 = KNOB_HIGH + round * (float) Math.sin(a0) / level;
                    float t1 = knob + round * (float) Math.cos(a1), h1 = KNOB_HIGH + round * (float) Math.sin(a1) / level;
                    windowPoint(c, 0, t0, h0, from);
                    windowPoint(c, 1, t1, h1, from);
                    windowPoint(c, 2, t1, h1, to);
                    windowPoint(c, 3, t0, h0, to);
                    SpriteGeometry.addPlainQuad(metal, c, r, g, b);
                    windowPoint(c, 0, knob, KNOB_HIGH, to);
                    windowPoint(c, 1, t0, h0, to);
                    windowPoint(c, 2, t1, h1, to);
                    windowPoint(c, 3, knob, KNOB_HIGH, to);
                    SpriteGeometry.addPlainQuad(metal, c, r, g, b);
                }
            }
        }
        SpriteGeometry.Mesh glass = WorldScene.laid.get(WorldScene.PLAIN, WallSides.PANES);
        glass.sway = 0f;
        glass.open = build.frame.open + 32f * WorldScene.PANE;
        glass.face(db, da, 0f);
        for (int pane = 0; pane < leaf.panes.length; pane += 5) {
            float t0 = leaf.panes[pane], t1 = leaf.panes[pane + 1], h0 = leaf.panes[pane + 2], h1 = leaf.panes[pane + 3];
            if (leaf.panes[pane + 4] >= 1f) {
                addGlass(glass, t0, h0, 0f, 0f, t1, h0, 1f, 0f, t1, h1, 1f, 1f);
                addGlass(glass, t0, h0, 0f, 0f, t1, h1, 1f, 1f, t0, h1, 0f, 1f);
            } else {
                addShards(glass, t0, t1, h0, h1, leaf.panes[pane + 4], x * 73856093 ^ y * 19349663 ^ z * 83492791 ^ pane * 2654435);
            }
        }
        return true;
    }

    /**
     * A window on the north or west edge of the square, built: the four sides of its frame
     * through the wall's thickness, a sill outside, its bars, and a sheet of glass in each
     * light that has one, the pieces left round the edge of one that is broken. Where and how
     * thick each is, and the frame's colour, are measured off the sprite: see Windows. Each
     * side of the glass is lit as its own side of the wall is. False, adding nothing, for a
     * sprite not measured yet or one that is no window in a square frame.
     */
    boolean addWindow(SpriteGeometry.Mesh wall, Texture texture, int x, int y, int z, float da, float db) {
        boolean glazedOnly = wall != null;
        boolean north = da != 0f;
        Windows.Shape shape = Windows.of(texture, build.frame, north);
        if (shape == null) {
            // The sprite is measured by the next frame drawn. A build that went without is
            // made again when next due, though nothing it read of the map has changed.
            if (Windows.unread(texture)) {
                build.building.signed = false;
            }
            return false;
        }
        if (glazedOnly && shape.panes.length == 0) {
            return false;
        }
        IsoGridSquare beyond = build.cell.getGridSquare(north ? x : x - 1, north ? y - 1 : y, z);
        boolean ownIsRoom = build.here.getRoomID() != -1L || beyond == null || beyond.getRoomID() == -1L;
        boolean beyondIsRoom = beyond != null && beyond.getRoomID() != -1L;
        boolean lit = WorldScene.lamplit(build.cell, ownIsRoom ? build.here : beyond, build.playerIndex, build.asTheGameShows || build.gamesView, build.lampsNear);

        windowX = x;
        windowY = y;
        windowAlongX = da;
        windowAlongY = db;
        windowFoot = z * ViewCamera.LEVEL_HEIGHT + (build.geometry.lift[0] + build.geometry.lift[1] + build.geometry.lift[2] + build.geometry.lift[3]) / 4f;
        int ownLight = (build.waterLocalX + 1) * WorldScene.LIGHT_SPAN + build.waterLocalY + 1, beyondLight = north ? ownLight - 1 : ownLight - WorldScene.LIGHT_SPAN;
        boolean farUnseen = build.lightOpen[beyondLight] < 0;
        float[] red = {build.frame.red, farUnseen ? build.unseenLight.r : build.lightRed[beyondLight]};
        float[] green = {build.frame.green, farUnseen ? build.unseenLight.g : build.lightGreen[beyondLight]};
        float[] blue = {build.frame.blue, farUnseen ? build.unseenLight.b : build.lightBlue[beyondLight]};
        float[] open = {build.frame.open, farUnseen ? 1f : build.lightOpen[beyondLight]};

        SpriteGeometry.Mesh joinery = WorldScene.solid.get(WorldScene.PLAIN, WallSides.key(shape.red, shape.green, shape.blue, WallSides.FLAT));
        joinery.sway = 0f;
        float made = shape.red - shape.blue > WOODEN ? WorldScene.WOOD : WorldScene.PAINT;
        float level = ViewCamera.LEVEL_HEIGHT, lap = FRAME_LAP / level;
        float deep = WorldScene.WALL_HALF + FRAME_PROUD;
        float inStart = shape.start + shape.startWide, inEnd = shape.end - shape.endWide;
        for (int side = 0; side < 2; side++) {
            float out = side == 0 ? 1f : -1f;
            joinery.open = open[side] + 32f * made;
            float r = red[side], g = green[side], b = blue[side];
            // A frame with no stile at one end runs on into the window on the next square.
            windowLit = side;
            if (shape.startWide > 0f) {
                addMember(joinery, wall, x, y, z, shape.start - FRAME_LAP, inStart, shape.foot - lap, shape.head + lap, out * deep, r, g, b, open[side]);
            }
            if (shape.endWide > 0f) {
                addMember(joinery, wall, x, y, z, inEnd, shape.end + FRAME_LAP, shape.foot - lap, shape.head + lap, out * deep, r, g, b, open[side]);
            }
            addMember(joinery, wall, x, y, z, inStart, inEnd, shape.head - shape.headHigh, shape.head + lap, out * deep, r, g, b, open[side]);
            addMember(joinery, wall, x, y, z, inStart, inEnd, shape.foot - lap, shape.foot + shape.footHigh, out * deep, r, g, b, open[side]);
            if (shape.arch != null) {
                addArch(joinery, shape, out * deep, r, g, b);
            }
            // Rain is thrown clear of the wall by a sill, which a window has on the side that is out of doors.
            if (shape.foot > SILL_LOWEST && (side == 0 ? !ownIsRoom : ownIsRoom && !beyondIsRoom)) {
                float pastStart = shape.startWide > 0f ? SILL_PAST + FRAME_LAP : 0f, pastEnd = shape.endWide > 0f ? SILL_PAST + FRAME_LAP : 0f;
                addJoinery(joinery, shape.start - pastStart, shape.end + pastEnd, shape.foot - lap - SILL_THICK / level, shape.foot - lap + 0.004f / level,
                        out * (WorldScene.WALL_HALF + SILL_OUT), r, g, b);
            }
            for (int bar = 0; bar < shape.bars.length; bar += 4) {
                float wide = Math.min(shape.bars[bar + 1] - shape.bars[bar], (shape.bars[bar + 3] - shape.bars[bar + 2]) * level);
                addJoinery(joinery, shape.bars[bar], shape.bars[bar + 1], shape.bars[bar + 2], shape.bars[bar + 3],
                        out * Math.clamp(wide * BAR_DEPTH, BAR_THINNEST, BAR_DEEPEST), r, g, b);
            }
        }

        SpriteGeometry.Mesh glass = WorldScene.laid.get(WorldScene.PLAIN, WallSides.PANES);
        glass.sway = 0f;
        glass.open = build.frame.open + 32f * (lit ? WorldScene.LIT_PANE : WorldScene.PANE);
        // A pane faces into its room, which is how the picture tells its street side.
        float faces = ownIsRoom ? 1f : -1f;
        glass.face(db * faces, da * faces, 0f);
        for (int pane = 0; pane < shape.panes.length; pane += 5) {
            float t0 = shape.panes[pane], t1 = shape.panes[pane + 1], h0 = shape.panes[pane + 2], h1 = shape.panes[pane + 3];
            if (shape.panes[pane + 4] >= 1f) {
                addGlass(glass, t0, h0, 0f, 0f, t1, h0, 1f, 0f, t1, h1, 1f, 1f);
                addGlass(glass, t0, h0, 0f, 0f, t1, h1, 1f, 1f, t0, h1, 0f, 1f);
            } else {
                addShards(glass, t0, t1, h0, h1, shape.panes[pane + 4], x * 73856093 ^ y * 19349663 ^ z * 83492791 ^ pane * 2654435);
            }
        }
        if (shape.arch != null) {
            // The light under the arch, from where it springs up to the frame's inside.
            int places = shape.arch.length / 3;
            float rise = Math.max(shape.arch[(places / 2) * 3 + 1] - shape.head, 0.001f);
            for (int place = 0; place + 1 < places; place++) {
                int at = place * 3;
                if (shape.arch[at + 2] < 0.5f || shape.arch[at + 5] < 0.5f) {
                    continue;
                }
                float t0 = shape.start + (shape.end - shape.start) * place / (places - 1), t1 = shape.start + (shape.end - shape.start) * (place + 1) / (places - 1);
                float u0 = (float) place / (places - 1), u1 = (float) (place + 1) / (places - 1);
                float in0 = shape.arch[at + 1], in1 = shape.arch[at + 4];
                addGlass(glass, t0, shape.head, u0, 0f, t1, shape.head, u1, 0f, t1, in1, u1, (in1 - shape.head) / rise);
                addGlass(glass, t0, shape.head, u0, 0f, t1, in1, u1, (in1 - shape.head) / rise, t0, in0, u0, (in0 - shape.head) / rise);
            }
        }
        return true;
    }

    /**
     * One side of the arched head of a window's frame, from the glass's plane out to across
     * tiles into the square: its face, a length for each place the arch was measured at, and
     * its rims outside and inside.
     */
    private void addArch(SpriteGeometry.Mesh mesh, Windows.Shape shape, float across, float red, float green, float blue) {
        int places = shape.arch.length / 3;
        float[] c = build.reveal;
        for (int place = 0; place + 1 < places; place++) {
            int at = place * 3;
            float t0 = shape.start + (shape.end - shape.start) * place / (places - 1), t1 = shape.start + (shape.end - shape.start) * (place + 1) / (places - 1);
            float out0 = Math.max(shape.arch[at], shape.head), in0 = Math.max(shape.arch[at + 1], shape.head);
            float out1 = Math.max(shape.arch[at + 3], shape.head), in1 = Math.max(shape.arch[at + 4], shape.head);
            if (out0 <= in0 && out1 <= in1) {
                continue;
            }
            windowPoint(c, 0, t0, in0, across);
            windowPoint(c, 1, t1, in1, across);
            windowPoint(c, 2, t1, out1, across);
            windowPoint(c, 3, t0, out0, across);
            SpriteGeometry.addPlainQuad(mesh, c, red, green, blue);
            windowPoint(c, 0, t0, out0, 0f);
            windowPoint(c, 1, t1, out1, 0f);
            windowPoint(c, 2, t1, out1, across);
            windowPoint(c, 3, t0, out0, across);
            SpriteGeometry.addPlainQuad(mesh, c, red, green, blue);
            windowPoint(c, 0, t0, in0, 0f);
            windowPoint(c, 1, t1, in1, 0f);
            windowPoint(c, 2, t1, in1, across);
            windowPoint(c, 3, t0, in0, across);
            SpriteGeometry.addPlainQuad(mesh, c, red, green, blue);
        }
    }

    /**
     * A side of a window's frame. In a window that is a length of wall, a shop's front, the
     * broad parts of the frame are wall, below the glass and over it, and show the wall's own
     * sprite on the skin; their edges, round the glass, are closed in the frame's colour.
     */
    private void addMember(SpriteGeometry.Mesh joinery, SpriteGeometry.Mesh wall, int x, int y, int z, float from, float to, float foot, float head,
            float across, float red, float green, float blue, float open) {
        if (wall == null || Math.min(to - from, (head - foot) * ViewCamera.LEVEL_HEIGHT) <= WALL_MEMBER) {
            addJoinery(joinery, from, to, foot, head, across, red, green, blue);
            return;
        }
        float out = Math.signum(across);
        addJoinery(joinery, from, to, foot, head, out * (WorldScene.WALL_HALF - 0.003f), red, green, blue);
        float start = Math.max(from, 0f), end = Math.min(to, 1f), low = Math.max(foot, 0f), high = Math.min(head, 1f);
        float wasRed = build.frame.red, wasGreen = build.frame.green, wasBlue = build.frame.blue, wasOpen = build.frame.open;
        build.frame.cornersInto(build.heldRed, build.heldGreen, build.heldBlue);
        if (windowLit == 1) {
            build.frame.light(red, green, blue);
            build.frame.open = open;
        }
        build.geometry.shownA = windowAlongY * out * WorldScene.WALL_HALF;
        build.geometry.shownB = windowAlongX * out * WorldScene.WALL_HALF;
        build.geometry.addFace(wall, build.frame, x, y, z, windowAlongX * start, windowAlongY * start, low, windowAlongX * (end - start), windowAlongY * (end - start), 0f,
                0f, 0f, high - low);
        build.geometry.shownA = build.geometry.shownB = 0f;
        build.frame.red = wasRed;
        build.frame.green = wasGreen;
        build.frame.blue = wasBlue;
        build.frame.open = wasOpen;
        build.frame.cornersFrom(build.heldRed, build.heldGreen, build.heldBlue);
    }

    /** Where the point a share along the window's edge, up levels high and across tiles into its square is, into c at i. */
    private void windowPoint(float[] c, int i, float along, float up, float across) {
        WorldScene.set(c, i, windowX + windowAlongX * along + windowAlongY * across, windowY + windowAlongY * along + windowAlongX * across,
                windowFoot + up * ViewCamera.LEVEL_HEIGHT);
    }

    /**
     * One side of a piece of a window's woodwork: from the glass's plane out to across tiles
     * into the square, its face and its four edges. The other side is added with the other
     * side's light.
     */
    private void addJoinery(SpriteGeometry.Mesh mesh, float from, float to, float foot, float head, float across, float red, float green, float blue) {
        float[] c = build.reveal;
        windowPoint(c, 0, from, foot, across);
        windowPoint(c, 1, to, foot, across);
        windowPoint(c, 2, to, head, across);
        windowPoint(c, 3, from, head, across);
        SpriteGeometry.addPlainQuad(mesh, c, red, green, blue);
        for (int edge = 0; edge < 4; edge++) {
            float along0 = edge == 1 ? to : from, along1 = edge == 0 ? from : to;
            float up0 = edge == 3 ? head : foot, up1 = edge == 2 ? foot : head;
            windowPoint(c, 0, along0, up0, 0f);
            windowPoint(c, 1, along1, up1, 0f);
            windowPoint(c, 2, along1, up1, across);
            windowPoint(c, 3, along0, up0, across);
            SpriteGeometry.addPlainQuad(mesh, c, red, green, blue);
        }
    }

    /** A triangle of glass in the window's plane: each corner a share along the edge, a height in levels, and where on its pane it is. */
    private void addGlass(SpriteGeometry.Mesh mesh, float t0, float h0, float u0, float v0, float t1, float h1, float u1, float v1,
            float t2, float h2, float u2, float v2) {
        float[] c = build.reveal;
        windowPoint(c, 0, t0, h0, 0f);
        windowPoint(c, 1, t1, h1, 0f);
        windowPoint(c, 2, t2, h2, 0f);
        mesh.add(c[0], c[1], c[2], u0, v0, build.frame.red, build.frame.green, build.frame.blue);
        mesh.add(c[3], c[4], c[5], u1, v1, build.frame.red, build.frame.green, build.frame.blue);
        mesh.add(c[6], c[7], c[8], u2, v2, build.frame.red, build.frame.green, build.frame.blue);
    }

    /**
     * What is left of a broken pane: teeth of glass standing in from the bars round it, the
     * longer the more of the pane the game's sprite still shows. Which teeth, and how long, is
     * settled by where the window is, so a window keeps its own break.
     */
    private void addShards(SpriteGeometry.Mesh mesh, float t0, float t1, float h0, float h1, float left, int seed) {
        float level = ViewCamera.LEVEL_HEIGHT, wide = t1 - t0, high = (h1 - h0) * level;
        float reach = Math.min(wide, high) * (0.25f + 0.75f * left);
        for (int side = 0; side < 4; side++) {
            float length = side < 2 ? wide : high;
            for (float at = 0f; at < length; ) {
                float base = Math.min(SHARD_NARROWEST + (SHARD_WIDEST - SHARD_NARROWEST) * chance(seed++), length - at);
                // Most of what is left is splinters along the putty, with a long blade here and
                // there, and a blade leans: its point is seldom over its foot.
                float long3 = chance(seed++);
                float tip = Math.clamp(at + base * (SHARD_LEAN * (2f * chance(seed++) - 1f) + 0.5f), 0f, length);
                float in = reach * (0.06f + 0.94f * long3 * long3 * long3);
                boolean stands = chance(seed++) < 0.35f + 0.6f * left;
                if (stands) {
                    // Along the foot, the head, the start and the end of the pane in turn.
                    float a0 = side < 2 ? at / wide : side == 2 ? 0f : 1f, b0 = side < 2 ? (side == 0 ? 0f : 1f) : at / high;
                    float a1 = side < 2 ? (at + base) / wide : a0, b1 = side < 2 ? b0 : (at + base) / high;
                    float a2 = side < 2 ? tip / wide : side == 2 ? in / wide : 1f - in / wide;
                    float b2 = side < 2 ? (side == 0 ? in / high : 1f - in / high) : tip / high;
                    addGlass(mesh, t0 + a0 * wide, h0 + b0 * (h1 - h0), a0, b0, t0 + a1 * wide, h0 + b1 * (h1 - h0), a1, b1,
                            t0 + a2 * wide, h0 + b2 * (h1 - h0), a2, b2);
                }
                at += base;
            }
        }
    }
}
