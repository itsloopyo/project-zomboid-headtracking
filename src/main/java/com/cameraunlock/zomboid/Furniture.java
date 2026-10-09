package com.cameraunlock.zomboid;

import java.util.IdentityHashMap;
import java.util.Set;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoDirections;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteGrid;

/** A sprite the mod has a model for, and how the model stands on the squares the game gives the piece. */
final class Furniture {
    /**
     * A model is in metres, and the view's lengths are in tiles, along the ground and up
     * alike: a level is ViewCamera.LEVEL_HEIGHT tiles up, and a pixel of the height the game
     * gives a surface is LEVEL_HEIGHT / WorldScene.SURFACE_PIXELS_PER_LEVEL of a tile. A tile is taken as
     * a metre. The character's eye stands 1.6 tiles up and a storey is 2.45, which is what
     * they are in metres.
     */
    static final float TILES_PER_METRE = 1f;
    /** How far inside the edge of its squares a piece of furniture stops, in tiles. */
    private static final float FURNITURE_CLEAR = 0.02f;
    /** How far the back of a piece that stands against a wall is from the edge of its square: clear of the wall's skin. */
    private static final float BACK_CLEAR = WorldScene.WALL_HALF + 0.02f;

    /** How high the foot of a cupboard on the wall is, and where on its sprite its colour is taken, in levels. */
    private static final float WALL_CUPBOARD_FOOT = 0.6f, WALL_CUPBOARD_DYE = 0.76f;
    /** The height, in levels, at which the sprite of a thing with no height of its own gives its colour: a bin, a barrel, a locker. */
    private static final float LOW_DYE = 0.2f, LOCKER_DYE = 0.35f;
    /** How high a picnic table's top is, in levels, and where on its first square the top's colour is taken. */
    private static final float PICNIC_TOP = 0.31f, PICNIC_DYE = 0.75f;

    /** What each sprite seen so far is, null for one that is no furniture the mod has a model of. */
    private static final IdentityHashMap<IsoSprite, Furniture> furniture = new IdentityHashMap<>();

    /** For a map loaded anew: its sprites are not the ones before's. */
    static void forget() {
        furniture.clear();
    }

    final Models.Model model;
    /** Quarter turns from facing north, as Models.turn takes them. */
    final int turns;
    /** The piece's squares when it has more than one, and how many there are along x and y. */
    final IsoSpriteGrid grid;
    final int width, height;
    /** Which of those squares this sprite is. */
    final int gridX, gridY;
    /** The model in place of model when a basin stands on the piece, or null for a piece that takes none. */
    Models.Model withBasin;
    /** Whether this is a basin: part of the model of the counter it stands on, and nothing of its own. */
    boolean basin;
    /** The surfaces that take their colour from the game's sprite, a bit for each, and those that take it from a second place on it. */
    int dyed, alsoDyed;
    /** Where on its square the sprite gives the colour, and how high, in levels: the first place and the second. */
    float dyeA = 0.5f, dyeB = 0.5f, dyeHigh, alsoHigh;
    /** Where on the square the second colour is given, where that is not over the first: NaN for over it. */
    float alsoA = Float.NaN, alsoB = Float.NaN;
    /** Whether this is a cake shop's glass case: DisplayCases lays its glass and stands its cakes. */
    boolean displayCase;
    /** The square whose sprite gives the colour. */
    int dyeX, dyeY;
    /** Tiles to a metre of the model along the ground and up, and how far above the floor its foot is, in tiles. */
    float scale = TILES_PER_METRE, scaleUp = TILES_PER_METRE, raised;
    /** Where the model's origin is from the corner of the piece's first square. */
    float centreX, centreY;
    /** For what hangs on a wall, which wall of its square: it is stood by addHung, where its sprite paints it. NONE for all else. */
    int wall = WallHung.NONE;
    /** Whether what its frame holds is the picture its sprite paints, and not a mirror's glass. */
    boolean pictured;
    /** See ownSize. */
    boolean ownSize;
    /** The picture of the mod's own that its frame holds in place of the sprite's painting, or Decor.NONE. */
    int picture = Decor.NONE;
    /** Whether this is a sheet pinned to a wall, a poster, which the mod hangs only to show its own picture of it. */
    boolean pinned;
    /** Whether this is the board the game lays over a counter as its top: see FurnishingBuilder.addCountertop. */
    boolean countertop;
    /** Whether it is a lettered board, which shows the mod's picture only where every square of it can: half a name in one hand and half in another reads as neither. */
    boolean lettered;
    /** How wide a frame is painted round that picture, in tiles: a painting on a board with no moulding has one. */
    float rail;
    /** Whether this is a television, which shows a picture when it is on, and whether it is one with no colour. */
    boolean television, blackAndWhite;
    /** For a piece painted in two sprites with no grid to tie them, the other's name and how far its square is from this one's: see Outdoors.Piece. */
    String partner;
    int partnerX, partnerY;
    /** Whether this sprite is the one of such a pair that stands nothing. */
    boolean follows;
    /** For a piece that stands on the floor and whose pane shows its sprite's painting, how far along the model's y the pane's face is. */
    float paneY;
    /** Whether this is a shop's fitting that ShopFittings stands goods or clothes on while the game paints what it holds. */
    boolean stocked;
    /** Whether this is a shop's glass counter, round whose case ShopFittings lays the glass. */
    boolean glazed;

