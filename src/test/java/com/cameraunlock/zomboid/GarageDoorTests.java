package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import zombie.core.properties.PropertyContainer;
import zombie.iso.sprite.IsoSprite;

/** Tests that a garage door, shut or open, is never taken for masonry or for a building's finish. */
final class GarageDoorTests {
    private GarageDoorTests() {}

    static void run() {
        Checks.run("noGarageDoorIsLaidAsMasonryOrIsAFinish", GarageDoorTests::noGarageDoorIsLaidAsMasonryOrIsAFinish);
        Checks.run("aPlainWallOfAGarageIsStillLaidAndAFinish", GarageDoorTests::aPlainWallOfAGarageIsStillLaidAndAFinish);
    }

    /** Every sprite of the game's tile definitions with a GarageDoor property, each with all its properties as the census of them read them. */
    private static final String[] GARAGE_DOORS = {
            "industry_trucks_01_32|CutawayHint|GarageDoorLeft|GarageDoor|1|MaterialType|Metal_Light|WallW||attachedW||doorW||wall||DoorSound|GarageDoor",
            "industry_trucks_01_33|CutawayHint|GarageDoorMiddle|GarageDoor|2|MaterialType|Metal_Light|WallW||attachedW||doorW||wall||DoorSound|GarageDoor",
            "industry_trucks_01_34|CutawayHint|GarageDoorRight|GarageDoor|3|MaterialType|Metal_Light|WallW||attachedW||doorW||wall||DoorSound|GarageDoor",
            "industry_trucks_01_35|CutawayHint|GarageDoorLeft|GarageDoor|1|MaterialType|Metal_Light|WallN||attachedN||doorN||wall||DoorSound|GarageDoor",
            "industry_trucks_01_36|CutawayHint|GarageDoorMiddle|GarageDoor|2|MaterialType|Metal_Light|WallN||attachedN||doorN||wall||DoorSound|GarageDoor",
            "industry_trucks_01_37|CutawayHint|GarageDoorRight|GarageDoor|3|MaterialType|Metal_Light|WallN||attachedN||doorN||wall||DoorSound|GarageDoor",
            "industry_trucks_01_40|CutawayHint|GarageDoorLeft|DoorWallW||GarageDoor|4|attachedW||doorW||wall||DoorSound|GarageDoor",
            "industry_trucks_01_41|CutawayHint|GarageDoorMiddle|DoorWallW||GarageDoor|5|attachedW||doorW||wall||DoorSound|GarageDoor",
            "industry_trucks_01_42|CutawayHint|GarageDoorRight|DoorWallW||GarageDoor|6|attachedW||doorW||wall||DoorSound|GarageDoor",
            "industry_trucks_01_43|CutawayHint|GarageDoorLeft|DoorWallN||GarageDoor|4|attachedN||doorN||wall||DoorSound|GarageDoor",
            "industry_trucks_01_44|CutawayHint|GarageDoorMiddle|DoorWallN||GarageDoor|5|attachedN||doorN||wall||DoorSound|GarageDoor",
            "industry_trucks_01_45|CutawayHint|GarageDoorRight|DoorWallN||GarageDoor|6|attachedN||doorN||wall||DoorSound|GarageDoor",
            "walls_garage_01_0|CanScrap||CutawayHint|GarageDoorLeft|GarageDoor|1|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallW||doorW||wall||DoorSound|GarageDoor",
            "walls_garage_01_1|CanScrap||CutawayHint|GarageDoorMiddle|GarageDoor|2|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallW||doorW||wall||DoorSound|GarageDoor",
            "walls_garage_01_2|CanScrap||CutawayHint|GarageDoorRight|GarageDoor|3|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallW||doorW||wall||DoorSound|GarageDoor",
            "walls_garage_01_3|CanScrap||CutawayHint|GarageDoorLeft|GarageDoor|1|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallN||doorN||wall||DoorSound|GarageDoor",
            "walls_garage_01_4|CanScrap||CutawayHint|GarageDoorMiddle|GarageDoor|2|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallN||doorN||wall||DoorSound|GarageDoor",
            "walls_garage_01_5|CanScrap||CutawayHint|GarageDoorRight|GarageDoor|3|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallN||doorN||wall||DoorSound|GarageDoor",
            "walls_garage_01_8|CutawayHint|GarageDoorLeft|GarageDoor|4|GrimeType|FullWindow|doorW||DoorSound|GarageDoor",
            "walls_garage_01_9|CutawayHint|GarageDoorMiddle|GarageDoor|5|GrimeType|FullWindow|doorW||DoorSound|GarageDoor",
            "walls_garage_01_10|CutawayHint|GarageDoorRight|GarageDoor|6|GrimeType|FullWindow|doorW||DoorSound|GarageDoor",
            "walls_garage_01_11|CutawayHint|GarageDoorLeft|GarageDoor|4|GrimeType|FullWindow|doorN||DoorSound|GarageDoor",
            "walls_garage_01_12|CutawayHint|GarageDoorMiddle|GarageDoor|5|GrimeType|FullWindow|doorN||DoorSound|GarageDoor",
            "walls_garage_01_13|CutawayHint|GarageDoorRight|GarageDoor|6|GrimeType|FullWindow|doorN||DoorSound|GarageDoor",
            "walls_garage_01_16|CanScrap||CutawayHint|GarageDoorLeft|GarageDoor|1|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallW||doorW||wall||DoorSound|GarageDoor",
            "walls_garage_01_17|CanScrap||CutawayHint|GarageDoorMiddle|GarageDoor|2|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallW||doorW||wall||DoorSound|GarageDoor",
            "walls_garage_01_18|CanScrap||CutawayHint|GarageDoorRight|GarageDoor|3|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallW||doorW||wall||DoorSound|GarageDoor",
            "walls_garage_01_19|CanScrap||CutawayHint|GarageDoorLeft|GarageDoor|1|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallN||doorN||wall||DoorSound|GarageDoor",
            "walls_garage_01_20|CanScrap||CutawayHint|GarageDoorMiddle|GarageDoor|2|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallN||doorN||wall||DoorSound|GarageDoor",
            "walls_garage_01_21|CanScrap||CutawayHint|GarageDoorRight|GarageDoor|3|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallN||doorN||wall||DoorSound|GarageDoor",
            "walls_garage_01_24|CutawayHint|GarageDoorLeft|GarageDoor|4|GrimeType|FullWindow|doorW||DoorSound|GarageDoor",
            "walls_garage_01_25|CutawayHint|GarageDoorMiddle|GarageDoor|5|GrimeType|FullWindow|doorW||DoorSound|GarageDoor",
            "walls_garage_01_26|CutawayHint|GarageDoorRight|GarageDoor|6|GrimeType|FullWindow|doorW||DoorSound|GarageDoor",
            "walls_garage_01_27|CutawayHint|GarageDoorLeft|GarageDoor|4|GrimeType|FullWindow|doorN||DoorSound|GarageDoor",
            "walls_garage_01_28|CutawayHint|GarageDoorMiddle|GarageDoor|5|GrimeType|FullWindow|doorN||DoorSound|GarageDoor",
            "walls_garage_01_29|CutawayHint|GarageDoorRight|GarageDoor|6|GrimeType|FullWindow|doorN||DoorSound|GarageDoor",
            "walls_garage_01_48|CanScrap||CutawayHint|GarageDoorLeft|GarageDoor|1|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallW||doorW||wall||DoorSound|GarageDoor",
            "walls_garage_01_49|CanScrap||CutawayHint|GarageDoorMiddle|GarageDoor|2|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallW||doorW||wall||DoorSound|GarageDoor",
            "walls_garage_01_50|CanScrap||CutawayHint|GarageDoorRight|GarageDoor|3|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallW||doorW||wall||DoorSound|GarageDoor",
            "walls_garage_01_51|CanScrap||CutawayHint|GarageDoorLeft|GarageDoor|1|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallN||doorN||wall||DoorSound|GarageDoor",
            "walls_garage_01_52|CanScrap||CutawayHint|GarageDoorMiddle|GarageDoor|2|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallN||doorN||wall||DoorSound|GarageDoor",
            "walls_garage_01_53|CanScrap||CutawayHint|GarageDoorRight|GarageDoor|3|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallN||doorN||wall||DoorSound|GarageDoor",
            "walls_garage_01_56|CutawayHint|GarageDoorLeft|GarageDoor|4|GrimeType|FullWindow|doorW||DoorSound|GarageDoor",
            "walls_garage_01_57|CutawayHint|GarageDoorMiddle|GarageDoor|5|GrimeType|FullWindow|doorW||DoorSound|GarageDoor",
            "walls_garage_01_58|CutawayHint|GarageDoorRight|GarageDoor|6|GrimeType|FullWindow|doorW||DoorSound|GarageDoor",
            "walls_garage_01_59|CutawayHint|GarageDoorLeft|GarageDoor|4|GrimeType|FullWindow|doorN||DoorSound|GarageDoor",
            "walls_garage_01_60|CutawayHint|GarageDoorMiddle|GarageDoor|5|GrimeType|FullWindow|doorN||DoorSound|GarageDoor",
            "walls_garage_01_61|CutawayHint|GarageDoorRight|GarageDoor|6|GrimeType|FullWindow|doorN||DoorSound|GarageDoor",
            "walls_garage_02_0|CanScrap||CutawayHint|GarageDoorLeft|GarageDoor|1|GrimeType|FullWindow|InteriorSide||Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallW||doorW||wall||DoorSound|GarageDoor",
            "walls_garage_02_1|CanScrap||CutawayHint|GarageDoorMiddle|GarageDoor|2|GrimeType|FullWindow|InteriorSide||Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallW||doorW||wall||DoorSound|GarageDoor",
            "walls_garage_02_2|CanScrap||CutawayHint|GarageDoorRight|GarageDoor|3|GrimeType|FullWindow|InteriorSide||Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallW||doorW||wall||DoorSound|GarageDoor",
            "walls_garage_02_3|CanScrap||CutawayHint|GarageDoorLeft|GarageDoor|1|GrimeType|FullWindow|InteriorSide||Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallN||doorN||wall||DoorSound|GarageDoor",
            "walls_garage_02_4|CanScrap||CutawayHint|GarageDoorMiddle|GarageDoor|2|GrimeType|FullWindow|InteriorSide||Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallN||doorN||wall||DoorSound|GarageDoor",
            "walls_garage_02_5|CanScrap||CutawayHint|GarageDoorRight|GarageDoor|3|GrimeType|FullWindow|InteriorSide||Material|MetalPlates|Material2|MetalPlates|MaterialType|Metal_Light|WallN||doorN||wall||DoorSound|GarageDoor",
            "walls_garage_02_8|CutawayHint|GarageDoorLeft|GarageDoor|4|GrimeType|FullWindow|InteriorSide||doorW||DoorSound|GarageDoor",
            "walls_garage_02_9|CutawayHint|GarageDoorMiddle|GarageDoor|5|GrimeType|FullWindow|InteriorSide||doorW||DoorSound|GarageDoor",
            "walls_garage_02_10|CutawayHint|GarageDoorRight|GarageDoor|6|GrimeType|FullWindow|InteriorSide||doorW||DoorSound|GarageDoor",
            "walls_garage_02_11|CutawayHint|GarageDoorLeft|GarageDoor|4|GrimeType|FullWindow|InteriorSide||doorN||DoorSound|GarageDoor",
            "walls_garage_02_12|CutawayHint|GarageDoorMiddle|GarageDoor|5|GrimeType|FullWindow|InteriorSide||doorN||DoorSound|GarageDoor",
            "walls_garage_02_13|CutawayHint|GarageDoorRight|GarageDoor|6|GrimeType|FullWindow|InteriorSide||doorN||DoorSound|GarageDoor",
            "walls_garage_02_32|CanScrap||CutawayHint|GarageDoorLeft|GarageDoor|1|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|Material3|Glass|MaterialType|Glass|WallW||doorTrans||doorW||wall||DoorSound|GarageDoor",
            "walls_garage_02_33|CanScrap||CutawayHint|GarageDoorMiddle|GarageDoor|2|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|Material3|Glass|MaterialType|Glass|WallW||doorTrans||doorW||wall||DoorSound|GarageDoor",
            "walls_garage_02_34|CanScrap||CutawayHint|GarageDoorRight|GarageDoor|3|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|Material3|Glass|MaterialType|Glass|WallW||doorTrans||doorW||wall||DoorSound|GarageDoor",
            "walls_garage_02_35|CanScrap||CutawayHint|GarageDoorLeft|GarageDoor|1|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|Material3|Glass|MaterialType|Glass|WallN||doorN||doorTrans||wall||DoorSound|GarageDoor",
            "walls_garage_02_36|CanScrap||CutawayHint|GarageDoorMiddle|GarageDoor|2|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|Material3|Glass|MaterialType|Glass|WallN||doorN||doorTrans||wall||DoorSound|GarageDoor",
            "walls_garage_02_37|CanScrap||CutawayHint|GarageDoorRight|GarageDoor|3|GrimeType|FullWindow|Material|MetalPlates|Material2|MetalPlates|Material3|Glass|MaterialType|Glass|WallN||doorN||doorTrans||wall||DoorSound|GarageDoor",
            "walls_garage_02_40|CutawayHint|GarageDoorLeft|GarageDoor|4|GrimeType|FullWindow|doorW||DoorSound|GarageDoor",
            "walls_garage_02_41|CutawayHint|GarageDoorMiddle|GarageDoor|5|GrimeType|FullWindow|doorW||DoorSound|GarageDoor",
            "walls_garage_02_42|CutawayHint|GarageDoorRight|GarageDoor|6|GrimeType|FullWindow|doorW||DoorSound|GarageDoor",
            "walls_garage_02_43|CutawayHint|GarageDoorLeft|GarageDoor|4|GrimeType|FullWindow|doorN||DoorSound|GarageDoor",
            "walls_garage_02_44|CutawayHint|GarageDoorMiddle|GarageDoor|5|GrimeType|FullWindow|doorN||DoorSound|GarageDoor",
            "walls_garage_02_45|CutawayHint|GarageDoorRight|GarageDoor|6|GrimeType|FullWindow|doorN||DoorSound|GarageDoor",
            "walls_garage_02_48|CanScrap||CutawayHint|GarageDoorLeft|GarageDoor|1|GrimeType|FullWindow|InteriorSide||Material|MetalPlates|Material2|MetalPlates|Material3|Glass|MaterialType|Glass|WallW||doorTrans||doorW||wall||DoorSound|GarageDoor",
            "walls_garage_02_49|CanScrap||CutawayHint|GarageDoorMiddle|GarageDoor|2|GrimeType|FullWindow|InteriorSide||Material|MetalPlates|Material2|MetalPlates|Material3|Glass|MaterialType|Glass|WallW||doorTrans||doorW||wall||DoorSound|GarageDoor",
            "walls_garage_02_50|CanScrap||CutawayHint|GarageDoorRight|GarageDoor|3|GrimeType|FullWindow|InteriorSide||Material|MetalPlates|Material2|MetalPlates|Material3|Glass|MaterialType|Glass|WallW||doorTrans||doorW||wall||DoorSound|GarageDoor",
            "walls_garage_02_51|CanScrap||CutawayHint|GarageDoorLeft|GarageDoor|1|GrimeType|FullWindow|InteriorSide||Material|MetalPlates|Material2|MetalPlates|Material3|Glass|MaterialType|Glass|WallN||doorN||doorTrans||wall||DoorSound|GarageDoor",
            "walls_garage_02_52|CanScrap||CutawayHint|GarageDoorMiddle|GarageDoor|2|GrimeType|FullWindow|InteriorSide||Material|MetalPlates|Material2|MetalPlates|Material3|Glass|MaterialType|Glass|WallN||doorN||doorTrans||wall||DoorSound|GarageDoor",
            "walls_garage_02_53|CanScrap||CutawayHint|GarageDoorRight|GarageDoor|3|GrimeType|FullWindow|InteriorSide||Material|MetalPlates|Material2|MetalPlates|Material3|Glass|MaterialType|Glass|WallN||doorN||doorTrans||wall||DoorSound|GarageDoor",
            "walls_garage_02_56|CutawayHint|GarageDoorLeft|GarageDoor|4|GrimeType|FullWindow|InteriorSide||doorW||DoorSound|GarageDoor",
            "walls_garage_02_57|CutawayHint|GarageDoorMiddle|GarageDoor|5|GrimeType|FullWindow|InteriorSide||doorW||DoorSound|GarageDoor",
            "walls_garage_02_58|CutawayHint|GarageDoorRight|GarageDoor|6|GrimeType|FullWindow|InteriorSide||doorW||DoorSound|GarageDoor",
            "walls_garage_02_59|CutawayHint|GarageDoorLeft|GarageDoor|4|GrimeType|FullWindow|InteriorSide||doorN||DoorSound|GarageDoor",
            "walls_garage_02_60|CutawayHint|GarageDoorMiddle|GarageDoor|5|GrimeType|FullWindow|InteriorSide||doorN||DoorSound|GarageDoor",
            "walls_garage_02_61|CutawayHint|GarageDoorRight|GarageDoor|6|GrimeType|FullWindow|InteriorSide||doorN||DoorSound|GarageDoor",
    };

