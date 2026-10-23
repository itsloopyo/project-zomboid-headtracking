package com.cameraunlock.zomboid;

/** Tests of which model stands for a sprite of a park, a gym, a track, a church or a music room, and where it stands. */
final class LeisureTests {
    private LeisureTests() {}

    static void run() {
        Checks.run("everyPieceOfPlayNamesAModelTheJarHolds", LeisureTests::everyPieceOfPlayNamesAModelTheJarHolds);
        Checks.run("aSwingsFrameStandsAtTheEndOfItsBeamAndItsLegsSpreadAcrossIt", LeisureTests::aSwingsFrameStandsAtTheEndOfItsBeamAndItsLegsSpreadAcrossIt);
        Checks.run("aSwingsBeamMeetsTheTopOfItsFrame", LeisureTests::aSwingsBeamMeetsTheTopOfItsFrame);
        Checks.run("aTyreStackIsAsHighAsItsSprite", LeisureTests::aTyreStackIsAsHighAsItsSprite);
        Checks.run("aGoalsCrossbarMeetsItsPostsAndARunLiesAlongTheWayItsSpritesDo", LeisureTests::aGoalsCrossbarMeetsItsPostsAndARunLiesAlongTheWayItsSpritesDo);
        Checks.run("aBasketballBoardStandsInFrontOfItsPole", LeisureTests::aBasketballBoardStandsInFrontOfItsPole);
        Checks.run("everyPieceOfAFieldARingAndAGymNamesAModelTheJarHolds", LeisureTests::everyPieceOfAFieldARingAndAGymNamesAModelTheJarHolds);
        Checks.run("aRingsRopesRunFromPostToPostAtTheHeightOfItsCorner", LeisureTests::aRingsRopesRunFromPostToPostAtTheHeightOfItsCorner);
        Checks.run("aGoalpostsCrossbarIsLevelWithTheTopOfItsPost", LeisureTests::aGoalpostsCrossbarIsLevelWithTheTopOfItsPost);
        Checks.run("aPieceLaidOutOtherwiseThanItsModelIsNone", LeisureTests::aPieceLaidOutOtherwiseThanItsModelIsNone);
        Checks.run("aSteeplesSpireStandsOnItsBelfry", LeisureTests::aSteeplesSpireStandsOnItsBelfry);
        Checks.run("aSteepleComesDownOntoARidgeShortOfItsFloor", LeisureTests::aSteepleComesDownOntoARidgeShortOfItsFloor);
        Checks.run("aTowersSpireStandsOnItsFrontSquareAndItsCrossOnItsPoint", LeisureTests::aTowersSpireStandsOnItsFrontSquareAndItsCrossOnItsPoint);
        Checks.run("aPairOfTheatreSeatsFacesTheWayItsSpriteSays", LeisureTests::aPairOfTheatreSeatsFacesTheWayItsSpriteSays);
    }

    private static void holds(Outdoors.Piece piece, String what) {
        Checks.check(piece != null, what);
        if (piece != null) {
            Models.get(piece.model);
        }
    }

    private static Outdoors.Piece unnamed(String name) {
        return Outdoors.of(name, null, null, Outdoors.NONE, 1, 1, 0);
    }