    Furniture(IsoSprite sprite, String model, int turns) {
        this(sprite, model, turns, false);
    }

    /** A square that stands as a piece by itself, whatever squares the game lays its piece on. */
    static Furniture single(IsoSprite sprite, String model, int turns) {
        return new Furniture(sprite, model, turns, true);
    }

    /** alone for a sprite that is a piece by itself whatever squares the game lays its piece on. */
    private Furniture(IsoSprite sprite, String model, int turns, boolean alone) {
        this.model = model == null ? null : Models.get(model);
        this.turns = turns;
        grid = alone ? null : sprite.getSpriteGrid();
        width = grid == null ? 1 : grid.getWidth();
        height = grid == null ? 1 : grid.getHeight();
        gridX = grid == null ? 0 : grid.getSpriteGridPosX(sprite);
        gridY = grid == null ? 0 : grid.getSpriteGridPosY(sprite);
        centreX = width / 2f;
        centreY = height / 2f;
    }

    /** The surfaces that take the colour the sprite has over the middle of the piece's first square, high levels up. */
    Furniture dye(int surfaces, float high) {
        dyed = surfaces;
        dyeHigh = high;
        return this;
    }

    /** As dye, from (a, b) on the square. */
    Furniture dye(int surfaces, float a, float b, float high) {
        dyeA = a;
        dyeB = b;
        return dye(surfaces, high);
    }

    /** As dye, from the square at the piece's front: a bed's blanket is on the squares of its foot. */
    Furniture dyeAtFront(int surfaces, float high) {
        float[] front = new float[2];
        Models.turn(turns, 0f, -1f, front);
        dyeX = front[0] > 0f ? width - 1 : 0;
        dyeY = front[1] > 0f ? height - 1 : 0;
        return dye(surfaces, high);
    }

    /** The surfaces that take the colour the sprite has at another height over the same place: a counter's top. */
    Furniture also(int surfaces, float high) {
        alsoDyed = surfaces;
        alsoHigh = high;
        return this;
    }

    /** As also, from (a, b) on the square. */
    Furniture alsoAt(int surfaces, float a, float b, float high) {
        alsoA = a;
        alsoB = b;
        return also(surfaces, high);
    }

    /**
     * Stands the piece inside its squares, no bigger than fits them.
     *
     * @param topUp        how high the game says the piece's top is, in tiles, for one made as high as that, or 0
     * @param reference    how high the model's own top is, in metres, where topUp is given
     * @param againstBack  whether the piece stands with its back at the edge of its squares and not in the middle of them
     */
    Furniture stand(float topUp, float reference, boolean againstBack) {
        boolean alongY = (turns & 1) == 0;
        float along = alongY ? height : width, across = alongY ? width : height;
        float fits = Math.min((along - 2f * FURNITURE_CLEAR) / model.depth(), (across - 2f * FURNITURE_CLEAR) / model.width());
        scale = scaleUp = Math.min(topUp > 0f ? topUp / reference : TILES_PER_METRE, fits);
        back(againstBack ? Math.max((along - model.depth() * scale) / 2f - BACK_CLEAR, 0f) : 0f);
        return this;
    }

    /**
     * Stands a piece that meets the next one along: a metre of it is a tile along the ground
     * whatever its height, so that a run of counters has no gaps in it.
     *
     * @param centred  whether the model is built round the middle of its own depth, to be stood at the back of its
     *                 square, and not round the middle of the square it fills
     */
    Furniture meet(float topUp, float reference, boolean centred) {
        scaleUp = topUp > 0f ? topUp / reference : TILES_PER_METRE;
        back(centred ? (1f - model.depth()) / 2f - BACK_CLEAR : 0f);
        return this;
    }

    private void back(float slack) {
        float[] front = new float[2];
        Models.turn(turns, 0f, -1f, front);
        centreX = width / 2f - front[0] * slack;
        centreY = height / 2f - front[1] * slack;
    }

    /** Makes a long seat as high as the game says its seat is and no shorter along the ground for it. */
    Furniture seat(float seatUp, float reference) {
        scaleUp = seatUp / reference;
        return this;
    }

    Furniture raise(float up) {
        raised = up;
        return this;
    }

    /** As dye, from over the back of the piece's square: a shelf on a wall is nowhere near the square's middle. */
    Furniture dyeAtBack(int surfaces, float high) {
        float[] back = new float[2];
        Models.turn(turns, 0f, 1f, back);
        return dye(surfaces, 0.5f + back[0] * BACK_DYE, 0.5f + back[1] * BACK_DYE, high);
    }

    /**
     * For a picture that hangs along several squares of a wall, how many lengths it is of. The
     * game numbers them across its grid for some pictures and down it for others, whichever
     * wall they hang on, and either way in the order they follow each other as the map's x or
     * y grows.
     */
    int lengths() {
        return Math.max(width, height);
    }

    /** Which of those lengths this sprite is. */
    int lengthAt() {
        return width > 1 ? gridX : gridY;
    }

    Furniture hang(int wall, boolean pictured) {
        this.wall = wall;
        this.pictured = pictured;
        return this;
    }

