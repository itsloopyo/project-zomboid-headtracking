package com.cameraunlock.zomboid;

import java.util.Arrays;
import zombie.core.textures.Texture;
import zombie.iso.IsoChunk;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * The poles and the overhead wires of a chunk being built. One to a frame of the world's picture, and it writes into
 * that frame's meshes.
 */
final class WireBuilder {
    /** The corners of a strip in the order of its two triangles: which end of the wire each is at, and which side of its middle. */
    private static final int[] STRIP_END = {0, 1, 1, 0, 1, 0}, STRIP_SIDE = {-1, -1, 1, -1, 1, 1};

    private final WorldScene world;
    private final WorldScene.Frame build;

    private final Spans spans = new Spans(new Spans.Hung() {
        @Override
        public int lines(int x, int y, int z, float[] out) {
            if (!loaded(x, y)) {
                return -1;
            }
            IsoGridSquare square = build.cell.getGridSquare(x, y, z);
            // As a build takes a sprite for wire: one hung on a square of air.
            if (square == null || square.getFloor() != null) {
                return 0;
            }
            int count = 0;
            PZArrayList<IsoObject> objects = square.getObjects();
            for (int i = 0; i < objects.size(); i++) {
                IsoObject object = objects.get(i);
                IsoSprite sprite = object.getSprite();
                if (sprite == null || !Wires.hangs(sprite.getName())) {
                    continue;
                }
                Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
                if (!WorldScene.usable(texture)) {
                    // Its picture is not there to read yet: what the wire does here is not known.
                    return -1;
                }
                build.hungFrame.scale = build.frame.scale;
                WorldScene.frameOf(build.hungFrame, object, texture);
                float[] lines = wiresOn(texture, sprite.getName(), build.hungFrame);
                for (int line = 0; line < lines.length; line += 2, count += 2) {
                    if (count + 2 <= out.length) {
                        out[count] = x + lines[line];
                        out[count + 1] = y + lines[line + 1];
                    }
                }
            }
            return count;
        }

        @Override
        public int pins(int x, int y, int z, float[] out, int at) {
            if (!loaded(x, y)) {
                return -1;
            }
            Poles.Arm arm = Poles.arm(standsOn(x, y, z));
            Posts shaft = arm == null ? null : shaftOf(x, y, z);
            if (shaft == null) {
                return 0;
            }
            float axisX = x + shaft.a, axisY = y + shaft.b;
            for (int which = -1; which <= 1; which++) {
                Poles.insulator(arm, which, axisX, axisY, shaft.half * Poles.TAPER, build.insulatorAt);
                out[at++] = build.insulatorAt[0];
                out[at++] = build.insulatorAt[1];
                out[at++] = axisX;
                out[at++] = axisY;
            }
            return Spans.POLE_PINS;
        }

        @Override
        public boolean settled(int x, int y, int z) {
            return !Poles.unsettled(drawn, loadedHere, x, y, z, build.lowestLevel);
        }
    });
    private final float[] poleFrom = new float[3], poleTo = new float[3], wirePlace = new float[3];
    private final Poles.Drawn drawn = this::poleOn;
    // Made once: a method reference made where it is passed is a new object at every call, and settled is asked for every square a span is checked on.
    private final Poles.Loaded loadedHere = this::loaded;
    private final Poles.Floored floored = this::floored;
    /** The frame the spans last found anything in. */
    private int spansFrame = -1;

    WireBuilder(WorldScene world, WorldScene.Frame build) {
        this.world = world;
        this.build = build;
    }

    /** Where the span found for one line of a wire sprite on a square is kept: line 0-3, pole 4-11, level 12-16, y 17-38, x 39 up. */
    static long spanKey(int x, int y, int z, int pole, int line) {
        if (line < 0 || line > 15 || pole < 0 || pole > 255 || z < 0 || z > 31 || y < 0 || y >= 1 << 22 || x < 0) {
            throw new IllegalArgumentException("wire span key out of range: square " + x + "," + y + "," + z + " pole " + pole + " line " + line);
        }
        return (long) x << 39 | (long) y << 17 | (long) z << 12 | (long) pole << 4 | line;
    }

