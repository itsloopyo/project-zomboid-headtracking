package com.cameraunlock.zomboid;

/**
 * How each broad-leaved species the game grows stands when its wood shows, bare in winter or
 * thinning in autumn: how many stems it has and how they lean, how high its leader goes, how
 * many limbs it has and how they climb, whether they leave in pairs, and how twiggy it is. The
 * wood itself is Flora.addCrown's; these are the proportions it is grown to.
 */
final class TreeForm {
    static final int NONE = -1, LINDEN = 0, MAPLE = 1, BIRCH = 2, HAWTHORN = 3, DOGWOOD = 4, SILVERBELL = 5, YELLOWWOOD = 6, REDBUD = 7, KINDS = 8;
    /** The species, as the game names its trees' tiles. Holly, hemlock and pine keep their leaves and are not here. */
    private static final String[] SPECIES = {"americanlinden", "redmaple", "riverbirch", "cockspurhawthorn", "dogwood", "carolinasilverbell", "yellowwood",
        "easternredbud"};

    /**
     * How many stems the trunk parts into, at least and at most, and how far each leans out,
     * in degrees: a river birch and a hawthorn are stems nearly from the ground, a redbud
     * opens into a vase.
     */
    static final int[] STEMS_LEAST = {1, 1, 2, 2, 1, 1, 1, 2}, STEMS_MOST = {1, 1, 3, 3, 1, 1, 1, 3};
    static final float[] STEM_LEAN = {0f, 0f, 9f, 16f, 0f, 0f, 0f, 24f};
    /** How far up the crown the leader or each stem goes, as a share of it. */
    static final float[] LEADER_TOP = {0.8f, 0.86f, 0.92f, 0.8f, 0.62f, 0.94f, 0.84f, 0.72f};
    /** How many limbs, for as many as the crown's width gives a tree, and how far up the crown a limb climbs to its end besides a share of its own. */
    static final float[] LIMBS = {1f, 1.1f, 0.85f, 1f, 1.05f, 1.25f, 1f, 0.9f};
    static final float[] LIMB_RISE = {0.16f, 0.28f, 0.1f, 0.2f, 0.03f, 0.02f, 0.18f, 0.12f};
    /** Whether limbs and branches leave in pairs, one either side, as a maple's and a dogwood's do. */
    static final boolean[] OPPOSITE = {false, true, false, false, true, false, false, false};
    /** How far up the crown a branch reaches beyond its limb, as a share of it: a birch's hang. */
    static final float[] BRANCH_RISE = {0.22f, 0.26f, 0.06f, 0.2f, 0.16f, 0.14f, 0.22f, 0.2f};
    /** How many twigs end each branch, and how steeply they turn up for how a typical tree's do. */
    static final int[] TWIGS = {3, 3, 3, 4, 4, 3, 3, 3};
    static final float[] TWIG_RISE = {1f, 1.15f, 0.55f, 0.9f, 1.6f, 0.8f, 1f, 1f};

    /** Dev only: every tree is grown as one of no species, as before TreeForm, to set the two side by side. See WorldScene.without. */
    static volatile boolean withoutForms;

    private TreeForm() {}

    /** Which species a tree's tile is, NONE for one that keeps its leaves or is no tree of the game's. */
    static int kindOf(String tile) {
        String species = FarTreeKinds.speciesOf(tile);
        for (int kind = 0; species != null && kind < KINDS; kind++) {
            if (SPECIES[kind].equals(species)) {
                return kind;
            }
        }
        return NONE;
    }

    /** Whether a tree of the kind is stems from a short foot: the game paints them side by side, and what it paints as their bole is all of them. */
    static boolean footed(int kind) {
        return kind != NONE && STEMS_LEAST[kind] > 1 && STEM_LEAN[kind] < 20f;
    }

    /** Of a bole read off a footed tree's sprite, how much is its foot, up it and across. */
    static final float FOOT_TALL = 0.4f, FOOT_THICK = 0.6f;
    /**
     * Of a bole read off any other broad-leaved tree's sprite, how much stands under its wood
     * when the wood shows: the bole is read as far up as a tree in leaf shows its trunk, and an
     * open-grown tree's lowest limbs leave it below that.
     */
    static final float UNDER_WOOD = 0.75f;
}