    /** For what hangs on a wall and stands out from it, a lamp: it is as big as its model, whatever its sprite's outline comes to. */
    Furniture ownSize() {
        ownSize = true;
        return this;
    }

    static Furniture known(IsoSprite sprite) {
        Furniture piece = furniture.get(sprite);
        if (piece == null && !furniture.containsKey(sprite)) {
            piece = recognise(sprite);
            furniture.put(sprite, piece);
        }
        return piece;
    }

    /**
     * Which of the mod's models a sprite is a square of, by what the game says the sprite is:
     * the name it gives the piece and the kind of piece, what the piece holds, which way it
     * faces, how high its top is and the squares it lies on. Null for a piece of another shape
     * than the models: a rocking chair is not the armchair, a round table not the square one.
     */
    private static Furniture recognise(IsoSprite sprite) {
        String name = sprite.getName();
        PropertyContainer properties = sprite.getProperties();
        String called = properties.get("CustomName");
        IsoSpriteGrid grid = sprite.getSpriteGrid();
        if (name != null && (grid == null || grid.getLevels() == 1)) {
            IsoDirections way = sprite.getFacing();
            int faces = way == IsoDirections.N ? Outdoors.NORTH : way == IsoDirections.E ? Outdoors.EAST : way == IsoDirections.S ? Outdoors.SOUTH
                    : way == IsoDirections.W ? Outdoors.WEST : Outdoors.NONE;
            Outdoors.Piece outside = Outdoors.of(name, called, properties.get("GroupName"), faces, grid == null ? 1 : grid.getWidth(),
                    grid == null ? 1 : grid.getHeight(), properties.getSurface());
            if (outside != null) {
                return outdoors(sprite, outside);
            }
            Outdoors.Piece works = Industry.of(name, called, properties.get("GroupName"), faces, grid == null ? 1 : grid.getWidth(),
                    grid == null ? 1 : grid.getHeight(), properties.getSurface());
            if (works != null) {
                return outdoors(sprite, works);
            }
            Outdoors.Piece camp = Camp.of(name, called, properties.get("GroupName"), faces, grid == null ? 1 : grid.getWidth(),
                    grid == null ? 1 : grid.getHeight(), properties.getSurface());
            if (camp != null) {
                return outdoors(sprite, camp);
            }
        }
        if (name != null && called == null && grid == null && name.startsWith(STREET_LAMPS)) {
            return streetLamp(sprite, name);
        }
        Furniture home = HomeFittings.of(sprite, name, called, properties);
        if (home != null) {
            return home;
        }
        if (name != null && called == null && grid == null && name.startsWith(Clinic.OFFICE)) {
            return Clinic.unnamedDesk(sprite, name);
        }
        if (name != null && called == null) {
            Furniture unnamed = ShopFittings.unnamed(sprite, name);
            if (unnamed != null) {
                return unnamed;
            }
        }
        if (name == null || called == null || grid != null && grid.getLevels() != 1) {
            return null;
        }
        String group = properties.get("GroupName") == null ? "" : properties.get("GroupName");
        String holds = properties.get("container") == null ? "" : properties.get("container");
        int surface = properties.getSurface();
        float surfaceHigh = surface / WorldScene.SURFACE_PIXELS_PER_LEVEL, surfaceUp = surfaceHigh * ViewCamera.LEVEL_HEIGHT;
        boolean low = properties.has("IsLow"), onTable = properties.has("IsTableTop");
        boolean counterHigh = surface >= 30 && surface <= 40;
        IsoDirections facing = sprite.getFacing();
        boolean blocks = properties.has(IsoFlagType.solid) || properties.has(IsoFlagType.solidtrans);
        Furniture indoors = Indoors.anyWay(sprite, name, called, group, holds, properties, facing, blocks);
        if (indoors != null) {
            return indoors;
        }
        Furniture shop = ShopFittings.anyWay(sprite, called, holds, facing);
        if (shop != null) {
            return shop;
        }
        if (facing != null && !blocks && holds.isEmpty() && !onTable && surface == 0) {
            // The game gives a facing as one of the four ways along the map's axes.
            int faces = facing == IsoDirections.N ? 0 : facing == IsoDirections.E ? 1 : facing == IsoDirections.S ? 2 : 3;
            if (called.equals("Clock") && grid == null) {
                return new Furniture(sprite, "wall_clock", faces).hang(WallHung.behind(facing), false);
            }
            // A lamp on a wall stands out from it, and laid in the wall's plane as its sprite is
            // painted it is a bowl with nothing holding it up. It faces away from its wall.
            if (called.equals("Wall Lamp") && group.endsWith("Half Sphere") && grid == null) {
                return new Furniture(sprite, "wall_bowl", faces).hang(WallHung.behind(facing), false).ownSize();
            }
            if (called.equals("Outdoor Lamp") && grid == null) {
                String lamp = group.equals("Fancy") ? "wall_bowl" : group.equals("Modern") || group.equals("Oval") ? "bulkhead_light" : "wall_lantern";
                return new Furniture(sprite, lamp, faces).hang(WallHung.behind(facing), false).ownSize();
            }
            // An air conditioner is let through its wall and stands out of it: the case of one, on
            // the sheet of things a works has. The grilles the game calls by the same name
            // are Indoors'.
            if (called.equals("Conditioner") && name.startsWith("industry_") && grid == null) {
                return new Furniture(sprite, "air_conditioner", faces).hang(WallHung.behind(facing), false).ownSize();
            }
            // One square of a mirror or a picture that is longer than a square has no frame of
            // its own, and each square's length of it meets the next.
            if (called.equals("Mirror") && name.startsWith("walls_decoration")) {
                return new Furniture(sprite, grid == null ? null : "canvas", faces).hang(WallHung.behind(facing), false);
            }
            // A picture's sprite shows its face only from the two sides the game's view sees.
            if (FRAMED.contains(called) && (facing == IsoDirections.S || facing == IsoDirections.E)) {
                Furniture framed = new Furniture(sprite, grid == null ? null : "canvas", faces).hang(WallHung.behind(facing), true);
                framed.picture = Decor.of(name, called, group, grid != null);
                framed.rail = grid != null && called.startsWith("Painting") ? PAINTED_RAIL : 0f;
                return framed;
            }
            // A poster is a sheet with no frame, and hangs as one only where the mod has a
            // picture for it: laid on its wall as the game's sprite it needs no model.
            if (PINNED.contains(called) && (facing == IsoDirections.S || facing == IsoDirections.E)) {
                int picture = Decor.of(name, called, group, grid != null);
                if (picture != Decor.NONE) {
                    Furniture pinned = new Furniture(sprite, "canvas", faces).hang(WallHung.behind(facing), true);
                    pinned.picture = picture;
                    pinned.pinned = true;
                    pinned.lettered = called.equals("Sign");
                    return pinned;
                }
            }
        }
        // A drum is round, and a crate the same from every side: the game gives most of them no front.
        if ((called.equals("Metal Drum") || called.equals("Barrel")) && grid == null) {
            return new Furniture(sprite, "barrel", 0).dye(1 << Models.PAINT, LOW_DYE).stand(0f, 0f, false);
        }
        if (called.equals("Barbecue") && holds.equals("barbecue") && grid == null) {
            return new Furniture(sprite, "kettle_barbecue", 0).dye(1 << Models.PAINT, KETTLE_LID).stand(0f, 0f, false);
        }
        if (called.equals("Stool") && grid == null && properties.has(IsoFlagType.bed) && !group.equals("Organs")) {
            Furniture stool = new Furniture(sprite, "stool", 0).dye(1 << Models.WOOD, surface > 0 ? surfaceHigh : LOW_DYE);
            return stool.stand(surfaceUp, stool.model.height(), false);
        }
        if (called.equals("Crate") && (holds.equals("crate") || holds.equals("militarycrate")) && !group.contains("Metal") && grid == null
                && surface >= 25 && surface <= 40) {
            Furniture crate = new Furniture(sprite, "crate", 0).dye(1 << Models.WOOD, surfaceHigh * 0.75f);
            return crate.stand(surfaceUp, crate.model.height(), false);
        }
        if (facing == null) {
            // A table for four has no front, and the game gives it none.
            if (grid == null && name.startsWith("furniture_tables_high") && properties.isTable() && surface > 0 && surface <= WorldScene.TABLE_HIGHEST) {
                Furniture table = new Furniture(sprite, called.contains("Round") ? "round_table" : "small_table", 0).dye(1 << Models.WOOD, surfaceHigh);
                return table.stand(surfaceUp, table.model.height(), false);
            }
            return null;
        }
        // The game gives a facing as one of the four ways along the map's axes.
        int turns = facing == IsoDirections.N ? 0 : facing == IsoDirections.E ? 1 : facing == IsoDirections.S ? 2 : 3;
        boolean alongY = (turns & 1) == 0;
        int width = grid == null ? 1 : grid.getWidth(), height = grid == null ? 1 : grid.getHeight();
        int along = alongY ? height : width, across = alongY ? width : height;
        boolean single = grid == null, pair = along == 1 && across == 2;
        boolean tableHigh = properties.isTable() && surface > 0 && surface <= WorldScene.TABLE_HIGHEST;
        indoors = Indoors.facing(sprite, name, called, group, holds, properties, turns, along, across);
        if (indoors != null) {
            return indoors;
        }
        Furniture glassCase = DisplayCases.of(sprite, group, holds, properties, turns, along, across);
        if (glassCase != null) {
            return glassCase;
        }
        Furniture onWall = WallObjects.model(sprite, called, group, properties, turns, along, across);
        if (onWall != null) {
            return onWall;
        }
        shop = ShopFittings.facing(sprite, called, group, holds, properties, turns, along, across);
        if (shop != null) {
            return shop;
        }
        // A bed faces the way its foot points, so its head is the model's back. Its blanket's
        // colour is on the squares of its foot: the squares of its head show the pillows.
        if (name.startsWith("furniture_bedding") && called.equals("Bed") && along == 2 && across <= 2) {
            return new Furniture(sprite, across == 2 ? "double_bed" : "single_bed", turns)
                    .dyeAtFront(1 << Models.CLOTH, surface > 0 ? surfaceHigh : WorldScene.BED_HEIGHT).stand(0f, 0f, false);
        }
        if (called.equals("Wardrobe") && holds.equals("wardrobe") && surface >= WorldScene.TALL_SURFACE && pair) {
            Furniture wardrobe = new Furniture(sprite, "wardrobe", turns).dye(1 << Models.WOOD, surfaceHigh / 2f);
            return wardrobe.stand(surfaceUp, wardrobe.model.height(), true);
        }
        // A tall cupboard on one square: a steel locker, or shelves that stand on the floor.
        if (called.equals("Locker") && single && !low && (holds.equals("locker") || holds.equals("militarylocker") || holds.equals("metal_shelves"))) {
            Furniture locker = new Furniture(sprite, "locker", turns).dye(1 << Models.PAINT, LOCKER_DYE);
            return locker.stand(surface >= WorldScene.TALL_SURFACE ? surfaceUp : 0f, locker.model.height(), true);
        }
        if (called.equals("Shelves") && holds.equals("shelves") && name.startsWith("furniture_shelving") && properties.has(IsoFlagType.solid)
                && surface >= WorldScene.TALL_SURFACE && single) {
            Furniture bookcase = new Furniture(sprite, "bookcase", turns).dye(1 << Models.WOOD, surfaceHigh / 2f);
            return bookcase.stand(surfaceUp, bookcase.model.height(), true);
        }
        if (name.startsWith("furniture_seating_indoor") && called.equals("Couch") && surface > 0 && pair) {
            return new Furniture(sprite, "sofa", turns).dye(1 << Models.CLOTH, surfaceHigh).stand(0f, 0f, true);
        }
        if (called.equals("Shelves") && single && properties.has("IsHigh") && !blocks && surface >= WALL_SHELF_LOWEST
                && holds.equals("shelves")) {
            // The game gives the height of the top board. Two boards where that leaves room for a second under it.
            Furniture shelf = new Furniture(sprite, surface >= WALL_SHELVES_LOWEST ? "wall_shelves" : "wall_shelf", turns)
                    .dyeAtBack(1 << Models.WOOD, surfaceHigh);
            return shelf.stand(0f, 0f, true).raise(surfaceUp - shelf.model.height());
        }
        // Low open shelves in a run, each square of them meeting the next as counters do.
        if (called.equals("Shelves") && single && low && properties.isTable() && counterHigh && holds.equals("shelves")
                && name.startsWith("furniture_shelving")) {
            Furniture shelves = new Furniture(sprite, "low_shelves", turns).dye(1 << Models.WOOD, surfaceHigh * 0.5f);
            return shelves.meet(surfaceUp, shelves.model.height(), true);
        }
        // Shelving two squares long with no height given: a shop's, or steel racking.
        if (called.equals("Shelves") && pair && surface == 0 && properties.has(IsoFlagType.solidtrans) && !low) {
            if (holds.equals("metal_shelves")) {
                return new Furniture(sprite, "metal_rack", turns).dye(1 << Models.PAINT, LOCKER_DYE).stand(0f, 0f, true);
            }
            if (holds.equals("shelves")) {
                return new Furniture(sprite, "store_shelves", turns).dye(1 << Models.PAINT, LOCKER_DYE).stand(0f, 0f, true);
            }
            return null;
        }
        if (called.equals("Chair") && surface > 0 && single && "badBed".equals(properties.get("BedType"))
                && (group.contains("Plastic") || group.equals("Folding"))) {
            return group.equals("Folding") ? new Furniture(sprite, "folding_chair", turns).dye(1 << Models.VINYL, surfaceHigh).stand(0f, 0f, false)
                    : new Furniture(sprite, "plastic_chair", turns).dye(1 << Models.PLASTIC, surfaceHigh).stand(0f, 0f, false);
        }
        // A diner's bench: the game marks it as something to rest on, two squares long.
        if (called.equals("Seat") && pair && properties.has(IsoFlagType.bed)) {
            return new Furniture(sprite, "booth", turns).dye(1 << Models.VINYL, BOOTH_SEAT).also(1 << Models.WOOD, BOOTH_BASE).stand(0f, 0f, true);
        }
        if (name.startsWith("furniture_seating_indoor") && called.equals("Chair") && surface > 0 && single) {
            // What the game rates as fair to sleep in is an armchair. What it rates as bad is a hard chair.
            if ("averageBed".equals(properties.get("BedType")) && !group.startsWith("Rocking")) {
                return new Furniture(sprite, "armchair", turns).dye(1 << Models.CLOTH, surfaceHigh).stand(0f, 0f, false);
            }
            if ("badBed".equals(properties.get("BedType")) && !group.contains("Plastic") && !group.contains("Folding")) {
                return new Furniture(sprite, group.contains("Office") ? "office_chair" : "dining_chair", turns).dye(1 << Models.CLOTH, surfaceHigh)
                        .stand(0f, 0f, false);
            }
            return null;
        }
        if (called.equals("Drawers") && surface > 0 && single && (holds.equals("sidetable") || holds.equals("dresser") && !group.contains("Mirror"))) {
            Furniture drawers = new Furniture(sprite, holds.equals("dresser") ? "dresser" : "bedside_cabinet", turns).dye(1 << Models.WOOD, surfaceHigh);
            return drawers.stand(surfaceUp, drawers.model.height(), true);
        }
        // A desk faces whoever sits at it: its drawers are on that side.
        if (called.equals("Desk") && surface > 0 && pair) {
            Furniture desk = new Furniture(sprite, "desk", turns).dye(1 << Models.WOOD, surfaceHigh);
            return desk.stand(surfaceUp, desk.model.height(), false);
        }
        if (called.equals("Desk") && tableHigh && along == 1 && across == 3) {
            Furniture desk = new Furniture(sprite, "office_desk", turns).dye(1 << Models.WOOD, surfaceHigh);
            return desk.stand(surfaceUp, desk.model.height(), false);
        }
        // A table lies across the way it faces, as a desk does. A pool table is no table to sit at.
        if ((called.equals("Table") || called.equals("Low Table")) && tableHigh && !group.equals("Pool") && !group.contains("Glass")) {
            boolean lowTable = surface <= LOW_TABLE_HIGHEST;
            String model = pair ? lowTable ? "low_table_2" : "table_2" : along == 1 && across == 3 && !lowTable ? "table_3"
                    : along == 2 && across == 2 && !lowTable ? "table_4" : single && lowTable && holds.isEmpty() ? "low_table"
                    : single && !lowTable && group.startsWith("Long") ? "narrow_table" : null;
            if (model == null) {
                return null;
            }
            Furniture table = new Furniture(sprite, model, turns).dye(1 << Models.WOOD, surfaceHigh);
            return table.stand(surfaceUp, table.model.height(), false);
        }
        if (called.equals("Counter") && single) {
            boolean corner = group.contains("Corner");
            // A shop's counter or a bar: a counter with no doors on the side it is seen from, at
            // whatever height the game gives it.
            if (low && properties.isTable() && surface >= 30 && surface <= WorldScene.TABLE_HIGHEST && (holds.isEmpty() || holds.equals("counter") && !counterHigh)) {
                Furniture counter = new Furniture(sprite, corner ? "counter_corner" : "shop_counter", turns)
                        .dye(1 << Models.WOOD, surfaceHigh * 0.45f).also(1 << Models.WORKTOP, surfaceHigh);
                return counter.meet(surfaceUp, counter.model.height(), !corner);
            }
            if (low && properties.isTable() && holds.equals("counter") && counterHigh) {
                Furniture counter = new Furniture(sprite, corner ? "counter_corner" : "counter", turns)
                        .dye(1 << Models.WOOD, surfaceHigh * 0.45f).also(1 << Models.WORKTOP, surfaceHigh);
                if (!corner) {
                    counter.withBasin = Models.get("counter_sink");
                }
                return counter.meet(surfaceUp, counter.model.height(), !corner);
            }
            // A canteen's serving counter: steel, with pans in its top under a sloping glass. The
            // game gives it no height, and wrapped on the box its outline makes the glass is a
            // tilted blue slab.
            if (low && surface == 0 && holds.equals("restaurantdisplay")) {
                return new Furniture(sprite, "serving_counter", turns).meet(0f, 0f, true);
            }
            if (properties.has("IsHigh") && surface == 0 && (holds.equals("counter") || holds.equals("overhead"))) {
                return new Furniture(sprite, corner ? "wall_cupboard_corner" : "wall_cupboard", turns).dye(1 << Models.WOOD, WALL_CUPBOARD_DYE)
                        .meet(0f, 0f, !corner).raise(WALL_CUPBOARD_FOOT * ViewCamera.LEVEL_HEIGHT);
            }
            return null;
        }
        // A basin on a wall with nothing under it. The game gives the height of its rim.
        if (called.equals("Sink") && !onTable && single && group.endsWith("Hanging") && surface > 0 && !blocks) {
            Furniture sink = new Furniture(sprite, "wall_sink", turns).stand(0f, 0f, true);
            return sink.raise(surfaceUp - SINK_RIM * sink.scaleUp);
        }
        // A basin let into a counter. The counter's model has it.
        if (called.equals("Sink") && onTable && single) {
            Furniture basin = new Furniture(sprite, null, turns);
            basin.basin = true;
            return basin;
        }
        if (called.equals("Fridge") && holds.equals("fridge") && (single || pair) && (surface >= 55 || single && counterHigh)) {
            Furniture fridge = new Furniture(sprite, pair ? "large_fridge" : surface >= 55 ? "fridge" : "mini_fridge", turns)
                    .dye(1 << Models.ENAMEL, surfaceHigh * 0.8f);
            return fridge.stand(surfaceUp, fridge.model.height(), true);
        }
        // A shop's freezer chest under glass, two squares long.
        if (called.equals("Freezer") && pair && low && surface >= 25 && surface <= 40) {
            Furniture freezer = new Furniture(sprite, "chest_freezer", turns).dye(1 << Models.ENAMEL, surfaceHigh * 0.5f);
            return freezer.stand(surfaceUp, freezer.model.height(), false);
        }
        if (holds.equals("dishwasher") && single && counterHigh) {
            Furniture washer = new Furniture(sprite, "dishwasher", turns).dye(1 << Models.ENAMEL, surfaceHigh * 0.5f);
            return washer.stand(surfaceUp, washer.model.height(), true);
        }
        // What stands on a counter says how high the counter is.
        if (onTable && single && surface > 0) {
            if (holds.equals("microwave")) {
                return new Furniture(sprite, "microwave", turns).stand(0f, 0f, false).raise(surfaceUp);
            }
            if (holds.equals("cashregister")) {
                return new Furniture(sprite, "register", turns).stand(0f, 0f, false).raise(surfaceUp);
            }
            if (called.equals("Radio")) {
                return new Furniture(sprite, "radio", turns).stand(0f, 0f, false).raise(surfaceUp);
            }
        }
        if (called.equals("Machine") && single && surface == 0) {
            if (holds.equals("vendingsnack") || holds.equals("vendingpop")) {
                return new Furniture(sprite, "vending_machine", turns).dye(1 << Models.PAINT, LOCKER_DYE).stand(0f, 0f, true);
            }
            if (group.endsWith("Arcade")) {
                return new Furniture(sprite, "arcade_machine", turns).stand(0f, 0f, true);
            }
            return null;
        }
        if (called.equals("Dumpster") && holds.equals("dumpster") && pair) {
            return new Furniture(sprite, "dumpster", turns).dye(1 << Models.PAINT, LOW_DYE).stand(0f, 0f, false);
        }
        if (called.equals("Barbecue") && holds.equals("barbecuepropane") && single) {
            return new Furniture(sprite, "gas_barbecue", turns).stand(0f, 0f, false);
        }
        // A shower is on the wall behind it. The model is built on its square, the wall at its back. One with no tray
        // stands the same on the walls the game's view sees from behind.
        if (called.equals("Shower") && single && name.startsWith("fixtures_bathroom") && (facing == IsoDirections.S || facing == IsoDirections.E || group.equals("Wall"))) {
            return group.equals("Wall") ? new Furniture(sprite, "shower_head", turns).meet(0f, 0f, false).raise(SHOWER_TAPS)
                    : new Furniture(sprite, "shower", turns).meet(0f, 0f, false);
        }
        if (called.equals("Oven") && holds.equals("stove") && single && counterHigh) {
            return new Furniture(sprite, "oven", turns).dye(1 << Models.ENAMEL, surfaceHigh * 0.45f).stand(surfaceUp, OVEN_HOB, true);
        }
        if (holds.equals("clothingwasher") && single && counterHigh) {
            return new Furniture(sprite, "washing_machine", turns).dye(1 << Models.ENAMEL, surfaceHigh * 0.5f).stand(surfaceUp, WASHER_TOP, true);
        }
        if (called.equals("Television") && single) {
            // A set on a table says how high the table it was painted for is. One in a cabinet of
            // its own stands on the floor, and is old enough to have no colour.
            Furniture set = onTable ? new Furniture(sprite, group.equals("Premium Technologies") ? "tv_wide" : "tv", turns).stand(0f, 0f, false).raise(surfaceUp)
                    : surface >= 25 && surface <= 40
                    ? new Furniture(sprite, "tv_console", turns).dye(1 << Models.WOOD, surfaceHigh * 0.25f).stand(surfaceUp, TV_CABINET_TOP, true) : null;
            if (set != null) {
                set.television = true;
                set.blackAndWhite = !onTable;
            }
            return set;
        }
        if (called.equals("Lamp") && onTable && single && surface > 0) {
            return new Furniture(sprite, "table_lamp", turns).stand(0f, 0f, false).raise(surfaceUp);
        }
        if (called.equals("Toilet") && single && name.startsWith("fixtures_bathroom_01")) {
            return new Furniture(sprite, "toilet", turns).stand(0f, 0f, true);
        }
        if (called.equals("Bath") && pair) {
            return new Furniture(sprite, "bath", turns).stand(0f, 0f, true);
        }
        if (called.equals("Bin") && holds.equals("bin") && single && (group.equals("Wheelie") || group.contains("Garbage"))) {
            return new Furniture(sprite, group.equals("Wheelie") ? "wheelie_bin" : "litter_bin", turns).dye(1 << Models.PLASTIC, LOW_DYE).stand(0f, 0f, false);
        }
        if (called.equals("Box") && holds.equals("postbox") && single) {
            return group.equals("Mail") ? new Furniture(sprite, "mailbox", turns).stand(0f, 0f, false)
                    : new Furniture(sprite, "postbox", turns).dye(1 << Models.PAINT, LOW_DYE).stand(0f, 0f, false);
        }
        if (called.equals("Bench") && (group.equals("Park") || group.equals("Fancy Outdoors")) && surface > 0 && pair) {
            return new Furniture(sprite, "park_bench", turns).dye(1 << Models.WOOD, surfaceHigh * 0.9f).stand(surfaceUp, BENCH_SEAT, false);
        }
        if ((called.equals("Picknic Table") || called.equals("Table") && group.equals("Picknic")) && width == 2 && height == 2) {
            return new Furniture(sprite, "picnic_table", turns).dye(1 << Models.WOOD, PICNIC_DYE, PICNIC_DYE, PICNIC_TOP).stand(0f, 0f, false);
        }
        return null;
    }

