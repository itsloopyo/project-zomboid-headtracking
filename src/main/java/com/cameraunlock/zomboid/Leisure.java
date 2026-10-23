package com.cameraunlock.zomboid;

import java.util.concurrent.ConcurrentHashMap;
import zombie.iso.IsoDirections;
import zombie.iso.sprite.IsoSprite;

/**
 * What a park, a gym, a track, a church and a music room have that the mod has a model of: swings,
 * slides, a play house, a roundabout and a fountain, running machines and weight benches, walls of
 * old tyres, basketball hoops, nets across courts, football goals, what a playing field, a boxing ring and a gym have,
 * pianos and their benches,
 * speakers, an amplifier, drums, a jukebox, a pinball table, the lamp over a pool table, a stage's microphone, theatre
 * seats, a lectern, an altar, a church's long panelled desk, a pipe organ, a crystal chandelier, a steeple and a television
 * studio's camera. Asked by
 * Outdoors.of before its own table, and stood the same way, so a piece of either is one kind of thing.
 *
 * Where each model stands on its squares was read off the game's sprites of it, laid over them in
 * the game's own view: a swing's frame stands at the end of its beam and not in the middle of its
 * square, and a basketball's board a little in front of its pole.
 */
final class Leisure {
    private Leisure() {}

    private static final String PARK = "location_community_park_01_", SPORTS = "recreational_sports_01_",
            CHURCH = "location_community_church_small_01_", CHANDELIERS = "lighting_indoor_03_";
    private static final int PAINT = 1 << Models.PAINT, METAL = 1 << Models.METAL, SHADE = 1 << Models.SHADE, WOOD = 1 << Models.WOOD,
            DARK_WOOD = 1 << Models.DARK_WOOD;
    private static final float LEVEL = ViewCamera.LEVEL_HEIGHT;

    /** Where a swing's beam runs across its squares, and where along it the frame's apex stands, both from the square's corner. */
    static final float SWING_BEAM = 0.84f, SWING_APEX = 0.88f;
    /** How high, in metres, the foot of a swing's beam and of its seats is: the models are lowered onto the floor when they are made. */
    static final float SWING_BEAM_FOOT = 2.185f, SWING_SEAT_FOOT = 0.44f;
    /** How far across from under the beam a leg of a swing's frame is half way up. */
    private static final float SWING_LEG_HALF = 0.36f;
    /** How high a swing's beam is painted on its sprite, and a leg of its frame, in levels. */
    private static final float SWING_BEAM_DYE = 0.91f, SWING_LEG_DYE = 0.45f;
    /** How high the fringe under a pool table's lamp hangs, in metres, and where its sprite paints a green shade, in levels. */
    static final float POOL_LAMP_FOOT = 1.545f, POOL_SHADE_DYE = 0.68f;
    /** How far in front of its pole a basketball's board stands. */
    static final float BOARD_AHEAD = 0.12f;
    /** How high a studio camera's sprite paints the top of its body, in levels, and how far behind the square's middle. */
    static final float CAMERA_BODY_DYE = 0.633f, CAMERA_BODY_BACK = 0.1f;
    /**
     * How high a lectern's sprite paints its post and its desk, in levels, and how far west and south of the square's middle the
     * left face of its post is painted where that face is the post's broad one and where it is its narrow one.
     */
    static final float LECTERN_POST_DYE = 0.2f, LECTERN_DESK_DYE = 0.43f, LECTERN_BROAD_LEFT = 0.03f, LECTERN_NARROW_LEFT = 0.105f;
    /**
     * Where a church's long desk takes its colours, its top and the front of its body: how far across the square and how high each
     * is, in levels, and how far along the square of each of its three sprites. Those are places where all three paint the same
     * two colours, so the shares of one desk are one colour where they meet.
     */
    static final float PULPIT_DYE_B = 0.65f, PULPIT_TOP_DYE = 0.39f, PULPIT_BODY_DYE = 0.2f, PULPIT_WEST_DYE = 0.705f, PULPIT_MIDDLE_DYE = 0.115f,
            PULPIT_EAST_DYE = 0.05f;

