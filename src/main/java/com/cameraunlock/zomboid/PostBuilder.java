package com.cameraunlock.zomboid;

import java.util.Arrays;

import zombie.core.properties.PropertyContainer;
import zombie.core.textures.Texture;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * The posts of a chunk being built and the sprites read as shapes: what stands in a wall's line, a post, a fence's
 * rail, a thing given a shape of its own. One to a frame of the world's picture, and it writes into that frame's meshes.
 */
final class PostBuilder {
    /** How near the edge of its square a post's foot is for the post to be in the line of the wall there, in tiles. */
    private static final float IN_WALL_LINE = 0.3f;
    /** What stands in a wall's line as a flat piece is at least this tall, in levels, this long, in tiles, and this many times as long as its foot is wide. */
    private static final float BRACED_TALL = 0.9f, BRACED_LONG = 0.6f, BRACED_SPREAD = 3f;
    /** How far past its square and how high a piece in a wall's line may reach: the face is cut to what the sprite paints. */
    private static final float BRACED_REACH = 0.5f, BRACED_HIGH = 1.5f;
    private static final Object NO_POST = new Object();
    /** How far apart, in tiles, the axes of two lengths of one post may be read on the squares one above the other. */
    private static final float SAME_POST = 0.2f;

    private final WorldScene.Frame build;

    private final SpriteGeometry.Frame shapeFrame = new SpriteGeometry.Frame();

    PostBuilder(WorldScene.Frame build) {
        this.build = build;
    }

    /**
     * A post with braces to a beam, an arch, a trellis: something tall whose foot is against
     * the edge of a square that has a wall or a fence on that edge, and which spreads along
     * it far wider than its foot. It is part of that wall's line and flat in its plane. Stood
     * on the middle of its square to face the eye, as a thing of unknown shape is, it is half
     * a square out from the wall it belongs to and turns as the eye passes. What is narrow all
     * the way up, a pole or a lamp post, stays a card: flat in the wall's line it would be gone
     * seen from along the wall. False, adding nothing, for anything else.
     */
    boolean addInWallLine(SpriteGeometry.Mesh mesh, IsoSprite sprite, Texture texture, int x, int y, int z) {
        if (!build.edgeWest && !build.edgeNorth) {
            return false;
        }
        float[] foot = WorldScene.feet.get(sprite);
        if (foot == null) {
            foot = new float[3];
            if (!SpriteShape.foot(texture::isMaskSet, build.frame, foot)) {
                foot = WorldScene.NO_BOLE;
            }
            foot = WorldScene.feet.keep(sprite, foot);
        }
        float pixelsPerTile = SpriteGeometry.PIXELS_ACROSS_PER_TILE * build.frame.scale;
        if (foot.length == 0 || build.frame.height < BRACED_TALL * SpriteGeometry.PIXELS_PER_LEVEL * build.frame.scale || build.frame.width < BRACED_LONG * pixelsPerTile
                || build.frame.width < BRACED_SPREAD * foot[2] * pixelsPerTile) {
            return false;
        }
        boolean west = build.edgeWest && foot[0] >= 0f && foot[0] < IN_WALL_LINE, north = build.edgeNorth && foot[1] >= 0f && foot[1] < IN_WALL_LINE;
        if (!west && !north) {
            return false;
        }
        build.frame.kind = SpriteGeometry.CARD;
        // The foot found is the corner nearest the game's view of something standing against
        // the edge, so its middle is half way back to the edge.
        if (west && (!north || foot[0] <= foot[1])) {
            build.geometry.addFace(mesh, build.frame, x, y, z, foot[0] / 2f, -BRACED_REACH, 0f, 0f, 1f + 2f * BRACED_REACH, 0f, 0f, 0f, BRACED_HIGH);
        } else {
            build.geometry.addFace(mesh, build.frame, x, y, z, -BRACED_REACH, foot[1] / 2f, 0f, 1f + 2f * BRACED_REACH, 0f, 0f, 0f, 0f, BRACED_HIGH);
        }
        build.frame.kind = 0f;
        return true;
    }

