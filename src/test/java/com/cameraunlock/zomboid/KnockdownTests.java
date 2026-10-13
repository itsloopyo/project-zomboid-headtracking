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
        Checks.run("theCrashIsTheGamesForTheSpeedLost", KnockdownTests::theCrashIsTheGamesForTheSpeedLost);
        Checks.run("onlyWhatIsAheadTheWayTheVehicleGoesIsMet", KnockdownTests::onlyWhatIsAheadTheWayTheVehicleGoesIsMet);
        Checks.run("aFenceIsLookedForAlongItsOwnEdge", KnockdownTests::aFenceIsLookedForAlongItsOwnEdge);
        Checks.run("aFenceFallsAwayFromTheVehicleAndTheEndThatHitIsDamaged", KnockdownTests::aFenceFallsAwayFromTheVehicleAndTheEndThatHitIsDamaged);
        Checks.run("onlyTheStreetLampOverAPostGoesWithIt", KnockdownTests::onlyTheStreetLampOverAPostGoesWithIt);
        Checks.run("aloneBushesDoNotSlowACarAndTheEngineIsWholeOffTheRoad", KnockdownTests::aloneBushesDoNotSlowACarAndTheEngineIsWholeOffTheRoad);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void aloneBushesDoNotSlowACarAndTheEngineIsWholeOffTheRoad() {
        check(Knockdown.brushedThrough(false, true, false), "alone a car goes through what only slows it unslowed");
        check(!Knockdown.brushedThrough(true, true, false), "on a server the game slows it");
        check(!Knockdown.brushedThrough(false, true, true), "a thing a car can also hit is the game's to handle");
        check(!Knockdown.brushedThrough(false, false, false), "and a thing that does not slow a car is nothing to go through");
        check(!Knockdown.engineHeldOffRoad(false, true), "alone the engine gives all of itself off the road");
        check(Knockdown.engineHeldOffRoad(true, true), "on a server the game holds it back off the road");
        check(!Knockdown.engineHeldOffRoad(true, false), "and on the road it never is");
        check(Knockdown.bodyPush(false) == Knockdown.BODY_PUSH && Knockdown.BODY_PUSH < 1f, "alone a zombie hit or a body run over pushes a car back less than the game has it");
        check(Knockdown.bodyPush(true) == 1f, "on a server as much as the game has it");
        check(Knockdown.slowFactor(false, 28f) == 0f && Knockdown.plantsGiveWay(false), "alone nothing a car touches caps its speed and plants give way");
        check(Knockdown.slowFactor(true, 28f) == 28f && !Knockdown.plantsGiveWay(true), "on a server the game's cap and push stand");
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
        check(Knockdown.smashing(false, true, 300f), "faster than any vehicle the game defines, off a ramp");
        check(!Knockdown.smashing(false, true, Knockdown.MOST_KMH + 1f), "not at a speed no vehicle goes");
        check(!Knockdown.smashing(false, true, Float.POSITIVE_INFINITY), "nor at an endless one");
        check(!Knockdown.smashing(false, true, Float.NaN), "nor at one that is no number");
        check(Knockdown.flung(Knockdown.MOST_KMH + 1f) && Knockdown.flung(Float.NaN) && !Knockdown.flung(120f), "flung past the most, or at no number");
        // The sweep looks at a square (2 * around + 1) across: around is the box, the clearance and ahead, plus one.
        int around = (int) Math.ceil(3f + Knockdown.ahead(Knockdown.MOST_KMH / 3.6f) + Knockdown.CLEARANCE) + 1;
        check((2 * around + 1) * (2 * around + 1) < 2000, "at the most a vehicle goes, the sweep looks at under 2000 squares: " + around);
    }

    private static void aPostCostsMoreThanAFenceAndPiecesMetAtOnceCompound() {
        float fence = Knockdown.speedKept(Knockdown.Kind.FENCE), post = Knockdown.speedKept(Knockdown.Kind.POST);
        check(fence > post, "a post takes more of the speed than a fence panel");
        check(fence >= 0.85f && fence < 1f, "a fence panel takes a little: " + fence);
        check(post >= 0.5f && post < 0.8f, "a post takes a good share and the vehicle still goes on: " + post);
        float three = Knockdown.speedKept(List.of(fence, fence, fence));
        check(Math.abs(three - fence * fence * fence) < 1e-6f, "three panels at once each take their share of what is left: " + three);
        check(Knockdown.speedKept(List.of()) == 1f, "nothing met takes nothing");
        check(Knockdown.squareness(true, false, 0f, 13.9f) == 1f, "a north fence driven at from the north is met square on");
        check(Knockdown.squareness(true, false, 13.9f, 0f) == 0f, "and one driven along not at all");
        check(Math.abs(Knockdown.squareness(false, true, 1f, 1f) - 0.7071f) < 1e-3f, "a west fence met at 45 degrees is met at its sine");
        check(Knockdown.squareness(false, false, 13.9f, 0f) == 1f, "a post is met whole whichever way");
        check(Knockdown.squareness(true, true, 13.9f, 0f) == 1f, "a corner piece is met as squarely as its squarer edge");
        check(Knockdown.speedKept(Knockdown.Kind.FENCE, 1f) == fence && Knockdown.speedKept(Knockdown.Kind.FENCE, 0f) == 1f,
                "a panel met square on takes its share, one grazed takes nothing");
        float grazed = 1f;
        for (int panel = 0; panel < 20; panel++) {
            grazed *= Knockdown.speedKept(Knockdown.Kind.FENCE, Knockdown.squareness(false, true, 1f, 10f));
        }
        check(grazed > 0.8f, "twenty panels grazed at about 6 degrees keep most of the speed: " + grazed);
    }

    private static void theCrashIsTheGamesForTheSpeedLost() {
        check(Knockdown.crash(0.5f) == 0f, "losing half a metre a second is no crash");
        check(Knockdown.crash(1f) == 0f, "nor is losing one");
        check(Knockdown.crash(1.39f) > 4f && Knockdown.crash(1.39f) < 4.3f, "a panel square on at 50 km/h is a crash of about 4");
        check(Knockdown.crash(4.86f) > Knockdown.crash(1.39f), "a post at 50 km/h is a harder one");
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