    static Outdoors.Piece of(String name, String called, String group, int faces, int width, int height) {
        boolean single = width == 1 && height == 1;
        boolean alongY = faces == Outdoors.NORTH || faces == Outdoors.SOUTH;
        int along = alongY ? height : width, across = alongY ? width : height;
        if (called == null) {
            return single ? unnamed(name) : null;
        }
        String kind = group == null ? "" : group;
        if (called.equals("Swingset") && kind.equals("Playground") && single) {
            return swing(Sheets.place(name, PARK));
        }
        // The flag in a golf hole, which the game names as a thing that can be carried off.
        if (called.equals("Golf Put") && single) {
            return new Outdoors.Piece("golf_flag", Outdoors.SOUTH).at(0.48f, 0.48f);
        }
        if (faces == Outdoors.NONE) {
            return null;
        }
        if (called.equals("Organ") && kind.equals("Church")) {
            return organ(Sheets.place(name, CHURCH));
        }
        if (called.equals("Chandelier") && kind.equals("Grey") && single) {
            return chandelier(Sheets.place(name, CHANDELIERS));
        }
        if (called.equals("Play Castle") && kind.equals("Childrens") && width == 2 && height == 2) {
            return new Outdoors.Piece("play_castle", faces).dye(PAINT, 0.05f, 0.05f, 0.28f);
        }
        if (called.equals("Round") && kind.equals("Merry Go") && width == 2 && height == 2) {
            return new Outdoors.Piece("merry_go_round", faces).dye(PAINT, 0.85f, 0.85f, 0.14f);
        }
        if (called.equals("Slide") && kind.equals("Playground") && along == 2 && across == 1) {
            return new Outdoors.Piece("slide", faces);
        }
        if (called.equals("Hamster Wheel") && kind.equals("Human") && along == 2 && across == 1) {
            return new Outdoors.Piece("treadmill", faces);
        }
        if (called.equals("Contraption") && kind.equals("Fitness") && along == 2 && across == 1) {
            return new Outdoors.Piece("weight_bench", faces);
        }
        if (called.equals("Piano") && kind.equals("Western") && along == 1 && across == 2) {
            return new Outdoors.Piece("upright_piano", faces);
        }
        if (called.equals("Stool") && kind.equals("Piano") && along == 1 && across == 2) {
            return new Outdoors.Piece("piano_bench", faces);
        }
        if (called.equals("Grand Piano") && along == 3 && across == 2) {
            return new Outdoors.Piece("grand_piano", faces);
        }
        if (called.equals("Machine") && kind.equals("PAWS Pinball") && along == 2 && across == 1) {
            // The green of its sides, where the sprite of its head square paints it for each way it faces.
            Outdoors.Piece pinball = new Outdoors.Piece("pinball", faces);
            return faces == Outdoors.EAST ? pinball.dye(PAINT, 0.05f, 0.7f, 0.27f) : pinball.dye(PAINT, 0.1f, 0.1f, 0.23f);
        }
        if (called.equals("Lamp") && kind.equals("Fancy Green") && along == 1 && across == 2) {
            return new Outdoors.Piece("pool_lamp", faces).raise(POOL_LAMP_FOOT / LEVEL).dye(SHADE, POOL_SHADE_DYE);
        }
        if (called.equals("Altar") && along == 1 && across == 2) {
            return new Outdoors.Piece("altar", faces);
        }
        if (!single) {
            return null;
        }
        if (called.equals("Chair") && kind.equals("Red Theatre")) {
            return new Outdoors.Piece("theatre_seats", faces);
        }
        if (called.equals("Microphone") && kind.equals("Standing")) {
            return new Outdoors.Piece("stage_microphone", faces);
        }
        if (called.equals("HK533p") && kind.equals("Imekagi")) {
            return studioCamera(faces);
        }
        if (called.equals("Jukebox")) {
            return new Outdoors.Piece("jukebox", faces);
        }
        if (called.equals("Speaker Cabinet") && (kind.equals("Wood") || kind.equals("Guitar"))) {
            return new Outdoors.Piece(kind.equals("Wood") ? "speaker_tall" : "speaker_low", faces);
        }
        if (called.equals("Amplifier") && kind.equals("Guitar")) {
            return new Outdoors.Piece("amplifier", faces);
        }
        // The bench in front of an organ, which the game names a stool.
        if (called.equals("Stool") && kind.equals("Organs")) {
            return new Outdoors.Piece("piano_bench", faces);
        }
        if (called.equals("Stand") && kind.equals("Lectern")) {
            return lectern(faces);
        }
        if (called.equals("Drum")) {
            return kind.equals("Kick") ? new Outdoors.Piece("drum_kick", faces)
                    : kind.equals("Tom") || kind.equals("Snare") ? new Outdoors.Piece("drum_stand", faces) : null;
        }
        return null;
    }

    /**
     * A lectern, its post dyed from the face of it the sprite paints lit, on the left, and its desk from the desk over the same
     * place: the post's broad face where it faces south or north, its narrow one where it faces east or west.
     */
    private static Outdoors.Piece lectern(int faces) {
        float across = faces == Outdoors.SOUTH || faces == Outdoors.NORTH ? LECTERN_BROAD_LEFT : LECTERN_NARROW_LEFT;
        return new Outdoors.Piece("lectern", faces).dye(DARK_WOOD, 0.5f - across, 0.5f + across, LECTERN_POST_DYE).also(WOOD, LECTERN_DESK_DYE);
    }

    /**
     * A square of a church's long desk, its top taking its colour from where the sprite paints the top over the front of its body,
     * a along the square, and its body from the front of the body under that.
     */
    private static Outdoors.Piece pulpit(String model, float a) {
        return new Outdoors.Piece(model, Outdoors.SOUTH).dye(WOOD, a, PULPIT_DYE_B, PULPIT_TOP_DYE).also(DARK_WOOD, PULPIT_BODY_DYE);
    }

    /** A studio camera on its tripod, its body dyed from the top of the body its sprite paints behind the tripod. */
    private static Outdoors.Piece studioCamera(int faces) {
        float a = 0.5f, b = 0.5f;
        switch (faces) {
            case Outdoors.NORTH -> b += CAMERA_BODY_BACK;
            case Outdoors.EAST -> a -= CAMERA_BODY_BACK;
            case Outdoors.SOUTH -> b -= CAMERA_BODY_BACK;
            default -> a += CAMERA_BODY_BACK;
        }
        return new Outdoors.Piece("studio_camera", faces).dye(PAINT, a, b, CAMERA_BODY_DYE);
    }