    /**
     * The wires a sprite of overhead wire shows, each hung where it is painted over the map
     * and as high as its span has it there: a wire is held at the insulators of two poles and
     * hangs between them, whatever the land under it does (Spans). The game's view cannot tell
     * how high a line in the air is, only where it is drawn, and at the height of the
     * insulators the lines of a run come out over their poles' arms, each square's meeting
     * the next square's end to end. False, adding nothing, for a sprite that is no wire: a
     * length of the pole.
     */
    boolean addWires(Texture texture, String name, int x, int y, int z) {
        if (world.frames != spansFrame) {
            spans.forget();
            spansFrame = world.frames;
        }
        float[] lines = wiresOn(texture, name, build.frame);
        int pole = Poles.index(name);
        for (int i = 0; i < lines.length; i += 4) {
            long key = spanKey(x, y, z, pole, i / 4);
            float[] pieces = world.strung.get(key);
            if (pieces == null) {
                pieces = spans.find(x, y, z, x + lines[i], y + lines[i + 1], x + lines[i + 2], y + lines[i + 3]);
                world.strung.put(key, pieces);
                if (FirstPerson.TRACE) {
                    Log.info("wire span: " + x + "," + y + "," + z + " " + name + " line " + i / 4 + " " + Arrays.toString(pieces) + " eye "
                            + Math.round(Math.hypot(x - world.lastEyeX, y - world.lastEyeY)));
                }
            }
            build.wantsMap |= Spans.wanting(pieces);
            for (int piece = 0; piece < pieces.length; piece += Spans.PIECE) {
                int from = piece + Spans.FROM, to = piece + Spans.TO;
                float levelHigh = ViewCamera.LEVEL_HEIGHT;
                float fromUp = (pieces[from + Spans.END_LEVEL] + Wires.UP) * levelHigh + build.landAt(pieces[from + Spans.END_LAND], pieces[from + Spans.END_LAND + 1]);
                float toUp = (pieces[to + Spans.END_LEVEL] + Wires.UP) * levelHigh + build.landAt(pieces[to + Spans.END_LAND], pieces[to + Spans.END_LAND + 1]);
                float sag = Spans.sag(Spans.span(pieces, piece));
                for (int end = 0; end < 2; end++) {
                    float drawnX = pieces[piece + end * 2], drawnY = pieces[piece + end * 2 + 1], along = Spans.along(pieces, piece, drawnX, drawnY);
                    Spans.placed(pieces, piece, drawnX, drawnY, wirePlace);
                    float endX = wirePlace[0], endY = wirePlace[1];
                    float straight = fromUp + (toUp - fromUp) * along;
                    float up = Math.max(straight - Spans.hangs(along, sag), build.landAt(endX, endY) + Spans.CLEAR);
                    WorldScene.set(build.wireEnds, end, endX, endY, up);
                    build.wireHangs[end] = Math.max(0f, straight - up);
                }
                cable(build.wireEnds[0], build.wireEnds[1], build.wireEnds[2], build.wireHangs[0], build.wireEnds[3], build.wireEnds[4], build.wireEnds[5], build.wireHangs[1], Spans.swing(pieces, piece));
            }
        }
        return lines.length > 0;
    }

    /** The wires a sprite shows, as Wires reads them off it in a frame set for it. Empty for a sprite that is no wire. */
    private float[] wiresOn(Texture texture, String name, SpriteGeometry.Frame in) {
        float[] lines = WorldScene.wires.get(texture);
        if (lines == null) {
            lines = Wires.hangs(name) ? Wires.read(texture::isMaskSet, in) : Wires.NONE;
            WorldScene.wires.put(texture, lines);
            if (FirstPerson.TRACE) {
                Log.info("wires read off " + texture.getName() + ": " + Arrays.toString(lines));
            }
        }
        return lines;
    }

    /** Whether the chunk a square is in is loaded. */
    private boolean loaded(int x, int y) {
        int gridX = Math.floorDiv(x, WorldScene.CHUNK_TILES) - world.mapWx, gridY = Math.floorDiv(y, WorldScene.CHUNK_TILES) - world.mapWy;
        if (gridX < 0 || gridY < 0 || gridX >= world.mapSide || gridY >= world.mapSide) {
            return false;
        }
        IsoChunk chunk = world.chunkMap.getChunk(gridX, gridY);
        return chunk != null && chunk.loaded;
    }

    private boolean drawnUp(int x, int y, int z) {
        return Poles.drawnUp(drawn, x, y, z, build.lowestLevel);
    }

    private String standsOn(int x, int y, int z) {
        return Poles.standsOn(drawn, x, y, z, build.lowestLevel);
    }