    /** A piece that stands out of doors, stood as Outdoors says it stands. */
    private static Furniture outdoors(IsoSprite sprite, Outdoors.Piece outside) {
        Furniture piece = new Furniture(sprite, outside.model, outside.turns, outside.each);
        if (outside.dyed != 0) {
            piece.dye(outside.dyed, outside.dyeA, outside.dyeB, outside.dyeHigh);
            piece.dyeX = outside.dyeX;
            piece.dyeY = outside.dyeY;
        }
        if (outside.alsoDyed != 0) {
            piece.also(outside.alsoDyed, outside.alsoHigh);
        }
        piece.partner = outside.partner;
        piece.partnerX = outside.partnerX;
        piece.partnerY = outside.partnerY;
        piece.follows = outside.follows;
        piece.pictured = outside.pictured;
        piece.paneY = outside.paneY;
        if (outside.fits) {
            piece.stand(outside.top, piece.model.height(), false);
        }
        if (piece.grid == null) {
            piece.centreX = outside.a;
            piece.centreY = outside.b;
        }
        return piece.raise(outside.raised);
    }

    /** The sheet the game's street lamps are on. They carry no name: which lamp a sprite is, is its place on the sheet. */
    private static final String STREET_LAMPS = "lighting_outdoor_01_";
    /** Where on its sprite a lamp's post gives its colour, in levels. */
    private static final float POST_DYE = 0.5f, UPPER_POST_DYE = 0.25f;