    /**
     * One square of a swing. The frame's end on the square of the beam, its other leg on the square
     * beside, a length of beam with or without a seat: each stands where its sprite paints it, the
     * beam a little inside the square's far edge and the frame at the square's far end along it.
     */
    private static Outdoors.Piece swing(int place) {
        boolean south = place >= 0 && place <= 4;
        int faces = south ? Outdoors.SOUTH : Outdoors.EAST;
        String model;
        float along, out;
        switch (place) {
            case 1, 13 -> {
                model = "swing_end";
                along = SWING_APEX;
                out = SWING_BEAM;
            }
            case 0, 14 -> {
                model = "swing_leg";
                along = SWING_APEX;
                out = SWING_BEAM - 1f;
            }
            case 3 -> {
                model = "swing_near";
                along = 0.5f;
                out = SWING_BEAM;
            }
            case 2 -> {
                model = "swing_far";
                along = 0.5f;
                out = SWING_BEAM;
            }
            case 11 -> {
                model = "swing_near_e";
                along = 0.5f;
                out = SWING_BEAM;
            }
            case 12 -> {
                model = "swing_far_e";
                along = 0.5f;
                out = SWING_BEAM;
            }
            case 4, 10 -> {
                model = "swing_beam";
                along = 0.5f;
                out = SWING_BEAM;
            }
            default -> {
                return null;
            }
        }
        float a = south ? along : out, b = south ? out : along;
        Outdoors.Piece piece = new Outdoors.Piece(model, faces).at(a, b);
        if (model.equals("swing_end") || model.equals("swing_leg")) {
            // Half way up the leg, which leans from its foot to the apex over the beam.
            float legAt = model.equals("swing_end") ? SWING_BEAM - SWING_LEG_HALF : SWING_BEAM - 1f + SWING_LEG_HALF;
            return piece.dye(PAINT, south ? a : legAt, south ? legAt : b, SWING_LEG_DYE);
        }
        float foot = model.equals("swing_beam") ? SWING_BEAM_FOOT : SWING_SEAT_FOOT;
        return piece.dye(PAINT, a, b, SWING_BEAM_DYE - foot / LEVEL).raise(foot / LEVEL);
    }

    /** How far, in levels, the foot of a steeple's spire is below the floor of its storey: on the belfry's cap, which reaches up into it. */
    static final float SPIRE_SUNK = 0.1f;
    /** Where the middle of a steeple stands on its square, a little to the west and south of the square's middle where its sprites paint it. */
    private static final float STEEPLE_A = 0.475f, STEEPLE_B = 0.525f;
    /** How far out from its middle a steeple's belfry reaches, in tiles: its boarded box. */
    private static final float BELFRY_HALF = 0.47f;

    /** Where on the square of a tower spire's front a sprite gives the colour of its slate, and how high on each kind, in levels. */
    private static final float SLATE_A = 0.3f, SLATE_B = 0.5f, SLATE_HIGH = 0.557f, SLATE_HIGH_FLARED = 0.302f;
    /** How far, in levels, the foot of the cross on a tower's spire is below the floor of its storey: down in the spire's point. */
    static final float CROSS_SUNK = 0.033f;

    /**
     * The spire on a church's tower of two squares by two. The game paints the part of it over each square and leaves out
     * the square behind: the apex's piece on the front square, whose north-west corner the apex is over, and a piece each
     * on the squares west and north of it. The front square stands the whole spire; the others stand nothing, and none
     * does unless the square the pairing names holds its piece, so a sheet's pieces laid as a roof of another shape stay roofing.
     */
    private static Outdoors.Piece spire(int place) {
        return switch (place) {
            case 179, 195 -> new Outdoors.Piece("tower_spire", 0).at(0f, 0f).pairedWith(CHURCH + (place + 1), -1, 0).dye(METAL, SLATE_A, SLATE_B, SLATE_HIGH);
            case 187 -> new Outdoors.Piece("tower_spire_flared", 0).at(0f, 0f).pairedWith(CHURCH + 188, -1, 0).dye(METAL, SLATE_A, SLATE_B, SLATE_HIGH_FLARED);
            case 180, 188, 196 -> following(new Outdoors.Piece(null, 0).pairedWith(CHURCH + (place - 1), 1, 0));
            case 181, 189, 197 -> following(new Outdoors.Piece(null, 0).pairedWith(CHURCH + (place - 2), 0, 1));
            default -> null;
        };
    }

    private static Outdoors.Piece following(Outdoors.Piece piece) {
        piece.follows = true;
        return piece;
    }

    /** Where the middle of an organ's back stands on the square of its west end, against the wall at the square's north edge. */
    static final float ORGAN_A = 1.5f, ORGAN_B = 0.48f;
    /** Where on the square of an organ's west end its sprite gives the colour of its case, and how high its pipes, in levels. */
    private static final float ORGAN_DYE_A = 0.5f, ORGAN_DYE_B = 0.8f, ORGAN_CASE_HIGH = 0.7f, ORGAN_PIPES_HIGH = 0.9f;

