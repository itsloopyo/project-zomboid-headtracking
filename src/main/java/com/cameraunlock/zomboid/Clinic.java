package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.Set;
import zombie.core.properties.PropertyContainer;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteGrid;

/**
 * What wards, surgeries, laboratories and offices have that the mod has a model for: beds,
 * couches and trolleys, stands and scales, cabinets, telephones, and the desks laid in an L.
 * Each is told by the name the game gives it and the sheet it is on, and every colour that
 * differs between the sprites of one kind is taken from the sprite.
 */
final class Clinic {
    private Clinic() {}

    static final String OFFICE = "location_business_office_generic_01_";
    private static final String MEDICAL = "location_community_medical_01_",
            PHONES = "location_business_office_generic_02_";
    /** Where on its sprite, in levels, a thing gives its colour: a bed's sheet on its foot square, a stool's seat, the
     *  paper on a couch, a drip stand's pump, a phone's body, the mattress in a crib, a dentist's chair. */
    private static final float UPHOLSTERY_DYE = 0.3f, SHEET_DYE = 0.3f, SEAT_DYE = 0.22f, PAPER_DYE = 0.3f, PUMP_DYE = 0.59f, ROTARY_DYE = 0.385f, KEYPAD_DYE = 0.255f,
            CRIB_DYE = 0.3f;
    /** How high, in metres, the part of a model is that the game gives the height of: a step's top, a crib's rim, a desk's top. */
    private static final float STEP_TOP = 0.38f, CRIB_RIM = 0.9f, DESK_TOP = 0.75f;
    /** The halves of the wide cabinet the sprites paint with its end on the model's -X, on every facing. */
    private static final Set<Integer> HOOD_LEFT = Set.of(152, 154, 238, 246);

    /** A piece told with no need of the way it faces. Null for anything else. */
    static Furniture anyWay(IsoSprite sprite, String name, String called) {
        if (called.equals("Medical Stool") && name.startsWith(MEDICAL) && sprite.getSpriteGrid() == null) {
            return new Furniture(sprite, "medical_stool", 0).dye(1 << Models.VINYL, SEAT_DYE).stand(0f, 0f, false);
        }
        return null;
    }

    /** A piece that stands the way it faces, along and across squares long. Null for anything else. */
    static Furniture facing(IsoSprite sprite, String name, String called, String group, String holds, PropertyContainer properties,
            int turns, int along, int across) {
        boolean single = along == 1 && across == 1, long2 = along == 2 && across == 1;
        float surfaceHigh = properties.getSurface() / WorldScene.SURFACE_PIXELS_PER_LEVEL, surfaceUp = surfaceHigh * ViewCamera.LEVEL_HEIGHT;
        boolean onTable = properties.has("IsTableTop");
        if (name.startsWith(PHONES) && called.equals("Phone") && onTable && single) {
            boolean rotary = group.endsWith("Rotary");
            return new Furniture(sprite, rotary ? "rotary_phone" : "desk_phone", turns).dye(1 << Models.PLASTIC, rotary ? ROTARY_DYE : KEYPAD_DYE)
                    .stand(0f, 0f, false).raise(surfaceUp);
        }
        if (name.startsWith(OFFICE) && called.equals("Desk") && properties.isTable() && surfaceUp > 0f) {
            return switch (group) {
                case "Office" -> straightDesk(sprite, name, holds, properties, turns, surfaceHigh, surfaceUp);
                case "Office L", "Office L2" -> lDesk(sprite, name, group, holds, turns, surfaceHigh, surfaceUp);
                default -> desk(sprite, holds, turns, surfaceHigh, surfaceUp);
            };
        }
        if (!name.startsWith(MEDICAL)) {
            return null;
        }
        // The two squares long are a bed's, its head at the back: the game's facing is the way its foot points.
        if (called.equals("Bed") && group.equals("Large Medical") && long2) {
            return new Furniture(sprite, "hospital_bed", turns).dyeAtFront(1 << Models.CLOTH, SHEET_DYE).stand(0f, 0f, false);
        }
        if (called.equals("Bed") && group.equals("Hospital") && long2) {
            return new Furniture(sprite, "gurney", turns).stand(0f, 0f, false);
        }
        if (called.equals("Table") && group.equals("Morgue") && long2) {
            return new Furniture(sprite, "morgue_table", turns).stand(0f, 0f, false);
        }
        if (called.equals("Chair") && group.equals("Patient") && long2) {
            // The one with drawers under it is the doctor's couch of wood; the other a pad on a steel column.
            return holds.equals("counter") ? new Furniture(sprite, "exam_table", turns).dyeAtFront(1 << Models.VINYL, PAPER_DYE).stand(0f, 0f, false)
                    : new Furniture(sprite, "treatment_couch", turns).stand(0f, 0f, false);
        }
        // The sprites of a chair facing north or west paint no upholstery where its colour is taken, and stay as they are.
        if (called.equals("Chair") && group.equals("Dentist Patient") && long2 && (turns == 1 || turns == 2)) {
            return new Furniture(sprite, "dentist_chair", turns).dye(1 << Models.VINYL, UPHOLSTERY_DYE).stand(0f, 0f, false);
        }
        // The cart is on the first square and the lamp's arm reaches over the second, where the chair is.
        if (called.equals("Workstation") && group.equals("Dentist") && long2) {
            return new Furniture(sprite, "dentist_unit", turns).stand(0f, 0f, false);
        }
        if (!single) {
            return null;
        }
        if (called.equals("Counter") && group.equals("Mobile Tool")) {
            return new Furniture(sprite, "mayo_stand", turns).stand(0f, 0f, false);
        }
        if (called.equals("Bloodbag") && group.equals("Mobile")) {
            return new Furniture(sprite, "iv_stand", turns).dye(1 << Models.PLASTIC, PUMP_DYE).stand(0f, 0f, false);
        }
        if (called.equals("Bench") && group.equals("Foot") && surfaceUp > 0f) {
            return new Furniture(sprite, "foot_bench", turns).dye(1 << Models.CLOTH, surfaceHigh).stand(surfaceUp, STEP_TOP, false);
        }
        if (called.equals("Bench") && group.equals("Tool") && surfaceUp > 0f) {
            return new Furniture(sprite, "bassinet", turns).dye(1 << Models.CLOTH, CRIB_DYE).stand(surfaceUp, CRIB_RIM, false);
        }
        if (called.equals("Microscope") && onTable && surfaceUp > 0f) {
            return new Furniture(sprite, "microscope", turns).stand(0f, 0f, false).raise(surfaceUp);
        }
        if (called.equals("Scale") && group.equals("Weighing")) {
            return new Furniture(sprite, "weighing_scale", turns).stand(0f, 0f, true);
        }
        if (called.equals("Bank") && group.equals("Blood")) {
            return new Furniture(sprite, "blood_bank", turns).stand(0f, 0f, true);
        }
        // Each square is half of one cabinet two squares wide, and the two halves meet.
        if (called.equals("Cabinet") && group.equals("Wide Medical")) {
            String half = HOOD_LEFT.contains(Sheets.place(name)) ? "fume_hood_left" : "fume_hood_right";
            return new Furniture(sprite, half, turns).meet(0f, 0f, true);
        }
        // Its front is the sprite's own painting of it: a red case, a green square and a white cross.
        if (called.equals("Cabinet") && group.equals("First Aid") && holds.equals("medicine") && sprite.getFacing() != null) {
            return new Furniture(sprite, "first_aid_case", turns).hang(WallHung.behind(sprite.getFacing()), true).ownSize();
        }
        return null;
    }