    /** The post an object's sprite is a picture of, read the first time the sprite is met, or null. */
    Posts postOf(IsoObject object, IsoSprite sprite, Texture texture) {
        Object post = WorldScene.posts.get(sprite);
        if (post == null) {
            shapeFrame.scale = build.frame.scale;
            WorldScene.frameOf(shapeFrame, object, texture);
            // A pole the game takes for a trunk is round, though its foot is drawn to a point.
            post = Posts.read(texture::isMaskSet, shapeFrame, "Tree".equals(sprite.getProperties().get("PhysicsShape")));
            if (post == null) {
                post = NO_POST;
            }
            post = WorldScene.posts.keep(sprite, post);
        }
        return post == NO_POST ? null : (Posts) post;
    }

    /** The model of the upper length of a post on a square, which a lower length is stood to meet, or null. */
    private Furniture upperLengthOn(int x, int y, int z) {
        IsoGridSquare square = Own.furniture() ? build.cell.getGridSquare(x, y, z) : null;
        if (square == null) {
            return null;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            Furniture piece = sprite == null ? null : Furniture.known(sprite);
            if (piece != null && piece.postFoot > 0f) {
                return piece;
            }
        }
        return null;
    }

    /** The length of post on a square whose axis is within SAME_POST of (a, b), or null. */
    private Posts postAt(int x, int y, int z, float a, float b) {
        IsoGridSquare square = build.cell.getGridSquare(x, y, z);
        if (square == null) {
            return null;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            IsoSprite sprite = object.getSprite();
            if (sprite == null) {
                continue;
            }
            Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
            if (!WorldScene.usable(texture)) {
                continue;
            }
            Posts post = postOf(object, sprite, texture);
            if (post != null && Math.abs(post.a - a) < SAME_POST && Math.abs(post.b - b) < SAME_POST) {
                return post;
            }
        }
        return null;
    }

    /**
     * Stands a sprite as the post its outline is, and says whether the sprite is thereby
     * dealt with: see Posts. A post taller than a storey is a sprite on each level, and every
     * length of it stands on the axis of the lowest, since each sprite's own reading of where
     * it stands is a little different.
     */
    boolean addPost(SpriteGeometry.Mesh mesh, IsoObject object, IsoSprite sprite, Texture texture, int x, int y, int z) {
        Posts post = postOf(object, sprite, texture);
        // A flat sign seen on the slant has upright sides and rows all of one width, as a
        // shaft has: its outline says it is a sheet, and it stays one.
        if (post == null || shapeOf(object, sprite, texture).kind == SpriteShape.PANEL) {
            return false;
        }
        float a = post.a, b = post.b;
        for (int under = z - 1; under >= build.lowestLevel; under--) {
            Posts below = postAt(x, y, under, a, b);
            if (below == null) {
                break;
            }
            a = below.a;
            b = below.b;
        }
        // Read each off its own sprite, a lower length and the model of the upper one on it met
        // a hand to one side and a step thinner or thicker: the lower length is stood to the model.
        Furniture upper = sprite.getName() != null && sprite.getName().startsWith(Furniture.STREET_LAMPS) ? upperLengthOn(x, y, z + 1) : null;
        if (upper != null) {
            a = upper.centreX;
            b = upper.centreY;
            post = post.meeting(upper.postFoot * upper.scale);
        }
        PropertyContainer properties = sprite.getProperties();
        String made = properties.get("Material"), kind = properties.get("MaterialType");
        boolean wooden = made != null && (made.startsWith("Wood") || made.equals("Log")) || kind != null && kind.startsWith("Wood")
                || sprite.getName() != null && sprite.getName().startsWith("carpentry_");
        // Braces lie along the wall or the fence their post stands against, where there is one.
        boolean bracesAlongX = build.edgeWest && a < IN_WALL_LINE && !(build.edgeNorth && b < a) ? false
                : build.edgeNorth && b < IN_WALL_LINE ? true : post.armsAlongX();
        build.geometry.standPost(mesh, build.frame, x, y, z, post, a, b, wooden && post.kind == Posts.SQUARE, bracesAlongX,
                postAt(x, y, z + 1, a, b) == null);
        build.boxes++;
        return true;
    }