    /** The shaft of the pole that has a length standing on a square, as Posts reads the lowest of its lengths, or null. */
    private Posts shaftOf(int x, int y, int z) {
        while (z > build.lowestLevel && standsOn(x, y, z - 1) != null) {
            z--;
        }
        if (Poles.drawnFrom(drawn, x, y, z, build.lowestLevel) == Poles.BACK) {
            x--;
            y--;
        }
        PZArrayList<IsoObject> objects = build.cell.getGridSquare(x, y, z).getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            IsoSprite sprite = object.getSprite();
            if (sprite != null && Poles.length(sprite.getName())) {
                Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
                return WorldScene.usable(texture) ? build.postBuilder.postOf(object, sprite, texture) : null;
            }
        }
        return null;
    }

    /** Whether a square has a floor. */
    private boolean floored(int x, int y, int z) {
        IsoGridSquare square = build.cell.getGridSquare(x, y, z);
        return square != null && square.getFloor() != null;
    }

    /** The sprite of the length of pole that stands on a square, by its name, or null. */
    private String poleOn(int x, int y, int z) {
        IsoGridSquare square = build.cell.getGridSquare(x, y, z);
        if (square == null) {
            return null;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite != null && Poles.length(sprite.getName())) {
                return sprite.getName();
            }
        }
        return null;
    }

    /**
     * Builds a pole whole from the lowest of its lengths: one shaft from the ground to its
     * top, and on it the arm its highest length shows, with its braces and the insulators its
     * wires lie on. The lengths above the lowest add nothing more. The top is as high over the
     * land as the wires are hung, so that they meet it. See Poles.
     */
    boolean addPole(SpriteGeometry.Mesh mesh, IsoObject object, IsoSprite sprite, Texture texture, int x, int y, int z) {
        // Where the length is drawn may be a level off where it stands: what is drawn on the map not yet loaded may tell.
        build.wantsMap |= Poles.unsettledPole(drawn, loadedHere, x, y, z, build.lowestLevel);
        if (drawnUp(x, y, z)) {
            return shaftOf(x - Spans.LEVEL_SQUARES, y - Spans.LEVEL_SQUARES, z - 1) != null;
        }
        if (Poles.setBack(drawn, x, y, z)) {
            x++;
            y++;
            build.wantsMap |= Poles.unsettledPole(drawn, loadedHere, x, y, z, build.lowestLevel);
        }
        if (z > build.lowestLevel && standsOn(x, y, z - 1) != null) {
            // Its foot has built it, unless its foot is a sprite Posts cannot read: then each length is left to stand as it did.
            return shaftOf(x, y, z) != null;
        }
        Posts post = build.postBuilder.postOf(object, sprite, texture);
        if (post == null) {
            return false;
        }
        int top = z;
        String highest = sprite.getName();
        for (String above = standsOn(x, y, top + 1); above != null; above = standsOn(x, y, top + 1)) {
            top++;
            highest = above;
        }
        Poles.Arm arm = Poles.arm(highest);
        float axisX = x + post.a, axisY = y + post.b, level = ViewCamera.LEVEL_HEIGHT;
        float foot = Poles.footLevel(floored, x, y, z) * level + build.geometry.liftAt(post.a, post.b), floor = top * level + build.landAt(axisX, axisY);
        float head = floor + (arm == null ? Poles.LENGTH : Poles.TOP) * level, thin = post.half * Poles.TAPER;
        float u = SpriteGeometry.textureU(build.frame, post.a, post.b), v = SpriteGeometry.textureV(build.frame, post.a, post.b, 0.5f);
        mesh.open = build.frame.open + 32f * SpriteGeometry.TIMBER;
        mesh.sway = 0f;
        mesh.lightFlat(build.frame);
        SpriteGeometry.addRound(mesh, axisX, axisY, foot, head, post.half, thin, 12, u, v, build.frame.red, build.frame.green, build.frame.blue);
        build.boxes++;
        if (arm == null) {
            return true;
        }
        float alongX = arm.alongX(), alongY = arm.alongY(), out = thin + Poles.ARM_HALF;
        float armX = axisX - alongY * out, armY = axisY + alongX * out, armUp = floor + Poles.ARM_UP * level;
        WorldScene.set(poleFrom, 0, armX - alongX * arm.half(), armY - alongY * arm.half(), armUp);
        WorldScene.set(poleTo, 0, armX + alongX * arm.half(), armY + alongY * arm.half(), armUp);
        build.geometry.addStrut(mesh, poleFrom, poleTo, Poles.ARM_HALF, u, v, build.frame.red, build.frame.green, build.frame.blue);
        for (int side = -1; side <= 1; side += 2) {
            float at = side * arm.half() * Poles.BRACE_AT;
            WorldScene.set(poleFrom, 0, axisX - alongY * (thin + Poles.BRACE_HALF), axisY + alongX * (thin + Poles.BRACE_HALF), floor + Poles.BRACE_UP * level);
            WorldScene.set(poleTo, 0, armX + alongX * at, armY + alongY * at, armUp - Poles.ARM_HALF);
            build.geometry.addStrut(mesh, poleFrom, poleTo, Poles.BRACE_HALF, u, v, build.frame.red, build.frame.green, build.frame.blue);
        }
        SpriteGeometry.Mesh glazed = WorldScene.solid.get(WorldScene.PLAIN, WallSides.key(Poles.INSULATOR_RED, Poles.INSULATOR_GREEN, Poles.INSULATOR_BLUE, WallSides.FLAT));
        glazed.open = build.frame.open;
        glazed.sway = 0f;
        glazed.lightFlat(build.frame);
        float wire = floor + Wires.UP * level;
        for (int which = -1; which <= 1; which++) {
            boolean onShaft = Poles.insulator(arm, which, axisX, axisY, thin, build.insulatorAt);
            float atX = build.insulatorAt[0], atY = build.insulatorAt[1];
            float from = onShaft ? head : armUp + Poles.ARM_HALF, neck = onShaft ? from : from + (wire - from) * Poles.PIN_SHARE;
            if (!onShaft) {
                SpriteGeometry.addRound(glazed, atX, atY, from, neck, Poles.PIN, Poles.PIN, 6, 0f, 0f, build.frame.red, build.frame.green, build.frame.blue);
            }
            SpriteGeometry.addRound(glazed, atX, atY, neck, wire, Poles.INSULATOR, Poles.INSULATOR * 0.6f, 8, 0f, 0f, build.frame.red, build.frame.green, build.frame.blue);
        }
        return true;
    }

    /**
     * A wire from one point to another: two strips crossed, so that it has a width from below
     * as from the side. A wire has no texel, and where a texel's place is each vertex carries
     * what the picture moves it by as it draws, see wireOff in PipelineShaders: the way
     * across the wire on the map, of a length that says which corner of which strip the
     * vertex is, 1 and 2 for the two sides of the level strip and 3 and 4 for the top and the
     * bottom of the upright one, and after that, between a tenth and nine tenths, where in
     * its swing the wire's span is (swing, 0 to 1). The picture stands a vertex off the
     * middle of the wire where it is far from the eye, so that the wire is never finer than
     * a pixel, and swings it in the wind by how far the wire hangs there below the line
     * between its insulators (hangs0, hangs1), which rides on the length of its facing as
     * a plant's sway does.
     *
     * The way across is the same one for every length of a span, whichever of its ends a
     * length is read from first: lengths that took it for themselves would swing against
     * each other.
     */
    void cable(float x0, float y0, float up0, float hangs0, float x1, float y1, float up1, float hangs1, float swing) {
        SpriteGeometry.Mesh plain = WorldScene.solid.get(WorldScene.PLAIN);
        plain.open = build.frame.open + SpriteGeometry.CARD + 32f * WorldScene.WIRE;
        plain.lightFlat(build.frame);
        float length = (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
        float turned = Spans.acrossSign(-(y1 - y0), x1 - x0) / length;
        float sideX = -(y1 - y0) * turned, sideY = (x1 - x0) * turned, half = WorldScene.WIRE_THICK / 2f, carried = 0.1f + 0.8f * swing;
        for (int strip = 0; strip < 2; strip++) {
            boolean upright = strip == 0;
            float offX = upright ? 0f : sideX, offY = upright ? 0f : sideY, offUp = upright ? 1f : 0f;
            plain.face(upright ? sideX : 0f, upright ? sideY : 0f, upright ? 0f : 1f);
            for (int corner = 0; corner < 6; corner++) {
                int side = STRIP_SIDE[corner];
                boolean far = STRIP_END[corner] == 1;
                float tells = (upright ? 3f : 1f) + (side > 0 ? 0f : 1f) + carried;
                plain.sway = far ? hangs1 : hangs0;
                plain.add((far ? x1 : x0) + offX * side * half, (far ? y1 : y0) + offY * side * half, (far ? up1 : up0) + offUp * side * half,
                        sideX * tells, sideY * tells, build.frame.red, build.frame.green, build.frame.blue);
            }
        }
        plain.sway = 0f;
    }
}
