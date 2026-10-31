package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.List;
import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteManager;

/**
 * What the pieces of a house's inside wall are made of where the game names nothing: see
 * InsideWalls. The rows are the game's tile definitions. Placed in the shipped map with no
 * MaterialType on the inside wall sheets: doorway pieces 89,340, window pieces 46,718, corner
 * posts 33,064, the double window frames of walls_interior_house_06 and 27 1,660 (census of the
 * map's placements, 2026-10-09).
 */
final class InsideWallTests {
    private InsideWallTests() {}

    private static final String PLAIN_NORTH = "walls_interior_house_01_1|IsPaintable||MaterialType|Plaster|PaintingType|wall|WallN||WallType|wall|wall|",
            PLAIN_CORNER = "walls_interior_house_01_2|CornerNorthWall|walls_interior_house_01_1|CornerWestWall|walls_interior_house_01_0|IsPaintable||MaterialType|Plaster|PaintingType|wall|WallNW||WallType|wall|wall|",
            DOORWAY = "walls_interior_house_01_11|DoorWallN||IsPaintable||PaintingType|doorframe|WallType|doorframe|wall|",
            WINDOW = "walls_interior_house_01_9|IsPaintable||PaintingType|windowsframe|WallType|windowsframe|WindowN||wall|",
            POST = "walls_interior_house_01_35|WallSE||wall|",
            SHAPED_WINDOW = "walls_interior_house_18_13|WindowN||WindowShape|7|wall|",
            SHAPED_SHEETMATE = "walls_interior_house_18_1|IsPaintable||PaintingType|windowsframe|WindowN||WindowShape|1|wall|",
            DOUBLE_WINDOW = "walls_interior_house_06_2|CutawayHint|DoubleWindowLeft|GrimeType|DoubleLeft|IsPaintable||PaintingType|dblwindowframeleft|WindowN||wall|",
            TILED = "walls_interior_bathroom_01_1|MaterialType|Ceramic|WallN||wall|",
            BLOCK = "walls_interior_bathroom_01_5|MaterialType|Cinderblock|WallN||wall|",
            TILED_DOORWAY = "walls_interior_bathroom_01_11|DoorWallN||wall|",
            WOODEN_DOORWAY = "walls_exterior_wooden_01_11|DoorWallN||IsPaintable||wall|",
            WOODEN_WALL = "walls_exterior_wooden_01_1|IsPaintable||MaterialType|Wood|WallN||wall|",
            CRATE = "carpentry_01_16|IsPaintable||PaintingType|crates|MaterialType|Wood|";

    private static final String[] GAME = {PLAIN_NORTH, PLAIN_CORNER, DOORWAY, WINDOW, POST, SHAPED_WINDOW, SHAPED_SHEETMATE, DOUBLE_WINDOW, TILED, BLOCK,
            TILED_DOORWAY, WOODEN_DOORWAY, WOODEN_WALL, CRATE};

    static void run() {
        Checks.run("aDoorwayOrAWindowInAnInsideWallIsPlasterboard", InsideWallTests::aDoorwayOrAWindowInAnInsideWallIsPlasterboard);
        Checks.run("aWallTheGameNamesNoMaterialForStillStops", InsideWallTests::aWallTheGameNamesNoMaterialForStillStops);
        Checks.run("roundsAndBlastsGoThroughAnInsideWallsDoorwayPiece", InsideWallTests::roundsAndBlastsGoThroughAnInsideWallsDoorwayPiece);
    }

    private static List<IsoSprite> sprites(String... rows) {
        List<IsoSprite> all = new ArrayList<>();
        for (String row : rows) {
            all.add(TileRows.sprite(row));
        }
        return all;
    }

    private static String of(InsideWalls walls, String row) {
        return walls.of(TileRows.sprite(row));
    }

