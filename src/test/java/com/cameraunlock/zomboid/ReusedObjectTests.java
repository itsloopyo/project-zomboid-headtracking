package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.lang.reflect.Field;
import java.util.Map;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.sprite.IsoSprite;

/**
 * The game hands a plain object it reset out again for any square, under the same object: nothing
 * the mod found or kept of it outlives that.
 */
final class ReusedObjectTests {
    private ReusedObjectTests() {}

    static void run() {
        Checks.run("aResetObjectKeepsNoHolesNorBrokenPickets", ReusedObjectTests::aResetObjectKeepsNoHolesNorBrokenPickets);
        Checks.run("aWallPiecesMaterialHoldsOnlyWhereAndAsItWasFound", ReusedObjectTests::aWallPiecesMaterialHoldsOnlyWhereAndAsItWasFound);
    }

    private static void aResetObjectKeepsNoHolesNorBrokenPickets() throws Throwable {
        TestMap map = new TestMap();
        IsoObject reset = map.cell.square(0, 1, 0).add(map.sprite("walls_exterior_house_01_1", IsoObjectType.wall, "WallN"));
        IsoObject other = map.cell.square(1, 1, 0).add(map.sprite("walls_exterior_house_01_1", IsoObjectType.wall, "WallN"));
        Map<IsoObject, float[]> holes = TestMap.read(null, BlastHoles.class, "kept");
        Map<IsoObject, long[]> pickets = TestMap.read(null, ShotPickets.class, "kept");
        Field holesAny = BlastHoles.class.getDeclaredField("any"), picketsAny = ShotPickets.class.getDeclaredField("any");
        holesAny.setAccessible(true);
        picketsAny.setAccessible(true);
        boolean holesWere = holesAny.getBoolean(null), picketsWere = picketsAny.getBoolean(null);
        try {
            holes.put(reset, new float[] {1f});
            holes.put(other, new float[] {2f});
            pickets.put(reset, new long[] {1L, 0L});
            pickets.put(other, new long[] {2L, 0L});
            holesAny.setBoolean(null, true);
            picketsAny.setBoolean(null, true);
            BlastHoles.reused(reset);
            ShotPickets.reused(reset);
            check(BlastHoles.of(reset) == null, "an object the game reset keeps none of the holes shot in it where it stood");
            check(ShotPickets.of(reset) == null, "nor the pickets broken off it");
            check(BlastHoles.of(other) != null && ShotPickets.of(other) != null, "another object keeps its own");
        } finally {
            holes.remove(reset);
            holes.remove(other);
            pickets.remove(reset);
            pickets.remove(other);
            holesAny.setBoolean(null, holesWere);
            picketsAny.setBoolean(null, picketsWere);
        }
    }

    private static void aWallPiecesMaterialHoldsOnlyWhereAndAsItWasFound() {
        TestMap map = new TestMap();
        IsoSprite siding = map.sprite("walls_exterior_house_03_1", IsoObjectType.wall, "WallN");
        IsoGridSquare square = map.cell.square(4, 7, 1);
        IsoObject piece = square.add(siding);
        long place = FigureLights.key(4, 7, 1);
        Map<IsoObject, WallRuns.Found> found = TestMap.read(null, WallRuns.class, "found");
        try {
            found.put(piece, new WallRuns.Found(place, siding, "Wood"));
            check("Wood".equals(WallRuns.materialOf(piece)), "a piece is what it was found made of where it was found");
            WallRuns.Found known = found.get(piece);
            check(!known.holds(FigureLights.key(5, 7, 1), siding), "not once the game hands it out for another square");
            check(!known.holds(place, map.sprite("walls_exterior_house_03_8", IsoObjectType.wall, "WallW")), "nor as another piece on the same square");
            WallRuns.forget(piece);
            check(!found.containsKey(piece), "a piece the game reset is forgotten");
            found.put(piece, new WallRuns.Found(place, siding, "Wood"));
            WallRuns.forget();
            check(found.isEmpty(), "and every piece is as the save is left");
        } finally {
            found.remove(piece);
        }
    }
}
