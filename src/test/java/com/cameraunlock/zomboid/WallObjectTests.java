package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import zombie.iso.sprite.IsoSprite;

/**
 * What the game hangs on a wall by the way it faces and marks for no wall: which wall each lies
 * flat on, which of them is painted in that wall's plane, and which stand out from it as a
 * model. The rows are the tiles' own (sounds left out), and the painted rectangles are the
 * sprites' own in the game's doubled sheets.
 */
final class WallObjectTests {
    private WallObjectTests() {}

    static void run() {
        Checks.run("aThingHungByItsFacingLiesOnTheWallBehindIt", WallObjectTests::aThingHungByItsFacingLiesOnTheWallBehindIt);
        Checks.run("whatIsSolidOrMarkedForAWallIsNotLaidByItsFacing", WallObjectTests::whatIsSolidOrMarkedForAWallIsNotLaidByItsFacing);
        Checks.run("aNoticeRunningPastItsSquaresEndIsStillOnItsWall", WallObjectTests::aNoticeRunningPastItsSquaresEndIsStillOnItsWall);
        Checks.run("aPieceOfALongBoardPaintedOnTheNextSquareIsOnTheWall", WallObjectTests::aPieceOfALongBoardPaintedOnTheNextSquareIsOnTheWall);
        Checks.run("whatStandsOnTheFloorInFrontOfAWallIsNotLaidOnIt", WallObjectTests::whatStandsOnTheFloorInFrontOfAWallIsNotLaidOnIt);
        Checks.run("whatStandsOutFromItsWallIsAModelWithItsBackToIt", WallObjectTests::whatStandsOutFromItsWallIsAModelWithItsBackToIt);
        Checks.run("aCellsSeatAndBunkAreAsHighAsTheGameSays", WallObjectTests::aCellsSeatAndBunkAreAsHighAsTheGameSays);
        Checks.run("aSeatWhoseHeightTheGameCannotReadIsLeftToTheGame", WallObjectTests::aSeatWhoseHeightTheGameCannotReadIsLeftToTheGame);
        Checks.run("aChalkboardSeenFromBehindIsABoardAndOneSeenFromInFrontLiesFlat", WallObjectTests::aChalkboardSeenFromBehindIsABoardAndOneSeenFromInFrontLiesFlat);
        Checks.run("aShowerOnTheWallsSeenFromBehindIsTheShowersModel", WallObjectTests::aShowerOnTheWallsSeenFromBehindIsTheShowersModel);
    }

    private static final String NOTICE = "trash_walls_01_2|CanScrap||CustomName|Notices|Facing|E|GroupName|Battered|IsMoveAble||Material|Paper|MoveType|WallObject|PickUpWeight|10|WallOverlay|";
    private static final String WANTED = "papernotices_01_0|CustomName|Notices|Facing|E|GroupName|Wanted|IsMoveAble||MoveType|WallObject|PickUpWeight|10";
    private static final String CHALK = "|CustomName|Board|ForceSingleItem||GroupName|Chalk|IsMoveAble||MaterialType|Wood|MoveType|WallObject|PickUpWeight|20|";
    private static final String PHONE = "|BlocksPlacement||CanBreak||CanScrap||CustomName|Phones|GroupName|Pay|IsMoveAble||Material|Electric|Material2|Mechanical|"
            + "Material3|MetalScrap|MaterialType|Plastic|MoveType|WallObject|PickUpLevel|2|PickUpTool|Crowbar|PickUpWeight|200|PlaceTool|Electrician|";
    private static final String SEAT = "location_community_police_01_34|BlocksPlacement||CanScrap||CustomName|Seat|Facing|S|GroupName|Hanging|IsLow||IsMoveAble||"
            + "ItemHeight|29|Material|Fabric|Material2|Screws|MoveType|WallObject|PickUpLevel|2|PickUpTool|Hammer|PickUpWeight|100|PlaceTool|Hammer|solidtrans|";
    private static final String BUNK = "|BlocksPlacement||CanScrap||CustomName|Bed|Facing|S|GroupName|Prisoners|IsLow||IsMoveAble||ItemHeight|29|Material|Fabric|"
            + "MoveType|WallObject|PickUpLevel|2|PickUpTool|Hammer|PickUpWeight|150|PlaceTool|Hammer|Surface|24|bed||solidtrans||SpriteGridPos|";
    private static final String TROPHY = "camping_01_17|CustomName|Deer Trophy|Facing|E|GroupName|Mounted|IsHigh||IsMoveAble||MoveType|WallObject|PickUpWeight|30";

    private static IsoSprite sprite(String row) {
        return TileRows.sprite(row);
    }