    /**
     * A church's pipe organ, three squares along a north wall and two storeys high, facing south. The game paints its lower half
     * over the three squares and its upper half over the same squares a storey up, a grid of both storeys. The west square of the
     * lower half stands the whole organ; the others stand nothing, and none does unless the square its pairing names holds its
     * piece. Each is a piece by itself, so the grid's other storey is not looked for on this one.
     */
    private static Outdoors.Piece organ(int place) {
        return switch (place) {
            case 96 -> new Outdoors.Piece("pipe_organ", Outdoors.SOUTH).each().at(ORGAN_A, ORGAN_B).pairedWith(CHURCH + 98, 2, 0)
                    .dye(1 << Models.WOOD, ORGAN_DYE_A, ORGAN_DYE_B, ORGAN_CASE_HIGH).also(METAL, ORGAN_PIPES_HIGH);
            case 97 -> following(new Outdoors.Piece(null, Outdoors.SOUTH).each().pairedWith(CHURCH + 96, -1, 0));
            case 98 -> following(new Outdoors.Piece(null, Outdoors.SOUTH).each().pairedWith(CHURCH + 96, -2, 0));
            case 88 -> following(new Outdoors.Piece(null, Outdoors.SOUTH).each().pairedWith(CHURCH + 90, 2, 0));
            case 89 -> following(new Outdoors.Piece(null, Outdoors.SOUTH).each().pairedWith(CHURCH + 88, -1, 0));
            case 90 -> following(new Outdoors.Piece(null, Outdoors.SOUTH).each().pairedWith(CHURCH + 88, -2, 0));
            default -> null;
        };
    }

    /**
     * Where a crystal chandelier's rod hangs on the square of the left half of its sprites: at the square's east corner, a little
     * to the west of it and south as the sprites paint it. How high, in metres, the drop under its bowl is over the floor of the
     * bowl's storey, and the drops under its widest tier over the floor of theirs: under it.
     */
    static final float CHANDELIER_A = 0.98f, CHANDELIER_B = 0.02f, CHANDELIER_BOWL_FOOT = 1.2f, CHANDELIER_TIERS_FOOT = -0.138f;
    /**
     * Where on the left half's square, and how high over its floor in metres, the sprites paint gold: the front of the ring round
     * the bowl, and the rim of the widest tier on its left.
     */
    static final float BOWL_GOLD_B = 0.714f, BOWL_GOLD_UP = 2.26f, TIERS_GOLD_B = 0.668f, TIERS_GOLD_UP = 1.208f;

    /**
     * A crystal chandelier, which the game paints in halves on two squares corner to corner, the left half on the square west and
     * south of the right: its bowl on one storey and the tiers over it on the next. The left half of each stands its part, hung
     * where the two squares' corners meet; the right half stands nothing, and neither does unless the square its pairing names
     * holds the other half. A few rooms have a bowl or the tiers alone, so each storey's part stands by itself.
     */
    private static Outdoors.Piece chandelier(int place) {
        return switch (place) {
            case 0 -> new Outdoors.Piece("chandelier_bowl", Outdoors.EAST).at(CHANDELIER_A, CHANDELIER_B).pairedWith(CHANDELIERS + 1, 1, -1)
                    .dye(METAL, CHANDELIER_A, BOWL_GOLD_B, (BOWL_GOLD_UP - CHANDELIER_BOWL_FOOT) / LEVEL).raise(CHANDELIER_BOWL_FOOT / LEVEL);
            case 2 -> new Outdoors.Piece("chandelier_tiers", Outdoors.EAST).at(CHANDELIER_A, CHANDELIER_B).pairedWith(CHANDELIERS + 3, 1, -1)
                    .dye(METAL, CHANDELIER_A, TIERS_GOLD_B, (TIERS_GOLD_UP - CHANDELIER_TIERS_FOOT) / LEVEL).raise(CHANDELIER_TIERS_FOOT / LEVEL);
            case 1, 3 -> following(new Outdoors.Piece(null, Outdoors.EAST).pairedWith(CHANDELIERS + (place - 1), -1, 1));
            default -> null;
        };
    }

    /**
     * A cross hung on a wall, which the game paints with no name, high on a wall or lower down, at its middle or towards an end:
     * on a west wall 22, 36, 38 and 54, on a north one 23, 37 and 55. It hangs where its sprite paints it, as a picture does.
     * Null for any other sprite.
     */
    static Furniture wallCross(IsoSprite sprite, String name) {
        IsoDirections away = switch (Sheets.place(name, CHURCH)) {
            case 22, 36, 38, 54 -> IsoDirections.E;
            case 23, 37, 55 -> IsoDirections.S;
            default -> null;
        };
        return away == null ? null : new Furniture(sprite, "wall_cross", Furniture.turnsOf(away)).hang(WallHung.behind(away), false);
    }

    /** Whether a sprite is a square of a church's pipe organ, whose grid the game lays over two storeys. */
    static boolean organPiece(String name) {
        return organ(Sheets.place(name, CHURCH)) != null;
    }

    /** Whether a sprite is a piece of a church tower's spire, which the game marks as a roof's. */
    static boolean spirePiece(String name) {
        return spire(Sheets.place(name, CHURCH)) != null;
    }