    private final float[] run = new float[2], face = new float[2], next = new float[2], walked = new float[2], ownLift = new float[4];
    private float runDepth, runLand;
    private final SpriteGeometry.Frame runFrame = new SpriteGeometry.Frame();

    /**
     * The paint on the back of a board standing in the air, over the sprite's outline, and how
     * far behind its face the back is, in tiles. The game paints only the side a board shows to
     * its view; drawn from behind, that side read as its lettering mirrored.
     */
    static final int SIGN_BACK = WallSides.key(0.36f, 0.37f, 0.38f, WallSides.FLAT);
    static final float SIGN_BACK_GAP = 0.04f;
    /** How far behind its lettering a billboard's board is laid, in tiles: in front of the lettering's back. */
    static final float BOARD_GAP = 0.02f;

    /**
     * Stands a piece of a sign the game hangs on nothing, one of a run of pieces that meet in
     * the game's picture, in the plane of the whole run (see EdgeRuns.board), and its back into
     * back, SIGN_BACK_GAP behind it, away from the side the game paints. False, adding nothing,
     * for a piece that meets no other. A run is of pieces that are all solid or all not: a
     * billboard's face is pasted over its board on the same squares, and each is a run of its own.
     */
    boolean addFreeSign(SpriteGeometry.Mesh mesh, SpriteGeometry.Mesh back, int x, int y, int z, boolean solid) {
        for (int way = 0; way < 2; way++) {
            boolean alongX = way == 0;
            int dx = alongX ? 1 : 0, dy = alongX ? 0 : 1;
            EdgeRuns.along(build.frame, dx, dy, run);
            if (!freePieceOn(x + dx, y + dy, z, alongX, 1, run, solid) && !freePieceOn(x - dx, y - dy, z, alongX, -1, run, solid)) {
                continue;
            }
            // A board's picture meets the lettering pasted on it only in the lettering's plane.
            if (solid && freeSignOn(x, y, z, alongX, face)) {
                place(x, y, z, alongX, face, false);
                runDepth -= BOARD_GAP;
            } else {
                place(x, y, z, alongX, run, solid);
            }
            SpriteShape board = EdgeRuns.board(build.frame, alongX, run, runDepth);
            float[] lift = build.geometry.lift;
            System.arraycopy(lift, 0, ownLift, 0, 4);
            float[] kerb = build.geometry.kerb;
            Arrays.fill(lift, runLand);
            build.geometry.kerb = null;
            build.geometry.addPanel(mesh, build.frame, x, y, z, board);
            // The same face, so the same outline, moved back: the game paints a board for a view
            // from the south and the east, so its back is to the north or the west.
            int from = back.floats;
            build.geometry.addPanel(back, build.frame, x, y, z, board);
            for (int at = from + (alongX ? 1 : 0); at < back.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                back.vertices[at] -= SIGN_BACK_GAP;
            }
            System.arraycopy(ownLift, 0, lift, 0, 4);
            build.geometry.kerb = kerb;
            return true;
        }
        return false;
    }

    /**
     * Into runDepth and runLand, the plane the run of the piece on (x, y) painted along as
     * along reads is stood in (see EdgeRuns.board), and the one lift of the whole run: the
     * land's at its middle. On each square's own lift, a board over a slope bent where the
     * ground does, as the KY-Transit billboard on Muldraugh's main street did over its six
     * squares.
     */
    private void place(int x, int y, int z, boolean alongX, float[] along, boolean solid) {
        int dx = alongX ? 1 : 0, dy = alongX ? 0 : 1;
        int behind = 0;
        System.arraycopy(along, 0, walked, 0, 2);
        while (freePieceOn(x - (behind + 1) * dx, y - (behind + 1) * dy, z, alongX, -1, walked, solid)) {
            behind++;
            System.arraycopy(next, 0, walked, 0, 2);
        }
        runDepth = -startOfWhole(x - behind * dx, y - behind * dy, z, alongX, solid);
        int ahead = 0;
        System.arraycopy(along, 0, walked, 0, 2);
        while (freePieceOn(x + (ahead + 1) * dx, y + (ahead + 1) * dy, z, alongX, 1, walked, solid)) {
            ahead++;
            System.arraycopy(next, 0, walked, 0, 2);
        }
        float middle = (ahead - behind) / 2f + 0.5f;
        runLand = alongX ? build.landAt(x + middle, y + runDepth) : build.landAt(x + runDepth, y + middle);
    }