    /**
     * A square of a desk the game lays in an L over a grid of three or four squares each way: a
     * piece of its own, its top as high as the game says and meeting the next square's.
     */
    private static Furniture desk(IsoSprite sprite, String holds, int turns, float surfaceHigh, float surfaceUp) {
        IsoSpriteGrid grid = sprite.getSpriteGrid();
        if (grid == null || grid.getWidth() < 3 || grid.getHeight() < 3) {
            return null;
        }
        boolean[][] filled = new boolean[grid.getWidth()][grid.getHeight()];
        for (int x = 0; x < filled.length; x++) {
            for (int y = 0; y < filled[x].length; y++) {
                filled[x][y] = grid.getSprite(x, y) != null;
            }
        }
        DeskRuns.Square square = DeskRuns.of(filled, grid.getSpriteGridPosX(sprite), grid.getSpriteGridPosY(sprite), holds.equals("desk"), turns);
        return deskSquare(sprite, square.model(), square.turns(), surfaceHigh, surfaceUp);
    }

    /** Where on the sheet its straight desks three squares long begin: those before are two squares long. */
    private static final int THREE_LONG = 72;

    /**
     * A square of one of the sheet's straight desks, two squares long or three. The game gives
     * them no grid: the tile definitions put two of them on one SpriteGridPos of a facing, and
     * the game drops every grid of a group that has a square twice. So each square is laid by
     * its own SpriteGridPos in a run across the way the desk faces.
     */
    private static Furniture straightDesk(IsoSprite sprite, String name, String holds, PropertyContainer properties, int turns, float surfaceHigh,
            float surfaceUp) {
        int along = Sheets.place(name) >= THREE_LONG ? 3 : 2;
        boolean alongY = (turns & 1) == 1;
        boolean[][] filled = new boolean[alongY ? 1 : along][alongY ? along : 1];
        for (boolean[] column : filled) {
            Arrays.fill(column, true);
        }
        String[] at = properties.get("SpriteGridPos").split(",");
        DeskRuns.Square square = DeskRuns.of(filled, Integer.parseInt(at[0].trim()), Integer.parseInt(at[1].trim()), holds.equals("desk"), turns);
        return deskSquare(sprite, square.model(), square.turns(), surfaceHigh, surfaceUp);
    }