    /**
     * What has no name: a park fountain's basin and the fountain in it, a basketball's pole and board, a court's net, a goal, a
     * track's stacks of tyres, what a playing field, a boxing ring and a gym have (see field), a church's long panelled desk, its
     * steeple and the spire on its tower and the cross on that.
     */
    private static Outdoors.Piece unnamed(String name) {
        int church = Sheets.place(name, CHURCH);
        Outdoors.Piece spire = spire(church);
        if (spire != null) {
            return spire;
        }
        switch (church) {
            case 39 -> {
                return new Outdoors.Piece("tower_cross", 0).at(0f, 0f).raise(-CROSS_SUNK);
            }
            // The belfry over the ridge, its boards white where its sprite paints the box and its metal from the skirt above them.
            case 35 -> {
                return new Outdoors.Piece("steeple_belfry", 0).at(STEEPLE_A, STEEPLE_B)
                        .dye(1 << Models.PAINT, 0.5f, 0.97f, 0.25f).also(1 << Models.METAL, 0.49f).throughRoofBelow(1, BELFRY_HALF);
            }
            // A long panelled desk, its front to the south: the west end, a square between, the east end. Each stands its own share.
            case 93 -> {
                return pulpit("pulpit_west", PULPIT_WEST_DYE);
            }
            case 94 -> {
                return pulpit("pulpit_middle", PULPIT_MIDDLE_DYE);
            }
            case 95 -> {
                return pulpit("pulpit_east", PULPIT_EAST_DYE);
            }
            case 34 -> {
                return new Outdoors.Piece("steeple_spire", 0).at(STEEPLE_A, STEEPLE_B).dye(1 << Models.METAL, 0.55f, 0.6f, 0.2f + SPIRE_SUNK).raise(-SPIRE_SUNK)
                        .throughRoofBelow(2, BELFRY_HALF);
            }
            default -> { }
        }
        int park = Sheets.place(name, PARK);
        String fountain = switch (park) {
            case 45 -> "fountain_0";
            case 42 -> "fountain_1";
            case 41 -> "fountain_2";
            case 40 -> "fountain_3";
            case 43 -> "fountain_4";
            case 47 -> "fountain_6";
            case 46 -> "fountain_7";
            case 44 -> "fountain_middle";
            case 48 -> "fountain_tiers";
            default -> null;
        };
        if (fountain != null) {
            return new Outdoors.Piece(fountain, 0);
        }
        int sports = Sheets.place(name, SPORTS);
        return switch (sports) {
            case 32, 33 -> new Outdoors.Piece("basket_pole", 0).dye(PAINT, 0.5f);
            // The board a storey up over the pole, facing south, east, north and west.
            case 24 -> new Outdoors.Piece("basket_board", Outdoors.SOUTH).at(0.5f, 0.5f + BOARD_AHEAD);
            case 25 -> new Outdoors.Piece("basket_board", Outdoors.EAST).at(0.5f + BOARD_AHEAD, 0.5f);
            case 26 -> new Outdoors.Piece("basket_board", Outdoors.NORTH).at(0.5f, 0.5f - BOARD_AHEAD);
            case 27 -> new Outdoors.Piece("basket_board", Outdoors.WEST).at(0.5f - BOARD_AHEAD, 0.5f);
            // A net across a court and a football goal, a square's length of each at a time, along x and then along y: the first
            // square of a run, those between and the last. The middle of a goal is its crossbar, high over its squares.
            case 88 -> court("net_first", true, NET_ACROSS);
            case 89 -> court("net_middle", true, NET_ACROSS);
            case 90 -> court("net_last", true, NET_ACROSS);
            case 93 -> court("net_first", false, NET_ACROSS);
            case 92 -> court("net_middle", false, NET_ACROSS);
            case 91 -> court("net_last", false, NET_ACROSS);
            case 19 -> goal("goal_first", true, GOAL_FIRST_POST, 1f);
            case 20 -> goal("goal_middle", true, 0f, 1f).raise(GOAL_BAR_FOOT / LEVEL);
            case 21 -> goal("goal_last", true, 0f, GOAL_LAST_POST);
            case 18 -> goal("goal_first", false, GOAL_FIRST_POST, 1f);
            case 17 -> goal("goal_middle", false, 0f, 1f).raise(GOAL_BAR_FOOT / LEVEL);
            case 16 -> goal("goal_last", false, 0f, GOAL_LAST_POST);
            default -> sports >= 136 && sports <= 163 ? tyres(sports) : field(sports, Sheets.place(name, MILITARY));
        };
    }

    /** Where a boxing ring's post stands on its square, and its ropes run, from the square's corner. */
    static final float RING_POST = 0.66f;
    /** How high, in metres, the foot of a ring's lowest rope is: the models are lowered onto the floor when they are made. */
    static final float RING_ROPES_FOOT = 0.728f;
    /** How high the sprites of a ring's ropes paint a blue rope and the red one under it, in levels, along x and along y, and where across. */
    private static final float RING_BLUE_X = 0.53f, RING_BLUE_Y = 0.543f, RING_RED_X = 0.405f, RING_RED_Y = 0.418f, RING_BLUE_A = 0.676f, RING_BLUE_B = 0.816f;
    /** Where across its square a goalpost's crossbar runs, and how high the foot of the crossbar and its ends is over the level the game paints them on, in metres. */
    static final float GOALPOST_ACROSS = 0.52f, GOALPOST_BAR_FOOT = 0.15f;
    /** How high a goalpost's sprites paint it yellow, in levels: the post, its pad, and the crossbar. */
    private static final float GOALPOST_DYE = 0.86f, GOALPOST_PAD_DYE = 0.4f, GOALPOST_BAR_DYE = 0.09f;
    /** Where across its squares a tennis net runs, from the square's corner, and how high the foot of its middle squares is, in metres. */
    static final float TENNIS_ACROSS = 0f, TENNIS_MIDDLE_FOOT = 0.037f;
    /** Where across its squares a show jump runs, and how high the foot of its middle square's poles is, in metres. */
    static final float JUMP_ACROSS = 0.6f, JUMP_MIDDLE_FOOT = 0.545f;
    /** How high the foot of a climbing frame's net, of its rope and of its high beam is over the level the game paints each on, in metres. */
    static final float CLIMB_NET_FOOT = 0.138f, CLIMB_ROPE_FOOT = 0.6f, CLIMB_BEAM_FOOT = 1.59f;
    private static final String MILITARY = "location_military_generic_01_";