    /** Whether (x, y) holds a piece of a sign hung on nothing that is not solid, with where along it paints into along. */
    private boolean freeSignOn(int x, int y, int z, boolean alongX, float[] along) {
        PZArrayList<IsoObject> objects = build.cell.getGridSquare(x, y, z).getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            IsoSprite sprite = object.getSprite();
            if (sprite == null || sprite.getProperties().has(IsoFlagType.solid) || !hangsOnNothing(sprite.getProperties())
                    || !EdgeRuns.freeSign(sprite.getName(), sprite.getProperties().get("CustomName"), sprite.getProperties().has("streetlight"))) {
                continue;
            }
            Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
            if (!WorldScene.usable(texture)) {
                continue;
            }
            runFrame.scale = build.frame.scale;
            WorldScene.frameOf(runFrame, object, texture);
            EdgeRuns.along(runFrame, alongX ? 1f : 0f, alongX ? 0f : 1f, along);
            return true;
        }
        return false;
    }

    /**
     * Where along its square the first piece of a run that is a whole square long starts, the
     * run's first piece starting at (x, y) with walked holding where it paints; the first
     * piece's start where none is. A whole piece starts at its square's edge, as SpriteShape
     * stands a whole panel; the end piece of a billboard is cut short at its picture's edge,
     * so the run is not stood by it: stood by that, a billboard's face stood 0.23 of a square
     * nearer the street than its catwalk.
     */
    private float startOfWhole(int x, int y, int z, boolean alongX, boolean solid) {
        int dx = alongX ? 1 : 0, dy = alongX ? 0 : 1;
        float first = walked[0];
        for (int step = 1; !EdgeRuns.whole(walked[0], walked[1]) && freePieceOn(x + step * dx, y + step * dy, z, alongX, 1, walked, solid); step++) {
            System.arraycopy(next, 0, walked, 0, 2);
        }
        return EdgeRuns.whole(walked[0], walked[1]) ? walked[0] : first;
    }

    /**
     * Whether the square (x, y) holds a piece of a sign that hangs on nothing and carries on
     * from one painted from[0] to from[1] on the square before it, that way along the run (1
     * along x or y, -1 back), solid or not as solid says. Leaves next with where that piece paints.
     */
    private boolean freePieceOn(int x, int y, int z, boolean alongX, int way, float[] from, boolean solid) {
        IsoGridSquare square = build.cell.getGridSquare(x, y, z);
        if (square == null) {
            return false;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            IsoSprite sprite = object.getSprite();
            if (sprite == null || sprite.getProperties().has(IsoFlagType.solid) != solid || !hangsOnNothing(sprite.getProperties()) || !EdgeRuns.freeSign(sprite.getName(), sprite.getProperties().get("CustomName"),
                    sprite.getProperties().has("streetlight"))) {
                continue;
            }
            Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
            if (!WorldScene.usable(texture)) {
                continue;
            }
            runFrame.scale = build.frame.scale;
            WorldScene.frameOf(runFrame, object, texture);
            EdgeRuns.along(runFrame, alongX ? 1f : 0f, alongX ? 0f : 1f, next);
            if (way > 0 ? EdgeRuns.carriesOn(from[0], from[1], next[0], next[1]) : EdgeRuns.carriesOn(next[0], next[1], from[0], from[1])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the game hangs a sprite on no wall at all, and it is nothing climbed: a billboard's
     * post, with the ladder up to its catwalk, is on the billboard's sheet and on a square of
     * its board.
     */
    static boolean hangsOnNothing(PropertyContainer properties) {
        return !properties.has(IsoFlagType.attachedN) && !properties.has(IsoFlagType.attachedW) && !properties.has(IsoFlagType.attachedE)
                && !properties.has(IsoFlagType.attachedS) && !properties.has(IsoFlagType.attachedNW) && !properties.has(IsoFlagType.attachedSE)
                && !properties.has(IsoFlagType.WallOverlay) && !properties.has("ladderN") && !properties.has("ladderW");
    }

    /** The shape of an object's sprite, read the first time the sprite is met. */
    SpriteShape shapeOf(IsoObject object, IsoSprite sprite, Texture texture) {
        SpriteShape shape = WorldScene.shapes.get(sprite);
        if (shape == null) {
            shapeFrame.scale = build.frame.scale;
            WorldScene.frameOf(shapeFrame, object, texture);
            shape = SpriteShape.read(texture::isMaskSet, shapeFrame);
            if (shape.kind == SpriteShape.BEAM) {
                // Its solid paint is read by the next frame drawn: until then it is the beam its outline makes, and the build is made again.
                Solids.Outline solid = Solids.of(texture, shapeFrame);
                if (solid == null) {
                    build.building.signed = false;
                    return shape;
                }
                shape = SpriteShape.withoutShadow(shape, solid, shapeFrame);
            }
            shape = WorldScene.shapes.keep(sprite, shape);
        }
        return shape;
    }

    /**
     * Stands a sprite as the panel or lays it as the beam its outline is, and says whether
     * the sprite is thereby dealt with. False for any other outline, for a length of panel
     * with no whole one beside it to say where it stands, and for a beam the game calls
     * furniture: a bench is as long and as low as a rafter, and is no box.
     */
    boolean addShaped(SpriteGeometry.Mesh mesh, IsoObject object, IsoSprite sprite, Texture texture, int x, int y, int z) {
        SpriteShape shape = shapeOf(object, sprite, texture);
        if (shape.kind == SpriteShape.CARD) {
            return false;
        }
        if (shape.kind == SpriteShape.BEAM) {
            PropertyContainer properties = sprite.getProperties();
            if (properties.has(IsoFlagType.solid) || properties.has(IsoFlagType.solidtrans) || properties.has(IsoFlagType.bed)
                    || Surfaces.table(properties) || Surfaces.surface(properties) > 0) {
                return false;
            }
            build.geometry.addBeam(mesh, build.frame, x, y, z, shape);
            build.boxes++;
            return true;
        }
        if (!shape.whole) {
            shape = besideWhole(shape, x, y, z);
            if (shape == null) {
                return false;
            }
        }
        build.geometry.addPanel(mesh, build.frame, x, y, z, shape);
        return true;
    }

    /**
     * A length of panel too short to say where on its square it stands, put in the plane of
     * the whole panel on the next square along its run: the end sheet of a billboard. Null
     * when neither of those squares has one.
     */
    private SpriteShape besideWhole(SpriteShape part, int x, int y, int z) {
        for (int step = -1; step <= 1; step += 2) {
            IsoGridSquare beside = build.cell.getGridSquare(part.alongX ? x + step : x, part.alongX ? y : y + step, z);
            if (beside == null) {
                continue;
            }
            PZArrayList<IsoObject> objects = beside.getObjects();
            for (int i = 0; i < objects.size(); i++) {
                IsoObject object = objects.get(i);
                IsoSprite sprite = object.getSprite();
                if (sprite == null) {
                    continue;
                }
                Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
                if (!WorldScene.usable(texture)) {
                    continue;
                }
                SpriteShape whole = shapeOf(object, sprite, texture);
                if (whole.kind == SpriteShape.PANEL && whole.whole && whole.alongX == part.alongX) {
                    return part.at(part.alongX ? whole.b0 : whole.a0);
                }
            }
        }
        return null;
    }
}