    /**
     * The model of a street lamp, by which sprite of the game's sheet of them it is. The first
     * four are whole lamps, a post with its light. A lamp taller than a storey is two sprites,
     * and the upper one, on the level above, is the head: an acorn lantern, or two of them on
     * arms out along x and then along y, or the arm of a road lamp, which reaches out north,
     * east, south and west in that order on the sheet, or the two flat heads of a car park's
     * lamp, side by side along y and then along x. Null for the
     * rest of the sheet: bare posts, which stand well enough as they are, signals, and heads
     * the mod has no model of.
     */
    private static Furniture streetLamp(IsoSprite sprite, String name) {
        // The sheet has a copy of itself with the lamps lit, the same lamps in the same places.
        String place = name.substring(STREET_LAMPS.length());
        if (place.startsWith("on_")) {
            place = place.substring(3);
        }
        if (place.isEmpty() || place.length() > 3 || !place.chars().allMatch(Character::isDigit)) {
            return null;
        }
        int which = Integer.parseInt(place);
        return switch (which) {
            case 0, 2 -> new Furniture(sprite, "cone_lamp", 0).dye(1 << Models.PAINT, POST_DYE);
            case 1 -> new Furniture(sprite, "globe_lamp", 0).dye(1 << Models.PAINT, POST_DYE);
            case 3 -> new Furniture(sprite, "lantern_lamp", 0).dye(1 << Models.PAINT, POST_DYE);
            case 5, 6, 7 -> onPost(new Furniture(sprite, which == 5 ? "acorn_head" : "twin_acorn_head", which == 7 ? 1 : 0).dye(1 << Models.PAINT, UPPER_POST_DYE));
            case 8, 9, 10, 11 -> new Furniture(sprite, "cobra_head", which - 8).dye(1 << Models.PAINT, UPPER_POST_DYE);
            case 18, 19 -> new Furniture(sprite, "twin_head", which - 18).dye(1 << Models.PAINT, UPPER_POST_DYE);
            default -> null;
        };
    }