    /** What a playing field, a boxing ring and a gym have on their squares, each standing where the game's sprite of it paints it. */
    private static Outdoors.Piece field(int sports, int military) {
        Outdoors.Piece piece = switch (sports) {
            case 80 -> ringRopes("ring_ropes", true);
            case 82 -> ringRopes("ring_ropes_tied", true);
            case 81 -> ringRopes("ring_ropes", false);
            case 83 -> ringRopes("ring_ropes_tied", false);
            case 84 -> new Outdoors.Piece("ring_post", 0).at(RING_POST, RING_POST);
            // A post and the steps up to it, along x and along y, and the back corner, whose post the game paints a second time
            // on the square behind it.
            case 85 -> new Outdoors.Piece("ring_steps", Outdoors.SOUTH).at(1f + RING_POST, RING_POST);
            case 86 -> new Outdoors.Piece("ring_steps", Outdoors.WEST).at(RING_POST, 1f + RING_POST);
            case 87 -> new Outdoors.Piece("ring_corner", Outdoors.SOUTH).at(RING_POST, RING_POST);
            case 95 -> Outdoors.following(new Outdoors.Piece(null, 0));
            // A goalpost along y: its post on the ground, and a level up the crossbar from the end over the post to the other.
            case 8 -> new Outdoors.Piece("goalpost_post", Outdoors.WEST).dye(PAINT, GOALPOST_DYE).also(1 << Models.CLOTH, GOALPOST_PAD_DYE);
            case 12 -> goalpost("goalpost_end_first", 0.63f);
            case 11, 10 -> goalpost("goalpost_bar", 0.5f);
            case 9 -> goalpost("goalpost_end_last", 0.11f);
            case 0 -> new Outdoors.Piece("base_bag", 0).at(0.45f, 0.5f);
            case 1, 3 -> new Outdoors.Piece("home_plate", Outdoors.NORTH);
            case 2, 4 -> new Outdoors.Piece("home_plate", Outdoors.SOUTH);
            case 5 -> new Outdoors.Piece("pitching_rubber", Outdoors.SOUTH).at(0.5f, 0.55f);
            case 6 -> new Outdoors.Piece("pitching_rubber", Outdoors.WEST).at(0.55f, 0.55f);
            case 123 -> court("jump_first", true, JUMP_ACROSS);
            case 124 -> court("jump_middle", true, JUMP_ACROSS).raise(JUMP_MIDDLE_FOOT / LEVEL);
            case 125 -> court("jump_last", true, JUMP_ACROSS);
            case 122 -> court("jump_first", false, JUMP_ACROSS);
            case 121 -> court("jump_middle", false, JUMP_ACROSS).raise(JUMP_MIDDLE_FOOT / LEVEL);
            case 120 -> court("jump_last", false, JUMP_ACROSS);
            case 49 -> court("tennis_net_first", true, TENNIS_ACROSS);
            case 50 -> court("tennis_net_middle", true, TENNIS_ACROSS).raise(TENNIS_MIDDLE_FOOT / LEVEL);
            case 51 -> court("tennis_net_last", true, TENNIS_ACROSS);
            case 54 -> court("tennis_net_first", false, TENNIS_ACROSS);
            case 53 -> court("tennis_net_middle", false, TENNIS_ACROSS).raise(TENNIS_MIDDLE_FOOT / LEVEL);
            case 52 -> court("tennis_net_last", false, TENNIS_ACROSS);
            default -> null;
        };
        if (piece != null) {
            return piece;
        }
        // A climbing frame runs along y from its north post to its south one, a level up and a level down, each square of it
        // the share of the frame the game paints there: the net frame, and the frame with a rope hung from its beam.
        return switch (military) {
            case 59 -> climb("climb_net_north_low", 0f);
            case 58, 57 -> climb("climb_net_low", CLIMB_NET_FOOT);
            case 56 -> climb("climb_net_south_low", 0f);
            case 51 -> climb("climb_net_north_high", 0f);
            case 50, 49 -> climb("climb_net_high", 0f);
            case 48 -> climb("climb_net_south_high", 0f);
            case 63 -> climb("climb_post_north_low", 0f);
            case 62, 61 -> climb("climb_rope_low", CLIMB_ROPE_FOOT);
            case 60 -> climb("climb_post_south_low", 0f);
            case 55 -> climb("climb_beam_north_high", 0f);
            case 54 -> climb("climb_beam_rope_high", 0f);
            case 53 -> climb("climb_beam_high", CLIMB_BEAM_FOOT);
            case 52 -> climb("climb_beam_south_rope_high", 0f);
            default -> null;
        };
    }

