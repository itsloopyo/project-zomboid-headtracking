package com.cameraunlock.zomboid;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.textures.Texture;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.objects.IsoBarbecue;
import zombie.iso.objects.IsoFireplace;
import zombie.iso.objects.IsoTelevision;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * The furniture of a chunk being built and what hangs on its walls, as models of the mod's own. One to a frame of the
 * world's picture, and it writes into that frame's meshes and its placed models.
 */
final class FurnishingBuilder {
    /** Where the sprite gives a piece's colour when it has none at the first place, as shares of that place's height. */
    private static final float[] DYE_TRIES = {1f, 0.5f, 0.75f};
    /** The frames a picture can hang in, from the tallest for its width to the widest. */
    private static final String[] FRAMES = {"frame_tall", "frame_square", "frame_wide"};
    /** The least and the most a clock is made of its model's size. */
    private static final float CLOCK_SMALLEST = 0.8f, CLOCK_LARGEST = 1.6f;
    /** How near the end of its square the sprite of one length of a long picture stops for the length to be stood to the end. */
    private static final float LENGTHS_MEET = 0.1f;
    /** The object on a square that shows the sprite, or null. */
    private static IsoObject holding(IsoGridSquare square, IsoSprite sprite) {
        if (square == null || sprite == null) {
            return null;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            if (objects.get(i).getSprite() == sprite) {
                return objects.get(i);
            }
        }
        return null;
    }

