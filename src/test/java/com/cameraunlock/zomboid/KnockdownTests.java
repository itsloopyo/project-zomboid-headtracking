package com.cameraunlock.zomboid;

/** What a vehicle knocks down, how fast it has to go, and what that costs it. */
public final class KnockdownTests {
    private KnockdownTests() {}

    static void run() {
        Checks.run("fencesAndPostsAreKnockedDownAndNothingElse", KnockdownTests::fencesAndPostsAreKnockedDownAndNothingElse);
        Checks.run("aPushAtAWalkKnocksNothingDown", KnockdownTests::aPushAtAWalkKnocksNothingDown);
        Checks.run("aPostCostsMoreThanAFence", KnockdownTests::aPostCostsMoreThanAFence);
        Checks.run("theDamageGrowsWithTheSpeedEitherWay", KnockdownTests::theDamageGrowsWithTheSpeedEitherWay);
        Checks.run("aThingIsMetAheadAndAlongsideButNotFurtherAside", KnockdownTests::aThingIsMetAheadAndAlongsideButNotFurtherAside);
    }

    private static void aThingIsMetAheadAndAlongsideButNotFurtherAside() {
        // A box 2 tiles wide and 4.5 long.
        float across = 1f, along = 2.25f, ahead = Knockdown.ahead(13.9f);
        check(ahead > 1.3f && ahead < 1.5f, "at 50 km/h a thing is met about a tile and a half ahead: " + ahead);
        check(Knockdown.ahead(0f) == 0f, "standing, nothing is met ahead of the box");
        check(Knockdown.inReach(0f, along + 1f, across, along, ahead), "a fence a tile ahead of the bumper at 50 km/h is met");
        check(!Knockdown.inReach(0f, along + 2f, across, along, ahead), "one two tiles ahead is not, yet");
        check(Knockdown.inReach(0f, along + 1f, across, along, ahead) == Knockdown.inReach(0.9f, along + 1f, across, along, ahead),
                "the whole width of the bumper meets it");
        check(Knockdown.inReach(across + 0.1f, 0f, across, along, ahead), "a fence brushed along the side is met");
        check(!Knockdown.inReach(across + 0.5f, 0f, across, along, ahead), "one half a tile off the side is not, however fast");
        check(!Knockdown.inReach(0f, -along - 0.5f, across, along, ahead), "nor is one behind, however fast");
        check(Knockdown.inReach(0f, -along - 0.1f, across, along, ahead), "though the back bumper touches what it backs onto");
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void fencesAndPostsAreKnockedDownAndNothingElse() {
        check(Knockdown.of("fencing_01_57", "MetalWire") == Knockdown.Kind.FENCE, "tall chain link is a fence");
        check(Knockdown.of("fencing_01_21", "MetalWire") == Knockdown.Kind.FENCE, "short chain link is a fence");
        check(Knockdown.of("fencing_01_5", "Wood") == Knockdown.Kind.FENCE, "a white picket fence is a fence");
        check(Knockdown.of("fencing_01_9", "Wood") == Knockdown.Kind.FENCE, "a tall wooden fence is a fence");
        check(Knockdown.of("fencing_01_19", "Wood") == Knockdown.Kind.FENCE, "a fence post goes with its fence");
        check(Knockdown.of("fencing_damaged_01_34", "MetalWire") == Knockdown.Kind.FENCE, "so does a piece already broken at one side");
        check(Knockdown.of("fencing_01_104", "Stone") == null, "a stone fence is a low wall and stands");
        check(Knockdown.of("lighting_outdoor_01_16", null) == Knockdown.Kind.POST, "the bare post under a tall street lamp is a post");
        check(Knockdown.of("lighting_outdoor_01_4", null) == Knockdown.Kind.POST, "and so is the black post under an acorn lamp");
        check(Knockdown.of("street_decoration_01_0", "MetalBars") == null, "a stop sign is left to the game");
        check(Knockdown.of("trashcontainers_01_0", "PlasticHard") == null, "and so is a bin");
        check(Knockdown.of("walls_exterior_house_01_0", "Wood") == null, "a wall stands");
        check(Knockdown.of(null, null) == null, "a thing with no sprite is nothing");
    }

    private static void aPushAtAWalkKnocksNothingDown() {
        check(!Knockdown.fastEnough(0f), "standing still");
        check(!Knockdown.fastEnough(5f), "a push at a walk");
        check(!Knockdown.fastEnough(Knockdown.WALKING_KMH), "just at the limit");
        check(Knockdown.fastEnough(Knockdown.WALKING_KMH + 0.5f), "just over it");
        check(Knockdown.fastEnough(60f), "driving");
        check(Knockdown.fastEnough(-20f), "reversing hard");
        check(!Knockdown.fastEnough(-5f), "but not reversing at a walk");
    }

    private static void aPostCostsMoreThanAFence() {
        float fence = Knockdown.speedKept(Knockdown.Kind.FENCE), post = Knockdown.speedKept(Knockdown.Kind.POST);
        check(fence > post, "a post takes more of the speed than a fence panel");
        check(fence >= 0.85f && fence < 1f, "a fence panel takes a little: " + fence);
        check(post >= 0.5f && post < 0.8f, "a post takes a good share and the vehicle still goes on: " + post);
        // Driven square through a fence, a car can meet three panels.
        float throughAFence = fence * fence * fence;
        check(throughAFence > 0.66f, "through a fence square on, a car keeps two thirds of its speed or more: " + throughAFence);
        check(Knockdown.damage(Knockdown.Kind.POST, 50f) > Knockdown.damage(Knockdown.Kind.FENCE, 50f), "a post dents more than a fence panel");
    }

    private static void theDamageGrowsWithTheSpeedEitherWay() {
        check(Knockdown.damage(Knockdown.Kind.FENCE, 50f) == 3, "a fence panel at 50 km/h: " + Knockdown.damage(Knockdown.Kind.FENCE, 50f));
        check(Knockdown.damage(Knockdown.Kind.POST, 50f) == 10, "a post at 50 km/h: " + Knockdown.damage(Knockdown.Kind.POST, 50f));
        check(Knockdown.damage(Knockdown.Kind.POST, 100f) > Knockdown.damage(Knockdown.Kind.POST, 50f), "faster hits harder");
        check(Knockdown.damage(Knockdown.Kind.FENCE, -40f) == Knockdown.damage(Knockdown.Kind.FENCE, 40f), "backwards as hard as forwards");
    }
}