    /** A square's length of a ring's ropes along x or along y, the blue ones and the red ones the colours the game paints them. */
    private static Outdoors.Piece ringRopes(String model, boolean alongX) {
        float foot = RING_ROPES_FOOT / LEVEL;
        Outdoors.Piece piece = alongX ? new Outdoors.Piece(model, Outdoors.SOUTH).at(0.5f + RING_POST, RING_POST).dye(PAINT, RING_BLUE_A, RING_POST, RING_BLUE_X - foot)
                .also(1 << Models.VINYL, RING_RED_X - foot)
                : new Outdoors.Piece(model, Outdoors.WEST).at(RING_POST, 0.5f + RING_POST).dye(PAINT, RING_POST, RING_BLUE_B, RING_BLUE_Y - foot)
                .also(1 << Models.VINYL, RING_RED_Y - foot);
        return piece.raise(foot);
    }

    /** A square of a goalpost's crossbar, painted the colour its sprite paints it at b along its square. */
    private static Outdoors.Piece goalpost(String model, float b) {
        float foot = GOALPOST_BAR_FOOT / LEVEL;
        return new Outdoors.Piece(model, Outdoors.WEST).at(GOALPOST_ACROSS, 0.5f).dye(PAINT, GOALPOST_ACROSS, b, GOALPOST_BAR_DYE - foot).raise(foot);
    }

    private static Outdoors.Piece climb(String model, float foot) {
        return new Outdoors.Piece(model, Outdoors.WEST).at(0.5f, 0.5f).raise(foot / LEVEL);
    }

    /** Where across its squares a run of net and of goal stands, from the square's corner. */
    static final float NET_ACROSS = 0.37f, GOAL_ACROSS = 0.57f;
    /** How high the foot of a goal's crossbar is, in metres. */
    static final float GOAL_BAR_FOOT = 2.38f;

    /** A square of a run along x or along y. Its model runs along its own X: turned twice that is the map's x, three times its y. */
    private static Outdoors.Piece court(String model, boolean alongX, float across) {
        return alongX ? new Outdoors.Piece(model, Outdoors.SOUTH).at(0.5f, across) : new Outdoors.Piece(model, Outdoors.WEST).at(across, 0.5f);
    }

    /** Where along their squares a goal's first and last posts stand, from the square's corner, as the models goal_first and goal_last have them. */
    static final float GOAL_FIRST_POST = 0.42f, GOAL_LAST_POST = 0.55f;
    /** How high a goal's crossbar is over the ground, to its middle, in metres. */
    static final float GOAL_TOP = 2.44f;

    /**
     * A square of a football goal: which way the goal runs, the sprite of its first post (its north or west end), and the
     * stretch of the square it spans along the goal, from the square's corner: the whole square, but from or to where a
     * post stands on it.
     */
    record Goal(boolean alongX, String firstPost, float from, float to) {}

    private static Outdoors.Piece goal(String model, boolean alongX, float from, float to) {
        Outdoors.Piece piece = court(model, alongX, GOAL_ACROSS);
        piece.goal = new Goal(alongX, SPORTS + (alongX ? 19 : 18), from, to);
        return piece;
    }

    /** Which side of a goal its net is, across the goal: where the map's x or y is less, where it is more, none, or not known until more of the map is loaded. */
    static final int NET_LESS = -1, NO_NET = 0, NET_MORE = 1, NET_UNKNOWN = 2;
    /**
     * How far a goal looks across the pitch for the goal it faces, in squares: the longest pitch on the map has its goals
     * 74 apart. And how near another goal is when it stands behind this one and not across a pitch: back to back on two
     * pitches side by side the goals are 9 and 11 squares apart, and the shortest pitch has them 18 apart.
     */
    static final int GOAL_REACH = 80, GOAL_BEHIND = 14;
    /** How many squares long a goal can be: the longest on the map spans 7. */
    private static final int GOAL_SQUARES = 10;
    /** The side of its net each goal was found to have, by the square of its first post: see netSide. */
    private static final ConcurrentHashMap<Long, Integer> nets = new ConcurrentHashMap<>();

    /** For a map loaded anew. */
    static void forgetGoals() {
        nets.clear();
    }

    /**
     * Which side of its goal a goal's square on (x, y, z) hangs the net. The game paints a goal the same at either end of
     * a pitch, so the net goes on the side away from the goal it faces: the first goal found looking out both ways across
     * it from its first post. One found nearer than GOAL_BEHIND stands behind it on the next pitch, and the net is on that
     * side. NO_NET where no other goal is within GOAL_REACH, and NET_UNKNOWN while a square that decides it is not loaded.
     * Once found, a goal keeps its side for as long as the map is loaded.
     */
    static int netSide(Towers.Squares squares, int x, int y, int z, Goal goal) {
        int alongX = goal.alongX() ? 1 : 0, alongY = 1 - alongX;
        int postX = 0, postY = 0;
        boolean found = false;
        for (int k = 0; k <= GOAL_SQUARES && !found; k++) {
            Boolean post = squares.holds(x - k * alongX, y - k * alongY, z, goal.firstPost());
            if (post == null) {
                return NET_UNKNOWN;
            }
            if (post) {
                postX = x - k * alongX;
                postY = y - k * alongY;
                found = true;
            }
        }
        if (!found) {
            return NO_NET;
        }
        long key = ((long) postX << 32 | postY & 0xFFFFFFFFL) * 64 + z;
        Integer known = nets.get(key);
        if (known != null) {
            return known;
        }
        int side = NO_NET;
        for (int d = 1; d <= GOAL_REACH; d++) {
            Boolean less = squares.holds(postX - d * alongY, postY - d * alongX, z, goal.firstPost());
            Boolean more = squares.holds(postX + d * alongY, postY + d * alongX, z, goal.firstPost());
            if (less == null || more == null) {
                return NET_UNKNOWN;
            }
            if (less && more) {
                // Two goals as far off either way is no pitch the map has: the goal stands as its frame.
                break;
            }
            if (less || more) {
                boolean behind = d < GOAL_BEHIND;
                side = less == behind ? NET_LESS : NET_MORE;
                break;
            }
        }
        nets.put(key, side);
        return side;
    }