    private static void everyPieceOfPlayNamesAModelTheJarHolds() {
        for (int place : new int[] {0, 1, 2, 3, 4, 10, 11, 12, 13, 14}) {
            String name = "location_community_park_01_" + place;
            holds(Outdoors.of(name, "Swingset", "Playground", place < 5 ? Outdoors.SOUTH : Outdoors.EAST, 1, 1, 0), name + " is a part of a swing");
        }
        for (int place : new int[] {40, 41, 42, 43, 44, 45, 46, 47, 48}) {
            holds(unnamed("location_community_park_01_" + place), "location_community_park_01_" + place + " is a part of a fountain");
        }
        for (int place : new int[] {16, 17, 18, 19, 20, 21, 24, 25, 26, 27, 32, 33, 88, 89, 90, 91, 92, 93}) {
            holds(unnamed("recreational_sports_01_" + place), "recreational_sports_01_" + place + " is a part of a goal, a hoop or a net");
        }
        String[][] grids = {{"Play Castle", "Childrens", "2", "2"}, {"Round", "Merry Go", "2", "2"}, {"Slide", "Playground", "1", "2"},
                {"Hamster Wheel", "Human", "1", "2"}, {"Contraption", "Fitness", "1", "2"}, {"Piano", "Western", "2", "1"}, {"Stool", "Piano", "2", "1"},
                {"Grand Piano", "Black", "2", "3"}, {"Machine", "PAWS Pinball", "1", "2"}, {"Altar", "Decorated", "2", "1"}};
        for (String[] piece : grids) {
            holds(Outdoors.of("sheet_01_0", piece[0], piece[1], Outdoors.SOUTH, Integer.parseInt(piece[2]), Integer.parseInt(piece[3]), 0),
                    piece[0] + " / " + piece[1] + " facing south has a model");
            holds(Outdoors.of("sheet_01_0", piece[0], piece[1], Outdoors.EAST, Integer.parseInt(piece[3]), Integer.parseInt(piece[2]), 0),
                    "and facing east");
        }
        String[][] singles = {{"Jukebox", "Oldies"}, {"Speaker Cabinet", "Wood"}, {"Speaker Cabinet", "Guitar"}, {"Amplifier", "Guitar"},
                {"Stand", "Lectern"}, {"Drum", "Kick"}, {"Drum", "Tom"}, {"Drum", "Snare"}};
        for (String[] piece : singles) {
            holds(Outdoors.of("sheet_01_0", piece[0], piece[1], Outdoors.WEST, 1, 1, 0), piece[0] + " / " + piece[1] + " has a model");
        }
    }

    private static void aSwingsFrameStandsAtTheEndOfItsBeamAndItsLegsSpreadAcrossIt() {
        Outdoors.Piece end = Outdoors.of("location_community_park_01_1", "Swingset", "Playground", Outdoors.SOUTH, 1, 1, 0);
        Outdoors.Piece leg = Outdoors.of("location_community_park_01_0", "Swingset", "Playground", Outdoors.SOUTH, 1, 1, 0);
        Outdoors.Piece seat = Outdoors.of("location_community_park_01_3", "Swingset", "Playground", Outdoors.SOUTH, 1, 1, 0);
        Checks.check(end.a == Leisure.SWING_APEX && end.b == Leisure.SWING_BEAM && seat.b == Leisure.SWING_BEAM && seat.a == 0.5f,
                "facing south the beam runs along x and the frame stands at its east end");
        Checks.check(leg.a == end.a && Math.abs(leg.b - (end.b - 1f)) < 1e-6f, "the other leg's model is placed from the square north of it, under the beam");
        float[] legRuns = new float[2];
        Models.turn(end.turns, 0f, 1f, legRuns);
        Checks.check(legRuns[0] == 0f && legRuns[1] == -1f, "the leg on the beam's square stands out to the north");
        Outdoors.Piece endEast = Outdoors.of("location_community_park_01_13", "Swingset", "Playground", Outdoors.EAST, 1, 1, 0);
        Models.turn(endEast.turns, 0f, 1f, legRuns);
        Checks.check(endEast.a == Leisure.SWING_BEAM && endEast.b == Leisure.SWING_APEX && legRuns[0] == -1f && legRuns[1] == 0f,
                "facing east the same, its x and y swapped");
        Checks.check(end.dyeA == end.a && Math.abs(end.dyeB - 0.48f) < 1e-5f && Math.abs(leg.dyeB - 0.2f) < 1e-5f, "each leg is coloured from half way up itself");
        Checks.check(Outdoors.of("location_community_park_01_5", "Swingset", "Playground", Outdoors.SOUTH, 1, 1, 0) == null, "a place of the sheet that is no part of one");
    }

    private static void aPairOfTheatreSeatsFacesTheWayItsSpriteSays() {
        for (int faces = Outdoors.NORTH; faces <= Outdoors.WEST; faces++) {
            Outdoors.Piece seats = Outdoors.of("location_entertainment_theatre_01_91", "Chair", "Red Theatre", faces, 1, 1, 0);
            holds(seats, "theatre seats facing " + faces + " have a model");
            Checks.check(seats != null && seats.model.equals("theatre_seats") && seats.turns == faces, "theatre seats are turned the way they face");
        }
        Checks.check(Outdoors.of("location_entertainment_theatre_01_91", "Chair", "Red Theatre", Outdoors.NONE, 1, 1, 0) == null,
                "seats facing no way are left to the game");
    }