    /** The object on a square whose sprite is called name, or null. */
    private static IsoObject named(IsoGridSquare square, String name) {
        if (square == null) {
            return null;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite != null && name.equals(sprite.getName())) {
                return objects.get(i);
            }
        }
        return null;
    }

    /**
     * Whether the other squares a piece on (x, y, z) is painted on hold their sprites: its
     * partner's, and the other two of its row. Null where none is missing but one is on a square
     * the map has not loaded.
     */
    static Boolean othersThere(Furniture piece, Towers.Squares squares, int x, int y, int z) {
        Boolean all = Boolean.TRUE;
        if (piece.partner != null) {
            all = both(all, squares.holds(x + piece.partnerX, y + piece.partnerY, z, piece.partner));
        }
        if (piece.row != null) {
            for (int i = 0; i < piece.row.length; i++) {
                all = both(all, squares.holds(x + piece.rowX[i], y + piece.rowY[i], z, piece.row[i]));
            }
        }
        return all;
    }

    private static Boolean both(Boolean one, Boolean other) {
        return one == Boolean.FALSE || other == Boolean.FALSE ? Boolean.FALSE : one == null || other == null ? null : Boolean.TRUE;
    }

    private final WorldScene.Frame build;
    /** The squares of the map the build is of, by the sprites they hold. */
    private final Towers.Squares squares;

    /**
     * Where the piece last found ready takes its colours, the surfaces that take the second, and the texture of the
     * sprite that gives them, PLAIN for a piece that takes none.
     */
    float dyeU, dyeV, alsoU, alsoV;
    int alsoDyed;
    private int dyeTexture;
    /** The sprites whose second colour was not found painted, each written to the log once. */
    private static final Set<String> UNDYED = ConcurrentHashMap.newKeySet();
    /** The sprites drawn as they are because nothing was found painted where their colour is taken, each written to the log once. */
    private static final Set<String> UNPAINTED = ConcurrentHashMap.newKeySet();
    private final SpriteGeometry.Frame partFrame = new SpriteGeometry.Frame();
    private final float[] partAt = new float[4], wholeAt = new float[4];
    private final float[] screenAt = new float[2];
    private final float[] roofCorners = new float[4], onRoof = new float[3];
    /** What placeHung leaves: where along its wall a hung piece's middle is, how high its foot is over the floor in tiles, and its scales. */
    static final int ALONG = 0, FOOT = 1, SCALE = 2, SCALE_UP = 3;
    private final float[] hungPlace = new float[4];

    FurnishingBuilder(WorldScene.Frame build) {
        this.build = build;
        squares = (x, y, z, sprite) -> Towers.holds(build.cell, x, y, z, sprite);
    }

    /**
     * Hangs the mod's model of a picture, a mirror or a clock on the wall the sprite was
     * painted for, where on that wall the sprite paints it and as big, and says whether the
     * sprite is thereby dealt with. A picture's frame holds the mod's own picture for the
     * sprite where it has one, and failing that what the sprite shows.
     *
     * False, which leaves the sprite to be drawn as it is, for a sprite that is no such thing
     * and for one not painted where something on that wall would be.
     */
    boolean addHung(IsoObject object, IsoSprite sprite, Texture texture, int x, int y, int z) {
        Furniture piece = Furniture.known(sprite);
        // The mod's picture for the sprite in the room it hangs in, where its pictures are on. A poster hangs as the
        // mod's sheet only to show one, and a frame, a clock or a lamp only where the mod's
        // furniture is on or it has a picture to show.
        int picture = piece != null && Own.pictures(build.playerIndex) ? Decor.inRoom(piece.picture, x, y, z) : Decor.NONE;
        if (picture != Decor.NONE && !Decor.ready(picture)) {
            // A picture not read yet would be drawn as nothing: the sprite paints the frame until
            // a build made again once it is read.
            Decor.held(picture);
            build.building.signed = false;
            picture = Decor.NONE;
        }
        if (piece == null || piece.wall == WallHung.NONE || picture == Decor.NONE && (piece.pinned || !Own.furniture())
                || !WallHung.painted(build.frame, piece.wall, build.hungAt) && !(piece.ownSize && WallHung.paintedProud(build.hungAt, piece.model.depth()))) {
            return false;
        }
        if (picture != Decor.NONE && piece.grid != null) {
            // The sprite of one square's length of a picture stops a pixel short of the square's
            // end, and the wall shows through between two lengths. Each is stood to the end
            // of its square where another length follows it.
            if (piece.lengthAt() > 0 && build.hungAt[WallHung.FROM] < LENGTHS_MEET) {
                build.hungAt[WallHung.FROM] = 0f;
            }
            if (piece.lengthAt() < piece.lengths() - 1 && build.hungAt[WallHung.TO] > 1f - LENGTHS_MEET) {
                build.hungAt[WallHung.TO] = 1f;
            }
            // And every length is as high as the whole picture: the sprites of two lengths
            // differ by a pixel at the foot, which is a step in the picture's lower edge.
            if (!wholeOf(object, piece) && piece.lettered) {
                return false;
            }
            build.hungAt[WallHung.BOTTOM] = wholeAt[WallHung.BOTTOM];
            build.hungAt[WallHung.TOP] = wholeAt[WallHung.TOP];
        }
        Models.Model model = placeHung(piece, build.hungAt, build.geometry.raised, hungPlace);
        float along = hungPlace[ALONG], foot = hungPlace[FOOT], scale = hungPlace[SCALE], scaleUp = hungPlace[SCALE_UP];
        // Its back clear of the wall's skin, which stands that far inside the square's edge.
        Models.turn(piece.turns, 0f, -1f, build.hungWay);
        float out = WorldScene.WALL_HALF + WorldScene.HUNG_CLEAR + model.depth() * scale / 2f;
        float a = WallHung.a(piece.wall, along), b = WallHung.b(piece.wall, along);
        float[] lift = build.geometry.lift;
        // On a wall on the square's north or west edge it hangs as high as the wall stands (Frame.standOnKerbTop).
        float rise = piece.wall == WallHung.NORTH || piece.wall == WallHung.WEST ? build.kerbTopRise(z, piece.wall == WallHung.NORTH) : 0f;
        build.building.plain = false;
        build.placed.add(model, build.frame, x, y, x + a + build.hungWay[0] * out, y + b + build.hungWay[1] * out,
                z * ViewCamera.LEVEL_HEIGHT + (lift[0] + lift[1] + lift[2] + lift[3]) / 4f + rise + foot, piece.turns, scale, scaleUp,
                !piece.pictured ? WorldScene.PLAIN : picture != Decor.NONE ? Decor.held(picture) : texture.getID(),
                piece.pictured ? 1 << Models.PANE : 0, 0f, 0f, 0, 0f, 0f);
        if (piece.mirror) {
            build.placed.glass(model);
        }
        if (picture != Decor.NONE && piece.grid == null) {
            float[] pane = Decor.pane(model);
            Decor.lay(pane[0], pane[1], pane[2], pane[3], (pane[1] - pane[0]) * scale, (pane[3] - pane[2]) * scaleUp, 0f,
                    Decor.wideForHigh(picture), build.hungShows);
            build.placed.picture(build.hungShows);
        } else if (picture != Decor.NONE) {
            // One square's length of a picture longer than a square shows its own part of it.
            // Whoever faces a north wall has the map's x growing to their right, and whoever
            // faces a west one has its y growing to their left.
            boolean north = piece.wall == WallHung.NORTH;
            float bottom = build.hungAt[WallHung.BOTTOM];
            Decor.lay((north ? wholeAt[WallHung.FROM] - along : along - wholeAt[WallHung.TO]) / scale,
                    (north ? wholeAt[WallHung.TO] - along : along - wholeAt[WallHung.FROM]) / scale,
                    (wholeAt[WallHung.BOTTOM] - bottom) * ViewCamera.LEVEL_HEIGHT / scaleUp, (wholeAt[WallHung.TOP] - bottom) * ViewCamera.LEVEL_HEIGHT / scaleUp,
                    wholeAt[WallHung.TO] - wholeAt[WallHung.FROM], (wholeAt[WallHung.TOP] - wholeAt[WallHung.BOTTOM]) * ViewCamera.LEVEL_HEIGHT,
                    piece.rail, Decor.wideForHigh(picture), build.hungShows);
            build.placed.picture(build.hungShows);
        } else if (piece.pictured) {
            Models.turn(piece.turns, 1f, 0f, build.hungWay);
            WallHung.shows(build.frame, a, b, build.hungAt[WallHung.BOTTOM], build.hungWay[0] * scale, build.hungWay[1] * scale, scaleUp / ViewCamera.LEVEL_HEIGHT, build.hungShows);
            build.placed.picture(build.hungShows);
        }
        if (SightTrace.ON && picture != Decor.NONE && build.building.pictured != null) {
            build.building.pictured.add(object);
        }
        build.furnished++;
        return true;
    }

    /**
     * Where on its wall a hung piece stands and how big, from where its sprite paints it (hungAt, as WallHung.painted
     * leaves it) and how many levels the game raises the object by: into place, as ALONG to SCALE_UP. The model it is.
     */
    static Models.Model placeHung(Furniture piece, float[] hungAt, float raisedLevels, float[] place) {
        float wide = hungAt[WallHung.TO] - hungAt[WallHung.FROM];
        float high = (hungAt[WallHung.TOP] - hungAt[WallHung.BOTTOM]) * ViewCamera.LEVEL_HEIGHT;
        float along = (hungAt[WallHung.FROM] + hungAt[WallHung.TO]) / 2f;
        Models.Model model = piece.model != null ? piece.model : Models.get(FRAMES[WallHung.frameFor(wide / high)]);
        float scale = wide / model.width(), scaleUp = high / model.height(), foot = hungAt[WallHung.BOTTOM] * ViewCamera.LEVEL_HEIGHT;
        if (piece.model != null && !piece.model.name.equals("canvas")) {
            // A clock is round whatever its sprite's outline comes to, and hangs about the middle of it.
            scale = scaleUp = piece.ownSize ? Furniture.TILES_PER_METRE : Math.clamp(Math.min(scale, scaleUp), CLOCK_SMALLEST, CLOCK_LARGEST);
            foot += (high - model.height() * scale) / 2f;
        }
        if (piece.ownSize) {
            // A lamp stands out into its square, and is painted to one side of its fixing for it.
            float proud = model.depth() * scale / 2f;
            along = Math.clamp(along + WallHung.alongProud(piece.wall, proud), 0f, 1f);
            foot += WallHung.upProud(piece.wall, proud) * ViewCamera.LEVEL_HEIGHT;
        }
        if (piece.raised > 0f) {
            // What the game paints bigger and higher than it is hangs at its own height, over whatever the game raises it by.
            foot = piece.raised + raisedLevels * ViewCamera.LEVEL_HEIGHT;
        }
        place[ALONG] = along;
        place[FOOT] = foot;
        place[SCALE] = scale;
        place[SCALE_UP] = scaleUp;
        return model;
    }

    /**
     * Finds the whole of a picture that hangs along several squares of one wall, from the
     * sprites of all its squares: into wholeAt as WallHung.painted leaves one square's, with
     * the places along the wall counted from the corner of the square the object is on. Each
     * square's sprite is taken to stand on its square as the object's does on this one, and
     * the lengths to follow each other along the wall the way the map's x or y grows. False
     * where a square's sprite is not painted as something on that wall: the whole is then
     * of the other squares alone.
     */
    private boolean wholeOf(IsoObject object, Furniture piece) {
        wholeAt[WallHung.FROM] = wholeAt[WallHung.BOTTOM] = Float.MAX_VALUE;
        wholeAt[WallHung.TO] = wholeAt[WallHung.TOP] = -Float.MAX_VALUE;
        partFrame.scale = build.frame.scale;
        boolean every = true;
        for (int k = 0; k < piece.lengths(); k++) {
            IsoSprite part = piece.width > 1 ? piece.grid.getSprite(k, piece.gridY) : piece.grid.getSprite(piece.gridX, k);
            Texture painting = part == null ? null : part.getTextureForCurrentFrame(object.getDir());
            if (!WorldScene.usable(painting)) {
                every = false;
                continue;
            }
            WorldScene.frameOf(partFrame, object, painting);
            if (!WallHung.painted(partFrame, piece.wall, partAt)) {
                every = false;
                continue;
            }
            int from = k - piece.lengthAt();
            wholeAt[WallHung.FROM] = Math.min(wholeAt[WallHung.FROM], from + partAt[WallHung.FROM]);
            wholeAt[WallHung.TO] = Math.max(wholeAt[WallHung.TO], from + partAt[WallHung.TO]);
            wholeAt[WallHung.BOTTOM] = Math.min(wholeAt[WallHung.BOTTOM], partAt[WallHung.BOTTOM]);
            wholeAt[WallHung.TOP] = Math.max(wholeAt[WallHung.TOP], partAt[WallHung.TOP]);
        }
        return every;
    }

    /** How far from the square ready was asked about the first square of its piece is, along x and y. */
    private final int[] readyOrigin = new int[2];

    /** What the squares round one hold: the object of that sprite on the square dx, dy from it, or null. */
    interface Around<T> {
        T holding(int dx, int dy, IsoSprite sprite);
    }

    /**
     * Whether every square of the piece holds its part, found from what the squares round the one asked about hold:
     * the part that gives the piece its colour (self for a piece on one square), or null. Leaves in origin how far from
     * the square asked about the piece's first square is.
     *
     * A run one square deep and three or more long is laid by the map's makers with any of its middle sprites on its
     * middle squares, which the game paints alike: a pharmacy in Rosewood has a shop's shelving of four laid first,
     * third, third, last. So on such a run a middle square is as far along as the run's first square is behind it, past
     * middle squares only, and holds its part if it holds any middle sprite; an end is where its sprite says.
     */
    static <T> T whole(Furniture piece, T self, Around<T> around, int[] origin) {
        origin[0] = -piece.gridX;
        origin[1] = -piece.gridY;
        if (piece.grid == null) {
            return self;
        }
        boolean alongX = piece.width > 1;
        int length = Math.max(piece.width, piece.height), stepX = alongX ? 1 : 0, stepY = 1 - stepX;
        boolean run = Math.min(piece.width, piece.height) == 1 && length >= 3;
        if (run && middle(piece, alongX ? piece.gridX : piece.gridY)) {
            IsoSprite first = piece.grid.getSprite(0, 0);
            for (int back = 1; back < length - 1; back++) {
                if (around.holding(-back * stepX, -back * stepY, first) != null) {
                    origin[0] = -back * stepX;
                    origin[1] = -back * stepY;
                    break;
                }
                if (holdingMiddle(around, -back * stepX, -back * stepY, piece) == null) {
                    break;
                }
            }
        }
        T dyer = self;
        for (int i = 0; i < piece.width; i++) {
            for (int j = 0; j < piece.height; j++) {
                int dx = origin[0] + i, dy = origin[1] + j;
                T part = run && middle(piece, alongX ? i : j) ? holdingMiddle(around, dx, dy, piece) : around.holding(dx, dy, piece.grid.getSprite(i, j));
                if (part == null) {
                    return null;
                }
                if (i == piece.dyeX && j == piece.dyeY) {
                    dyer = part;
                }
            }
        }
        return dyer;
    }

    private static boolean middle(Furniture piece, int at) {
        return at > 0 && at < Math.max(piece.width, piece.height) - 1;
    }

    /** What the square dx, dy from the one asked about holds of any middle square of the run the piece is, or null. */
    private static <T> T holdingMiddle(Around<T> around, int dx, int dy, Furniture piece) {
        for (int k = 1; k < Math.max(piece.width, piece.height) - 1; k++) {
            T part = around.holding(dx, dy, piece.width > 1 ? piece.grid.getSprite(k, 0) : piece.grid.getSprite(0, k));
            if (part != null) {
                return part;
            }
        }
        return null;
    }

    /**
     * Whether the piece the object is a square of can stand as its model: every square of it is
     * there, and the sprite that is to give it its colour is painted where the colour is
     * taken. Leaves where the colours are taken in dyeU to alsoV, alsoDyed and dyeTexture.
     */
    private boolean ready(IsoObject object, Furniture piece, int x, int y, int z) {
        // A square of a row takes its colour from the one of the row that gives it, as the others do.
        IsoObject self = piece.rowDyer < 0 ? object : named(build.cell.getGridSquare(x + piece.rowX[piece.rowDyer], y + piece.rowY[piece.rowDyer], z), piece.row[piece.rowDyer]);
        IsoObject dyer = whole(piece, self, (dx, dy, sprite) -> holding(build.cell.getGridSquare(x + dx, y + dy, z), sprite), readyOrigin);
        if (dyer == null) {
            return false;
        }
        dyeTexture = WorldScene.PLAIN;
        alsoDyed = piece.alsoDyed;
        if (piece.dyed == 0) {
            return true;
        }
        Texture dye = dyer.getSprite().getTextureForCurrentFrame(dyer.getDir());
        if (!WorldScene.usable(dye)) {
            return false;
        }
        build.dyeFrame.scale = build.frame.scale;
        WorldScene.frameOf(build.dyeFrame, dyer, dye);
        dyeTexture = dye.getID();
        return dyes(dye::isMaskSet, build.dyeFrame, piece, dyer.getSprite().getName());
    }

    /**
     * Where a piece takes its colours on the sprite that gives them, laid in frame and painted where painted says, into
     * dyeU to alsoV and alsoDyed. Whether the piece can stand: its first colour's place is painted. Where the place of
     * its second is not, the surfaces that would take it keep their own colour, alsoDyed none of them, and that is
     * written once for the sprite.
     */
    boolean dyes(SpriteShape.Painted painted, SpriteGeometry.Frame frame, Furniture piece, String sprite) {
        for (float share : DYE_TRIES) {
            float high = piece.dyeHigh * share;
            if (painted.at(Math.round(SpriteGeometry.frameU(frame, piece.dyeA, piece.dyeB)), Math.round(SpriteGeometry.frameV(frame, piece.dyeA, piece.dyeB, high)))) {
                dyeU = SpriteGeometry.textureU(frame, piece.dyeA, piece.dyeB);
                dyeV = SpriteGeometry.textureV(frame, piece.dyeA, piece.dyeB, high);
                alsoDyed = piece.alsoDyed;
                if (alsoDyed == 0) {
                    return true;
                }
                float alsoA = Float.isNaN(piece.alsoA) ? piece.dyeA : piece.alsoA, alsoB = Float.isNaN(piece.alsoB) ? piece.dyeB : piece.alsoB;
                alsoU = SpriteGeometry.textureU(frame, alsoA, alsoB);
                alsoV = SpriteGeometry.textureV(frame, alsoA, alsoB, piece.alsoHigh);
                if (!painted.at(Math.round(SpriteGeometry.frameU(frame, alsoA, alsoB)), Math.round(SpriteGeometry.frameV(frame, alsoA, alsoB, piece.alsoHigh)))) {
                    alsoDyed = 0;
                    if (UNDYED.add(sprite)) {
                        if (DevBuild.ON) {
                            Log.info("furniture " + sprite + ": nothing painted where its second colour is taken, at (" + alsoA + ", " + alsoB + ", "
                                    + piece.alsoHigh + "): those surfaces keep the model's own colour");
                        }
                    }
                }
                return true;
            }
        }
        if (UNPAINTED.add(sprite)) {
            if (DevBuild.ON) {
                Log.info("furniture " + sprite + ": nothing painted where its colour is taken, at (" + piece.dyeA + ", " + piece.dyeB + ", "
                        + piece.dyeHigh + "): drawn as the game's sprite");
            }
        }
        return false;
    }

    /**
     * Where a piece that stands on the roof of its square stands on a roof whose heights over the floor at the square's
     * corners are corners, into out: a and b on its square and what is added to its foot, in tiles. Says which model
     * it stands as. A piece made with a post stands on its post, its foot on the roof, but where walled, the wall it
     * backs onto stands on its square: there it hangs on that wall as its sprite paints it, over a roof below the wall.
     */
    static Models.Model onRoof(Furniture piece, Models.Model model, float[] corners, boolean walled, float[] out) {
        if (piece.posted != null && walled) {
            out[0] = piece.centreX;
            out[1] = piece.centreY;
            out[2] = 0f;
            return model;
        }
        RoofFit.standOn(corners, piece.centreX, piece.centreY, out);
        out[2] *= ViewCamera.LEVEL_HEIGHT;
        if (piece.posted == null) {
            return model;
        }
        out[2] -= piece.raised;
        return piece.posted;
    }

    /**
     * How far, in levels, a steeple stands lower than the floor of its belfry's storey so its foot is on the roof a storey
     * down: the lowest that roof is under the belfry, at a and b on its square and half across, from the roof's heights over
     * that storey's floor at the square's corners (north-west, north-east, south-east, south-west). The game paints a
     * belfry on the storey over a ridge, and a ridge that stops short of that storey's floor, two thirds of a storey up on
     * the Muldraugh church at 10735, 9721, left it standing in the air. Never raised: a roof that reaches the floor is
     * met by the belfry's foot already.
     */
    static float sunkOntoRoof(float[] corners, float a, float b, float half) {
        float lowest = Float.POSITIVE_INFINITY;
        for (int corner = 0; corner < 4; corner++) {
            float u = Math.clamp(a + (corner == 1 || corner == 2 ? half : -half), 0f, 1f);
            float v = Math.clamp(b + (corner >= 2 ? half : -half), 0f, 1f);
            lowest = Math.min(lowest, (1f - u) * (1f - v) * corners[0] + u * (1f - v) * corners[1] + u * v * corners[2] + (1f - u) * v * corners[3]);
        }
        return Math.max(0f, 1f - lowest);
    }

    /** Whether a wall, a window, a door or a railing stands on the edge of the square a piece backs onto. */
    private boolean backedByWall(Furniture piece, int x, int y, int z) {
        return switch (piece.turns & 3) {
            case 0 -> edged(build.cell.getGridSquare(x, y + 1, z), true);
            case 1 -> edged(build.cell.getGridSquare(x, y, z), false);
            case 2 -> edged(build.cell.getGridSquare(x, y, z), true);
            default -> edged(build.cell.getGridSquare(x + 1, y, z), false);
        };
    }

    /** Whether something stands on the north or the west edge of a square. */
    private static boolean edged(IsoGridSquare square, boolean north) {
        if (square == null) {
            return false;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite != null && (north ? WorldScene.onNorthEdge(sprite.getProperties()) : WorldScene.onWestEdge(sprite.getProperties()))) {
                return true;
            }
        }
        return false;
    }

    /** The object on the square that is a counter a basin can stand in, or a basin, or null. */
    private IsoObject onSquare(int x, int y, int z, boolean basin) {
        IsoGridSquare square = build.cell.getGridSquare(x, y, z);
        if (square == null) {
            return null;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            Furniture piece = sprite == null ? null : Furniture.known(sprite);
            if (piece != null && (basin ? piece.basin : piece.withBasin != null)) {
                return objects.get(i);
            }
        }
        return null;
    }

    /**
     * Stands the mod's own model of a piece of furniture where the game has the sprites of
     * one, and says whether this sprite is thereby dealt with. A piece on several squares is
     * one model, added by the first of its squares, and its other squares add nothing.
     *
     * False, which leaves the sprite to be drawn as it is, when there is no model of the
     * piece, when one of its squares is missing, and when the sprite that is to give it its
     * colour has nothing painted where the colour is taken. Every square of a piece reaches
     * the same answer, so none of it is drawn twice or left out.
     */
    boolean addFurniture(IsoObject object, IsoSprite sprite, int x, int y, int z) {
        Furniture piece = Own.furniture() ? Furniture.known(sprite) : null;
        // What hangs on a wall is addHung's: one it could not hang is drawn as it was.
        if (piece == null || piece.wall != WallHung.NONE) {
            return false;
        }
        // A counter's top that is no overlay has no counter under it to stand over, and is drawn as the game's sprite.
        if (piece.countertop) {
            return false;
        }
        if (piece.basin) {
            // Dealt with when the counter under it is: the counter's model has the basin in it.
            IsoObject counter = onSquare(x, y, z, false);
            return counter != null && ready(counter, Furniture.known(counter.getSprite()), x, y, z);
        }
        if (piece.partner != null || piece.row != null) {
            Boolean others = othersThere(piece, squares, x, y, z);
            if (others == null) {
                // Made again when the map has loaded the square that decides it.
                build.wantsMap = true;
            }
            if (others != Boolean.TRUE) {
                return false;
            }
        }
        if (piece.follows) {
            // Dealt with by another sprite of the piece, which stands it: a square of a row only where that one's colour
            // is painted, as that one asks.
            return piece.row == null || ready(object, piece, x, y, z);
        }
        if (!ready(object, piece, x, y, z)) {
            return false;
        }
        float[] lift = build.geometry.lift;
        // The game draws a thing that stands on a table higher than its sprite has it where the
        // table is higher than the one the sprite was painted on: a set on a chest of drawers.
        float foot = z * ViewCamera.LEVEL_HEIGHT + (lift[0] + lift[1] + lift[2] + lift[3]) / 4f + piece.raised + build.geometry.raised * ViewCamera.LEVEL_HEIGHT;
        // A case's cakes are the game's painting of them over each of its squares, made.
        if (piece.displayCase && build.overLayer == 0) {
            build.building.plain = false;
            DisplayCases.addStock(build, object, piece, x, y, foot);
        }
        // What the game paints a shop's fitting holding, over each square of it, made.
        if (piece.stocked && build.overLayer == 0) {
            build.building.plain = false;
            ShopFittings.addStock(build, object, piece, x, y, foot);
        }
        IsoSprite first = piece.grid == null ? null : piece.grid.getSprite(0, 0);
        boolean firstPaints = first == null || WorldScene.usable(first.getTextureForCurrentFrame(object.getDir()));
        // What the game lays over the piece's sprite, a stain, has no place on the model.
        if (!stands(piece, firstPaints, readyOrigin) || build.overLayer > 0) {
            return true;
        }
        x += readyOrigin[0];
        y += readyOrigin[1];
        Models.Model model = piece.withBasin != null && onSquare(x, y, z, true) != null ? piece.withBasin : piece.model;
        boolean burning = object instanceof IsoFireplace hearth && hearth.isLit();
        if (burning && model.name.equals("barrel")) {
            // A drum lit as a burn barrel has had its top cut out: the fire is drawn burning in it.
            model = Models.get("burn_barrel");
        }
        build.building.plain = false;
        float centreX = piece.centreX, centreY = piece.centreY;
        if (piece.onRoof && build.roofBuilder.cornersAt(x, y, z, roofCorners)) {
            model = onRoof(piece, model, roofCorners, piece.posted != null && backedByWall(piece, x, y, z), onRoof);
            centreX = onRoof[0];
            centreY = onRoof[1];
            foot += onRoof[2];
        }
        if (piece.roofBelow > 0 && build.roofBuilder.cornersAt(x, y, z - piece.roofBelow, roofCorners)) {
            foot -= sunkOntoRoof(roofCorners, piece.centreX, piece.centreY, piece.footHalf) * ViewCamera.LEVEL_HEIGHT;
        }
        build.placed.add(model, build.frame, x, y, x + centreX, y + centreY, foot, piece.turns, piece.scale, piece.scaleUp,
                dyeTexture, piece.dyed, dyeU, dyeV, alsoDyed, alsoU, alsoV);
        if (piece.mirror) {
            build.placed.glass(model);
        }
        float[] fire = burning ? Fires.hearth(model.name) : null;
        if (fire != null) {
            build.placed.fire(fire, x + centreX, y + centreY, foot, piece.turns, piece.scale, piece.scaleUp, Fires.seed(x, y, z), FireLooks.look(object, "Fire"),
                    FireLooks.look(object, "Smoke"));
        }
        if (piece.pictured) {
            // Each point of the pane looks at where the sprite paints it: the pane's face is paneY in front of the model's origin.
            // The sprite paints it over its unmoved place, and a model stood on a roof is where the view draws that place.
            Models.turn(piece.turns, 0f, piece.paneY * piece.scale, build.hungWay);
            float a = piece.centreX + build.hungWay[0], b = piece.centreY + build.hungWay[1];
            Models.turn(piece.turns, 1f, 0f, build.hungWay);
            WallHung.shows(build.frame, a, b, (piece.raised + build.geometry.raised * ViewCamera.LEVEL_HEIGHT) / ViewCamera.LEVEL_HEIGHT,
                    build.hungWay[0] * piece.scale, build.hungWay[1] * piece.scale, piece.scaleUp / ViewCamera.LEVEL_HEIGHT, build.hungShows);
            build.placed.picture(build.hungShows);
        }
        build.furnished++;
        if (piece.displayCase) {
            DisplayCases.addGlass(build, piece, x, y, foot);
        }
        if (piece.glazed) {
            ShopFittings.addGlass(build, piece, x, y, foot);
        }
        if (piece.glassLegs != null) {
            Outdoors.addGlass(build, piece, x, y, foot);
        }
        if (piece.television && object instanceof IsoTelevision set) {
            // The game's sprite of a lit screen is painted for its own view and has no place on
            // the model: it is never drawn over one. With the mod's pictures switched off a set
            // that is on shows the bars, which is what the game's sprite is a picture of.
            int showing = Screens.showing(set);
            if (showing != Screens.DARK) {
                addLitScreen(piece, model, Screens.held(set, Own.pictures(build.playerIndex) ? showing : Screens.BARS, piece.blackAndWhite), piece.blackAndWhite, x, y, foot);
            }
        }
        return true;
    }

    /**
     * Whether the square of a piece that origin leads back from stands its model: the first square, or where the first
     * square's sprite paints nothing, as a big tent's does, the square that gives the piece its colour. The world
     * passes over a square with nothing to draw, and the piece would stand nowhere.
     */
    static boolean stands(Furniture piece, boolean firstPaints, int[] origin) {
        return firstPaints ? origin[0] == 0 && origin[1] == 0 : piece.gridX == piece.dyeX && piece.gridY == piece.dyeY;
    }

    /**
     * Notes the fire of a lit hearth or barbecue that addFurniture stood no model of, which the
     * game's sprite stands for: where the mod's model of it would hold it, as its piece places the
     * model, where there is one (furniture of the mod's own switched off), a lit drum's in the drum;
     * otherwise over the middle of its square, as its sprite shows it (Fires.unmodelled).
     */
    void addFireOverSprite(IsoObject object, IsoSprite sprite, int x, int y, int z) {
        float[] lift = build.geometry.lift;
        float foot = z * ViewCamera.LEVEL_HEIGHT + (lift[0] + lift[1] + lift[2] + lift[3]) / 4f + build.geometry.raised * ViewCamera.LEVEL_HEIGHT;
        Object flames = FireLooks.look(object, "Fire"), smoke = FireLooks.look(object, "Smoke");
        float seed = Fires.seed(x, y, z);
        if (object instanceof IsoBarbecue) {
            build.placed.fire(Fires.BARBECUE, x + 0.5f, y + 0.5f, foot, 0, 1f, 1f, seed, flames, smoke);
            return;
        }
        Furniture piece = Furniture.known(sprite);
        String model = piece == null || piece.model == null ? null : piece.model.name.equals("barrel") ? "burn_barrel" : piece.model.name;
        float[] hearth = model == null ? null : Fires.hearth(model);
        if (hearth != null) {
            build.placed.fire(hearth, x + piece.centreX, y + piece.centreY, foot + piece.raised, piece.turns, piece.scale, piece.scaleUp, seed, flames, smoke);
        } else {
            build.placed.fire(Fires.unmodelled(sprite.getName()), x + 0.5f, y + 0.5f, foot, 0, 1f, 1f, seed, flames, smoke);
        }
    }

    /**
     * Stands the board the game lays over a counter as its top, over the mod's model of that counter, coloured from the
     * board's own sprite, and says whether the board is thereby dealt with. The counter's model has a top of its own,
     * coloured from the counter's sprite, which the game's board covers: this one covers it the same way.
     *
     * False, which leaves the board to be as it was, for a sprite that is no such board, and for one over a counter
     * the mod stands no model of or whose board has nothing painted where its colour is taken.
     */
    boolean addCountertop(IsoObject object, IsoSprite counterSprite, IsoSprite board, Texture texture, int x, int y, int z) {
        Furniture top = Own.furniture() ? Furniture.known(board) : null;
        Furniture counter = top != null && top.countertop ? Furniture.known(counterSprite) : null;
        if (counter == null || counter.model == null || !ready(object, counter, x, y, z)) {
            return false;
        }
        Models.Model under = counter.withBasin != null && onSquare(x, y, z, true) != null ? counter.withBasin : counter.model;
        String worktop = HomeFittings.worktopOver(under.name);
        if (worktop == null) {
            return false;
        }
        build.dyeFrame.scale = build.frame.scale;
        WorldScene.frameOf(build.dyeFrame, object, texture);
        float high = board.getProperties().getSurface() / WorldScene.SURFACE_PIXELS_PER_LEVEL;
        if (!texture.isMaskSet(Math.round(SpriteGeometry.frameU(build.dyeFrame, 0.5f, 0.5f)), Math.round(SpriteGeometry.frameV(build.dyeFrame, 0.5f, 0.5f, high)))) {
            return false;
        }
        float[] lift = build.geometry.lift;
        build.building.plain = false;
        float foot = z * ViewCamera.LEVEL_HEIGHT + (lift[0] + lift[1] + lift[2] + lift[3]) / 4f + counter.raised + build.geometry.raised * ViewCamera.LEVEL_HEIGHT
                + (HomeFittings.WORKTOP_HIGH - HomeFittings.WORKTOP_THICK + HomeFittings.WORKTOP_PROUD) * counter.scaleUp;
        build.placed.add(Models.get(worktop), build.frame, x, y, x + counter.centreX, y + counter.centreY, foot, counter.turns, counter.scale, counter.scaleUp,
                texture.getID(), 1 << Models.WORKTOP, SpriteGeometry.textureU(build.dyeFrame, 0.5f, 0.5f), SpriteGeometry.textureV(build.dyeFrame, 0.5f, 0.5f, high),
                0, 0f, 0f);
        build.furnished++;
        return true;
    }

    /**
     * Stands a picture just proud of the glass of a set that is on, as wide and as high as the glass. held is what
     * Screens.held gives for it. Whether the tube has colour goes where a piece's second dye is looked up, which a
     * sheet has none of: a programme's picture is in colour in the jar, and the picture's program takes it out.
     */
    private void addLitScreen(Furniture piece, Models.Model set, int held, boolean inGrey, int x, int y, float foot) {
        float[] glass = Screens.glass(set);
        float wide = (glass[1] - glass[0]) * piece.scale, high = (glass[3] - glass[2]) * piece.scaleUp;
        Models.turn(piece.turns, (glass[0] + glass[1]) / 2f * piece.scale, (glass[4] - Screens.PROUD) * piece.scale, screenAt);
        build.placed.add(Models.get("lit_screen"), build.frame, x, y, x + piece.centreX + screenAt[0], y + piece.centreY + screenAt[1], foot + glass[2] * piece.scaleUp,
                piece.turns, wide, high, held, 1 << Models.SCREEN, 0f, 0f, 0, inGrey ? Screens.IN_GREY : Screens.IN_COLOUR, 0f);
        Decor.lay(-0.5f, 0.5f, 0f, 1f, wide, high, 0f, Screens.WIDE_FOR_HIGH, build.hungShows);
        build.placed.picture(build.hungShows);
    }
}