    /** Where on the sheet the squares of "Office L" and of "Office L2" begin. */
    private static final int L_FIRST = 104, L2_FIRST = 136;
    /**
     * Where each square of those L desks lies in its three squares by three, as x then y, by its
     * place after the first: those facing east from 0, north from 8, south from 16, west from 24.
     * Read off the map's L desks: the tile definitions' SpriteGridPos agrees for east and south,
     * and for north and west puts squares on one another.
     */
    private static final int[][] L_SQUARES = {
        {0, 2}, {0, 1}, {0, 0}, {1, 0}, {2, 0}, null, null, null,
        {0, 2}, {1, 2}, {2, 2}, {2, 1}, {2, 0}, null, null, null,
        {0, 0}, {1, 0}, {2, 0}, {2, 1}, {2, 2}, null, null, null,
        {2, 2}, {1, 2}, {0, 2}, {0, 1}, {0, 0},
    };

    /**
     * A square of one of the sheet's desks laid in an L, "Office L" or "Office L2". The game
     * gives them no grid, as it gives the straight desks none, so each square is laid by where
     * it lies in its L.
     */
    private static Furniture lDesk(IsoSprite sprite, String name, String group, String holds, int turns, float surfaceHigh, float surfaceUp) {
        int which = Sheets.place(name) - (group.equals("Office L") ? L_FIRST : L2_FIRST), first = which / 8 * 8;
        boolean[][] filled = new boolean[3][3];
        for (int i = first; i < first + 5; i++) {
            filled[L_SQUARES[i][0]][L_SQUARES[i][1]] = true;
        }
        DeskRuns.Square square = DeskRuns.of(filled, L_SQUARES[which][0], L_SQUARES[which][1], holds.equals("desk"), turns);
        return deskSquare(sprite, square.model(), square.turns(), surfaceHigh, surfaceUp);
    }

    /**
     * A square of a desk as a piece of its own: a tile a metre along the ground, its top as high as the game gives. Its
     * top takes the colour the sprite paints the top, and what holds it up the colour the sprite paints that, where the
     * sprite paints it.
     */
    private static Furniture deskSquare(IsoSprite sprite, String model, int turns, float surfaceHigh, float surfaceUp) {
        Furniture desk = Furniture.single(sprite, model, turns).dye(1 << Models.WOOD, surfaceHigh).meet(surfaceUp, DESK_TOP, false);
        float[] body = bodyPlace(model, turns);
        if (body != null) {
            float[] at = new float[2];
            Models.turn(turns, body[0], body[1], at);
            desk.alsoAt(1 << Models.METAL, 0.5f + at[0], 0.5f + at[1], body[2] * desk.scaleUp / ViewCamera.LEVEL_HEIGHT);
        }
        return desk.raise(surfaceUp - desk.model.height() * desk.scaleUp);
    }

    /** Where on a desk square's model, in metres, the back board's outside, a free end's panel, the drawers' front and the corner's column are. */
    private static final float BOARD_Y = 0.42f, END_X = 0.44f, END_Y = -0.3f, DRAWERS_Y = -0.44f;
    /** How high on those, in metres, the body's colour is taken: clear of the top's edge, of the drawers' handles and of the shadow under them. */
    private static final float BOARD_HIGH = 0.4f, END_HIGH = 0.15f, COLUMN_HIGH = 0.3f;

    /**
     * Where a desk square's sprite paints what holds its top up, as x, y and height on the model in metres. The sprite
     * is drawn from the south east: the back board where its outside faces that way, else a free end's panel from
     * inside, under the top, else the drawers' front. Null for a length with its back board facing away, under whose
     * top the sprite paints nothing.
     */
    private static float[] bodyPlace(String model, int turns) {
        if (model.equals("desk_corner")) {
            return new float[] {0f, 0f, COLUMN_HIGH};
        }
        float[] back = new float[2];
        Models.turn(turns, 0f, 1f, back);
        if (back[0] > 0.5f || back[1] > 0.5f) {
            return new float[] {0f, BOARD_Y, BOARD_HIGH};
        }
        if (model.startsWith("desk_end")) {
            return new float[] {model.endsWith("_left") ? -END_X : END_X, END_Y, END_HIGH};
        }
        if (model.startsWith("desk_pedestal")) {
            return new float[] {0f, DRAWERS_Y, BOARD_HIGH};
        }
        return null;
    }

    /** How high the game gives the top of every desk of the office sheet, in its surface's pixels. */
    private static final int OFFICE_DESK_SURFACE = 33;

    /**
     * The squares of a desk of the office sheet that the game gives no name and no properties, laid
     * beside its desks to make an L of them: one with the board under its top on its east edge, one
     * on its south edge, the corner on a column, and an end with a panel to the floor on its east.
     * Their tops are as high as the sheet's desks'. Null for the rest of the sheet.
     */
    static Furniture unnamedDesk(IsoSprite sprite, String name) {
        int which = Sheets.place(name);
        String model = which == 2 || which == 4 ? "desk_run" : which == 3 ? "desk_corner" : which == 12 ? "desk_end_left" : null;
        if (model == null) {
            return null;
        }
        float surfaceHigh = OFFICE_DESK_SURFACE / WorldScene.SURFACE_PIXELS_PER_LEVEL, surfaceUp = surfaceHigh * ViewCamera.LEVEL_HEIGHT;
        return deskSquare(sprite, model, which == 2 ? 3 : 0, surfaceHigh, surfaceUp);
    }
}