        private static void aSteeplesSpireStandsOnItsBelfry() {
        Outdoors.Piece belfry = unnamed("location_community_church_small_01_35"), spire = unnamed("location_community_church_small_01_34");
        holds(belfry, "location_community_church_small_01_35 is a steeple's belfry");
        holds(spire, "location_community_church_small_01_34 is a steeple's spire");
        // The spire is on the storey over the belfry's.
        float cap = belfry.raised + Models.get(belfry.model).height(), foot = ViewCamera.LEVEL_HEIGHT + spire.raised;
        Checks.check(Math.abs(cap - foot) < 0.01f, "the spire's foot is on the belfry's cap: " + foot + " on " + cap);
        Checks.check(spire.a == belfry.a && spire.b == belfry.b, "the spire stands over the middle of its belfry");
        Checks.check(unnamed("location_community_church_small_01_33") == null, "a column of the church is no part of its steeple");
    }

    private static void aSteepleComesDownOntoARidgeShortOfItsFloor() {
        Outdoors.Piece belfry = unnamed("location_community_church_small_01_35"), spire = unnamed("location_community_church_small_01_34");
        Checks.check(belfry.roofBelow == 1 && spire.roofBelow == 2 && belfry.footHalf == spire.footHalf,
                "the belfry looks a storey down for its roof, the spire two, both under the belfry's foot");
        // The Muldraugh church at 10735, 9721: the slope off its ridge, two thirds of a storey up at the north edge and a third at the south.
        float[] slope = {2f / 3f, 2f / 3f, 1f / 3f, 1f / 3f};
        float sunk = FurnishingBuilder.sunkOntoRoof(slope, belfry.a, belfry.b, belfry.footHalf);
        float under = 2f / 3f - (belfry.b + belfry.footHalf) / 3f;
        Checks.check(Math.abs(sunk - (1f - under)) < 0.001f, "the belfry comes down to the lowest of the roof under it: " + sunk);
        Checks.check(FurnishingBuilder.sunkOntoRoof(slope, spire.a, spire.b, spire.footHalf) == sunk, "the spire comes down with its belfry");
        Checks.check(FurnishingBuilder.sunkOntoRoof(new float[] {1f, 1f, 1f, 1f}, belfry.a, belfry.b, belfry.footHalf) == 0f,
                "a ridge at the belfry's floor leaves it where it stands");
        Checks.check(FurnishingBuilder.sunkOntoRoof(new float[] {1.2f, 1.2f, 1.1f, 1.1f}, belfry.a, belfry.b, belfry.footHalf) == 0f,
                "a roof higher than the belfry's floor does not raise it");
    }

    private static void aTowersSpireStandsOnItsFrontSquareAndItsCrossOnItsPoint() {
        String sheet = "location_community_church_small_01_";
        int[][] kinds = {{179, 180, 181}, {187, 188, 189}, {195, 196, 197}};
        for (int[] kind : kinds) {
            Outdoors.Piece front = unnamed(sheet + kind[0]), west = unnamed(sheet + kind[1]), north = unnamed(sheet + kind[2]);
            holds(front, sheet + kind[0] + " is a tower's spire");
            Checks.check(west != null && north != null && west.follows && north.follows && !front.follows, "the spire's other pieces stand nothing of their own");
            if (front == null || west == null || north == null) {
                continue;
            }
            Checks.check(front.a == 0f && front.b == 0f, "the apex is over the front square's north-west corner");
            Checks.check((sheet + kind[1]).equals(front.partner) && front.partnerX == -1 && front.partnerY == 0, "the front square stands the spire only beside its west piece");
            Checks.check((sheet + kind[0]).equals(west.partner) && west.partnerX == 1 && west.partnerY == 0, kind[1] + " is west of the front square");
            Checks.check((sheet + kind[0]).equals(north.partner) && north.partnerX == 0 && north.partnerY == 1, kind[2] + " is north of the front square");
            Models.Model spire = Models.get(front.model);
            Checks.check(Math.abs(spire.width() - 2f) < 0.1f && Math.abs(spire.depth() - 2f) < 0.1f, front.model + " covers the tower's two squares by two: " + spire.width());
        }
        Outdoors.Piece cross = unnamed(sheet + "39"), spire = unnamed(sheet + "187");
        holds(cross, sheet + "39 is the cross on a tower's spire");
        float point = spire.raised + Models.get(spire.model).height(), foot = ViewCamera.LEVEL_HEIGHT + cross.raised;
        // A quarter of a metre under its tip each spire is still wider than the cross's post.
        Checks.check(foot < point && point - foot < 0.25f, "the cross's foot is in the spire's point: " + foot + " under " + point);
        Checks.check(cross.a == spire.a && cross.b == spire.b, "the cross stands over the spire's apex");
        Checks.check(unnamed(sheet + "176") == null && unnamed(sheet + "192") == null, "a long run of the spires' sheet is no tower's spire");
    }