    /**
     * Where on its square the axis of the post under an acorn lamp's head stands: the game's
     * lower sprite of that post (lighting_outdoor_01_4) is stood by Posts, which reads its foot
     * there, and a head stood in the square's middle met it a hand to one side.
     */
    static final float LAMP_POST_AXIS = 0.53f;

    private static Furniture onPost(Furniture head) {
        head.centreX = LAMP_POST_AXIS;
        head.centreY = LAMP_POST_AXIS;
        return head;
    }

    /** How high, in metres, the part of a model is that the game gives the height of: an oven's hob under its back, a bench's seat, a basin's rim. */
    private static final float OVEN_HOB = 0.91f, WASHER_TOP = 0.92f, TV_CABINET_TOP = 0.78f, BENCH_SEAT = 0.455f, SINK_RIM = 0.44f;
    /** How far from the middle of its square, towards its back, a piece on the wall gives its colour. */
    private static final float BACK_DYE = 0.36f;
    /** How high above the floor the lowest part of a shower with no tray is, its taps, in tiles: its model starts there. */
    private static final float SHOWER_TAPS = 0.99f;
    /** The height the game gives a shelf on a wall from which it is a shelf and no low cupboard, and from which there is room for a second board under the first. */
    private static final int WALL_SHELF_LOWEST = 50, WALL_SHELVES_LOWEST = 60;
    /** The highest a table's top is, as the game gives it, for the table to be one to sit on a sofa at. */
    private static final int LOW_TABLE_HIGHEST = 22;
    /** Where on its sprite a kettle grill's lid gives its colour, and a diner bench's seat and its base, in levels. */
    private static final float KETTLE_LID = 0.36f, BOOTH_SEAT = 0.2f, BOOTH_BASE = 0.06f;
    /** What the game calls the things that hang in a frame, or as a board, with a picture on their face. */
    private static final Set<String> FRAMED = Set.of("Painting", "Paintings", "Certificate", "Whiteboard", "Noteboard", "Dartboard");
    /** How wide the frame round a painting longer than a square is, in tiles. */
    private static final float PAINTED_RAIL = 0.05f;
    /** And the sheets pinned to a wall with one, the boards of a shop's sign and a flag hung flat. */
    private static final Set<String> PINNED = Set.of("Poster", "Banner", "Sign", "Flag");
}