    private static int flatOn(IsoSprite sprite) {
        return WallObjects.flatOn(sprite.getProperties(), sprite.getFacing());
    }

    private static String model(Furniture piece) {
        return piece == null ? "nothing" : piece.model == null ? "no model" : piece.model.name;
    }

    /** The frame of a sprite of the doubled sheets, its painted rectangle from (left, top), as the build reads it. */
    private static SpriteGeometry.Frame frame(float left, float top, float width, float height) {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 2f;
        frame.originU = 64f;
        frame.originV = 192f;
        frame.left = left;
        frame.top = top;
        frame.width = width;
        frame.height = height;
        return frame;
    }

    private static void aThingHungByItsFacingLiesOnTheWallBehindIt() {
        check(flatOn(sprite(NOTICE)) == WallHung.WEST, "a notice facing east is on the west wall: " + flatOn(sprite(NOTICE)));
        check(flatOn(sprite("location_community_school_01_19" + CHALK + "Facing|S|SpriteGridPos|0,0")) == WallHung.NORTH, "a chalkboard facing south is on the north wall");
        check(flatOn(sprite("location_community_school_01_27" + CHALK + "Facing|N|SpriteGridPos|0,0")) == WallHung.SOUTH, "one facing north on the south wall");
        check(flatOn(sprite("street_decoration_01_46" + PHONE + "Facing|W")) == WallHung.EAST, "a pay phone facing west on the east wall, where it has no model");
    }

    private static void whatIsSolidOrMarkedForAWallIsNotLaidByItsFacing() {
        check(flatOn(sprite(SEAT)) == WallHung.NONE, "a seat the game says fills part of its square stands out from the wall");
        check(flatOn(sprite("walls_decoration_01_31|CustomName|Mirror|Facing|N|GroupName|Large|IsHigh||IsMirror||IsMoveAble||MaterialType|Glass|MoveType|WallObject|"
                + "WallOverlay||attachedS|")) == WallHung.NONE, "a mirror the game marks for its wall goes on that wall by the mark, as before");
        check(flatOn(sprite("furniture_seating_indoor_01_0|CustomName|Chair|Facing|S|GroupName|Wooden|IsMoveAble||MoveType|Object")) == WallHung.NONE,
                "a chair with a facing is no thing hung on a wall");
        check(flatOn(sprite("papernotices_01_99|CustomName|Notices|IsMoveAble||MoveType|WallObject")) == WallHung.NONE, "nor is one with no facing to say which wall");
    }

    private static void aNoticeRunningPastItsSquaresEndIsStillOnItsWall() {
        float[] found = new float[4];
        // papernotices_01_0: four wanted posters on the west wall, the first of them painted over the square's north end.
        SpriteGeometry.Frame wanted = frame(27f, 52f, 47f, 86f);
        int wall = flatOn(sprite(WANTED));
        check(!WallHung.painted(wanted, wall, found), "WallHung.painted, which keeps a hung model on its own square's wall, refuses the posters");
        WallHung.extent(wanted, wall, found);
        check(found[WallHung.FROM] < -0.12f && found[WallHung.FROM] > -0.2f, "they start a little past the square's end: " + found[WallHung.FROM]);
        check(WallHung.paintedAlong(found), "and are laid on the wall all the same");
    }

    private static void aPieceOfALongBoardPaintedOnTheNextSquareIsOnTheWall() {
        float[] found = new float[4];
        // location_community_school_01_71, the last square of a board four squares long, which paints the end of the square before it.
        WallHung.extent(frame(0f, 29f, 106f, 124f), WallHung.NORTH, found);
        check(found[WallHung.FROM] < -0.9f && found[WallHung.FROM] >= -1.12f, "it starts almost a whole square back along the wall: " + found[WallHung.FROM]);
        check(WallHung.paintedAlong(found), "and lies flat on the wall with the rest of its board");
    }

    private static void whatStandsOnTheFloorInFrontOfAWallIsNotLaidOnIt() {
        float[] found = new float[4];
        // advertising_02_1, a mall's directory on its own foot, which the game hangs by its facing too.
        WallHung.extent(frame(28f, 72f, 74f, 170f), WallHung.NORTH, found);
        check(!WallHung.paintedAlong(found), "a sign standing on the floor before the wall would have its foot under the floor laid on the wall: "
                + found[WallHung.BOTTOM]);
    }

