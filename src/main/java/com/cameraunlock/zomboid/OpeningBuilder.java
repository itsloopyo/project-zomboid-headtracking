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
    static final float DOOR_HALF = 0.022f;
    /** How high up a door its width is read, in levels, and how far in from its edge the colour of its edges is taken, in frame pixels at tile scale 1. */
    private static final float DOOR_READ_AT = 0.4f, DOOR_STILE = 2.5f;
    private static final int SPAN_FLOATS = 4;
    /** Cloth the picture has hung in folds of its own. */
    static final float DRAPE = 69f;
    /** How far a curtain hangs clear of the wall's skin, how thick its rod is and how far the rod runs past the cloth at each end, in tiles. */
    private static final float DRAPE_CLEAR = 0.04f, ROD_THICK = 0.022f, ROD_PAST = 0.04f;
    /** How wide a fold is in a curtain drawn across and the narrowest one in a curtain pulled back, and how deep each is, in tiles. */
    private static final float FOLD_WIDE = 0.055f, FOLD_NARROW = 0.022f, FOLD_DEEP = 0.016f, FOLD_DEEP_BACK = 0.024f;
    /** How many strips a fold is made of, how much of a curtain's height its hem is and how much darker. */
    private static final int FOLD_STRIPS = 6;
    private static final float HEM = 0.07f, HEM_SHADE = 0.86f;
    /** The colour of a curtain rod. */
    private static final float ROD_RED = 0.2f, ROD_GREEN = 0.16f, ROD_BLUE = 0.13f;
    /**
     * A door's raised panel: the groove cut round it, how wide the bevel rising from the groove
     * to the flat field is, and how far the field stands out of the leaf, in tiles. The bevel is
     * at most this share of the panel's narrower side.
     */
    private static final float GROOVE_WIDE = 0.007f, BEVEL_WIDE = 0.034f, FIELD_PROUD = 0.006f, BEVEL_MOST = 0.3f;
    /** How far the groove lies out of the leaf's face, so the two are not drawn through each other. */
    static final float GROOVE_PROUD = 0.0015f;
    /**
     * A room's light is much the same from every side, and shows little relief: the groove is
     * drawn this much darker than the leaf, as its own shadow makes it, the bevel facing down,
     * the two facing sideways and the one facing up these much, and the field this much.
     */
    private static final float GROOVE_SHADE = 0.55f, BEVEL_DOWN_SHADE = 0.74f, BEVEL_SIDE_SHADE = 0.87f, BEVEL_UP_SHADE = 1.04f, FIELD_SHADE = 0.97f;
    /** A hinge's knuckle: how high it is and how far round, in tiles, how far out of the face it stands, and where up the leaf the three are. */
    private static final float HINGE_HIGH = 0.09f, HINGE_ROUND = 0.007f;
    static final float HINGE_OUT = 0.004f;
    private static final float[] HINGES_AT = {0.11f, 0.5f, 0.89f};
    /**
     * The face of a leaf its hinges' knuckles stand out of: side 0, the one at +across. Swung
     * open, the leaf stands SWUNG_OUT into its square from the wall it is back against, so that
     * face looks away from the wall, as a door's knuckles do when it is open square.
     */
    static final int HINGED_SIDE = 0;
    /** The rose a knob stands on: how far round and how far out of the face, and the least it keeps clear of a panel beside it, in tiles. */
    private static final float ROSE_ROUND = 0.03f, ROSE_OUT = 0.004f, ROSE_CLEAR = 0.003f;
    /** How far a door swung open stands into its square from the wall it is back against, to the leaf's middle, in tiles. */
    static final float SWUNG_OUT = WorldScene.WALL_HALF + WorldScene.HUNG_CLEAR + DOOR_HALF;
    /**
     * How far inside the wall's skins the wood filling a doorway's jamb to a shut leaf stands, in tiles. The leaf is
     * measured off its own sprite and the doorway off the wall's, and the two need not meet: what is between them is
     * open through the wall but for the pictures on its two faces. A wall cut for half of a doorway two squares wide has
     * no reveal at its cut, and a leaf ending right at the cut leaves a hairline of the street beside it, so the fill
     * goes JAMB_OVER on into the wall, between its skins.
     */
    private static final float JAMB_IN = 0.004f;
    static final float JAMB_OVER = 0.03f;
    /**
     * Wood the picture gives a grain with no joints across: a door's leaf, a window's frame.
     * JOINERY's runs up; JOINERY_LEVEL's runs along, for a member longer than it is high (a
     * rail, a head, a sill). addJoinery picks between them by the member's shape.
     */
    static final float JOINERY = 86f, JOINERY_LEVEL = 88f;
    /** Where the panels of a door are, as shares of the part of the leaf they are on: across, then up a whole leaf, then up what is below a light. */
    static final float[] PANELS_ACROSS = {0.14f, 0.455f, 0.545f, 0.86f};
    private static final float[] PANELS_UP = {0.1f, 0.37f, 0.45f, 0.8f, 0.86f, 0.95f}, PANELS_UP_BELOW = {0.16f, 0.86f};
    /** A boarded door: the gap between two boards, how far its ledges stand out and how high each is, in tiles, and where up the leaf they are. */
    private static final float BOARD_GAP = 0.005f, BOARD_USUAL = 0.16f, LEDGE_PROUD = 0.014f, LEDGE_HIGH = 0.085f;
    private static final float[] LEDGES_AT = {0.12f, 0.5f, 0.88f};
    /** How much darker than the boards the open joint between two is. */
    private static final float JOINT_SHADE = 0.4f;
    /** A door's knob: how high up it is in levels, how far in from the leaf's end as a share of the leaf, how thick and how far out, in tiles. */
    static final float KNOB_HIGH = 0.4f, KNOB_IN = 0.1f, KNOB_ROUND = 0.017f, KNOB_OUT = 0.034f, KNOB_NECK = 0.4f;
    /** A lever's arm: how long along the leaf from its neck, how high, and how far off the face it starts (where the neck ends), in tiles. */
    static final float LEVER_ARM = 0.1f, LEVER_HIGH = 0.016f, LEVER_NEAR = KNOB_OUT * 0.45f;
    /** A glazing bead: how wide, and how far proud of the face, in tiles. */
    static final float BEAD_WIDE = 0.014f, BEAD_PROUD = 0.004f;
    /**
     * A door's hinges and knob, of metal. A model's own metal is numbered among the models'
     * surfaces, which on a face of the world's own are kinds of floorboard: see WORLD_VERTEX.
     */
    static final float HARDWARE = 87f;
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
    /**
     * A frame redder than it is blue by this share of its brightest channel is bare wood, and anything else is painted. A share,
     * not an amount: the game paints a west face darker than a north one, and the two sprites of one brown door are to read alike.
     * Never less than WOODEN_LEAST, or a black frame a hair warm is wood and the next one a hair cooler paint.
     */
    private static final float WOODEN = 0.2f, WOODEN_LEAST = 0.04f;
    /** A member of a frame wider than this each way, in tiles, is a piece of the wall the window is in and keeps the wall's own picture on its faces. */
    private static final float WALL_MEMBER = 0.14f;
    /**
     * How far, in tiles, the front of such a member stands back from the wall's picture laid over it. Its sides run on to
     * MEMBER_SIDES short of the picture, closing the reveal; a front that near was in the depth buffer's tolerance at any
     * range, and the frame's colour and the wall's fought all down every jamb.
     */
    private static final float MEMBER_UNDER = 0.02f, MEMBER_SIDES = 0.003f;
    /** The cosine and sine of each eighth of a turn, from none to the whole turn: the corners of something of eight sides. */
    private static final float[] EIGHTH_COS = new float[9], EIGHTH_SIN = new float[9];

    static {
        for (int turn = 0; turn <= 8; turn++) {
            EIGHTH_COS[turn] = (float) Math.cos(turn * Math.PI / 4.0);
            EIGHTH_SIN[turn] = (float) Math.sin(turn * Math.PI / 4.0);
        }
    }

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
    /** The light on each side of the window or the door being built, as windowLit numbers them: see lightSides. */
    private final float[] sideRed = new float[2], sideGreen = new float[2], sideBlue = new float[2], sideOpen = new float[2], sideDay = new float[2];
    /** The side the panes being laid face into: see facePane. */
    private int paneSide;
    /**
     * The side of lightSides the joinery being laid is lit from, and the side its edges take their
     * light from, or -1 for its own. A shut leaf's edges and the reveals round its panes are in the
     * jamb's and the glass's shade: lit as the street, the street's half of each showed from a dark
     * room as a bright line round the leaf and its panes.
     */
    private int joinerySide, edgeSide = -1;
    /**
     * Where along the wall a shut leaf ends at a doorway's revealed cut, or NaN: no joinery closes its end there. The
     * wall's reveal is in that plane across the whole thickness, and the leaf's end laid in it too took turns with it
     * down the jamb as a flickering line.
     */
    private float revealedFrom = Float.NaN, revealedTo = Float.NaN;

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
        float out = build.doorSwung ? SWUNG_OUT + build.wallBuilder.logsProud(x, y, z, da != 0f) : 0f;
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
        // Its window is on the kerb's top where it is the north or west edge's (Frame.standOnKerbTop).
        windowFoot = footOf(z) + (far ? 0f : build.kerbTopRise(z, north));
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
                float d0 = out + round * EIGHTH_COS[side], h0 = middle + round * EIGHTH_SIN[side] / level;
                float d1 = out + round * EIGHTH_COS[side + 1], h1 = middle + round * EIGHTH_SIN[side + 1] / level;
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
            double phase = 6.0 * chance(seed + hung);
            for (int strip = 0; strip <= strips; strip++) {
                int fold = strip / FOLD_STRIPS;
                // No two folds hang quite alike, and cloth spreads a little towards its foot.
                float much = deep * (0.65f + 0.7f * chance(seed + hung * 31 + fold));
                float turn = (float) Math.sin(2.0 * Math.PI * strip / FOLD_STRIPS + phase);
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

    /** The doorway or window opening the wall on the square's north or west edge was last measured with: see doorwayIn. */
    private int doorwayX, doorwayY, doorwayZ = Integer.MIN_VALUE;
    private boolean doorwayNorth;
    private float doorwayStart = -1f, doorwayEnd;
    private boolean doorwayRevealed;
    private final float[] jambs = new float[4];

    /**
     * The clear run across the sprite of the wall a doorway or a window is in on a square's edge, as shares of its
     * length, or a start below 0 for none measured, and whether the wall has its reveals at the cut. The wall is added
     * before the door that hangs in it or the window that stands in it.
     */
    void doorwayIn(int x, int y, int z, boolean north, float start, float end, boolean revealed) {
        doorwayX = x;
        doorwayY = y;
        doorwayZ = z;
        doorwayNorth = north;
        doorwayStart = start;
        doorwayEnd = end;
        doorwayRevealed = revealed;
    }

    /**
     * The jambs to fill between a doorway cut from cutStart to cutEnd and a shut leaf from leafStart to leafEnd, as
     * pairs of shares along the wall in out; how many pairs. None with no cut measured, none where the leaf already
     * reaches JAMB_OVER into the wall, and none at an end where the cut reaches the square's edge: there the doorway
     * goes on into the next square, to the other leaf of a pair, and the gap between the two leaves is a real one.
     * None either where the wall's reveal closes the cut (revealed) and the leaf meets it: the fill's edge lay in the
     * reveal's plane, and the two took turns down the jamb as a flickering line.
     */
    static int jambs(float leafStart, float leafEnd, float cutStart, float cutEnd, boolean revealed, float[] out) {
        if (cutStart < 0f) {
            return 0;
        }
        int pairs = 0;
        if (cutStart > 0f && leafStart > cutStart - JAMB_OVER && !(revealed && leafStart <= cutStart)) {
            out[0] = Math.max(0f, cutStart - JAMB_OVER);
            out[1] = leafStart;
            pairs++;
        }
        if (cutEnd < 1f && leafEnd < cutEnd + JAMB_OVER && !(revealed && leafEnd >= cutEnd)) {
            out[2 * pairs] = leafEnd;
            out[2 * pairs + 1] = Math.min(1f, cutEnd + JAMB_OVER);
            pairs++;
        }
        return pairs;
    }

    /**
     * A door, built: a leaf as thick as a door in the colour of the game's sprite, with glass
     * in its lights, raised panels or boards on ledges where the sprite draws them, a knob on
     * a rose on each face and three hinges. Bare wood is joinery, its grain running up the
     * leaf; anything else is painted. Its outline, its lights and how it is worked are measured
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
        if (build.shutTexture != null) {
            // Open, it is the leaf its shut sprite paints, which stands on the other edge.
            Doors.Shape shut = Doors.of(build.shutTexture, build.shutFrame, !north);
            if (shut == null && Doors.unread(build.shutTexture)) {
                build.building.signed = false;
                return false;
            }
            if (shut != null) {
                Doors.tellPair(texture, build.shutTexture);
                door = Doors.swung(door, shut);
            }
        }
        // A garage door rolls up in its own plane and has no jamb to fill, and a door swung open stands clear of its doorway.
        boolean measured = doorwayX == x && doorwayY == y && doorwayZ == z && doorwayNorth == north;
        boolean inDoorway = !build.doorSwung && !build.garageDoor && measured;
        Windows.Shape leaf = inDoorway ? withinCut(door.leaf, doorwayStart, doorwayEnd) : door.leaf;
        float out = build.doorSwung ? SWUNG_OUT + build.wallBuilder.logsProud(x, y, z, da != 0f) : 0f;
        windowX = x + db * out;
        windowY = y + da * out;
        windowAlongX = da;
        windowAlongY = db;
        windowFoot = footOf(z);
        // A door shut in its doorway has a room or the street on each face. Swung open it stands in its own square.
        lightSides(north, build.doorSwung);
        // Swung open, the leaf stands in its own square, which is on both its faces.
        IsoGridSquare beyond = build.doorSwung ? build.here : beyondEdge(north, x, y, z);
        boolean ownIsRoom = indoors(build.here) || !indoors(beyond);
        edgeSide = paneSide(ownIsRoom);

        boolean wooden = door.kind == Doors.PLANKED || wooden(leaf.red, leaf.green, leaf.blue);
        // Kept as finely as a tile's colour (WallSides.FINE): at a fifteenth a channel a dark maroon door rounds to a grey one.
        SpriteGeometry.Mesh wood = WorldScene.solid.get(WorldScene.PLAIN, WallSides.laid(leaf.red, leaf.green, leaf.blue, 0f, 0f, 0f, WallSides.FINE));
        SpriteGeometry.Mesh joint = WorldScene.solid.get(WorldScene.PLAIN,
                WallSides.laid(leaf.red * JOINT_SHADE, leaf.green * JOINT_SHADE, leaf.blue * JOINT_SHADE, 0f, 0f, 0f, WallSides.FINE));
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
        int filled = inDoorway ? jambs(leaf.start, leaf.end, doorwayStart, doorwayEnd, doorwayRevealed, jambs) : 0;
        boolean revealed = inDoorway && doorwayRevealed && doorwayStart >= 0f;
        revealedFrom = revealed && leaf.start <= doorwayStart ? leaf.start : Float.NaN;
        revealedTo = revealed && leaf.end >= doorwayEnd ? leaf.end : Float.NaN;
        float knob = door.handleAtEnd ? leaf.end - KNOB_IN * wide : leaf.start + KNOB_IN * wide;
        float[] bars = barsToRails(leaf);
        for (int side = 0; side < 2; side++) {
            float face = (side == 0 ? 1f : -1f) * DOOR_HALF;
            float r = sideRed[side], g = sideGreen[side], b = sideBlue[side];
            joinerySide = side;
            wood.open = joint.open = sideOpen[side] + 32f * (wooden ? JOINERY : WorldScene.PAINT);
            wood.day = joint.day = sideDay[side];
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
                for (int bar = 0; bar < bars.length; bar += 4) {
                    addJoinery(wood, bars[bar], bars[bar + 1], bars[bar + 2], bars[bar + 3], face, r, g, b);
                }
            }
            // The jamb is the doorway's, not the leaf's: the line round a door E would open goes round the leaf.
            long thing = WorldScene.solid.owner();
            WorldScene.solid.own(Outlines.NONE);
            for (int jamb = 0; jamb < 2 * filled; jamb += 2) {
                addJoinery(wood, jambs[jamb], jambs[jamb + 1], leaf.foot, leaf.head, Math.signum(face) * (WorldScene.WALL_HALF - JAMB_IN), r, g, b);
            }
            WorldScene.solid.own(thing);
            if (door.kind == Doors.PANELLED && panelled - leaf.foot > 0.15f) {
                float high = panelled - leaf.foot;
                for (int column = 0; column < PANELS_ACROSS.length; column += 2) {
                    float from = leaf.start + wide * PANELS_ACROSS[column], to = leaf.start + wide * PANELS_ACROSS[column + 1];
                    for (int row = 0; row < up.length; row += 2) {
                        addRaisedPanel(wood, from, to, leaf.foot + high * up[row], leaf.foot + high * up[row + 1], face, r, g, b);
                    }
                }
            }
            // A bead of wood holds the glass in each light, standing a little proud of the face round it.
            for (int pane = 0; pane < leaf.panes.length; pane += 5) {
                float[] bead = bead(leaf.panes[pane], leaf.panes[pane + 1], leaf.panes[pane + 2], leaf.panes[pane + 3], leaf.start, leaf.end, leaf.foot, leaf.head);
                for (int at = 0; at < bead.length; at += 4) {
                    addJoinery(wood, bead[at], bead[at + 1], bead[at + 2], bead[at + 3], face + Math.signum(face) * BEAD_PROUD, r, g, b);
                }
            }
            // A garage door is lifted by a handle at its foot and rides in tracks: no knob, no hinges.
            if (build.garageDoor) {
                continue;
            }
            metal.open = sideOpen[side] + 32f * HARDWARE;
            metal.day = sideDay[side];
            float[] c = build.reveal;
            if (side == HINGED_SIDE) {
                float[] hinges = hinges(leaf.start, leaf.end, leaf.foot, leaf.head, door.handleAtEnd);
                for (int at = 0; at < hinges.length; at += 4) {
                    addJoinery(metal, hinges[at], hinges[at + 1], hinges[at + 2], hinges[at + 3], face + Math.signum(face) * HINGE_OUT, r, g, b);
                }
            }
            // The knob: a rose on the leaf, then a neck and a head, each of eight sides. The rose
            // keeps clear of a panel beside it. A lever has the rose and the neck, and an arm
            // along the leaf from the neck towards the hinges in place of the head.
            float rose = rose(door.kind == Doors.PANELLED, wide);
            if (door.lever) {
                float[] arm = lever(knob, door.handleAtEnd);
                addBlock(metal, arm[0], arm[1], arm[2], arm[3], face + Math.signum(face) * LEVER_NEAR, face + Math.signum(face) * KNOB_OUT, r, g, b);
            }
            for (int part = 0; part < (door.lever ? 2 : 3); part++) {
                float round = part == 0 ? rose : part == 1 ? KNOB_ROUND * KNOB_NECK : KNOB_ROUND;
                float near = part == 0 ? 0f : part == 1 ? ROSE_OUT : KNOB_OUT * 0.45f, far = part == 0 ? ROSE_OUT : part == 1 ? KNOB_OUT * 0.45f : KNOB_OUT;
                float from = face + Math.signum(face) * near, to = face + Math.signum(face) * far;
                for (int turn = 0; turn < 8; turn++) {
                    float t0 = knob + round * EIGHTH_COS[turn], h0 = KNOB_HIGH + round * EIGHTH_SIN[turn] / level;
                    float t1 = knob + round * EIGHTH_COS[turn + 1], h1 = KNOB_HIGH + round * EIGHTH_SIN[turn + 1] / level;
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
        edgeSide = -1;
        facePane(glass, beyond, false, da, db);
        for (int pane = 0; pane < leaf.panes.length; pane += 5) {
            float t0 = leaf.panes[pane], t1 = leaf.panes[pane + 1], h0 = leaf.panes[pane + 2], h1 = leaf.panes[pane + 3];
            if (leaf.panes[pane + 4] >= 1f) {
                addGlass(glass, t0, h0, 0f, 0f, t1, h0, 1f, 0f, t1, h1, 1f, 1f);
                addGlass(glass, t0, h0, 0f, 0f, t1, h1, 1f, 1f, t0, h1, 0f, 1f);
            } else {
                addShards(glass, t0, t1, h0, h1, leaf.panes[pane + 4], x * 73856093 ^ y * 19349663 ^ z * 83492791 ^ pane * 2654435);
            }
        }
        // Shared with other builders, which leave day as they find it.
        wood.day = joint.day = metal.day = glass.day = 0f;
        revealedFrom = revealedTo = Float.NaN;
        return true;
    }

    /**
     * A door's casing, built: two posts and a head on each face of the wall, boards standing
     * CASING_PROUD out of the skin with their outer edge bevelled, in the colour of the game's
     * sprite. Where the doorway the wall is cut with was measured, the posts' inner edges are at
     * its cut. False, adding nothing, for a sprite not measured yet or one that is no casing: see
     * Casings.
     */
    boolean addBuiltCasing(Texture texture, int x, int y, int z, float da, float db) {
        boolean north = da != 0f;
        Windows.Shape casing = Casings.of(texture, build.frame, north);
        if (casing == null) {
            if (Casings.unread(texture)) {
                build.building.signed = false;
            }
            return false;
        }
        // The sprite's layers would lie both faces into the square and sink the far one behind the far skin.
        WorldScene.layers.none();
        boolean measured = doorwayX == x && doorwayY == y && doorwayZ == z && doorwayNorth == north && doorwayStart >= 0f;
        float[] members = casingMembers(casing, measured ? doorwayStart : -1f, measured ? doorwayEnd : -1f);
        windowX = x;
        windowY = y;
        windowAlongX = da;
        windowAlongY = db;
        windowFoot = footOf(z);
        lightSides(north, false);
        boolean wooden = wooden(casing.red, casing.green, casing.blue);
        SpriteGeometry.Mesh wood = WorldScene.solid.get(WorldScene.PLAIN, WallSides.key(casing.red, casing.green, casing.blue, WallSides.FLAT));
        wood.sway = 0f;
        long thing = WorldScene.solid.owner();
        WorldScene.solid.own(Outlines.NONE);
        for (int side = 0; side < 2; side++) {
            float out = side == 0 ? 1f : -1f;
            wood.day = sideDay[side];
            for (int at = 0; at < members.length; at += CASING_MEMBER) {
                wood.open = sideOpen[side] + 32f * casingMaterial(wooden, (int) members[at + 4]);
                addBoard(wood, members[at], members[at + 1], members[at + 2], members[at + 3], out * (WorldScene.WALL_HALF - CASING_INTO),
                        out * (WorldScene.WALL_HALF + CASING_PROUD), (int) members[at + 4], doorwayRevealed ? (int) members[at + 5] : NO_EDGE,
                        sideRed[side], sideGreen[side], sideBlue[side]);
            }
        }
        WorldScene.solid.own(thing);
        wood.day = 0f;
        return true;
    }

    /** What a casing's board bevelled along edge bevelled is drawn as: the head's grain runs along it, a post's up it. */
    static float casingMaterial(boolean wooden, int bevelled) {
        return !wooden ? WorldScene.PAINT : bevelled == AT_HEAD ? JOINERY_LEVEL : JOINERY;
    }

    /**
     * How far a casing's boards stand out of the wall's skin, and how far in from its outer edge and back from its front the
     * bevel along that edge runs, in tiles. A skirting runs on behind a post's foot: as proud as the skirting (Trims.BOARD),
     * the two fronts fought down the foot of every post.
     */
    static final float CASING_PROUD = 0.022f, CASING_BEVEL = 0.009f;
    /** How far a casing's boards run back into the wall behind its skin, so no gap shows between the two at a slant. */
    private static final float CASING_INTO = 0.004f;
    /** How near the doorway's cut a post's inner edge has to be read to be put at it, as a share of the wall's length. */
    static final float CASING_SNAP = 0.05f;
    /**
     * Floats a member of a casing takes in casingMembers: along from, along to, foot and head in
     * levels, which of its edges is bevelled, as addBoard numbers them: the edge at from, at to,
     * the foot, the head; and which edge is at the doorway's cut, or NO_EDGE.
     */
    static final int CASING_MEMBER = 6;
    static final int AT_FROM = 0, AT_TO = 1, AT_HEAD = 3, NO_EDGE = -1;

    /**
     * The boards of a casing, CASING_MEMBER floats each: the posts, then the head over them. The
     * sprite paints the post nearer its view's side with that side's face as well, so two posts are
     * each as wide as the narrower. A post whose inner edge is within CASING_SNAP of the doorway's
     * cut, cutStart or cutEnd (below 0 for none measured), has its inner edge at the cut, and says so.
     */
    static float[] casingMembers(Windows.Shape casing, float cutStart, float cutEnd) {
        float inStart = casing.start + casing.startWide, inEnd = casing.end - casing.endWide;
        boolean startAtCut = casing.startWide > 0f && cutStart >= 0f && Math.abs(inStart - cutStart) < CASING_SNAP;
        boolean endAtCut = casing.endWide > 0f && cutStart >= 0f && Math.abs(inEnd - cutEnd) < CASING_SNAP;
        if (startAtCut) {
            inStart = cutStart;
        }
        if (endAtCut) {
            inEnd = cutEnd;
        }
        float wide = casing.startWide > 0f && casing.endWide > 0f ? Math.min(casing.startWide, casing.endWide) : Math.max(casing.startWide, casing.endWide);
        float start = casing.startWide > 0f ? inStart - wide : inStart, end = casing.endWide > 0f ? inEnd + wide : inEnd;
        float[] made = new float[CASING_MEMBER * 3];
        int count = 0;
        float headFoot = casing.head - casing.headHigh;
        if (casing.startWide > 0f) {
            count = member(made, count, start, inStart, casing.foot, headFoot, AT_FROM, startAtCut ? AT_TO : NO_EDGE);
        }
        if (casing.endWide > 0f) {
            count = member(made, count, inEnd, end, casing.foot, headFoot, AT_TO, endAtCut ? AT_FROM : NO_EDGE);
        }
        count = member(made, count, start, end, headFoot, casing.head, AT_HEAD, NO_EDGE);
        return Arrays.copyOf(made, count);
    }

    private static int member(float[] made, int at, float from, float to, float foot, float head, int bevelled, int atCut) {
        made[at] = from;
        made[at + 1] = to;
        made[at + 2] = foot;
        made[at + 3] = head;
        made[at + 4] = bevelled;
        made[at + 5] = atCut;
        return at + CASING_MEMBER;
    }

    /**
     * A board from along from to along to and foot to head in levels, between across back and front
     * tiles into the square: its front, its four edges, and a bevel CASING_BEVEL wide and deep along
     * the edge bevelled, numbered as in CASING_MEMBER. The two edges beside the bevel are cut back
     * with it. The edge atCut runs back only to the skin: behind it is the wall's reveal, in the
     * same plane, and the two fought down the doorway's side of every post.
     */
    private void addBoard(SpriteGeometry.Mesh mesh, float from, float to, float foot, float head, float back, float front, int bevelled, int atCut,
            float red, float green, float blue) {
        float[] c = build.reveal;
        float behind = front - Math.signum(front) * CASING_BEVEL;
        float f0 = bevelled == AT_FROM ? from + CASING_BEVEL : from, f1 = bevelled == AT_TO ? to - CASING_BEVEL : to;
        float h1 = bevelled == AT_HEAD ? head - CASING_BEVEL / ViewCamera.LEVEL_HEIGHT : head;
        windowPoint(c, 0, f0, foot, front);
        windowPoint(c, 1, f1, foot, front);
        windowPoint(c, 2, f1, h1, front);
        windowPoint(c, 3, f0, h1, front);
        SpriteGeometry.addPlainQuad(mesh, c, red, green, blue);
        for (int edge = 0; edge < 4; edge++) {
            float along0 = edge == 1 ? to : from, along1 = edge == 0 ? from : to;
            float up0 = edge == 3 ? head : foot, up1 = edge == 2 ? foot : head;
            if (edge == bevelled) {
                windowPoint(c, 0, along0, up0, back);
                windowPoint(c, 1, along1, up1, back);
                windowPoint(c, 2, along1, up1, behind);
                windowPoint(c, 3, along0, up0, behind);
                SpriteGeometry.addPlainQuad(mesh, c, red, green, blue);
                continue;
            }
            // An edge beside the bevel is a quad back to the bevel's depth and one from there to the front, cut along the bevel.
            boolean upright = edge < 2;
            boolean beside = bevelled == AT_HEAD ? upright : !upright;
            float uncut0 = upright ? up0 : along0, uncut1 = upright ? up1 : along1;
            float cutEnd = bevelled == AT_HEAD ? head : bevelled == AT_FROM ? from : to, cutFront = bevelled == AT_HEAD ? h1 : bevelled == AT_FROM ? f0 : f1;
            float fixed = upright ? along0 : up0;
            float edgeBack = edge == atCut ? Math.signum(front) * WorldScene.WALL_HALF : back;
            float edgeFront = beside ? behind : front;
            edgePoint(c, 0, upright, fixed, uncut0, edgeBack);
            edgePoint(c, 1, upright, fixed, uncut1, edgeBack);
            edgePoint(c, 2, upright, fixed, uncut1, edgeFront);
            edgePoint(c, 3, upright, fixed, uncut0, edgeFront);
            SpriteGeometry.addPlainQuad(mesh, c, red, green, blue);
            if (beside) {
                float near0 = Math.abs(uncut0 - cutEnd) < 1e-6f ? cutFront : uncut0, near1 = Math.abs(uncut1 - cutEnd) < 1e-6f ? cutFront : uncut1;
                edgePoint(c, 0, upright, fixed, uncut0, behind);
                edgePoint(c, 1, upright, fixed, uncut1, behind);
                edgePoint(c, 2, upright, fixed, near1, front);
                edgePoint(c, 3, upright, fixed, near0, front);
                SpriteGeometry.addPlainQuad(mesh, c, red, green, blue);
            }
        }
        // The bevel, from the front's edge back to the edge's side.
        if (bevelled == AT_HEAD) {
            windowPoint(c, 0, f0, h1, front);
            windowPoint(c, 1, f1, h1, front);
            windowPoint(c, 2, to, head, behind);
            windowPoint(c, 3, from, head, behind);
        } else {
            float edgeAt = bevelled == AT_FROM ? from : to, frontAt = bevelled == AT_FROM ? f0 : f1;
            windowPoint(c, 0, frontAt, foot, front);
            windowPoint(c, 1, frontAt, head, front);
            windowPoint(c, 2, edgeAt, head, behind);
            windowPoint(c, 3, edgeAt, foot, behind);
        }
        SpriteGeometry.addPlainQuad(mesh, c, red, green, blue);
    }

    /** A point of an upright edge (along fixed, up at) or a level one (up fixed, along at), across tiles into the square, into c at i. */
    private void edgePoint(float[] c, int i, boolean upright, float fixed, float at, float across) {
        windowPoint(c, i, upright ? fixed : at, upright ? at : fixed, across);
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
            if (Windows.unread(texture, north)) {
                build.building.signed = false;
            }
            return false;
        }
        if (glazedOnly && shape.panes.length == 0) {
            return false;
        }
        if (doorwayX == x && doorwayY == y && doorwayZ == z && doorwayNorth == north) {
            shape = fitted(shape, doorwayStart, doorwayEnd);
        }
        IsoGridSquare beyond = beyondEdge(north, x, y, z);
        boolean ownIsRoom = indoors(build.here) || !indoors(beyond);
        boolean beyondIsRoom = indoors(beyond);
        boolean lit = WorldScene.lamplit(build.cell, ownIsRoom ? build.here : beyond, build.playerIndex, build.asTheGameShows || build.gamesView, build.lampsNear);

        windowX = x;
        windowY = y;
        windowAlongX = da;
        windowAlongY = db;
        windowFoot = footOf(z);
        lightSides(north, false);

        SpriteGeometry.Mesh joinery = WorldScene.solid.get(WorldScene.PLAIN, WallSides.key(shape.red, shape.green, shape.blue, WallSides.FLAT));
        joinery.sway = 0f;
        float made = wooden(shape.red, shape.green, shape.blue) ? JOINERY : WorldScene.PAINT;
        float level = ViewCamera.LEVEL_HEIGHT, lap = FRAME_LAP / level;
        float deep = WorldScene.WALL_HALF + FRAME_PROUD;
        float inStart = shape.start + shape.startWide, inEnd = shape.end - shape.endWide;
        for (int side = 0; side < 2; side++) {
            float out = side == 0 ? 1f : -1f;
            joinery.open = sideOpen[side] + 32f * made;
            joinery.day = sideDay[side];
            float r = sideRed[side], g = sideGreen[side], b = sideBlue[side];
            // A frame with no stile at one end runs on into the window on the next square.
            windowLit = side;
            if (shape.startWide > 0f) {
                addMember(joinery, wall, x, y, z, shape.start - FRAME_LAP, inStart, shape.foot - lap, shape.head + lap, out * deep, r, g, b, sideOpen[side]);
            }
            if (shape.endWide > 0f) {
                addMember(joinery, wall, x, y, z, inEnd, shape.end + FRAME_LAP, shape.foot - lap, shape.head + lap, out * deep, r, g, b, sideOpen[side]);
            }
            addMember(joinery, shape.headSolid ? wall : null, x, y, z, inStart, inEnd, shape.head - shape.headHigh, shape.head + lap, out * deep, r, g, b, sideOpen[side]);
            addMember(joinery, shape.footSolid ? wall : null, x, y, z, inStart, inEnd, shape.foot - lap, shape.foot + shape.footHigh, out * deep, r, g, b, sideOpen[side]);
            if (shape.arch != null) {
                addArch(joinery, shape, out * deep, r, g, b);
            }
            // Rain is thrown clear of the wall by a sill, which a window has on the side that is out of doors, past a log wall's logs.
            if (shape.foot > SILL_LOWEST && (side == 0 ? !ownIsRoom : ownIsRoom && !beyondIsRoom)) {
                float pastStart = shape.startWide > 0f ? SILL_PAST + FRAME_LAP : 0f, pastEnd = shape.endWide > 0f ? SILL_PAST + FRAME_LAP : 0f;
                addJoinery(joinery, shape.start - pastStart, shape.end + pastEnd, shape.foot - lap - SILL_THICK / level, shape.foot - lap + 0.004f / level,
                        out * (WorldScene.WALL_HALF + build.wallBuilder.logsProud(x, y, z, north) + SILL_OUT), r, g, b);
            }
            for (int bar = 0; bar < shape.bars.length; bar += 4) {
                float wide = Math.min(shape.bars[bar + 1] - shape.bars[bar], (shape.bars[bar + 3] - shape.bars[bar + 2]) * level);
                addJoinery(joinery, shape.bars[bar], shape.bars[bar + 1], shape.bars[bar + 2], shape.bars[bar + 3],
                        out * Math.clamp(wide * BAR_DEPTH, BAR_THINNEST, BAR_DEEPEST), r, g, b);
            }
        }

        SpriteGeometry.Mesh glass = WorldScene.laid.get(WorldScene.PLAIN, WallSides.PANES);
        glass.sway = 0f;
        facePane(glass, beyond, lit, da, db);
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
        if (indoors(build.here) != beyondIsRoom) {
            addBlind(shape, beyondIsRoom ? TallHalls.roomOf(build.cell, beyond.getX(), beyond.getY(), beyond.getZ())
                    : TallHalls.roomOf(build.cell, build.here.getX(), build.here.getY(), build.here.getZ()), beyondIsRoom ? 1 : 0);
        }
        joinery.day = glass.day = 0f;
        return true;
    }

    /**
     * The window carried out to the ends of the opening cut in its wall, from cutStart to cutEnd along the edge, where
     * its sprite stops short of one by no more than FIT_MOST: the opening is see-through wherever the window does not
     * cover it. A stile is widened out to the cut's end, and an end with no stile, which runs on into the window on
     * the next square, takes its panes and bars along. The shape itself where nothing is to be carried out, or no cut
     * was measured (a start below 0); otherwise a copy, the shape being every square's that shows its sprite.
     */
    static Windows.Shape fitted(Windows.Shape shape, float cutStart, float cutEnd) {
        boolean fromStart = cutStart >= 0f && shape.start > cutStart && shape.start - cutStart <= FIT_MOST;
        boolean toEnd = cutStart >= 0f && shape.end < cutEnd && cutEnd - shape.end <= FIT_MOST;
        if (!fromStart && !toEnd) {
            return shape;
        }
        Windows.Shape fit = new Windows.Shape();
        fit.originU = shape.originU;
        fit.originV = shape.originV;
        fit.scale = shape.scale;
        fit.north = shape.north;
        fit.start = fromStart ? cutStart : shape.start;
        fit.end = toEnd ? cutEnd : shape.end;
        fit.startWide = shape.startWide > 0f && fromStart ? shape.startWide + shape.start - cutStart : shape.startWide;
        fit.endWide = shape.endWide > 0f && toEnd ? shape.endWide + cutEnd - shape.end : shape.endWide;
        fit.foot = shape.foot;
        fit.head = shape.head;
        fit.footHigh = shape.footHigh;
        fit.headHigh = shape.headHigh;
        fit.red = shape.red;
        fit.green = shape.green;
        fit.blue = shape.blue;
        fit.arch = shape.arch;
        fit.bars = runOn(shape.bars, 4, shape, fit);
        fit.panes = runOn(shape.panes, 5, shape, fit);
        return fit;
    }

    /**
     * The shut leaf cut back to the doorway cut in its wall from cutStart to cutEnd, at each end where it runs on past
     * the cut into the wall and the cut does not run to the square's edge (where the doorway goes on to the other leaf of
     * a pair). The leaf is measured off its own sprite and the doorway off the wall's, and the two can be a pixel apart:
     * what of the leaf runs on inside the wall is never seen, and its edge stood behind the cut-out edge of the wall's
     * skin. The shape itself where nothing is cut back or no cut was measured (a start below 0); otherwise a copy.
     */
    static Windows.Shape withinCut(Windows.Shape shape, float cutStart, float cutEnd) {
        boolean atStart = cutStart > 0f && shape.start < cutStart, atEnd = cutStart >= 0f && cutEnd < 1f && shape.end > cutEnd;
        if (!atStart && !atEnd) {
            return shape;
        }
        Windows.Shape cut = new Windows.Shape();
        cut.originU = shape.originU;
        cut.originV = shape.originV;
        cut.scale = shape.scale;
        cut.north = shape.north;
        cut.start = atStart ? cutStart : shape.start;
        cut.end = atEnd ? cutEnd : shape.end;
        cut.startWide = shape.startWide > 0f ? Math.max(shape.startWide - (cut.start - shape.start), 0f) : 0f;
        cut.endWide = shape.endWide > 0f ? Math.max(shape.endWide - (shape.end - cut.end), 0f) : 0f;
        cut.foot = shape.foot;
        cut.head = shape.head;
        cut.footHigh = shape.footHigh;
        cut.headHigh = shape.headHigh;
        cut.red = shape.red;
        cut.green = shape.green;
        cut.blue = shape.blue;
        cut.arch = shape.arch;
        cut.bars = clipped(shape.bars, 4, cut.start, cut.end);
        cut.panes = clipped(shape.panes, 5, cut.start, cut.end);
        return cut;
    }

    /** The bars or panes, each so many numbers, kept within start to end along the edge; those left with no length go. */
    private static float[] clipped(float[] list, int each, float start, float end) {
        float[] kept = new float[list.length];
        int count = 0;
        for (int at = 0; at < list.length; at += each) {
            float from = Math.max(list[at], start), to = Math.min(list[at + 1], end);
            if (to <= from) {
                continue;
            }
            System.arraycopy(list, at, kept, count, each);
            kept[count] = from;
            kept[count + 1] = to;
            count += each;
        }
        return Arrays.copyOf(kept, count);
    }

    /** How far, in tiles along the edge, a window is carried out to the end of its wall's opening: two stiles' width. */
    static final float FIT_MOST = 0.15f;
    /** A bar or a pane that ends this near the end of a window with no stile there ends at it. */
    private static final float RUNS_ON = 1e-4f;

    /** The bars or panes, each so many numbers, of a window with no stile at an end, carried with that end from where it was to where it is. */
    private static float[] runOn(float[] list, int each, Windows.Shape was, Windows.Shape fit) {
        float[] moved = list.clone();
        for (int at = 0; at < moved.length; at += each) {
            if (was.startWide == 0f && Math.abs(moved[at] - was.start) < RUNS_ON) {
                moved[at] = fit.start;
            }
            if (was.endWide == 0f && Math.abs(moved[at + 1] - was.end) < RUNS_ON) {
                moved[at + 1] = fit.end;
            }
        }
        return moved;
    }

    /**
     * What the street sees through a window into a room the eye does not see into (RoomSight.blind):
     * a dark sheet across the opening at the room's face of the wall, lit as the room is, where
     * the room left out would leave the glass showing through the building.
     */
    private void addBlind(Windows.Shape shape, long room, int side) {
        long given = WorldScene.solid.room;
        build.contentsOf(RoomSight.blind(room));
        SpriteGeometry.Mesh sheet = WorldScene.solid.get(WorldScene.PLAIN, WallSides.key(BLIND_RED, BLIND_GREEN, BLIND_BLUE, WallSides.FLAT));
        sheet.sway = 0f;
        sheet.open = sideOpen[side] + 32f * WorldScene.PAINT;
        sheet.day = sideDay[side];
        float head = shape.head;
        if (shape.arch != null) {
            for (int at = 0; at < shape.arch.length; at += 3) {
                head = Math.max(head, shape.arch[at]);
            }
        }
        float[] c = build.reveal;
        float across = (side == 0 ? 1f : -1f) * BLIND_IN;
        windowPoint(c, 0, shape.start - BLIND_PAST, shape.foot - BLIND_PAST, across);
        windowPoint(c, 1, shape.end + BLIND_PAST, shape.foot - BLIND_PAST, across);
        windowPoint(c, 2, shape.end + BLIND_PAST, head + BLIND_PAST, across);
        windowPoint(c, 3, shape.start - BLIND_PAST, head + BLIND_PAST, across);
        SpriteGeometry.addPlainQuad(sheet, c, sideRed[side], sideGreen[side], sideBlue[side]);
        sheet.day = 0f;
        build.contentsOf(given);
    }

    /** A blind's colour, a room's dim inside, and how far into the wall from the glass it stands and past the opening it reaches, in tiles. */
    private static final float BLIND_RED = 0.2f, BLIND_GREEN = 0.18f, BLIND_BLUE = 0.16f;
    private static final float BLIND_IN = WorldScene.WALL_HALF * 0.9f, BLIND_PAST = 0.03f;

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
        addJoinery(joinery, from, to, foot, head, out * (WorldScene.WALL_HALF - MEMBER_SIDES), out * (WorldScene.WALL_HALF - MEMBER_UNDER), red, green, blue);
        float start = Math.max(from, 0f), end = Math.min(to, 1f), low = Math.max(foot, 0f), high = Math.min(head, 1f);
        float wasRed = build.frame.red, wasGreen = build.frame.green, wasBlue = build.frame.blue, wasOpen = build.frame.open;
        build.frame.cornersInto(build.heldRed, build.heldGreen, build.heldBlue, build.heldUnlit);
        if (windowLit == 1) {
            build.frame.light(red, green, blue);
            build.frame.open = open;
            Arrays.fill(build.frame.cornerDay, sideDay[1]);
        }
        build.geometry.shownA = windowAlongY * out * WorldScene.WALL_HALF;
        build.geometry.shownB = windowAlongX * out * WorldScene.WALL_HALF;
        // The skin's own plane, where the skin is as opaque as this is: laid a layer behind it, this shows only through the
        // skin's opening. In the plane, the two fought down every jamb; in front, it stood off the wall as a paler slab.
        WorldScene.layers.lie(windowAlongY != 0f ? SpriteGeometry.LAYERS_X : SpriteGeometry.LAYERS_Y, -out);
        build.geometry.addFace(wall, build.frame, x, y, z, windowAlongX * start, windowAlongY * start, low, windowAlongX * (end - start), windowAlongY * (end - start), 0f,
                0f, 0f, high - low);
        WorldScene.layers.none();
        build.geometry.shownA = build.geometry.shownB = 0f;
        build.frame.red = wasRed;
        build.frame.green = wasGreen;
        build.frame.blue = wasBlue;
        build.frame.open = wasOpen;
        build.frame.cornersFrom(build.heldRed, build.heldGreen, build.heldBlue, build.heldUnlit);
    }

    /** The height of the foot of a window or a door on level z of the square being added: the floor there, raised by the mean of the ground's lift at its corners. */
    private float footOf(int z) {
        float[] lift = build.geometry.lift;
        return z * ViewCamera.LEVEL_HEIGHT + (lift[0] + lift[1] + lift[2] + lift[3]) / 4f;
    }

    /**
     * Sets the light on the two sides of the window or the door on the north or west edge of the square being added:
     * its own square's on side 0, and on side 1 the square's beyond the edge, or the open ground's (OPEN_UNLIT) where that square is not
     * seen. With same, both sides are lit as the square's own.
     */
    private void lightSides(boolean north, boolean same) {
        int ownLight = (build.waterLocalX + 1) * WorldScene.LIGHT_SPAN + build.waterLocalY + 1, beyondLight = north ? ownLight - 1 : ownLight - WorldScene.LIGHT_SPAN;
        boolean unseen = !same && build.lightOpen[beyondLight] < 0;
        sideRed[0] = build.frame.red;
        sideGreen[0] = build.frame.green;
        sideBlue[0] = build.frame.blue;
        sideOpen[0] = build.frame.open;
        sideRed[1] = same ? build.frame.red : unseen ? 1f : build.lightRed[beyondLight];
        sideGreen[1] = same ? build.frame.green : unseen ? 1f : build.lightGreen[beyondLight];
        sideBlue[1] = same ? build.frame.blue : unseen ? 1f : build.lightBlue[beyondLight];
        sideOpen[1] = same ? build.frame.open : unseen ? WorldScene.OPEN_UNLIT : build.openOf(beyondLight);
        sideDay[0] = build.lightDay[ownLight];
        sideDay[1] = same ? sideDay[0] : unseen ? 0f : build.lightDay[beyondLight];
    }

    static boolean wooden(float red, float green, float blue) {
        return red - blue > Math.max(WOODEN_LEAST, WOODEN * Math.max(red, Math.max(green, blue)));
    }

    /** The square across the north or west edge of square x, y on level z, null where the map has none. */
    private IsoGridSquare beyondEdge(boolean north, int x, int y, int z) {
        return build.cell.getGridSquare(north ? x : x - 1, north ? y - 1 : y, z);
    }

    /** In a room and under its roof: a square of a room that is out of doors, under a shop's canopy, is the street's. */
    static boolean indoors(IsoGridSquare square) {
        return square != null && square.getRoomID() != -1L && !square.isOutside();
    }

    /**
     * Makes the panes laid next on the edge face into the room, lit as the room is (see
     * lightSides), and of the material paneMaterial gives: the room side's light is what a
     * pane mirrors to an eye in the room, and on a south or east wall the square the edge is
     * read from is the street. Which side is the room's is roomSide's.
     */
    private void facePane(SpriteGeometry.Mesh glass, IsoGridSquare beyond, boolean lit, float da, float db) {
        boolean hereIndoors = indoors(build.here), beyondIndoors = indoors(beyond);
        boolean ownIsRoom = roomSide(hereIndoors, roofed(build.here), beyondIndoors), beyondIsRoom = roomSide(beyondIndoors, roofed(beyond), hereIndoors);
        paneSide = paneSide(ownIsRoom || !beyondIsRoom);
        glass.open = sideOpen[paneSide] + 32f * paneMaterial(ownIsRoom, beyondIsRoom, lit);
        glass.day = sideDay[paneSide];
        float faces = paneSide == 0 ? 1f : -1f;
        glass.face(db * faces, da * faces, 0f);
    }

    /**
     * Whether one side of a pane is a room's to paneMaterial: a room's square, or, with no room
     * on either side, a square under a roof. A building the player has put up has no rooms, and
     * its roofed squares are not out of doors (IsoGridSquare.isOutside), which is how WindowGlass
     * takes an eye in it for indoors and lays it the room's picture.
     */
    static boolean roomSide(boolean indoors, boolean roofed, boolean otherIndoors) {
        return indoors || !otherIndoors && roofed;
    }

    /** Whether a square is under a roof: none past the loaded map is. */
    static boolean roofed(IsoGridSquare square) {
        return square != null && !square.isOutside();
    }

    /** Which of lightSides' two sides a pane faces into: 0 the square the edge is read from, 1 the one beyond. */
    static int paneSide(boolean ownIsRoom) {
        return ownIsRoom ? 0 : 1;
    }

    /**
     * What a pane in an edge is made of, from whether the square the edge is read from and the
     * one beyond are rooms (roomSide). It faces into a room where it has one (or into the
     * square, with the street both sides), and mirrors the street only to an eye on its other
     * face: with a room on both sides, a window between two rooms or a door, it has no street
     * side and mirrors only the room the eye is in; with the street on both, a door swung open
     * in the open air, it mirrors the street to either face. A lamp behind a pane lights it
     * for the street, so a pane with no street side, or no room, is never lit.
     */
    static float paneMaterial(boolean ownIsRoom, boolean beyondIsRoom, boolean lit) {
        return ownIsRoom && beyondIsRoom ? WorldScene.INNER_PANE : !ownIsRoom && !beyondIsRoom ? WorldScene.OUTER_PANE : lit ? WorldScene.LIT_PANE : WorldScene.PANE;
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
        addJoinery(mesh, from, to, foot, head, across, across, red, green, blue);
    }

    /** As addJoinery, its sides out to across and its front at front. */
    private void addJoinery(SpriteGeometry.Mesh mesh, float from, float to, float foot, float head, float across, float front, float red, float green, float blue) {
        float open = mesh.open;
        if (Math.floor((open % WorldScene.LIT_STEP + 0.5f) / 32f) == JOINERY && to - from > (head - foot) * ViewCamera.LEVEL_HEIGHT) {
            mesh.open = open + 32f * (JOINERY_LEVEL - JOINERY);
        }
        float[] c = build.reveal;
        windowPoint(c, 0, from, foot, front);
        windowPoint(c, 1, to, foot, front);
        windowPoint(c, 2, to, head, front);
        windowPoint(c, 3, from, head, front);
        SpriteGeometry.addPlainQuad(mesh, c, red, green, blue);
        float day = mesh.day;
        if (edgeSide >= 0) {
            mesh.open += sideOpen[edgeSide] - sideOpen[joinerySide];
            mesh.day = sideDay[edgeSide];
            red = sideRed[edgeSide];
            green = sideGreen[edgeSide];
            blue = sideBlue[edgeSide];
        }
        for (int edge = 0; edge < 4; edge++) {
            if (edge == 0 && from == revealedFrom || edge == 1 && to == revealedTo) {
                continue;
            }
            float along0 = edge == 1 ? to : from, along1 = edge == 0 ? from : to;
            float up0 = edge == 3 ? head : foot, up1 = edge == 2 ? foot : head;
            windowPoint(c, 0, along0, up0, 0f);
            windowPoint(c, 1, along1, up1, 0f);
            windowPoint(c, 2, along1, up1, across);
            windowPoint(c, 3, along0, up0, across);
            SpriteGeometry.addPlainQuad(mesh, c, red, green, blue);
        }
        mesh.open = open;
        mesh.day = day;
    }

    /**
     * A door's bars, those that run between lights to the highest lights' head or the lowest
     * lights' foot carried on to the rail there. The lights are read from the first whole dot
     * under the head rail and over the foot rail, so a rail a part dot high leaves its lights
     * and bars short of it, and a bar left short opens a slot between the beads over it.
     */
    static float[] barsToRails(Windows.Shape leaf) {
        float[] bars = leaf.bars.clone();
        if (leaf.panes.length == 0) {
            return bars;
        }
        float lightsHead = -Float.MAX_VALUE, lightsFoot = Float.MAX_VALUE;
        for (int pane = 0; pane < leaf.panes.length; pane += 5) {
            lightsFoot = Math.min(lightsFoot, leaf.panes[pane + 2]);
            lightsHead = Math.max(lightsHead, leaf.panes[pane + 3]);
        }
        float headRail = leaf.head - leaf.headHigh, footRail = leaf.foot + leaf.footHigh;
        for (int bar = 0; bar < bars.length; bar += 4) {
            if (bars[bar + 3] == lightsHead && headRail > lightsHead) {
                bars[bar + 3] = headRail;
            }
            if (bars[bar + 2] == lightsFoot && footRail < lightsFoot) {
                bars[bar + 2] = footRail;
            }
        }
        return bars;
    }

    /**
     * The four strips of a bead round a light from along t0 to t1 and from foot h0 to head h1 in
     * levels: each strip's along from, along to, foot and head. Each lies half over the glass
     * and half over the wood round it, the head and foot across the whole light, and none past
     * the leaf's start, end, foot or head, where a light with no stile or rail beside it runs.
     */
    static float[] bead(float t0, float t1, float h0, float h1, float start, float end, float foot, float head) {
        float half = BEAD_WIDE / 2f, up = half / ViewCamera.LEVEL_HEIGHT;
        float from = Math.max(start, t0 - half), to = Math.min(end, t1 + half), low = Math.max(foot, h0 - up), high = Math.min(head, h1 + up);
        return new float[] {
                from, to, low, h0 + up,
                from, to, h1 - up, high,
                from, t0 + half, h0 + up, h1 - up,
                t1 - half, to, h0 + up, h1 - up};
    }

    /** A lever's arm: along from, along to, foot and head in levels, from the neck at along knob towards the hinges, at the end away from the handle. */
    static float[] lever(float knob, boolean handleAtEnd) {
        float half = LEVER_HIGH / (2f * ViewCamera.LEVEL_HEIGHT);
        float far = handleAtEnd ? knob - LEVER_ARM : knob + LEVER_ARM, near = handleAtEnd ? knob + KNOB_ROUND : knob - KNOB_ROUND;
        return new float[] {Math.min(far, near), Math.max(far, near), KNOB_HIGH - half, KNOB_HIGH + half};
    }

    /** A block standing off a leaf's face, from along from to along to, foot to head in levels, and across tiles out of the leaf's middle from near to far: its six faces. */
    private void addBlock(SpriteGeometry.Mesh mesh, float from, float to, float foot, float head, float near, float far, float red, float green, float blue) {
        float[] c = build.reveal;
        for (int side = 0; side < 6; side++) {
            for (int corner = 0; corner < 4; corner++) {
                int i = BLOCK_FACES[side][corner];
                windowPoint(c, corner, (i & 1) == 0 ? from : to, (i & 2) == 0 ? foot : head, (i & 4) == 0 ? near : far);
            }
            SpriteGeometry.addPlainQuad(mesh, c, red, green, blue);
        }
    }

    /** The corners of each face of a block, as bits: along, up, across. */
    private static final int[][] BLOCK_FACES = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};

    /** How far round a knob's rose is on a leaf so wide, in tiles: on a panelled one, no further than leaves it clear of the panel beside the knob. */
    static float rose(boolean panelled, float wide) {
        return panelled ? Math.min(ROSE_ROUND, (PANELS_ACROSS[0] - KNOB_IN) * wide - ROSE_CLEAR) : ROSE_ROUND;
    }

    /**
     * Where a leaf's three hinges are, from start to end along the edge and foot to head in
     * levels: each knuckle's along from, along to, foot and head. They are on the edge away
     * from the knob.
     */
    static float[] hinges(float start, float end, float foot, float head, boolean handleAtEnd) {
        float edge = handleAtEnd ? start : end, half = HINGE_HIGH / (2f * ViewCamera.LEVEL_HEIGHT);
        float[] made = new float[4 * HINGES_AT.length];
        for (int i = 0; i < HINGES_AT.length; i++) {
            float middle = foot + (head - foot) * HINGES_AT[i];
            made[4 * i] = edge - HINGE_ROUND;
            made[4 * i + 1] = edge + HINGE_ROUND;
            made[4 * i + 2] = middle - half;
            made[4 * i + 3] = middle + half;
        }
        return made;
    }

    /** Floats a quad of a raised panel takes in raisedPanel: four corners of along, up and across, then the share of the light it is drawn in. */
    static final int PANEL_QUAD = 13;

    /**
     * A raised panel on one face of a door's leaf, from along from to along to and from foot to
     * head in levels, on the face across tiles out of the leaf's middle: a groove round its
     * edge, then a bevel on each side rising to the flat field in its middle. Its quads, each
     * PANEL_QUAD floats, the groove's four first, then the bevels facing down, up and to each
     * side, then the field. Each quad's corners go round the same way, the turn of along into up.
     */
    static float[] raisedPanel(float from, float to, float foot, float head, float face) {
        float level = ViewCamera.LEVEL_HEIGHT, out = Math.signum(face);
        float groove = GROOVE_WIDE, grooveUp = GROOVE_WIDE / level;
        float onFace = face + out * GROOVE_PROUD;
        float a0 = from + groove, a1 = to - groove, f0 = foot + grooveUp, h0 = head - grooveUp;
        float bevel = Math.min(BEVEL_WIDE, BEVEL_MOST * Math.min(a1 - a0, (h0 - f0) * level));
        float b0 = a0 + bevel, b1 = a1 - bevel, g0 = f0 + bevel / level, g1 = h0 - bevel / level, field = face + out * FIELD_PROUD;
        float[][] quads = {
                {from, foot, onFace, a0, foot, onFace, a0, head, onFace, from, head, onFace, GROOVE_SHADE},
                {a1, foot, onFace, to, foot, onFace, to, head, onFace, a1, head, onFace, GROOVE_SHADE},
                {a0, foot, onFace, a1, foot, onFace, a1, f0, onFace, a0, f0, onFace, GROOVE_SHADE},
                {a0, h0, onFace, a1, h0, onFace, a1, head, onFace, a0, head, onFace, GROOVE_SHADE},
                {a0, f0, onFace, a1, f0, onFace, b1, g0, field, b0, g0, field, BEVEL_DOWN_SHADE},
                {b0, g1, field, b1, g1, field, a1, h0, onFace, a0, h0, onFace, BEVEL_UP_SHADE},
                {a0, f0, onFace, b0, g0, field, b0, g1, field, a0, h0, onFace, BEVEL_SIDE_SHADE},
                {b1, g0, field, a1, f0, onFace, a1, h0, onFace, b1, g1, field, BEVEL_SIDE_SHADE},
                {b0, g0, field, b1, g0, field, b1, g1, field, b0, g1, field, FIELD_SHADE}};
        float[] made = new float[quads.length * PANEL_QUAD];
        for (int quad = 0; quad < quads.length; quad++) {
            System.arraycopy(quads[quad], 0, made, quad * PANEL_QUAD, PANEL_QUAD);
        }
        return made;
    }

    private void addRaisedPanel(SpriteGeometry.Mesh mesh, float from, float to, float foot, float head, float face, float red, float green, float blue) {
        float[] quads = raisedPanel(from, to, foot, head, face), c = build.reveal;
        for (int at = 0; at < quads.length; at += PANEL_QUAD) {
            for (int corner = 0; corner < 4; corner++) {
                windowPoint(c, corner, quads[at + 3 * corner], quads[at + 3 * corner + 1], quads[at + 3 * corner + 2]);
            }
            float shade = quads[at + 12];
            SpriteGeometry.addPlainQuad(mesh, c, red * shade, green * shade, blue * shade);
        }
    }

    /** A triangle of glass in the window's plane: each corner a share along the edge, a height in levels, and where on its pane it is. */
    private void addGlass(SpriteGeometry.Mesh mesh, float t0, float h0, float u0, float v0, float t1, float h1, float u1, float v1,
            float t2, float h2, float u2, float v2) {
        float[] c = build.reveal;
        windowPoint(c, 0, t0, h0, 0f);
        windowPoint(c, 1, t1, h1, 0f);
        windowPoint(c, 2, t2, h2, 0f);
        float r = sideRed[paneSide], g = sideGreen[paneSide], b = sideBlue[paneSide];
        mesh.add(c[0], c[1], c[2], u0, v0, r, g, b);
        mesh.add(c[3], c[4], c[5], u1, v1, r, g, b);
        mesh.add(c[6], c[7], c[8], u2, v2, r, g, b);
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