    private static void noGarageDoorIsLaidAsMasonryOrIsAFinish() {
        check(GARAGE_DOORS.length == 84, "the table holds every garage door sprite of the tile definitions");
        int onAWallSheet = 0;
        for (String row : GARAGE_DOORS) {
            IsoSprite sprite = TileRows.sprite(row);
            PropertyContainer properties = sprite.getProperties();
            onAWallSheet += Masonry.suits(sprite.getName()) ? 1 : 0;
            check(!WorldScene.Kind.laidWall(sprite.getName(), properties), sprite.getName() + " is not laid as masonry");
            check(!WallFinish.votes(properties), sprite.getName() + " has no say in its building's finish");
            check(WallFinish.opens(properties), sprite.getName() + " is no plain wall of a finish");
        }
        check(onAWallSheet == 72, "the garage doors of the sheets of outside walls are among them: " + onAWallSheet);
    }

    private static void aPlainWallOfAGarageIsStillLaidAndAFinish() {
        IsoSprite wall = TileRows.sprite("walls_garage_01_33|WallN||wall|");
        check(WorldScene.Kind.laidWall(wall.getName(), wall.getProperties()), "a garage's plain wall is laid as masonry");
        check(WallFinish.votes(wall.getProperties()) && !WallFinish.opens(wall.getProperties()), "and is a finish");
    }
}