    private static void whatStandsOutFromItsWallIsAModelWithItsBackToIt() {
        float[] front = new float[2];
        Furniture phone = Furniture.known(sprite("street_decoration_01_39" + PHONE + "Facing|S"));
        check(model(phone).equals("pay_phone"), "a pay phone is the pay phone's model: " + model(phone));
        Furniture trophy = Furniture.known(sprite(TROPHY));
        check(model(trophy).equals("deer_trophy"), "a deer's head on a wall is the trophy's model: " + model(trophy));
        for (Furniture piece : new Furniture[] {phone, trophy}) {
            if (piece == null || piece.model == null) {
                continue;
            }
            Models.turn(piece.turns, 0f, -1f, front);
            check((piece.centreX - 0.5f) * front[0] + (piece.centreY - 0.5f) * front[1] < -0.1f,
                    piece.model.name + " stands with its back to the wall behind its facing, not in the middle of the square");
        }
        if (phone != null) {
            check(phone.turns == 2 && phone.dyed != 0, "the phone faces south as its sprite does, and takes the colour of its hood from it");
        }
        if (trophy != null && trophy.model != null) {
            float top = trophy.raised + trophy.model.height() * trophy.scaleUp;
            check(trophy.raised > 1.2f && top < ViewCamera.LEVEL_HEIGHT, "the head hangs over a standing head and under the ceiling: " + trophy.raised + " to " + top);
        }
    }

    private static void aSeatWhoseHeightTheGameCannotReadIsLeftToTheGame() {
        for (String height : new String[] {"29.5", " 29", "", "200", "-3", "99999999999"}) {
            Furniture seat = Furniture.known(sprite("mod_cells_01_" + height.length() + SEAT.substring(SEAT.indexOf('|')).replace("ItemHeight|29|", "ItemHeight|" + height + "|")));
            check(model(seat).equals("nothing"), "a seat a mod gives the height '" + height + "' has no model: " + model(seat));
        }
    }

    private static void aCellsSeatAndBunkAreAsHighAsTheGameSays() {
        Furniture seat = Furniture.known(sprite(SEAT));
        check(model(seat).equals("cell_seat"), "a seat hung in a cell is the cell's seat: " + model(seat));
        if (seat != null && seat.model != null) {
            float top = seat.raised + seat.model.height() * seat.scaleUp;
            check(Math.abs(top - 29f / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT) < 0.01f, "its top is where the game puts what is put on it: " + top);
        }
        IsoSprite head = sprite("location_community_police_01_26" + BUNK + "0,0"), foot = sprite("location_community_police_01_27" + BUNK + "1,0");
        TileRows.piece(2, 1, head, foot);
        Furniture bunk = Furniture.known(head);
        check(model(bunk).equals("cell_bunk"), "a prisoner's bed two squares along its wall is the cell's bunk: " + model(bunk));
        if (bunk != null && bunk.model != null) {
            float top = bunk.raised + bunk.model.height() * bunk.scaleUp;
            check(Math.abs(top - 24f / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT) < 0.01f, "its top is the surface the game gives: " + top);
            check(model(Furniture.known(foot)).equals("cell_bunk") && Furniture.known(foot).gridX == 1, "and its second square is the same bunk");
        }
    }

    private static void aChalkboardSeenFromBehindIsABoardAndOneSeenFromInFrontLiesFlat() {
        IsoSprite[] north = new IsoSprite[3], south = new IsoSprite[3];
        for (int i = 0; i < 3; i++) {
            north[i] = sprite("location_community_school_01_" + (27 + i) + CHALK + "Facing|N|SpriteGridPos|" + i + ",0");
            south[i] = sprite("location_community_school_01_" + (19 + i) + CHALK + "Facing|S|SpriteGridPos|" + i + ",0");
        }
        TileRows.piece(3, 1, north);
        TileRows.piece(3, 1, south);
        Furniture board = Furniture.known(north[0]);
        check(model(board).equals("chalkboard_3"), "a board three squares long facing north is the blank board's model: " + model(board));
        if (board != null && board.model != null) {
            check(board.raised > 0.5f && board.raised + board.model.height() * board.scaleUp < 2.2f, "hung at the height its sprites paint it: from " + board.raised);
        }
        check(Furniture.known(south[1]) == null, "one facing south is no model");
        check(flatOn(south[1]) == WallHung.NORTH, "and lies flat on the north wall, its face as its sprite paints it");
    }

    private static void aShowerOnTheWallsSeenFromBehindIsTheShowersModel() {
        Furniture shower = Furniture.known(sprite("fixtures_bathroom_01_22|CanBreak||CanScrap||CustomName|Shower|Facing|N|GroupName|Wall|IsMoveAble||Material|Plumbing|"
                + "Material2|Pipes|MoveType|WallObject|PickUpLevel|2|PickUpTool|Hammer|PickUpWeight|50|PlaceTool|Hammer|ScrapSize|Small"));
        check(model(shower).equals("shower_head"), "a shower on a south wall is the shower's model, as one on a north wall is: " + model(shower));
        check(shower == null || shower.turns == 0, "facing north");
    }
}
