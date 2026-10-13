package com.cameraunlock.zomboid;

import java.util.List;
import zombie.iso.IsoDirections;

/** What a vehicle knocks down, how fast it has to go, and what that costs it. */
public final class KnockdownTests {
    private KnockdownTests() {}

    static void run() {
        Checks.run("fencesAndPostsAreKnockedDownAndNothingElse", KnockdownTests::fencesAndPostsAreKnockedDownAndNothingElse);
        Checks.run("onlyAVehicleDrivenFasterThanAWalkAloneKnocksThingsDown", KnockdownTests::onlyAVehicleDrivenFasterThanAWalkAloneKnocksThingsDown);
        Checks.run("aPostCostsMoreThanAFenceAndPiecesMetAtOnceCompound", KnockdownTests::aPostCostsMoreThanAFenceAndPiecesMetAtOnceCompound);
        Checks.run("theDamageGrowsWithTheSpeedEitherWay", KnockdownTests::theDamageGrowsWithTheSpeedEitherWay);
        Checks.run("onlyWhatIsAheadTheWayTheVehicleGoesIsMet", KnockdownTests::onlyWhatIsAheadTheWayTheVehicleGoesIsMet);
        Checks.run("aFenceIsLookedForAlongItsOwnEdge", KnockdownTests::aFenceIsLookedForAlongItsOwnEdge);
        Checks.run("aFenceFallsAwayFromTheVehicleAndTheEndThatHitIsDamaged", KnockdownTests::aFenceFallsAwayFromTheVehicleAndTheEndThatHitIsDamaged);
        Checks.run("onlyTheStreetLampOverAPostGoesWithIt", KnockdownTests::onlyTheStreetLampOverAPostGoesWithIt);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void fencesAndPostsAreKnockedDownAndNothingElse() {
        check(Knockdown.of("fencing_01_57", "MetalWire", true, true) == Knockdown.Kind.FENCE, "tall chain link is a fence");
        check(Knockdown.of("fencing_01_21", "MetalWire", true, true) == Knockdown.Kind.FENCE, "short chain link is a fence");
        check(Knockdown.of("fencing_01_81", "MetalWire", true, true) == Knockdown.Kind.FENCE, "the tall chain link the game brings down a span at a time is a fence");
        check(Knockdown.of("fencing_01_49", "MetalWire", false, true) == Knockdown.Kind.FENCE, "a wire fence the game does not know is a fence");
        check(Knockdown.of("fencing_damaged_01_81", "MetalWire", false, false) == null, "chain link lying broken on no edge is left lying");
        check(Knockdown.of("fencing_damaged_01_2", "MetalBars", true, true) == Knockdown.Kind.FENCE, "the piece either side of a hole the game can break is a fence");
        check(Knockdown.of("fencing_01_9", "Wood", false, true) == null, "a wooden fence the game does not know is left to the game");
        check(Knockdown.of("fencing_01_5", "Wood", true, true) == Knockdown.Kind.FENCE, "a white picket fence is a fence");
        check(Knockdown.of("fencing_01_19", "Wood", true, false) == Knockdown.Kind.FENCE, "a fence post goes with its fence");
        check(Knockdown.of("fencing_01_104", "Stone", true, true) == null, "a stone fence is a low wall and stands");
        check(Knockdown.of("lighting_outdoor_01_16", null, true, false) == Knockdown.Kind.POST, "the bare post under a tall street lamp is a post");
        check(Knockdown.of("lighting_outdoor_01_4", null, true, false) == Knockdown.Kind.POST, "and so is the black post under an acorn lamp");
        check(Knockdown.of("lighting_outdoor_01_24", null, false, false) == null, "a lamp on a wall is not a post");
        check(Knockdown.of("street_decoration_01_0", "MetalBars", true, false) == null, "a stop sign is left to the game");
        check(Knockdown.of("trashcontainers_01_0", "PlasticHard", true, false) == null, "and so is a bin");
        check(Knockdown.of("walls_exterior_house_01_0", "Wood", false, true) == null, "a wall stands");
        check(Knockdown.of(null, null, true, true) == null, "a thing with no sprite is nothing");
    }

    private static void onlyAVehicleDrivenFasterThanAWalkAloneKnocksThingsDown() {
        check(Knockdown.smashing(false, true, 50f), "alone, engine running, at 50 km/h");
        check(!Knockdown.smashing(true, true, 50f), "not on a server, whatever the speed");
        check(!Knockdown.smashing(false, false, 50f), "not a vehicle rolling with its engine off");
        check(!Knockdown.smashing(false, true, 5f), "not a push at a walk");
        check(!Knockdown.smashing(false, true, Knockdown.WALKING_KMH), "nor just at the limit");
        check(Knockdown.smashing(false, true, Knockdown.WALKING_KMH + 0.5f), "just over it");
        check(Knockdown.smashing(false, true, -20f), "reversing hard");
    }

    private static void aPostCostsMoreThanAFenceAndPiecesMetAtOnceCompound() {
        float fence = Knockdown.speedKept(Knockdown.Kind.FENCE), post = Knockdown.speedKept(Knockdown.Kind.POST);
        check(fence > post, "a post takes more of the speed than a fence panel");
        check(fence >= 0.85f && fence < 1f, "a fence panel takes a little: " + fence);
        check(post >= 0.5f && post < 0.8f, "a post takes a good share and the vehicle still goes on: " + post);
        float three = Knockdown.speedKept(List.of(Knockdown.Kind.FENCE, Knockdown.Kind.FENCE, Knockdown.Kind.FENCE));
        check(Math.abs(three - fence * fence * fence) < 1e-6f, "three panels at once each take their share of what is left: " + three);
        float seven = Knockdown.speedKept(List.of(Knockdown.Kind.FENCE, Knockdown.Kind.FENCE, Knockdown.Kind.FENCE, Knockdown.Kind.FENCE,
                Knockdown.Kind.FENCE, Knockdown.Kind.FENCE, Knockdown.Kind.FENCE));
        check(seven > 0.45f && seven < 0.5f, "a drift along seven panels keeps under half, and never goes backwards: " + seven);
        check(Knockdown.speedKept(List.of()) == 1f, "nothing met takes nothing");
        check(Knockdown.damage(Knockdown.Kind.POST, 50f) > Knockdown.damage(Knockdown.Kind.FENCE, 50f), "a post dents more than a fence panel");
    }

    private static void theDamageGrowsWithTheSpeedEitherWay() {
        check(Knockdown.damage(Knockdown.Kind.FENCE, 50f) == 3, "a fence panel at 50 km/h: " + Knockdown.damage(Knockdown.Kind.FENCE, 50f));
        check(Knockdown.damage(Knockdown.Kind.POST, 50f) == 10, "a post at 50 km/h: " + Knockdown.damage(Knockdown.Kind.POST, 50f));
        check(Knockdown.damage(Knockdown.Kind.POST, 100f) > Knockdown.damage(Knockdown.Kind.POST, 50f), "faster hits harder");
        check(Knockdown.damage(Knockdown.Kind.FENCE, -40f) == Knockdown.damage(Knockdown.Kind.FENCE, 40f), "backwards as hard as forwards");
    }

    private static void onlyWhatIsAheadTheWayTheVehicleGoesIsMet() {
        // A box 2 tiles wide and 4.5 long, going forward at 50 km/h.
        float across = 1f, along = 2.25f, ahead = Knockdown.ahead(13.9f);
        check(ahead > 1.3f && ahead < 1.5f, "at 50 km/h a thing is met about a tile and a half ahead: " + ahead);
        check(Knockdown.ahead(0f) == 0f, "standing, nothing is met ahead of the box");
        check(Knockdown.met(0f, along + 1f, 0f, 13.9f, across, along, ahead), "a fence a tile ahead of the bumper is met");
        check(Knockdown.met(0.9f, along + 1f, 0f, 13.9f, across, along, ahead), "across the whole width of the bumper");
        check(!Knockdown.met(0f, along + 2f, 0f, 13.9f, across, along, ahead), "one two tiles ahead is not, yet");
        check(!Knockdown.met(across + 0.1f, 1f, 0f, 13.9f, across, along, ahead), "nor a fence the side passes a tenth of a tile off");
        check(!Knockdown.met(across + 0.1f, -1f, 0f, 13.9f, across, along, ahead), "nor one alongside the back half");
        check(!Knockdown.met(0f, -along - 0.1f, 0f, 13.9f, across, along, ahead), "nor one behind, going forward");
        check(Knockdown.met(0f, -along - 0.1f, 0f, -5f, across, along, Knockdown.ahead(5f)), "but the back meets what it reverses onto");
        check(Knockdown.met(across + 0.3f, 0.5f, 10f, 1f, across, along, ahead), "drifting sideways, the side meets the fence it slides onto");
        check(!Knockdown.met(-across - 0.3f, 0.5f, 10f, 1f, across, along, ahead), "and not the one on the other side");
        check(!Knockdown.met(0f, along + 0.1f, 0f, 0f, across, along, ahead), "standing, nothing is met");
        check(Knockdown.met(0f, along + 1f, 0f, 1f, across, along, ahead) == Knockdown.met(0f, along + 1f, 0f, 50f, across, along, ahead),
                "the way it goes counts, not the length it is given in");
    }

    private static void aFenceIsLookedForAlongItsOwnEdge() {
        check(Knockdown.edgeX(10, true, 0.5f) == 10.5f && Knockdown.edgeY(20, true, 0.5f) == 20f, "a north edge runs along x at the square's y");
        check(Knockdown.edgeX(10, false, 0.5f) == 10f && Knockdown.edgeY(20, false, 0.5f) == 20.5f, "a west edge runs along y at the square's x");
    }

    private static void aFenceFallsAwayFromTheVehicleAndTheEndThatHitIsDamaged() {
        check(Knockdown.breakTowards(true, 5f, 9.5f, 5f, 10f) == IsoDirections.S, "a north fence hit from the north falls south");
        check(Knockdown.breakTowards(true, 5f, 10.5f, 5f, 10f) == IsoDirections.N, "and hit from the south falls north");
        check(Knockdown.breakTowards(false, 4.5f, 10f, 5f, 10f) == IsoDirections.E, "a west fence hit from the west falls east");
        check(Knockdown.breakTowards(false, 5.5f, 10f, 5f, 10f) == IsoDirections.W, "and hit from the east falls west");
        check(Knockdown.front(13.9f), "going forward the front takes it");
        check(!Knockdown.front(-5f), "reversing the back does");
    }

    private static void onlyTheStreetLampOverAPostGoesWithIt() {
        check(Knockdown.streetLamp("lighting_outdoor_01_11", null), "the cobra head over a tall post");
        check(Knockdown.streetLamp("lighting_outdoor_01_5", null), "the acorn over a black post");
        check(!Knockdown.streetLamp("lighting_outdoor_01_24", "Outdoor Lamp"), "not a lamp on a wall");
        check(!Knockdown.streetLamp("lighting_outdoor_01_42", "Sign"), "nor a neon sign");
        check(!Knockdown.streetLamp("lighting_outdoor_01_48", "Lights"), "nor a floodlight");
        check(!Knockdown.streetLamp("walls_exterior_house_01_0", null), "nor anything else");
    }
}
