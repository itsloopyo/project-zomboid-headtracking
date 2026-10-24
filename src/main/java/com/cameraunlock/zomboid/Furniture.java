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
    private static final float LOW_DYE = 0.2f, LOCKER_DYE = 0.35f, VENDING_CABINET = 0.04f;
    /** Where the face of a vending machine's window and sign is along its model's y: vending_machine's window in furniture.py. */
    private static final float VENDING_GLASS_Y = -0.388f;
    /** How far across its square a dumpster's south side is, where its colour is taken. */
    static final float DUMPSTER_SIDE = 0.95f;
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
    /** For what hangs on a wall, which wall of its square: it is stood by addHung, where its sprite paints it, or with its foot at raised where that is set. NONE for all else. */
    int wall = WallHung.NONE;
    /** Whether what its frame holds is the picture its sprite paints, and not a mirror's glass. */
    boolean pictured;
    /** Whether its pane is a mirror's glass, which shows the room: see LookingGlass. */
    boolean mirror;
    /** See ownSize. */
    boolean ownSize;
    /** See overEnds. */
    boolean overEnds;
    /** The picture of the mod's own that its frame holds in place of the sprite's painting, or Decor.NONE. */
    int picture = Decor.NONE;
    /** Whether this is a sheet pinned to a wall, a poster, which the mod hangs only to show its own picture of it. */
    boolean pinned;
    /** For a sheaf of notices pinned to a wall, the mod's sheets it hangs in their place, or null: see FurnishingBuilder.addCollage. */
    Decor.Collage collage;
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
    /** For a piece painted in sprites on three squares in a row: the others' names, where they are, and which gives the colour, or -1 for this one. See Outdoors.Piece. */
    String[] row;
    int[] rowX, rowY;
    int rowDyer = -1;
    /** For a piece that stands on the floor and whose pane shows its sprite's painting, how far along the model's y the pane's face is. */
    float paneY;
    /**
     * For a piece whose sprite paints its back, the name of the sprite of the same piece turned the other way, which paints
     * its front: the piece takes its colours and its picture from that sprite as that sprite's piece stands, and is turned
     * as its own (FurnishingBuilder.ready). Null for a piece that takes them from its own.
     */
    String paintedBy;
    /** Whether this is a shop's fitting that ShopFittings stands goods or clothes on while the game paints what it holds. */
    boolean stocked;
    /** Whether this is a shop's glass counter, round whose case ShopFittings lays the glass. */
    boolean glazed;
    /** Whether it stands on the roof of its square where there is one: see Outdoors.Piece. */
    boolean onRoof;
    /** For a piece that comes up through a roof levels down, how many, and how far out its foot reaches: see Outdoors.Piece. */
    int roofBelow;
    float footHalf;
    /** For a glass balustrade, the lengths Outdoors.addGlass lays its panes along, or null. */
    float[] glassLegs;
    /** For a square of a football goal, the goal it is of, or null. See Outdoors.Piece. */
    Leisure.Goal goal;
    /**
     * For what hangs on a wall and is put on a roof as often, the model it is there: the same thing on a post from the
     * roof, its foot on the roof and not raised. Null for a piece that is the same model on a roof.
     */
    Models.Model posted;

    Furniture(IsoSprite sprite, String model, int turns) {
        this(sprite, model, turns, false);
    }

    /** The quarter turns from north of a facing the game gives, which is one of the four ways along the map's axes. */
    static int turnsOf(IsoDirections facing) {
        return facing == IsoDirections.N ? 0 : facing == IsoDirections.E ? 1 : facing == IsoDirections.S ? 2 : 3;
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

    /** Has its pane and its glass show the sprite, their face y along the model's y. */
    Furniture pictured(float y) {
        pictured = true;
        paneY = y;
        return this;
    }

    Furniture hang(int wall, boolean pictured) {
        this.wall = wall;
        this.pictured = pictured;
        return this;
    }

    /** Has its pane be a mirror's glass. */
    Furniture mirror() {
        mirror = true;
        return this;
    }

    /** For what hangs on a wall and stands out from it, a lamp: it is as big as its model, whatever its sprite's outline comes to. */
    Furniture ownSize() {
        ownSize = true;
        return this;
    }

    /** For what hangs on a wall and may be painted across its square's end, half on the next square's length of the wall: a cross. */
    Furniture overEnds() {
        overEnds = true;
        return this;
    }

    /** The glass round a piece, laid by places on its model in metres: a shop's glass counter's case, a cake shop's glass case. */
    static final class Glass {
        private final SpriteGeometry.Mesh mesh;
        private final Furniture piece;
        private final float x, y, foot, red, green, blue;
        private final float[] turned = new float[2];

        /** Laid in a window's panes, still in the wind. x and y are the piece's first square, foot the floor under it. */
        Glass(WorldScene.Frame build, Furniture piece, int x, int y, float foot) {
            mesh = WorldScene.laid.get(WorldScene.PLAIN, WallSides.PANES);
            mesh.sway = 0f;
            mesh.open = build.frame.open + 32f * WorldScene.INNER_PANE;
            this.piece = piece;
            this.x = x;
            this.y = y;
            this.foot = foot;
            red = build.frame.red;
            green = build.frame.green;
            blue = build.frame.blue;
        }

        /** Faces what is laid next the way (modelX, modelY, up) is in the model. */
        void face(float modelX, float modelY, float up) {
            Models.turn(piece.turns, modelX, modelY, turned);
            mesh.face(turned[0], turned[1], up);
        }

        /** A corner of a sheet at (modelX, modelY, modelZ) in the model, (u, v) of its pane. */
        void corner(float modelX, float modelY, float modelZ, float u, float v) {
            Models.turn(piece.turns, modelX, modelY, turned);
            mesh.add(x + piece.centreX + turned[0] * piece.scale, y + piece.centreY + turned[1] * piece.scale, foot + modelZ * piece.scaleUp, u, v, red, green, blue);
        }
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
        int width = grid == null ? 1 : grid.getWidth(), height = grid == null ? 1 : grid.getHeight();
        String groupName = properties.get("GroupName");
        int surface = properties.getSurface();
        // A piece one square across that the game paints on more than one storey, a flag pole, is a sprite a storey: each stands its own length.
        // An organ's squares are each a piece by itself (Leisure.organ), and a gold cross's (Leisure.glory) and a chandelier's (Leisure.chandelier).
        if (name != null && (grid == null || grid.getLevels() == 1 || width == 1 && height == 1 || Leisure.organPiece(name)
                || Leisure.gloryPiece(name) || Leisure.chandelierPiece(name))) {
            IsoDirections way = sprite.getFacing();
            int faces = way == IsoDirections.N ? Outdoors.NORTH : way == IsoDirections.E ? Outdoors.EAST : way == IsoDirections.S ? Outdoors.SOUTH
                    : way == IsoDirections.W ? Outdoors.WEST : Outdoors.NONE;
            Outdoors.Piece outside = Outdoors.of(name, called, groupName, faces, width, height, surface);
            if (outside != null) {
                return outdoors(sprite, outside);
            }
            Outdoors.Piece works = Industry.of(name, called, groupName, faces, width, height, surface);
            if (works != null) {
                return outdoors(sprite, works);
            }
            Outdoors.Piece camp = Camp.of(name, called, groupName, faces, width, height, surface);
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
        if (name != null) {
            Furniture panelling = Indoors.barPanelling(sprite, name);
            if (panelling != null) {
                return panelling;
            }
        }
        if (name != null && called == null && grid == null && name.startsWith(Clinic.OFFICE)) {
            return Clinic.unnamedDesk(sprite, name);
        }
        if (name != null && called == null && grid == null) {
            Furniture cross = Leisure.wallCross(sprite, name);
            if (cross != null) {
                return cross;
            }
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
        String group = groupName == null ? "" : groupName;
        String holds = properties.get("container") == null ? "" : properties.get("container");
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
            int faces = turnsOf(facing);
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
                return new Furniture(sprite, grid == null ? null : "canvas", faces).hang(WallHung.behind(facing), false).mirror();
            }
            // A picture's sprite shows its face only from the two sides the game's view sees.
            if (FRAMED.contains(called) && (facing == IsoDirections.S || facing == IsoDirections.E)) {
                Furniture framed = new Furniture(sprite, grid == null ? null : "canvas", faces).hang(WallHung.behind(facing), true);
                framed.picture = Decor.of(name, called, group, grid != null);
                framed.rail = grid != null && called.startsWith("Painting") ? PAINTED_RAIL : 0f;
                return framed;
            }
            // Notices are sheets pinned over each other, and hang as the mod's sheets only where it has them.
            if (Decor.SHEETS && called.equals("Notices") && grid == null && (facing == IsoDirections.S || facing == IsoDirections.E)) {
                Decor.Collage collage = Decor.collage(name, called, group);
                if (collage != null) {
                    Furniture notices = new Furniture(sprite, "sheet", faces).hang(WallHung.behind(facing), true);
                    notices.collage = collage;
                    notices.pinned = true;
                    return notices;
                }
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
        int turns = turnsOf(facing);
        boolean alongY = (turns & 1) == 0;
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
            // The plain Plastic ones are the moulded armchairs set out on patios; Blue and Grey Plastic are stacking chairs.
            String model = group.equals("Folding") ? "folding_chair" : group.equals("Plastic") ? "garden_chair" : "plastic_chair";
            return new Furniture(sprite, model, turns).dye(1 << (model.equals("folding_chair") ? Models.VINYL : Models.PLASTIC), surfaceHigh).stand(0f, 0f, false);
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
                // These chairs are one colour all over, white or red paint as well as bare wood, so the frame takes the seat's colour too.
                // Green Dinner and Fancy Wooden are wooden-seated as well, but their seats are green and rose on a brown and a black frame.
                boolean oneColour = ONE_COLOUR_CHAIRS.contains(group);
                return new Furniture(sprite, group.contains("Office") ? "office_chair" : "dining_chair", turns)
                        .dye(1 << Models.CLOTH | (oneColour ? 1 << Models.WOOD : 0), surfaceHigh).stand(0f, 0f, false);
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
        int large = Sheets.place(name, LARGE_TABLES);
        if (called.equals("Table") && single && large >= 36 && large <= 47) {
            return largeTable(sprite, large, turns, surfaceHigh, surfaceUp);
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
            if (pair && large >= 44 && large <= 47) {
                model = "patio_table";
            }
            Furniture table = new Furniture(sprite, model, turns).dye(1 << (model.equals("patio_table") ? Models.PLASTIC : Models.WOOD), surfaceHigh);
            return table.stand(surfaceUp, table.model.height(), false);
        }
        // A bank's safe deposit boxes standing in a run along a wall, each square of them meeting the next.
        if (called.equals("Vault") && group.equals("Standing") && single) {
            return new Furniture(sprite, "deposit_boxes", turns).meet(0f, 0f, true);
        }
        // A shop's counter open over two shelves, which the game calls shelves. Its sprite paints the shelves on the
        // other of the two sides the game's view sees from the one it says it faces: facing south it is open to the
        // east, facing east to the south.
        if (called.equals("Shelves") && group.equals("Generic Shop Low") && holds.equals("counter") && single && low && properties.isTable()
                && counterHigh && (facing == IsoDirections.S || facing == IsoDirections.E)) {
            Furniture counter = new Furniture(sprite, "open_counter", facing == IsoDirections.S ? 1 : 2)
                    .dye(1 << Models.WOOD, surfaceHigh * 0.45f).also(1 << Models.WORKTOP, surfaceHigh);
            return counter.meet(surfaceUp, counter.model.height(), true);
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
            // A small rack of wire whose length runs the way it faces.
            if (called.equals("Display") && group.equals("Small Black") && holds.equals("shelves")) {
                return new Furniture(sprite, "counter_rack", turns).stand(0f, 0f, false).raise(surfaceUp);
            }
        }
        if (called.equals("Machine") && single && surface == 0) {
            // The window and the sign over it show the sprite's own, the snack machine's glass or the pop machine's
            // picture. The cabinet's colour is taken below them: taken at the window, the cabinet came out as pale as the glass.
            if (holds.equals("vendingsnack") || holds.equals("vendingpop")) {
                return new Furniture(sprite, "vending_machine", turns).dye(1 << Models.PANE, LOCKER_DYE).also(1 << Models.PAINT, VENDING_CABINET)
                        .pictured(VENDING_GLASS_Y).stand(0f, 0f, true);
            }
            if (group.endsWith("Arcade") && name.startsWith(ARCADES)) {
                return arcade(sprite, name, turns);
            }
            return null;
        }
        // The orange dumpster is a framed box under two arched lids, the blue one a bin under lids sloping to its front.
        // Its colour is taken low on its south side, which the game's view sees whichever way it faces: the middle of
        // the first square is clear in the orange one's sprite facing south, which left it the game's sprite.
        if (called.equals("Dumpster") && holds.equals("dumpster") && pair) {
            Furniture dumpster = new Furniture(sprite, group.equals("Orange") ? "dumpster_domed" : "dumpster_sloped", turns)
                    .dye(1 << Models.PAINT, 0.5f, DUMPSTER_SIDE, LOW_DYE).stand(0f, 0f, false);
            dumpster.dyeY = dumpster.height - 1;
            return dumpster;
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

    /** The sheet of arcade cabinets, four sprites to a cabinet from 16: facing south, east, west and north. */
    static final String ARCADES = "recreational_01_";
    /**
     * Where an arcade cabinet's colours are taken. Its marquee is the sprite's own painting, the face of the model's pane
     * ARCADE_PANE_Y along its y from its middle. Its side panels, the edge of its control panel and its buttons are the
     * colour of the art the sprite paints on the side it shows, ARCADE_ART_ALONG in from the back of the square and
     * ARCADE_ART_OUT across it, ARCADE_ART_HIGH levels up: the green of Dr. Oids, the orange of Kaboom. Whether the sprite
     * paints the cabinet is asked at its middle, as high as its marquee.
     */
    static final float ARCADE_PANE_Y = -0.247f, ARCADE_ART_ALONG = 0.25f, ARCADE_ART_OUT = 0.915f, ARCADE_ART_HIGH = 1.2f / ViewCamera.LEVEL_HEIGHT,
            ARCADE_MARQUEE_HIGH = 1.6f / ViewCamera.LEVEL_HEIGHT;

    /**
     * An arcade cabinet. The game paints one facing north or west from its back, so it takes its marquee and its colours
     * from the sprite of the same cabinet facing south or east, which paints its front and the same art on its side.
     */
    static Furniture arcade(IsoSprite sprite, String name, int turns) {
        int place = Sheets.place(name, ARCADES), way = (place - 16) % 4, first = place - way;
        // The side the sprite shows: east of a cabinet facing south, south of one facing east.
        boolean south = way == 0;
        Furniture cabinet = new Furniture(sprite, "arcade_machine", turns).dye(1 << Models.PANE, ARCADE_MARQUEE_HIGH)
                .alsoAt(1 << Models.PAINT, south ? ARCADE_ART_OUT : ARCADE_ART_ALONG, south ? ARCADE_ART_ALONG : ARCADE_ART_OUT, ARCADE_ART_HIGH)
                .pictured(ARCADE_PANE_Y).stand(0f, 0f, true);
        if (way >= 2) {
            cabinet.paintedBy = ARCADES + (way == 3 ? first : first + 1);
        }
        return cabinet;
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
        piece.row = outside.row;
        piece.rowX = outside.rowX;
        piece.rowY = outside.rowY;
        piece.rowDyer = outside.rowDyer;
        piece.paintedBy = outside.paintedBy;
        piece.pictured = outside.pictured;
        piece.paneY = outside.paneY;
        piece.onRoof = outside.onRoof;
        piece.roofBelow = outside.roofBelow;
        piece.footHalf = outside.footHalf;
        piece.glassLegs = outside.glassLegs;
        piece.goal = outside.goal;
        if (outside.fits) {
            piece.stand(outside.top, piece.model.height(), false);
        }
        if (piece.grid == null) {
            piece.centreX = outside.a;
            piece.centreY = outside.b;
        }
        return piece.raise(outside.raised);
    }

    /**
     * The sheet of large tables, whose two squares the game ties with no grid from 36 to 47: each finds the other, and
     * the first stands the whole table and the second nothing. Each four of them is one table, along x in the first
     * two and along y in the last two, and 44 to 47 are a moulded plastic one, the rest wood.
     */
    private static final String LARGE_TABLES = "furniture_tables_high_01_";

    private static Furniture largeTable(IsoSprite sprite, int place, int turns, float surfaceHigh, float surfaceUp) {
        int block = place - place % 4, at = place % 4;
        boolean first = at == 0 || at == 3, alongX = at < 2;
        int toOther = first ? 1 : -1;
        String other = LARGE_TABLES + (block + (at == 0 ? 1 : at == 1 ? 0 : at == 2 ? 3 : 2));
        boolean plastic = block == 44;
        Furniture table = new Furniture(sprite, first ? plastic ? "patio_table" : "table_2" : null, turns);
        table.partner = other;
        table.partnerX = alongX ? toOther : 0;
        table.partnerY = alongX ? 0 : toOther;
        table.follows = !first;
        if (!first) {
            return table;
        }
        table.dye(1 << (plastic ? Models.PLASTIC : Models.WOOD), surfaceHigh);
        float fits = Math.min((1f - 2f * FURNITURE_CLEAR) / table.model.depth(), (2f - 2f * FURNITURE_CLEAR) / table.model.width());
        table.scale = table.scaleUp = Math.min(surfaceUp / table.model.height(), fits);
        table.centreX = alongX ? 1f : 0.5f;
        table.centreY = alongX ? 0.5f : 1f;
        return table;
    }

    /** The sheet the game's street lamps are on. They carry no name: which lamp a sprite is, is its place on the sheet. */
    private static final String STREET_LAMPS = "lighting_outdoor_01_";
    /** The sheet has a copy of itself with the lamps lit, the same lamps in the same places. */
    private static final String LIT_STREET_LAMPS = STREET_LAMPS + "on_";
    /** Where on its sprite a lamp's post gives its colour, in levels. */
    private static final float POST_DYE = 0.5f, UPPER_POST_DYE = 0.25f;

    /**
     * The model of a street lamp, by which sprite of the game's sheet of them it is. The first
     * four are whole lamps, a post with its light. A lamp taller than a storey is two sprites,
     * and the upper one, on the level above, is the head: an acorn lantern, or two of them on
     * arms out along x and then along y, or the arm of a road lamp, which reaches out north,
     * east, south and west in that order on the sheet, or the two flat heads of a car park's
     * lamp, side by side along y and then along x, or a traffic signal's head. Null for the
     * rest of the sheet: bare posts, which stand well enough as they are, and heads the mod
     * has no model of.
     */
    private static Furniture streetLamp(IsoSprite sprite, String name) {
        int which = Sheets.place(name, name.startsWith(LIT_STREET_LAMPS) ? LIT_STREET_LAMPS : STREET_LAMPS);
        return switch (which) {
            case 0, 2 -> new Furniture(sprite, "cone_lamp", 0).dye(1 << Models.PAINT, POST_DYE);
            case 1 -> new Furniture(sprite, "globe_lamp", 0).dye(1 << Models.PAINT, POST_DYE);
            case 3 -> new Furniture(sprite, "lantern_lamp", 0).dye(1 << Models.PAINT, POST_DYE);
            case 5, 6, 7 -> onPost(new Furniture(sprite, which == 5 ? "acorn_head" : "twin_acorn_head", which == 7 ? 1 : 0).dye(1 << Models.PAINT, UPPER_POST_DYE));
            case 8, 9, 10, 11 -> new Furniture(sprite, "cobra_head", which - 8).dye(1 << Models.PAINT, UPPER_POST_DYE);
            case 18, 19 -> new Furniture(sprite, "twin_head", which - 18).dye(1 << Models.PAINT, UPPER_POST_DYE);
            // A traffic signal's head on the upper length of its post, its arm out west, south, east and north in that order on
            // the sheet, its lights a quarter turn on from its arm.
            case 12, 13, 14, 15 -> onSignalPost(new Furniture(sprite, "traffic_light", new int[] {2, 1, 0, 3}[which - 12]));
            // The same heads with their lights a quarter turn the other way from their arm.
            case 20, 21, 22, 23 -> onSignalPost(new Furniture(sprite, "traffic_light_r", new int[] {0, 3, 2, 1}[which - 20]));
            default -> null;
        };
    }

    /**
     * Where on its square the axis of the post under an acorn lamp's head stands: the game's
     * lower sprite of that post (lighting_outdoor_01_4) is stood by Posts, which reads its foot
     * there, and a head stood in the square's middle met it a hand to one side.
     */
    static final float LAMP_POST_AXIS = 0.53f;

    /** Where on its square the axis of a traffic signal's post stands, read off the game's sprites of its two lengths. */
    static final float SIGNAL_POST_A = 0.54f, SIGNAL_POST_B = 0.58f;

    private static Furniture onSignalPost(Furniture head) {
        head.centreX = SIGNAL_POST_A;
        head.centreY = SIGNAL_POST_B;
        return head;
    }

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
    /** The hard chairs whose sprite paints frame and seat alike. */
    private static final Set<String> ONE_COLOUR_CHAIRS = Set.of("Wooden", "Dark Wooden", "Red Wooden", "White Wooden", "White Simple");
}
