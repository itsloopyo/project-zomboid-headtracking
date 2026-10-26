package com.cameraunlock.zomboid;

/** What made ground is made of, where the game's own words for a floor decide it. */
final class Ground {
    /** Loose stone, and the pale poured concrete of pavements, squares and car parks: materials of the picture, between the surfaces a model has, which run from 16 to 51, and what grows, which runs from 56. */
    static final float GRAVEL = 54f, PAVING = 55f;

    private Ground() {}

    /**
     * What a sprite on the ground is made of, or 0 where its sheet says. The game has one word,
     * Gravel, for how two grounds sound underfoot: the loose stone of a drive, and the pale
     * floor it lays along every street as the pavement and across squares and car parks,
     * which it calls Road_04 and paints smooth. Only the first is loose stone, and only a
     * floor is: an edging laid over a floor is not. Road_02, the sandier floor of old lots,
     * yards and warehouses, is poured concrete too, older and dirtier.
     */
    static float of(String floorMaterial, String footstep, boolean isFloor) {
        if ("Road_04".equals(floorMaterial) || "Road_02".equals(floorMaterial)) {
            return PAVING;
        }
        return isFloor && "Gravel".equals(footstep) ? GRAVEL : 0f;
    }

    /**
     * What a floor is made of: what of() makes it, or else what its sheet says (WorldScene.materialOf), but for sand,
     * which is the picture as the game paints it. The sheet of open ground has sand on it with the earth, and read as earth
     * a sandpit was a heap of grey soil.
     */
    static float floor(String floorMaterial, String footstep, String name) {
        float of = of(floorMaterial, footstep, true);
        return of != 0f ? of : "Sand".equals(floorMaterial) ? 0f : WorldScene.materialOf(name, true);
    }

    /**
     * Whether a made floor outdoors is loose ground, a gravel drive or a path of earth, sand or grass, which the
     * ground beside it runs a ragged fringe over. Paving of any kind is not: it ends at a straight edge.
     */
    static boolean loose(String floorMaterial, String footstep) {
        float of = of(floorMaterial, footstep, true);
        return of == GRAVEL || of != PAVING && ("Dirt".equals(footstep) || "Sand".equals(footstep) || "Grass".equals(footstep));
    }
}
