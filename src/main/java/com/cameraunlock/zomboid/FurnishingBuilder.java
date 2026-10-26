package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.textures.Texture;
import zombie.iso.IsoDirections;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.objects.IsoBarbecue;
import zombie.iso.objects.IsoFireplace;
import zombie.iso.objects.IsoTelevision;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteManager;
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
    /** How much prouder of its wall each sheet of a collage stands than the one under it, in tiles: a sheet pinned over another lies on it. */
    private static final float SHEET_PROUD = 0.002f;
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

    /** Whether the square at (x, y, z) holds any of the sprites called names: null for a square not loaded, and the build then made again once it is. */
    private Boolean holdsAny(int x, int y, int z, String[] names) {
        Boolean any = Boolean.FALSE;
        for (String name : names) {
            Boolean holds = squares.holds(x, y, z, name);
            if (holds == Boolean.TRUE) {
                return Boolean.TRUE;
            }
            if (holds == null) {
                any = null;
            }
        }
        if (any == null) {
            build.wantsMap = true;
        }
        return any;
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
    /** The piece as stood by the sprite that gave those colours: the piece itself, or the one its paintedBy names. */
    private Furniture painter;
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
    /** The way a piece faces, as clearOfLogs leaves it. */
    private final float[] front = new float[2];
    /** How far the back of a piece is from a log wall's logs behind it, in tiles. */
    private static final float LOGS_CLEAR = 0.02f;

    FurnishingBuilder(WorldScene.Frame build) {
        this.build = build;
        squares = (x, y, z, sprite) -> Towers.holds(build.cell, x, y, z, sprite);
    }

    /**
     * A length of a church's drape: see WallDrapes. The length at its foot hangs the drape up
     * through the plain length over it, which adds nothing, and a banner over it is its sprite
     * stood in front of the folds. A plain length with no foot under it hangs its own level of
     * drape, and a banner with none is left as painted. False, leaving the sprite laid as it is,
     * for one on no north or west wall and while the colour the drape takes is not read yet.
     */
    private boolean addDrape(int kind, IsoSprite sprite, Texture texture, int x, int y, int z) {
        int wall = WallHung.wallOf(sprite);
        if (wall != WallHung.NORTH && wall != WallHung.WEST) {
            return false;
        }
        if (kind == WallDrapes.FOOT) {
            return hangDrape(texture, x, y, z, wall, true, footAt(x, y, z + 1, wall, WallDrapes.HEAD) != null || footAt(x, y, z + 1, wall, WallDrapes.BANNER) != null);
        }
        Texture foot = footAt(x, y, z - 1, wall, WallDrapes.FOOT);
        if (foot == null) {
            return kind == WallDrapes.HEAD && hangDrape(texture, x, y, z, wall, false, false);
        }
        if (SpriteColours.of(foot) == null) {
            if (SpriteColours.unread(foot)) {
                build.building.signed = false;
            }
            return false;
        }
        if (kind == WallDrapes.BANNER) {
            boolean north = wall == WallHung.NORTH;
            float out = WorldScene.WALL_HALF + WallDrapes.CLEAR + WallDrapes.BANNER_PROUD;
            build.geometry.shownA = north ? 0f : out;
            build.geometry.shownB = north ? out : 0f;
            build.geometry.addFace(WorldScene.solid.get(texture.getID()), build.frame, x, y, z, 0f, 0f, 0f, north ? 1f : 0f, north ? 0f : 1f, 0f, 0f, 0f, 1f);
            build.geometry.shownA = build.geometry.shownB = 0f;
        }
        return true;
    }

    /** The sprite of the kind asked for on the square's north or west wall, or null. */
    private Texture footAt(int x, int y, int z, int wall, int kind) {
        IsoGridSquare square = build.cell.getGridSquare(x, y, z);
        if (square == null) {
            return null;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            IsoSprite sprite = object.getSprite();
            if (sprite != null && WallDrapes.kind(sprite.getName()) == kind && WallHung.wallOf(sprite) == wall) {
                Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
                return WorldScene.usable(texture) ? texture : null;
            }
        }
        return null;
    }

    private final float[] drapeAt = new float[4], drapeCorners = new float[12];

    /**
     * Hangs a square's length of drape on its wall, in the colour the sprite is mostly painted
     * in, from the foot of what the sprite paints to its top, and a level higher with over.
     * With fringe its foot is the gold fringe. Each vertex takes the light the square's corners
     * have where along the wall it is, as the next square's length does at the same place.
     */
    private boolean hangDrape(Texture texture, int x, int y, int z, int wall, boolean fringe, boolean over) {
        float[] colour = SpriteColours.of(texture);
        if (colour == null) {
            if (SpriteColours.unread(texture)) {
                build.building.signed = false;
            }
            return false;
        }
        if (!WallHung.painted(build.frame, wall, drapeAt)) {
            return false;
        }
        boolean north = wall == WallHung.NORTH;
        float level = ViewCamera.LEVEL_HEIGHT;
        float[] lift = build.geometry.lift;
        float floor = z * level + (lift[0] + lift[1] + lift[2] + lift[3]) / 4f;
        float foot = floor + Math.max(0f, drapeAt[WallHung.BOTTOM]) * level, head = floor + (drapeAt[WallHung.TOP] + (over ? 1f : 0f)) * level;
        float hem = fringe ? foot + WallDrapes.FRINGE * (drapeAt[WallHung.TOP] - Math.max(0f, drapeAt[WallHung.BOTTOM])) * level : foot;
        float red = colour[0], green = colour[1], blue = colour[2];
        SpriteGeometry.Mesh cloth = WorldScene.solid.get(WorldScene.PLAIN, WallSides.key(red, green, blue, WallSides.FLAT));
        SpriteGeometry.Mesh gold = WorldScene.solid.get(WorldScene.PLAIN,
                WallSides.key(red * WallDrapes.FRINGE_RED, red * WallDrapes.FRINGE_GREEN, red * WallDrapes.FRINGE_BLUE, WallSides.FLAT));
        long line = WallDrapes.line(north, north ? y : x, z);
        int strips = WallDrapes.FOLDS * WallDrapes.FOLD_STRIPS;
        for (SpriteGeometry.Mesh mesh : fringe ? new SpriteGeometry.Mesh[] {gold, cloth} : new SpriteGeometry.Mesh[] {cloth}) {
            float low = mesh == cloth ? hem : foot, high = mesh == cloth ? head : hem;
            // A key keeps a colour to sixteen steps a channel, which took the cloth's dark red to brown: the light
            // the cloth is drawn in carries what the key lost.
            float tintRed = mesh == cloth ? red / WallSides.kept(red) : 1f, tintGreen = mesh == cloth ? green / WallSides.kept(green) : 1f,
                    tintBlue = mesh == cloth ? blue / WallSides.kept(blue) : 1f;
            mesh.sway = 0f;
            mesh.open = build.frame.open + 32f * OpeningBuilder.DRAPE;
            for (int strip = 0; strip < strips; strip++) {
                float t0 = (float) strip / strips, t1 = (float) (strip + 1) / strips;
                drapePoint(0, north, x, y, line, t0, low, foot, head);
                drapePoint(1, north, x, y, line, t1, low, foot, head);
                drapePoint(2, north, x, y, line, t1, high, foot, head);
                drapePoint(3, north, x, y, line, t0, high, foot, head);
                float[] c = drapeCorners;
                float ux = c[3] - c[0], uy = c[4] - c[1], uz = c[5] - c[2], vx = c[9] - c[0], vy = c[10] - c[1], vz = c[11] - c[2];
                float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                // Facing the room: away from the wall.
                float turn = (north ? ny : nx) < 0f ? -1f : 1f;
                mesh.face(turn * nx, turn * ny, turn * nz);
                for (int corner : DRAPE_QUAD) {
                    float t = corner == 1 || corner == 2 ? t1 : t0;
                    float w0 = 1f - t, w1 = north ? t : 0f, w3 = north ? 0f : t;
                    SpriteGeometry.Frame frame = build.frame;
                    mesh.litAt(frame, w0, w1, 0f, w3);
                    mesh.add(c[corner * 3], c[corner * 3 + 1], c[corner * 3 + 2], 0f, 0f,
                            (frame.cornerRed[0] * w0 + frame.cornerRed[1] * w1 + frame.cornerRed[3] * w3) * tintRed,
                            (frame.cornerGreen[0] * w0 + frame.cornerGreen[1] * w1 + frame.cornerGreen[3] * w3) * tintGreen,
                            (frame.cornerBlue[0] * w0 + frame.cornerBlue[1] * w1 + frame.cornerBlue[3] * w3) * tintBlue);
                }
            }
            mesh.litAsFace();
        }
        return true;
    }

    private static final int[] DRAPE_QUAD = {0, 1, 2, 0, 2, 3};

    /** Corner i of a strip of drape: t along the square's wall, at height up in tiles, with the drape from foot to head. */
    private void drapePoint(int i, boolean north, int x, int y, long line, float t, float up, float foot, float head) {
        float out = WorldScene.WALL_HALF + WallDrapes.CLEAR + WallDrapes.out(line, north ? x : y, t, (up - foot) / (head - foot));
        WorldScene.set(drapeCorners, i, north ? x + t : x + out, north ? y + out : y + t, up);
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
        int drape = WallDrapes.kind(sprite.getName());
        if (drape != WallDrapes.NONE && Own.furniture()) {
            return addDrape(drape, sprite, texture, x, y, z);
        }
        Furniture piece = Furniture.known(sprite);
        if (piece != null && piece.collage != null) {
            return addCollage(object, piece, x, y, z);
        }
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
                || !WallHung.painted(build.frame, piece.wall, build.hungAt) && !(piece.ownSize && WallHung.paintedProud(build.hungAt, piece.model.depth()))
                && !(piece.overEnds && WallHung.paintedAlong(build.hungAt))) {
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
        float along = hungPlace[ALONG], scale = hungPlace[SCALE], scaleUp = hungPlace[SCALE_UP];
        float a = WallHung.a(piece.wall, along), b = WallHung.b(piece.wall, along);
        // Clear of a moulding along the wall behind it.
        float foot = hungPlace[FOOT];
        float clear = behind(piece, build.hungAt[WallHung.FROM], build.hungAt[WallHung.TO], foot / ViewCamera.LEVEL_HEIGHT,
                (foot + model.height() * scaleUp) / ViewCamera.LEVEL_HEIGHT);
        standHung(piece, model, x, y, z, clear, !piece.pictured ? WorldScene.PLAIN : picture != Decor.NONE ? Decor.held(picture) : texture.getID(),
                piece.pictured ? 1 << Models.PANE : 0);
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

    /** A frame hung on a square's south wall facing north, and on its east wall facing west, for HiddenWalls.Drawn. */
    private static final Furniture DRAWN_SOUTH = Furniture.single(null, null, Furniture.turnsOf(IsoDirections.N)).hang(WallHung.SOUTH, true),
            DRAWN_EAST = Furniture.single(null, null, Furniture.turnsOf(IsoDirections.W)).hang(WallHung.EAST, true);

    private final ArrayList<IsoSprite> drawnSprites = new ArrayList<>();

    /** Hangs the pictures HiddenWalls draws on the square's south and east walls, where the player's pictures are on. */
    void addDrawn(int x, int y, int z) {
        if (!Own.pictures(build.playerIndex)) {
            return;
        }
        for (int wall = HiddenWalls.SOUTH; wall <= HiddenWalls.EAST; wall++) {
            HiddenWalls.Drawn drawn = HiddenWalls.drawnAt(build.cell, x, y, z, wall, drawnSprites);
            if (drawn == null) {
                continue;
            }
            int picture = drawn.picture();
            if (!Decor.ready(picture)) {
                // Drawn before it is read it would be an empty frame: the build is made again once it is.
                Decor.held(picture);
                build.building.signed = false;
                continue;
            }
            Furniture piece = wall == HiddenWalls.SOUTH ? DRAWN_SOUTH : DRAWN_EAST;
            build.hungAt[WallHung.FROM] = drawn.from();
            build.hungAt[WallHung.TO] = drawn.to();
            build.hungAt[WallHung.BOTTOM] = drawn.bottom();
            build.hungAt[WallHung.TOP] = drawn.top();
            Models.Model model = placeHung(piece, build.hungAt, 0f, hungPlace);
            float scale = hungPlace[SCALE], scaleUp = hungPlace[SCALE_UP];
            standHung(piece, model, x, y, z, 0f, Decor.held(picture), 1 << Models.PANE);
            float[] pane = Decor.pane(model);
            Decor.lay(pane[0], pane[1], pane[2], pane[3], (pane[1] - pane[0]) * scale, (pane[3] - pane[2]) * scaleUp, 0f,
                    Decor.wideForHigh(picture), build.hungShows);
            build.placed.picture(build.hungShows);
            build.furnished++;
        }
    }

    /**
     * How far out a moulding along the piece's wall stands behind the stretch of it from to to along the wall and
     * bottom to top in levels, which what hangs there stands clear of.
     */
    private float behind(Furniture piece, float from, float to, float bottom, float top) {
        return piece.wall == WallHung.NORTH || piece.wall == WallHung.WEST ? build.trimClear(piece.wall == WallHung.NORTH, from, to, bottom, top) : 0f;
    }

    /**
     * Stands a model placeHung has just placed, with hungPlace as it left it, on its wall: its back clear of the wall's
     * skin by standOut, and its dye and dyed surfaces as Models.Instances.add takes them.
     */
    private void standHung(Furniture piece, Models.Model model, int x, int y, int z, float standOut, int dye, int dyedSurfaces) {
        float along = hungPlace[ALONG], foot = hungPlace[FOOT], scale = hungPlace[SCALE], scaleUp = hungPlace[SCALE_UP];
        // Its back clear of the wall's skin, which stands that far inside the square's edge.
        Models.turn(piece.turns, 0f, -1f, build.hungWay);
        float logs = switch (piece.wall) {
            case WallHung.NORTH -> build.wallBuilder.logsProud(x, y, z, true);
            case WallHung.WEST -> build.wallBuilder.logsProud(x, y, z, false);
            case WallHung.SOUTH -> build.wallBuilder.logsProud(x, y + 1, z, true);
            default -> build.wallBuilder.logsProud(x + 1, y, z, false);
        };
        float out = WorldScene.WALL_HALF + logs + WorldScene.HUNG_CLEAR + standOut + model.depth() * scale / 2f;
        float a = WallHung.a(piece.wall, along), b = WallHung.b(piece.wall, along);
        float[] lift = build.geometry.lift;
        // On a wall on the square's north or west edge it hangs as high as the wall stands (Frame.standOnKerbTop).
        float rise = piece.wall == WallHung.NORTH || piece.wall == WallHung.WEST ? build.kerbTopRise(z, piece.wall == WallHung.NORTH) : 0f;
        build.building.plain = false;
        build.placed.add(model, build.frame, x, y, x + a + build.hungWay[0] * out, y + b + build.hungWay[1] * out,
                z * ViewCamera.LEVEL_HEIGHT + (lift[0] + lift[1] + lift[2] + lift[3]) / 4f + rise + foot, piece.turns, scale, scaleUp,
                dye, dyedSurfaces, 0f, 0f, 0, 0f, 0f);
    }

    /**
     * Hangs a sheaf of notices as the mod's sheets of it, each where its share of the place the sprite paints says and
     * each a little proud of those under it. False, leaving the sprite, where the player's pictures are off, where the
     * sprite is not painted as something on its wall or the next square's length of it, and while a sheet's picture is
     * still being read.
     */
    private boolean addCollage(IsoObject object, Furniture piece, int x, int y, int z) {
        Decor.Collage collage = piece.collage;
        // The game pins some sheaves near a square's end, running onto the next square's length of the wall.
        if (!Own.pictures(build.playerIndex) || !WallHung.painted(build.frame, piece.wall, build.hungAt) && !WallHung.paintedAlong(build.hungAt)) {
            return false;
        }
        boolean ready = true;
        for (int picture : collage.pictures()) {
            if (!Decor.ready(picture)) {
                // The sprite paints the notices until a build made again once every sheet is read.
                Decor.held(picture);
                ready = false;
            }
        }
        if (!ready) {
            build.building.signed = false;
            return false;
        }
        float from = build.hungAt[WallHung.FROM], to = build.hungAt[WallHung.TO], bottom = build.hungAt[WallHung.BOTTOM], top = build.hungAt[WallHung.TOP];
        // Whoever faces a north wall has the map's x growing to their right, and whoever faces a west one has its y growing to their left.
        boolean north = piece.wall == WallHung.NORTH;
        float[] boxes = collage.boxes();
        // The whole sheaf stands clear of a moulding behind any of it, so no sheet is pushed out past one pinned over it.
        float clear = behind(piece, from, to, bottom, top);
        for (int k = 0; k < collage.count(); k++) {
            float left = boxes[4 * k], up = boxes[4 * k + 1], right = boxes[4 * k + 2], down = boxes[4 * k + 3];
            build.hungAt[WallHung.FROM] = north ? from + left * (to - from) : to - right * (to - from);
            build.hungAt[WallHung.TO] = north ? from + right * (to - from) : to - left * (to - from);
            build.hungAt[WallHung.TOP] = top - up * (top - bottom);
            build.hungAt[WallHung.BOTTOM] = top - down * (top - bottom);
            int picture = collage.pictures()[k];
            Models.Model model = placeHung(piece, build.hungAt, build.geometry.raised, hungPlace);
            standHung(piece, model, x, y, z, clear + k * SHEET_PROUD, Decor.held(picture), 1 << Models.PANE);
            float[] pane = Decor.pane(model);
            Decor.lay(pane[0], pane[1], pane[2], pane[3], (pane[1] - pane[0]) * hungPlace[SCALE], (pane[3] - pane[2]) * hungPlace[SCALE_UP], 0f,
                    Decor.wideForHigh(picture), build.hungShows);
            build.placed.picture(build.hungShows);
        }
        if (SightTrace.ON && build.building.pictured != null) {
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
        if (piece.model != null && !piece.model.name.equals("canvas") && !piece.model.name.equals("sheet")) {
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
        painter = piece;
        if (piece.dyed == 0) {
            return true;
        }
        IsoSprite paints = piece.paintedBy == null ? dyer.getSprite() : IsoSpriteManager.instance.namedMap.get(piece.paintedBy);
        if (paints == null) {
            throw new IllegalStateException(dyer.getSprite().getName() + " is painted by " + piece.paintedBy + ", which the game has no sprite of");
        }
        if (piece.paintedBy != null) {
            painter = Furniture.known(paints);
        }
        Texture dye = paints.getTextureForCurrentFrame(dyer.getDir());
        if (!WorldScene.usable(dye)) {
            return false;
        }
        build.dyeFrame.scale = build.frame.scale;
        WorldScene.frameOf(build.dyeFrame, dyer, dye);
        dyeTexture = dye.getID();
        return dyes(dye::isMaskSet, build.dyeFrame, painter, paints.getName());
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

    /**
     * How much further out from the edge behind it a piece at (centreX, centreY) on its first square (x, y) stands, along
     * its front, where a log wall stands on that edge: its back clear of the logs as of a wall's skin, in tiles. Left in
     * front leaves the way it faces.
     */
    private float clearOfLogs(Furniture piece, Models.Model model, int x, int y, int z, float centreX, float centreY) {
        Models.turn(piece.turns, 0f, -1f, front);
        float logs, gap;
        if (front[1] < -0.5f) {
            logs = build.wallBuilder.logsProud(x, y + piece.height, z, true);
            gap = piece.height - centreY;
        } else if (front[1] > 0.5f) {
            logs = build.wallBuilder.logsProud(x, y, z, true);
            gap = centreY;
        } else if (front[0] < -0.5f) {
            logs = build.wallBuilder.logsProud(x + piece.width, y, z, false);
            gap = piece.width - centreX;
        } else {
            logs = build.wallBuilder.logsProud(x, y, z, false);
            gap = centreX;
        }
        // The model's front is its -y, so its back reaches maxY behind its origin.
        return logs == 0f ? 0f : Math.max(WorldScene.WALL_HALF + logs + LOGS_CLEAR - (gap - model.maxY * piece.scale), 0f);
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
        // Stood on its own square, a name blade the map has one on along x and y from its post hung in the air a step
        // out from the post.
        if (piece.hungOn != null && holdsAny(x, y, z, piece.hungOn) == Boolean.FALSE && holdsAny(x - 1, y - 1, z, piece.hungOn) == Boolean.TRUE) {
            centreX -= 1f;
            centreY -= 1f;
        }
        if (piece.onRoof && build.roofBuilder.cornersAt(x, y, z, roofCorners)) {
            model = onRoof(piece, model, roofCorners, piece.posted != null && backedByWall(piece, x, y, z), onRoof);
            centreX = onRoof[0];
            centreY = onRoof[1];
            foot += onRoof[2];
        }
        if (piece.roofBelow > 0 && build.roofBuilder.cornersAt(x, y, z - piece.roofBelow, roofCorners)) {
            foot -= sunkOntoRoof(roofCorners, piece.centreX, piece.centreY, piece.footHalf) * ViewCamera.LEVEL_HEIGHT;
        }
        if (!piece.onRoof) {
            float clear = clearOfLogs(piece, model, x, y, z, centreX, centreY);
            centreX += front[0] * clear;
            centreY += front[1] * clear;
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
            // A piece painted by the sprite of it turned the other way looks where that sprite paints the piece as that one stands.
            Furniture look = painter;
            Models.turn(look.turns, 0f, look.paneY * look.scale, build.hungWay);
            float a = look.centreX + build.hungWay[0], b = look.centreY + build.hungWay[1];
            Models.turn(look.turns, 1f, 0f, build.hungWay);
            WallHung.shows(look == piece ? build.frame : build.dyeFrame, a, b, (look.raised + build.geometry.raised * ViewCamera.LEVEL_HEIGHT) / ViewCamera.LEVEL_HEIGHT,
                    build.hungWay[0] * look.scale, build.hungWay[1] * look.scale, look.scaleUp / ViewCamera.LEVEL_HEIGHT, build.hungShows);
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
        if (piece.goal != null) {
            addNet(piece.goal, x, y, z, foot - piece.raised);
        }
        if (piece.trough) {
            Troughs.addFill(build, piece, model, x, y, z, centreX, centreY, foot);
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
     * Hangs the net of a football goal's square behind its frame, on the side Leisure.netSide finds: the mesh as chain
     * link's fabric is woven (ChainLink.FABRIC), in white, and the white tubes that hold it out. A goal whose side is not
     * known until more of the map is loaded stands its frame alone until then. ground is the height of the square's foot.
     */
    private void addNet(Leisure.Goal goal, int x, int y, int z, float ground) {
        int side = Leisure.netSide(squares, x, y, z, goal);
        if (side == Leisure.NET_UNKNOWN) {
            build.wantsMap = true;
            return;
        }
        if (side == Leisure.NO_NET) {
            return;
        }
        SpriteGeometry.Mesh white = WorldScene.solid.get(WorldScene.PLAIN, WallSides.key(NET_WHITE, NET_WHITE, NET_WHITE, WallSides.FLAT));
        white.sway = 0f;
        float red = build.frame.red, green = build.frame.green, blue = build.frame.blue;
        int sheets = Leisure.netSheets(goal, side, netPoints);
        white.open = build.frame.open + 32f * ChainLink.FABRIC;
        for (int sheet = 0; sheet < sheets; sheet++) {
            for (int corner = 0; corner < 4; corner++) {
                int from = (sheet * 4 + corner) * 3;
                WorldScene.set(netCorners, corner, x + netPoints[from], y + netPoints[from + 1], ground + netPoints[from + 2]);
            }
            SpriteGeometry.addPlainQuad(white, netCorners, red, green, blue);
        }
        int tubes = Leisure.netTubes(goal, side, netPoints);
        white.open = build.frame.open;
        for (int tube = 0; tube < tubes; tube++) {
            int from = tube * 6;
            WorldScene.set(netFrom, 0, x + netPoints[from], y + netPoints[from + 1], ground + netPoints[from + 2]);
            WorldScene.set(netTo, 0, x + netPoints[from + 3], y + netPoints[from + 4], ground + netPoints[from + 5]);
            SpriteGeometry.addTube(white, netFrom, netTo, NET_TUBE_RADIUS, NET_TUBE_SIDES, red, green, blue);
        }
    }

    /** A goal's net and the tubes that hold it: white, and 40 mm across. */
    private static final float NET_WHITE = 0.9f, NET_TUBE_RADIUS = 0.02f;
    private static final int NET_TUBE_SIDES = 8;
    private final float[] netPoints = new float[48], netCorners = new float[12], netFrom = new float[3], netTo = new float[3];

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
