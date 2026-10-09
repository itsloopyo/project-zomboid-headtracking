package com.cameraunlock.zomboid;

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
            WorldScene.feet.put(sprite, foot);
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
            WorldScene.posts.put(sprite, post);
        }
        return post == NO_POST ? null : (Posts) post;
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

    /** The shape of an object's sprite, read the first time the sprite is met. */
    private SpriteShape shapeOf(IsoObject object, IsoSprite sprite, Texture texture) {
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
            WorldScene.shapes.put(sprite, shape);
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
                    || properties.isTable() || properties.getSurface() > 0) {
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