    private static void aDoorwayOrAWindowInAnInsideWallIsPlasterboard() {
        InsideWalls walls = InsideWalls.read(sprites(GAME));
        Checks.check("Plaster".equals(walls.material), "the game's painted walls are plaster: " + walls.material);
        Checks.check("Plaster".equals(of(walls, DOORWAY)), "the piece of an inside wall round a doorway is plasterboard");
        Checks.check("Plaster".equals(of(walls, WINDOW)), "and the piece round a window");
        Checks.check("Plaster".equals(of(walls, POST)), "and a corner post the game paints nothing on, on the same sheet");
        Checks.check("Plaster".equals(of(walls, SHAPED_WINDOW)), "and a shaped window frame on a sheet of window frames the game paints");
        Checks.check("Plaster".equals(of(walls, DOUBLE_WINDOW)), "and a double window's frame, on a sheet with no plain wall");
        Checks.check("Plaster".equals(of(walls, PLAIN_NORTH)) && "Ceramic".equals(of(walls, TILED)), "a piece the game names a material for keeps it");
        Checks.check(Penetration.cost(of(walls, DOORWAY)) == Penetration.cost("Plaster"), "a round spends on a doorway's piece what it spends on the wall beside it");
    }

    private static void aWallTheGameNamesNoMaterialForStillStops() {
        InsideWalls walls = InsideWalls.read(sprites(GAME));
        Checks.check(of(walls, TILED_DOORWAY) == null, "the doorway of a tiled wall over block names nothing, and stops a round");
        Checks.check(of(walls, WOODEN_DOORWAY) == null, "nor is an outside wall's doorway given a material, painted or not");
        Checks.check(of(walls, "carpentry_01_17|IsPaintable||PaintingType|crates|") == null, "something painted that is no wall is not a wall's piece");
        InsideWalls disagreeing = InsideWalls.read(sprites(PLAIN_NORTH, DOORWAY, "walls_interior_house_99_1|PaintingType|wall|MaterialType|Wood|WallN||wall|"));
        Checks.check(disagreeing.material == null && of(disagreeing, DOORWAY) == null,
                "where the painted walls name two materials no doorway is given either, and it stops a round");
        Checks.check(of(InsideWalls.read(sprites(DOORWAY)), DOORWAY) == null, "nor where no painted wall names one");
    }

    /** As the game has it: Penetration and BlastHoles read the game's own sprites. */
    private static void roundsAndBlastsGoThroughAnInsideWallsDoorwayPiece() {
        for (IsoSprite sprite : sprites(GAME)) {
            IsoSpriteManager.instance.namedMap.put(sprite.getName(), sprite);
        }
        IsoObject doorway = new IsoObject(TileRows.sprite(DOORWAY)), tiled = new IsoObject(TileRows.sprite(TILED_DOORWAY));
        IsoObject plain = new IsoObject(TileRows.sprite(PLAIN_NORTH)), corner = new IsoObject(TileRows.sprite(PLAIN_CORNER));
        IsoObject boards = new IsoObject(TileRows.sprite(WOODEN_WALL));
        Checks.check(Penetration.costOf(doorway) == Penetration.cost("Plaster"),
                "a round spends on an inside doorway's piece what it spends on plaster (if not, the game's sprites were read before this test put these among them)");
        Checks.check(Penetration.costOf(tiled) == Penetration.STOPS, "a tiled doorway stops it");
        Checks.check(BlastHoles.holedByRounds(doorway) && !BlastHoles.holedByRounds(tiled), "a round leaves its hole in the doorway's plasterboard, none in tiles");
        Checks.check(BlastHoles.blastable(doorway) && BlastHoles.blastable(plain) && BlastHoles.blastable(corner),
                "a blast blows its hole through plasterboard: a doorway's piece, a plain length and a corner");
        Checks.check(!BlastHoles.blastable(tiled) && !BlastHoles.blastable(boards),
                "but not through tiles over block, nor a wall of boards, which stops a pellet");
        Checks.check(Penetration.cost("Plaster") < Penetration.Cartridge.SHOT.budget, "a shotgun's pellet goes on through plasterboard, as a blast through it has to");
    }
}
