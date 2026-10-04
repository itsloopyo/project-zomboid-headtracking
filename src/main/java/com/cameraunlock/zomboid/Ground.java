package com.cameraunlock.zomboid;

/** What made ground is made of, where the game's own words for a floor decide it. */
final class Ground {
    /** Loose stone, and the pale slabs of pavements, squares and car parks: materials of the picture, between the surfaces a model has, which run from 16 to 51, and what grows, which runs from 56. */
    static final float GRAVEL = 54f, PAVING = 55f;

    private Ground() {}

    /**
     * What a sprite on the ground is made of, or 0 where its sheet says. The game has one word,
     * Gravel, for how two grounds sound underfoot: the loose stone of a drive, and the pale
     * floor it lays along every street as the pavement and across squares and car parks,
     * which it calls Road_04 and paints smooth. Only the first is loose stone, and only a
     * floor is: an edging laid over a floor is not.
     */
    static float of(String floorMaterial, String footstep, boolean isFloor) {
        if ("Road_04".equals(floorMaterial)) {
            return PAVING;
        }
        return isFloor && "Gravel".equals(footstep) ? GRAVEL : 0f;
    }
}