    private static void aSwingsBeamMeetsTheTopOfItsFrame() {
        Outdoors.Piece beam = Outdoors.of("location_community_park_01_4", "Swingset", "Playground", Outdoors.SOUTH, 1, 1, 0);
        Outdoors.Piece seat = Outdoors.of("location_community_park_01_2", "Swingset", "Playground", Outdoors.SOUTH, 1, 1, 0);
        float beamTop = beam.raised + Models.get(beam.model).height(), seatTop = seat.raised + Models.get(seat.model).height();
        Checks.check(Math.abs(beamTop - seatTop) < 0.005f, "a length with a seat is as high as one without: " + beamTop + ", " + seatTop);
        float apex = Models.get("swing_end").height();
        Checks.check(beamTop > apex - 0.03f && beamTop < apex, "the beam's top is just under the cap of the frame: " + beamTop + " under " + apex);
        Checks.check(Math.abs(beam.dyeHigh - seat.dyeHigh) < 1e-5f, "both are coloured from the beam");
    }

    private static void aTyreStackIsAsHighAsItsSprite() {
        int[][] high = {{136, 4}, {137, 4}, {138, 4}, {139, 3}, {140, 3}, {141, 3}, {144, 4}, {149, 3}, {152, 1}, {153, 2}, {154, 2}, {155, 2}, {160, 1}, {163, 2}};
        for (int[] stack : high) {
            Outdoors.Piece piece = unnamed("recreational_sports_01_" + stack[0]);
            holds(piece, "recreational_sports_01_" + stack[0] + " is a stack of tyres");
            float tall = Models.get(piece.model).height();
            Checks.check(Math.abs(tall - 0.205f * stack[1]) < 0.01f, "recreational_sports_01_" + stack[0] + " is " + stack[1] + " tyres high, not " + tall);
        }
        for (int place : new int[] {142, 143, 150, 151, 156, 157, 158, 159, 164}) {
            Checks.check(unnamed("recreational_sports_01_" + place) == null, "recreational_sports_01_" + place + " is no stack");
        }
    }

    private static void aGoalsCrossbarMeetsItsPostsAndARunLiesAlongTheWayItsSpritesDo() {
        Outdoors.Piece post = unnamed("recreational_sports_01_19"), bar = unnamed("recreational_sports_01_20");
        float barTop = bar.raised + Models.get(bar.model).height(), postTop = Models.get(post.model).height();
        Checks.check(Math.abs(barTop - postTop) < 0.005f, "a goal's crossbar is as high as the top of its post: " + barTop + ", " + postTop);
        float[] along = new float[2];
        Models.turn(post.turns, 1f, 0f, along);
        Checks.check(along[0] == 1f && along[1] == 0f && post.b == Leisure.GOAL_ACROSS, "recreational_sports_01_19 runs along x");
        Outdoors.Piece net = unnamed("recreational_sports_01_92");
        Models.turn(net.turns, 1f, 0f, along);
        Checks.check(along[0] == 0f && along[1] == 1f && net.a == Leisure.NET_ACROSS && net.raised == 0f, "recreational_sports_01_92 runs along y, on the ground");
    }

    private static void aBasketballBoardStandsInFrontOfItsPole() {
        int[] faces = {Outdoors.SOUTH, Outdoors.EAST, Outdoors.NORTH, Outdoors.WEST};
        for (int i = 0; i < 4; i++) {
            Outdoors.Piece board = unnamed("recreational_sports_01_" + (24 + i));
            float[] front = new float[2];
            Models.turn(board.turns, 0f, -1f, front);
            Checks.check(board.turns == faces[i] && Math.abs(board.a - 0.5f - front[0] * Leisure.BOARD_AHEAD) < 1e-6f
                    && Math.abs(board.b - 0.5f - front[1] * Leisure.BOARD_AHEAD) < 1e-6f, "board " + (24 + i) + " stands out from the pole the way it faces");
        }
        Outdoors.Piece pole = unnamed("recreational_sports_01_32");
        Checks.check(pole.a == 0.5f && pole.b == 0.5f && Models.get(pole.model).height() > ViewCamera.LEVEL_HEIGHT,
                "the pole is in the middle of its square and reaches up behind the board a storey up");
    }

