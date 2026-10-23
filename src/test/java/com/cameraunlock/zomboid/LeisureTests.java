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
        Checks.run("aGoalsNetHangsOnTheSideAwayFromTheGoalItFaces", LeisureTests::aGoalsNetHangsOnTheSideAwayFromTheGoalItFaces);
        Checks.run("aGoalsNetRunsFromItsFrameBackToTheGroundAndClosesAtItsPosts", LeisureTests::aGoalsNetRunsFromItsFrameBackToTheGroundAndClosesAtItsPosts);
        Checks.run("aBasketballBoardStandsInFrontOfItsPole", LeisureTests::aBasketballBoardStandsInFrontOfItsPole);
        Checks.run("everyPieceOfAFieldARingAndAGymNamesAModelTheJarHolds", LeisureTests::everyPieceOfAFieldARingAndAGymNamesAModelTheJarHolds);
        Checks.run("aRingsRopesRunFromPostToPostAtTheHeightOfItsCorner", LeisureTests::aRingsRopesRunFromPostToPostAtTheHeightOfItsCorner);
        Checks.run("aGoalpostsCrossbarIsLevelWithTheTopOfItsPost", LeisureTests::aGoalpostsCrossbarIsLevelWithTheTopOfItsPost);
        Checks.run("aClimbingFrameMeetsItselfFromLevelToLevel", LeisureTests::aClimbingFrameMeetsItselfFromLevelToLevel);
        Checks.run("aShowJumpOrANetLiesAlongTheWayItsSpritesDo", LeisureTests::aShowJumpOrANetLiesAlongTheWayItsSpritesDo);
        Checks.run("aPieceLaidOutOtherwiseThanItsModelIsNone", LeisureTests::aPieceLaidOutOtherwiseThanItsModelIsNone);
        Checks.run("aSteeplesSpireStandsOnItsBelfry", LeisureTests::aSteeplesSpireStandsOnItsBelfry);
        Checks.run("aSteepleComesDownOntoARidgeShortOfItsFloor", LeisureTests::aSteepleComesDownOntoARidgeShortOfItsFloor);
        Checks.run("aTowersSpireStandsOnItsFrontSquareAndItsCrossOnItsPoint", LeisureTests::aTowersSpireStandsOnItsFrontSquareAndItsCrossOnItsPoint);
        Checks.run("aPairOfTheatreSeatsFacesTheWayItsSpriteSays", LeisureTests::aPairOfTheatreSeatsFacesTheWayItsSpriteSays);
        Checks.run("aStageMicrophoneFacesTheWayItsSpriteSays", LeisureTests::aStageMicrophoneFacesTheWayItsSpriteSays);
        Checks.run("aStudioCameraFacesTheWayItsSpriteSaysAndIsDyedFromItsBody", LeisureTests::aStudioCameraFacesTheWayItsSpriteSaysAndIsDyedFromItsBody);
        Checks.run("aChurchsLongDeskStandsAShareOnEachSquareThatMeetsTheNext", LeisureTests::aChurchsLongDeskStandsAShareOnEachSquareThatMeetsTheNext);
        Checks.run("aLecternIsDyedFromItsPostsLitFaceAndItsDesk", LeisureTests::aLecternIsDyedFromItsPostsLitFaceAndItsDesk);
        Checks.run("aPoolTablesLampHangsFromTheCeilingOverBothSquares", LeisureTests::aPoolTablesLampHangsFromTheCeilingOverBothSquares);
        Checks.run("anOrgansWestSquareStandsItAgainstItsWallAndTheRestStandNothing", LeisureTests::anOrgansWestSquareStandsItAgainstItsWallAndTheRestStandNothing);
        Checks.run("aChandeliersBowlAndTiersHangFromOneRodWhereTheirHalvesMeet", LeisureTests::aChandeliersBowlAndTiersHangFromOneRodWhereTheirHalvesMeet);
        Checks.run("aCrossOnAChurchWallHangsOnTheWallItsSpriteIsAttachedTo", LeisureTests::aCrossOnAChurchWallHangsOnTheWallItsSpriteIsAttachedTo);
        Checks.run("aChurchsLongDeskAlongYIsTheSameDeskTurnedToFaceEast", LeisureTests::aChurchsLongDeskAlongYIsTheSameDeskTurnedToFaceEast);
        Checks.run("aGoldCrossWithRaysIsStoodByItsLowerWestSquareAgainstItsWall", LeisureTests::aGoldCrossWithRaysIsStoodByItsLowerWestSquareAgainstItsWall);
        Checks.run("aChurchsColouredWindowsAreStainedGlassAndItsClearOnesAreNot", LeisureTests::aChurchsColouredWindowsAreStainedGlassAndItsClearOnesAreNot);
        Checks.run("whatAPitchACourtAndADiamondStandOnIsLevelGround", LeisureTests::whatAPitchACourtAndADiamondStandOnIsLevelGround);
        Checks.run("levelGroundIsFlatInItsBoxAndEasesBackOutToTheLand", LeisureTests::levelGroundIsFlatInItsBoxAndEasesBackOutToTheLand);
        Checks.run("betweenTwoLevelGroundsTheFootingRunsFromOneToTheOtherWithNoStep", LeisureTests::betweenTwoLevelGroundsTheFootingRunsFromOneToTheOtherWithNoStep);
        Checks.run("levelGroundsThatOverlapShareTheLargerOnesFootingInWhateverOrderMet", LeisureTests::levelGroundsThatOverlapShareTheLargerOnesFootingInWhateverOrderMet);
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

    private static void aStageMicrophoneFacesTheWayItsSpriteSays() {
        for (int faces = Outdoors.NORTH; faces <= Outdoors.WEST; faces++) {
            Outdoors.Piece microphone = Outdoors.of("appliances_com_01_" + (68 + faces), "Microphone", "Standing", faces, 1, 1, 0);
            holds(microphone, "a microphone stand facing " + faces + " has a model");
            Checks.check(microphone != null && microphone.model.equals("stage_microphone") && microphone.turns == faces, "it is turned the way it faces");
        }
        Checks.check(Outdoors.of("appliances_com_01_68", "Microphone", "Standing", Outdoors.NONE, 1, 1, 0) == null, "a microphone facing no way is left to the game");
    }

    private static void aStudioCameraFacesTheWayItsSpriteSaysAndIsDyedFromItsBody() {
        int[] sprites = {47, 45, 44, 46};
        for (int faces = Outdoors.NORTH; faces <= Outdoors.WEST; faces++) {
            Outdoors.Piece camera = Outdoors.of("appliances_com_01_" + sprites[faces], "HK533p", "Imekagi", faces, 1, 1, 0);
            holds(camera, "a studio camera facing " + faces + " has a model");
            Checks.check(camera != null && camera.model.equals("studio_camera") && camera.turns == faces, "it is turned the way it faces");
            Checks.check(camera != null && camera.dyed == 1 << Models.PAINT && camera.dyeHigh == Leisure.CAMERA_BODY_DYE, "its body is dyed from the sprite");
            float behindA = faces == Outdoors.EAST ? -1 : faces == Outdoors.WEST ? 1 : 0, behindB = faces == Outdoors.SOUTH ? -1 : faces == Outdoors.NORTH ? 1 : 0;
            Checks.check(camera != null && camera.dyeA == 0.5f + behindA * Leisure.CAMERA_BODY_BACK && camera.dyeB == 0.5f + behindB * Leisure.CAMERA_BODY_BACK,
                    "where the body is painted behind the tripod");
        }
        Checks.check(Outdoors.of("appliances_com_01_44", "HK533p", "Imekagi", Outdoors.NONE, 1, 1, 0) == null, "a camera facing no way is left to the game");
    }

    private static void aChurchsLongDeskStandsAShareOnEachSquareThatMeetsTheNext() {
        String[] models = {"pulpit_west", "pulpit_middle", "pulpit_east"};
        Models.Model[] shares = new Models.Model[3];
        for (int i = 0; i < 3; i++) {
            Outdoors.Piece piece = unnamed("location_community_church_small_01_" + (93 + i));
            holds(piece, "location_community_church_small_01_" + (93 + i) + " is a share of a church's long desk");
            Checks.check(piece != null && piece.model.equals(models[i]) && piece.turns == Outdoors.SOUTH && piece.a == 0.5f && piece.b == 0.5f,
                    "it is " + models[i] + " in the middle of its square, its front to the south");
            Checks.check(piece != null && piece.dyed == 1 << Models.WOOD && piece.alsoDyed == 1 << Models.DARK_WOOD,
                    "its top and its body take their colours from the sprite");
            shares[i] = Models.get(models[i]);
        }
        // Turned to face south a model's x runs east, so each share's east side meets the next one's west side at the square's edge.
        Checks.check(Math.abs(shares[0].maxX - 0.5f) < 0.002f && Math.abs(shares[1].minX + 0.5f) < 0.002f, "the west end meets the square after it");
        Checks.check(Math.abs(shares[1].maxX - 0.5f) < 0.002f && Math.abs(shares[2].minX + 0.5f) < 0.002f, "a square between meets the east end");
        Checks.check(shares[0].minX > -0.5f && shares[2].maxX < 0.5f, "the ends stop short of their squares' far edges, as the sprites paint them");
        Checks.check(shares[0].maxZ == shares[1].maxZ && shares[1].maxZ == shares[2].maxZ, "the top is one height from end to end");
        Checks.check(unnamed("location_community_church_small_01_92") == null && unnamed("location_community_church_small_01_96") == null,
                "the sprites on either side of the desk's are none of it");
    }

    private static void aLecternIsDyedFromItsPostsLitFaceAndItsDesk() {
        for (int faces = Outdoors.NORTH; faces <= Outdoors.WEST; faces++) {
            Outdoors.Piece lectern = Outdoors.of("location_community_church_small_01_44", "Stand", "Lectern", faces, 1, 1, 0);
            holds(lectern, "a lectern facing " + faces + " has a model");
            float across = faces == Outdoors.SOUTH || faces == Outdoors.NORTH ? Leisure.LECTERN_BROAD_LEFT : Leisure.LECTERN_NARROW_LEFT;
            Checks.check(lectern != null && lectern.model.equals("lectern") && lectern.turns == faces, "it is turned the way it faces");
            Checks.check(lectern != null && lectern.dyed == 1 << Models.DARK_WOOD && lectern.dyeA == 0.5f - across && lectern.dyeB == 0.5f + across
                    && lectern.dyeHigh == Leisure.LECTERN_POST_DYE, "its post is dyed from the post's left face");
            Checks.check(lectern != null && lectern.alsoDyed == 1 << Models.WOOD && lectern.alsoHigh == Leisure.LECTERN_DESK_DYE, "its desk from the desk");
        }
    }

    private static void aPoolTablesLampHangsFromTheCeilingOverBothSquares() {
        Outdoors.Piece south = Outdoors.of("recreational_01_84", "Lamp", "Fancy Green", Outdoors.SOUTH, 2, 1, 0);
        Outdoors.Piece east = Outdoors.of("recreational_01_87", "Lamp", "Fancy Green", Outdoors.EAST, 1, 2, 0);
        holds(south, "a pool table's lamp across x has a model");
        holds(east, "and one across y");
        Checks.check(south.model.equals("pool_lamp") && east.model.equals("pool_lamp"), "both are the pool table's lamp");
        Models.Model lamp = Models.get("pool_lamp");
        Checks.check(Math.abs(east.raised + lamp.height() - ViewCamera.LEVEL_HEIGHT) < 0.002f, "its pole meets the ceiling: " + (east.raised + lamp.height()));
        float shade = east.dyeHigh * ViewCamera.LEVEL_HEIGHT;
        Checks.check(shade > east.raised && shade < east.raised + 0.2f, "its colour is taken from a shade, just over its fringe: " + shade);
        Checks.check(Outdoors.of("recreational_01_87", "Lamp", "Fancy Green", Outdoors.EAST, 1, 1, 0) == null, "a lamp of one square is none of it");
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

    /** A map holding the first post of a goal along x on each square given as x, y, and nothing loaded north of loadedFrom. */
    private static Towers.Squares goalsAt(int loadedFrom, int... posts) {
        java.util.Set<Long> at = new java.util.HashSet<>();
        for (int i = 0; i < posts.length; i += 2) {
            at.add((long) posts[i] << 32 | posts[i + 1] & 0xFFFFFFFFL);
        }
        return (x, y, z, sprite) -> y < loadedFrom ? null : sprite.equals("recreational_sports_01_19") && at.contains((long) x << 32 | y & 0xFFFFFFFFL);
    }

    private static void aGoalsNetHangsOnTheSideAwayFromTheGoalItFaces() {
        Leisure.Goal middle = new Leisure.Goal(true, "recreational_sports_01_19", 0f, 1f);
        Leisure.forgetGoals();
        Towers.Squares pitch = goalsAt(Integer.MIN_VALUE, 10, 100, 10, 140);
        Checks.check(Leisure.netSide(pitch, 12, 100, 0, middle) == Leisure.NET_LESS, "the goal at the north end of a pitch has its net north of it");
        Checks.check(Leisure.netSide(pitch, 11, 140, 0, middle) == Leisure.NET_MORE, "the goal at the south end of a pitch has its net south of it");
        Leisure.forgetGoals();
        Towers.Squares sideBySide = goalsAt(Integer.MIN_VALUE, 10, 200, 10, 240, 10, 251, 10, 291);
        Checks.check(Leisure.netSide(sideBySide, 10, 240, 0, middle) == Leisure.NET_MORE && Leisure.netSide(sideBySide, 10, 251, 0, middle) == Leisure.NET_LESS,
                "two goals back to back on two pitches have their nets towards each other");
        Leisure.forgetGoals();
        Checks.check(Leisure.netSide(goalsAt(Integer.MIN_VALUE, 10, 300), 10, 300, 0, middle) == Leisure.NO_NET, "a goal with no other near it stands its frame alone");
        Checks.check(Leisure.netSide(goalsAt(Integer.MIN_VALUE), 10, 300, 0, middle) == Leisure.NO_NET, "a goal's square with no post along it has no net");
        Towers.Squares half = goalsAt(130, 10, 140), whole = goalsAt(Integer.MIN_VALUE, 10, 100, 10, 140);
        Leisure.forgetGoals();
        Checks.check(Leisure.netSide(half, 10, 140, 0, middle) == Leisure.NET_UNKNOWN, "a goal whose pitch is not all loaded has no side yet");
        Checks.check(Leisure.netSide(whole, 10, 140, 0, middle) == Leisure.NET_MORE, "and finds it once the pitch is loaded");
        Checks.check(Leisure.netSide(half, 10, 140, 0, middle) == Leisure.NET_MORE, "and keeps it while the map is loaded");
    }

    private static void aGoalsNetRunsFromItsFrameBackToTheGroundAndClosesAtItsPosts() {
        float[] sheets = new float[48];
        Leisure.Goal middle = new Leisure.Goal(true, "recreational_sports_01_19", 0f, 1f);
        Checks.check(Leisure.netSheets(middle, Leisure.NET_MORE, sheets) == 2, "a goal's middle square has the top and the back of its net");
        float bar = Leisure.GOAL_ACROSS + 0.06f, back = Leisure.GOAL_ACROSS + Leisure.NET_FOOT_DEPTH;
        Checks.check(sheets[0] == 0f && Math.abs(sheets[1] - bar) < 1e-6f && sheets[3] == 1f && Math.abs(sheets[2] - sheets[5]) < 1e-6f,
                "the top of a goal's net runs along it from behind its crossbar");
        Checks.check(Math.abs(sheets[12 + 7] - back) < 1e-6f && sheets[12 + 8] == 0f && Math.abs(sheets[12 + 10] - back) < 1e-6f && sheets[12 + 11] == 0f,
                "the back of a goal's net comes down to the ground behind it");
        Leisure.Goal first = new Leisure.Goal(false, "recreational_sports_01_18", Leisure.GOAL_FIRST_POST, 1f);
        Checks.check(Leisure.netSheets(first, Leisure.NET_LESS, sheets) == 3, "a goal's square with a post has the side of its net too");
        boolean side = true;
        for (int corner = 0; corner < 4; corner++) {
            side &= sheets[24 + corner * 3 + 1] == Leisure.GOAL_FIRST_POST && sheets[24 + corner * 3] <= Leisure.GOAL_ACROSS;
        }
        Checks.check(side && sheets[1] == Leisure.GOAL_FIRST_POST && Math.abs(sheets[12 + 9] - (Leisure.GOAL_ACROSS - Leisure.NET_FOOT_DEPTH)) < 1e-6f,
                "a goal along y with its net west of it closes the net at its post, across x");
        float[] tubes = new float[48];
        Checks.check(Leisure.netTubes(middle, Leisure.NET_MORE, tubes) == 2 && Leisure.netTubes(first, Leisure.NET_LESS, tubes) == 5,
                "a goal's net is held out by tubes along its back and down from each post");
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
        for (int place : new int[] {0, 1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 12, 49, 50, 51, 52, 53, 54, 80, 81, 82, 83, 84, 85, 86, 87, 120, 121, 122, 123, 124, 125}) {
            holds(unnamed("recreational_sports_01_" + place), "recreational_sports_01_" + place + " is a part of something on a field or in a ring");
        }
        for (int place = 48; place <= 63; place++) {
            holds(unnamed("location_military_generic_01_" + place), "location_military_generic_01_" + place + " is a part of a climbing frame");
        }
        holds(Outdoors.of("recreational_sports_01_48", "Golf Put", null, Outdoors.NONE, 1, 1, 0), "the golf hole's flag has a model");
        Outdoors.Piece twice = unnamed("recreational_sports_01_95");
        Checks.check(twice != null && twice.model == null && twice.follows, "the ring's back post the game paints a second time stands nothing");
        Checks.check(unnamed("location_military_generic_01_47") == null && unnamed("location_military_generic_01_64") == null, "what else is on the army's sheet");
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

    private static void aClimbingFrameMeetsItselfFromLevelToLevel() {
        String sheet = "location_military_generic_01_";
        for (int place : new int[] {56, 57, 59, 60, 61, 63}) {
            Outdoors.Piece low = unnamed(sheet + place);
            float top = low.raised + Models.get(low.model).height();
            Checks.check(Math.abs(top - ViewCamera.LEVEL_HEIGHT) < 0.005f, sheet + place + " reaches up to the level over it: " + top);
        }
        Outdoors.Piece beam = unnamed(sheet + 53), end = unnamed(sheet + 55);
        float beamTop = beam.raised + Models.get(beam.model).height(), endTop = Models.get(end.model).height();
        Checks.check(Math.abs(beamTop - endTop) < 0.005f, "the beam is as high as the top of the post it runs from: " + beamTop + ", " + endTop);
    }

    private static void aShowJumpOrANetLiesAlongTheWayItsSpritesDo() {
        float[] along = new float[2];
        for (int place : new int[] {49, 50, 51, 123, 124, 125}) {
            Models.turn(unnamed("recreational_sports_01_" + place).turns, 1f, 0f, along);
            Checks.check(along[0] == 1f && along[1] == 0f, "recreational_sports_01_" + place + " runs along x");
        }
        for (int place : new int[] {52, 53, 54, 120, 121, 122}) {
            Models.turn(unnamed("recreational_sports_01_" + place).turns, 1f, 0f, along);
            Checks.check(along[0] == 0f && along[1] == 1f, "recreational_sports_01_" + place + " runs along y");
        }
    }

    private static void aPieceLaidOutOtherwiseThanItsModelIsNone() {
        Checks.check(Outdoors.of("sheet_01_0", "Piano", "Western", Outdoors.SOUTH, 1, 2, 0) == null, "a piano two squares along the way it faces");
        Checks.check(Outdoors.of("sheet_01_0", "Slide", "Playground", Outdoors.SOUTH, 2, 1, 0) == null, "a slide two squares across");
        Checks.check(Outdoors.of("sheet_01_0", "Drum", "Cymbal", Outdoors.SOUTH, 1, 1, 0) == null, "a drum the mod has no model of");
        Checks.check(Outdoors.of("sheet_01_0", "Jukebox", "Oldies", Outdoors.NONE, 1, 1, 0) == null, "a jukebox with no front");
        Checks.check(unnamed("location_community_park_01_49") == null && unnamed("recreational_sports_01_34") == null, "what else is on the sheets");
    }

    private static final String CHURCH = "location_community_church_small_01_";

    private static void aGoldCrossWithRaysIsStoodByItsLowerWestSquareAgainstItsWall() {
        Outdoors.Piece cross = Outdoors.of(CHURCH + 82, "Cross", "Golden", Outdoors.SOUTH, 2, 1, 0);
        holds(cross, "the gold cross's lower west square has a model");
        Checks.check(cross != null && cross.model.equals("glory_cross") && !cross.follows && cross.each && cross.turns == Outdoors.SOUTH
                && (CHURCH + 83).equals(cross.partner) && cross.partnerX == 1 && cross.partnerY == 0, "it stands the whole cross, facing south, beside its east square");
        Models.Model model = Models.get("glory_cross");
        Checks.check(cross != null && cross.b - model.maxY >= WorldScene.WALL_HALF - 0.01f && cross.b - model.maxY < 0.06f,
                "its back is against the wall on the north edge of its square");
        Checks.check(cross != null && cross.dyed == 1 << Models.METAL, "its gold takes its colour from the sprite's upright");
        for (int[] other : new int[][] {{83, -1, 0}, {74, 1, 0}, {75, -1, 0}}) {
            Outdoors.Piece piece = Outdoors.of(CHURCH + other[0], "Cross", "Golden", Outdoors.SOUTH, 2, 1, 0);
            Checks.check(piece != null && piece.follows && piece.model == null && piece.partnerX == other[1] && piece.partnerY == other[2],
                    CHURCH + other[0] + " stands nothing and hides while its pair is there");
            Checks.check(Leisure.gloryPiece(CHURCH + other[0]), CHURCH + other[0] + " is a square of the cross, whatever its grid's storeys");
        }
        Checks.check(!Leisure.gloryPiece(CHURCH + 39) && !Leisure.gloryPiece(CHURCH + 22), "the cross on a spire and one on a wall are not");
    }

    private static void aChurchsLongDeskAlongYIsTheSameDeskTurnedToFaceEast() {
        // 101 is the north end, 99 the south: turned to face east a model's x runs north, so its east end is the north one.
        String[] models = {"pulpit_west", "pulpit_middle", "pulpit_east"};
        float[] along = {Leisure.PULPIT_WEST_DYE, Leisure.PULPIT_MIDDLE_DYE, Leisure.PULPIT_EAST_DYE};
        float[] turned = new float[2];
        Models.turn(Outdoors.EAST, 1f, 0f, turned);
        Checks.check(turned[0] == 0f && turned[1] == -1f, "facing east a model's x runs north");
        for (int i = 0; i < 3; i++) {
            Outdoors.Piece piece = unnamed(CHURCH + (99 + i));
            holds(piece, CHURCH + (99 + i) + " is a share of a church's long desk laid along y");
            Checks.check(piece != null && piece.model.equals(models[i]) && piece.turns == Outdoors.EAST && piece.a == 0.5f && piece.b == 0.5f,
                    "it is " + models[i] + " in the middle of its square, its front to the east");
            Checks.check(piece != null && piece.dyed == 1 << Models.WOOD && piece.alsoDyed == 0 && piece.dyeA == Leisure.PULPIT_DYE_B
                    && piece.dyeB == 1f - along[i], "its top takes its colour from the top, where the desk along x takes it, turned");
        }
    }

    private static void aCrossOnAChurchWallHangsOnTheWallItsSpriteIsAttachedTo() {
        for (int place : new int[] {22, 23, 36, 37, 38, 54, 55}) {
            boolean west = place == 22 || place == 36 || place == 38 || place == 54;
            String name = CHURCH + place;
            Furniture cross = Furniture.known(TileRows.sprite(name + (west ? "|attachedW|" : "|attachedN|")));
            Checks.check(cross != null && cross.model != null && cross.model.name.equals("wall_cross"), name + " is a cross");
            Checks.check(cross != null && cross.wall == (west ? WallHung.WEST : WallHung.NORTH)
                    && cross.turns == (west ? Outdoors.EAST : Outdoors.SOUTH), name + " hangs on its wall, facing away from it");
        }
        Furniture spire = Furniture.known(TileRows.sprite(CHURCH + 39 + "|attachedN|"));
        Checks.check(spire == null || spire.model == null || !spire.model.name.equals("wall_cross"), "the cross on a tower's spire is not one");
        Models.Model model = Models.get("wall_cross");
        Checks.check(model.height() > 1.1f && model.width() < 0.75f && model.depth() < 0.06f, "it is a latin cross, standing a little off its wall");
    }

    private static void aChandeliersBowlAndTiersHangFromOneRodWhereTheirHalvesMeet() {
        String sheet = "lighting_indoor_03_";
        Outdoors.Piece bowl = Outdoors.of(sheet + 0, "Chandelier", "Grey", Outdoors.EAST, 1, 1, 0);
        Outdoors.Piece tiers = Outdoors.of(sheet + 2, "Chandelier", "Grey", Outdoors.EAST, 1, 1, 0);
        holds(bowl, "the left half of a chandelier's bowl has a model");
        holds(tiers, "the left half of its tiers has a model");
        Checks.check(bowl != null && bowl.model.equals("chandelier_bowl") && !bowl.follows && (sheet + 1).equals(bowl.partner)
                && bowl.partnerX == 1 && bowl.partnerY == -1, "the bowl stands where the right half is on the square east and north of it");
        Checks.check(tiers != null && tiers.model.equals("chandelier_tiers") && !tiers.follows && (sheet + 3).equals(tiers.partner)
                && tiers.partnerX == 1 && tiers.partnerY == -1, "so do the tiers");
        Checks.check(bowl != null && tiers != null && bowl.a == tiers.a && bowl.b == tiers.b && Math.abs(bowl.a - 1f) < 0.05f && Math.abs(bowl.b) < 0.05f,
                "both hang on one rod at the corner the two squares share");
        Checks.check(bowl != null && tiers != null && bowl.dyed == 1 << Models.METAL && tiers.dyed == 1 << Models.METAL,
                "their gold takes its colour from where the sprites paint gold");
        for (int place : new int[] {1, 3}) {
            Outdoors.Piece half = Outdoors.of(sheet + place, "Chandelier", "Grey", Outdoors.EAST, 1, 1, 0);
            Checks.check(half != null && half.follows && half.model == null && (sheet + (place - 1)).equals(half.partner) && half.partnerX == -1
                    && half.partnerY == 1, sheet + place + " stands nothing and hides while its left half is there");
        }
        Models.Model bowlModel = Models.get("chandelier_bowl"), tiersModel = Models.get("chandelier_tiers");
        float storey = ViewCamera.LEVEL_HEIGHT;
        Checks.check(bowl != null && Math.abs(bowl.raised + bowlModel.maxZ - storey) < 0.001f, "the bowl's rod reaches the ceiling of its storey");
        Checks.check(tiers != null && Math.abs(tiers.raised + tiersModel.maxZ - storey) < 0.001f, "the tiers' rod reaches the ceiling of theirs");
        Checks.check(tiers != null && bowl != null && storey + tiers.raised < bowl.raised + bowlModel.maxZ && storey + tiers.raised > bowl.raised + 0.9f,
                "the foot of the tiers sits down in the bowl, over its crystal");
        Checks.check(Outdoors.of(sheet + 4, "Lamp", "Grey", Outdoors.NONE, 1, 1, 0) == null, "a lamp on the same sheet is none of it");
    }

    private static void anOrgansWestSquareStandsItAgainstItsWallAndTheRestStandNothing() {
        Outdoors.Piece organ = Outdoors.of(CHURCH + 96, "Organ", "Church", Outdoors.SOUTH, 3, 1, 0);
        holds(organ, "the organ's west square of its lower storey has a model");
        Checks.check(organ.model.equals("pipe_organ") && !organ.follows && organ.each && organ.turns == Outdoors.SOUTH, "it stands the whole organ, facing south");
        Checks.check((CHURCH + 98).equals(organ.partner) && organ.partnerX == 2 && organ.partnerY == 0, "only where its east square holds its piece");
        Models.Model model = Models.get("pipe_organ");
        Checks.check(model.width() <= 3f && Math.abs(organ.a - 1.5f) < 1e-6f, "it is no wider than its three squares, and stands in the middle of them");
        Checks.check(organ.b - model.maxY >= 0f && organ.b - model.maxY < 0.05f, "its back is against the wall on the north edge of its squares");
        int[][] others = {{97, 96, -1}, {98, 96, -2}, {88, 90, 2}, {89, 88, -1}, {90, 88, -2}};
        for (int[] other : others) {
            Outdoors.Piece piece = Outdoors.of(CHURCH + other[0], "Organ", "Church", Outdoors.SOUTH, 3, 1, 0);
            Checks.check(piece != null && piece.model == null && piece.follows && piece.each, CHURCH + other[0] + " stands nothing and hides its sprite");
            Checks.check(piece != null && (CHURCH + other[1]).equals(piece.partner) && piece.partnerX == other[2] && piece.partnerY == 0,
                    CHURCH + other[0] + " is paired with " + other[1] + " on its own storey");
            Checks.check(Leisure.organPiece(CHURCH + other[0]), CHURCH + other[0] + " is a square of an organ, whatever its grid's storeys");
        }
        Checks.check(!Leisure.organPiece(CHURCH + 91) && !Leisure.organPiece(CHURCH + 127), "its stool and a window are not");
        Outdoors.Piece stool = Outdoors.of(CHURCH + 91, "Stool", "Organs", Outdoors.NORTH, 1, 1, 0);
        holds(stool, "the organ's stool has a model");
        Checks.check(stool.model.equals("piano_bench"), "the stool is the piano's bench");
    }

    private static void aChurchsColouredWindowsAreStainedGlassAndItsClearOnesAreNot() {
        for (int place : new int[] {114, 119, 122, 127}) {
            Checks.check(StainedGlass.of(CHURCH + place), CHURCH + place + " is stained glass");
        }
        for (int place : new int[] {112, 113, 120, 121, 128, 133, 141}) {
            Checks.check(!StainedGlass.of(CHURCH + place), CHURCH + place + " is a clear window or a wall");
        }
        for (int place : new int[] {0, 7, 14, 19, 30, 31}) {
            Checks.check(StainedGlass.of("fixtures_windows_church_" + place), "fixtures_windows_church_" + place + " is stained glass");
        }
        for (int place : new int[] {8, 13, 20, 29}) {
            Checks.check(!StainedGlass.of("fixtures_windows_church_" + place), "fixtures_windows_church_" + place + " is diamond leaded clear glass");
        }
        Checks.check(!StainedGlass.of(null) && !StainedGlass.of("fixtures_windows_01_0"), "nor is a sprite with no name or a house's window");
    }

    private static void whatAPitchACourtAndADiamondStandOnIsLevelGround() {
        Checks.check(Grounds.of("recreational_sports_01_19") == Grounds.GOAL_ALONG_X && Grounds.of("recreational_sports_01_16") == Grounds.GOAL_ALONG_Y, "a football goal marks a pitch, and which way it faces");
        Checks.check(Grounds.of("recreational_sports_01_22") == Grounds.SURFACE && Grounds.of("recreational_sports_01_23") == Grounds.SURFACE, "a court's surface and a track are level ground");
        Checks.check(Grounds.of("recreational_sports_01_0") == Grounds.BASE && Grounds.of("recreational_sports_01_24") == Grounds.HOOP && Grounds.of("recreational_sports_01_120") == Grounds.JUMP,
                "a base, a hoop and a show jump mark level ground");
        Checks.check(Grounds.of("recreational_sports_01_49") == Grounds.PIECE && Grounds.of("location_military_generic_01_48") == Grounds.PIECE && Grounds.of("recreational_sports_01_7") == Grounds.PIECE,
                "a tennis net, a climbing frame and a gridiron's goalposts stand level");
        Checks.check(Grounds.of("recreational_sports_01_40") == Grounds.NONE && Grounds.of("location_military_generic_01_0") == Grounds.NONE && Grounds.of("blends_natural_01_16") == Grounds.NONE, "the rest is not");
    }

    private static final float EASE = 16f;

    private static void levelGroundIsFlatInItsBoxAndEasesBackOutToTheLand() {
        float[] grounds = {0, 0, 10, 20, 3.5f};
        float[] pad = new float[1];
        Checks.check(Grounds.weight(grounds, 1, 5, 10, EASE, pad) == 0f && pad[0] == 3.5f, "inside the box the land keeps none of its own height");
        Checks.check(Grounds.weight(grounds, 1, 10, 20, EASE, pad) == 0f && pad[0] == 3.5f, "nor on its edge");
        float near = Grounds.weight(grounds, 1, 10.01f, 10, EASE, pad);
        Checks.check(near < 0.001f && pad[0] == 3.5f, "just outside it hardly any, got " + near);
        float half = Grounds.weight(grounds, 1, 18, 10, EASE, pad);
        Checks.check(half > 0.4f && half < 0.6f, "half way out about half, got " + half);
        Checks.check(Grounds.weight(grounds, 1, 10 + EASE, 10, EASE, pad) == 1f, "and all of it by the end of the ease");
        float last = -1f;
        for (float x = 10f; x <= 10f + EASE; x += 0.25f) {
            float keeps = Grounds.weight(grounds, 1, x, 10, EASE, pad);
            Checks.check(keeps >= last, "the land comes back steadily, not at " + x);
            last = keeps;
        }
    }

    private static void betweenTwoLevelGroundsTheFootingRunsFromOneToTheOtherWithNoStep() {
        // A pitch at 1 and a court at 4, eight squares apart.
        float[] grounds = {0, 0, 10, 20, 1f, 18, 0, 28, 20, 4f};
        float[] pad = new float[1];
        float lastHeight = Float.NaN;
        for (float x = 9f; x <= 19f; x += 0.05f) {
            float keeps = Grounds.weight(grounds, 2, x, 10, EASE, pad);
            float height = pad[0] * (1f - keeps);
            if (!Float.isNaN(lastHeight)) {
                Checks.check(Math.abs(height - lastHeight) < 0.1f, "no step between them at " + x + ": " + lastHeight + " to " + height);
            }
            lastHeight = height;
        }
        Grounds.weight(grounds, 2, 10, 10, EASE, pad);
        Checks.check(pad[0] == 1f, "at the pitch's edge its own footing");
        Grounds.weight(grounds, 2, 18, 10, EASE, pad);
        Checks.check(pad[0] == 4f, "at the court's its own");
    }

    private static void levelGroundsThatOverlapShareTheLargerOnesFootingInWhateverOrderMet() {
        float[] track = {0, 0, 40, 80, 2f}, pitch = {5, 10, 35, 70, 3f}, court = {100, 0, 110, 10, 7f};
        float[] one = concat(track, pitch, court), other = concat(court, pitch, track);
        Grounds.settle(one, 3);
        Grounds.settle(other, 3);
        Checks.check(java.util.Arrays.equals(one, other), "the same grounds come out the same whatever order they were met in");
        float[] pad = new float[1];
        Grounds.weight(one, 3, 20, 40, EASE, pad);
        Checks.check(pad[0] == 2f, "a pitch inside a track lies at the track's footing, got " + pad[0]);
        Grounds.weight(one, 3, 105, 5, EASE, pad);
        Checks.check(pad[0] == 7f, "a court apart from both keeps its own");
    }

    private static float[] concat(float[]... each) {
        float[] all = new float[each.length * Grounds.STRIDE];
        for (int at = 0; at < each.length; at++) {
            System.arraycopy(each[at], 0, all, at * Grounds.STRIDE, Grounds.STRIDE);
        }
        return all;
    }
}
