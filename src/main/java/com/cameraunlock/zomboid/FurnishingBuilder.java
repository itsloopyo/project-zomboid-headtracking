package com.cameraunlock.zomboid;

import zombie.core.textures.Texture;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
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

    /** Whether a square has an object on it whose sprite is called name. */
    private static boolean holdsNamed(IsoGridSquare square, String name) {
        if (square == null) {
            return false;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite != null && name.equals(sprite.getName())) {
                return true;
            }
        }
        return false;
    }

    private final WorldScene.Frame build;

    /** Where the piece last found ready takes its colours, and the texture of the sprite that gives them, PLAIN for a piece that takes none. */
    private float dyeU, dyeV, alsoU, alsoV;
    private int dyeTexture;
    private final SpriteGeometry.Frame partFrame = new SpriteGeometry.Frame();
    private final float[] partAt = new float[4], wholeAt = new float[4];
    private final float[] screenAt = new float[2];
    private final float[] roofCorners = new float[4], onRoof = new float[3];

    FurnishingBuilder(WorldScene.Frame build) {
        this.build = build;
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
        // The mod's picture for the sprite, where its pictures are on. A poster hangs as the
        // mod's sheet only to show one, and a frame, a clock or a lamp only where the mod's
        // furniture is on or it has a picture to show.
        int picture = piece != null && Own.pictures() ? piece.picture : Decor.NONE;
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
        float wide = build.hungAt[WallHung.TO] - build.hungAt[WallHung.FROM];
        float high = (build.hungAt[WallHung.TOP] - build.hungAt[WallHung.BOTTOM]) * ViewCamera.LEVEL_HEIGHT;
        float along = (build.hungAt[WallHung.FROM] + build.hungAt[WallHung.TO]) / 2f;
        Models.Model model = piece.model != null ? piece.model : Models.get(FRAMES[WallHung.frameFor(wide / high)]);
        float scale = wide / model.width(), scaleUp = high / model.height(), foot = build.hungAt[WallHung.BOTTOM] * ViewCamera.LEVEL_HEIGHT;
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
        // Its back clear of the wall's skin, which stands that far inside the square's edge.
        Models.turn(piece.turns, 0f, -1f, build.hungWay);
        float out = WorldScene.WALL_HALF + WorldScene.HUNG_CLEAR + model.depth() * scale / 2f;
        float a = WallHung.a(piece.wall, along), b = WallHung.b(piece.wall, along);
        float[] lift = build.geometry.lift;
        build.building.plain = false;
        build.placed.add(model, build.frame, x, y, x + a + build.hungWay[0] * out, y + b + build.hungWay[1] * out,
                z * ViewCamera.LEVEL_HEIGHT + (lift[0] + lift[1] + lift[2] + lift[3]) / 4f + foot, piece.turns, scale, scaleUp,
                !piece.pictured ? WorldScene.PLAIN : picture != Decor.NONE ? Decor.held(picture) : texture.getID(),
                piece.pictured ? 1 << Models.PANE : 0, 0f, 0f, 0, 0f, 0f);
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
        build.furnished++;
        return true;
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

    /**
     * Whether the piece the object is a square of can stand as its model: every square of it is
     * there, and the sprite that is to give it its colour is painted where the colour is
     * taken. Leaves where the colour is taken in dyeU to alsoV and dyeTexture.
     */
    private boolean ready(IsoObject object, Furniture piece, int x, int y, int z) {
        int originX = x - piece.gridX, originY = y - piece.gridY;
        IsoObject dyer = object;
        if (piece.grid != null) {
            for (int i = 0; i < piece.width; i++) {
                for (int j = 0; j < piece.height; j++) {
                    IsoObject part = holding(build.cell.getGridSquare(originX + i, originY + j, z), piece.grid.getSprite(i, j));
                    if (part == null) {
                        return false;
                    }
                    if (i == piece.dyeX && j == piece.dyeY) {
                        dyer = part;
                    }
                }
            }
        }
        dyeTexture = WorldScene.PLAIN;
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
        for (float share : DYE_TRIES) {
            float high = piece.dyeHigh * share;
            if (dye.isMaskSet(Math.round(SpriteGeometry.frameU(build.dyeFrame, piece.dyeA, piece.dyeB)),
                    Math.round(SpriteGeometry.frameV(build.dyeFrame, piece.dyeA, piece.dyeB, high)))) {
                dyeU = SpriteGeometry.textureU(build.dyeFrame, piece.dyeA, piece.dyeB);
                dyeV = SpriteGeometry.textureV(build.dyeFrame, piece.dyeA, piece.dyeB, high);
                if (piece.alsoDyed == 0) {
                    return true;
                }
                float alsoA = Float.isNaN(piece.alsoA) ? piece.dyeA : piece.alsoA, alsoB = Float.isNaN(piece.alsoB) ? piece.dyeB : piece.alsoB;
                alsoU = SpriteGeometry.textureU(build.dyeFrame, alsoA, alsoB);
                alsoV = SpriteGeometry.textureV(build.dyeFrame, alsoA, alsoB, piece.alsoHigh);
                return dye.isMaskSet(Math.round(SpriteGeometry.frameU(build.dyeFrame, alsoA, alsoB)),
                        Math.round(SpriteGeometry.frameV(build.dyeFrame, alsoA, alsoB, piece.alsoHigh)));
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
        if (piece.partner != null && !holdsNamed(build.cell.getGridSquare(x + piece.partnerX, y + piece.partnerY, z), piece.partner)) {
            return false;
        }
        if (piece.follows) {
            // Dealt with by the other sprite of the pair, which stands the whole piece.
            return true;
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
        // What the game lays over the piece's sprite, a stain, has no place on the model.
        if (piece.gridX != 0 || piece.gridY != 0 || build.overLayer > 0) {
            return true;
        }
        Models.Model model = piece.withBasin != null && onSquare(x, y, z, true) != null ? piece.withBasin : piece.model;
        build.building.plain = false;
        float centreX = piece.centreX, centreY = piece.centreY;
        if (piece.onRoof && build.roofBuilder.cornersAt(x, y, z, roofCorners)) {
            RoofFit.standOn(roofCorners, centreX, centreY, onRoof);
            centreX = onRoof[0];
            centreY = onRoof[1];
            foot += onRoof[2] * ViewCamera.LEVEL_HEIGHT;
            if (piece.posted != null) {
                model = piece.posted;
                foot -= piece.raised;
            }
        }
        build.placed.add(model, build.frame, x, y, x + centreX, y + centreY, foot, piece.turns, piece.scale, piece.scaleUp,
                dyeTexture, piece.dyed, dyeU, dyeV, piece.alsoDyed, alsoU, alsoV);
        if (piece.pictured) {
            // Each point of the pane looks at where the sprite paints it: the pane's face is paneY in front of the model's origin.
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
        if (piece.television && object instanceof IsoTelevision set) {
            // The game's sprite of a lit screen is painted for its own view and has no place on
            // the model: it is never drawn over one. With the mod's pictures switched off a set
            // that is on shows the bars, which is what the game's sprite is a picture of.
            int showing = Screens.showing(set);
            if (showing != Screens.DARK) {
                addLitScreen(piece, model, Screens.held(set, Own.pictures() ? showing : Screens.BARS, piece.blackAndWhite), piece.blackAndWhite, x, y, foot);
            }
        }
        return true;
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