    private static void everyPieceOfAFieldARingAndAGymNamesAModelTheJarHolds() {
        for (int place : new int[] {8, 9, 10, 11, 12, 80, 81, 82, 83, 84, 85, 86, 87}) {
            holds(unnamed("recreational_sports_01_" + place), "recreational_sports_01_" + place + " is a part of something on a field or in a ring");
        }
        holds(Outdoors.of("recreational_sports_01_48", "Golf Put", null, Outdoors.NONE, 1, 1, 0), "the golf hole's flag has a model");
        Outdoors.Piece twice = unnamed("recreational_sports_01_95");
        Checks.check(twice != null && twice.model == null && twice.follows, "the ring's back post the game paints a second time stands nothing");
    }

    private static void aRingsRopesRunFromPostToPostAtTheHeightOfItsCorner() {
        float[] along = new float[2];
        for (int place : new int[] {80, 81, 82, 83}) {
            Outdoors.Piece ropes = unnamed("recreational_sports_01_" + place);
            Models.turn(ropes.turns, 1f, 0f, along);
            boolean alongX = place % 2 == 0;
            float through = alongX ? ropes.b : ropes.a, middle = alongX ? ropes.a : ropes.b;
            Checks.check(along[alongX ? 0 : 1] == 1f && through == Leisure.RING_POST && middle == 0.5f + Leisure.RING_POST,
                    "recreational_sports_01_" + place + " runs along " + (alongX ? "x" : "y") + " through the posts, from one square's post to the next");
            Checks.check(Math.abs(ropes.raised - Leisure.RING_ROPES_FOOT) < 1e-5f, "and its lowest rope is as high as the corner's");
        }
        float ropesTop = Leisure.RING_ROPES_FOOT + Models.get("ring_ropes").height(), cornerTop = Models.get("ring_corner").height();
        Checks.check(ropesTop < cornerTop && ropesTop > cornerTop - 0.25f, "the top rope runs just under the top of a post: " + ropesTop + " under " + cornerTop);
    }

    private static void aGoalpostsCrossbarIsLevelWithTheTopOfItsPost() {
        Outdoors.Piece post = unnamed("recreational_sports_01_8"), bar = unnamed("recreational_sports_01_10");
        float barMiddle = ViewCamera.LEVEL_HEIGHT + bar.raised + Models.get(bar.model).height() / 2f, postTop = Models.get(post.model).height();
        Checks.check(Math.abs(barMiddle - postTop) < 0.005f, "a goalpost's crossbar, a level up, goes through the top of its post: " + barMiddle + ", " + postTop);
        for (int place : new int[] {9, 11, 12}) {
            Outdoors.Piece piece = unnamed("recreational_sports_01_" + place);
            Checks.check(piece.raised == bar.raised && piece.turns == bar.turns && piece.a == bar.a, "recreational_sports_01_" + place + " is on the line of the crossbar");
        }
    }

    private static void aPieceLaidOutOtherwiseThanItsModelIsNone() {
        Checks.check(Outdoors.of("sheet_01_0", "Piano", "Western", Outdoors.SOUTH, 1, 2, 0) == null, "a piano two squares along the way it faces");
        Checks.check(Outdoors.of("sheet_01_0", "Slide", "Playground", Outdoors.SOUTH, 2, 1, 0) == null, "a slide two squares across");
        Checks.check(Outdoors.of("sheet_01_0", "Drum", "Cymbal", Outdoors.SOUTH, 1, 1, 0) == null, "a drum the mod has no model of");
        Checks.check(Outdoors.of("sheet_01_0", "Jukebox", "Oldies", Outdoors.NONE, 1, 1, 0) == null, "a jukebox with no front");
        Checks.check(unnamed("location_community_park_01_49") == null && unnamed("recreational_sports_01_34") == null, "what else is on the sheets");
    }
}