    /**
     * How deep behind its frame a goal's net reaches at the crossbar's height and at the ground, and how far its top falls
     * from the crossbar to the back, in metres. The game paints no net, so these are a full-size goal's box net, inside
     * the 1.57 m the nearest fence behind a goal on the map leaves it (7151, 8404).
     */
    static final float NET_TOP_DEPTH = 0.8f, NET_FOOT_DEPTH = 1.2f, NET_TOP_FALL = 0.25f;
    /** How far behind the frame's line the net hangs off the posts and the crossbar: their radius. How high the middle of a tube along the ground is. */
    private static final float NET_OFF_FRAME = 0.06f, NET_GROUND_TUBE = 0.02f;

    /**
     * The sheets of a goal's square's net on the side netSide gives, into out: four corners each, (x, y, up) in tiles from
     * the square's corner and the ground. The square has the top and the back of the net along its stretch, and where a
     * post stands on it the side from that post. Says how many.
     */
    static int netSheets(Goal goal, int side, float[] out) {
        float frame = GOAL_ACROSS + side * NET_OFF_FRAME, top = GOAL_ACROSS + side * NET_TOP_DEPTH, foot = GOAL_ACROSS + side * NET_FOOT_DEPTH;
        float bar = GOAL_TOP - NET_OFF_FRAME, back = GOAL_TOP - NET_TOP_FALL;
        float from = goal.from(), to = goal.to();
        int count = 0;
        count = corners(goal, out, count, 4, from, frame, bar, to, frame, bar, to, top, back, from, top, back);
        count = corners(goal, out, count, 4, from, top, back, to, top, back, to, foot, 0f, from, foot, 0f);
        for (float end : new float[] {from, to}) {
            if (end > 0f && end < 1f) {
                count = corners(goal, out, count, 4, end, frame, 0f, end, frame, bar, end, top, back, end, foot, 0f);
            }
        }
        return count;
    }

    /**
     * The tubes that hold a goal's square's net out behind its frame, into out: two ends each, as netSheets gives corners.
     * Along its stretch one on the ground at the back and one across the top of the back; where a post stands, one on the
     * ground from the post, one from the top of the post to the top of the back, and one down the back's corner. Says how many.
     */
    static int netTubes(Goal goal, int side, float[] out) {
        float frame = GOAL_ACROSS + side * NET_OFF_FRAME, top = GOAL_ACROSS + side * NET_TOP_DEPTH, foot = GOAL_ACROSS + side * NET_FOOT_DEPTH;
        float bar = GOAL_TOP - NET_OFF_FRAME, back = GOAL_TOP - NET_TOP_FALL, low = NET_GROUND_TUBE;
        float from = goal.from(), to = goal.to();
        int count = 0;
        count = corners(goal, out, count, 2, from, foot, low, to, foot, low);
        count = corners(goal, out, count, 2, from, top, back, to, top, back);
        for (float end : new float[] {from, to}) {
            if (end > 0f && end < 1f) {
                count = corners(goal, out, count, 2, end, GOAL_ACROSS, low, end, foot, low);
                count = corners(goal, out, count, 2, end, frame, bar, end, top, back);
                count = corners(goal, out, count, 2, end, top, back, end, foot, low);
            }
        }
        return count;
    }

    /** Puts the points of one more shape into out after the count there are, each given along the goal, across it and up. */
    private static int corners(Goal goal, float[] out, int count, int points, float... at) {
        for (int i = 0; i < points; i++) {
            int to = (count * points + i) * 3;
            out[to] = goal.alongX() ? at[i * 3] : at[i * 3 + 1];
            out[to + 1] = goal.alongX() ? at[i * 3 + 1] : at[i * 3];
            out[to + 2] = at[i * 3 + 2];
        }
        return count + 1;
    }

    /**
     * A stack of tyres. The sheet has them in red and again in white, eight places apart: four high
     * and three high, each straight, with every other tyre set off and the same turned, then one
     * tyre, two, and two set off either way.
     */
    private static Outdoors.Piece tyres(int place) {
        int in = (place - 136) % 8;
        String model;
        int turns = 0;
        if (place < 152 && in < 6) {
            model = "tyres_" + (in < 3 ? 4 : 3) + (in % 3 == 0 ? "" : "_askew");
            turns = in % 3 == 2 ? 1 : 0;
        } else if (place >= 152 && in < 4) {
            model = in == 0 ? "tyres_1" : in == 1 ? "tyres_2" : "tyres_2_askew";
            turns = in == 3 ? 1 : 0;
        } else {
            return null;
        }
        return new Outdoors.Piece(model, turns).dye(PAINT, 0.72f, 0.72f, 0.04f);
    }
}
