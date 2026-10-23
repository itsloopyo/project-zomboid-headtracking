package com.cameraunlock.zomboid;

/**
 * What a park, a gym, a track, a church and a music room have that the mod has a model of: swings,
 * slides, a play house, a roundabout and a fountain, running machines and weight benches, walls of
 * old tyres, basketball hoops, nets across courts, football goals, what a playing field, a boxing ring and a gym have,
 * pianos and their benches,
 * speakers, an amplifier, drums, a jukebox, a pinball table, a stage's microphone, theatre seats, a lectern, an altar
 * and a steeple. Asked by
 * Outdoors.of before its own table, and stood the same way, so a piece of either is one kind of thing.
 *
 * Where each model stands on its squares was read off the game's sprites of it, laid over them in
 * the game's own view: a swing's frame stands at the end of its beam and not in the middle of its
 * square, and a basketball's board a little in front of its pole.
 */
final class Leisure {
    private Leisure() {}

    private static final String PARK = "location_community_park_01_", SPORTS = "recreational_sports_01_",
            CHURCH = "location_community_church_small_01_";
    private static final int PAINT = 1 << Models.PAINT, METAL = 1 << Models.METAL;
    private static final float LEVEL = ViewCamera.LEVEL_HEIGHT;

    /** Where a swing's beam runs across its squares, and where along it the frame's apex stands, both from the square's corner. */
    static final float SWING_BEAM = 0.84f, SWING_APEX = 0.88f;
    /** How high, in metres, the foot of a swing's beam and of its seats is: the models are lowered onto the floor when they are made. */
    static final float SWING_BEAM_FOOT = 2.185f, SWING_SEAT_FOOT = 0.44f;
    /** How far across from under the beam a leg of a swing's frame is half way up. */
    private static final float SWING_LEG_HALF = 0.36f;
    /** How high a swing's beam is painted on its sprite, and a leg of its frame, in levels. */
    private static final float SWING_BEAM_DYE = 0.91f, SWING_LEG_DYE = 0.45f;
    /** How far in front of its pole a basketball's board stands. */
    static final float BOARD_AHEAD = 0.12f;

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
        if (called.equals("Jukebox")) {
            return new Outdoors.Piece("jukebox", faces);
        }
        if (called.equals("Speaker Cabinet") && (kind.equals("Wood") || kind.equals("Guitar"))) {
            return new Outdoors.Piece(kind.equals("Wood") ? "speaker_tall" : "speaker_low", faces);
        }
        if (called.equals("Amplifier") && kind.equals("Guitar")) {
            return new Outdoors.Piece("amplifier", faces);
        }
        if (called.equals("Stand") && kind.equals("Lectern")) {
            return new Outdoors.Piece("lectern", faces);
        }
        if (called.equals("Drum")) {
            return kind.equals("Kick") ? new Outdoors.Piece("drum_kick", faces)
                    : kind.equals("Tom") || kind.equals("Snare") ? new Outdoors.Piece("drum_stand", faces) : null;
        }
        return null;
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

    /** Whether a sprite is a piece of a church tower's spire, which the game marks as a roof's. */
    static boolean spirePiece(String name) {
        return spire(Sheets.place(name, CHURCH)) != null;
    }

    /**
     * What has no name: a park fountain's basin and the fountain in it, a basketball's pole and board, a court's net, a goal, a
     * track's stacks of tyres, what a playing field, a boxing ring and a gym have (see field), a church's steeple and the spire
     * on its tower and the cross on that.
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
            case 19 -> court("goal_first", true, GOAL_ACROSS);
            case 20 -> court("goal_middle", true, GOAL_ACROSS).raise(GOAL_BAR_FOOT / LEVEL);
            case 21 -> court("goal_last", true, GOAL_ACROSS);
            case 18 -> court("goal_first", false, GOAL_ACROSS);
            case 17 -> court("goal_middle", false, GOAL_ACROSS).raise(GOAL_BAR_FOOT / LEVEL);
            case 16 -> court("goal_last", false, GOAL_ACROSS);
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
